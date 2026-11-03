package fr.bloup.partyfinder.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.bloup.partyfinder.Partyfinder;
import fr.bloup.partyfinder.client.config.PartyFinderConfig;
import fr.bloup.partyfinder.client.gui.CategoryMenuScreen;
import fr.bloup.partyfinder.client.gui.PfSprites;
import fr.bloup.partyfinder.client.hypixel.HypixelBridge;
import fr.bloup.partyfinder.client.net.BackendConnection;
import fr.bloup.partyfinder.client.hud.PartyHud;
import fr.bloup.partyfinder.client.party.PartyStatus;
import fr.bloup.partyfinder.client.party.PartySync;
import fr.bloup.partyfinder.mixin.client.AbstractContainerScreenAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class PartyfinderClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Partyfinder.MOD_ID, "main"));
    private static KeyMapping openKey;

    @Override
    public void onInitializeClient() {
        PartyFinderConfig.load();
        HypixelBridge.init();

        openKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.partyfinder.open", InputConstants.Type.KEYSYM, InputConstants.KEY_O, CATEGORY));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> PartyFinderCommands.register(dispatcher));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            PartyActions.tick(mc);
            PartySync.tick(mc);
            PartyStatus.tick();
            while (openKey.consumeClick()) {
                if (mc.gui.screen() == null) mc.gui.setScreen(new CategoryMenuScreen(null));
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
            BackendConnection.connect();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
            BackendConnection.disconnect();
            HypixelBridge.reset();
            PartySync.reset();
            PartyStatus.reset();
            PartyHud.reset();
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) PartyStatus.onActionBar(message);
            else HypixelBridge.onChat(message);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> BackendConnection.disconnect());

        ScreenEvents.AFTER_INIT.register(this::addMenuButton);
        HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR,
                Identifier.fromNamespaceAndPath(Partyfinder.MOD_ID, "party_hud"), new PartyHud());
    }

    private void addMenuButton(Minecraft mc, Screen screen, int width, int height) {
        if (!PartyFinderConfig.get().showMenuButton || !(screen instanceof AbstractContainerScreen<?>)) return;
        if (mc.player == null || !BackendConnection.isAllowedServer()) return;
        AbstractContainerScreenAccessor container = (AbstractContainerScreenAccessor) screen;
        int x = container.partyfinder$getLeftPos() + container.partyfinder$getImageWidth() + 4;
        if (x + 20 > width) x = container.partyfinder$getLeftPos() - 24;
        x = Math.clamp(x, 0, Math.max(0, width - 20));
        int y = Math.clamp(container.partyfinder$getTopPos(), 0, Math.max(0, height - 20));
        SpriteIconButton button = SpriteIconButton.builder(Component.translatable("partyfinder.screen.title"),
                        b -> mc.gui.setScreen(new CategoryMenuScreen(screen)), true)
                .sprite(PfSprites.MENU_ICON, 16, 16)
                .size(20, 20)
                .withTootip()
                .build();
        button.setX(x);
        button.setY(y);
        Screens.getWidgets(screen).add(button);
    }
}
