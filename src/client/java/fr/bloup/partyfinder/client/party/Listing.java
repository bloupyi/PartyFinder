package fr.bloup.partyfinder.client.party;

import com.google.gson.JsonElement;
import fr.bloup.partyfinder.client.activity.Activities;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

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
        Map<String, Integer> minStats,
        List<String> roles,
        ReportSummary ownerReports,
        List<Member> members,
        UUID leader,
        long createdAt,
        long updatedAt,
        String category
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
    public record Member(UUID uuid, String name, @SerializedName("stats") JsonElement rawStats, boolean finder,
                         @SerializedName("rank") JsonElement rawRank, String role) {
        /** The backend sends null while stats are loading; Gson cannot read null into a JsonObject field. */
        public JsonObject stats() {
            return rawStats != null && rawStats.isJsonObject() ? rawStats.getAsJsonObject() : null;
        }

        /** Hypixel rank looked up by the server; null for none or unknown yet. */
        public Rank rank() {
            return Rank.parse(rawRank);
        }
    }

    /**
     * Category set by the server from its official activity list (a pack cannot move a listing elsewhere); older
     * servers do not send it, then the local activities.json decides.
     */
    public String effectiveCategory() {
        return category != null ? category : Activities.get(activity).category();
    }

    /** Current Hypixel party leader as reported by the host; differs from the owner after an automatic transfer. */
    public UUID hypixelLeader() {
        return leader != null && hasMember(leader) ? leader : owner.uuid();
    }

    public boolean hasMember(UUID uuid) {
        return members.stream().anyMatch(m -> m.uuid().equals(uuid));
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

    public static final List<String> CLASSES = List.of("healer", "mage", "berserk", "archer", "tank");
    /** Kuudra roles, picked by the requester when asking to join. */
    public static final List<String> KUUDRA_ROLES = List.of("stun", "dps", "tank", "healer");

    /** Roles a listing of this category can want: dungeon classes, Kuudra roles, or none. */
    public static List<String> rolesFor(String category) {
        return switch (category) {
            case "dungeons" -> CLASSES;
            case "kuudra" -> KUUDRA_ROLES;
            default -> List.of();
        };
    }
    private static final Pattern STAT_ID = Pattern.compile("sb|cata|class|hotm|skill_[a-z]{1,16}|slayer_[a-z]{1,16}");

    private static Map<String, Integer> cleanMinStats(Map<String, Integer> raw) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (raw == null) return Map.of();
        raw.forEach((id, min) -> {
            if (result.size() < 4 && id != null && min != null && STAT_ID.matcher(id).matches() && min > 0) result.put(id, Math.min(min, 1000));
        });
        return Map.copyOf(result);
    }

    private static List<String> cleanRoles(List<String> raw) {
        if (raw == null) return List.of();
        return raw.stream().filter(r -> CLASSES.contains(r) || KUUDRA_ROLES.contains(r)).limit(39).toList();
    }

    private static List<Member> cleanMembers(List<Member> raw, Player owner) {
        List<Member> members = new ArrayList<>();
        if (raw != null) {
            for (Member member : raw) {
                if (members.size() >= 40 || member == null || member.uuid() == null) continue;
                String name = isValidName(member.name()) ? member.name() : null;
                members.add(new Member(member.uuid(), name, member.stats(), member.finder(), member.rawRank(),
                        KUUDRA_ROLES.contains(member.role()) ? member.role() : null));
            }
        }
        if (members.isEmpty()) members.add(new Member(owner.uuid(), owner.name(), null, false, null, null));
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
                cleanMinStats(raw.minStats()),
                cleanRoles(raw.roles()),
                ReportSummary.sanitize(raw.ownerReports()),
                cleanMembers(raw.members(), raw.owner()),
                raw.leader(),
                raw.createdAt(),
                raw.updatedAt(),
                raw.category() != null && ACTIVITY.matcher(raw.category()).matches() ? raw.category() : null);
    }
}
