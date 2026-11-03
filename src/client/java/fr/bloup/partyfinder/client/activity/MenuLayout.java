package fr.bloup.partyfinder.client.activity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.bloup.partyfinder.Partyfinder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public record MenuLayout(Identifier texture, int rows, ItemStack filler, List<Slot> slots) {
    private static final Identifier FILE = Identifier.fromNamespaceAndPath(Partyfinder.MOD_ID, "partyfinder/menu.json");
    private static final Identifier DEFAULT_TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");

    public enum Action { CATEGORY, ALL, CREATE, REQUESTS, CLOSE, MODERATION, RECONNECT }

    public record Slot(int index, Action action, String category, ItemStack icon) {
    }

    public static MenuLayout load() {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(FILE);
        if (resource.isPresent()) {
            try (Reader reader = resource.get().openAsReader()) {
                return parse(JsonParser.parseReader(reader).getAsJsonObject());
            } catch (Exception e) {
                Partyfinder.LOGGER.error("Failed to load {}", FILE, e);
            }
        }
        return new MenuLayout(DEFAULT_TEXTURE, 4, ItemStack.EMPTY, List.of());
    }

    private static MenuLayout parse(JsonObject json) {
        Identifier texture = json.has("texture") ? Identifier.parse(json.get("texture").getAsString()) : DEFAULT_TEXTURE;
        int rows = json.has("rows") ? Math.clamp(json.get("rows").getAsInt(), 1, 6) : 4;
        ItemStack filler = json.has("filler") ? item(json.get("filler").getAsString()) : ItemStack.EMPTY;
        List<Slot> slots = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("slots")) {
            JsonObject slot = element.getAsJsonObject();
            int index = slot.get("slot").getAsInt();
            if (index < 0 || index >= rows * 9) continue;
            Action action = Action.valueOf(slot.get("action").getAsString().toUpperCase(Locale.ROOT));
            String category = slot.has("category") ? slot.get("category").getAsString() : "";
            ItemStack icon = slot.has("icon") ? item(slot.get("icon").getAsString()) : new ItemStack(Items.PAPER);
            slots.add(new Slot(index, action, category, icon));
        }
        return new MenuLayout(texture, rows, filler, List.copyOf(slots));
    }

    private static ItemStack item(String id) {
        return BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }
}
