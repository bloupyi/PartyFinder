package fr.bloup.partyfinder.client.gui;

import fr.bloup.partyfinder.client.activity.Activities;
import fr.bloup.partyfinder.client.net.BackendConnection;
import fr.bloup.partyfinder.client.party.Listing;
import fr.bloup.partyfinder.client.party.ReportSummary;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class ReportScreen extends Screen {
    private static final int MAX_WIDTH = 220;
    private static final int FORM_HEIGHT = 110;

    private final Screen parent;
    private final Listing.Player target;
    private String reason = ReportSummary.REASONS.getFirst();
    private String comment = "";
    private int formWidth = MAX_WIDTH;
    private int top;

    public ReportScreen(Screen parent, Listing.Player target) {
        super(Component.translatable("partyfinder.report.title", target.name()));
        this.parent = parent;
        this.target = target;
    }

    @Override
    protected void init() {
        formWidth = Math.min(MAX_WIDTH, width - 20);
        int x = width / 2 - formWidth / 2;
        top = Math.max(24, (height - FORM_HEIGHT) / 2);
        int y = top;

        addRenderableWidget(CycleButton.builder(ReportSummary::reasonName, reason)
                .withValues(ReportSummary.REASONS)
                .create(x, y, formWidth, 20, Component.translatable("partyfinder.report.reason"), (b, value) -> reason = value));
        y += 38;

        EditBox commentBox = new EditBox(font, x, y, formWidth, 20, Component.translatable("partyfinder.report.comment"));
        commentBox.setMaxLength(120);
        commentBox.setValue(comment);
        commentBox.setHint(Component.translatable("partyfinder.report.comment.hint"));
        commentBox.setResponder(value -> comment = value);
        addRenderableWidget(commentBox);
        y += 32;

        int half = (formWidth - 4) / 2;
        addRenderableWidget(Button.builder(Component.translatable("partyfinder.report.send"), b -> {
            BackendConnection.report(target, reason, comment.trim());
            onClose();
        }).bounds(x, y, half, 20).build()).active = BackendConnection.isConnected();
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(x + half + 4, y, half, 20).build());
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int x = width / 2 - formWidth / 2;
        g.centeredText(font, font.plainSubstrByWidth(title.getString(), width - 20), width / 2, top - 16,
                Activities.color("title", 0xFFFFFF));
        g.text(font, Component.translatable("partyfinder.report.comment"), x, top + 26, Activities.color("label", 0xA0A0A0));
    }
}
