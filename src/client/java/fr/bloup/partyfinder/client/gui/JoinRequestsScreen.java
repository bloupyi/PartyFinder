package fr.bloup.partyfinder.client.gui;

import fr.bloup.partyfinder.client.PartyActions;
import fr.bloup.partyfinder.client.activity.Activities;
import fr.bloup.partyfinder.client.net.BackendEvents;
import fr.bloup.partyfinder.client.party.PartyFinderState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;

public class JoinRequestsScreen extends Screen {
    private static final int ROW = 30;
    private static final int MAX_ROWS = 8;
    private static final int BUTTON = 54;
    private static final int MAX_WIDTH = 320;

    private final Screen parent;
    private final Runnable listener = this::rebuildWidgets;
    private List<PartyFinderState.JoinRequest> requests = List.of();
    private int top;
    private int left;
    private int rowWidth;

    public JoinRequestsScreen(Screen parent) {
        super(Component.translatable("partyfinder.requests.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        requests = PartyFinderState.joinRequests();
        int shown = Math.min(requests.size(), MAX_ROWS);
        rowWidth = Math.min(MAX_WIDTH, width - 20);
        left = (width - rowWidth) / 2;
        top = Math.max(10, height / 2 - (shown * ROW) / 2 - 12);
        int buttonsX = left + rowWidth - 3 * BUTTON - 8;
        for (int i = 0; i < shown; i++) {
            PartyFinderState.JoinRequest request = requests.get(i);
            String name = request.player().name();
            int y = top + 16 + i * ROW;
            addRenderableWidget(Button.builder(Component.translatable("partyfinder.chat.accept"),
                    b -> PartyActions.accept(name)).bounds(buttonsX, y, BUTTON, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("partyfinder.chat.deny"),
                    b -> PartyActions.deny(name)).bounds(buttonsX + BUTTON + 4, y, BUTTON, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("partyfinder.screen.report"),
                    b -> minecraft.gui.setScreen(new ReportScreen(this, request.player())))
                    .bounds(buttonsX + 2 * (BUTTON + 4), y, BUTTON, 20).build());
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 - 75, top + 24 + shown * ROW, 150, 20).build());
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

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(font, title, width / 2, top, Activities.color("title", 0xFFFFFF));
        int textRoom = rowWidth - 3 * BUTTON - 16;
        for (int i = 0; i < Math.min(requests.size(), MAX_ROWS); i++) {
            PartyFinderState.JoinRequest request = requests.get(i);
            int y = top + 17 + i * ROW;
            String badge = request.reports().badge();
            int badgeWidth = font.width(badge);
            String name = font.plainSubstrByWidth(request.player().name(), Math.max(0, textRoom - badgeWidth - 4));
            g.text(font, name, left, y, Activities.color("owner", 0xFFFFFF));
            int badgeX = left + font.width(name) + 4;
            int badgeColor = request.reports().count() > 0 ? Activities.color("reports_some", 0xFF5555) : Activities.color("reports_none", 0x55FF55);
            g.text(font, badge, badgeX, y, badgeColor);
            if (mouseX >= left && mouseX < badgeX + badgeWidth && mouseY >= y - 2 && mouseY < y + font.lineHeight + 2) {
                g.setComponentTooltipForNextFrame(font, request.reports().describe(), mouseX, mouseY);
            }
            String stats = TimeFormat.clip(font, BackendEvents.statsLine(request).getString(), textRoom);
            g.text(font, stats, left, y + font.lineHeight + 1, request.unverified()
                    ? Activities.color("status_connecting", 0xFFFF55) : Activities.color("stat", 0x55FFFF));
        }
        if (requests.isEmpty()) {
            g.centeredText(font, Component.translatable("partyfinder.requests.empty"), width / 2, top + 14,
                    Activities.color("empty", 0xA0A0A0));
        }
    }
}
