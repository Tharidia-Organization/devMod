package com.frenkvs.devmod;

import static com.frenkvs.devmod.DevMod.MODID;

import com.frenkvs.devmod.endurance.EnduranceQuestManager;
import com.frenkvs.devmod.testing.stats.HazardTypeRegistry;
import com.frenkvs.devmod.util.ConfigPaths;
import com.frenkvs.devmod.util.DamageTypeConfig;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
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
}
