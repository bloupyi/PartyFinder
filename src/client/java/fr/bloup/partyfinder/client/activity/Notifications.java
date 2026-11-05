package fr.bloup.partyfinder.client.activity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.bloup.partyfinder.client.gui.PfToast;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * notifications.json: per notification, the toast icon and how long it stays, and the sound played. Toasts exist for
 * join_request, party_full, warp, transfer, event and report; the others are chat only and just play their sound.
 */
public final class Notifications {
    public static final List<String> IDS = List.of("join_request", "join_sent", "join_accepted", "join_denied", "party_full",
            "warp", "transfer", "invite_accepted", "event", "report");
    private static final Identifier DEFAULT_BACKGROUND = Identifier.withDefaultNamespace("toast/system");
    private static final int DEFAULT_SECONDS = 5;

    public record Notification(ItemStack icon, int seconds, PackSound sound) {
    }

    private static final Notification NONE = new Notification(ItemStack.EMPTY, DEFAULT_SECONDS, null);
    private static final Map<String, Notification> BY_ID = new HashMap<>();
    private static Identifier background = DEFAULT_BACKGROUND;

    private Notifications() {
    }

    static void load(JsonObject json) {
        BY_ID.clear();
        background = DEFAULT_BACKGROUND;
        if (json == null) return;
        if (json.has("toast_background")) {
            Identifier id = Identifier.tryParse(json.get("toast_background").getAsString());
            if (id != null) background = id;
        }
        for (String id : IDS) {
            JsonElement element = json.get(id);
            if (element == null || !element.isJsonObject()) continue;
            JsonObject o = element.getAsJsonObject();
            int seconds = o.has("duration") ? Math.clamp(o.get("duration").getAsInt(), 1, 30) : DEFAULT_SECONDS;
            BY_ID.put(id, new Notification(Icons.parse(o.get("icon"), ItemStack.EMPTY), seconds, PackSound.parse(o.get("sound"))));
        }
    }

    public static Notification get(String id) {
        return BY_ID.getOrDefault(id, NONE);
    }

    public static Identifier toastBackground() {
        return background;
    }

    /** Shows (or updates) this notification's toast and plays its sound. */
    public static void toast(String id, Component title, Component body) {
        PfToast.show(Minecraft.getInstance().gui.toastManager(), id, get(id), title, body);
        sound(id);
    }

    /** Plays this notification's sound, if it has one. */
    public static void sound(String id) {
        PackSound sound = get(id).sound();
        if (sound != null && Minecraft.getInstance().player != null) sound.play();
    }
}
