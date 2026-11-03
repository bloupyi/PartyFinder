package fr.bloup.partyfinder.client.hud;

import fr.bloup.partyfinder.client.config.PartyFinderConfig.Hud;
import fr.bloup.partyfinder.client.party.MemberStatus;
import fr.bloup.partyfinder.client.party.PartyStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Real party when there is one, otherwise sample members so the layout can be tuned while solo. */
final class HudPreview {
    private static final UUID SAMPLE_A = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final UUID SAMPLE_B = UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6");

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
        members.add(new PartyHud.Member(SAMPLE_A, "Notch", false, false,
                new MemberStatus("Notch", 0.42f, 1260, 3000, 180, 600, 820, "mining_3", "Dwarven Mines", List.of(), false),
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
