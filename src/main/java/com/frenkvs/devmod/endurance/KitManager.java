package com.frenkvs.devmod.endurance;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Manages kit application and inventory handling for Endurance Quests.
 * Supports both preset kits (enum-based) and custom user-defined kits.
 */
public final class KitManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(KitManager.class);

    public static final KitManager INSTANCE = new KitManager();

    // Currently selected kit for quick-test (on-the-fly selection)
    private List<ItemStack> temporaryKit = null;
    private String temporaryKitName = null;

    private KitManager() {
        // Load custom kits on initialization
        KitPersistence.loadKits();
    }

    /**
     * Apply a kit preset to a player, replacing their inventory.
     * For CUSTOM kit, keeps the player's current inventory.
     *
     * @param player The player to equip
     * @param kit The kit preset to apply
     * @return true if kit was applied successfully
     */
    public boolean applyKit(ServerPlayer player, KitPreset kit) {
        if (player == null || kit == null) {
            return false;
        }

        // Custom kit = keep current inventory
        if (kit.isCustom()) {
            LOGGER.info("[KitManager] Player {} using custom kit (current inventory)", player.getName().getString());
            return true;
        }

        LOGGER.info("[KitManager] Applying kit {} to player {}", kit.name(), player.getName().getString());

        // Clear current inventory
        player.getInventory().clearContent();

        // Get kit items with enchantments
        List<ItemStack> items = kit.getItems(player.level());

        // Equip armor first (check for armor slots)
        List<ItemStack> nonArmorItems = new ArrayList<>();
        for (ItemStack stack : items) {
            EquipmentSlot slot = getEquipmentSlot(stack);
            if (slot != null && slot.isArmor()) {
                player.setItemSlot(slot, java.util.Objects.requireNonNull(stack.copy()));
            } else {
                nonArmorItems.add(stack);
            }
        }

        // Add remaining items to hotbar and inventory
        int hotbarSlot = 0;
        for (ItemStack stack : nonArmorItems) {
            if (hotbarSlot < 9) {
                player.getInventory().setItem(hotbarSlot, java.util.Objects.requireNonNull(stack.copy()));
                hotbarSlot++;
            } else {
                // Add to main inventory
                player.getInventory().add(java.util.Objects.requireNonNull(stack.copy()));
            }
        }

        // Select first hotbar slot
        player.getInventory().selected = 0;

        LOGGER.info("[KitManager] Kit {} applied with {} items", kit.name(), items.size());
        return true;
    }

    /**
     * Get the equipment slot for an item, if it's armor.
     */
    @Nullable
    private EquipmentSlot getEquipmentSlot(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        net.minecraft.world.item.Item item = stack.getItem();

        // Check armor items
        if (item instanceof net.minecraft.world.item.ArmorItem armorItem) {
            return armorItem.getEquipmentSlot();
        }

        // Check shield (offhand)
        if (item == net.minecraft.world.item.Items.SHIELD) {
            return EquipmentSlot.OFFHAND;
        }

        return null;
    }

    /**
     * Validate that a kit preset string is valid.
     */
    public boolean isValidKitId(String kitId) {
        if (kitId == null || kitId.isEmpty()) {
            return false;
        }
        try {
            KitPreset.valueOf(kitId.toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Get a kit preset by ID string.
     */
    @Nullable
    public KitPreset getKitById(String kitId) {
        if (kitId == null || kitId.isEmpty()) {
            return null;
        }
        try {
            return KitPreset.valueOf(kitId.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Get all available kit presets.
     */
    public KitPreset[] getAllKits() {
        return KitPreset.values();
    }

    /**
     * Get the default kit for new quests.
     */
    public KitPreset getDefaultKit() {
        return KitPreset.STARTER;
    }

    // ========== Custom Kit Support ==========

    /**
     * Apply a custom kit to a player.
     */
    public boolean applyCustomKit(ServerPlayer player, CustomKit kit) {
        if (player == null || kit == null) {
            return false;
        }

        LOGGER.info("[KitManager] Applying custom kit '{}' to player {}",
            kit.getName(), player.getName().getString());

        // Clear current inventory
        player.getInventory().clearContent();

        // Get kit items
        List<ItemStack> items = kit.toItemStacks();

        // Equip armor first
        List<ItemStack> nonArmorItems = new ArrayList<>();
        for (ItemStack stack : items) {
            EquipmentSlot slot = getEquipmentSlot(stack);
            if (slot != null && slot.isArmor()) {
                player.setItemSlot(slot, java.util.Objects.requireNonNull(stack.copy()));
            } else {
                nonArmorItems.add(stack);
            }
        }

        // Add remaining items to hotbar and inventory
        int hotbarSlot = 0;
        for (ItemStack stack : nonArmorItems) {
            if (hotbarSlot < 9) {
                player.getInventory().setItem(hotbarSlot, java.util.Objects.requireNonNull(stack.copy()));
                hotbarSlot++;
            } else {
                player.getInventory().add(java.util.Objects.requireNonNull(stack.copy()));
            }
        }

        player.getInventory().selected = 0;

        LOGGER.info("[KitManager] Custom kit '{}' applied with {} items", kit.getName(), items.size());
        return true;
    }

    /**
     * Apply a custom kit by ID.
     */
    public boolean applyCustomKitById(ServerPlayer player, String kitId) {
        return KitPersistence.getKit(kitId)
            .map(kit -> applyCustomKit(player, kit))
            .orElse(false);
    }

    /**
     * Get all custom kits.
     */
    public List<CustomKit> getAllCustomKits() {
        return KitPersistence.getAllCustomKits();
    }

    /**
     * Get a custom kit by ID.
     */
    public Optional<CustomKit> getCustomKit(String id) {
        return KitPersistence.getKit(id);
    }

    /**
     * Save a custom kit.
     */
    public void saveCustomKit(CustomKit kit) {
        KitPersistence.saveKit(kit);
    }

    /**
     * Delete a custom kit.
     */
    public boolean deleteCustomKit(String id) {
        return KitPersistence.deleteKit(id);
    }

    /**
     * Create a custom kit from a preset.
     */
    public CustomKit createKitFromPreset(KitPreset preset) {
        return CustomKit.fromPreset(preset);
    }

    /**
     * Create a custom kit from player's current inventory.
     */
    public CustomKit createKitFromInventory(ServerPlayer player, String name) {
        return CustomKit.fromInventory(player.getInventory(), name);
    }

    // ========== Temporary Kit (On-the-fly) Support ==========

    /**
     * Set a temporary kit for quick testing.
     * This kit is not persisted and is cleared after use.
     */
    public void setTemporaryKit(List<ItemStack> items, String name) {
        this.temporaryKit = items != null ? new ArrayList<>(items) : null;
        this.temporaryKitName = name;
        LOGGER.info("[KitManager] Temporary kit set: {} ({} items)",
            name, items != null ? items.size() : 0);
    }

    /**
     * Clear the temporary kit.
     */
    public void clearTemporaryKit() {
        this.temporaryKit = null;
        this.temporaryKitName = null;
    }

    /**
     * Check if a temporary kit is set.
     */
    public boolean hasTemporaryKit() {
        return temporaryKit != null && !temporaryKit.isEmpty();
    }

    /**
     * Get the temporary kit name.
     */
    @Nullable
    public String getTemporaryKitName() {
        return temporaryKitName;
    }

    /**
     * Apply the temporary kit to a player.
     */
    public boolean applyTemporaryKit(ServerPlayer player) {
        if (player == null || temporaryKit == null || temporaryKit.isEmpty()) {
            return false;
        }

        LOGGER.info("[KitManager] Applying temporary kit '{}' to player {}",
            temporaryKitName, player.getName().getString());

        // Clear current inventory
        player.getInventory().clearContent();

        // Equip armor first
        List<ItemStack> nonArmorItems = new ArrayList<>();
        for (ItemStack stack : temporaryKit) {
            EquipmentSlot slot = getEquipmentSlot(stack);
            if (slot != null && slot.isArmor()) {
                player.setItemSlot(slot, java.util.Objects.requireNonNull(stack.copy()));
            } else {
                nonArmorItems.add(stack);
            }
        }

        // Add remaining items
        int hotbarSlot = 0;
        for (ItemStack stack : nonArmorItems) {
            if (hotbarSlot < 9) {
                player.getInventory().setItem(hotbarSlot, java.util.Objects.requireNonNull(stack.copy()));
                hotbarSlot++;
            } else {
                player.getInventory().add(java.util.Objects.requireNonNull(stack.copy()));
            }
        }

        player.getInventory().selected = 0;

        LOGGER.info("[KitManager] Temporary kit applied with {} items", temporaryKit.size());
        return true;
    }

    /**
     * Reload custom kits from disk.
     */
    public void reloadKits() {
        KitPersistence.loadKits();
        LOGGER.info("[KitManager] Reloaded {} custom kits", KitPersistence.getKitCount());
    }
}
