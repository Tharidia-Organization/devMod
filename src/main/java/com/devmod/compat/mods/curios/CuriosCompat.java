package com.devmod.compat.mods.curios;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;

/**
 * Compatibility module for Curios API 9.x (NeoForge 1.21.1)
 *
 * Uses the new CuriosApi.getCuriosInventory() pattern instead of the
 * deprecated ICuriosHelper methods.
 */
public class CuriosCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(CuriosCompat.class);
    public static final String MOD_ID = "curios";

    private static boolean available = false;
    private static boolean initialized = false;

    // Cached reflection references
    private static Class<?> curiosApiClass;
    private static Class<?> curiosItemHandlerClass;
    private static Class<?> slotStacksHandlerClass;
    private static Class<?> dynamicStackHandlerClass;

    private static Method getCuriosInventoryMethod;
    private static Method getStacksHandlerMethod;
    private static Method getCuriosMethod;
    private static Method getStacksMethod;
    private static Method getSlotsMethod;

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
            // Load Curios API 9.x classes via reflection
            curiosApiClass = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            curiosItemHandlerClass = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");

            // Try to load slot handler classes
            try {
                slotStacksHandlerClass = Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler");
            } catch (ClassNotFoundException e) {
                // Try alternative class name
                slotStacksHandlerClass = Class.forName("top.theillusivec4.curios.api.type.capability.ICurioStacksHandler");
            }

            try {
                dynamicStackHandlerClass = Class.forName("top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler");
            } catch (ClassNotFoundException e) {
                // May not exist in all versions
                dynamicStackHandlerClass = null;
            }

            // Get CuriosApi.getCuriosInventory(LivingEntity) - returns Optional<ICuriosItemHandler>
            getCuriosInventoryMethod = curiosApiClass.getMethod("getCuriosInventory", LivingEntity.class);

            // Get ICuriosItemHandler methods
            // getStacksHandler(String identifier) - returns Optional<ICurioStacksHandler>
            try {
                getStacksHandlerMethod = curiosItemHandlerClass.getMethod("getStacksHandler", String.class);
            } catch (NoSuchMethodException e) {
                LOGGER.debug("[Compat:curios] getStacksHandler method not found, trying alternatives");
            }

            // getCurios() - returns Map<String, ICurioStacksHandler>
            try {
                getCuriosMethod = curiosItemHandlerClass.getMethod("getCurios");
            } catch (NoSuchMethodException e) {
                LOGGER.debug("[Compat:curios] getCurios method not found");
            }

            // Get slot handler methods for getting stacks
            if (slotStacksHandlerClass != null) {
                try {
                    getStacksMethod = slotStacksHandlerClass.getMethod("getStacks");
                } catch (NoSuchMethodException e) {
                    LOGGER.debug("[Compat:curios] getStacks method not found on slot handler");
                }
                try {
                    getSlotsMethod = slotStacksHandlerClass.getMethod("getSlots");
                } catch (NoSuchMethodException e) {
                    LOGGER.debug("[Compat:curios] getSlots method not found on slot handler");
                }
            }

            available = true;
            LOGGER.info("[Compat:curios] Curios API 9.x detected and available");
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
     * Get the ICuriosItemHandler for an entity via reflection.
     *
     * @param entity The living entity
     * @return Optional containing the handler, or empty
     */
    @SuppressWarnings("unchecked")
    private static Optional<Object> getCuriosInventory(LivingEntity entity) {
        if (!available || entity == null || getCuriosInventoryMethod == null) {
            return Optional.empty();
        }

        try {
            Object result = getCuriosInventoryMethod.invoke(null, entity);
            if (result instanceof Optional<?>) {
                return (Optional<Object>) result;
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:curios] Failed to get curios inventory: {}", e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Get the slot handler for a specific slot type.
     *
     * @param handler The ICuriosItemHandler
     * @param slotType The slot type identifier
     * @return Optional containing the slot handler, or empty
     */
    @SuppressWarnings("unchecked")
    private static Optional<Object> getSlotHandler(Object handler, String slotType) {
        if (handler == null || slotType == null) {
            return Optional.empty();
        }

        try {
            if (getStacksHandlerMethod != null) {
                Object result = getStacksHandlerMethod.invoke(handler, slotType);
                if (result instanceof Optional<?>) {
                    return (Optional<Object>) result;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:curios] Failed to get slot handler for {}: {}", slotType, e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Get all curios map from handler.
     *
     * @param handler The ICuriosItemHandler
     * @return Map of slot type to handler, or null
     */
    @SuppressWarnings("unchecked")
    @Nullable
    private static Map<String, Object> getAllCuriosHandlers(Object handler) {
        if (handler == null || getCuriosMethod == null) {
            return null;
        }

        try {
            Object result = getCuriosMethod.invoke(handler);
            if (result instanceof Map) {
                return (Map<String, Object>) result;
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:curios] Failed to get all curios handlers: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Get ItemStacks from a slot handler.
     *
     * @param slotHandler The ICurioStacksHandler
     * @return List of ItemStacks in this slot
     */
    private static List<ItemStack> getStacksFromHandler(Object slotHandler) {
        List<ItemStack> stacks = new ArrayList<>();
        if (slotHandler == null) {
            return stacks;
        }

        try {
            // Try getStacks() method which returns IDynamicStackHandler
            if (getStacksMethod != null) {
                Object stackHandler = getStacksMethod.invoke(slotHandler);
                if (stackHandler != null) {
                    // Get slots count and iterate
                    if (getSlotsMethod != null) {
                        Object slotsObj = getSlotsMethod.invoke(slotHandler);
                        int slots = (slotsObj instanceof Number) ? ((Number) slotsObj).intValue() : 0;

                        // Try to get getStackInSlot method
                        Method getStackInSlotMethod = stackHandler.getClass().getMethod("getStackInSlot", int.class);
                        for (int i = 0; i < slots; i++) {
                            Object stack = getStackInSlotMethod.invoke(stackHandler, i);
                            if (stack instanceof ItemStack itemStack && !itemStack.isEmpty()) {
                                stacks.add(itemStack);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[Compat:curios] Failed to get stacks from handler: {}", e.getMessage());
        }

        return stacks;
    }

    /**
     * Find all curios of a specific slot type equipped by an entity.
     *
     * @param entity The entity to check
     * @param slotType The slot type (e.g., "ring", "necklace")
     * @return List of ItemStacks in that slot
     */
    public static List<ItemStack> findCurios(LivingEntity entity, String slotType) {
        List<ItemStack> results = new ArrayList<>();
        if (!available || entity == null || slotType == null) {
            return results;
        }

        Optional<Object> inventoryOpt = getCuriosInventory(entity);
        if (inventoryOpt.isEmpty()) {
            return results;
        }

        Optional<Object> slotHandlerOpt = getSlotHandler(inventoryOpt.get(), slotType);
        if (slotHandlerOpt.isEmpty()) {
            return results;
        }

        return getStacksFromHandler(slotHandlerOpt.get());
    }

    /**
     * Find the first curio of a specific slot type.
     *
     * @param entity The entity to check
     * @param slotType The slot type
     * @return Optional containing the first ItemStack, or empty
     */
    public static Optional<ItemStack> findFirstCurio(LivingEntity entity, String slotType) {
        List<ItemStack> curios = findCurios(entity, slotType);
        return curios.isEmpty() ? Optional.empty() : Optional.of(curios.get(0));
    }

    /**
     * Get all equipped curio ItemStacks for an entity.
     * Checks common slot types.
     *
     * @param entity The entity
     * @return List of all equipped curio ItemStacks
     */
    public static List<ItemStack> getAllEquippedCurios(LivingEntity entity) {
        List<ItemStack> result = new ArrayList<>();
        if (!available || entity == null) {
            return result;
        }

        Optional<Object> inventoryOpt = getCuriosInventory(entity);
        if (inventoryOpt.isEmpty()) {
            return result;
        }

        // Try to get all curios at once if possible
        Map<String, Object> allHandlers = getAllCuriosHandlers(inventoryOpt.get());
        if (allHandlers != null && !allHandlers.isEmpty()) {
            for (Object slotHandler : allHandlers.values()) {
                result.addAll(getStacksFromHandler(slotHandler));
            }
            return result;
        }

        // Fallback: check known slot types
        String[] slotTypes = {SLOT_HEAD, SLOT_NECKLACE, SLOT_BACK, SLOT_BODY,
                              SLOT_HANDS, SLOT_RING, SLOT_BELT, SLOT_CHARM, SLOT_CURIO};

        for (String slotType : slotTypes) {
            result.addAll(findCurios(entity, slotType));
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
        return !findCurios(entity, slotType).isEmpty();
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
        return findCurios(player, SLOT_RING);
    }

    /**
     * Get the first necklace equipped by a player.
     */
    public static ItemStack getEquippedNecklace(Player player) {
        return findFirstCurio(player, SLOT_NECKLACE).orElse(ItemStack.EMPTY);
    }

    /**
     * Get total curio count across all slot types.
     */
    public static int getTotalCurioCount(LivingEntity entity) {
        return getAllEquippedCurios(entity).size();
    }
}
