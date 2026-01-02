package com.devmod.arena;

import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import com.devmod.DevMod;
import com.devmod.arena.alert.AlertRouter;
import com.devmod.arena.alert.AlertRouterRegistry;
import com.devmod.arena.alert.ConsoleAlertChannel;
import com.devmod.arena.alert.DiscordAlertChannel;
import com.devmod.arena.alert.DuckDbAlertRecorder;
import com.devmod.arena.alert.LogAlertChannel;
import com.devmod.arena.alert.MailboxAlertChannel;
import com.devmod.arena.alert.TelemetryAlertChannel;
import com.devmod.arena.alert.WebhookAlertChannel;
import com.devmod.arena.autosmoke.AutosmokeReportWriter;
import com.devmod.arena.autosmoke.AutosmokeRunner;
import com.devmod.arena.autosmoke.AutosmokeScheduler;
import com.devmod.arena.builder.AsyncArenaBuildCoordinator;
import com.devmod.arena.builder.AsyncArenaBuilder;
import com.devmod.arena.builder.ChunkLoadingManager;
import com.devmod.arena.builder.TemplateArenaBuilder;
import com.devmod.arena.command.ArenaCommands;
import com.devmod.arena.config.ArenaTemplateConfig;
import com.devmod.arena.config.InstanceLimitConfig;
import com.devmod.arena.integration.BatchBlockPlacer;
import com.devmod.arena.integration.MinecraftEntitySpawner;
import com.devmod.arena.logging.DuckDbDestination;
import com.devmod.arena.logging.LogAggregationPipeline;
import com.devmod.arena.logging.NdjsonWriter;
import com.devmod.arena.override.ForceTemplateCapability;
import com.devmod.arena.registry.ArenaTemplateRegistry;
import com.devmod.arena.registry.TemplateRegistryBootstrap;
import com.devmod.arena.security.ArenaCommandPermissions;
import com.devmod.arena.telemetry.ArenaTelemetry;
import com.devmod.endurance.EnduranceQuestManager;

@EventBusSubscriber(modid = DevMod.MODID)
public final class ArenaCommandEvents {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArenaCommandEvents.class);

    private static AutosmokeRunner autosmokeRunner;
    private static AutosmokeScheduler autosmokeScheduler;
    private static AutosmokeReportWriter autosmokeReportWriter;
    private static AlertRouter alertRouter;
    private static LogAggregationPipeline telemetryPipeline;
    private static ForceTemplateCapability forceTemplateCapability;
    private static final AtomicReference<ArenaTemplateConfig.ConfigSnapshot> CONFIG_SNAPSHOT = new AtomicReference<>();
    private static final AsyncArenaBuildCoordinator ASYNC_COORDINATOR =
        new AsyncArenaBuildCoordinator(CONFIG_SNAPSHOT::get);

    private ArenaCommandEvents() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ensureTelemetryPipeline();
        ArenaTemplateRegistry registry = DevMod.getArenaTemplateRegistry();
        if (registry == null) {
            LOGGER.warn("[ArenaCommands] Registry not initialized; skipping command registration");
            return;
        }

        TemplateRegistryBootstrap bootstrap = DevMod.getArenaTemplateBootstrap();
        ArenaTemplateConfig config = ArenaTemplateConfig.load();
        ArenaTemplateConfig.ConfigSnapshot snapshot = bootstrap != null ? bootstrap.configSnapshot() : config.snapshot();
        CONFIG_SNAPSHOT.set(snapshot);

        AutosmokeRunner runner = autosmokeRunner != null ? autosmokeRunner : new AutosmokeRunner(registry);
        AutosmokeScheduler scheduler = autosmokeScheduler != null
            ? autosmokeScheduler
            : new AutosmokeScheduler(
                runner,
                AutosmokeScheduler.ScheduleConfig.fromCron(config.autosmokeSchedule()),
                resolveZone(config.autosmokeTimezone())
            );

        autosmokeRunner = runner;
        autosmokeScheduler = scheduler;
        if (forceTemplateCapability == null) {
            forceTemplateCapability = buildForceTemplateCapability();
        }
        if (forceTemplateCapability != null) {
            autosmokeRunner.setForceTemplateCapability(forceTemplateCapability);
            EnduranceQuestManager.INSTANCE.setForceTemplateCapability(forceTemplateCapability);
        }
        ensureAlertRouter();
        if (alertRouter != null) {
            autosmokeScheduler.setAlertRouter(alertRouter);
        }
        if (autosmokeReportWriter == null) {
            autosmokeReportWriter = new AutosmokeReportWriter(Path.of("run", "autosmoke-reports"),
                resolveZone(config.autosmokeTimezone()), 30);
            autosmokeRunner.addReportListener(autosmokeReportWriter::writeReport);
        }

        Path templateDir = snapshot != null ? snapshot.templateDirectory() : Path.of("config/devmod/arena_templates/");

        Function<ServerLevel, TemplateArenaBuilder> builderFactory = level -> createBuilder(level, CONFIG_SNAPSHOT.get());
        Function<ServerLevel, AsyncArenaBuilder> asyncBuilderFactory = level -> ASYNC_COORDINATOR.getOrCreate(level);

        ArenaCommands commands = new ArenaCommands(
            registry,
            runner,
            scheduler,
            templateDir,
            builderFactory,
            asyncBuilderFactory,
            CONFIG_SNAPSHOT::get,
            forceTemplateCapability,
            bootstrap
        );
        commands.register(event.getDispatcher());
        LOGGER.info("[ArenaCommands] Registered /arena commands");
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (autosmokeScheduler != null) {
            autosmokeScheduler.start();
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        if (autosmokeScheduler != null) {
            autosmokeScheduler.close();
            autosmokeScheduler = null;
        }
        if (autosmokeRunner != null) {
            autosmokeRunner.shutdown();
            autosmokeRunner = null;
        }
        ASYNC_COORDINATOR.clear();
        if (alertRouter != null) {
            alertRouter.close();
            alertRouter = null;
        }
        if (telemetryPipeline != null) {
            telemetryPipeline.close();
            telemetryPipeline = null;
            ArenaTelemetry.setGlobalHandler(null);
        }
        TemplateRegistryBootstrap bootstrap = DevMod.getArenaTemplateBootstrap();
        if (bootstrap != null) {
            bootstrap.close();
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ASYNC_COORDINATOR.onServerTick(event.getServer());
    }

    public static void onArenaConfigReload(ArenaTemplateConfig newConfig) {
        if (newConfig == null) return;
        CONFIG_SNAPSHOT.set(newConfig.snapshot());
        ASYNC_COORDINATOR.clear();
        if (autosmokeScheduler != null) {
            autosmokeScheduler.updateConfig(
                AutosmokeScheduler.ScheduleConfig.fromCron(newConfig.autosmokeSchedule())
            );
            autosmokeScheduler.updateTimezone(resolveZone(newConfig.autosmokeTimezone()));
        }
    }

    private static void ensureTelemetryPipeline() {
        if (telemetryPipeline != null) {
            return;
        }
        try {
            NdjsonWriter ndjsonWriter = new NdjsonWriter(Path.of("run"), "arena-telemetry");
            telemetryPipeline = LogAggregationPipeline.builder()
                .addDestination(new LogAggregationPipeline.NdjsonDestination(ndjsonWriter))
                .addDestination(new DuckDbDestination())
                .addConsoleDestination()
                .build();
            telemetryPipeline.start();
            ArenaTelemetry.setGlobalHandler(telemetryPipeline.asTelemetryConsumer());
        } catch (Exception e) {
            LOGGER.warn("[ArenaCommands] Failed to initialize telemetry pipeline: {}", e.getMessage());
        }
    }

    private static void ensureAlertRouter() {
        if (alertRouter != null) {
            return;
        }
        ArenaTelemetry telemetry = new ArenaTelemetry();
        AlertRouter router = new AlertRouter();
        router.registerChannel(new ConsoleAlertChannel());
        router.registerChannel(new LogAlertChannel());
        router.registerChannel(new TelemetryAlertChannel(telemetry));
        router.registerChannel(new MailboxAlertChannel());
        router.setDeliveryRecorder(new DuckDbAlertRecorder());

        String webhookUrl = System.getenv("DEVMOD_ARENA_ALERT_WEBHOOK_URL");
        String webhookAuth = System.getenv("DEVMOD_ARENA_ALERT_WEBHOOK_AUTH");
        if (webhookUrl != null && !webhookUrl.isBlank()) {
            router.registerChannel(new WebhookAlertChannel("webhook", webhookUrl, true, 5000, webhookAuth));
        }

        String discordUrl = System.getenv("DEVMOD_ARENA_ALERT_DISCORD_WEBHOOK_URL");
        if (discordUrl != null && !discordUrl.isBlank()) {
            router.registerChannel(new DiscordAlertChannel("discord", discordUrl, true, 5000, "Arena Alerts", null, null));
        }

        alertRouter = router;
        AlertRouterRegistry.set(router);
    }

    private static ForceTemplateCapability buildForceTemplateCapability() {
        ArenaTelemetry telemetry = new ArenaTelemetry();
        ForceTemplateCapability.PermissionChecker permissionChecker = new ForceTemplateCapability.PermissionChecker() {
            @Override
            public boolean hasPermission(UUID playerId, String permission) {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server == null) {
                    return false;
                }
                if (playerId == null) {
                    return false;
                }
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) {
                    return false;
                }
                return ArenaCommandPermissions.getInstance()
                    .hasPermission(player, ArenaCommandPermissions.CommandCategory.FORCE_TEMPLATE);
            }

            @Override
            public Set<String> getPlayerRoles(UUID playerId) {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server == null) {
                    return Set.of();
                }
                if (playerId == null) {
                    return Set.of();
                }
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) {
                    return Set.of();
                }
                var level = ArenaCommandPermissions.getInstance().getPlayerLevel(player);
                return Set.of(level.name().toLowerCase(Locale.ROOT));
            }
        };

        ForceTemplateCapability.CapabilityTelemetry capabilityTelemetry =
            new ForceTemplateCapability.CapabilityTelemetry() {
                @Override
                public void onSessionCreated(ForceTemplateCapability.ForceSession session) {
                    telemetry.emit("arena.force.session_created", java.util.Map.of(
                        "playerId", session.playerId().toString(),
                        "templateId", session.templateId(),
                        "expiresAt", session.expiresAt().toString(),
                        "reason", session.reason() != null ? session.reason() : ""
                    ));
                }

                @Override
                public void onSessionExpired(ForceTemplateCapability.ForceSession session) {
                    telemetry.emit("arena.force.session_expired", java.util.Map.of(
                        "playerId", session.playerId().toString(),
                        "templateId", session.templateId()
                    ));
                }

                @Override
                public void onSessionRevoked(ForceTemplateCapability.ForceSession session, String revokedBy) {
                    telemetry.emit("arena.force.session_revoked", java.util.Map.of(
                        "playerId", session.playerId().toString(),
                        "templateId", session.templateId(),
                        "revokedBy", revokedBy != null ? revokedBy : ""
                    ));
                }

                @Override
                public void onCapabilityDenied(UUID playerId, String templateId, String reason) {
                    telemetry.emit("arena.force.session_denied", java.util.Map.of(
                        "playerId", playerId.toString(),
                        "templateId", templateId != null ? templateId : "",
                        "reason", reason != null ? reason : ""
                    ));
                }
            };

        return new ForceTemplateCapability(permissionChecker, capabilityTelemetry);
    }

    private static TemplateArenaBuilder createBuilder(ServerLevel level, ArenaTemplateConfig.ConfigSnapshot snapshot) {
        Objects.requireNonNull(level, "level");

        ArenaTelemetry telemetry = new ArenaTelemetry();
        BatchBlockPlacer blockPlacer = new BatchBlockPlacer(level);
        MinecraftEntitySpawner entitySpawner = new MinecraftEntitySpawner(level);
        ChunkStatus fullStatus = Objects.requireNonNull(ChunkStatus.FULL, "ChunkStatus.FULL");

        ChunkLoadingManager chunkManager = new ChunkLoadingManager(
            (chunkX, chunkZ) -> level.getChunk(chunkX, chunkZ),
            (chunkX, chunkZ) -> level.getChunk(chunkX, chunkZ, fullStatus, false) != null,
            new ChunkLoadingManager.TicketManager() {
                @Override
                public void addTicket(int chunkX, int chunkZ) {
                    level.setChunkForced(chunkX, chunkZ, true);
                }

                @Override
                public void removeTicket(int chunkX, int chunkZ) {
                    level.setChunkForced(chunkX, chunkZ, false);
                }
            }
        );

        var instanceLimits = InstanceLimitConfig.load().toLimits();
        return new TemplateArenaBuilder(
            telemetry,
            blockPlacer,
            entitySpawner,
            chunkManager,
            null,
            instanceLimits,
            null,
            snapshot
        );
    }

    private static ZoneId resolveZone(String tz) {
        if (tz == null || tz.isBlank() || "SERVER".equalsIgnoreCase(tz)) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(tz, ZoneId.SHORT_IDS);
        } catch (Exception e) {
            LOGGER.warn("[ArenaCommands] Invalid timezone '{}', using system default", tz);
            return ZoneId.systemDefault();
        }
    }
}
