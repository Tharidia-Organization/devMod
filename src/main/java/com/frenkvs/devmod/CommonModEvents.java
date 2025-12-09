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

@EventBusSubscriber(modid = MODID)
public class CommonModEvents {
	private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void modifyEntityAttributes(EntityAttributeModificationEvent event) {
        LOGGER.info("Inizio scansione entità per ENTITY_INTERACTION_RANGE...");
        LOGGER.info("Numero di tipi di entità disponibili: {}", event.getTypes().size());
        int successCount = 0;
        int failCount = 0;

        // Scansiona TUTTI i tipi di entità che hanno attributi
        for (EntityType<? extends LivingEntity> type : event.getTypes()) {
            // Prova ad aggiungere l'attributo ENTITY_INTERACTION_RANGE
            // Se già presente, getAttributeValue non è null
            if (!event.has(type, Attributes.ENTITY_INTERACTION_RANGE)) {
                try {
                    event.add(type, Attributes.ENTITY_INTERACTION_RANGE, 0.0);
                    successCount++;

                    // Log solo i primi 5 successi per debug
                    if (successCount <= 5) {
                        LOGGER.info("Aggiunto ENTITY_INTERACTION_RANGE a: {}", EntityType.getKey(type));
                    }
                } catch (Exception e) {
                    // Attributo già presente o errore, skip
                    failCount++;
                    LOGGER.debug("Impossibile aggiungere attributo a {}: {}", EntityType.getKey(type), e.getMessage());
                }
            } else {
                failCount++;
            }
        }

        LOGGER.info("Fine scansione. Aggiunti ENTITY_INTERACTION_RANGE: {} | Già presenti (Skipped): {}", successCount, failCount);
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
            LOGGER.info("[DevMod] EnduranceQuestManager initialized successfully");
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
