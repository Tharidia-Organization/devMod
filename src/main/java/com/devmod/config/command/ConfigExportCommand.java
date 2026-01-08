package com.devmod.config.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.devmod.config.handler.ConfigHandlerRegistry;
import com.devmod.config.handler.IConfigHandler;
import com.devmod.config.handler.export.IExportableConfigHandler;
import com.devmod.config.stats.IItemStats;

/**
 * Command for exporting config to script format.
 *
 * <p>Usage:
 * <pre>
 *   /devmod export weapons                    - Export all weapon configs
 *   /devmod export weapons --pretty           - With formatting
 *   /devmod export weapons --file             - Save to file
 *   /devmod export all                        - Export everything
 *   /devmod export all --pretty --file        - All, formatted, to file
 *   /devmod export hand                       - Export held item
 * </pre>
 */
public class ConfigExportCommand {

    private static final DateTimeFormatter FILE_DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("devmod")
                .then(Commands.literal("export")
                    .requires(source -> source.hasPermission(2))
                    // Export specific type
                    .then(Commands.argument("type", StringArgumentType.word())
                        .suggests(ConfigExportCommand::suggestTypes)
                        // Base: export all of type
                        .executes(ctx -> exportType(ctx, false, false))
                        // With pretty flag
                        .then(Commands.literal("--pretty")
                            .executes(ctx -> exportType(ctx, true, false))
                            .then(Commands.literal("--file")
                                .executes(ctx -> exportType(ctx, true, true))))
                        // With file flag
                        .then(Commands.literal("--file")
                            .executes(ctx -> exportType(ctx, false, true))
                            .then(Commands.literal("--pretty")
                                .executes(ctx -> exportType(ctx, true, true)))))
                    // Export hand-held item
                    .then(Commands.literal("hand")
                        .executes(ctx -> exportHand(ctx, false))
                        .then(Commands.literal("--pretty")
                            .executes(ctx -> exportHand(ctx, true))))
                )
        );
    }

    private static CompletableFuture<Suggestions> suggestTypes(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String input = builder.getRemaining().toLowerCase(Locale.ROOT);

        // Add "all" option
        if ("all".startsWith(input)) {
            builder.suggest("all");
        }

        // Add handler namespaces
        for (IConfigHandler<?> handler : ConfigHandlerRegistry.getAll()) {
            if (handler instanceof IExportableConfigHandler<?> exp) {
                String ns = exp.getCommandNamespace();
                if (ns.toLowerCase(Locale.ROOT).startsWith(input)) {
                    builder.suggest(ns);
                }
            }
        }

        return builder.buildFuture();
    }

    private static int exportType(CommandContext<CommandSourceStack> ctx,
                                  boolean pretty, boolean toFile) {
        String type = StringArgumentType.getString(ctx, "type");
        CommandSourceStack source = ctx.getSource();

        if ("all".equalsIgnoreCase(type)) {
            return exportAll(source, pretty, toFile);
        }

        // Find handler by namespace
        IExportableConfigHandler<?> handler = findHandler(type);
        if (handler == null) {
            source.sendFailure(Component.literal("Unknown config type: " + type));
            return 0;
        }

        List<String> commands = handler.exportAllToCommandStrings(pretty);
        if (commands.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No " + type + " configs to export"), false);
            return 0;
        }

        if (toFile) {
            return saveToFile(source, type, String.join("\n\n", commands));
        }

        // Output to chat
        for (String cmd : commands) {
            source.sendSuccess(() -> Component.literal(cmd), false);
        }
        source.sendSuccess(() -> Component.literal(
            "Exported " + commands.size() + " " + type + " config(s)"), false);

        return commands.size();
    }

    private static int exportHand(CommandContext<CommandSourceStack> ctx, boolean pretty) {
        CommandSourceStack source = ctx.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Must be run by a player"));
            return 0;
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.literal("No item in hand"));
            return 0;
        }

        // Find applicable handler
        IConfigHandler<?> rawHandler = ConfigHandlerRegistry.getForItem(held);
        if (!(rawHandler instanceof IExportableConfigHandler<?> handler)) {
            source.sendFailure(Component.literal("No exportable handler for this item type"));
            return 0;
        }

        String command = exportSingleItem(handler, held.getItem(), pretty);
        if (command == null) {
            source.sendFailure(Component.literal("No config for held item"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(command), false);
        return 1;
    }

    private static int exportAll(CommandSourceStack source, boolean pretty, boolean toFile) {
        StringBuilder sb = new StringBuilder();
        sb.append("// DevMod Config Export - All Types\n");
        sb.append("// Generated: ").append(ZonedDateTime.now(ZoneId.systemDefault())).append("\n\n");

        int total = 0;
        for (IConfigHandler<?> rawHandler : ConfigHandlerRegistry.getAll()) {
            if (rawHandler instanceof IExportableConfigHandler<?> handler) {
                List<String> commands = handler.exportAllToCommandStrings(pretty);
                if (!commands.isEmpty()) {
                    sb.append("// === ").append(handler.getCommandNamespace().toUpperCase(Locale.ROOT))
                      .append(" ===\n\n");
                    for (String cmd : commands) {
                        sb.append(cmd).append("\n\n");
                    }
                    total += commands.size();
                }
            }
        }

        if (total == 0) {
            source.sendSuccess(() -> Component.literal("No configs to export"), false);
            return 0;
        }

        if (toFile) {
            return saveToFile(source, "all", sb.toString());
        }

        // Output to chat (may be truncated for large exports)
        final String output = sb.toString();
        source.sendSuccess(() -> Component.literal(output), false);
        return total;
    }

    private static int saveToFile(CommandSourceStack source, String type, String content) {
        try {
            String timestamp = ZonedDateTime.now(ZoneId.systemDefault()).format(FILE_DATE_FORMAT);
            Path exportDir = Path.of("config/devmod/exports");
            Files.createDirectories(exportDir);

            Path file = exportDir.resolve("devmod_" + type + "_" + timestamp + ".ds");
            Files.writeString(file, content);

            source.sendSuccess(() -> Component.literal("Exported to: " + file), false);
            return 1;
        } catch (IOException e) {
            source.sendFailure(Component.literal("Failed to save file: " + e.getMessage()));
            return 0;
        }
    }

    private static <S extends IItemStats> String exportSingleItem(
            IExportableConfigHandler<S> handler, Item item, boolean pretty) {
        S stats = handler.getGlobalStats(item);
        if (stats == null) {
            stats = handler.createDefault();
        }
        return handler.exportToCommandString(item, stats, pretty);
    }

    private static IExportableConfigHandler<?> findHandler(String namespace) {
        for (IConfigHandler<?> handler : ConfigHandlerRegistry.getAll()) {
            if (handler instanceof IExportableConfigHandler<?> exp) {
                if (exp.getCommandNamespace().equalsIgnoreCase(namespace)) {
                    return exp;
                }
            }
        }
        return null;
    }
}
