package fr.bloup.partyfinder.client.gui;

import fr.bloup.partyfinder.client.activity.Activities;
import fr.bloup.partyfinder.client.activity.ListingMenu;
import fr.bloup.partyfinder.client.activity.MenuLayout;
import fr.bloup.partyfinder.client.activity.Theme;
import fr.bloup.partyfinder.client.net.BackendConnection;
import fr.bloup.partyfinder.client.party.Listing;
import fr.bloup.partyfinder.client.party.PartyFinderState;
import fr.bloup.partyfinder.client.party.PartySync;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Listings as a Hypixel Party Finder style chest: one head per party (its leader's), the same lore as the list style
 * on hover, click to join, right-click to report. Purely client-side: nothing here talks to the Minecraft server.
 */
public class ChestListingScreen extends Screen {
    private static final Identifier SLOT_HIGHLIGHT_BACK = Identifier.withDefaultNamespace("container/slot_highlight_back");
    private static final Identifier SLOT_HIGHLIGHT_FRONT = Identifier.withDefaultNamespace("container/slot_highlight_front");
    private static final int IMAGE_WIDTH = 176;
    private static final Map<UUID, ItemStack> HEADS = new HashMap<>();

    private final Screen parent;
    private final String category;
    private final Set<String> activities;
    private ListingMenu menu;
    private boolean soundPlayed;
    private int left;
    private int top;
    private int page;
    private long shownRevision = -1;
    private List<Listing> listings = List.of();

    public ChestListingScreen(Screen parent, String category, Set<String> activities, Component title) {
        super(title);
        this.parent = parent;
        this.category = category;
        this.activities = activities;
    }

    @Override
    protected void init() {
        Activities.reload();
        BackendConnection.connect();
        menu = ListingMenu.load(MenuLayout.load());
        if (!soundPlayed && menu.openSound() != null) {
            soundPlayed = true;
            menu.openSound().play();
        }
        int imageHeight = menu.rows() * 18 + 24;
        left = (width - IMAGE_WIDTH) / 2;
        top = Math.max(4, (height - imageHeight) / 2);
        refresh();
    }

    /** Follows the listings live, like the list style. */
    @Override
    public void tick() {
        if (PartyFinderState.revision() != shownRevision) refresh();
    }

    private void refresh() {
        shownRevision = PartyFinderState.revision();
        listings = PartyFinderScreen.filter(category, activities, "");
        page = Math.clamp(page, 0, pages() - 1);
    }

    private int pages() {
        int perPage = menu.listingSlots().size();
        return Math.max(1, (listings.size() + perPage - 1) / perPage);
    }

    /** Position of this slot among the listing slots of listing_menu.json; -1 for the others. */
    private int innerIndex(int slot) {
        return menu.listingSlots().indexOf(slot);
    }

    private Listing listingAt(int slot) {
        int inner = innerIndex(slot);
        if (inner < 0) return null;
        int index = page * menu.listingSlots().size() + inner;
        return index < listings.size() ? listings.get(index) : null;
    }

    private static ItemStack head(UUID uuid) {
        return HEADS.computeIfAbsent(uuid, id -> {
            ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
            stack.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(id));
            return stack;
        });
    }

    /** The listing's icon: its leader's head or its activity icon, glinting for your own listing. */
    private ItemStack listingIcon(Listing listing) {
        ItemStack icon = menu.headIcons() ? head(listing.owner().uuid()) : Activities.get(listing.activity()).icon();
        if (!menu.ownGlint() || !PartyFinderState.isSelf(listing.owner())) return icon;
        ItemStack stack = icon.copy();
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    /** Button at this slot when it is usable right now, or null. */
    private ListingMenu.Button button(int slot) {
        ListingMenu.Button button = menu.buttonAt(slot).orElse(null);
        if (button == null) return null;
        boolean online = BackendConnection.isConnected();
        boolean shown = switch (button) {
            case PREVIOUS -> page > 0;
            case NEXT -> page < pages() - 1;
            case BACK, CLOSE -> true;
            case CREATE -> online;
            case REQUESTS -> !PartyFinderState.joinRequests().isEmpty();
            case LEAVE -> online && (PartyFinderState.ownListing().isPresent() || PartySync.joinedListing().isPresent());
        };
        return shown ? button : null;
    }

    private List<Component> buttonTooltip(ListingMenu.Button button) {
        List<Component> lines = new ArrayList<>();
        switch (button) {
            case PREVIOUS, NEXT -> {
                lines.add(Component.translatable(button == ListingMenu.Button.NEXT ? "partyfinder.chest.next" : "partyfinder.chest.previous")
                        .withStyle(ChatFormatting.GREEN));
                lines.add(Component.translatable("partyfinder.chest.page", page + 1, pages()).withStyle(ChatFormatting.GRAY));
            }
            case BACK -> lines.add(Component.translatable("partyfinder.screen.back").withStyle(ChatFormatting.GREEN));
            case CLOSE -> lines.add(Component.translatable("partyfinder.menu.close").withStyle(ChatFormatting.RED));
            case CREATE -> {
                boolean own = PartyFinderState.ownListing().isPresent();
                lines.add(Component.translatable(own ? "partyfinder.screen.edit" : "partyfinder.screen.create").withStyle(ChatFormatting.GREEN));
                lines.add(Component.translatable("partyfinder.menu.create.desc").withStyle(ChatFormatting.GRAY));
            }
            case REQUESTS -> lines.add(Component.translatable("partyfinder.screen.requests", PartyFinderState.joinRequests().size())
                    .withStyle(ChatFormatting.GREEN));
            case LEAVE -> lines.add(Component.translatable(PartySync.joinedListing().isPresent()
                    ? "partyfinder.screen.leave" : "partyfinder.screen.delist").withStyle(ChatFormatting.RED));
        }
        return lines;
    }

    /** The shared listing lore plus what a click does here. */
    private static List<Component> listingTooltip(Listing listing) {
        List<Component> lines = new ArrayList<>(ListingList.tooltip(listing));
        lines.add(Component.empty());
        if (PartyFinderState.isSelf(listing.owner())) {
            lines.add(Component.translatable("partyfinder.chest.click_edit").withStyle(ChatFormatting.YELLOW));
            return lines;
        }
        if (listing.isFull()) {
            lines.add(Component.translatable("partyfinder.chest.full").withStyle(ChatFormatting.RED));
        } else if (!listing.accepts(PartyFinderState.selfReports())) {
            lines.add(Component.translatable("partyfinder.screen.join.blocked", PartyFinderState.selfReports().count(), listing.maxReports())
                    .withStyle(ChatFormatting.RED));
        } else {
            lines.add(Component.translatable("partyfinder.chest.click_join").withStyle(ChatFormatting.YELLOW));
        }
        lines.add(Component.translatable("partyfinder.chest.right_click_report").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int slot = slotAt(event.x(), event.y());
        if (slot < 0) return super.mouseClicked(event, doubleClick);
        Listing listing = listingAt(slot);
        if (listing != null) {
            AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
            if (PartyFinderState.isSelf(listing.owner())) {
                minecraft.gui.setScreen(new CreateListingScreen(this));
            } else if (event.button() == 1) {
                minecraft.gui.setScreen(new ReportScreen(this, listing.owner()));
            } else {
                PartyFinderScreen.join(listing);
            }
            return true;
        }
        ListingMenu.Button button = button(slot);
        if (button == null) return true;
        AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
        switch (button) {
            case PREVIOUS -> page--;
            case NEXT -> page++;
            case BACK -> onClose();
            case CLOSE -> minecraft.gui.setScreen(null);
            case CREATE -> minecraft.gui.setScreen(new CreateListingScreen(this));
            case REQUESTS -> minecraft.gui.setScreen(new JoinRequestsScreen(this));
            case LEAVE -> {
                if (!PartySync.leave()) BackendConnection.delist();
            }
        }
        return true;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Container textures go in the background stratum, like the category menu. */
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        int topHeight = menu.rows() * 18 + 17;
        g.blit(RenderPipelines.GUI_TEXTURED, menu.texture(), left, top, 0, 0, IMAGE_WIDTH, topHeight, 256, 256);
        g.blit(RenderPipelines.GUI_TEXTURED, menu.texture(), left, top + topHeight, 0, 215, IMAGE_WIDTH, 7, 256, 256);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int titleColor = Activities.color("menu_title", 0x404040);
        PartyFinderState.Status status = PartyFinderState.status();
        Component statusText = Component.translatable("partyfinder.status." + status.name().toLowerCase(Locale.ROOT));
        int statusWidth = font.width(statusText);
        String titleText = TimeFormat.clip(font, title.getString(), IMAGE_WIDTH - 16 - statusWidth - 4);
        g.text(font, titleText, left + 8, top + 6, titleColor, false);
        g.text(font, statusText, left + IMAGE_WIDTH - 8 - statusWidth, top + 6, statusColor(status), false);

        int hovered = slotAt(mouseX, mouseY);
        List<Component> tooltip = null;
        Component fullMarker = Component.translatable("partyfinder.symbol.full");
        for (int slot = 0; slot < menu.rows() * 9; slot++) {
            int x = slotX(slot);
            int y = slotY(slot);
            Listing listing = listingAt(slot);
            ListingMenu.Button button = listing == null ? button(slot) : null;
            boolean empty = listings.isEmpty() && slot == menu.emptySlot();
            ItemStack icon = listing != null ? listingIcon(listing)
                    : button != null ? menu.buttons().get(button).icon()
                    : empty ? menu.emptyIcon()
                    : innerIndex(slot) < 0 ? menu.filler() : ItemStack.EMPTY;
            boolean active = listing != null || button != null || empty;
            if (slot == hovered && active) g.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_BACK, x - 4, y - 4, 24, 24);
            if (!icon.isEmpty()) g.item(icon, x, y);
            if (menu.fullMarker() && listing != null && listing.isFull()) {
                g.text(font, fullMarker, x + 17 - font.width(fullMarker), y + 9, Theme.color("full_marker", 0xFF5555), true);
            }
            if (slot == hovered && active) {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_FRONT, x - 4, y - 4, 24, 24);
                tooltip = listing != null ? listingTooltip(listing)
                        : button != null ? buttonTooltip(button)
                        : List.of(Component.translatable(status == PartyFinderState.Status.ONLINE
                        ? "partyfinder.screen.empty" : "partyfinder.screen.offline").withStyle(ChatFormatting.GRAY));
            }
        }
        if (tooltip != null) g.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
    }

    private int slotAt(double mouseX, double mouseY) {
        for (int slot = 0; slot < menu.rows() * 9; slot++) {
            int x = slotX(slot);
            int y = slotY(slot);
            if (mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17) return slot;
        }
        return -1;
    }

    private int slotX(int slot) {
        return left + 8 + (slot % 9) * 18;
    }

    private int slotY(int slot) {
        return top + 18 + (slot / 9) * 18;
    }

    private static int statusColor(PartyFinderState.Status status) {
        return switch (status) {
            case ONLINE -> Activities.color("menu_status_online", 0x00AA00);
            case CONNECTING -> Activities.color("menu_status_connecting", 0xAA8800);
            case OFFLINE -> Activities.color("menu_status_offline", 0xAA0000);
        };
    }
}
