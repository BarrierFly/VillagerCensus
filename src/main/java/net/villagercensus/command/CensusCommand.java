package net.villagercensus.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import net.villagercensus.census.CensusManager;

public class CensusCommand
{
    public static void register()
    {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
        {
            LiteralArgumentBuilder<FabricClientCommandSource> root = LiteralArgumentBuilder.<FabricClientCommandSource>literal("census")
                    .executes(ctx ->
                    {
                        sendHelp(ctx.getSource());
                        return 1;
                    })
                    .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("help").executes(ctx ->
                    {
                        sendHelp(ctx.getSource());
                        return 1;
                    }))
                    .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(ctx ->
                    {
                        ctx.getSource().sendFeedback(Component.literal(CensusManager.getInstance().status()));
                        return 1;
                    }))
                    .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("start")
                            .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.greedyString())
                                    .executes(ctx ->
                                    {
                                        CensusManager.getInstance().startSession(StringArgumentType.getString(ctx, "name"));
                                        return 1;
                                    }))
                            .executes(ctx ->
                            {
                                error(ctx.getSource(), "villagercensus.message.name_required");
                                return 0;
                            }))
                    .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("fork")
                            .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("name", StringArgumentType.greedyString())
                                    .executes(ctx ->
                                    {
                                        CensusManager.getInstance().forkSession(StringArgumentType.getString(ctx, "name"));
                                        return 1;
                                    }))
                            .executes(ctx ->
                            {
                                error(ctx.getSource(), "villagercensus.message.fork_name_required");
                                return 0;
                            }))
                    .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("stop").executes(ctx ->
                    {
                        CensusManager.getInstance().stopSession();
                        return 1;
                    }))
                    .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("abort").executes(ctx ->
                    {
                        CensusManager.getInstance().abortSession();
                        return 1;
                    }))
                    .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("undo").executes(ctx ->
                    {
                        CensusManager.getInstance().undo();
                        return 1;
                    }))
                    .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("remark")
                            .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("text", StringArgumentType.greedyString())
                                    .executes(ctx ->
                                    {
                                        CensusManager.getInstance().remark(StringArgumentType.getString(ctx, "text"));
                                        return 1;
                                    }))
                            .executes(ctx ->
                            {
                                CensusManager.getInstance().remark("");
                                return 1;
                            }))
                    .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("resume").executes(ctx ->
                    {
                        CensusManager.getInstance().resumeSession();
                        return 1;
                    }))
                    .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("discard").executes(ctx ->
                    {
                        CensusManager.getInstance().discardDraft();
                        return 1;
                    }))
                    .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("pause").executes(ctx ->
                    {
                        CensusManager.getInstance().togglePaused();
                        return 1;
                    }))
                    .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reverse").executes(ctx ->
                    {
                        CensusManager.getInstance().toggleReverseMarkers();
                        return 1;
                    }));

            dispatcher.register(root);
        });
    }

    private static void sendHelp(FabricClientCommandSource source)
    {
        send(source, "villagercensus.command.help.start");
        send(source, "villagercensus.command.help.fork");
        send(source, "villagercensus.command.help.stop");
        send(source, "villagercensus.command.help.abort");
        send(source, "villagercensus.command.help.undo");
        send(source, "villagercensus.command.help.remark");
        send(source, "villagercensus.command.help.status");
        send(source, "villagercensus.command.help.resume");
        send(source, "villagercensus.command.help.discard");
        send(source, "villagercensus.command.help.pause");
        send(source, "villagercensus.command.help.reverse");
    }

    private static void send(FabricClientCommandSource source, String key)
    {
        source.sendFeedback(Component.translatable(key));
    }

    private static void error(FabricClientCommandSource source, String key, Object... args)
    {
        source.sendError(Component.translatable(key, args));
    }
}
