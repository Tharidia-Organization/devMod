package com.devmod.arena.api;

import com.devmod.arena.policy.ResolveContext;
import com.devmod.arena.policy.ResolvedArena;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Arena Service V2 API implementing DD14: API Migration.
 *
 * <p>Key features:
 * <ul>
 *   <li>New method: prepareArenaForPartyV2 with ArenaHandle return type</li>
 *   <li>Deprecated methods marked with @Deprecated</li>
 *   <li>Runtime warnings for deprecated method usage</li>
 * </ul>
 *
 * <p>Migration path (DD14):
 * <ol>
 *   <li>Add V2 methods alongside legacy methods</li>
 *   <li>Mark legacy methods @Deprecated with forRemoval=true</li>
 *   <li>Log warnings on legacy method calls</li>
 *   <li>Remove legacy methods after migration complete</li>
 * </ol>
 */
public interface ArenaServiceV2 {

    // ========================================
    // V2 API - New Methods (DD14)
    // ========================================

    /**
     * Prepares an arena for a party using V2 API.
     * DD14: Returns ArenaHandle with full context.
     *
     * @param partyId the party identifier
     * @param context resolution context for template/policy selection
     * @return CompletableFuture with ArenaHandle
     */
    CompletableFuture<ArenaHandle> prepareArenaForPartyV2(UUID partyId, ResolveContext context);

    /**
     * Prepares an arena for a party with specific template.
     * DD14: Allows forcing a specific template.
     *
     * @param partyId the party identifier
     * @param templateId the template to use
     * @param policyId optional policy override
     * @return CompletableFuture with ArenaHandle
     */
    CompletableFuture<ArenaHandle> prepareArenaForPartyV2(
        UUID partyId,
        String templateId,
        @Nullable String policyId
    );

    /**
     * Gets an active arena by ID.
     *
     * @param arenaId the arena identifier
     * @return Optional ArenaHandle if found
     */
    Optional<ArenaHandle> getArena(UUID arenaId);

    /**
     * Gets all active arenas for a party.
     *
     * @param partyId the party identifier
     * @return list of active ArenaHandles
     */
    List<ArenaHandle> getArenasForParty(UUID partyId);

    /**
     * Releases an arena, triggering cleanup.
     *
     * @param arenaId the arena to release
     * @return CompletableFuture completing when cleanup is done
     */
    CompletableFuture<Void> releaseArena(UUID arenaId);

    /**
     * Gets the resolved arena configuration without building.
     * Useful for dry-run and preview.
     *
     * @param context resolution context
     * @return the resolved arena configuration
     */
    ResolvedArena resolveArena(ResolveContext context);

    // ========================================
    // Legacy API - Deprecated (DD14)
    // ========================================

    /**
     * @deprecated Use {@link #prepareArenaForPartyV2(UUID, ResolveContext)} instead.
     *             Will be removed in version 3.0.
     *             DD14: Legacy method returns only arena ID, not full context.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    default CompletableFuture<UUID> prepareArenaForParty(UUID partyId, String mobType) {
        logDeprecationWarning("prepareArenaForParty(UUID, String)", "prepareArenaForPartyV2");

        ResolveContext context = ResolveContext.builder(partyId)
            .partyId(partyId)
            .mobType(mobType)
            .build();

        return prepareArenaForPartyV2(partyId, context)
            .thenApply(ArenaHandle::arenaId);
    }

    /**
     * @deprecated Use {@link #prepareArenaForPartyV2(UUID, String, String)} instead.
     *             Will be removed in version 3.0.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    default CompletableFuture<UUID> prepareArenaForParty(UUID partyId, String mobType, String difficulty) {
        logDeprecationWarning("prepareArenaForParty(UUID, String, String)", "prepareArenaForPartyV2");

        ResolveContext context = ResolveContext.builder(partyId)
            .partyId(partyId)
            .mobType(mobType)
            .difficulty(difficulty)
            .build();

        return prepareArenaForPartyV2(partyId, context)
            .thenApply(ArenaHandle::arenaId);
    }

    /**
     * @deprecated Use {@link #getArena(UUID)} instead.
     *             Will be removed in version 3.0.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    default boolean isArenaActive(UUID arenaId) {
        logDeprecationWarning("isArenaActive", "getArena");
        return getArena(arenaId).isPresent();
    }

    /**
     * @deprecated Use {@link #releaseArena(UUID)} instead.
     *             Will be removed in version 3.0.
     */
    @Deprecated(since = "2.0", forRemoval = true)
    default void destroyArena(UUID arenaId) {
        logDeprecationWarning("destroyArena", "releaseArena");
        releaseArena(arenaId).join();
    }

    /**
     * Logs a deprecation warning.
     * DD14: Runtime warning for deprecated method usage.
     */
    private static void logDeprecationWarning(String oldMethod, String newMethod) {
        StackTraceElement caller = Thread.currentThread().getStackTrace()[3];
        System.err.printf(
            "[DEPRECATION WARNING] %s is deprecated, use %s instead. Called from %s.%s(%s:%d)%n",
            oldMethod,
            newMethod,
            caller.getClassName(),
            caller.getMethodName(),
            caller.getFileName(),
            caller.getLineNumber()
        );
    }

    // ========================================
    // Service Lifecycle
    // ========================================

    /**
     * Initializes the service.
     */
    void initialize();

    /**
     * Shuts down the service gracefully.
     */
    void shutdown();

    /**
     * Gets service statistics.
     */
    ServiceStats getStats();

    /**
     * Service statistics record.
     */
    record ServiceStats(
        int activeArenas,
        int totalBuildsToday,
        int failedBuildsToday,
        long avgBuildTimeMs,
        int queuedRequests
    ) {}
}
