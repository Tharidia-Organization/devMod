package com.devmod.arena.builder;

import com.devmod.arena.config.ArenaTemplateConfig;
import com.devmod.arena.policy.ArenaPolicy;
import com.devmod.arena.policy.ResolvedArena;
import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.registry.InstanceSettingsValidator;
import com.devmod.arena.telemetry.ArenaTelemetry;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Template-focused builder entrypoint.
 *
 * <p>Thin wrapper around {@link ArenaBuilder} that preserves the legacy API
 * while offering policy-aware build helpers.</p>
 */
public class TemplateArenaBuilder extends ArenaBuilder {

    public TemplateArenaBuilder(
            ArenaTelemetry telemetry,
            BlockPlacer blockPlacer,
            EntitySpawner entitySpawner,
            ChunkLoadingManager chunkManager,
            @Nullable BuildHistoryStore historyStore,
            InstanceSettingsValidator.InstanceLimits instanceLimits,
            @Nullable CustomHazardHandler customHazardHandler,
            @Nullable ArenaTemplateConfig.ConfigSnapshot configSnapshot) {
        super(telemetry, blockPlacer, entitySpawner, chunkManager, historyStore, instanceLimits, customHazardHandler, configSnapshot);
    }

    public TemplateArenaBuilder(
            ArenaTelemetry telemetry,
            BlockPlacer blockPlacer,
            EntitySpawner entitySpawner,
            ChunkLoadingManager chunkManager,
            @Nullable BuildHistoryStore historyStore,
            InstanceSettingsValidator.InstanceLimits instanceLimits,
            @Nullable CustomHazardHandler customHazardHandler) {
        super(telemetry, blockPlacer, entitySpawner, chunkManager, historyStore, instanceLimits, customHazardHandler);
    }

    public TemplateArenaBuilder(
            ArenaTelemetry telemetry,
            BlockPlacer blockPlacer,
            EntitySpawner entitySpawner,
            ChunkLoadingManager chunkManager,
            @Nullable BuildHistoryStore historyStore,
            InstanceSettingsValidator.InstanceLimits instanceLimits) {
        super(telemetry, blockPlacer, entitySpawner, chunkManager, historyStore, instanceLimits);
    }

    public TemplateArenaBuilder(
            ArenaTelemetry telemetry,
            BlockPlacer blockPlacer,
            EntitySpawner entitySpawner,
            ChunkLoadingManager chunkManager,
            @Nullable BuildHistoryStore historyStore) {
        super(telemetry, blockPlacer, entitySpawner, chunkManager, historyStore);
    }

    public BuildResult build(ArenaTemplate template,
                             @Nullable ArenaPolicy policy,
                             int originX,
                             int originY,
                             int originZ) {
        Objects.requireNonNull(template, "template");
        String policyId = policy != null ? policy.id() : null;
        int policyVersion = policy != null ? policy.version() : 0;
        return build(template, policyId, policyVersion, originX, originY, originZ);
    }

    public BuildResult build(ResolvedArena resolved,
                             int originX,
                             int originY,
                             int originZ) {
        Objects.requireNonNull(resolved, "resolved");
        return build(resolved.template(), resolved.policy(), originX, originY, originZ);
    }
}
