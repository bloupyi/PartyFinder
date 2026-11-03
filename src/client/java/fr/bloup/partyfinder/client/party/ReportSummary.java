package fr.bloup.partyfinder.client.party;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public record ReportSummary(int count, Map<String, Integer> reasons) {
    public static final ReportSummary NONE = new ReportSummary(0, Map.of());
    public static final List<String> REASONS = List.of("spam", "scam", "toxic", "afk", "left", "griefing", "other");
    private static final Pattern REASON = Pattern.compile("[a-z_]{1,16}");

    public static ReportSummary sanitize(ReportSummary raw) {
        if (raw == null) return NONE;
        Map<String, Integer> reasons = new LinkedHashMap<>();
        if (raw.reasons() != null) {
            raw.reasons().forEach((reason, count) -> {
                if (reasons.size() < 16 && reason != null && count != null && REASON.matcher(reason).matches() && count > 0) {
                    reasons.put(reason, Math.min(count, 9999));
                }
            });
        }
        return new ReportSummary(Math.clamp(raw.count(), 0, 9999), Map.copyOf(reasons));
    }

    public static Component reasonName(String reason) {
        return Component.translatable("partyfinder.report.reason." + reason);
    }

    public List<Component> describe() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("partyfinder.report.count", count)
                .withStyle(count > 0 ? ChatFormatting.RED : ChatFormatting.GREEN));
        reasons.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> lines.add(Component.literal(" - ").append(reasonName(e.getKey()))
                        .append(" x" + e.getValue()).withStyle(ChatFormatting.GRAY)));
        return lines;
    }
}
