package fr.bloup.partyfinder.client;

import fr.bloup.partyfinder.Partyfinder;
import fr.bloup.partyfinder.client.activity.Activities;
import fr.bloup.partyfinder.client.hypixel.HypixelBridge;
import fr.bloup.partyfinder.client.net.BackendConnection;
import fr.bloup.partyfinder.client.party.Listing;
import fr.bloup.partyfinder.client.party.PartyFinderState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The only place the mod sends anything to the Minecraft server besides the official Hypixel Mod API packets
 * (HypixelProtocol): chat commands from fixed allowlists. No container clicks, no GUI packets, no chat messages;
 * the build's checkOutboundPackets task fails if other code starts sending packets.
 */
public final class PartyActions {
    private static final Component PREFIX = Component.literal("[PF] ").withStyle(ChatFormatting.DARK_AQUA);
    private static final int INVITE_ACCEPT_DELAY_TICKS = 20;
    private static final int COMMAND_SPACING_TICKS = 10;
    private static final Pattern PARTY_COMMAND =
            Pattern.compile("party (?:(?:invite|kick|transfer|accept) [A-Za-z0-9_]{1,16}|leave|disband|warp)");

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
            // Replacing a server menu (Hypixel chest GUI) would send a container close packet: never do it.
            if (!(mc.gui.screen() instanceof AbstractContainerScreen<?>)) mc.gui.setScreen(pendingScreen);
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

    /** Party commands only: anything else (from the network, a resource pack or a bug) is dropped. */
    public static void command(String command) {
        if (!PARTY_COMMAND.matcher(command).matches()) {
            Partyfinder.LOGGER.warn("Blocked non-party command \"{}\"", command);
            return;
        }
        send(command);
    }

    /** Every command goes through here and must match one of the allowlists, whoever the caller is. */
    private static void send(String command) {
        if (!PARTY_COMMAND.matcher(command).matches() && !Activities.isSafeCommand(command)) {
            Partyfinder.LOGGER.warn("Blocked command \"{}\"", command);
            return;
        }
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

    /** The request only counts as sent once the backend forwards it (join_sent); a refusal leaves nothing pending. */
    public static void requestJoin(Listing listing) {
        BackendConnection.requestJoin(listing.id());
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

    /**
     * Activity launch commands, only ever run from the player's own click: travel commands only (warp, joininstance),
     * spaced out so the server does not drop them.
     */
    public static void runLaunchCommands(List<String> commands) {
        int delay = 0;
        for (String command : commands) {
            if (!Activities.isSafeCommand(command)) continue;
            DELAYED.add(new Delayed(Math.max(1, delay), () -> send(command)));
            delay += COMMAND_SPACING_TICKS;
        }
    }

    /** Invites several players one after another. */
    public static void inviteAll(List<String> names) {
        int delay = 0;
        for (String name : names) {
            DELAYED.add(new Delayed(Math.max(1, delay), () -> partyCommand("invite", name)));
            delay += COMMAND_SPACING_TICKS;
        }
    }

    public static void acceptInviteLater(String owner) {
        if (!Listing.isValidName(owner)) return;
        DELAYED.add(new Delayed(INVITE_ACCEPT_DELAY_TICKS, () -> partyCommand("accept", owner)));
    }
}
