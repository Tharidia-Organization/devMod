# Architettura Recipe Editor

> Data structures, sealed interfaces e design patterns

## Overview Architettura

```
src/main/java/com/frenkvs/devmod/
├── recipe/
│   ├── RecipeData.java              # Sealed interface base
│   ├── CraftingRecipeData.java      # Record crafting
│   ├── SmeltingRecipeData.java      # Record smelting
│   ├── SmithingRecipeData.java      # Record smithing
│   ├── StonecuttingRecipeData.java  # Record stonecutting
│   ├── IngredientData.java          # Record ingrediente
│   ├── ResultData.java              # Record risultato
│   ├── RecipeConfigManager.java     # Singleton storage
│   ├── RecipeSerializer.java        # JSON conversion
│   └── RecipeValidator.java         # Validation logic
│
├── ui/editor/
│   ├── modules/
│   │   ├── CraftingEditorModule.java
│   │   ├── SmeltingEditorModule.java
│   │   ├── SmithingEditorModule.java
│   │   └── StonecuttingEditorModule.java
│   │
│   └── components/
│       ├── RecipeGridComponent.java
│       ├── IngredientSlotComponent.java
│       └── RecipeResultSlot.java
│
└── network/
    └── RecipeSyncPayload.java
```

---

## Sealed Interface Pattern

```java
/**
 * RecipeData - Interfaccia sigillata per tutti i tipi di ricetta.
 * Garantisce type-safety e pattern matching esaustivo.
 */
public sealed interface RecipeData permits
    CraftingRecipeData,
    SmeltingRecipeData,
    SmithingRecipeData,
    StonecuttingRecipeData {

    // === Identificazione ===
    ResourceLocation id();

    // === Metadata ===
    RecipeCategory category();
    @Nullable String group();

    // === Tracking modifiche ===
    boolean isModified();
    @Nullable ResourceLocation originalId();

    // === Serialization ===
    JsonObject toJson();

    // === Factory ===
    static RecipeData fromJson(ResourceLocation id, JsonObject json) {
        String type = GsonHelper.getAsString(json, "type");

        return switch (type) {
            case "minecraft:crafting_shaped",
                 "minecraft:crafting_shapeless" -> CraftingRecipeData.fromJson(id, json);
            case "minecraft:smelting",
                 "minecraft:blasting",
                 "minecraft:smoking",
                 "minecraft:campfire_cooking" -> SmeltingRecipeData.fromJson(id, json);
            case "minecraft:smithing_transform",
                 "minecraft:smithing_trim" -> SmithingRecipeData.fromJson(id, json);
            case "minecraft:stonecutting" -> StonecuttingRecipeData.fromJson(id, json);
            default -> throw new IllegalArgumentException("Unknown recipe type: " + type);
        };
    }
}
```

---

## Record Definitions

### CraftingRecipeData

```java
public record CraftingRecipeData(
    ResourceLocation id,
    CraftingType craftingType,           // SHAPED o SHAPELESS
    RecipeCategory category,             // equipment, building, misc, redstone
    @Nullable String group,              // Raggruppamento recipe book
    List<IngredientData> ingredients,    // 9 slots per shaped, 1-9 per shapeless
    @Nullable String[] pattern,          // Solo per shaped (es. ["###", " | ", " | "])
    @Nullable Map<Character, Integer> keyToSlot, // Mappa key -> slot index (per shaped)
    ResultData result,
    boolean showNotification,
    boolean isModified,
    @Nullable ResourceLocation originalId
) implements RecipeData {

    // === Validation ===
    public CraftingRecipeData {
        Objects.requireNonNull(id, "Recipe ID cannot be null");
        Objects.requireNonNull(craftingType, "Crafting type cannot be null");
        Objects.requireNonNull(result, "Result cannot be null");

        if (craftingType == CraftingType.SHAPED) {
            Objects.requireNonNull(pattern, "Pattern required for shaped recipes");
            if (pattern.length == 0 || pattern.length > 3) {
                throw new IllegalArgumentException("Pattern must have 1-3 rows");
            }
        }

        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException("Recipe must have at least one ingredient");
        }
    }

    // === Factory Methods ===
    public static CraftingRecipeData empty(ItemStack resultItem) {
        return new CraftingRecipeData(
            ResourceLocation.fromNamespaceAndPath("devmod", "custom_" + System.currentTimeMillis()),
            CraftingType.SHAPED,
            RecipeCategory.MISC,
            null,
            List.of(),
            new String[]{"   ", "   ", "   "},
            Map.of(),
            ResultData.of(resultItem),
            true,
            false,
            null
        );
    }

    public static CraftingRecipeData fromJson(ResourceLocation id, JsonObject json) {
        String type = GsonHelper.getAsString(json, "type");
        CraftingType craftingType = type.contains("shapeless")
            ? CraftingType.SHAPELESS
            : CraftingType.SHAPED;

        // Parse category
        String catStr = GsonHelper.getAsString(json, "category", "misc");
        RecipeCategory category = RecipeCategory.fromString(catStr);

        // Parse group
        String group = json.has("group") ? GsonHelper.getAsString(json, "group") : null;

        // Parse ingredients
        List<IngredientData> ingredients;
        String[] pattern = null;
        Map<Character, Integer> keyToSlot = null;

        if (craftingType == CraftingType.SHAPED) {
            // Parse pattern
            JsonArray patternArray = GsonHelper.getAsJsonArray(json, "pattern");
            pattern = new String[patternArray.size()];
            for (int i = 0; i < patternArray.size(); i++) {
                pattern[i] = patternArray.get(i).getAsString();
            }

            // Parse key
            JsonObject keyObj = GsonHelper.getAsJsonObject(json, "key");
            ingredients = new ArrayList<>();
            keyToSlot = new HashMap<>();

            for (Map.Entry<String, JsonElement> entry : keyObj.entrySet()) {
                char c = entry.getKey().charAt(0);
                IngredientData ing = IngredientData.fromJson(entry.getValue());
                int slotIndex = findSlotForKey(pattern, c);
                keyToSlot.put(c, slotIndex);
                ingredients.add(ing);
            }
        } else {
            // Shapeless - direct ingredients array
            JsonArray ingArray = GsonHelper.getAsJsonArray(json, "ingredients");
            ingredients = new ArrayList<>();
            for (JsonElement elem : ingArray) {
                ingredients.add(IngredientData.fromJson(elem));
            }
        }

        // Parse result
        ResultData result = ResultData.fromJson(GsonHelper.getAsJsonObject(json, "result"));

        // Parse show_notification
        boolean showNotification = GsonHelper.getAsBoolean(json, "show_notification", true);

        return new CraftingRecipeData(
            id, craftingType, category, group,
            ingredients, pattern, keyToSlot, result,
            showNotification, false, null
        );
    }

    // === Serialization ===
    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();

        if (craftingType == CraftingType.SHAPED) {
            json.addProperty("type", "minecraft:crafting_shaped");

            // Pattern
            JsonArray patternArray = new JsonArray();
            for (String row : pattern) {
                patternArray.add(row);
            }
            json.add("pattern", patternArray);

            // Key
            JsonObject keyObj = new JsonObject();
            // ... build key from ingredients
            json.add("key", keyObj);
        } else {
            json.addProperty("type", "minecraft:crafting_shapeless");

            // Ingredients array
            JsonArray ingArray = new JsonArray();
            for (IngredientData ing : ingredients) {
                ingArray.add(ing.toJson());
            }
            json.add("ingredients", ingArray);
        }

        // Category
        json.addProperty("category", category.getId());

        // Group
        if (group != null && !group.isEmpty()) {
            json.addProperty("group", group);
        }

        // Result
        json.add("result", result.toJson());

        // Show notification
        if (!showNotification) {
            json.addProperty("show_notification", false);
        }

        return json;
    }
}
```

### SmeltingRecipeData

```java
public record SmeltingRecipeData(
    ResourceLocation id,
    SmeltingType smeltingType,       // SMELTING, BLASTING, SMOKING, CAMPFIRE
    RecipeCategory category,          // food, blocks, misc
    @Nullable String group,
    IngredientData ingredient,        // Singolo ingrediente
    ResultData result,
    float experience,
    int cookingTime,                  // In ticks
    boolean isModified,
    @Nullable ResourceLocation originalId
) implements RecipeData {

    // === Defaults per tipo ===
    public static int defaultCookingTime(SmeltingType type) {
        return switch (type) {
            case SMELTING -> 200;    // 10 secondi
            case BLASTING -> 100;    // 5 secondi
            case SMOKING -> 100;     // 5 secondi
            case CAMPFIRE -> 600;    // 30 secondi
        };
    }

    public static SmeltingRecipeData fromJson(ResourceLocation id, JsonObject json) {
        String type = GsonHelper.getAsString(json, "type");
        SmeltingType smeltingType = switch (type) {
            case "minecraft:smelting" -> SmeltingType.SMELTING;
            case "minecraft:blasting" -> SmeltingType.BLASTING;
            case "minecraft:smoking" -> SmeltingType.SMOKING;
            case "minecraft:campfire_cooking" -> SmeltingType.CAMPFIRE;
            default -> throw new IllegalArgumentException("Unknown smelting type: " + type);
        };

        // ... parse altri campi

        return new SmeltingRecipeData(
            id, smeltingType, category, group,
            ingredient, result, experience, cookingTime,
            false, null
        );
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();

        json.addProperty("type", switch (smeltingType) {
            case SMELTING -> "minecraft:smelting";
            case BLASTING -> "minecraft:blasting";
            case SMOKING -> "minecraft:smoking";
            case CAMPFIRE -> "minecraft:campfire_cooking";
        });

        json.addProperty("category", category.getId());

        if (group != null) {
            json.addProperty("group", group);
        }

        json.add("ingredient", ingredient.toJson());
        json.add("result", result.toJson());
        json.addProperty("experience", experience);
        json.addProperty("cookingtime", cookingTime);

        return json;
    }
}
```

### SmithingRecipeData

```java
public record SmithingRecipeData(
    ResourceLocation id,
    SmithingType smithingType,        // TRANSFORM o TRIM
    @Nullable IngredientData template,
    IngredientData base,
    @Nullable IngredientData addition,
    @Nullable ResultData result,      // Solo per TRANSFORM, null per TRIM
    boolean isModified,
    @Nullable ResourceLocation originalId
) implements RecipeData {

    @Override
    public RecipeCategory category() {
        return RecipeCategory.EQUIPMENT;
    }

    @Override
    public @Nullable String group() {
        return null; // Smithing non usa groups
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();

        if (smithingType == SmithingType.TRANSFORM) {
            json.addProperty("type", "minecraft:smithing_transform");
            json.add("result", result.toJson());
        } else {
            json.addProperty("type", "minecraft:smithing_trim");
        }

        if (template != null) {
            json.add("template", template.toJson());
        }
        json.add("base", base.toJson());
        if (addition != null) {
            json.add("addition", addition.toJson());
        }

        return json;
    }
}
```

### StonecuttingRecipeData

```java
public record StonecuttingRecipeData(
    ResourceLocation id,
    IngredientData ingredient,
    ResultData result,
    boolean isModified,
    @Nullable ResourceLocation originalId
) implements RecipeData {

    @Override
    public RecipeCategory category() {
        return RecipeCategory.BUILDING;
    }

    @Override
    public @Nullable String group() {
        return null;
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:stonecutting");
        json.add("ingredient", ingredient.toJson());
        json.add("result", result.toJson());
        return json;
    }
}
```

---

## Supporting Types

### IngredientData

```java
/**
 * Rappresenta un ingrediente flessibile (item, tag, o multiple options).
 */
public record IngredientData(
    @Nullable ResourceLocation item,           // Item singolo
    @Nullable TagKey<Item> tag,                // Tag (es. c:ingots/iron)
    @Nullable List<ResourceLocation> alternatives, // OR di piu item
    @Nullable DataComponentPredicate componentPredicate // Per DataComponentIngredient
) {
    // === Factory Methods ===

    public static IngredientData ofItem(ResourceLocation item) {
        return new IngredientData(item, null, null, null);
    }

    public static IngredientData ofItem(Item item) {
        return ofItem(BuiltInRegistries.ITEM.getKey(item));
    }

    public static IngredientData ofTag(TagKey<Item> tag) {
        return new IngredientData(null, tag, null, null);
    }

    public static IngredientData ofTag(String tagPath) {
        ResourceLocation loc = ResourceLocation.parse(tagPath.startsWith("#")
            ? tagPath.substring(1)
            : tagPath);
        return ofTag(TagKey.create(Registries.ITEM, loc));
    }

    public static IngredientData ofAny(List<ResourceLocation> items) {
        return new IngredientData(null, null, items, null);
    }

    public static IngredientData empty() {
        return new IngredientData(null, null, null, null);
    }

    // === Queries ===

    public boolean isEmpty() {
        return item == null && tag == null && alternatives == null;
    }

    public boolean isTag() {
        return tag != null;
    }

    public boolean isAlternatives() {
        return alternatives != null && !alternatives.isEmpty();
    }

    // === Serialization ===

    public static IngredientData fromJson(JsonElement elem) {
        if (elem.isJsonPrimitive()) {
            String str = elem.getAsString();
            if (str.startsWith("#")) {
                return ofTag(str.substring(1));
            } else {
                return ofItem(ResourceLocation.parse(str));
            }
        } else if (elem.isJsonArray()) {
            List<ResourceLocation> alts = new ArrayList<>();
            for (JsonElement e : elem.getAsJsonArray()) {
                alts.add(ResourceLocation.parse(e.getAsString()));
            }
            return ofAny(alts);
        } else if (elem.isJsonObject()) {
            JsonObject obj = elem.getAsJsonObject();
            if (obj.has("item")) {
                return ofItem(ResourceLocation.parse(GsonHelper.getAsString(obj, "item")));
            } else if (obj.has("tag")) {
                return ofTag(GsonHelper.getAsString(obj, "tag"));
            }
        }
        return empty();
    }

    public JsonElement toJson() {
        if (tag != null) {
            return new JsonPrimitive("#" + tag.location());
        }
        if (alternatives != null && !alternatives.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (ResourceLocation alt : alternatives) {
                arr.add(alt.toString());
            }
            return arr;
        }
        if (item != null) {
            return new JsonPrimitive(item.toString());
        }
        // Empty - shouldn't happen in valid recipes
        return JsonNull.INSTANCE;
    }

    // === Display ===

    public String getDisplayName() {
        if (tag != null) {
            return "#" + tag.location();
        }
        if (alternatives != null && !alternatives.isEmpty()) {
            return alternatives.get(0).toString() + " (+" + (alternatives.size() - 1) + ")";
        }
        if (item != null) {
            return item.toString();
        }
        return "Empty";
    }

    /**
     * Ottiene un ItemStack rappresentativo per rendering.
     * Per tags, cicla tra gli items del tag.
     */
    public ItemStack getDisplayStack(long tick) {
        if (item != null) {
            Item i = BuiltInRegistries.ITEM.get(item);
            return i != null ? new ItemStack(i) : ItemStack.EMPTY;
        }
        if (tag != null) {
            List<Item> items = getTagItems(tag);
            if (!items.isEmpty()) {
                int index = (int) ((tick / 20) % items.size());
                return new ItemStack(items.get(index));
            }
        }
        if (alternatives != null && !alternatives.isEmpty()) {
            int index = (int) ((tick / 20) % alternatives.size());
            Item i = BuiltInRegistries.ITEM.get(alternatives.get(index));
            return i != null ? new ItemStack(i) : ItemStack.EMPTY;
        }
        return ItemStack.EMPTY;
    }
}
```

### ResultData

```java
/**
 * Rappresenta il risultato di una ricetta.
 */
public record ResultData(
    ResourceLocation itemId,
    int count,
    @Nullable CompoundTag components      // Data components opzionali (1.21+)
) {
    // === Factory Methods ===

    public static ResultData of(ResourceLocation itemId) {
        return new ResultData(itemId, 1, null);
    }

    public static ResultData of(ResourceLocation itemId, int count) {
        return new ResultData(itemId, count, null);
    }

    public static ResultData of(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        // TODO: extract components from stack
        return new ResultData(id, stack.getCount(), null);
    }

    // === Queries ===

    public boolean isEmpty() {
        return itemId == null;
    }

    public ItemStack toItemStack() {
        if (isEmpty()) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item, count);
        // TODO: apply components
        return stack;
    }

    // === Serialization ===

    public static ResultData fromJson(JsonObject json) {
        ResourceLocation id = ResourceLocation.parse(GsonHelper.getAsString(json, "id"));
        int count = GsonHelper.getAsInt(json, "count", 1);
        // TODO: parse components
        return new ResultData(id, count, null);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", itemId.toString());
        if (count > 1) {
            json.addProperty("count", count);
        }
        if (components != null && !components.isEmpty()) {
            // TODO: serialize components
        }
        return json;
    }
}
```

### Enums

```java
public enum CraftingType {
    SHAPED,
    SHAPELESS
}

public enum SmeltingType {
    SMELTING,   // Furnace
    BLASTING,   // Blast Furnace
    SMOKING,    // Smoker
    CAMPFIRE    // Campfire
}

public enum SmithingType {
    TRANSFORM,  // Con result
    TRIM        // Senza result (armor trim)
}

public enum RecipeCategory {
    EQUIPMENT("equipment"),
    BUILDING("building"),
    MISC("misc"),
    REDSTONE("redstone"),
    FOOD("food"),
    BLOCKS("blocks");

    private final String id;

    RecipeCategory(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static RecipeCategory fromString(String s) {
        for (RecipeCategory cat : values()) {
            if (cat.id.equals(s)) return cat;
        }
        return MISC;
    }
}
```

---

## RecipeConfigManager

```java
/**
 * Singleton per gestione ricette custom.
 * Thread-safe con ConcurrentHashMap.
 */
public class RecipeConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecipeConfigManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Thread-safe storage
    private static final Map<ResourceLocation, RecipeData> customRecipes = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, RecipeData> overriddenRecipes = new ConcurrentHashMap<>();

    private static Path dataDirectory = null;
    private static boolean initialized = false;

    // === Initialization ===

    public static void initialize(Path configDir) {
        if (initialized) return;

        dataDirectory = configDir.resolve("devmod").resolve("recipes");
        try {
            Files.createDirectories(dataDirectory);
            load();
            initialized = true;
            LOGGER.info("[RecipeConfig] Initialized with {} custom recipes", customRecipes.size());
        } catch (Exception e) {
            LOGGER.error("[RecipeConfig] Failed to initialize", e);
        }
    }

    // === CRUD Operations ===

    public static void addRecipe(RecipeData recipe) {
        Objects.requireNonNull(recipe, "Recipe cannot be null");
        Objects.requireNonNull(recipe.id(), "Recipe ID cannot be null");

        customRecipes.put(recipe.id(), recipe);
        save();
        LOGGER.debug("[RecipeConfig] Added recipe: {}", recipe.id());
    }

    public static void updateRecipe(ResourceLocation id, RecipeData recipe) {
        if (!customRecipes.containsKey(id)) {
            LOGGER.warn("[RecipeConfig] Attempted to update non-existent recipe: {}", id);
            return;
        }
        customRecipes.put(id, recipe);
        save();
    }

    public static void removeRecipe(ResourceLocation id) {
        RecipeData removed = customRecipes.remove(id);
        if (removed != null) {
            save();
            LOGGER.debug("[RecipeConfig] Removed recipe: {}", id);
        }
    }

    public static Optional<RecipeData> getRecipe(ResourceLocation id) {
        return Optional.ofNullable(customRecipes.get(id));
    }

    public static List<RecipeData> getAllCustomRecipes() {
        return new ArrayList<>(customRecipes.values());
    }

    public static List<RecipeData> getRecipesByType(Class<? extends RecipeData> type) {
        return customRecipes.values().stream()
            .filter(type::isInstance)
            .toList();
    }

    // === Persistence ===

    private static void load() {
        if (dataDirectory == null) return;

        try {
            Path indexFile = dataDirectory.resolve("index.json");
            if (!Files.exists(indexFile)) return;

            String content = Files.readString(indexFile);
            JsonObject index = GSON.fromJson(content, JsonObject.class);

            JsonArray recipes = index.getAsJsonArray("recipes");
            for (JsonElement elem : recipes) {
                try {
                    JsonObject recipeJson = elem.getAsJsonObject();
                    ResourceLocation id = ResourceLocation.parse(
                        GsonHelper.getAsString(recipeJson, "id")
                    );
                    RecipeData recipe = RecipeData.fromJson(id, recipeJson);
                    customRecipes.put(id, recipe);
                } catch (Exception e) {
                    LOGGER.error("[RecipeConfig] Failed to load recipe", e);
                }
            }
        } catch (Exception e) {
            LOGGER.error("[RecipeConfig] Failed to load index", e);
        }
    }

    public static void save() {
        if (dataDirectory == null) return;

        try {
            JsonObject index = new JsonObject();
            JsonArray recipes = new JsonArray();

            for (RecipeData recipe : customRecipes.values()) {
                JsonObject recipeJson = recipe.toJson();
                recipeJson.addProperty("id", recipe.id().toString());
                recipes.add(recipeJson);
            }

            index.add("recipes", recipes);

            Path indexFile = dataDirectory.resolve("index.json");
            Files.writeString(indexFile, GSON.toJson(index), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.error("[RecipeConfig] Failed to save", e);
        }
    }

    // === Validation ===

    public static RecipeValidator.ValidationResult validate(RecipeData recipe) {
        return RecipeValidator.validate(recipe);
    }
}
```
