package com.devmod.compat.mods.mowziesmobs;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;
public class MowziesMobsCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(MowziesMobsCompat.class);
    public static final String MOD_ID = "mowziesmobs";

    private static boolean available = false;
    private static boolean initialized = false;
    private static boolean apiAvailable = false;

    // Cached reflection references
    private static Class<?> mowzieEntityClass;
    private static Class<?> bossEntityClass;
    private static Class<?> abilityHandlerClass;
    private static Method getAbilityHandlerMethod;

    // Known boss entity classes
    private static final List<String> BOSS_ENTITY_NAMES = Arrays.asList(
        "EntityFrostmaw",
        "EntityFerrousWroughtnaut",
        "EntityNaga",
        "EntityBarako"
    );

    // Known entity class names for detection
    private static final Map<String, String> ENTITY_DISPLAY_NAMES = new LinkedHashMap<>();
    static {
        ENTITY_DISPLAY_NAMES.put("EntityFrostmaw", "Frostmaw");
        ENTITY_DISPLAY_NAMES.put("EntityFerrousWroughtnaut", "Ferrous Wroughtnaut");
        ENTITY_DISPLAY_NAMES.put("EntityNaga", "Naga");
        ENTITY_DISPLAY_NAMES.put("EntityBarako", "Barako");
        ENTITY_DISPLAY_NAMES.put("EntityFoliaath", "Foliaath");
        ENTITY_DISPLAY_NAMES.put("EntityLantern", "Lantern");
        ENTITY_DISPLAY_NAMES.put("EntityBarakoa", "Barakoa");
        ENTITY_DISPLAY_NAMES.put("EntityBarakoana", "Barakoana");
        ENTITY_DISPLAY_NAMES.put("EntityGrottol", "Grottol");
        ENTITY_DISPLAY_NAMES.put("EntityUmvuthana", "Umvuthana");
    }

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "Mowzie's Mobs";
    }

    @Override
    public int priority() {
        // Medium priority - boss mobs
        return 35;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        if (!Compat.isLoaded(MOD_ID)) {
            LOGGER.debug("[Compat:mowziesmobs] Mowzie's Mobs not found");
            return;
        }

        available = true;
        LOGGER.info("[Compat:mowziesmobs] Mowzie's Mobs detected");
        LOGGER.debug("[Compat:mowziesmobs] Version: {}", Compat.getVersion(MOD_ID));

        // Load API classes
        loadApi();
    }

    /**
     * Load Mowzie's Mobs API classes via reflection.
     */
    private void loadApi() {
        try {
            // Try common package structures
            String[] packages = {
                "com.bobmowzie.mowziesmobs.server.entity",
                "com.bobmowzie.mowziesmobs.common.entity"
            };

            for (String pkg : packages) {
                try {
                    mowzieEntityClass = Class.forName(pkg + ".MowzieEntity");
                    LOGGER.debug("[Compat:mowziesmobs] Found MowzieEntity at {}", pkg);
                    break;
                } catch (ClassNotFoundException ignored) {}
            }

            // Try to find ability handler
            try {
                abilityHandlerClass = Class.forName(
                    "com.bobmowzie.mowziesmobs.server.ability.AbilityHandler");
            } catch (ClassNotFoundException ignored) {}

            if (mowzieEntityClass != null) {
                apiAvailable = true;
                LOGGER.info("[Compat:mowziesmobs] Mowzie's Mobs API loaded");
            }

        } catch (Exception e) {
            LOGGER.debug("[Compat:mowziesmobs] Error loading API: {}", e.getMessage());
        }
    }

    @Override
    public void initClient() {
        if (!available) return;
        LOGGER.debug("[Compat:mowziesmobs] Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "Boss detection, ability tracking, combat telemetry";
    }

    /**
     * Check if Mowzie's Mobs is available.
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
     * Check if an entity is from Mowzie's Mobs.
     *
     * @param entity The entity to check
     * @return true if entity is a Mowzie's Mobs entity
     */
    public static boolean isMowzieEntity(Entity entity) {
        if (!available || entity == null) {
            return false;
        }

        // Check by class name
        String className = entity.getClass().getSimpleName();
        return ENTITY_DISPLAY_NAMES.containsKey(className);
    }

    /**
     * Check if an entity is a Mowzie's Mobs boss.
     *
     * @param entity The entity to check
     * @return true if entity is a boss
     */
    public static boolean isMowzieBoss(Entity entity) {
        if (!isMowzieEntity(entity)) {
            return false;
        }

        String className = entity.getClass().getSimpleName();
        return BOSS_ENTITY_NAMES.contains(className);
    }

    /**
     * Get the display name for a Mowzie's Mobs entity.
     *
     * @param entity The entity
     * @return Display name, or null if not a Mowzie entity
     */
    @Nullable
    public static String getMowzieEntityName(Entity entity) {
        if (!isMowzieEntity(entity)) {
            return null;
        }
        return ENTITY_DISPLAY_NAMES.get(entity.getClass().getSimpleName());
    }

    /**
     * Get ability info for a Mowzie's Mobs entity.
     *
     * @param entity The entity
     * @return Map of ability info
     */
    public static Map<String, Object> getAbilityInfo(LivingEntity entity) {
        Map<String, Object> info = new LinkedHashMap<>();

        if (!isMowzieEntity(entity)) {
            return info;
        }

        info.put("entityType", getMowzieEntityName(entity));
        info.put("isBoss", isMowzieBoss(entity));

        if (entity instanceof LivingEntity living) {
            info.put("health", living.getHealth());
            info.put("maxHealth", living.getMaxHealth());
            info.put("healthPercent", (living.getHealth() / living.getMaxHealth()) * 100);
        }

        // Try to get ability handler info
        try {
            if (abilityHandlerClass != null) {
                Method getHandlerMethod = entity.getClass().getMethod("getAbilityHandler");
                Object handler = getHandlerMethod.invoke(entity);

                if (handler != null) {
                    // Try to get active abilities
                    Method getActiveAbilitiesMethod = handler.getClass()
                        .getMethod("getActiveAbilities");
                    Object abilities = getActiveAbilitiesMethod.invoke(handler);

                    if (abilities instanceof Collection<?> abilityList) {
                        List<String> abilityNames = new ArrayList<>();
                        for (Object ability : abilityList) {
                            try {
                                Method getNameMethod = ability.getClass().getMethod("getName");
                                Object name = getNameMethod.invoke(ability);
                                if (name != null) {
                                    abilityNames.add(name.toString());
                                }
                            } catch (Exception ignored) {}
                        }
                        if (!abilityNames.isEmpty()) {
                            info.put("activeAbilities", abilityNames);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:mowziesmobs] Could not get abilities: {}", e.getMessage());
        }

        return info;
    }

    /**
     * Get boss fight status for telemetry.
     *
     * @param entity The boss entity
     * @return Fight status map
     */
    public static Map<String, Object> getBossFightStatus(LivingEntity entity) {
        Map<String, Object> status = new LinkedHashMap<>();

        if (!isMowzieBoss(entity)) {
            return status;
        }

        String bossName = getMowzieEntityName(entity);
        status.put("boss", bossName);
        status.put("health", entity.getHealth());
        status.put("maxHealth", entity.getMaxHealth());

        // Calculate phase based on health percentage
        float healthPercent = entity.getHealth() / entity.getMaxHealth();
        int phase;
        if (healthPercent > 0.66f) {
            phase = 1;
        } else if (healthPercent > 0.33f) {
            phase = 2;
        } else {
            phase = 3;
        }
        status.put("phase", phase);

        return status;
    }

    /**
     * Get all known Mowzie's Mobs entity types.
     */
    public static Set<String> getKnownEntityTypes() {
        return Collections.unmodifiableSet(ENTITY_DISPLAY_NAMES.keySet());
    }

    /**
     * Get all known boss types.
     */
    public static List<String> getBossTypes() {
        return Collections.unmodifiableList(BOSS_ENTITY_NAMES);
    }

    /**
     * Get status summary.
     */
    public static String getStatusSummary() {
        if (!available) {
            return "Mowzie's Mobs: not available";
        }
        if (!apiAvailable) {
            return "Mowzie's Mobs: detected (API not loaded)";
        }
        return String.format("Mowzie's Mobs: %d entity types", ENTITY_DISPLAY_NAMES.size());
    }
}
