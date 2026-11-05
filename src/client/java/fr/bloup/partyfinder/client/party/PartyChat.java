package fr.bloup.partyfinder.client.party;

import com.google.common.collect.MapMaker;
import com.google.gson.JsonObject;
import fr.bloup.partyfinder.client.PartyActions;
import fr.bloup.partyfinder.client.activity.Activities;
import fr.bloup.partyfinder.client.activity.Activity;
import fr.bloup.partyfinder.client.activity.Theme;
import fr.bloup.partyfinder.client.config.PartyFinderConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Less chat clutter in finder parties: Hypixel's framed party messages the finder causes are hidden and replaced by
 * one-line messages of ours (with the newcomer's stats on hover). Hidden messages are still received, logged and kept
 * in the chat history, so other mods reading chat see them as usual: they are only left out of the chat display
 * (ChatComponentMixin). The "cleanPartyChat" setting turns all of this off.
 */
public final class PartyChat {
    // "[MVP+] Name": the whole part (kept with Hypixel's colors), then the name alone.
    private static final String PLAYER = "((?:\\[[^\\]]+\\] )?([A-Za-z0-9_]{1,16}))";
    private static final Pattern JOINED = Pattern.compile("(?m)^" + PLAYER + " joined the party\\.$");
    private static final Pattern LEFT = Pattern.compile("(?m)^" + PLAYER + " has left the party\\.$");
    private static final Pattern REMOVED = Pattern.compile("(?m)^" + PLAYER + " has been removed from the party\\.$");
    private static final Pattern INVITED = Pattern.compile(
            "(?m)^" + PLAYER + " invited " + PLAYER + " to the party! They have 60 seconds to accept\\.$");
    private static final Pattern EXPIRED = Pattern.compile("(?m)^The party invite to " + PLAYER + " has expired$");
    private static final Pattern TRANSFERRED = Pattern.compile(
            "(?m)^The party was transferred to " + PLAYER + " by " + PLAYER + "$");
    private static final long EXPECT_MS = 15_000;
    private static final long STATS_WAIT_MS = 3_000;

    /** Messages left out of the chat display; weak identity keys, so they go away with the chat history. */
    private static final Set<Component> HIDDEN = Collections.newSetFromMap(new MapMaker().weakKeys().makeMap());
    /** Invites our client sends for accepted finder players, lowercase name -> until. */
    private static final Map<String, Long> EXPECTED_INVITES = new HashMap<>();
    /** Requests we accepted as owner, kept for the join message stats. */
    private static final Map<String, PartyFinderState.JoinRequest> ACCEPTED = new HashMap<>();
    private static final List<PendingJoin> JOINS = new ArrayList<>();

    /** styled: "[MVP++] Name" as Hypixel colored it, used when the server knows no rank for the player. */
    private record PendingJoin(String name, Component styled, long since) {
    }

    private PartyChat() {
    }

    public static boolean isHidden(Component message) {
        return HIDDEN.contains(message);
    }

    public static void hide(Component message) {
        HIDDEN.add(message);
    }

    /** Our client is about to /p invite an accepted finder player: Hypixel's "X invited Y" echo is not needed. */
    public static void expectInvite(String name) {
        EXPECTED_INVITES.put(name.toLowerCase(Locale.ROOT), System.currentTimeMillis() + EXPECT_MS);
    }

    /** We (the owner) accepted this request: its stats are used when the player joins. */
    public static void accepted(PartyFinderState.JoinRequest request) {
        ACCEPTED.put(request.player().name().toLowerCase(Locale.ROOT), request);
    }

    private static boolean enabled() {
        return PartyFinderConfig.get().cleanPartyChat;
    }

    /** Every incoming game chat message (not the action bar). */
    public static void onChat(Component message) {
        if (!enabled()) return;
        StyledText styled = StyledText.of(message);
        String text = styled.plain();
        if (!text.contains("party") && !text.contains("Party")) return;
        Optional<Listing> listing = PartySync.currentListing();
        if (listing.isEmpty()) return;

        Matcher m = INVITED.matcher(text);
        if (m.find()) {
            String invited = m.group(4);
            hide(message);
            Long until = EXPECTED_INVITES.remove(invited.toLowerCase(Locale.ROOT));
            // Our own invite of an accepted player: our "Inviting..." line already said it.
            if (until != null && until >= System.currentTimeMillis()) return;
            line(Component.translatable("partyfinder.party.invited", name(listing.get(), styled, m, 1), name(listing.get(), styled, m, 3)),
                    "chat_notice");
            return;
        }
        m = JOINED.matcher(text);
        if (m.find()) {
            hide(message);
            JOINS.add(new PendingJoin(m.group(2), styled.slice(m.start(1), m.end(1)), System.currentTimeMillis()));
            tick();
            return;
        }
        m = LEFT.matcher(text);
        if (m.find()) {
            hide(message);
            line(Component.translatable("partyfinder.party.left", name(listing.get(), styled, m, 1)), "chat_notice");
            return;
        }
        m = REMOVED.matcher(text);
        if (m.find()) {
            hide(message);
            line(Component.translatable("partyfinder.party.removed", name(listing.get(), styled, m, 1)), "chat_notice");
            return;
        }
        m = EXPIRED.matcher(text);
        if (m.find()) {
            hide(message);
            line(Component.translatable("partyfinder.party.expired", name(listing.get(), styled, m, 1)), "chat_muted");
            return;
        }
        m = TRANSFERRED.matcher(text);
        if (m.find()) {
            hide(message);
            line(Component.translatable("partyfinder.party.transferred", name(listing.get(), styled, m, 1)), "chat_notice");
        }
    }

    /** Join messages wait a little for the newcomer's stats (the listing update follows Hypixel's message). */
    public static void tick() {
        long now = System.currentTimeMillis();
        EXPECTED_INVITES.values().removeIf(until -> until < now);
        Iterator<PendingJoin> it = JOINS.iterator();
        while (it.hasNext()) {
            PendingJoin join = it.next();
            Optional<Listing> listing = PartySync.currentListing();
            PartyFinderState.JoinRequest request = ACCEPTED.get(join.name().toLowerCase(Locale.ROOT));
            Listing.Member member = listing.flatMap(l -> member(l, join.name())).orElse(null);
            JsonObject stats = request != null ? request.stats() : member != null ? member.stats() : null;
            if (stats == null && now - join.since() < STATS_WAIT_MS && listing.isPresent()) continue;
            it.remove();
            ACCEPTED.remove(join.name().toLowerCase(Locale.ROOT));
            Rank rank = request != null ? request.rank() : member != null ? member.rank() : null;
            String role = request != null ? request.role() : member != null ? member.role() : null;
            Component shown = rank != null || join.styled() == null ? Rank.name(rank, join.name()) : join.styled();
            announceJoin(listing.orElse(null), join.name(), shown, rank, stats, request, role);
        }
    }

    private static void announceJoin(Listing listing, String name, Component shown, Rank rank, JsonObject stats,
                                     PartyFinderState.JoinRequest request, String role) {
        MutableComponent line = Component.translatable("partyfinder.party.joined", shown)
                .withColor(Theme.rgb("chat_notice"));
        if (listing != null) {
            Activity activity = Activities.get(listing.activity());
            String category = listing.effectiveCategory();
            Component headline = StatsCard.headline(activity, category, stats, role);
            if (headline != null) line.append(" ").append(headline);
            Component card = StatsCard.card(name, rank, stats, request != null ? request.reports() : null,
                    request != null && request.unverified(), activity, category, role);
            line.withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(card)));
        }
        PartyActions.chat(line);
    }

    private static Optional<Listing.Member> member(Listing listing, String name) {
        return listing.members().stream().filter(m -> m.name() != null && m.name().equalsIgnoreCase(name)).findFirst();
    }

    /**
     * The "[MVP+] Name" part of Hypixel's message with its own colors; when it has none, the rank the server knows for
     * a member, else the plain name.
     */
    private static Component name(Listing listing, StyledText styled, Matcher m, int group) {
        Component original = styled.slice(m.start(group), m.end(group));
        if (original != null) return original;
        String name = m.group(group + 1);
        return Rank.name(member(listing, name).map(Listing.Member::rank).orElse(null), name);
    }

    private static void line(Component message, String colorKey) {
        PartyActions.chat(message.copy().withColor(Theme.rgb(colorKey)));
    }

    public static void reset() {
        EXPECTED_INVITES.clear();
        ACCEPTED.clear();
        JOINS.clear();
    }
}
