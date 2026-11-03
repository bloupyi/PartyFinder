package fr.bloup.partyfinder.client.net;

import fr.bloup.partyfinder.client.PartyActions;
import fr.bloup.partyfinder.client.config.PartyFinderConfig;
import fr.bloup.partyfinder.client.party.Listing;
import fr.bloup.partyfinder.client.party.Moderation;
import fr.bloup.partyfinder.client.activity.Activities;
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
                Component.translatable("partyfinder.toast.join_request.body", from.name()));
        PartyActions.chat(Component.translatable("partyfinder.chat.join_request", from.name())
                .append(" ")
                .append(reportBadge(request.reports()))
                .append(" ")
                .append(chatButton("partyfinder.chat.accept", ChatFormatting.GREEN, "/partyfinder accept " + from.name()))
                .append(" ")
                .append(chatButton("partyfinder.chat.deny", ChatFormatting.RED, "/partyfinder deny " + from.name())));
    }

    public static void onJoinResponse(String listingId, Listing.Player owner, boolean accept) {
        if (!PartyFinderState.consumeRequested(owner.uuid())) return;
        if (!accept) {
            PartyActions.chat(Component.translatable("partyfinder.chat.join_denied", owner.name()).withStyle(ChatFormatting.RED));
            return;
        }
        PartySync.onJoinAccepted(listingId);
        PartyActions.chat(Component.translatable("partyfinder.chat.join_accepted", owner.name()).withStyle(ChatFormatting.GREEN));
        if (PartyFinderConfig.get().autoAcceptInvites) PartyActions.acceptInviteLater(owner.name());
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

    public static void onError(String key) {
        if (!key.matches(KEY_PATTERN)) return;
        PartyActions.chat(Component.translatable("partyfinder.error." + key).withStyle(ChatFormatting.RED));
    }

    private static MutableComponent reportBadge(ReportSummary reports) {
        MutableComponent hover = Component.empty();
        List<Component> lines = reports.describe();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) hover.append("\n");
            hover.append(lines.get(i));
        }
        ChatFormatting color = reports.count() > 0 ? ChatFormatting.RED : ChatFormatting.GREEN;
        return Component.translatable("partyfinder.report.badge", reports.count())
                .withStyle(style -> style.withColor(color).withHoverEvent(new HoverEvent.ShowText(hover)));
    }

    private static MutableComponent chatButton(String key, ChatFormatting color, String command) {
        return Component.literal("[").append(Component.translatable(key)).append("]")
                .withStyle(style -> style.withColor(color)
                        .withClickEvent(new ClickEvent.SuggestCommand(command))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
    }
}
