package fr.bloup.partyfinder.client.gui;

import fr.bloup.partyfinder.client.activity.Activities;
import fr.bloup.partyfinder.client.net.BackendConnection;
import fr.bloup.partyfinder.client.party.Listing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class BanScreen extends Screen {
    private static final int MAX_WIDTH = 240;

    private final Screen parent;
    private final Listing.Player target;
    private String reason = "";
    private int formWidth = MAX_WIDTH;
    private int top;

    public BanScreen(Screen parent, Listing.Player target) {
        super(Component.translatable("partyfinder.mod.ban.title", target.name()));
        this.parent = parent;
        this.target = target;
    }

    @Override
    protected void init() {
        formWidth = Math.min(MAX_WIDTH, width - 20);
        int x = width / 2 - formWidth / 2;
        top = Math.max(24, height / 2 - 50);

        EditBox reasonBox = new EditBox(font, x, top + 30, formWidth, 20, Component.translatable("partyfinder.mod.ban.reason"));
        reasonBox.setMaxLength(120);
        reasonBox.setValue(reason);
        reasonBox.setHint(Component.translatable("partyfinder.mod.ban.reason"));
        reasonBox.setResponder(value -> reason = value);
        addRenderableWidget(reasonBox);

        int half = (formWidth - 4) / 2;
        addRenderableWidget(Button.builder(Component.translatable("partyfinder.mod.ban.confirm"), b -> {
            BackendConnection.opBan(target.uuid(), reason.trim());
            onClose();
        }).bounds(x, top + 60, half, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(x + half + 4, top + 60, half, 20).build());
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.centeredText(font, TimeFormat.clip(font, title.getString(), width - 20), width / 2, top, Activities.color("title", 0xFFFFFF));
        g.centeredText(font, TimeFormat.clip(font, Component.translatable("partyfinder.mod.ban.warning").getString(), width - 20),
                width / 2, top + 14, Activities.color("banned", 0xFF5555));
    }
}
