package com.devmod.recipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
public record CraftingRecipeData(
    ResourceLocation id,
    CraftingType craftingType,
    RecipeCategory category,
    @Nullable String group,
    List<IngredientData> ingredients,
    @Nullable String[] pattern,
    @Nullable Map<Character, IngredientData> keyMap,
    ResultData result,
    boolean showNotification,
    boolean isModified,
    @Nullable ResourceLocation originalId
) implements RecipeData {

    // ═══════════════════════════════════════════════════════════════
    // VALIDATION
    // ═══════════════════════════════════════════════════════════════

    public CraftingRecipeData {
        Objects.requireNonNull(id, "Recipe ID cannot be null");
        Objects.requireNonNull(craftingType, "Crafting type cannot be null");
        Objects.requireNonNull(result, "Result cannot be null");
        if (category == null) category = RecipeCategory.MISC;
        if (ingredients == null) ingredients = List.of();

        if (craftingType == CraftingType.SHAPED) {
            if (pattern == null || pattern.length == 0) {
                pattern = new String[]{"   ", "   ", "   "};
            }
            if (pattern.length > 3) {
                throw new IllegalArgumentException("Pattern can have at most 3 rows");
            }
            for (String row : pattern) {
                if (row != null && row.length() > 3) {
                    throw new IllegalArgumentException("Pattern row can have at most 3 characters");
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // FACTORY METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Create an empty recipe for a given result item.
     * The recipe ID is deterministic based on the item, so editing the same
     * item will always produce the same recipe ID (allowing overwrites).
     */
    public static CraftingRecipeData empty(ItemStack resultItem) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(resultItem.getItem()));
        // Use deterministic ID based on item - this ensures editing the same item
        // overwrites the previous recipe instead of creating duplicates
        String path = "custom_" + itemId.getPath().replace("/", "_");
        ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath("devmod", path);

        return new CraftingRecipeData(
            recipeId,
            CraftingType.SHAPED,
            RecipeCategory.MISC,
            null,
            List.of(),
            new String[]{"   ", "   ", "   "},
            new HashMap<>(),
            ResultData.of(resultItem),
            true,
            false,
            null
        );
    }

    /**
     * Create a shaped recipe.
     */
    public static CraftingRecipeData shaped(
        ResourceLocation id,
        String[] pattern,
        Map<Character, IngredientData> key,
        ResultData result
    ) {
        List<IngredientData> ingredients = new ArrayList<>(key.values());
        return new CraftingRecipeData(
            id, CraftingType.SHAPED, RecipeCategory.MISC, null,
            ingredients, pattern, key, result, true, false, null
        );
    }

    /**
     * Create a shapeless recipe.
     */
    public static CraftingRecipeData shapeless(
        ResourceLocation id,
        List<IngredientData> ingredients,
        ResultData result
    ) {
        return new CraftingRecipeData(
            id, CraftingType.SHAPELESS, RecipeCategory.MISC, null,
            ingredients, null, null, result, true, false, null
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // DESERIALIZATION
    // ═══════════════════════════════════════════════════════════════

    public static CraftingRecipeData fromJson(ResourceLocation id, JsonObject json) {
        ResourceLocation safeId = Objects.requireNonNull(id, "id");
        JsonObject root = Objects.requireNonNull(json, "json");
        String type = GsonHelper.getAsString(root, "type");
        CraftingType craftingType = CraftingType.fromMinecraftType(type);

        // Category
        String catStr = GsonHelper.getAsString(root, "category", "misc");
        RecipeCategory category = RecipeCategory.fromString(catStr);

        // Group
        String group = root.has("group") ? GsonHelper.getAsString(root, "group") : null;

        // Show notification
        boolean showNotification = GsonHelper.getAsBoolean(root, "show_notification", true);

        // Parse based on type
        List<IngredientData> ingredients;
        String[] pattern = null;
        Map<Character, IngredientData> keyMap = null;

        if (craftingType == CraftingType.SHAPED) {
            // Parse pattern
            JsonArray patternArray = GsonHelper.getAsJsonArray(root, "pattern");
            pattern = new String[patternArray.size()];
            for (int i = 0; i < patternArray.size(); i++) {
                pattern[i] = patternArray.get(i).getAsString();
            }

            // Parse key
            JsonObject keyObj = GsonHelper.getAsJsonObject(root, "key");
            keyMap = new HashMap<>();
            ingredients = new ArrayList<>();

            for (Map.Entry<String, JsonElement> entry : keyObj.entrySet()) {
                if (entry.getKey().isEmpty()) continue;
                char c = entry.getKey().charAt(0);
                IngredientData ing = IngredientData.fromJson(entry.getValue());
                keyMap.put(c, ing);
                if (!ingredients.contains(ing)) {
                    ingredients.add(ing);
                }
            }
        } else {
            // Shapeless - parse ingredients array
            JsonArray ingArray = GsonHelper.getAsJsonArray(root, "ingredients");
            ingredients = new ArrayList<>();
            for (JsonElement elem : ingArray) {
                ingredients.add(IngredientData.fromJson(elem));
            }
        }

        // Parse result
        ResultData result = ResultData.fromJson(Objects.requireNonNull(GsonHelper.getAsJsonObject(root, "result"), "result"));

        return new CraftingRecipeData(
            safeId, craftingType, category, group,
            List.copyOf(ingredients), pattern, keyMap, result,
            showNotification, false, null
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // SERIALIZATION
    // ═══════════════════════════════════════════════════════════════

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();

        json.addProperty("type", getMinecraftType());

        if (category != RecipeCategory.MISC) {
            json.addProperty("category", category.getId());
        }

        if (group != null && !group.isEmpty()) {
            json.addProperty("group", group);
        }

        if (craftingType == CraftingType.SHAPED) {
            serializeShaped(json);
        } else {
            serializeShapeless(json);
        }

        json.add("result", result.toJson());

        if (!showNotification) {
            json.addProperty("show_notification", false);
        }

        return json;
    }

    private void serializeShaped(JsonObject json) {
        // Pattern
        JsonArray patternArray = new JsonArray();
        if (pattern != null) {
            for (String row : pattern) {
                patternArray.add(row != null ? row : "   ");
            }
        }
        json.add("pattern", patternArray);

        // Key
        JsonObject keyObj = new JsonObject();
        if (keyMap != null) {
            for (Map.Entry<Character, IngredientData> entry : keyMap.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                    keyObj.add(String.valueOf(entry.getKey()), entry.getValue().toJson());
                }
            }
        }
        json.add("key", keyObj);
    }

    private void serializeShapeless(JsonObject json) {
        JsonArray ingArray = new JsonArray();
        for (IngredientData ing : ingredients) {
            if (ing != null && !ing.isEmpty()) {
                ingArray.add(ing.toJson());
            }
        }
        json.add("ingredients", ingArray);
    }

    @Override
    public String getMinecraftType() {
        return craftingType.getMinecraftType();
    }

    // ═══════════════════════════════════════════════════════════════
    // GRID OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get ingredient at grid position (for shaped recipes).
     * @param row 0-2
     * @param col 0-2
     * @return Ingredient at position, or empty
     */
    public IngredientData getIngredientAt(int row, int col) {
        if (craftingType != CraftingType.SHAPED || pattern == null || keyMap == null) {
            return IngredientData.empty();
        }

        if (row < 0 || row >= pattern.length) return IngredientData.empty();
        String rowStr = pattern[row];
        if (rowStr == null || col < 0 || col >= rowStr.length()) return IngredientData.empty();

        char c = rowStr.charAt(col);
        if (c == ' ') return IngredientData.empty();

        return keyMap.getOrDefault(c, IngredientData.empty());
    }

    /**
     * Get all ingredients as a 9-element list (3x3 grid, row-major).
     */
    public List<IngredientData> getGridIngredients() {
        List<IngredientData> grid = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            grid.add(IngredientData.empty());
        }

        if (craftingType == CraftingType.SHAPED && pattern != null && keyMap != null) {
            for (int row = 0; row < Math.min(pattern.length, 3); row++) {
                String rowStr = pattern[row];
                if (rowStr == null) continue;
                for (int col = 0; col < Math.min(rowStr.length(), 3); col++) {
                    char c = rowStr.charAt(col);
                    if (c != ' ' && keyMap.containsKey(c)) {
                        grid.set(row * 3 + col, keyMap.get(c));
                    }
                }
            }
        } else if (craftingType == CraftingType.SHAPELESS) {
            for (int i = 0; i < Math.min(ingredients.size(), 9); i++) {
                grid.set(i, ingredients.get(i));
            }
        }

        return grid;
    }

    /**
     * Check if the recipe has any ingredients.
     */
    public boolean hasIngredients() {
        if (craftingType == CraftingType.SHAPELESS) {
            return !ingredients.isEmpty() && ingredients.stream().anyMatch(i -> !i.isEmpty());
        }
        if (keyMap != null) {
            return keyMap.values().stream().anyMatch(i -> !i.isEmpty());
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════
    // MODIFICATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Create a modified copy with new ingredients grid.
     */
    public CraftingRecipeData withGrid(List<IngredientData> newGrid) {
        if (craftingType == CraftingType.SHAPELESS) {
            List<IngredientData> nonEmpty = newGrid.stream()
                .filter(i -> !i.isEmpty())
                .toList();
            return new CraftingRecipeData(
                id, craftingType, category, group,
                nonEmpty, null, null, result,
                showNotification, true, originalId != null ? originalId : id
            );
        }

        // Shaped - build pattern and key
        StringBuilder[] rows = new StringBuilder[3];
        for (int i = 0; i < 3; i++) rows[i] = new StringBuilder();

        Map<IngredientData, Character> ingredientToKey = new HashMap<>();
        Map<Character, IngredientData> newKeyMap = new HashMap<>();
        char nextKey = 'A';

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int idx = row * 3 + col;
                IngredientData ing = idx < newGrid.size() ? newGrid.get(idx) : IngredientData.empty();

                if (ing.isEmpty()) {
                    rows[row].append(' ');
                } else {
                    Character key = ingredientToKey.get(ing);
                    if (key == null) {
                        key = nextKey++;
                        ingredientToKey.put(ing, key);
                        newKeyMap.put(key, ing);
                    }
                    rows[row].append(key);
                }
            }
        }

        String[] newPattern = trimPattern(rows);
        List<IngredientData> newIngredients = new ArrayList<>(newKeyMap.values());

        return new CraftingRecipeData(
            id, craftingType, category, group,
            newIngredients, newPattern, newKeyMap, result,
            showNotification, true, originalId != null ? originalId : id
        );
    }

    /**
     * Create a modified copy with new result.
     */
    public CraftingRecipeData withResult(ResultData newResult) {
        return new CraftingRecipeData(
            id, craftingType, category, group,
            ingredients, pattern, keyMap, newResult,
            showNotification, true, originalId != null ? originalId : id
        );
    }

    /**
     * Create a modified copy with different crafting type.
     */
    public CraftingRecipeData withCraftingType(CraftingType newType) {
        if (newType == craftingType) return this;

        if (newType == CraftingType.SHAPELESS) {
            // Convert to shapeless - flatten grid
            List<IngredientData> flattened = getGridIngredients().stream()
                .filter(i -> !i.isEmpty())
                .toList();
            return new CraftingRecipeData(
                id, newType, category, group,
                flattened, null, null, result,
                showNotification, true, originalId != null ? originalId : id
            );
        } else {
            // Convert to shaped - put ingredients in first slots
            List<IngredientData> grid = new ArrayList<>();
            for (int i = 0; i < 9; i++) {
                grid.add(i < ingredients.size() ? ingredients.get(i) : IngredientData.empty());
            }
            return withGrid(grid);
        }
    }

    /**
     * Create a modified copy with new category.
     */
    public CraftingRecipeData withCategory(RecipeCategory newCategory) {
        return new CraftingRecipeData(
            id, craftingType, newCategory, group,
            ingredients, pattern, keyMap, result,
            showNotification, true, originalId != null ? originalId : id
        );
    }

    /**
     * Create a modified copy with new ID.
     */
    public CraftingRecipeData withId(ResourceLocation newId) {
        return new CraftingRecipeData(
            newId, craftingType, category, group,
            ingredients, pattern, keyMap, result,
            showNotification, isModified, originalId
        );
    }

    /**
     * Create a modified copy that replaces an original recipe.
     * Sets isModified=true and originalId to the recipe being replaced.
     */
    public CraftingRecipeData withOriginalId(ResourceLocation originalRecipeId) {
        return new CraftingRecipeData(
            id, craftingType, category, group,
            ingredients, pattern, keyMap, result,
            showNotification, true, originalRecipeId
        );
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Trim empty rows/cols from pattern (Minecraft auto-shrinks patterns).
     */
    private static String[] trimPattern(StringBuilder[] rows) {
        // Find bounds
        int minRow = 3, maxRow = -1;
        int minCol = 3, maxCol = -1;

        for (int r = 0; r < 3; r++) {
            String row = rows[r].toString();
            for (int c = 0; c < 3; c++) {
                if (c < row.length() && row.charAt(c) != ' ') {
                    minRow = Math.min(minRow, r);
                    maxRow = Math.max(maxRow, r);
                    minCol = Math.min(minCol, c);
                    maxCol = Math.max(maxCol, c);
                }
            }
        }

        if (maxRow < 0) {
            // Empty pattern
            return new String[]{" "};
        }

        // Extract trimmed pattern
        List<String> trimmed = new ArrayList<>();
        for (int r = minRow; r <= maxRow; r++) {
            String row = rows[r].toString();
            String sub = row.substring(minCol, Math.min(maxCol + 1, row.length()));
            trimmed.add(sub);
        }

        return trimmed.toArray(new String[0]);
    }

    @Override
    public String toString() {
        return "CraftingRecipeData{" +
            "id=" + id +
            ", type=" + craftingType +
            ", ingredients=" + (craftingType == CraftingType.SHAPED ?
                (keyMap != null ? keyMap.size() : 0) + " keys" :
                ingredients.size() + " items") +
            ", result=" + result +
            '}';
    }
}
