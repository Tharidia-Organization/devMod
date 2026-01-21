package com.devmod.foundry.data.listener;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Reload listener that only runs when the mod loading state is valid.
 */
public interface FoundryEarlySafeManagerReloadListener extends PreparableReloadListener {
    @Override
    default CompletableFuture<Void> reload(
        PreparationBarrier stage,
        ResourceManager resourceManager,
        ProfilerFiller preparationsProfiler,
        ProfilerFiller reloadProfiler,
        Executor backgroundExecutor,
        Executor gameExecutor
    ) {
        return CompletableFuture.runAsync(() -> onReloadSafe(resourceManager), backgroundExecutor).thenCompose(stage::wait);
    }

    void onReloadSafe(ResourceManager resourceManager);
}
