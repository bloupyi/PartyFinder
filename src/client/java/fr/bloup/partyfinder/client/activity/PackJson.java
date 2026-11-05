package fr.bloup.partyfinder.client.activity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.bloup.partyfinder.Partyfinder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.Map;

/**
 * Pack json files merged key by key over every enabled pack, the mod's own file first: a pack only lists what it
 * changes. Objects merge recursively; any other value (arrays, null included) replaces the one below.
 */
final class PackJson {
    private PackJson() {
    }

    static JsonObject merged(ResourceManager manager, Identifier id) {
        JsonObject result = new JsonObject();
        for (Resource resource : manager.getResourceStack(id)) {
            try (Reader reader = resource.openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                if (json.isJsonObject()) merge(result, json.getAsJsonObject());
            } catch (Exception e) {
                Partyfinder.LOGGER.error("Failed to load {} from {}", id, resource.sourcePackId(), e);
            }
        }
        return result;
    }

    private static void merge(JsonObject into, JsonObject from) {
        for (Map.Entry<String, JsonElement> e : from.entrySet()) {
            JsonElement current = into.get(e.getKey());
            if (current != null && current.isJsonObject() && e.getValue().isJsonObject()) {
                merge(current.getAsJsonObject(), e.getValue().getAsJsonObject());
            } else {
                into.add(e.getKey(), e.getValue().deepCopy());
            }
        }
    }
}
