# Crafting Info Panel

> **NOTA:** Questa sezione è ora **in scope per l'iterazione corrente**: deve essere implementata (UI + logica) nei due editor. Mostra la ricetta di crafting dell'item corrente e il suo valore calcolato. Attualmente non ancora realizzata: seguire le specifiche sotto per lo sviluppo.

## Layout

```
┌────────────────────────────────────────┐
│           CRAFTING RECIPE              │
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
│   • 2x Diamond      (Rare)    +80      │
│   • 1x Stick        (Common)   +2      │
│                           ─────────    │
│  Total Value:                  82      │
│  Rarity Tier:              RARE        │
│                                        │
└────────────────────────────────────────┘
```

## Specifiche

| Proprietà | Valore |
|-----------|--------|
| Width | 300px (popup/overlay) |
| Height | Auto (based on content) |
| Trigger | Button "Recipe" in footer o Tab dedicata |
| Position | Centered overlay o integrato in tab COMPONENTS |

## Visualizzazione Ricetta (Feature A)

Mostra la griglia di crafting 3x3 con gli ingredienti posizionati:

```java
private void renderCraftingGrid(GuiGraphics graphics, int x, int y, CraftingRecipe recipe) {
    int cellSize = 24;
    int gridSize = cellSize * 3;

    // Background grid
    for (int row = 0; row < 3; row++) {
        for (int col = 0; col < 3; col++) {
            int cellX = x + col * cellSize;
            int cellY = y + row * cellSize;

            graphics.fill(cellX, cellY, cellX + cellSize, cellY + cellSize,
                UIConstants.Background.INPUT);
            AxiomRenderer.drawBorder(graphics, cellX, cellY, cellSize, cellSize,
                UIConstants.Border.MUTED);

            // Render ingredient if present
            ItemStack ingredient = recipe.getIngredient(row, col);
            if (!ingredient.isEmpty()) {
                graphics.renderItem(ingredient, cellX + 4, cellY + 4);
            }
        }
    }

    // Arrow
    int arrowX = x + gridSize + 10;
    int arrowY = y + cellSize;
    graphics.drawString(font, "→", arrowX, arrowY + 4, UIConstants.Text.SECONDARY, false);

    // Result
    int resultX = arrowX + 20;
    graphics.renderItem(recipe.getResult(), resultX, arrowY);
}
```

## Sistema di Valutazione Item (Feature C)

Calcola un valore numerico basato sulla rarità degli ingredienti:

```java
public record ItemValueAnalysis(
    List<IngredientValue> ingredients,
    int totalValue,
    RarityTier rarityTier
) {}

public record IngredientValue(
    ItemStack item,
    int count,
    RarityTier rarity,
    int value
) {}

public enum RarityTier {
    COMMON(1, 0xFF888888, "Common"),      // Dirt, Cobblestone, Stick
    UNCOMMON(5, 0xFF55FF55, "Uncommon"),  // Iron, Coal, Redstone
    RARE(40, 0xFF5555FF, "Rare"),         // Diamond, Gold, Lapis
    EPIC(100, 0xFFAA00AA, "Epic"),        // Netherite, Emerald
    LEGENDARY(250, 0xFFFFAA00, "Legendary"); // Dragon Egg, Nether Star

    final int baseValue;
    final int color;
    final String displayName;
}

// Calcolo valore
private ItemValueAnalysis analyzeItemValue(ItemStack stack) {
    Optional<CraftingRecipe> recipe = findRecipeFor(stack);
    if (recipe.isEmpty()) {
        return new ItemValueAnalysis(List.of(), 0, RarityTier.COMMON);
    }

    List<IngredientValue> ingredients = new ArrayList<>();
    int totalValue = 0;
    RarityTier highestRarity = RarityTier.COMMON;

    for (ItemStack ingredient : recipe.get().getIngredients()) {
        RarityTier rarity = determineRarity(ingredient);
        int value = rarity.baseValue * ingredient.getCount();

        ingredients.add(new IngredientValue(ingredient, ingredient.getCount(), rarity, value));
        totalValue += value;

        if (rarity.ordinal() > highestRarity.ordinal()) {
            highestRarity = rarity;
        }
    }

    return new ItemValueAnalysis(ingredients, totalValue, highestRarity);
}

// Rendering
private void renderValueAnalysis(GuiGraphics graphics, int x, int y, ItemValueAnalysis analysis) {
    graphics.drawString(font, "ITEM VALUE ANALYSIS", x, y, UIConstants.Text.TITLE, false);

    int lineY = y + 18;
    graphics.drawString(font, "Ingredients:", x, lineY, UIConstants.Text.SECONDARY, false);
    lineY += 14;

    for (IngredientValue ing : analysis.ingredients()) {
        String line = String.format("• %dx %s", ing.count(), ing.item().getHoverName().getString());
        graphics.drawString(font, line, x + 5, lineY, UIConstants.Text.PRIMARY, false);

        // Rarity tag
        String rarityTag = String.format("(%s)", ing.rarity().displayName);
        int tagX = x + 150;
        graphics.drawString(font, rarityTag, tagX, lineY, ing.rarity().color, false);

        // Value
        String valueStr = String.format("+%d", ing.value());
        int valueX = x + 230;
        graphics.drawString(font, valueStr, valueX, lineY, UIConstants.Text.VALUE, false);

        lineY += 12;
    }

    // Separator
    lineY += 4;
    graphics.fill(x, lineY, x + 260, lineY + 1, UIConstants.Border.SEPARATOR);
    lineY += 8;

    // Total
    graphics.drawString(font, "Total Value:", x, lineY, UIConstants.Text.SECONDARY, false);
    graphics.drawString(font, String.valueOf(analysis.totalValue()), x + 200, lineY,
        UIConstants.Text.VALUE, false);

    lineY += 14;
    graphics.drawString(font, "Rarity Tier:", x, lineY, UIConstants.Text.SECONDARY, false);
    graphics.drawString(font, analysis.rarityTier().displayName, x + 200, lineY,
        analysis.rarityTier().color, false);
}
```

## Integrazione negli Editor

| Editor | Posizione |
|--------|-----------|
| Weapon | Tab "COMPONENTS" o bottone "Recipe" nel footer |
| Armor | Tab "COMPONENTS" (nuova) o bottone "Recipe" nel footer |

## Mappa Rarità Ingredienti (Configurabile)

```java
private static final Map<String, RarityTier> INGREDIENT_RARITY = Map.ofEntries(
    // Common
    Map.entry("minecraft:stick", RarityTier.COMMON),
    Map.entry("minecraft:cobblestone", RarityTier.COMMON),
    Map.entry("minecraft:oak_planks", RarityTier.COMMON),
    Map.entry("minecraft:leather", RarityTier.COMMON),
    Map.entry("minecraft:string", RarityTier.COMMON),

    // Uncommon
    Map.entry("minecraft:iron_ingot", RarityTier.UNCOMMON),
    Map.entry("minecraft:gold_ingot", RarityTier.UNCOMMON),
    Map.entry("minecraft:redstone", RarityTier.UNCOMMON),
    Map.entry("minecraft:coal", RarityTier.UNCOMMON),
    Map.entry("minecraft:copper_ingot", RarityTier.UNCOMMON),

    // Rare
    Map.entry("minecraft:diamond", RarityTier.RARE),
    Map.entry("minecraft:lapis_lazuli", RarityTier.RARE),
    Map.entry("minecraft:obsidian", RarityTier.RARE),
    Map.entry("minecraft:blaze_rod", RarityTier.RARE),

    // Epic
    Map.entry("minecraft:netherite_ingot", RarityTier.EPIC),
    Map.entry("minecraft:emerald", RarityTier.EPIC),
    Map.entry("minecraft:echo_shard", RarityTier.EPIC),

    // Legendary
    Map.entry("minecraft:nether_star", RarityTier.LEGENDARY),
    Map.entry("minecraft:dragon_egg", RarityTier.LEGENDARY),
    Map.entry("minecraft:elytra", RarityTier.LEGENDARY)
);
```