package fr.bloup.partyfinder.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.bloup.partyfinder.Partyfinder;
import fr.bloup.partyfinder.client.config.PartyFinderConfig;
import fr.bloup.partyfinder.client.gui.CategoryMenuScreen;
import fr.bloup.partyfinder.client.hypixel.HypixelBridge;
import fr.bloup.partyfinder.client.net.BackendConnection;
import fr.bloup.partyfinder.client.hud.PartyHud;
import fr.bloup.partyfinder.client.party.EventAlerts;
import fr.bloup.partyfinder.client.party.InviteAccept;
import fr.bloup.partyfinder.client.party.LiveStats;
import fr.bloup.partyfinder.client.party.PartyStatus;
import fr.bloup.partyfinder.client.party.PartySync;
import fr.bloup.partyfinder.client.party.PartyTransfer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public class PartyfinderClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Partyfinder.MOD_ID, "main"));
    private static KeyMapping openKey;
    public static KeyMapping acceptKey;
    public static KeyMapping denyKey;

    @Override
    public void onInitializeClient() {
        PartyFinderConfig.load();
        HypixelBridge.init();

        openKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.partyfinder.open", InputConstants.Type.KEYSYM, InputConstants.KEY_O, CATEGORY));

        acceptKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.partyfinder.accept", InputConstants.Type.KEYSYM, InputConstants.KEY_Y, CATEGORY));
        denyKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.partyfinder.deny", InputConstants.Type.KEYSYM, InputConstants.KEY_N, CATEGORY));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> PartyFinderCommands.register(dispatcher));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            PartyActions.tick(mc);
            PartySync.tick(mc);
            PartyTransfer.tick();
            PartyStatus.tick();
            LiveStats.tick();
            InviteAccept.tick();
            EventAlerts.tick();
            BackendConnection.checkPublishTimeout();
            while (acceptKey.consumeClick()) {
                if (mc.gui.screen() == null) PartyActions.respondToLatest(true);
            }
            while (denyKey.consumeClick()) {
                if (mc.gui.screen() == null) PartyActions.respondToLatest(false);
            }
            while (openKey.consumeClick()) {
                if (mc.gui.screen() == null) mc.gui.setScreen(new CategoryMenuScreen(null));
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
            BackendConnection.connect();
            PartyTransfer.onJoin();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
            BackendConnection.disconnect();
            HypixelBridge.reset();
            PartySync.reset();
            PartyTransfer.reset();
            PartyStatus.reset();
            LiveStats.reset();
            InviteAccept.reset();
            PartyHud.reset();
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) PartyStatus.onActionBar(message);
            else {
                HypixelBridge.onChat(message);
                PartyStatus.onChat(message);
                LiveStats.onChat(message);
                InviteAccept.onChat(message);
                PartyTransfer.onChat(message);
            }
        });
        ClientSendMessageEvents.ALLOW_COMMAND.register(PartyTransfer::allowCommand);
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> BackendConnection.disconnect());

        HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR,
                Identifier.fromNamespaceAndPath(Partyfinder.MOD_ID, "party_hud"), new PartyHud());
    }
}
