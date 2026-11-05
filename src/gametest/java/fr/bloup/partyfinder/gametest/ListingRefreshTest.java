package fr.bloup.partyfinder.gametest;

import fr.bloup.partyfinder.client.gui.PartyFinderScreen;
import fr.bloup.partyfinder.client.net.BackendConnection;
import fr.bloup.partyfinder.client.party.Listing;
import fr.bloup.partyfinder.client.party.PartyFinderState;
import fr.bloup.partyfinder.client.party.ReportSummary;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractSelectionList;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The open listing screen must follow listings arriving, changing and disappearing without being reopened. */
public class ListingRefreshTest implements FabricClientGameTest {
    private static final Listing.Player SELF = new Listing.Player(UUID.randomUUID(), "Tester");

    @Override
    public void runTest(ClientGameTestContext context) {
        context.setScreen(() -> new PartyFinderScreen(null, ""));
        // Opening the screen connects; there is no backend in tests, so stop it before faking an online session.
        context.runOnClient(client -> BackendConnection.disconnect());
        context.waitTicks(2);
        context.runOnClient(client -> {
            PartyFinderState.setStatus(PartyFinderState.Status.ONLINE);
            PartyFinderState.setSession(SELF, false, ReportSummary.NONE);
            PartyFinderState.replaceAll(List.of());
        });
        context.waitTicks(5);
        expectRows(context, 0, "empty");

        Listing.Player owner = new Listing.Player(UUID.randomUUID(), "Alice");
        context.runOnClient(client -> PartyFinderState.upsert(listing("a", owner, 1, "first")));
        context.waitTicks(5);
        expectRows(context, 1, "after upsert");
        context.takeScreenshot("listing_refresh_added");

        context.runOnClient(client -> PartyFinderState.upsert(listing("a", owner, 3, "changed")));
        context.waitTicks(5);
        context.takeScreenshot("listing_refresh_changed");

        context.runOnClient(client -> PartyFinderState.remove("a"));
        context.waitTicks(5);
        expectRows(context, 0, "after remove");
        context.setScreen(() -> null);
    }

    private static void expectRows(ClientGameTestContext context, int expected, String step) {
        int rows = context.computeOnClient(client -> {
            for (var child : client.gui.screen().children()) {
                if (child instanceof AbstractSelectionList<?> list) return list.children().size();
            }
            return -1;
        });
        if (rows != expected) throw new AssertionError("Listing screen " + step + ": " + rows + " rows, expected " + expected);
    }

    private static Listing listing(String id, Listing.Player owner, int size, String note) {
        long now = System.currentTimeMillis();
        List<Listing.Member> members = List.of(new Listing.Member(owner.uuid(), owner.name(), null, false, null, null));
        return new Listing(id, owner, "other", Map.of(), note, "", size, 5, Listing.NO_REPORT_LIMIT, Map.of(), List.of(),
                ReportSummary.NONE, members, null, now, now, "misc", List.of());
    }
}
