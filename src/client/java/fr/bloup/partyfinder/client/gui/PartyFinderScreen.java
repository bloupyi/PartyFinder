package fr.bloup.partyfinder.client.gui;

import fr.bloup.partyfinder.client.PartyActions;
import fr.bloup.partyfinder.client.activity.Activities;
import fr.bloup.partyfinder.client.activity.Activity;
import fr.bloup.partyfinder.client.net.BackendConnection;
import fr.bloup.partyfinder.client.party.Listing;
import fr.bloup.partyfinder.client.party.PartyFinderState;
import fr.bloup.partyfinder.client.party.PartySync;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

public class PartyFinderScreen extends Screen {
    private static final int LIST_TOP = 48;
    private static final int FOOTER_HEIGHT = 56;
    private static final int MAX_CONTENT_WIDTH = 320;
    private static final int GAP = 4;

    private final Screen parent;
    private final String category;
    private final Runnable listener = this::refresh;

    private String search = "";
    private ListingList list;
    private Button joinButton;
    private Button ownButton;
    private Button delistButton;
    private Button requestsButton;
    private Button reportButton;

    public PartyFinderScreen(Screen parent, String category) {
        super(category.isEmpty()
                ? Component.translatable("partyfinder.category.all")
                : Component.translatable("partyfinder.category." + category));
        this.parent = parent;
        this.category = category;
    }

    @Override
    protected void init() {
        Activities.reload();
        BackendConnection.connect();

        int contentWidth = Math.min(MAX_CONTENT_WIDTH, width - 20);
        int left = (width - contentWidth) / 2;
        int half = (contentWidth - GAP) / 2;
        int third = (contentWidth - 2 * GAP) / 3;

        addRenderableWidget(Button.builder(Component.translatable("partyfinder.screen.back"), b -> onClose())
                .bounds(left, 22, half, 20).build());

        EditBox searchBox = new EditBox(font, left + half + GAP, 22, contentWidth - half - GAP, 20,
                Component.translatable("partyfinder.screen.search"));
        searchBox.setHint(Component.translatable("partyfinder.screen.search"));
        searchBox.setMaxLength(32);
        searchBox.setValue(search);
        searchBox.setResponder(value -> {
            search = value;
            refresh();
        });
        addRenderableWidget(searchBox);

        list = addRenderableWidget(new ListingList(minecraft, width, height - LIST_TOP - FOOTER_HEIGHT, LIST_TOP,
                this::join, this::updateButtons));

        int row1 = height - FOOTER_HEIGHT + 6;
        int row2 = row1 + 24;
        int col2 = left + third + GAP;
        int col3 = left + 2 * (third + GAP);
        int lastWidth = contentWidth - 2 * (third + GAP);
        joinButton = addRenderableWidget(Button.builder(Component.translatable("partyfinder.screen.join"),
                b -> join(list.selectedListing())).bounds(left, row1, third, 20).build());
        ownButton = addRenderableWidget(Button.builder(Component.empty(),
                b -> minecraft.gui.setScreen(new CreateListingScreen(this))).bounds(col2, row1, third, 20).build());
        delistButton = addRenderableWidget(Button.builder(Component.translatable("partyfinder.screen.delist"),
                b -> {
                    if (!PartySync.leave()) BackendConnection.delist();
                }).bounds(col3, row1, lastWidth, 20).build());
        requestsButton = addRenderableWidget(Button.builder(Component.empty(),
                b -> minecraft.gui.setScreen(new JoinRequestsScreen(this))).bounds(left, row2, third, 20).build());
        reportButton = addRenderableWidget(Button.builder(Component.translatable("partyfinder.screen.report"), b -> {
            Listing selected = list.selectedListing();
            if (selected != null) minecraft.gui.setScreen(new ReportScreen(this, selected.owner()));
        }).bounds(col2, row2, third, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> minecraft.gui.setScreen(null))
                .bounds(col3, row2, lastWidth, 20).build());

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
        String query = search.toLowerCase(Locale.ROOT);
        List<Listing> filtered = PartyFinderState.listings().stream().filter(l -> {
            Activity activity = Activities.get(l.activity());
            if (!category.isEmpty() && !activity.category().equals(category)) return false;
            if (query.isEmpty()) return true;
            return activity.name().getString().toLowerCase(Locale.ROOT).contains(query)
                    || l.owner().name().toLowerCase(Locale.ROOT).contains(query)
                    || l.note().toLowerCase(Locale.ROOT).contains(query)
                    || l.requirements().toLowerCase(Locale.ROOT).contains(query);
        }).toList();
        list.setListings(filtered);
        updateButtons();
    }

    private void updateButtons() {
        if (joinButton == null) return;
        boolean online = BackendConnection.isConnected();
        Listing selected = list.selectedListing();
        boolean hasOwn = PartyFinderState.ownListing().isPresent();
        boolean selectedIsOwn = selected != null && PartyFinderState.self()
                .map(s -> s.uuid().equals(selected.owner().uuid())).orElse(false);
        boolean blocked = selected != null && !selected.accepts(PartyFinderState.selfReports());
        joinButton.active = online && selected != null && !selected.isFull() && !selectedIsOwn && !blocked;
        joinButton.setTooltip(blocked ? Tooltip.create(Component.translatable("partyfinder.screen.join.blocked",
                PartyFinderState.selfReports().count(), selected.maxReports())) : null);
        reportButton.active = online && selected != null && !selectedIsOwn;
        ownButton.active = online;
        ownButton.setMessage(Component.translatable(hasOwn ? "partyfinder.screen.edit" : "partyfinder.screen.create"));
        boolean joined = PartySync.joinedListing().isPresent();
        delistButton.active = online && (hasOwn || joined);
        delistButton.setMessage(Component.translatable(joined ? "partyfinder.screen.leave" : "partyfinder.screen.delist"));
        int requests = PartyFinderState.joinRequests().size();
        requestsButton.active = requests > 0;
        requestsButton.setMessage(Component.translatable("partyfinder.screen.requests", requests));
    }

    private void join(Listing listing) {
        if (listing == null || !BackendConnection.isConnected() || listing.isFull()) return;
        if (!listing.accepts(PartyFinderState.selfReports())) return;
        if (PartyFinderState.self().map(s -> s.uuid().equals(listing.owner().uuid())).orElse(false)) return;
        PartyActions.requestJoin(listing);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int contentWidth = Math.min(MAX_CONTENT_WIDTH, width - 20);
        int left = (width - contentWidth) / 2;

        PartyFinderState.Status status = PartyFinderState.status();
        Component statusText = Component.translatable("partyfinder.status." + status.name().toLowerCase(Locale.ROOT));
        int statusWidth = font.width(statusText);
        int statusColor = switch (status) {
            case ONLINE -> Activities.color("status_online", 0x55FF55);
            case CONNECTING -> Activities.color("status_connecting", 0xFFFF55);
            case OFFLINE -> Activities.color("status_offline", 0xFF5555);
        };
        g.text(font, statusText, left + contentWidth - statusWidth, 8, statusColor);

        Component screenTitle = Component.translatable("partyfinder.screen.title").append(" - ").append(title);
        String titleText = font.plainSubstrByWidth(screenTitle.getString(), contentWidth - statusWidth - 8);
        g.text(font, titleText, left, 8, Activities.color("title", 0xFFFFFF));

        if (list.children().isEmpty()) {
            Component empty = Component.translatable(status == PartyFinderState.Status.ONLINE
                    ? "partyfinder.screen.empty" : "partyfinder.screen.offline");
            String text = font.plainSubstrByWidth(empty.getString(), contentWidth);
            g.centeredText(font, text, width / 2, LIST_TOP + 20, Activities.color("empty", 0xA0A0A0));
        }
    }
}
