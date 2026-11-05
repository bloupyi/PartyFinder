package fr.bloup.partyfinder.client.activity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.TextColor;

import java.util.HashMap;
import java.util.Map;

/**
 * theme.json: text colors ("colors") and the look of the mod's chat messages ("chat"). Every key is optional, a pack
 * only lists what it changes.
 */
public final class Theme {
    /** Defaults for the keys whose callers do not pass their own fallback. */
    private static final Map<String, Integer> DEFAULTS = Map.ofEntries(
            Map.entry("chat_prefix", 0xFF55FF),
            Map.entry("chat_frame", 0x5555FF),
            Map.entry("chat_success", 0x55FF55),
            Map.entry("chat_error", 0xFF5555),
            Map.entry("chat_notice", 0xFFFF55),
            Map.entry("chat_detail", 0xFFAA00),
            Map.entry("chat_event", 0xFF55FF),
            Map.entry("chat_muted", 0xAAAAAA),
            Map.entry("chat_stats", 0x55FFFF),
            Map.entry("chat_button_accept", 0x55FF55),
            Map.entry("chat_button_decline", 0xFF5555),
            Map.entry("chat_button_auto", 0xFFAA00),
            Map.entry("chat_button_link", 0x55FFFF),
            Map.entry("toast_title", 0xFFFF00),
            Map.entry("toast_body", 0xFFFFFF),
            Map.entry("full_marker", 0xFF5555),
            Map.entry("card_line", 0xFF5555),
            Map.entry("card_name", 0xFFAA00),
            Map.entry("card_label", 0xFFAA00),
            Map.entry("card_value", 0xFFAA00),
            Map.entry("card_highlight", 0xFF5555),
            Map.entry("card_muted", 0xAAAAAA),
            Map.entry("card_item", 0xFF55FF),
            Map.entry("reports_some", 0xFF5555),
            Map.entry("reports_none", 0x55FF55));
    private static final int MAX_PREFIX = 32;
    private static final int MAX_FRAME_TEXT = 4;

    private static final Map<String, Integer> COLORS = new HashMap<>();
    private static String prefix = "[PF] ";
    private static boolean frame = true;
    private static String frameText = "-";
    private static int frameLength = 53;
    private static boolean frameStrikethrough = true;

    private Theme() {
    }

    static void load(JsonObject json) {
        COLORS.clear();
        prefix = "[PF] ";
        frame = true;
        frameText = "-";
        frameLength = 53;
        frameStrikethrough = true;
        if (json == null) return;
        if (json.has("colors") && json.get("colors").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("colors").entrySet()) {
                if (!e.getValue().isJsonPrimitive()) continue;
                TextColor.parseColor(e.getValue().getAsString()).result().ifPresent(c -> COLORS.put(e.getKey(), c.getValue()));
            }
        }
        if (json.has("chat") && json.get("chat").isJsonObject()) {
            JsonObject chat = json.getAsJsonObject("chat");
            if (chat.has("prefix")) prefix = clean(chat.get("prefix").getAsString(), MAX_PREFIX);
            if (chat.has("frame")) frame = chat.get("frame").getAsBoolean();
            if (chat.has("frame_text")) {
                String text = clean(chat.get("frame_text").getAsString(), MAX_FRAME_TEXT);
                if (!text.isEmpty()) frameText = text;
            }
            if (chat.has("frame_length")) frameLength = Math.clamp(chat.get("frame_length").getAsInt(), 1, 80);
            if (chat.has("frame_strikethrough")) frameStrikethrough = chat.get("frame_strikethrough").getAsBoolean();
        }
    }

    /** Opaque ARGB color for drawing. */
    public static int color(String key, int fallback) {
        return 0xFF000000 | rgb(key, fallback);
    }

    public static int rgb(String key, int fallback) {
        return COLORS.getOrDefault(key, fallback) & 0xFFFFFF;
    }

    /** RGB color for chat components, with the built-in default of that key. */
    public static int rgb(String key) {
        return rgb(key, DEFAULTS.getOrDefault(key, 0xFFFFFF));
    }

    public static String prefix() {
        return prefix;
    }

    public static boolean frame() {
        return frame;
    }

    /** The frame line drawn above and below framed messages, "frame_length" characters long. */
    public static String frameLine() {
        StringBuilder line = new StringBuilder();
        while (line.length() < frameLength) line.append(frameText);
        return line.substring(0, frameLength);
    }

    public static boolean frameStrikethrough() {
        return frameStrikethrough;
    }

    /** No formatting codes, control or line break characters in pack texts. */
    private static String clean(String text, int max) {
        String cleaned = text.replaceAll("[\\p{Cntrl}§]", "");
        return cleaned.length() > max ? cleaned.substring(0, max) : cleaned;
    }
}
