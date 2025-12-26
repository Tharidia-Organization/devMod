package com.devmod.compat.mods.smartbrainlib;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;

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

            if (smartBrainProviderClass != null) {
                LOGGER.debug("[Compat:smartbrainlib] Found SmartBrainProvider at {}",
                    smartBrainProviderClass.getName());
            }
            if (brainActivityGroupClass != null) {
                LOGGER.debug("[Compat:smartbrainlib] Found BrainActivityGroup at {}",
                    brainActivityGroupClass.getName());
            }

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
                // Use reflection to avoid deprecated Brain APIs.
                Object runningBehaviorsObj = brain.getClass()
                    .getMethod("getRunningBehaviors")
                    .invoke(brain);
                List<String> behaviorNames = new ArrayList<>();

                if (runningBehaviorsObj instanceof Iterable<?> runningBehaviors) {
                    for (Object behavior : runningBehaviors) {
                        if (behavior != null) {
                            behaviorNames.add(behavior.getClass().getSimpleName());
                        }
                    }
                }

                if (!behaviorNames.isEmpty()) {
                    status.put("activeBehaviors", behaviorNames);
                    status.put("behaviorCount", behaviorNames.size());
                }

                Object memoriesObj = brain.getClass()
                    .getMethod("getMemories")
                    .invoke(brain);
                if (memoriesObj instanceof Map<?, ?> memories) {
                    status.put("memoryCount", memories.size());

                    List<String> activeMemories = new ArrayList<>();
                    memories.forEach((memoryType, optional) -> {
                        if (optional instanceof Optional<?> opt && opt.isPresent()) {
                            activeMemories.add(String.valueOf(memoryType));
                        }
                    });

                    if (!activeMemories.isEmpty() && activeMemories.size() <= 10) {
                        status.put("activeMemories", activeMemories);
                    }
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
                var attackTargetType = Objects.requireNonNull(
                    MemoryModuleType.ATTACK_TARGET, "ATTACK_TARGET");
                var attackTarget = brain.getMemory(attackTargetType);
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
                var attackTargetType = Objects.requireNonNull(
                    MemoryModuleType.ATTACK_TARGET, "ATTACK_TARGET");
                var attackTargetOpt = brain.getMemory(attackTargetType);

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
            if (entity instanceof Mob mob) {
                LivingEntity target = mob.getTarget();
                if (target != null) {
                    info.put("hasTarget", true);
                    info.put("targetType", target.getType().toShortString());
                    info.put("targetHealth", target.getHealth());
                    info.put("targetDistance", entity.distanceTo(target));
                }
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
