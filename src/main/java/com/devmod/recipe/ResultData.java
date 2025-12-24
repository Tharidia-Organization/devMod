package com.devmod.recipe;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Represents the result of a recipe.
 * Immutable record for recipe result data.
 */
public record ResultData(
    ResourceLocation itemId,
    int count,
    @Nullable CompoundTag components
) {
    // ═══════════════════════════════════════════════════════════════
    // VALIDATION
    // ═══════════════════════════════════════════════════════════════

    public ResultData {
        if (count < 0) count = 1;
        if (count > 64) count = 64;
    }

    // ═══════════════════════════════════════════════════════════════
    // FACTORY METHODS
    // ═══════════════════════════════════════════════════════════════

    public static ResultData empty() {
        return new ResultData(null, 0, null);
    }

    public static ResultData of(ResourceLocation itemId) {
        return new ResultData(itemId, 1, null);
    }

    public static ResultData of(ResourceLocation itemId, int count) {
        return new ResultData(itemId, count, null);
    }

    public static ResultData of(Item item) {
        Objects.requireNonNull(item, "Item cannot be null");
        return of(BuiltInRegistries.ITEM.getKey(item));
    }

    public static ResultData of(Item item, int count) {
        Objects.requireNonNull(item, "Item cannot be null");
        return of(BuiltInRegistries.ITEM.getKey(item), count);
    }

    public static ResultData of(ItemStack stack) {
        if (stack.isEmpty()) return empty();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(stack.getItem()));
        // NOTE: Components are not extracted yet.
        return new ResultData(id, stack.getCount(), null);
    }

    public static ResultData of(String itemId) {
        return of(ResourceLocation.parse(Objects.requireNonNull(itemId, "itemId")));
    }

    public static ResultData of(String itemId, int count) {
        return of(ResourceLocation.parse(Objects.requireNonNull(itemId, "itemId")), count);
    }

    // ═══════════════════════════════════════════════════════════════
    // QUERIES
    // ═══════════════════════════════════════════════════════════════

    public boolean isEmpty() {
        return itemId == null || count <= 0;
    }

    public boolean hasComponents() {
        return components != null && !components.isEmpty();
    }

    // ═══════════════════════════════════════════════════════════════
    // CONVERSION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Convert to ItemStack.
     */
    public ItemStack toItemStack() {
        if (isEmpty()) return ItemStack.EMPTY;

        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == null || item == Items.AIR) return ItemStack.EMPTY;

        ItemStack stack = new ItemStack(item, count);
        // NOTE: Components are not applied yet.
        return stack;
    }

    /**
     * Create a copy with different count.
     */
    public ResultData withCount(int newCount) {
        return new ResultData(itemId, newCount, components);
    }

    /**
     * Create a copy with components.
     */
    public ResultData withComponents(CompoundTag newComponents) {
        return new ResultData(itemId, count, newComponents);
    }

    // ═══════════════════════════════════════════════════════════════
    // SERIALIZATION
    // ═══════════════════════════════════════════════════════════════

    public static ResultData fromJson(JsonObject json) {
        if (json == null || json.isEmpty()) {
            return empty();
        }
        JsonObject root = Objects.requireNonNull(json, "json");

        // 1.21 uses "id" instead of "item"
        String idStr = null;
        if (root.has("id")) {
            idStr = GsonHelper.getAsString(root, "id");
        } else if (root.has("item")) {
            idStr = GsonHelper.getAsString(root, "item");
        }

        if (idStr == null || idStr.isEmpty()) {
            return empty();
        }

        ResourceLocation id = ResourceLocation.parse(Objects.requireNonNull(idStr, "itemId"));
        int count = GsonHelper.getAsInt(root, "count", 1);

        // NOTE: Components are not parsed yet.
        CompoundTag components = null;

        return new ResultData(id, count, components);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();

        if (isEmpty()) {
            return json;
        }

        // Use "id" for 1.21+ format
        json.addProperty("id", itemId.toString());

        if (count > 1) {
            json.addProperty("count", count);
        }

        // NOTE: Components are not serialized yet.

        return json;
    }

    // ═══════════════════════════════════════════════════════════════
    // DISPLAY
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get display name for UI.
     */
    public String getDisplayName() {
        if (isEmpty()) return "Empty";
        String name = itemId.getPath();
        if (count > 1) {
            return name + " x" + count;
        }
        return name;
    }

    // ═══════════════════════════════════════════════════════════════
    // EQUALITY
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResultData that)) return false;
        return count == that.count &&
               Objects.equals(itemId, that.itemId) &&
               Objects.equals(components, that.components);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, count, components);
    }

    @Override
    public String toString() {
        if (isEmpty()) return "ResultData{empty}";
        return "ResultData{" + itemId + " x" + count + "}";
    }
}
