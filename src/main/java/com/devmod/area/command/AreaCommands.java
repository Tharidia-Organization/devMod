package com.devmod.area.command;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import com.devmod.DevMod;
import com.devmod.area.builder.AreaBuildTaskManager;
import com.devmod.area.data.AreaDefinition;
import com.devmod.area.data.AreaRegistry;
import com.devmod.area.snapshot.AreaSnapshot;
import com.devmod.area.snapshot.AreaSnapshotManager;
import com.devmod.area.snapshot.AreaSnapshotRegistry;
import com.devmod.area.template.AreaTemplate;
import com.devmod.area.template.AreaTemplateRegistry;
import com.devmod.zone.data.ZoneDefinition;
import com.devmod.zone.data.ZoneRegistry;

/**
 * CLI commands for Area module operations.
 * Provides quick access to area, snapshot, and template management.
 */
public final class AreaCommands {

    private static final int ITEMS_PER_PAGE = 10;

    private AreaCommands() {}

    /**
     * Registers all area commands under /devmod area.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("devmod")
                .then(Commands.literal("area")
                    .requires(src -> src.hasPermission(2))
                    .then(listCommand())
                    .then(infoCommand())
                    .then(buildCommand())
                    .then(pauseCommand())
                    .then(resumeCommand())
                    .then(cloneCommand())
                    .then(promoteCommand())
                    .then(statusCommand())
                    .then(snapshotCommands())
                    .then(templateCommands())
                    .then(zoneCommands())
                    .then(deleteCommand())
                    .executes(AreaCommands::showHelp)
                )
        );
    }

    // ========================================================================
    // Help
    // ========================================================================

    private static int showHelp(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        source.sendSuccess(() -> Objects.requireNonNull(Component.literal("=== DevMod Area Commands ===").withStyle(ChatFormatting.GOLD)), false);
        source.sendSuccess(() -> Objects.requireNonNull(Component.literal("/devmod area list [page]").withStyle(ChatFormatting.YELLOW)
            .append(Objects.requireNonNull(Component.literal(" - List all areas").withStyle(ChatFormatting.GRAY)))), false);
        source.sendSuccess(() -> Objects.requireNonNull(Component.literal("/devmod area info <name>").withStyle(ChatFormatting.YELLOW)
            .append(Objects.requireNonNull(Component.literal(" - Show area details").withStyle(ChatFormatting.GRAY)))), false);
        source.sendSuccess(() -> Objects.requireNonNull(Component.literal("/devmod area build <name> [clear]").withStyle(ChatFormatting.YELLOW)
            .append(Objects.requireNonNull(Component.literal(" - Build an area").withStyle(ChatFormatting.GRAY)))), false);
        source.sendSuccess(() -> Objects.requireNonNull(Component.literal("/devmod area pause <name>").withStyle(ChatFormatting.YELLOW)
            .append(Objects.requireNonNull(Component.literal(" - Pause active build").withStyle(ChatFormatting.GRAY)))), false);
        source.sendSuccess(() -> Objects.requireNonNull(Component.literal("/devmod area resume <name>").withStyle(ChatFormatting.YELLOW)
            .append(Objects.requireNonNull(Component.literal(" - Resume paused build").withStyle(ChatFormatting.GRAY)))), false);
        source.sendSuccess(() -> Objects.requireNonNull(Component.literal("/devmod area clone <name> <newName>").withStyle(ChatFormatting.YELLOW)
            .append(Objects.requireNonNull(Component.literal(" - Clone an area").withStyle(ChatFormatting.GRAY)))), false);
        source.sendSuccess(() -> Objects.requireNonNull(Component.literal("/devmod area promote <name>").withStyle(ChatFormatting.YELLOW)
            .append(Objects.requireNonNull(Component.literal(" - Set as main hub").withStyle(ChatFormatting.GRAY)))), false);
        source.sendSuccess(() -> Objects.requireNonNull(Component.literal("/devmod area status").withStyle(ChatFormatting.YELLOW)
            .append(Objects.requireNonNull(Component.literal(" - Show build queue status").withStyle(ChatFormatting.GRAY)))), false);
        source.sendSuccess(() -> Objects.requireNonNull(Component.literal("/devmod area snapshot list|capture|restore|delete").withStyle(ChatFormatting.YELLOW)
            .append(Objects.requireNonNull(Component.literal(" - Snapshot commands").withStyle(ChatFormatting.GRAY)))), false);
        source.sendSuccess(() -> Objects.requireNonNull(Component.literal("/devmod area template list|save|delete|apply").withStyle(ChatFormatting.YELLOW)
            .append(Objects.requireNonNull(Component.literal(" - Template commands").withStyle(ChatFormatting.GRAY)))), false);
        source.sendSuccess(() -> Objects.requireNonNull(Component.literal("/devmod area zone list|info|link|unlink").withStyle(ChatFormatting.YELLOW)
            .append(Objects.requireNonNull(Component.literal(" - Zone commands").withStyle(ChatFormatting.GRAY)))), false);
        source.sendSuccess(() -> Objects.requireNonNull(Component.literal("/devmod area delete <name> <confirm>").withStyle(ChatFormatting.YELLOW)
            .append(Objects.requireNonNull(Component.literal(" - Delete an area").withStyle(ChatFormatting.GRAY)))), false);
        return 1;
    }

    // ========================================================================
    // List Command
    // ========================================================================

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> listCommand() {
        return Commands.literal("list")
            .executes(ctx -> listAreas(ctx, 1))
            .then(Commands.argument("page", Objects.requireNonNull(IntegerArgumentType.integer(1)))
                .executes(ctx -> listAreas(ctx, IntegerArgumentType.getInteger(ctx, "page"))));
    }

    private static int listAreas(CommandContext<CommandSourceStack> ctx, int page) {
        CommandSourceStack source = ctx.getSource();
        var server = source.getServer();

        AreaRegistry registry = AreaRegistry.get(Objects.requireNonNull(server));
        Collection<AreaDefinition> allAreas = registry.getAllAreas();
        int totalAreas = allAreas.size();

        if (totalAreas == 0) {
            source.sendSuccess(() -> Component.literal("No areas defined.").withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        int totalPages = (totalAreas + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE;
        int safePage = Math.max(1, Math.min(page, totalPages));
        int skip = (safePage - 1) * ITEMS_PER_PAGE;

        List<AreaDefinition> pageAreas = allAreas.stream()
            .skip(skip)
            .limit(ITEMS_PER_PAGE)
            .toList();

        source.sendSuccess(() -> Component.literal("=== Areas (Page " + safePage + "/" + totalPages + ") ===")
            .withStyle(ChatFormatting.GOLD), false);

        UUID mainHubId = registry.getMainHubId();

        for (AreaDefinition area : pageAreas) {
            boolean isMainHub = area.id().equals(mainHubId);
            MutableComponent line = Component.literal(" - ")
                .append(Objects.requireNonNull(Component.literal(Objects.requireNonNull(area.name())).withStyle(ChatFormatting.WHITE)))
                .append(Objects.requireNonNull(Component.literal(" [" + area.shape().getSerializedName() + "]").withStyle(ChatFormatting.GRAY)));

            if (isMainHub) {
                line.append(Objects.requireNonNull(Component.literal(" [MAIN HUB]").withStyle(ChatFormatting.GREEN)));
            }

            // Add click to get info
            line.withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/devmod area info " + area.name()))
                .withHoverEvent(new HoverEvent(Objects.requireNonNull(HoverEvent.Action.SHOW_TEXT), Objects.requireNonNull(Component.literal("Click for details")))));

            source.sendSuccess(() -> line, false);
        }

        if (safePage < totalPages) {
            source.sendSuccess(() -> Component.literal("[Next Page]").withStyle(ChatFormatting.AQUA)
                .withStyle(style -> style.withClickEvent(
                    new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/devmod area list " + (safePage + 1)))), false);
        }

        return totalAreas;
    }

    // ========================================================================
    // Info Command
    // ========================================================================

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> infoCommand() {
        return Commands.literal("info")
            .then(Commands.argument("name", Objects.requireNonNull(StringArgumentType.greedyString()))
                .suggests(AreaCommands::suggestAreaNames)
                .executes(ctx -> showInfo(ctx, StringArgumentType.getString(ctx, "name"))));
    }

    private static int showInfo(CommandContext<CommandSourceStack> ctx, String name) {
        CommandSourceStack source = ctx.getSource();
        AreaRegistry registry = AreaRegistry.get(Objects.requireNonNull(source.getServer()));

        Optional<AreaDefinition> areaOpt = findAreaByName(registry, name);
        if (areaOpt.isEmpty()) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Area not found: " + name)));
            return 0;
        }

        AreaDefinition area = areaOpt.get();
        source.sendSuccess(() -> Objects.requireNonNull(Component.literal("=== Area: " + Objects.requireNonNull(area.name()) + " ===").withStyle(ChatFormatting.GOLD)), false);
        source.sendSuccess(() -> Objects.requireNonNull(Component.literal("ID: ").withStyle(ChatFormatting.GRAY)
            .append(Objects.requireNonNull(Component.literal(Objects.requireNonNull(area.id().toString())).withStyle(ChatFormatting.WHITE)))), false);
        source.sendSuccess(() -> Objects.requireNonNull(Component.literal("Shape: ").withStyle(ChatFormatting.GRAY)
            .append(Objects.requireNonNull(Component.literal(area.shape().getSerializedName()).withStyle(ChatFormatting.WHITE)))), false);
        source.sendSuccess(() -> Objects.requireNonNull(Component.literal("Dimensions: ").withStyle(ChatFormatting.GRAY)
            .append(Objects.requireNonNull(Component.literal(area.dimensions().width() + "x" + area.dimensions().length() + "x" + area.dimensions().height())
                .withStyle(ChatFormatting.WHITE)))), false);
        source.sendSuccess(() -> Objects.requireNonNull(Component.literal("Center: ").withStyle(ChatFormatting.GRAY)
            .append(Objects.requireNonNull(Component.literal(Objects.requireNonNull(area.centerPosition().toShortString())).withStyle(ChatFormatting.WHITE)))), false);
        source.sendSuccess(() -> Objects.requireNonNull(Component.literal("Dimension: ").withStyle(ChatFormatting.GRAY)
            .append(Objects.requireNonNull(Component.literal(Objects.requireNonNull(area.dimensionId().toString())).withStyle(ChatFormatting.WHITE)))), false);
        source.sendSuccess(() -> Objects.requireNonNull(Component.literal("Generation: ").withStyle(ChatFormatting.GRAY)
            .append(Objects.requireNonNull(Component.literal(area.generationType().getSerializedName()).withStyle(ChatFormatting.WHITE)))), false);

        UUID mainHubId = registry.getMainHubId();
        if (area.id().equals(mainHubId)) {
            source.sendSuccess(() -> Objects.requireNonNull(Component.literal("Main Hub: ").withStyle(ChatFormatting.GRAY)
                .append(Objects.requireNonNull(Component.literal("YES").withStyle(ChatFormatting.GREEN)))), false);
        }

        // Show if currently building
        if (AreaBuildTaskManager.INSTANCE.isBuildingArea(area.id())) {
            int progress = AreaBuildTaskManager.INSTANCE.getBuildProgress(area.id());
            source.sendSuccess(() -> Objects.requireNonNull(Component.literal("Build Status: ").withStyle(ChatFormatting.GRAY)
                .append(Objects.requireNonNull(Component.literal("IN PROGRESS (" + progress + "%)").withStyle(ChatFormatting.YELLOW)))), false);
        }

        return 1;
    }

    // ========================================================================
    // Build Command
    // ========================================================================

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildCommand() {
        return Commands.literal("build")
            .then(Commands.argument("name", Objects.requireNonNull(StringArgumentType.string()))
                .suggests(AreaCommands::suggestAreaNames)
                .executes(ctx -> buildArea(ctx, StringArgumentType.getString(ctx, "name"), false))
                .then(Commands.argument("clear", Objects.requireNonNull(BoolArgumentType.bool()))
                    .executes(ctx -> buildArea(ctx, StringArgumentType.getString(ctx, "name"),
                        BoolArgumentType.getBool(ctx, "clear")))));
    }

    private static int buildArea(CommandContext<CommandSourceStack> ctx, String name, boolean clearFirst) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        AreaRegistry registry = AreaRegistry.get(Objects.requireNonNull(source.getServer()));

        Optional<AreaDefinition> areaOpt = findAreaByName(registry, name);
        if (areaOpt.isEmpty()) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Area not found: " + name)));
            return 0;
        }

        AreaDefinition area = areaOpt.get();
        ServerLevel level = player.serverLevel();

        // Check dimension
        if (!area.dimensionId().equals(level.dimension().location())) {
            source.sendFailure(Objects.requireNonNull(Component.literal("You must be in dimension: " + area.dimensionId())));
            return 0;
        }

        // Check if already building
        if (AreaBuildTaskManager.INSTANCE.isBuildingArea(area.id())) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Area is already being built.")));
            return 0;
        }

        // SEC-08 fix: Block builds while a snapshot restore is in progress
        if (AreaSnapshotManager.INSTANCE.isRestoringArea(Objects.requireNonNull(area.id()))) {
            source.sendFailure(Objects.requireNonNull(Component.translatable("area.message.error_restore_in_progress")));
            return 0;
        }

        // Start build
        AreaBuildTaskManager.BuildStartResult result = AreaBuildTaskManager.INSTANCE.startBuild(
            level, area, player, clearFirst, true);

        switch (result) {
            case STARTED_IMMEDIATE -> source.sendSuccess(() ->
                Objects.requireNonNull(Component.literal("Build completed immediately: " + area.name()).withStyle(ChatFormatting.GREEN)), true);
            case STARTED_MULTI_TICK -> source.sendSuccess(() ->
                Objects.requireNonNull(Component.literal("Build started: " + area.name()).withStyle(ChatFormatting.GREEN)), true);
            case QUEUED -> {
                int pos = AreaBuildTaskManager.INSTANCE.getQueuePosition(Objects.requireNonNull(area.id())) + 1;
                source.sendSuccess(() ->
                    Objects.requireNonNull(Component.literal("Build queued (position " + pos + "): " + area.name()).withStyle(ChatFormatting.YELLOW)), true);
            }
            case ALREADY_BUILDING -> source.sendFailure(Objects.requireNonNull(Component.literal("Area is already being built.")));
            case QUEUE_FULL -> source.sendFailure(Objects.requireNonNull(Component.literal("Build queue is full. Try again later.")));
        }

        return 1;
    }

    // ========================================================================
    // Pause Command
    // ========================================================================

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> pauseCommand() {
        return Commands.literal("pause")
            .then(Commands.argument("name", Objects.requireNonNull(StringArgumentType.string()))
                .suggests(AreaCommands::suggestAreaNames)
                .executes(ctx -> pauseBuild(ctx, StringArgumentType.getString(ctx, "name"))));
    }

    private static int pauseBuild(CommandContext<CommandSourceStack> ctx, String name) {
        CommandSourceStack source = ctx.getSource();
        AreaRegistry registry = AreaRegistry.get(Objects.requireNonNull(source.getServer()));

        Optional<AreaDefinition> areaOpt = findAreaByName(registry, name);
        if (areaOpt.isEmpty()) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Area not found: " + name)));
            return 0;
        }

        AreaDefinition area = areaOpt.get();

        if (!AreaBuildTaskManager.INSTANCE.isBuildingArea(area.id())) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Area is not currently building: " + name)));
            return 0;
        }

        boolean paused = AreaBuildTaskManager.INSTANCE.pauseBuild(
            Objects.requireNonNull(area.id()), Objects.requireNonNull(source.getServer()));

        if (paused) {
            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("Build paused: " + area.name()).withStyle(ChatFormatting.YELLOW)), true);
            return 1;
        } else {
            source.sendFailure(Objects.requireNonNull(Component.literal("Failed to pause build.")));
            return 0;
        }
    }

    // ========================================================================
    // Resume Command
    // ========================================================================

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> resumeCommand() {
        return Commands.literal("resume")
            .then(Commands.argument("name", Objects.requireNonNull(StringArgumentType.string()))
                .suggests(AreaCommands::suggestAreaNames)
                .executes(ctx -> resumeBuild(ctx, StringArgumentType.getString(ctx, "name"))));
    }

    private static int resumeBuild(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        AreaRegistry registry = AreaRegistry.get(Objects.requireNonNull(source.getServer()));

        Optional<AreaDefinition> areaOpt = findAreaByName(registry, name);
        if (areaOpt.isEmpty()) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Area not found: " + name)));
            return 0;
        }

        AreaDefinition area = areaOpt.get();

        boolean resumed = AreaBuildTaskManager.INSTANCE.resumeBuild(
            Objects.requireNonNull(source.getServer()),
            Objects.requireNonNull(area.id()),
            player);

        if (resumed) {
            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("Build resumed: " + area.name()).withStyle(ChatFormatting.GREEN)), true);
            return 1;
        } else {
            source.sendFailure(Objects.requireNonNull(Component.literal("No paused build found for: " + name)));
            return 0;
        }
    }

    // ========================================================================
    // Clone Command
    // ========================================================================

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> cloneCommand() {
        return Commands.literal("clone")
            .then(Commands.argument("name", Objects.requireNonNull(StringArgumentType.string()))
                .suggests(AreaCommands::suggestAreaNames)
                .then(Commands.argument("newName", Objects.requireNonNull(StringArgumentType.greedyString()))
                    .executes(ctx -> cloneArea(ctx,
                        StringArgumentType.getString(ctx, "name"),
                        StringArgumentType.getString(ctx, "newName")))));
    }

    private static int cloneArea(CommandContext<CommandSourceStack> ctx, String name, String newName) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        AreaRegistry registry = AreaRegistry.get(Objects.requireNonNull(source.getServer()));

        Optional<AreaDefinition> sourceOpt = findAreaByName(registry, name);
        if (sourceOpt.isEmpty()) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Area not found: " + name)));
            return 0;
        }

        AreaDefinition sourceArea = sourceOpt.get();

        // Validate new name
        String trimmedName = newName.trim();
        if (trimmedName.isEmpty() || trimmedName.length() > 64) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Invalid name. Must be 1-64 characters.")));
            return 0;
        }

        // Check if name already exists
        for (AreaDefinition existing : registry.getAllAreas()) {
            if (existing.name().equalsIgnoreCase(trimmedName)) {
                source.sendFailure(Objects.requireNonNull(Component.literal("An area with this name already exists.")));
                return 0;
            }
        }

        // Create cloned area using builder
        AreaDefinition cloned = new AreaDefinition.Builder()
            .id(Objects.requireNonNull(UUID.randomUUID()))
            .name(trimmedName)
            .generationType(Objects.requireNonNull(sourceArea.generationType()))
            .shape(Objects.requireNonNull(sourceArea.shape()))
            .dimensions(Objects.requireNonNull(sourceArea.dimensions()))
            .center(Objects.requireNonNull(sourceArea.centerPosition()))
            .dimension(Objects.requireNonNull(sourceArea.dimensionId()))
            .palette(Objects.requireNonNull(sourceArea.palette()))
            .biomeConfig(sourceArea.biomeConfig())
            .options(Objects.requireNonNull(sourceArea.options()))
            .creator(player.getUUID())
            .linkedZone(null) // Don't copy linked zone for clone
            .customShapeNbt(sourceArea.customShapeNbt())
            .mainHub(false)
            .build();

        registry.createArea(cloned);

        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("Area cloned: " + sourceArea.name() + " -> " + trimmedName).withStyle(ChatFormatting.GREEN)), true);
        DevMod.LOGGER.info("Player {} cloned area {} -> {} ({})",
            player.getName().getString(), sourceArea.name(), trimmedName, cloned.id());

        return 1;
    }

    // ========================================================================
    // Promote Command
    // ========================================================================

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> promoteCommand() {
        return Commands.literal("promote")
            .then(Commands.argument("name", Objects.requireNonNull(StringArgumentType.string()))
                .suggests(AreaCommands::suggestAreaNames)
                .executes(ctx -> promoteToMainHub(ctx, StringArgumentType.getString(ctx, "name"))));
    }

    private static int promoteToMainHub(CommandContext<CommandSourceStack> ctx, String name) {
        CommandSourceStack source = ctx.getSource();
        AreaRegistry registry = AreaRegistry.get(Objects.requireNonNull(source.getServer()));

        Optional<AreaDefinition> areaOpt = findAreaByName(registry, name);
        if (areaOpt.isEmpty()) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Area not found: " + name)));
            return 0;
        }

        AreaDefinition area = areaOpt.get();
        UUID currentMainHubId = registry.getMainHubId();

        if (area.id().equals(currentMainHubId)) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Area is already the main hub.")));
            return 0;
        }

        // Promote new area first
        AreaDefinition promoted = area.withMainHub(true);
        boolean updated = registry.updateArea(Objects.requireNonNull(area.id()), promoted, area.revision());
        if (!updated) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Failed to promote area (revision mismatch).")));
            return 0;
        }

        // Demote old main hub
        if (currentMainHubId != null) {
            Optional<AreaDefinition> oldMainHubOpt = registry.getArea(currentMainHubId);
            if (oldMainHubOpt.isPresent()) {
                AreaDefinition oldMainHub = oldMainHubOpt.get();
                AreaDefinition demoted = oldMainHub.withMainHub(false);
                registry.updateArea(currentMainHubId, demoted, oldMainHub.revision());
            }
        }

        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("Promoted to main hub: " + area.name()).withStyle(ChatFormatting.GREEN)), true);
        DevMod.LOGGER.info("Area promoted to main hub via CLI: {} ({})", area.name(), area.id());

        return 1;
    }

    // ========================================================================
    // Status Command
    // ========================================================================

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> statusCommand() {
        return Commands.literal("status")
            .executes(AreaCommands::showBuildStatus);
    }

    private static int showBuildStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("=== Build Queue Status ===").withStyle(ChatFormatting.GOLD)), false);

        // Show active build count
        int activeCount = AreaBuildTaskManager.INSTANCE.getActiveBuildCount();
        if (activeCount == 0) {
            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("No active builds.").withStyle(ChatFormatting.GRAY)), false);
        } else {
            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("Active Builds: " + activeCount).withStyle(ChatFormatting.GREEN)), false);

            // Check each area to see if it's building and show progress
            AreaRegistry registry = AreaRegistry.get(Objects.requireNonNull(source.getServer()));
            for (AreaDefinition area : registry.getAllAreas()) {
                if (AreaBuildTaskManager.INSTANCE.isBuildingArea(area.id())) {
                    int progress = AreaBuildTaskManager.INSTANCE.getBuildProgress(area.id());
                    source.sendSuccess(() -> Objects.requireNonNull(
                        Component.literal(" - " + area.name() + ": " + progress + "%")
                            .withStyle(ChatFormatting.YELLOW)), false);
                }
            }
        }

        // Get queued builds
        int queuedCount = AreaBuildTaskManager.INSTANCE.getQueuedBuildCount();
        if (queuedCount > 0) {
            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("Queued: " + queuedCount + " builds waiting").withStyle(ChatFormatting.YELLOW)), false);
        }

        return 1;
    }

    // ========================================================================
    // Snapshot Commands
    // ========================================================================

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> snapshotCommands() {
        return Commands.literal("snapshot")
            .then(Commands.literal("list")
                .then(Commands.argument("areaName", Objects.requireNonNull(StringArgumentType.string()))
                    .suggests(AreaCommands::suggestAreaNames)
                    .executes(ctx -> listSnapshots(ctx, StringArgumentType.getString(ctx, "areaName")))))
            .then(Commands.literal("capture")
                .then(Commands.argument("areaName", Objects.requireNonNull(StringArgumentType.string()))
                    .suggests(AreaCommands::suggestAreaNames)
                    .executes(ctx -> captureSnapshot(ctx, StringArgumentType.getString(ctx, "areaName"), ""))
                    .then(Commands.argument("description", Objects.requireNonNull(StringArgumentType.greedyString()))
                        .executes(ctx -> captureSnapshot(ctx,
                            StringArgumentType.getString(ctx, "areaName"),
                            StringArgumentType.getString(ctx, "description"))))))
            .then(Commands.literal("restore")
                .then(Commands.argument("snapshotId", Objects.requireNonNull(UuidArgument.uuid()))
                    .executes(ctx -> restoreSnapshot(Objects.requireNonNull(ctx), UuidArgument.getUuid(ctx, "snapshotId")))))
            .then(Commands.literal("delete")
                .then(Commands.argument("snapshotId", Objects.requireNonNull(UuidArgument.uuid()))
                    .then(Commands.argument("confirm", Objects.requireNonNull(BoolArgumentType.bool()))
                        .executes(ctx -> deleteSnapshot(Objects.requireNonNull(ctx),
                            UuidArgument.getUuid(ctx, "snapshotId"),
                            BoolArgumentType.getBool(ctx, "confirm"))))));
    }

    private static int listSnapshots(CommandContext<CommandSourceStack> ctx, String areaName) {
        CommandSourceStack source = ctx.getSource();
        AreaRegistry registry = AreaRegistry.get(Objects.requireNonNull(source.getServer()));

        Optional<AreaDefinition> areaOpt = findAreaByName(registry, areaName);
        if (areaOpt.isEmpty()) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Area not found: " + areaName)));
            return 0;
        }

        AreaDefinition area = areaOpt.get();
        AreaSnapshotRegistry snapshotRegistry = AreaSnapshotRegistry.get(Objects.requireNonNull(source.getServer()));
        List<AreaSnapshot> snapshots = snapshotRegistry.getSnapshotsForArea(Objects.requireNonNull(area.id()));

        if (snapshots.isEmpty()) {
            source.sendSuccess(() -> Objects.requireNonNull(Component.literal("No snapshots for area: " + areaName).withStyle(ChatFormatting.YELLOW)), false);
            return 0;
        }

        source.sendSuccess(() -> Objects.requireNonNull(Component.literal("=== Snapshots for " + areaName + " ===").withStyle(ChatFormatting.GOLD)), false);

        for (AreaSnapshot snapshot : snapshots) {
            MutableComponent line = Component.literal(" - ")
                .append(Objects.requireNonNull(Component.literal(snapshot.id().toString().substring(0, 8) + "...").withStyle(ChatFormatting.WHITE)))
                .append(Objects.requireNonNull(Component.literal(" by " + snapshot.creatorName()).withStyle(ChatFormatting.GRAY)));

            if (snapshot.description() != null && !snapshot.description().isEmpty()) {
                line.append(Objects.requireNonNull(Component.literal(" \"" + snapshot.description() + "\"").withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY)));
            }

            line.withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/devmod area snapshot restore " + snapshot.id()))
                .withHoverEvent(new HoverEvent(Objects.requireNonNull(HoverEvent.Action.SHOW_TEXT), Objects.requireNonNull(Component.literal("Click to restore")))));

            source.sendSuccess(() -> line, false);
        }

        return snapshots.size();
    }

    private static int captureSnapshot(CommandContext<CommandSourceStack> ctx, String areaName, String description) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        AreaRegistry registry = AreaRegistry.get(Objects.requireNonNull(source.getServer()));

        Optional<AreaDefinition> areaOpt = findAreaByName(registry, areaName);
        if (areaOpt.isEmpty()) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Area not found: " + areaName)));
            return 0;
        }

        AreaDefinition area = areaOpt.get();
        ServerLevel level = player.serverLevel();

        // Check dimension
        if (!area.dimensionId().equals(level.dimension().location())) {
            source.sendFailure(Objects.requireNonNull(Component.literal("You must be in dimension: " + area.dimensionId())));
            return 0;
        }

        UUID snapshotId = AreaSnapshotManager.INSTANCE.startCapture(level, area, Objects.requireNonNull(description), player);
        if (snapshotId != null) {
            source.sendSuccess(() -> Objects.requireNonNull(Component.literal("Snapshot capture started for: " + areaName).withStyle(ChatFormatting.GREEN)), true);
            return 1;
        } else {
            source.sendFailure(Objects.requireNonNull(Component.literal("Failed to start snapshot capture.")));
            return 0;
        }
    }

    private static int restoreSnapshot(CommandContext<CommandSourceStack> ctx, UUID snapshotId) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();

        boolean started = AreaSnapshotManager.INSTANCE.startRestore(Objects.requireNonNull(level), Objects.requireNonNull(snapshotId), player);
        if (started) {
            source.sendSuccess(() -> Objects.requireNonNull(Component.literal("Snapshot restore started.").withStyle(ChatFormatting.GREEN)), true);
            return 1;
        } else {
            source.sendFailure(Objects.requireNonNull(Component.literal("Failed to start snapshot restore. Check logs for details.")));
            return 0;
        }
    }

    private static int deleteSnapshot(CommandContext<CommandSourceStack> ctx, UUID snapshotId, boolean confirm) {
        CommandSourceStack source = ctx.getSource();

        if (!confirm) {
            source.sendFailure(Objects.requireNonNull(Component.literal("You must confirm deletion with 'true'. This action cannot be undone!")));
            return 0;
        }

        AreaSnapshotRegistry registry = AreaSnapshotRegistry.get(Objects.requireNonNull(source.getServer()));

        Optional<AreaSnapshot> snapshotOpt = registry.getSnapshot(Objects.requireNonNull(snapshotId));
        if (snapshotOpt.isEmpty()) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Snapshot not found: " + snapshotId)));
            return 0;
        }

        AreaSnapshot snapshot = snapshotOpt.get();
        java.nio.file.Path worldFolder = Objects.requireNonNull(source.getServer())
            .getWorldPath(Objects.requireNonNull(net.minecraft.world.level.storage.LevelResource.ROOT));

        boolean deleted = registry.deleteSnapshot(snapshotId, worldFolder);
        if (deleted) {
            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("Snapshot deleted: " + snapshot.areaName()).withStyle(ChatFormatting.RED)), true);
            DevMod.LOGGER.info("Snapshot deleted via CLI: {} for area {}", snapshotId, snapshot.areaName());
            return 1;
        } else {
            source.sendFailure(Objects.requireNonNull(Component.literal("Failed to delete snapshot.")));
            return 0;
        }
    }

    // ========================================================================
    // Template Commands
    // ========================================================================

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> templateCommands() {
        return Commands.literal("template")
            .then(Commands.literal("list")
                .executes(AreaCommands::listTemplates))
            .then(Commands.literal("save")
                .then(Commands.argument("areaName", Objects.requireNonNull(StringArgumentType.string()))
                    .suggests(AreaCommands::suggestAreaNames)
                    .then(Commands.argument("templateName", Objects.requireNonNull(StringArgumentType.string()))
                        .executes(ctx -> saveTemplate(ctx,
                            StringArgumentType.getString(ctx, "areaName"),
                            StringArgumentType.getString(ctx, "templateName"),
                            ""))
                        .then(Commands.argument("description", Objects.requireNonNull(StringArgumentType.greedyString()))
                            .executes(ctx -> saveTemplate(ctx,
                                StringArgumentType.getString(ctx, "areaName"),
                                StringArgumentType.getString(ctx, "templateName"),
                                StringArgumentType.getString(ctx, "description")))))))
            .then(Commands.literal("delete")
                .then(Commands.argument("templateName", Objects.requireNonNull(StringArgumentType.string()))
                    .suggests(AreaCommands::suggestTemplateNames)
                    .then(Commands.argument("confirm", Objects.requireNonNull(BoolArgumentType.bool()))
                        .executes(ctx -> deleteTemplate(ctx,
                            StringArgumentType.getString(ctx, "templateName"),
                            BoolArgumentType.getBool(ctx, "confirm"))))))
            .then(Commands.literal("apply")
                .then(Commands.argument("templateName", Objects.requireNonNull(StringArgumentType.string()))
                    .suggests(AreaCommands::suggestTemplateNames)
                    .then(Commands.argument("newAreaName", Objects.requireNonNull(StringArgumentType.greedyString()))
                        .executes(ctx -> applyTemplate(ctx,
                            StringArgumentType.getString(ctx, "templateName"),
                            StringArgumentType.getString(ctx, "newAreaName"))))));
    }

    private static int listTemplates(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        AreaTemplateRegistry registry = AreaTemplateRegistry.get(Objects.requireNonNull(source.getServer()));
        Collection<AreaTemplate> templates = registry.getAllTemplates();

        if (templates.isEmpty()) {
            source.sendSuccess(() -> Objects.requireNonNull(Component.literal("No templates defined.").withStyle(ChatFormatting.YELLOW)), false);
            return 0;
        }

        source.sendSuccess(() -> Objects.requireNonNull(Component.literal("=== Area Templates ===").withStyle(ChatFormatting.GOLD)), false);

        for (AreaTemplate template : templates) {
            MutableComponent line = Component.literal(" - ")
                .append(Objects.requireNonNull(Component.literal(Objects.requireNonNull(template.name())).withStyle(ChatFormatting.WHITE)))
                .append(Objects.requireNonNull(Component.literal(" [" + template.shape().getSerializedName() + "]").withStyle(ChatFormatting.GRAY)));

            if (template.author() != null) {
                line.append(Objects.requireNonNull(Component.literal(" by " + template.author()).withStyle(ChatFormatting.DARK_GRAY)));
            }

            line.withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/devmod area template apply \"" + template.name() + "\" "))
                .withHoverEvent(new HoverEvent(Objects.requireNonNull(HoverEvent.Action.SHOW_TEXT),
                    Objects.requireNonNull(Component.literal("Click to apply template")))));

            source.sendSuccess(() -> line, false);
        }

        return templates.size();
    }

    private static int saveTemplate(CommandContext<CommandSourceStack> ctx, String areaName, String templateName, String description) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        AreaRegistry areaRegistry = AreaRegistry.get(Objects.requireNonNull(source.getServer()));
        AreaTemplateRegistry templateRegistry = AreaTemplateRegistry.get(Objects.requireNonNull(source.getServer()));

        Optional<AreaDefinition> areaOpt = findAreaByName(areaRegistry, areaName);
        if (areaOpt.isEmpty()) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Area not found: " + areaName)));
            return 0;
        }

        if (!templateRegistry.hasRoom()) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Template registry is full (max " + AreaTemplateRegistry.MAX_TEMPLATES + ").")));
            return 0;
        }

        AreaDefinition area = areaOpt.get();
        AreaTemplate template = AreaTemplate.fromDefinition(
            Objects.requireNonNull(area),
            Objects.requireNonNull(templateName.trim()),
            Objects.requireNonNull(description.trim()),
            Objects.requireNonNull(player.getName().getString())
        );

        Optional<UUID> savedId = templateRegistry.saveTemplate(template);
        if (savedId.isPresent()) {
            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("Template saved: " + templateName).withStyle(ChatFormatting.GREEN)), true);
            DevMod.LOGGER.info("Template saved via CLI: {} from area {} by {}",
                templateName, areaName, player.getName().getString());
            return 1;
        } else {
            source.sendFailure(Objects.requireNonNull(Component.literal("Failed to save template.")));
            return 0;
        }
    }

    private static int deleteTemplate(CommandContext<CommandSourceStack> ctx, String templateName, boolean confirm) {
        CommandSourceStack source = ctx.getSource();

        if (!confirm) {
            source.sendFailure(Objects.requireNonNull(Component.literal("You must confirm deletion with 'true'. This action cannot be undone!")));
            return 0;
        }

        AreaTemplateRegistry registry = AreaTemplateRegistry.get(Objects.requireNonNull(source.getServer()));

        // Find template by name
        Optional<AreaTemplate> templateOpt = registry.getAllTemplates().stream()
            .filter(t -> t.name().equalsIgnoreCase(templateName.trim()))
            .findFirst();

        if (templateOpt.isEmpty()) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Template not found: " + templateName)));
            return 0;
        }

        AreaTemplate template = templateOpt.get();
        boolean deleted = registry.deleteTemplate(Objects.requireNonNull(template.id()));

        if (deleted) {
            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("Template deleted: " + templateName).withStyle(ChatFormatting.RED)), true);
            DevMod.LOGGER.info("Template deleted via CLI: {} ({})", templateName, template.id());
            return 1;
        } else {
            source.sendFailure(Objects.requireNonNull(Component.literal("Failed to delete template.")));
            return 0;
        }
    }

    private static int applyTemplate(CommandContext<CommandSourceStack> ctx, String templateName, String newAreaName) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        AreaTemplateRegistry templateRegistry = AreaTemplateRegistry.get(Objects.requireNonNull(source.getServer()));
        AreaRegistry areaRegistry = AreaRegistry.get(Objects.requireNonNull(source.getServer()));

        // Find template by name
        Optional<AreaTemplate> templateOpt = templateRegistry.getAllTemplates().stream()
            .filter(t -> t.name().equalsIgnoreCase(templateName.trim()))
            .findFirst();

        if (templateOpt.isEmpty()) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Template not found: " + templateName)));
            return 0;
        }

        String trimmedName = newAreaName.trim();
        if (trimmedName.isEmpty() || trimmedName.length() > 64) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Invalid area name. Must be 1-64 characters.")));
            return 0;
        }

        // Check if name already exists
        for (AreaDefinition existing : areaRegistry.getAllAreas()) {
            if (existing.name().equalsIgnoreCase(trimmedName)) {
                source.sendFailure(Objects.requireNonNull(Component.literal("An area with this name already exists.")));
                return 0;
            }
        }

        AreaTemplate template = templateOpt.get();
        ServerLevel level = player.serverLevel();

        // Create area at player's position
        AreaDefinition newArea = template.toDefinition(
            Objects.requireNonNull(UUID.randomUUID()),
            trimmedName,
            Objects.requireNonNull(player.blockPosition()),
            Objects.requireNonNull(level.dimension().location()),
            player.getUUID()
        );

        areaRegistry.createArea(newArea);

        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("Area created from template: " + trimmedName).withStyle(ChatFormatting.GREEN)), true);
        DevMod.LOGGER.info("Area created from template via CLI: {} from {} by {}",
            trimmedName, templateName, player.getName().getString());

        return 1;
    }

    private static CompletableFuture<Suggestions> suggestTemplateNames(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        AreaTemplateRegistry registry = AreaTemplateRegistry.get(Objects.requireNonNull(ctx.getSource().getServer()));
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);

        for (AreaTemplate template : registry.getAllTemplates()) {
            String name = template.name();
            if (name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                if (name.contains(" ")) {
                    builder.suggest("\"" + name + "\"");
                } else {
                    builder.suggest(name);
                }
            }
        }

        return builder.buildFuture();
    }

    // ========================================================================
    // Zone Commands
    // ========================================================================

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> zoneCommands() {
        return Commands.literal("zone")
            .then(Commands.literal("list")
                .executes(AreaCommands::listZones))
            .then(Commands.literal("info")
                .then(Commands.argument("zoneId", Objects.requireNonNull(StringArgumentType.string()))
                    .suggests(AreaCommands::suggestZoneIds)
                    .executes(ctx -> showZoneInfo(ctx, StringArgumentType.getString(ctx, "zoneId")))))
            .then(Commands.literal("link")
                .then(Commands.argument("areaName", Objects.requireNonNull(StringArgumentType.string()))
                    .suggests(AreaCommands::suggestAreaNames)
                    .then(Commands.argument("zoneId", Objects.requireNonNull(StringArgumentType.string()))
                        .suggests(AreaCommands::suggestZoneIds)
                        .executes(ctx -> linkAreaToZone(ctx,
                            StringArgumentType.getString(ctx, "areaName"),
                            StringArgumentType.getString(ctx, "zoneId"))))))
            .then(Commands.literal("unlink")
                .then(Commands.argument("areaName", Objects.requireNonNull(StringArgumentType.string()))
                    .suggests(AreaCommands::suggestAreaNames)
                    .executes(ctx -> unlinkAreaFromZone(ctx, StringArgumentType.getString(ctx, "areaName")))));
    }

    private static int listZones(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ZoneRegistry registry = ZoneRegistry.get(Objects.requireNonNull(source.getServer()));
        Collection<ZoneDefinition> zones = registry.getAllZones();

        if (zones.isEmpty()) {
            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("No zones defined.").withStyle(ChatFormatting.YELLOW)), false);
            return 0;
        }

        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("=== Zones (" + zones.size() + ") ===").withStyle(ChatFormatting.GOLD)), false);

        for (ZoneDefinition zone : zones) {
            boolean hasAreas = registry.hasLinkedAreas(zone.zoneId());
            MutableComponent line = Component.literal(" - ")
                .append(Objects.requireNonNull(Component.literal(zone.zoneId()).withStyle(ChatFormatting.WHITE)))
                .append(Objects.requireNonNull(Component.literal(" [" + zone.displayName() + "]").withStyle(ChatFormatting.GRAY)));

            if (hasAreas) {
                int areaCount = registry.getAreasForZone(zone.zoneId()).size();
                line.append(Objects.requireNonNull(Component.literal(" (" + areaCount + " areas)").withStyle(ChatFormatting.GREEN)));
            }

            line.withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/devmod area zone info " + zone.zoneId()))
                .withHoverEvent(new HoverEvent(Objects.requireNonNull(HoverEvent.Action.SHOW_TEXT),
                    Objects.requireNonNull(Component.literal("Click for details")))));

            source.sendSuccess(() -> line, false);
        }

        return zones.size();
    }

    private static int showZoneInfo(CommandContext<CommandSourceStack> ctx, String zoneId) {
        CommandSourceStack source = ctx.getSource();
        ZoneRegistry zoneRegistry = ZoneRegistry.get(Objects.requireNonNull(source.getServer()));
        AreaRegistry areaRegistry = AreaRegistry.get(Objects.requireNonNull(source.getServer()));

        Optional<ZoneDefinition> zoneOpt = zoneRegistry.getZoneById(Objects.requireNonNull(zoneId.trim().toLowerCase(Locale.ROOT)));
        if (zoneOpt.isEmpty()) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Zone not found: " + zoneId)));
            return 0;
        }

        ZoneDefinition zone = zoneOpt.get();
        source.sendSuccess(() -> Objects.requireNonNull(
            Component.literal("=== Zone: " + zone.zoneId() + " ===").withStyle(ChatFormatting.GOLD)), false);
        source.sendSuccess(() -> Objects.requireNonNull(Component.literal("Display Name: ").withStyle(ChatFormatting.GRAY)
            .append(Objects.requireNonNull(Component.literal(zone.displayName()).withStyle(ChatFormatting.WHITE)))), false);
        source.sendSuccess(() -> Objects.requireNonNull(Component.literal("Priority: ").withStyle(ChatFormatting.GRAY)
            .append(Objects.requireNonNull(Component.literal(Objects.requireNonNull(String.valueOf(zone.priority()))).withStyle(ChatFormatting.WHITE)))), false);

        if (zone.hasTeleport()) {
            source.sendSuccess(() -> Objects.requireNonNull(Component.literal("Teleport: ").withStyle(ChatFormatting.GRAY)
                .append(Objects.requireNonNull(Component.literal("Yes").withStyle(ChatFormatting.GREEN)))), false);
        }

        // Show linked areas
        java.util.Set<UUID> linkedAreaIds = zoneRegistry.getAreasForZone(zone.zoneId());
        if (linkedAreaIds.isEmpty()) {
            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("Linked Areas: None").withStyle(ChatFormatting.GRAY)), false);
        } else {
            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("Linked Areas:").withStyle(ChatFormatting.GRAY)), false);
            for (UUID areaId : linkedAreaIds) {
                Optional<AreaDefinition> areaOpt = areaRegistry.getArea(Objects.requireNonNull(areaId));
                String areaName = areaOpt.map(AreaDefinition::name).orElse(areaId.toString().substring(0, 8));
                source.sendSuccess(() -> Objects.requireNonNull(
                    Component.literal(" - " + areaName).withStyle(ChatFormatting.WHITE)), false);
            }
        }

        return 1;
    }

    private static int linkAreaToZone(CommandContext<CommandSourceStack> ctx, String areaName, String zoneId) {
        CommandSourceStack source = ctx.getSource();
        AreaRegistry areaRegistry = AreaRegistry.get(Objects.requireNonNull(source.getServer()));
        ZoneRegistry zoneRegistry = ZoneRegistry.get(Objects.requireNonNull(source.getServer()));

        Optional<AreaDefinition> areaOpt = findAreaByName(areaRegistry, areaName);
        if (areaOpt.isEmpty()) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Area not found: " + areaName)));
            return 0;
        }

        String normalizedZoneId = Objects.requireNonNull(zoneId.trim().toLowerCase(Locale.ROOT));
        if (!zoneRegistry.hasZoneId(normalizedZoneId)) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Zone not found: " + zoneId)));
            return 0;
        }

        AreaDefinition area = areaOpt.get();

        // Update area with linked zone
        AreaDefinition updated = area.withLinkedZone(normalizedZoneId);
        boolean success = areaRegistry.updateArea(Objects.requireNonNull(area.id()), updated, area.revision());

        if (success) {
            // Also update zone registry's reverse mapping
            zoneRegistry.linkAreaToZone(Objects.requireNonNull(area.id()), Objects.requireNonNull(normalizedZoneId));

            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("Area '" + area.name() + "' linked to zone '" + normalizedZoneId + "'")
                    .withStyle(ChatFormatting.GREEN)), true);
            DevMod.LOGGER.info("Area {} linked to zone {} via CLI", area.name(), normalizedZoneId);
            return 1;
        } else {
            source.sendFailure(Objects.requireNonNull(Component.literal("Failed to update area (revision mismatch).")));
            return 0;
        }
    }

    private static int unlinkAreaFromZone(CommandContext<CommandSourceStack> ctx, String areaName) {
        CommandSourceStack source = ctx.getSource();
        AreaRegistry areaRegistry = AreaRegistry.get(Objects.requireNonNull(source.getServer()));
        ZoneRegistry zoneRegistry = ZoneRegistry.get(Objects.requireNonNull(source.getServer()));

        Optional<AreaDefinition> areaOpt = findAreaByName(areaRegistry, areaName);
        if (areaOpt.isEmpty()) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Area not found: " + areaName)));
            return 0;
        }

        AreaDefinition area = areaOpt.get();
        String currentZoneId = area.linkedZoneId();

        if (currentZoneId == null || currentZoneId.isEmpty()) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Area is not linked to any zone.")));
            return 0;
        }

        // Update area to remove linked zone
        AreaDefinition updated = area.withLinkedZone(null);
        boolean success = areaRegistry.updateArea(Objects.requireNonNull(area.id()), updated, area.revision());

        if (success) {
            // Also update zone registry's reverse mapping
            zoneRegistry.unlinkAreaFromZone(Objects.requireNonNull(area.id()), currentZoneId);

            source.sendSuccess(() -> Objects.requireNonNull(
                Component.literal("Area '" + area.name() + "' unlinked from zone '" + currentZoneId + "'")
                    .withStyle(ChatFormatting.YELLOW)), true);
            DevMod.LOGGER.info("Area {} unlinked from zone {} via CLI", area.name(), currentZoneId);
            return 1;
        } else {
            source.sendFailure(Objects.requireNonNull(Component.literal("Failed to update area (revision mismatch).")));
            return 0;
        }
    }

    private static CompletableFuture<Suggestions> suggestZoneIds(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        ZoneRegistry registry = ZoneRegistry.get(Objects.requireNonNull(ctx.getSource().getServer()));
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);

        for (ZoneDefinition zone : registry.getAllZones()) {
            String zoneId = zone.zoneId();
            if (zoneId.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(zoneId);
            }
        }

        return builder.buildFuture();
    }

    // ========================================================================
    // Delete Command
    // ========================================================================

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> deleteCommand() {
        return Commands.literal("delete")
            .then(Commands.argument("name", Objects.requireNonNull(StringArgumentType.string()))
                .suggests(AreaCommands::suggestAreaNames)
                .then(Commands.argument("confirm", Objects.requireNonNull(BoolArgumentType.bool()))
                    .executes(ctx -> deleteArea(ctx,
                        StringArgumentType.getString(ctx, "name"),
                        BoolArgumentType.getBool(ctx, "confirm")))));
    }

    private static int deleteArea(CommandContext<CommandSourceStack> ctx, String name, boolean confirm) {
        CommandSourceStack source = ctx.getSource();

        if (!confirm) {
            source.sendFailure(Objects.requireNonNull(Component.literal("You must confirm deletion with 'true'. This action cannot be undone!")));
            return 0;
        }

        AreaRegistry registry = AreaRegistry.get(Objects.requireNonNull(source.getServer()));

        Optional<AreaDefinition> areaOpt = findAreaByName(registry, name);
        if (areaOpt.isEmpty()) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Area not found: " + name)));
            return 0;
        }

        AreaDefinition area = areaOpt.get();
        UUID mainHubId = registry.getMainHubId();

        if (area.id().equals(mainHubId)) {
            source.sendFailure(Objects.requireNonNull(Component.literal("Cannot delete the main hub area.")));
            return 0;
        }

        // Cancel any active builds
        AreaBuildTaskManager.INSTANCE.cancelBuild(Objects.requireNonNull(area.id()));
        AreaBuildTaskManager.INSTANCE.removeFromQueue(Objects.requireNonNull(area.id()));

        registry.deleteArea(Objects.requireNonNull(area.id()));
        source.sendSuccess(() -> Objects.requireNonNull(Component.literal("Area deleted: " + name).withStyle(ChatFormatting.RED)), true);
        DevMod.LOGGER.info("Area deleted via command: {} ({})", name, area.id());

        return 1;
    }

    // ========================================================================
    // Suggestions
    // ========================================================================

    private static CompletableFuture<Suggestions> suggestAreaNames(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        AreaRegistry registry = AreaRegistry.get(Objects.requireNonNull(ctx.getSource().getServer()));
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);

        for (AreaDefinition area : registry.getAllAreas()) {
            String areaName = area.name();
            if (areaName.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                // Quote names with spaces
                if (areaName.contains(" ")) {
                    builder.suggest("\"" + areaName + "\"");
                } else {
                    builder.suggest(areaName);
                }
            }
        }

        return builder.buildFuture();
    }

    // ========================================================================
    // Utilities
    // ========================================================================

    private static Optional<AreaDefinition> findAreaByName(AreaRegistry registry, String name) {
        // Try exact match first
        for (AreaDefinition area : registry.getAllAreas()) {
            if (area.name().equalsIgnoreCase(name)) {
                return Optional.of(area);
            }
        }

        // Try UUID
        try {
            UUID uuid = UUID.fromString(name);
            return registry.getArea(Objects.requireNonNull(uuid));
        } catch (IllegalArgumentException e) {
            // Not a valid UUID
        }

        return Optional.empty();
    }
}
