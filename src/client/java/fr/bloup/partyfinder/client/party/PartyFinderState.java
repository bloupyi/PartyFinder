package fr.bloup.partyfinder.client.party;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PartyFinderState {
    public enum Status { OFFLINE, CONNECTING, ONLINE }

    /** stats is the requester's compact SkyBlock summary; unverified when the server could not check requirements. */
    public record JoinRequest(Listing.Player player, ReportSummary reports, JsonObject stats, boolean unverified) {
    }

    private static final Map<String, Listing> LISTINGS = new LinkedHashMap<>();
    private static final Map<UUID, JoinRequest> JOIN_REQUESTS = new LinkedHashMap<>();
    private static final Map<UUID, Long> OUTGOING_REQUESTS = new LinkedHashMap<>();
    private static final long OUTGOING_REQUEST_TTL_MS = 2 * 60 * 1000;
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private static Status status = Status.OFFLINE;
    private static Listing.Player self;
    private static ReportSummary selfReports = ReportSummary.NONE;
    private static boolean op;

    private PartyFinderState() {
    }

    public static Status status() {
        return status;
    }

    public static Optional<Listing.Player> self() {
        return Optional.ofNullable(self);
    }

    public static boolean isSelf(Listing.Player player) {
        return self != null && player != null && self.uuid().equals(player.uuid());
    }

    public static ReportSummary selfReports() {
        return selfReports;
    }

    public static boolean isOp() {
        return op && status == Status.ONLINE;
    }

    public static void setStatus(Status newStatus) {
        status = newStatus;
        if (newStatus != Status.ONLINE) {
            LISTINGS.clear();
            JOIN_REQUESTS.clear();
            OUTGOING_REQUESTS.clear();
            Moderation.clear();
        }
        changed();
    }

    public static void setSession(Listing.Player player, boolean isOp, ReportSummary reports) {
        self = player;
        op = isOp;
        selfReports = reports;
        changed();
    }

    public static void setSelfReports(ReportSummary reports) {
        selfReports = reports;
        changed();
    }

    public static void replaceAll(Collection<Listing> listings) {
        LISTINGS.clear();
        listings.forEach(l -> LISTINGS.put(l.id(), l));
        changed();
    }

    public static void upsert(Listing listing) {
        LISTINGS.put(listing.id(), listing);
        changed();
    }

    public static void remove(String id) {
        if (LISTINGS.remove(id) != null) changed();
    }

    public static Optional<Listing> get(String id) {
        return Optional.ofNullable(LISTINGS.get(id));
    }

    public static List<Listing> listings() {
        List<Listing> list = new ArrayList<>(LISTINGS.values());
        // Full parties always sink to the bottom; newest first within each group.
        list.sort(Comparator.comparing(Listing::isFull).thenComparing(Comparator.comparingLong(Listing::createdAt).reversed()));
        return list;
    }

    public static Optional<Listing> ownListing() {
        if (self == null) return Optional.empty();
        return LISTINGS.values().stream().filter(l -> isSelf(l.owner())).findFirst();
    }

    public static Optional<Listing.Player> findPlayer(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (Listing listing : LISTINGS.values()) {
            if (listing.owner().name().toLowerCase(Locale.ROOT).equals(lower)) return Optional.of(listing.owner());
        }
        for (JoinRequest request : JOIN_REQUESTS.values()) {
            if (request.player().name().toLowerCase(Locale.ROOT).equals(lower)) return Optional.of(request.player());
        }
        return Optional.empty();
    }

    public static List<String> knownNames() {
        List<String> names = new ArrayList<>();
        LISTINGS.values().forEach(l -> names.add(l.owner().name()));
        JOIN_REQUESTS.values().forEach(r -> names.add(r.player().name()));
        return names;
    }

    public static void addJoinRequest(JoinRequest request) {
        JOIN_REQUESTS.put(request.player().uuid(), request);
        changed();
    }

    public static Optional<Listing.Player> takeJoinRequest(String name) {
        for (JoinRequest request : JOIN_REQUESTS.values()) {
            if (request.player().name().equalsIgnoreCase(name)) {
                JOIN_REQUESTS.remove(request.player().uuid());
                changed();
                return Optional.of(request.player());
            }
        }
        return Optional.empty();
    }

    public static void markRequested(UUID owner) {
        OUTGOING_REQUESTS.put(owner, System.currentTimeMillis() + OUTGOING_REQUEST_TTL_MS);
    }

    public static boolean consumeRequested(UUID owner) {
        Long expiry = OUTGOING_REQUESTS.remove(owner);
        return expiry != null && expiry >= System.currentTimeMillis();
    }

    public static List<JoinRequest> joinRequests() {
        return List.copyOf(JOIN_REQUESTS.values());
    }

    public static void addListener(Runnable listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(Runnable listener) {
        LISTENERS.remove(listener);
    }

    public static void changed() {
        LISTENERS.forEach(Runnable::run);
    }
}
