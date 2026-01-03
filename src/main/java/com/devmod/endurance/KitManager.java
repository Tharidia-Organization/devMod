package com.devmod.endurance;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;

public final class KitManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(KitManager.class);

    public static final KitManager INSTANCE = new KitManager();

    // Currently selected kit for quick-test (on-the-fly selection)
    private List<ItemStack> temporaryKit = null;
    private String temporaryKitName = null;

    // Per-player synced kits for dedicated server usage
    private final Map<UUID, TemporaryKit> syncedTemporaryKits = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, CustomKit>> syncedCustomKits = new ConcurrentHashMap<>();
    @Nullable
    private KitSyncPersistence syncPersistence;

    private KitManager() {
        // Load custom kits on initialization
        KitPersistence.loadKits();
    }

    public void initializeSyncPersistence(@Nullable java.nio.file.Path dataDirectory) {
        if (syncPersistence != null || dataDirectory == null) {
            return;
        }
        syncPersistence = new KitSyncPersistence();
        syncPersistence.initialize(dataDirectory);
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

        // Equip armor/offhand first (check for equipable slots)
        List<ItemStack> nonArmorItems = new ArrayList<>();
        for (ItemStack stack : items) {
            EquipmentSlot slot = getEquipmentSlot(stack);
            if (slot != null && (slot.isArmor() || slot == EquipmentSlot.OFFHAND)) {
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
     * Get the equipment slot for an item, if it's equipable.
     */
    @Nullable
    private EquipmentSlot getEquipmentSlot(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        Equipable equipable = Equipable.get(stack);
        if (equipable != null) {
            return equipable.getEquipmentSlot();
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
            KitPreset.valueOf(kitId.toUpperCase(Locale.ROOT));
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
            return KitPreset.valueOf(kitId.toUpperCase(Locale.ROOT));
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

        // Get kit items with full data restoration (attributes, durability, NBT, etc.)
        List<ItemStack> items = kit.toItemStacks(player.registryAccess());

        // Equip armor/offhand first
        List<ItemStack> nonArmorItems = new ArrayList<>();
        for (ItemStack stack : items) {
            EquipmentSlot slot = getEquipmentSlot(stack);
            if (slot != null && (slot.isArmor() || slot == EquipmentSlot.OFFHAND)) {
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
        return CustomKit.fromInventory(player.getInventory(), name, player.registryAccess());
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
     * Set a temporary kit for a specific player (dedicated server sync).
     */
    public void setTemporaryKit(UUID playerId, List<ItemStack> items, String name) {
        setTemporaryKit(playerId, items, name, null);
    }

    public void setTemporaryKit(UUID playerId, List<ItemStack> items, String name,
                                @Nullable RegistryAccess registryAccess) {
        if (playerId == null) {
            return;
        }
        if (items == null || items.isEmpty()) {
            syncedTemporaryKits.remove(playerId);
            if (syncPersistence != null) {
                syncPersistence.clearTemporaryKit(playerId);
            }
            return;
        }
        cacheTemporaryKit(playerId, new ArrayList<>(items), name);
        if (syncPersistence != null) {
            syncPersistence.setTemporaryKit(playerId, name, toKitItems(items, registryAccess));
        }
        LOGGER.info("[KitManager] Synced temporary kit for {}: {} ({} items)",
            playerId, name, items.size());
    }

    /**
     * Clear the temporary kit.
     */
    public void clearTemporaryKit() {
        this.temporaryKit = null;
        this.temporaryKitName = null;
    }

    /**
     * Clear synced kits for a player (logout/cleanup).
     */
    public void clearSyncedKits(UUID playerId) {
        if (playerId == null) {
            return;
        }
        syncedTemporaryKits.remove(playerId);
        syncedCustomKits.remove(playerId);
    }

    public void restoreSyncedKits(ServerPlayer player) {
        if (player == null || syncPersistence == null) {
            return;
        }
        UUID playerId = player.getUUID();
        syncPersistence.getPlayerSnapshot(playerId).ifPresent(snapshot -> {
            KitSyncPersistence.TemporaryKitSnapshot temporary = snapshot.temporaryKit();
            if (temporary != null && temporary.items() != null && !temporary.items().isEmpty()) {
                List<ItemStack> items = toItemStacks(temporary.items(), player.registryAccess());
                if (!items.isEmpty()) {
                    cacheTemporaryKit(playerId, items, temporary.name());
                }
            }
            if (snapshot.customKits() != null) {
                for (CustomKit kit : snapshot.customKits()) {
                    cacheCustomKit(playerId, kit);
                }
            }
        });
    }

    /**
     * Check if a temporary kit is set.
     */
    public boolean hasTemporaryKit() {
        return temporaryKit != null && !temporaryKit.isEmpty();
    }

    public boolean hasTemporaryKit(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        TemporaryKit kit = syncedTemporaryKits.get(playerId);
        return kit != null && kit.items != null && !kit.items.isEmpty();
    }

    /**
     * Get the temporary kit name.
     */
    @Nullable
    public String getTemporaryKitName() {
        return temporaryKitName;
    }

    @Nullable
    public String getTemporaryKitName(@Nullable UUID playerId) {
        if (playerId == null) {
            return temporaryKitName;
        }
        TemporaryKit kit = syncedTemporaryKits.get(playerId);
        if (kit != null && kit.name != null && !kit.name.isBlank()) {
            return kit.name;
        }
        return temporaryKitName;
    }

    /**
     * Get the temporary kit items for preview.
     * Returns an empty list if no temporary kit is set.
     */
    public List<ItemStack> getTemporaryKitItems() {
        if (temporaryKit == null) {
            return List.of();
        }
        return new ArrayList<>(temporaryKit);
    }

    public Optional<CustomKit> getSyncedCustomKit(UUID playerId, String kitId) {
        if (playerId == null || kitId == null || kitId.isEmpty()) {
            return Optional.empty();
        }
        Map<String, CustomKit> kits = syncedCustomKits.get(playerId);
        if (kits == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(kits.get(kitId));
    }

    public void registerSyncedCustomKit(UUID playerId, CustomKit kit) {
        if (playerId == null || kit == null) {
            return;
        }
        cacheCustomKit(playerId, kit);
        if (syncPersistence != null) {
            syncPersistence.setCustomKit(playerId, kit);
        }
    }

    /**
     * Apply the temporary kit to a player.
     */
    public boolean applyTemporaryKit(ServerPlayer player) {
        if (player == null) {
            return false;
        }

        TemporaryKit syncedKit = syncedTemporaryKits.get(player.getUUID());
        List<ItemStack> items = syncedKit != null ? syncedKit.items : temporaryKit;
        String name = syncedKit != null ? syncedKit.name : temporaryKitName;

        if (items == null || items.isEmpty()) {
            return false;
        }

        LOGGER.info("[KitManager] Applying temporary kit '{}' to player {}",
            name, player.getName().getString());

        // Clear current inventory
        player.getInventory().clearContent();

        // Equip armor first
        List<ItemStack> nonArmorItems = new ArrayList<>();
        for (ItemStack stack : items) {
            EquipmentSlot slot = getEquipmentSlot(stack);
            if (slot != null && (slot.isArmor() || slot == EquipmentSlot.OFFHAND)) {
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

        LOGGER.info("[KitManager] Temporary kit applied with {} items", items.size());
        return true;
    }

    private static final class TemporaryKit {
        private final List<ItemStack> items;
        private final String name;

        private TemporaryKit(List<ItemStack> items, String name) {
            this.items = items;
            this.name = name;
        }
    }

    private void cacheTemporaryKit(UUID playerId, List<ItemStack> items, String name) {
        syncedTemporaryKits.put(playerId, new TemporaryKit(items, name));
    }

    private void cacheCustomKit(UUID playerId, CustomKit kit) {
        syncedCustomKits.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>())
            .put(kit.getId(), kit);
    }

    private List<CustomKit.KitItem> toKitItems(List<ItemStack> items, @Nullable RegistryAccess registryAccess) {
        List<CustomKit.KitItem> kitItems = new ArrayList<>();
        if (items == null) {
            return kitItems;
        }
        for (ItemStack stack : items) {
            if (stack != null && !stack.isEmpty()) {
                kitItems.add(CustomKit.KitItem.fromItemStack(stack, registryAccess));
            }
        }
        return kitItems;
    }

    private List<ItemStack> toItemStacks(List<CustomKit.KitItem> kitItems, RegistryAccess registryAccess) {
        List<ItemStack> items = new ArrayList<>();
        if (kitItems == null) {
            return items;
        }
        for (CustomKit.KitItem kitItem : kitItems) {
            if (kitItem == null) {
                continue;
            }
            ItemStack stack = kitItem.toItemStackWithEnchantments(registryAccess);
            if (!stack.isEmpty()) {
                items.add(stack);
            }
        }
        return items;
    }

    /**
     * Reload custom kits from disk.
     */
    public void reloadKits() {
        KitPersistence.loadKits();
        LOGGER.info("[KitManager] Reloaded {} custom kits", KitPersistence.getKitCount());
    }
}
