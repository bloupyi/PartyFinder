package fr.bloup.partyfinder.client.party;

import fr.bloup.partyfinder.client.activity.Theme;
import fr.bloup.partyfinder.client.PartyActions;
import fr.bloup.partyfinder.client.activity.Activities;
import fr.bloup.partyfinder.client.activity.Activity;
import fr.bloup.partyfinder.client.hypixel.HypixelBridge;
import fr.bloup.partyfinder.client.net.BackendConnection;
import fr.bloup.partyfinder.client.net.BackendEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Keeps the finder party and the real Hypixel party linked: the host pushes its Hypixel party to the backend
 * (size, members, stats), finder members who leave the mod get kicked, and members who left the Hypixel party
 * drop out of the finder.
 */
public final class PartySync {
    private static final long SYNC_INTERVAL_MS = 5_000;
    private static final long JOIN_GRACE_MS = 20_000;

    private static final Set<String> notifiedFull = new HashSet<>();
    private static String joinedListingId;
    private static long joinedAt;
    private static long lastSync;
    private static Listing lastSeen;
    private static String lastMode = "";
    private static String delistedFor;

    private PartySync() {
    }

    public static Optional<Listing> joinedListing() {
        return joinedListingId == null ? Optional.empty() : PartyFinderState.get(joinedListingId);
    }

    /** The listing the local player is part of, as host or as finder member. */
    public static Optional<Listing> currentListing() {
        return PartyFinderState.ownListing().or(PartySync::joinedListing);
    }

    /** Hypixel leader (or solo) who is not inside someone else's finder party: may publish a listing. */
    public static boolean canHost() {
        return HypixelBridge.isLeaderOrSolo() && joinedListing().isEmpty();
    }

    /**
     * The Hypixel lead may sit with a member who joined through the finder (/pf warp transfers, any activity): the
     * finder leader keeps control through the backend. Anyone else holding it means the party left the finder's hands.
     */
    private static boolean leadWithFinder(Listing listing) {
        Optional<UUID> leader = HypixelBridge.leader();
        return leader.isPresent() && listing.members().stream().anyMatch(m -> m.finder() && m.uuid().equals(leader.get()));
    }

    public static void tick(Minecraft mc) {
        trackHistory(mc);
        boolean online = BackendConnection.isConnected();
        boolean needsParty = online && currentListing().isPresent();
        HypixelBridge.tick(needsParty);
        if (!online) return;
        long now = System.currentTimeMillis();

        PartyFinderState.ownListing().ifPresent(listing -> {
            if (HypixelBridge.isKnown() && !HypixelBridge.isLeaderOrSolo() && !leadWithFinder(listing)) {
                // The lead went to someone outside the finder: the listing can no longer be run, so it goes away.
                if (listing.id().equals(delistedFor)) return;
                delistedFor = listing.id();
                BackendConnection.delist();
                PartyActions.chat(Component.translatable("partyfinder.chat.delisted_not_leader").withColor(Theme.rgb("chat_notice")));
                return;
            }
            if (!HypixelBridge.isKnown() || now - lastSync < SYNC_INTERVAL_MS) return;
            Set<UUID> party = HypixelBridge.members();
            Set<UUID> listed = listing.members().stream().map(Listing.Member::uuid).collect(Collectors.toSet());
            UUID leader = HypixelBridge.leader().filter(party::contains).orElse(listing.owner().uuid());
            if (!party.equals(listed) || !leader.equals(listing.hypixelLeader())) {
                lastSync = now;
                BackendConnection.partyUpdate(party, leader);
            }
        });

        if (joinedListingId != null) {
            Optional<Listing> joined = joinedListing();
            boolean leftParty = HypixelBridge.isKnown() && now - joinedAt > JOIN_GRACE_MS && joined
                    .map(l -> !HypixelBridge.inParty() || !HypixelBridge.members().contains(l.owner().uuid()))
                    .orElse(true);
            if (leftParty) joinedListingId = null;
        }

        for (Listing listing : List.of(PartyFinderState.ownListing(), joinedListing()).stream().flatMap(Optional::stream).toList()) {
            if (!listing.isFull()) {
                notifiedFull.remove(listing.id());
            } else if (notifiedFull.add(listing.id())) {
                BackendEvents.onPartyFull(listing, canLaunch(listing));
            }
        }
    }

    /** A finder party that disappears (disbanded, delisted, left) is saved to the local history. */
    private static void trackHistory(Minecraft mc) {
        Listing current = currentListing().orElse(null);
        String mode = HypixelBridge.mode();
        // Leaving a dungeon or Kuudra instance ends a run even though the Hypixel party stays together.
        boolean leftInstance = !mode.equals(lastMode) && (lastMode.equals("dungeon") || lastMode.equals("kuudra"));
        lastMode = mode;
        if (leftInstance && current != null && mc.player != null) PartyHistory.record(current, mc.player.getUUID());
        if (lastSeen != null && (current == null || !current.id().equals(lastSeen.id())) && mc.player != null) {
            PartyHistory.record(lastSeen, mc.player.getUUID());
        }
        lastSeen = current;
    }

    public static void onJoinAccepted(String listingId) {
        joinedListingId = listingId;
        joinedAt = System.currentTimeMillis();
        HypixelBridge.requestRefresh(3_000);
    }

    public static void onPartyRemoved(String listingId) {
        if (!listingId.equals(joinedListingId)) return;
        String host = joinedListing().map(l -> l.owner().name()).orElse("?");
        joinedListingId = null;
        PartyActions.chat(Component.translatable("partyfinder.chat.party_removed", host).withColor(Theme.rgb("chat_notice")));
    }

    /** Backend asks the Hypixel leader to kick a finder member who left through the mod (or disconnected for too long). */
    public static void onKickMember(String listingId, UUID uuid, String name) {
        if (currentListing().filter(l -> l.id().equals(listingId)).isEmpty()) return;
        if (!HypixelBridge.inParty() || !HypixelBridge.isLeaderOrSolo()) return;
        if (!HypixelBridge.members().contains(uuid)) return;
        if (PartyActions.partyCommand("kick", name)) {
            PartyActions.chat(Component.translatable("partyfinder.chat.member_left", name).withColor(Theme.rgb("chat_notice")));
            HypixelBridge.requestRefresh(1_500);
        }
    }

    public static boolean leave() {
        if (joinedListingId == null) return false;
        joinedListingId = null;
        BackendConnection.leave();
        PartyActions.command("party leave");
        HypixelBridge.requestRefresh(1_500);
        return true;
    }

    public static boolean canLaunch(Listing listing) {
        Activity activity = Activities.get(listing.activity());
        boolean host = PartyFinderState.isSelf(listing.owner());
        if (activity.launchCommands(listing.options()).isEmpty()) return false;
        return switch (activity.launch()) {
            case NONE -> false;
            case HOST -> host;
            case ALL -> true;
        };
    }

    /** Runs the activity's launch commands (floor queue, island warp...). Always triggered by the player. */
    public static boolean launch() {
        Optional<Listing> listing = currentListing();
        if (listing.isEmpty() || !canLaunch(listing.get())) return false;
        Activity activity = Activities.get(listing.get().activity());
        PartyActions.runLaunchCommands(activity.launchCommands(listing.get().options()));
        return true;
    }

    public static void reset() {
        Minecraft mc = Minecraft.getInstance();
        if (lastSeen != null && mc.player != null) PartyHistory.record(lastSeen, mc.player.getUUID());
        lastSeen = null;
        joinedListingId = null;
        notifiedFull.clear();
        lastSync = 0;
    }
}
