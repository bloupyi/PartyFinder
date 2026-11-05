package fr.bloup.partyfinder.client.party;

import fr.bloup.partyfinder.client.activity.Notifications;
import fr.bloup.partyfinder.client.activity.Theme;
import fr.bloup.partyfinder.client.PartyActions;
import fr.bloup.partyfinder.client.activity.Activities;
import fr.bloup.partyfinder.client.activity.Activity;
import fr.bloup.partyfinder.client.config.PartyFinderConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Alerts for party-friendly SkyBlock events (schedule computed by the backend from the calendar and mayors). */
public final class EventAlerts {
    public static final List<String> IDS = List.of("fishing_festival", "spooky_festival", "mining_fiesta", "diana");
    private static final Pattern ID = Pattern.compile("[a-z_]{1,32}");
    private static final long TICK_MS = 1_000;

    public record Event(String id, long start, long end, boolean term) {
        public boolean isValid() {
            return id != null && ID.matcher(id).matches() && end > start;
        }

        String key() {
            return id + "@" + (term ? "term" : start);
        }
    }

    private static List<Event> events = List.of();
    private static final Set<String> SOON = new HashSet<>();
    private static final Set<String> STARTED = new HashSet<>();
    private static long lastCheck;

    private EventAlerts() {
    }

    public static void setEvents(List<Event> received) {
        events = received.stream().filter(Event::isValid).limit(16).toList();
    }

    public static List<Event> active() {
        long now = System.currentTimeMillis();
        return events.stream().filter(e -> now >= e.start() && now < e.end()).toList();
    }

    public static boolean enabled(String id) {
        return PartyFinderConfig.get().eventAlerts.getOrDefault(id, true);
    }

    public static Component name(String id) {
        return Component.translatable("partyfinder.event." + id);
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        if (now - lastCheck < TICK_MS || Minecraft.getInstance().player == null) return;
        lastCheck = now;
        long lead = PartyFinderConfig.get().eventLeadMinutes * 60_000L;
        for (Event event : events) {
            if (!enabled(event.id())) continue;
            if (now >= event.start() && now < event.end()) {
                if (STARTED.add(event.key())) announce(event, event.term() ? "partyfinder.event.active" : "partyfinder.event.started", 0);
            } else if (!event.term() && lead > 0 && now < event.start() && event.start() - now <= lead) {
                if (SOON.add(event.key())) {
                    announce(event, "partyfinder.event.soon", (int) Math.ceil((event.start() - now) / 60_000.0));
                }
            }
        }
    }

    private static void announce(Event event, String key, int minutes) {
        List<Activity> activities = Activities.forEvent(event.id());
        long open = PartyFinderState.listings().stream()
                .filter(l -> activities.stream().anyMatch(a -> a.id().equals(l.activity())) && !l.isFull())
                .count();
        Component title = name(event.id());
        Component body = minutes > 0
                ? Component.translatable(key, title, minutes)
                : Component.translatable(key, title);
        Notifications.toast("event", body, Component.translatable("partyfinder.event.open_parties", open));
        MutableComponent line = body.copy().withColor(Theme.rgb("chat_event"))
                .append(Component.literal(" ").append(Component.translatable("partyfinder.event.open_parties", open))
                        .withColor(Theme.rgb("chat_muted")));
        if (!activities.isEmpty()) {
            String command = "/partyfinder event " + event.id();
            line.append(" ").append(Component.literal("[").append(Component.translatable("partyfinder.event.view")).append("]")
                    .withStyle(style -> style.withColor(Theme.rgb("chat_button_link"))
                            .withClickEvent(new ClickEvent.SuggestCommand(command))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal(command)))));
        }
        PartyActions.chat(line);
    }

    public static void reset() {
        events = List.of();
    }
}
