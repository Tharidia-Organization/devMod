package com.devmod.client.ui.editor.systems;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ClientActionContexts;
import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.BaseOverlay;
import com.devmod.client.ui.editor.core.ScaledCoord;
import com.devmod.client.ui.editor.core.UIConstants;

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
    private static final int PANEL_HEIGHT = 200;
    private static final int PANEL_PADDING = 10;
    private static final int PANEL_SCREEN_MARGIN = 20;

    private static final int GRID_CELL_SIZE = 24;
    private static final int GRID_ROWS = 3;
    private static final int GRID_COLS = 3;
    private static final int GRID_SECTION_GAP = 20;
    private static final int GRID_BLOCK_HEIGHT = GRID_CELL_SIZE * GRID_ROWS + GRID_SECTION_GAP;
    private static final int GRID_ARROW_OFFSET = 10;
    private static final int GRID_RESULT_OFFSET = 20;

    private static final int RECIPE_TITLE_HEIGHT = 16;
    private static final int VALUE_TITLE_HEIGHT = 14;
    private static final int TITLE_GAP = 2;

    private static final int RECIPE_SELECTOR_WIDTH = 90;
    private static final int RECIPE_SELECTOR_Y_OFFSET = 2;
    private static final int RECIPE_SELECTOR_BTN_WIDTH = 16;
    private static final int RECIPE_SELECTOR_BTN_HEIGHT = 14;
    private static final int RECIPE_SELECTOR_BTN_X_PAD = 2;
    private static final int RECIPE_SELECTOR_BTN_Y_PAD = 1;
    private static final int RECIPE_SELECTOR_BTN_Y_TRIM = 2;
    private static final int RECIPE_SELECTOR_GAP = 6;
    private static final int RECIPE_SELECTOR_LABEL_WIDTH = 50;
    private static final int RECIPE_SELECTOR_LABEL_OFFSET = 40;
    private static final int RECIPE_SELECTOR_LABEL_Y = 3;

    private static final int INGREDIENT_HEADER_HEIGHT = 14;
    private static final int INGREDIENT_LINE_HEIGHT = 12;
    private static final int INGREDIENT_TEXT_OFFSET = 5;
    private static final int INGREDIENT_TAG_OFFSET = 150;
    private static final int INGREDIENT_VALUE_OFFSET = 230;

    private static final int SEPARATOR_BLOCK_HEIGHT = UIConstants.Spacing.SM + UIConstants.Spacing.MD + 1;
    private static final int SUMMARY_SEPARATOR_WIDTH = 260;
    private static final int SUMMARY_VALUE_OFFSET = 200;
    private static final int SUMMARY_LINE_HEIGHT = 14;
    private static final int SUMMARY_SEPARATOR_HEIGHT = 1;

    private static final int VALUE_BASE_HEIGHT = 50;
    private static final int VALUE_MIN_HEIGHT = 60;

    private static final String TITLE_RECIPE_TEXT = "CRAFTING RECIPE";
    private static final String TITLE_VALUE_TEXT = "ITEM VALUE ANALYSIS";
    private static final String CLOSE_HINT_TEXT = "Click outside or press ESC to close";
    private static final String INGREDIENTS_LABEL = "Ingredients:";
    private static final String TOTAL_VALUE_LABEL = "Total Value:";
    private static final String RARITY_LABEL = "Rarity Tier:";
    private static final String INGREDIENT_LINE_FORMAT = "• %dx %s";
    private static final String RARITY_TAG_FORMAT = "(%s)";
    private static final String VALUE_FORMAT = "+%d";
    private static final String ARROW_GLYPH = "→";

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

    // Navigation buttons using EditorButton component (lazy init to avoid this-escape)
    private EditorButton prevButton;
    private EditorButton nextButton;
    private EditorButton editButton;
    private boolean buttonsInitialized = false;

    // Cached metrics for current frame (set during render)
    private int cachedValueHeight = 0;


    public CraftingInfoPanel() {
        // Buttons initialized lazily to avoid this-escape warning
    }

    /** Ensures buttons are initialized (lazy init to avoid this-escape). */
    private void ensureButtons() {
        if (!buttonsInitialized) {
            prevButton = new EditorButton("prev", "<")
                .style(EditorButton.Style.GHOST)
                .size(EditorButton.Size.SMALL)
                .onClick(() -> selectRecipe(selectedRecipeIndex - 1));
            nextButton = new EditorButton("next", ">")
                .style(EditorButton.Style.GHOST)
                .size(EditorButton.Size.SMALL)
                .onClick(() -> selectRecipe(selectedRecipeIndex + 1));
            editButton = new EditorButton("edit", "Edit Recipe")
                .style(EditorButton.Style.PRIMARY)
                .size(EditorButton.Size.SMALL)
                .onClick(() -> openRecipeEditor());
            buttonsInitialized = true;
        }
    }

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
        return PANEL_HEIGHT;
    }

    @Override
    protected int getPanelHeight(int screenHeight) {
        Font font = Objects.requireNonNull(Minecraft.getInstance().font, "font cannot be null");
        int padding = ScaledCoord.scaleDim(PANEL_PADDING);
        int gridSize = ScaledCoord.scaleDim(GRID_BLOCK_HEIGHT);
        int titleH = ScaledCoord.scaleDim(RECIPE_TITLE_HEIGHT);
        int valueTitleH = ScaledCoord.scaleDim(VALUE_TITLE_HEIGHT);
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
        ensureButtons();
        Font safeFont = Objects.requireNonNull(font, "font cannot be null");
        int padding = ScaledCoord.scaleDim(PANEL_PADDING);
        int gridSize = ScaledCoord.scaleDim(GRID_BLOCK_HEIGHT);
        int titleH = ScaledCoord.scaleDim(RECIPE_TITLE_HEIGHT);
        int valueTitleH = ScaledCoord.scaleDim(VALUE_TITLE_HEIGHT);

        int cursorY = y + padding;
        g.drawString(safeFont, TITLE_RECIPE_TEXT, x + padding, cursorY, UIConstants.Text.TITLE(), false);
        drawRecipeSelector(g, safeFont,
            x + panelW - padding - ScaledCoord.scaleDim(RECIPE_SELECTOR_WIDTH),
            cursorY - ScaledCoord.scaleDim(RECIPE_SELECTOR_Y_OFFSET),
            mouseX, mouseY);
        cursorY += titleH + ScaledCoord.scaleDim(TITLE_GAP);

        renderCraftingGrid(g, safeFont, x + padding, cursorY);
        cursorY += gridSize + padding;

        g.drawString(safeFont, TITLE_VALUE_TEXT, x + padding, cursorY, UIConstants.Text.TITLE(), false);
        cursorY += valueTitleH;
        // Track ingredient area for scroll handling
        this.ingredientAreaX = x + padding;
        this.ingredientAreaY = cursorY;
        this.ingredientAreaW = panelW - padding * 2;
        this.ingredientAreaH = cachedValueHeight;
        renderValueAnalysis(g, safeFont, ingredientAreaX, ingredientAreaY, cachedValueHeight, ingredientAreaW);

        // Close hint at bottom left, Edit button at bottom right
        int bottomY = y + panelH - padding - safeFont.lineHeight;
        g.drawString(safeFont, CLOSE_HINT_TEXT, x + padding, bottomY, UIConstants.Text.MUTED(), false);

        // Edit Recipe button - positioned at bottom right, same line as close hint
        int editBtnW = ScaledCoord.scaleDim(70);
        int editBtnH = ScaledCoord.scaleDim(18);
        int editBtnX = x + panelW - padding - editBtnW;
        int editBtnY = bottomY - ScaledCoord.scaleDim(2); // Align with close hint text
        editButton.render(g, editBtnX, editBtnY, editBtnW, editBtnH, mouseX, mouseY);
    }

    private void renderCraftingGrid(GuiGraphics g, Font font, int x, int y) {
        Font safeFont = Objects.requireNonNull(font, "font cannot be null");
        int cellSize = ScaledCoord.scaleDim(GRID_CELL_SIZE);
        int gridSize = cellSize * GRID_COLS;

        // Background grid
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int cellX = x + col * cellSize;
                int cellY = y + row * cellSize;

                g.fill(cellX, cellY, cellX + cellSize, cellY + cellSize, UIConstants.Background.INPUT());
                AxiomRenderer.drawBorder(g, cellX, cellY, cellSize, cellSize, UIConstants.Border.MUTED());

                ItemStack ingredient = getIngredient(row, col);
                if (!ingredient.isEmpty()) {
                    int iconPad = ScaledCoord.scaleDim(UIConstants.Spacing.SM);
                    g.renderItem(ingredient, cellX + iconPad, cellY + iconPad);
                }
            }
        }

        // Arrow
        int arrowX = x + gridSize + ScaledCoord.scaleDim(GRID_ARROW_OFFSET);
        int arrowY = y + cellSize;
        g.drawString(safeFont, ARROW_GLYPH, arrowX, arrowY + ScaledCoord.scaleDim(UIConstants.Spacing.SM),
            UIConstants.Text.SECONDARY(), false);

        // Result
        int resultX = arrowX + ScaledCoord.scaleDim(GRID_RESULT_OFFSET);
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
            idx = row * GRID_COLS + col;
        }
        if (idx < 0 || idx >= ingredients.size()) return ItemStack.EMPTY;

        return resolveIngredientStack(ingredients.get(idx));
    }

    private void renderValueAnalysis(GuiGraphics g, Font font, int x, int y, int maxHeight, int areaWidth) {
        Font safeFont = Objects.requireNonNull(font, "font cannot be null");

        int lineY = y;
        g.drawString(safeFont, INGREDIENTS_LABEL, x, lineY, UIConstants.Text.SECONDARY(), false);
        int headerHeight = ScaledCoord.scaleDim(INGREDIENT_HEADER_HEIGHT);
        lineY += headerHeight;

        int ingredientLineHeight = ScaledCoord.scaleDim(INGREDIENT_LINE_HEIGHT);
        int separatorBlock = ScaledCoord.scaleDim(SEPARATOR_BLOCK_HEIGHT); // spacing + line + spacing
        int summaryHeight = separatorBlock + ScaledCoord.scaleDim(SUMMARY_LINE_HEIGHT) * 2; // total + rarity lines

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
            String line = String.format(INGREDIENT_LINE_FORMAT, ing.count(), ing.item().getHoverName().getString());
            g.drawString(safeFont, line, x + ScaledCoord.scaleDim(INGREDIENT_TEXT_OFFSET), rowY,
                UIConstants.Text.PRIMARY(), false);

            String rarityTag = String.format(RARITY_TAG_FORMAT, ing.rarity().displayName);
            int tagX = x + ScaledCoord.scaleDim(INGREDIENT_TAG_OFFSET);
            g.drawString(safeFont, rarityTag, tagX, rowY, ing.rarity().color, false);

            String valueStr = String.format(VALUE_FORMAT, ing.value());
            int valueX = x + ScaledCoord.scaleDim(INGREDIENT_VALUE_OFFSET);
            g.drawString(safeFont, valueStr, valueX, rowY, UIConstants.Text.VALUE(), false);
        }

        g.disableScissor();

        lineY += availableForIngredients;

        lineY += ScaledCoord.scaleDim(UIConstants.Spacing.SM);
        g.fill(x, lineY, x + Math.min(areaWidth, ScaledCoord.scaleDim(SUMMARY_SEPARATOR_WIDTH)),
            lineY + SUMMARY_SEPARATOR_HEIGHT, UIConstants.Border.SEPARATOR());
        lineY += ScaledCoord.scaleDim(UIConstants.Spacing.MD);

        g.drawString(safeFont, TOTAL_VALUE_LABEL, x, lineY, UIConstants.Text.SECONDARY(), false);
        g.drawString(safeFont, String.valueOf(analysis.totalValue()), x + ScaledCoord.scaleDim(SUMMARY_VALUE_OFFSET),
            lineY, UIConstants.Text.VALUE(), false);
        lineY += ScaledCoord.scaleDim(SUMMARY_LINE_HEIGHT);

        g.drawString(safeFont, RARITY_LABEL, x, lineY, UIConstants.Text.SECONDARY(), false);
        g.drawString(safeFont, analysis.rarityTier().displayName, x + ScaledCoord.scaleDim(SUMMARY_VALUE_OFFSET),
            lineY, analysis.rarityTier().color, false);
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
        // Edit button click
        if (editButton.mouseClicked(mouseX, mouseY, 0)) {
            editButton.mouseReleased(mouseX, mouseY, 0);
            return true;
        }

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
                ingredientScrollOffset - (int) (scrollDelta * ScaledCoord.scaleDim(INGREDIENT_LINE_HEIGHT))));
            return true;
        }
        return false;
    }

    private PanelMetrics computePanelMetrics(int screenHeight, Font font, int padding, int gridSize, int titleH, int valueTitleH) {
        Font safeFont = Objects.requireNonNull(font, "font cannot be null");
        int hintH = safeFont.lineHeight;
        int idealValueHeight = ScaledCoord.scaleDim(analysis.ingredients().size() * INGREDIENT_LINE_HEIGHT + VALUE_BASE_HEIGHT);
        int maxPanelH = screenHeight - ScaledCoord.scaleDim(PANEL_SCREEN_MARGIN);
        int baseWithoutValue = padding + titleH + gridSize + padding + valueTitleH + padding + hintH;
        int valueHeight = Math.max(ScaledCoord.scaleDim(VALUE_MIN_HEIGHT), idealValueHeight);
        int panelH = baseWithoutValue + valueHeight;
        if (panelH > maxPanelH) {
            valueHeight = Math.max(ScaledCoord.scaleDim(VALUE_MIN_HEIGHT), maxPanelH - baseWithoutValue);
            panelH = baseWithoutValue + valueHeight;
        }
        return new PanelMetrics(panelH, valueHeight);
    }

    private void drawRecipeSelector(GuiGraphics g, Font font, int x, int y, int mouseX, int mouseY) {
        if (recipes.isEmpty()) {
            return;
        }
        Font safeFont = Objects.requireNonNull(font, "font cannot be null");
        int btnW = ScaledCoord.scaleDim(RECIPE_SELECTOR_BTN_WIDTH);
        int btnH = ScaledCoord.scaleDim(RECIPE_SELECTOR_BTN_HEIGHT);
        int gap = ScaledCoord.scaleDim(RECIPE_SELECTOR_GAP);
        int totalW = btnW * 2 + gap + ScaledCoord.scaleDim(RECIPE_SELECTOR_LABEL_WIDTH);

        int boxX = x;
        int boxY = y;
        g.fill(boxX, boxY, boxX + totalW, boxY + btnH, UIConstants.Background.INPUT());
        AxiomRenderer.drawBorder(g, boxX, boxY, totalW, btnH, UIConstants.Border.MUTED());

        // Prev button using EditorButton
        int prevX = boxX + ScaledCoord.scaleDim(RECIPE_SELECTOR_BTN_X_PAD);
        prevButton.setEnabled(selectedRecipeIndex > 0);
        prevButton.render(g, prevX, boxY + ScaledCoord.scaleDim(RECIPE_SELECTOR_BTN_Y_PAD),
            btnW, btnH - ScaledCoord.scaleDim(RECIPE_SELECTOR_BTN_Y_TRIM), mouseX, mouseY);

        // Label
        String label = (selectedRecipeIndex + 1) + "/" + recipes.size();
        int labelX = prevX + btnW + gap;
        g.drawString(safeFont, label, labelX, boxY + ScaledCoord.scaleDim(RECIPE_SELECTOR_LABEL_Y),
            UIConstants.Text.SECONDARY(), false);

        // Next button using EditorButton
        int nextX = labelX + ScaledCoord.scaleDim(RECIPE_SELECTOR_LABEL_OFFSET);
        nextButton.setEnabled(selectedRecipeIndex < recipes.size() - 1);
        nextButton.render(g, nextX, boxY + ScaledCoord.scaleDim(RECIPE_SELECTOR_BTN_Y_PAD),
            btnW, btnH - ScaledCoord.scaleDim(RECIPE_SELECTOR_BTN_Y_TRIM), mouseX, mouseY);
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

    /**
     * Open the Recipe Editor for the current item.
     */
    private void openRecipeEditor() {
        if (targetItem.isEmpty()) return;

        // Close this panel first
        hide();

        // Open the ItemEditorScreen with Recipe tab via ActionRegistry
        ActionRegistry.invoke(ActionIds.UI_ITEM_EDITOR_OPEN_RECIPE,
            ClientActionContexts.forClient(ActionOrigin.UI, targetItem.copy()));
    }
}
