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

public class PartyFinderConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("partyfinder.json");

    private static PartyFinderConfig instance = new PartyFinderConfig();

    public String backendUrl = "ws://localhost:8765";
    public boolean allowInsecureBackend = false;
    public boolean hypixelOnly = true;
    public boolean showMenuButton = true;
    public boolean autoAcceptInvites = true;
    public boolean notifyJoinRequests = true;

    public static PartyFinderConfig get() {
        return instance;
    }

    public static void load() {
        if (Files.exists(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH)) {
                PartyFinderConfig loaded = GSON.fromJson(reader, PartyFinderConfig.class);
                if (loaded != null) instance = loaded;
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
