package com.devmod.clone.client.renderer;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Registry for mannequin equipment data used in billboard rendering.
 *
 * <p>When a mannequin needs a billboard, it registers its equipment here
 * with a unique key. The EntityBillboardCache then retrieves this data
 * to render the correct armor/weapons on the billboard sprite.
 *
 * <p>Keys are generated from equipment hash codes to enable caching of
 * identical equipment combinations.
 */
public final class MannequinBillboardRegistry {

    /** Prefix for mannequin billboard keys in the atlas */
    public static final String KEY_PREFIX = "devmod:mannequin:";

    @Nullable
    private static MannequinBillboardRegistry instance;

    /** Map of billboard key -> equipment data */
    private final Map<String, EquipmentData> pendingEquipment = new ConcurrentHashMap<>();

    private MannequinBillboardRegistry() {}

    /**
     * Get the singleton instance.
     */
    @Nonnull
    public static synchronized MannequinBillboardRegistry getInstance() {
        if (instance == null) {
            instance = new MannequinBillboardRegistry();
        }
        return Objects.requireNonNull(instance);
    }

    /**
     * Generate a unique billboard key for the given equipment.
     * Identical equipment combinations will produce the same key,
     * enabling atlas caching.
     *
     * @param head Head armor
     * @param chest Chest armor
     * @param legs Leg armor
     * @param feet Foot armor
     * @param mainHand Main hand item
     * @param offHand Off hand item
     * @return Unique billboard key
     */
    @Nonnull
    public String generateKey(@Nonnull ItemStack head, @Nonnull ItemStack chest,
                               @Nonnull ItemStack legs, @Nonnull ItemStack feet,
                               @Nonnull ItemStack mainHand, @Nonnull ItemStack offHand) {
        // Generate hash from all equipment
        int hash = 17;
        hash = 31 * hash + getItemHash(head);
        hash = 31 * hash + getItemHash(chest);
        hash = 31 * hash + getItemHash(legs);
        hash = 31 * hash + getItemHash(feet);
        hash = 31 * hash + getItemHash(mainHand);
        hash = 31 * hash + getItemHash(offHand);

        return KEY_PREFIX + Integer.toHexString(hash);
    }

    /**
     * Register equipment data for a billboard key.
     * Call this before adding the billboard to the batcher.
     *
     * @param key The billboard key (from generateKey)
     * @param head Head armor
     * @param chest Chest armor
     * @param legs Leg armor
     * @param feet Foot armor
     * @param mainHand Main hand item
     * @param offHand Off hand item
     */
    public void registerEquipment(@Nonnull String key,
                                   @Nonnull ItemStack head, @Nonnull ItemStack chest,
                                   @Nonnull ItemStack legs, @Nonnull ItemStack feet,
                                   @Nonnull ItemStack mainHand, @Nonnull ItemStack offHand) {
        pendingEquipment.put(key, new EquipmentData(
            Objects.requireNonNull(head.copy()),
            Objects.requireNonNull(chest.copy()),
            Objects.requireNonNull(legs.copy()),
            Objects.requireNonNull(feet.copy()),
            Objects.requireNonNull(mainHand.copy()),
            Objects.requireNonNull(offHand.copy())
        ));
    }

    /**
     * Get equipment data for a billboard key.
     *
     * @param key The billboard key
     * @return Equipment data, or null if not registered
     */
    @Nullable
    public EquipmentData getEquipment(@Nonnull String key) {
        return pendingEquipment.get(key);
    }

    /**
     * Check if a key is a mannequin billboard key.
     *
     * @param key The key to check
     * @return true if it's a mannequin key
     */
    public static boolean isMannequinKey(@Nonnull String key) {
        return key.startsWith(KEY_PREFIX);
    }

    /**
     * Get hash code for an item stack.
     * Uses ItemStack.hashCode() which accounts for item type and components.
     */
    private int getItemHash(@Nonnull ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        // ItemStack.hashCode() includes item type and all components
        return stack.hashCode();
    }

    /**
     * Clear all registered equipment data.
     */
    public void clear() {
        pendingEquipment.clear();
    }

    /**
     * Reset the singleton instance.
     */
    public static synchronized void reset() {
        if (instance != null) {
            instance.clear();
            instance = null;
        }
    }

    /**
     * Equipment data for a mannequin billboard.
     */
    public record EquipmentData(
        @Nonnull ItemStack head,
        @Nonnull ItemStack chest,
        @Nonnull ItemStack legs,
        @Nonnull ItemStack feet,
        @Nonnull ItemStack mainHand,
        @Nonnull ItemStack offHand
    ) {
        /**
         * Apply this equipment to a player entity.
         */
        public void applyTo(@Nonnull net.minecraft.world.entity.LivingEntity entity) {
            entity.setItemSlot(EquipmentSlot.HEAD, head);
            entity.setItemSlot(EquipmentSlot.CHEST, chest);
            entity.setItemSlot(EquipmentSlot.LEGS, legs);
            entity.setItemSlot(EquipmentSlot.FEET, feet);
            entity.setItemSlot(EquipmentSlot.MAINHAND, mainHand);
            entity.setItemSlot(EquipmentSlot.OFFHAND, offHand);
        }
    }
}
