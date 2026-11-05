package fr.bloup.partyfinder.client.party;

import fr.bloup.partyfinder.client.hud.DungeonTab;
import fr.bloup.partyfinder.client.net.BackendConnection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Own stat changes seen live (dungeon class picked, level ups, the Skills menu), sent to the backend so listings show them right
 * away instead of after the Hypixel API cache expires.
 */
public final class LiveStats {
    private static final Set<String> CLASSES = Set.of("healer", "mage", "berserk", "archer", "tank");
    private static final Set<String> SKILLS = Set.of("mining", "fishing", "combat", "farming", "foraging", "enchanting",
            "alchemy", "taming", "hunting");
    // Anchored at the start: player chat begins with a rank, a name and a colon, or a channel prefix, never this.
    private static final Pattern LEVEL_UP = Pattern.compile(
            "^[A-Z ]*LEVEL UP\\s+([A-Za-z][A-Za-z ]*?)\\s+(?:[IVXLC]+|\\d+)?\\s*(?:➜|→|->)\\s*([IVXLC]+|\\d+)\\b");
    private static final Pattern CLASS_SELECTED = Pattern.compile(
            "^You have selected the (Healer|Mage|Berserk|Archer|Tank) Dungeon Class!?$");
    // SkyBlock's /skills menu: one item per skill named "Combat XXV" (no number below level 1).
    private static final String SKILLS_MENU = "Your Skills";
    private static final Pattern SKILL_ITEM = Pattern.compile("^([A-Za-z]+) ([IVXLC]+|\\d+)$");
    private static final long SEND_INTERVAL_MS = 3_000;
    private static final int MENU_SCAN_TICKS = 10;

    private static final Map<String, Integer> PENDING = new HashMap<>();
    private static final Map<String, Integer> SENT = new HashMap<>();
    private static String pendingClass;
    private static String sentClass;
    private static long lastSentAt;
    private static int menuTicks;

    private LiveStats() {
    }

    public static void onChat(Component message) {
        String text = message.getString().strip();
        Matcher m = CLASS_SELECTED.matcher(text);
        if (m.find()) {
            setClass(m.group(1).toLowerCase(Locale.ROOT));
            return;
        }
        m = LEVEL_UP.matcher(text);
        if (!m.find()) return;
        String id = statId(m.group(1).strip().toLowerCase(Locale.ROOT));
        int level = DungeonTab.level(m.group(2));
        if (id != null && level > 0) setLevel(id, level);
    }

    /** Requirement ids the backend understands for a level-up message's subject. */
    private static String statId(String name) {
        if (SKILLS.contains(name)) return "skill_" + name;
        if (CLASSES.contains(name)) return "class_" + name;
        return switch (name) {
            case "catacombs" -> "cata";
            case "hotm", "heart of the mountain" -> "hotm";
            default -> null;
        };
    }

    private static void setClass(String cls) {
        if (!cls.equals(sentClass)) pendingClass = cls;
    }

    private static void setLevel(String id, int level) {
        if (!Objects.equals(SENT.get(id), level)) PENDING.put(id, level);
    }

    /** Item contents arrive after the menu opens, so it is rescanned while it stays open. */
    private static void scanSkillsMenu(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> container) || !SKILLS_MENU.equals(screen.getTitle().getString().strip())) {
            menuTicks = 0;
            return;
        }
        if (menuTicks++ % MENU_SCAN_TICKS != 0) return;
        for (Slot slot : container.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            Matcher m = SKILL_ITEM.matcher(stack.getHoverName().getString().strip());
            if (!m.matches()) continue;
            String skill = m.group(1).toLowerCase(Locale.ROOT);
            int level = DungeonTab.level(m.group(2));
            if (SKILLS.contains(skill) && level > 0) setLevel("skill_" + skill, level);
        }
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        scanSkillsMenu(mc.gui.screen());
        LocalPlayer player = mc.player;
        if (player != null && DungeonTab.inDungeon()) {
            // The Catacombs tab list shows our class and its level: "[40] Name (Mage XL)".
            DungeonTab.Info tab = DungeonTab.of(player.getGameProfile().name());
            if (tab != null && tab.dungeonClass() != null) {
                setClass(tab.dungeonClass());
                if (tab.level() > 0) setLevel("class_" + tab.dungeonClass(), tab.level());
            }
        }
        if (pendingClass == null && PENDING.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (!BackendConnection.isConnected() || now - lastSentAt < SEND_INTERVAL_MS) return;
        lastSentAt = now;
        BackendConnection.selfStats(pendingClass, Map.copyOf(PENDING));
        if (pendingClass != null) sentClass = pendingClass;
        SENT.putAll(PENDING);
        pendingClass = null;
        PENDING.clear();
    }

    /** A new backend session starts without our live reports. */
    public static void onBackendReady() {
        if (pendingClass == null) pendingClass = sentClass;
        SENT.forEach(PENDING::putIfAbsent);
        sentClass = null;
        SENT.clear();
    }

    public static void reset() {
        PENDING.clear();
        SENT.clear();
        pendingClass = null;
        sentClass = null;
        lastSentAt = 0;
        menuTicks = 0;
    }
}
