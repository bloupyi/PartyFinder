package fr.bloup.partyfinder.client.net;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.bloup.partyfinder.Partyfinder;
import fr.bloup.partyfinder.client.PartyActions;
import fr.bloup.partyfinder.client.config.PartyFinderConfig;
import fr.bloup.partyfinder.client.party.EventAlerts;
import fr.bloup.partyfinder.client.party.Listing;
import fr.bloup.partyfinder.client.party.ListingDraft;
import fr.bloup.partyfinder.client.party.MemberStatus;
import fr.bloup.partyfinder.client.party.PartyStatus;
import fr.bloup.partyfinder.client.party.Moderation;
import fr.bloup.partyfinder.client.party.PartyFinderState;
import fr.bloup.partyfinder.client.party.PartySync;
import fr.bloup.partyfinder.client.party.PartyTransfer;
import fr.bloup.partyfinder.client.party.ReportSummary;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.User;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class BackendConnection {
    private static final String BACKEND_URL = "wss://partyfinder.bloup.site";
    private static final int PROTOCOL = 6;
    private static final int CLOSE_BANNED = 4004;
    private static final int CLOSE_OUTDATED = 4005;
    private static final int MAX_MESSAGE_CHARS = 4 * 1024 * 1024;
    private static final int MAX_LISTINGS = 5000;
    private static final Pattern SERVER_ID = Pattern.compile("[0-9a-f]{16,64}");
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "PartyFinder-Backend");
        thread.setDaemon(true);
        return thread;
    });

    private static WebSocket socket;
    private static CompletableFuture<?> sendChain = CompletableFuture.completedFuture(null);
    private static boolean wanted;
    private static int attempt;
    private static long generation;

    private BackendConnection() {
    }

    public static boolean isConnected() {
        return PartyFinderState.status() == PartyFinderState.Status.ONLINE;
    }

    /** Only Hypixel: the finder is meaningless elsewhere and must not be fed from fake servers. */
    public static boolean isAllowedServer() {
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) return true;
        ServerData server = Minecraft.getInstance().getCurrentServer();
        if (server == null) return false;
        String host = server.ip.toLowerCase(Locale.ROOT).strip();
        int port = host.lastIndexOf(':');
        if (port > 0 && host.indexOf(':') == port) host = host.substring(0, port);
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        return host.equals("hypixel.net") || host.endsWith(".hypixel.net");
    }

    public static synchronized void connect() {
        if (!isAllowedServer()) return;
        wanted = true;
        if (socket != null || PartyFinderState.status() == PartyFinderState.Status.CONNECTING) return;
        open(++generation);
    }

    public static synchronized void disconnect() {
        wanted = false;
        generation++;
        attempt = 0;
        if (socket != null) {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
            socket = null;
        }
        onClient(() -> PartyFinderState.setStatus(PartyFinderState.Status.OFFLINE));
    }

    public static void reconnect() {
        disconnect();
        connect();
    }

    private static void open(long gen) {
        onClient(() -> PartyFinderState.setStatus(PartyFinderState.Status.CONNECTING));
        URI uri = backendUri();
        if (uri == null) {
            onClient(() -> PartyFinderState.setStatus(PartyFinderState.Status.OFFLINE));
            return;
        }
        HTTP.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(uri, new Listener(gen))
                .whenComplete((ws, error) -> {
                    synchronized (BackendConnection.class) {
                        if (error != null) {
                            Partyfinder.LOGGER.warn("Backend connection failed: {}", error.getMessage());
                            scheduleReconnect(gen);
                        } else if (gen != generation) {
                            ws.sendClose(WebSocket.NORMAL_CLOSURE, "stale");
                        } else {
                            socket = ws;
                            attempt = 0;
                        }
                    }
                });
    }

    private static URI backendUri() {
        // Players cannot redirect the mod; only a development run may point it at a local server.
        String override = FabricLoader.getInstance().isDevelopmentEnvironment() ? System.getProperty("partyfinder.backend") : null;
        if (override == null) return URI.create(BACKEND_URL);
        try {
            URI uri = URI.create(override);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if ("wss".equals(uri.getScheme()) || host.equals("localhost") || host.equals("127.0.0.1")) return uri;
        } catch (IllegalArgumentException ignored) {
        }
        Partyfinder.LOGGER.error("Ignoring invalid dev backend override {}", override);
        return URI.create(BACKEND_URL);
    }

    private static synchronized void scheduleReconnect(long gen) {
        if (gen != generation) return;
        socket = null;
        onClient(() -> PartyFinderState.setStatus(PartyFinderState.Status.OFFLINE));
        if (!wanted) return;
        long delay = Math.min(60, 2L << Math.min(attempt++, 5));
        SCHEDULER.schedule(() -> {
            synchronized (BackendConnection.class) {
                if (wanted && gen == generation && socket == null) open(++generation);
            }
        }, delay, TimeUnit.SECONDS);
    }

    public static synchronized void send(JsonObject message) {
        WebSocket ws = socket;
        if (ws == null) return;
        String text = GSON.toJson(message);
        sendChain = sendChain.handle((r, e) -> null).thenCompose(v -> ws.sendText(text, true));
    }

    private static long publishSentAt;

    /** Publishing gets a visible answer: the listing, a refusal, or this timeout message. */
    public static void checkPublishTimeout() {
        if (publishSentAt == 0 || System.currentTimeMillis() - publishSentAt < 8_000) return;
        publishSentAt = 0;
        PartyActions.chat(Component.translatable("partyfinder.error.no_response").withStyle(ChatFormatting.RED));
    }

    public static void publishAnswered() {
        publishSentAt = 0;
    }

    public static void publish(ListingDraft draft) {
        if (!isConnected()) {
            PartyActions.chat(Component.translatable("partyfinder.screen.offline").withStyle(ChatFormatting.RED));
            return;
        }
        publishSentAt = System.currentTimeMillis();
        publish(draft.activity(), draft.options(), draft.note(), draft.requirements(), draft.maxSize(), draft.maxReports(),
                draft.minStats(), draft.roles());
    }

    public static void publish(String activity, Map<String, String> options, String note, String requirements, int maxSize, int maxReports,
                               Map<String, Integer> minStats, List<String> roles) {
        JsonObject msg = message("publish");
        msg.addProperty("activity", activity);
        JsonObject optionsJson = new JsonObject();
        options.forEach(optionsJson::addProperty);
        msg.add("options", optionsJson);
        msg.addProperty("note", note);
        msg.addProperty("requirements", requirements);
        msg.addProperty("maxSize", maxSize);
        msg.addProperty("maxReports", maxReports);
        JsonObject minJson = new JsonObject();
        minStats.forEach(minJson::addProperty);
        msg.add("minStats", minJson);
        JsonArray rolesJson = new JsonArray();
        roles.forEach(rolesJson::add);
        msg.add("roles", rolesJson);
        send(msg);
    }

    public static void delist() {
        send(message("delist"));
    }

    public static void requestJoin(String listingId) {
        JsonObject msg = message("join");
        msg.addProperty("listing", listingId);
        send(msg);
    }

    public static void partyUpdate(Set<UUID> members, UUID leader) {
        JsonObject msg = message("party_update");
        msg.addProperty("leader", leader.toString());
        JsonArray array = new JsonArray();
        members.forEach(uuid -> array.add(uuid.toString()));
        msg.add("members", array);
        send(msg);
    }

    public static void memberStatus(Set<UUID> party, MemberStatus status) {
        JsonObject msg = message("member_status");
        JsonArray array = new JsonArray();
        party.forEach(uuid -> array.add(uuid.toString()));
        msg.add("party", array);
        if (status != null) msg.add("status", GSON.toJsonTree(status));
        send(msg);
    }

    public static void endorse(UUID target) {
        JsonObject msg = message("endorse");
        msg.addProperty("target", target.toString());
        send(msg);
    }

    public static void leave() {
        send(message("leave"));
    }

    public static void respond(Listing.Player player, boolean accept, boolean invited) {
        JsonObject msg = message("respond");
        msg.addProperty("player", player.uuid().toString());
        msg.addProperty("accept", accept);
        msg.addProperty("invited", invited);
        send(msg);
    }

    /** Asks the current Hypixel leader of the finder party to transfer the lead to us so we can /p warp. */
    public static void warp() {
        send(message("warp"));
    }

    /** PF leader who lost the Hypixel lead: the current Hypixel leader sends the invite. */
    public static void invite(String name) {
        JsonObject msg = message("invite");
        msg.addProperty("name", name);
        send(msg);
    }

    public static void report(Listing.Player target, String reason, String comment) {
        JsonObject msg = message("report");
        msg.addProperty("target", target.uuid().toString());
        msg.addProperty("reason", reason);
        msg.addProperty("comment", comment);
        send(msg);
    }

    public static void opReports() {
        send(message("op_reports"));
    }

    public static void opPlayer(UUID uuid) {
        JsonObject msg = message("op_player");
        msg.addProperty("uuid", uuid.toString());
        send(msg);
    }

    public static void opBan(UUID uuid, String reason) {
        JsonObject msg = message("op_ban");
        msg.addProperty("uuid", uuid.toString());
        msg.addProperty("reason", reason);
        send(msg);
    }

    public static void opUnban(UUID uuid) {
        JsonObject msg = message("op_unban");
        msg.addProperty("uuid", uuid.toString());
        send(msg);
    }

    public static void opDismiss(UUID uuid, String reportId) {
        JsonObject msg = message("op_dismiss");
        msg.addProperty("uuid", uuid.toString());
        if (reportId != null) msg.addProperty("report", reportId);
        send(msg);
    }

    private static JsonObject message(String type) {
        JsonObject msg = new JsonObject();
        msg.addProperty("t", type);
        return msg;
    }

    private static void onClient(Runnable task) {
        Minecraft.getInstance().execute(task);
    }

    private static void authenticate(WebSocket ws, String serverId) {
        Minecraft mc = Minecraft.getInstance();
        User user = mc.getUser();
        try {
            mc.services().sessionService().joinServer(user.getProfileId(), user.getAccessToken(), serverId);
        } catch (Exception e) {
            Partyfinder.LOGGER.warn("Mojang session join failed, backend may reject auth: {}", e.getMessage());
        }
        JsonObject msg = message("auth");
        msg.addProperty("protocol", PROTOCOL);
        msg.addProperty("name", user.getName());
        msg.addProperty("uuid", user.getProfileId().toString());
        ws.sendText(GSON.toJson(msg), true);
    }

    private static void handle(WebSocket ws, JsonObject msg) {
        String type = msg.has("t") ? msg.get("t").getAsString() : "";
        switch (type) {
            case "hello" -> {
                String serverId = msg.get("serverId").getAsString();
                if (!SERVER_ID.matcher(serverId).matches()) {
                    ws.sendClose(WebSocket.NORMAL_CLOSURE, "bad hello");
                    return;
                }
                SCHEDULER.execute(() -> authenticate(ws, serverId));
            }
            case "ready" -> {
                Listing.Player you = player(msg.get("you"));
                if (you == null) return;
                boolean op = msg.has("op") && msg.get("op").getAsBoolean();
                ReportSummary reports = reports(msg.get("reports"));
                onClient(() -> {
                    PartyFinderState.setSession(you, op, reports);
                    PartyFinderState.setStatus(PartyFinderState.Status.ONLINE);
                });
            }
            case "self_reports" -> {
                ReportSummary reports = reports(msg.get("reports"));
                onClient(() -> PartyFinderState.setSelfReports(reports));
            }
            case "info" -> {
                String key = msg.get("key").getAsString();
                List<String> args = new ArrayList<>();
                if (msg.has("args")) {
                    for (JsonElement arg : msg.getAsJsonArray("args")) {
                        if (args.size() < 4) args.add(Listing.cleanText(arg.getAsString(), 64));
                    }
                }
                onClient(() -> BackendEvents.onInfo(key, args));
            }
            case "report_new" -> {
                Moderation.Report report = report(msg.get("report"));
                Listing.Player target = player(msg.getAsJsonObject("report").get("target"));
                int count = msg.get("count").getAsInt();
                if (report != null && target != null) onClient(() -> BackendEvents.onReportNew(target, report, count));
            }
            case "op_reports" -> {
                List<Moderation.ReportedPlayer> players = new ArrayList<>();
                for (JsonElement element : msg.getAsJsonArray("players")) {
                    Moderation.ReportedPlayer player = reportedPlayer(element);
                    if (player != null && players.size() < MAX_LISTINGS) players.add(player);
                }
                onClient(() -> {
                    Moderation.setReportedPlayers(players);
                    PartyFinderState.changed();
                });
            }
            case "op_player" -> {
                Listing.Player player = player(msg.get("player"));
                if (player == null) return;
                boolean banned = msg.get("banned").getAsBoolean();
                Moderation.Ban ban = null;
                if (msg.has("ban") && msg.get("ban").isJsonObject()) {
                    Moderation.Ban raw = GSON.fromJson(msg.get("ban"), Moderation.Ban.class);
                    ban = new Moderation.Ban(Listing.cleanText(raw.name(), 16), Listing.cleanText(raw.reason(), 120),
                            Listing.cleanText(raw.by(), 16), raw.at());
                }
                List<Moderation.Report> reports = new ArrayList<>();
                for (JsonElement element : msg.getAsJsonArray("reports")) {
                    Moderation.Report report = report(element);
                    if (report != null && reports.size() < MAX_LISTINGS) reports.add(report);
                }
                Moderation.PlayerDetail detail = new Moderation.PlayerDetail(player, banned, ban, reports);
                onClient(() -> {
                    Moderation.setDetail(detail);
                    PartyFinderState.changed();
                });
            }
            case "snapshot" -> {
                List<Listing> listings = new ArrayList<>();
                JsonArray array = msg.getAsJsonArray("listings");
                for (JsonElement element : array) {
                    if (listings.size() >= MAX_LISTINGS) break;
                    Listing listing = listing(element);
                    if (listing != null) listings.add(listing);
                }
                onClient(() -> PartyFinderState.replaceAll(listings));
            }
            case "upsert" -> {
                Listing listing = listing(msg.get("listing"));
                if (listing == null) {
                    Partyfinder.LOGGER.warn("Dropped invalid listing from backend: {}", msg.get("listing"));
                } else {
                    onClient(() -> {
                        if (PartyFinderState.isSelf(listing.owner())) publishAnswered();
                        PartyFinderState.upsert(listing);
                    });
                }
            }
            case "remove" -> {
                String id = msg.get("id").getAsString();
                onClient(() -> PartyFinderState.remove(id));
            }
            case "join_request" -> {
                Listing.Player from = player(msg.get("from"));
                ReportSummary reports = reports(msg.get("reports"));
                JsonObject stats = msg.has("stats") && msg.get("stats").isJsonObject() ? msg.getAsJsonObject("stats") : null;
                boolean unverified = msg.has("unverified") && msg.get("unverified").getAsBoolean();
                if (from != null) onClient(() -> BackendEvents.onJoinRequest(new PartyFinderState.JoinRequest(from, reports, stats, unverified)));
            }
            case "join_response" -> {
                Listing.Player owner = player(msg.get("owner"));
                String listingId = msg.get("listing").getAsString();
                boolean accept = msg.get("accept").getAsBoolean();
                String inviter = msg.has("inviter") ? msg.get("inviter").getAsString() : null;
                if (owner != null) onClient(() -> BackendEvents.onJoinResponse(listingId, owner, inviter, accept));
            }
            case "kick_member" -> {
                Listing.Player member = player(msg);
                String listingId = msg.has("listing") ? msg.get("listing").getAsString() : "";
                if (member != null) onClient(() -> PartySync.onKickMember(listingId, member.uuid(), member.name()));
            }
            case "transfer_request" -> {
                Listing.Player to = player(msg.get("to"));
                String listingId = msg.get("listing").getAsString();
                if (to != null) onClient(() -> PartyTransfer.onTransferRequest(listingId, to));
            }
            case "invite_member" -> {
                String name = msg.get("name").getAsString();
                String listingId = msg.has("listing") ? msg.get("listing").getAsString() : "";
                onClient(() -> PartyTransfer.onInviteMember(listingId, name));
            }
            case "member_status" -> {
                UUID uuid = UUID.fromString(msg.get("uuid").getAsString());
                MemberStatus status = MemberStatus.sanitize(GSON.fromJson(msg.get("status"), MemberStatus.class));
                if (status != null) onClient(() -> PartyStatus.onRemote(uuid, status));
            }
            case "events" -> {
                List<EventAlerts.Event> events = new ArrayList<>();
                for (JsonElement element : msg.getAsJsonArray("events")) {
                    try {
                        events.add(GSON.fromJson(element, EventAlerts.Event.class));
                    } catch (RuntimeException ignored) {
                    }
                }
                onClient(() -> EventAlerts.setEvents(events));
            }
            case "party_removed" -> {
                String listingId = msg.get("listing").getAsString();
                onClient(() -> PartySync.onPartyRemoved(listingId));
            }
            case "error" -> {
                String key = msg.get("key").getAsString();
                List<String> args = new ArrayList<>();
                if (msg.has("args") && msg.get("args").isJsonArray()) {
                    for (JsonElement arg : msg.getAsJsonArray("args")) {
                        if (args.size() < 8) args.add(Listing.cleanText(arg.getAsString(), 32));
                    }
                }
                onClient(() -> {
                    publishAnswered();
                    BackendEvents.onError(key, args);
                });
            }
            default -> Partyfinder.LOGGER.debug("Unknown backend message {}", type);
        }
    }

    private static Listing.Player player(JsonElement json) {
        try {
            Listing.Player player = GSON.fromJson(json, Listing.Player.class);
            return player != null && player.isValid() ? player : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static ReportSummary reports(JsonElement json) {
        try {
            return ReportSummary.sanitize(GSON.fromJson(json, ReportSummary.class));
        } catch (RuntimeException e) {
            return ReportSummary.NONE;
        }
    }

    private static Moderation.Report report(JsonElement json) {
        try {
            Moderation.Report raw = GSON.fromJson(json, Moderation.Report.class);
            if (raw == null || raw.reporter() == null || !raw.reporter().isValid()) return null;
            if (raw.reason() == null || !ReportSummary.REASONS.contains(raw.reason())) return null;
            return new Moderation.Report(Listing.cleanText(raw.id(), 36), raw.reporter(), raw.reason(),
                    Listing.cleanText(raw.comment(), 120), raw.at());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Moderation.ReportedPlayer reportedPlayer(JsonElement json) {
        try {
            Moderation.ReportedPlayer raw = GSON.fromJson(json, Moderation.ReportedPlayer.class);
            if (raw == null || raw.uuid() == null || !Listing.isValidName(raw.name())) return null;
            ReportSummary summary = ReportSummary.sanitize(new ReportSummary(raw.count(), raw.reasons(), 0));
            return new Moderation.ReportedPlayer(raw.uuid(), raw.name(), summary.count(), summary.reasons(), raw.lastAt(), raw.banned());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Listing listing(JsonElement json) {
        try {
            return Listing.sanitize(GSON.fromJson(json, Listing.class));
        } catch (RuntimeException e) {
            Partyfinder.LOGGER.warn("Cannot read listing: {}", e.toString());
            return null;
        }
    }

    private static final class Listener implements WebSocket.Listener {
        private final long gen;
        private final StringBuilder buffer = new StringBuilder();

        private Listener(long gen) {
            this.gen = gen;
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buffer.append(data);
            if (buffer.length() > MAX_MESSAGE_CHARS) {
                Partyfinder.LOGGER.warn("Backend message too large, closing connection");
                buffer.setLength(0);
                ws.abort();
                return null;
            }
            if (last) {
                String text = buffer.toString();
                buffer.setLength(0);
                try {
                    handle(ws, JsonParser.parseString(text).getAsJsonObject());
                } catch (Exception e) {
                    Partyfinder.LOGGER.warn("Bad backend message: {}", e.getMessage());
                }
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            Partyfinder.LOGGER.info("Backend closed ({}): {}", statusCode, reason);
            if (statusCode == CLOSE_BANNED || statusCode == CLOSE_OUTDATED) {
                synchronized (BackendConnection.class) {
                    wanted = false;
                }
            }
            scheduleReconnect(gen);
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            Partyfinder.LOGGER.warn("Backend error: {}", error.getMessage());
            scheduleReconnect(gen);
        }
    }
}
