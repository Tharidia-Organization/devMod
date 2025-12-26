# NeoForge 1.21 Recipe API

> Riferimento tecnico completo per l'integrazione con il sistema ricette NeoForge

## RecipeManager & RecipeHolder

```java
// Accesso al RecipeManager (server-side)
RecipeManager manager = serverLevel.recipeAccess();

// Le ricette sono wrappate in RecipeHolder
RecipeHolder<CraftingRecipe> holder = ...;
ResourceLocation id = holder.id();        // ID univoco ricetta
CraftingRecipe recipe = holder.value();   // Ricetta effettiva
```

### Metodi Chiave

| Metodo | Descrizione |
|--------|-------------|
| `recipeAccess()` | Ottiene RecipeManager dal ServerLevel |
| `holder.id()` | ResourceLocation della ricetta |
| `holder.value()` | Oggetto Recipe effettivo |
| `byType(RecipeType<T>)` | Filtra ricette per tipo |

---

## Recipe Priorities (NeoForge Feature)

NeoForge supporta priorita tra ricette con stesso output via `data/<namespace>/recipe_priorities.json`:

```json
{
  "values": {
    "mymod:better_diamond_sword": 100,
    "minecraft:diamond_sword": 0
  }
}
```

### Comportamento

- Priorita piu alta vince in caso di conflitto
- Default: 0
- Range: qualsiasi intero (positivo = piu prioritario)

---

## Custom RecipeType & Serializer

Per ricette custom serve registrare:

### 1. RecipeType

```java
public static final Supplier<RecipeType<MyRecipe>> MY_RECIPE_TYPE =
    RECIPE_TYPES.register("my_recipe", RecipeType::simple);
```

### 2. RecipeSerializer

```java
public class MyRecipeSerializer implements RecipeSerializer<MyRecipe> {

    // MapCodec per JSON serialization
    public static final MapCodec<MyRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
        instance.group(
            Ingredient.CODEC.fieldOf("ingredient").forGetter(MyRecipe::ingredient),
            ItemStack.CODEC.fieldOf("result").forGetter(MyRecipe::result)
        ).apply(instance, MyRecipe::new)
    );

    // StreamCodec per network sync
    public static final StreamCodec<RegistryFriendlyByteBuf, MyRecipe> STREAM_CODEC =
        StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, MyRecipe::ingredient,
            ItemStack.STREAM_CODEC, MyRecipe::result,
            MyRecipe::new
        );

    @Override
    public MapCodec<MyRecipe> codec() { return CODEC; }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, MyRecipe> streamCodec() {
        return STREAM_CODEC;
    }
}
```

### 3. Registrazione

```java
public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
    DeferredRegister.create(Registries.RECIPE_SERIALIZER, MODID);

public static final Supplier<MyRecipeSerializer> MY_SERIALIZER =
    RECIPE_SERIALIZERS.register("my_recipe", MyRecipeSerializer::new);
```

---

## Ingredient System (NeoForge Extensions)

NeoForge estende gli ingredienti vanilla con tipi speciali:

| Tipo | Classe | Descrizione |
|------|--------|-------------|
| Compound | `CompoundIngredient` | Match se ANY child matcha |
| Difference | `DifferenceIngredient` | Match items in A ma non in B |
| Intersection | `IntersectionIngredient` | Match items in BOTH A e B |
| DataComponent | `DataComponentIngredient` | Valida item + data components |
| BlockTag | `BlockTagIngredient` | Match block tags invece di item tags |

### Uso Compound Ingredient

```java
// Crea ingrediente che accetta QUALSIASI dei children
Ingredient compound = CompoundIngredient.of(
    Ingredient.of(Items.DIAMOND),
    Ingredient.of(Items.EMERALD),
    Ingredient.of(ItemTags.PLANKS)
);
```

### Uso DataComponent Ingredient

```java
// Valida item con specifici data components
DataComponentIngredient ingredient = DataComponentIngredient.of(
    Items.ENCHANTED_BOOK,
    DataComponentPredicate.builder()
        .expect(DataComponents.STORED_ENCHANTMENTS, ...)
        .build()
);
```

---

## Sync Server → Client

> **IMPORTANTE:** "Recipe logic should always run on the server - the server doesn't sync recipes to clients by default"

### Events per Sincronizzazione

| Event | Quando | Uso |
|-------|--------|-----|
| `OnDatapackSyncEvent` | Datapack reload | Sync custom recipes |
| `RecipesReceivedEvent` | Client riceve ricette | Gestione client-side |
| `AddReloadListenerEvent` | Server startup | Registra reload listener |

### Pattern di Sync

```java
@SubscribeEvent
public static void onDatapackSync(OnDatapackSyncEvent event) {
    // event.getPlayer() - null se broadcast a tutti
    // event.getPlayerList() - lista players

    if (event.getPlayer() != null) {
        // Sync a singolo player
        sendRecipesToPlayer(event.getPlayer());
    } else {
        // Broadcast a tutti
        for (ServerPlayer player : event.getPlayerList().getPlayers()) {
            sendRecipesToPlayer(player);
        }
    }
}

private static void sendRecipesToPlayer(ServerPlayer player) {
    List<RecipeData> customRecipes = RecipeConfigManager.getAllCustomRecipes();
    PacketDistributor.sendToPlayer(player, new RecipeSyncPayload(customRecipes));
}
```

### Client-Side Handler

```java
@SubscribeEvent
public static void onRecipesReceived(RecipesReceivedEvent event) {
    // Ricette vanilla gia ricevute
    // Possiamo aggiungere le nostre custom qui
}
```

---

## RecipeInput

NeoForge 1.21 usa `RecipeInput` invece di Container per il matching:

```java
public interface RecipeInput {
    ItemStack getItem(int slot);
    int size();
}
```

### Implementazioni Built-in

| Classe | Uso |
|--------|-----|
| `CraftingInput` | Crafting table 3x3 |
| `SingleRecipeInput` | Furnace, stonecutter |
| `SmithingRecipeInput` | Smithing table (3 slot) |

---

## Recipe Categories

```java
public enum CraftingBookCategory {
    BUILDING,      // Blocchi costruzione
    REDSTONE,      // Redstone components
    EQUIPMENT,     // Tools, armor, weapons
    MISC           // Altro
}
```

Per ricette cooking:

```java
public enum CookingBookCategory {
    FOOD,          // Cibo
    BLOCKS,        // Blocchi (es. smooth stone)
    MISC           // Altro
}
```

---

## Esempio Completo: Custom Recipe

```java
public record MyCustomRecipe(
    Ingredient input,
    ItemStack result,
    int processingTime
) implements Recipe<SingleRecipeInput> {

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return this.input.test(input.getItem(0));
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        return this.result.copy();
    }

    @Override
    public RecipeSerializer<? extends Recipe<SingleRecipeInput>> getSerializer() {
        return MyModRecipes.MY_SERIALIZER.get();
    }

    @Override
    public RecipeType<? extends Recipe<SingleRecipeInput>> getType() {
        return MyModRecipes.MY_RECIPE_TYPE.get();
    }

    // ... altri metodi required
}
```
