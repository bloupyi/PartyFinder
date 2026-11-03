package fr.bloup.partyfinder.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.bloup.partyfinder.client.activity.Activities;
import fr.bloup.partyfinder.client.config.PartyFinderConfig;
import fr.bloup.partyfinder.client.gui.CreateListingScreen;
import fr.bloup.partyfinder.client.gui.CategoryMenuScreen;
import fr.bloup.partyfinder.client.gui.ModerationScreen;
import fr.bloup.partyfinder.client.gui.ReportScreen;
import fr.bloup.partyfinder.client.net.BackendConnection;
import fr.bloup.partyfinder.client.party.PartyFinderState;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class PartyFinderCommands {
    private PartyFinderCommands() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(build("partyfinder"));
        dispatcher.register(build("pf"));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> build(String name) {
        return literal(name)
                .executes(ctx -> {
                    PartyActions.openNextTick(new CategoryMenuScreen(null));
                    return 1;
                })
                .then(literal("create").executes(ctx -> {
                    Activities.reload();
                    BackendConnection.connect();
                    PartyActions.openNextTick(new CreateListingScreen(new CategoryMenuScreen(null)));
                    return 1;
                }))
                .then(literal("delist").executes(ctx -> {
                    BackendConnection.delist();
                    return 1;
                }))
                .then(literal("accept").then(argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            PartyFinderState.joinRequests().forEach(r -> builder.suggest(r.player().name()));
                            return builder.buildFuture();
                        })
                        .executes(ctx -> respond(ctx.getSource(), StringArgumentType.getString(ctx, "player"), true))))
                .then(literal("deny").then(argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            PartyFinderState.joinRequests().forEach(r -> builder.suggest(r.player().name()));
                            return builder.buildFuture();
                        })
                        .executes(ctx -> respond(ctx.getSource(), StringArgumentType.getString(ctx, "player"), false))))
                .then(literal("mod").executes(ctx -> {
                    if (!PartyFinderState.isOp()) {
                        ctx.getSource().sendError(Component.translatable("partyfinder.error.not_op"));
                        return 0;
                    }
                    PartyActions.openNextTick(new ModerationScreen(new CategoryMenuScreen(null)));
                    return 1;
                }))
                .then(literal("report").then(argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            PartyFinderState.knownNames().forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String target = StringArgumentType.getString(ctx, "player");
                            return PartyFinderState.findPlayer(target).map(player -> {
                                PartyActions.openNextTick(new ReportScreen(null, player));
                                return 1;
                            }).orElseGet(() -> {
                                ctx.getSource().sendError(Component.translatable("partyfinder.error.report_unknown"));
                                return 0;
                            });
                        })))
                .then(literal("reconnect").executes(ctx -> {
                    BackendConnection.reconnect();
                    return 1;
                }))
                .then(literal("reload").executes(ctx -> {
                    PartyFinderConfig.load();
                    Activities.reload();
                    BackendConnection.reconnect();
                    ctx.getSource().sendFeedback(Component.translatable("partyfinder.command.reloaded"));
                    return 1;
                }));
    }

    private static int respond(FabricClientCommandSource source, String player, boolean accept) {
        boolean found = accept ? PartyActions.accept(player) : PartyActions.deny(player);
        if (!found) {
            source.sendError(Component.translatable("partyfinder.command.no_request", player));
            return 0;
        }
        return 1;
    }
}
