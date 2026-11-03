package fr.bloup.partyfinder.client.party;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public record Listing(
        String id,
        Player owner,
        String activity,
        Map<String, String> options,
        String note,
        String requirements,
        int size,
        int maxSize,
        int maxReports,
        ReportSummary ownerReports,
        List<Member> members,
        long createdAt,
        long updatedAt
) {
    public static final int NO_REPORT_LIMIT = -1;

    private static final Pattern ID = Pattern.compile("[0-9a-f-]{1,36}");
    private static final Pattern ACTIVITY = Pattern.compile("[a-z0-9_]{1,32}");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern FORMATTING = Pattern.compile("§.", Pattern.DOTALL);
    private static final Pattern UNSAFE_CHARS = Pattern.compile("[\\p{Cc}\\p{Cf}§]");

    public record Player(UUID uuid, String name) {
        public boolean isValid() {
            return uuid != null && isValidName(name);
        }
    }

    /** A member of the host's real Hypixel party; stats is the backend's compact SkyBlock summary (may be null). */
    public record Member(UUID uuid, String name, JsonObject stats, boolean finder) {
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
        String cleaned = UNSAFE_CHARS.matcher(FORMATTING.matcher(text).replaceAll("")).replaceAll("").strip();
        return cleaned.length() > maxLength ? cleaned.substring(0, maxLength) : cleaned;
    }

    private static Map<String, String> cleanOptions(Map<String, String> raw) {
        Map<String, String> options = new LinkedHashMap<>();
        if (raw == null) return Map.of();
        raw.forEach((key, value) -> {
            if (options.size() < 4 && key != null && value != null && ACTIVITY.matcher(key).matches() && ACTIVITY.matcher(value).matches()) {
                options.put(key, value);
            }
        });
        return Map.copyOf(options);
    }

    private static List<Member> cleanMembers(List<Member> raw, Player owner) {
        List<Member> members = new ArrayList<>();
        if (raw != null) {
            for (Member member : raw) {
                if (members.size() >= 40 || member == null || member.uuid() == null) continue;
                String name = isValidName(member.name()) ? member.name() : null;
                members.add(new Member(member.uuid(), name, member.stats(), member.finder()));
            }
        }
        if (members.isEmpty()) members.add(new Member(owner.uuid(), owner.name(), null, false));
        return List.copyOf(members);
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
                cleanOptions(raw.options()),
                cleanText(raw.note(), 96),
                cleanText(raw.requirements(), 48),
                Math.clamp(raw.size(), 1, maxSize),
                maxSize,
                Math.clamp(raw.maxReports(), NO_REPORT_LIMIT, 50),
                ReportSummary.sanitize(raw.ownerReports()),
                cleanMembers(raw.members(), raw.owner()),
                raw.createdAt(),
                raw.updatedAt());
    }
}
