package fr.bloup.partyfinder.client.gui;

import fr.bloup.partyfinder.client.activity.Notifications;
import fr.bloup.partyfinder.client.activity.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Toast like the vanilla system toast, with the icon and duration from notifications.json; one per notification id. */
public final class PfToast implements Toast {
    private static final int MIN_WIDTH = 160;
    private static final int MAX_WIDTH = 240;

    private final String id;
    private Notifications.Notification style;
    private Component title;
    private List<FormattedCharSequence> body;
    private int width;
    private long shownAt;
    private boolean changed = true;
    private Visibility wanted = Visibility.SHOW;

    private PfToast(String id, Notifications.Notification style, Component title, Component body) {
        this.id = id;
        reset(style, title, body);
    }

    public static void show(ToastManager manager, String id, Notifications.Notification style, Component title, Component body) {
        PfToast toast = manager.getToast(PfToast.class, id);
        if (toast == null) manager.addToast(new PfToast(id, style, title, body));
        else toast.reset(style, title, body);
    }

    private void reset(Notifications.Notification style, Component title, Component body) {
        this.style = style;
        this.title = title;
        Font font = Minecraft.getInstance().font;
        int textX = textX();
        int textWidth = Math.max(font.width(title), font.width(body));
        width = Math.clamp(textX + textWidth + 8, MIN_WIDTH, MAX_WIDTH);
        this.body = font.split(body, width - textX - 8);
        changed = true;
    }

    private int textX() {
        return style.icon().isEmpty() ? 10 : 30;
    }

    @Override
    public Visibility getWantedVisibility() {
        return wanted;
    }

    @Override
    public void update(ToastManager manager, long time) {
        if (changed) {
            shownAt = time;
            changed = false;
        }
        double duration = style.seconds() * 1000.0 * manager.getNotificationDisplayTimeMultiplier();
        wanted = time - shownAt < duration ? Visibility.SHOW : Visibility.HIDE;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, Font font, long time) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, Notifications.toastBackground(), 0, 0, width(), height());
        ItemStack icon = style.icon();
        if (!icon.isEmpty()) g.item(icon, 8, (height() - 16) / 2);
        int x = textX();
        g.text(font, title, x, 7, Theme.color("toast_title", 0xFFFF00), false);
        for (int i = 0; i < body.size(); i++) {
            g.text(font, body.get(i), x, 18 + i * 12, Theme.color("toast_body", 0xFFFFFF), false);
        }
    }

    @Override
    public Object getToken() {
        return id;
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return 20 + Math.max(body.size(), 1) * 12;
    }
}
