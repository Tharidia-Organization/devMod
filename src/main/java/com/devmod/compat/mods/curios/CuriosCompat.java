package com.devmod.compat.mods.curios;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;

public class CuriosCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(CuriosCompat.class);
    public static final String MOD_ID = "curios";

    private static boolean available = false;
    private static boolean initialized = false;

    // Cached reflection references
    private static Class<?> curiosApiClass;
    private static Class<?> curiosHelperClass;
    private static Class<?> slotResultClass;
    private static Method getCuriosHelperMethod;
    private static Method findCuriosMethod;
    private static Method findFirstCurioMethod;

    // Slot type constants
    public static final String SLOT_HEAD = "head";
    public static final String SLOT_NECKLACE = "necklace";
    public static final String SLOT_BACK = "back";
    public static final String SLOT_BODY = "body";
    public static final String SLOT_HANDS = "hands";
    public static final String SLOT_RING = "ring";
    public static final String SLOT_BELT = "belt";
    public static final String SLOT_CHARM = "charm";
    public static final String SLOT_CURIO = "curio"; // Generic slot

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "Curios API";
    }

    @Override
    public int priority() {
        // Medium-high priority - equipment API
        return 20;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        try {
            // Try to load Curios API classes via reflection
            curiosApiClass = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            curiosHelperClass = Class.forName("top.theillusivec4.curios.api.type.util.ICuriosHelper");
            slotResultClass = Class.forName("top.theillusivec4.curios.api.SlotResult");

            // Get CuriosApi.getCuriosHelper() method
            getCuriosHelperMethod = curiosApiClass.getMethod("getCuriosHelper");

            // Get ICuriosHelper methods
            findCuriosMethod = curiosHelperClass.getMethod("findCurios",
                LivingEntity.class, String.class);
            findFirstCurioMethod = curiosHelperClass.getMethod("findFirstCurio",
                LivingEntity.class, String.class);

            available = true;
            LOGGER.info("[Compat:curios] Curios API detected and available");
            LOGGER.debug("[Compat:curios] Version: {}", Compat.getVersion(MOD_ID));
        } catch (ClassNotFoundException e) {
            available = false;
            LOGGER.debug("[Compat:curios] Curios classes not found - integration disabled");
        } catch (NoSuchMethodException e) {
            available = false;
            LOGGER.warn("[Compat:curios] Curios API method not found: {}", e.getMessage());
        } catch (Exception e) {
            available = false;
            LOGGER.warn("[Compat:curios] Error initializing: {}", e.getMessage());
        }
    }

    @Override
    public void initClient() {
        if (!available) return;
        LOGGER.debug("[Compat:curios] Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "Equipment slot detection, curio attributes in HUD, item editor support";
    }

    /**
     * Check if Curios is available.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Get the ICuriosHelper instance via reflection.
     */
    @Nullable
    private static Object getCuriosHelper() {
        if (!available || getCuriosHelperMethod == null) return null;

        try {
            return getCuriosHelperMethod.invoke(null);
        } catch (Exception e) {
            LOGGER.debug("[Compat:curios] Failed to get CuriosHelper: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Find all curios of a specific slot type equipped by an entity.
     *
     * @param entity The entity to check
     * @param slotType The slot type (e.g., "ring", "necklace")
     * @return List of SlotResult objects (as Object), or empty list
     */
    public static List<Object> findCurios(LivingEntity entity, String slotType) {
        List<Object> results = new ArrayList<>();
        if (!available || entity == null || slotType == null) {
            return results;
        }

        try {
            Object helper = getCuriosHelper();
            if (helper == null) return results;

            Object result = findCuriosMethod.invoke(helper, entity, slotType);
            if (result instanceof List) {
                results.addAll((List<?>) result);
                return results;
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:curios] Failed to find curios: {}", e.getMessage());
        }

        return results;
    }

    /**
     * Find the first curio of a specific slot type.
     *
     * @param entity The entity to check
     * @param slotType The slot type
     * @return Optional containing the SlotResult, or empty
     */
    @SuppressWarnings("unchecked")
    public static Optional<Object> findFirstCurio(LivingEntity entity, String slotType) {
        if (!available || entity == null || slotType == null) {
            return Optional.empty();
        }

        try {
            Object helper = getCuriosHelper();
            if (helper == null) return Optional.empty();

            Object result = findFirstCurioMethod.invoke(helper, entity, slotType);
            if (result instanceof Optional<?>) {
                return (Optional<Object>) result;
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:curios] Failed to find first curio: {}", e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Get the ItemStack from a SlotResult.
     *
     * @param slotResult The SlotResult object
     * @return The ItemStack, or ItemStack.EMPTY
     */
    public static ItemStack getStackFromResult(Object slotResult) {
        if (slotResult == null || slotResultClass == null) {
            return ItemStack.EMPTY;
        }

        try {
            Method getStackMethod = slotResultClass.getMethod("stack");
            Object stack = getStackMethod.invoke(slotResult);
            if (stack instanceof ItemStack) {
                return (ItemStack) stack;
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:curios] Failed to get stack from result: {}", e.getMessage());
        }

        return ItemStack.EMPTY;
    }

    /**
     * Get all equipped curio ItemStacks for an entity.
     * Checks common slot types.
     *
     * @param entity The entity
     * @return List of all equipped curio ItemStacks
     */
    public static List<ItemStack> getAllEquippedCurios(LivingEntity entity) {
        if (!available || entity == null) {
            return new ArrayList<>();
        }

        List<ItemStack> result = new ArrayList<>();
        String[] slotTypes = {SLOT_HEAD, SLOT_NECKLACE, SLOT_BACK, SLOT_BODY,
                              SLOT_HANDS, SLOT_RING, SLOT_BELT, SLOT_CHARM, SLOT_CURIO};

        for (String slotType : slotTypes) {
            List<Object> slotResults = findCurios(entity, slotType);
            for (Object slotResult : slotResults) {
                ItemStack stack = getStackFromResult(slotResult);
                if (!stack.isEmpty()) {
                    result.add(stack);
                }
            }
        }

        return result;
    }

    /**
     * Check if an entity has any curio of the specified slot type.
     *
     * @param entity The entity
     * @param slotType The slot type
     * @return true if at least one curio is equipped in that slot
     */
    public static boolean hasCurioEquipped(LivingEntity entity, String slotType) {
        return findFirstCurio(entity, slotType).isPresent();
    }

    /**
     * Get count of curios equipped in a slot type.
     *
     * @param entity The entity
     * @param slotType The slot type
     * @return Number of curios in that slot type
     */
    public static int getCurioCount(LivingEntity entity, String slotType) {
        return findCurios(entity, slotType).size();
    }

    /**
     * Check if a player has any rings equipped.
     * Convenience method for common use case.
     */
    public static boolean hasRingEquipped(Player player) {
        return hasCurioEquipped(player, SLOT_RING);
    }

    /**
     * Check if a player has a necklace equipped.
     */
    public static boolean hasNecklaceEquipped(Player player) {
        return hasCurioEquipped(player, SLOT_NECKLACE);
    }

    /**
     * Get all rings equipped by a player.
     */
    public static List<ItemStack> getEquippedRings(Player player) {
        List<ItemStack> rings = new ArrayList<>();
        for (Object result : findCurios(player, SLOT_RING)) {
            ItemStack stack = getStackFromResult(result);
            if (!stack.isEmpty()) {
                rings.add(stack);
            }
        }
        return rings;
    }

    /**
     * Get the first necklace equipped by a player.
     */
    public static ItemStack getEquippedNecklace(Player player) {
        return findFirstCurio(player, SLOT_NECKLACE)
            .map(CuriosCompat::getStackFromResult)
            .orElse(ItemStack.EMPTY);
    }

    /**
     * Get total curio count across all slot types.
     */
    public static int getTotalCurioCount(LivingEntity entity) {
        return getAllEquippedCurios(entity).size();
    }
}
