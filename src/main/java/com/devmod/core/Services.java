package com.devmod.core;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.collision.registry.BodyPartRegistry;
import com.devmod.combat.ExecutionSystem;
import com.devmod.combat.signature.SoulImprintManager;
import com.devmod.config.GameplayOverridesManager;
import com.devmod.config.TesterModality;
import com.devmod.config.gamedesign.GameDesignConfigManager;
import com.devmod.debug.DebugManager;
import com.devmod.endurance.EnduranceQuestManager;
import com.devmod.endurance.EnduranceQuestRegistry;
import com.devmod.endurance.LeaderboardSystem;
import com.devmod.endurance.RewardSystem;
import com.devmod.endurance.WaveManager;
import com.devmod.endurance.hazard.ArenaHazardSystem;
import com.devmod.endurance.services.PlayerStateServicesFacade;
import com.devmod.mailbox.MailboxManager;
import com.devmod.notification.NotificationService;
import com.devmod.party.PartyManager;
import com.devmod.quest.QuestManager;
import com.devmod.quest.QuestRegistry;
import com.devmod.runtime.InstanceManager;
import com.devmod.telemetry.TelemetryService;
import com.devmod.transport.executor.TransportExecutor;
import com.devmod.util.DamageTypeConfig;

/**
 * P2 Architecture: Central service access point.
 * Provides type-safe access to core services via {@link ServiceRegistry}.
 *
 * <p>All accessors delegate to the registry. If a service was not registered
 * (e.g. {@link #endurance()} when {@link TesterModality} is disabled), the
 * accessor returns {@code null} rather than silently falling back to a
 * singleton. This makes the registry meaningful for testing and ensures
 * callers handle missing services explicitly.</p>
 *
 * <p>Benefits over direct INSTANCE access:
 * <ul>
 *   <li>Testable - services can be mocked via ServiceRegistry.override()</li>
 *   <li>Discoverable - all core services in one place</li>
 *   <li>Future-proof - easy to migrate to full DI later</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * // Instead of: PartyManager.INSTANCE.createParty(...)
 * Services.party().createParty(...)
 *
 * // For testing:
 * ServiceRegistry.override(PartyManager.class, mockPartyManager);
 * Services.party().createParty(...) // uses mock
 * </pre>
 */
public final class Services {

    private static final Logger LOGGER = LoggerFactory.getLogger(Services.class);
    private static volatile boolean initialized = false;

    private Services() {} // Utility class

    /**
     * Initialize all core services in the registry.
     * Should be called once during mod initialization.
     */
    public static void initialize() {
        if (initialized) {
            LOGGER.debug("Services already initialized, skipping");
            return;
        }

        // Register core services with lazy factories pointing to existing singletons
        ServiceRegistry.register(PartyManager.class, () -> PartyManager.INSTANCE);
        ServiceRegistry.register(WaveManager.class, () -> WaveManager.INSTANCE);
        ServiceRegistry.register(TelemetryService.class, () -> TelemetryService.INSTANCE);
        ServiceRegistry.register(NotificationService.class, () -> NotificationService.INSTANCE);
        ServiceRegistry.register(MailboxManager.class, () -> MailboxManager.INSTANCE);
        ServiceRegistry.register(InstanceManager.class, () -> InstanceManager.INSTANCE);
        ServiceRegistry.register(RewardSystem.class, () -> RewardSystem.INSTANCE);
        ServiceRegistry.register(EnduranceQuestRegistry.class, () -> EnduranceQuestRegistry.INSTANCE);
        ServiceRegistry.register(QuestManager.class, () -> QuestManager.INSTANCE);
        ServiceRegistry.register(QuestRegistry.class, () -> QuestRegistry.INSTANCE);
        ServiceRegistry.register(LeaderboardSystem.class, () -> LeaderboardSystem.INSTANCE);
        ServiceRegistry.register(DebugManager.class, () -> DebugManager.INSTANCE);
        ServiceRegistry.register(BodyPartRegistry.class, () -> BodyPartRegistry.INSTANCE);
        ServiceRegistry.register(ExecutionSystem.class, () -> ExecutionSystem.INSTANCE);
        ServiceRegistry.register(SoulImprintManager.class, () -> SoulImprintManager.INSTANCE);
        ServiceRegistry.register(GameDesignConfigManager.class, () -> GameDesignConfigManager.INSTANCE);
        ServiceRegistry.register(GameplayOverridesManager.class, () -> GameplayOverridesManager.INSTANCE);
        ServiceRegistry.register(DamageTypeConfig.class, () -> DamageTypeConfig.INSTANCE);
        ServiceRegistry.register(TransportExecutor.class, () -> TransportExecutor.INSTANCE);
        ServiceRegistry.register(ArenaHazardSystem.class, () -> ArenaHazardSystem.INSTANCE);
        ServiceRegistry.register(PlayerStateServicesFacade.class, () -> PlayerStateServicesFacade.INSTANCE);

        if (TesterModality.isEnabled()) {
            ServiceRegistry.register(EnduranceQuestManager.class, () -> EnduranceQuestManager.INSTANCE);
        }

        initialized = true;
        LOGGER.info("Initialized {} core services in registry", ServiceRegistry.size());
    }

    /**
     * Check if services have been initialized.
     */
    public static boolean isInitialized() {
        return initialized;
    }

    // === Type-safe Service Accessors ===

    /**
     * Get the party management service.
     *
     * @throws IllegalStateException if services have not been initialized
     */
    public static PartyManager party() {
        return getRequired(PartyManager.class);
    }

    /**
     * Get the wave management service.
     *
     * @throws IllegalStateException if services have not been initialized
     */
    public static WaveManager waves() {
        return getRequired(WaveManager.class);
    }

    /**
     * Get the telemetry service.
     *
     * @throws IllegalStateException if services have not been initialized
     */
    public static TelemetryService telemetry() {
        return getRequired(TelemetryService.class);
    }

    /**
     * Get the notification service.
     *
     * @throws IllegalStateException if services have not been initialized
     */
    public static NotificationService notifications() {
        return getRequired(NotificationService.class);
    }

    /**
     * Get the mailbox management service.
     *
     * @throws IllegalStateException if services have not been initialized
     */
    public static MailboxManager mailbox() {
        return getRequired(MailboxManager.class);
    }

    /**
     * Get the instance management service.
     *
     * @throws IllegalStateException if services have not been initialized
     */
    public static InstanceManager instances() {
        return getRequired(InstanceManager.class);
    }

    /**
     * Get the endurance quest management service.
     * Returns {@code null} when {@link TesterModality} is disabled,
     * since the endurance module is not registered in that case.
     *
     * @return the endurance service, or null if not registered
     */
    @Nullable
    public static EnduranceQuestManager endurance() {
        if (!initialized) {
            LOGGER.warn("Services.endurance() called before initialization");
            return null;
        }
        return ServiceRegistry.getOrNull(EnduranceQuestManager.class);
    }

    /**
     * Get the reward system service.
     *
     * @throws IllegalStateException if services have not been initialized
     */
    public static RewardSystem rewards() {
        return getRequired(RewardSystem.class);
    }

    /**
     * Get the endurance quest registry service.
     *
     * @throws IllegalStateException if services have not been initialized
     */
    public static EnduranceQuestRegistry questRegistry() {
        return getRequired(EnduranceQuestRegistry.class);
    }

    /**
     * Get the quest manager service.
     *
     * @throws IllegalStateException if services have not been initialized
     */
    public static QuestManager quests() {
        return getRequired(QuestManager.class);
    }

    /**
     * Get the quest definitions registry.
     *
     * @throws IllegalStateException if services have not been initialized
     */
    public static QuestRegistry questDefs() {
        return getRequired(QuestRegistry.class);
    }

    /**
     * Get the leaderboard system service.
     *
     * @throws IllegalStateException if services have not been initialized
     */
    public static LeaderboardSystem leaderboard() {
        return getRequired(LeaderboardSystem.class);
    }

    /**
     * Get the debug manager service.
     *
     * @throws IllegalStateException if services have not been initialized
     */
    public static DebugManager debug() {
        return getRequired(DebugManager.class);
    }

    /**
     * Get the body part registry service.
     *
     * @throws IllegalStateException if services have not been initialized
     */
    public static BodyPartRegistry bodyParts() {
        return getRequired(BodyPartRegistry.class);
    }

    /**
     * Get the execution system service.
     *
     * @throws IllegalStateException if services have not been initialized
     */
    public static ExecutionSystem execution() {
        return getRequired(ExecutionSystem.class);
    }

    /**
     * Get the soul imprint manager service.
     *
     * @throws IllegalStateException if services have not been initialized
     */
    public static SoulImprintManager soulImprints() {
        return getRequired(SoulImprintManager.class);
    }

    /**
     * Get the game design config manager service.
     *
     * @throws IllegalStateException if services have not been initialized
     */
    public static GameDesignConfigManager gameDesign() {
        return getRequired(GameDesignConfigManager.class);
    }

    /**
     * Get the gameplay overrides manager service.
     *
     * @throws IllegalStateException if services have not been initialized
     */
    public static GameplayOverridesManager gameplayOverrides() {
        return getRequired(GameplayOverridesManager.class);
    }

    /**
     * Get the damage type config service.
     *
     * @throws IllegalStateException if services have not been initialized
     */
    public static DamageTypeConfig damageTypes() {
        return getRequired(DamageTypeConfig.class);
    }

    /**
     * Get the transport executor service.
     *
     * @throws IllegalStateException if services have not been initialized
     */
    public static TransportExecutor transport() {
        return getRequired(TransportExecutor.class);
    }

    /**
     * Get the arena hazard system service.
     *
     * @throws IllegalStateException if services have not been initialized
     */
    public static ArenaHazardSystem hazards() {
        return getRequired(ArenaHazardSystem.class);
    }

    /**
     * Get the player state services facade.
     *
     * @throws IllegalStateException if services have not been initialized
     */
    public static PlayerStateServicesFacade playerState() {
        return getRequired(PlayerStateServicesFacade.class);
    }

    // === Helper Methods ===

    /**
     * Get a required service from the registry.
     * Throws if services are not initialized or the service is not registered.
     *
     * @throws IllegalStateException if not initialized or service not found
     */
    private static <T> T getRequired(Class<T> serviceClass) {
        if (!initialized) {
            throw new IllegalStateException(
                "Services not initialized. Call Services.initialize() during mod setup. "
                + "Requested: " + serviceClass.getName());
        }
        return ServiceRegistry.get(serviceClass);
    }

    /**
     * Reset services (for testing only).
     */
    public static void reset() {
        ServiceRegistry.reset();
        initialized = false;
        LOGGER.debug("Services reset");
    }
}
