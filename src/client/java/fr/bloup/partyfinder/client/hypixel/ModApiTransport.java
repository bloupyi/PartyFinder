package fr.bloup.partyfinder.client.hypixel;

import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.packet.impl.clientbound.ClientboundPartyInfoPacket;
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket;
import net.hypixel.modapi.packet.impl.serverbound.ServerboundPartyInfoPacket;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Used only when the official Hypixel Mod API mod is installed: it owns the hypixel:* channels, so we go through it
 * instead of registering them twice. Never loaded otherwise.
 */
final class ModApiTransport implements HypixelBridge.Transport {
    @Override
    public void init() {
        HypixelModAPI api = HypixelModAPI.getInstance();
        api.subscribeToEventPacket(ClientboundLocationPacket.class);
        api.createHandler(ClientboundPartyInfoPacket.class, packet -> Minecraft.getInstance().execute(() -> {
            Map<UUID, HypixelBridge.Role> members = new LinkedHashMap<>();
            packet.getMemberMap().forEach((uuid, member) -> members.put(uuid, HypixelBridge.Role.valueOf(member.getRole().name())));
            HypixelBridge.onParty(packet.isInParty(), members);
        }));
        api.createHandler(ClientboundLocationPacket.class, packet -> Minecraft.getInstance().execute(() ->
                HypixelBridge.onLocation(packet.getServerType().map(t -> t.name()).orElse(null),
                        packet.getMode().orElse(null), packet.getMap().orElse(null))));
    }

    @Override
    public void requestParty() {
        HypixelModAPI.getInstance().sendPacket(new ServerboundPartyInfoPacket());
    }
}
