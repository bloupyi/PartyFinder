package fr.bloup.partyfinder.client.party;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.bloup.partyfinder.Partyfinder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Local cache of listing settings: the last one, the last one per activity, and named presets. */
public final class ListingDrafts {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("partyfinder_listings.json");
    private static final int MAX_PRESETS = 32;

    public record Preset(String name, ListingDraft draft) {
    }

    private static final class Data {
        ListingDraft last;
        Map<String, ListingDraft> byActivity = new LinkedHashMap<>();
        List<Preset> presets = new ArrayList<>();
    }

    private static Data data;

    private ListingDrafts() {
    }

    private static Data data() {
        if (data != null) return data;
        data = new Data();
        if (Files.exists(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH)) {
                Data loaded = GSON.fromJson(reader, Data.class);
                if (loaded != null) data = loaded;
                if (data.byActivity == null) data.byActivity = new LinkedHashMap<>();
                if (data.presets == null) data.presets = new ArrayList<>();
                data.presets.removeIf(p -> p == null || p.name() == null || p.draft() == null || p.draft().activity() == null);
            } catch (Exception e) {
                Partyfinder.LOGGER.error("Failed to read {}", PATH, e);
            }
        }
        return data;
    }

    private static void save() {
        try (Writer writer = Files.newBufferedWriter(PATH)) {
            GSON.toJson(data(), writer);
        } catch (Exception e) {
            Partyfinder.LOGGER.error("Failed to write {}", PATH, e);
        }
    }

    public static void remember(ListingDraft draft) {
        data().last = draft;
        data().byActivity.put(draft.activity(), draft);
        save();
    }

    public static Optional<ListingDraft> last() {
        return Optional.ofNullable(data().last).filter(d -> d.activity() != null).map(ListingDraft::normalized);
    }

    public static Optional<ListingDraft> lastFor(String activity) {
        return Optional.ofNullable(data().byActivity.get(activity)).map(ListingDraft::normalized);
    }

    public static List<Preset> presets() {
        return List.copyOf(data().presets);
    }

    public static void savePreset(String name, ListingDraft draft) {
        data().presets.removeIf(p -> p.name().equalsIgnoreCase(name));
        data().presets.addFirst(new Preset(name, draft));
        while (data().presets.size() > MAX_PRESETS) data().presets.removeLast();
        save();
    }

    public static void deletePreset(String name) {
        data().presets.removeIf(p -> p.name().equals(name));
        save();
    }
}
