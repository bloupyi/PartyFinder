package fr.bloup.partyfinder.client.activity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.bloup.partyfinder.Partyfinder;
import fr.bloup.partyfinder.client.gui.PfSprites;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class Activities {
    private static final Identifier ACTIVITIES = Identifier.fromNamespaceAndPath(Partyfinder.MOD_ID, "partyfinder/activities.json");
    private static final Identifier THEME = Identifier.fromNamespaceAndPath(Partyfinder.MOD_ID, "partyfinder/theme.json");
    private static final Identifier NOTIFICATIONS = Identifier.fromNamespaceAndPath(Partyfinder.MOD_ID, "partyfinder/notifications.json");

    // Launch commands can come from any resource pack: only travel commands, never e.g. /coopadd or /p transfer.
    private static final Pattern SAFE_COMMAND = Pattern.compile("warp [a-z0-9_]{1,32}|joininstance [A-Z0-9_]{1,48}");
    private static final Map<String, Activity> BY_ID = new LinkedHashMap<>();
    private static final List<String> CATEGORIES = new ArrayList<>();
    private static Activity unknown;
    private static final Map<String, List<String>> CATEGORY_ITEMS = new LinkedHashMap<>();
    /** Most tags a category can offer; the server holds the same limit. */
    public static final int MAX_CATEGORY_TAGS = 20;
    private static final Pattern TAG_ID = Pattern.compile("[a-z0-9_]{1,32}");
    private static final Map<String, List<String>> CATEGORY_TAGS = new LinkedHashMap<>();
    private static boolean pending;

    private Activities() {
    }

    public static void reload() {
        reload(Minecraft.getInstance().getResourceManager());
    }

    /** Reads activities.json, theme.json and notifications.json; also run on every resource reload (F3+T, pack change). */
    public static void reload(ResourceManager manager) {
        Theme.load(PackJson.merged(manager, THEME));
        PfSprites.refresh(manager);
        // The first resource load runs before item components are bound: item icons cannot be built yet, so the
        // rest waits for the first client tick after it (tick()).
        if (!Items.PAPER.builtInRegistryHolder().areComponentsBound()) {
            pending = true;
            return;
        }
        pending = false;
        BY_ID.clear();
        CATEGORIES.clear();
        unknown = null;
        CATEGORY_ITEMS.clear();
        CATEGORY_TAGS.clear();
        read(manager, ACTIVITIES).ifPresent(json -> {
            // Optional explicit category order; categories it does not name follow in activity order.
            if (json.has("categories") && json.get("categories").isJsonArray()) {
                for (JsonElement e : json.getAsJsonArray("categories")) {
                    String category = e.getAsString();
                    if (!CATEGORIES.contains(category)) CATEGORIES.add(category);
                }
            }
            for (JsonElement element : json.getAsJsonArray("activities")) {
                Activity activity = parse(element.getAsJsonObject());
                BY_ID.put(activity.id(), activity);
                if (!CATEGORIES.contains(activity.category())) CATEGORIES.add(activity.category());
            }
            if (json.has("category_items") && json.get("category_items").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("category_items").entrySet()) {
                    if (!e.getValue().isJsonArray()) continue;
                    List<String> ids = new ArrayList<>();
                    e.getValue().getAsJsonArray().forEach(id -> ids.add(id.getAsString()));
                    CATEGORY_ITEMS.put(e.getKey(), List.copyOf(ids));
                }
            }
            if (json.has("category_tags") && json.get("category_tags").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("category_tags").entrySet()) {
                    if (!e.getValue().isJsonArray()) continue;
                    List<String> ids = new ArrayList<>();
                    for (JsonElement id : e.getValue().getAsJsonArray()) {
                        String tag = id.isJsonPrimitive() ? id.getAsString() : "";
                        if (TAG_ID.matcher(tag).matches() && !ids.contains(tag) && ids.size() < MAX_CATEGORY_TAGS) ids.add(tag);
                    }
                    CATEGORY_TAGS.put(e.getKey(), List.copyOf(ids));
                }
            }
            if (json.has("unknown") && json.get("unknown").isJsonObject()) {
                JsonObject o = json.getAsJsonObject("unknown").deepCopy();
                o.addProperty("id", "unknown");
                if (!o.has("category")) o.addProperty("category", "other");
                if (!o.has("stats")) {
                    com.google.gson.JsonArray stats = new com.google.gson.JsonArray();
                    stats.add("sb");
                    o.add("stats", stats);
                }
                o.remove("options");
                o.remove("launch");
                unknown = parse(o);
            }
        });
        Notifications.load(PackJson.merged(manager, NOTIFICATIONS));
    }

    /** Finishes a reload that had to wait for item components. */
    public static void tick() {
        if (pending && Items.PAPER.builtInRegistryHolder().areComponentsBound()) reload();
    }

    private static Activity unknown() {
        if (unknown == null) unknown = defaultUnknown();
        return unknown;
    }

    private static Activity defaultUnknown() {
        return new Activity("unknown", "other", new ItemStack(Items.PAPER), null, 0xFFFFFF, 5, List.of(), Activity.Launch.NONE, List.of("sb"), List.of());
    }

    public static List<Activity> all() {
        return List.copyOf(BY_ID.values());
    }

    /** Activities a SkyBlock event is relevant for ("events" field in activities.json). */
    public static List<Activity> forEvent(String event) {
        return BY_ID.values().stream().filter(a -> a.events().contains(event)).toList();
    }

    public static List<String> categories() {
        return List.copyOf(CATEGORIES);
    }

    public static Activity get(String id) {
        Activity activity = BY_ID.get(id);
        if (activity != null) return activity;
        // Unknown ids (newer mod version, other pack): styled by the "unknown" entry of activities.json.
        Activity style = unknown();
        return new Activity(id, style.category(), style.icon(), style.sprite(), style.color(), style.maxSize(), List.of(),
                Activity.Launch.NONE, style.stats(), List.of());
    }

    /** Notable SkyBlock item ids shown for a category's members ("category_items"), in display order. */
    public static List<String> itemsFor(String category) {
        return CATEGORY_ITEMS.getOrDefault(category, List.of());
    }

    /** Tags a listing of this category can carry ("category_tags"), in display order. */
    public static List<String> tagsFor(String category) {
        return CATEGORY_TAGS.getOrDefault(category, List.of());
    }

    /** Tags of several categories, each once, in category order. */
    public static List<String> tagsFor(Collection<String> categories) {
        return categories.stream().flatMap(c -> tagsFor(c).stream()).distinct().toList();
    }

    public static Component tagName(String tag) {
        return Component.translatable("partyfinder.tag." + tag);
    }

    public static boolean isSafeCommand(String command) {
        return SAFE_COMMAND.matcher(command).matches();
    }

    /** Theme color (theme.json "colors"), opaque ARGB. */
    public static int color(String key, int fallback) {
        return Theme.color(key, fallback);
    }

    private static Optional<JsonObject> read(ResourceManager manager, Identifier id) {
        Optional<Resource> resource = manager.getResource(id);
        if (resource.isEmpty()) return Optional.empty();
        try (Reader reader = resource.get().openAsReader()) {
            return Optional.of(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception e) {
            Partyfinder.LOGGER.error("Failed to load {}", id, e);
            return Optional.empty();
        }
    }

    private static Activity parse(JsonObject json) {
        String id = json.get("id").getAsString();
        String category = json.has("category") ? json.get("category").getAsString() : "other";
        ItemStack icon = Icons.parse(json.get("icon"), new ItemStack(Items.PAPER));
        Identifier sprite = json.has("sprite") ? Identifier.parse(json.get("sprite").getAsString()) : null;
        int color = json.has("color") ? parseColor(json.get("color").getAsString(), 0xFFFFFF) : 0xFFFFFF;
        int maxSize = json.has("max_size") ? json.get("max_size").getAsInt() : 5;
        List<Activity.Option> options = new ArrayList<>();
        if (json.has("options")) {
            for (JsonElement element : json.getAsJsonArray("options")) {
                JsonObject option = element.getAsJsonObject();
                List<Activity.Value> values = new ArrayList<>();
                for (JsonElement v : option.getAsJsonArray("values")) {
                    JsonObject value = v.getAsJsonObject();
                    List<String> commands = new ArrayList<>();
                    if (value.has("commands")) {
                        for (JsonElement c : value.getAsJsonArray("commands")) {
                            String command = c.getAsString().strip();
                            if (command.startsWith("/")) command = command.substring(1);
                            if (isSafeCommand(command)) commands.add(command);
                            else Partyfinder.LOGGER.warn("Ignoring unsafe launch command \"{}\" in {}", command, id);
                        }
                    }
                    values.add(new Activity.Value(value.get("id").getAsString(), List.copyOf(commands)));
                }
                if (!values.isEmpty()) options.add(new Activity.Option(option.get("id").getAsString(), List.copyOf(values)));
            }
        }
        Activity.Launch launch = Activity.Launch.NONE;
        if (json.has("launch")) {
            try {
                launch = Activity.Launch.valueOf(json.get("launch").getAsString().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        List<String> stats = new ArrayList<>();
        if (json.has("stats")) json.getAsJsonArray("stats").forEach(e -> stats.add(e.getAsString()));
        List<String> events = new ArrayList<>();
        if (json.has("events")) json.getAsJsonArray("events").forEach(e -> events.add(e.getAsString()));
        return new Activity(id, category, icon, sprite, color, Math.max(2, maxSize), List.copyOf(options), launch, List.copyOf(stats),
                List.copyOf(events));
    }

    private static int parseColor(String value, int fallback) {
        return TextColor.parseColor(value).result().map(TextColor::getValue).orElse(fallback);
    }
}
