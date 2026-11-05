package fr.bloup.partyfinder.client.gui;

import fr.bloup.partyfinder.Partyfinder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

public final class PfSprites {
    public static final Identifier ENTRY = id("listing/entry");
    public static final Identifier ENTRY_HIGHLIGHTED = id("listing/entry_highlighted");
    public static final Identifier ENTRY_OWN = id("listing/entry_own");
    public static final Identifier HUD_FRAME = id("hud/frame");
    public static final Identifier HUD_FRAME_SELF = id("hud/frame_self");
    public static final Identifier HUD_LEADER = id("hud/leader");
    public static final Identifier HUD_BAR_BACKGROUND = id("hud/bar_background");
    public static final Identifier HUD_BAR_HEALTH = id("hud/bar_health");
    public static final Identifier HUD_BAR_ABSORPTION = id("hud/bar_absorption");
    public static final Identifier HUD_BAR_MANA = id("hud/bar_mana");
    public static final Identifier HUD_ICON_HEALTH = id("hud/icon_health");
    public static final Identifier HUD_ICON_DEFENSE = id("hud/icon_defense");

    // The stat icons are optional: a pack adding hud/icon_health.png or hud/icon_defense.png replaces the ❤ / ❈ text.
    private static boolean healthIcon;
    private static boolean defenseIcon;

    private PfSprites() {
    }

    /** Checks which optional sprites the enabled packs provide (on every resource reload). */
    public static void refresh(ResourceManager manager) {
        healthIcon = has(manager, HUD_ICON_HEALTH);
        defenseIcon = has(manager, HUD_ICON_DEFENSE);
    }

    public static Identifier healthIcon() {
        return healthIcon ? HUD_ICON_HEALTH : null;
    }

    public static Identifier defenseIcon() {
        return defenseIcon ? HUD_ICON_DEFENSE : null;
    }

    private static boolean has(ResourceManager manager, Identifier sprite) {
        return manager.getResource(sprite.withPath(p -> "textures/gui/sprites/" + p + ".png")).isPresent();
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Partyfinder.MOD_ID, path);
    }
}
