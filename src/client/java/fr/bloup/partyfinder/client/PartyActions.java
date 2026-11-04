package fr.bloup.partyfinder.client;

import fr.bloup.partyfinder.client.hypixel.HypixelBridge;
import fr.bloup.partyfinder.client.net.BackendConnection;
import fr.bloup.partyfinder.client.party.Listing;
import fr.bloup.partyfinder.client.party.PartyFinderState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class PartyActions {
    private static final Component PREFIX = Component.literal("[PF] ").withStyle(ChatFormatting.DARK_AQUA);
    private static final int INVITE_ACCEPT_DELAY_TICKS = 20;
    private static final int COMMAND_SPACING_TICKS = 10;

    private static final List<Delayed> DELAYED = new ArrayList<>();
    private static Screen pendingScreen;
    private static boolean sendingOwn;

    private static final class Delayed {
        private int ticks;
        private final Runnable task;

        private Delayed(int ticks, Runnable task) {
            this.ticks = ticks;
            this.task = task;
        }
    }

    private PartyActions() {
    }

    public static void tick(Minecraft mc) {
        if (pendingScreen != null) {
            mc.gui.setScreen(pendingScreen);
            pendingScreen = null;
        }
        Iterator<Delayed> it = DELAYED.iterator();
        while (it.hasNext()) {
            Delayed delayed = it.next();
            if (--delayed.ticks <= 0) {
                it.remove();
                delayed.task.run();
            }
        }
    }

    public static void openNextTick(Screen screen) {
        pendingScreen = screen;
    }

    public static void chat(Component message) {
        Minecraft mc = Minecraft.getInstance();
        // LocalPlayer.sendSystemMessage targets the action bar in 26.x, which Hypixel overwrites constantly.
        if (mc.player != null) mc.gui.chatListener().handleSystemMessage(PREFIX.copy().append(message), false);
    }

    public static void command(String command) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        sendingOwn = true;
        try {
            player.connection.sendCommand(command);
        } finally {
            sendingOwn = false;
        }
    }

    /** True while the mod itself sends a command, so the party command guard lets it through. */
    public static boolean isSendingOwnCommand() {
        return sendingOwn;
    }

    public static void later(int ticks, Runnable task) {
        DELAYED.add(new Delayed(Math.max(1, ticks), task));
    }

    public static boolean partyCommand(String action, String name) {
        // Names come from the network: never let one inject extra arguments into a command.
        if (!Listing.isValidName(name)) return false;
        command("party " + action + " " + name);
        return true;
    }

    public static void requestJoin(Listing listing) {
        PartyFinderState.markRequested(listing.owner().uuid());
        BackendConnection.requestJoin(listing.id());
        chat(Component.translatable("partyfinder.chat.join_sent", listing.owner().name()));
    }

    public static boolean accept(String name) {
        return PartyFinderState.takeJoinRequest(name).map(player -> {
            if (!Listing.isValidName(player.name())) return true;
            // After an automatic transfer only the current Hypixel leader can invite; the backend forwards it otherwise.
            boolean invited = HypixelBridge.isLeaderOrSolo() && partyCommand("invite", player.name());
            BackendConnection.respond(player, true, invited);
            return true;
        }).orElse(false);
    }

    /** Quick answer (keybind) to the most recent join request, while it is still pending. */
    public static void respondToLatest(boolean accept) {
        List<PartyFinderState.JoinRequest> requests = PartyFinderState.joinRequests();
        if (requests.isEmpty()) return;
        String name = requests.getLast().player().name();
        if (accept) accept(name);
        else deny(name);
        chat(Component.translatable(accept ? "partyfinder.chat.request_accepted" : "partyfinder.chat.request_denied", name)
                .withStyle(accept ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    public static boolean deny(String name) {
        return PartyFinderState.takeJoinRequest(name).map(player -> {
            BackendConnection.respond(player, false, false);
            return true;
        }).orElse(false);
    }

    /** Runs commands one after another, spaced out so the server does not drop them. */
    public static void runCommands(List<String> commands) {
        int delay = 0;
        for (String command : commands) {
            if (!command.matches("[A-Za-z0-9_ ]{1,64}")) continue;
            DELAYED.add(new Delayed(Math.max(1, delay), () -> command(command)));
            delay += COMMAND_SPACING_TICKS;
        }
    }

    public static void acceptInviteLater(String owner) {
        if (!Listing.isValidName(owner)) return;
        DELAYED.add(new Delayed(INVITE_ACCEPT_DELAY_TICKS, () -> partyCommand("accept", owner)));
    }
}
