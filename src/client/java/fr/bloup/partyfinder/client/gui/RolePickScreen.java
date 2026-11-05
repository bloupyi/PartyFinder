package fr.bloup.partyfinder.client.gui;

import fr.bloup.partyfinder.client.PartyActions;
import fr.bloup.partyfinder.client.activity.Activities;
import fr.bloup.partyfinder.client.activity.StatFormat;
import fr.bloup.partyfinder.client.party.Listing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashSet;
import java.util.List;

/** Joining a Kuudra party that wants roles: the requester picks one of the open roles, sent with the request. */
public class RolePickScreen extends Screen {
    private static final int WIDTH = 200;

    private final Screen parent;
    private final Listing listing;
    private final List<String> open;
    private int top;

    public RolePickScreen(Screen parent, Listing listing, List<String> open) {
        super(Component.translatable("partyfinder.join.role.title"));
        this.parent = parent;
        this.listing = listing;
        this.open = List.copyOf(new LinkedHashSet<>(open));
    }

    @Override
    protected void init() {
        int w = Math.min(WIDTH, width - 20);
        int x = width / 2 - w / 2;
        top = Math.max(20, height / 2 - (open.size() * 24 + 48) / 2);
        int y = top + 20;
        for (String role : open) {
            addRenderableWidget(Button.builder(StatFormat.roleName(listing.effectiveCategory(), role), b -> {
                PartyActions.requestJoin(listing, role);
                onClose();
            }).bounds(x, y, w, 20).build());
            y += 24;
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(width / 2 - 75, y + 8, 150, 20).build());
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(font, title, width / 2, top, Activities.color("title", 0xFFFFFF));
    }
}
