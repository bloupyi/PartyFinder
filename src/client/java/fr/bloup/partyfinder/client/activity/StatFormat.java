package fr.bloup.partyfinder.client.activity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Turns the backend's compact SkyBlock stats into short labels, picked per activity in activities.json:
 * "sb", "cata", "class", "hotm", "kuudra", "skill_&lt;skill&gt;", "slayer_&lt;boss&gt;".
 */
public final class StatFormat {
    private static final List<String> KUUDRA_TIERS = List.of("infernal", "fiery", "burning", "hot", "none");
    private static final String VALUE = "[a-z_]{1,16}";

    private StatFormat() {
    }

    public static List<Component> format(List<String> statIds, JsonObject stats) {
        List<Component> parts = new ArrayList<>();
        if (stats == null) return parts;
        for (String id : statIds) {
            Component part = format(id, stats);
            if (part != null) parts.add(part);
        }
        return parts;
    }

    private static Component format(String id, JsonObject stats) {
        if (id.equals("sb")) return value(stats, "sb").stream().mapToObj(v -> stat("sb", v)).findFirst().orElse(null);
        if (id.equals("cata")) return value(stats, "cata").stream().mapToObj(v -> stat("cata", v)).findFirst().orElse(null);
        if (id.equals("hotm")) return value(stats, "hotm").stream().mapToObj(v -> stat("hotm", v)).findFirst().orElse(null);
        if (id.equals("class")) {
            String selected = string(stats, "selectedClass");
            if (selected == null) return null;
            return value(stats, "classes", selected).stream()
                    .mapToObj(v -> (Component) Component.translatable("partyfinder.stat.class." + selected, v))
                    .findFirst().orElse(null);
        }
        if (id.equals("kuudra")) {
            for (String tier : KUUDRA_TIERS) {
                OptionalInt runs = value(stats, "kuudra", tier);
                if (runs.isPresent() && runs.getAsInt() > 0) {
                    return Component.translatable("partyfinder.stat.kuudra." + tier, runs.getAsInt());
                }
            }
            return null;
        }
        if (id.startsWith("skill_")) {
            String skill = id.substring(6);
            if (!skill.matches(VALUE)) return null;
            return value(stats, "skills", skill).stream()
                    .mapToObj(v -> (Component) Component.translatable("partyfinder.stat.skill." + skill, v))
                    .findFirst().orElse(null);
        }
        if (id.startsWith("slayer_")) {
            String boss = id.substring(7);
            if (!boss.matches(VALUE)) return null;
            return value(stats, "slayers", boss).stream()
                    .mapToObj(v -> (Component) Component.translatable("partyfinder.stat.slayer." + boss, v))
                    .findFirst().orElse(null);
        }
        return null;
    }

    private static Component stat(String key, int value) {
        return Component.translatable("partyfinder.stat." + key, value);
    }

    private static OptionalInt value(JsonObject root, String... path) {
        JsonElement element = root;
        for (String key : path) {
            if (element == null || !element.isJsonObject()) return OptionalInt.empty();
            element = element.getAsJsonObject().get(key);
        }
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return OptionalInt.empty();
        return OptionalInt.of(Math.clamp(element.getAsLong(), 0, 99_999));
    }

    private static String string(JsonObject root, String key) {
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) return null;
        String value = element.getAsString();
        return value.matches(VALUE) ? value : null;
    }
}
