package fr.bloup.partyfinder.client.hud;

import fr.bloup.partyfinder.client.config.PartyFinderConfig.Hud;
import fr.bloup.partyfinder.client.party.MemberStatus;
import fr.bloup.partyfinder.client.party.PartyStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Real party when there is one, otherwise sample members so the layout can be tuned while solo. */
final class HudPreview {
    private static final UUID SAMPLE_A = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final UUID SAMPLE_B = UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6");
    private static final List<MemberStatus.Gear> SAMPLE_GEAR = List.of(
            MemberStatus.Gear.of(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET)),
            MemberStatus.Gear.of(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE)),
            MemberStatus.Gear.of(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS)),
            MemberStatus.Gear.of(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS)),
            MemberStatus.Gear.of(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD)));

    private HudPreview() {
    }

    static List<PartyHud.Member> members(Hud cfg) {
        Hud forced = copyShowing(cfg);
        List<PartyHud.Member> real = PartyHud.collect(forced);
        if (real.size() > 1) return real;
        List<PartyHud.Member> members = new ArrayList<>(real);
        var player = Minecraft.getInstance().player;
        if (members.isEmpty() && player != null && cfg.showSelf) {
            members.add(new PartyHud.Member(player.getUUID(), player.getGameProfile().name(), true, true, PartyStatus.self(),
                    player, -1, List.of(Component.literal("Tank 31"), Component.literal("Cata 40")), true));
        }
        // A sample leader when nobody shown leads, so the leader mark can be previewed while solo.
        boolean sampleLeader = members.stream().noneMatch(PartyHud.Member::leader);
        members.add(new PartyHud.Member(SAMPLE_A, "Notch", sampleLeader, false,
                new MemberStatus("Notch", 1f, 3000, 3000, 180, 600, 820, 0.2f, "mining_3", "Dwarven Mines", SAMPLE_GEAR, false),
                null, 18, List.of(Component.literal("Mage 38"), Component.literal("Cata 42")), true));
        members.add(new PartyHud.Member(SAMPLE_B, "jeb_", false, false, null, null, -1, List.of(), false));
        return members.subList(0, Math.min(members.size(), cfg.maxMembers));
    }

    private static Hud copyShowing(Hud cfg) {
        Hud copy = new Hud();
        copy.showSelf = cfg.showSelf;
        copy.maxMembers = cfg.maxMembers;
        copy.localRange = cfg.localRange;
        copy.hideWhenSolo = false;
        return copy;
    }
}
