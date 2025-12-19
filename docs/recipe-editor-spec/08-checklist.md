# Implementation Checklist - Recipe Editor

> Checklist dettagliata per ogni milestone

---

## Milestone 1: Crafting (MVP)

### 1.1 Core Data Structures

- [ ] **RecipeData.java** - Sealed interface
  - [ ] Define `permits` clause (Crafting, Smelting, Smithing, Stonecutting)
  - [ ] Common methods: `id()`, `category()`, `group()`, `isModified()`, `originalId()`
  - [ ] `toJson()` abstract method
  - [ ] `fromJson(ResourceLocation, JsonObject)` static factory

- [ ] **CraftingRecipeData.java** - Record
  - [ ] All fields: id, craftingType, category, group, ingredients, pattern, result, showNotification, isModified, originalId
  - [ ] Validation in compact constructor
  - [ ] `empty(ItemStack)` factory method
  - [ ] `fromJson()` implementation
  - [ ] `toJson()` implementation
  - [ ] Pattern building helpers

- [ ] **IngredientData.java** - Record
  - [ ] Fields: item, tag, alternatives, componentPredicate
  - [ ] `ofItem()`, `ofTag()`, `ofAny()`, `empty()` factories
  - [ ] `isEmpty()`, `isTag()`, `isAlternatives()` queries
  - [ ] `fromJson(JsonElement)` parser
  - [ ] `toJson()` serializer
  - [ ] `getDisplayName()` for UI
  - [ ] `getDisplayStack(long tick)` for cycling display

- [ ] **ResultData.java** - Record
  - [ ] Fields: itemId, count, components
  - [ ] `of()` factory methods
  - [ ] `isEmpty()` query
  - [ ] `toItemStack()` converter
  - [ ] `fromJson()` / `toJson()`

### 1.2 Manager & Validation

- [ ] **RecipeConfigManager.java**
  - [ ] `serverRecipes` ConcurrentHashMap
  - [ ] `clientRecipes` ConcurrentHashMap
  - [ ] `initializeServer(Path)` - create dirs, load
  - [ ] `initializeClient()` - clear client map
  - [ ] `addRecipe(RecipeData)` - add + save
  - [ ] `removeRecipe(ResourceLocation)` - remove + save
  - [ ] `getRecipe(ResourceLocation)` - Optional
  - [ ] `getAllCustomRecipes()` - List
  - [ ] `addRecipeClientOnly()` - client-side
  - [ ] `removeRecipeClientOnly()` - client-side
  - [ ] `loadServerRecipes()` - from index.json
  - [ ] `saveServerRecipes()` - to index.json
  - [ ] `exportToDatapack(String)` - datapack export
  - [ ] Test: save/load roundtrip

- [ ] **RecipeValidator.java**
  - [ ] `ValidationResult` record (valid, errors, warnings)
  - [ ] `validate(RecipeData)` main method
  - [ ] Check: ID not null/empty
  - [ ] Check: ID valid ResourceLocation format
  - [ ] Check: at least one ingredient
  - [ ] Check: result not empty
  - [ ] Check: pattern valid (if shaped)
  - [ ] Check: ingredients exist in registry
  - [ ] Warn: potential conflicts
  - [ ] Test: edge cases

- [ ] **RecipeSerializer.java**
  - [ ] `toMinecraftJson(RecipeData)` - main entry
  - [ ] `serializeShaped()` - pattern + key + result
  - [ ] `serializeShapeless()` - ingredients array
  - [ ] `buildPatternArray()` helper
  - [ ] `buildKeyObject()` helper
  - [ ] Test: export matches Minecraft format

### 1.3 Network

- [ ] **RecipeDataCodec.java**
  - [ ] `STREAM_CODEC` for single RecipeData
  - [ ] `LIST_CODEC` for List<RecipeData>
  - [ ] Type discriminator (crafting/smelting/smithing/stonecutting)
  - [ ] `encodeCrafting()` / `decodeCrafting()`
  - [ ] `encodeIngredient()` / `decodeIngredient()`
  - [ ] `encodeResult()` / `decodeResult()`
  - [ ] Test: encode/decode roundtrip

- [ ] **RecipeSyncPayload.java**
  - [ ] Record: recipes, isGlobal, operation
  - [ ] `SyncOperation` enum (ADD, UPDATE, DELETE)
  - [ ] `TYPE` and `STREAM_CODEC` constants
  - [ ] `handleOnServer()` - permission, validate, store, broadcast
  - [ ] `handleOnClient()` - apply to client storage
  - [ ] `hasPermission()` helper
  - [ ] `sendError()` helper
  - [ ] `broadcastToAll()` helper

- [ ] **NetworkHandler.java** modifications
  - [ ] Register `RecipeSyncPayload` playToServer
  - [ ] Register `RecipeSyncPayload` playToClient
  - [ ] `handleRecipeSyncServer()` handler
  - [ ] `handleRecipeSyncClient()` handler

### 1.4 UI Components

- [ ] **RecipeGridComponent.java**
  - [ ] 9 `IngredientSlot` array
  - [ ] `hoveredSlot`, `selectedSlot` tracking
  - [ ] `isDragging`, `draggedItem` state
  - [ ] `render()` - grid + slots + dragged item
  - [ ] `renderSlot()` - bg, border, item/tag icon
  - [ ] `renderTooltips()` - hover tooltips
  - [ ] `mouseClicked()` - select/clear
  - [ ] `mouseDragged()` - start drag
  - [ ] `mouseReleased()` - complete drag/swap
  - [ ] `getSlotAt()` - hit detection
  - [ ] `setSlot()`, `clearSlot()`, `clear()` setters
  - [ ] `getSlots()`, `getNonEmptyIngredients()` getters
  - [ ] `loadFromRecipe()` - load from CraftingRecipeData
  - [ ] Callbacks: `onSlotClick`, `onSlotChange`

- [ ] **IngredientSlotComponent.java**
  - [ ] Single slot rendering
  - [ ] Item display
  - [ ] Tag indicator (#)
  - [ ] Cycling animation for tags
  - [ ] Hover highlight
  - [ ] Selection highlight

- [ ] **RecipeResultSlot.java**
  - [ ] Larger slot (32x32)
  - [ ] Item display
  - [ ] Quantity badge
  - [ ] `render()` method
  - [ ] `mouseClicked()` - select item
  - [ ] `mouseScrolled()` - change quantity
  - [ ] `setItem()`, `getItem()` setters
  - [ ] `setQuantity()`, `getQuantity()` setters
  - [ ] `toResultData()` converter

### 1.5 Editor Module

- [ ] **CraftingEditorModule.java**
  - [ ] Extend `AbstractEditorModule`
  - [ ] `RecipeGridComponent` instance
  - [ ] `RecipeResultSlot` instance
  - [ ] State: currentRecipe, originalRecipe, selectedType, recipeId, group, category
  - [ ] `setupCallbacks()` - wire grid/result changes
  - [ ] `onItemSet()` - create empty recipe with item as result
  - [ ] `initializeTabs()` - Grid, Result, Settings tabs
  - [ ] Tab Grid: type toggle, recipe grid
  - [ ] Tab Result: result slot, quantity slider
  - [ ] Tab Settings: ID input, category dropdown, group input, validation
  - [ ] `buildCurrentRecipe()` - construct from current state
  - [ ] `buildPatternFromGrid()` - grid to pattern array
  - [ ] `buildPayload()` - create RecipeSyncPayload
  - [ ] `applyPreview()` - validate + store locally
  - [ ] `generateRecipeId()` helper

### 1.6 Integration

- [ ] **DevMod.java**
  - [ ] Add `RecipeConfigManager.initializeServer()` in server setup event

- [ ] **CommonModEvents.java**
  - [ ] Add `OnDatapackSyncEvent` handler
  - [ ] Sync custom recipes to joining player
  - [ ] Broadcast on datapack reload

- [ ] **DatapackIO.java**
  - [ ] Add `exportRecipes(String packName)` method
  - [ ] Create recipe directory structure
  - [ ] Export each recipe as JSON file

- [ ] **ItemEditorScreen.java**
  - [ ] Instantiate `CraftingEditorModule`
  - [ ] Add to modules list if `canEditRecipes()`
  - [ ] `canEditRecipes()` permission check

### 1.7 Localization

- [ ] **en_us.json**
  - [ ] Add all `devmod.recipe.*` keys

- [ ] **it_it.json**
  - [ ] Add all `devmod.recipe.*` translations

---

## Milestone 2: Smelting

### 2.1 Data

- [ ] **SmeltingRecipeData.java**
  - [ ] Fields: id, smeltingType, category, group, ingredient, result, experience, cookingTime, isModified, originalId
  - [ ] `SmeltingType` enum (SMELTING, BLASTING, SMOKING, CAMPFIRE)
  - [ ] `defaultCookingTime(SmeltingType)` static method
  - [ ] `fromJson()` / `toJson()`

- [ ] **RecipeSerializer.java** extension
  - [ ] `serializeSmelting()` method
  - [ ] Handle all 4 smelting types

- [ ] **RecipeDataCodec.java** extension
  - [ ] `encodeSmelting()` / `decodeSmelting()`

### 2.2 UI

- [ ] **SmeltingEditorModule.java**
  - [ ] Type selector dropdown
  - [ ] Input slot (single ingredient)
  - [ ] Output slot (result)
  - [ ] Experience slider (0.0-10.0, step 0.1)
  - [ ] Cooking time slider (20-1200)
  - [ ] Seconds preview info
  - [ ] Auto-adjust cookingTime on type change

---

## Milestone 3: Smithing & Stonecutting

### 3.1 Data

- [ ] **SmithingRecipeData.java**
  - [ ] Fields: id, smithingType, template, base, addition, result, isModified, originalId
  - [ ] `SmithingType` enum (TRANSFORM, TRIM)
  - [ ] `fromJson()` / `toJson()`
  - [ ] Note: result is null for TRIM

- [ ] **StonecuttingRecipeData.java**
  - [ ] Fields: id, ingredient, result, isModified, originalId
  - [ ] `fromJson()` / `toJson()`

- [ ] **RecipeSerializer.java** extension
  - [ ] `serializeSmithingTransform()`
  - [ ] `serializeSmithingTrim()`
  - [ ] `serializeStonecutting()`

- [ ] **RecipeDataCodec.java** extension
  - [ ] `encodeSmithing()` / `decodeSmithing()`
  - [ ] `encodeStonecutting()` / `decodeStonecutting()`

### 3.2 UI

- [ ] **SmithingEditorModule.java**
  - [ ] Type toggle (Transform/Trim)
  - [ ] Template slot
  - [ ] Base slot
  - [ ] Addition slot
  - [ ] Result slot (only for Transform)
  - [ ] Info: "Result inherits components"

- [ ] **StonecuttingEditorModule.java**
  - [ ] Input slot
  - [ ] Output slot
  - [ ] Simple 2-slot layout

---

## Milestone 4: Advanced

### 4.1 Tag Selector

- [ ] **TagSelectorPopup.java**
  - [ ] Browse available tags
  - [ ] Filter by namespace
  - [ ] Preview items in tag
  - [ ] Common tags shortcuts
  - [ ] Selection callback

### 4.2 NeoForge Ingredients

- [ ] Compound ingredient support in IngredientData
- [ ] Difference ingredient support
- [ ] Intersection ingredient support
- [ ] DataComponent ingredient support
- [ ] UI for compound/difference creation

### 4.3 Recipe Priorities

- [ ] Priority field in RecipeData
- [ ] Priority slider in UI
- [ ] `exportPriorities()` in RecipeConfigManager
- [ ] Conflict detection enhancement

### 4.4 Bulk Operations

- [ ] Import recipes from JSON file
- [ ] Export selected recipes
- [ ] Backup/restore functionality
- [ ] Batch validation

### 4.5 Search

- [ ] Recipe list/browser UI
  - [ ] Filter by type
  - [ ] Filter by namespace
  - [ ] Search by name
  - [ ] Sort options

---

## Testing Checklist

### Unit Tests

- [ ] RecipeData serialization roundtrip
- [ ] IngredientData all formats
- [ ] ResultData with/without components
- [ ] RecipeValidator edge cases
- [ ] RecipeDataCodec encode/decode

### Integration Tests

- [ ] Create crafting recipe flow
- [ ] Network sync client ↔ server
- [ ] Datapack export validity
- [ ] Server restart persistence
- [ ] Permission checks

### Manual Tests

- [ ] Grid drag-drop
- [ ] Tag cycling animation
- [ ] Shaped/shapeless toggle
- [ ] All smelting types
- [ ] Smithing transform/trim
- [ ] Stonecutting
- [ ] Multiplayer sync
- [ ] Export to datapack
- [ ] Load in vanilla

---

## Definition of Done

### Per Task
- [ ] Code implemented
- [ ] No compiler errors
- [ ] Basic testing done
- [ ] Matches existing code style

### Per Milestone
- [ ] All tasks complete
- [ ] Integration tested
- [ ] Manual testing passed
- [ ] Localization added
- [ ] Documentation updated
