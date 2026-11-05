package fr.bloup.partyfinder.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.bloup.partyfinder.Partyfinder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class PartyFinderConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("partyfinder.json");

    private static PartyFinderConfig instance = new PartyFinderConfig();

    public boolean autoAcceptInvites = true;
    // Hypixel's framed party messages in finder parties are replaced by one-line ones (hidden from display only).
    public boolean cleanPartyChat = true;
    // Party Leader side: /pf warp and /pf ptme requests from party members are asked (toast + chat) unless these are on.
    public boolean autoAcceptWarps = false;
    public boolean autoAcceptTransfers = false;
    public boolean notifyJoinRequests = true;
    public boolean notifyPartyFull = true;
    public Map<String, Boolean> eventAlerts = new LinkedHashMap<>();
    public int eventLeadMinutes = 5;
    public ListingStyle listingStyle = ListingStyle.LIST;
    public Hud hud = new Hud();

    /** LIST: scrolling rows with a search box; CHEST: Hypixel Party Finder style chest with one head per party. */
    public enum ListingStyle { LIST, CHEST }

    public enum Anchor { TOP_LEFT, TOP_RIGHT, CENTER_LEFT, CENTER_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    public enum Layout { VERTICAL, HORIZONTAL }

    public enum Side { LEFT, RIGHT }

    public enum Portrait { HEAD, MODEL, NONE }

    public enum HealthText { NUMBERS, PERCENT, NONE }

    public static class Hud {
        public boolean enabled = true;
        public boolean onlyOnSkyBlock = true;
        public boolean showSelf = true;
        public boolean hideWhenSolo = true;
        public Anchor anchor = Anchor.TOP_LEFT;
        public int offsetX = 4;
        public int offsetY = 4;
        public Layout layout = Layout.VERTICAL;
        public Side side = Side.LEFT;
        public int spacing = 2;
        public int scale = 100;
        public int maxMembers = 8;
        public boolean compact = false;
        public Portrait portrait = Portrait.HEAD;
        public int modelRotation = 25;
        public boolean showName = true;
        public boolean showLeader = true;
        public boolean showHealthBar = true;
        public HealthText healthText = HealthText.NUMBERS;
        public boolean showManaBar = true;
        public boolean showDefense = false;
        public boolean showLocation = true;
        public boolean showDistance = true;
        public boolean showStats = true;
        public boolean showHeldItem = false;
        public boolean showDungeonClass = true;
        public boolean showArmor = false;
        public boolean shareStatus = true;
        public int backgroundOpacity = 70;
        public boolean textShadow = true;
        public int localRange = 48;

        void sanitize() {
            Hud defaults = new Hud();
            if (anchor == null) anchor = defaults.anchor;
            if (layout == null) layout = defaults.layout;
            if (side == null) side = defaults.side;
            if (portrait == null) portrait = defaults.portrait;
            if (healthText == null) healthText = defaults.healthText;
            scale = Math.clamp(scale, 50, 200);
            maxMembers = Math.clamp(maxMembers, 1, 20);
            spacing = Math.clamp(spacing, 0, 20);
            backgroundOpacity = Math.clamp(backgroundOpacity, 0, 100);
            localRange = Math.clamp(localRange, 0, 128);
            modelRotation = Math.clamp(modelRotation, -90, 90);
        }
    }

    public static PartyFinderConfig get() {
        return instance;
    }

    public static void load() {
        if (Files.exists(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH)) {
                PartyFinderConfig loaded = GSON.fromJson(reader, PartyFinderConfig.class);
                if (loaded != null) instance = loaded;
                if (instance.hud == null) instance.hud = new Hud();
                if (instance.eventAlerts == null) instance.eventAlerts = new LinkedHashMap<>();
                instance.eventLeadMinutes = Math.clamp(instance.eventLeadMinutes, 0, 30);
                instance.hud.sanitize();
            } catch (Exception e) {
                Partyfinder.LOGGER.error("Failed to read {}", PATH, e);
            }
        }
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            Partyfinder.LOGGER.error("Failed to write {}", PATH, e);
        }
    }
}
