package fr.bloup.partyfinder.client.party;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Moderation {
    public record ReportedPlayer(UUID uuid, String name, int count, Map<String, Integer> reasons, long lastAt, boolean banned) {
        public ReportSummary summary() {
            return new ReportSummary(count, reasons == null ? Map.of() : reasons, 0);
        }
    }

    public record Report(String id, Listing.Player reporter, String reason, String comment, long at) {
    }

    public record Ban(String name, String reason, String by, long at) {
    }

    public record PlayerDetail(Listing.Player player, boolean banned, Ban ban, List<Report> reports) {
    }

    private static List<ReportedPlayer> reportedPlayers = List.of();
    private static PlayerDetail detail;

    private Moderation() {
    }

    public static List<ReportedPlayer> reportedPlayers() {
        return reportedPlayers;
    }

    public static PlayerDetail detail() {
        return detail;
    }

    public static void setReportedPlayers(List<ReportedPlayer> players) {
        reportedPlayers = List.copyOf(players);
    }

    public static void setDetail(PlayerDetail value) {
        detail = value;
    }

    public static void clear() {
        reportedPlayers = List.of();
        detail = null;
    }
}
