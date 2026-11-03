package fr.bloup.partyfinder.client.gui;

import fr.bloup.partyfinder.Partyfinder;
import net.minecraft.resources.Identifier;

public final class PfSprites {
    public static final Identifier MENU_ICON = id("icon/party");
    public static final Identifier ENTRY = id("listing/entry");
    public static final Identifier ENTRY_HIGHLIGHTED = id("listing/entry_highlighted");
    public static final Identifier ENTRY_OWN = id("listing/entry_own");

    private PfSprites() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Partyfinder.MOD_ID, path);
    }
}
