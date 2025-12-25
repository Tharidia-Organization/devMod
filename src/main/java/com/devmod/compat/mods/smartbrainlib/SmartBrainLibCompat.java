package com.devmod.compat.mods.smartbrainlib;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Compatibility module for SmartBrainLib.
 *
 * SmartBrainLib provides:
 * - Advanced mob AI system using Brain API
 * - Sensor and memory management
 * - Behavior tree implementation
 * - Task scheduling and priorities
 *
 * This integration allows DevMod to:
 * - Detect SmartBrainLib-powered mobs for Arena
 * - Track AI states and active behaviors
 * - Monitor mob memory/sensors for telemetry
 * - Debug mob AI in development
 *
 * @see <a href="https://github.com/Tslat/SmartBrainLib">SmartBrainLib GitHub</a>
 */
public class SmartBrainLibCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(SmartBrainLibCompat.class);
    public static final String MOD_ID = "smartbrainlib";

    private static boolean available = false;
    private static boolean initialized = false;
    private static boolean apiAvailable = false;

    // Cached reflection references
    private static Class<?> smartBrainOwnerClass;
    private static Class<?> smartBrainProviderClass;
    private static Class<?> brainActivityGroupClass;
    private static Method getBrainMethod;

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "SmartBrainLib";
    }

    @Override
    public int priority() {
        // Medium priority - mob AI
        return 30;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        if (!Compat.isLoaded(MOD_ID)) {
            LOGGER.debug("[Compat:smartbrainlib] SmartBrainLib not found");
            return;
        }

        available = true;
        LOGGER.info("[Compat:smartbrainlib] SmartBrainLib detected");
        LOGGER.debug("[Compat:smartbrainlib] Version: {}", Compat.getVersion(MOD_ID));

        loadApi();
    }

    /**
     * Load SmartBrainLib API classes via reflection.
     */
    private void loadApi() {
        try {
            // SmartBrainLib package structure
            String[] packages = {
                "net.tslat.smartbrainlib.api.core",
                "net.tslat.smartbrainlib.api"
            };

            for (String pkg : packages) {
                try {
                    smartBrainOwnerClass = Class.forName(pkg + ".SmartBrainOwner");
                    LOGGER.debug("[Compat:smartbrainlib] Found SmartBrainOwner at {}", pkg);
                    break;
                } catch (ClassNotFoundException ignored) {}
            }

            // Try to load additional classes
            try {
                smartBrainProviderClass = Class.forName(
                    "net.tslat.smartbrainlib.api.core.SmartBrainProvider");
            } catch (ClassNotFoundException ignored) {}

            try {
                brainActivityGroupClass = Class.forName(
                    "net.tslat.smartbrainlib.api.core.BrainActivityGroup");
            } catch (ClassNotFoundException ignored) {}

            if (smartBrainOwnerClass != null) {
                apiAvailable = true;
                LOGGER.info("[Compat:smartbrainlib] SmartBrainLib API loaded");
            }

        } catch (Exception e) {
            LOGGER.debug("[Compat:smartbrainlib] Error loading API: {}", e.getMessage());
        }
    }

    @Override
    public void initClient() {
        if (!available) return;
        LOGGER.debug("[Compat:smartbrainlib] Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "Mob AI detection, behavior tracking, memory monitoring";
    }

    /**
     * Check if SmartBrainLib is available.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Check if the API is accessible.
     */
    public static boolean isApiAvailable() {
        return apiAvailable;
    }

    /**
     * Check if an entity uses SmartBrainLib AI.
     *
     * @param entity The entity to check
     * @return true if entity implements SmartBrainOwner
     */
    public static boolean isSmartBrainEntity(LivingEntity entity) {
        if (!apiAvailable || entity == null || smartBrainOwnerClass == null) {
            return false;
        }
        return smartBrainOwnerClass.isInstance(entity);
    }

    /**
     * Get AI status info for a SmartBrainLib entity.
     *
     * @param entity The entity
     * @return Map of AI state info
     */
    @SuppressWarnings("deprecation") // Brain.getRunningBehaviors/getMemories - no replacement available
    public static Map<String, Object> getAiStatus(LivingEntity entity) {
        Map<String, Object> status = new LinkedHashMap<>();

        if (!isSmartBrainEntity(entity)) {
            return status;
        }

        status.put("isSmartBrain", true);
        status.put("entityType", entity.getType().toShortString());

        try {
            // Get the brain
            var brain = entity.getBrain();
            if (brain != null) {
                // Get active behaviors from vanilla Brain
                var runningBehaviors = brain.getRunningBehaviors();
                List<String> behaviorNames = new ArrayList<>();

                for (var behavior : runningBehaviors) {
                    behaviorNames.add(behavior.getClass().getSimpleName());
                }

                if (!behaviorNames.isEmpty()) {
                    status.put("activeBehaviors", behaviorNames);
                    status.put("behaviorCount", behaviorNames.size());
                }

                // Get memories
                var memories = brain.getMemories();
                status.put("memoryCount", memories.size());

                List<String> activeMemories = new ArrayList<>();
                memories.forEach((memoryType, optional) -> {
                    if (optional.isPresent()) {
                        activeMemories.add(memoryType.toString());
                    }
                });

                if (!activeMemories.isEmpty() && activeMemories.size() <= 10) {
                    status.put("activeMemories", activeMemories);
                }
            }

        } catch (Exception e) {
            LOGGER.debug("[Compat:smartbrainlib] Error getting AI status: {}", e.getMessage());
        }

        return status;
    }

    /**
     * Get the current activity for a SmartBrainLib mob.
     *
     * @param entity The mob
     * @return Activity name, or null
     */
    public static String getCurrentActivity(LivingEntity entity) {
        if (!isSmartBrainEntity(entity)) {
            return null;
        }

        try {
            var brain = entity.getBrain();
            if (brain != null) {
                var schedule = brain.getSchedule();
                // Try to get active activity through reflection
                Method getActiveNonCoreActivityMethod = brain.getClass()
                    .getDeclaredMethod("getActiveNonCoreActivity");
                getActiveNonCoreActivityMethod.setAccessible(true);
                Object activity = getActiveNonCoreActivityMethod.invoke(brain);
                if (activity != null) {
                    return activity.toString();
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:smartbrainlib] Error getting activity: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Check if a mob is in combat mode (has attack target in memory).
     *
     * @param entity The mob
     * @return true if mob is targeting something
     */
    public static boolean isInCombat(LivingEntity entity) {
        if (entity == null) return false;

        try {
            var brain = entity.getBrain();
            if (brain != null) {
                // Check for attack target memory
                var attackTarget = brain.getMemory(
                    net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET);
                return attackTarget.isPresent();
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:smartbrainlib] Error checking combat: {}", e.getMessage());
        }

        // Fallback to vanilla check
        if (entity instanceof Mob mob) {
            return mob.getTarget() != null;
        }

        return false;
    }

    /**
     * Get target info if mob is in combat.
     *
     * @param entity The mob
     * @return Target info map
     */
    public static Map<String, Object> getTargetInfo(LivingEntity entity) {
        Map<String, Object> info = new LinkedHashMap<>();

        if (entity == null) return info;

        try {
            var brain = entity.getBrain();
            if (brain != null) {
                var attackTargetOpt = brain.getMemory(
                    net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET);

                if (attackTargetOpt.isPresent()) {
                    LivingEntity target = attackTargetOpt.get();
                    info.put("hasTarget", true);
                    info.put("targetType", target.getType().toShortString());
                    info.put("targetHealth", target.getHealth());
                    info.put("targetDistance", entity.distanceTo(target));
                }
            }
        } catch (Exception e) {
            // Fallback to vanilla
            if (entity instanceof Mob mob && mob.getTarget() != null) {
                LivingEntity target = mob.getTarget();
                info.put("hasTarget", true);
                info.put("targetType", target.getType().toShortString());
                info.put("targetHealth", target.getHealth());
                info.put("targetDistance", entity.distanceTo(target));
            }
        }

        return info;
    }

    /**
     * Get status summary.
     */
    public static String getStatusSummary() {
        if (!available) {
            return "SmartBrainLib: not available";
        }
        if (!apiAvailable) {
            return "SmartBrainLib: detected (API not loaded)";
        }
        return "SmartBrainLib: API available";
    }
}
