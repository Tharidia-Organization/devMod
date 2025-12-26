package com.devmod.mailbox.attachment;

import javax.annotation.Nullable;

import com.google.gson.JsonObject;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Attachment representing one or more items.
 */
public record ItemAttachment(
    ResourceLocation itemId,
    int count,
    @Nullable String nbtData
) implements MailAttachment {

    public static final String TYPE = "item";

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
        // Check if player has inventory space
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == Items.AIR) {
            return false;
        }

        ItemStack stack = new ItemStack(item, count);
        return player.getInventory().getFreeSlot() != -1 || player.getInventory().canPlaceItem(0, stack);
    }

    @Override
    public ClaimResult claim(ServerPlayer player) {
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == Items.AIR) {
            return ClaimResult.failure("Invalid item: " + itemId);
        }

        ItemStack stack = new ItemStack(item, count);

        // Apply NBT data if present
        if (nbtData != null && !nbtData.isBlank()) {
            try {
                // NBT parsing would go here
                // For now, skip NBT handling
            } catch (Exception e) {
                // Ignore NBT errors
            }
        }

        // Try to add to inventory
        if (player.getInventory().add(stack)) {
            return ClaimResult.successResult("Received " + getDescription());
        } else {
            // Drop at player's feet if inventory is full
            player.drop(stack, false);
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
            String itemIdStr = json.get("item").getAsString();
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
    public static ItemAttachment of(String itemId, int count) {
        ResourceLocation loc = ResourceLocation.tryParse(itemId);
        return loc != null ? new ItemAttachment(loc, count, null) : null;
    }
}
