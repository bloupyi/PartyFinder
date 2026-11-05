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

    /** Stats an activity can require, in the order shown in the listing form ("kuudra" is a run count, not a level). */
    public static List<String> requirementIds(List<String> statIds) {
        return statIds.stream().filter(id -> !id.equals("kuudra")).toList();
    }

    public static int maxFor(String id) {
        if (id.equals("sb")) return 500;
        if (id.equals("hotm")) return 10;
        if (id.startsWith("slayer_")) return 9;
        if (id.startsWith("skill_")) return 60;
        return 50;
    }

    /** Label of a requirement id without value, e.g. "Cata", "Mining", "Revenant". */
    public static Component label(String id) {
        if (id.equals("class")) return Component.translatable("partyfinder.stat.class_level");
        if (id.startsWith("skill_")) return Component.translatable("partyfinder.stat.skill." + id.substring(6), "").copy();
        if (id.startsWith("slayer_")) return Component.translatable("partyfinder.stat.slayer." + id.substring(7), "").copy();
        return Component.translatable("partyfinder.stat." + id, "");
    }

    /** "Cata 30+" */
    public static Component requirement(String id, int min) {
        return Component.literal(label(id).getString().strip() + " " + min + "+");
    }

    /** Numeric value of a stat id ("sb", "cata", "hotm", "class" for the selected class, "skill_x", "slayer_x"). */
    public static OptionalInt level(String id, JsonObject stats) {
        if (stats == null) return OptionalInt.empty();
        if (id.equals("sb") || id.equals("cata") || id.equals("hotm") || id.equals("secrets")) return value(stats, id);
        if (id.equals("class")) {
            String selected = selectedClass(stats);
            return selected == null ? OptionalInt.empty() : value(stats, "classes", selected);
        }
        if (id.startsWith("class_")) return value(stats, "classes", id.substring(6));
        if (id.startsWith("skill_")) return value(stats, "skills", id.substring(6));
        if (id.startsWith("slayer_")) return value(stats, "slayers", id.substring(7));
        return OptionalInt.empty();
    }

    /** Value at a path of the stats object ("floors", "7"...). */
    public static OptionalInt at(JsonObject stats, String... path) {
        return stats == null ? OptionalInt.empty() : value(stats, path);
    }

    public static String selectedClass(JsonObject stats) {
        return stats == null ? null : string(stats, "selectedClass");
    }

    /** Name of a stat for "X Level N": "SkyBlock", "Catacombs", "Fishing", "Revenant"... */
    public static Component name(String id) {
        if (id.equals("class")) return Component.translatable("partyfinder.stat.class_level");
        if (id.startsWith("class_")) return className(id.substring(6));
        if (id.equals("sb") || id.equals("cata") || id.equals("hotm") || id.equals("secrets")) {
            return Component.translatable("partyfinder.stat.name." + id);
        }
        return Component.literal(label(id).getString().strip());
    }

    /** Name of a wanted role: a dungeon class, or a Kuudra role (Stun, DPS, Tank, Healer). */
    public static Component roleName(String category, String role) {
        return category.equals("kuudra") ? Component.translatable("partyfinder.role." + role) : className(role);
    }

    public static Component className(String cls) {
        return Component.translatable("partyfinder.class." + cls);
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
