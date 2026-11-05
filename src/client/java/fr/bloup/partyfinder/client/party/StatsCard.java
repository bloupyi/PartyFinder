package fr.bloup.partyfinder.client.party;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.bloup.partyfinder.client.activity.Activities;
import fr.bloup.partyfinder.client.activity.Activity;
import fr.bloup.partyfinder.client.activity.StatFormat;
import fr.bloup.partyfinder.client.activity.Theme;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;

/**
 * A player's stats for a party finder category, Hypixel Party Finder style: the short headline shown in chat
 * ("(Archer Level 40)") and the card shown on hover. Colors come from theme.json ("card_*").
 */
public final class StatsCard {
    private static final List<String> CLASSES = List.of("healer", "mage", "berserk", "archer", "tank");
    private static final List<String> SLAYERS = List.of("zombie", "spider", "wolf", "enderman", "blaze", "vampire");
    private static final List<String> KUUDRA_TIERS = List.of("none", "hot", "burning", "fiery", "infernal");

    private StatsCard() {
    }

    /**
     * "(Archer Level 40)" for dungeons, else the activity's first stat ("(Fishing Level 50)"), led by the chosen Kuudra
     * role ("(Stun · Infernal x120)"); null without anything to show.
     */
    public static Component headline(Activity activity, String category, JsonObject stats, String role) {
        Component stat = statHeadline(activity, category, stats);
        if (role == null) return stat == null ? null : paren(stat);
        MutableComponent text = StatFormat.roleName(category, role).copy();
        if (stat != null) text.append(" · ").append(stat);
        return paren(text);
    }

    private static Component statHeadline(Activity activity, String category, JsonObject stats) {
        if (stats == null) return null;
        if (category.equals("dungeons")) {
            String cls = StatFormat.selectedClass(stats);
            OptionalInt level = StatFormat.level("class", stats);
            if (cls != null && level.isPresent()) return level(StatFormat.className(cls), level.getAsInt());
            OptionalInt cata = StatFormat.level("cata", stats);
            return cata.isPresent() ? level(StatFormat.name("cata"), cata.getAsInt()) : null;
        }
        for (String id : activity.stats()) {
            if (id.equals("kuudra")) {
                List<Component> parts = StatFormat.format(List.of(id), stats);
                if (!parts.isEmpty()) return parts.getFirst();
                continue;
            }
            OptionalInt value = StatFormat.level(id, stats);
            if (value.isPresent()) return level(StatFormat.name(id), value.getAsInt());
        }
        OptionalInt sb = StatFormat.level("sb", stats);
        return sb.isPresent() ? level(StatFormat.name("sb"), sb.getAsInt()) : null;
    }

    private static Component level(Component name, int value) {
        return Component.translatable("partyfinder.stat.level", name, value);
    }

    private static Component paren(Component inner) {
        return Component.literal("(").append(inner).append(")").withColor(Theme.rgb("chat_stats"));
    }

    /** The hover card: header with the name, then the stats that matter for this category. */
    public static Component card(String name, Rank rank, JsonObject stats, ReportSummary reports, boolean unverified,
                                 Activity activity, String category, String role) {
        List<Component> lines = new ArrayList<>();
        int line = Theme.rgb("card_line");
        MutableComponent bar = Component.literal("        ").withStyle(s -> s.withColor(line).withStrikethrough(true));
        lines.add(Component.empty().append(bar.copy()).append(Component.literal("[- ").withColor(line))
                .append(Component.literal(name).withStyle(s -> (rank != null ? s.withColor(rank.nameColor())
                        : s.withColor(Theme.rgb("card_name"))).withBold(true)))
                .append(Component.literal(" -]").withColor(line)).append(bar.copy()));

        if (role != null) {
            lines.add(row("partyfinder.card.role", StatFormat.roleName(category, role).copy().withColor(Theme.rgb("card_highlight"))));
        }
        if (stats == null) {
            lines.add(Component.translatable(unverified ? "partyfinder.requests.unverified" : "partyfinder.card.no_stats")
                    .withColor(Theme.rgb("card_muted")));
        } else if (category.equals("dungeons")) {
            dungeons(lines, stats);
        } else {
            general(lines, stats, activity, category);
        }
        if (stats != null) items(lines, stats, category);
        if (reports != null && (reports.count() > 0 || reports.endorsements() > 0)) {
            lines.add(row("partyfinder.card.reputation", Component.literal(reports.badge())
                    .withColor(Theme.rgb(reports.count() > 0 ? "reports_some" : "reports_none"))));
        }
        MutableComponent card = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) card.append("\n");
            card.append(lines.get(i));
        }
        return card;
    }

    private static void dungeons(List<Component> lines, JsonObject stats) {
        StatFormat.level("cata", stats).ifPresent(v -> lines.add(row("partyfinder.card.level", number(v))));
        StatFormat.level("secrets", stats).ifPresent(v -> lines.add(row("partyfinder.card.secrets", number(v))));
        String selected = StatFormat.selectedClass(stats);
        if (selected != null) {
            lines.add(row("partyfinder.card.selected_class", StatFormat.className(selected).copy().withColor(Theme.rgb("card_highlight"))));
        }
        MutableComponent classes = Component.literal("[ ").withColor(Theme.rgb("card_label"));
        boolean any = false;
        for (String cls : CLASSES) {
            OptionalInt level = StatFormat.level("class_" + cls, stats);
            if (level.isEmpty()) continue;
            if (any) classes.append(Component.literal(" · ").withColor(Theme.rgb("card_muted")));
            any = true;
            int color = Theme.rgb(cls.equals(selected) ? "card_highlight" : "card_value");
            classes.append(Component.translatable("partyfinder.card.class_short." + cls).withColor(Theme.rgb("card_label")))
                    .append(Component.literal(" » ").withColor(Theme.rgb("card_label")))
                    .append(Component.literal(String.valueOf(level.getAsInt())).withColor(color));
        }
        if (any) lines.add(classes.append(Component.literal(" ]").withColor(Theme.rgb("card_label"))));
        completions(lines, stats, "floors", "partyfinder.card.floors", "F", 0);
        completions(lines, stats, "master", "partyfinder.card.master", "M", 1);
    }

    private static void completions(List<Component> lines, JsonObject stats, String key, String label, String prefix, int first) {
        MutableComponent value = Component.empty();
        boolean any = false;
        for (int floor = first; floor <= 7; floor++) {
            OptionalInt count = StatFormat.at(stats, key, String.valueOf(floor));
            if (count.isEmpty() || count.getAsInt() <= 0) continue;
            if (any) value.append(Component.literal(" · ").withColor(Theme.rgb("card_muted")));
            any = true;
            String name = floor == 0 ? "E" : prefix + floor;
            value.append(Component.literal(name + " ").withColor(Theme.rgb("card_label")))
                    .append(Component.literal(number(count.getAsInt())).withColor(Theme.rgb("card_value")));
        }
        if (any) {
            if (lines.size() > 1 && !key.equals("master")) lines.add(Component.empty());
            lines.add(Component.translatable(label).withColor(Theme.rgb("card_muted")));
            lines.add(value);
        }
    }

    private static void general(List<Component> lines, JsonObject stats, Activity activity, String category) {
        StatFormat.level("sb", stats).ifPresent(v -> lines.add(row("partyfinder.card.sb_level", number(v))));
        List<String> shown = new ArrayList<>();
        for (String id : activity.stats()) {
            if (id.equals("sb") || id.equals("kuudra") || shown.contains(id)) continue;
            shown.add(id);
            StatFormat.level(id, stats).ifPresent(v -> lines.add(row(StatFormat.name(id), number(v))));
        }
        if (category.equals("slayer")) {
            MutableComponent bosses = Component.literal("[ ").withColor(Theme.rgb("card_label"));
            boolean any = false;
            for (String boss : SLAYERS) {
                OptionalInt level = StatFormat.level("slayer_" + boss, stats);
                if (level.isEmpty()) continue;
                if (any) bosses.append(Component.literal(" · ").withColor(Theme.rgb("card_muted")));
                any = true;
                bosses.append(Component.translatable("partyfinder.card.slayer_short." + boss).withColor(Theme.rgb("card_label")))
                        .append(Component.literal(" » ").withColor(Theme.rgb("card_label")))
                        .append(Component.literal(String.valueOf(level.getAsInt())).withColor(Theme.rgb("card_value")));
            }
            if (any) lines.add(bosses.append(Component.literal(" ]").withColor(Theme.rgb("card_label"))));
        }
        if (category.equals("kuudra") || activity.stats().contains("kuudra")) {
            MutableComponent tiers = Component.empty();
            boolean any = false;
            for (String tier : KUUDRA_TIERS) {
                OptionalInt runs = StatFormat.at(stats, "kuudra", tier);
                if (runs.isEmpty() || runs.getAsInt() <= 0) continue;
                if (any) tiers.append(Component.literal(" · ").withColor(Theme.rgb("card_muted")));
                any = true;
                tiers.append(Component.translatable("partyfinder.card.kuudra." + tier).withColor(Theme.rgb("card_label")))
                        .append(Component.literal(" " + number(runs.getAsInt())).withColor(Theme.rgb("card_value")));
            }
            if (any) {
                lines.add(Component.translatable("partyfinder.card.kuudra").withColor(Theme.rgb("card_muted")));
                lines.add(tiers);
            }
        }
    }

    /** Notable items of the category the player owns, or a note when their inventory API is off. */
    private static void items(List<Component> lines, JsonObject stats, String category) {
        List<String> wanted = Activities.itemsFor(category);
        if (wanted.isEmpty()) return;
        JsonElement owned = stats.get("items");
        Component value;
        if (owned == null || !owned.isJsonArray()) {
            value = Component.translatable("partyfinder.card.inventory_off").withColor(Theme.rgb("card_muted"));
        } else {
            List<String> ids = new ArrayList<>();
            owned.getAsJsonArray().forEach(e -> {
                if (e.isJsonPrimitive()) ids.add(e.getAsString());
            });
            MutableComponent list = Component.empty();
            boolean any = false;
            for (String id : wanted) {
                if (!ids.contains(id)) continue;
                if (any) list.append(Component.literal(", ").withColor(Theme.rgb("card_muted")));
                any = true;
                list.append(itemName(id).copy().withColor(Theme.rgb("card_item")));
            }
            value = any ? list : Component.translatable("partyfinder.card.no_items").withColor(Theme.rgb("card_muted"));
        }
        lines.add(Component.empty());
        lines.add(row("partyfinder.card.items", value));
    }

    /** "partyfinder.item.<id>" or the id made readable ("GYROKINETIC_WAND" -> "Gyrokinetic Wand"). */
    public static Component itemName(String id) {
        StringBuilder readable = new StringBuilder();
        for (String word : id.toLowerCase(Locale.ROOT).split("_")) {
            if (word.isEmpty()) continue;
            if (!readable.isEmpty()) readable.append(' ');
            readable.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return Component.translatableWithFallback("partyfinder.item." + id.toLowerCase(Locale.ROOT), readable.toString());
    }

    private static Component row(String labelKey, Object value) {
        return row(Component.translatable(labelKey), value);
    }

    private static Component row(Component label, Object value) {
        MutableComponent row = Component.empty().append(label.copy().withColor(Theme.rgb("card_label")))
                .append(Component.literal(" » ").withColor(Theme.rgb("card_label")));
        if (value instanceof Component c) row.append(c);
        else row.append(Component.literal(String.valueOf(value)).withColor(Theme.rgb("card_value")));
        return row;
    }

    private static String number(int value) {
        return NumberFormat.getIntegerInstance(Locale.US).format(value);
    }
}
