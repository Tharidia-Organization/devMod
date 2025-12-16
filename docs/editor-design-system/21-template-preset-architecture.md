# Template & Preset Architecture

I template/preset seguono una **gerarchia a 3 livelli** con risoluzione prioritaria:

## Gerarchia dei Livelli

```
┌─────────────────────────────────────────────────────────────────┐
│                    TEMPLATE RESOLUTION ORDER                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  LEVEL 3: MODPACK PRESETS (highest priority)                    │
│  └─ config/devmod/presets/modpack/<modpack-id>/                 │
│     └─ Definiti dal modpack, override tutto                     │
│                                                                 │
│  LEVEL 2: CATEGORY PRESETS                                      │
│  └─ config/devmod/presets/category/<category>/                  │
│     └─ Per categoria item (swords, bows, helmets...)            │
│                                                                 │
│  LEVEL 1: GLOBAL PRESETS (lowest priority)                      │
│  └─ config/devmod/presets/global/                               │
│     └─ Disponibili ovunque, base defaults                       │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

Resolution: MODPACK → CATEGORY → GLOBAL (first match wins)
```

## Struttura File System

```
config/devmod/presets/
├── global/                          # LEVEL 1: Global presets
│   ├── weapon/
│   │   ├── vanilla_default.json
│   │   ├── balanced_pvp.json
│   │   └── overpowered_debug.json
│   └── armor/
│       ├── vanilla_default.json
│       ├── tank_build.json
│       └── glass_cannon.json
│
├── category/                        # LEVEL 2: Category-specific
│   ├── sword/
│   │   ├── diamond_tier.json
│   │   └── netherite_tier.json
│   ├── bow/
│   │   ├── sniper.json
│   │   └── rapid_fire.json
│   ├── helmet/
│   │   └── night_vision_focus.json
│   ├── chestplate/
│   │   └── thorns_tank.json
│   ├── leggings/
│   │   └── speed_focus.json
│   └── boots/
│       └── feather_fall.json
│
└── modpack/                         # LEVEL 3: Modpack overrides
    ├── rlcraft/
    │   ├── manifest.json            # Modpack metadata
    │   ├── weapon/
    │   │   └── rlcraft_balanced.json
    │   └── armor/
    │       └── rlcraft_survival.json
    └── better_minecraft/
        ├── manifest.json
        └── weapon/
            └── bmc_standard.json
```

## Formato Preset JSON

```json
// config/devmod/presets/category/sword/diamond_tier.json
{
  "id": "diamond_tier",
  "name": "Diamond Tier Standard",
  "description": "Balanced stats for diamond-tier swords",
  "version": "1.0.0",
  "author": "DevMod",
  "scope": {
    "level": "category",
    "category": "sword",
    "itemFilter": {
      "material": ["diamond", "netherite"],
      "tags": ["minecraft:swords"]
    }
  },
  "values": {
    "baseDamage": 7.0,
    "attackSpeed": 1.6,
    "critChance": 0.15,
    "critMultiplier": 1.5,
    "durabilityMultiplier": 1.0,
    "enchantability": 10
  },
  "metadata": {
    "created": "2025-01-15T10:30:00Z",
    "modified": "2025-01-15T10:30:00Z",
    "tags": ["balanced", "pvp", "official"]
  }
}
```

```json
// config/devmod/presets/modpack/rlcraft/manifest.json
{
  "modpackId": "rlcraft",
  "modpackName": "RLCraft",
  "modpackVersion": "2.9.3",
  "devmodPresetVersion": "1.0.0",
  "description": "Preset pack for RLCraft hardcore survival",
  "author": "Shivaxi",
  "overrideGlobal": true,
  "overrideCategory": true,
  "presets": [
    "weapon/rlcraft_balanced.json",
    "armor/rlcraft_survival.json"
  ]
}
```

## Categorie Item Supportate

| Categoria | Items Inclusi | Preset Type |
|-----------|---------------|-------------|
| `sword` | Tutte le spade (vanilla + modded) | WeaponPreset |
| `axe` | Tutte le asce | WeaponPreset |
| `pickaxe` | Picconi (se weapon-enabled) | WeaponPreset |
| `bow` | Archi | WeaponPreset |
| `crossbow` | Balestre | WeaponPreset |
| `trident` | Tridenti | WeaponPreset |
| `helmet` | Tutti gli elmi | ArmorPreset |
| `chestplate` | Tutti i corpetti | ArmorPreset |
| `leggings` | Tutti i pantaloni | ArmorPreset |
| `boots` | Tutti gli stivali | ArmorPreset |
| `shield` | Scudi | ArmorPreset |

## Java Interface

```java
/**
 * Sealed hierarchy for preset scopes.
 */
public sealed interface PresetScope {
    record Global() implements PresetScope {}
    record Category(String category) implements PresetScope {}
    record Modpack(String modpackId, String category) implements PresetScope {}
}

/**
 * A preset definition with metadata and values.
 */
public record Preset<T>(
    String id,
    String name,
    String description,
    PresetScope scope,
    T values,
    PresetMetadata metadata
) {
    public record PresetMetadata(
        String version,
        String author,
        Instant created,
        Instant modified,
        List<String> tags
    ) {}
}

/**
 * Registry and resolver for presets.
 */
public final class PresetRegistry {
    private final Map<PresetScope, List<Preset<?>>> presets = new HashMap<>();

    /**
     * Load all presets from config directory.
     */
    public void loadFromConfig(Path configDir) { /* ... */ }

    /**
     * Resolve applicable presets for an item, respecting hierarchy.
     * Returns presets in priority order: MODPACK → CATEGORY → GLOBAL
     */
    public List<Preset<?>> resolveForItem(ItemStack item) {
        List<Preset<?>> result = new ArrayList<>();

        // 1. Check modpack presets (highest priority)
        String modpackId = detectActiveModpack();
        if (modpackId != null) {
            String category = ItemTypeHelper.getCategory(item);
            result.addAll(getPresets(new PresetScope.Modpack(modpackId, category)));
        }

        // 2. Check category presets
        String category = ItemTypeHelper.getCategory(item);
        result.addAll(getPresets(new PresetScope.Category(category)));

        // 3. Check global presets (lowest priority)
        result.addAll(getPresets(new PresetScope.Global()));

        return result;
    }

    /**
     * Detect active modpack from environment.
     * Checks: modpack.json, manifest.json, known mod combinations
     */
    @Nullable
    private String detectActiveModpack() { /* ... */ }

    /**
     * Save a user-created preset.
     */
    public void savePreset(Preset<?> preset, PresetScope scope) { /* ... */ }

    /**
     * Delete a preset (only user-created, not bundled).
     */
    public boolean deletePreset(String presetId, PresetScope scope) { /* ... */ }
}
```

## UI: Preset Selector

```
┌─────────────────────────────────────────────────────────────────┐
│  PRESETS                                                   [X]  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  📁 Filter: [All ▾] [🔍 Search...]                              │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ 🎮 MODPACK: RLCraft                                       │  │
│  │   └─ ⚔️ rlcraft_balanced      "RLCraft weapon balance"    │  │
│  │   └─ 🛡️ rlcraft_survival     "Survival-focused armor"    │  │
│  ├───────────────────────────────────────────────────────────┤  │
│  │ 📂 CATEGORY: Sword                                        │  │
│  │   └─ ⚔️ diamond_tier         "Diamond tier standard"      │  │
│  │   └─ ⚔️ netherite_tier       "Netherite tier standard"    │  │
│  ├───────────────────────────────────────────────────────────┤  │
│  │ 🌐 GLOBAL                                                 │  │
│  │   └─ ⚔️ vanilla_default      "Vanilla-like stats"         │  │
│  │   └─ ⚔️ balanced_pvp         "PvP balanced"               │  │
│  │   └─ ⚔️ overpowered_debug    "Debug testing"      [🔧]    │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  Selected: diamond_tier                                         │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ Preview:                                                  │  │
│  │   Base Damage: 7.0 → 8.5 (+1.5)                           │  │
│  │   Attack Speed: 1.6 → 1.8 (+0.2)                          │  │
│  │   Crit Chance: 15% → 20% (+5%)                            │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│  [Save Current as Preset]    [Delete]    [Load] [Apply]         │
└─────────────────────────────────────────────────────────────────┘
```

## Preset Operazioni

| Operazione | Scope | Descrizione |
|------------|-------|-------------|
| **Load** | Read-only | Mostra preview dei valori |
| **Apply** | Write | Applica valori all'item corrente |
| **Save As** | Write | Salva config corrente come nuovo preset |
| **Delete** | Write | Rimuove preset (solo user-created) |
| **Export** | Read | Esporta preset come file JSON |
| **Import** | Write | Importa preset da file JSON |

## Modpack Detection Strategy

```java
public final class ModpackDetector {

    private static final Map<String, Set<String>> KNOWN_MODPACKS = Map.of(
        "rlcraft", Set.of("lycanitesmobs", "iceandfire", "spartan_weaponry"),
        "better_minecraft", Set.of("create", "farmers_delight", "supplementaries"),
        "all_the_mods_9", Set.of("mekanism", "thermal", "applied_energistics_2")
    );

    /**
     * Detect modpack by checking:
     * 1. Explicit config (config/devmod/modpack.txt)
     * 2. manifest.json in minecraft root
     * 3. Known mod combinations
     */
    @Nullable
    public static String detect() {
        // 1. Explicit config
        Path explicit = FMLPaths.CONFIGDIR.get().resolve("devmod/modpack.txt");
        if (Files.exists(explicit)) {
            return Files.readString(explicit).trim();
        }

        // 2. Manifest check
        Path manifest = FMLPaths.GAMEDIR.get().resolve("manifest.json");
        if (Files.exists(manifest)) {
            JsonObject json = JsonParser.parseReader(
                Files.newBufferedReader(manifest)
            ).getAsJsonObject();
            if (json.has("name")) {
                return normalizeModpackName(json.get("name").getAsString());
            }
        }

        // 3. Mod combination detection
        Set<String> loadedMods = ModList.get().getMods().stream()
            .map(ModInfo::getModId)
            .collect(Collectors.toSet());

        for (var entry : KNOWN_MODPACKS.entrySet()) {
            if (loadedMods.containsAll(entry.getValue())) {
                return entry.getKey();
            }
        }

        return null; // No modpack detected
    }
}
```

## Bundled Default Presets

DevMod include preset di default per testing:

| Preset ID | Scope | Tipo | Descrizione |
|-----------|-------|------|-------------|
| `vanilla_default` | Global | Both | Stats vanilla-like |
| `balanced_pvp` | Global | Weapon | Bilanciato per PvP |
| `overpowered_debug` | Global | Both | Valori alti per debug |
| `tank_build` | Global | Armor | Alta difesa, bassa mobilità |
| `glass_cannon` | Global | Armor | Bassa difesa, alta mobilità |
| `diamond_tier` | Category | Weapon | Standard tier diamante |
| `netherite_tier` | Category | Weapon | Standard tier netherite |

## Stima Implementazione

| Componente | LOC | Complessità |
|------------|-----|-------------|
| PresetScope + Preset records | ~80 | Bassa |
| PresetRegistry | ~250 | Media |
| PresetSerializer (JSON) | ~150 | Media |
| ModpackDetector | ~100 | Media |
| ItemTypeHelper (extended) | ~100 | Bassa |
| PresetSelectorScreen | ~400 | Alta |
| UI integration | ~100 | Media |
| Default preset JSONs | ~20 files | Bassa |
| **Totale** | **~1180** | **Media** |

**Priorità:** P2 - Implementare DOPO single editor (Fase 0-3), PRIMA di batch edit.