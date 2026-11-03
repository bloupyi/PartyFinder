package fr.bloup.partyfinder.client.gui;

import fr.bloup.partyfinder.client.activity.Activities;
import fr.bloup.partyfinder.client.activity.MenuLayout;
import fr.bloup.partyfinder.client.hud.HudConfigScreen;
import fr.bloup.partyfinder.client.net.BackendConnection;
import fr.bloup.partyfinder.client.party.Listing;
import fr.bloup.partyfinder.client.party.Moderation;
import fr.bloup.partyfinder.client.party.PartyFinderState;
import fr.bloup.partyfinder.client.party.PartySync;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CategoryMenuScreen extends Screen {
    private static final Identifier SLOT_HIGHLIGHT_BACK = Identifier.withDefaultNamespace("container/slot_highlight_back");
    private static final Identifier SLOT_HIGHLIGHT_FRONT = Identifier.withDefaultNamespace("container/slot_highlight_front");
    private static final int IMAGE_WIDTH = 176;

    private final Screen parent;
    private MenuLayout layout;
    private int left;
    private int top;
    private int imageHeight;

    public CategoryMenuScreen(Screen parent) {
        super(Component.translatable("partyfinder.screen.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Activities.reload();
        layout = MenuLayout.load();
        BackendConnection.connect();
        if (PartyFinderState.isOp()) BackendConnection.opReports();
        imageHeight = layout.rows() * 18 + 24;
        left = (width - IMAGE_WIDTH) / 2;
        top = (height - imageHeight) / 2;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int topHeight = layout.rows() * 18 + 17;
        g.blit(RenderPipelines.GUI_TEXTURED, layout.texture(), left, top, 0, 0, IMAGE_WIDTH, topHeight, 256, 256);
        g.blit(RenderPipelines.GUI_TEXTURED, layout.texture(), left, top + topHeight, 0, 215, IMAGE_WIDTH, 7, 256, 256);

        int titleColor = Activities.color("menu_title", 0x404040);
        g.text(font, title, left + 8, top + 6, titleColor, false);
        PartyFinderState.Status status = PartyFinderState.status();
        Component statusText = Component.translatable("partyfinder.status." + status.name().toLowerCase(Locale.ROOT));
        g.text(font, statusText, left + IMAGE_WIDTH - 8 - font.width(statusText), top + 6, statusColor(status), false);

        MenuLayout.Slot hovered = null;
        int hoveredIndex = slotAt(mouseX, mouseY);
        for (int i = 0; i < layout.rows() * 9; i++) {
            int x = slotX(i);
            int y = slotY(i);
            MenuLayout.Slot slot = slot(i);
            if (i == hoveredIndex && slot != null) {
                hovered = slot;
                g.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_BACK, x - 4, y - 4, 24, 24);
            }
            if (slot != null) {
                g.item(slot.icon(), x, y);
                int count = count(slot);
                if (count > 0) g.itemDecorations(font, slot.icon(), x, y, String.valueOf(Math.min(count, 99)));
            } else if (!layout.filler().isEmpty()) {
                g.item(layout.filler(), x, y);
            }
            if (i == hoveredIndex && slot != null) {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_FRONT, x - 4, y - 4, 24, 24);
            }
        }

        if (hovered != null) g.setComponentTooltipForNextFrame(font, tooltip(hovered), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        MenuLayout.Slot slot = slot(slotAt(event.x(), event.y()));
        if (slot == null) return super.mouseClicked(event, doubleClick);
        AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
        switch (slot.action()) {
            case CATEGORY -> minecraft.gui.setScreen(new PartyFinderScreen(this, slot.category()));
            case ALL -> minecraft.gui.setScreen(new PartyFinderScreen(this, ""));
            case CREATE -> {
                if (BackendConnection.isConnected()) minecraft.gui.setScreen(new CreateListingScreen(this));
            }
            case REQUESTS -> minecraft.gui.setScreen(new JoinRequestsScreen(this));
            case MODERATION -> minecraft.gui.setScreen(new ModerationScreen(this));
            case RECONNECT -> BackendConnection.reconnect();
            case HUD -> minecraft.gui.setScreen(new HudConfigScreen(this));
            case SETTINGS -> minecraft.gui.setScreen(new SettingsScreen(this));
            case LAUNCH -> {
                if (PartySync.launch()) onClose();
            }
            case CLOSE -> onClose();
        }
        return true;
    }

    private List<Component> tooltip(MenuLayout.Slot slot) {
        List<Component> lines = new ArrayList<>();
        switch (slot.action()) {
            case CATEGORY -> {
                lines.add(Component.translatable("partyfinder.category." + slot.category()).withStyle(ChatFormatting.GREEN));
                lines.add(Component.translatable("partyfinder.menu.category.desc").withStyle(ChatFormatting.GRAY));
                lines.add(Component.empty());
                lines.add(Component.translatable("partyfinder.menu.listings", count(slot)).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("partyfinder.menu.click_browse").withStyle(ChatFormatting.YELLOW));
            }
            case ALL -> {
                lines.add(Component.translatable("partyfinder.category.all").withStyle(ChatFormatting.GREEN));
                lines.add(Component.translatable("partyfinder.menu.all.desc").withStyle(ChatFormatting.GRAY));
                lines.add(Component.empty());
                lines.add(Component.translatable("partyfinder.menu.listings", count(slot)).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("partyfinder.menu.click_browse").withStyle(ChatFormatting.YELLOW));
            }
            case CREATE -> {
                boolean hasOwn = PartyFinderState.ownListing().isPresent();
                lines.add(Component.translatable(hasOwn ? "partyfinder.screen.edit" : "partyfinder.screen.create").withStyle(ChatFormatting.GREEN));
                lines.add(Component.translatable("partyfinder.menu.create.desc").withStyle(ChatFormatting.GRAY));
                if (!BackendConnection.isConnected()) {
                    lines.add(Component.empty());
                    lines.add(Component.translatable("partyfinder.screen.offline").withStyle(ChatFormatting.RED));
                }
            }
            case REQUESTS -> {
                lines.add(Component.translatable("partyfinder.requests.title").withStyle(ChatFormatting.GREEN));
                lines.add(Component.translatable("partyfinder.menu.requests.desc").withStyle(ChatFormatting.GRAY));
                lines.add(Component.empty());
                lines.add(Component.translatable("partyfinder.menu.pending", count(slot)).withStyle(ChatFormatting.GRAY));
            }
            case MODERATION -> {
                lines.add(Component.translatable("partyfinder.mod.title").withStyle(ChatFormatting.GOLD));
                lines.add(Component.translatable("partyfinder.menu.moderation.desc").withStyle(ChatFormatting.GRAY));
                lines.add(Component.empty());
                lines.add(Component.translatable("partyfinder.mod.count", count(slot)).withStyle(ChatFormatting.GRAY));
            }
            case RECONNECT -> {
                PartyFinderState.Status status = PartyFinderState.status();
                lines.add(Component.translatable("partyfinder.screen.reconnect").withStyle(ChatFormatting.GREEN));
                lines.add(Component.translatable("partyfinder.status." + status.name().toLowerCase(Locale.ROOT)).withColor(statusColor(status)));
                lines.add(Component.empty());
                lines.add(PartyFinderState.selfReports().describe().getFirst());
            }
            case SETTINGS -> {
                lines.add(Component.translatable("partyfinder.settings.title").withStyle(ChatFormatting.GREEN));
                lines.add(Component.translatable("partyfinder.menu.settings.desc").withStyle(ChatFormatting.GRAY));
            }
            case HUD -> {
                lines.add(Component.translatable("partyfinder.hud.title").withStyle(ChatFormatting.GREEN));
                lines.add(Component.translatable("partyfinder.menu.hud.desc").withStyle(ChatFormatting.GRAY));
            }
            case LAUNCH -> {
                lines.add(Component.translatable("partyfinder.menu.launch").withStyle(ChatFormatting.GREEN));
                PartySync.currentListing().ifPresentOrElse(listing -> {
                    lines.add(Activities.get(listing.activity()).title(listing.options()).copy().withStyle(ChatFormatting.GRAY));
                    lines.add(Component.translatable("partyfinder.listing.members", listing.size(), listing.maxSize()).withStyle(ChatFormatting.GRAY));
                    lines.add(Component.empty());
                    lines.add(PartySync.canLaunch(listing)
                            ? Component.translatable("partyfinder.menu.launch.click").withStyle(ChatFormatting.YELLOW)
                            : Component.translatable("partyfinder.error.cannot_launch").withStyle(ChatFormatting.RED));
                }, () -> lines.add(Component.translatable("partyfinder.error.not_in_finder_party").withStyle(ChatFormatting.RED)));
            }
            case CLOSE -> lines.add(Component.translatable("partyfinder.menu.close").withStyle(ChatFormatting.RED));
        }
        return lines;
    }

    private int count(MenuLayout.Slot slot) {
        return switch (slot.action()) {
            case CATEGORY -> (int) PartyFinderState.listings().stream()
                    .map(Listing::activity)
                    .filter(id -> Activities.get(id).category().equals(slot.category()))
                    .count();
            case ALL -> PartyFinderState.listings().size();
            case REQUESTS -> PartyFinderState.joinRequests().size();
            case MODERATION -> Moderation.reportedPlayers().stream().filter(p -> p.count() > 0 && !p.banned()).toList().size();
            default -> 0;
        };
    }

    private MenuLayout.Slot slot(int index) {
        if (index < 0) return null;
        for (MenuLayout.Slot slot : layout.slots()) {
            if (slot.action() == MenuLayout.Action.MODERATION && !PartyFinderState.isOp()) continue;
            if (slot.index() == index) return slot;
        }
        return null;
    }

    private int slotAt(double mouseX, double mouseY) {
        for (int i = 0; i < layout.rows() * 9; i++) {
            int x = slotX(i);
            int y = slotY(i);
            if (mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17) return i;
        }
        return -1;
    }

    private int slotX(int index) {
        return left + 8 + (index % 9) * 18;
    }

    private int slotY(int index) {
        return top + 18 + (index / 9) * 18;
    }

    private static int statusColor(PartyFinderState.Status status) {
        return switch (status) {
            case ONLINE -> Activities.color("menu_status_online", 0x00AA00);
            case CONNECTING -> Activities.color("menu_status_connecting", 0xAA8800);
            case OFFLINE -> Activities.color("menu_status_offline", 0xAA0000);
        };
    }
}
