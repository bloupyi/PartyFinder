package fr.bloup.partyfinder.mixin.client;

import fr.bloup.partyfinder.client.party.PartyTransfer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    // Runs while the connection is still open, so the lead can be handed back before leaving.
    @Inject(method = "disconnect", at = @At("HEAD"))
    private void partyfinder$returnLead(Component reason, CallbackInfo ci) {
        PartyTransfer.onDisconnecting();
    }
}
