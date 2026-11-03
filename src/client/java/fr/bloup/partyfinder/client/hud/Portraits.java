package fr.bloup.partyfinder.client.hud;

import fr.bloup.partyfinder.client.party.MemberStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Client-side stand-ins for party members who are not loaded nearby: a mannequin wearing their skin and the
 * equipment their own client reported, plus resolvable profiles for head portraits.
 */
final class Portraits {
    private record Stand(Mannequin entity, List<MemberStatus.Gear> gear) {
    }

    private static final Map<UUID, Stand> STANDS = new HashMap<>();
    private static final Map<UUID, ResolvableProfile> PROFILES = new HashMap<>();
    private static final Set<UUID> BROKEN = new HashSet<>();
    // Client-only stand-ins need an entity id for rendering; negative ids never collide with server entities.
    private static int nextId = -1_000_000;
    private static ClientLevel level;

    private Portraits() {
    }

    static ResolvableProfile profile(UUID uuid) {
        return PROFILES.computeIfAbsent(uuid, ResolvableProfile::createUnresolved);
    }

    static PlayerSkin tabSkin(UUID uuid) {
        var connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(uuid);
        return info == null ? null : info.getSkin();
    }

    static Mannequin stand(UUID uuid, List<MemberStatus.Gear> gear) {
        ClientLevel current = Minecraft.getInstance().level;
        if (current == null) return null;
        if (current != level) {
            STANDS.clear();
            level = current;
        }
        Stand stand = STANDS.get(uuid);
        if (stand == null) {
            Mannequin mannequin = Mannequin.create(EntityTypes.MANNEQUIN, current);
            mannequin.setId(nextId--);
            mannequin.setComponent(DataComponents.PROFILE, profile(uuid));
            stand = new Stand(mannequin, null);
        }
        if (!Objects.equals(stand.gear(), gear)) {
            for (EquipmentSlot slot : MemberStatus.SLOTS) stand.entity().setItemSlot(slot, ItemStack.EMPTY);
            if (gear != null) {
                for (MemberStatus.Gear item : gear) {
                    EquipmentSlot slot = item.equipmentSlot();
                    if (slot != null) stand.entity().setItemSlot(slot, item.toStack());
                }
            }
            stand = new Stand(stand.entity(), gear);
        }
        STANDS.put(uuid, stand);
        return stand.entity();
    }

    static boolean isBroken(UUID uuid) {
        return BROKEN.contains(uuid);
    }

    static void markBroken(UUID uuid) {
        BROKEN.add(uuid);
    }

    static void clear() {
        BROKEN.clear();
        STANDS.clear();
        PROFILES.clear();
        level = null;
    }
}
