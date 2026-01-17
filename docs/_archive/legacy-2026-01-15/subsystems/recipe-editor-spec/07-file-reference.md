# File Reference - Recipe Editor

> Last updated: 2025-12-26
> Status: PLANNING

> Tutti i file da creare e modificare

## Nuovi File

### Core (`recipe/`)

| File | Path | Milestone | LOC Est. |
|------|------|-----------|----------|
| RecipeData.java | `src/main/java/com/devmod/recipe/RecipeData.java` | M1 | 50 |
| CraftingRecipeData.java | `src/main/java/com/devmod/recipe/CraftingRecipeData.java` | M1 | 150 |
| SmeltingRecipeData.java | `src/main/java/com/devmod/recipe/SmeltingRecipeData.java` | M2 | 100 |
| SmithingRecipeData.java | `src/main/java/com/devmod/recipe/SmithingRecipeData.java` | M3 | 100 |
| StonecuttingRecipeData.java | `src/main/java/com/devmod/recipe/StonecuttingRecipeData.java` | M3 | 60 |
| IngredientData.java | `src/main/java/com/devmod/recipe/IngredientData.java` | M1 | 120 |
| ResultData.java | `src/main/java/com/devmod/recipe/ResultData.java` | M1 | 80 |
| RecipeConfigManager.java | `src/main/java/com/devmod/recipe/RecipeConfigManager.java` | M1 | 200 |
| RecipeSerializer.java | `src/main/java/com/devmod/recipe/RecipeSerializer.java` | M1 | 150 |
| RecipeValidator.java | `src/main/java/com/devmod/recipe/RecipeValidator.java` | M1 | 100 |
| RecipeDataCodec.java | `src/main/java/com/devmod/recipe/RecipeDataCodec.java` | M1 | 200 |

### UI Components (`ui/editor/components/`)

| File | Path | Milestone | LOC Est. |
|------|------|-----------|----------|
| RecipeGridComponent.java | `src/main/java/com/devmod/client/ui/editor/components/RecipeGridComponent.java` | M1 | 250 |
| IngredientSlotComponent.java | `src/main/java/com/devmod/client/ui/editor/components/IngredientSlotComponent.java` | M1 | 100 |
| RecipeResultSlot.java | `src/main/java/com/devmod/client/ui/editor/components/RecipeResultSlot.java` | M1 | 80 |
| TagSelectorPopup.java | `src/main/java/com/devmod/client/ui/editor/components/TagSelectorPopup.java` | M4 | 150 |

### Editor Modules (`ui/editor/modules/`)

| File | Path | Milestone | LOC Est. |
|------|------|-----------|----------|
| CraftingEditorModule.java | `src/main/java/com/devmod/client/ui/editor/modules/CraftingEditorModule.java` | M1 | 300 |
| SmeltingEditorModule.java | `src/main/java/com/devmod/client/ui/editor/modules/SmeltingEditorModule.java` | M2 | 150 |
| SmithingEditorModule.java | `src/main/java/com/devmod/client/ui/editor/modules/SmithingEditorModule.java` | M3 | 150 |
| StonecuttingEditorModule.java | `src/main/java/com/devmod/client/ui/editor/modules/StonecuttingEditorModule.java` | M3 | 100 |

### Network (`network/`)

| File | Path | Milestone | LOC Est. |
|------|------|-----------|----------|
| RecipeSyncPayload.java | `src/main/java/com/devmod/network/RecipeSyncPayload.java` | M1 | 150 |

---

## File da Modificare

### Core

| File | Path | Modifiche |
|------|------|-----------|
| DevMod.java | `src/main/java/com/devmod/DevMod.java` | `+RecipeConfigManager.initializeServer()` in server setup |
| CommonModEvents.java | `src/main/java/com/devmod/CommonModEvents.java` | `+OnDatapackSyncEvent` handler |

### Network

| File | Path | Modifiche |
|------|------|-----------|
| NetworkHandler.java | `src/main/java/com/devmod/NetworkHandler.java` | `+RecipeSyncPayload` registration (playToServer + playToClient) |

### Persistence

| File | Path | Modifiche |
|------|------|-----------|
| DatapackIO.java | `src/main/java/com/devmod/util/DatapackIO.java` | `+exportRecipes()`, `+importRecipes()` methods |

### UI Integration

| File | Path | Modifiche |
|------|------|-----------|
| ItemEditorScreen.java | `src/main/java/com/devmod/client/ui/editor/ItemEditorScreen.java` | `+RecipeModule` instantiation, `+canEditRecipes()` permission check |

### Localization

| File | Path | Modifiche |
|------|------|-----------|
| en_us.json | `src/main/resources/assets/devmod/lang/en_us.json` | `+devmod.recipe.*` translations |
| it_it.json | `src/main/resources/assets/devmod/lang/it_it.json` | `+devmod.recipe.*` translations |

---

## Struttura Directory

```
src/main/java/com/devmod/
├── recipe/                              # NEW PACKAGE
│   ├── RecipeData.java                  # Sealed interface
│   ├── CraftingRecipeData.java          # Crafting record
│   ├── SmeltingRecipeData.java          # Smelting record
│   ├── SmithingRecipeData.java          # Smithing record
│   ├── StonecuttingRecipeData.java      # Stonecutting record
│   ├── IngredientData.java              # Ingredient record
│   ├── ResultData.java                  # Result record
│   ├── RecipeConfigManager.java         # Storage singleton
│   ├── RecipeSerializer.java            # JSON serialization
│   ├── RecipeValidator.java             # Validation logic
│   └── RecipeDataCodec.java             # Network codec
│
├── network/
│   ├── ... existing ...
│   └── RecipeSyncPayload.java           # NEW
│
├── ui/editor/
│   ├── components/
│   │   ├── ... existing ...
│   │   ├── RecipeGridComponent.java     # NEW
│   │   ├── IngredientSlotComponent.java # NEW
│   │   ├── RecipeResultSlot.java        # NEW
│   │   └── TagSelectorPopup.java        # NEW (M4)
│   │
│   └── modules/
│       ├── ... existing ...
│       ├── CraftingEditorModule.java    # NEW
│       ├── SmeltingEditorModule.java    # NEW
│       ├── SmithingEditorModule.java    # NEW
│       └── StonecuttingEditorModule.java # NEW
│
└── util/
    └── DatapackIO.java                  # MODIFY (+recipes)
```

---

## Translations Keys

### English (en_us.json)

```json
{
  "devmod.recipe.editor_title": "Recipe Editor",
  "devmod.recipe.tab.grid": "Recipe",
  "devmod.recipe.tab.result": "Result",
  "devmod.recipe.tab.settings": "Settings",

  "devmod.recipe.type.shaped": "Shaped Recipe",
  "devmod.recipe.type.shapeless": "Shapeless Recipe",
  "devmod.recipe.type.smelting": "Smelting",
  "devmod.recipe.type.blasting": "Blasting",
  "devmod.recipe.type.smoking": "Smoking",
  "devmod.recipe.type.campfire": "Campfire Cooking",
  "devmod.recipe.type.smithing_transform": "Smithing Transform",
  "devmod.recipe.type.smithing_trim": "Armor Trim",
  "devmod.recipe.type.stonecutting": "Stonecutting",

  "devmod.recipe.category.equipment": "Equipment",
  "devmod.recipe.category.building": "Building",
  "devmod.recipe.category.misc": "Miscellaneous",
  "devmod.recipe.category.redstone": "Redstone",
  "devmod.recipe.category.food": "Food",
  "devmod.recipe.category.blocks": "Blocks",

  "devmod.recipe.slot.template": "Template",
  "devmod.recipe.slot.base": "Base Item",
  "devmod.recipe.slot.addition": "Addition",
  "devmod.recipe.slot.input": "Input",
  "devmod.recipe.slot.output": "Output",
  "devmod.recipe.slot.result": "Result",

  "devmod.recipe.setting.id": "Recipe ID",
  "devmod.recipe.setting.group": "Group (Optional)",
  "devmod.recipe.setting.experience": "Experience",
  "devmod.recipe.setting.cooking_time": "Cooking Time",
  "devmod.recipe.setting.quantity": "Quantity",

  "devmod.recipe.validation.valid": "Recipe is valid",
  "devmod.recipe.validation.error.no_id": "Recipe ID is required",
  "devmod.recipe.validation.error.no_ingredients": "At least one ingredient required",
  "devmod.recipe.validation.error.no_result": "Result item is required",
  "devmod.recipe.validation.error.invalid_id": "Invalid recipe ID format",
  "devmod.recipe.validation.warning.conflict": "May conflict with: %s",

  "devmod.recipe.action.save": "Save Recipe",
  "devmod.recipe.action.export": "Export to Datapack",
  "devmod.recipe.action.reset": "Reset Changes",
  "devmod.recipe.action.delete": "Delete Recipe",

  "devmod.recipe.info.shaped": "Items must match exact grid positions",
  "devmod.recipe.info.shapeless": "Items can be placed in any position",
  "devmod.recipe.info.smithing_inherit": "Result inherits enchantments from base item",
  "devmod.recipe.info.cooking_time": "%d ticks (%.1f seconds)",

  "devmod.recipe.sync.saved": "Recipe saved successfully",
  "devmod.recipe.sync.exported": "Exported %d recipes to datapack '%s'",
  "devmod.recipe.sync.error.no_permission": "No permission to modify recipes"
}
```

### Italian (it_it.json)

```json
{
  "devmod.recipe.editor_title": "Editor Ricette",
  "devmod.recipe.tab.grid": "Ricetta",
  "devmod.recipe.tab.result": "Risultato",
  "devmod.recipe.tab.settings": "Impostazioni",

  "devmod.recipe.type.shaped": "Ricetta con Forma",
  "devmod.recipe.type.shapeless": "Ricetta senza Forma",
  "devmod.recipe.type.smelting": "Fusione",
  "devmod.recipe.type.blasting": "Altoforno",
  "devmod.recipe.type.smoking": "Affumicatura",
  "devmod.recipe.type.campfire": "Cottura Falò",
  "devmod.recipe.type.smithing_transform": "Trasformazione Fabbro",
  "devmod.recipe.type.smithing_trim": "Armatura Decorata",
  "devmod.recipe.type.stonecutting": "Tagliapietra",

  "devmod.recipe.category.equipment": "Equipaggiamento",
  "devmod.recipe.category.building": "Costruzione",
  "devmod.recipe.category.misc": "Varie",
  "devmod.recipe.category.redstone": "Redstone",
  "devmod.recipe.category.food": "Cibo",
  "devmod.recipe.category.blocks": "Blocchi",

  "devmod.recipe.slot.template": "Template",
  "devmod.recipe.slot.base": "Item Base",
  "devmod.recipe.slot.addition": "Aggiunta",
  "devmod.recipe.slot.input": "Input",
  "devmod.recipe.slot.output": "Output",
  "devmod.recipe.slot.result": "Risultato",

  "devmod.recipe.setting.id": "ID Ricetta",
  "devmod.recipe.setting.group": "Gruppo (Opzionale)",
  "devmod.recipe.setting.experience": "Esperienza",
  "devmod.recipe.setting.cooking_time": "Tempo Cottura",
  "devmod.recipe.setting.quantity": "Quantità",

  "devmod.recipe.validation.valid": "Ricetta valida",
  "devmod.recipe.validation.error.no_id": "ID ricetta obbligatorio",
  "devmod.recipe.validation.error.no_ingredients": "Almeno un ingrediente richiesto",
  "devmod.recipe.validation.error.no_result": "Item risultato obbligatorio",
  "devmod.recipe.validation.error.invalid_id": "Formato ID non valido",
  "devmod.recipe.validation.warning.conflict": "Possibile conflitto con: %s",

  "devmod.recipe.action.save": "Salva Ricetta",
  "devmod.recipe.action.export": "Esporta in Datapack",
  "devmod.recipe.action.reset": "Annulla Modifiche",
  "devmod.recipe.action.delete": "Elimina Ricetta",

  "devmod.recipe.info.shaped": "Gli item devono corrispondere alle posizioni esatte",
  "devmod.recipe.info.shapeless": "Gli item possono essere in qualsiasi posizione",
  "devmod.recipe.info.smithing_inherit": "Il risultato eredita gli incantesimi dall'item base",
  "devmod.recipe.info.cooking_time": "%d tick (%.1f secondi)",

  "devmod.recipe.sync.saved": "Ricetta salvata con successo",
  "devmod.recipe.sync.exported": "Esportate %d ricette nel datapack '%s'",
  "devmod.recipe.sync.error.no_permission": "Permessi insufficienti per modificare ricette"
}
```

---

## LOC Summary

| Category | Files | Est. LOC |
|----------|-------|----------|
| Core (recipe/) | 11 | 1310 |
| UI Components | 4 | 580 |
| Editor Modules | 4 | 700 |
| Network | 1 | 150 |
| **Total New** | **20** | **2740** |
| Modifications | 6 | ~200 |
| **Grand Total** | **26** | **~2940** |
