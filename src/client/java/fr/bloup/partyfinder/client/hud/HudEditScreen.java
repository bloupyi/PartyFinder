package fr.bloup.partyfinder.client.hud;

import fr.bloup.partyfinder.client.activity.Activities;
import fr.bloup.partyfinder.client.config.PartyFinderConfig;
import fr.bloup.partyfinder.client.config.PartyFinderConfig.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Drag the party HUD to place it; it snaps to the nearest screen corner or side. */
public class HudEditScreen extends Screen {
    private final Screen parent;
    private boolean dragging;
    private double grabX;
    private double grabY;

    public HudEditScreen(Screen parent) {
        super(Component.translatable("partyfinder.hud.edit.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 - 50, height - 28, 100, 20).build());
    }

    @Override
    public void onClose() {
        PartyFinderConfig.save();
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        Hud cfg = PartyFinderConfig.get().hud;
        List<PartyHud.Member> members = HudPreview.members(cfg);
        PartyHud.render(g, members, cfg);
        PartyHud.Bounds b = PartyHud.lastBounds();
        int color = dragging || b.contains(mouseX, mouseY) ? 0xFFFFFF55 : 0xAAFFFFFF;
        g.outline(b.x() - 1, b.y() - 1, b.width() + 2, b.height() + 2, color);
        g.centeredText(font, Component.translatable("partyfinder.hud.edit.hint"), width / 2, height / 2 - 4,
                Activities.color("title", 0xFFFFFF));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        PartyHud.Bounds b = PartyHud.lastBounds();
        if (b.contains(event.x(), event.y())) {
            dragging = true;
            grabX = event.x() - b.x();
            grabY = event.y() - b.y();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!dragging) return super.mouseDragged(event, dx, dy);
        PartyHud.Bounds b = PartyHud.lastBounds();
        int x = (int) Math.round(event.x() - grabX);
        int y = (int) Math.round(event.y() - grabY);
        place(PartyFinderConfig.get().hud, x, y, b.width(), b.height());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    private void place(Hud cfg, int x, int y, int w, int h) {
        x = Math.clamp(x, 0, Math.max(0, width - w));
        y = Math.clamp(y, 0, Math.max(0, height - h));
        boolean right = x + w / 2 > width / 2;
        int third = height / 3;
        int middle = y + h / 2;
        PartyFinderConfig.Anchor anchor;
        if (middle < third) anchor = right ? PartyFinderConfig.Anchor.TOP_RIGHT : PartyFinderConfig.Anchor.TOP_LEFT;
        else if (middle > 2 * third) anchor = right ? PartyFinderConfig.Anchor.BOTTOM_RIGHT : PartyFinderConfig.Anchor.BOTTOM_LEFT;
        else anchor = right ? PartyFinderConfig.Anchor.CENTER_RIGHT : PartyFinderConfig.Anchor.CENTER_LEFT;
        cfg.anchor = anchor;
        cfg.offsetX = right ? width - w - x : x;
        cfg.offsetY = switch (anchor) {
            case TOP_LEFT, TOP_RIGHT -> y;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> height - h - y;
            case CENTER_LEFT, CENTER_RIGHT -> y - (height - h) / 2;
        };
    }
}
