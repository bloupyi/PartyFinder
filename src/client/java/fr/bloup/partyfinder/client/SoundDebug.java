package fr.bloup.partyfinder.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;

import java.util.Locale;

/** "/pf sounds": prints the sounds the server plays, to identify e.g. the SkyBlock menu sound for menu.json. */
public final class SoundDebug {
    private static boolean enabled;

    private SoundDebug() {
    }

    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    public static void onSound(ClientboundSoundPacket packet) {
        // The packet handler runs once on the network thread before being re-dispatched to the client thread.
        if (!enabled || !Minecraft.getInstance().isSameThread()) return;
        String id = packet.getSound().value().location().toString();
        String details = String.format(Locale.ROOT, "%s  vol %.2f  pitch %.2f", id, packet.getVolume(), packet.getPitch());
        PartyActions.chat(Component.literal(details).withStyle(style -> style.withColor(ChatFormatting.GRAY)
                .withClickEvent(new ClickEvent.CopyToClipboard(id))));
    }
}
