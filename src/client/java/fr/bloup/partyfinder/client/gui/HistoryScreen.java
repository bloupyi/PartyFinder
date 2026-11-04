package fr.bloup.partyfinder.client.gui;

import fr.bloup.partyfinder.client.PartyActions;
import fr.bloup.partyfinder.client.activity.Activities;
import fr.bloup.partyfinder.client.activity.Activity;
import fr.bloup.partyfinder.client.hypixel.HypixelBridge;
import fr.bloup.partyfinder.client.net.BackendConnection;
import fr.bloup.partyfinder.client.party.Listing;
import fr.bloup.partyfinder.client.party.PartyHistory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.stream.Collectors;

/** Past finder parties: play again with the same players, republish the same listing, or rate the players. */
public class HistoryScreen extends Screen {
    private static final int TOP = 24;
    private static final int FOOTER = 36;
    private static final int MAX_WIDTH = 320;

    private final Screen parent;
    private RunList list;
    private Button playersButton;
    private Button inviteButton;
    private Button recreateButton;

    public HistoryScreen(Screen parent) {
        super(Component.translatable("partyfinder.history.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = Math.min(MAX_WIDTH, width - 20);
        int x = (width - w) / 2;
        list = addRenderableWidget(new RunList(minecraft, width, height - TOP - FOOTER, TOP, w));
        int quarter = (w - 12) / 4;
        int y = height - FOOTER + 8;
        playersButton = addRenderableWidget(Button.builder(Component.translatable("partyfinder.history.players"), b -> {
            PartyHistory.Run run = list.selected();
            if (run != null) minecraft.gui.setScreen(new RunScreen(this, run));
        }).bounds(x, y, quarter, 20).build());
        inviteButton = addRenderableWidget(Button.builder(Component.translatable("partyfinder.history.invite"), b -> invite())
                .bounds(x + quarter + 4, y, quarter, 20).build());
        recreateButton = addRenderableWidget(Button.builder(Component.translatable("partyfinder.history.recreate"), b -> {
            PartyHistory.Run run = list.selected();
            if (run == null) return;
            CreateListingScreen screen = new CreateListingScreen(this);
            screen.apply(run.draft().normalized());
            minecraft.gui.setScreen(screen);
        }).bounds(x + 2 * (quarter + 4), y, quarter, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("partyfinder.screen.back"), b -> onClose())
                .bounds(x + 3 * (quarter + 4), y, w - 3 * (quarter + 4), 20).build());
        list.replaceEntries(PartyHistory.all().stream().map(list::entry).toList());
        updateButtons();
    }

    private void invite() {
        PartyHistory.Run run = list.selected();
        if (run == null) return;
        // Players still in the Hypixel party (it usually survives a run) do not need a new invite.
        List<Listing.Player> missing = run.members().stream()
                .filter(p -> !HypixelBridge.members().contains(p.uuid()) && Listing.isValidName(p.name()))
                .toList();
        if (missing.isEmpty()) {
            PartyActions.chat(Component.translatable("partyfinder.history.invite.all_present"));
            return;
        }
        String names = missing.stream().map(Listing.Player::name).collect(Collectors.joining(", "));
        minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                PartyActions.inviteAll(missing.stream().map(Listing.Player::name).toList());
            }
            minecraft.gui.setScreen(this);
        }, Component.translatable("partyfinder.history.invite.confirm"), Component.literal(names)));
    }

    private void updateButtons() {
        if (playersButton == null) return;
        boolean selected = list.selected() != null;
        playersButton.active = selected;
        inviteButton.active = selected;
        recreateButton.active = selected && BackendConnection.isConnected();
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
            g.centeredText(font, Component.translatable("partyfinder.history.empty"), width / 2, TOP + 20, Activities.color("empty", 0xA0A0A0));
        }
    }

    private class RunList extends ObjectSelectionList<RunList.Entry> {
        private final int rowWidth;

        RunList(Minecraft mc, int width, int height, int y, int rowWidth) {
            super(mc, width, height, y, 30);
            this.rowWidth = rowWidth;
        }

        Entry entry(PartyHistory.Run run) {
            return new Entry(run);
        }

        PartyHistory.Run selected() {
            return getSelected() == null ? null : getSelected().run;
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
            private final PartyHistory.Run run;

            Entry(PartyHistory.Run run) {
                this.run = run;
            }

            @Override
            public Component getNarration() {
                return Activities.get(run.draft().activity()).name();
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
                setSelected(this);
                if (doubleClick) minecraft.gui.setScreen(new RunScreen(HistoryScreen.this, run));
                return true;
            }

            @Override
            public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float partialTick) {
                Font font = minecraft.font;
                int x = getContentX();
                int y = getContentY();
                int w = getContentWidth();
                g.blitSprite(RenderPipelines.GUI_TEXTURED, hovered ? PfSprites.ENTRY_HIGHLIGHTED : PfSprites.ENTRY, x, y, w, getContentHeight());
                Activity activity = Activities.get(run.draft().activity());
                String age = TimeFormat.ago(run.at()).getString();
                int ageWidth = font.width(age);
                g.text(font, age, x + w - 4 - ageWidth, y + 4, Activities.color("age", 0x808080));
                String title = activity.title(run.draft().options()).getString()
                        + (run.host() ? " " + Component.translatable("partyfinder.history.host").getString() : "");
                g.text(font, TimeFormat.clip(font, title, w - ageWidth - 14), x + 4, y + 4, 0xFF000000 | activity.color());
                // Players still in the Hypixel party (it usually survives a run) do not need a new invite.
        List<Listing.Player> missing = run.members().stream()
                .filter(p -> !HypixelBridge.members().contains(p.uuid()) && Listing.isValidName(p.name()))
                .toList();
        if (missing.isEmpty()) {
            PartyActions.chat(Component.translatable("partyfinder.history.invite.all_present"));
            return;
        }
        String names = missing.stream().map(Listing.Player::name).collect(Collectors.joining(", "));
                g.text(font, TimeFormat.clip(font, names, w - 8), x + 4, y + 15, Activities.color("owner", 0xAAAAAA));
            }
        }
    }

    /** Players of one past party, each with +1 and report buttons. */
    private static class RunScreen extends Screen {
        private static final int ROW = 24;

        private final Screen parent;
        private final PartyHistory.Run run;
        private int top;
        private int left;

        RunScreen(Screen parent, PartyHistory.Run run) {
            super(Activities.get(run.draft().activity()).title(run.draft().options()));
            this.parent = parent;
            this.run = run;
        }

        @Override
        protected void init() {
            List<Listing.Player> players = run.members().subList(0, Math.min(run.members().size(), 8));
            int w = Math.min(300, width - 20);
            left = (width - w) / 2;
            top = Math.max(10, height / 2 - (players.size() * ROW) / 2 - 16);
            int buttons = left + w - 2 * 64 - 4;
            boolean recent = System.currentTimeMillis() - run.at() < 24 * 60 * 60_000L;
            for (int i = 0; i < players.size(); i++) {
                Listing.Player player = players.get(i);
                int y = top + 18 + i * ROW;
                Button endorse = addRenderableWidget(Button.builder(Component.translatable("partyfinder.history.endorse"), b -> {
                    BackendConnection.endorse(player.uuid());
                    b.active = false;
                }).bounds(buttons, y, 64, 20).build());
                endorse.active = recent && BackendConnection.isConnected();
                addRenderableWidget(Button.builder(Component.translatable("partyfinder.screen.report"),
                        b -> minecraft.gui.setScreen(new ReportScreen(this, player))).bounds(buttons + 68, y, 64, 20).build())
                        .active = BackendConnection.isConnected();
            }
            addRenderableWidget(Button.builder(Component.translatable("partyfinder.screen.back"), b -> onClose())
                    .bounds(width / 2 - 75, top + 26 + players.size() * ROW, 150, 20).build());
        }

        @Override
        public void onClose() {
            minecraft.gui.setScreen(parent);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            super.extractRenderState(g, mouseX, mouseY, partialTick);
            g.centeredText(font, title, width / 2, top, Activities.color("title", 0xFFFFFF));
            List<Listing.Player> players = run.members().subList(0, Math.min(run.members().size(), 8));
            for (int i = 0; i < players.size(); i++) {
                g.text(font, players.get(i).name(), left, top + 24 + i * ROW, Activities.color("owner", 0xFFFFFF));
            }
            if (System.currentTimeMillis() - run.at() >= 24 * 60 * 60_000L) {
                g.centeredText(font, Component.translatable("partyfinder.history.endorse.expired"), width / 2,
                        top + 50 + players.size() * ROW, Activities.color("label", 0xA0A0A0));
            }
        }
    }
}
