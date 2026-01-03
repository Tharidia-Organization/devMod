package com.devmod.arena.command;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import com.devmod.actions.ActionCommandInvoker;
import com.devmod.actions.ActionIds;
import com.devmod.arena.ArenaDebugState;
import com.devmod.arena.alert.AlertRouter;
import com.devmod.arena.alert.AlertRouterRegistry;
import com.devmod.arena.autosmoke.AutosmokeRunner;
import com.devmod.arena.autosmoke.AutosmokeScheduler;
import com.devmod.arena.builder.ArenaBuilder;
import com.devmod.arena.builder.AsyncArenaBuilder;
import com.devmod.arena.builder.TemplateArenaBuilder;
import com.devmod.arena.gate.InstanceOnlyGate;
import com.devmod.arena.override.ForceTemplateCapability;
import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.registry.ArenaTemplateRegistry;
import com.devmod.arena.registry.TemplateRegistryBootstrap;
import com.devmod.arena.registry.TemplateValidator;
import com.devmod.arena.registry.ValidationResult;
import com.devmod.arena.security.ArenaCommandAudit;
import com.devmod.arena.security.ArenaCommandPermissions;
import com.devmod.arena.security.ArenaCommandPermissions.CommandCategory;
import com.devmod.arena.telemetry.ArenaTelemetry;

@SuppressWarnings("null") // Minecraft API null annotations are overly strict
public class ArenaCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArenaCommands.class);

    private final ArenaTemplateRegistry registry;
    private final ArenaCommandPermissions permissions;
    private final ArenaCommandAudit audit;
    private final AutosmokeRunner autosmokeRunner;
    private final AutosmokeScheduler autosmokeScheduler;
    private final Path templatesDirectory;
    private final TemplateArenaBuilder arenaBuilder;
    private final Function<ServerLevel, TemplateArenaBuilder> builderFactory;
    @Nullable
    private final Function<ServerLevel, AsyncArenaBuilder> asyncBuilderFactory;
    @Nullable
    private final Supplier<com.devmod.arena.config.ArenaTemplateConfig.ConfigSnapshot> configSnapshotSupplier;
    private final ForceTemplateCapability forceTemplateCapability;
    private final TemplateValidator templateValidator;
    private final TemplateRegistryBootstrap bootstrap;
    private final Collection<CompletableFuture<?>> pendingTasks = new ConcurrentLinkedQueue<>();

    public ArenaCommands(
            ArenaTemplateRegistry registry,
            AutosmokeRunner autosmokeRunner,
            AutosmokeScheduler autosmokeScheduler,
            Path templatesDirectory,
            TemplateArenaBuilder arenaBuilder) {
        this(registry, autosmokeRunner, autosmokeScheduler, templatesDirectory, arenaBuilder,
            (Supplier<com.devmod.arena.config.ArenaTemplateConfig.ConfigSnapshot>) null, null, null);
    }

    public ArenaCommands(
            ArenaTemplateRegistry registry,
            AutosmokeRunner autosmokeRunner,
            AutosmokeScheduler autosmokeScheduler,
            Path templatesDirectory,
            TemplateArenaBuilder arenaBuilder,
            com.devmod.arena.config.ArenaTemplateConfig.ConfigSnapshot configSnapshot) {
        this(registry, autosmokeRunner, autosmokeScheduler, templatesDirectory, arenaBuilder,
            configSnapshot != null ? () -> configSnapshot : null, null, null);
    }

    public ArenaCommands(
            ArenaTemplateRegistry registry,
            AutosmokeRunner autosmokeRunner,
            AutosmokeScheduler autosmokeScheduler,
            Path templatesDirectory,
            TemplateArenaBuilder arenaBuilder,
            com.devmod.arena.config.ArenaTemplateConfig.ConfigSnapshot configSnapshot,
            ForceTemplateCapability forceTemplateCapability) {
        this(registry, autosmokeRunner, autosmokeScheduler, templatesDirectory, arenaBuilder,
            configSnapshot != null ? () -> configSnapshot : null, forceTemplateCapability, null);
    }

    public ArenaCommands(
            ArenaTemplateRegistry registry,
            AutosmokeRunner autosmokeRunner,
            AutosmokeScheduler autosmokeScheduler,
            Path templatesDirectory,
            TemplateArenaBuilder arenaBuilder,
            @Nullable Supplier<com.devmod.arena.config.ArenaTemplateConfig.ConfigSnapshot> configSnapshotSupplier,
            ForceTemplateCapability forceTemplateCapability,
            TemplateRegistryBootstrap bootstrap) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.permissions = ArenaCommandPermissions.getInstance();
        this.audit = ArenaCommandAudit.getInstance();
        this.autosmokeRunner = autosmokeRunner;
        this.autosmokeScheduler = autosmokeScheduler;
        this.templatesDirectory = templatesDirectory;
        this.arenaBuilder = Objects.requireNonNull(arenaBuilder, "arenaBuilder");
        this.builderFactory = level -> this.arenaBuilder;
        this.asyncBuilderFactory = null;
        this.configSnapshotSupplier = configSnapshotSupplier;
        this.forceTemplateCapability = forceTemplateCapability;
        this.templateValidator = new TemplateValidator();
        this.bootstrap = bootstrap;
        if (this.autosmokeScheduler != null && configSnapshotSupplier != null && configSnapshotSupplier.get() != null) {
            AlertRouter router = AlertRouterRegistry.get();
            if (router != null) {
                this.autosmokeScheduler.setAlertRouter(router);
            }
        }
    }

    public ArenaCommands(
            ArenaTemplateRegistry registry,
            AutosmokeRunner autosmokeRunner,
            AutosmokeScheduler autosmokeScheduler,
            Path templatesDirectory,
            Function<ServerLevel, TemplateArenaBuilder> builderFactory,
            @Nullable Function<ServerLevel, AsyncArenaBuilder> asyncBuilderFactory,
            @Nullable Supplier<com.devmod.arena.config.ArenaTemplateConfig.ConfigSnapshot> configSnapshotSupplier,
            ForceTemplateCapability forceTemplateCapability,
            TemplateRegistryBootstrap bootstrap) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.permissions = ArenaCommandPermissions.getInstance();
        this.audit = ArenaCommandAudit.getInstance();
        this.autosmokeRunner = autosmokeRunner;
        this.autosmokeScheduler = autosmokeScheduler;
        this.templatesDirectory = templatesDirectory;
        this.arenaBuilder = null;
        this.builderFactory = Objects.requireNonNull(builderFactory, "builderFactory");
        this.asyncBuilderFactory = asyncBuilderFactory;
        this.configSnapshotSupplier = configSnapshotSupplier;
        this.forceTemplateCapability = forceTemplateCapability;
        this.templateValidator = new TemplateValidator();
        this.bootstrap = bootstrap;
        if (this.autosmokeScheduler != null && configSnapshotSupplier != null && configSnapshotSupplier.get() != null) {
            AlertRouter router = AlertRouterRegistry.get();
            if (router != null) {
                this.autosmokeScheduler.setAlertRouter(router);
            }
        }
    }

    /**
     * Registers all arena commands.
     */
    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        registerBridgeHandlers();
        dispatcher.register(
            Commands.literal("arena")
                // /arena create <template>
                .then(Commands.literal("create")
                    .requires(src -> permissions.hasPermission(src, CommandCategory.CREATE))
                    .then(Commands.argument("template", StringArgumentType.word())
                        .suggests(this::suggestTemplates)
                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.ARENA_CREATE, ctx))))

                // /arena template ...
                .then(Commands.literal("template")
                    // /arena template list
                    .then(Commands.literal("list")
                        .requires(src -> permissions.hasPermission(src, CommandCategory.LIST))
                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.ARENA_TEMPLATE_LIST, ctx)))

                    // /arena template info <id>
                    .then(Commands.literal("info")
                        .requires(src -> permissions.hasPermission(src, CommandCategory.INFO))
                        .then(Commands.argument("id", StringArgumentType.word())
                            .suggests(this::suggestTemplates)
                            .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.ARENA_TEMPLATE_INFO, ctx))))

                    // /arena template reload
                    .then(Commands.literal("reload")
                        .requires(src -> permissions.hasPermission(src, CommandCategory.TEMPLATE_MANAGE))
                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.ARENA_TEMPLATE_RELOAD, ctx)))

                    // /arena template status
                    .then(Commands.literal("status")
                        .requires(src -> permissions.hasPermission(src, CommandCategory.INFO))
                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.ARENA_TEMPLATE_STATUS, ctx))))

                // /arena autosmoke ...
                .then(Commands.literal("autosmoke")
                    .requires(src -> permissions.hasPermission(src, CommandCategory.AUTOSMOKE_RUN))

                    // /arena autosmoke run
                    .then(Commands.literal("run")
                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.ARENA_AUTOSMOKE_RUN, ctx)))

                    // /arena autosmoke status
                    .then(Commands.literal("status")
                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.ARENA_AUTOSMOKE_STATUS, ctx)))

                    // /arena autosmoke schedule
                    .then(Commands.literal("schedule")
                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.ARENA_AUTOSMOKE_SCHEDULE_STATUS, ctx))))

                // /arena status
                .then(Commands.literal("status")
                    .requires(src -> permissions.hasPermission(src, CommandCategory.STATUS))
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.ARENA_STATUS, ctx)))

                // /arena validate <id> - DD29 dry-run validation
                .then(Commands.literal("validate")
                    .requires(src -> permissions.hasPermission(src, CommandCategory.INFO))
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(this::suggestTemplates)
                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.ARENA_VALIDATE, ctx))))

                // /arena force <id> [minutes] - DD29 force template session
                .then(Commands.literal("force")
                    .requires(src -> permissions.hasPermission(src, CommandCategory.FORCE_TEMPLATE))
                    // /arena force clear
                    .then(Commands.literal("clear")
                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.ARENA_FORCE_CLEAR, ctx)))
                    // /arena force status
                    .then(Commands.literal("status")
                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.ARENA_FORCE_STATUS, ctx)))
                    // /arena force <id> [minutes]
                    .then(Commands.argument("template", StringArgumentType.word())
                        .suggests(this::suggestTemplates)
                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.ARENA_FORCE, ctx))
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 240))
                            .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.ARENA_FORCE, ctx)))))

                // /arena metrics <id> - Show template build metrics
                .then(Commands.literal("metrics")
                    .requires(src -> permissions.hasPermission(src, CommandCategory.INFO))
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(this::suggestTemplates)
                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.ARENA_METRICS, ctx))))

                // /arena hud - DD30 HUD toggle commands
                .then(Commands.literal("hud")
                    .requires(src -> permissions.hasPermission(src, CommandCategory.INFO))
                    // /arena hud toggle
                    .then(Commands.literal("toggle")
                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.ARENA_HUD_TOGGLE, ctx)))
                    // /arena hud on
                    .then(Commands.literal("on")
                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.ARENA_HUD_ON, ctx)))
                    // /arena hud off
                    .then(Commands.literal("off")
                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.ARENA_HUD_OFF, ctx)))
                    // /arena hud status
                    .then(Commands.literal("status")
                        .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.ARENA_HUD_STATUS, ctx))))

                // /arena help
                .then(Commands.literal("help")
                    .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.ARENA_HELP, ctx)))

                // Default: show help
                .executes(ctx -> ActionCommandInvoker.invoke(ActionIds.ARENA_HELP, ctx))
        );

        LOGGER.info("Arena commands registered");
    }

    private void registerBridgeHandlers() {
        ArenaActionBridge.register(ActionIds.ARENA_HELP, this::showHelp);
        ArenaActionBridge.register(ActionIds.ARENA_CREATE, this::createArena);
        ArenaActionBridge.register(ActionIds.ARENA_TEMPLATE_LIST, this::listTemplates);
        ArenaActionBridge.register(ActionIds.ARENA_TEMPLATE_INFO, this::templateInfo);
        ArenaActionBridge.register(ActionIds.ARENA_TEMPLATE_RELOAD, this::reloadTemplates);
        ArenaActionBridge.register(ActionIds.ARENA_TEMPLATE_STATUS, this::templateStatus);
        ArenaActionBridge.register(ActionIds.ARENA_AUTOSMOKE_RUN, this::runAutosmoke);
        ArenaActionBridge.register(ActionIds.ARENA_AUTOSMOKE_STATUS, this::autosmokeStatus);
        ArenaActionBridge.register(ActionIds.ARENA_AUTOSMOKE_SCHEDULE_STATUS, this::autosmokeScheduleStatus);
        ArenaActionBridge.register(ActionIds.ARENA_STATUS, this::arenaStatus);
        ArenaActionBridge.register(ActionIds.ARENA_VALIDATE, this::validateTemplate);
        ArenaActionBridge.register(ActionIds.ARENA_FORCE, ctx -> forceTemplate(ctx, resolveForceMinutes(ctx)));
        ArenaActionBridge.register(ActionIds.ARENA_FORCE_CLEAR, this::clearForceTemplate);
        ArenaActionBridge.register(ActionIds.ARENA_FORCE_STATUS, this::forceTemplateStatus);
        ArenaActionBridge.register(ActionIds.ARENA_METRICS, this::templateMetrics);
        ArenaActionBridge.register(ActionIds.ARENA_HUD_TOGGLE, this::toggleHud);
        ArenaActionBridge.register(ActionIds.ARENA_HUD_ON, ctx -> setHud(ctx, true));
        ArenaActionBridge.register(ActionIds.ARENA_HUD_OFF, ctx -> setHud(ctx, false));
        ArenaActionBridge.register(ActionIds.ARENA_HUD_STATUS, this::hudStatus);
    }

    private int resolveForceMinutes(CommandContext<CommandSourceStack> ctx) {
        int minutes = 30;
        try {
            minutes = IntegerArgumentType.getInteger(ctx, "minutes");
        } catch (IllegalArgumentException ignored) {
            // Optional argument not provided.
        }
        return minutes;
    }

    // ========== Command Handlers ==========

    private int showHelp(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();

        src.sendSuccess(() -> Component.literal("§e=== Arena Commands ==="), false);
        src.sendSuccess(() -> Component.literal("§7/arena create <template> §f- Create arena"), false);
        src.sendSuccess(() -> Component.literal("§7/arena template list §f- List templates"), false);
        src.sendSuccess(() -> Component.literal("§7/arena template info <id> §f- Template details"), false);
        src.sendSuccess(() -> Component.literal("§7/arena template reload §f- Reload templates"), false);
        src.sendSuccess(() -> Component.literal("§7/arena autosmoke run §f- Run smoke tests"), false);
        src.sendSuccess(() -> Component.literal("§7/arena autosmoke status §f- Autosmoke status"), false);
        src.sendSuccess(() -> Component.literal("§7/arena status §f- Arena system status"), false);
        src.sendSuccess(() -> Component.literal("§7/arena validate <id> §f- Validate template (dry-run)"), false);
        src.sendSuccess(() -> Component.literal("§7/arena force <id> [mins] §f- Force template session"), false);
        src.sendSuccess(() -> Component.literal("§7/arena force clear §f- Clear forced template"), false);
        src.sendSuccess(() -> Component.literal("§7/arena metrics <id> §f- Template build metrics"), false);
        src.sendSuccess(() -> Component.literal("§7/arena hud toggle §f- Toggle debug HUD"), false);
        src.sendSuccess(() -> Component.literal("§7/arena hud status §f- Show HUD status"), false);

        return 1;
    }

    private int createArena(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String templateId = StringArgumentType.getString(ctx, "template");

        // Audit log
        logCommand(src, "create", templateId);

        Optional<ArenaTemplate> templateOpt = registry.get(templateId);
        if (templateOpt.isEmpty()) {
            src.sendFailure(Component.literal("§cTemplate not found: " + templateId));
            return 0;
        }

        ArenaTemplate template = templateOpt.get();

        // Determine origin position (player position or world spawn)
        int originX;
        int originY;
        int originZ;

        InstanceOnlyGate gate = resolveInstanceGate();
        ResourceKey<Level> dimensionKey = resolveDimensionKey(src);
        String dimensionLabel = dimensionKey.location().toString();
        if (gate != null) {
            String gateCaller = getClass().getName();
            var gateResult = gate.checkDimensionKey(dimensionKey, gateCaller);
            if (gateResult == InstanceOnlyGate.Result.BLOCKED) {
                src.sendFailure(Component.literal("§cInstance-only mode: cannot create arenas in " + dimensionLabel));
                return 0;
            } else if (gateResult == InstanceOnlyGate.Result.ALLOWED_DEBUG_ONLY) {
                src.sendSuccess(() -> Component.literal("§eDebug-only override: creating in " + dimensionLabel), false);
            }
        }

        var level = src.getLevel();
        if (src.getEntity() instanceof ServerPlayer player) {
            originX = player.blockPosition().getX();
            originY = player.blockPosition().getY();
            originZ = player.blockPosition().getZ();
        } else {
            // Console: use world spawn
            var spawnPos = level.getSharedSpawnPos();
            originX = spawnPos.getX();
            originY = spawnPos.getY();
            originZ = spawnPos.getZ();
        }

        src.sendSuccess(() -> Component.literal(
            String.format("§7Creating arena from template '%s' v%d at (%d, %d, %d)...",
                templateId, template.version(), originX, originY, originZ)
        ), false);

        ArenaTemplate.BuildSettings buildSettings = template.buildSettings();
        boolean asyncPreferred = buildSettings != null
            && buildSettings.buildPriority() == ArenaTemplate.BuildSettings.Priority.ASYNC;

        if (asyncPreferred) {
            if (asyncBuilderFactory == null) {
                src.sendFailure(Component.literal("§cAsync arena builder not available"));
                return 0;
            }
            AsyncArenaBuilder asyncBuilder = asyncBuilderFactory.apply(level);
            if (asyncBuilder == null) {
                src.sendFailure(Component.literal("§cAsync arena builder not available"));
                return 0;
            }
            UUID arenaId = UUID.randomUUID();
            src.sendSuccess(() -> Component.literal(
                String.format("§7Queued ASYNC arena build '%s' v%d (arenaId: %s)",
                    templateId, template.version(), arenaId)
            ), false);

            CompletableFuture<AsyncArenaBuilder.AsyncBuildResult> trackedFuture = asyncBuilder.submitBuildAsync(
                arenaId, template, originX, originY, originZ);
            var server = src.getServer();
            trackedFuture = trackedFuture.whenComplete((asyncResult, throwable) -> {
                Runnable notifier = () -> {
                    if (throwable != null) {
                        String message = throwable.getMessage() != null ? throwable.getMessage() : "Unknown error";
                        if (throwable instanceof AsyncArenaBuilder.BuildException be
                            && be.getResult() != null
                            && be.getResult().errorMessage() != null) {
                            message = be.getResult().errorMessage();
                        }
                        src.sendFailure(Component.literal("§cAsync arena build failed: " + message));
                        LOGGER.error("Async arena build failed for template '{}': {}", templateId, message);
                        return;
                    }
                    if (asyncResult == null || !asyncResult.success()) {
                        String message = asyncResult != null && asyncResult.errorMessage() != null
                            ? asyncResult.errorMessage() : "Unknown error";
                        src.sendFailure(Component.literal("§cAsync arena build failed: " + message));
                        LOGGER.error("Async arena build failed for template '{}': {}", templateId, message);
                        return;
                    }

                    int sizeX = Objects.requireNonNullElse(template.sizeX(), template.size());
                    int sizeZ = Objects.requireNonNullElse(template.sizeZ(), template.size());
                    int spawnSlotCount = template.spawnSlots() != null ? template.spawnSlots().size() : 0;

                    src.sendSuccess(() -> Component.literal(
                        String.format("§aAsync arena created successfully! ID: %s", asyncResult.arenaId())
                    ), true);
                    src.sendSuccess(() -> Component.literal(
                        String.format("§7Size: %dx%d, Blocks placed: %d, Duration: %dms",
                            sizeX, sizeZ, asyncResult.blocksPlaced(), asyncResult.durationMs())
                    ), false);
                    src.sendSuccess(() -> Component.literal(
                        String.format("§7Spawn slots: %d", spawnSlotCount)
                    ), false);

                    LOGGER.info("Async arena '{}' created by {} at ({},{},{}) - {} blocks in {}ms",
                        asyncResult.arenaId(), src.getTextName(), originX, originY, originZ,
                        asyncResult.blocksPlaced(), asyncResult.durationMs());
                };
                if (server != null) {
                    server.execute(notifier);
                } else {
                    notifier.run();
                }
            });
            trackPendingTask(trackedFuture);
            return 1;
        }

        // Build the arena using TemplateArenaBuilder (sync)
        TemplateArenaBuilder builder = builderFactory.apply(level);
        if (builder == null) {
            src.sendFailure(Component.literal("§cArena builder not available"));
            return 0;
        }
        ArenaBuilder.BuildResult result = builder.build(template, originX, originY, originZ);

        if (!result.success()) {
            src.sendFailure(Component.literal("§cArena creation failed: " + result.errorMessage()));

            if (result.rollbackResult() != null) {
                var rollback = result.rollbackResult();
                src.sendFailure(Component.literal(
                    String.format("§7Rollback: %d blocks reverted in %dms",
                        rollback.blocksReverted(), rollback.durationMs())
                ));
            }

            LOGGER.error("Arena creation failed for template '{}': {}", templateId, result.errorMessage());
            return 0;
        }

        int sizeX = Objects.requireNonNullElse(template.sizeX(), template.size());
        int sizeZ = Objects.requireNonNullElse(template.sizeZ(), template.size());
        int spawnSlotCount = template.spawnSlots() != null ? template.spawnSlots().size() : 0;

        src.sendSuccess(() -> Component.literal(
            String.format("§aArena created successfully! ID: %s", result.arenaId())
        ), true);
        src.sendSuccess(() -> Component.literal(
            String.format("§7Size: %dx%d, Blocks placed: %d, Duration: %dms",
                sizeX, sizeZ, result.blockCount(), result.durationMs())
        ), false);
        src.sendSuccess(() -> Component.literal(
            String.format("§7Spawn slots: %d", spawnSlotCount)
        ), false);

        LOGGER.info("Arena '{}' created by {} at ({},{},{}) - {} blocks in {}ms",
            result.arenaId(), src.getTextName(), originX, originY, originZ,
            result.blockCount(), result.durationMs());

        return 1;
    }

    private int listTemplates(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();

        logCommand(src, "template list", null);

        Collection<ArenaTemplate> templates = registry.all();

        if (templates.isEmpty()) {
            src.sendSuccess(() -> Component.literal("§7No templates loaded"), false);
            return 1;
        }

        src.sendSuccess(() -> Component.literal(
            String.format("§e=== Templates (%d) ===", templates.size())
        ), false);

        for (ArenaTemplate t : templates) {
            String status = t.deprecated() ? "§c[DEPRECATED]" : "§a[ACTIVE]";
            String tags = t.tags() != null && !t.tags().isEmpty()
                ? " §8[" + String.join(", ", t.tags()) + "]"
                : "";

            final String line = String.format("%s §f%s §7v%d%s", status, t.id(), t.version(), tags);
            src.sendSuccess(() -> Component.literal(line), false);
        }

        return 1;
    }

    private int templateInfo(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String templateId = StringArgumentType.getString(ctx, "id");

        logCommand(src, "template info", templateId);

        Optional<ArenaTemplate> templateOpt = registry.get(templateId);
        if (templateOpt.isEmpty()) {
            src.sendFailure(Component.literal("§cTemplate not found: " + templateId));
            return 0;
        }

        ArenaTemplate t = templateOpt.get();

        src.sendSuccess(() -> Component.literal("§e=== Template: " + t.id() + " ==="), false);
        src.sendSuccess(() -> Component.literal("§7Version: §f" + t.version()), false);
        src.sendSuccess(() -> Component.literal("§7Schema: §f" + t.schemaVersion()), false);

        if (t.extendsTemplate() != null) {
            src.sendSuccess(() -> Component.literal("§7Extends: §f" + t.extendsTemplate()), false);
        }

        int infoSizeX = Objects.requireNonNullElse(t.sizeX(), t.size());
        int infoSizeZ = Objects.requireNonNullElse(t.sizeZ(), t.size());
        src.sendSuccess(() -> Component.literal(
            String.format("§7Size: §f%d x %d", infoSizeX, infoSizeZ)
        ), false);

        if (t.floor() != null) {
            src.sendSuccess(() -> Component.literal(
                String.format("§7Floor: §f%s at Y=%d", t.floor().material(), t.floor().y())
            ), false);
        }

        int spawnCount = t.spawnSlots() != null ? t.spawnSlots().size() : 0;
        src.sendSuccess(() -> Component.literal("§7Spawn slots: §f" + spawnCount), false);

        int hazardCount = t.hazards() != null ? t.hazards().size() : 0;
        src.sendSuccess(() -> Component.literal("§7Hazards: §f" + hazardCount), false);

        if (t.tags() != null && !t.tags().isEmpty()) {
            src.sendSuccess(() -> Component.literal(
                "§7Tags: §f" + String.join(", ", t.tags())
            ), false);
        }

        if (t.deprecated()) {
            src.sendSuccess(() -> Component.literal("§c⚠ DEPRECATED"), false);
            if (t.replacementVersion() != null) {
                src.sendSuccess(() -> Component.literal(
                    "§7Replacement: §f" + t.replacementVersion()
                ), false);
            }
        }

        return 1;
    }

    private int reloadTemplates(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();

        logCommand(src, "template reload", null);

        // Guard: prevent concurrent reloads
        if (bootstrap != null && bootstrap.isReloadInProgress()) {
            src.sendFailure(Component.literal("§cReload already in progress. Please wait..."));
            return 0;
        }

        src.sendSuccess(() -> Component.literal("§7Reloading templates..."), false);

        try {
            ArenaTemplateRegistry.ReloadResult result;
            if (bootstrap != null) {
                result = bootstrap.reloadWithConfig();
            } else {
                Path directory = resolveTemplatesDirectory();
                if (configSnapshotSupplier != null) {
                    registry.applyConfigSnapshot(configSnapshotSupplier.get());
                }
                result = registry.reloadFromDirectoryAtomic(directory);
            }

            if (result.success()) {
                src.sendSuccess(() -> Component.literal(
                    String.format("§aReloaded %d templates successfully", result.loadedCount())
                ), false);
            } else {
                src.sendFailure(Component.literal("§cReload failed with errors:"));
                for (String error : result.errors()) {
                    src.sendFailure(Component.literal("§c  - " + error));
                }
            }

            return result.success() ? 1 : 0;

        } catch (Exception e) {
            LOGGER.error("Template reload failed", e);
            src.sendFailure(Component.literal("§cReload failed: " + e.getMessage()));
            return 0;
        }
    }

    private int templateStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();

        logCommand(src, "template status", null);

        src.sendSuccess(() -> Component.literal("§e=== Template System Status ==="), false);

        // Registry info
        src.sendSuccess(() -> Component.literal(
            String.format("§7Templates loaded: §f%d", registry.size())
        ), false);
        src.sendSuccess(() -> Component.literal(
            String.format("§7Registry generation: §f%d", registry.getGeneration())
        ), false);

        // Bootstrap status
        if (bootstrap != null) {
            // Watcher status
            boolean watcherActive = bootstrap.isWatcherActive();
            String watcherStatus = watcherActive ? "§aActive" : "§7Inactive";
            src.sendSuccess(() -> Component.literal("§7Hot-reload watcher: " + watcherStatus), false);

            // Reload status
            boolean reloadInProgress = bootstrap.isReloadInProgress();
            if (reloadInProgress) {
                src.sendSuccess(() -> Component.literal("§e⟳ Reload in progress..."), false);
            }

            // Last load result
            var lastResult = bootstrap.getLastLoadResult();
            if (lastResult != null) {
                String resultStatus = lastResult.success() ? "§aSuccess" : "§cFailed";
                src.sendSuccess(() -> Component.literal(
                    String.format("§7Last load: %s §7(%d templates, %d errors)",
                        resultStatus, lastResult.templates().size(), lastResult.errors().size())
                ), false);

                // Show errors if any
                if (!lastResult.errors().isEmpty()) {
                    src.sendSuccess(() -> Component.literal("§cErrors:"), false);
                    for (String error : lastResult.errors()) {
                        src.sendSuccess(() -> Component.literal("§c  • " + error), false);
                    }
                }
            } else {
                src.sendSuccess(() -> Component.literal("§7Last load: §8Not initialized"), false);
            }

            // Directory info
            src.sendSuccess(() -> Component.literal(
                "§7Directory: §f" + bootstrap.templateDirectory()
            ), false);
            src.sendSuccess(() -> Component.literal(
                "§7Validation mode: §f" + bootstrap.validationMode()
            ), false);
        } else {
            src.sendSuccess(() -> Component.literal("§7Bootstrap: §cNot configured"), false);
        }

        return 1;
    }

    @Nullable
    private InstanceOnlyGate resolveInstanceGate() {
        if (configSnapshotSupplier == null) {
            return null;
        }
        com.devmod.arena.config.ArenaTemplateConfig.ConfigSnapshot snapshot = configSnapshotSupplier.get();
        if (snapshot == null) {
            return null;
        }
        return new InstanceOnlyGate(snapshot, new ArenaTelemetry());
    }

    protected ResourceKey<Level> resolveDimensionKey(CommandSourceStack source) {
        return source.getLevel().dimension();
    }

    private Path resolveTemplatesDirectory() {
        if (configSnapshotSupplier == null) {
            return templatesDirectory;
        }
        com.devmod.arena.config.ArenaTemplateConfig.ConfigSnapshot snapshot = configSnapshotSupplier.get();
        if (snapshot != null && snapshot.templateDirectory() != null) {
            return snapshot.templateDirectory();
        }
        return templatesDirectory;
    }

    private int runAutosmoke(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();

        logCommand(src, "autosmoke run", null);

        if (autosmokeRunner == null) {
            src.sendFailure(Component.literal("§cAutosmoke runner not configured"));
            return 0;
        }

        if (autosmokeRunner.isRunning()) {
            src.sendFailure(Component.literal("§cAutosmoke already running"));
            return 0;
        }

        src.sendSuccess(() -> Component.literal("§7Starting autosmoke tests..."), false);

        // Run async
        CompletableFuture<Void> reportFuture = autosmokeRunner.runAllAsync().thenAccept(report -> {
            if (report == null) {
                src.sendFailure(Component.literal("§cAutosmoke blocked by guard"));
                return;
            }

            String resultColor = report.allPassed() ? "§a" : "§c";
            src.sendSuccess(() -> Component.literal(
                String.format("%s=== Autosmoke Results ===", resultColor)
            ), false);
            src.sendSuccess(() -> Component.literal(
                String.format("§7Passed: §a%d §7| Failed: §c%d §7| Duration: §f%dms",
                    report.passedCount(), report.failedCount(), report.totalDuration().toMillis())
            ), false);

            // Show failures
            for (AutosmokeRunner.TemplateTestResult r : report.results()) {
                if (!r.passed()) {
                    src.sendSuccess(() -> Component.literal(
                        String.format("§c  [FAIL] %s: %s", r.templateId(), r.errorMessage())
                    ), false);
                }
            }
        });
        trackPendingTask(reportFuture);

        return 1;
    }

    private void trackPendingTask(CompletableFuture<?> task) {
        pendingTasks.removeIf(CompletableFuture::isDone);
        pendingTasks.add(task);
    }

    private int autosmokeStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();

        logCommand(src, "autosmoke status", null);

        src.sendSuccess(() -> Component.literal("§e=== Autosmoke Status ==="), false);

        if (autosmokeRunner == null) {
            src.sendSuccess(() -> Component.literal("§7Runner: §cNot configured"), false);
            return 1;
        }

        // Runner status
        String runnerStatus = autosmokeRunner.isRunning() ? "§aRUNNING" : "§7Idle";
        src.sendSuccess(() -> Component.literal("§7Runner: " + runnerStatus), false);

        // Last report
        autosmokeRunner.getLastReport().ifPresentOrElse(
            report -> {
                String result = report.allPassed() ? "§aPASSED" : "§cFAILED";
                src.sendSuccess(() -> Component.literal(
                    String.format("§7Last run: %s (§f%d§7/§f%d§7)",
                        result, report.passedCount(), report.results().size())
                ), false);
            },
            () -> src.sendSuccess(() -> Component.literal("§7Last run: §8Never"), false)
        );

        return 1;
    }

    private int autosmokeScheduleStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();

        logCommand(src, "autosmoke schedule", null);

        src.sendSuccess(() -> Component.literal("§e=== Autosmoke Schedule ==="), false);

        if (autosmokeScheduler == null) {
            src.sendSuccess(() -> Component.literal("§7Scheduler: §cNot configured"), false);
            return 1;
        }

        String enabled = autosmokeScheduler.isEnabled() ? "§aEnabled" : "§cDisabled";
        src.sendSuccess(() -> Component.literal("§7Status: " + enabled), false);

        if (autosmokeScheduler.getNextRunTime() != null) {
            src.sendSuccess(() -> Component.literal(
                "§7Next run: §f" + autosmokeScheduler.getNextRunTime()
            ), false);
        }

        AutosmokeScheduler.RunStatus lastRun = autosmokeScheduler.getLastRunStatus();
        if (lastRun != null) {
            src.sendSuccess(() -> Component.literal(
                "§7Last: §f" + lastRun.formatSummary()
            ), false);
        }

        return 1;
    }

    private int arenaStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();

        logCommand(src, "status", null);

        src.sendSuccess(() -> Component.literal("§e=== Arena System Status ==="), false);
        src.sendSuccess(() -> Component.literal(
            String.format("§7Templates: §f%d loaded", registry.size())
        ), false);
        src.sendSuccess(() -> Component.literal(
            String.format("§7Generation: §f%d", registry.getGeneration())
        ), false);

        ArenaTemplateRegistry.RegistryStats stats = registry.getStats();
        src.sendSuccess(() -> Component.literal(
            String.format("§7Stats: §f%d loads, %d fallbacks, %d replacements",
                stats.getLoads(), stats.getFallbacks(), stats.getVersionReplacements())
        ), false);

        return 1;
    }

    // ========== Validate Command (DD29) ==========

    private int validateTemplate(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String templateId = StringArgumentType.getString(ctx, "id");

        logCommand(src, "validate", templateId);

        Optional<ArenaTemplate> templateOpt = registry.get(templateId);
        if (templateOpt.isEmpty()) {
            src.sendFailure(Component.literal("§cTemplate not found: " + templateId));
            return 0;
        }

        ArenaTemplate template = templateOpt.get();
        ValidationResult result = templateValidator.validate(template);
        ArenaBuilder.BuildValidation buildValidation = arenaBuilder.validateBuild(template);

        src.sendSuccess(() -> Component.literal("§e=== Validation: " + templateId + " ==="), false);

        if (result.valid() && buildValidation.valid()) {
            src.sendSuccess(() -> Component.literal("§a✓ Template is valid"), false);
        } else {
            src.sendSuccess(() -> Component.literal("§c✗ Template has errors"), false);
        }

        src.sendSuccess(() -> Component.literal(String.format(
            "§7Estimated blocks: §f%d §7| chunks: §f%d §7| est. time: §f%dms",
            buildValidation.blocksRequired(),
            buildValidation.chunksRequired(),
            buildValidation.estimatedMs()
        )), false);

        // Show errors
        if (!result.errors().isEmpty()) {
            src.sendSuccess(() -> Component.literal("§cErrors (" + result.errors().size() + "):"), false);
            for (String error : result.errors()) {
                src.sendSuccess(() -> Component.literal("§c  • " + error), false);
            }
        }
        if (!buildValidation.errors().isEmpty()) {
            src.sendSuccess(() -> Component.literal("§cBuild Errors (" + buildValidation.errors().size() + "):"), false);
            for (String error : buildValidation.errors()) {
                src.sendSuccess(() -> Component.literal("§c  • " + error), false);
            }
        }

        // Show warnings
        if (!result.warnings().isEmpty()) {
            src.sendSuccess(() -> Component.literal("§eWarnings (" + result.warnings().size() + "):"), false);
            for (String warning : result.warnings()) {
                src.sendSuccess(() -> Component.literal("§e  • " + warning), false);
            }
        }
        if (!buildValidation.warnings().isEmpty()) {
            src.sendSuccess(() -> Component.literal("§eBuild Warnings (" + buildValidation.warnings().size() + "):"), false);
            for (String warning : buildValidation.warnings()) {
                src.sendSuccess(() -> Component.literal("§e  • " + warning), false);
            }
        }

        if (result.valid() && result.warnings().isEmpty()
            && buildValidation.valid() && buildValidation.warnings().isEmpty()) {
            src.sendSuccess(() -> Component.literal("§7No issues found."), false);
        }

        return (result.valid() && buildValidation.valid()) ? 1 : 0;
    }

    // ========== Force Template Commands (DD29) ==========

    private int forceTemplate(CommandContext<CommandSourceStack> ctx, int minutes) {
        CommandSourceStack src = ctx.getSource();
        String templateId = StringArgumentType.getString(ctx, "template");

        logCommand(src, "force", templateId + " " + minutes + "min");

        if (forceTemplateCapability == null) {
            src.sendFailure(Component.literal("§cForce template capability not configured"));
            return 0;
        }

        // Must be a player
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("§cThis command can only be used by players"));
            return 0;
        }

        // Verify template exists
        Optional<ArenaTemplate> templateOpt = registry.get(templateId);
        if (templateOpt.isEmpty()) {
            src.sendFailure(Component.literal("§cTemplate not found: " + templateId));
            return 0;
        }

        // Create force session
        Duration duration = Duration.ofMinutes(minutes);
        Optional<ForceTemplateCapability.ForceSession> sessionOpt =
            forceTemplateCapability.createSession(player.getUUID(), templateId, duration, "CLI command");

        if (sessionOpt.isEmpty()) {
            src.sendFailure(Component.literal("§cFailed to create force session - check permissions"));
            return 0;
        }

        ForceTemplateCapability.ForceSession session = sessionOpt.get();
        src.sendSuccess(() -> Component.literal(
            String.format("§aForced template '%s' for %d minutes", templateId, minutes)
        ), false);
        src.sendSuccess(() -> Component.literal(
            String.format("§7Session ID: %s", session.sessionId().toString().substring(0, 8))
        ), false);
        src.sendSuccess(() -> Component.literal(
            "§7Use §f/arena force clear§7 to cancel"
        ), false);

        LOGGER.info("Player {} forced template '{}' for {} minutes", player.getName().getString(), templateId, minutes);

        return 1;
    }

    private int clearForceTemplate(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();

        logCommand(src, "force clear", null);

        if (forceTemplateCapability == null) {
            src.sendFailure(Component.literal("§cForce template capability not configured"));
            return 0;
        }

        // Must be a player
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("§cThis command can only be used by players"));
            return 0;
        }

        boolean revoked = forceTemplateCapability.revokeSession(player.getUUID(), "CLI command");

        if (revoked) {
            src.sendSuccess(() -> Component.literal("§aForced template cleared"), false);
            LOGGER.info("Player {} cleared forced template", player.getName().getString());
            return 1;
        } else {
            src.sendSuccess(() -> Component.literal("§7No active force session"), false);
            return 1;
        }
    }

    private int forceTemplateStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();

        logCommand(src, "force status", null);

        if (forceTemplateCapability == null) {
            src.sendFailure(Component.literal("§cForce template capability not configured"));
            return 0;
        }

        // Must be a player
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            // Console: show all active sessions
            src.sendSuccess(() -> Component.literal("§e=== Active Force Sessions ==="), false);
            var sessions = forceTemplateCapability.getActiveSessions();
            if (sessions.isEmpty()) {
                src.sendSuccess(() -> Component.literal("§7No active sessions"), false);
            } else {
                for (var entry : sessions.entrySet()) {
                    var s = entry.getValue();
                    src.sendSuccess(() -> Component.literal(
                        String.format("§f%s §7-> §f%s §7(expires in %dm)",
                            entry.getKey().toString().substring(0, 8),
                            s.templateId(),
                            s.remaining().toMinutes())
                    ), false);
                }
            }
            return 1;
        }

        // Player: show their session
        Optional<ForceTemplateCapability.ForceSession> sessionOpt =
            forceTemplateCapability.getSession(player.getUUID());

        src.sendSuccess(() -> Component.literal("§e=== Force Template Status ==="), false);

        if (sessionOpt.isEmpty()) {
            src.sendSuccess(() -> Component.literal("§7No active force session"), false);
        } else {
            ForceTemplateCapability.ForceSession session = sessionOpt.get();
            src.sendSuccess(() -> Component.literal(
                "§7Template: §f" + session.templateId()
            ), false);
            src.sendSuccess(() -> Component.literal(
                String.format("§7Expires in: §f%d minutes", session.remaining().toMinutes())
            ), false);
            src.sendSuccess(() -> Component.literal(
                "§7Session ID: §f" + session.sessionId().toString().substring(0, 8)
            ), false);
        }

        return 1;
    }

    // ========== Metrics Command ==========

    private int templateMetrics(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String templateId = StringArgumentType.getString(ctx, "id");

        logCommand(src, "metrics", templateId);

        Optional<ArenaTemplate> templateOpt = registry.get(templateId);
        if (templateOpt.isEmpty()) {
            src.sendFailure(Component.literal("§cTemplate not found: " + templateId));
            return 0;
        }

        ArenaTemplate template = templateOpt.get();

        src.sendSuccess(() -> Component.literal("§e=== Metrics: " + templateId + " ==="), false);

        // Show template metadata
        src.sendSuccess(() -> Component.literal("§7Version: §f" + template.version()), false);

        // Compute estimated metrics
        int sizeX = Objects.requireNonNullElse(template.sizeX(), template.size());
        int sizeZ = Objects.requireNonNullElse(template.sizeZ(), template.size());
        int estimatedBlocks = sizeX * sizeZ * 3; // floor + walls estimate

        src.sendSuccess(() -> Component.literal(
            String.format("§7Size: §f%d x %d", sizeX, sizeZ)
        ), false);
        src.sendSuccess(() -> Component.literal(
            String.format("§7Estimated blocks: §f~%d", estimatedBlocks)
        ), false);

        // Show limits if present
        if (template.limits() != null) {
            src.sendSuccess(() -> Component.literal(
                String.format("§7Max blocks: §f%d", template.limits().maxBlocks())
            ), false);
            src.sendSuccess(() -> Component.literal(
                String.format("§7Max build time: §f%dms", template.limits().maxBuildTimeMs())
            ), false);
        }

        // Spawn slots count
        int spawnCount = template.spawnSlots() != null ? template.spawnSlots().size() : 0;
        src.sendSuccess(() -> Component.literal("§7Spawn slots: §f" + spawnCount), false);

        // Hazards count
        int hazardCount = template.hazards() != null ? template.hazards().size() : 0;
        src.sendSuccess(() -> Component.literal("§7Hazards: §f" + hazardCount), false);

        // Registry stats for this template
        ArenaTemplateRegistry.RegistryStats stats = registry.getStats();
        src.sendSuccess(() -> Component.literal(
            String.format("§7Registry gen: §f%d §7(loads: %d)", registry.getGeneration(), stats.getLoads())
        ), false);

        return 1;
    }

    // ========== HUD Commands (DD30) ==========

    private int toggleHud(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();

        logCommand(src, "hud toggle", null);

        // Must be a player
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("§cThis command can only be used by players"));
            return 0;
        }

        // Check permission
        if (!hasHudPermission(player)) {
            src.sendFailure(Component.literal("§cYou don't have permission to use the debug HUD"));
            return 0;
        }

        boolean newState = ArenaDebugState.getInstance().toggleHud(player.getUUID());

        if (newState) {
            src.sendSuccess(() -> Component.literal("§aDebug HUD enabled"), false);
        } else {
            src.sendSuccess(() -> Component.literal("§7Debug HUD disabled"), false);
        }

        LOGGER.debug("Player {} toggled HUD to {}", player.getName().getString(), newState);
        return 1;
    }

    private int setHud(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        CommandSourceStack src = ctx.getSource();

        logCommand(src, "hud " + (enabled ? "on" : "off"), null);

        // Must be a player
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("§cThis command can only be used by players"));
            return 0;
        }

        // Check permission
        if (!hasHudPermission(player)) {
            src.sendFailure(Component.literal("§cYou don't have permission to use the debug HUD"));
            return 0;
        }

        ArenaDebugState state = ArenaDebugState.getInstance();
        if (enabled) {
            state.enableHud(player.getUUID());
            src.sendSuccess(() -> Component.literal("§aDebug HUD enabled"), false);
        } else {
            state.disableHud(player.getUUID());
            src.sendSuccess(() -> Component.literal("§7Debug HUD disabled"), false);
        }

        LOGGER.debug("Player {} set HUD to {}", player.getName().getString(), enabled);
        return 1;
    }

    private int hudStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();

        logCommand(src, "hud status", null);

        ArenaDebugState state = ArenaDebugState.getInstance();

        // Console: show global status
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendSuccess(() -> Component.literal("§e=== Debug HUD Status ==="), false);
            src.sendSuccess(() -> Component.literal(
                String.format("§7Global: %s", state.isGlobalHudEnabled() ? "§aEnabled" : "§cDisabled")
            ), false);
            src.sendSuccess(() -> Component.literal(
                String.format("§7Players with HUD: §f%d", state.getEnabledPlayerCount())
            ), false);
            return 1;
        }

        // Player: show their status
        boolean hasPermission = hasHudPermission(player);
        boolean isEnabled = state.isHudEnabled(player.getUUID());
        boolean globalEnabled = state.isGlobalHudEnabled();

        src.sendSuccess(() -> Component.literal("§e=== Debug HUD Status ==="), false);
        src.sendSuccess(() -> Component.literal(
            String.format("§7Permission: %s", hasPermission ? "§aYes" : "§cNo")
        ), false);
        src.sendSuccess(() -> Component.literal(
            String.format("§7Your HUD: %s", isEnabled ? "§aEnabled" : "§7Disabled")
        ), false);
        src.sendSuccess(() -> Component.literal(
            String.format("§7Global: %s", globalEnabled ? "§aEnabled" : "§cDisabled")
        ), false);

        if (hasPermission && isEnabled && globalEnabled) {
            src.sendSuccess(() -> Component.literal("§a✓ HUD is visible"), false);
        } else if (!hasPermission) {
            src.sendSuccess(() -> Component.literal("§c✗ Missing permission: " + ArenaDebugState.PERMISSION_VIEW_HUD), false);
        } else if (!globalEnabled) {
            src.sendSuccess(() -> Component.literal("§c✗ HUD globally disabled"), false);
        } else {
            src.sendSuccess(() -> Component.literal("§7Use §f/arena hud on§7 to enable"), false);
        }

        return 1;
    }

    /**
     * Checks if a player has the HUD permission.
     * Uses a simple check - in production this would integrate with permission plugin.
     */
    private boolean hasHudPermission(ServerPlayer player) {
        // For now, allow if player has at least op level 2 or INFO permission
        return permissions.hasPermission(player, CommandCategory.INFO);
    }

    // ========== Helpers ==========

    private CompletableFuture<Suggestions> suggestTemplates(
            CommandContext<CommandSourceStack> ctx,
            SuggestionsBuilder builder) {

        String input = builder.getRemaining().toLowerCase(Locale.ROOT);

        registry.all().stream()
            .map(ArenaTemplate::id)
            .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(input))
            .forEach(builder::suggest);

        return builder.buildFuture();
    }

    private void logCommand(CommandSourceStack src, String command, String args) {
        String fullCommand = "arena " + command + (args != null ? " " + args : "");
        if (src.getEntity() instanceof ServerPlayer player) {
            audit.logSecurityEvent("COMMAND", player.getUUID(), fullCommand);
        } else {
            audit.logSecurityEvent("COMMAND_CONSOLE", null, fullCommand);
        }
    }
}
