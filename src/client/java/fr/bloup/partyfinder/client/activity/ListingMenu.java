package fr.bloup.partyfinder.client.activity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.bloup.partyfinder.Partyfinder;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * listing_menu.json: layout of the chest listing style. Keys left out keep the defaults (Hypixel's Party Finder look);
 * texture and filler default to the ones of menu.json.
 */
public record ListingMenu(Identifier texture, int rows, ItemStack filler, PackSound openSound, List<Integer> listingSlots,
                          boolean headIcons, boolean ownGlint, boolean fullMarker, int emptySlot, ItemStack emptyIcon,
                          Map<Button, ButtonSlot> buttons) {
    public enum Button {
        PREVIOUS, NEXT, BACK, CLOSE, CREATE, REQUESTS, LEAVE, TAGS;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public record ButtonSlot(int slot, ItemStack icon) {
    }

    private static final Identifier FILE = Identifier.fromNamespaceAndPath(Partyfinder.MOD_ID, "partyfinder/listing_menu.json");
    private static final int DEFAULT_ROWS = 6;

    public static ListingMenu load(MenuLayout menu) {
        JsonObject json = PackJson.merged(Minecraft.getInstance().getResourceManager(), FILE);
        try {
            return parse(json, menu);
        } catch (Exception e) {
            Partyfinder.LOGGER.error("Invalid {}", FILE, e);
            return parse(new JsonObject(), menu);
        }
    }

    private static ListingMenu parse(JsonObject json, MenuLayout menu) {
        Identifier texture = json.has("texture") ? Identifier.parse(json.get("texture").getAsString()) : menu.texture();
        int rows = json.has("rows") ? Math.clamp(json.get("rows").getAsInt(), 2, 6) : DEFAULT_ROWS;
        int size = rows * 9;
        ItemStack filler = json.has("filler") ? Icons.parse(json.get("filler"), ItemStack.EMPTY) : menu.filler();
        PackSound openSound = PackSound.parse(json.get("open_sound"));

        List<Integer> slots = new ArrayList<>();
        if (json.has("listing_slots") && json.get("listing_slots").isJsonArray()) {
            for (JsonElement e : json.getAsJsonArray("listing_slots")) {
                int slot = e.getAsInt();
                if (slot >= 0 && slot < size && !slots.contains(slot)) slots.add(slot);
            }
        }
        if (slots.isEmpty()) {
            // Inner area: every row but the first and the last, without the side columns.
            for (int row = 1; row < rows - 1; row++) for (int col = 1; col < 8; col++) slots.add(row * 9 + col);
            if (slots.isEmpty()) for (int slot = 0; slot < size - 9; slot++) slots.add(slot);
        }

        boolean heads = !json.has("listing_icon") || !json.get("listing_icon").getAsString().equals("activity");
        boolean ownGlint = !json.has("own_glint") || json.get("own_glint").getAsBoolean();
        boolean fullMarker = !json.has("full_marker") || json.get("full_marker").getAsBoolean();

        int bottom = size - 9;
        int emptySlot = slots.contains(22) ? 22 : slots.get(slots.size() / 2);
        if (json.has("empty_slot")) emptySlot = Math.clamp(json.get("empty_slot").getAsInt(), 0, size - 1);
        ItemStack emptyIcon = Icons.parse(json.get("empty_icon"), new ItemStack(Items.MAP));

        Map<Button, ButtonSlot> buttons = new EnumMap<>(Button.class);
        defaults(buttons, bottom);
        if (json.has("buttons") && json.get("buttons").isJsonObject()) {
            JsonObject b = json.getAsJsonObject("buttons");
            for (Button button : Button.values()) {
                JsonElement element = b.get(button.id());
                if (element == null) continue;
                if (element.isJsonNull()) {
                    buttons.remove(button);
                    continue;
                }
                JsonObject o = element.getAsJsonObject();
                ButtonSlot current = buttons.get(button);
                int slot = o.has("slot") ? o.get("slot").getAsInt() : current.slot();
                ItemStack icon = o.has("icon") ? Icons.parse(o.get("icon"), current.icon()) : current.icon();
                if (slot >= 0 && slot < size) buttons.put(button, new ButtonSlot(slot, icon));
            }
        }
        return new ListingMenu(texture, rows, filler, openSound, List.copyOf(slots), heads, ownGlint, fullMarker, emptySlot,
                emptyIcon, Map.copyOf(buttons));
    }

    private static void defaults(Map<Button, ButtonSlot> buttons, int bottom) {
        buttons.put(Button.PREVIOUS, new ButtonSlot(bottom, new ItemStack(Items.ARROW)));
        buttons.put(Button.TAGS, new ButtonSlot(bottom + 1, new ItemStack(Items.NAME_TAG)));
        buttons.put(Button.BACK, new ButtonSlot(bottom + 3, new ItemStack(Items.ARROW)));
        buttons.put(Button.CLOSE, new ButtonSlot(bottom + 4, new ItemStack(Items.BARRIER)));
        buttons.put(Button.CREATE, new ButtonSlot(bottom + 5, new ItemStack(Items.WRITABLE_BOOK)));
        buttons.put(Button.REQUESTS, new ButtonSlot(bottom + 6, new ItemStack(Items.PAPER)));
        buttons.put(Button.LEAVE, new ButtonSlot(bottom + 7, new ItemStack(Items.IRON_DOOR)));
        buttons.put(Button.NEXT, new ButtonSlot(bottom + 8, new ItemStack(Items.ARROW)));
    }

    /** Button drawn at this slot, if any. */
    public Optional<Button> buttonAt(int slot) {
        for (Map.Entry<Button, ButtonSlot> e : buttons.entrySet()) if (e.getValue().slot() == slot) return Optional.of(e.getKey());
        return Optional.empty();
    }
}
