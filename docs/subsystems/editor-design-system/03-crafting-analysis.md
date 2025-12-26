# 2.6 Crafting Info Panel *(IN SCOPE — IMPLEMENTED)*

Overlay modale centrato con visualizzazione ricetta, selezione multi-ricetta, lista ingredienti scrollabile e analisi valore. Trigger tramite bottone "Recipe" nel footer. Estende `BaseOverlay` per comportamento modale consistente.

## Layout
```
┌────────────────────────────────────────┐
│  CRAFTING RECIPE            [< 1/3 >]  │
├────────────────────────────────────────┤
│                                        │
│    ┌───┬───┬───┐                       │
│    │   │ D │   │     D = Diamond       │
│    ├───┼───┼───┤     S = Stick         │
│    │   │ D │   │                       │
│    ├───┼───┼───┤     Result: Diamond   │
│    │   │ S │   │             Sword     │
│    └───┴───┴───┘                       │
│                                        │
├────────────────────────────────────────┤
│  ITEM VALUE ANALYSIS                   │
│                                        │
│  Ingredients:                          │
│   • 2x Diamond      (Rare)    +80      │  ← Scrollabile
│   • 1x Stick        (Common)   +2      │
│                           ─────────    │
│  Total Value:                  82      │
│  Rarity Tier:              RARE        │
│                                        │
│  Click outside or press ESC to close   │
└────────────────────────────────────────┘
```

## Specifiche

| Proprietà | Valore |
|-----------|--------|
| Width | 300px |
| Height | Dinamica (basata su contenuto e schermo) |
| Trigger | Button "Recipe" in footer |
| Position | Centered overlay |
| Base class | `BaseOverlay` |

## Architettura

```java
public class CraftingInfoPanel extends BaseOverlay {

    // Records per struttura dati
    public record IngredientValue(ItemStack item, int count, RarityTier rarity, int value) {}
    public record ItemValueAnalysis(List<IngredientValue> ingredients, int totalValue, RarityTier rarityTier) {}
    private record RecipeAnalysis(RecipeHolder<CraftingRecipe> recipe, ItemValueAnalysis analysis) {}
    private record PanelMetrics(int panelHeight, int valueHeight) {}

    // Stato
    private ItemStack targetItem = ItemStack.EMPTY;
    private RecipeHolder<CraftingRecipe> recipe = null;
    private List<RecipeHolder<CraftingRecipe>> recipes = List.of();
    private int selectedRecipeIndex = -1;
    private ItemValueAnalysis analysis;

    // Scroll state per ingredienti
    private int ingredientScrollOffset = 0;
    private int ingredientMaxScroll = 0;

    // Navigation buttons (EditorButton component)
    private final EditorButton prevButton = new EditorButton("prev", "<")
        .style(EditorButton.Style.GHOST)
        .size(EditorButton.Size.SMALL)
        .onClick(() -> selectRecipe(selectedRecipeIndex - 1));
    private final EditorButton nextButton = new EditorButton("next", ">")
        .style(EditorButton.Style.GHOST)
        .size(EditorButton.Size.SMALL)
        .onClick(() -> selectRecipe(selectedRecipeIndex + 1));
}
```

## Altezza Dinamica

Il pannello usa `usesDynamicHeight()` per adattarsi allo schermo:

```java
@Override
protected boolean usesDynamicHeight() {
    return true;
}

@Override
protected int getPanelHeight(int screenHeight) {
    Font font = Minecraft.getInstance().font;
    PanelMetrics metrics = computePanelMetrics(screenHeight, font, ...);
    this.cachedValueHeight = metrics.valueHeight();
    float scale = ScaledCoord.getScale();
    return scale > 0 ? Math.round(metrics.panelHeight() / scale) : metrics.panelHeight();
}

private PanelMetrics computePanelMetrics(int screenHeight, Font font, ...) {
    int idealValueHeight = ScaledCoord.scaleDim(analysis.ingredients().size() * 12 + 50);
    int maxPanelH = screenHeight - ScaledCoord.scaleDim(20);
    // ... calcolo con clamp a maxPanelH
    return new PanelMetrics(panelH, valueHeight);
}
```

## Multi-Ricetta

Se esistono più ricette per lo stesso item:

1. `findRecipesFor(item)` trova tutte le ricette candidate
2. `selectBestRecipe(item)` sceglie quella con valore totale più alto
3. Controlli ←/→ permettono di navigare tra le ricette
4. Keyboard: frecce sinistra/destra

```java
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
```

## Sistema di Valutazione Item

### RarityTier Enum

```java
public enum RarityTier {
    COMMON(1, 0xFF888888, "Common"),      // Dirt, Cobblestone, Stick
    UNCOMMON(5, 0xFF55FF55, "Uncommon"),  // Iron, Coal, Redstone
    RARE(40, 0xFF5555FF, "Rare"),         // Diamond, Gold, Lapis
    EPIC(100, 0xFFAA00AA, "Epic"),        // Netherite, Emerald
    LEGENDARY(250, 0xFFFFAA00, "Legendary"); // Dragon Egg, Nether Star

    public final int baseValue;
    public final int color;
    public final String displayName;
}
```

### Aggregazione Ingredienti

Gli ingredienti duplicati vengono aggregati (count sommato):

```java
private ItemValueAnalysis analyzeRecipe(RecipeHolder<CraftingRecipe> recipeHolder) {
    Map<ResourceLocation, IngredientValue> aggregated = new HashMap<>();
    RarityTier highest = RarityTier.COMMON;

    for (Ingredient ing : recipe.getIngredients()) {
        ItemStack ingredient = resolveIngredientStack(ing);
        if (ingredient.isEmpty()) continue;

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(ingredient.getItem());
        RarityTier rarity = determineRarity(ingredient);

        // Aggrega se già presente
        IngredientValue existing = aggregated.get(id);
        int newCount = (existing == null ? ingredient.getCount() : existing.count() + ingredient.getCount());
        int value = rarity.baseValue * newCount;

        aggregated.put(id, new IngredientValue(ingredient, newCount, rarity, value));

        if (rarity.ordinal() > highest.ordinal()) {
            highest = rarity;
        }
    }

    int aggregatedTotal = aggregated.values().stream().mapToInt(IngredientValue::value).sum();
    return new ItemValueAnalysis(List.copyOf(aggregated.values()), aggregatedTotal, highest);
}
```

### Fallback Rarity

Se l'item non è nella mappa `INGREDIENT_RARITY`, usa la rarità vanilla:

```java
private RarityTier determineRarity(ItemStack stack) {
    ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
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
```

## Mappa Rarità Ingredienti

```java
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
```

## Visualizzazione Ricetta

Griglia 3x3 con supporto per ricette shaped e shapeless:

```java
private void renderCraftingGrid(GuiGraphics g, Font font, int x, int y) {
    int cellSize = ScaledCoord.scaleDim(24);
    int gridSize = cellSize * 3;

    // Background grid 3x3
    for (int row = 0; row < 3; row++) {
        for (int col = 0; col < 3; col++) {
            int cellX = x + col * cellSize;
            int cellY = y + row * cellSize;

            g.fill(cellX, cellY, cellX + cellSize, cellY + cellSize, UIConstants.Background.INPUT);
            AxiomRenderer.drawBorder(g, cellX, cellY, cellSize, cellSize, UIConstants.Border.MUTED);

            ItemStack ingredient = getIngredient(row, col);
            if (!ingredient.isEmpty()) {
                g.renderItem(ingredient, cellX + 4, cellY + 4);
            }
        }
    }

    // Arrow → Result
    int arrowX = x + gridSize + ScaledCoord.scaleDim(10);
    int arrowY = y + cellSize;
    g.drawString(font, "→", arrowX, arrowY + 4, UIConstants.Text.SECONDARY, false);

    int resultX = arrowX + ScaledCoord.scaleDim(20);
    g.renderItem(targetItem, resultX, arrowY);
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
        // Shapeless: place row-major
        idx = row * 3 + col;
    }

    if (idx < 0 || idx >= ingredients.size()) return ItemStack.EMPTY;
    return resolveIngredientStack(ingredients.get(idx));
}
```

## Scroll Ingredienti

Lista ingredienti scrollabile con scissor:

```java
private void renderValueAnalysis(GuiGraphics g, Font font, int x, int y, int maxHeight, int areaWidth) {
    // ... header

    int availableForIngredients = maxHeight - headerHeight - summaryHeight;
    int totalIngredients = analysis.ingredients().size();
    ingredientMaxScroll = Math.max(0, totalIngredients * ingredientLineHeight - availableForIngredients);
    ingredientScrollOffset = Math.max(0, Math.min(ingredientScrollOffset, ingredientMaxScroll));

    // Scissor per area ingredienti
    g.enableScissor(x, lineY, x + areaWidth, lineY + availableForIngredients);

    // Render solo ingredienti visibili (virtualization)
    int startIndex = ingredientScrollOffset / ingredientLineHeight;
    int offsetY = -(ingredientScrollOffset % ingredientLineHeight);

    for (int i = startIndex; i < totalIngredients && ...) {
        // Render ingredient line
    }

    g.disableScissor();

    // ... separator, total, rarity
}

@Override
protected boolean handleMouseScrolled(double mouseX, double mouseY, double scrollDelta, ...) {
    if (isInIngredientArea(mouseX, mouseY)) {
        ingredientScrollOffset = Math.max(0, Math.min(ingredientMaxScroll,
            ingredientScrollOffset - (int)(scrollDelta * ScaledCoord.scaleDim(12))));
        return true;
    }
    return false;
}
```

## Input Handling

### Mouse Click

```java
@Override
protected boolean handleMouseClicked(double mouseX, double mouseY, int panelX, int panelY, int panelW, int panelH) {
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
```

### Keyboard

```java
@Override
protected boolean handleKeyPressed(int keyCode) {
    if (keyCode == GLFW.GLFW_KEY_LEFT) {
        selectRecipe(selectedRecipeIndex - 1);
        return true;
    }
    if (keyCode == GLFW.GLFW_KEY_RIGHT) {
        selectRecipe(selectedRecipeIndex + 1);
        return true;
    }
    return true; // Consume all keys
}
```

## Integrazione

Chiamato da `ItemEditorScreen`:

```java
// In ItemEditorScreen
private final CraftingInfoPanel craftingPanel = new CraftingInfoPanel();

// Show
craftingPanel.show(item);

// Render
craftingPanel.render(graphics, font, width, height, mouseX, mouseY);

// Input
craftingPanel.mouseClicked(mouseX, mouseY, width, height);
craftingPanel.mouseScrolled(mouseX, mouseY, scrollY, width, height);
craftingPanel.keyPressed(keyCode);
```

---

## Changelog

### 2025-12-17
- Aggiornata documentazione per riflettere implementazione reale
- Documentato `extends BaseOverlay` e altezza dinamica
- Documentato sistema multi-ricetta con selezione automatica migliore
- Documentato aggregazione ingredienti duplicati
- Documentato fallback rarity via `stack.getRarity()`
- Documentato uso EditorButton per navigazione
- Documentato scroll ingredienti con scissor e virtualization
- Aggiunti esempi codice per tutti i metodi chiave
