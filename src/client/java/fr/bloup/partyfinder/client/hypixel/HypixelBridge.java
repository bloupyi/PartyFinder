package fr.bloup.partyfinder.client.hypixel;

import fr.bloup.partyfinder.Partyfinder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Real Hypixel party and location (Hypixel Mod API protocol). Party info is pulled, not pushed, so it is refreshed
 * after party chat messages and periodically while something needs it.
 */
public final class HypixelBridge {
    public enum Role { LEADER, MOD, MEMBER }

    interface Transport {
        void init();

        void requestParty();
    }

    private static final long MIN_REQUEST_INTERVAL_MS = 3_000;
    private static final long CHAT_REFRESH_DELAY_MS = 750;
    private static final long PERIODIC_REFRESH_MS = 30_000;
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private static Transport transport;
    private static boolean known;
    private static boolean inParty;
    private static Map<UUID, Role> members = Map.of();
    private static boolean onSkyBlock;
    private static String mode = "";
    private static String map = "";
    private static long lastRequest;
    private static long refreshAt;

    private HypixelBridge() {
    }

    public static void init() {
        boolean official = FabricLoader.getInstance().isModLoaded("hypixel-mod-api");
        transport = official ? new ModApiTransport() : new HypixelProtocol();
        transport.init();
        Partyfinder.LOGGER.info("Hypixel party data through {}", official ? "the Hypixel Mod API mod" : "the built-in protocol");
    }

    public static void addListener(Runnable listener) {
        LISTENERS.add(listener);
    }

    public static boolean isKnown() {
        return known;
    }

    public static boolean inParty() {
        return inParty;
    }

    public static Optional<UUID> leader() {
        return members.entrySet().stream().filter(e -> e.getValue() == Role.LEADER).map(Map.Entry::getKey).findFirst();
    }

    public static Role role(UUID uuid) {
        return members.getOrDefault(uuid, Role.MEMBER);
    }

    /** Party members including the local player (leader first), or just the local player when not in a party. */
    public static Set<UUID> members() {
        if (inParty && !members.isEmpty()) return members.keySet();
        return Set.of(Minecraft.getInstance().getUser().getProfileId());
    }

    public static boolean isLeaderOrSolo() {
        return !inParty || leader().map(Minecraft.getInstance().getUser().getProfileId()::equals).orElse(false);
    }

    public static boolean isOnSkyBlock() {
        return onSkyBlock;
    }

    /** Hypixel island id, e.g. "hub", "dungeon", "mining_3". */
    public static String mode() {
        return mode;
    }

    /** Human readable map name, e.g. "Dwarven Mines". */
    public static String map() {
        return map;
    }

    public static void reset() {
        known = false;
        inParty = false;
        members = Map.of();
        onSkyBlock = false;
        mode = "";
        map = "";
        refreshAt = 0;
    }

    public static void requestRefresh(long delayMs) {
        long at = System.currentTimeMillis() + delayMs;
        if (refreshAt == 0 || at < refreshAt) refreshAt = at;
    }

    public static void onChat(Component message) {
        String text = message.getString().toLowerCase(Locale.ROOT);
        if (text.contains("party") || text.contains("disbanded")) requestRefresh(CHAT_REFRESH_DELAY_MS);
    }

    public static void tick(boolean needsParty) {
        if (transport == null || Minecraft.getInstance().getConnection() == null) return;
        long now = System.currentTimeMillis();
        if (needsParty && now - lastRequest >= PERIODIC_REFRESH_MS) requestRefresh(0);
        if (refreshAt != 0 && now >= refreshAt && now - lastRequest >= MIN_REQUEST_INTERVAL_MS) {
            refreshAt = 0;
            lastRequest = now;
            transport.requestParty();
        }
    }

    static void onParty(boolean nowInParty, Map<UUID, Role> partyMembers) {
        Map<UUID, Role> ordered = new LinkedHashMap<>();
        partyMembers.forEach((uuid, role) -> {
            if (role == Role.LEADER) ordered.put(uuid, role);
        });
        partyMembers.forEach(ordered::putIfAbsent);
        Map<UUID, Role> next = nowInParty ? Map.copyOf(ordered) : Map.of();
        boolean changed = !known || inParty != nowInParty || !next.equals(members);
        known = true;
        inParty = nowInParty;
        members = nowInParty ? Collections.unmodifiableMap(ordered) : Map.of();
        if (changed) LISTENERS.forEach(Runnable::run);
    }

    static void onLocation(String serverType, String newMode, String newMap) {
        onSkyBlock = "SKYBLOCK".equals(serverType);
        mode = newMode == null ? "" : newMode;
        map = newMap == null ? "" : newMap;
        requestRefresh(0);
    }
}
