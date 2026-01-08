package com.devmod.recipe.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import com.devmod.recipe.CraftingRecipeData;
import com.devmod.recipe.RecipeConfigManager;
import com.devmod.recipe.RecipeData;
import com.devmod.recipe.SmeltingRecipeData;
import com.devmod.recipe.SmithingRecipeData;
import com.devmod.recipe.StonecuttingRecipeData;
import com.devmod.recipe.export.RecipeExportHelper;

/**
 * Command for exporting recipes to script format.
 *
 * <p>Usage:
 * <pre>
 *   /devmod exportrecipes                       - Export all custom recipes
 *   /devmod exportrecipes --pretty              - With formatting
 *   /devmod exportrecipes --file                - Save to file
 *   /devmod exportrecipes crafting              - Export only crafting recipes
 *   /devmod exportrecipes smelting --pretty     - Export smelting, formatted
 * </pre>
 */
public final class RecipeExportCommand {

    private static final DateTimeFormatter FILE_DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private RecipeExportCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("devmod")
                .then(Commands.literal("exportrecipes")
                    .requires(source -> source.hasPermission(2))
                    // Export all recipes
                    .executes(ctx -> exportRecipes(ctx, null, false, false))
                    // With pretty flag
                    .then(Commands.literal("--pretty")
                        .executes(ctx -> exportRecipes(ctx, null, true, false))
                        .then(Commands.literal("--file")
                            .executes(ctx -> exportRecipes(ctx, null, true, true))))
                    // With file flag
                    .then(Commands.literal("--file")
                        .executes(ctx -> exportRecipes(ctx, null, false, true))
                        .then(Commands.literal("--pretty")
                            .executes(ctx -> exportRecipes(ctx, null, true, true))))
                    // Export by type
                    .then(Commands.argument("type", StringArgumentType.word())
                        .suggests(RecipeExportCommand::suggestTypes)
                        .executes(ctx -> exportByType(ctx, false, false))
                        .then(Commands.literal("--pretty")
                            .executes(ctx -> exportByType(ctx, true, false))
                            .then(Commands.literal("--file")
                                .executes(ctx -> exportByType(ctx, true, true))))
                        .then(Commands.literal("--file")
                            .executes(ctx -> exportByType(ctx, false, true))
                            .then(Commands.literal("--pretty")
                                .executes(ctx -> exportByType(ctx, true, true)))))
                )
        );
    }

    private static CompletableFuture<Suggestions> suggestTypes(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String input = builder.getRemaining().toLowerCase(Locale.ROOT);

        String[] types = {"crafting", "smelting", "smithing", "stonecutting"};
        for (String type : types) {
            if (type.startsWith(input)) {
                builder.suggest(type);
            }
        }

        return builder.buildFuture();
    }

    private static int exportByType(CommandContext<CommandSourceStack> ctx,
                                    boolean pretty, boolean toFile) {
        String type = StringArgumentType.getString(ctx, "type").toLowerCase(Locale.ROOT);
        return exportRecipes(ctx, type, pretty, toFile);
    }

    private static int exportRecipes(CommandContext<CommandSourceStack> ctx,
                                     @Nullable String typeFilter, boolean pretty, boolean toFile) {
        CommandSourceStack source = ctx.getSource();

        List<RecipeData> recipes = RecipeConfigManager.getAllCustomRecipes();
        if (recipes.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No custom recipes to export"), false);
            return 0;
        }

        // Filter by type if specified
        if (typeFilter != null) {
            recipes = filterByType(recipes, typeFilter);
            if (recipes.isEmpty()) {
                source.sendSuccess(() -> Component.literal(
                    "No " + typeFilter + " recipes to export"), false);
                return 0;
            }
        }

        // Export to command strings
        List<String> commands = RecipeExportHelper.toCommandStrings(recipes, pretty);

        if (toFile) {
            String filename = typeFilter != null ? "recipes_" + typeFilter : "recipes_all";
            return saveToFile(source, filename, buildFileContent(commands, typeFilter, pretty));
        }

        // Output to chat
        for (String cmd : commands) {
            source.sendSuccess(() -> Component.literal(cmd), false);
        }

        String typeDesc = typeFilter != null ? typeFilter + " " : "";
        source.sendSuccess(() -> Component.literal(
            "Exported " + commands.size() + " " + typeDesc + "recipe(s)"), false);

        return commands.size();
    }

    private static List<RecipeData> filterByType(List<RecipeData> recipes, String type) {
        return switch (type) {
            case "crafting" -> recipes.stream()
                .filter(r -> r instanceof CraftingRecipeData)
                .toList();
            case "smelting" -> recipes.stream()
                .filter(r -> r instanceof SmeltingRecipeData)
                .toList();
            case "smithing" -> recipes.stream()
                .filter(r -> r instanceof SmithingRecipeData)
                .toList();
            case "stonecutting" -> recipes.stream()
                .filter(r -> r instanceof StonecuttingRecipeData)
                .toList();
            default -> recipes;
        };
    }

    private static String buildFileContent(List<String> commands, @Nullable String typeFilter, boolean pretty) {
        StringBuilder sb = new StringBuilder();

        sb.append("// DevMod Recipe Export");
        if (typeFilter != null) {
            sb.append(" - ").append(typeFilter.substring(0, 1).toUpperCase(Locale.ROOT))
              .append(typeFilter.substring(1));
        }
        sb.append("\n");
        sb.append("// Generated: ").append(ZonedDateTime.now(ZoneId.systemDefault())).append("\n");
        sb.append("// Total: ").append(commands.size()).append(" recipe(s)\n\n");

        for (String cmd : commands) {
            sb.append(cmd);
            if (pretty) {
                sb.append("\n\n");
            } else {
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    private static int saveToFile(CommandSourceStack source, String filename, String content) {
        try {
            String timestamp = ZonedDateTime.now(ZoneId.systemDefault()).format(FILE_DATE_FORMAT);
            Path exportDir = Path.of("config/devmod/exports");
            Files.createDirectories(exportDir);

            Path file = exportDir.resolve("devmod_" + filename + "_" + timestamp + ".ds");
            Files.writeString(file, content);

            source.sendSuccess(() -> Component.literal("Exported to: " + file), false);
            return 1;
        } catch (IOException e) {
            source.sendFailure(Component.literal("Failed to save file: " + e.getMessage()));
            return 0;
        }
    }
}
