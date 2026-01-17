package com.devmod.npc.command;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

import com.devmod.npc.dialog.DialogRegistry;
import com.devmod.npc.dialog.DialogSet;
import com.devmod.npc.io.DialogSetIO;
import com.devmod.npc.network.NpcNetworkHandler;

/**
 * Commands for managing NPC dialog sets.
 * Provides export/import functionality for dialog JSON files.
 *
 * <p>Available commands:
 * <ul>
 *   <li>/npc dialog list - Lists all available dialog sets</li>
 *   <li>/npc dialog export {@literal <id>} - Exports a dialog set to JSON</li>
 *   <li>/npc dialog import {@literal <file>} - Imports a dialog set from JSON</li>
 *   <li>/npc dialog info {@literal <id>} - Shows info about a dialog set</li>
 * </ul>
 */
public final class NpcDialogCommand {

    private NpcDialogCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("npc")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("dialog")
                    .then(Commands.literal("list")
                        .executes(NpcDialogCommand::listDialogs))
                    .then(Commands.literal("export")
                        .then(Commands.argument("id", Objects.requireNonNull(StringArgumentType.string()))
                            .suggests(NpcDialogCommand::suggestDialogIds)
                            .executes(NpcDialogCommand::exportDialog)))
                    .then(Commands.literal("import")
                        .then(Commands.argument("file", Objects.requireNonNull(StringArgumentType.string()))
                            .suggests(NpcDialogCommand::suggestImportFiles)
                            .executes(NpcDialogCommand::importDialog)))
                    .then(Commands.literal("edit")
                        .then(Commands.argument("id", Objects.requireNonNull(StringArgumentType.string()))
                            .suggests(NpcDialogCommand::suggestDialogIds)
                            .executes(NpcDialogCommand::openDialogEditor)))
                    .then(Commands.literal("info")
                        .then(Commands.argument("id", Objects.requireNonNull(StringArgumentType.string()))
                            .suggests(NpcDialogCommand::suggestDialogIds)
                            .executes(NpcDialogCommand::showDialogInfo)))
                    .then(Commands.literal("reload")
                        .executes(NpcDialogCommand::reloadDialogs))
                    .executes(NpcDialogCommand::showHelp))
                .executes(NpcDialogCommand::showHelp)
        );
    }

    private static int showHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("\u00A7e=== NPC Dialog Commands ==="), false);
        source.sendSuccess(() -> Component.literal("\u00A77/npc dialog list \u00A7f- List all dialog sets"), false);
        source.sendSuccess(() -> Component.literal("\u00A77/npc dialog export <id> \u00A7f- Export to JSON"), false);
        source.sendSuccess(() -> Component.literal("\u00A77/npc dialog import <file> \u00A7f- Import from JSON"), false);
        source.sendSuccess(() -> Component.literal("\u00A77/npc dialog edit <id> \u00A7f- Open dialog editor"), false);
        source.sendSuccess(() -> Component.literal("\u00A77/npc dialog info <id> \u00A7f- Show dialog info"), false);
        source.sendSuccess(() -> Component.literal("\u00A77/npc dialog reload \u00A7f- Reload dialog files"), false);
        return 1;
    }

    private static int listDialogs(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        var allDialogs = DialogRegistry.getAllDialogSets();

        if (allDialogs.isEmpty()) {
            source.sendSuccess(() -> Component.literal("\u00A77No dialog sets found"), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("\u00A7e=== Dialog Sets (" + allDialogs.size() + ") ==="), false);

        var ids = new ArrayList<>(allDialogs.keySet());
        ids.sort(String::compareTo);
        for (String id : ids) {
            DialogSet set = allDialogs.get(id);
            if (set == null) continue;
            String status = set.isPreset() ? "\u00A78[preset]" : "\u00A7a[custom]";
            int nodeCount = set.nodes().size();

            MutableComponent line = Component.literal("  \u00A7f" + id + " " + status + " \u00A78(" + nodeCount + " nodes)");

            // Add click action to show info
            line.setStyle(Objects.requireNonNull(Objects.requireNonNull(Style.EMPTY)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/npc dialog info " + id))
                .withHoverEvent(new HoverEvent(Objects.requireNonNull(HoverEvent.Action.SHOW_TEXT),
                    Objects.requireNonNull(Component.literal("Click to view details"))))));

            source.sendSuccess(() -> line, false);
        }

        // Show exported files
        Path[] exported = DialogSetIO.listExportedDialogs();
        if (exported.length > 0) {
            source.sendSuccess(() -> Component.literal(""), false);
            source.sendSuccess(() -> Component.literal("\u00A7e=== Exported Files (" + exported.length + ") ==="), false);
            for (Path file : exported) {
                String fileName = file.getFileName().toString();
                source.sendSuccess(() -> Component.literal("  \u00A77" + fileName), false);
            }
        }

        return 1;
    }

    private static int exportDialog(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String id = Objects.requireNonNull(StringArgumentType.getString(context, "id"));

        Optional<DialogSet> dialogSet = DialogRegistry.getDialogSet(id);
        if (dialogSet.isEmpty()) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Dialog set not found: " + id)));
            return 0;
        }

        try {
            Path exportPath = Objects.requireNonNull(DialogSetIO.getDefaultExportPath(id));
            DialogSetIO.exportToFile(dialogSet.get(), exportPath);

            MutableComponent successMsg = Component.literal("\u00A7aExported dialog '" + id + "' to: ");
            MutableComponent pathText = Component.literal("\u00A7n" + exportPath.toString());
            pathText.setStyle(Objects.requireNonNull(Objects.requireNonNull(Style.EMPTY)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, Objects.requireNonNull(exportPath.toAbsolutePath().toString())))
                .withHoverEvent(new HoverEvent(Objects.requireNonNull(HoverEvent.Action.SHOW_TEXT),
                    Objects.requireNonNull(Component.literal("Click to open file location"))))));

            source.sendSuccess(() -> successMsg.append(pathText), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Failed to export: " + e.getMessage())));
            return 0;
        }
    }

    private static int importDialog(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String fileName = Objects.requireNonNull(StringArgumentType.getString(context, "file"));

        // Add .json extension if not present
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".json")) {
            fileName = fileName + ".json";
        }

        Path importPath = DialogSetIO.getExportDirectory().resolve(fileName);

        Optional<DialogSet> dialogSet = DialogSetIO.importFromFile(Objects.requireNonNull(importPath));
        if (dialogSet.isEmpty()) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Failed to import dialog from: " + importPath)));
            return 0;
        }

        DialogSet set = dialogSet.get();

        var result = DialogRegistry.registerCustomDialog(Objects.requireNonNull(set));
        if (!result.success()) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Failed to register dialog: " + result.message())));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("\u00A7aImported dialog: " + set.id()), false);
        source.sendSuccess(() -> Component.literal("\u00A77  Name: \u00A7f" + set.name()), false);
        source.sendSuccess(() -> Component.literal("\u00A77  Nodes: \u00A7f" + set.nodes().size()), false);
        source.sendSuccess(() -> Component.literal("\u00A77  Entry: \u00A7f" + set.entryNodeId()), false);
        return 1;
    }

    private static int openDialogEditor(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String id = Objects.requireNonNull(StringArgumentType.getString(context, "id"));

        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Only players can open the dialog editor")));
            return 0;
        }

        DialogSet dialogSet = DialogRegistry.getDialogSet(Objects.requireNonNull(id)).orElse(null);
        NpcNetworkHandler.openDialogEditor(player, Objects.requireNonNull(id), dialogSet);
        source.sendSuccess(() -> Component.literal("\u00A7aOpened dialog editor: " + id), false);
        return 1;
    }

    private static int showDialogInfo(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String id = Objects.requireNonNull(StringArgumentType.getString(context, "id"));

        Optional<DialogSet> dialogSet = DialogRegistry.getDialogSet(id);
        if (dialogSet.isEmpty()) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Dialog set not found: " + id)));
            return 0;
        }

        DialogSet set = dialogSet.get();

        source.sendSuccess(() -> Component.literal("\u00A7e=== Dialog: " + set.id() + " ==="), false);
        source.sendSuccess(() -> Component.literal("\u00A77Name: \u00A7f" + set.name()), false);
        source.sendSuccess(() -> Component.literal("\u00A77Entry Node: \u00A7f" + set.entryNodeId()), false);
        source.sendSuccess(() -> Component.literal("\u00A77Nodes: \u00A7f" + set.nodes().size()), false);
        source.sendSuccess(() -> Component.literal("\u00A77Revision: \u00A7f" + set.revision()), false);
        source.sendSuccess(() -> Component.literal("\u00A77Type: " + (set.isPreset() ? "\u00A78Preset" : "\u00A7aCustom")), false);

        // List nodes
        if (!set.nodes().isEmpty()) {
            source.sendSuccess(() -> Component.literal(""), false);
            source.sendSuccess(() -> Component.literal("\u00A7eNodes:"), false);

            set.nodes().forEach((nodeId, node) -> {
                int optCount = node.options().size();
                String indicator = nodeId.equals(set.entryNodeId()) ? "\u00A7a\u25B6 " : "  ";
                source.sendSuccess(() -> Component.literal(indicator + "\u00A77" + nodeId +
                    " \u00A78(" + optCount + " options, " + node.lines().size() + " lines)"), false);
            });
        }

        return 1;
    }

    private static int reloadDialogs(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        Path[] files = DialogSetIO.listExportedDialogs();
        int loaded = DialogRegistry.reloadFromDisk();
        int failed = Math.max(0, files.length - loaded);

        final int loadedCount = loaded;
        final int failedCount = failed;

        if (loadedCount > 0 || failedCount > 0) {
            source.sendSuccess(() -> Component.literal("\u00A7aReloaded " + loadedCount + " dialog(s)"), false);
            if (failedCount > 0) {
                source.sendSuccess(() -> Component.literal("\u00A7cFailed to load " + failedCount + " dialog(s)"), false);
            }
        } else {
            source.sendSuccess(() -> Component.literal("\u00A77No dialog files found in " + DialogSetIO.getExportDirectory()), false);
        }

        return 1;
    }

    private static CompletableFuture<Suggestions> suggestDialogIds(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {

        String input = builder.getRemaining().toLowerCase(Locale.ROOT);

        DialogRegistry.getAllDialogIds().stream()
            .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(input))
            .forEach(builder::suggest);

        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestImportFiles(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {

        String input = builder.getRemaining().toLowerCase(Locale.ROOT);

        for (Path file : DialogSetIO.listExportedDialogs()) {
            String fileName = file.getFileName().toString();
            String nameWithoutExt = fileName.replaceAll("\\.json$", "");

            if (nameWithoutExt.toLowerCase(Locale.ROOT).startsWith(input)) {
                builder.suggest(nameWithoutExt);
            }
        }

        return builder.buildFuture();
    }
}
