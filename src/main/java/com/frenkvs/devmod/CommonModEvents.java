package com.frenkvs.devmod;

import static com.frenkvs.devmod.DevMod.MODID;

import com.frenkvs.devmod.endurance.EnduranceQuestManager;
import com.frenkvs.devmod.testing.stats.HazardTypeRegistry;
import com.frenkvs.devmod.util.ConfigPaths;
import com.frenkvs.devmod.util.DamageTypeConfig;
import com.frenkvs.devmod.ui.editor.WeaponTypeDetector;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.Objects;

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

        // Reload weapon detection lists (whitelist/blacklist) to pick up server config changes
        try {
            WeaponTypeDetector.reloadWeaponLists();
        } catch (Exception e) {
            LOGGER.warn("[DevMod] Failed to reload weapon lists: {}", e.getMessage());
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
                var comp = stack.get(Objects.requireNonNull(com.frenkvs.devmod.WeaponComponents.WEAPON_STATS.get()));
                hasDevmodData = comp != null && !comp.isEmpty();
            } catch (Exception ignored) {}
            try {
                var custom = stack.get(Objects.requireNonNull(net.minecraft.core.component.DataComponents.CUSTOM_DATA));
                hasDevmodData = hasDevmodData || (custom != null && custom.contains("WeaponModStats"));
            } catch (Exception ignored) {}
            try {
                hasDevmodData = hasDevmodData || (com.frenkvs.devmod.WeaponConfigManager.loadFromAttributeModifiers(stack) != null);
            } catch (Exception ignored) {}

            if (hasDevmodData) {
                try {
                    com.frenkvs.devmod.WeaponStats stats = com.frenkvs.devmod.WeaponConfigManager.getStats(stack);
                    // Clamp and reapply (ensures modifiers/tool clear) and log if modifiers exceeded limits
                    stats = com.frenkvs.devmod.WeaponConfigManager.clampStats(stats);
                    com.frenkvs.devmod.WeaponConfigManager.setSpecificStats(stack, stats);
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
                    } catch (Exception ignored) {}
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
            } catch (Exception ignored) {}
            try {
                var custom = stack.get(Objects.requireNonNull(net.minecraft.core.component.DataComponents.CUSTOM_DATA));
                hasDevmodArmor = hasDevmodArmor || (custom != null && custom.contains("ArmorModStats"));
            } catch (Exception ignored) {}

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
                    } catch (Exception ignored) {}
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
            var comp = stack.get(Objects.requireNonNull(com.frenkvs.devmod.WeaponComponents.WEAPON_STATS.get()));
            hasDevmodData = comp != null && !comp.isEmpty();
        } catch (Exception ignored) {}
        try {
            var custom = stack.get(Objects.requireNonNull(net.minecraft.core.component.DataComponents.CUSTOM_DATA));
            hasDevmodData = hasDevmodData || (custom != null && custom.contains("WeaponModStats"));
        } catch (Exception ignored) {}
        if (!hasDevmodData) return;

        try {
            com.frenkvs.devmod.WeaponStats stats = com.frenkvs.devmod.WeaponConfigManager.getStats(stack);
            stats = com.frenkvs.devmod.WeaponConfigManager.clampStats(stats);
            com.frenkvs.devmod.WeaponConfigManager.setSpecificStats(stack, stats);
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
            var comp = stack.get(Objects.requireNonNull(com.frenkvs.devmod.WeaponComponents.WEAPON_STATS.get()));
            hasDevmodData = comp != null && !comp.isEmpty();
        } catch (Exception ignored) {}
        try {
            var custom = stack.get(Objects.requireNonNull(net.minecraft.core.component.DataComponents.CUSTOM_DATA));
            hasDevmodData = hasDevmodData || (custom != null && custom.contains("WeaponModStats"));
        } catch (Exception ignored) {}
        if (!hasDevmodData) return;
        try {
            com.frenkvs.devmod.WeaponStats stats = com.frenkvs.devmod.WeaponConfigManager.getStats(stack);
            if (stats.clearToolRules && event.getPlayer() != null) {
                // Remove any custom tool component; vanilla drop logic will apply
                stack.remove(Objects.requireNonNull(net.minecraft.core.component.DataComponents.TOOL));
                LOGGER.debug("[DevMod] Cleared tool component on drop due to clearToolRules");
            }
        } catch (Exception e) {
            LOGGER.debug("[DevMod] Drop enforcement failed: {}", e.getMessage());
        }
    }
}
