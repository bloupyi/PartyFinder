package fr.bloup.partyfinder.client.party;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.google.common.collect.ImmutableMultimap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** Live status of a party member, either read locally or relayed by the backend from that member's own client. */
public record MemberStatus(
        String name,
        float healthRatio,
        int health,
        int maxHealth,
        int mana,
        int maxMana,
        int defense,
        String location,
        String map,
        List<Gear> equipment
) {
    public static final List<EquipmentSlot> SLOTS = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND);
    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]{1,32}:[a-z0-9_./-]{1,64}");
    private static final Pattern TEXTURE = Pattern.compile("[A-Za-z0-9+/=]{1,2048}");
    private static final Pattern LOCATION = Pattern.compile("[a-z0-9_]{0,32}");

    /** One worn or held item, reduced to what is needed to draw it: item, leather dye, head texture, glint. */
    public record Gear(String slot, String id, Integer color, String texture, boolean glint) {
        public static Gear of(EquipmentSlot slot, ItemStack stack) {
            if (stack.isEmpty()) return null;
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            DyedItemColor dye = stack.get(DataComponents.DYED_COLOR);
            ResolvableProfile profile = stack.get(DataComponents.PROFILE);
            String texture = null;
            if (profile != null) {
                texture = profile.partialProfile().properties().get("textures").stream()
                        .map(Property::value).findFirst().orElse(null);
            }
            return new Gear(slot.getSerializedName(), id, dye == null ? null : dye.rgb(), texture, stack.hasFoil());
        }

        public EquipmentSlot equipmentSlot() {
            for (EquipmentSlot s : SLOTS) if (s.getSerializedName().equals(slot)) return s;
            return null;
        }

        public ItemStack toStack() {
            ItemStack stack = BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).map(ItemStack::new).orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) return stack;
            if (color != null) stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color & 0xFFFFFF));
            if (texture != null) {
                PropertyMap properties = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", texture)));
                stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(new GameProfile(UUID.nameUUIDFromBytes(texture.getBytes()), "", properties)));
            }
            if (glint) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            return stack;
        }

        Gear sanitize() {
            if (slot == null || id == null || !ITEM_ID.matcher(id).matches() || equipmentSlot() == null) return null;
            String safeTexture = texture != null && TEXTURE.matcher(texture).matches() ? texture : null;
            return new Gear(slot, id, color, safeTexture, glint);
        }
    }

    public static List<Gear> equipmentOf(LivingEntity entity) {
        List<Gear> gear = new ArrayList<>();
        for (EquipmentSlot slot : SLOTS) {
            Gear item = Gear.of(slot, entity.getItemBySlot(slot));
            if (item != null) gear.add(item);
        }
        return gear;
    }

    public boolean hasNumbers() {
        return maxHealth > 0;
    }

    public static MemberStatus sanitize(MemberStatus raw) {
        if (raw == null) return null;
        List<Gear> gear = new ArrayList<>();
        if (raw.equipment() != null) {
            for (Gear item : raw.equipment()) {
                Gear clean = item == null ? null : item.sanitize();
                if (clean != null && gear.size() < SLOTS.size()) gear.add(clean);
            }
        }
        String location = raw.location() == null ? "" : raw.location().toLowerCase(Locale.ROOT);
        return new MemberStatus(
                Listing.isValidName(raw.name()) ? raw.name() : null,
                Math.clamp(raw.healthRatio(), 0f, 1f),
                Math.clamp(raw.health(), 0, 10_000_000),
                Math.clamp(raw.maxHealth(), 0, 10_000_000),
                Math.clamp(raw.mana(), 0, 10_000_000),
                Math.clamp(raw.maxMana(), 0, 10_000_000),
                Math.clamp(raw.defense(), 0, 10_000_000),
                LOCATION.matcher(location).matches() ? location : "",
                Listing.cleanText(raw.map(), 32),
                List.copyOf(gear));
    }
}
