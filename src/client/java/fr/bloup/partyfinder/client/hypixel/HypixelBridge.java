package fr.bloup.partyfinder.client.hypixel;

import net.hypixel.data.type.GameType;
import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.packet.impl.clientbound.ClientboundPartyInfoPacket;
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket;
import net.hypixel.modapi.packet.impl.serverbound.ServerboundPartyInfoPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Real Hypixel party and location, from the Hypixel Mod API. Party info is pulled (not pushed), so it is
 * refreshed after party chat messages and periodically while the party finder needs it.
 */
public final class HypixelBridge {
    private static final long MIN_REQUEST_INTERVAL_MS = 3_000;
    private static final long CHAT_REFRESH_DELAY_MS = 750;
    private static final long PERIODIC_REFRESH_MS = 30_000;

    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private static boolean known;
    private static boolean inParty;
    private static UUID leader;
    private static Set<UUID> members = Set.of();
    private static boolean onSkyBlock;
    private static String mode = "";
    private static long lastRequest;
    private static long refreshAt;

    private HypixelBridge() {
    }

    public static void init() {
        HypixelModAPI api = HypixelModAPI.getInstance();
        api.subscribeToEventPacket(ClientboundLocationPacket.class);
        api.createHandler(ClientboundLocationPacket.class, packet -> Minecraft.getInstance().execute(() -> onLocation(packet)));
        api.createHandler(ClientboundPartyInfoPacket.class, packet -> Minecraft.getInstance().execute(() -> onParty(packet)));
    }

    public static void addListener(Runnable listener) {
        LISTENERS.add(listener);
    }

    /** True once the party state has been received at least once on this server. */
    public static boolean isKnown() {
        return known;
    }

    public static boolean inParty() {
        return inParty;
    }

    public static Optional<UUID> leader() {
        return Optional.ofNullable(leader);
    }

    /** Party members including the local player, or just the local player when not in a party. */
    public static Set<UUID> members() {
        if (inParty && !members.isEmpty()) return members;
        UUID self = Minecraft.getInstance().getUser().getProfileId();
        return Set.of(self);
    }

    public static boolean isLeaderOrSolo() {
        return !inParty || Minecraft.getInstance().getUser().getProfileId().equals(leader);
    }

    public static boolean isOnSkyBlock() {
        return onSkyBlock;
    }

    public static String mode() {
        return mode;
    }

    public static void reset() {
        known = false;
        inParty = false;
        leader = null;
        members = Set.of();
        onSkyBlock = false;
        mode = "";
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
        if (Minecraft.getInstance().getConnection() == null) return;
        long now = System.currentTimeMillis();
        if (needsParty && now - lastRequest >= PERIODIC_REFRESH_MS) requestRefresh(0);
        if (refreshAt != 0 && now >= refreshAt && now - lastRequest >= MIN_REQUEST_INTERVAL_MS) {
            refreshAt = 0;
            lastRequest = now;
            HypixelModAPI.getInstance().sendPacket(new ServerboundPartyInfoPacket());
        }
    }

    private static void onParty(ClientboundPartyInfoPacket packet) {
        boolean changed = !known || inParty != packet.isInParty()
                || !packet.getMembers().equals(members) || !Objects.equals(packet.getLeader().orElse(null), leader);
        known = true;
        inParty = packet.isInParty();
        leader = packet.getLeader().orElse(null);
        members = inParty ? new LinkedHashSet<>(packet.getMembers()) : Set.of();
        if (changed) LISTENERS.forEach(Runnable::run);
    }

    private static void onLocation(ClientboundLocationPacket packet) {
        onSkyBlock = packet.getServerType().map(type -> type == GameType.SKYBLOCK).orElse(false);
        mode = packet.getMode().orElse("");
        requestRefresh(0);
    }
}
