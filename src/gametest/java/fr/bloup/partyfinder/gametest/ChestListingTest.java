package fr.bloup.partyfinder.gametest;

import fr.bloup.partyfinder.client.config.PartyFinderConfig;
import fr.bloup.partyfinder.client.gui.ChestListingScreen;
import fr.bloup.partyfinder.client.gui.PartyFinderScreen;
import fr.bloup.partyfinder.client.net.BackendConnection;
import fr.bloup.partyfinder.client.party.Listing;
import fr.bloup.partyfinder.client.party.PartyFinderState;
import fr.bloup.partyfinder.client.party.ReportSummary;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The chest listing style: opened through the setting, follows listings live, shows the lore on hover. */
public class ChestListingTest implements FabricClientGameTest {
    private static final Listing.Player SELF = new Listing.Player(UUID.randomUUID(), "Tester");

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> PartyFinderConfig.get().listingStyle = PartyFinderConfig.ListingStyle.CHEST);
        context.setScreen(() -> PartyFinderScreen.open(null, ""));
        context.runOnClient(client -> BackendConnection.disconnect());
        context.waitTicks(2);
        context.runOnClient(client -> {
            if (!(client.gui.screen() instanceof ChestListingScreen)) throw new AssertionError("Chest style setting not used");
            PartyFinderState.setStatus(PartyFinderState.Status.ONLINE);
            PartyFinderState.setSession(SELF, false, ReportSummary.NONE);
            PartyFinderState.replaceAll(List.of());
        });
        context.waitTicks(3);
        context.takeScreenshot("chest_empty");

        context.runOnClient(client -> {
            PartyFinderState.upsert(listing("a", new Listing.Player(UUID.randomUUID(), "Alice"), 2, 5, "F7 carry, need healer"));
            PartyFinderState.upsert(listing("b", new Listing.Player(UUID.randomUUID(), "Bobby"), 5, 5, "full one"));
            PartyFinderState.upsert(listing("c", SELF, 1, 4, "my party"));
        });
        context.waitTicks(5);
        context.takeScreenshot("chest_listings");

        // Hover the first inner slot (slot 10): the listing lore shows as the tooltip.
        double[] cursor = context.computeOnClient(client -> {
            double scale = client.getWindow().getGuiScale();
            int left = (client.getWindow().getGuiScaledWidth() - 176) / 2;
            int top = Math.max(4, (client.getWindow().getGuiScaledHeight() - (6 * 18 + 24)) / 2);
            return new double[]{(left + 8 + 18 + 8) * scale, (top + 18 + 18 + 8) * scale};
        });
        context.getInput().setCursorPos(cursor[0], cursor[1]);
        context.waitTicks(3);
        context.takeScreenshot("chest_hover");

        context.runOnClient(client -> PartyFinderConfig.get().listingStyle = PartyFinderConfig.ListingStyle.LIST);
        context.setScreen(() -> null);
    }

    private static Listing listing(String id, Listing.Player owner, int size, int max, String note) {
        long now = System.currentTimeMillis();
        List<Listing.Member> members = List.of(new Listing.Member(owner.uuid(), owner.name(), null, false));
        return new Listing(id, owner, "other", Map.of(), note, "", size, max, Listing.NO_REPORT_LIMIT, Map.of(), List.of(),
                ReportSummary.NONE, members, null, now, now);
    }
}
