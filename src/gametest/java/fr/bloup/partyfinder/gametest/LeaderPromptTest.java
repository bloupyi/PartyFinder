package fr.bloup.partyfinder.gametest;

import fr.bloup.partyfinder.client.PartyActions;
import fr.bloup.partyfinder.client.config.PartyFinderConfig;
import fr.bloup.partyfinder.client.party.LeaderPrompts;
import fr.bloup.partyfinder.client.party.Listing;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.UUID;

/** Warp / lead requests to the Party Leader: toast + framed chat prompt, Y answers it, "auto accept" turns the setting on. */
public class LeaderPromptTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            Listing.Player bobby = new Listing.Player(UUID.randomUUID(), "Bobby");
            Listing.Player carol = new Listing.Player(UUID.randomUUID(), "Carol");
            context.runOnClient(client -> {
                PartyFinderConfig.get().autoAcceptWarps = false;
                PartyFinderConfig.get().autoAcceptTransfers = false;
                LeaderPrompts.offer(LeaderPrompts.Type.WARP, "listing", bobby);
                LeaderPrompts.offer(LeaderPrompts.Type.TRANSFER, "listing", carol);
            });
            context.waitTicks(10);
            context.takeScreenshot("prompt_warp_transfer");

            // Y answers the most recent one (Carol's lead request), then Bobby's warp is next.
            context.runOnClient(client -> {
                PartyActions.respondToLatest(true);
                LeaderPrompts.Prompt next = LeaderPrompts.latest().orElseThrow(() -> new AssertionError("warp prompt gone too early"));
                if (next.type() != LeaderPrompts.Type.WARP) throw new AssertionError("Y did not answer the latest request");
                LeaderPrompts.enableAuto(LeaderPrompts.Type.WARP);
                if (!PartyFinderConfig.get().autoAcceptWarps) throw new AssertionError("auto accept not saved");
                if (LeaderPrompts.latest().isPresent()) throw new AssertionError("auto accept left the warp pending");
                PartyFinderConfig.get().autoAcceptWarps = false;
            });
            context.waitTicks(5);
            context.takeScreenshot("prompt_answered");
        }
    }
}
