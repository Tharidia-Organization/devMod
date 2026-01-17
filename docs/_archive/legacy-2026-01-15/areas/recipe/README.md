# Recipe System

> Ultimo aggiornamento: 2025-12-30

Creare, modificare e gestire ricette Minecraft in modo programmatico.

---

## Perché Questo Sistema?

Le ricette vanilla di Minecraft sono definite in file JSON nei datapack. Funziona, ma ha limiti:

- **Non puoi crearle a runtime** — devi riavviare il server
- **Non puoi modificarle facilmente** — devi editare JSON
- **Non hai validazione** — errori nel JSON causano crash silenti
- **Non hai sync automatico** — devi gestire tu client/server

Il Recipe System di DevMod risolve tutto questo.

---

## Cosa Puoi Fare

✅ Creare ricette da codice Java
✅ Modificarle mentre il server è attivo
✅ Validarle prima di salvarle
✅ Sincronizzarle automaticamente ai client
✅ Esportarle come datapack
✅ Importarle da file JSON

---

## Struttura Package

```
com.devmod.recipe/
├── RecipeData.java              # Interface sealed per tutti i tipi
├── CraftingRecipeData.java      # Ricette crafting (shaped/shapeless)
├── SmeltingRecipeData.java      # Ricette fornace/blast/smoker/campfire
├── SmithingRecipeData.java      # Ricette smithing table
├── StonecuttingRecipeData.java  # Ricette stonecutter
├── IngredientData.java          # Ingrediente (item, tag, o alternative)
├── ResultData.java              # Risultato ricetta
├── RecipeCategory.java          # Categoria nel recipe book
├── CraftingType.java            # Shaped vs Shapeless
├── SmeltingType.java            # Furnace/Blast/Smoking/Campfire
├── SmithingType.java            # Transform vs Trim
├── RecipeConfigManager.java     # Storage e persistenza
├── RecipeInjector.java          # Inietta nel RecipeManager
├── RecipeValidator.java         # Validazione
└── RecipeReloadListener.java    # Lifecycle e sync
```

---

## Guida Rapida: Creare una Ricetta

### Ricetta Shaped (con pattern)

```java
// Spada di diamante potenziata
var recipe = CraftingRecipeData.shaped(
    ResourceLocation.parse("mymod:super_sword"),
    new String[]{
        " D ",    // D = diamante
        " D ",
        " S "     // S = stick
    },
    Map.of(
        'D', IngredientData.ofItem("minecraft:diamond"),
        'S', IngredientData.ofItem("minecraft:stick")
    ),
    ResultData.of("minecraft:diamond_sword")
);

// Valida e salva
RecipeConfigManager.addRecipeIfValid(recipe);
```

### Ricetta Shapeless (ordine libero)

```java
// Mix colorante
var recipe = CraftingRecipeData.shapeless(
    ResourceLocation.parse("mymod:purple_dye"),
    List.of(
        IngredientData.ofItem("minecraft:red_dye"),
        IngredientData.ofItem("minecraft:blue_dye")
    ),
    ResultData.of("minecraft:purple_dye", 2)
);

RecipeConfigManager.addRecipeIfValid(recipe);
```

### Ricetta Fornace

```java
// Smelting veloce con blast furnace
var recipe = SmeltingRecipeData.create(
    ResourceLocation.parse("mymod:fast_iron"),
    SmeltingType.BLASTING,  // 5 secondi invece di 10
    IngredientData.ofTag("#c:iron_ores"),
    ResultData.of("minecraft:iron_ingot", 2),
    0.7f  // XP
);

RecipeConfigManager.addRecipeIfValid(recipe);
```

### Ricetta Smithing

```java
// Upgrade a netherite
var recipe = SmithingRecipeData.transform(
    ResourceLocation.parse("mymod:netherite_upgrade"),
    IngredientData.ofItem("minecraft:netherite_upgrade_smithing_template"),
    IngredientData.ofItem("minecraft:diamond_sword"),
    IngredientData.ofItem("minecraft:netherite_ingot"),
    ResultData.of("minecraft:netherite_sword")
);

RecipeConfigManager.addRecipeIfValid(recipe);
```

---

## Ingredienti: Le Tre Opzioni

### Item Specifico
```java
IngredientData.ofItem("minecraft:diamond")
// Accetta SOLO diamanti
```

### Tag (gruppo di item)
```java
IngredientData.ofTag("#c:gems")
// Accetta qualsiasi gemma nel tag
// Il # indica che è un tag
```

### Alternative (uno qualsiasi)
```java
IngredientData.ofAny(List.of(
    "minecraft:diamond",
    "minecraft:emerald",
    "minecraft:amethyst_shard"
))
// Accetta uno qualsiasi di questi
```

---

## Validazione

Prima di salvare, **sempre** validare:

```java
var result = RecipeValidator.validate(recipe);

if (result.valid()) {
    RecipeConfigManager.addRecipe(recipe);
} else {
    // Mostra errori
    for (String error : result.errors()) {
        System.err.println("Errore: " + error);
    }
}
```

### Controlli Automatici

| Tipo | Controlli |
|------|-----------|
| **Crafting** | Pattern max 3x3, no chiavi inutilizzate, max 9 ingredienti shapeless |
| **Smelting** | Tempo cottura > 0, XP ≥ 0, ingrediente esiste |
| **Smithing** | Base obbligatoria, transform richiede risultato |
| **Stonecutting** | Ingrediente e risultato esistono |

---

## Persistenza e Sync

### Dove Vengono Salvate

```
config/devmod/recipes/index.json
```

Formato:
```json
{
  "version": 1,
  "savedAt": 1735123456789,
  "count": 42,
  "recipes": [
    {
      "id": "mymod:super_sword",
      "type": "minecraft:crafting_shaped",
      "pattern": [" D ", " D ", " S "],
      "key": { "D": {...}, "S": {...} },
      "result": {...}
    }
  ]
}
```

### Sync Automatico

1. **Server avvia** → Carica ricette da JSON → Inietta nel RecipeManager
2. **Player entra** → Server invia `RecipeClientSyncPayload` → Client aggiorna
3. **Ricetta modificata** → Broadcast a tutti i client connessi

Non devi fare nulla, è tutto automatico.

---

## Modificare Ricette Esistenti

```java
// Recupera ricetta
Optional<RecipeData> opt = RecipeConfigManager.getRecipe(
    ResourceLocation.parse("mymod:super_sword")
);

if (opt.isPresent() && opt.get() instanceof CraftingRecipeData crafting) {
    // Modifica (le ricette sono immutabili, crei una copia)
    var modified = crafting
        .withResult(ResultData.of("minecraft:netherite_sword"))
        .withCategory(RecipeCategory.EQUIPMENT);

    // Salva la versione modificata
    RecipeConfigManager.addRecipe(modified);
}
```

---

## Export come Datapack

```java
// Esporta tutte le ricette custom in un datapack
int count = RecipeConfigManager.exportToDatapack(
    gameDir,           // Directory del gioco
    "my_recipe_pack"   // Nome del datapack
);

System.out.println("Esportate " + count + " ricette");
```

Crea:
```
datapacks/my_recipe_pack/
├── pack.mcmeta
└── data/
    └── mymod/
        └── recipes/
            ├── super_sword.json
            └── ...
```

---

## Import da File

```java
// Importa ricette da un file JSON esterno
RecipeConfigManager.importFromFile(Path.of("my_recipes.json"));
```

---

## Tipi di Ricetta Supportati

| Tipo | Classe | Tempi Default |
|------|--------|---------------|
| Crafting Shaped | `CraftingRecipeData` | — |
| Crafting Shapeless | `CraftingRecipeData` | — |
| Fornace | `SmeltingRecipeData` | 200 tick (10s) |
| Blast Furnace | `SmeltingRecipeData` | 100 tick (5s) |
| Smoker | `SmeltingRecipeData` | 100 tick (5s) |
| Campfire | `SmeltingRecipeData` | 600 tick (30s) |
| Smithing Transform | `SmithingRecipeData` | — |
| Smithing Trim | `SmithingRecipeData` | — |
| Stonecutter | `StonecuttingRecipeData` | — |

---

## Lifecycle: Cosa Succede Quando

```
Server Avvia
    ↓
RecipeReloadListener.onServerStarted()
    ↓
RecipeConfigManager carica da JSON
    ↓
RecipeInjector.injectAll() → ricette disponibili
    ↓
Player si connette
    ↓
RecipeClientSyncPayload inviato
    ↓
Client riceve e aggiorna recipe book
```

---

## Esempio Completo: Sistema di Ricette Custom

```java
public class MyModRecipes {

    public static void registerAll() {
        // Ricetta crafting
        addSuperPickaxe();

        // Ricetta smelting
        addFastOreProcessing();

        // Ricetta smithing
        addCustomUpgrade();
    }

    private static void addSuperPickaxe() {
        var recipe = CraftingRecipeData.shaped(
            ResourceLocation.parse("mymod:super_pickaxe"),
            new String[]{
                "DDD",
                " S ",
                " S "
            },
            Map.of(
                'D', IngredientData.ofTag("#c:gems"),
                'S', IngredientData.ofItem("minecraft:blaze_rod")
            ),
            ResultData.of("minecraft:netherite_pickaxe")
        ).withCategory(RecipeCategory.EQUIPMENT);

        if (!RecipeConfigManager.addRecipeIfValid(recipe)) {
            LOGGER.warn("Ricetta super_pickaxe non valida!");
        }
    }

    private static void addFastOreProcessing() {
        // Blast furnace per tutti i minerali
        for (String ore : List.of("iron", "gold", "copper")) {
            var recipe = SmeltingRecipeData.create(
                ResourceLocation.parse("mymod:fast_" + ore),
                SmeltingType.BLASTING,
                IngredientData.ofTag("#c:" + ore + "_ores"),
                ResultData.of("minecraft:" + ore + "_ingot", 2),
                1.0f
            );
            RecipeConfigManager.addRecipeIfValid(recipe);
        }
    }

    private static void addCustomUpgrade() {
        var recipe = SmithingRecipeData.transform(
            ResourceLocation.parse("mymod:enchanted_upgrade"),
            IngredientData.ofItem("minecraft:nether_star"),
            IngredientData.ofTag("#c:tools"),
            IngredientData.ofItem("minecraft:echo_shard"),
            ResultData.of("mymod:enchanted_tool")
        );
        RecipeConfigManager.addRecipeIfValid(recipe);
    }
}
```

---

## Dipendenze

- Minecraft RecipeManager — per iniezione
- GSON — per serializzazione JSON
- NeoForge Events — per lifecycle
- `com.devmod.network` — per sync client
