package com.frenkvs.devmod.ui.editor.systems;

import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.editor.components.EditorButton;
import com.frenkvs.devmod.ui.editor.core.BaseOverlay;
import com.frenkvs.devmod.ui.editor.core.ScaledCoord;
import com.frenkvs.devmod.ui.editor.core.UIConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Crafting recipe + value analysis overlay.
 * Extends BaseOverlay for consistent modal behavior with dynamic height.
 * Mirrors docs/editor-design-system/03-crafting-analysis.md (Section 2.6).
 */
public class CraftingInfoPanel extends BaseOverlay {

    public record IngredientValue(ItemStack item, int count, RarityTier rarity, int value) {}

    public record ItemValueAnalysis(List<IngredientValue> ingredients, int totalValue, RarityTier rarityTier) {}

    private record RecipeAnalysis(RecipeHolder<CraftingRecipe> recipe, ItemValueAnalysis analysis) {}

    public enum RarityTier {
        COMMON(1, 0xFF888888, "Common"),
        UNCOMMON(5, 0xFF55FF55, "Uncommon"),
        RARE(40, 0xFF5555FF, "Rare"),
        EPIC(100, 0xFFAA00AA, "Epic"),
        LEGENDARY(250, 0xFFFFAA00, "Legendary");

        public final int baseValue;
        public final int color;
        public final String displayName;

        RarityTier(int baseValue, int color, String displayName) {
            this.baseValue = baseValue;
            this.color = color;
            this.displayName = displayName;
        }
    }

    private static final Map<String, RarityTier> INGREDIENT_RARITY = new HashMap<>();

    static {
        // Common
        INGREDIENT_RARITY.put("minecraft:stick", RarityTier.COMMON);
        INGREDIENT_RARITY.put("minecraft:cobblestone", RarityTier.COMMON);
        INGREDIENT_RARITY.put("minecraft:oak_planks", RarityTier.COMMON);
        INGREDIENT_RARITY.put("minecraft:leather", RarityTier.COMMON);
        INGREDIENT_RARITY.put("minecraft:string", RarityTier.COMMON);

        // Uncommon
        INGREDIENT_RARITY.put("minecraft:iron_ingot", RarityTier.UNCOMMON);
        INGREDIENT_RARITY.put("minecraft:gold_ingot", RarityTier.UNCOMMON);
        INGREDIENT_RARITY.put("minecraft:redstone", RarityTier.UNCOMMON);
        INGREDIENT_RARITY.put("minecraft:coal", RarityTier.UNCOMMON);
        INGREDIENT_RARITY.put("minecraft:copper_ingot", RarityTier.UNCOMMON);

        // Rare
        INGREDIENT_RARITY.put("minecraft:diamond", RarityTier.RARE);
        INGREDIENT_RARITY.put("minecraft:lapis_lazuli", RarityTier.RARE);
        INGREDIENT_RARITY.put("minecraft:obsidian", RarityTier.RARE);
        INGREDIENT_RARITY.put("minecraft:blaze_rod", RarityTier.RARE);

        // Epic
        INGREDIENT_RARITY.put("minecraft:netherite_ingot", RarityTier.EPIC);
        INGREDIENT_RARITY.put("minecraft:emerald", RarityTier.EPIC);
        INGREDIENT_RARITY.put("minecraft:echo_shard", RarityTier.EPIC);

        // Legendary
        INGREDIENT_RARITY.put("minecraft:nether_star", RarityTier.LEGENDARY);
        INGREDIENT_RARITY.put("minecraft:dragon_egg", RarityTier.LEGENDARY);
        INGREDIENT_RARITY.put("minecraft:elytra", RarityTier.LEGENDARY);
    }

    private record PanelMetrics(int panelHeight, int valueHeight) {}

    private static final int PANEL_WIDTH = 300;

    private ItemStack targetItem = ItemStack.EMPTY;
    private RecipeHolder<CraftingRecipe> recipe = null;
    private List<RecipeHolder<CraftingRecipe>> recipes = List.of();
    private int selectedRecipeIndex = -1;
    private ItemValueAnalysis analysis = new ItemValueAnalysis(List.of(), 0, RarityTier.COMMON);
    // Scroll state for long ingredient lists
    private int ingredientScrollOffset = 0;
    private int ingredientMaxScroll = 0;
    private int ingredientAreaX = 0;
    private int ingredientAreaY = 0;
    private int ingredientAreaW = 0;
    private int ingredientAreaH = 0;

    // Navigation buttons using EditorButton component
    private final EditorButton prevButton = new EditorButton("prev", "<")
        .style(EditorButton.Style.GHOST)
        .size(EditorButton.Size.SMALL)
        .onClick(() -> selectRecipe(selectedRecipeIndex - 1));
    private final EditorButton nextButton = new EditorButton("next", ">")
        .style(EditorButton.Style.GHOST)
        .size(EditorButton.Size.SMALL)
        .onClick(() -> selectRecipe(selectedRecipeIndex + 1));

    // Cached metrics for current frame (set during render)
    private int cachedValueHeight = 0;

    public void show(ItemStack item) {
        this.targetItem = item.copy();
        RecipeAnalysis selected = selectBestRecipe(item);
        this.recipe = selected != null ? selected.recipe() : null;
        this.analysis = selected != null ? selected.analysis() : new ItemValueAnalysis(List.of(), 0, RarityTier.COMMON);
        this.recipes = findRecipesFor(item);
        if (selected == null) {
            this.selectedRecipeIndex = -1;
        } else {
            this.selectedRecipeIndex = Math.max(0, this.recipes.indexOf(selected.recipe()));
        }
        this.ingredientScrollOffset = 0;
        super.show(); // Use BaseOverlay's show()
    }

    // =========================================================================
    // BaseOverlay IMPLEMENTATION
    // =========================================================================

    @Override
    protected int getPanelWidth() {
        return PANEL_WIDTH;
    }

    @Override
    protected int getPanelHeight() {
        // Default height, but dynamic height is used via getPanelHeight(screenHeight)
        return 200;
    }

    @Override
    protected int getPanelHeight(int screenHeight) {
        Font font = Objects.requireNonNull(Minecraft.getInstance().font, "font cannot be null");
        int padding = ScaledCoord.scaleDim(10);
        int gridSize = ScaledCoord.scaleDim(24 * 3 + 20);
        int titleH = ScaledCoord.scaleDim(16);
        int valueTitleH = ScaledCoord.scaleDim(14);
        // computePanelMetrics returns scaled values, we need to return unscaled for BaseOverlay
        PanelMetrics metrics = computePanelMetrics(screenHeight, font, padding, gridSize, titleH, valueTitleH);
        this.cachedValueHeight = metrics.valueHeight();
        // Return unscaled height: divide scaled value by current scale
        float scale = ScaledCoord.getScale();
        return scale > 0 ? Math.round(metrics.panelHeight() / scale) : metrics.panelHeight();
    }

    @Override
    protected boolean usesDynamicHeight() {
        return true;
    }

    @Override
    protected void renderContent(GuiGraphics g, Font font, int x, int y, int panelW, int panelH,
                                  int mouseX, int mouseY) {
        Font safeFont = Objects.requireNonNull(font, "font cannot be null");
        int padding = ScaledCoord.scaleDim(10);
        int gridSize = ScaledCoord.scaleDim(24 * 3 + 20);
        int titleH = ScaledCoord.scaleDim(16);
        int valueTitleH = ScaledCoord.scaleDim(14);

        int cursorY = y + padding;
        g.drawString(safeFont, "CRAFTING RECIPE", x + padding, cursorY, UIConstants.Text.TITLE(), false);
        drawRecipeSelector(g, safeFont, x + panelW - padding - ScaledCoord.scaleDim(90), cursorY - ScaledCoord.scaleDim(2), mouseX, mouseY);
        cursorY += titleH + ScaledCoord.scaleDim(2);

        renderCraftingGrid(g, safeFont, x + padding, cursorY);
        cursorY += gridSize + padding;

        g.drawString(safeFont, "ITEM VALUE ANALYSIS", x + padding, cursorY, UIConstants.Text.TITLE(), false);
        cursorY += valueTitleH;
        // Track ingredient area for scroll handling
        this.ingredientAreaX = x + padding;
        this.ingredientAreaY = cursorY;
        this.ingredientAreaW = panelW - padding * 2;
        this.ingredientAreaH = cachedValueHeight;
        renderValueAnalysis(g, safeFont, ingredientAreaX, ingredientAreaY, cachedValueHeight, ingredientAreaW);

        // Close hint
        String closeHint = "Click outside or press ESC to close";
        g.drawString(safeFont, closeHint, x + padding, y + panelH - padding - safeFont.lineHeight, UIConstants.Text.MUTED(), false);
    }

    private void renderCraftingGrid(GuiGraphics g, Font font, int x, int y) {
        Font safeFont = Objects.requireNonNull(font, "font cannot be null");
        int cellSize = ScaledCoord.scaleDim(24);
        int gridSize = cellSize * 3;

        // Background grid
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int cellX = x + col * cellSize;
                int cellY = y + row * cellSize;

                g.fill(cellX, cellY, cellX + cellSize, cellY + cellSize, UIConstants.Background.INPUT());
                AxiomRenderer.drawBorder(g, cellX, cellY, cellSize, cellSize, UIConstants.Border.MUTED());

                ItemStack ingredient = getIngredient(row, col);
                if (!ingredient.isEmpty()) {
                    int iconPad = ScaledCoord.scaleDim(4);
                    g.renderItem(ingredient, cellX + iconPad, cellY + iconPad);
                }
            }
        }

        // Arrow
        int arrowX = x + gridSize + ScaledCoord.scaleDim(10);
        int arrowY = y + cellSize;
        g.drawString(safeFont, "→", arrowX, arrowY + ScaledCoord.scaleDim(4), UIConstants.Text.SECONDARY(), false);

        // Result
        int resultX = arrowX + ScaledCoord.scaleDim(20);
        g.renderItem(Objects.requireNonNull(targetItem, "targetItem cannot be null"), resultX, arrowY);
    }

    private ItemStack getIngredient(int row, int col) {
        if (recipe == null) return ItemStack.EMPTY;

        CraftingRecipe value = recipe.value();
        NonNullList<Ingredient> ingredients = value.getIngredients();

        int idx;
        if (value instanceof ShapedRecipe shaped) {
            int w = shaped.getWidth();
            int h = shaped.getHeight();
            if (row >= h || col >= w) return ItemStack.EMPTY;
            idx = row * w + col;
        } else {
            // shapeless: place ingredients row-major
            idx = row * 3 + col;
        }
        if (idx < 0 || idx >= ingredients.size()) return ItemStack.EMPTY;

        return resolveIngredientStack(ingredients.get(idx));
    }

    private void renderValueAnalysis(GuiGraphics g, Font font, int x, int y, int maxHeight, int areaWidth) {
        Font safeFont = Objects.requireNonNull(font, "font cannot be null");

        int lineY = y;
        g.drawString(safeFont, "Ingredients:", x, lineY, UIConstants.Text.SECONDARY(), false);
        int headerHeight = ScaledCoord.scaleDim(14);
        lineY += headerHeight;

        int ingredientLineHeight = ScaledCoord.scaleDim(12);
        int separatorBlock = ScaledCoord.scaleDim(4 + 1 + 8); // spacing + line + spacing
        int summaryHeight = separatorBlock + ScaledCoord.scaleDim(14) * 2; // total + rarity lines

        int availableForIngredients = Math.max(0, maxHeight - headerHeight - summaryHeight);
        int totalIngredients = analysis.ingredients().size();
        ingredientMaxScroll = Math.max(0, totalIngredients * ingredientLineHeight - availableForIngredients);
        ingredientScrollOffset = Math.max(0, Math.min(ingredientScrollOffset, ingredientMaxScroll));

        // Scissor to ingredients area
        int scissorX2 = x + areaWidth;
        int scissorY2 = lineY + availableForIngredients;
        g.enableScissor(x, lineY, scissorX2, scissorY2);

        int startIndex = ingredientLineHeight == 0 ? 0 : ingredientScrollOffset / ingredientLineHeight;
        int offsetY = ingredientLineHeight == 0 ? 0 : -(ingredientScrollOffset % ingredientLineHeight);

        for (int i = startIndex; i < totalIngredients && (i - startIndex) * ingredientLineHeight + offsetY < availableForIngredients; i++) {
            IngredientValue ing = analysis.ingredients().get(i);
            int rowY = lineY + (i - startIndex) * ingredientLineHeight + offsetY;
            String line = String.format("• %dx %s", ing.count(), ing.item().getHoverName().getString());
            g.drawString(safeFont, line, x + ScaledCoord.scaleDim(5), rowY, UIConstants.Text.PRIMARY(), false);

            String rarityTag = String.format("(%s)", ing.rarity().displayName);
            int tagX = x + ScaledCoord.scaleDim(150);
            g.drawString(safeFont, rarityTag, tagX, rowY, ing.rarity().color, false);

            String valueStr = String.format("+%d", ing.value());
            int valueX = x + ScaledCoord.scaleDim(230);
            g.drawString(safeFont, valueStr, valueX, rowY, UIConstants.Text.VALUE(), false);
        }

        g.disableScissor();

        lineY += availableForIngredients;

        lineY += ScaledCoord.scaleDim(4);
        g.fill(x, lineY, x + Math.min(areaWidth, ScaledCoord.scaleDim(260)), lineY + 1, UIConstants.Border.SEPARATOR());
        lineY += ScaledCoord.scaleDim(8);

        g.drawString(safeFont, "Total Value:", x, lineY, UIConstants.Text.SECONDARY(), false);
        g.drawString(safeFont, String.valueOf(analysis.totalValue()), x + ScaledCoord.scaleDim(200), lineY, UIConstants.Text.VALUE(), false);
        lineY += ScaledCoord.scaleDim(14);

        g.drawString(safeFont, "Rarity Tier:", x, lineY, UIConstants.Text.SECONDARY(), false);
        g.drawString(safeFont, analysis.rarityTier().displayName, x + ScaledCoord.scaleDim(200), lineY, analysis.rarityTier().color, false);
    }

    private List<RecipeHolder<CraftingRecipe>> findRecipesFor(ItemStack stack) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return List.of();
        }
        ItemStack target = Objects.requireNonNull(stack, "stack cannot be null");
        RecipeManager manager = level.getRecipeManager();
        var recipeType = Objects.requireNonNull(net.minecraft.world.item.crafting.RecipeType.CRAFTING);
        HolderLookup.Provider lookup = Objects.requireNonNull(level.registryAccess(), "registry access");
        return manager.getAllRecipesFor(recipeType)
            .stream()
            .filter(holder -> {
                ItemStack result = Objects.requireNonNull(holder.value().getResultItem(lookup), "recipe result cannot be null");
                return ItemStack.isSameItem(target, result);
            })
            .toList();
    }

    private ItemValueAnalysis analyzeRecipe(RecipeHolder<CraftingRecipe> recipeHolder) {
        CraftingRecipe recipe = recipeHolder.value();
        Map<ResourceLocation, IngredientValue> aggregated = new HashMap<>();
        RarityTier highest = RarityTier.COMMON;

        for (Ingredient ing : recipe.getIngredients()) {
            ItemStack ingredient = resolveIngredientStack(ing);
            if (ingredient.isEmpty()) continue;

            ResourceLocation id = Objects.requireNonNull(
                BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(ingredient.getItem(), "ingredient item")));
            RarityTier rarity = determineRarity(ingredient);

            IngredientValue existing = aggregated.get(id);
            int newCount = (existing == null ? ingredient.getCount() : existing.count() + ingredient.getCount());
            int value = rarity.baseValue * newCount;

            IngredientValue updated = new IngredientValue(ingredient, newCount, rarity, value);
            aggregated.put(id, updated);

            if (rarity.ordinal() > highest.ordinal()) {
                highest = rarity;
            }
        }

        // recompute total based on aggregated values to keep UI consistent
        int aggregatedTotal = aggregated.values().stream().mapToInt(IngredientValue::value).sum();
        return new ItemValueAnalysis(List.copyOf(aggregated.values()), aggregatedTotal, highest);
    }

    private RecipeAnalysis selectBestRecipe(ItemStack stack) {
        List<RecipeHolder<CraftingRecipe>> candidates = findRecipesFor(stack);
        RecipeAnalysis best = null;
        for (RecipeHolder<CraftingRecipe> holder : candidates) {
            ItemValueAnalysis result = analyzeRecipe(holder);
            if (best == null || result.totalValue() > best.analysis().totalValue() ||
                (result.totalValue() == best.analysis().totalValue() &&
                    result.rarityTier().ordinal() > best.analysis().rarityTier().ordinal())) {
                best = new RecipeAnalysis(holder, result);
            }
        }
        return best;
    }

    private ItemStack resolveIngredientStack(Ingredient ingredient) {
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0) return ItemStack.EMPTY;
        ItemStack stack = items[0].copy();
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (stack.getCount() <= 0) {
            stack.setCount(1);
        }
        return stack;
    }

    private RarityTier determineRarity(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(Objects.requireNonNull(stack.getItem(), "stack item"));
        if (id != null) {
            RarityTier tier = INGREDIENT_RARITY.get(id.toString());
            if (tier != null) return tier;
        }
        // Fallback by item rarity
        return switch (stack.getRarity()) {
            case COMMON -> RarityTier.COMMON;
            case UNCOMMON -> RarityTier.UNCOMMON;
            case RARE -> RarityTier.RARE;
            case EPIC -> RarityTier.EPIC;
        };
    }

    @Override
    protected boolean handleMouseClicked(double mouseX, double mouseY,
                                          int panelX, int panelY, int panelW, int panelH) {
        // Recipe selector clicks using EditorButton
        if (!recipes.isEmpty()) {
            if (prevButton.mouseClicked(mouseX, mouseY, 0)) {
                prevButton.mouseReleased(mouseX, mouseY, 0);
                return true;
            }
            if (nextButton.mouseClicked(mouseX, mouseY, 0)) {
                nextButton.mouseReleased(mouseX, mouseY, 0);
                return true;
            }
        }
        return true; // Consume click
    }

    @Override
    protected boolean handleKeyPressed(int keyCode) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT) {
            selectRecipe(selectedRecipeIndex - 1);
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) {
            selectRecipe(selectedRecipeIndex + 1);
            return true;
        }
        return true; // Consume all keys when visible
    }

    @Override
    protected boolean handleMouseScrolled(double mouseX, double mouseY, double scrollDelta,
                                           int panelX, int panelY, int panelW, int panelH) {
        if (mouseX >= ingredientAreaX && mouseX <= ingredientAreaX + ingredientAreaW &&
            mouseY >= ingredientAreaY && mouseY <= ingredientAreaY + ingredientAreaH) {
            ingredientScrollOffset = Math.max(0, Math.min(ingredientMaxScroll,
                ingredientScrollOffset - (int) (scrollDelta * ScaledCoord.scaleDim(12))));
            return true;
        }
        return false;
    }

    private PanelMetrics computePanelMetrics(int screenHeight, Font font, int padding, int gridSize, int titleH, int valueTitleH) {
        Font safeFont = Objects.requireNonNull(font, "font cannot be null");
        int hintH = safeFont.lineHeight;
        int idealValueHeight = ScaledCoord.scaleDim(analysis.ingredients().size() * 12 + 50);
        int maxPanelH = screenHeight - ScaledCoord.scaleDim(20);
        int baseWithoutValue = padding + titleH + gridSize + padding + valueTitleH + padding + hintH;
        int valueHeight = Math.max(ScaledCoord.scaleDim(60), idealValueHeight);
        int panelH = baseWithoutValue + valueHeight;
        if (panelH > maxPanelH) {
            valueHeight = Math.max(ScaledCoord.scaleDim(60), maxPanelH - baseWithoutValue);
            panelH = baseWithoutValue + valueHeight;
        }
        return new PanelMetrics(panelH, valueHeight);
    }

    private void drawRecipeSelector(GuiGraphics g, Font font, int x, int y, int mouseX, int mouseY) {
        if (recipes.isEmpty()) {
            return;
        }
        Font safeFont = Objects.requireNonNull(font, "font cannot be null");
        int btnW = ScaledCoord.scaleDim(16);
        int btnH = ScaledCoord.scaleDim(14);
        int gap = ScaledCoord.scaleDim(6);
        int totalW = btnW * 2 + gap + ScaledCoord.scaleDim(50);

        int boxX = x;
        int boxY = y;
        g.fill(boxX, boxY, boxX + totalW, boxY + btnH, UIConstants.Background.INPUT());
        AxiomRenderer.drawBorder(g, boxX, boxY, totalW, btnH, UIConstants.Border.MUTED());

        // Prev button using EditorButton
        int prevX = boxX + ScaledCoord.scaleDim(2);
        prevButton.setEnabled(selectedRecipeIndex > 0);
        prevButton.render(g, prevX, boxY + ScaledCoord.scaleDim(1), btnW, btnH - ScaledCoord.scaleDim(2), mouseX, mouseY);

        // Label
        String label = (selectedRecipeIndex + 1) + "/" + recipes.size();
        int labelX = prevX + btnW + gap;
        g.drawString(safeFont, label, labelX, boxY + ScaledCoord.scaleDim(3), UIConstants.Text.SECONDARY(), false);

        // Next button using EditorButton
        int nextX = labelX + ScaledCoord.scaleDim(40);
        nextButton.setEnabled(selectedRecipeIndex < recipes.size() - 1);
        nextButton.render(g, nextX, boxY + ScaledCoord.scaleDim(1), btnW, btnH - ScaledCoord.scaleDim(2), mouseX, mouseY);
    }

    private void selectRecipe(int index) {
        if (recipes.isEmpty()) return;
        int newIndex = Math.max(0, Math.min(recipes.size() - 1, index));
        if (newIndex == selectedRecipeIndex) return;
        RecipeHolder<CraftingRecipe> newRecipe = recipes.get(newIndex);
        this.recipe = newRecipe;
        this.analysis = analyzeRecipe(newRecipe);
        this.selectedRecipeIndex = newIndex;
    }
}
