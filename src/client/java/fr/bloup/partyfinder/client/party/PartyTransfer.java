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
 * Automatic party transfers (/pf warp) for activities where whoever found the spot must warp the party (mineshafts),
 * and the guard that keeps Hypixel party leader powers with the finder leader.
 */
public final class PartyTransfer {
    private static final long WARP_TIMEOUT_MS = 15_000;
    private static final int WARP_DELAY_TICKS = 10;
    // Right after Hypixel's transfer message: just enough spacing for Hypixel to take the next command.
    private static final int CHAT_WARP_DELAY_TICKS = 4;
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

    private static long warpUntil;
    private static long rejoinCheckUntil;

    private PartyTransfer() {
    }

    /** /pf warp: warps right away when leader, otherwise gets the lead transferred first. */
    public static Optional<String> warp() {
        Optional<Listing> listing = PartySync.currentListing();
        if (listing.isEmpty()) return Optional.of("partyfinder.error.not_in_finder_party");
        if (!HypixelBridge.inParty()) return Optional.of("partyfinder.error.not_in_hypixel_party");
        if (HypixelBridge.isLeaderOrSolo()) {
            PartyActions.command("party warp");
            return Optional.empty();
        }
        if (!PartySync.allowsTransfer(listing.get())) return Optional.of("partyfinder.error.transfer_not_allowed");
        warpUntil = System.currentTimeMillis() + WARP_TIMEOUT_MS;
        BackendConnection.warp();
        HypixelBridge.requestRefresh(2_000);
        PartyActions.chat(Component.translatable("partyfinder.chat.warp_requested").withStyle(ChatFormatting.YELLOW));
        return Optional.empty();
    }

    /**
     * Waiting for the lead to warp: Hypixel's transfer message naming us is the fastest signal, well before the Mod API
     * party info confirms it (tick() keeps that as the fallback).
     */
    public static void onChat(Component message) {
        if (warpUntil == 0 || System.currentTimeMillis() > warpUntil) return;
        Matcher m = TRANSFERRED.matcher(FORMATTING.matcher(message.getString()).replaceAll(""));
        String self = Minecraft.getInstance().getUser().getName();
        while (m.find()) {
            if (!m.group(1).equalsIgnoreCase(self)) continue;
            warpUntil = 0;
            PartyActions.later(CHAT_WARP_DELAY_TICKS, () -> PartyActions.command("party warp"));
            HypixelBridge.requestRefresh(0);
            return;
        }
    }

    public static void tick() {
        if (rejoinCheckUntil != 0) {
            if (System.currentTimeMillis() > rejoinCheckUntil) rejoinCheckUntil = 0;
            else if (HypixelBridge.isKnown() && returnLead()) rejoinCheckUntil = 0;
        }
        if (warpUntil == 0) return;
        if (HypixelBridge.inParty() && HypixelBridge.isLeaderOrSolo()) {
            warpUntil = 0;
            PartyActions.later(WARP_DELAY_TICKS, () -> PartyActions.command("party warp"));
        } else if (System.currentTimeMillis() > warpUntil) {
            warpUntil = 0;
            PartyActions.chat(Component.translatable("partyfinder.chat.warp_timeout").withStyle(ChatFormatting.RED));
        }
    }

    /** Backend relays a member's /pf warp to us, the current Hypixel leader. */
    public static void onTransferRequest(String listingId, Listing.Player to) {
        Optional<Listing> listing = PartySync.currentListing().filter(l -> l.id().equals(listingId));
        if (listing.isEmpty() || !PartySync.allowsTransfer(listing.get())) return;
        if (!HypixelBridge.inParty() || !HypixelBridge.isLeaderOrSolo() || !HypixelBridge.members().contains(to.uuid())) return;
        if (PartyActions.partyCommand("transfer", to.name())) {
            PartyActions.chat(Component.translatable("partyfinder.chat.transferring", to.name()).withStyle(ChatFormatting.YELLOW));
            HypixelBridge.requestRefresh(1_500);
        }
    }

    /** Backend relays an invite (request accepted or /p invite by the PF leader) to us, the current Hypixel leader. */
    public static void onInviteMember(String listingId, String name) {
        if (PartySync.currentListing().filter(l -> l.id().equals(listingId)).isEmpty() || !HypixelBridge.isLeaderOrSolo()) return;
        if (PartyActions.partyCommand("invite", name)) {
            PartyActions.chat(Component.translatable("partyfinder.chat.inviting_for", name).withStyle(ChatFormatting.YELLOW));
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

        if (sub.equals("warp") && !HypixelBridge.isLeaderOrSolo() && PartySync.allowsTransfer(listing.get())) {
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
        warpUntil = 0;
        returnLead();
    }

    /** After a crash or timeout the lead could not be returned: do it as soon as we are back. */
    public static void onJoin() {
        rejoinCheckUntil = System.currentTimeMillis() + REJOIN_CHECK_MS;
    }

    public static void reset() {
        warpUntil = 0;
        rejoinCheckUntil = 0;
    }
}
