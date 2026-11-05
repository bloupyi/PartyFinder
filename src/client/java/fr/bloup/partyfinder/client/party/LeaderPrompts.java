package fr.bloup.partyfinder.client.party;

import fr.bloup.partyfinder.client.PartyActions;
import fr.bloup.partyfinder.client.PartyfinderClient;
import fr.bloup.partyfinder.client.config.PartyFinderConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Requests the Party Leader answers for the party: warp it (/pf warp) or hand over the lead (/pf ptme). Asked with a
 * toast (Y/N keys) and a chat message with Accept / Decline / "I get it, auto accept", or done right away when the
 * matching auto-accept setting is on.
 */
public final class LeaderPrompts {
    public enum Type {
        WARP, TRANSFER;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Type> of(String id) {
            for (Type type : values()) if (type.id().equals(id)) return Optional.of(type);
            return Optional.empty();
        }
    }

    public record Prompt(Type type, String listingId, Listing.Player player, long at) {
    }

    private static final long TTL_MS = 30_000;
    private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId();
    private static final List<Prompt> PROMPTS = new ArrayList<>();

    private LeaderPrompts() {
    }

    public static boolean auto(Type type) {
        PartyFinderConfig config = PartyFinderConfig.get();
        return type == Type.WARP ? config.autoAcceptWarps : config.autoAcceptTransfers;
    }

    /** A validated request reached us, the Party Leader. */
    public static void offer(Type type, String listingId, Listing.Player player) {
        if (auto(type)) {
            run(new Prompt(type, listingId, player, System.currentTimeMillis()));
            return;
        }
        PROMPTS.removeIf(p -> p.type() == type && p.player().uuid().equals(player.uuid()));
        PROMPTS.add(new Prompt(type, listingId, player, System.currentTimeMillis()));

        Minecraft mc = Minecraft.getInstance();
        SystemToast.addOrUpdate(mc.gui.toastManager(), TOAST,
                Component.translatable("partyfinder.toast." + type.id() + ".title"),
                Component.translatable("partyfinder.prompt." + type.id(), player.name()).append(" · ")
                        .append(Component.translatable("partyfinder.prompt.keys",
                                PartyfinderClient.acceptKey.getTranslatedKeyMessage(), PartyfinderClient.denyKey.getTranslatedKeyMessage())));

        String answer = "/partyfinder answer " + type.id() + " " + player.name();
        PartyActions.framed(Component.translatable("partyfinder.prompt." + type.id(), player.name()).withStyle(ChatFormatting.YELLOW)
                        .append("\n")
                        .append(button("partyfinder.prompt.accept", ChatFormatting.GREEN, answer + " yes"))
                        .append(" ")
                        .append(button("partyfinder.prompt.decline", ChatFormatting.RED, answer + " no"))
                        .append(" ")
                        .append(button("partyfinder.prompt.auto", ChatFormatting.GOLD, "/partyfinder auto " + type.id())));
    }

    /** Answer from the chat buttons, the command or the Y/N keys; false when no such request is pending. */
    public static boolean answer(Type type, String name, boolean accept) {
        Optional<Prompt> prompt = take(type, name);
        if (prompt.isEmpty()) return false;
        if (accept) {
            run(prompt.get());
        } else {
            PartyActions.chat(Component.translatable("partyfinder.prompt.declined", prompt.get().player().name()).withStyle(ChatFormatting.RED));
        }
        return true;
    }

    /** "I get it, auto accept": accepts what is pending of this type and every later request. */
    public static void enableAuto(Type type) {
        PartyFinderConfig config = PartyFinderConfig.get();
        if (type == Type.WARP) config.autoAcceptWarps = true;
        else config.autoAcceptTransfers = true;
        PartyFinderConfig.save();
        PartyActions.chat(Component.translatable("partyfinder.prompt.auto_on." + type.id()).withStyle(ChatFormatting.GREEN));
        for (Prompt prompt : List.copyOf(PROMPTS)) {
            if (prompt.type() == type) answer(type, prompt.player().name(), true);
        }
    }

    /** Most recent pending request, for the Y/N keys. */
    public static Optional<Prompt> latest() {
        prune();
        return PROMPTS.isEmpty() ? Optional.empty() : Optional.of(PROMPTS.getLast());
    }

    public static List<String> pendingNames(Type type) {
        prune();
        return PROMPTS.stream().filter(p -> p.type() == type).map(p -> p.player().name()).toList();
    }

    public static void tick() {
        prune();
    }

    public static void reset() {
        PROMPTS.clear();
    }

    private static Optional<Prompt> take(Type type, String name) {
        prune();
        for (Prompt prompt : PROMPTS) {
            if (prompt.type() == type && prompt.player().name().equalsIgnoreCase(name)) {
                PROMPTS.remove(prompt);
                return Optional.of(prompt);
            }
        }
        return Optional.empty();
    }

    private static void run(Prompt prompt) {
        // Checked again now: the lead or the party may have changed since the request came in.
        switch (prompt.type()) {
            case WARP -> PartyTransfer.warpFor(prompt.listingId(), prompt.player());
            case TRANSFER -> PartyTransfer.transferTo(prompt.listingId(), prompt.player());
        }
    }

    private static void prune() {
        long now = System.currentTimeMillis();
        PROMPTS.removeIf(p -> now - p.at() > TTL_MS);
    }

    private static MutableComponent button(String key, ChatFormatting color, String command) {
        return Component.literal("[").append(Component.translatable(key)).append("]")
                .withStyle(style -> style.withColor(color).withBold(true)
                        .withClickEvent(new ClickEvent.SuggestCommand(command))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
    }
}
