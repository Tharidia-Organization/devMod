package com.devmod.endurance;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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
     * Put a kit's items on a player, and report how many actually landed.
     *
     * <p>There were three byte-for-byte copies of this loop -- in {@link #applyKit},
     * {@link #applyCustomKit} and {@link #applyTemporaryKit} -- and every defect below existed in
     * all three, which is what three copies buys you.
     *
     * <ul>
     *   <li>The boolean from {@code Inventory.add} was discarded. Vanilla returns false when
     *       nothing fits and keeps nothing: the stack was <b>destroyed</b>, silently. Leftovers now
     *       drop at the player's feet, which is what every other insertion in this mod already
     *       does (see MailboxAttachmentHandler and QuestTracker).</li>
     *   <li>Two kit entries mapping to the same {@code EquipmentSlot} overwrote each other without
     *       a word. The displaced stack now goes through the normal path instead of vanishing.</li>
     *   <li>The caller logged {@code items.size()} -- the size of the <b>source list</b>, before a
     *       single insertion. It printed 9 even if all nine writes had failed, so the log could not
     *       distinguish "kit delivered" from "kit lost". It now reports what landed.</li>
     *   <li>{@code selected = 0} was written server-side with no packet. Vanilla only sends
     *       ClientboundSetCarriedItemPacket at login and from sendAllPlayerInfo, which the quest's
     *       dimension change fires <i>before</i> the kit is applied -- so the client kept its old
     *       slot while the server believed slot 0. The items were there; the wrong one was in the
     *       player's hand, and attacks resolved with an item the client was not drawing.</li>
     * </ul>
     *
     * @param player the player receiving the kit
     * @param items the kit's items; not modified
     * @return how many stacks reached the player, dropped stacks included
     */
    private int deliverItems(ServerPlayer player, List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        int delivered = 0;
        List<ItemStack> loose = new ArrayList<>();
        for (ItemStack stack : items) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            EquipmentSlot slot = getEquipmentSlot(stack);
            if (slot != null && (slot.isArmor() || slot == EquipmentSlot.OFFHAND)) {
                ItemStack occupant = player.getItemBySlot(slot);
                if (!occupant.isEmpty()) {
                    // Do not overwrite: the displaced piece is a real item the kit asked for.
                    loose.add(occupant.copy());
                }
                player.setItemSlot(slot, Objects.requireNonNull(stack.copy()));
                delivered++;
            } else {
                loose.add(stack);
            }
        }

        int hotbarSlot = 0;
        for (ItemStack stack : loose) {
            ItemStack copy = Objects.requireNonNull(stack.copy());
            if (hotbarSlot < 9) {
                player.getInventory().setItem(hotbarSlot, copy);
                hotbarSlot++;
                delivered++;
                continue;
            }
            if (player.getInventory().add(copy)) {
                delivered++;
                continue;
            }
            // Full. Drop rather than destroy, and say so: a kit that silently loses items is
            // indistinguishable from a kit that was never granted.
            LOGGER.warn("[KitManager] Inventory full for {}, dropping {} x{}",
                player.getName().getString(), copy.getItem(), copy.getCount());
            player.drop(copy, false);
            delivered++;
        }

        selectFirstHotbarSlot(player);
        return delivered;
    }

    /**
     * Point the player at the first hotbar slot, on both sides.
     *
     * @param player the player whose held slot is being reset
     */
    private void selectFirstHotbarSlot(ServerPlayer player) {
        player.getInventory().selected = 0;
        if (player.connection != null) {
            player.connection.send(
                new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));
        }
    }

    /**
     * Apply a kit preset to a player, replacing their inventory.
     *
     * @param player the player to equip
     * @param kit the kit preset to apply
     * @return true when at least one stack reached the player; false for CUSTOM, which has no items
     *     of its own, so the caller can fall back to a real kit
     */
    public boolean applyKit(ServerPlayer player, KitPreset kit) {
        if (player == null || kit == null) {
            return false;
        }

        // KitPreset.CUSTOM means "keep whatever the player is carrying", and as a QUEST kit that is
        // a contradiction: the only caller is EndurancePlayerStateManager.applyKitToPlayer, and
        // preparePlayerForQuest empties the inventory immediately before calling it. So this branch
        // used to report success while guaranteeing the player entered the arena with nothing, and
        // the server-side validator accepted the id (KitPreset.valueOf("CUSTOM") resolves).
        // Returning false makes the caller fall back to a real kit instead of believing this one.
        if (kit.isCustom()) {
            LOGGER.warn("[KitManager] Kit CUSTOM means 'keep current inventory', which is empty at "
                + "quest start. Delivering nothing for {}; the caller must fall back.",
                player.getName().getString());
            return false;
        }

        LOGGER.info("[KitManager] Applying kit {} to player {}", kit.name(), player.getName().getString());

        // Clear current inventory
        player.getInventory().clearContent();

        // Get kit items with enchantments
        List<ItemStack> items = kit.getItems(player.level());

        int delivered = deliverItems(player, items);

        LOGGER.info("[KitManager] Kit {} applied: {} of {} stacks delivered",
            kit.name(), delivered, items.size());
        return delivered > 0;
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

        int delivered = deliverItems(player, items);

        LOGGER.info("[KitManager] Custom kit '{}' applied: {} of {} stacks delivered",
            kit.getName(), delivered, items.size());
        // False on an empty result, so the caller can fall back instead of reporting success. A
        // restored synced kit whose entries all failed to deserialise reaches here with zero items.
        return delivered > 0;
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
                    // Guarded like the temporary kit three lines above, which it was not.
                    // KitSyncPersistence builds a CustomKit even when every entry failed to
                    // deserialise, so a zero-item kit was cached, then passed the quest-start
                    // validator (which only checks that the kit is present) and was "applied":
                    // inventory cleared, nothing added, success logged. The fresh-sync path already
                    // rejects an empty kit -- only the restored-from-disk path did not.
                    if (kit == null || kit.getKitItems() == null || kit.getKitItems().isEmpty()) {
                        LOGGER.warn("[KitManager] Ignoring restored custom kit '{}' for {}: it has "
                                + "no usable items",
                            kit != null ? kit.getName() : "null", player.getName().getString());
                        continue;
                    }
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

        int delivered = deliverItems(player, items);

        LOGGER.info("[KitManager] Temporary kit '{}' applied: {} of {} stacks delivered",
            name, delivered, items.size());
        return delivered > 0;
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
