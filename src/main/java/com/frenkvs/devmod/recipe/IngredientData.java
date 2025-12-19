package com.frenkvs.devmod.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a flexible ingredient (item, tag, or alternatives).
 * Immutable record for recipe ingredient data.
 */
public record IngredientData(
    @Nullable ResourceLocation item,
    @Nullable TagKey<Item> tag,
    @Nullable List<ResourceLocation> alternatives
) {
    // ═══════════════════════════════════════════════════════════════
    // FACTORY METHODS
    // ═══════════════════════════════════════════════════════════════

    public static IngredientData empty() {
        return new IngredientData(null, null, null);
    }

    public static IngredientData ofItem(ResourceLocation item) {
        Objects.requireNonNull(item, "Item cannot be null");
        return new IngredientData(item, null, null);
    }

    public static IngredientData ofItem(Item item) {
        Objects.requireNonNull(item, "Item cannot be null");
        return ofItem(BuiltInRegistries.ITEM.getKey(item));
    }

    public static IngredientData ofItem(String itemId) {
        return ofItem(ResourceLocation.parse(Objects.requireNonNull(itemId, "itemId")));
    }

    public static IngredientData ofTag(TagKey<Item> tag) {
        Objects.requireNonNull(tag, "Tag cannot be null");
        return new IngredientData(null, tag, null);
    }

    public static IngredientData ofTag(String tagPath) {
        String path = Objects.requireNonNull(tagPath, "tagPath");
        path = path.startsWith("#") ? path.substring(1) : path;
        ResourceLocation loc = ResourceLocation.parse(Objects.requireNonNull(path, "tagPath"));
        return ofTag(TagKey.create(Objects.requireNonNull(Registries.ITEM), Objects.requireNonNull(loc)));
    }

    public static IngredientData ofAny(List<ResourceLocation> items) {
        if (items == null || items.isEmpty()) {
            return empty();
        }
        return new IngredientData(null, null, List.copyOf(items));
    }

    public static IngredientData ofAnyItems(List<Item> items) {
        if (items == null || items.isEmpty()) {
            return empty();
        }
        List<ResourceLocation> locs = items.stream()
            .map(BuiltInRegistries.ITEM::getKey)
            .toList();
        return ofAny(locs);
    }

    // ═══════════════════════════════════════════════════════════════
    // QUERIES
    // ═══════════════════════════════════════════════════════════════

    public boolean isEmpty() {
        return item == null && tag == null && (alternatives == null || alternatives.isEmpty());
    }

    public boolean isItem() {
        return item != null;
    }

    public boolean isTag() {
        return tag != null;
    }

    public boolean isAlternatives() {
        return alternatives != null && !alternatives.isEmpty();
    }

    // ═══════════════════════════════════════════════════════════════
    // SERIALIZATION
    // ═══════════════════════════════════════════════════════════════

    public static IngredientData fromJson(JsonElement elem) {
        if (elem == null || elem.isJsonNull()) {
            return empty();
        }

        if (elem.isJsonPrimitive()) {
            String str = elem.getAsString();
            if (str.isEmpty()) return empty();

            if (str.startsWith("#")) {
                return ofTag(str.substring(1));
            } else {
                return ofItem(ResourceLocation.parse(Objects.requireNonNull(str)));
            }
        }

        if (elem.isJsonArray()) {
            JsonArray arr = elem.getAsJsonArray();
            List<ResourceLocation> alts = new ArrayList<>();
            for (JsonElement e : arr) {
                if (e.isJsonPrimitive()) {
                    String s = e.getAsString();
                    if (s.startsWith("#")) {
                        // Tag in array - treat as single tag for now
                        return ofTag(s.substring(1));
                    }
                    alts.add(ResourceLocation.parse(Objects.requireNonNull(s)));
                } else if (e.isJsonObject()) {
                    JsonObject obj = e.getAsJsonObject();
                    if (obj.has("item")) {
                        alts.add(ResourceLocation.parse(Objects.requireNonNull(GsonHelper.getAsString(obj, "item"))));
                    }
                }
            }
            if (alts.isEmpty()) return empty();
            if (alts.size() == 1) return ofItem(alts.getFirst());
            return ofAny(alts);
        }

        if (elem.isJsonObject()) {
            JsonObject obj = elem.getAsJsonObject();
            if (obj.has("item")) {
                return ofItem(ResourceLocation.parse(Objects.requireNonNull(GsonHelper.getAsString(obj, "item"))));
            } else if (obj.has("tag")) {
                return ofTag(Objects.requireNonNull(GsonHelper.getAsString(obj, "tag")));
            }
        }

        return empty();
    }

    public JsonElement toJson() {
        if (isEmpty()) {
            return JsonNull.INSTANCE;
        }

        if (tag != null) {
            return new JsonPrimitive("#" + tag.location());
        }

        if (alternatives != null && !alternatives.isEmpty()) {
            if (alternatives.size() == 1) {
                return new JsonPrimitive(alternatives.getFirst().toString());
            }
            JsonArray arr = new JsonArray();
            for (ResourceLocation alt : alternatives) {
                arr.add(alt.toString());
            }
            return arr;
        }

        if (item != null) {
            return new JsonPrimitive(item.toString());
        }

        return JsonNull.INSTANCE;
    }

    // ═══════════════════════════════════════════════════════════════
    // DISPLAY
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get display name for UI.
     */
    public String getDisplayName() {
        if (tag != null) {
            return "#" + tag.location();
        }
        if (alternatives != null && !alternatives.isEmpty()) {
            String first = alternatives.getFirst().getPath();
            if (alternatives.size() > 1) {
                return first + " (+" + (alternatives.size() - 1) + ")";
            }
            return first;
        }
        if (item != null) {
            return item.getPath();
        }
        return "Empty";
    }

    /**
     * Get an ItemStack for rendering.
     * For tags/alternatives, cycles through options based on tick.
     */
    public ItemStack getDisplayStack(long tick) {
        if (item != null) {
            Item i = BuiltInRegistries.ITEM.get(item);
            return i != null && i != Items.AIR ? new ItemStack(i) : ItemStack.EMPTY;
        }

        if (tag != null) {
            List<Item> items = getTagItems();
            if (!items.isEmpty()) {
                int index = (int) ((tick / 20) % items.size());
                return new ItemStack(Objects.requireNonNull(items.get(index)));
            }
        }

        if (alternatives != null && !alternatives.isEmpty()) {
            int index = (int) ((tick / 20) % alternatives.size());
            Item i = BuiltInRegistries.ITEM.get(alternatives.get(index));
            return i != null && i != Items.AIR ? new ItemStack(Objects.requireNonNull(i)) : ItemStack.EMPTY;
        }

        return ItemStack.EMPTY;
    }

    /**
     * Get all items in the tag (if this is a tag ingredient).
     */
    public List<Item> getTagItems() {
        if (tag == null) return List.of();

        List<Item> items = new ArrayList<>();
        for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(Objects.requireNonNull(tag))) {
            items.add(Objects.requireNonNull(holder.value()));
        }
        return items;
    }

    /**
     * Check if the given ItemStack matches this ingredient.
     */
    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) return isEmpty();
        if (isEmpty()) return false;

        Item stackItem = stack.getItem();

        if (item != null) {
            return BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(stackItem)).equals(item);
        }

        if (tag != null) {
            return stack.is(Objects.requireNonNull(tag));
        }

        if (alternatives != null) {
            ResourceLocation stackId = BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(stackItem));
            return alternatives.contains(stackId);
        }

        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // EQUALITY
    // ═══════════════════════════════════════════════════════════════

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IngredientData that)) return false;
        return Objects.equals(item, that.item) &&
               Objects.equals(tag, that.tag) &&
               Objects.equals(alternatives, that.alternatives);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, tag, alternatives);
    }

    @Override
    public String toString() {
        if (isEmpty()) return "IngredientData{empty}";
        if (tag != null) return "IngredientData{tag=" + tag.location() + "}";
        if (alternatives != null) return "IngredientData{alternatives=" + alternatives + "}";
        return "IngredientData{item=" + item + "}";
    }
}
