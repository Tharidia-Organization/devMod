package com.devmod.mailbox.attachment;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/**
 * Attachment representing one or more items.
 * Supports NBT data parsing and item validation.
 */
public record ItemAttachment(
    ResourceLocation itemId,
    int count,
    @Nullable String nbtData
) implements MailAttachment {

    private static final Logger LOGGER = LoggerFactory.getLogger(ItemAttachment.class);

    public static final String TYPE = "item";

    /** Maximum stack size for attachments. */
    public static final int MAX_COUNT = 64;

    /** Blacklisted items that cannot be sent. */
    private static final Set<ResourceLocation> BLACKLISTED_ITEMS = new CopyOnWriteArraySet<>();

    /** Whitelisted items (if non-empty, only these can be sent). */
    private static final Set<ResourceLocation> WHITELISTED_ITEMS = new CopyOnWriteArraySet<>();

    /** Whether to use whitelist mode (only allow whitelisted items). */
    private static volatile boolean useWhitelist = false;

    // ============================================================================
    // ITEM RULES CONFIGURATION
    // ============================================================================

    /**
     * Add an item to the blacklist.
     */
    public static void addBlacklistedItem(ResourceLocation itemId) {
        BLACKLISTED_ITEMS.add(itemId);
        LOGGER.info("[ItemAttachment] Blacklisted item: {}", itemId);
    }

    /**
     * Remove an item from the blacklist.
     */
    public static void removeBlacklistedItem(ResourceLocation itemId) {
        BLACKLISTED_ITEMS.remove(itemId);
    }

    /**
     * Add an item to the whitelist.
     */
    public static void addWhitelistedItem(ResourceLocation itemId) {
        WHITELISTED_ITEMS.add(itemId);
        LOGGER.info("[ItemAttachment] Whitelisted item: {}", itemId);
    }

    /**
     * Remove an item from the whitelist.
     */
    public static void removeWhitelistedItem(ResourceLocation itemId) {
        WHITELISTED_ITEMS.remove(itemId);
    }

    /**
     * Set whether to use whitelist mode.
     */
    public static void setUseWhitelist(boolean enabled) {
        useWhitelist = enabled;
        LOGGER.info("[ItemAttachment] Whitelist mode: {}", enabled);
    }

    /**
     * Check if whitelist mode is enabled.
     */
    public static boolean isUseWhitelist() {
        return useWhitelist;
    }

    /**
     * Get the current blacklisted items.
     */
    public static Set<ResourceLocation> getBlacklistedItems() {
        return Set.copyOf(BLACKLISTED_ITEMS);
    }

    /**
     * Get the current whitelisted items.
     */
    public static Set<ResourceLocation> getWhitelistedItems() {
        return Set.copyOf(WHITELISTED_ITEMS);
    }

    /**
     * Clear all item rules.
     */
    public static void clearItemRules() {
        BLACKLISTED_ITEMS.clear();
        WHITELISTED_ITEMS.clear();
        useWhitelist = false;
    }

    // ============================================================================
    // VALIDATION
    // ============================================================================

    /**
     * Check if an item is allowed to be sent as an attachment.
     */
    public static boolean isItemAllowed(ResourceLocation itemId) {
        if (itemId == null) {
            return false;
        }

        // Check blacklist first
        if (BLACKLISTED_ITEMS.contains(itemId)) {
            return false;
        }

        // If whitelist mode, check whitelist
        if (useWhitelist && !WHITELISTED_ITEMS.isEmpty()) {
            return WHITELISTED_ITEMS.contains(itemId);
        }

        return true;
    }

    /**
     * Validate this attachment.
     *
     * @return error message if invalid, null if valid
     */
    @Nullable
    public String validate() {
        // Check item exists
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == Items.AIR) {
            return "Invalid item: " + itemId;
        }

        // Check item is allowed
        if (!isItemAllowed(itemId)) {
            return "Item not allowed: " + itemId;
        }

        // Check count
        if (count <= 0) {
            return "Invalid count: " + count;
        }
        if (count > MAX_COUNT) {
            return "Count exceeds maximum (" + MAX_COUNT + "): " + count;
        }

        // Validate NBT if present
        String nbt = nbtData;
        if (nbt != null && !nbt.isBlank()) {
            try {
                TagParser.parseTag(nbt);
            } catch (Exception e) {
                return "Invalid NBT data: " + e.getMessage();
            }
        }

        return null;
    }

    /**
     * Check if this attachment is valid.
     */
    public boolean isValid() {
        return validate() == null;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getDescription() {
        Item item = BuiltInRegistries.ITEM.get(itemId);
        String itemName = item != Items.AIR ? item.getDescription().getString() : itemId.toString();
        return count > 1 ? count + "x " + itemName : itemName;
    }

    @Override
    public boolean canClaim(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        // Use validation to check if attachment is valid
        return isValid();
    }

    @Override
    public ClaimResult claim(ServerPlayer player) {
        // Validate first
        String validationError = validate();
        if (validationError != null) {
            return ClaimResult.failure(validationError);
        }

        Item item = BuiltInRegistries.ITEM.get(itemId);
        ItemStack stack = new ItemStack(item, count);

        // Apply NBT data if present
        String nbt = nbtData;
        if (nbt != null && !nbt.isBlank()) {
            try {
                CompoundTag tag = TagParser.parseTag(nbt);
                if (tag != null && !tag.isEmpty()) {
                    // Apply custom data component
                    CustomData customData = CustomData.of(tag);
                    stack.set(Objects.requireNonNull(DataComponents.CUSTOM_DATA), customData);
                    LOGGER.debug("[ItemAttachment] Applied NBT data to item: {}", itemId);
                }
            } catch (Exception e) {
                LOGGER.warn("[ItemAttachment] Failed to apply NBT data: {}", e.getMessage());
                // Continue without NBT - item is still valid
            }
        }

        // Try to add to inventory
        if (player.getInventory().add(stack)) {
            LOGGER.info("[ItemAttachment] Player {} claimed {}x {}",
                player.getName().getString(), count, itemId);
            return ClaimResult.successResult("Received " + getDescription());
        } else {
            // Drop at player's feet if inventory is full
            player.drop(stack, false);
            LOGGER.info("[ItemAttachment] Player {} claimed {}x {} (dropped)",
                player.getName().getString(), count, itemId);
            return ClaimResult.successResult("Received " + getDescription() + " (dropped)");
        }
    }

    @Override
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", TYPE);
        obj.addProperty("item", itemId.toString());
        obj.addProperty("count", count);
        if (nbtData != null) {
            obj.addProperty("nbt", nbtData);
        }
        return obj;
    }

    /**
     * Create an ItemAttachment from JSON.
     */
    @Nullable
    public static ItemAttachment fromJson(JsonObject json) {
        try {
            String itemIdStr = Objects.requireNonNull(json.get("item").getAsString());
            ResourceLocation itemId = ResourceLocation.tryParse(itemIdStr);
            if (itemId == null) {
                return null;
            }

            int count = json.has("count") ? json.get("count").getAsInt() : 1;
            String nbt = json.has("nbt") ? json.get("nbt").getAsString() : null;

            return new ItemAttachment(itemId, Math.max(1, count), nbt);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Create an ItemAttachment for a specific item.
     */
    public static ItemAttachment of(ResourceLocation itemId, int count) {
        return new ItemAttachment(itemId, count, null);
    }

    /**
     * Create an ItemAttachment from a string item ID.
     */
    @Nullable
    public static ItemAttachment of(String itemId, int count) {
        if (itemId == null) {
            return null;
        }
        ResourceLocation loc = ResourceLocation.tryParse(itemId);
        return loc != null ? new ItemAttachment(loc, count, null) : null;
    }
}
