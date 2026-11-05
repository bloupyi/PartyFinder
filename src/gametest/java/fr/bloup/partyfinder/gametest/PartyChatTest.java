package fr.bloup.partyfinder.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.bloup.partyfinder.client.PartyActions;
import fr.bloup.partyfinder.client.activity.Activities;
import fr.bloup.partyfinder.client.config.PartyFinderConfig;
import fr.bloup.partyfinder.client.gui.RolePickScreen;
import fr.bloup.partyfinder.client.net.BackendConnection;
import fr.bloup.partyfinder.client.net.BackendEvents;
import fr.bloup.partyfinder.client.party.InviteAccept;
import fr.bloup.partyfinder.client.party.Listing;
import fr.bloup.partyfinder.client.party.PartyChat;
import fr.bloup.partyfinder.client.party.PartyFinderState;
import fr.bloup.partyfinder.client.party.Rank;
import fr.bloup.partyfinder.client.party.ReportSummary;
import fr.bloup.partyfinder.client.party.StatsCard;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Party chat in a finder party, one screenshot per step: the queued message, the Party Finder style join request (and
 * its stats on hover), Hypixel's framed party messages hidden (still received) and replaced by one line, and the
 * requester's side where only "You have joined" stays.
 */
public class PartyChatTest implements FabricClientGameTest {
    // Hypixel's party messages with their colors: blue struck-through frame, yellow text, ranks in their colors.
    private static final String FRAME = "§9§m-----------------------------------------------------";
    private static final String MVP_PLUS = "§b[MVP§c+§b] ";
    private static final String MVP_PLUS_PLUS_TAG = "§6[MVP§c++§6] ";
    private static final String VIP = "§a[VIP] ";
    private static final String STATS = """
            {"sb": 312, "cata": 43, "secrets": 18906, "selectedClass": "archer",
             "classes": {"healer": 38, "mage": 36, "berserk": 38, "archer": 40, "tank": 36},
             "skills": {"fishing": 50}, "slayers": {}, "kuudra": {},
             "floors": {"5": 120, "6": 80, "7": 210}, "master": {"5": 12, "7": 30},
             "items": ["TERMINATOR", "LAST_BREATH", "ICE_SPRAY_WAND", "ASPECT_OF_THE_END"]}""";
    private static final Rank MVP_PLUS_PLUS = Rank.parse(JsonParser.parseString("{\"rank\": \"MVP_PLUS_PLUS\", \"plus\": \"RED\"}"));

    @Override
    public void runTest(ClientGameTestContext context) {
        // The test world is not Hypixel: keep the auto-accept's /party accept from reaching it.
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> !command.startsWith("party accept"));
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            JsonObject stats = JsonParser.parseString(STATS).getAsJsonObject();
            PartyFinderState.JoinRequest request = new PartyFinderState.JoinRequest(
                    new Listing.Player(UUID.randomUUID(), "ForagingMommy"), new ReportSummary(0, Map.of(), 12), stats, false, MVP_PLUS_PLUS, null);

            // 1. Owner publishes: the queued message.
            step(context, "chat_1_queued", client -> {
                BackendConnection.disconnect();
                PartyFinderConfig.get().cleanPartyChat = true;
                PartyFinderConfig.get().autoAcceptInvites = true;
                client.options.chatHeightUnfocused().set(1.0);
                client.options.chatWidth().set(1.0);
                Listing.Player self = new Listing.Player(client.getUser().getProfileId(), client.getUser().getName());
                PartyFinderState.setStatus(PartyFinderState.Status.ONLINE);
                PartyFinderState.setSession(self, false, ReportSummary.NONE);
                PartyFinderState.replaceAll(List.of());
                long now = System.currentTimeMillis();
                PartyFinderState.upsert(new Listing("own", self, "dungeons_floor", Map.of(), "", "", 1, 5, Listing.NO_REPORT_LIMIT,
                        Map.of(), List.of(), ReportSummary.NONE, List.of(new Listing.Member(self.uuid(), self.name(), null, false, null, null)),
                        null, now, now, "dungeons", List.of()));
            });

            // 2. A player asks to join.
            step(context, "chat_2_join_request", client -> BackendEvents.onJoinRequest(request));

            // 3. Same message, chat open and the mouse on "(Hover to view stats)": the stats card.
            context.setScreen(() -> new ChatScreen("", false));
            context.waitTicks(3);
            double[] cursor = context.computeOnClient(client -> {
                double scale = client.getWindow().getGuiScale();
                // Bottom chat line is the buttons, the one above is the hover hint.
                int y = client.getWindow().getGuiScaledHeight() - 40 - 9 - 4;
                return new double[]{20 * scale, y * scale};
            });
            context.getInput().setCursorPos(cursor[0], cursor[1]);
            context.waitTicks(3);
            context.takeScreenshot("chat_3_join_request_hover");
            context.setScreen(() -> null);

            // 4. Accepted, invited and joined: Hypixel's framed messages are hidden, one line of ours instead.
            step(context, "chat_4_member_joined", client -> {
                PartyChat.accepted(request);
                PartyChat.expectInvite("ForagingMommy");
                Component invited = hypixel(client, MVP_PLUS + client.getUser().getName() + " §einvited " + MVP_PLUS_PLUS_TAG
                        + "ForagingMommy §eto the party! They have §c60 §eseconds to accept.");
                Component joined = hypixel(client, MVP_PLUS_PLUS_TAG + "ForagingMommy §ejoined the party.");
                check(PartyChat.isHidden(invited) && PartyChat.isHidden(joined), "invite and join hidden");
            });

            // 5. Other party messages in one line each; a player typing the same words is left alone.
            step(context, "chat_5_party_lines", client -> {
                List<Component> lines = partyLines(client);
                check(lines.stream().allMatch(PartyChat::isHidden), "party lines hidden");
                Component typed = Component.literal(VIP + "Someone§f: ForagingMommy joined the party.");
                client.gui.chatListener().handleSystemMessage(typed, false);
                check(!PartyChat.isHidden(typed), "player chat left alone");
            });
            // The same messages with the clean party chat off: Hypixel's originals, for comparison.
            step(context, "chat_5b_clean_off", client -> {
                PartyFinderConfig.get().cleanPartyChat = false;
                check(partyLines(client).stream().noneMatch(PartyChat::isHidden), "nothing hidden when off");
                PartyFinderConfig.get().cleanPartyChat = true;
            });

            // 6-7. The stats card on its own, for two categories.
            step(context, "chat_6_card_dungeons", client -> PartyActions.chat(StatsCard.card("ForagingMommy", MVP_PLUS_PLUS, stats,
                    request.reports(), false, Activities.get("dungeons_floor"), "dungeons", null)));
            step(context, "chat_7_card_fishing", client -> PartyActions.chat(StatsCard.card("ForagingMommy", MVP_PLUS_PLUS, stats,
                    ReportSummary.NONE, false, Activities.get("sea_creatures"), "fishing", null)));
            // Kuudra: the role the requester picked leads the request line and the card.
            step(context, "chat_7b_kuudra_request", client -> {
                Listing.Player self = new Listing.Player(client.getUser().getProfileId(), client.getUser().getName());
                long now = System.currentTimeMillis();
                PartyFinderState.upsert(new Listing("own", self, kuudraActivity(), Map.of(), "", "", 1, 4, Listing.NO_REPORT_LIMIT,
                        Map.of(), List.of("stun", "dps", "dps"), ReportSummary.NONE,
                        List.of(new Listing.Member(self.uuid(), self.name(), null, false, null, null)), null, now, now, "kuudra", List.of()));
                BackendEvents.onJoinRequest(new PartyFinderState.JoinRequest(
                        new Listing.Player(UUID.randomUUID(), "Stunner"), ReportSummary.NONE, kuudraStats(), false, MVP_PLUS_PLUS, "stun"));
            });
            step(context, "chat_7c_kuudra_card", client -> PartyActions.chat(StatsCard.card("Stunner", MVP_PLUS_PLUS, kuudraStats(),
                    ReportSummary.NONE, false, Activities.get(kuudraActivity()), "kuudra", "stun")));

            // Joining a Kuudra party that wants roles: pick one of the open ones.
            context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
            context.setScreen(() -> new RolePickScreen(null, PartyFinderState.ownListing().orElseThrow(), List.of("stun", "dps", "dps")));
            context.waitTicks(3);
            context.takeScreenshot("chat_7d_kuudra_role_pick");
            context.setScreen(() -> null);

            // 8. Requester side: the auto-accepted invite is hidden, Hypixel's "You have joined" stays.
            step(context, "chat_8_requester", client -> {
                PartyFinderState.replaceAll(List.of());
                BackendEvents.onJoinSent(new Listing.Player(UUID.randomUUID(), "PartyOwner"));
                InviteAccept.expect("PartyOwner", "PartyOwner");
                Component invite = Component.literal(FRAME + "\n" + MVP_PLUS + "PartyOwner §ehas invited you to join their party!\n"
                        + "§eYou have §c60 §eseconds to accept. §6Click here to join!\n" + FRAME);
                client.gui.chatListener().handleSystemMessage(invite, false);
                check(PartyChat.isHidden(invite), "auto-accepted invite hidden");
                client.gui.chatListener().handleSystemMessage(Component.literal(FRAME + "\n§eYou have joined " + MVP_PLUS
                        + "PartyOwner's §eparty!\n\n§eYou'll be partying with: " + VIP + "Friend\n" + FRAME), false);
            });

            context.runOnClient(client -> {
                PartyFinderState.replaceAll(List.of());
                PartyChat.reset();
                InviteAccept.reset();
            });
        }
    }

    private static JsonObject kuudraStats() {
        return JsonParser.parseString("""
                {"sb": 290, "skills": {"combat": 55}, "kuudra": {"none": 40, "hot": 60, "burning": 80, "fiery": 150, "infernal": 120},
                 "items": ["TERMINATOR", "RAGNAROCK_AXE"]}""").getAsJsonObject();
    }

    private static String kuudraActivity() {
        return Activities.all().stream().filter(a -> a.category().equals("kuudra")).findFirst().map(a -> a.id()).orElse("kuudra");
    }

    /** Clears the chat, runs the step, then screenshots it. */
    private static void step(ClientGameTestContext context, String name, Consumer<Minecraft> action) {
        context.runOnClient(client -> {
            client.gui.toastManager().clear();
            client.gui.hud.getChat().clearMessages(false);
            action.accept(client);
        });
        context.waitTicks(5);
        context.takeScreenshot(name);
    }

    /** Leave, kick, transfer and expired invite, as Hypixel sends them. */
    private static List<Component> partyLines(Minecraft client) {
        return List.of(
                hypixel(client, VIP + "Someone §ehas left the party."),
                hypixel(client, VIP + "Troll §ehas been removed from the party."),
                hypixel(client, "§eThe party was transferred to " + MVP_PLUS_PLUS_TAG + "ForagingMommy §eby " + MVP_PLUS
                        + client.getUser().getName()),
                hypixel(client, "§eThe party invite to " + VIP + "Late §ehas expired"));
    }

    /** A framed party message as Hypixel sends it. */
    private static Component hypixel(Minecraft client, String line) {
        Component message = Component.literal(FRAME + "\n" + line + "\n" + FRAME);
        client.gui.chatListener().handleSystemMessage(message, false);
        return message;
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError("Party chat: " + what);
    }
}
