package com.devmod.portal.command;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import com.devmod.portal.PortalColor;
import com.devmod.portal.PortalData;
import com.devmod.portal.PortalRegistry;

/**
 * Commands for managing and viewing portal information.
 * Provides listing, filtering, and statistics for custom portals.
 */
public final class PortalCommand {

    private PortalCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("portal")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list")
                    .executes(PortalCommand::listAll)
                    .then(Commands.argument("filter", StringArgumentType.word())
                        .suggests(PortalCommand::suggestFilters)
                        .executes(PortalCommand::listFiltered)))
                .then(Commands.literal("stats")
                    .executes(PortalCommand::showStats))
                .then(Commands.literal("nearest")
                    .executes(PortalCommand::findNearest))
                .executes(PortalCommand::showHelp)
        );
    }

    private static int showHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("\u00A7e=== Custom Portals ==="), false);
        source.sendSuccess(() -> Component.literal("\u00A77/portal list \u00A7f- List all portals"), false);
        source.sendSuccess(() -> Component.literal("\u00A77/portal list <color> \u00A7f- Filter by color"), false);
        source.sendSuccess(() -> Component.literal("\u00A77/portal stats \u00A7f- Show portal statistics"), false);
        source.sendSuccess(() -> Component.literal("\u00A77/portal nearest \u00A7f- Find nearest portal"), false);
        return 1;
    }

    private static int listAll(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        PortalRegistry registry = PortalRegistry.get(level);

        List<PortalData> portals = getAllPortals(registry);

        if (portals.isEmpty()) {
            source.sendSuccess(() -> Component.literal("\u00A77No portals registered"), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("\u00A7e=== Registered Portals (" + portals.size() + ") ==="), false);

        int shown = 0;
        int limit = 20;
        for (PortalData portal : portals) {
            if (shown >= limit) {
                int remaining = portals.size() - limit;
                source.sendSuccess(() -> Component.literal("\u00A78... and " + remaining + " more"), false);
                break;
            }
            sendPortalInfo(source, portal);
            shown++;
        }

        return 1;
    }

    private static int listFiltered(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        PortalRegistry registry = PortalRegistry.get(level);
        String filter = StringArgumentType.getString(context, "filter").toLowerCase(Locale.ROOT);

        // Try to parse as color
        PortalColor filterColor = null;
        for (PortalColor color : PortalColor.values()) {
            if (color.getSerializedName().equals(filter) || color.name().toLowerCase(Locale.ROOT).equals(filter)) {
                filterColor = color;
                break;
            }
        }

        List<PortalData> portals;
        String filterDesc;

        if (filterColor != null) {
            portals = registry.getByColor(filterColor);
            filterDesc = filterColor.getSerializedName();
        } else if (filter.equals("linked")) {
            portals = getAllPortals(registry).stream()
                .filter(PortalData::isLinked)
                .toList();
            filterDesc = "linked";
        } else if (filter.equals("unlinked")) {
            portals = getAllPortals(registry).stream()
                .filter(p -> !p.isLinked())
                .toList();
            filterDesc = "unlinked";
        } else {
            source.sendFailure(Component.literal("Unknown filter: " + filter + ". Use a color name, 'linked', or 'unlinked'."));
            return 0;
        }

        if (portals.isEmpty()) {
            source.sendSuccess(() -> Component.literal("\u00A77No " + filterDesc + " portals found"), false);
            return 1;
        }

        final String finalDesc = filterDesc;
        source.sendSuccess(() -> Component.literal("\u00A7e=== " + finalDesc + " Portals (" + portals.size() + ") ==="), false);

        int shown = 0;
        int limit = 20;
        for (PortalData portal : portals) {
            if (shown >= limit) {
                int remaining = portals.size() - limit;
                source.sendSuccess(() -> Component.literal("\u00A78... and " + remaining + " more"), false);
                break;
            }
            sendPortalInfo(source, portal);
            shown++;
        }

        return 1;
    }

    private static int showStats(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        PortalRegistry registry = PortalRegistry.get(level);

        List<PortalData> portals = getAllPortals(registry);
        int total = portals.size();
        long linked = portals.stream().filter(PortalData::isLinked).count();
        int pairs = (int) (linked / 2);

        source.sendSuccess(() -> Component.literal("\u00A7e=== Portal Statistics ==="), false);
        source.sendSuccess(() -> Component.literal("\u00A77Total portals: \u00A7f" + total), false);
        source.sendSuccess(() -> Component.literal("\u00A77Linked portals: \u00A7a" + linked + " \u00A78(" + pairs + " pairs)"), false);
        source.sendSuccess(() -> Component.literal("\u00A77Unlinked portals: \u00A7c" + (total - linked)), false);

        // Count by color
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("\u00A7eBy color:"), false);

        for (PortalColor color : PortalColor.values()) {
            long count = portals.stream().filter(p -> p.color() == color).count();
            if (count > 0) {
                source.sendSuccess(() -> Component.literal("  \u00A77" + color.getSerializedName() + ": \u00A7f" + count), false);
            }
        }

        return 1;
    }

    private static int findNearest(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        PortalRegistry registry = PortalRegistry.get(level);

        BlockPos playerPos = BlockPos.containing(source.getPosition());
        ResourceLocation currentDim = level.dimension().location();

        List<PortalData> portals = getAllPortals(registry);

        Optional<PortalData> nearest = portals.stream()
            .filter(p -> p.dimension().orElse(null) != null && p.dimension().get().equals(currentDim))
            .filter(p -> p.position().isPresent())
            .min((a, b) -> {
                double distA = a.position().get().distSqr(playerPos);
                double distB = b.position().get().distSqr(playerPos);
                return Double.compare(distA, distB);
            });

        if (nearest.isEmpty()) {
            source.sendSuccess(() -> Component.literal("\u00A77No portals found in this dimension"), false);
            return 1;
        }

        PortalData portal = nearest.get();
        BlockPos pos = portal.position().get();
        double distance = Math.sqrt(pos.distSqr(playerPos));

        source.sendSuccess(() -> Component.literal("\u00A7e=== Nearest Portal ==="), false);
        sendPortalInfo(source, portal);
        source.sendSuccess(() -> Component.literal("\u00A77Distance: \u00A7f" + String.format("%.1f", distance) + " blocks"), false);

        return 1;
    }

    private static void sendPortalInfo(CommandSourceStack source, PortalData portal) {
        String colorName = portal.color().getSerializedName();
        String linkStatus = portal.isLinked() ? "\u00A7aLinked" : "\u00A7cNot Linked";
        String position = portal.position().map(pos ->
            pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
        ).orElse("unknown");
        String dimension = portal.dimension().map(dim -> dim.getPath()).orElse("unknown");

        source.sendSuccess(() -> Component.literal(
            "  \u00A77" + colorName + " \u00A78[" + position + "] \u00A78(" + dimension + ") " + linkStatus
        ), false);
    }

    private static List<PortalData> getAllPortals(PortalRegistry registry) {
        // Get all colors and combine
        return Arrays.stream(PortalColor.values())
            .flatMap(color -> registry.getByColor(color).stream())
            .toList();
    }

    private static CompletableFuture<Suggestions> suggestFilters(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {

        String input = builder.getRemaining().toLowerCase(Locale.ROOT);

        // Suggest colors
        Arrays.stream(PortalColor.values())
            .map(PortalColor::getSerializedName)
            .filter(name -> name.startsWith(input))
            .forEach(builder::suggest);

        // Suggest status filters
        if ("linked".startsWith(input)) builder.suggest("linked");
        if ("unlinked".startsWith(input)) builder.suggest("unlinked");

        return builder.buildFuture();
    }
}
