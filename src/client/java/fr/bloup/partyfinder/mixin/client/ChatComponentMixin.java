package fr.bloup.partyfinder.mixin.client;

import fr.bloup.partyfinder.client.party.PartyChat;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    // Only the display is skipped: the message was received, logged and stays in the chat history (allMessages), so
    // other mods still see it. Also applies when the chat is rebuilt (resize, scale change).
    @Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"), cancellable = true)
    private void partyfinder$hidePartyClutter(GuiMessage message, CallbackInfo ci) {
        if (PartyChat.isHidden(message.content())) ci.cancel();
    }
}
