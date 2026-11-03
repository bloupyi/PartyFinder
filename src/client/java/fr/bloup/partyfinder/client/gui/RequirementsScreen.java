package fr.bloup.partyfinder.client.gui;

import fr.bloup.partyfinder.client.activity.Activities;
import fr.bloup.partyfinder.client.activity.StatFormat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

/** Minimum stats a requester must have; the server checks them against the player's real profile. */
public class RequirementsScreen extends Screen {
    private static final int WIDTH = 220;

    private final CreateListingScreen parent;
    private final List<String> ids;
    private int top;

    public RequirementsScreen(CreateListingScreen parent, List<String> ids) {
        super(Component.translatable("partyfinder.create.min_stats.title"));
        this.parent = parent;
        this.ids = ids.subList(0, Math.min(4, ids.size()));
    }

    @Override
    protected void init() {
        int w = Math.min(WIDTH, width - 20);
        int x = width / 2 - w / 2;
        top = Math.max(20, height / 2 - (ids.size() * 24 + 40) / 2);
        int y = top + 16;
        for (String id : ids) {
            int max = StatFormat.maxFor(id);
            int value = parent.minStats.getOrDefault(id, 0);
            addRenderableWidget(new AbstractSliderButton(x, y, w, 20, Component.empty(), (double) value / max) {
                {
                    updateMessage();
                }

                private int current() {
                    return (int) Math.round(Mth.lerp(this.value, 0, max));
                }

                @Override
                protected void updateMessage() {
                    int v = current();
                    setMessage(v == 0
                            ? StatFormat.label(id).copy().append(": ").append(Component.translatable("partyfinder.create.min_stats.any"))
                            : StatFormat.requirement(id, v));
                }

                @Override
                protected void applyValue() {
                    int v = current();
                    if (v == 0) parent.minStats.remove(id);
                    else parent.minStats.put(id, v);
                }
            });
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
    }
}
