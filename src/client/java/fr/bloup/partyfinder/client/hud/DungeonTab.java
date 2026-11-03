package fr.bloup.partyfinder.client.hud;

import fr.bloup.partyfinder.client.hypixel.HypixelBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dungeon class and death state of party members, read from the Catacombs tab list
 * (entries look like "[40] Name (Mage XL)" or "[40] Name (DEAD)").
 */
public final class DungeonTab {
    public record Info(String dungeonClass, int level, boolean dead) {
    }

    private static final Pattern ENTRY = Pattern.compile(
            "([A-Za-z0-9_]{1,16})\\s.*?\\((Healer|Mage|Berserk|Archer|Tank|DEAD|EMPTY)(?:\\s+([IVXLC]+|\\d+))?\\)");
    private static final long REFRESH_MS = 500;

    private static Map<String, Info> cache = Map.of();
    private static long cachedAt;

    private DungeonTab() {
    }

    public static boolean inDungeon() {
        return "dungeon".equals(HypixelBridge.mode());
    }

    public static Info of(String name) {
        if (name == null || !inDungeon()) return null;
        long now = System.currentTimeMillis();
        if (now - cachedAt > REFRESH_MS) {
            cache = parse();
            cachedAt = now;
        }
        return cache.get(name.toLowerCase(Locale.ROOT));
    }

    private static Map<String, Info> parse() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return Map.of();
        Map<String, Info> result = new HashMap<>();
        for (PlayerInfo info : connection.getListedOnlinePlayers()) {
            Component display = info.getTabListDisplayName();
            if (display == null) continue;
            Matcher m = ENTRY.matcher(display.getString());
            if (!m.find()) continue;
            String state = m.group(2);
            boolean dead = state.equals("DEAD");
            String cls = dead || state.equals("EMPTY") ? null : state.toLowerCase(Locale.ROOT);
            result.put(m.group(1).toLowerCase(Locale.ROOT), new Info(cls, level(m.group(3)), dead));
        }
        return result;
    }

    private static int level(String raw) {
        if (raw == null) return 0;
        if (Character.isDigit(raw.charAt(0))) return Integer.parseInt(raw);
        int total = 0;
        int previous = 0;
        for (int i = raw.length() - 1; i >= 0; i--) {
            int value = switch (raw.charAt(i)) {
                case 'I' -> 1;
                case 'V' -> 5;
                case 'X' -> 10;
                case 'L' -> 50;
                case 'C' -> 100;
                default -> 0;
            };
            total += value < previous ? -value : value;
            previous = Math.max(previous, value);
        }
        return total;
    }
}
