package fr.bloup.partyfinder.client.party;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.google.common.collect.ImmutableMultimap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
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
        float absorption,
        String location,
        String map,
        List<Gear> equipment,
        boolean dead
) {
    public static final List<EquipmentSlot> SLOTS = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND);
    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]{1,32}:[a-z0-9_./-]{1,64}");
    private static final Pattern TEXTURE = Pattern.compile("[A-Za-z0-9+/=]{1,2048}");
    private static final Pattern LOCATION = Pattern.compile("[a-z0-9_]{0,32}");
    private static final Pattern SKYBLOCK_ID = Pattern.compile("[A-Za-z0-9_:;.-]{1,64}");
    private static final Pattern MODEL_STRING = Pattern.compile("[A-Za-z0-9_:;./-]{1,64}");
    private static final int MAX_MODEL_VALUES = 4;
    private static final int MAX_NAME = 48;

    /**
     * One worn or held item, reduced to what is needed to draw it: item, leather dye, head texture, glint, plus what
     * resource packs match on to retexture it (SkyBlock id in custom_data, item_model, custom_model_data, name).
     */
    public record Gear(String slot, String id, Integer color, String texture, boolean glint, String skyblockId,
                       String model, List<Float> modelFloats, List<String> modelStrings, String name) {
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
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            String skyblockId = data == null ? null : data.copyTag().getString("id").orElse(null);
            Identifier model = stack.get(DataComponents.ITEM_MODEL);
            String modelId = model == null || model.toString().equals(id) ? null : model.toString();
            CustomModelData modelData = stack.get(DataComponents.CUSTOM_MODEL_DATA);
            Component name = stack.get(DataComponents.CUSTOM_NAME);
            return new Gear(slot.getSerializedName(), id, dye == null ? null : dye.rgb(), texture, stack.hasFoil(), skyblockId,
                    modelId, modelData == null ? List.of() : modelData.floats(), modelData == null ? List.of() : modelData.strings(),
                    name == null ? null : name.getString()).sanitize();
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
            if (skyblockId != null) {
                CompoundTag tag = new CompoundTag();
                tag.putString("id", skyblockId);
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            }
            if (model != null) stack.set(DataComponents.ITEM_MODEL, Identifier.parse(model));
            if (!modelFloats.isEmpty() || !modelStrings.isEmpty()) {
                stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(modelFloats, List.of(), modelStrings, List.of()));
            }
            if (name != null) stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
            return stack;
        }

        Gear sanitize() {
            if (slot == null || id == null || !ITEM_ID.matcher(id).matches() || equipmentSlot() == null) return null;
            String safeTexture = texture != null && TEXTURE.matcher(texture).matches() ? texture : null;
            String safeId = skyblockId != null && SKYBLOCK_ID.matcher(skyblockId).matches() ? skyblockId : null;
            String safeModel = model != null && ITEM_ID.matcher(model).matches() ? model : null;
            List<Float> floats = modelFloats == null ? List.of() : modelFloats.stream()
                    .filter(f -> f != null && Float.isFinite(f)).limit(MAX_MODEL_VALUES).toList();
            List<String> strings = modelStrings == null ? List.of() : modelStrings.stream()
                    .filter(v -> v != null && MODEL_STRING.matcher(v).matches()).limit(MAX_MODEL_VALUES).toList();
            String safeName = name == null ? null : Listing.cleanText(name, MAX_NAME);
            return new Gear(slot, id, color, safeTexture, glint, safeId, safeModel, floats, strings,
                    safeName == null || safeName.isEmpty() ? null : safeName);
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

    /**
     * Absorption as a share of max health. Clients from 0.6.0 sent SkyBlock absorption as health above the max
     * instead of in this field.
     */
    public float absorptionRatio() {
        if (absorption > 0 || !hasNumbers()) return absorption;
        return Math.max(0, health - maxHealth) / (float) maxHealth;
    }

    /** Health as SkyBlock displays it: real health plus absorption. */
    public int displayedHealth() {
        return health > maxHealth ? health : health + Math.round(absorptionRatio() * maxHealth);
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
                Float.isFinite(raw.absorption()) ? Math.clamp(raw.absorption(), 0f, 10f) : 0f,
                LOCATION.matcher(location).matches() ? location : "",
                Listing.cleanText(raw.map(), 32),
                List.copyOf(gear),
                raw.dead());
    }
}
