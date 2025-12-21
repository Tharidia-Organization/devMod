package com.frenkvs.devmod.arena;

import com.devmod.arena.autosmoke.AutosmokeRunner;
import com.devmod.arena.autosmoke.AutosmokeScheduler;
import com.devmod.arena.builder.ArenaBuilder;
import com.devmod.arena.builder.AsyncArenaBuildCoordinator;
import com.devmod.arena.builder.AsyncArenaBuilder;
import com.devmod.arena.builder.ChunkLoadingManager;
import com.devmod.arena.config.ArenaTemplateConfig;
import com.devmod.arena.config.InstanceLimitConfig;
import com.devmod.arena.command.ArenaCommands;
import com.devmod.arena.integration.MinecraftBlockPlacer;
import com.devmod.arena.integration.MinecraftEntitySpawner;
import com.devmod.arena.registry.ArenaTemplateRegistry;
import com.devmod.arena.registry.TemplateRegistryBootstrap;
import com.devmod.arena.telemetry.ArenaTelemetry;
import com.frenkvs.devmod.DevMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

@EventBusSubscriber(modid = DevMod.MODID)
public final class ArenaCommandEvents {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArenaCommandEvents.class);

    private static AutosmokeRunner autosmokeRunner;
    private static AutosmokeScheduler autosmokeScheduler;
    private static final AtomicReference<ArenaTemplateConfig.ConfigSnapshot> CONFIG_SNAPSHOT = new AtomicReference<>();
    private static final AsyncArenaBuildCoordinator ASYNC_COORDINATOR =
        new AsyncArenaBuildCoordinator(CONFIG_SNAPSHOT::get);

    private ArenaCommandEvents() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
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

        Path templateDir = snapshot != null ? snapshot.templateDirectory() : Path.of("config/devmod/arena_templates/");

        Function<ServerLevel, ArenaBuilder> builderFactory = level -> createBuilder(level, CONFIG_SNAPSHOT.get());
        Function<ServerLevel, AsyncArenaBuilder> asyncBuilderFactory = level -> ASYNC_COORDINATOR.getOrCreate(level);

        ArenaCommands commands = new ArenaCommands(
            registry,
            runner,
            scheduler,
            templateDir,
            builderFactory,
            asyncBuilderFactory,
            CONFIG_SNAPSHOT::get,
            null,
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
        }
        if (autosmokeRunner != null) {
            autosmokeRunner.shutdown();
        }
        ASYNC_COORDINATOR.clear();
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

    private static ArenaBuilder createBuilder(ServerLevel level, ArenaTemplateConfig.ConfigSnapshot snapshot) {
        Objects.requireNonNull(level, "level");

        ArenaTelemetry telemetry = new ArenaTelemetry();
        MinecraftBlockPlacer blockPlacer = new MinecraftBlockPlacer(level);
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
        return new ArenaBuilder(
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
