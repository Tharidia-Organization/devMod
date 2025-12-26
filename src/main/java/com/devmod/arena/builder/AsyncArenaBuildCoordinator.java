package com.devmod.arena.builder;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import com.devmod.arena.config.ArenaTemplateConfig;
import com.devmod.arena.integration.MinecraftBlockPlacer;
import com.devmod.arena.telemetry.ArenaTelemetry;

public class AsyncArenaBuildCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncArenaBuildCoordinator.class);

    private final Map<ResourceKey<Level>, AsyncArenaBuilder> builders = new ConcurrentHashMap<>();
    @Nullable
    private final Supplier<ArenaTemplateConfig.ConfigSnapshot> configSnapshotSupplier;

    public AsyncArenaBuildCoordinator(@Nullable Supplier<ArenaTemplateConfig.ConfigSnapshot> configSnapshotSupplier) {
        this.configSnapshotSupplier = configSnapshotSupplier;
    }

    public AsyncArenaBuilder getOrCreate(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        ResourceKey<Level> key = level.dimension();
        return builders.computeIfAbsent(key, ignored -> createBuilder(level));
    }

    public void onServerTick(@Nullable MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            AsyncArenaBuilder builder = builders.get(level.dimension());
            if (builder != null) {
                builder.onTick();
            }
        }
    }

    public void clear() {
        builders.clear();
    }

    private AsyncArenaBuilder createBuilder(ServerLevel level) {
        ArenaTelemetry telemetry = new ArenaTelemetry();
        MinecraftBlockPlacer blockPlacer = new MinecraftBlockPlacer(level);
        Supplier<Double> msptSupplier = () -> {
            MinecraftServer server = Objects.requireNonNull(level.getServer(), "server");
            return server.getAverageTickTimeNanos() / 1_000_000.0;
        };

        ArenaTemplateConfig.ConfigSnapshot snapshot = configSnapshotSupplier != null ? configSnapshotSupplier.get() : null;
        LOGGER.info("Created AsyncArenaBuilder for {}", level.dimension().location());
        return new AsyncArenaBuilder(telemetry, blockPlacer, msptSupplier, snapshot);
    }
}
