package fr.bloup.partyfinder.mixin.client;

import fr.bloup.partyfinder.client.SoundDebug;
import fr.bloup.partyfinder.client.party.PartyStatus;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleSoundEvent", at = @At("HEAD"))
    private void partyfinder$logSound(ClientboundSoundPacket packet, CallbackInfo ci) {
        SoundDebug.onSound(packet);
    }

    /**
     * Hypixel's health/mana/defense line comes as an action bar packet, which skips the chat message events. TAIL is
     * only reached on the client thread (the network thread call bails out to reschedule itself).
     */
    @Inject(method = "setActionBarText", at = @At("TAIL"))
    private void partyfinder$readActionBar(ClientboundSetActionBarTextPacket packet, CallbackInfo ci) {
        PartyStatus.onActionBar(packet.text());
    }
}
