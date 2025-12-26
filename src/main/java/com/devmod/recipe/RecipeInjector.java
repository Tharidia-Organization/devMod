package com.devmod.recipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;

import com.devmod.DevMod;
public final class RecipeInjector {

    private RecipeInjector() {}

    // Shadow registry for custom recipes
    private static final Map<ResourceLocation, RecipeHolder<?>> INJECTED_RECIPES = new ConcurrentHashMap<>();

    // Recipes marked for removal (overrides)
    private static final Set<ResourceLocation> REMOVED_RECIPES = ConcurrentHashMap.newKeySet();

    // Lock for compound operations (clear + inject all)
    private static final ReadWriteLock lock = new ReentrantReadWriteLock();

    // ═══════════════════════════════════════════════════════════════
    // INJECTION API
    // ═══════════════════════════════════════════════════════════════

    /**
     * Inject all custom recipes from RecipeConfigManager.
     * This is an atomic operation - clears existing and injects all new recipes.
     *
     * @param recipeManager The RecipeManager (may be null, not used directly but kept for API compatibility)
     */
    public static void injectAll(RecipeManager recipeManager) {
        List<RecipeData> recipes = RecipeConfigManager.getAllCustomRecipes();

        lock.writeLock().lock();
        try {
            INJECTED_RECIPES.clear();

            if (recipes.isEmpty()) {
                DevMod.LOGGER.debug("[RecipeInjector] No custom recipes to inject");
                return;
            }

            int injected = 0;
            int failed = 0;

            for (RecipeData recipe : recipes) {
                if (recipe == null || recipe.id() == null) {
                    failed++;
                    continue;
                }
                if (injectRecipeUnsafe(recipe)) {
                    injected++;
                } else {
                    failed++;
                }
            }

            DevMod.LOGGER.info("[RecipeInjector] Injected {}/{} custom recipes ({} failed)",
                injected, recipes.size(), failed);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Inject a single recipe.
     *
     * @param recipeManager The RecipeManager (may be null, not used directly but kept for API compatibility)
     * @param recipe The recipe to inject
     * @return true if injection was successful
     */
    public static boolean injectSingle(RecipeManager recipeManager, RecipeData recipe) {
        if (recipe == null || recipe.id() == null) {
            DevMod.LOGGER.warn("[RecipeInjector] Cannot inject null recipe or recipe with null ID");
            return false;
        }

        lock.writeLock().lock();
        try {
            if (injectRecipeUnsafe(recipe)) {
                DevMod.LOGGER.debug("[RecipeInjector] Injected recipe: {}", recipe.id());
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Remove a single recipe by ID.
     *
     * @param recipeManager The RecipeManager (may be null, not used directly but kept for API compatibility)
     * @param recipeId The recipe ID to remove
     */
    public static void removeSingle(RecipeManager recipeManager, ResourceLocation recipeId) {
        if (recipeId == null) {
            DevMod.LOGGER.warn("[RecipeInjector] Cannot remove recipe with null ID");
            return;
        }

        lock.writeLock().lock();
        try {
            INJECTED_RECIPES.remove(recipeId);
            REMOVED_RECIPES.add(recipeId);
            DevMod.LOGGER.debug("[RecipeInjector] Marked recipe for removal: {}", recipeId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Clear all injected recipes.
     */
    public static void clear() {
        lock.writeLock().lock();
        try {
            INJECTED_RECIPES.clear();
            REMOVED_RECIPES.clear();
            DevMod.LOGGER.debug("[RecipeInjector] Cleared all injected recipes");
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // RECIPE LOOKUP (for use by mixins/events)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Get an injected recipe by ID.
     */
    public static Optional<RecipeHolder<?>> getInjectedRecipe(ResourceLocation id) {
        if (id == null) return Optional.empty();

        lock.readLock().lock();
        try {
            return Optional.ofNullable(INJECTED_RECIPES.get(id));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get all injected recipes (snapshot copy for thread safety).
     */
    public static List<RecipeHolder<?>> getAllInjectedRecipes() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(INJECTED_RECIPES.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Check if a recipe ID should be removed/overridden.
     */
    public static boolean isRemoved(ResourceLocation id) {
        if (id == null) return false;

        lock.readLock().lock();
        try {
            return REMOVED_RECIPES.contains(id);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Check if there are any recipes marked for removal.
     */
    public static boolean hasRemovedRecipes() {
        lock.readLock().lock();
        try {
            return !REMOVED_RECIPES.isEmpty();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Check if we have an injected recipe with this ID.
     */
    public static boolean hasInjectedRecipe(ResourceLocation id) {
        if (id == null) return false;

        lock.readLock().lock();
        try {
            return INJECTED_RECIPES.containsKey(id);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get injected recipes by type (snapshot copy for thread safety).
     */
    public static <T extends Recipe<?>> List<RecipeHolder<T>> getInjectedByType(RecipeType<T> type) {
        if (type == null) return List.of();

        lock.readLock().lock();
        try {
            List<RecipeHolder<T>> result = new ArrayList<>();
            for (RecipeHolder<?> holder : INJECTED_RECIPES.values()) {
                if (holder != null && holder.value() != null && holder.value().getType() == type) {
                    result.add(castHolder(holder));
                }
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Type-cast helper for RecipeHolder. Safe when caller has verified type match.
     */
    @SuppressWarnings("unchecked")
    private static <T extends Recipe<?>> RecipeHolder<T> castHolder(RecipeHolder<?> holder) {
        return (RecipeHolder<T>) holder;
    }

    /**
     * Get count of injected recipes.
     */
    public static int getInjectedCount() {
        lock.readLock().lock();
        try {
            return INJECTED_RECIPES.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // INTERNAL INJECTION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Internal injection method - MUST be called with lock.writeLock() held.
     */
    private static boolean injectRecipeUnsafe(RecipeData recipeData) {
        try {
            RecipeHolder<?> holder = createRecipeHolder(recipeData);
            if (holder != null) {
                INJECTED_RECIPES.put(recipeData.id(), holder);

                // If this recipe is a modification of an existing one, mark the original for removal
                if (recipeData.isModified() && recipeData.originalId() != null) {
                    REMOVED_RECIPES.add(recipeData.originalId());
                    DevMod.LOGGER.debug("[RecipeInjector] Marked original recipe for removal: {}",
                        recipeData.originalId());
                }

                return true;
            }
        } catch (Exception e) {
            DevMod.LOGGER.error("[RecipeInjector] Failed to inject recipe {}: {}",
                recipeData.id(), e.getMessage());
        }
        return false;
    }

    @Nullable
    private static RecipeHolder<?> createRecipeHolder(RecipeData recipeData) {
        return switch (recipeData) {
            case CraftingRecipeData crafting -> createCraftingHolder(crafting);
            case SmeltingRecipeData smelting -> createSmeltingHolder(smelting);
            case SmithingRecipeData smithing -> createSmithingHolder(smithing);
            case StonecuttingRecipeData stonecutting -> createStonecuttingHolder(stonecutting);
        };
    }

    // ═══════════════════════════════════════════════════════════════
    // CRAFTING RECIPES
    // ═══════════════════════════════════════════════════════════════

    @Nullable
    private static RecipeHolder<?> createCraftingHolder(CraftingRecipeData data) {
        try {
            Recipe<?> recipe = data.craftingType() == CraftingType.SHAPED
                ? createShapedRecipe(data)
                : createShapelessRecipe(data);

            if (recipe != null) {
                return new RecipeHolder<>(Objects.requireNonNull(data.id(), "recipeId"), recipe);
            }
        } catch (Exception e) {
            DevMod.LOGGER.error("[RecipeInjector] Failed to create crafting recipe {}: {}",
                data.id(), e.getMessage());
        }
        return null;
    }

    @Nullable
    private static ShapedRecipe createShapedRecipe(CraftingRecipeData data) {
        try {
            // Build pattern and ingredient map from grid
            List<IngredientData> grid = data.getGridIngredients();
            Map<Character, Ingredient> keyMap = new HashMap<>();
            char[][] patternChars = new char[3][3];
            char nextKey = 'A';

            // Map unique ingredients to keys
            Map<String, Character> ingredientToKey = new HashMap<>();

            DevMod.LOGGER.debug("[RecipeInjector] Creating shaped recipe {} with {} grid entries",
                data.id(), grid.size());

            for (int i = 0; i < 9; i++) {
                int row = i / 3;
                int col = i % 3;
                IngredientData ing = i < grid.size() ? grid.get(i) : IngredientData.empty();

                if (ing == null || ing.isEmpty()) {
                    patternChars[row][col] = ' ';
                } else {
                    String ingKey = ing.toString();
                    Character key = ingredientToKey.get(ingKey);
                    if (key == null) {
                        key = nextKey++;
                        ingredientToKey.put(ingKey, key);
                        Ingredient mcIng = convertIngredient(ing);
                        if (mcIng != null && !mcIng.isEmpty()) {
                            keyMap.put(key, mcIng);
                            DevMod.LOGGER.debug("[RecipeInjector] Slot {} -> key '{}' = {}",
                                i, key, ing);
                        } else {
                            DevMod.LOGGER.warn("[RecipeInjector] Slot {} has invalid ingredient: {}", i, ing);
                        }
                    }
                    patternChars[row][col] = key;
                }
            }

            // Build pattern strings, trimming empty rows/cols
            List<String> pattern = buildTrimmedPattern(patternChars);
            if (pattern.isEmpty()) {
                DevMod.LOGGER.warn("[RecipeInjector] Empty pattern for recipe {}", data.id());
                return null;
            }

            DevMod.LOGGER.debug("[RecipeInjector] Pattern for {}: {}, keyMap: {}",
                data.id(), pattern, keyMap.keySet());

            // Create shaped pattern
            ShapedRecipePattern shapedPattern = Objects.requireNonNull(ShapedRecipePattern.of(keyMap, pattern), "pattern");
            ResultData resultData = data.result();
            if (resultData == null || resultData.isEmpty()) {
                DevMod.LOGGER.warn("[RecipeInjector] Missing result for shaped recipe {}", data.id());
                return null;
            }
            ItemStack resultStack = resultData.toItemStack();
            if (resultStack.isEmpty()) {
                DevMod.LOGGER.warn("[RecipeInjector] Empty result for shaped recipe {}", data.id());
                return null;
            }
            String group = Objects.requireNonNull(data.group() != null ? data.group() : "", "group");
            CraftingBookCategory category = Objects.requireNonNull(
                toCraftingBookCategory(Objects.requireNonNull(data.category(), "category")),
                "craftingCategory"
            );

            DevMod.LOGGER.info("[RecipeInjector] Created shaped recipe {} -> {}",
                data.id(), resultStack);

            return new ShapedRecipe(
                group,
                category,
                shapedPattern,
                resultStack
            );
        } catch (Exception e) {
            DevMod.LOGGER.error("[RecipeInjector] Failed to create shaped recipe: {}", e.getMessage(), e);
            return null;
        }
    }

    @Nullable
    private static ShapelessRecipe createShapelessRecipe(CraftingRecipeData data) {
        try {
            NonNullList<Ingredient> ingredients = NonNullList.create();

            for (IngredientData ing : data.ingredients()) {
                if (ing != null && !ing.isEmpty()) {
                    Ingredient mcIng = convertIngredient(ing);
                    if (mcIng != null && !mcIng.isEmpty()) {
                        ingredients.add(mcIng);
                    }
                }
            }

            if (ingredients.isEmpty()) {
                DevMod.LOGGER.warn("[RecipeInjector] Shapeless recipe {} has no valid ingredients", data.id());
                return null;
            }

            ResultData resultData = data.result();
            if (resultData == null || resultData.isEmpty()) {
                DevMod.LOGGER.warn("[RecipeInjector] Missing result for shapeless recipe {}", data.id());
                return null;
            }
            ItemStack resultStack = resultData.toItemStack();
            if (resultStack.isEmpty()) {
                DevMod.LOGGER.warn("[RecipeInjector] Empty result for shapeless recipe {}", data.id());
                return null;
            }
            String group = Objects.requireNonNull(data.group() != null ? data.group() : "", "group");
            CraftingBookCategory category = Objects.requireNonNull(
                toCraftingBookCategory(Objects.requireNonNull(data.category(), "category")),
                "craftingCategory"
            );

            return new ShapelessRecipe(
                group,
                category,
                resultStack,
                ingredients
            );
        } catch (Exception e) {
            DevMod.LOGGER.error("[RecipeInjector] Failed to create shapeless recipe: {}", e.getMessage());
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SMELTING RECIPES
    // ═══════════════════════════════════════════════════════════════

    @Nullable
    private static RecipeHolder<?> createSmeltingHolder(SmeltingRecipeData data) {
        try {
            Ingredient ingredient = convertIngredient(data.ingredient());
            if (ingredient == null || ingredient.isEmpty()) return null;

            ResultData resultData = data.result();
            if (resultData == null || resultData.isEmpty()) {
                DevMod.LOGGER.warn("[RecipeInjector] Missing result for smelting recipe {}", data.id());
                return null;
            }
            ItemStack result = resultData.toItemStack();
            if (result.isEmpty()) return null;
            CookingBookCategory cookingCategory = Objects.requireNonNull(
                toCookingBookCategory(Objects.requireNonNull(data.category(), "category")),
                "cookingCategory"
            );
            String group = Objects.requireNonNull(data.group() != null ? data.group() : "", "group");

            AbstractCookingRecipe recipe = switch (data.smeltingType()) {
                case SMELTING -> new SmeltingRecipe(group, cookingCategory, ingredient, result,
                    data.experience(), data.cookingTime());
                case BLASTING -> new BlastingRecipe(group, cookingCategory, ingredient, result,
                    data.experience(), data.cookingTime());
                case SMOKING -> new SmokingRecipe(group, cookingCategory, ingredient, result,
                    data.experience(), data.cookingTime());
                case CAMPFIRE -> new CampfireCookingRecipe(group, cookingCategory, ingredient, result,
                    data.experience(), data.cookingTime());
            };

            return new RecipeHolder<>(Objects.requireNonNull(data.id(), "recipeId"), recipe);
        } catch (Exception e) {
            DevMod.LOGGER.error("[RecipeInjector] Failed to create smelting recipe: {}", e.getMessage());
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SMITHING RECIPES
    // ═══════════════════════════════════════════════════════════════

    @Nullable
    private static RecipeHolder<?> createSmithingHolder(SmithingRecipeData data) {
        try {
            Ingredient template = data.template() != null ? convertIngredient(data.template()) : Ingredient.EMPTY;
            Ingredient base = convertIngredient(data.base());
            Ingredient addition = data.addition() != null ? convertIngredient(data.addition()) : Ingredient.EMPTY;

            if (base == null || base.isEmpty()) return null;

            if (data.smithingType() == SmithingType.TRANSFORM) {
                ResultData resultData = data.result();
                if (resultData == null || resultData.isEmpty()) {
                    DevMod.LOGGER.warn("[RecipeInjector] Missing result for smithing recipe {}", data.id());
                    return null;
                }
                ItemStack result = resultData.toItemStack();
                if (result.isEmpty()) return null;
                SmithingTransformRecipe recipe = new SmithingTransformRecipe(
                    Objects.requireNonNull(template != null ? template : Ingredient.EMPTY, "template"),
                    Objects.requireNonNull(base, "base"),
                    Objects.requireNonNull(addition != null ? addition : Ingredient.EMPTY, "addition"),
                    result
                );
                return new RecipeHolder<>(Objects.requireNonNull(data.id(), "recipeId"), recipe);
            } else {
                // Trim recipes require armor trim patterns - not supported yet
                DevMod.LOGGER.warn("[RecipeInjector] Smithing trim recipes not yet supported: {}", data.id());
                return null;
            }
        } catch (Exception e) {
            DevMod.LOGGER.error("[RecipeInjector] Failed to create smithing recipe: {}", e.getMessage());
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // STONECUTTING RECIPES
    // ═══════════════════════════════════════════════════════════════

    @Nullable
    private static RecipeHolder<?> createStonecuttingHolder(StonecuttingRecipeData data) {
        try {
            Ingredient ingredient = convertIngredient(data.ingredient());
            if (ingredient == null || ingredient.isEmpty()) return null;

            ResultData resultData = data.result();
            if (resultData == null || resultData.isEmpty()) {
                DevMod.LOGGER.warn("[RecipeInjector] Missing result for stonecutting recipe {}", data.id());
                return null;
            }
            ItemStack result = resultData.toItemStack();
            if (result.isEmpty()) return null;
            String group = Objects.requireNonNull(data.group() != null ? data.group() : "", "group");

            StonecutterRecipe recipe = new StonecutterRecipe(
                group,
                Objects.requireNonNull(ingredient, "ingredient"),
                result
            );

            return new RecipeHolder<>(Objects.requireNonNull(data.id(), "recipeId"), recipe);
        } catch (Exception e) {
            DevMod.LOGGER.error("[RecipeInjector] Failed to create stonecutting recipe: {}", e.getMessage());
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════

    private static Ingredient convertIngredient(IngredientData data) {
        if (data == null || data.isEmpty()) return Ingredient.EMPTY;

        try {
            if (data.isItem()) {
                ResourceLocation itemId = data.item();
                var item = itemId != null ? BuiltInRegistries.ITEM.get(itemId) : null;
                if (item != null && item != Items.AIR) {
                    return Ingredient.of(Objects.requireNonNull(item, "item"));
                }
            } else if (data.isTag()) {
                var tag = data.tag();
                if (tag != null) {
                    return Ingredient.of(tag);
                }
            } else if (data.isAlternatives()) {
                List<ItemStack> stacks = new ArrayList<>();
                List<ResourceLocation> alternatives = data.alternatives();
                if (alternatives != null) {
                    for (ResourceLocation alt : alternatives) {
                        var item = BuiltInRegistries.ITEM.get(alt);
                        if (item != null && item != Items.AIR) {
                            stacks.add(new ItemStack(Objects.requireNonNull(item, "item")));
                        }
                    }
                }
                if (!stacks.isEmpty()) {
                    return Ingredient.of(Objects.requireNonNull(stacks.stream(), "stackStream"));
                }
            }
        } catch (Exception e) {
            DevMod.LOGGER.warn("[RecipeInjector] Failed to convert ingredient: {}", e.getMessage());
        }

        return Ingredient.EMPTY;
    }

    private static List<String> buildTrimmedPattern(char[][] pattern) {
        // Find bounds
        int minRow = 3, maxRow = -1, minCol = 3, maxCol = -1;

        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (pattern[r][c] != ' ') {
                    minRow = Math.min(minRow, r);
                    maxRow = Math.max(maxRow, r);
                    minCol = Math.min(minCol, c);
                    maxCol = Math.max(maxCol, c);
                }
            }
        }

        if (maxRow < 0) return List.of(); // Empty pattern

        List<String> result = new ArrayList<>();
        for (int r = minRow; r <= maxRow; r++) {
            StringBuilder sb = new StringBuilder();
            for (int c = minCol; c <= maxCol; c++) {
                sb.append(pattern[r][c]);
            }
            result.add(sb.toString());
        }

        return result;
    }

    private static CraftingBookCategory toCraftingBookCategory(RecipeCategory category) {
        return switch (category) {
            case EQUIPMENT -> CraftingBookCategory.EQUIPMENT;
            case BUILDING -> CraftingBookCategory.BUILDING;
            case REDSTONE -> CraftingBookCategory.REDSTONE;
            default -> CraftingBookCategory.MISC;
        };
    }

    private static CookingBookCategory toCookingBookCategory(RecipeCategory category) {
        return switch (category) {
            case FOOD -> CookingBookCategory.FOOD;
            case BLOCKS -> CookingBookCategory.BLOCKS;
            default -> CookingBookCategory.MISC;
        };
    }
}
