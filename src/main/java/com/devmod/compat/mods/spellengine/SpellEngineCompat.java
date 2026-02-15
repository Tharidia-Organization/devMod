package com.devmod.compat.mods.spellengine;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;

public class SpellEngineCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpellEngineCompat.class);
    public static final String MOD_ID = "spell_engine";

    private static volatile boolean available = false;
    private static volatile boolean initialized = false;

    // Cached reflection references
    private static Class<?> spellContainerClass;
    private static Class<?> spellCasterEntityClass;
    private static Class<?> spellHelperClass;

    private static Method getSpellIdsMethod;
    private static Method getMaxSpellsMethod;
    private static Method getCurrentSpellMethod;
    private static Method isCastingSpellMethod;
    private static Method getComponentMethod;

    private static DataComponentType<?> spellContainerComponentType;

    // Targeting mode constants
    public static final String TARGET_AIM = "AIM";
    public static final String TARGET_BEAM = "BEAM";
    public static final String TARGET_AREA = "AREA";
    public static final String TARGET_CASTER = "CASTER";
    public static final String TARGET_NONE = "NONE";

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "Spell Engine";
    }

    @Override
    public int priority() {
        // High priority - major spell framework
        return 24;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        try {
            // Try to load Spell Engine API classes via reflection
            spellContainerClass = Class.forName("net.spell_engine.api.spell.SpellContainer");

            // Try to find SpellCasterEntity interface or helper
            try {
                spellCasterEntityClass = Class.forName("net.spell_engine.entity.SpellCasterEntity");
            } catch (ClassNotFoundException e) {
                LOGGER.debug("[Compat:spell_engine] SpellCasterEntity not found, trying alternate path");
            }

            // Try to find spell helper utilities
            try {
                spellHelperClass = Class.forName("net.spell_engine.api.spell.SpellHelper");

                // Get methods from SpellHelper if available
                try {
                    getCurrentSpellMethod = spellHelperClass.getMethod("getCurrentSpell", LivingEntity.class);
                } catch (NoSuchMethodException e) {
                    LOGGER.debug("[Compat:spell_engine] getCurrentSpell method not found");
                }
            } catch (ClassNotFoundException e) {
                LOGGER.debug("[Compat:spell_engine] SpellHelper class not found");
            }

            // Get SpellContainer methods
            getSpellIdsMethod = spellContainerClass.getMethod("spell_ids");
            getMaxSpellsMethod = spellContainerClass.getMethod("max_spell_count");

            available = true;
            LOGGER.info("[Compat:spell_engine] Spell Engine detected and available");
            LOGGER.debug("[Compat:spell_engine] Version: {}", Compat.getVersion(MOD_ID));
        } catch (ClassNotFoundException e) {
            available = false;
            LOGGER.debug("[Compat:spell_engine] Spell Engine classes not found: {}", e.getMessage());
        } catch (NoSuchMethodException e) {
            available = false;
            LOGGER.warn("[Compat:spell_engine] API method not found: {}", e.getMessage());
        } catch (Exception e) {
            available = false;
            LOGGER.warn("[Compat:spell_engine] Error initializing: {}", e.getMessage());
        }
    }

    @Override
    public void initClient() {
        if (!available) return;
        LOGGER.debug("[Compat:spell_engine] Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "Spell container detection, casting state, spell damage tracking";
    }

    /**
     * Check if Spell Engine is available.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Get the SpellContainer from an ItemStack.
     *
     * @param stack The item to check
     * @return SpellContainer object, or null if not available
     */
    @Nullable
    public static Object getSpellContainer(ItemStack stack) {
        if (!available || stack == null || stack.isEmpty()) {
            return null;
        }

        if (spellContainerClass == null) {
            return null;
        }

        try {
            if (getComponentMethod == null) {
                getComponentMethod = ItemStack.class.getMethod("get", DataComponentType.class);
            }
            DataComponentType<?> componentType = resolveSpellContainerComponentType(stack);
            if (componentType == null) {
                return null;
            }
            Object result = getComponentMethod.invoke(stack, componentType);
            if (spellContainerClass.isInstance(result)) {
                return result;
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:spell_engine] Failed to read SpellContainer component: {}", e.getMessage());
        }

        return null;
    }

    @Nullable
    private static DataComponentType<?> resolveSpellContainerComponentType(ItemStack stack) {
        if (spellContainerComponentType != null) {
            return spellContainerComponentType;
        }
        if (getComponentMethod == null || spellContainerClass == null) {
            return null;
        }

        String[] candidates = {
            "net.spell_engine.api.spell.SpellComponents",
            "net.spell_engine.api.spell.SpellContainerComponents",
            "net.spell_engine.api.spell.SpellDataComponents",
            "net.spell_engine.api.spell.SpellEngineComponents",
            "net.spell_engine.init.DataComponentRegistry",
            "net.spell_engine.init.SpellDataComponents",
            "net.spell_engine.init.SpellComponents"
        };

        for (String className : candidates) {
            DataComponentType<?> componentType = findComponentTypeFromClass(stack, className);
            if (componentType != null) {
                return componentType;
            }
        }

        return findComponentTypeFromClass(stack, spellContainerClass);
    }

    @Nullable
    private static DataComponentType<?> findComponentTypeFromClass(ItemStack stack, String className) {
        try {
            return findComponentTypeFromClass(stack, Class.forName(className));
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    @Nullable
    private static DataComponentType<?> findComponentTypeFromClass(ItemStack stack, Class<?> holderClass) {
        if (spellContainerClass == null || getComponentMethod == null) {
            return null;
        }
        try {
            for (Field field : holderClass.getFields()) {
                if (!DataComponentType.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                Object candidate = field.get(null);
                if (!(candidate instanceof DataComponentType<?> componentType)) {
                    continue;
                }
                Object value = getComponentMethod.invoke(stack, componentType);
                if (spellContainerClass.isInstance(value)) {
                    spellContainerComponentType = componentType;
                    return componentType;
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    /**
     * Get the list of spell IDs from a SpellContainer.
     *
     * @param container The SpellContainer
     * @return List of spell IDs, or empty list
     */
    @SuppressWarnings("unchecked")
    public static List<String> getSpellIds(Object container) {
        if (container == null || getSpellIdsMethod == null) {
            return Collections.emptyList();
        }

        try {
            Object result = getSpellIdsMethod.invoke(container);
            if (result instanceof List) {
                return (List<String>) result;
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:spell_engine] Failed to get spell IDs: {}", e.getMessage());
        }

        return Collections.emptyList();
    }

    /**
     * Get the maximum number of spells a container can hold.
     *
     * @param container The SpellContainer
     * @return Max spell count, or -1 if not available
     */
    public static int getMaxSpells(Object container) {
        if (container == null || getMaxSpellsMethod == null) {
            return -1;
        }

        try {
            Object result = getMaxSpellsMethod.invoke(container);
            if (result instanceof Number) {
                return ((Number) result).intValue();
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:spell_engine] Failed to get max spells: {}", e.getMessage());
        }

        return -1;
    }

    /**
     * Check if an entity is currently casting a spell.
     * This requires the SpellCasterEntity interface.
     *
     * @param entity The entity to check
     * @return true if casting, false otherwise
     */
    public static boolean isCastingSpell(LivingEntity entity) {
        if (!available || entity == null || spellCasterEntityClass == null) {
            return false;
        }

        try {
            // Check if entity implements SpellCasterEntity
            if (!spellCasterEntityClass.isInstance(entity)) {
                return false;
            }

            // Try to get casting state
            if (isCastingSpellMethod == null) {
                isCastingSpellMethod = spellCasterEntityClass.getMethod("isCastingSpell");
            }

            Object result = isCastingSpellMethod.invoke(entity);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            LOGGER.debug("[Compat:spell_engine] Failed to check casting state: {}", e.getMessage());
        }

        return false;
    }

    /**
     * Get the current spell being cast by an entity.
     *
     * @param entity The entity
     * @return Spell object, or null if not casting
     */
    @Nullable
    public static Object getCurrentSpell(LivingEntity entity) {
        if (!available || entity == null) {
            return null;
        }

        // Try SpellHelper method first
        if (getCurrentSpellMethod != null) {
            try {
                return getCurrentSpellMethod.invoke(null, entity);
            } catch (Exception e) {
                LOGGER.debug("[Compat:spell_engine] Failed to get current spell via helper: {}", e.getMessage());
            }
        }

        // Try SpellCasterEntity method
        if (spellCasterEntityClass != null && spellCasterEntityClass.isInstance(entity)) {
            try {
                Method getSpellMethod = spellCasterEntityClass.getMethod("getCurrentSpell");
                return getSpellMethod.invoke(entity);
            } catch (Exception e) {
                LOGGER.debug("[Compat:spell_engine] Failed to get current spell from entity: {}", e.getMessage());
            }
        }

        return null;
    }

    /**
     * Get the ID/name of a spell.
     *
     * @param spell The Spell object
     * @return Spell ID string, or null
     */
    @Nullable
    public static String getSpellId(Object spell) {
        if (spell == null) {
            return null;
        }

        try {
            // Spells have an id() method or field
            Method idMethod = spell.getClass().getMethod("id");
            Object result = idMethod.invoke(spell);
            if (result != null) {
                return result.toString();
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:spell_engine] Failed to get spell ID: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Check if an item has spells assigned.
     *
     * @param stack The item to check
     * @return true if item has spell container with spells
     */
    public static boolean hasSpells(ItemStack stack) {
        Object container = getSpellContainer(stack);
        if (container == null) {
            return false;
        }

        List<String> spells = getSpellIds(container);
        return !spells.isEmpty();
    }

    /**
     * Get a status string for display.
     *
     * @param entity The entity to check
     * @return Status like "Casting: Fireball" or empty string
     */
    public static String getCastingStatusString(LivingEntity entity) {
        if (!available || entity == null) {
            return "";
        }

        if (isCastingSpell(entity)) {
            Object spell = getCurrentSpell(entity);
            if (spell != null) {
                String spellId = getSpellId(spell);
                if (spellId != null) {
                    return "Casting: " + spellId;
                }
                return "Casting spell...";
            }
        }

        return "";
    }

    /**
     * Check if Spell Engine API is fully functional.
     */
    public static boolean isApiFullyFunctional() {
        return available && spellContainerClass != null;
    }
}
