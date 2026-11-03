package fr.bloup.partyfinder.client.activity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.bloup.partyfinder.Partyfinder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class Activities {
    private static final Identifier ACTIVITIES = Identifier.fromNamespaceAndPath(Partyfinder.MOD_ID, "partyfinder/activities.json");
    private static final Identifier THEME = Identifier.fromNamespaceAndPath(Partyfinder.MOD_ID, "partyfinder/theme.json");

    private static final Map<String, Activity> BY_ID = new LinkedHashMap<>();
    private static final List<String> CATEGORIES = new ArrayList<>();
    private static final Map<String, Integer> COLORS = new LinkedHashMap<>();

    private Activities() {
    }

    public static void reload() {
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        BY_ID.clear();
        CATEGORIES.clear();
        COLORS.clear();
        read(manager, ACTIVITIES).ifPresent(json -> {
            for (JsonElement element : json.getAsJsonArray("activities")) {
                Activity activity = parse(element.getAsJsonObject());
                BY_ID.put(activity.id(), activity);
                if (!CATEGORIES.contains(activity.category())) CATEGORIES.add(activity.category());
            }
        });
        read(manager, THEME).ifPresent(json -> json.getAsJsonObject("colors").entrySet()
                .forEach(e -> COLORS.put(e.getKey(), parseColor(e.getValue().getAsString(), 0xFFFFFF))));
    }

    public static List<Activity> all() {
        return List.copyOf(BY_ID.values());
    }

    public static List<String> categories() {
        return List.copyOf(CATEGORIES);
    }

    public static Activity get(String id) {
        Activity activity = BY_ID.get(id);
        if (activity != null) return activity;
        return new Activity(id, "other", new ItemStack(Items.PAPER), null, 0xFFFFFF, 5);
    }

    public static int color(String key, int fallback) {
        return 0xFF000000 | COLORS.getOrDefault(key, fallback);
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
        ItemStack icon = new ItemStack(Items.PAPER);
        if (json.has("icon")) {
            icon = BuiltInRegistries.ITEM.getOptional(Identifier.parse(json.get("icon").getAsString()))
                    .map(ItemStack::new).orElse(icon);
        }
        Identifier sprite = json.has("sprite") ? Identifier.parse(json.get("sprite").getAsString()) : null;
        int color = json.has("color") ? parseColor(json.get("color").getAsString(), 0xFFFFFF) : 0xFFFFFF;
        int maxSize = json.has("max_size") ? json.get("max_size").getAsInt() : 5;
        return new Activity(id, category, icon, sprite, color, Math.max(2, maxSize));
    }

    private static int parseColor(String value, int fallback) {
        return TextColor.parseColor(value).result().map(TextColor::getValue).orElse(fallback);
    }
}
