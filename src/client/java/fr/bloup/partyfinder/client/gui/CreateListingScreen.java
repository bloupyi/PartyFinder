package fr.bloup.partyfinder.client.gui;

import fr.bloup.partyfinder.client.activity.Activities;
import fr.bloup.partyfinder.client.activity.Activity;
import fr.bloup.partyfinder.client.activity.StatFormat;
import fr.bloup.partyfinder.client.hypixel.HypixelBridge;
import fr.bloup.partyfinder.client.net.BackendConnection;
import fr.bloup.partyfinder.client.party.Listing;
import fr.bloup.partyfinder.client.party.ListingDraft;
import fr.bloup.partyfinder.client.party.ListingDrafts;
import fr.bloup.partyfinder.client.party.PartyFinderState;
import fr.bloup.partyfinder.client.party.PartySync;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CreateListingScreen extends Screen {
    private static final int MAX_WIDTH = 240;
    private static final int ROW = 24;
    private static final List<Integer> REPORT_LIMITS = List.of(Listing.NO_REPORT_LIMIT, 0, 1, 2, 3, 5, 10, 20);

    private final Screen parent;
    final Map<String, String> options = new LinkedHashMap<>();
    final Map<String, Integer> minStats = new LinkedHashMap<>();
    final List<String> roles = new ArrayList<>();
    private int formWidth = MAX_WIDTH;
    Activity activity;
    int maxSize;
    private String note = "";
    private int maxReports = Listing.NO_REPORT_LIMIT;
    private int top;
    private int noteLabelY;

    public CreateListingScreen(Screen parent) {
        super(Component.translatable("partyfinder.create.title"));
        this.parent = parent;
        List<Activity> all = Activities.all();
        activity = all.isEmpty() ? Activities.get("other") : all.getFirst();
        maxSize = activity.maxSize();
        PartyFinderState.ownListing().map(ListingDraft::of).or(ListingDrafts::last).ifPresent(this::apply);
    }

    void apply(ListingDraft draft) {
        activity = Activities.get(draft.activity());
        maxSize = draft.maxSize();
        options.clear();
        options.putAll(draft.options());
        note = draft.note();
        maxReports = REPORT_LIMITS.contains(draft.maxReports()) ? draft.maxReports() : Listing.NO_REPORT_LIMIT;
        minStats.clear();
        minStats.putAll(draft.minStats());
        roles.clear();
        roles.addAll(draft.roles());
    }

    /** Free-text requirements were replaced by verified minimum stats and roles; the note covers anything else. */
    ListingDraft draft() {
        return new ListingDraft(activity.id(), Map.copyOf(options), note.trim(), "", maxSize, maxReports,
                Map.copyOf(minStats), List.copyOf(roles));
    }

    /** Dungeons (wanted classes) and Kuudra (wanted roles) can ask for roles. */
    boolean hasRoles() {
        return !Listing.rolesFor(activity.category()).isEmpty();
    }

    String category() {
        return activity.category();
    }

    boolean isDungeon() {
        return activity.category().equals("dungeons");
    }

    private static int partySize() {
        return HypixelBridge.members().size();
    }

    @Override
    protected void init() {
        formWidth = Math.min(MAX_WIDTH, width - 20);
        int x = width / 2 - formWidth / 2;
        int half = (formWidth - 4) / 2;
        int formHeight = ROW * (4 + activity.options().size()) + 38 + 32;
        top = Math.max(24, (height - formHeight) / 2);
        int y = top;

        if (!Activities.all().isEmpty()) {
            Component label = Component.translatable("partyfinder.create.activity").append(": ")
                    .append(activity.name().copy().withColor(activity.color()));
            addRenderableWidget(Button.builder(label, b -> minecraft.gui.setScreen(
                    new ActivitySelectScreen(this, activity, this::selectActivity))).bounds(x, y, formWidth, 20).build());
        }
        y += ROW;

        for (Activity.Option option : activity.options()) {
            List<String> values = option.values().stream().map(Activity.Value::id).toList();
            String current = values.contains(options.get(option.id())) ? options.get(option.id()) : values.getFirst();
            options.put(option.id(), current);
            addRenderableWidget(CycleButton.builder((String v) -> Activity.optionName(option.id(), v), current)
                    .withValues(values)
                    .create(x, y, formWidth, 20, Component.translatable("partyfinder.option." + option.id()),
                            (b, value) -> options.put(option.id(), value)));
            y += ROW;
        }
        options.keySet().retainAll(activity.options().stream().map(Activity.Option::id).toList());
        List<String> allowed = StatFormat.requirementIds(activity.stats());
        minStats.keySet().retainAll(allowed);
        if (!hasRoles()) roles.clear();
        roles.retainAll(Listing.rolesFor(activity.category()));

        int minSize = Math.max(2, partySize());
        maxSize = Math.max(minSize, maxSize);
        int upper = Math.max(maxSize, activity.maxSize());
        addRenderableWidget(CycleButton.builder((Integer v) -> Component.literal(String.valueOf(v)), maxSize)
                .withValues(IntStream.rangeClosed(minSize, upper).boxed().toList())
                .withTooltip(v -> Tooltip.create(Component.translatable("partyfinder.create.size.auto", partySize())))
                .create(x, y, half, 20, Component.translatable("partyfinder.create.max_size"), (b, value) -> maxSize = value));
        addRenderableWidget(CycleButton.builder(CreateListingScreen::reportLimitName, maxReports)
                .withValues(REPORT_LIMITS)
                .withTooltip(v -> Tooltip.create(Component.translatable("partyfinder.create.max_reports.tooltip")))
                .create(x + half + 4, y, formWidth - half - 4, 20, Component.translatable("partyfinder.create.max_reports.short"),
                        (b, value) -> maxReports = value));
        y += ROW;

        Button requirementsButton = addRenderableWidget(Button.builder(requirementsLabel(),
                b -> minecraft.gui.setScreen(new RequirementsScreen(this, allowed))).bounds(x, y, half, 20).build());
        requirementsButton.active = !allowed.isEmpty();
        Button rolesButton = addRenderableWidget(Button.builder(rolesLabel(),
                b -> minecraft.gui.setScreen(new RolesScreen(this))).bounds(x + half + 4, y, formWidth - half - 4, 20).build());
        rolesButton.active = hasRoles();
        if (!hasRoles()) rolesButton.setTooltip(Tooltip.create(Component.translatable("partyfinder.create.roles.dungeons_only")));
        y += ROW + 14;

        noteLabelY = y - 12;
        EditBox noteBox = new EditBox(font, x, y, formWidth, 20, Component.translatable("partyfinder.create.note"));
        noteBox.setMaxLength(96);
        noteBox.setValue(note);
        noteBox.setHint(Component.translatable("partyfinder.create.note.hint"));
        noteBox.setResponder(value -> note = value);
        addRenderableWidget(noteBox);
        y += 32;

        int third = (formWidth - 8) / 3;
        Button publish = addRenderableWidget(Button.builder(Component.translatable("partyfinder.create.publish"), b -> {
            ListingDraft draft = draft();
            ListingDrafts.remember(draft);
            BackendConnection.publish(draft);
            onClose();
        }).bounds(x, y, third, 20).build());
        publish.active = BackendConnection.isConnected() && PartySync.canHost();
        if (!PartySync.canHost()) publish.setTooltip(Tooltip.create(Component.translatable("partyfinder.error.not_leader")));
        addRenderableWidget(Button.builder(Component.translatable("partyfinder.presets.button"),
                b -> minecraft.gui.setScreen(new PresetsScreen(this))).bounds(x + third + 4, y, third, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(x + 2 * (third + 4), y, formWidth - 2 * (third + 4), 20).build());
    }

    private void selectActivity(Activity value) {
        if (value.id().equals(activity.id())) return;
        ListingDraft remembered = ListingDrafts.lastFor(value.id()).orElse(null);
        if (remembered != null) {
            apply(remembered);
        } else {
            activity = value;
            maxSize = value.maxSize();
            options.clear();
            minStats.clear();
            roles.clear();
        }
    }

    private Component requirementsLabel() {
        if (minStats.isEmpty()) return Component.translatable("partyfinder.create.min_stats.none");
        return Component.literal(minStats.entrySet().stream()
                .map(e -> StatFormat.requirement(e.getKey(), e.getValue()).getString())
                .collect(Collectors.joining(", ")));
    }

    private Component rolesLabel() {
        if (roles.isEmpty()) return Component.translatable("partyfinder.create.roles.none");
        return Component.translatable("partyfinder.create.roles.label", roles.stream()
                .map(r -> StatFormat.roleName(activity.category(), r).getString()).collect(Collectors.joining(", ")));
    }

    private static Component reportLimitName(Integer limit) {
        return limit == Listing.NO_REPORT_LIMIT
                ? Component.translatable("partyfinder.create.max_reports.none")
                : Component.literal(String.valueOf(limit));
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int x = width / 2 - formWidth / 2;
        int labelColor = Activities.color("label", 0xA0A0A0);
        g.centeredText(font, title, width / 2, top - 16, Activities.color("title", 0xFFFFFF));
        Component size = Component.translatable("partyfinder.create.size.current", partySize());
        g.text(font, size, x + formWidth - font.width(size), noteLabelY, labelColor);
        g.text(font, Component.translatable("partyfinder.create.note"), x, noteLabelY, labelColor);
    }
}
