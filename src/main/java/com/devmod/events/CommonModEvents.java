package com.devmod.events;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.DevMod;
import com.devmod.components.ArmorComponents;
import com.devmod.config.ArmorConfigManager;
import com.devmod.config.FuelConfigManager;
import com.devmod.config.WeaponConfigManager;
import com.devmod.endurance.EnduranceQuestManager;
import com.devmod.mailbox.MailboxPermissions;
import com.devmod.network.GameMechanicsSyncPayload;
import com.devmod.notification.network.NotificationPreferencesSyncPayload;
import com.devmod.notification.PartyNotificationBridge;
import com.devmod.notification.persistence.NotificationHistoryRepository;
import com.devmod.notification.persistence.NotificationPreferencesRepository;
import com.devmod.stats.ArmorStats;
import com.devmod.telemetry.duckdb.aggregation.AggregationConfig;
import com.devmod.telemetry.duckdb.aggregation.TelemetryAggregatorRegistry;
import com.devmod.testing.stats.HazardTypeRegistry;
import com.devmod.util.ConfigPaths;
import com.devmod.util.DamageTypeConfig;

import static com.devmod.DevMod.MODID;

@EventBusSubscriber(modid = MODID)
public class CommonModEvents {
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

        // Initialize EnduranceQuestManager
        try {
            EnduranceQuestManager.INSTANCE.initialize(ConfigPaths.getConfigDir());
            // Enable instance dimensions for quest isolation
            EnduranceQuestManager.INSTANCE.setUseInstanceDimensions(true);
            LOGGER.info("[DevMod] EnduranceQuestManager initialized successfully with instance dimensions enabled");
        } catch (Exception e) {
            LOGGER.error("[DevMod] Failed to initialize EnduranceQuestManager", e);
        }

        // Initialize ArmorConfigManager for custom armor stats
        try {
            var serverConfigDir = event.getServer()
                .getWorldPath(Objects.requireNonNull(LevelResource.ROOT))
                .resolve("serverconfig")
                .resolve("devmod");
            ArmorConfigManager.initialize(serverConfigDir);
            LOGGER.info("[DevMod] ArmorConfigManager initialized successfully");
        } catch (Exception e) {
            LOGGER.error("[DevMod] Failed to initialize ArmorConfigManager", e);
        }

        // Initialize WeaponConfigManager for weapon stats persistence
        try {
            var serverConfigDir = event.getServer()
                .getWorldPath(Objects.requireNonNull(LevelResource.ROOT))
                .resolve("serverconfig")
                .resolve("devmod");
            WeaponConfigManager.initialize(serverConfigDir);
            LOGGER.info("[DevMod] WeaponConfigManager initialized successfully");
        } catch (Exception e) {
            LOGGER.error("[DevMod] Failed to initialize WeaponConfigManager", e);
        }

        // Initialize FuelConfigManager for fuel stats persistence
        try {
            FuelConfigManager.initialize();
            LOGGER.info("[DevMod] FuelConfigManager initialized successfully");
        } catch (Exception e) {
            LOGGER.error("[DevMod] Failed to initialize FuelConfigManager", e);
        }

        // NOTE: WeaponTypeDetector.reloadWeaponLists() is called client-side only
        // in ClientModEvents.onPlayerLogin() to avoid dedicated server crashes

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

        // Initialize MailboxConfig persistence
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

        // Initialize LeaderboardSystem for global rankings
        try {
            var serverDataDir = event.getServer()
                .getWorldPath(java.util.Objects.requireNonNull(LevelResource.ROOT));
            com.devmod.endurance.LeaderboardSystem.INSTANCE.initialize(serverDataDir);
            LOGGER.info("[DevMod] LeaderboardSystem initialized successfully");
        } catch (Exception e) {
            LOGGER.error("[DevMod] Failed to initialize LeaderboardSystem", e);
        }

        // Initialize MailboxManager for in-game messaging system
        try {
            com.devmod.mailbox.MailboxManager.INSTANCE.initialize().join();

            // Set up callback to notify clients of new messages
            com.devmod.mailbox.MailboxManager.INSTANCE.setNewMessageCallback((recipientUuid, message) -> {
                ServerPlayer recipient = event.getServer().getPlayerList().getPlayer(java.util.Objects.requireNonNull(recipientUuid));
                if (recipient != null) {
                    // Get unread count and send notification
                    observeFuture(com.devmod.mailbox.MailboxManager.INSTANCE.getUnreadCount(recipientUuid)
                        .thenAccept(unreadCount -> {
                            com.devmod.mailbox.network.MailboxNetworkHandler.sendNotification(recipient, message, unreadCount);
                            com.devmod.notification.NotificationService.INSTANCE.notifyMailboxMessage(
                                recipientUuid, message, unreadCount);
                        }),
                        "mailbox unread count");
                }
            });

            com.devmod.mailbox.news.NewsManager.INSTANCE.setNewNewsCallback(article -> {
                var server = event.getServer();
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    try {
                        com.devmod.mailbox.network.MailboxNetworkHandler.sendNewsSync(player);
                        com.devmod.notification.NotificationService.INSTANCE.notifyNewsArticle(player.getUUID(), article);
                    } catch (Exception e) {
                        LOGGER.warn("[DevMod] Failed to notify news for {}: {}",
                            player.getName().getString(), e.getMessage());
                    }
                }
            });

            LOGGER.info("[DevMod] MailboxManager initialized successfully");
        } catch (Exception e) {
            LOGGER.error("[DevMod] Failed to initialize MailboxManager", e);
        }

        // Initialize notification persistence repositories
        try {
            java.nio.file.Path dbPath = ConfigPaths.getGameDir()
                    .resolve("devmod")
                    .resolve("notifications.duckdb");
            NotificationHistoryRepository.INSTANCE.initialize(dbPath).join();
            NotificationPreferencesRepository.INSTANCE.initialize(dbPath).join();
            LOGGER.info("[DevMod] Notification repositories initialized successfully");
        } catch (Exception e) {
            LOGGER.error("[DevMod] Failed to initialize notification repositories", e);
        }

        // Initialize Unified Notification Center
        try {
            com.devmod.notification.NotificationService.INSTANCE.initialize();
            LOGGER.info("[DevMod] NotificationService initialized successfully");
        } catch (Exception e) {
            LOGGER.error("[DevMod] Failed to initialize NotificationService", e);
        }

        // Register party notification bridge after notification service is ready
        try {
            PartyNotificationBridge.register();
        } catch (Exception e) {
            LOGGER.error("[DevMod] Failed to register PartyNotificationBridge", e);
        }
    }

    /**
     * Called when the server is stopping.
     * Cleans up server-side systems.
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("[DevMod] Server stopping, cleaning up EnduranceQuestManager...");
        try {
            EnduranceQuestManager.INSTANCE.shutdown();
            LOGGER.info("[DevMod] EnduranceQuestManager shutdown complete");
        } catch (Exception e) {
            LOGGER.error("[DevMod] Error during EnduranceQuestManager shutdown", e);
        }

        // Save leaderboard data
        try {
            com.devmod.endurance.LeaderboardSystem.INSTANCE.saveAll();
            LOGGER.info("[DevMod] LeaderboardSystem saved successfully");
        } catch (Exception e) {
            LOGGER.error("[DevMod] Error saving LeaderboardSystem", e);
        }

        // Save MailboxConfig before shutdown
        try {
            com.devmod.mailbox.MailboxConfig.INSTANCE.save();
            LOGGER.info("[DevMod] MailboxConfig saved successfully");
        } catch (Exception e) {
            LOGGER.error("[DevMod] Error saving MailboxConfig", e);
        }

        // Shutdown NotificationService
        try {
            com.devmod.notification.NotificationService.INSTANCE.shutdown();
            LOGGER.info("[DevMod] NotificationService shutdown complete");
        } catch (Exception e) {
            LOGGER.error("[DevMod] Error during NotificationService shutdown", e);
        }

        // Unregister party notification bridge
        try {
            PartyNotificationBridge.unregister();
        } catch (Exception e) {
            LOGGER.error("[DevMod] Failed to unregister PartyNotificationBridge", e);
        }

        // Shutdown notification repositories
        try {
            NotificationHistoryRepository.INSTANCE.shutdown();
            NotificationPreferencesRepository.INSTANCE.shutdown();
            LOGGER.info("[DevMod] Notification repositories shutdown complete");
        } catch (Exception e) {
            LOGGER.error("[DevMod] Error during notification repositories shutdown", e);
        }

        // Shutdown MailboxManager
        try {
            com.devmod.mailbox.MailboxManager.INSTANCE.shutdown().join();
            LOGGER.info("[DevMod] MailboxManager shutdown complete");
        } catch (Exception e) {
            LOGGER.error("[DevMod] Error during MailboxManager shutdown", e);
        }
    }

    /**
     * Sync GameMechanicsConfig to client on login.
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Sync global mechanics config
            try {
                GameMechanicsSyncPayload payload = Objects.requireNonNull(
                    GameMechanicsSyncPayload.fromGlobalConfig(), "gameMechanicsSyncPayload");
                PacketDistributor.sendToPlayer(player, payload);
                LOGGER.debug("[DevMod] Synced game mechanics config to {}", player.getName().getString());
            } catch (Exception e) {
                LOGGER.warn("[DevMod] Failed to sync game mechanics config to {}: {}",
                    player.getName().getString(), e.getMessage());
            }

            // Initialize telemetry aggregator for this player
            if (AggregationConfig.AGGREGATION_ENABLED) {
                TelemetryAggregatorRegistry.INSTANCE.onPlayerJoin(player.getUUID());
            }

            // Sync mailbox to client
            try {
                com.devmod.mailbox.network.MailboxNetworkHandler.sendMailboxSync(player);
                com.devmod.mailbox.network.MailboxNetworkHandler.sendAccessSync(player);
                LOGGER.debug("[DevMod] Synced mailbox to {}", player.getName().getString());
            } catch (Exception e) {
                LOGGER.warn("[DevMod] Failed to sync mailbox to {}: {}",
                    player.getName().getString(), e.getMessage());
            }

            // Sync news to client
            try {
                com.devmod.mailbox.network.MailboxNetworkHandler.sendNewsSync(player);
                LOGGER.debug("[DevMod] Synced news to {}", player.getName().getString());
            } catch (Exception e) {
                LOGGER.warn("[DevMod] Failed to sync news to {}: {}",
                    player.getName().getString(), e.getMessage());
            }

            // Sync tester tasks to client (if applicable)
            try {
                if (MailboxPermissions.INSTANCE.hasPermission(player, MailboxPermissions.Permission.TESTER)) {
                    com.devmod.mailbox.network.MailboxNetworkHandler.sendTaskSync(player);
                    LOGGER.debug("[DevMod] Synced tasks to {}", player.getName().getString());
                }
            } catch (Exception e) {
                LOGGER.warn("[DevMod] Failed to sync tasks to {}: {}",
                    player.getName().getString(), e.getMessage());
            }

            // Sync tickets to client
            try {
                com.devmod.mailbox.network.TicketNetworkHandler.sendTicketSync(player);
                LOGGER.debug("[DevMod] Synced tickets to {}", player.getName().getString());
            } catch (Exception e) {
                LOGGER.warn("[DevMod] Failed to sync tickets to {}: {}",
                    player.getName().getString(), e.getMessage());
            }

            // Sync notification preferences to client
            try {
                NotificationPreferencesRepository repo = NotificationPreferencesRepository.INSTANCE;
                if (repo.isInitialized()) {
                    java.util.UUID playerUuid = player.getUUID();
                    repo.loadPreferences(playerUuid).thenAccept(prefs -> {
                        if (player.isRemoved() || player.server == null) {
                            return;
                        }
                        player.server.execute(() ->
                                PacketDistributor.sendToPlayer(player, java.util.Objects.requireNonNull(NotificationPreferencesSyncPayload.from(prefs))));
                    });
                }
            } catch (Exception e) {
                LOGGER.warn("[DevMod] Failed to sync notification preferences to {}: {}",
                    player.getName().getString(), e.getMessage());
            }
        }
    }

    /**
     * Sanitize weapon stats/components when equipment changes to avoid stale modifiers and enforce clear tool rules.
     */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        ItemStack stack = event.getTo();
        if (stack == null || stack.isEmpty()) return;

        EquipmentSlot slot = event.getSlot();
        var armorComponent = ArmorComponents.armorStatsComponent();
        // Weapons (mainhand/offhand)
        if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
            boolean hasDevmodData = false;
            try {
                var comp = stack.get(Objects.requireNonNull(com.devmod.components.WeaponComponents.WEAPON_STATS.get()));
                hasDevmodData = comp != null && !comp.isEmpty();
            } catch (Exception e) {
                logComponentAccessFailure("Weapon stats component read", e);
            }
            try {
                var custom = stack.get(Objects.requireNonNull(net.minecraft.core.component.DataComponents.CUSTOM_DATA));
                hasDevmodData = hasDevmodData || (custom != null && custom.contains("WeaponModStats"));
            } catch (Exception e) {
                logComponentAccessFailure("Weapon custom data read", e);
            }
            try {
                hasDevmodData = hasDevmodData || (com.devmod.config.WeaponConfigManager.loadFromAttributeModifiers(stack) != null);
            } catch (Exception e) {
                logComponentAccessFailure("Weapon attribute modifier load", e);
            }

            if (hasDevmodData) {
                try {
                    com.devmod.stats.WeaponStats stats = com.devmod.config.WeaponConfigManager.getStats(stack);
                    // Clamp and reapply (ensures modifiers/tool clear) and log if modifiers exceeded limits
                    stats = com.devmod.config.WeaponConfigManager.clampStats(stats);
                    com.devmod.config.WeaponConfigManager.setSpecificStats(stack, stats);
                    LOGGER.debug("[DevMod] Sanitized weapon stats on equip for {}", stack.getItem());
                    // Warn if any modifier ids are non-DevMod for tracked attributes
                    try {
                        var mods = stack.getOrDefault(
                            Objects.requireNonNull(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS),
                            Objects.requireNonNull(net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY)
                        );
                        mods.modifiers().forEach(entry -> {
                            var id = entry.modifier().id();
                            if (id != null && !DevMod.MODID.equals(id.getNamespace())) {
                                LOGGER.warn("[DevMod] Non-DevMod modifier {} present on {} for attribute {}", id, stack.getItem(), entry.attribute());
                            }
                        });
                    } catch (Exception e) {
                        logComponentAccessFailure("Weapon attribute modifiers scan", e);
                    }
                } catch (Exception e) {
                    LOGGER.warn("[DevMod] Failed to sanitize weapon stats on equip: {}", e.getMessage());
                }
            }
        }

        // Armor/shields (humanoid armor slots or shield in hand)
        boolean isArmorSlot = slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
        boolean isShield = stack.getItem() instanceof net.minecraft.world.item.ShieldItem;
        if (isArmorSlot || isShield) {
            boolean hasDevmodArmor = false;
            try {
                var comp = armorComponent != null ? stack.get(armorComponent) : null;
                hasDevmodArmor = comp != null && !comp.isEmpty();
            } catch (Exception e) {
                logComponentAccessFailure("Armor stats component read", e);
            }
            try {
                var custom = stack.get(Objects.requireNonNull(net.minecraft.core.component.DataComponents.CUSTOM_DATA));
                hasDevmodArmor = hasDevmodArmor || (custom != null && custom.contains("ArmorModStats"));
            } catch (Exception e) {
                logComponentAccessFailure("Armor custom data read", e);
            }

            ArmorStats stats = null;
            if (hasDevmodArmor) {
                try {
                    stats = ArmorConfigManager.getStats(stack);
                } catch (Exception e) {
                    LOGGER.warn("[DevMod] Failed to read armor stats on equip: {}", e.getMessage());
                }
            } else if (ArmorConfigManager.hasGlobalConfig(stack.getItem())) {
                stats = ArmorConfigManager.getGlobalStats(stack.getItem());
            }

            if (stats != null) {
                try {
                    stats = ArmorConfigManager.clampStats(stats);
                    ArmorConfigManager.setSpecificStats(stack, stats);
                    LOGGER.debug("[DevMod] Sanitized armor stats on equip for {}", stack.getItem());
                    try {
                        var mods = stack.getOrDefault(
                            Objects.requireNonNull(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS),
                            Objects.requireNonNull(net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY)
                        );
                        mods.modifiers().forEach(entry -> {
                            var id = entry.modifier().id();
                            Attribute attrVal = entry.attribute().value();
                            if (id != null && !DevMod.MODID.equals(id.getNamespace())
                                && (attrVal == net.minecraft.world.entity.ai.attributes.Attributes.ARMOR.value()
                                    || attrVal == net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS.value()
                                    || attrVal == net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE.value())) {
                                LOGGER.warn("[DevMod] External modifier {} present on {} for armor attribute {}", id, stack.getItem(), attrVal);
                            }
                        });
                    } catch (Exception e) {
                        logComponentAccessFailure("Armor attribute modifiers scan", e);
                    }
                } catch (Exception e) {
                    LOGGER.warn("[DevMod] Failed to sanitize armor stats on equip: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Refresh tool component/modifiers on mining to ensure clear toggle and rules are in sync.
     */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        ItemStack stack = event.getEntity().getMainHandItem();
        if (stack == null || stack.isEmpty()) return;
        boolean hasDevmodData = false;
        try {
            var comp = stack.get(Objects.requireNonNull(com.devmod.components.WeaponComponents.WEAPON_STATS.get()));
            hasDevmodData = comp != null && !comp.isEmpty();
        } catch (Exception e) {
            logComponentAccessFailure("BreakSpeed weapon component read", e);
        }
        try {
            var custom = stack.get(Objects.requireNonNull(net.minecraft.core.component.DataComponents.CUSTOM_DATA));
            hasDevmodData = hasDevmodData || (custom != null && custom.contains("WeaponModStats"));
        } catch (Exception e) {
            logComponentAccessFailure("BreakSpeed custom data read", e);
        }
        if (!hasDevmodData) return;

        try {
            com.devmod.stats.WeaponStats stats = com.devmod.config.WeaponConfigManager.getStats(stack);
            stats = com.devmod.config.WeaponConfigManager.clampStats(stats);
            com.devmod.config.WeaponConfigManager.setSpecificStats(stack, stats);
            // If clear tool rules is set, ensure no extra speed modifiers are added here; vanilla will use default
        } catch (Exception e) {
            LOGGER.debug("[DevMod] BreakSpeed sanitize failed: {}", e.getMessage());
        }
    }

    /**
     * Enforce clear-tool toggle on drops: if clearToolRules is true, prevent custom drop overrides.
     */
    @SubscribeEvent
    public static void onBlockDrop(BlockEvent.BreakEvent event) {
        ItemStack stack = event.getPlayer() != null ? event.getPlayer().getMainHandItem() : ItemStack.EMPTY;
        if (stack == null || stack.isEmpty()) return;
        boolean hasDevmodData = false;
        try {
            var comp = stack.get(Objects.requireNonNull(com.devmod.components.WeaponComponents.WEAPON_STATS.get()));
            hasDevmodData = comp != null && !comp.isEmpty();
        } catch (Exception e) {
            logComponentAccessFailure("BlockDrop weapon component read", e);
        }
        try {
            var custom = stack.get(Objects.requireNonNull(net.minecraft.core.component.DataComponents.CUSTOM_DATA));
            hasDevmodData = hasDevmodData || (custom != null && custom.contains("WeaponModStats"));
        } catch (Exception e) {
            logComponentAccessFailure("BlockDrop custom data read", e);
        }
        if (!hasDevmodData) return;
        try {
            com.devmod.stats.WeaponStats stats = com.devmod.config.WeaponConfigManager.getStats(stack);
            if (stats.clearToolRules && event.getPlayer() != null) {
                // Remove any custom tool component; vanilla drop logic will apply
                stack.remove(Objects.requireNonNull(net.minecraft.core.component.DataComponents.TOOL));
                LOGGER.debug("[DevMod] Cleared tool component on drop due to clearToolRules");
            }
        } catch (Exception e) {
            LOGGER.debug("[DevMod] Drop enforcement failed: {}", e.getMessage());
        }
    }

    private static void observeFuture(CompletableFuture<?> future, String context) {
        CompletableFuture<?> observed = future.exceptionally(throwable -> {
            LOGGER.warn("[DevMod] Async operation failed during {}: {}", context, throwable.getMessage());
            return null;
        });
        if (observed.isCancelled()) {
            LOGGER.debug("[DevMod] Async operation cancelled during {}", context);
        }
    }

    private static void logComponentAccessFailure(String context, Exception e) {
        LOGGER.debug("[DevMod] {} failed", context, e);
    }
}
