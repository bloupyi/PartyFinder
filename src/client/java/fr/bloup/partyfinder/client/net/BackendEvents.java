package fr.bloup.partyfinder.client.net;

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
import fr.bloup.partyfinder.client.party.ReportSummary;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.stream.Collectors;

public final class BackendEvents {
    private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId();
    private static final SystemToast.SystemToastId REPORT_TOAST = new SystemToast.SystemToastId();
    private static final String KEY_PATTERN = "[a-z_]{1,32}";

    private BackendEvents() {
    }

    public static void onJoinRequest(PartyFinderState.JoinRequest request) {
        if (PartyFinderState.ownListing().isEmpty()) return;
        PartyFinderState.addJoinRequest(request);
        if (!PartyFinderConfig.get().notifyJoinRequests) return;
        Listing.Player from = request.player();
        Minecraft mc = Minecraft.getInstance();
        SystemToast.addOrUpdate(mc.gui.toastManager(), TOAST,
                Component.translatable("partyfinder.toast.join_request.title"),
                Component.translatable("partyfinder.toast.join_request.keys", from.name(),
                        PartyfinderClient.acceptKey.getTranslatedKeyMessage(), PartyfinderClient.denyKey.getTranslatedKeyMessage()));
        PartyActions.chat(Component.translatable("partyfinder.chat.join_request", from.name())
                .append(" ")
                .append(reportBadge(request.reports()))
                .append(" ")
                .append(statsLine(request))
                .append(" ")
                .append(chatButton("partyfinder.chat.accept", ChatFormatting.GREEN, "/partyfinder accept " + from.name()))
                .append(" ")
                .append(chatButton("partyfinder.chat.deny", ChatFormatting.RED, "/partyfinder deny " + from.name())));
    }

    public static void onJoinSent(Listing.Player owner) {
        PartyFinderState.markRequested(owner.uuid());
        PartyActions.chat(Component.translatable("partyfinder.chat.join_sent", owner.name()));
    }

    public static void onJoinResponse(String listingId, Listing.Player owner, String inviter, boolean accept) {
        if (!PartyFinderState.consumeRequested(owner.uuid())) return;
        if (!accept) {
            PartyActions.chat(Component.translatable("partyfinder.chat.join_denied", owner.name()).withStyle(ChatFormatting.RED));
            return;
        }
        PartySync.onJoinAccepted(listingId);
        PartyActions.chat(Component.translatable("partyfinder.chat.join_accepted", owner.name()).withStyle(ChatFormatting.GREEN));
        if (PartyFinderConfig.get().autoAcceptInvites) {
            InviteAccept.expect(Listing.isValidName(inviter) ? inviter : owner.name(), owner.name());
        }
    }

    public static void onReportNew(Listing.Player target, Moderation.Report report, int count) {
        if (!PartyFinderState.isOp()) return;
        Minecraft mc = Minecraft.getInstance();
        SystemToast.addOrUpdate(mc.gui.toastManager(), REPORT_TOAST,
                Component.translatable("partyfinder.toast.report.title"),
                Component.translatable("partyfinder.toast.report.body", target.name(), ReportSummary.reasonName(report.reason())));
        MutableComponent line = Component.translatable("partyfinder.chat.report_new",
                        report.reporter().name(), target.name(), ReportSummary.reasonName(report.reason()), count)
                .withStyle(ChatFormatting.GOLD);
        if (!report.comment().isEmpty()) {
            line.append(Component.literal(" \"" + report.comment() + "\"").withStyle(ChatFormatting.GRAY));
        }
        PartyActions.chat(line.append(" ").append(chatButton("partyfinder.chat.open_moderation", ChatFormatting.AQUA, "/partyfinder mod")));
        BackendConnection.opReports();
    }

    public static void onPartyFull(Listing listing, boolean canLaunch) {
        if (!PartyFinderConfig.get().notifyPartyFull) return;
        Minecraft mc = Minecraft.getInstance();
        Component title = Activities.get(listing.activity()).title(listing.options());
        SystemToast.addOrUpdate(mc.gui.toastManager(), TOAST,
                Component.translatable("partyfinder.toast.full.title"),
                title);
        MutableComponent line = Component.translatable("partyfinder.chat.party_full", title).withStyle(ChatFormatting.GREEN);
        if (canLaunch) line.append(" ").append(chatButton("partyfinder.chat.launch", ChatFormatting.AQUA, "/partyfinder go"));
        PartyActions.chat(line);
    }

    public static void onInfo(String key, List<String> args) {
        if (!key.matches(KEY_PATTERN)) return;
        PartyActions.chat(Component.translatable("partyfinder.info." + key, args.toArray()).withStyle(ChatFormatting.GREEN));
    }

    public static void onError(String key, List<String> args) {
        if (!key.matches(KEY_PATTERN)) return;
        MutableComponent message = Component.translatable("partyfinder.error." + key).withStyle(ChatFormatting.RED);
        String details = switch (key) {
            case "requirements_not_met" -> args.stream().map(BackendEvents::requirementArg).filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(", "));
            case "role_not_needed" -> args.stream().filter(Listing.CLASSES::contains)
                    .map(c -> StatFormat.className(c).getString()).collect(Collectors.joining(", "));
            default -> "";
        };
        if (!details.isEmpty()) message.append(Component.literal(" " + details).withStyle(ChatFormatting.GOLD));
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
        if (request.unverified()) return Component.translatable("partyfinder.requests.unverified").withStyle(ChatFormatting.YELLOW);
        List<String> ids = PartyFinderState.ownListing().map(l -> Activities.get(l.activity()).stats()).orElse(List.of("sb"));
        List<Component> parts = StatFormat.format(ids, request.stats());
        return Component.literal(parts.stream().map(Component::getString).collect(Collectors.joining(" · ")))
                .withStyle(ChatFormatting.AQUA);
    }

    private static MutableComponent reportBadge(ReportSummary reports) {
        MutableComponent hover = Component.empty();
        List<Component> lines = reports.describe();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) hover.append("\n");
            hover.append(lines.get(i));
        }
        ChatFormatting color = reports.count() > 0 ? ChatFormatting.RED : ChatFormatting.GREEN;
        return Component.literal(reports.badge())
                .withStyle(style -> style.withColor(color).withHoverEvent(new HoverEvent.ShowText(hover)));
    }

    private static MutableComponent chatButton(String key, ChatFormatting color, String command) {
        return Component.literal("[").append(Component.translatable(key)).append("]")
                .withStyle(style -> style.withColor(color)
                        .withClickEvent(new ClickEvent.SuggestCommand(command))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
    }
}
