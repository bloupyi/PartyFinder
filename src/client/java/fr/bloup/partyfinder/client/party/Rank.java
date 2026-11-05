package fr.bloup.partyfinder.client.party;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;
import java.util.Set;

/** Hypixel rank of a player (looked up by the server from the Hypixel API), drawn like Hypixel does. */
public record Rank(String id, ChatFormatting plus, ChatFormatting color) {
    private static final Set<String> IDS = Set.of("VIP", "VIP_PLUS", "MVP", "MVP_PLUS", "MVP_PLUS_PLUS", "ADMIN",
            "GAME_MASTER", "MODERATOR", "HELPER", "YOUTUBER");
    private static final Set<String> COLORS = Set.of("BLACK", "DARK_BLUE", "DARK_GREEN", "DARK_AQUA", "DARK_RED", "DARK_PURPLE",
            "GOLD", "GRAY", "DARK_GRAY", "BLUE", "GREEN", "AQUA", "RED", "LIGHT_PURPLE", "YELLOW", "WHITE");

    /** Null for no rank or anything unexpected. */
    public static Rank parse(JsonElement json) {
        if (json == null || !json.isJsonObject()) return null;
        JsonObject o = json.getAsJsonObject();
        if (!o.has("rank") || !o.get("rank").isJsonPrimitive()) return null;
        String id = o.get("rank").getAsString();
        if (!IDS.contains(id)) return null;
        return new Rank(id, color(o, "plus", ChatFormatting.RED), color(o, "color", ChatFormatting.GOLD));
    }

    private static ChatFormatting color(JsonObject o, String key, ChatFormatting fallback) {
        if (!o.has(key) || !o.get(key).isJsonPrimitive()) return fallback;
        String name = o.get(key).getAsString().toUpperCase(Locale.ROOT);
        return COLORS.contains(name) ? ChatFormatting.valueOf(name) : fallback;
    }

    /** Color of the name after the rank. */
    public ChatFormatting nameColor() {
        return switch (id) {
            case "VIP", "VIP_PLUS" -> ChatFormatting.GREEN;
            case "MVP", "MVP_PLUS" -> ChatFormatting.AQUA;
            case "MVP_PLUS_PLUS" -> color == ChatFormatting.AQUA ? ChatFormatting.AQUA : ChatFormatting.GOLD;
            case "ADMIN", "YOUTUBER" -> ChatFormatting.RED;
            case "GAME_MASTER", "MODERATOR" -> ChatFormatting.DARK_GREEN;
            case "HELPER" -> ChatFormatting.BLUE;
            default -> ChatFormatting.GRAY;
        };
    }

    /** "[MVP+]" with Hypixel's colors. */
    public MutableComponent tag() {
        ChatFormatting main = nameColor();
        return switch (id) {
            case "VIP" -> Component.literal("[VIP]").withStyle(main);
            case "VIP_PLUS" -> Component.literal("[VIP").withStyle(main)
                    .append(Component.literal("+").withStyle(ChatFormatting.GOLD)).append(Component.literal("]").withStyle(main));
            case "MVP" -> Component.literal("[MVP]").withStyle(main);
            case "MVP_PLUS" -> Component.literal("[MVP").withStyle(main)
                    .append(Component.literal("+").withStyle(plus)).append(Component.literal("]").withStyle(main));
            case "MVP_PLUS_PLUS" -> Component.literal("[MVP").withStyle(main)
                    .append(Component.literal("++").withStyle(plus)).append(Component.literal("]").withStyle(main));
            case "YOUTUBER" -> Component.literal("[").withStyle(main)
                    .append(Component.literal("YOUTUBE").withStyle(ChatFormatting.WHITE)).append(Component.literal("]").withStyle(main));
            case "GAME_MASTER" -> Component.literal("[GM]").withStyle(main);
            case "MODERATOR" -> Component.literal("[MOD]").withStyle(main);
            default -> Component.literal("[" + id + "]").withStyle(main);
        };
    }

    /** "[MVP+] Name" in Hypixel's colors, or the gray name of a player without rank. */
    public static MutableComponent name(Rank rank, String name) {
        if (rank == null) return Component.literal(name).withStyle(ChatFormatting.GRAY);
        return Component.empty().append(rank.tag()).append(" ").append(Component.literal(name).withStyle(rank.nameColor()));
    }
}
