package com.devmod.compat.mods.accessories;

import com.devmod.compat.Compat;
import com.devmod.compat.CompatModule;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Compatibility module for Accessories.
 *
 * Accessories is a modern equipment slot API providing:
 * - Flexible slot system (rings, necklaces, capes, etc.)
 * - Data-driven slot definitions
 * - Attribute modifiers from accessories
 * - Rendering support for worn items
 *
 * This integration allows DevMod to:
 * - Detect equipped accessories for stat calculations
 * - Display accessory info in HUD overlays
 * - Support accessories in the item editor
 * - Include accessory stats in telemetry
 *
 * Note: Accessories is similar to Curios but with a different API.
 * Both may be present in a modpack.
 *
 * @see <a href="https://github.com/wisp-forest/accessories">Accessories GitHub</a>
 */
public class AccessoriesCompat implements CompatModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccessoriesCompat.class);
    public static final String MOD_ID = "accessories";

    private static boolean available = false;
    private static boolean initialized = false;
    private static boolean apiAvailable = false;

    // Cached reflection references
    private static Class<?> accessoriesApiClass;
    private static Class<?> accessoriesContainerClass;
    private static Class<?> slotReferenceClass;
    private static Method getCapabilityMethod;
    private static Method getContainerMethod;
    private static Method getEquippedMethod;

    // Common slot types
    public static final String SLOT_RING = "ring";
    public static final String SLOT_NECKLACE = "necklace";
    public static final String SLOT_CAPE = "cape";
    public static final String SLOT_BELT = "belt";
    public static final String SLOT_HAT = "hat";
    public static final String SLOT_FACE = "face";
    public static final String SLOT_HAND = "hand";
    public static final String SLOT_BACK = "back";
    public static final String SLOT_CHARM = "charm";

    private static final List<String> ALL_SLOT_TYPES = Arrays.asList(
        SLOT_RING, SLOT_NECKLACE, SLOT_CAPE, SLOT_BELT,
        SLOT_HAT, SLOT_FACE, SLOT_HAND, SLOT_BACK, SLOT_CHARM
    );

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public String displayName() {
        return "Accessories";
    }

    @Override
    public int priority() {
        // Same priority as Curios - equipment system
        return 21;
    }

    @Override
    public void initCommon() {
        if (initialized) return;
        initialized = true;

        if (!Compat.isLoaded(MOD_ID)) {
            LOGGER.debug("[Compat:accessories] Accessories not found");
            return;
        }

        available = true;
        LOGGER.info("[Compat:accessories] Accessories detected");
        LOGGER.debug("[Compat:accessories] Version: {}", Compat.getVersion(MOD_ID));

        // Try to load API classes
        tryLoadApi();
    }

    /**
     * Attempt to load Accessories API classes via reflection.
     */
    private void tryLoadApi() {
        try {
            // Accessories API package locations
            String[] possiblePackages = {
                "io.wispforest.accessories.api",
                "io.wispforest.accessories"
            };

            for (String pkg : possiblePackages) {
                try {
                    accessoriesApiClass = Class.forName(pkg + ".AccessoriesAPI");
                    LOGGER.debug("[Compat:accessories] Found AccessoriesAPI at {}", pkg);
                    break;
                } catch (ClassNotFoundException ignored) {}
            }

            if (accessoriesApiClass != null) {
                // Try to find container and capability classes
                try {
                    accessoriesContainerClass = Class.forName(
                        "io.wispforest.accessories.api.AccessoriesContainer");
                    slotReferenceClass = Class.forName(
                        "io.wispforest.accessories.api.slot.SlotReference");

                    // Get API methods
                    getCapabilityMethod = accessoriesApiClass.getMethod(
                        "getCapability", LivingEntity.class);

                    apiAvailable = true;
                    LOGGER.info("[Compat:accessories] Accessories API available");
                } catch (Exception e) {
                    LOGGER.debug("[Compat:accessories] Could not load full API: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            LOGGER.debug("[Compat:accessories] Error loading API: {}", e.getMessage());
        }
    }

    @Override
    public void initClient() {
        if (!available) return;
        LOGGER.debug("[Compat:accessories] Client initialization complete");
    }

    @Override
    public String getFeatureDescription() {
        return "Equipment slot detection (rings, necklaces, etc.), accessory stats in HUD";
    }

    /**
     * Check if Accessories is available.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Check if the Accessories API is accessible.
     */
    public static boolean isApiAvailable() {
        return apiAvailable;
    }

    /**
     * Get the accessories capability for an entity.
     *
     * @param entity The entity
     * @return The capability object, or null
     */
    @Nullable
    public static Object getCapability(LivingEntity entity) {
        if (!apiAvailable || entity == null || getCapabilityMethod == null) {
            return null;
        }

        try {
            Object result = getCapabilityMethod.invoke(null, entity);
            if (result instanceof Optional<?>) {
                return ((Optional<?>) result).orElse(null);
            }
            return result;
        } catch (Exception e) {
            LOGGER.debug("[Compat:accessories] Error getting capability: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Check if an entity has accessories equipped.
     *
     * @param entity The entity
     * @return true if has any accessories
     */
    public static boolean hasAccessories(LivingEntity entity) {
        if (!available || entity == null) {
            return false;
        }

        Object capability = getCapability(entity);
        if (capability == null) {
            return false;
        }

        // Check if any slots have items
        List<ItemStack> all = getAllEquippedAccessories(entity);
        return !all.isEmpty();
    }

    /**
     * Get all equipped accessories for an entity.
     *
     * @param entity The entity
     * @return List of equipped accessory ItemStacks
     */
    public static List<ItemStack> getAllEquippedAccessories(LivingEntity entity) {
        List<ItemStack> result = new ArrayList<>();

        if (!apiAvailable || entity == null) {
            return result;
        }

        Object capability = getCapability(entity);
        if (capability == null) {
            return result;
        }

        try {
            // Try to iterate through all containers
            Method getContainersMethod = capability.getClass().getMethod("getContainers");
            Object containers = getContainersMethod.invoke(capability);

            if (containers instanceof Map<?, ?>) {
                for (Object container : ((Map<?, ?>) containers).values()) {
                    if (container == null) continue;

                    // Get stacks from container
                    try {
                        Method getAccessoriesMethod = container.getClass().getMethod("getAccessories");
                        Object accessoriesList = getAccessoriesMethod.invoke(container);

                        if (accessoriesList instanceof List<?>) {
                            for (Object item : (List<?>) accessoriesList) {
                                if (item instanceof ItemStack stack && !stack.isEmpty()) {
                                    result.add(stack);
                                }
                            }
                        }
                    } catch (NoSuchMethodException ignored) {
                        // Try alternative method
                        try {
                            Method sizeMethod = container.getClass().getMethod("getSize");
                            int size = (int) sizeMethod.invoke(container);

                            Method getStackMethod = container.getClass().getMethod(
                                "getItem", int.class);

                            for (int i = 0; i < size; i++) {
                                Object stack = getStackMethod.invoke(container, i);
                                if (stack instanceof ItemStack itemStack && !itemStack.isEmpty()) {
                                    result.add(itemStack);
                                }
                            }
                        } catch (Exception ignored2) {}
                    }
                }
            }

        } catch (Exception e) {
            LOGGER.debug("[Compat:accessories] Error getting accessories: {}", e.getMessage());
        }

        return result;
    }

    /**
     * Get accessories of a specific slot type.
     *
     * @param entity The entity
     * @param slotType The slot type (e.g., "ring", "necklace")
     * @return List of ItemStacks in that slot type
     */
    public static List<ItemStack> getAccessoriesBySlot(LivingEntity entity, String slotType) {
        List<ItemStack> result = new ArrayList<>();

        if (!apiAvailable || entity == null || slotType == null) {
            return result;
        }

        Object capability = getCapability(entity);
        if (capability == null) {
            return result;
        }

        try {
            // Try to get container for specific slot type
            Method getContainerMethod = capability.getClass().getMethod("getContainer", String.class);
            Object container = getContainerMethod.invoke(capability, slotType);

            if (container != null) {
                // Get stacks from container
                try {
                    Method sizeMethod = container.getClass().getMethod("getSize");
                    int size = (int) sizeMethod.invoke(container);

                    Method getStackMethod = container.getClass().getMethod("getItem", int.class);

                    for (int i = 0; i < size; i++) {
                        Object stack = getStackMethod.invoke(container, i);
                        if (stack instanceof ItemStack itemStack && !itemStack.isEmpty()) {
                            result.add(itemStack);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.debug("[Compat:accessories] Error getting slot items: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            LOGGER.debug("[Compat:accessories] Error getting slot container: {}", e.getMessage());
        }

        return result;
    }

    /**
     * Check if an entity has a specific type of accessory equipped.
     *
     * @param entity The entity
     * @param slotType The slot type
     * @return true if at least one accessory is in that slot
     */
    public static boolean hasAccessoryInSlot(LivingEntity entity, String slotType) {
        return !getAccessoriesBySlot(entity, slotType).isEmpty();
    }

    /**
     * Get the first ring equipped by an entity.
     */
    public static ItemStack getFirstRing(LivingEntity entity) {
        List<ItemStack> rings = getAccessoriesBySlot(entity, SLOT_RING);
        return rings.isEmpty() ? ItemStack.EMPTY : rings.get(0);
    }

    /**
     * Get all rings equipped by an entity.
     */
    public static List<ItemStack> getRings(LivingEntity entity) {
        return getAccessoriesBySlot(entity, SLOT_RING);
    }

    /**
     * Get the necklace equipped by an entity.
     */
    public static ItemStack getNecklace(LivingEntity entity) {
        List<ItemStack> necklaces = getAccessoriesBySlot(entity, SLOT_NECKLACE);
        return necklaces.isEmpty() ? ItemStack.EMPTY : necklaces.get(0);
    }

    /**
     * Get the cape equipped by an entity.
     */
    public static ItemStack getCape(LivingEntity entity) {
        List<ItemStack> capes = getAccessoriesBySlot(entity, SLOT_CAPE);
        return capes.isEmpty() ? ItemStack.EMPTY : capes.get(0);
    }

    /**
     * Get the total count of equipped accessories.
     */
    public static int getTotalAccessoryCount(LivingEntity entity) {
        return getAllEquippedAccessories(entity).size();
    }

    /**
     * Get a map of slot types to their equipped items.
     */
    public static Map<String, List<ItemStack>> getAccessoriesBySlotType(LivingEntity entity) {
        Map<String, List<ItemStack>> result = new LinkedHashMap<>();

        if (!available || entity == null) {
            return result;
        }

        for (String slotType : ALL_SLOT_TYPES) {
            List<ItemStack> items = getAccessoriesBySlot(entity, slotType);
            if (!items.isEmpty()) {
                result.put(slotType, items);
            }
        }

        return result;
    }

    /**
     * Check if both Accessories and Curios are present.
     * Some modpacks use both.
     */
    public static boolean isCuriosAlsoPresent() {
        return Compat.isLoaded("curios");
    }

    /**
     * Get status summary.
     */
    public static String getStatusSummary() {
        if (!available) {
            return "Accessories: not available";
        }
        if (!apiAvailable) {
            return "Accessories: detected (API not available)";
        }
        String curiosNote = isCuriosAlsoPresent() ? " (+ Curios)" : "";
        return "Accessories: API available" + curiosNote;
    }

    /**
     * Get all known slot types.
     */
    public static List<String> getAllSlotTypes() {
        return Collections.unmodifiableList(ALL_SLOT_TYPES);
    }
}
