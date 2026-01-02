package com.devmod.compat.mods.curios;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.devmod.compat.BaseCompatModule;

/**
 * Compatibility module for Curios API 9.x (NeoForge 1.21.1)
 *
 * <p>Uses the new CuriosApi.getCuriosInventory() pattern instead of the
 * deprecated ICuriosHelper methods.
 *
 * <p>Migrated to extend BaseCompatModule for standardized initialization,
 * reflection caching, and error handling.
 */
public class CuriosCompat extends BaseCompatModule {

    public static final String MOD_ID = "curios";

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

    // Class names for reflection
    private static final String CURIOS_API_CLASS = "top.theillusivec4.curios.api.CuriosApi";
    private static final String CURIOS_ITEM_HANDLER_CLASS = "top.theillusivec4.curios.api.type.capability.ICuriosItemHandler";
    private static final String SLOT_STACKS_HANDLER_CLASS = "top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler";
    private static final String SLOT_STACKS_HANDLER_CLASS_ALT = "top.theillusivec4.curios.api.type.capability.ICurioStacksHandler";
    private static final String DYNAMIC_STACK_HANDLER_CLASS = "top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler";

    // Method cache keys
    private static final String KEY_GET_CURIOS_INVENTORY = "getCuriosInventory";
    private static final String KEY_GET_STACKS_HANDLER = "getStacksHandler";
    private static final String KEY_GET_CURIOS = "getCurios";
    private static final String KEY_GET_STACKS = "getStacks";
    private static final String KEY_GET_SLOTS = "getSlots";
    private static final String KEY_GET_STACK_IN_SLOT = "getStackInSlot";
    private static final String KEY_GET_DYNAMIC_SLOTS = "getDynamicSlots";

    // Singleton instance
    private static CuriosCompat instance;

    public CuriosCompat() {
        super(MOD_ID, "Curios API", 20); // Medium-high priority - equipment API
        instance = this;
    }

    @Override
    protected void doInitCommon() throws Exception {
        // Load CuriosApi class
        Class<?> curiosApiClass = loadRequiredClass(CURIOS_API_CLASS);

        // Load ICuriosItemHandler
        Class<?> curiosItemHandlerClass = loadRequiredClass(CURIOS_ITEM_HANDLER_CLASS);

        // Load slot handler class (try both names)
        Class<?> slotStacksHandlerClass = loadClassFromAny(
            SLOT_STACKS_HANDLER_CLASS,
            SLOT_STACKS_HANDLER_CLASS_ALT
        );
        if (slotStacksHandlerClass == null) {
            throw new ClassNotFoundException("Could not find ICurioStacksHandler class");
        }

        // Cache main methods
        Method getCuriosInventoryMethod = getMethod(curiosApiClass, "getCuriosInventory", LivingEntity.class);
        if (getCuriosInventoryMethod == null) {
            throw new NoSuchMethodException("getCuriosInventory(LivingEntity) not found");
        }
        methodCache.put(KEY_GET_CURIOS_INVENTORY, getCuriosInventoryMethod);

        // Cache optional methods on ICuriosItemHandler
        Method getStacksHandlerMethod = getMethod(curiosItemHandlerClass, "getStacksHandler", String.class);
        if (getStacksHandlerMethod != null) {
            methodCache.put(KEY_GET_STACKS_HANDLER, getStacksHandlerMethod);
        } else {
            debug("getStacksHandler method not found, trying alternatives");
        }

        Method getCuriosMethod = getMethod(curiosItemHandlerClass, "getCurios");
        if (getCuriosMethod != null) {
            methodCache.put(KEY_GET_CURIOS, getCuriosMethod);
        } else {
            debug("getCurios method not found");
        }

        // Cache slot handler methods
        Method getStacksMethod = getMethod(slotStacksHandlerClass, "getStacks");
        if (getStacksMethod != null) {
            methodCache.put(KEY_GET_STACKS, getStacksMethod);
        }

        Method getSlotsMethod = getMethod(slotStacksHandlerClass, "getSlots");
        if (getSlotsMethod != null) {
            methodCache.put(KEY_GET_SLOTS, getSlotsMethod);
        }

        // Load IDynamicStackHandler for efficient stack access
        Class<?> dynamicStackHandlerClass = loadOptionalClass(DYNAMIC_STACK_HANDLER_CLASS);
        if (dynamicStackHandlerClass != null) {
            Method getStackInSlotMethod = getMethod(dynamicStackHandlerClass, "getStackInSlot", int.class);
            Method getDynamicSlotsMethod = getMethod(dynamicStackHandlerClass, "getSlots");

            if (getStackInSlotMethod != null) {
                methodCache.put(KEY_GET_STACK_IN_SLOT, getStackInSlotMethod);
            }
            if (getDynamicSlotsMethod != null) {
                methodCache.put(KEY_GET_DYNAMIC_SLOTS, getDynamicSlotsMethod);
            }
            debug("IDynamicStackHandler methods cached");
        } else {
            debug("IDynamicStackHandler not found, will use runtime reflection");
        }

        info("Curios API 9.x detected and available");
    }

    @Override
    protected void doInitClient() {
        debug("Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "Equipment slot detection, curio attributes in HUD, item editor support";
    }

    // ============================================================================
    // STATIC API
    // ============================================================================

    /**
     * Check if Curios is loaded and available.
     */
    public static boolean isCuriosLoaded() {
        return instance != null && instance.available;
    }

    /**
     * Get the ICuriosItemHandler for an entity via reflection.
     *
     * @param entity The living entity
     * @return Optional containing the handler, or empty
     */
    @SuppressWarnings("unchecked")
    private static Optional<Object> getCuriosInventory(LivingEntity entity) {
        if (instance == null || !instance.available || entity == null) {
            return Optional.empty();
        }

        Method method = instance.methodCache.get(KEY_GET_CURIOS_INVENTORY);
        if (method == null) {
            return Optional.empty();
        }

        try {
            Object result = method.invoke(null, entity);
            if (result instanceof Optional<?>) {
                return (Optional<Object>) result;
            }
        } catch (Exception e) {
            instance.debug("Failed to get curios inventory: {}", e.getMessage());
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
        if (instance == null || handler == null || slotType == null) {
            return Optional.empty();
        }

        Method method = instance.methodCache.get(KEY_GET_STACKS_HANDLER);
        if (method == null) {
            return Optional.empty();
        }

        try {
            Object result = method.invoke(handler, slotType);
            if (result instanceof Optional<?>) {
                return (Optional<Object>) result;
            }
        } catch (Exception e) {
            instance.debug("Failed to get slot handler for {}: {}", slotType, e.getMessage());
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
        if (instance == null || handler == null) {
            return null;
        }

        Method method = instance.methodCache.get(KEY_GET_CURIOS);
        if (method == null) {
            return null;
        }

        try {
            Object result = method.invoke(handler);
            if (result instanceof Map) {
                return (Map<String, Object>) result;
            }
        } catch (Exception e) {
            instance.debug("Failed to get all curios handlers: {}", e.getMessage());
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
        if (instance == null || slotHandler == null) {
            return stacks;
        }

        try {
            Method getStacksMethod = instance.methodCache.get(KEY_GET_STACKS);
            if (getStacksMethod == null) {
                return stacks;
            }

            Object stackHandler = getStacksMethod.invoke(slotHandler);
            if (stackHandler == null) {
                return stacks;
            }

            // Get slot count - prefer IDynamicStackHandler.getSlots() if cached
            int slots = 0;
            Method getDynamicSlotsMethod = instance.methodCache.get(KEY_GET_DYNAMIC_SLOTS);
            Method getSlotsMethod = instance.methodCache.get(KEY_GET_SLOTS);

            if (getDynamicSlotsMethod != null) {
                Object slotsObj = getDynamicSlotsMethod.invoke(stackHandler);
                slots = (slotsObj instanceof Number) ? ((Number) slotsObj).intValue() : 0;
            } else if (getSlotsMethod != null) {
                // Fallback to ICurioStacksHandler.getSlots()
                Object slotsObj = getSlotsMethod.invoke(slotHandler);
                slots = (slotsObj instanceof Number) ? ((Number) slotsObj).intValue() : 0;
            }

            // Get stacks using cached method or fallback to runtime reflection
            Method stackMethod = instance.methodCache.get(KEY_GET_STACK_IN_SLOT);
            if (stackMethod == null) {
                // Fallback: get method from runtime class (less efficient)
                stackMethod = stackHandler.getClass().getMethod("getStackInSlot", int.class);
            }

            for (int i = 0; i < slots; i++) {
                Object stack = stackMethod.invoke(stackHandler, i);
                if (stack instanceof ItemStack itemStack && !itemStack.isEmpty()) {
                    stacks.add(itemStack);
                }
            }
        } catch (Exception e) {
            instance.debug("Failed to get stacks from handler: {}", e.getMessage());
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
        if (!isCuriosLoaded() || entity == null || slotType == null) {
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
        if (!isCuriosLoaded() || entity == null) {
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
