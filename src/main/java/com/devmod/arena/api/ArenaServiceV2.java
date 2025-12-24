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
 *   <li>prepareArenaForPartyV2 with ArenaHandle return type</li>
 *   <li>Full resolution context support</li>
 *   <li>Async operations with CompletableFuture</li>
 * </ul>
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
