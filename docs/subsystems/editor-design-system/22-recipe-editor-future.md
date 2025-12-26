# Recipe Editor - Piano Implementazione Completo

> **STATUS:** PIANIFICATO - Feature complessa che richiede integrazione profonda con Minecraft
>
> **SCOPE:** Sistema universale per editing di TUTTE le ricette (non solo armi/armature)

## Obiettivo

Permettere la **creazione e modifica runtime** di qualsiasi tipo di ricetta direttamente dall'editor DevMod:
- Crafting (shaped, shapeless)
- Smelting, Blasting, Smoking, Campfire
- Smithing (transform, trim)
- Stonecutting

Con persistenza via datapack e sincronizzazione multiplayer.

---

## Riferimenti Tecnici NeoForge 1.21

> **Fonti:** [NeoForge Docs - Recipes](https://docs.neoforged.net/docs/resources/server/recipes/), [Custom Recipes](https://docs.neoforged.net/docs/resources/server/recipes/custom), [Ingredients](https://docs.neoforged.net/docs/resources/server/recipes/ingredients), [Minecraft Wiki - Recipe](https://minecraft.wiki/w/Recipe)

### RecipeManager & RecipeHolder

```java
// Accesso al RecipeManager (server-side)
RecipeManager manager = serverLevel.recipeAccess();

// Le ricette sono wrappate in RecipeHolder
RecipeHolder<CraftingRecipe> holder = ...;
ResourceLocation id = holder.id();        // ID univoco ricetta
CraftingRecipe recipe = holder.value();   // Ricetta effettiva
```

### Recipe Priorities (NeoForge Feature)

NeoForge supporta priorità tra ricette con stesso output via `data/<namespace>/recipe_priorities.json`:

```json
{
  "values": {
    "mymod:better_diamond_sword": 100,
    "minecraft:diamond_sword": 0
  }
}
```

### Custom RecipeType & Serializer

Per ricette custom serve registrare:

```java
// 1. RecipeType
public static final Supplier<RecipeType<MyRecipe>> MY_RECIPE_TYPE =
    RECIPE_TYPES.register("my_recipe", RecipeType::simple);

// 2. RecipeSerializer con MapCodec + StreamCodec
public class MyRecipeSerializer implements RecipeSerializer<MyRecipe> {
    public static final MapCodec<MyRecipe> CODEC = ...;
    public static final StreamCodec<RegistryFriendlyByteBuf, MyRecipe> STREAM_CODEC = ...;

    @Override
    public MapCodec<MyRecipe> codec() { return CODEC; }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, MyRecipe> streamCodec() {
        return STREAM_CODEC;
    }
}
```

### Ingredient System (NeoForge Extensions)

NeoForge estende gli ingredienti vanilla con:

| Tipo | Descrizione |
|------|-------------|
| `CompoundIngredient` | Match se ANY child matcha |
| `DifferenceIngredient` | Match items in A ma non in B |
| `IntersectionIngredient` | Match items in BOTH A e B |
| `DataComponentIngredient` | Valida item + data components |
| `BlockTagIngredient` | Match block tags invece di item tags |

### Sync Server→Client

> ⚠️ **IMPORTANTE:** "Recipe logic should always run on the server - the server doesn't sync recipes to clients by default"

Per sincronizzare ricette custom al client:
- `OnDatapackSyncEvent` - Per sync durante datapack reload
- `RecipesReceivedEvent` - Per gestire ricette ricevute

---

## Formati JSON Ricette Minecraft 1.21

### Crafting Shaped

```json
{
  "type": "minecraft:crafting_shaped",
  "category": "equipment",
  "group": "diamond_tools",
  "show_notification": true,
  "pattern": [
    "###",
    " | ",
    " | "
  ],
  "key": {
    "#": "minecraft:diamond",
    "|": "minecraft:stick"
  },
  "result": {
    "id": "minecraft:diamond_pickaxe",
    "count": 1
  }
}
```

**Campi:**
- `category`: `"equipment"`, `"building"`, `"misc"`, `"redstone"` (default: `"misc"`)
- `group`: Raggruppamento nel recipe book (opzionale)
- `pattern`: Array 1-3 stringhe, max 3 caratteri ciascuna
- `key`: Mappa carattere → ingrediente (item ID, `#tag`, o array)
- `result.id`: **NOTA 1.21:** usa `id` non `item`

### Crafting Shapeless

```json
{
  "type": "minecraft:crafting_shapeless",
  "category": "misc",
  "ingredients": [
    "minecraft:diamond",
    "#c:ingots/iron",
    ["minecraft:gold_ingot", "minecraft:copper_ingot"]
  ],
  "result": {
    "id": "minecraft:diamond_sword",
    "count": 1
  }
}
```

**Ingredienti:**
- Stringa: `"minecraft:diamond"`
- Tag: `"#c:ingots/iron"`
- Array (OR): `["item1", "item2"]`

### Smelting / Blasting / Smoking / Campfire

```json
{
  "type": "minecraft:smelting",
  "category": "misc",
  "ingredient": "minecraft:iron_ore",
  "result": {
    "id": "minecraft:iron_ingot"
  },
  "experience": 0.7,
  "cookingtime": 200
}
```

| Tipo | cookingtime default |
|------|---------------------|
| `smelting` | 200 ticks (10s) |
| `blasting` | 100 ticks (5s) |
| `smoking` | 100 ticks (5s) |
| `campfire_cooking` | 600 ticks (30s) |

### Smithing Transform

```json
{
  "type": "minecraft:smithing_transform",
  "template": "minecraft:netherite_upgrade_smithing_template",
  "base": "minecraft:diamond_sword",
  "addition": "minecraft:netherite_ingot",
  "result": {
    "id": "minecraft:netherite_sword"
  }
}
```

> **Nota:** Il risultato **eredita i componenti** dell'item base.

### Stonecutting

```json
{
  "type": "minecraft:stonecutting",
  "ingredient": "minecraft:stone",
  "result": {
    "id": "minecraft:stone_bricks",
    "count": 1
  }
}
```

---

## Analisi Architetturale

### Infrastruttura Esistente (Riutilizzabile)

| Componente | File | Riusabile Per |
|------------|------|---------------|
| **CraftingInfoPanel** | `ui/editor/systems/CraftingInfoPanel.java` | Grid 3x3 rendering, recipe reading |
| **DatapackIO** | `util/DatapackIO.java` | Export JSON pattern, pack.mcmeta |
| **AbstractEditorModule** | `ui/editor/AbstractEditorModule.java` | Base module, dirty tracking, undo/redo |
| **SlotSelector** | `ui/editor/components/SlotSelector.java` | Item slot UI pattern |
| **GlobalConfigSyncPayload** | `network/GlobalConfigSyncPayload.java` | Network sync pattern |
| **ConfigPaths** | `util/ConfigPaths.java` | Path management |
| **EditorSection** | `ui/editor/EditorSection.java` | Section system |

### Pattern Chiave da Seguire

**1. Module System (da WeaponModule/ArmorModule)**
```
RecipeEditorModule extends AbstractEditorModule
├── Tab 1: Recipe Grid (3x3 crafting)
├── Tab 2: Result Config (output item + count)
├── Tab 3: Settings (recipe ID, type, group)
└── Tab 4: Debug/History
```

**2. Persistence Layers (da 06-persistence.md)**
```
Layer 2: GLOBAL → serverconfig/devmod/recipe_overrides.json
Layer 3: EXPORT → datapacks/<pack>/data/devmod/recipes/<id>.json
```

**3. Network Sync (da GlobalConfigSyncPayload)**
```java
RecipeSyncPayload implements CustomPacketPayload
├── List<RecipeData> recipes
├── fromCurrentConfigs() - serialize modified recipes
└── applyToClientConfigs() - apply on client
```

---

## Architettura Proposta

### Componenti Nuovi

```
src/main/java/com/devmod/
├── recipe/
│   ├── RecipeConfigManager.java      # Gestione ricette modificate (singleton)
│   ├── RecipeData.java               # Record per dati ricetta
│   ├── RecipeValidator.java          # Validazione ricette
│   └── RecipeSerializer.java         # Serializzazione JSON Minecraft format
│
├── ui/editor/
│   ├── modules/
│   │   └── RecipeModule.java         # Module per editing ricette
│   │
│   └── components/
│       ├── RecipeGridComponent.java  # Grid 3x3 interattiva con drag-drop
│       ├── IngredientSlot.java       # Singolo slot ingrediente
│       └── RecipeResultSlot.java     # Slot risultato con quantity
│
└── network/
    └── RecipeSyncPayload.java        # Payload per sync ricette
```

### Data Structures (Universali - Tutti i Tipi)

```java
// RecipeData.java - Struttura universale per TUTTI i tipi di ricetta
public sealed interface RecipeData permits
    CraftingRecipeData, SmeltingRecipeData, SmithingRecipeData, StonecuttingRecipeData {

    ResourceLocation id();
    RecipeCategory category();
    @Nullable String group();
    boolean isModified();
    @Nullable ResourceLocation originalId();

    // Factory per deserializzazione
    static RecipeData fromJson(JsonObject json) { ... }
    JsonObject toJson();
}

// Crafting (shaped + shapeless)
public record CraftingRecipeData(
    ResourceLocation id,
    CraftingType craftingType,       // SHAPED o SHAPELESS
    RecipeCategory category,         // equipment, building, misc, redstone
    @Nullable String group,
    List<IngredientData> ingredients, // 9 per shaped, 1-9 per shapeless
    @Nullable String[] pattern,       // Solo per shaped (es. ["###", " | ", " | "])
    ResultData result,
    boolean showNotification,
    boolean isModified,
    @Nullable ResourceLocation originalId
) implements RecipeData {}

// Smelting/Blasting/Smoking/Campfire
public record SmeltingRecipeData(
    ResourceLocation id,
    SmeltingType smeltingType,       // SMELTING, BLASTING, SMOKING, CAMPFIRE
    RecipeCategory category,
    @Nullable String group,
    IngredientData ingredient,        // Singolo ingrediente
    ResultData result,
    float experience,
    int cookingTime,                  // In ticks
    boolean isModified,
    @Nullable ResourceLocation originalId
) implements RecipeData {}

// Smithing (transform + trim)
public record SmithingRecipeData(
    ResourceLocation id,
    SmithingType smithingType,        // TRANSFORM o TRIM
    @Nullable IngredientData template,
    IngredientData base,
    @Nullable IngredientData addition,
    ResultData result,                // Solo per TRANSFORM
    boolean isModified,
    @Nullable ResourceLocation originalId
) implements RecipeData {}

// Stonecutting
public record StonecuttingRecipeData(
    ResourceLocation id,
    IngredientData ingredient,
    ResultData result,
    boolean isModified,
    @Nullable ResourceLocation originalId
) implements RecipeData {}

// === Supporting Types ===

public enum CraftingType { SHAPED, SHAPELESS }
public enum SmeltingType { SMELTING, BLASTING, SMOKING, CAMPFIRE }
public enum SmithingType { TRANSFORM, TRIM }
public enum RecipeCategory { EQUIPMENT, BUILDING, MISC, REDSTONE, FOOD, BLOCKS }

// Ingrediente flessibile (item, tag, o multiple options)
public record IngredientData(
    @Nullable ResourceLocation item,     // Item singolo
    @Nullable TagKey<Item> tag,          // Tag (es. c:ingots/iron)
    @Nullable List<ResourceLocation> alternatives, // OR di più item
    @Nullable DataComponentPredicate componentPredicate // Per DataComponentIngredient
) {
    public static IngredientData ofItem(ResourceLocation item) { ... }
    public static IngredientData ofTag(TagKey<Item> tag) { ... }
    public static IngredientData ofAny(List<ResourceLocation> items) { ... }

    public boolean isEmpty() { return item == null && tag == null && alternatives == null; }

    public JsonElement toJson() {
        if (tag != null) return new JsonPrimitive("#" + tag.location());
        if (alternatives != null) { /* array */ }
        return new JsonPrimitive(item.toString());
    }
}

// Risultato con quantità e componenti opzionali
public record ResultData(
    ResourceLocation itemId,
    int count,
    @Nullable CompoundTag components      // Data components opzionali
) {
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", itemId.toString());
        if (count > 1) json.addProperty("count", count);
        if (components != null) { /* serialize components */ }
        return json;
    }
}
```

### Architettura Module per Tipo

```
RecipeEditorModule (abstract base)
├── CraftingEditorModule     → Tab: Grid 3x3, Pattern, Shapeless toggle
├── SmeltingEditorModule     → Tab: Input, Output, Time slider, XP
├── SmithingEditorModule     → Tab: Template, Base, Addition, Result
└── StonecuttingEditorModule → Tab: Input, Output (simple)
```

Ogni module estende `AbstractEditorModule` e implementa l'interfaccia comune:

```java
public interface RecipeEditorModule {
    RecipeData buildRecipeData();
    void loadFromRecipe(RecipeData data);
    ValidationResult validate();
    List<String> getSupportedRecipeTypes();
}
```

---

## Implementazione Dettagliata

### Fase 1: Core Infrastructure (300-400 righe)

#### 1.1 RecipeConfigManager.java

```java
public class RecipeConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecipeConfigManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Thread-safe storage
    private static final Map<ResourceLocation, RecipeData> customRecipes = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, RecipeData> overriddenRecipes = new ConcurrentHashMap<>();

    private static Path dataDirectory = null;

    public static void initialize(Path configDir) {
        dataDirectory = configDir.resolve("devmod").resolve("recipes");
        try {
            Files.createDirectories(dataDirectory);
            load();
        } catch (Exception e) {
            LOGGER.error("[RecipeConfig] Failed to initialize", e);
        }
    }

    // CRUD operations
    public static void addRecipe(RecipeData recipe) { ... }
    public static void updateRecipe(ResourceLocation id, RecipeData recipe) { ... }
    public static void removeRecipe(ResourceLocation id) { ... }
    public static Optional<RecipeData> getRecipe(ResourceLocation id) { ... }
    public static List<RecipeData> getAllCustomRecipes() { ... }

    // Persistence
    private static void load() { ... }
    public static void save() { ... }

    // Validation
    public static ValidationResult validate(RecipeData recipe) { ... }
}
```

#### 1.2 RecipeSerializer.java

```java
public class RecipeSerializer {

    /**
     * Converte RecipeData nel formato JSON di Minecraft.
     */
    public static JsonObject toMinecraftJson(RecipeData recipe) {
        JsonObject json = new JsonObject();

        switch (recipe.type()) {
            case SHAPED_3X3, SHAPED_2X2 -> serializeShaped(json, recipe);
            case SHAPELESS -> serializeShapeless(json, recipe);
            case SMITHING_TRANSFORM -> serializeSmithing(json, recipe);
            // ... altri tipi
        }

        return json;
    }

    private static void serializeShaped(JsonObject json, RecipeData recipe) {
        json.addProperty("type", "minecraft:crafting_shaped");

        // Pattern (es. ["###", " | ", " | "])
        JsonArray pattern = buildPatternArray(recipe);
        json.add("pattern", pattern);

        // Key (es. {"#": {"item": "minecraft:diamond"}, "|": {"item": "minecraft:stick"}})
        JsonObject key = buildKeyObject(recipe);
        json.add("key", key);

        // Result
        JsonObject result = new JsonObject();
        result.addProperty("id", BuiltInRegistries.ITEM.getKey(recipe.result().getItem()).toString());
        result.addProperty("count", recipe.resultCount());
        json.add("result", result);
    }

    // ... altri metodi serializzazione
}
```

#### 1.3 RecipeValidator.java

```java
public class RecipeValidator {

    public record ValidationResult(
        boolean valid,
        List<String> errors,
        List<String> warnings
    ) {
        public static ValidationResult ok() {
            return new ValidationResult(true, List.of(), List.of());
        }

        public static ValidationResult error(String... errors) {
            return new ValidationResult(false, List.of(errors), List.of());
        }
    }

    public static ValidationResult validate(RecipeData recipe) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 1. ID valido
        if (recipe.id() == null || recipe.id().getPath().isEmpty()) {
            errors.add("Recipe ID is required");
        }

        // 2. Almeno un ingrediente
        boolean hasIngredient = recipe.pattern().stream()
            .anyMatch(slot -> !slot.item().isEmpty() || slot.tag() != null);
        if (!hasIngredient) {
            errors.add("Recipe must have at least one ingredient");
        }

        // 3. Risultato valido
        if (recipe.result().isEmpty()) {
            errors.add("Recipe must have a result item");
        }

        // 4. Check conflitti
        Optional<ResourceLocation> conflict = findConflict(recipe);
        if (conflict.isPresent()) {
            warnings.add("Recipe may conflict with: " + conflict.get());
        }

        // 5. Ingredienti validi
        for (IngredientSlot slot : recipe.pattern()) {
            if (!slot.item().isEmpty() && !isValidItem(slot.item())) {
                errors.add("Invalid ingredient at slot " + slot.index());
            }
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    private static Optional<ResourceLocation> findConflict(RecipeData recipe) {
        // Controlla se esiste già una ricetta con stesso pattern
        // ... implementazione
        return Optional.empty();
    }
}
```

---

### Fase 2: UI Components (400-500 righe)

#### 2.1 RecipeGridComponent.java

```java
public class RecipeGridComponent {

    // Constants (from CraftingInfoPanel pattern)
    private static final int CELL_SIZE = 24;
    private static final int GRID_SIZE = 3;
    private static final int GRID_GAP = 2;

    // State
    private final IngredientSlot[] slots = new IngredientSlot[9];
    private int hoveredSlot = -1;
    private int selectedSlot = -1;
    private ItemStack draggedItem = ItemStack.EMPTY;
    private boolean isDragging = false;

    // Callbacks
    private Consumer<Integer> onSlotClick;
    private BiConsumer<Integer, ItemStack> onSlotChange;

    public RecipeGridComponent() {
        // Initialize empty slots
        for (int i = 0; i < 9; i++) {
            slots[i] = new IngredientSlot(i, ItemStack.EMPTY, null, false);
        }
    }

    public int render(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        int cellSize = ScaledCoord.scaleDim(CELL_SIZE);
        int totalSize = cellSize * GRID_SIZE + GRID_GAP * (GRID_SIZE - 1);

        // Update hover state
        hoveredSlot = getSlotAt(mouseX, mouseY, x, y, cellSize);

        // Render grid
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int index = row * GRID_SIZE + col;
                int cellX = x + col * (cellSize + GRID_GAP);
                int cellY = y + row * (cellSize + GRID_GAP);

                renderSlot(graphics, font, cellX, cellY, cellSize, index);
            }
        }

        // Render dragged item
        if (isDragging && !draggedItem.isEmpty()) {
            graphics.renderItem(draggedItem, mouseX - 8, mouseY - 8);
        }

        return totalSize;
    }

    private void renderSlot(GuiGraphics g, Font font, int x, int y, int size, int index) {
        IngredientSlot slot = slots[index];

        // Background
        int bgColor = (index == selectedSlot) ? UIConstants.Background.ACTIVE() :
                      (index == hoveredSlot) ? UIConstants.Background.HOVER() :
                      UIConstants.Background.INPUT();
        g.fill(x, y, x + size, y + size, bgColor);

        // Border
        int borderColor = (index == selectedSlot) ? UIConstants.Border.ACCENT() :
                          UIConstants.Border.MUTED();
        AxiomRenderer.drawBorder(g, x, y, size, size, borderColor);

        // Item or tag indicator
        if (!slot.item().isEmpty()) {
            int pad = (size - 16) / 2;
            g.renderItem(slot.item(), x + pad, y + pad);
        } else if (slot.tag() != null) {
            // Show tag icon
            g.drawString(font, "#", x + size/2 - 3, y + size/2 - 4,
                        UIConstants.Text.MUTED(), false);
        }
    }

    // Input handling
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int slot = getSlotAt((int)mouseX, (int)mouseY, ...);
        if (slot >= 0) {
            if (button == 0) { // Left click
                if (onSlotClick != null) onSlotClick.accept(slot);
                selectedSlot = slot;
                return true;
            } else if (button == 1) { // Right click - clear slot
                setSlot(slot, ItemStack.EMPTY);
                return true;
            }
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && selectedSlot >= 0) {
            isDragging = true;
            draggedItem = slots[selectedSlot].item().copy();
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDragging) {
            int targetSlot = getSlotAt((int)mouseX, (int)mouseY, ...);
            if (targetSlot >= 0 && targetSlot != selectedSlot) {
                // Swap items
                ItemStack temp = slots[targetSlot].item();
                setSlot(targetSlot, draggedItem);
                setSlot(selectedSlot, temp);
            }
            isDragging = false;
            draggedItem = ItemStack.EMPTY;
            return true;
        }
        return false;
    }

    // Slot management
    public void setSlot(int index, ItemStack item) {
        slots[index] = new IngredientSlot(index, item, null, false);
        if (onSlotChange != null) onSlotChange.accept(index, item);
    }

    public void setSlotTag(int index, TagKey<Item> tag) {
        slots[index] = new IngredientSlot(index, ItemStack.EMPTY, tag, true);
        if (onSlotChange != null) onSlotChange.accept(index, ItemStack.EMPTY);
    }

    public IngredientSlot[] getSlots() {
        return slots.clone();
    }

    public void loadFromRecipe(RecipeData recipe) {
        for (IngredientSlot slot : recipe.pattern()) {
            slots[slot.index()] = slot;
        }
    }
}
```

#### 2.2 RecipeModule.java (Module principale)

```java
public class RecipeModule extends AbstractEditorModule {

    private static final String TAB_GRID = "grid";
    private static final String TAB_RESULT = "result";
    private static final String TAB_SETTINGS = "settings";

    // Components
    private final RecipeGridComponent recipeGrid = new RecipeGridComponent();
    private final RecipeResultSlot resultSlot = new RecipeResultSlot();

    // Current recipe state
    private RecipeData currentRecipe;
    private RecipeData originalRecipe;
    private RecipeType selectedType = RecipeType.SHAPED_3X3;
    private String recipeId = "";
    private String recipeGroup = "";

    // UI Components
    private EditorTextField idField;
    private EditorTextField groupField;
    private List<EditorButton> typeButtons;

    public RecipeModule(Font font) {
        super(font);
        setupCallbacks();
    }

    private void setupCallbacks() {
        recipeGrid.setOnSlotChange((index, item) -> {
            markDirty("Changed ingredient at slot " + index);
            updatePreview();
        });

        resultSlot.setOnChange(item -> {
            markDirty("Changed result item");
            updatePreview();
        });
    }

    @Override
    public void onItemSet(ItemStack stack) {
        // Per ora: crea ricetta vuota con result = stack
        this.currentRecipe = RecipeData.empty(stack);
        this.originalRecipe = currentRecipe;

        resultSlot.setItem(stack);
        recipeGrid.clear();
        recipeId = generateRecipeId(stack);

        initializeTabs();
    }

    @Override
    protected void initializeTabs() {
        tabs.clear();

        // Tab 1: Recipe Grid
        tabs.add(ModuleTab.of(TAB_GRID, "Recipe", () -> List.of(
            new RecipeGridSection(recipeGrid),
            new HeaderSection("Recipe Type"),
            createTypeSelector()
        )));

        // Tab 2: Result Configuration
        tabs.add(ModuleTab.of(TAB_RESULT, "Result", () -> List.of(
            new RecipeResultSection(resultSlot),
            createQuantitySlider()
        )));

        // Tab 3: Settings
        tabs.add(ModuleTab.of(TAB_SETTINGS, "Settings", () -> List.of(
            new HeaderSection("Recipe ID"),
            createIdInput(),
            new HeaderSection("Group (Optional)"),
            createGroupInput(),
            new SpacerSection(16),
            createValidationSection()
        )));
    }

    @Override
    public CustomPacketPayload buildPayload(boolean isGlobal) {
        RecipeData recipe = buildCurrentRecipe();
        return new RecipeSyncPayload(List.of(recipe), isGlobal);
    }

    @Override
    public void applyPreview() {
        RecipeData recipe = buildCurrentRecipe();

        // Validate
        ValidationResult result = RecipeValidator.validate(recipe);
        if (!result.valid()) {
            // Show validation errors
            showValidationErrors(result.errors());
            return;
        }

        // Apply locally
        RecipeConfigManager.addRecipe(recipe);

        // Update state
        originalRecipe = recipe;
        clearDirty();
    }

    private RecipeData buildCurrentRecipe() {
        return new RecipeData(
            ResourceLocation.tryParse(recipeId),
            selectedType,
            List.of(recipeGrid.getSlots()),
            resultSlot.getItem(),
            resultSlot.getQuantity(),
            recipeGroup.isEmpty() ? null : recipeGroup,
            !originalRecipe.equals(currentRecipe),
            originalRecipe.id()
        );
    }

    // ... altri metodi helper
}
```

---

### Fase 3: Network & Persistence (200-300 righe)

#### 3.1 RecipeSyncPayload.java

```java
public record RecipeSyncPayload(
    List<RecipeData> recipes,
    boolean isGlobal
) implements CustomPacketPayload {

    public static final ResourceLocation ID =
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "recipe_sync");

    public static final CustomPacketPayload.Type<RecipeSyncPayload> TYPE =
        new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeSyncPayload> STREAM_CODEC =
        StreamCodec.composite(
            RecipeData.LIST_CODEC, RecipeSyncPayload::recipes,
            ByteBufCodecs.BOOL, RecipeSyncPayload::isGlobal,
            RecipeSyncPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void applyOnServer(ServerPlayer player) {
        if (!hasPermission(player)) {
            LOGGER.warn("Player {} tried to modify recipes without permission", player.getName());
            return;
        }

        for (RecipeData recipe : recipes) {
            ValidationResult result = RecipeValidator.validate(recipe);
            if (result.valid()) {
                RecipeConfigManager.addRecipe(recipe);
            }
        }

        // Sync to all clients
        broadcastToAllPlayers(this);
    }

    public void applyOnClient() {
        for (RecipeData recipe : recipes) {
            RecipeConfigManager.addRecipeClientOnly(recipe);
        }
    }
}
```

#### 3.2 DatapackIO Extension

```java
// Aggiungere a DatapackIO.java

/**
 * Export custom recipes to datapack.
 */
public static int exportRecipes(String packName) {
    Path gameDir = ConfigPaths.getGameDir();
    if (gameDir == null) {
        LOGGER.error("[DatapackIO] Game directory is null");
        return 0;
    }

    Path base = gameDir.resolve("datapacks").resolve(packName);
    int count = 0;

    try {
        writePackMeta(base);

        Path recipesDir = base.resolve("data/devmod/recipe");
        Files.createDirectories(recipesDir);

        for (RecipeData recipe : RecipeConfigManager.getAllCustomRecipes()) {
            String filename = recipe.id().getPath().replace("/", "_") + ".json";
            Path out = recipesDir.resolve(filename);

            JsonObject json = RecipeSerializer.toMinecraftJson(recipe);
            Files.writeString(out, GSON.toJson(json), StandardCharsets.UTF_8);
            count++;
        }

        LOGGER.info("[DatapackIO] Exported {} recipes to '{}'", count, packName);
    } catch (Exception e) {
        LOGGER.error("[DatapackIO] Failed to export recipes", e);
    }

    return count;
}
```

---

### Fase 4: Integration (100-200 righe)

#### 4.1 Registrazione in ItemEditorScreen

```java
// In ItemEditorScreen.java, aggiungere:

private RecipeModule recipeModule;

@Override
protected void init() {
    super.init();

    // Existing modules...

    // Recipe module (disponibile per tutti gli items craftabili)
    recipeModule = new RecipeModule(font);
    if (canEditRecipes()) {
        modules.add(recipeModule);
    }
}

private boolean canEditRecipes() {
    // Permesso solo se il player ha i permessi
    return hasOperatorPermission() || isCreativeMode();
}
```

#### 4.2 Registrazione Network

```java
// In NetworkHandler.java, aggiungere:

// Server-bound (client sends recipe edit)
event.registrar("recipes").playToServer(
    nn(RecipeSyncPayload.TYPE),
    nn(RecipeSyncPayload.STREAM_CODEC),
    NetworkHandler::handleRecipeSync
);

// Client-bound (server broadcasts changes)
event.registrar("recipes").playToClient(
    nn(RecipeSyncPayload.TYPE),
    nn(RecipeSyncPayload.STREAM_CODEC),
    NetworkHandler::handleRecipeSyncClient
);

private static void handleRecipeSync(RecipeSyncPayload payload, IPayloadContext context) {
    context.enqueueWork(() -> {
        ServerPlayer player = (ServerPlayer) context.player();
        payload.applyOnServer(player);
    });
}

private static void handleRecipeSyncClient(RecipeSyncPayload payload, IPayloadContext context) {
    context.enqueueWork(payload::applyOnClient);
}
```

---

## Strategia di Implementazione Incrementale

### Milestone 1: Foundation (MVP - Solo Crafting)
**Target:** Editor funzionale per ricette crafting shaped/shapeless

| Componente | Priorità | Note |
|------------|----------|------|
| `RecipeData` (sealed interface) | P0 | Solo `CraftingRecipeData` inizialmente |
| `IngredientData`, `ResultData` | P0 | Records base |
| `RecipeConfigManager` | P0 | CRUD + persistence |
| `RecipeSerializer` (crafting only) | P0 | JSON export |
| `CraftingEditorModule` | P0 | Grid 3x3 + result |
| `RecipeSyncPayload` | P0 | Network sync |
| DatapackIO extension | P0 | Export crafting |

**Effort:** ~800-1000 righe

### Milestone 2: Smelting & Cooking
**Target:** Supporto furnace, blast furnace, smoker, campfire

| Componente | Priorità | Note |
|------------|----------|------|
| `SmeltingRecipeData` | P1 | Nuovo record |
| `SmeltingEditorModule` | P1 | Input, output, time, XP sliders |
| `RecipeSerializer` extension | P1 | Smelting JSON |
| Time/XP validation | P1 | Limiti ragionevoli |

**Effort:** ~300-400 righe

### Milestone 3: Smithing & Stonecutting
**Target:** Supporto smithing table e stonecutter

| Componente | Priorità | Note |
|------------|----------|------|
| `SmithingRecipeData` | P2 | Template, base, addition |
| `StonecuttingRecipeData` | P2 | Semplice |
| Relativi modules | P2 | UI specializzata |

**Effort:** ~400-500 righe

### Milestone 4: Advanced Features
**Target:** Feature avanzate per power users

| Feature | Priorità | Note |
|---------|----------|------|
| Tag ingredients (`#c:ingots`) | P3 | NeoForge ingredient types |
| DataComponent matching | P3 | Per item specifici |
| Recipe priorities export | P3 | `recipe_priorities.json` |
| Bulk import/export | P3 | Multiple recipes |
| Recipe search/filter | P3 | UI enhancement |

**Effort:** ~400-600 righe

---

## Effort Estimate Totale (Sistema Completo)

| Milestone | Componenti | Righe | Complessità |
|-----------|------------|-------|-------------|
| **M1: Crafting** | Core + CraftingModule + Network | 800-1000 | Alta |
| **M2: Smelting** | SmeltingData + Module + Serializer | 300-400 | Media |
| **M3: Smithing** | Smithing + Stonecutting | 400-500 | Media |
| **M4: Advanced** | Tags, Components, Priorities | 400-600 | Alta |
| **Totale** | | **1900-2500** | **Alta** |

> **Raccomandazione:** Iniziare con Milestone 1 (crafting) per validare l'architettura, poi procedere incrementalmente.

---

## Dipendenze e Rischi

### Dipendenze

| Dipendenza | Stato | Note |
|------------|-------|------|
| AbstractEditorModule | ✅ Exists | Base per RecipeModule |
| DatapackIO | ✅ Exists | Pattern per export |
| NetworkHandler | ✅ Exists | Pattern per payloads |
| CraftingInfoPanel | ✅ Exists | Reference per grid |
| SlotSelector | ✅ Exists | Pattern per slots |
| ConfigPaths | ✅ Exists | Path management |

### Rischi

| Rischio | Probabilità | Impatto | Mitigazione |
|---------|-------------|---------|-------------|
| Recipe conflicts | Alta | Medio | Validation + warnings |
| Sync issues | Media | Alto | Thorough testing |
| Performance (many recipes) | Bassa | Medio | Lazy loading |
| Minecraft updates | Media | Alto | Abstract recipe format |

---

## Checklist Implementazione per Milestone

### Milestone 1: Crafting (MVP)

#### Core Infrastructure
- [ ] `RecipeData.java` - Sealed interface + `CraftingRecipeData`
- [ ] `IngredientData.java` - Record ingrediente (item/tag/alternatives)
- [ ] `ResultData.java` - Record risultato (id, count, components)
- [ ] `RecipeConfigManager.java` - Singleton con ConcurrentHashMap
- [ ] `RecipeSerializer.java` - Crafting shaped/shapeless JSON
- [ ] `RecipeValidator.java` - Validazione base

#### UI Components
- [ ] `RecipeGridComponent.java` - Grid 3x3 interattiva
- [ ] `IngredientSlotComponent.java` - Slot singolo con hover/click
- [ ] `RecipeResultSlot.java` - Slot risultato con quantity
- [ ] `CraftingEditorModule.java` - Module crafting completo
- [ ] Tab switching Shaped ↔ Shapeless

#### Network & Persistence
- [ ] `RecipeSyncPayload.java` - Payload con StreamCodec
- [ ] Registrazione in `NetworkHandler.java`
- [ ] `DatapackIO.exportRecipes()` - Export crafting
- [ ] Load/save JSON in RecipeConfigManager
- [ ] `OnDatapackSyncEvent` handling per client sync

#### Integration
- [ ] Nuovo keybind/menu per Recipe Editor
- [ ] Permission check (op/creative)
- [ ] Recipe browser/selector UI

### Milestone 2: Smelting
- [ ] `SmeltingRecipeData.java`
- [ ] `SmeltingEditorModule.java` - Input, output, time, XP
- [ ] `RecipeSerializer` extension per smelting types
- [ ] Cookingtime slider (configurable per type)
- [ ] Experience input

### Milestone 3: Smithing & Stonecutting
- [ ] `SmithingRecipeData.java`
- [ ] `StonecuttingRecipeData.java`
- [ ] `SmithingEditorModule.java` - 3 slot UI
- [ ] `StonecuttingEditorModule.java` - Simple 2 slot
- [ ] Template item picker

### Milestone 4: Advanced
- [ ] Tag selector popup (`#c:ingots`, `#minecraft:planks`)
- [ ] NeoForge `CompoundIngredient` support
- [ ] `recipe_priorities.json` export
- [ ] Bulk operations (import/export multiple)
- [ ] Recipe search con filtri

---

## File Reference (Tutti i Milestone)

### Nuovi File

| File | Path | Milestone |
|------|------|-----------|
| RecipeData | `src/main/java/com/devmod/recipe/RecipeData.java` | M1 |
| CraftingRecipeData | `src/main/java/com/devmod/recipe/CraftingRecipeData.java` | M1 |
| SmeltingRecipeData | `src/main/java/com/devmod/recipe/SmeltingRecipeData.java` | M2 |
| SmithingRecipeData | `src/main/java/com/devmod/recipe/SmithingRecipeData.java` | M3 |
| StonecuttingRecipeData | `src/main/java/com/devmod/recipe/StonecuttingRecipeData.java` | M3 |
| IngredientData | `src/main/java/com/devmod/recipe/IngredientData.java` | M1 |
| ResultData | `src/main/java/com/devmod/recipe/ResultData.java` | M1 |
| RecipeConfigManager | `src/main/java/com/devmod/recipe/RecipeConfigManager.java` | M1 |
| RecipeSerializer | `src/main/java/com/devmod/recipe/RecipeSerializer.java` | M1 |
| RecipeValidator | `src/main/java/com/devmod/recipe/RecipeValidator.java` | M1 |
| RecipeGridComponent | `src/main/java/com/devmod/client/ui/editor/components/RecipeGridComponent.java` | M1 |
| IngredientSlotComponent | `src/main/java/com/devmod/client/ui/editor/components/IngredientSlotComponent.java` | M1 |
| CraftingEditorModule | `src/main/java/com/devmod/client/ui/editor/modules/CraftingEditorModule.java` | M1 |
| SmeltingEditorModule | `src/main/java/com/devmod/client/ui/editor/modules/SmeltingEditorModule.java` | M2 |
| SmithingEditorModule | `src/main/java/com/devmod/client/ui/editor/modules/SmithingEditorModule.java` | M3 |
| StonecuttingEditorModule | `src/main/java/com/devmod/client/ui/editor/modules/StonecuttingEditorModule.java` | M3 |
| RecipeSyncPayload | `src/main/java/com/devmod/network/RecipeSyncPayload.java` | M1 |

### File da Modificare

| File | Path | Modifiche |
|------|------|-----------|
| DatapackIO | `src/main/java/com/devmod/util/DatapackIO.java` | +exportRecipes(), +importRecipes() |
| NetworkHandler | `src/main/java/com/devmod/NetworkHandler.java` | +RecipeSyncPayload registration |
| DevMod | `src/main/java/com/devmod/DevMod.java` | +RecipeConfigManager.initialize() |
| CommonModEvents | `src/main/java/com/devmod/CommonModEvents.java` | +OnDatapackSyncEvent handling |

---

## Riferimenti Esterni

**NeoForge Documentation:**
- [Recipes Overview](https://docs.neoforged.net/docs/resources/server/recipes/)
- [Custom Recipe Types](https://docs.neoforged.net/docs/resources/server/recipes/custom)
- [Ingredients](https://docs.neoforged.net/docs/resources/server/recipes/ingredients)

**Minecraft Wiki:**
- [Recipe JSON Format](https://minecraft.wiki/w/Recipe)

**Codice Reference Interno:**
- [06-persistence.md](06-persistence.md) - Architettura persistenza
- [CraftingInfoPanel.java](../../src/main/java/com/devmod/client/ui/editor/systems/CraftingInfoPanel.java) - Grid rendering
- [AbstractEditorModule.java](../../src/main/java/com/devmod/client/ui/editor/AbstractEditorModule.java) - Base module
- [DatapackIO.java](../../src/main/java/com/devmod/util/DatapackIO.java) - Export pattern
- [GlobalConfigSyncPayload.java](../../src/main/java/com/devmod/network/GlobalConfigSyncPayload.java) - Network pattern
