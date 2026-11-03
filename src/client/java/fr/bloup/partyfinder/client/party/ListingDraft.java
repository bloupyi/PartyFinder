package fr.bloup.partyfinder.client.party;

import java.util.List;
import java.util.Map;

/** Everything a leader sets when publishing a listing; also what presets and the local history store. */
public record ListingDraft(
        String activity,
        Map<String, String> options,
        String note,
        String requirements,
        int maxSize,
        int maxReports,
        Map<String, Integer> minStats,
        List<String> roles
) {
    public static ListingDraft of(Listing listing) {
        return new ListingDraft(listing.activity(), listing.options(), listing.note(), listing.requirements(), listing.maxSize(),
                listing.maxReports(), listing.minStats(), listing.roles());
    }

    /** Fills nulls left by older or hand-edited files. */
    public ListingDraft normalized() {
        return new ListingDraft(activity, options == null ? Map.of() : Map.copyOf(options), note == null ? "" : note,
                requirements == null ? "" : requirements, Math.clamp(maxSize, 2, 40), Math.clamp(maxReports, Listing.NO_REPORT_LIMIT, 50),
                minStats == null ? Map.of() : Map.copyOf(minStats), roles == null ? List.of() : List.copyOf(roles));
    }
}
