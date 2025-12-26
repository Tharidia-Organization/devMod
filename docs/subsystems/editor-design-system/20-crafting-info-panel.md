# Crafting Info Panel

> **STATUS:** ✅ **Implementato** in `ui/editor/systems/CraftingInfoPanel.java`. Estende `BaseOverlay` per comportamento modale consistente.

## Layout

```
┌────────────────────────────────────────┐
│  CRAFTING RECIPE            [< 1/3 >]  │  ← Recipe selector (se multiple)
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
│  Ingredients:                          │  ← Scrollable se > 5 ingredienti
│   • 2x Diamond      (Rare)    +80      │
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
| Width | 300px (PANEL_WIDTH) |
| Height | Dinamico (basato su screen height e numero ingredienti) |
| Min Value Height | 60px |
| Trigger | `showCraftingPanel` toggle in ItemEditorScreen |
| Position | Centered overlay (via BaseOverlay) |
| Base Class | `BaseOverlay` |

## Architettura

```
CraftingInfoPanel extends BaseOverlay
├── Records
│   ├── IngredientValue(item, count, rarity, value)
│   ├── ItemValueAnalysis(ingredients, totalValue, rarityTier)
│   └── RecipeAnalysis(recipe, analysis) [interno]
├── Enum
│   └── RarityTier(COMMON, UNCOMMON, RARE, EPIC, LEGENDARY)
├── UI Components
│   ├── prevButton: EditorButton (recipe navigation)
│   └── nextButton: EditorButton (recipe navigation)
└── State
    ├── targetItem: ItemStack
    ├── recipe: RecipeHolder<CraftingRecipe>
    ├── recipes: List<RecipeHolder> (tutte le ricette disponibili)
    ├── selectedRecipeIndex: int
    ├── analysis: ItemValueAnalysis
    └── ingredientScrollOffset: int
```

## Data Types

```java
public record IngredientValue(
    ItemStack item,
    int count,
    RarityTier rarity,
    int value
) {}

public record ItemValueAnalysis(
    List<IngredientValue> ingredients,
    int totalValue,
    RarityTier rarityTier
) {}

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

## Features

### Multi-Recipe Support

Se un item ha più ricette di crafting disponibili, il pannello mostra un selettore:

```java
// Navigation buttons using EditorButton component
private final EditorButton prevButton = new EditorButton("prev", "<")
    .style(EditorButton.Style.GHOST)
    .size(EditorButton.Size.SMALL)
    .onClick(() -> selectRecipe(selectedRecipeIndex - 1));

private final EditorButton nextButton = new EditorButton("next", ">")
    .style(EditorButton.Style.GHOST)
    .size(EditorButton.Size.SMALL)
    .onClick(() -> selectRecipe(selectedRecipeIndex + 1));
```

**Keyboard shortcuts:** `←` / `→` per navigare tra ricette.

### Best Recipe Selection

Alla prima apertura, seleziona automaticamente la ricetta "migliore" basandosi su:
1. Valore totale più alto
2. A parità di valore, rarità più alta

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

### Ingredient Aggregation

Gli ingredienti identici vengono aggregati per ResourceLocation:

```java
Map<ResourceLocation, IngredientValue> aggregated = new HashMap<>();
for (Ingredient ing : recipe.getIngredients()) {
    ItemStack ingredient = resolveIngredientStack(ing);
    ResourceLocation id = BuiltInRegistries.ITEM.getKey(ingredient.getItem());

    IngredientValue existing = aggregated.get(id);
    int newCount = (existing == null ? 1 : existing.count() + 1);
    int value = rarity.baseValue * newCount;

    aggregated.put(id, new IngredientValue(ingredient, newCount, rarity, value));
}
```

### Dynamic Height

L'altezza del pannello si adatta automaticamente:

```java
@Override
protected int getPanelHeight(int screenHeight) {
    // Calcola altezza ideale basata su numero ingredienti
    int idealValueHeight = analysis.ingredients().size() * INGREDIENT_LINE_HEIGHT + VALUE_BASE_HEIGHT;
    int maxPanelH = screenHeight - PANEL_SCREEN_MARGIN;

    // Limita se troppo alto per lo schermo
    int valueHeight = Math.max(VALUE_MIN_HEIGHT, idealValueHeight);
    if (panelH > maxPanelH) {
        valueHeight = Math.max(VALUE_MIN_HEIGHT, maxPanelH - baseWithoutValue);
    }
    return panelH;
}
```

### Ingredient Scrolling

Se la lista ingredienti è troppo lunga, supporta scroll con mouse wheel:

```java
@Override
protected boolean handleMouseScrolled(double mouseX, double mouseY, double scrollDelta,
                                       int panelX, int panelY, int panelW, int panelH) {
    if (mouseX >= ingredientAreaX && mouseX <= ingredientAreaX + ingredientAreaW &&
        mouseY >= ingredientAreaY && mouseY <= ingredientAreaY + ingredientAreaH) {
        ingredientScrollOffset = Math.max(0, Math.min(ingredientMaxScroll,
            ingredientScrollOffset - (int) (scrollDelta * INGREDIENT_LINE_HEIGHT)));
        return true;
    }
    return false;
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

**Fallback:** Se un item non è nella mappa, usa `stack.getRarity()` di Minecraft.

## Integrazione in ItemEditorScreen

```java
// In ItemEditorScreen.java
private boolean showCraftingPanel = false;
private final CraftingInfoPanel craftingPanel = new CraftingInfoPanel();

// Toggle
public void toggleCraftingPanel() {
    if (showCraftingPanel) {
        craftingPanel.hide();
    } else {
        craftingPanel.show(getActiveItem());
    }
    showCraftingPanel = !showCraftingPanel;
}

// In render()
if (showCraftingPanel) {
    craftingPanel.render(graphics, font, width, height, mouseX, mouseY);
}
```

## Layout Constants

```java
private static final int PANEL_WIDTH = 300;
private static final int PANEL_PADDING = 10;
private static final int PANEL_SCREEN_MARGIN = 20;

private static final int GRID_CELL_SIZE = 24;
private static final int GRID_ROWS = 3;
private static final int GRID_COLS = 3;

private static final int INGREDIENT_LINE_HEIGHT = 12;
private static final int VALUE_MIN_HEIGHT = 60;
private static final int VALUE_BASE_HEIGHT = 50;
```

---

## Implementation Status (2025-01)

| Component | Status |
|-----------|--------|
| `CraftingInfoPanel` class | ✅ Implemented |
| `BaseOverlay` extension | ✅ Implemented |
| `RarityTier` enum | ✅ Implemented |
| `ItemValueAnalysis` record | ✅ Implemented |
| `IngredientValue` record | ✅ Implemented |
| Multi-recipe selector | ✅ Implemented |
| Best recipe selection | ✅ Implemented |
| Ingredient aggregation | ✅ Implemented |
| Dynamic height | ✅ Implemented |
| Ingredient scrolling | ✅ Implemented |
| Keyboard navigation | ✅ Implemented (←/→) |
| Rarity map | ✅ Implemented |
| Fallback rarity | ✅ Implemented |

---

**Riferimenti:**
- [03-crafting-analysis.md](03-crafting-analysis.md) - Dettagli analisi crafting
- [BaseOverlay](../../../src/main/java/com/devmod/client/ui/editor/core/BaseOverlay.java) - Classe base overlay
