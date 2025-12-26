package com.devmod.compat.mods.spellpower;

import java.lang.reflect.Method;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.world.entity.LivingEntity;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;

/**
 * Compatibility module for Spell Power by ZsoltMolnarrr.
 *
 * Spell Power provides:
 * - Spell school attributes (Arcane, Fire, Frost, Healing, Lightning, Soul)
 * - Secondary attributes (Critical Chance, Critical Damage, Haste)
 * - Spell power calculation API
 * - Magical resistance system
 * - Vulnerability/weakness mechanics
 *
 * This integration allows DevMod to:
 * - Query spell power values for entities
 * - Display magic stats in HUD overlays
 * - Include spell power in damage calculations
 * - Track magical damage types in telemetry
 * - Support spell power items in the item editor
 *
 * @see <a href="https://github.com/ZsoltMolnarrr/SpellPower">GitHub</a>
 * @see <a href="https://modrinth.com/mod/spell-power">Modrinth</a>
 */
public class SpellPowerCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpellPowerCompat.class);
    public static final String MOD_ID = "spell_power";

    private static boolean available = false;
    private static boolean initialized = false;

    // Cached reflection references
    private static Class<?> spellPowerClass;
    private static Class<?> spellSchoolsClass;
    private static Class<?> spellSchoolClass;

    private static Method getSpellPowerMethod;
    private static Method getHasteMethod;

    // School field references (lazily loaded)
    private static Object schoolArcane;
    private static Object schoolFire;
    private static Object schoolFrost;
    private static Object schoolHealing;
    private static Object schoolLightning;
    private static Object schoolSoul;

    // Spell school constants for reference
    public static final String SCHOOL_ARCANE = "arcane";
    public static final String SCHOOL_FIRE = "fire";
    public static final String SCHOOL_FROST = "frost";
    public static final String SCHOOL_HEALING = "healing";
    public static final String SCHOOL_LIGHTNING = "lightning";
    public static final String SCHOOL_SOUL = "soul";

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "Spell Power";
    }

    @Override
    public int priority() {
        // High priority - attribute system
        return 23;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        try {
            // Try to load Spell Power API classes via reflection
            spellPowerClass = Class.forName("net.spell_power.api.SpellPower");
            spellSchoolsClass = Class.forName("net.spell_power.api.SpellSchools");
            spellSchoolClass = Class.forName("net.spell_power.api.SpellSchool");

            // Get SpellPower.getSpellPower method
            getSpellPowerMethod = spellPowerClass.getMethod("getSpellPower",
                spellSchoolClass, LivingEntity.class);

            // Get SpellPower.getHaste method
            try {
                getHasteMethod = spellPowerClass.getMethod("getHaste", LivingEntity.class, spellSchoolClass);
            } catch (NoSuchMethodException e) {
                LOGGER.debug("[Compat:spell_power] getHaste method not found");
            }

            // Load school constants
            loadSpellSchools();

            available = true;
            LOGGER.info("[Compat:spell_power] Spell Power detected and available");
            LOGGER.debug("[Compat:spell_power] Version: {}", Compat.getVersion(MOD_ID));
        } catch (ClassNotFoundException e) {
            available = false;
            LOGGER.debug("[Compat:spell_power] Spell Power classes not found: {}", e.getMessage());
        } catch (NoSuchMethodException e) {
            available = false;
            LOGGER.warn("[Compat:spell_power] API method not found: {}", e.getMessage());
        } catch (Exception e) {
            available = false;
            LOGGER.warn("[Compat:spell_power] Error initializing: {}", e.getMessage());
        }
    }

    private static void loadSpellSchools() {
        try {
            schoolArcane = spellSchoolsClass.getField("ARCANE").get(null);
            schoolFire = spellSchoolsClass.getField("FIRE").get(null);
            schoolFrost = spellSchoolsClass.getField("FROST").get(null);
            schoolHealing = spellSchoolsClass.getField("HEALING").get(null);
            schoolLightning = spellSchoolsClass.getField("LIGHTNING").get(null);
            schoolSoul = spellSchoolsClass.getField("SOUL").get(null);
        } catch (Exception e) {
            LOGGER.debug("[Compat:spell_power] Failed to load spell schools: {}", e.getMessage());
        }
    }

    @Override
    public void initClient() {
        if (!available) return;
        LOGGER.debug("[Compat:spell_power] Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "Spell power attributes, magic school detection, damage calculation";
    }

    /**
     * Check if Spell Power is available.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Get a spell school object by name.
     *
     * @param schoolName The school name (arcane, fire, frost, etc.)
     * @return The SpellSchool object, or null
     */
    @Nullable
    public static Object getSpellSchool(String schoolName) {
        if (!available || schoolName == null) {
            return null;
        }

        return switch (schoolName.toLowerCase()) {
            case SCHOOL_ARCANE -> schoolArcane;
            case SCHOOL_FIRE -> schoolFire;
            case SCHOOL_FROST -> schoolFrost;
            case SCHOOL_HEALING -> schoolHealing;
            case SCHOOL_LIGHTNING -> schoolLightning;
            case SCHOOL_SOUL -> schoolSoul;
            default -> null;
        };
    }

    /**
     * Get the spell power value for an entity in a specific school.
     *
     * @param entity The entity
     * @param schoolName The school name
     * @return Spell power result object, or null if not available
     */
    @Nullable
    public static Object getSpellPowerResult(LivingEntity entity, String schoolName) {
        if (!available || entity == null || getSpellPowerMethod == null) {
            return null;
        }

        Object school = getSpellSchool(schoolName);
        if (school == null) {
            return null;
        }

        try {
            return getSpellPowerMethod.invoke(null, school, entity);
        } catch (Exception e) {
            LOGGER.debug("[Compat:spell_power] Failed to get spell power: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get the non-critical spell power value for an entity.
     *
     * @param entity The entity
     * @param schoolName The school name
     * @return Spell power value, or -1 if not available
     */
    public static double getSpellPower(LivingEntity entity, String schoolName) {
        Object result = getSpellPowerResult(entity, schoolName);
        if (result == null) {
            return -1;
        }

        try {
            Method nonCritMethod = result.getClass().getMethod("nonCriticalValue");
            Object value = nonCritMethod.invoke(result);
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:spell_power] Failed to get non-critical value: {}", e.getMessage());
        }

        return -1;
    }

    /**
     * Get the critical spell power value for an entity.
     *
     * @param entity The entity
     * @param schoolName The school name
     * @return Critical spell power value, or -1 if not available
     */
    public static double getCriticalSpellPower(LivingEntity entity, String schoolName) {
        Object result = getSpellPowerResult(entity, schoolName);
        if (result == null) {
            return -1;
        }

        try {
            Method critMethod = result.getClass().getMethod("forcedCriticalValue");
            Object value = critMethod.invoke(result);
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:spell_power] Failed to get critical value: {}", e.getMessage());
        }

        return -1;
    }

    /**
     * Get the haste value for an entity.
     *
     * @param entity The entity
     * @param schoolName The school name (can be null for generic)
     * @return Haste value (100 = normal speed), or -1 if not available
     */
    public static double getHaste(LivingEntity entity, @Nullable String schoolName) {
        if (!available || entity == null || getHasteMethod == null) {
            return -1;
        }

        try {
            Object school = schoolName != null ? getSpellSchool(schoolName) : null;
            Object result = getHasteMethod.invoke(null, entity, school);
            if (result instanceof Number) {
                return ((Number) result).doubleValue();
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:spell_power] Failed to get haste: {}", e.getMessage());
        }

        return -1;
    }

    /**
     * Get the total spell power across all schools.
     *
     * @param entity The entity
     * @return Sum of all spell power values
     */
    public static double getTotalSpellPower(LivingEntity entity) {
        if (!available || entity == null) {
            return 0;
        }

        double total = 0;
        String[] schools = {SCHOOL_ARCANE, SCHOOL_FIRE, SCHOOL_FROST,
                           SCHOOL_HEALING, SCHOOL_LIGHTNING, SCHOOL_SOUL};

        for (String school : schools) {
            double power = getSpellPower(entity, school);
            if (power > 0) {
                total += power;
            }
        }

        return total;
    }

    /**
     * Get the highest spell power school for an entity.
     *
     * @param entity The entity
     * @return The school name with highest power, or null
     */
    @Nullable
    public static String getStrongestSchool(LivingEntity entity) {
        if (!available || entity == null) {
            return null;
        }

        String[] schools = {SCHOOL_ARCANE, SCHOOL_FIRE, SCHOOL_FROST,
                           SCHOOL_HEALING, SCHOOL_LIGHTNING, SCHOOL_SOUL};
        String strongest = null;
        double maxPower = 0;

        for (String school : schools) {
            double power = getSpellPower(entity, school);
            if (power > maxPower) {
                maxPower = power;
                strongest = school;
            }
        }

        return strongest;
    }

    /**
     * Get a formatted status string for display.
     *
     * @param entity The entity
     * @return Status like "Fire: 50.0 | Frost: 25.0" or empty string
     */
    public static String getSpellPowerSummary(LivingEntity entity) {
        if (!available || entity == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        String[] schools = {SCHOOL_FIRE, SCHOOL_FROST, SCHOOL_ARCANE,
                           SCHOOL_LIGHTNING, SCHOOL_HEALING, SCHOOL_SOUL};

        for (String school : schools) {
            double power = getSpellPower(entity, school);
            if (power > 0) {
                if (sb.length() > 0) {
                    sb.append(" | ");
                }
                sb.append(capitalize(school)).append(": ").append(String.format("%.1f", power));
            }
        }

        return sb.toString();
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    /**
     * Check if entity has any spell power.
     */
    public static boolean hasSpellPower(LivingEntity entity) {
        return getTotalSpellPower(entity) > 0;
    }

    /**
     * Check if Spell Power API is fully functional.
     */
    public static boolean isApiFullyFunctional() {
        return available
            && spellPowerClass != null
            && spellSchoolsClass != null
            && getSpellPowerMethod != null;
    }
}
