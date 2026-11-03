package fr.bloup.partyfinder.client.gui;

import fr.bloup.partyfinder.client.activity.Activities;
import fr.bloup.partyfinder.client.activity.StatFormat;
import fr.bloup.partyfinder.client.party.Listing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.stream.IntStream;

/** Dungeon classes the leader is looking for; members' selected classes fill them automatically. */
public class RolesScreen extends Screen {
    private static final int WIDTH = 220;

    private final CreateListingScreen parent;
    private int top;

    public RolesScreen(CreateListingScreen parent) {
        super(Component.translatable("partyfinder.create.roles.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = Math.min(WIDTH, width - 20);
        int x = width / 2 - w / 2;
        top = Math.max(20, height / 2 - (Listing.CLASSES.size() * 24 + 40) / 2);
        int y = top + 16;
        int maxPerClass = Math.max(1, parent.maxSize - 1);
        for (String cls : Listing.CLASSES) {
            int count = Collections.frequency(parent.roles, cls);
            addRenderableWidget(CycleButton.builder((Integer v) -> Component.literal(String.valueOf(v)), Math.min(count, maxPerClass))
                    .withValues(IntStream.rangeClosed(0, maxPerClass).boxed().toList())
                    .create(x, y, w, 20, StatFormat.className(cls), (b, value) -> {
                        parent.roles.removeIf(cls::equals);
                        for (int i = 0; i < value; i++) parent.roles.add(cls);
                        while (parent.roles.size() > parent.maxSize - 1) parent.roles.removeLast();
                    }));
            y += 24;
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 - 75, y + 8, 150, 20).build());
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(font, title, width / 2, top, Activities.color("title", 0xFFFFFF));
        Component hint = Component.translatable("partyfinder.create.roles.hint", parent.maxSize - 1);
        g.centeredText(font, TimeFormat.clip(font, hint.getString(), width - 20), width / 2, top + 16 + Listing.CLASSES.size() * 24 + 32,
                Activities.color("label", 0xA0A0A0));
    }
}
