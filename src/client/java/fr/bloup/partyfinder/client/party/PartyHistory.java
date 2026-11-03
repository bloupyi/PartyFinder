package fr.bloup.partyfinder.client.party;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import fr.bloup.partyfinder.Partyfinder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Finder parties the player took part in, kept locally to play again with the same people or rate them. */
public final class PartyHistory {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("partyfinder_history.json");
    private static final int MAX_RUNS = 50;
    private static final long DUPLICATE_WINDOW_MS = 5 * 60_000;

    public record Run(String id, long at, boolean host, ListingDraft draft, List<Listing.Player> members) {
    }

    private static List<Run> runs;

    private PartyHistory() {
    }

    private static List<Run> runs() {
        if (runs != null) return runs;
        runs = new ArrayList<>();
        if (Files.exists(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH)) {
                List<Run> loaded = GSON.fromJson(reader, new TypeToken<List<Run>>() {
                }.getType());
                if (loaded != null) {
                    loaded.stream().filter(r -> r != null && r.draft() != null && r.draft().activity() != null && r.members() != null)
                            .forEach(runs::add);
                }
            } catch (Exception e) {
                Partyfinder.LOGGER.error("Failed to read {}", PATH, e);
            }
        }
        return runs;
    }

    public static List<Run> all() {
        return List.copyOf(runs());
    }

    /**
     * Records the listing as it was last seen, if other players were in it. Runs are recorded at the end of each
     * instance (the party usually stays together), so a later record of the same listing within a few minutes is
     * a duplicate.
     */
    public static void record(Listing listing, UUID self) {
        List<Listing.Player> members = listing.members().stream()
                .filter(m -> !m.uuid().equals(self) && m.name() != null)
                .map(m -> new Listing.Player(m.uuid(), m.name()))
                .toList();
        if (members.isEmpty()) return;
        long now = System.currentTimeMillis();
        boolean duplicate = runs().stream().anyMatch(r -> r.id().startsWith(listing.id()) && now - r.at() < DUPLICATE_WINDOW_MS
                && r.members().equals(members));
        if (duplicate) return;
        runs().addFirst(new Run(listing.id() + "@" + now, now, listing.owner().uuid().equals(self),
                ListingDraft.of(listing), members));
        while (runs().size() > MAX_RUNS) runs().removeLast();
        try (Writer writer = Files.newBufferedWriter(PATH)) {
            GSON.toJson(runs(), writer);
        } catch (Exception e) {
            Partyfinder.LOGGER.error("Failed to write {}", PATH, e);
        }
    }
}
