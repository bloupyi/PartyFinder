package fr.bloup.partyfinder.client.party;

import fr.bloup.partyfinder.client.config.PartyFinderConfig;
import fr.bloup.partyfinder.client.hypixel.HypixelBridge;
import fr.bloup.partyfinder.client.net.BackendConnection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local player's SkyBlock status (from the action bar) shared with party members through the backend, and the
 * statuses received from the other members.
 */
public final class PartyStatus {
    private static final Pattern HEALTH = Pattern.compile("([\\d,]+)/([\\d,]+)❤");
    private static final Pattern MANA = Pattern.compile("([\\d,]+)/([\\d,]+)✎");
    private static final Pattern DEFENSE = Pattern.compile("([\\d,]+)❈");
    private static final long SEND_INTERVAL_MS = 2_000;
    private static final long HEARTBEAT_MS = 15_000;
    private static final long ACTION_BAR_TTL_MS = 5_000;
    private static final long REMOTE_TTL_MS = 60_000;

    private record Remote(MemberStatus status, long at) {
    }

    private static final Map<UUID, Remote> REMOTE = new HashMap<>();

    private static int health;
    private static int maxHealth;
    private static int mana;
    private static int maxMana;
    private static int defense;
    private static long actionBarAt;
    private static MemberStatus lastSent;
    private static Set<UUID> lastParty = Set.of();
    private static long lastSentAt;

    private PartyStatus() {
    }

    public static void onActionBar(Component message) {
        String text = message.getString();
        boolean found = false;
        Matcher m = HEALTH.matcher(text);
        if (m.find()) {
            health = parse(m.group(1));
            maxHealth = parse(m.group(2));
            found = true;
        }
        m = MANA.matcher(text);
        if (m.find()) {
            mana = parse(m.group(1));
            maxMana = parse(m.group(2));
        }
        m = DEFENSE.matcher(text);
        if (m.find()) defense = parse(m.group(1));
        if (found) actionBarAt = System.currentTimeMillis();
    }

    private static int parse(String value) {
        try {
            return Integer.parseInt(value.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static MemberStatus self() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return null;
        boolean fresh = System.currentTimeMillis() - actionBarAt < ACTION_BAR_TTL_MS;
        float ratio = fresh && maxHealth > 0 ? (float) health / maxHealth : player.getHealth() / Math.max(1f, player.getMaxHealth());
        return new MemberStatus(
                player.getGameProfile().name(),
                Math.clamp(ratio, 0f, 1f),
                fresh ? health : 0,
                fresh ? maxHealth : 0,
                fresh ? mana : 0,
                fresh ? maxMana : 0,
                fresh ? defense : 0,
                HypixelBridge.mode(),
                HypixelBridge.map(),
                MemberStatus.equipmentOf(player));
    }

    public static Optional<MemberStatus> remote(UUID uuid) {
        Remote remote = REMOTE.get(uuid);
        if (remote == null || System.currentTimeMillis() - remote.at() > REMOTE_TTL_MS) return Optional.empty();
        return Optional.of(remote.status());
    }

    public static void onRemote(UUID uuid, MemberStatus status) {
        REMOTE.put(uuid, new Remote(status, System.currentTimeMillis()));
    }

    /** Pushes our status to the backend while in a party, so members far away still see it. */
    public static void tick() {
        if (!PartyFinderConfig.get().hud.shareStatus || !BackendConnection.isConnected()) return;
        if (!HypixelBridge.isKnown() || !HypixelBridge.inParty()) {
            if (!lastParty.isEmpty()) {
                lastParty = Set.of();
                BackendConnection.memberStatus(Set.of(), null);
            }
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastSentAt < SEND_INTERVAL_MS) return;
        MemberStatus status = self();
        if (status == null) return;
        Set<UUID> party = HypixelBridge.members();
        if (status.equals(lastSent) && party.equals(lastParty) && now - lastSentAt < HEARTBEAT_MS) return;
        lastSent = status;
        lastParty = Set.copyOf(party);
        lastSentAt = now;
        BackendConnection.memberStatus(party, status);
    }

    public static void reset() {
        REMOTE.clear();
        lastSent = null;
        lastParty = Set.of();
        lastSentAt = 0;
        actionBarAt = 0;
    }
}
