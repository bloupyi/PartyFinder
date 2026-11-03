package fr.bloup.partyfinder.client.gui;

import fr.bloup.partyfinder.client.activity.Activities;
import fr.bloup.partyfinder.client.party.ListingDrafts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Save the listing form as a named preset, or load / delete one. */
public class PresetsScreen extends Screen {
    private static final int TOP = 56;
    private static final int FOOTER = 36;
    private static final int MAX_WIDTH = 300;

    private final CreateListingScreen parent;
    private PresetList list;
    private EditBox nameBox;
    private Button saveButton;
    private Button loadButton;
    private Button deleteButton;

    public PresetsScreen(CreateListingScreen parent) {
        super(Component.translatable("partyfinder.presets.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = Math.min(MAX_WIDTH, width - 20);
        int x = (width - w) / 2;
        nameBox = addRenderableWidget(new EditBox(font, x, 24, w - 84, 20, Component.translatable("partyfinder.presets.name")));
        nameBox.setMaxLength(32);
        nameBox.setHint(Component.translatable("partyfinder.presets.name"));
        nameBox.setResponder(v -> updateButtons());
        saveButton = addRenderableWidget(Button.builder(Component.translatable("partyfinder.presets.save"), b -> {
            ListingDrafts.savePreset(nameBox.getValue().strip(), parent.draft());
            nameBox.setValue("");
            list.refresh();
        }).bounds(x + w - 80, 24, 80, 20).build());

        list = addRenderableWidget(new PresetList(minecraft, width, height - TOP - FOOTER, TOP, w));
        int third = (w - 8) / 3;
        int y = height - FOOTER + 8;
        loadButton = addRenderableWidget(Button.builder(Component.translatable("partyfinder.presets.load"), b -> load())
                .bounds(x, y, third, 20).build());
        deleteButton = addRenderableWidget(Button.builder(Component.translatable("partyfinder.presets.delete"), b -> {
            ListingDrafts.Preset selected = list.selected();
            if (selected != null) ListingDrafts.deletePreset(selected.name());
            list.refresh();
        }).bounds(x + third + 4, y, third, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("partyfinder.screen.back"), b -> onClose())
                .bounds(x + 2 * (third + 4), y, w - 2 * (third + 4), 20).build());
        list.refresh();
    }

    private void load() {
        ListingDrafts.Preset selected = list.selected();
        if (selected == null) return;
        parent.apply(selected.draft().normalized());
        onClose();
    }

    private void updateButtons() {
        if (saveButton == null || list == null) return;
        saveButton.active = !nameBox.getValue().isBlank();
        loadButton.active = list.selected() != null;
        deleteButton.active = list.selected() != null;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(font, title, width / 2, 8, Activities.color("title", 0xFFFFFF));
        if (list.children().isEmpty()) {
            g.centeredText(font, Component.translatable("partyfinder.presets.empty"), width / 2, TOP + 20, Activities.color("empty", 0xA0A0A0));
        }
    }

    private class PresetList extends ObjectSelectionList<PresetList.Entry> {
        private final int rowWidth;

        PresetList(Minecraft mc, int width, int height, int y, int rowWidth) {
            super(mc, width, height, y, 26);
            this.rowWidth = rowWidth;
        }

        void refresh() {
            List<Entry> entries = ListingDrafts.presets().stream().map(Entry::new).toList();
            replaceEntries(entries);
            updateButtons();
        }

        ListingDrafts.Preset selected() {
            return getSelected() == null ? null : getSelected().preset;
        }

        @Override
        public void setSelected(Entry entry) {
            super.setSelected(entry);
            updateButtons();
        }

        @Override
        public int getRowWidth() {
            return rowWidth;
        }

        class Entry extends ObjectSelectionList.Entry<Entry> {
            private final ListingDrafts.Preset preset;

            Entry(ListingDrafts.Preset preset) {
                this.preset = preset;
            }

            @Override
            public Component getNarration() {
                return Component.literal(preset.name());
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
                setSelected(this);
                if (doubleClick) load();
                return true;
            }

            @Override
            public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float partialTick) {
                Font font = minecraft.font;
                int x = getContentX();
                int y = getContentY();
                int w = getContentWidth();
                g.blitSprite(RenderPipelines.GUI_TEXTURED, hovered ? PfSprites.ENTRY_HIGHLIGHTED : PfSprites.ENTRY, x, y, w, getContentHeight());
                g.text(font, TimeFormat.clip(font, preset.name(), w - 8), x + 4, y + 3, Activities.color("title", 0xFFFFFF));
                var activity = Activities.get(preset.draft().activity());
                g.text(font, TimeFormat.clip(font, activity.title(preset.draft().options()).getString(), w - 8), x + 4, y + 13,
                        0xFF000000 | activity.color());
            }
        }
    }
}
