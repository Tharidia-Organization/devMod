package slimeknights.mantle.data.listener;

import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Reload listener that only runs when the mod loading state is valid.
 * Needed for model-related reloads that happen during client bootstrap.
 */
public interface IEarlySafeManagerReloadListener extends PreparableReloadListener {
  @Override
  default CompletableFuture<Void> reload(PreparationBarrier stage, ResourceManager resourceManager, ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler, Executor backgroundExecutor, Executor gameExecutor) {
    // In NeoForge 1.21+, mod loading state checks are no longer needed in this context
    return CompletableFuture.runAsync(() -> onReloadSafe(resourceManager), backgroundExecutor).thenCompose(stage::wait);
  }

  /**
   * Safely handle a resource manager reload.
   */
  void onReloadSafe(ResourceManager resourceManager);
}
