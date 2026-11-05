package fr.bloup.partyfinder.client.party;

import fr.bloup.partyfinder.client.activity.Notifications;
import fr.bloup.partyfinder.client.PartyActions;
import fr.bloup.partyfinder.client.config.PartyFinderConfig;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auto-accept for finder parties only: armed by an accepted join request of ours (BackendEvents.onJoinResponse), it
 * sends /party accept as soon as Hypixel's invite from that party shows in chat. The invite may also arrive before
 * the finder's answer, so recent invites are remembered for a short while.
 */
public final class InviteAccept {
    // "[MVP+] Name has invited you to join their party!" or "... to join [VIP] Leader's party!" (a member inviting).
    // Anchored per line: a player typing this in chat shows as "Name: ...", which does not match.
    private static final Pattern INVITE = Pattern.compile(
            "(?m)^(?:\\[[^\\]]+\\] )?([A-Za-z0-9_]{1,16}) has invited you to join (?:their|(?:\\[[^\\]]+\\] )?([A-Za-z0-9_]{1,16})'s) party!");
    private static final Pattern FORMATTING = Pattern.compile("§.", Pattern.DOTALL);
    private static final long WAIT_MS = 30_000;
    private static final long SEEN_MS = 30_000;
    private static final int MAX_SEEN = 8;

    private record Invite(String inviter, String leader, long at) {
        boolean from(Set<String> names) {
            return names.contains(inviter.toLowerCase(Locale.ROOT)) || leader != null && names.contains(leader.toLowerCase(Locale.ROOT));
        }
    }

    private record Pending(Set<String> names, String fallback, long deadline) {
    }

    private static final Deque<Invite> SEEN = new ArrayDeque<>();
    private static Pending pending;

    private InviteAccept() {
    }

    /** Our join request was accepted: accept the invite from whoever sends it for that party (inviter or owner). */
    public static void expect(String inviter, String owner) {
        // Usually the same player (the owner holds the Hypixel lead): Set.of would throw on the duplicate.
        Set<String> names = Set.copyOf(List.of(inviter.toLowerCase(Locale.ROOT), owner.toLowerCase(Locale.ROOT)));
        long now = System.currentTimeMillis();
        for (Invite invite : SEEN) {
            if (now - invite.at() <= SEEN_MS && invite.from(names)) {
                SEEN.remove(invite);
                accept(invite.inviter());
                return;
            }
        }
        pending = new Pending(names, inviter, now + WAIT_MS);
    }

    public static void onChat(Component message) {
        String text = FORMATTING.matcher(message.getString()).replaceAll("");
        Matcher m = INVITE.matcher(text);
        while (m.find()) {
            Invite invite = new Invite(m.group(1), m.group(2), System.currentTimeMillis());
            if (pending != null && invite.from(pending.names())) {
                pending = null;
                accept(invite.inviter());
                // Accepted for the player: only Hypixel's "You have joined ..." needs to show.
                if (PartyFinderConfig.get().cleanPartyChat) PartyChat.hide(message);
                return;
            }
            SEEN.addLast(invite);
            while (SEEN.size() > MAX_SEEN) SEEN.removeFirst();
        }
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        SEEN.removeIf(invite -> now - invite.at() > SEEN_MS);
        if (pending != null && now > pending.deadline()) {
            // No recognizable invite (Hypixel may word it differently): one try anyway, the invite lasts 60 s.
            String name = pending.fallback();
            pending = null;
            accept(name);
        }
    }

    private static void accept(String name) {
        if (PartyActions.partyCommand("accept", name)) Notifications.sound("invite_accepted");
    }

    public static void reset() {
        SEEN.clear();
        pending = null;
    }
}
