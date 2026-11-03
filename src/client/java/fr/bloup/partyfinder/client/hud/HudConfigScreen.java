package fr.bloup.partyfinder.client.hud;

import fr.bloup.partyfinder.client.config.PartyFinderConfig;
import fr.bloup.partyfinder.client.config.PartyFinderConfig.Hud;
import fr.bloup.partyfinder.client.gui.OptionWidgets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Vanilla-style options list for the party HUD, with a live preview. */
public class HudConfigScreen extends OptionsSubScreen {
    private static final String KEY = "partyfinder.hud.option.";
    private static final OptionWidgets W = new OptionWidgets(KEY);

    public HudConfigScreen(Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.translatable("partyfinder.hud.title"));
    }

    private static Hud cfg() {
        return PartyFinderConfig.get().hud;
    }

    @Override
    protected void addOptions() {
        Hud c = cfg();
        list.addHeader(Component.translatable(KEY + "section.general"));
        list.addSmall(List.of(
                W.toggle("enabled", c.enabled, v -> c.enabled = v),
                W.toggle("only_skyblock", c.onlyOnSkyBlock, v -> c.onlyOnSkyBlock = v),
                W.toggle("show_self", c.showSelf, v -> c.showSelf = v),
                W.toggle("hide_solo", c.hideWhenSolo, v -> c.hideWhenSolo = v),
                W.toggle("share_status", c.shareStatus, v -> c.shareStatus = v),
                W.slider("local_range", 0, 128, c.localRange, "m", v -> c.localRange = v)));

        list.addHeader(Component.translatable(KEY + "section.layout"));
        list.addSmall(List.of(
                Button.builder(Component.translatable(KEY + "edit_position"), b -> minecraft.gui.setScreen(new HudEditScreen(this)))
                        .width(OptionWidgets.WIDTH).build(),
                W.choice("anchor", PartyFinderConfig.Anchor.values(), c.anchor, v -> c.anchor = v),
                W.choice("layout", PartyFinderConfig.Layout.values(), c.layout, v -> c.layout = v),
                W.slider("scale", 50, 200, c.scale, "%", v -> c.scale = v),
                W.slider("spacing", 0, 20, c.spacing, "px", v -> c.spacing = v),
                W.slider("max_members", 1, 20, c.maxMembers, "", v -> c.maxMembers = v),
                W.toggle("compact", c.compact, v -> c.compact = v),
                W.slider("background_opacity", 0, 100, c.backgroundOpacity, "%", v -> c.backgroundOpacity = v),
                W.toggle("text_shadow", c.textShadow, v -> c.textShadow = v)));

        list.addHeader(Component.translatable(KEY + "section.portrait"));
        list.addSmall(List.of(
                W.choice("portrait", PartyFinderConfig.Portrait.values(), c.portrait, v -> c.portrait = v),
                W.slider("model_rotation", -90, 90, c.modelRotation, "°", v -> c.modelRotation = v)));

        list.addHeader(Component.translatable(KEY + "section.content"));
        list.addSmall(List.of(
                W.toggle("show_name", c.showName, v -> c.showName = v),
                W.toggle("show_leader", c.showLeader, v -> c.showLeader = v),
                W.toggle("show_health_bar", c.showHealthBar, v -> c.showHealthBar = v),
                W.choice("health_text", PartyFinderConfig.HealthText.values(), c.healthText, v -> c.healthText = v),
                W.toggle("show_mana_bar", c.showManaBar, v -> c.showManaBar = v),
                W.toggle("show_defense", c.showDefense, v -> c.showDefense = v),
                W.toggle("show_location", c.showLocation, v -> c.showLocation = v),
                W.toggle("show_distance", c.showDistance, v -> c.showDistance = v),
                W.toggle("show_stats", c.showStats, v -> c.showStats = v),
                W.toggle("show_armor", c.showArmor, v -> c.showArmor = v),
                W.toggle("show_held_item", c.showHeldItem, v -> c.showHeldItem = v)));
    }

    @Override
    public void removed() {
        PartyFinderConfig.save();
        super.removed();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        // Live preview pinned to the left edge so it never hides the options list.
        Hud preview = PartyFinderConfig.get().hud;
        PartyFinderConfig.Anchor anchor = preview.anchor;
        int offsetX = preview.offsetX;
        int offsetY = preview.offsetY;
        preview.anchor = PartyFinderConfig.Anchor.CENTER_LEFT;
        preview.offsetX = 4;
        preview.offsetY = 0;
        PartyHud.render(g, HudPreview.members(preview), preview);
        preview.anchor = anchor;
        preview.offsetX = offsetX;
        preview.offsetY = offsetY;
    }
}
