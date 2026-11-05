package fr.bloup.partyfinder.client.activity;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import fr.bloup.partyfinder.Partyfinder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Item icons in resource pack json files. An icon is either an item id ("minecraft:diamond_pickaxe") or an object:
 * <pre>
 * {"texture": "eyJ0ZXh0dXJlcyI6..."}   custom head, base64 "Value" as given by head sites or copied from Hypixel
 * {"skin": "&lt;hash or textures.minecraft.net url&gt;"}   custom head from a skin texture
 * {"player": "Notch"}                  a player's head, by name or uuid
 * {"item": "minecraft:diamond", "glint": true, "model": "namespace:item_model"}
 * </pre>
 * "glint" and "model" also work on heads. Heads without "item" are player heads.
 */
public final class Icons {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern HASH = Pattern.compile("[0-9a-fA-F]{32,128}");
    private static final String SKIN_URL = "http://textures.minecraft.net/texture/";
    private static final int MAX_TEXTURE = 4096;

    private Icons() {
    }

    /** Parses an icon, or returns the fallback when it is missing or invalid. */
    public static ItemStack parse(JsonElement json, ItemStack fallback) {
        try {
            if (json == null || json.isJsonNull()) return fallback;
            if (json.isJsonPrimitive()) return item(json.getAsString(), fallback);
            JsonObject o = json.getAsJsonObject();
            String texture = o.has("texture") ? o.get("texture").getAsString().trim() : null;
            if (texture == null && o.has("skin")) texture = fromSkin(o.get("skin").getAsString().trim());
            ResolvableProfile profile = null;
            if (texture != null) profile = textured(texture);
            else if (o.has("player")) profile = player(o.get("player").getAsString().trim());
            boolean head = texture != null || o.has("player");
            ItemStack stack = o.has("item") ? item(o.get("item").getAsString(), ItemStack.EMPTY)
                    : head ? new ItemStack(Items.PLAYER_HEAD) : ItemStack.EMPTY;
            if (stack.isEmpty() || head && profile == null) return fallback;
            if (profile != null) stack.set(DataComponents.PROFILE, profile);
            if (o.has("glint")) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, o.get("glint").getAsBoolean());
            if (o.has("model")) {
                Identifier model = Identifier.tryParse(o.get("model").getAsString());
                if (model != null) stack.set(DataComponents.ITEM_MODEL, model);
            }
            return stack;
        } catch (Exception e) {
            Partyfinder.LOGGER.warn("Invalid icon {}: {}", json, e.toString());
            return fallback;
        }
    }

    /** A head showing the given base64 texture value; null if it is not one. */
    public static ResolvableProfile textured(String texture) {
        if (texture.isEmpty() || texture.length() > MAX_TEXTURE) return null;
        try {
            Base64.getDecoder().decode(texture);
        } catch (IllegalArgumentException e) {
            return null;
        }
        PropertyMap properties = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", texture)));
        UUID id = UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8));
        return ResolvableProfile.createResolved(new GameProfile(id, "", properties));
    }

    private static String fromSkin(String skin) {
        String hash = skin.startsWith(SKIN_URL) ? skin.substring(SKIN_URL.length())
                : skin.startsWith("https://textures.minecraft.net/texture/") ? skin.substring(SKIN_URL.length() + 1) : skin;
        if (!HASH.matcher(hash).matches()) return null;
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + SKIN_URL + hash + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static ResolvableProfile player(String player) {
        if (NAME.matcher(player).matches()) return ResolvableProfile.createUnresolved(player);
        try {
            return ResolvableProfile.createUnresolved(UUID.fromString(player));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static ItemStack item(String id, ItemStack fallback) {
        Identifier key = Identifier.tryParse(id);
        if (key == null) return fallback;
        return BuiltInRegistries.ITEM.getOptional(key).map(ItemStack::new).orElse(fallback);
    }
}
