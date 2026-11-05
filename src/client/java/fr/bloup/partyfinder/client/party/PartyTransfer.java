package fr.bloup.partyfinder.client.party;

import fr.bloup.partyfinder.client.PartyActions;
import fr.bloup.partyfinder.client.hypixel.HypixelBridge;
import fr.bloup.partyfinder.client.net.BackendConnection;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Party commands between Party Owner (who listed the party in the finder) and Party Leader (who holds the Hypixel
 * lead): /pf warp asks the Party Leader to /p warp you, /pf pt or /pf ptme gets you the lead (any activity), and
 * the guard that keeps leader powers with the Party Owner.
 */
public final class PartyTransfer {
    private static final long PTME_TIMEOUT_MS = 15_000;
    // A Party Leader warps for each request, but not more often than this.
    private static final long SUMMON_COOLDOWN_MS = 5_000;
    // "The party was transferred to [MVP+] Name by [VIP] Leader" (or "... because Leader left"). Anchored per line: a
    // player typing this shows as "Name: ...", which does not match.
    private static final Pattern TRANSFERRED = Pattern.compile(
            "(?m)^The party was transferred to (?:\\[[^\\]]+\\] )?([A-Za-z0-9_]{1,16})\\b");
    private static final Pattern FORMATTING = Pattern.compile("§.", Pattern.DOTALL);
    private static final Set<String> ROOTS = Set.of("p", "party");
    private static final Set<String> SUBCOMMANDS = Set.of("accept", "allinvite", "chat", "challenge", "demote", "disband", "help",
            "hijack", "invite", "join", "kick", "kickoffline", "leave", "list", "mute", "poll", "private", "promote", "settings",
            "transfer", "warp");
    private static final Set<String> LEADER_ONLY = Set.of("transfer", "kick", "kickoffline", "disband", "promote", "demote");

    private static final long REJOIN_CHECK_MS = 30_000;

    private static long ptmeUntil;
    private static long rejoinCheckUntil;
    private static long lastSummonAt;

    private PartyTransfer() {
    }

    /** Finder party we are in: as owner, through the finder, or listed by the owner as a member of their party. */
    private static Optional<Listing> partyListing() {
        return PartySync.currentListing().or(() -> PartyFinderState.self().flatMap(self -> PartyFinderState.listings().stream()
                .filter(l -> l.hasMember(self.uuid())).findFirst()));
    }

    /**
     * /pf warp: /p warp when we are the Party Leader, otherwise asks the Party Leader's mod to run it (only the
     * Hypixel leader can warp). Anyone in the party can ask, not only the owner.
     */
    public static Optional<String> warp() {
        if (!HypixelBridge.inParty()) return Optional.of("partyfinder.error.not_in_hypixel_party");
        if (HypixelBridge.isLeaderOrSolo()) {
            PartyActions.command("party warp");
            return Optional.empty();
        }
        if (partyListing().isEmpty()) return Optional.of("partyfinder.error.not_in_finder_party");
        // Whoever holds the Hypixel lead right now, as the Mod API tells us: that is whose lobby we get warped into.
        BackendConnection.summon(HypixelBridge.leader().orElse(null));
        PartyActions.chat(Component.translatable("partyfinder.chat.warp_asked").withStyle(ChatFormatting.YELLOW));
        return Optional.empty();
    }

    /** /pf pt, /pf ptme: asks the Party Leader for the Hypixel lead (finder members and the Party Owner). */
    public static Optional<String> ptme() {
        if (PartySync.currentListing().isEmpty()) return Optional.of("partyfinder.error.not_in_finder_party");
        if (!HypixelBridge.inParty()) return Optional.of("partyfinder.error.not_in_hypixel_party");
        if (HypixelBridge.isLeaderOrSolo()) return Optional.of("partyfinder.error.already_leader");
        ptmeUntil = System.currentTimeMillis() + PTME_TIMEOUT_MS;
        BackendConnection.ptme();
        HypixelBridge.requestRefresh(2_000);
        PartyActions.chat(Component.translatable("partyfinder.chat.ptme_requested").withStyle(ChatFormatting.YELLOW));
        return Optional.empty();
    }

    /** Waiting for the lead: Hypixel's transfer message naming us confirms it before the Mod API party info does. */
    public static void onChat(Component message) {
        if (ptmeUntil == 0) return;
        Matcher m = TRANSFERRED.matcher(FORMATTING.matcher(message.getString()).replaceAll(""));
        String self = Minecraft.getInstance().getUser().getName();
        while (m.find()) {
            if (!m.group(1).equalsIgnoreCase(self)) continue;
            ptmeUntil = 0;
            HypixelBridge.requestRefresh(0);
            return;
        }
    }

    public static void tick() {
        if (rejoinCheckUntil != 0) {
            if (System.currentTimeMillis() > rejoinCheckUntil) rejoinCheckUntil = 0;
            else if (HypixelBridge.isKnown() && returnLead()) rejoinCheckUntil = 0;
        }
        if (ptmeUntil == 0) return;
        if (HypixelBridge.inParty() && HypixelBridge.isLeaderOrSolo()) {
            ptmeUntil = 0;
        } else if (System.currentTimeMillis() > ptmeUntil) {
            ptmeUntil = 0;
            PartyActions.chat(Component.translatable("partyfinder.chat.ptme_timeout").withStyle(ChatFormatting.RED));
        }
    }

    /** Backend relays a member's /pf ptme to us, the Party Leader. */
    public static void onTransferRequest(String listingId, Listing.Player to) {
        if (canTransfer(listingId, to)) LeaderPrompts.offer(LeaderPrompts.Type.TRANSFER, listingId, to);
    }

    private static boolean canTransfer(String listingId, Listing.Player to) {
        return PartySync.currentListing().filter(l -> l.id().equals(listingId)).isPresent()
                && HypixelBridge.inParty() && HypixelBridge.isLeaderOrSolo() && HypixelBridge.members().contains(to.uuid());
    }

    /** Accepted transfer request (prompt or auto-accept). */
    static void transferTo(String listingId, Listing.Player to) {
        if (!canTransfer(listingId, to)) return;
        if (PartyActions.partyCommand("transfer", to.name())) {
            PartyActions.chat(Component.translatable("partyfinder.chat.transferring", to.name()).withStyle(ChatFormatting.YELLOW));
            HypixelBridge.requestRefresh(1_500);
        }
    }

    /** Backend relays a party member's /pf warp to us, the Party Leader: warp them into our lobby. */
    public static void onSummonRequest(String listingId, Listing.Player from) {
        if (canWarp(listingId, from)) LeaderPrompts.offer(LeaderPrompts.Type.WARP, listingId, from);
    }

    private static boolean canWarp(String listingId, Listing.Player from) {
        return partyListing().filter(l -> l.id().equals(listingId)).isPresent()
                && HypixelBridge.inParty() && HypixelBridge.isLeaderOrSolo() && HypixelBridge.members().contains(from.uuid());
    }

    /** Accepted warp request (prompt or auto-accept). */
    static void warpFor(String listingId, Listing.Player from) {
        if (!canWarp(listingId, from)) return;
        long now = System.currentTimeMillis();
        if (now - lastSummonAt < SUMMON_COOLDOWN_MS) return;
        lastSummonAt = now;
        PartyActions.command("party warp");
        PartyActions.chat(Component.translatable("partyfinder.chat.warping_for", from.name()).withStyle(ChatFormatting.YELLOW));
    }

    /** Backend relays an invite (request accepted or /p invite by the PF leader) to us, the current Hypixel leader. */
    public static void onInviteMember(String listingId, String name) {
        if (PartySync.currentListing().filter(l -> l.id().equals(listingId)).isEmpty() || !HypixelBridge.isLeaderOrSolo()) return;
        if (PartyActions.partyCommand("invite", name)) {
            boolean owner = PartySync.currentListing().map(l -> PartyFinderState.isSelf(l.owner())).orElse(false);
            PartyActions.chat(Component.translatable(owner ? "partyfinder.chat.inviting" : "partyfinder.chat.inviting_for", name)
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    /**
     * Typed Hypixel party commands inside a finder party: only the finder leader keeps leader powers, even while
     * someone else holds the Hypixel lead after an automatic transfer.
     */
    public static boolean allowCommand(String command) {
        if (PartyActions.isSendingOwnCommand()) return true;
        Optional<Listing> listing = PartySync.currentListing();
        if (listing.isEmpty()) return true;
        String[] args = command.strip().split("\\s+");
        if (args.length < 2 || !ROOTS.contains(args[0].toLowerCase(Locale.ROOT))) return true;
        String sub = args[1].toLowerCase(Locale.ROOT);
        boolean invite = sub.equals("invite") || !SUBCOMMANDS.contains(sub);
        List<String> invited = Arrays.asList(args).subList(sub.equals("invite") ? 2 : 1, args.length);
        boolean owner = PartyFinderState.isSelf(listing.get().owner());

        if (sub.equals("warp") && !HypixelBridge.isLeaderOrSolo()) {
            warp().ifPresent(key -> PartyActions.chat(Component.translatable(key).withStyle(ChatFormatting.RED)));
            return false;
        }
        if (!owner && (invite || LEADER_ONLY.contains(sub))) {
            PartyActions.chat(Component.translatable("partyfinder.chat.command_blocked").withStyle(ChatFormatting.RED));
            return false;
        }
        if (owner && invite && HypixelBridge.inParty() && !HypixelBridge.isLeaderOrSolo() && !invited.isEmpty()) {
            for (String name : invited) {
                if (!Listing.isValidName(name)) continue;
                BackendConnection.invite(name);
                PartyActions.chat(Component.translatable("partyfinder.chat.invite_forwarded", name).withStyle(ChatFormatting.YELLOW));
            }
            return false;
        }
        return true;
    }

    /**
     * A finder member holding the Hypixel lead (after a transfer) hands it back to the finder leader. Returns true when
     * a transfer was sent.
     */
    public static boolean returnLead() {
        if (!HypixelBridge.inParty() || !HypixelBridge.isLeaderOrSolo()) return false;
        Optional<Listing> listing = PartySync.currentListing().or(PartyTransfer::listingAsFinderMember);
        if (listing.isEmpty() || PartyFinderState.isSelf(listing.get().owner())) return false;
        Listing.Player owner = listing.get().owner();
        if (!HypixelBridge.members().contains(owner.uuid())) return false;
        return PartyActions.partyCommand("transfer", owner.name());
    }

    /** Client state is lost on reconnect; the backend listing still flags us as a finder member. */
    private static Optional<Listing> listingAsFinderMember() {
        return PartyFinderState.self().flatMap(self -> PartyFinderState.listings().stream()
                .filter(l -> !l.owner().uuid().equals(self.uuid()))
                .filter(l -> l.members().stream().anyMatch(m -> m.finder() && m.uuid().equals(self.uuid())))
                .findFirst());
    }

    /** Leaving the server while holding the lead of someone else's finder party gives it back first. */
    public static void onDisconnecting() {
        ptmeUntil = 0;
        returnLead();
    }

    /** After a crash or timeout the lead could not be returned: do it as soon as we are back. */
    public static void onJoin() {
        rejoinCheckUntil = System.currentTimeMillis() + REJOIN_CHECK_MS;
    }

    public static void reset() {
        ptmeUntil = 0;
        lastSummonAt = 0;
        rejoinCheckUntil = 0;
    }
}
