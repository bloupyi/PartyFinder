package fr.bloup.partyfinder.client.gui;

import fr.bloup.partyfinder.client.activity.Activities;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks tags from a list: the tags of a new listing (up to Listing.MAX_TAGS) or the tags to filter listings by (no
 * limit). Edits the given list in place; onDone runs when the screen closes.
 */
public class TagsScreen extends Screen {
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW = 22;
    private static final int GAP = 4;

    private final Screen parent;
    private final List<String> available;
    private final List<String> selected;
    private final int max;
    private final String hintKey;
    private final Runnable onDone;
    private final List<Button> tagButtons = new ArrayList<>();
    private int top;
    private int hintY;

    /** max: most tags that can be picked, 0 for no limit; hintKey: the line under the buttons, given (picked, max) when max > 0. */
    public TagsScreen(Screen parent, Component title, List<String> available, List<String> selected, int max, String hintKey,
                      Runnable onDone) {
        super(title);
        this.parent = parent;
        this.available = available;
        this.selected = selected;
        this.max = max;
        this.hintKey = hintKey;
        this.onDone = onDone;
    }

    @Override
    protected void init() {
        tagButtons.clear();
        selected.retainAll(available);
        int columns = width >= 340 ? 3 : 2;
        int buttonWidth = Math.min(110, (width - 20 - (columns - 1) * GAP) / columns);
        int gridWidth = columns * buttonWidth + (columns - 1) * GAP;
        int rows = (available.size() + columns - 1) / columns;
        int contentHeight = 16 + rows * ROW + 14 + 4 + BUTTON_HEIGHT;
        top = Math.max(8, (height - contentHeight) / 2);
        int left = (width - gridWidth) / 2;
        for (int i = 0; i < available.size(); i++) {
            String tag = available.get(i);
            int x = left + (i % columns) * (buttonWidth + GAP);
            int y = top + 16 + (i / columns) * ROW;
            Button button = addRenderableWidget(Button.builder(label(tag), b -> toggle(tag)).bounds(x, y, buttonWidth, BUTTON_HEIGHT).build());
            tagButtons.add(button);
        }
        hintY = top + 16 + rows * ROW + 4;
        int buttonsY = hintY + 14;
        int half = Math.min(100, (gridWidth - GAP) / 2);
        addRenderableWidget(Button.builder(Component.translatable("partyfinder.tags.clear"), b -> {
            selected.clear();
            refresh();
        }).bounds(width / 2 - half - GAP / 2, buttonsY, half, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 + GAP / 2, buttonsY, half, BUTTON_HEIGHT).build());
        refresh();
    }

    private void toggle(String tag) {
        if (!selected.remove(tag) && (max <= 0 || selected.size() < max)) selected.add(tag);
        refresh();
    }

    private void refresh() {
        boolean full = max > 0 && selected.size() >= max;
        for (int i = 0; i < tagButtons.size(); i++) {
            String tag = available.get(i);
            Button button = tagButtons.get(i);
            button.setMessage(label(tag));
            button.active = selected.contains(tag) || !full;
        }
    }

    private Component label(String tag) {
        boolean on = selected.contains(tag);
        Component name = Activities.tagName(tag);
        return on ? Component.literal("✔ ").append(name).withColor(Activities.color("tag_selected", 0x55FF55)) : name;
    }

    @Override
    public void onClose() {
        onDone.run();
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(font, title, width / 2, top, Activities.color("title", 0xFFFFFF));
        Component text = max > 0 ? Component.translatable(hintKey, selected.size(), max) : Component.translatable(hintKey);
        g.centeredText(font, TimeFormat.clip(font, text.getString(), width - 20), width / 2, hintY, Activities.color("label", 0xA0A0A0));
    }
}
