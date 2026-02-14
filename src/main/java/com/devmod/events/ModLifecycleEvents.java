package com.devmod.events;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.storage.LevelResource;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import com.devmod.area.builder.BiomeRegistry;
import com.devmod.config.TesterModality;
import com.devmod.endurance.EnduranceQuestManager;
import com.devmod.endurance.config.GlobalMobConfigStorage;
import com.devmod.mob.MobRequirementsRegistry;
import com.devmod.notification.PartyNotificationBridge;
import com.devmod.notification.persistence.NotificationHistoryRepository;
import com.devmod.notification.persistence.NotificationPreferencesRepository;
import com.devmod.runtime.DynamicDimensionManager;
import com.devmod.telemetry.duckdb.DuckDBBootstrap;
import com.devmod.testing.stats.HazardTypeRegistry;
import com.devmod.util.ConfigPaths;
import com.devmod.util.DamageTypeConfig;
import com.devmod.util.MixinLogFilter;

import static com.devmod.DevMod.MODID;

@EventBusSubscriber(modid = MODID)
public class ModLifecycleEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void modifyEntityAttributes(EntityAttributeModificationEvent event) {
        LOGGER.info("Starting entity scan for ENTITY_INTERACTION_RANGE...");
        LOGGER.info("Number of available entity types: {}", event.getTypes().size());
        int successCount = 0;
        int failCount = 0;
        var interactionRange = Objects.requireNonNull(Attributes.ENTITY_INTERACTION_RANGE);

        // Scan ALL entity types that have attributes
        for (EntityType<? extends LivingEntity> typeRaw : event.getTypes()) {
            EntityType<? extends LivingEntity> type = Objects.requireNonNull(typeRaw);
            // Try to add the ENTITY_INTERACTION_RANGE attribute
            // If already present, getAttributeValue is not null
            if (!event.has(type, interactionRange)) {
                try {
                    event.add(type, interactionRange, 0.0);
                    successCount++;

                    // Log only the first 5 successes for debug
                    if (successCount <= 5) {
                        LOGGER.info("Added ENTITY_INTERACTION_RANGE to: {}", EntityType.getKey(type));
                    }
                } catch (Exception e) {
                    // Attribute already present or error, skip
                    failCount++;
                    LOGGER.debug("Unable to add attribute to {}: {}", EntityType.getKey(type), e.getMessage());
                }
            } else {
                failCount++;
            }
        }

        LOGGER.info("Scan complete. Added ENTITY_INTERACTION_RANGE: {} | Already present (Skipped): {}", successCount, failCount);
    }

    /**
     * Called when the server is starting (both integrated and dedicated).
     * Initializes server-side systems like EnduranceQuestManager.
     */
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[DevMod] Server starting, initializing systems...");
        boolean testerModulesEnabled = TesterModality.isEnabled();

        // Load damage type config (creates default file if missing)
        try {
            DamageTypeConfig.INSTANCE.load();
            LOGGER.info("[DevMod] DamageTypeConfig loaded successfully");
        } catch (Exception e) {
            LOGGER.error("[DevMod] Failed to load DamageTypeConfig", e);
        }

        // Initialize HazardTypeRegistry for environmental damage classification
        try {
            HazardTypeRegistry.INSTANCE.initialize(ConfigPaths.getConfigDir());
            LOGGER.info("[DevMod] HazardTypeRegistry initialized successfully");
        } catch (Exception e) {
            LOGGER.error("[DevMod] Failed to initialize HazardTypeRegistry", e);
        }

        // Initialize BiomeRegistry with all biomes (vanilla + modded)
        try {
            BiomeRegistry.initialize(Objects.requireNonNull(event.getServer().registryAccess()));
            LOGGER.info("[DevMod] BiomeRegistry initialized with {} biomes from {} categories",
                BiomeRegistry.getTotalBiomeCount(), BiomeRegistry.getCategoryCount());
        } catch (Exception e) {
            LOGGER.error("[DevMod] Failed to initialize BiomeRegistry", e);
        }

        // Core gameplay systems: Endurance Quest, QuestEventBus, ComboSystem, etc.
        // These are part of the core gameplay loop and must be available to all players.

        // Initialize EnduranceQuestManager
        try {
            EnduranceQuestManager.INSTANCE.initialize(ConfigPaths.getConfigDir());
            // Enable instance dimensions for quest isolation
            EnduranceQuestManager.INSTANCE.setUseInstanceDimensions(true);
            LOGGER.info("[DevMod] EnduranceQuestManager initialized successfully with instance dimensions enabled");
        } catch (Exception e) {
            LOGGER.error("[DevMod] Failed to initialize EnduranceQuestManager", e);
        }

        // Register QuestEventBus listeners for decoupled subsystems
        // NOTE: ComboSystem now uses ComboSystemFacade (initialized in DevMod.java)
        try {
            var eventBus = com.devmod.endurance.lifecycle.QuestEventBus.INSTANCE;
            // Critical systems (priority 1000)
            eventBus.register(com.devmod.endurance.CombatTracker.INSTANCE);
            // Core gameplay systems (priority 500-900)
            eventBus.register(com.devmod.endurance.MomentumTracker.INSTANCE);
            eventBus.register(com.devmod.endurance.TensionSystem.INSTANCE);
            eventBus.register(com.devmod.endurance.MutatorSystem.INSTANCE);
            eventBus.register(com.devmod.endurance.PerkSystem.INSTANCE);
            eventBus.register(com.devmod.endurance.bargain.DevilsBargainManager.INSTANCE);
            eventBus.register(com.devmod.endurance.hazard.ArenaHazardSystem.INSTANCE);
            eventBus.register(com.devmod.endurance.perk.PerkSynergyWeb.INSTANCE);
            eventBus.register(com.devmod.endurance.nutrition.NutritionBridgeSystem.INSTANCE);
            eventBus.register(com.devmod.endurance.ComebackSystem.INSTANCE);
            eventBus.register(com.devmod.endurance.combat.ExecutionCleanupListener.INSTANCE);
            // Feature systems (priority 50)
            eventBus.register(com.devmod.endurance.season.SeasonPassXPListener.INSTANCE);
            // Post-processing (negative priority)
            eventBus.register(com.devmod.endurance.lifecycle.PartyStatsCoordinator.INSTANCE);
            LOGGER.info("[DevMod] QuestEventBus initialized with {} listeners: {}",
                eventBus.getListenerCount(), eventBus.getListenerNames());

            // Register SeasonPassXPListener as combo event listener
            if (com.devmod.endurance.combat.ComboSystemFacade.isInitialized()) {
                com.devmod.endurance.combat.ComboSystemFacade.get()
                    .addListener(com.devmod.endurance.season.SeasonPassXPListener.INSTANCE);
                LOGGER.info("[DevMod] SeasonPassXPListener registered with ComboSystemFacade");
            }

            // Register SeasonPassXPListener as ability event listener (perfect dodges)
            com.devmod.abilities.api.AbilityEventDispatcher.INSTANCE
                .addListener(com.devmod.endurance.season.SeasonPassXPListener.INSTANCE);
            LOGGER.info("[DevMod] SeasonPassXPListener registered with AbilityEventDispatcher");
        } catch (Exception e) {
            LOGGER.error("[DevMod] Failed to initialize QuestEventBus listeners", e);
        }

        // Register CombatEnduranceBridge real implementation
        // This decouples the combat module from direct endurance imports.
        try {
            com.devmod.endurance.combat.CombatEnduranceBridgeImpl.register();
            LOGGER.info("[DevMod] CombatEnduranceBridge registered successfully");
        } catch (Exception e) {
            LOGGER.error("[DevMod] Failed to register CombatEnduranceBridge", e);
        }

        // Load global mob configuration for Endurance mode
        // Clear cache first to handle crash recovery (stale cache from previous session)
        GlobalMobConfigStorage.clearCache();
        try {
            var globalMobConfig = GlobalMobConfigStorage.load();
            if (globalMobConfig.isPresent()) {
                LOGGER.info("[DevMod] GlobalMobConfigStorage loaded with custom mob settings");
            } else {
                LOGGER.info("[DevMod] GlobalMobConfigStorage using default settings");
            }
        } catch (Exception e) {
            LOGGER.error("[DevMod] Failed to load GlobalMobConfigStorage", e);
        }

        // Register quest definitions and load persisted progress
        try {
            if (com.devmod.quest.QuestRegistry.INSTANCE.getQuestIds().isEmpty()) {
                com.devmod.quest.QuestRegistry.INSTANCE.registerExampleQuests();
            }
            com.devmod.quest.QuestProgressSavedData savedData =
                com.devmod.quest.QuestProgressSavedData.get(event.getServer());
            savedData.loadIntoRegistry();
            LOGGER.info("[DevMod] Quest system initialized with {} quest definitions",
                com.devmod.quest.QuestRegistry.INSTANCE.getQuestIds().size());
        } catch (Exception e) {
            LOGGER.error("[DevMod] Failed to initialize quest system", e);
        }

        // Initialize ConfigHandlerRegistry (handles all config handlers including weapon stats)
        try {
            var serverConfigDir = event.getServer()
                .getWorldPath(Objects.requireNonNull(LevelResource.ROOT))
                .resolve("serverconfig");
            com.devmod.config.handler.ConfigHandlerRegistry.registerAll();
            com.devmod.config.handler.ConfigHandlerRegistry.initializeAll(serverConfigDir);
            LOGGER.info("[DevMod] ConfigHandlerRegistry initialized successfully");
        } catch (Exception e) {
            LOGGER.error("[DevMod] Failed to initialize ConfigHandlerRegistry", e);
        }

        // NOTE: WeaponTypeDetector.reloadWeaponLists() is called client-side only
        // in ClientModEvents.onPlayerLogin() to avoid dedicated server crashes

        // Core gameplay systems: challenges, leaderboards, mob requirements
        // These are part of the core gameplay loop and must be available to all players.

        // Initialize DailyChallengeManager for daily challenge rotation
        try {
            var serverDataDir = event.getServer()
                .getWorldPath(java.util.Objects.requireNonNull(LevelResource.ROOT));
            com.devmod.endurance.challenges.DailyChallengeManager.INSTANCE.initialize(serverDataDir);
            LOGGER.info("[DevMod] DailyChallengeManager initialized successfully");
        } catch (Exception e) {
            LOGGER.error("[DevMod] Failed to initialize DailyChallengeManager", e);
        }

        // Initialize WeeklyChallengeManager for weekly challenge rotation
        try {
            var serverDataDir = event.getServer()
                .getWorldPath(java.util.Objects.requireNonNull(LevelResource.ROOT));
            com.devmod.endurance.challenges.WeeklyChallengeManager.INSTANCE.initialize(serverDataDir);
            LOGGER.info("[DevMod] WeeklyChallengeManager initialized successfully");
        } catch (Exception e) {
            LOGGER.error("[DevMod] Failed to initialize WeeklyChallengeManager", e);
        }

        // Initialize MobRequirementsRegistry for optimal mob conditions
        try {
            MobRequirementsRegistry.INSTANCE.initialize(ConfigPaths.getGameDir());
            // Pre-cache requirements with server access for better biome detection
            MobRequirementsRegistry.INSTANCE.preCacheAll(event.getServer());
            LOGGER.info("[DevMod] MobRequirementsRegistry initialized successfully");
        } catch (Exception e) {
            LOGGER.error("[DevMod] Failed to initialize MobRequirementsRegistry", e);
        }

        // Initialize LeaderboardSystem for global rankings
        try {
            var serverDataDir = event.getServer()
                .getWorldPath(java.util.Objects.requireNonNull(LevelResource.ROOT));
            com.devmod.endurance.LeaderboardSystem.INSTANCE.initialize(serverDataDir);
            LOGGER.info("[DevMod] LeaderboardSystem initialized successfully");
        } catch (Exception e) {
            LOGGER.error("[DevMod] Failed to initialize LeaderboardSystem", e);
        }

        if (testerModulesEnabled) {
            // Initialize MailboxConfig persistence (tester-only: DuckDB-dependent)
            try {
                var serverConfigDir = event.getServer()
                    .getWorldPath(java.util.Objects.requireNonNull(LevelResource.ROOT))
                    .resolve("serverconfig")
                    .resolve("devmod");
                com.devmod.mailbox.MailboxConfig.INSTANCE.initialize(serverConfigDir);
                LOGGER.info("[DevMod] MailboxConfig initialized successfully");
            } catch (Exception e) {
                LOGGER.error("[DevMod] Failed to initialize MailboxConfig", e);
            }

            // Initialize DuckDB-dependent services (Mailbox, Notifications)
            DataInitializer.initializeDuckDBServices(event);
        } else {
            LOGGER.info("[DevMod] TesterModality disabled: skipping tester-only server services (Mailbox, DuckDB)");
        }
    }

    /*
     * Called when the server is stopping.
     * Cleans up server-side systems.
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("[DevMod] Server stopping, cleaning up...");
        boolean testerModulesEnabled = TesterModality.isEnabled();

        // Save quest progress for all online players
        try {
            var server = event.getServer();
            com.devmod.quest.QuestProgressSavedData savedData =
                com.devmod.quest.QuestProgressSavedData.get(server);
            savedData.saveFromRegistry();
            LOGGER.info("[DevMod] Quest progress saved for all players");
        } catch (Exception e) {
            LOGGER.error("[DevMod] Failed to save quest progress", e);
        }

        // Log mixin filter summary
        MixinLogFilter.logSummary();

        // Core gameplay shutdown (always runs)
        try {
            EnduranceQuestManager.INSTANCE.shutdown();
            LOGGER.info("[DevMod] EnduranceQuestManager shutdown complete");
        } catch (Exception e) {
            LOGGER.error("[DevMod] Error during EnduranceQuestManager shutdown", e);
        }

        // Clear global mob config cache to prevent stale data on server restart
        GlobalMobConfigStorage.clearCache();

        // Save leaderboard data
        try {
            com.devmod.endurance.LeaderboardSystem.INSTANCE.saveAll();
            LOGGER.info("[DevMod] LeaderboardSystem saved successfully");
        } catch (Exception e) {
            LOGGER.error("[DevMod] Error saving LeaderboardSystem", e);
        }

        // Cleanup Area build tasks
        try {
            com.devmod.area.AreaModule.cleanup();
            LOGGER.info("[DevMod] AreaModule cleanup complete");
        } catch (Exception e) {
            LOGGER.error("[DevMod] Error during AreaModule cleanup", e);
        }

        if (testerModulesEnabled) {
            // Save MailboxConfig before shutdown (tester-only: DuckDB-dependent)
            try {
                com.devmod.mailbox.MailboxConfig.INSTANCE.save();
                LOGGER.info("[DevMod] MailboxConfig saved successfully");
            } catch (Exception e) {
                LOGGER.error("[DevMod] Error saving MailboxConfig", e);
            }

            // Shutdown DuckDB-dependent services
            DataInitializer.shutdownDuckDBServices();
        }
    }

    /*
     * Called when a level/dimension is unloaded.
     * Notifies DynamicDimensionManager to invalidate any proxy generators
     * that were using the unloaded dimension as their source.
     */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            DynamicDimensionManager.INSTANCE.onDimensionUnload(serverLevel.dimension());
        }
    }

    static void observeFuture(CompletableFuture<?> future, String context) {
        CompletableFuture<?> observed = future.exceptionally(throwable -> {
            LOGGER.warn("[DevMod] Async operation failed during {}: {}", context, throwable.getMessage());
            return null;
        });
        if (observed.isCancelled()) {
            LOGGER.debug("[DevMod] Async operation cancelled during {}", context);
        }
    }
}
