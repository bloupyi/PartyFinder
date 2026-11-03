package fr.bloup.partyfinder.client.gui;

import fr.bloup.partyfinder.client.activity.Activities;
import fr.bloup.partyfinder.client.net.BackendConnection;
import fr.bloup.partyfinder.client.party.Moderation;
import fr.bloup.partyfinder.client.party.PartyFinderState;
import fr.bloup.partyfinder.client.party.ReportSummary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class PlayerReportsScreen extends Screen {
    private static final int LIST_TOP = 36;
    private static final int FOOTER_HEIGHT = 56;
    private static final int MAX_CONTENT_WIDTH = 320;
    private static final int GAP = 4;

    private final Screen parent;
    private final UUID uuid;
    private final Runnable listener = this::refresh;
    private ReportList list;
    private Button banButton;
    private Button dismissButton;
    private Button dismissAllButton;

    public PlayerReportsScreen(Screen parent, UUID uuid) {
        super(Component.translatable("partyfinder.mod.details"));
        this.parent = parent;
        this.uuid = uuid;
    }

    private Moderation.PlayerDetail detail() {
        Moderation.PlayerDetail detail = Moderation.detail();
        return detail != null && detail.player().uuid().equals(uuid) ? detail : null;
    }

    @Override
    protected void init() {
        if (detail() == null) Moderation.setDetail(null);
        BackendConnection.opPlayer(uuid);
        int contentWidth = Math.min(MAX_CONTENT_WIDTH, width - 20);
        int left = (width - contentWidth) / 2;
        int half = (contentWidth - GAP) / 2;

        list = addRenderableWidget(new ReportList(minecraft, width, height - LIST_TOP - FOOTER_HEIGHT, LIST_TOP));

        int row1 = height - FOOTER_HEIGHT + 6;
        int row2 = row1 + 24;
        banButton = addRenderableWidget(Button.builder(Component.translatable("partyfinder.mod.ban"), b -> {
            Moderation.PlayerDetail detail = detail();
            if (detail == null) return;
            if (detail.banned()) BackendConnection.opUnban(uuid);
            else minecraft.gui.setScreen(new BanScreen(this, detail.player()));
        }).bounds(left, row1, half, 20).build());
        dismissButton = addRenderableWidget(Button.builder(Component.translatable("partyfinder.mod.dismiss"), b -> {
            Moderation.Report selected = list.selected();
            if (selected != null) BackendConnection.opDismiss(uuid, selected.id());
        }).bounds(left + half + GAP, row1, contentWidth - half - GAP, 20).build());
        dismissAllButton = addRenderableWidget(Button.builder(Component.translatable("partyfinder.mod.dismiss_all"),
                b -> BackendConnection.opDismiss(uuid, null)).bounds(left, row2, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("partyfinder.screen.back"), b -> onClose())
                .bounds(left + half + GAP, row2, contentWidth - half - GAP, 20).build());

        refresh();
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
        Moderation.PlayerDetail detail = detail();
        list.setReports(detail == null ? List.of() : detail.reports());
        updateButtons();
    }

    private void updateButtons() {
        if (banButton == null) return;
        Moderation.PlayerDetail detail = detail();
        boolean hasReports = detail != null && !detail.reports().isEmpty();
        banButton.active = detail != null;
        banButton.setMessage(Component.translatable(detail != null && detail.banned() ? "partyfinder.mod.unban" : "partyfinder.mod.ban"));
        dismissButton.active = list.selected() != null;
        dismissAllButton.active = hasReports;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int contentWidth = Math.min(MAX_CONTENT_WIDTH, width - 20);
        int left = (width - contentWidth) / 2;
        Moderation.PlayerDetail detail = detail();
        if (detail == null) {
            g.centeredText(font, Component.translatable("partyfinder.status.connecting"), width / 2, 10, Activities.color("label", 0xA0A0A0));
            return;
        }
        String badge = Component.translatable("partyfinder.report.badge", detail.reports().size()).getString();
        int badgeWidth = font.width(badge);
        g.text(font, badge, left + contentWidth - badgeWidth, 8, detail.reports().isEmpty()
                ? Activities.color("reports_none", 0x55FF55) : Activities.color("reports_some", 0xFF5555));
        g.text(font, TimeFormat.clip(font, detail.player().name(), contentWidth - badgeWidth - 8), left, 8, Activities.color("title", 0xFFFFFF));
        if (detail.banned() && detail.ban() != null) {
            Component ban = Component.translatable("partyfinder.mod.banned_info", detail.ban().by(), TimeFormat.ago(detail.ban().at()),
                    detail.ban().reason().isEmpty() ? "-" : detail.ban().reason());
            g.text(font, TimeFormat.clip(font, ban.getString(), contentWidth), left, 20, Activities.color("banned", 0xAA0000));
        }
        if (list.children().isEmpty()) {
            g.centeredText(font, Component.translatable("partyfinder.mod.no_reports"), width / 2, LIST_TOP + 20, Activities.color("empty", 0xA0A0A0));
        }
    }

    private class ReportList extends ObjectSelectionList<ReportList.Entry> {
        ReportList(Minecraft mc, int width, int height, int y) {
            super(mc, width, height, y, 28);
        }

        void setReports(List<Moderation.Report> reports) {
            String selectedId = selected() != null ? selected().id() : null;
            List<Entry> entries = new ArrayList<>();
            reports.stream().sorted(Comparator.comparingLong(Moderation.Report::at).reversed()).forEach(r -> entries.add(new Entry(r)));
            replaceEntries(entries);
            entries.stream().filter(e -> e.report.id().equals(selectedId)).findFirst().ifPresent(this::setSelected);
        }

        Moderation.Report selected() {
            return getSelected() != null ? getSelected().report : null;
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
            private final Moderation.Report report;

            Entry(Moderation.Report report) {
                this.report = report;
            }

            @Override
            public Component getNarration() {
                return ReportSummary.reasonName(report.reason());
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
                setSelected(this);
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

                String age = TimeFormat.ago(report.at()).getString();
                int ageWidth = font.width(age);
                g.text(font, age, right - ageWidth, line1, Activities.color("age", 0x808080));
                String reason = ReportSummary.reasonName(report.reason()).getString();
                String head = TimeFormat.clip(font, reason, right - ageWidth - 6 - textX);
                g.text(font, head, textX, line1, Activities.color("reports_some", 0xFF5555));
                int byX = textX + font.width(head) + 6;
                String by = Component.translatable("partyfinder.mod.by", report.reporter().name()).getString();
                g.text(font, TimeFormat.clip(font, by, right - ageWidth - 6 - byX), byX, line1, Activities.color("owner", 0xAAAAAA));

                String comment = report.comment().isEmpty() ? Component.translatable("partyfinder.mod.no_comment").getString() : report.comment();
                g.text(font, TimeFormat.clip(font, comment, right - textX), textX, line2, Activities.color("note", 0xE0E0E0));

                if (hovered && font.width(comment) > right - textX) {
                    g.setComponentTooltipForNextFrame(font, List.of(Component.literal(comment)), mouseX, mouseY);
                }
            }
        }
    }
}
