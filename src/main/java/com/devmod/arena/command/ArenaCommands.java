package com.devmod.arena.command;

import com.devmod.arena.autosmoke.AutosmokeRunner;
import com.devmod.arena.autosmoke.AutosmokeScheduler;
import com.devmod.arena.builder.ArenaBuilder;
import com.devmod.arena.hud.ArenaDebugHud;
import com.devmod.arena.hud.ArenaDebugState;
import com.devmod.arena.override.ForceTemplateCapability;
import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.registry.ArenaTemplateRegistry;
import com.devmod.arena.registry.TemplateValidator;
import com.devmod.arena.registry.ValidationResult;
import com.devmod.arena.registry.TemplateRegistryBootstrap;
import com.devmod.arena.security.ArenaCommandAudit;
import com.devmod.arena.security.ArenaCommandPermissions;
import com.devmod.arena.security.ArenaCommandPermissions.CommandCategory;
import com.devmod.arena.gate.InstanceOnlyGate;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * DD30: Arena dev commands for template management and testing.
 *
 * <p>Commands:</p>
 * <ul>
 *   <li>/arena create &lt;template&gt; - Create arena from template</li>
 *   <li>/arena template list - List all templates</li>
 *   <li>/arena template info &lt;id&gt; - Show template details</li>
 *   <li>/arena template reload - Hot-reload templates</li>
 *   <li>/arena validate &lt;id&gt; - Validate template (dry-run)</li>
 *   <li>/arena force &lt;id&gt; [minutes] - Force template for session (DD29)</li>
 *   <li>/arena force clear - Clear forced template</li>
 *   <li>/arena metrics &lt;id&gt; - Show template build metrics</li>
 *   <li>/arena autosmoke run - Run autosmoke tests</li>
 *   <li>/arena autosmoke status - Show autosmoke status</li>
 *   <li>/arena debug - Debug commands</li>
 * </ul>
 */
@SuppressWarnings("null") // Minecraft API null annotations are overly strict
public class ArenaCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArenaCommands.class);

    private final ArenaTemplateRegistry registry;
    private final ArenaCommandPermissions permissions;
    private final ArenaCommandAudit audit;
    private final AutosmokeRunner autosmokeRunner;
    private final AutosmokeScheduler autosmokeScheduler;
    private final Path templatesDirectory;
    private final ArenaBuilder arenaBuilder;
    private final InstanceOnlyGate instanceGate;
    private final ForceTemplateCapability forceTemplateCapability;
    private final TemplateValidator templateValidator;
    private final TemplateRegistryBootstrap bootstrap;

    public ArenaCommands(
            ArenaTemplateRegistry registry,
            AutosmokeRunner autosmokeRunner,
            AutosmokeScheduler autosmokeScheduler,
            Path templatesDirectory,
            ArenaBuilder arenaBuilder) {
        this(registry, autosmokeRunner, autosmokeScheduler, templatesDirectory, arenaBuilder, null, null, null);
    }

    public ArenaCommands(
            ArenaTemplateRegistry registry,
            AutosmokeRunner autosmokeRunner,
            AutosmokeScheduler autosmokeScheduler,
            Path templatesDirectory,
            ArenaBuilder arenaBuilder,
            com.devmod.arena.config.ArenaTemplateConfig.ConfigSnapshot configSnapshot) {
        this(registry, autosmokeRunner, autosmokeScheduler, templatesDirectory, arenaBuilder, configSnapshot, null, null);
    }

    public ArenaCommands(
            ArenaTemplateRegistry registry,
            AutosmokeRunner autosmokeRunner,
            AutosmokeScheduler autosmokeScheduler,
            Path templatesDirectory,
            ArenaBuilder arenaBuilder,
            com.devmod.arena.config.ArenaTemplateConfig.ConfigSnapshot configSnapshot,
            ForceTemplateCapability forceTemplateCapability) {
        this(registry, autosmokeRunner, autosmokeScheduler, templatesDirectory, arenaBuilder, configSnapshot, forceTemplateCapability, null);
    }

    public ArenaCommands(
            ArenaTemplateRegistry registry,
            AutosmokeRunner autosmokeRunner,
            AutosmokeScheduler autosmokeScheduler,
            Path templatesDirectory,
            ArenaBuilder arenaBuilder,
            com.devmod.arena.config.ArenaTemplateConfig.ConfigSnapshot configSnapshot,
            ForceTemplateCapability forceTemplateCapability,
            TemplateRegistryBootstrap bootstrap) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.permissions = ArenaCommandPermissions.getInstance();
        this.audit = ArenaCommandAudit.getInstance();
        this.autosmokeRunner = autosmokeRunner;
        this.autosmokeScheduler = autosmokeScheduler;
        this.templatesDirectory = templatesDirectory;
        this.arenaBuilder = Objects.requireNonNull(arenaBuilder, "arenaBuilder");
        this.instanceGate = configSnapshot != null ? new InstanceOnlyGate(configSnapshot, null) : null;
        this.forceTemplateCapability = forceTemplateCapability;
        this.templateValidator = new TemplateValidator();
        this.bootstrap = bootstrap;
        if (this.autosmokeScheduler != null && configSnapshot != null) {
            // Wire alert router for autosmoke if available
            this.autosmokeScheduler.setAlertRouter(new com.devmod.arena.alert.AlertRouter());
        }
    }

    /**
     * Registers all arena commands.
     */
    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("arena")
                // /arena create <template>
                .then(Commands.literal("create")
                    .requires(src -> permissions.hasPermission(src, CommandCategory.CREATE))
                    .then(Commands.argument("template", StringArgumentType.word())
                        .suggests(this::suggestTemplates)
                        .executes(this::createArena)))

                // /arena template ...
                .then(Commands.literal("template")
                    // /arena template list
                    .then(Commands.literal("list")
                        .requires(src -> permissions.hasPermission(src, CommandCategory.LIST))
                        .executes(this::listTemplates))

                    // /arena template info <id>
                    .then(Commands.literal("info")
                        .requires(src -> permissions.hasPermission(src, CommandCategory.INFO))
                        .then(Commands.argument("id", StringArgumentType.word())
                            .suggests(this::suggestTemplates)
                            .executes(this::templateInfo)))

                    // /arena template reload
                    .then(Commands.literal("reload")
                        .requires(src -> permissions.hasPermission(src, CommandCategory.TEMPLATE_MANAGE))
                        .executes(this::reloadTemplates)))

                // /arena autosmoke ...
                .then(Commands.literal("autosmoke")
                    .requires(src -> permissions.hasPermission(src, CommandCategory.AUTOSMOKE_RUN))

                    // /arena autosmoke run
                    .then(Commands.literal("run")
                        .executes(this::runAutosmoke))

                    // /arena autosmoke status
                    .then(Commands.literal("status")
                        .executes(this::autosmokeStatus))

                    // /arena autosmoke schedule
                    .then(Commands.literal("schedule")
                        .executes(this::autosmokeScheduleStatus)))

                // /arena status
                .then(Commands.literal("status")
                    .requires(src -> permissions.hasPermission(src, CommandCategory.STATUS))
                    .executes(this::arenaStatus))

                // /arena validate <id> - DD29 dry-run validation
                .then(Commands.literal("validate")
                    .requires(src -> permissions.hasPermission(src, CommandCategory.INFO))
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(this::suggestTemplates)
                        .executes(ctx -> validateTemplate(ctx))))

                // /arena force <id> [minutes] - DD29 force template session
                .then(Commands.literal("force")
                    .requires(src -> permissions.hasPermission(src, CommandCategory.FORCE_TEMPLATE))
                    // /arena force clear
                    .then(Commands.literal("clear")
                        .executes(ctx -> clearForceTemplate(ctx)))
                    // /arena force status
                    .then(Commands.literal("status")
                        .executes(ctx -> forceTemplateStatus(ctx)))
                    // /arena force <id> [minutes]
                    .then(Commands.argument("template", StringArgumentType.word())
                        .suggests(this::suggestTemplates)
                        .executes(ctx -> forceTemplate(ctx, 30))
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 240))
                            .executes(ctx -> forceTemplate(ctx, IntegerArgumentType.getInteger(ctx, "minutes"))))))

                // /arena metrics <id> - Show template build metrics
                .then(Commands.literal("metrics")
                    .requires(src -> permissions.hasPermission(src, CommandCategory.INFO))
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests(this::suggestTemplates)
                        .executes(ctx -> templateMetrics(ctx))))

                // /arena hud - DD30 HUD toggle commands
                .then(Commands.literal("hud")
                    .requires(src -> permissions.hasPermission(src, CommandCategory.INFO))
                    // /arena hud toggle
                    .then(Commands.literal("toggle")
                        .executes(ctx -> toggleHud(ctx)))
                    // /arena hud on
                    .then(Commands.literal("on")
                        .executes(ctx -> setHud(ctx, true)))
                    // /arena hud off
                    .then(Commands.literal("off")
                        .executes(ctx -> setHud(ctx, false)))
                    // /arena hud status
                    .then(Commands.literal("status")
                        .executes(ctx -> hudStatus(ctx))))

                // /arena help
                .then(Commands.literal("help")
                    .executes(this::showHelp))

                // Default: show help
                .executes(this::showHelp)
        );

        LOGGER.info("Arena commands registered");
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
        var level = src.getLevel();

        if (instanceGate != null) {
            var gateResult = instanceGate.check(level, "/arena create");
            if (gateResult == InstanceOnlyGate.Result.BLOCKED) {
                src.sendFailure(Component.literal("§cInstance-only mode: cannot create arenas in " + level.dimension().location()));
                return 0;
            } else if (gateResult == InstanceOnlyGate.Result.ALLOWED_DEBUG_ONLY) {
                src.sendSuccess(() -> Component.literal("§eDebug-only override: creating in " + level.dimension().location()), false);
            }
        }

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

        // Build the arena using ArenaBuilder
        ArenaBuilder.BuildResult result = arenaBuilder.build(template, originX, originY, originZ);

        if (result.success()) {
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
        } else {
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

        src.sendSuccess(() -> Component.literal("§7Reloading templates..."), false);

        try {
            ArenaTemplateRegistry.ReloadResult result;
            if (bootstrap != null) {
                result = bootstrap.reloadWithConfig();
            } else {
                result = registry.reloadFromDirectoryAtomic(templatesDirectory);
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
        autosmokeRunner.runAllAsync().thenAccept(report -> {
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

        return 1;
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

        src.sendSuccess(() -> Component.literal("§e=== Validation: " + templateId + " ==="), false);

        if (result.valid()) {
            src.sendSuccess(() -> Component.literal("§a✓ Template is valid"), false);
        } else {
            src.sendSuccess(() -> Component.literal("§c✗ Template has errors"), false);
        }

        // Show errors
        if (!result.errors().isEmpty()) {
            src.sendSuccess(() -> Component.literal("§cErrors (" + result.errors().size() + "):"), false);
            for (String error : result.errors()) {
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

        if (result.valid() && result.warnings().isEmpty()) {
            src.sendSuccess(() -> Component.literal("§7No issues found."), false);
        }

        return result.valid() ? 1 : 0;
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
            src.sendSuccess(() -> Component.literal("§c✗ Missing permission: " + ArenaDebugHud.PERMISSION_VIEW_HUD), false);
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
