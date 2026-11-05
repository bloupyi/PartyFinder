package fr.bloup.partyfinder.gametest;

import fr.bloup.partyfinder.client.activity.Activities;
import fr.bloup.partyfinder.client.activity.ListingMenu;
import fr.bloup.partyfinder.client.activity.MenuLayout;
import fr.bloup.partyfinder.client.activity.Notifications;
import fr.bloup.partyfinder.client.activity.Theme;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

/** Default pack files (theme, notifications, listing menu, unknown activity) load on resource reload; toasts render. */
public class PackFilesTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            // Loaded by the resource reload listener, before any PartyFinder screen opened.
            check(Theme.prefix().equals("[PF] "), "chat prefix");
            check(Theme.frame() && Theme.frameLine().length() == 53, "chat frame");
            check(Theme.rgb("chat_error") == 0xFF5555, "chat color");

            Notifications.Notification request = Notifications.get("join_request");
            check(request.icon().is(Items.WRITABLE_BOOK) && request.sound() != null, "join_request notification");
            check(Notifications.get("join_sent").sound() == null, "silent notification");

            ListingMenu menu = ListingMenu.load(MenuLayout.load());
            check(menu.rows() == 6 && menu.listingSlots().size() == 28 && menu.listingSlots().getFirst() == 10, "listing slots");
            check(menu.buttons().get(ListingMenu.Button.CLOSE).slot() == 49 && menu.buttons().get(ListingMenu.Button.CLOSE).icon().is(Items.BARRIER),
                    "close button");
            check(menu.emptySlot() == 22 && menu.headIcons() && menu.ownGlint() && menu.fullMarker(), "listing menu flags");

            check(Activities.get("not_an_activity").icon().is(Items.PAPER), "unknown activity icon");
            check(Activities.categories().getFirst().equals("mining"), "category order");

            Notifications.toast("join_request", Component.translatable("partyfinder.toast.join_request.title"),
                    Component.literal("Notch wants to join your party · Y accept / N deny"));
            Notifications.toast("event", Component.literal("Spooky Festival started"), Component.literal("3 open parties"));
        });
        context.waitTicks(20);
        context.takeScreenshot("pack_toasts");
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError("Pack files: " + what);
    }
}
