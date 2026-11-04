package fr.bloup.partyfinder.gametest;

import fr.bloup.partyfinder.client.config.PartyFinderConfig;
import fr.bloup.partyfinder.client.config.PartyFinderConfig.Hud;
import fr.bloup.partyfinder.client.hud.HudConfigScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Consumer;

/**
 * Screenshots of the party HUD preview (HUD settings screen) in a few setups, to check its layout by eye. Saved in the
 * game test run directory, under screenshots/.
 */
public class HudScreenshots implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            shoot(context, "hud_default", hud -> {
            });
            shoot(context, "hud_no_health_bar", hud -> hud.showHealthBar = false);
            shoot(context, "hud_gear", hud -> {
                hud.showArmor = true;
                hud.showHeldItem = true;
                hud.showDefense = true;
            });
            shoot(context, "hud_gear_percent", hud -> {
                hud.showArmor = true;
                hud.showHeldItem = true;
                hud.healthText = PartyFinderConfig.HealthText.PERCENT;
            });
            shoot(context, "hud_compact_right", hud -> {
                hud.compact = true;
                hud.side = PartyFinderConfig.Side.RIGHT;
                hud.showArmor = true;
            });
            // The real HUD element (no screen open), solo in the test world.
            shootInGame(context, "ingame_default", hud -> {
            });
            shootInGame(context, "ingame_no_health_bar", hud -> hud.showHealthBar = false);
            shootInGame(context, "ingame_model", HudScreenshots::modelSetup);
            shootInGame(context, "ingame_model_no_health_bar", hud -> {
                modelSetup(hud);
                hud.showHealthBar = false;
            });
        }
    }

    /** 3D model portrait with gear, anchored left like a typical setup. */
    private static void modelSetup(Hud hud) {
        hud.portrait = PartyFinderConfig.Portrait.MODEL;
        hud.anchor = PartyFinderConfig.Anchor.CENTER_LEFT;
        hud.offsetX = 3;
        hud.offsetY = -9;
        hud.modelRotation = -35;
        hud.showDefense = true;
        hud.showArmor = true;
        hud.showHeldItem = true;
    }

    private static void shootInGame(ClientGameTestContext context, String name, Consumer<Hud> setup) {
        context.runOnClient(client -> {
            PartyFinderConfig.get().hud = new Hud();
            Hud hud = PartyFinderConfig.get().hud;
            hud.portrait = PartyFinderConfig.Portrait.HEAD;
            hud.onlyOnSkyBlock = false;
            hud.hideWhenSolo = false;
            setup.accept(hud);
            // Something to show in the gear slots.
            client.player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
            client.player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_PICKAXE));
        });
        context.setScreen(() -> null);
        context.waitTicks(5);
        context.takeScreenshot(name);
    }

    private static void shoot(ClientGameTestContext context, String name, Consumer<Hud> setup) {
        context.runOnClient(client -> {
            PartyFinderConfig.get().hud = new Hud();
            Hud hud = PartyFinderConfig.get().hud;
            hud.portrait = PartyFinderConfig.Portrait.HEAD;
            setup.accept(hud);
        });
        context.setScreen(() -> new HudConfigScreen(null));
        context.waitTicks(5);
        context.takeScreenshot(name);
    }
}
