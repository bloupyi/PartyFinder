package fr.bloup.partyfinder.gametest;

import fr.bloup.partyfinder.client.activity.Activities;
import fr.bloup.partyfinder.client.config.PartyFinderConfig;
import fr.bloup.partyfinder.client.gui.CreateListingScreen;
import fr.bloup.partyfinder.client.gui.PartyFinderScreen;
import fr.bloup.partyfinder.client.gui.TagsScreen;
import fr.bloup.partyfinder.client.net.BackendConnection;
import fr.bloup.partyfinder.client.party.Listing;
import fr.bloup.partyfinder.client.party.PartyFinderState;
import fr.bloup.partyfinder.client.party.ReportSummary;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Listing tags, one screenshot per step: the tags in the create form and their picker (max 5), the fishing listings
 * with their tags, then filtered and sorted by tags in both listing styles.
 */
public class TagsTest implements FabricClientGameTest {
    private static final Listing.Player SELF = new Listing.Player(UUID.randomUUID(), "Tester");

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            BackendConnection.disconnect();
            PartyFinderConfig.get().listingStyle = PartyFinderConfig.ListingStyle.LIST;
            PartyFinderState.setStatus(PartyFinderState.Status.ONLINE);
            PartyFinderState.setSession(SELF, false, ReportSummary.NONE);
            PartyFinderState.replaceAll(List.of());
            check(Activities.tagsFor("fishing").contains("stridersurfer"), "fishing tags loaded");
            check(Activities.categories().stream().allMatch(c -> Activities.tagsFor(c).size() <= Activities.MAX_CATEGORY_TAGS),
                    "at most 20 tags per category");
            PartyFinderState.upsert(listing("own", SELF, "Stridersurfer grind on Galatea", List.of("stridersurfer", "chill")));
        });

        // 1. The create form of our own listing: its tags on the Tags button.
        context.setScreen(() -> new CreateListingScreen(null));
        context.waitTicks(3);
        context.takeScreenshot("tags_1_create");

        // 2. The picker: 5 tags at most, the others greyed out once 5 are picked.
        List<String> picked = new ArrayList<>(List.of("stridersurfer", "thunder", "lord_jawbus", "plhlegblast", "ragnarok"));
        context.setScreen(() -> new TagsScreen(null, Component.translatable("partyfinder.tags.title"), Activities.tagsFor("fishing"),
                picked, Listing.MAX_TAGS, "partyfinder.tags.hint", () -> {
        }));
        context.waitTicks(3);
        context.takeScreenshot("tags_2_picker_full");

        // 3. Fishing listings with their tags, no filter.
        context.runOnClient(client -> PartyFinderScreen.tagFilter("fishing", Set.of()).clear());
        openFishing(context);
        context.takeScreenshot("tags_3_list_all");

        // 4. Filtered by Stridersurfer and Thunder: the listing with both first, Carla's left out.
        context.runOnClient(client -> {
            seed();
            List<String> filter = PartyFinderScreen.tagFilter("fishing", Set.of());
            filter.addAll(List.of("stridersurfer", "thunder"));
            List<String> order = PartyFinderScreen.filter("fishing", Set.of(), "", filter).stream().map(Listing::id).toList();
            check(order.equals(List.of("b", "a", "d")) || order.equals(List.of("b", "d", "a")), "filtered order " + order);
        });
        openFishing(context);
        context.takeScreenshot("tags_4_list_filtered");

        // 5. The filter picker (no limit).
        context.setScreen(() -> new TagsScreen(null, Component.translatable("partyfinder.tags.filter.title"), Activities.tagsFor("fishing"),
                PartyFinderScreen.tagFilter("fishing", Set.of()), 0, "partyfinder.tags.filter.hint", () -> {
        }));
        context.waitTicks(3);
        context.takeScreenshot("tags_5_filter_picker");

        // 6. Chest style with the same filter, the mouse on the tag filter button (slot 46).
        context.runOnClient(client -> PartyFinderConfig.get().listingStyle = PartyFinderConfig.ListingStyle.CHEST);
        openFishing(context);
        double[] cursor = context.computeOnClient(client -> {
            double scale = client.getWindow().getGuiScale();
            int left = (client.getWindow().getGuiScaledWidth() - 176) / 2;
            int top = Math.max(4, (client.getWindow().getGuiScaledHeight() - (6 * 18 + 24)) / 2);
            return new double[]{(left + 8 + 18 + 8) * scale, (top + 18 + 5 * 18 + 8) * scale};
        });
        context.getInput().setCursorPos(cursor[0], cursor[1]);
        context.waitTicks(3);
        context.takeScreenshot("tags_6_chest_filter");

        context.runOnClient(client -> {
            PartyFinderConfig.get().listingStyle = PartyFinderConfig.ListingStyle.LIST;
            PartyFinderScreen.tagFilter("fishing", Set.of()).clear();
            PartyFinderState.replaceAll(List.of());
        });
        context.setScreen(() -> null);
    }

    /** Opening a listing screen connects to the (absent) server, which clears listings: they are added after. */
    private static void openFishing(ClientGameTestContext context) {
        context.setScreen(() -> PartyFinderScreen.open(null, "fishing"));
        context.runOnClient(client -> BackendConnection.disconnect());
        context.waitTicks(2);
        context.runOnClient(client -> seed());
        context.waitTicks(3);
    }

    private static void seed() {
        PartyFinderState.setStatus(PartyFinderState.Status.ONLINE);
        PartyFinderState.setSession(SELF, false, ReportSummary.NONE);
        PartyFinderState.replaceAll(List.of());
        PartyFinderState.upsert(listing("a", player("Alice"), "Jawbus and Thunder, lava rods", List.of("lord_jawbus", "thunder")));
        PartyFinderState.upsert(listing("b", player("Bobby"), "Stridersurfer + Thunder", List.of("stridersurfer", "thunder", "xp")));
        PartyFinderState.upsert(listing("c", player("Carla"), "chill bayou fishing", List.of("titanoboa", "chill")));
        PartyFinderState.upsert(listing("d", player("Dylan"), "striders only", List.of("stridersurfer")));
    }

    private static Listing.Player player(String name) {
        return new Listing.Player(UUID.randomUUID(), name);
    }

    private static Listing listing(String id, Listing.Player owner, String note, List<String> tags) {
        long now = System.currentTimeMillis();
        List<Listing.Member> members = List.of(new Listing.Member(owner.uuid(), owner.name(), null, false, null, null));
        return new Listing(id, owner, "sea_creatures", Map.of("island", "crimson"), note, "", 1, 5, Listing.NO_REPORT_LIMIT, Map.of(),
                List.of(), ReportSummary.NONE, members, null, now, now, "fishing", tags);
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError("Tags: " + what);
    }
}
