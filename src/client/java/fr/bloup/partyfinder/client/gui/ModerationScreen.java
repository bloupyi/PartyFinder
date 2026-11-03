package fr.bloup.partyfinder.client.gui;

import fr.bloup.partyfinder.client.activity.Activities;
import fr.bloup.partyfinder.client.net.BackendConnection;
import fr.bloup.partyfinder.client.party.Listing;
import fr.bloup.partyfinder.client.party.Moderation;
import fr.bloup.partyfinder.client.party.PartyFinderState;
import fr.bloup.partyfinder.client.party.ReportSummary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class ModerationScreen extends Screen {
    private static final int LIST_TOP = 48;
    private static final int FOOTER_HEIGHT = 56;
    private static final int MAX_CONTENT_WIDTH = 320;
    private static final int GAP = 4;

    public enum Sort {
        REPORTS(Comparator.comparingInt(Moderation.ReportedPlayer::count).reversed()
                .thenComparing(Comparator.comparingLong(Moderation.ReportedPlayer::lastAt).reversed())),
        RECENT(Comparator.comparingLong(Moderation.ReportedPlayer::lastAt).reversed()),
        NAME(Comparator.comparing(p -> p.name().toLowerCase(Locale.ROOT))),
        BANNED(Comparator.comparing(Moderation.ReportedPlayer::banned).reversed()
                .thenComparing(Comparator.comparingInt(Moderation.ReportedPlayer::count).reversed()));

        private final Comparator<Moderation.ReportedPlayer> comparator;

        Sort(Comparator<Moderation.ReportedPlayer> comparator) {
            this.comparator = comparator;
        }

        Component label() {
            return Component.translatable("partyfinder.mod.sort." + name().toLowerCase(Locale.ROOT));
        }
    }

    private static Sort sort = Sort.REPORTS;

    private final Screen parent;
    private final Runnable listener = this::refresh;
    private String search = "";
    private PlayerList list;
    private Button detailsButton;
    private Button banButton;
    private Button dismissButton;

    public ModerationScreen(Screen parent) {
        super(Component.translatable("partyfinder.mod.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        BackendConnection.opReports();
        int contentWidth = Math.min(MAX_CONTENT_WIDTH, width - 20);
        int left = (width - contentWidth) / 2;
        int half = (contentWidth - GAP) / 2;
        int third = (contentWidth - 2 * GAP) / 3;

        addRenderableWidget(CycleButton.builder(Sort::label, sort)
                .withValues(Sort.values())
                .create(left, 22, half, 20, Component.translatable("partyfinder.mod.sort"), (b, value) -> {
                    sort = value;
                    refresh();
                }));
        EditBox searchBox = new EditBox(font, left + half + GAP, 22, contentWidth - half - GAP, 20,
                Component.translatable("partyfinder.screen.search"));
        searchBox.setHint(Component.translatable("partyfinder.screen.search"));
        searchBox.setMaxLength(16);
        searchBox.setValue(search);
        searchBox.setResponder(value -> {
            search = value;
            refresh();
        });
        addRenderableWidget(searchBox);

        list = addRenderableWidget(new PlayerList(minecraft, width, height - LIST_TOP - FOOTER_HEIGHT, LIST_TOP));

        int row1 = height - FOOTER_HEIGHT + 6;
        int row2 = row1 + 24;
        int col2 = left + third + GAP;
        int col3 = left + 2 * (third + GAP);
        int lastWidth = contentWidth - 2 * (third + GAP);
        detailsButton = addRenderableWidget(Button.builder(Component.translatable("partyfinder.mod.details"),
                b -> openDetails(list.selected())).bounds(left, row1, third, 20).build());
        banButton = addRenderableWidget(Button.builder(Component.translatable("partyfinder.mod.ban"), b -> {
            Moderation.ReportedPlayer selected = list.selected();
            if (selected == null) return;
            if (selected.banned()) BackendConnection.opUnban(selected.uuid());
            else minecraft.gui.setScreen(new BanScreen(this, new Listing.Player(selected.uuid(), selected.name())));
        }).bounds(col2, row1, third, 20).build());
        dismissButton = addRenderableWidget(Button.builder(Component.translatable("partyfinder.mod.dismiss_all"), b -> {
            Moderation.ReportedPlayer selected = list.selected();
            if (selected != null) BackendConnection.opDismiss(selected.uuid(), null);
        }).bounds(col3, row1, lastWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("partyfinder.mod.refresh"),
                b -> BackendConnection.opReports()).bounds(left, row2, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("partyfinder.screen.back"), b -> onClose())
                .bounds(left + half + GAP, row2, contentWidth - half - GAP, 20).build());

        refresh();
    }

    private void openDetails(Moderation.ReportedPlayer player) {
        if (player != null) minecraft.gui.setScreen(new PlayerReportsScreen(this, player.uuid()));
    }

    @Override
    public void added() {
        PartyFinderState.addListener(listener);
    }

    @Override
    public void removed() {
        PartyFinderState.removeListener(listener);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    private void refresh() {
        if (list == null) return;
        if (!PartyFinderState.isOp()) {
            onClose();
            return;
        }
        String query = search.toLowerCase(Locale.ROOT);
        List<Moderation.ReportedPlayer> players = Moderation.reportedPlayers().stream()
                .filter(p -> query.isEmpty() || p.name().toLowerCase(Locale.ROOT).contains(query))
                .sorted(sort.comparator)
                .toList();
        list.setPlayers(players);
        updateButtons();
    }

    private void updateButtons() {
        if (detailsButton == null) return;
        Moderation.ReportedPlayer selected = list.selected();
        detailsButton.active = selected != null;
        banButton.active = selected != null;
        banButton.setMessage(Component.translatable(selected != null && selected.banned() ? "partyfinder.mod.unban" : "partyfinder.mod.ban"));
        dismissButton.active = selected != null && selected.count() > 0;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int contentWidth = Math.min(MAX_CONTENT_WIDTH, width - 20);
        int left = (width - contentWidth) / 2;
        String count = Component.translatable("partyfinder.mod.count", Moderation.reportedPlayers().size()).getString();
        int countWidth = font.width(count);
        g.text(font, count, left + contentWidth - countWidth, 8, Activities.color("label", 0xA0A0A0));
        g.text(font, TimeFormat.clip(font, title.getString(), contentWidth - countWidth - 8), left, 8, Activities.color("title", 0xFFFFFF));
        if (list.children().isEmpty()) {
            g.centeredText(font, Component.translatable("partyfinder.mod.empty"), width / 2, LIST_TOP + 20, Activities.color("empty", 0xA0A0A0));
        }
    }

    static String topReasons(Map<String, Integer> reasons) {
        return reasons.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .map(e -> ReportSummary.reasonName(e.getKey()).getString() + " x" + e.getValue())
                .collect(Collectors.joining(", "));
    }

    private class PlayerList extends ObjectSelectionList<PlayerList.Entry> {
        PlayerList(Minecraft mc, int width, int height, int y) {
            super(mc, width, height, y, 28);
        }

        void setPlayers(List<Moderation.ReportedPlayer> players) {
            UUID selectedId = selected() != null ? selected().uuid() : null;
            List<Entry> entries = new ArrayList<>();
            for (Moderation.ReportedPlayer player : players) entries.add(new Entry(player));
            replaceEntries(entries);
            entries.stream().filter(e -> e.player.uuid().equals(selectedId)).findFirst().ifPresent(this::setSelected);
        }

        Moderation.ReportedPlayer selected() {
            return getSelected() != null ? getSelected().player : null;
        }

        @Override
        public void setSelected(Entry entry) {
            super.setSelected(entry);
            updateButtons();
        }

        @Override
        public int getRowWidth() {
            return Math.min(MAX_CONTENT_WIDTH, width - 20);
        }

        class Entry extends ObjectSelectionList.Entry<Entry> {
            private final Moderation.ReportedPlayer player;

            Entry(Moderation.ReportedPlayer player) {
                this.player = player;
            }

            @Override
            public Component getNarration() {
                return Component.literal(player.name());
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
                setSelected(this);
                if (doubleClick) openDetails(player);
                return true;
            }

            @Override
            public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float partialTick) {
                Font font = minecraft.font;
                int x = getContentX();
                int y = getContentY();
                int w = getContentWidth();
                int h = getContentHeight();
                g.blitSprite(RenderPipelines.GUI_TEXTURED, hovered ? PfSprites.ENTRY_HIGHLIGHTED : PfSprites.ENTRY, x, y, w, h);
                int textX = x + 4;
                int right = x + w - 4;
                int line1 = y + (h - 2 * font.lineHeight - 2) / 2;
                int line2 = line1 + font.lineHeight + 2;

                String badge = Component.translatable("partyfinder.report.badge", player.count()).getString();
                int badgeWidth = font.width(badge);
                g.text(font, badge, right - badgeWidth, line1, player.count() > 0
                        ? Activities.color("reports_some", 0xFF5555) : Activities.color("reports_none", 0x55FF55));

                int nameRoom = right - badgeWidth - 6 - textX;
                String banned = player.banned() ? Component.translatable("partyfinder.mod.banned_tag").getString() : "";
                String name = TimeFormat.clip(font, player.name(), nameRoom - (banned.isEmpty() ? 0 : font.width(banned) + 4));
                g.text(font, name, textX, line1, Activities.color("owner", 0xFFFFFF));
                if (!banned.isEmpty()) g.text(font, banned, textX + font.width(name) + 4, line1, Activities.color("banned", 0xAA0000));

                String age = TimeFormat.ago(player.lastAt()).getString();
                int ageWidth = font.width(age);
                g.text(font, age, right - ageWidth, line2, Activities.color("age", 0x808080));
                g.text(font, TimeFormat.clip(font, topReasons(player.reasons()), right - ageWidth - 6 - textX), textX, line2,
                        Activities.color("note", 0xE0E0E0));

                if (hovered) g.setComponentTooltipForNextFrame(font, player.summary().describe(), mouseX, mouseY);
            }
        }
    }
}
