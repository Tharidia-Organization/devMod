package com.devmod.events;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.DevMod;
import com.devmod.components.ArmorComponents;
import com.devmod.config.handler.impl.WeaponConfigHandler;
import com.devmod.mailbox.MailboxPermissions;
import com.devmod.network.GameMechanicsSyncPayload;
import com.devmod.notification.network.NotificationPreferencesSyncPayload;
import com.devmod.notification.persistence.NotificationPreferencesRepository;
import com.devmod.stats.ArmorStats;
import com.devmod.telemetry.TelemetryService;
import com.devmod.telemetry.duckdb.DuckDBBootstrap;
import com.devmod.telemetry.duckdb.aggregation.AggregationConfig;
import com.devmod.telemetry.duckdb.aggregation.TelemetryAggregatorRegistry;
import com.devmod.telemetry.economy.EconomyMetricsService;

import static com.devmod.DevMod.MODID;

/**
 * Per-player and per-block gameplay events.
 *
 * <p>Server lifecycle (start, stop, attribute modification, level unload) lives
 * in {@link ModLifecycleEvents}. Both classes are {@code @EventBusSubscriber}, so
 * a handler declared in both runs twice.
 */
@EventBusSubscriber(modid = MODID)
public class CommonModEvents {
	private static final Logger LOGGER = LogUtils.getLogger();

    /*
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

            // Sync DuckDB-dependent features only if available
            if (DuckDBBootstrap.isAvailable()) {
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
                        java.util.concurrent.CompletableFuture<?> future = repo.loadPreferences(playerUuid).thenAccept(prefs -> {
                            if (player.isRemoved() || player.server == null) {
                                return;
                            }
                            player.server.execute(() ->
                                    PacketDistributor.sendToPlayer(player, java.util.Objects.requireNonNull(NotificationPreferencesSyncPayload.from(prefs))));
                        }).handle((result, ex) -> {
                            if (ex != null) {
                                LOGGER.warn("[DevMod] Failed to sync notification preferences async for {}: {}",
                                    playerUuid, ex.getMessage());
                            }
                            return null;
                        });
                        if (future.isDone() && LOGGER.isTraceEnabled()) {
                            LOGGER.trace("[DevMod] Preferences synced immediately for {}", playerUuid);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("[DevMod] Failed to sync notification preferences to {}: {}",
                        player.getName().getString(), e.getMessage());
                }
            }
        }
    }

    /*
     * Sanitize weapon stats/components when equipment changes to avoid stale modifiers and enforce clear tool rules.
     */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        ItemStack stack = event.getTo();
        if (stack == null || stack.isEmpty()) return;

        EquipmentSlot slot = event.getSlot();
        if (event.getEntity() instanceof ServerPlayer player && !player.level().isClientSide()) {
            var equipRecord = EconomyMetricsService.INSTANCE.recordItemEquipped(player, stack, slot.getName());
            if (equipRecord != null) {
                TelemetryService.INSTANCE.appendEconomyLine(equipRecord.toJson());
            }
        }
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
                hasDevmodData = hasDevmodData || (WeaponConfigHandler.loadFromAttributeModifiers(stack) != null);
            } catch (Exception e) {
                logComponentAccessFailure("Weapon attribute modifier load", e);
            }

            if (hasDevmodData) {
                try {
                    com.devmod.stats.WeaponStats stats = WeaponConfigHandler.INSTANCE.getStats(stack);
                    // Clamp and reapply (ensures modifiers/tool clear) and log if modifiers exceeded limits
                    stats = WeaponConfigHandler.clampStats(stats);
                    WeaponConfigHandler.INSTANCE.setSpecificStats(stack, stats);
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
                    stats = com.devmod.config.handler.impl.ArmorConfigHandler.INSTANCE.getStats(stack);
                } catch (Exception e) {
                    LOGGER.warn("[DevMod] Failed to read armor stats on equip: {}", e.getMessage());
                }
            } else if (com.devmod.config.handler.impl.ArmorConfigHandler.hasGlobalConfig(stack.getItem())) {
                stats = com.devmod.config.handler.impl.ArmorConfigHandler.getItemGlobalStats(stack.getItem());
            }

            if (stats != null) {
                try {
                    stats = com.devmod.config.handler.impl.ArmorConfigHandler.clampStats(stats);
                    com.devmod.config.handler.impl.ArmorConfigHandler.INSTANCE.setSpecificStats(stack, stats);
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

    /*
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
            com.devmod.stats.WeaponStats stats = WeaponConfigHandler.INSTANCE.getStats(stack);
            stats = WeaponConfigHandler.clampStats(stats);
            WeaponConfigHandler.INSTANCE.setSpecificStats(stack, stats);
            // If clear tool rules is set, ensure no extra speed modifiers are added here; vanilla will use default
        } catch (Exception e) {
            LOGGER.debug("[DevMod] BreakSpeed sanitize failed: {}", e.getMessage());
        }
    }

    /*
     * Enforce clear-tool toggle on drops: if clearToolRules is true, prevent custom drop overrides.
     */
    @SubscribeEvent
    public static void onBlockDrop(BlockEvent.BreakEvent event) {
        ItemStack stack = event.getPlayer().getMainHandItem();
        if (stack.isEmpty()) return;
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
            com.devmod.stats.WeaponStats stats = WeaponConfigHandler.INSTANCE.getStats(stack);
            if (stats.isClearToolRules()) {
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
