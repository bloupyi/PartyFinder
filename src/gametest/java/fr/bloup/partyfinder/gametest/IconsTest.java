package fr.bloup.partyfinder.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.authlib.properties.Property;
import fr.bloup.partyfinder.client.activity.Icons;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Icons in pack json files: item ids, custom heads (texture, skin, player), glint and model, invalid fallbacks. */
public class IconsTest implements FabricClientGameTest {
    private static final String HASH = "7dfa7b8ed4ae5a4b4b3a0e6e1d3e4d7b8c5f2c0d1e2f3a4b5c6d7e8f9a0b1c2d";

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            ItemStack fallback = new ItemStack(Items.PAPER);

            check(parse("\"minecraft:diamond_pickaxe\"", fallback).is(Items.DIAMOND_PICKAXE), "item id");
            check(parse("\"minecraft:not_an_item\"", fallback).is(Items.PAPER), "unknown item falls back");

            String value = Base64.getEncoder().encodeToString(
                    ("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + HASH + "\"}}}").getBytes(StandardCharsets.UTF_8));
            ItemStack textured = parse("{\"texture\":\"" + value + "\"}", fallback);
            check(textured.is(Items.PLAYER_HEAD), "texture gives a player head");
            check(value.equals(texture(textured)), "texture kept");

            ItemStack skin = parse("{\"skin\":\"http://textures.minecraft.net/texture/" + HASH + "\",\"glint\":true}", fallback);
            check(value.equals(texture(skin)), "skin url turned into the same texture");
            check(Boolean.TRUE.equals(skin.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)), "glint");
            check(value.equals(texture(parse("{\"skin\":\"" + HASH + "\"}", fallback))), "bare skin hash");

            ItemStack player = parse("{\"player\":\"Notch\"}", fallback);
            ResolvableProfile profile = player.get(DataComponents.PROFILE);
            check(player.is(Items.PLAYER_HEAD) && profile != null && profile.name().orElse("").equals("Notch"), "player head");

            ItemStack model = parse("{\"item\":\"minecraft:diamond\",\"model\":\"minecraft:emerald\"}", fallback);
            check(model.is(Items.DIAMOND) && "minecraft:emerald".equals(String.valueOf(model.get(DataComponents.ITEM_MODEL))), "item model");

            check(parse("{\"texture\":\"not base64 !\"}", fallback).is(Items.PAPER), "bad texture falls back");
            check(parse("{\"skin\":\"https://example.com/skin.png\"}", fallback).is(Items.PAPER), "foreign skin url refused");
            check(parse("{\"player\":\"bad name!\"}", fallback).is(Items.PAPER), "bad player falls back");
            check(parse("42", ItemStack.EMPTY).isEmpty(), "number falls back");
        });
    }

    private static ItemStack parse(String json, ItemStack fallback) {
        JsonElement element = JsonParser.parseString(json);
        return Icons.parse(element, fallback);
    }

    private static String texture(ItemStack stack) {
        ResolvableProfile profile = stack.get(DataComponents.PROFILE);
        if (profile == null) return null;
        return profile.partialProfile().properties().get("textures").stream().map(Property::value).findFirst().orElse(null);
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError("Icon: " + what);
    }
}
