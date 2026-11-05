package fr.bloup.partyfinder.client;

import fr.bloup.partyfinder.client.activity.Theme;
import fr.bloup.partyfinder.Partyfinder;
import fr.bloup.partyfinder.client.activity.Activities;
import fr.bloup.partyfinder.client.hypixel.HypixelBridge;
import fr.bloup.partyfinder.client.net.BackendConnection;
import fr.bloup.partyfinder.client.party.LeaderPrompts;
import fr.bloup.partyfinder.client.party.Listing;
import fr.bloup.partyfinder.client.party.PartyChat;
import fr.bloup.partyfinder.client.party.PartyFinderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The only place the mod sends anything to the Minecraft server besides the official Hypixel Mod API packets
 * (HypixelProtocol): chat commands from fixed allowlists. No container clicks, no GUI packets, no chat messages;
 * the build's checkOutboundPackets task fails if other code starts sending packets.
 */
public final class PartyActions {
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

    /** Chat prefix of every mod message (theme.json "chat.prefix", color "chat_prefix"). */
    private static MutableComponent prefix() {
        return Component.literal(Theme.prefix()).withColor(Theme.rgb("chat_prefix"));
    }

    /**
     * Hypixel-style framed message: blue struck-through lines above and below, like party notifications. The line is
     * set in theme.json ("chat": frame, frame_text, frame_length, frame_strikethrough; color "chat_frame").
     */
    public static void framed(Component message) {
        framed(message, null);
    }

    /** Framed message followed by lines below the frame (hover hint, buttons), all in one chat message. */
    public static void framed(Component message, Component after) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        MutableComponent full = Component.empty();
        if (Theme.frame()) {
            Component line = Component.literal(Theme.frameLine())
                    .withStyle(style -> style.withColor(Theme.rgb("chat_frame")).withStrikethrough(Theme.frameStrikethrough()));
            full.append(line).append("\n").append(prefix()).append(message).append("\n").append(line);
        } else {
            full.append(prefix()).append(message);
        }
        if (after != null) full.append("\n").append(after);
        mc.gui.chatListener().handleSystemMessage(full, false);
    }

    public static void chat(Component message) {
        Minecraft mc = Minecraft.getInstance();
        // LocalPlayer.sendSystemMessage targets the action bar in 26.x, which Hypixel overwrites constantly.
        if (mc.player != null) mc.gui.chatListener().handleSystemMessage(Component.empty().append(prefix()).append(message), false);
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
    public static void requestJoin(Listing listing, String role) {
        BackendConnection.requestJoin(listing.id(), role);
    }

    public static boolean accept(String name) {
        PartyFinderState.joinRequests().stream().filter(r -> r.player().name().equalsIgnoreCase(name)).findFirst()
                .ifPresent(PartyChat::accepted);
        return PartyFinderState.takeJoinRequest(name).map(player -> {
            if (!Listing.isValidName(player.name())) return true;
            // No /p invite from here: the server first checks the request is still valid and a seat is free, then
            // tells whoever holds the Hypixel lead (us or someone else) to invite (invite_member).
            BackendConnection.respond(player, true, false);
            return true;
        }).orElse(false);
    }

    /** Quick answer (keybind) to the most recent join request, while it is still pending. */
    public static void respondToLatest(boolean accept) {
        // Y/N answer whatever came last: a join request, or a warp / transfer request to us as Party Leader.
        Optional<LeaderPrompts.Prompt> prompt = LeaderPrompts.latest();
        if (prompt.isPresent() && prompt.get().at() >= PartyFinderState.latestJoinRequestAt()) {
            LeaderPrompts.answer(prompt.get().type(), prompt.get().player().name(), accept);
            return;
        }
        List<PartyFinderState.JoinRequest> requests = PartyFinderState.joinRequests();
        if (requests.isEmpty()) return;
        String name = requests.getLast().player().name();
        if (accept) accept(name);
        else deny(name);
        chat(Component.translatable(accept ? "partyfinder.chat.request_accepted" : "partyfinder.chat.request_denied", name)
                .withColor(Theme.rgb(accept ? "chat_success" : "chat_error")));
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

}
