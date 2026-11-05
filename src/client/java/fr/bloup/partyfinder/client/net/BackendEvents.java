package fr.bloup.partyfinder.client.net;

import fr.bloup.partyfinder.client.activity.Notifications;
import fr.bloup.partyfinder.client.activity.Theme;
import fr.bloup.partyfinder.client.PartyActions;
import fr.bloup.partyfinder.client.PartyfinderClient;
import fr.bloup.partyfinder.client.config.PartyFinderConfig;
import fr.bloup.partyfinder.client.party.InviteAccept;
import fr.bloup.partyfinder.client.party.Listing;
import fr.bloup.partyfinder.client.party.Moderation;
import fr.bloup.partyfinder.client.activity.Activities;
import fr.bloup.partyfinder.client.activity.StatFormat;
import fr.bloup.partyfinder.client.party.PartyFinderState;
import fr.bloup.partyfinder.client.party.PartySync;
import fr.bloup.partyfinder.client.party.Rank;
import fr.bloup.partyfinder.client.party.ReportSummary;
import fr.bloup.partyfinder.client.party.StatsCard;
import fr.bloup.partyfinder.client.activity.Activity;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.stream.Collectors;

public final class BackendEvents {
    private static final String KEY_PATTERN = "[a-z_]{1,32}";

    private BackendEvents() {
    }

    public static void onJoinRequest(PartyFinderState.JoinRequest request) {
        if (PartyFinderState.ownListing().isEmpty()) return;
        PartyFinderState.addJoinRequest(request);
        if (!PartyFinderConfig.get().notifyJoinRequests) return;
        Listing.Player from = request.player();
        Notifications.toast("join_request",
                Component.translatable("partyfinder.toast.join_request.title"),
                Component.translatable("partyfinder.toast.join_request.keys", from.name(),
                        PartyfinderClient.acceptKey.getTranslatedKeyMessage(), PartyfinderClient.denyKey.getTranslatedKeyMessage()));
        Listing own = PartyFinderState.ownListing().orElse(null);
        Activity activity = Activities.get(own != null ? own.activity() : "");
        String category = own != null ? own.effectiveCategory() : activity.category();
        Component card = StatsCard.card(from.name(), request.rank(), request.stats(), request.reports(), request.unverified(),
                activity, category, request.role());
        HoverEvent hover = new HoverEvent.ShowText(card);
        MutableComponent name = Rank.name(request.rank(), from.name()).withStyle(s -> s.withHoverEvent(hover));
        if (request.reports().count() > 0) name.append(" ").append(reportBadge(request.reports()));
        Component headline = request.unverified()
                ? Component.literal("(").append(Component.translatable("partyfinder.requests.unverified")).append(")")
                .withColor(Theme.rgb("chat_notice"))
                : StatsCard.headline(activity, category, request.stats(), request.role());
        MutableComponent seconds = Component.literal(String.valueOf(PartyFinderState.JOIN_REQUEST_TTL_MS / 1000))
                .withColor(Theme.rgb("chat_error"));
        MutableComponent time = Component.translatable("partyfinder.chat.join_request.time", seconds).withColor(Theme.rgb("chat_notice"));
        if (headline != null) time.append(" ").append(headline);
        Component inside = Component.translatable("partyfinder.chat.join_request", name).withColor(Theme.rgb("chat_notice"))
                .append("\n").append(time);
        Component after = Component.translatable("partyfinder.chat.hover_stats")
                .withStyle(s -> s.withColor(Theme.rgb("chat_detail")).withHoverEvent(hover))
                .append("\n")
                .append(chatButton("partyfinder.chat.accept", "chat_button_accept", "/partyfinder accept " + from.name()))
                .append(" ")
                .append(chatButton("partyfinder.chat.deny", "chat_button_decline", "/partyfinder deny " + from.name()));
        PartyActions.framed(inside, after);
    }

    public static void onJoinSent(Listing.Player owner) {
        PartyFinderState.markRequested(owner.uuid());
        Notifications.sound("join_sent");
        PartyActions.chat(Component.translatable("partyfinder.chat.join_sent", owner.name()));
    }

    public static void onJoinResponse(String listingId, Listing.Player owner, String inviter, boolean accept) {
        if (!PartyFinderState.consumeRequested(owner.uuid())) return;
        Notifications.sound(accept ? "join_accepted" : "join_denied");
        if (!accept) {
            PartyActions.chat(Component.translatable("partyfinder.chat.join_denied", owner.name()).withColor(Theme.rgb("chat_error")));
            return;
        }
        PartySync.onJoinAccepted(listingId);
        PartyFinderConfig config = PartyFinderConfig.get();
        // With auto-accept and the clean party chat, Hypixel's "You have joined ..." follows right away and says it.
        if (!config.autoAcceptInvites || !config.cleanPartyChat) {
            PartyActions.chat(Component.translatable("partyfinder.chat.join_accepted", owner.name()).withColor(Theme.rgb("chat_success")));
        }
        if (config.autoAcceptInvites) {
            InviteAccept.expect(Listing.isValidName(inviter) ? inviter : owner.name(), owner.name());
        }
    }

    public static void onReportNew(Listing.Player target, Moderation.Report report, int count) {
        if (!PartyFinderState.isOp()) return;
        Notifications.toast("report",
                Component.translatable("partyfinder.toast.report.title"),
                Component.translatable("partyfinder.toast.report.body", target.name(), ReportSummary.reasonName(report.reason())));
        MutableComponent line = Component.translatable("partyfinder.chat.report_new",
                        report.reporter().name(), target.name(), ReportSummary.reasonName(report.reason()), count)
                .withColor(Theme.rgb("chat_detail"));
        if (!report.comment().isEmpty()) {
            line.append(Component.literal(" \"" + report.comment() + "\"").withColor(Theme.rgb("chat_muted")));
        }
        PartyActions.chat(line.append(" ").append(chatButton("partyfinder.chat.open_moderation", "chat_button_link", "/partyfinder mod")));
        BackendConnection.opReports();
    }

    public static void onPartyFull(Listing listing, boolean canLaunch) {
        if (!PartyFinderConfig.get().notifyPartyFull) return;
        Component title = Activities.get(listing.activity()).title(listing.options());
        Notifications.toast("party_full", Component.translatable("partyfinder.toast.full.title"), title);
        MutableComponent line = Component.translatable("partyfinder.chat.party_full", title).withColor(Theme.rgb("chat_success"));
        if (canLaunch) line.append(" ").append(chatButton("partyfinder.chat.launch", "chat_button_link", "/partyfinder go"));
        PartyActions.chat(line);
    }

    public static void onInfo(String key, List<String> args) {
        if (!key.matches(KEY_PATTERN)) return;
        PartyActions.chat(Component.translatable("partyfinder.info." + key, args.toArray()).withColor(Theme.rgb("chat_success")));
    }

    public static void onError(String key, List<String> args) {
        if (!key.matches(KEY_PATTERN)) return;
        // Keys like accept_full name the player (%s); extra args are ignored by texts without placeholders.
        MutableComponent message = Component.translatable("partyfinder.error." + key, args.toArray()).withColor(Theme.rgb("chat_error"));
        String details = switch (key) {
            case "requirements_not_met" -> args.stream().map(BackendEvents::requirementArg).filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(", "));
            case "role_not_needed", "role_required", "role_taken" -> args.stream()
                    .filter(r -> Listing.CLASSES.contains(r) || Listing.KUUDRA_ROLES.contains(r))
                    .map(r -> (Listing.KUUDRA_ROLES.contains(r) && !Listing.CLASSES.contains(r)
                            ? Component.translatable("partyfinder.role." + r) : StatFormat.className(r)).getString())
                    .collect(Collectors.joining(", "));
            default -> "";
        };
        if (!details.isEmpty()) message.append(Component.literal(" " + details).withColor(Theme.rgb("chat_detail")));
        PartyActions.chat(message);
    }

    /** Server sends requirements as "id:min". */
    private static String requirementArg(String arg) {
        int colon = arg.indexOf(':');
        if (colon <= 0) return "";
        try {
            return StatFormat.requirement(arg.substring(0, colon), Integer.parseInt(arg.substring(colon + 1))).getString();
        } catch (NumberFormatException e) {
            return "";
        }
    }

    /** Requester stats for the leader's activity, or a warning when the server could not verify them. */
    public static MutableComponent statsLine(PartyFinderState.JoinRequest request) {
        if (request.unverified()) return Component.translatable("partyfinder.requests.unverified").withColor(Theme.rgb("chat_notice"));
        List<String> ids = PartyFinderState.ownListing().map(l -> Activities.get(l.activity()).stats()).orElse(List.of("sb"));
        List<Component> parts = StatFormat.format(ids, request.stats());
        return Component.literal(parts.stream().map(Component::getString).collect(Collectors.joining(" · ")))
                .withColor(Theme.rgb("chat_stats"));
    }

    private static MutableComponent reportBadge(ReportSummary reports) {
        MutableComponent hover = Component.empty();
        List<Component> lines = reports.describe();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) hover.append("\n");
            hover.append(lines.get(i));
        }
        int color = Theme.rgb(reports.count() > 0 ? "reports_some" : "reports_none");
        return Component.literal(reports.badge())
                .withStyle(style -> style.withColor(color).withHoverEvent(new HoverEvent.ShowText(hover)));
    }

    private static MutableComponent chatButton(String key, String colorKey, String command) {
        return Component.literal("[").append(Component.translatable(key)).append("]")
                .withStyle(style -> style.withColor(Theme.rgb(colorKey))
                        .withClickEvent(new ClickEvent.SuggestCommand(command))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
    }
}
