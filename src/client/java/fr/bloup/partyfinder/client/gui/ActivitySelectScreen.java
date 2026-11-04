package fr.bloup.partyfinder.client.gui;

import fr.bloup.partyfinder.client.activity.Activities;
import fr.bloup.partyfinder.client.activity.Activity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Searchable activity picker, grouped by category, instead of cycling through every activity. */
public class ActivitySelectScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int TOP = 48;
    private static final int FOOTER = 32;

    private final Screen parent;
    private final Activity current;
    private final Consumer<Activity> onPick;
    private String query = "";
    private ActivityList list;

    public ActivitySelectScreen(Screen parent, Activity current, Consumer<Activity> onPick) {
        super(Component.translatable("partyfinder.create.activity.pick"));
        this.parent = parent;
        this.current = current;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        int boxWidth = Math.min(240, width - 20);
        EditBox search = new EditBox(font, width / 2 - boxWidth / 2, 22, boxWidth, 20, Component.translatable("partyfinder.create.activity.search"));
        search.setHint(Component.translatable("partyfinder.create.activity.search"));
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            refresh();
        });
        addRenderableWidget(search);
        list = addRenderableWidget(new ActivityList(minecraft, width, height - TOP - FOOTER, TOP));
        refresh();
        list.children().stream().filter(e -> e.activity != null && e.activity.id().equals(current.id())).findFirst()
                .ifPresent(list::select);
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(width / 2 - 75, height - FOOTER + 6, 150, 20).build());
        setInitialFocus(search);
    }

    private void refresh() {
        String q = query.strip().toLowerCase(Locale.ROOT);
        List<String> categories = Activities.categories();
        List<Activity> matches = Activities.all().stream()
                .filter(a -> q.isEmpty() || matches(a, q))
                .sorted(Comparator.comparingInt(a -> categories.indexOf(a.category())))
                .toList();
        List<Row> entries = new ArrayList<>();
        String category = null;
        for (Activity activity : matches) {
            if (!activity.category().equals(category)) {
                category = activity.category();
                entries.add(new Row(null, activity.categoryName()));
            }
            entries.add(new Row(activity, activity.name()));
        }
        list.replaceEntries(entries);
        list.setScrollAmount(0);
    }

    private static boolean matches(Activity activity, String query) {
        return activity.name().getString().toLowerCase(Locale.ROOT).contains(query)
                || activity.categoryName().getString().toLowerCase(Locale.ROOT).contains(query)
                || activity.id().contains(query);
    }

    private void pick(Activity activity) {
        onPick.accept(activity);
        minecraft.gui.setScreen(parent);
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
            g.centeredText(font, Component.translatable("partyfinder.create.activity.none"), width / 2, TOP + 12,
                    Activities.color("label", 0xA0A0A0));
        }
    }

    private class ActivityList extends ObjectSelectionList<Row> {
        ActivityList(Minecraft mc, int width, int height, int y) {
            super(mc, width, height, y, ROW_HEIGHT);
        }

        void select(Row entry) {
            setSelected(entry);
            centerScrollOn(entry);
        }

        @Override
        public int getRowWidth() {
            return Math.min(240, width - 20);
        }
    }

    /** A category header when activity is null, otherwise a pickable activity. */
    private class Row extends ObjectSelectionList.Entry<Row> {
        private final Activity activity;
        private final Component label;

        Row(Activity activity, Component label) {
            this.activity = activity;
            this.label = label;
        }

        @Override
        public Component getNarration() {
            return label;
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (activity == null) return false;
            pick(activity);
            return true;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float partialTick) {
            Font font = minecraft.font;
            int x = getContentX();
            int y = getContentY();
            int h = getContentHeight();
            int textY = y + (h - font.lineHeight) / 2 + 1;
            if (activity == null) {
                g.text(font, label, x + 2, textY, Activities.color("label", 0xA0A0A0));
                return;
            }
            if (hovered) g.blitSprite(RenderPipelines.GUI_TEXTURED, PfSprites.ENTRY_HIGHLIGHTED, x, y, getContentWidth(), h);
            int iconY = y + (h - 16) / 2;
            if (activity.sprite() != null) g.blitSprite(RenderPipelines.GUI_TEXTURED, activity.sprite(), x + 12, iconY, 16, 16);
            else g.item(activity.icon(), x + 12, iconY);
            g.text(font, label, x + 34, textY, 0xFF000000 | activity.color());
        }
    }
}
