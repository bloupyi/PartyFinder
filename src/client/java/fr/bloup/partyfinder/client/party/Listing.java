package fr.bloup.partyfinder.client.party;

import java.util.UUID;
import java.util.regex.Pattern;

public record Listing(
        String id,
        Player owner,
        String activity,
        String note,
        String requirements,
        int size,
        int maxSize,
        int maxReports,
        ReportSummary ownerReports,
        long createdAt,
        long updatedAt
) {
    public static final int NO_REPORT_LIMIT = -1;

    private static final Pattern ID = Pattern.compile("[0-9a-f-]{1,36}");
    private static final Pattern ACTIVITY = Pattern.compile("[a-z0-9_]{1,32}");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern UNSAFE_CHARS = Pattern.compile("[\\p{Cc}\\p{Cf}§]");

    public record Player(UUID uuid, String name) {
        public boolean isValid() {
            return uuid != null && isValidName(name);
        }
    }

    public boolean isFull() {
        return size >= maxSize;
    }

    public boolean hasReportLimit() {
        return maxReports >= 0;
    }

    public boolean accepts(ReportSummary reports) {
        return !hasReportLimit() || reports.count() <= maxReports;
    }

    public static boolean isValidName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    public static String cleanText(String text, int maxLength) {
        if (text == null) return "";
        String cleaned = UNSAFE_CHARS.matcher(text).replaceAll("").strip();
        return cleaned.length() > maxLength ? cleaned.substring(0, maxLength) : cleaned;
    }

    public static Listing sanitize(Listing raw) {
        if (raw == null || raw.id() == null || !ID.matcher(raw.id()).matches()) return null;
        if (raw.owner() == null || !raw.owner().isValid()) return null;
        if (raw.activity() == null || !ACTIVITY.matcher(raw.activity()).matches()) return null;
        int maxSize = Math.clamp(raw.maxSize(), 2, 40);
        return new Listing(
                raw.id(),
                raw.owner(),
                raw.activity(),
                cleanText(raw.note(), 96),
                cleanText(raw.requirements(), 48),
                Math.clamp(raw.size(), 1, maxSize),
                maxSize,
                Math.clamp(raw.maxReports(), NO_REPORT_LIMIT, 50),
                ReportSummary.sanitize(raw.ownerReports()),
                raw.createdAt(),
                raw.updatedAt());
    }
}
