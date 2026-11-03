package fr.bloup.partyfinder.client.gui;

import fr.bloup.partyfinder.client.activity.Activities;
import fr.bloup.partyfinder.client.activity.Activity;
import fr.bloup.partyfinder.client.net.BackendConnection;
import fr.bloup.partyfinder.client.party.Listing;
import fr.bloup.partyfinder.client.party.PartyFinderState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.stream.IntStream;

public class CreateListingScreen extends Screen {
    private static final int MAX_WIDTH = 220;
    private static final int FORM_HEIGHT = 178;
    private static final List<Integer> REPORT_LIMITS = List.of(Listing.NO_REPORT_LIMIT, 0, 1, 2, 3, 5, 10, 20);

    private final Screen parent;
    private int formWidth = MAX_WIDTH;
    private Activity activity;
    private int size = 1;
    private int maxSize;
    private String note = "";
    private String requirements = "";
    private int maxReports = Listing.NO_REPORT_LIMIT;
    private int top;

    public CreateListingScreen(Screen parent) {
        super(Component.translatable("partyfinder.create.title"));
        this.parent = parent;
        List<Activity> all = Activities.all();
        activity = all.isEmpty() ? Activities.get("other") : all.getFirst();
        maxSize = activity.maxSize();
        PartyFinderState.ownListing().ifPresent(this::fill);
    }

    private void fill(Listing listing) {
        activity = Activities.get(listing.activity());
        size = listing.size();
        maxSize = listing.maxSize();
        note = listing.note();
        requirements = listing.requirements();
        maxReports = REPORT_LIMITS.contains(listing.maxReports()) ? listing.maxReports() : Listing.NO_REPORT_LIMIT;
    }

    @Override
    protected void init() {
        formWidth = Math.min(MAX_WIDTH, width - 20);
        int x = width / 2 - formWidth / 2;
        top = Math.max(24, (height - FORM_HEIGHT) / 2);
        int y = top;

        List<Activity> activities = Activities.all();
        if (!activities.isEmpty()) {
            addRenderableWidget(CycleButton.builder(Activity::name, activity)
                    .withValues(activities)
                    .create(x, y, formWidth, 20, Component.translatable("partyfinder.create.activity"), (b, value) -> {
                        activity = value;
                        maxSize = value.maxSize();
                        size = Math.min(size, maxSize);
                        rebuildWidgets();
                    }));
        }
        y += 24;

        maxSize = Math.max(2, maxSize);
        size = Math.max(1, Math.min(size, maxSize));
        int half = (formWidth - 4) / 2;
        addRenderableWidget(CycleButton.builder((Integer v) -> Component.literal(String.valueOf(v)), size)
                .withValues(IntStream.rangeClosed(1, maxSize).boxed().toList())
                .create(x, y, half, 20, Component.translatable("partyfinder.create.size"), (b, value) -> size = value));
        addRenderableWidget(CycleButton.builder((Integer v) -> Component.literal(String.valueOf(v)), maxSize)
                .withValues(IntStream.rangeClosed(2, Math.max(maxSize, activity.maxSize())).boxed().toList())
                .create(x + half + 4, y, half, 20, Component.translatable("partyfinder.create.max_size"), (b, value) -> {
                    maxSize = value;
                    size = Math.min(size, maxSize);
                    rebuildWidgets();
                }));
        y += 24;

        addRenderableWidget(CycleButton.builder(CreateListingScreen::reportLimitName, maxReports)
                .withValues(REPORT_LIMITS)
                .withTooltip(v -> Tooltip.create(Component.translatable("partyfinder.create.max_reports.tooltip")))
                .create(x, y, formWidth, 20, Component.translatable("partyfinder.create.max_reports"), (b, value) -> maxReports = value));
        y += 38;

        EditBox noteBox = new EditBox(font, x, y, formWidth, 20, Component.translatable("partyfinder.create.note"));
        noteBox.setMaxLength(96);
        noteBox.setValue(note);
        noteBox.setHint(Component.translatable("partyfinder.create.note.hint"));
        noteBox.setResponder(value -> note = value);
        addRenderableWidget(noteBox);
        y += 38;

        EditBox reqBox = new EditBox(font, x, y, formWidth, 20, Component.translatable("partyfinder.create.requirements"));
        reqBox.setMaxLength(48);
        reqBox.setValue(requirements);
        reqBox.setHint(Component.translatable("partyfinder.create.requirements.hint"));
        reqBox.setResponder(value -> requirements = value);
        addRenderableWidget(reqBox);
        y += 32;

        addRenderableWidget(Button.builder(Component.translatable("partyfinder.create.publish"), b -> {
            BackendConnection.publish(activity.id(), note.trim(), requirements.trim(), size, maxSize, maxReports);
            onClose();
        }).bounds(x, y, half, 20).build()).active = BackendConnection.isConnected();
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(x + half + 4, y, half, 20).build());
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
        g.text(font, Component.translatable("partyfinder.create.note"), x, top + 74, labelColor);
        g.text(font, Component.translatable("partyfinder.create.requirements"), x, top + 112, labelColor);
    }
}
