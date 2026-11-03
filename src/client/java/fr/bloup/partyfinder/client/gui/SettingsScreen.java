package fr.bloup.partyfinder.client.gui;

import fr.bloup.partyfinder.client.config.PartyFinderConfig;
import fr.bloup.partyfinder.client.hud.HudConfigScreen;
import fr.bloup.partyfinder.client.party.EventAlerts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** General PartyFinder settings (the party HUD has its own screen). */
public class SettingsScreen extends OptionsSubScreen {
    private static final String KEY = "partyfinder.settings.option.";
    private static final OptionWidgets W = new OptionWidgets(KEY);

    public SettingsScreen(Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.translatable("partyfinder.settings.title"));
    }

    @Override
    protected void addOptions() {
        PartyFinderConfig c = PartyFinderConfig.get();
        list.addHeader(Component.translatable(KEY + "section.party"));
        list.addSmall(List.of(
                W.toggle("auto_accept_invites", c.autoAcceptInvites, v -> c.autoAcceptInvites = v),
                W.toggle("notify_join_requests", c.notifyJoinRequests, v -> c.notifyJoinRequests = v),
                W.toggle("notify_party_full", c.notifyPartyFull, v -> c.notifyPartyFull = v),
                W.toggle("share_status", c.hud.shareStatus, v -> c.hud.shareStatus = v)));

        list.addHeader(Component.translatable(KEY + "section.events"));
        List<AbstractWidget> events = new ArrayList<>();
        for (String id : EventAlerts.IDS) {
            events.add(W.toggle("event." + id, EventAlerts.enabled(id), v -> c.eventAlerts.put(id, v)));
        }
        events.add(W.slider("event_lead", 0, 30, c.eventLeadMinutes, " min", v -> c.eventLeadMinutes = v));
        list.addSmall(events);

        list.addHeader(Component.translatable(KEY + "section.interface"));
        list.addSmall(List.of(
                W.toggle("show_menu_button", c.showMenuButton, v -> c.showMenuButton = v),
                Button.builder(Component.translatable(KEY + "party_hud"), b -> minecraft.gui.setScreen(new HudConfigScreen(this)))
                        .width(OptionWidgets.WIDTH).build()));
    }

    @Override
    public void removed() {
        PartyFinderConfig.save();
        super.removed();
    }
}
