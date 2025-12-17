# Template & Preset Architecture

## Overview

Il sistema preset di DevMod supporta una **gerarchia a 3 livelli** per la risoluzione dei preset, con supporto per modpack detection automatico e preset bundled.

## Architettura Sistema Preset

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         PRESET SYSTEM (v2 - Hierarchical)                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                        PresetRegistry (Singleton)                    │   │
│  │  ┌─────────────────┬─────────────────┬─────────────────────────┐   │   │
│  │  │ MODPACK (P=3)   │ CATEGORY (P=2)  │ GLOBAL (P=1)            │   │   │
│  │  │ rlcraft/sword/* │ category/sword/*│ global/*                │   │   │
│  │  └─────────────────┴─────────────────┴─────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                      │                                      │
│                              resolveForItem()                               │
│                                      ▼                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                          PresetBridge                                │   │
│  │         RegistryPreset ◄──────────────────► PresetData              │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                      │                                      │
│                                      ▼                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    ItemEditorDataManager.PresetData                  │   │
│  │                     (User presets + converted registry)              │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Gerarchia Scope (PresetScope)

```java
public sealed interface PresetScope permits
    PresetScope.Global,
    PresetScope.Category,
    PresetScope.Modpack {

    record Global() implements PresetScope {}          // Priority 1
    record Category(String category) implements PresetScope {}  // Priority 2
    record Modpack(String modpackId, String category) implements PresetScope {} // Priority 3

    default int priority() {
        return switch (this) {
            case Modpack m -> 3;   // Highest - modpack-specific
            case Category c -> 2;  // Medium - category-specific
            case Global g -> 1;    // Lowest - global defaults
        };
    }

    default String label() {
        return switch (this) {
            case Modpack m -> "MODPACK: " + m.modpackId();
            case Category c -> "CATEGORY: " + c.category();
            case Global g -> "GLOBAL";
        };
    }
}
```

## Struttura File System

```
config/devmod/
├── presets.json                    # User presets (legacy format)
├── modpack.txt                     # Optional: explicit modpack ID
├── presets/
│   ├── global/                     # Global presets (all items)
│   │   ├── vanilla_default.json
│   │   ├── balanced_pvp.json
│   │   └── overpowered_debug.json
│   ├── category/                   # Category-specific presets
│   │   ├── sword/
│   │   │   └── diamond_tier.json
│   │   ├── armor/
│   │   │   ├── tank_build.json
│   │   │   └── glass_cannon.json
│   │   └── bow/
│   └── modpack/                    # Modpack-specific presets
│       ├── rlcraft/
│       │   └── rlcraft_balanced.json
│       └── better_minecraft/
│
resources/data/devmod/presets/      # Bundled presets (copied on first run)
├── vanilla_default.json
├── balanced_pvp.json
├── overpowered_debug.json
├── tank_build.json
├── glass_cannon.json
└── diamond_tier.json
```

## Formato Preset JSON (Registry Format)

```json
{
  "id": "balanced_pvp",
  "name": "Balanced PvP",
  "description": "Balanced stats for player vs player combat",
  "version": "1.0.0",
  "author": "DevMod",
  "category": "weapon",
  "scope": "global",
  "values": {
    "headMult": 1.5,
    "bodyMult": 1.0,
    "limbMult": 0.75,
    "baseDamage": 6.0,
    "attackSpeed": 1.8,
    "critChance": 0.15,
    "critMultiplier": 1.75,
    "armorPen": 0.1,
    "lifesteal": 0.0,
    "knockback": 0.2,
    "damageBonus": 0.0,
    "range": 3.5,
    "blockBreakSpeed": 1.0,
    "durabilityMult": 1.0,
    "enchantability": 12.0
  },
  "tags": ["pvp", "balanced", "official"]
}
```

## ModpackDetector

Rileva automaticamente il modpack in uso tramite 3 strategie:

```java
public final class ModpackDetector {
    // Strategy 1: Explicit config file
    // config/devmod/modpack.txt -> "rlcraft"

    // Strategy 2: Manifest detection
    // manifest.json in game root

    // Strategy 3: Mod signature detection
    private static final Map<String, Set<String>> KNOWN_MODPACKS = Map.of(
        "rlcraft", Set.of("lycanitesmobs", "iceandfire", "spartanweaponry"),
        "better_minecraft", Set.of("create", "farmers_delight", "supplementaries"),
        "all_the_mods_9", Set.of("mekanism", "thermal", "applied_energistics_2")
        // ... more modpacks
    );
}
```

## Core Classes

| File | LOC | Responsabilita |
|------|-----|----------------|
| `PresetScope.java` | ~53 | Sealed interface per scope hierarchy |
| `ModpackDetector.java` | ~201 | Rilevamento automatico modpack |
| `PresetRegistry.java` | ~480 | Registry centrale, loading, risoluzione |
| `PresetBridge.java` | ~238 | Conversione RegistryPreset ↔ PresetData |
| `PresetSelectorOverlay.java` | ~789 | UI overlay per selezione/gestione preset |

### PresetRegistry

```java
public final class PresetRegistry {
    private static PresetRegistry INSTANCE;
    private final Map<PresetScope, List<RegistryPreset>> presets;
    private String detectedModpack = null;

    // Singleton
    public static void init() { ... }
    public static PresetRegistry getInstance() { ... }

    // Loading
    public void loadFromConfig() { ... }
    private void copyBundledPresets(Path targetDir) { ... }

    // Resolution
    public List<RegistryPreset> resolveForItem(ItemStack item) { ... }
    public List<RegistryPreset> getPresetsForCategory(String category) { ... }
    public List<RegistryPreset> getAllPresets() { ... }

    // Records
    public record RegistryPreset(
        String id, String name, String description,
        PresetScope scope, String category,
        Map<String, Object> values, PresetMetadata metadata
    ) {}

    public record PresetMetadata(
        String version, String author, Instant created, List<String> tags
    ) {}
}
```

### PresetBridge

```java
public class PresetBridge {
    // RegistryPreset → PresetData (per UI compatibility)
    public static ItemEditorDataManager.PresetData toPresetData(RegistryPreset rp) { ... }

    // PresetData → RegistryPreset (per salvataggio in registry format)
    public static RegistryPreset toRegistryPreset(ItemEditorDataManager.PresetData pd) { ... }

    // Helper per generare ID univoci
    public static String generateId(String name) { ... }
}
```

## UI: PresetSelectorOverlay

Estende `BaseOverlay` per un'esperienza utente coerente con altri overlay dell'editor.

### Layout

```
┌─────────────────────────────────────────────────────────────────┐
│  PRESETS                                                   [X]  │
├─────────────────────────────────────────────────────────────────┤
│  Category: sword                                                │
├─────────────────────────────────────────────────────────────────┤
│  [Search presets...]                                            │
├─────────────────────────────────────────────────────────────────┤
│  ▌ rlcraft_balanced        weapon      │ ← Orange = MODPACK     │
│  ▌ diamond_tier            sword       │ ← Blue = CATEGORY      │
│  ▌ vanilla_default         weapon      │ ← Green = GLOBAL       │
│  ▌ balanced_pvp            weapon      │                        │
│  ▌ my_custom_preset        sword [User]│ ← Light green = USER   │
├─────────────────────────────────────────────────────────────────┤
│  Selected: diamond_tier                                         │
│  CATEGORY: sword                                                │
│  "Diamond tier standard weapon stats"                           │
├─────────────────────────────────────────────────────────────────┤
│  [Save Current]         [Delete]              [Apply]           │
└─────────────────────────────────────────────────────────────────┘
```

### Scope Color Indicators

| Scope | Color | Hex |
|-------|-------|-----|
| MODPACK | Orange | `0xFFFF9900` |
| CATEGORY | Blue | `0xFF66AAFF` |
| GLOBAL | Green | `0xFF88FF88` |
| GLOBAL (User) | Light Green | `0xFFAAFF88` |

### Features

- **VirtualizedList** per performance con molti preset
- **Search** real-time con filtro nome/descrizione/scope
- **Preview** del preset selezionato
- **Scope indicator** colorato per ogni riga
- **Delete** solo per user presets
- **Rename inline** per user presets (bottone o F2)
- **Double-click** per applicare rapidamente
- **Keyboard navigation** completa (frecce, Enter, Escape, Delete, F2, Ctrl+F)

### Keyboard Shortcuts

| Key | Action |
|-----|--------|
| `↑` / `↓` | Navigate list |
| `Enter` | Apply selected preset |
| `Delete` | Delete selected user preset |
| `F2` | Start rename (user presets only) |
| `Escape` | Cancel rename / Close overlay |
| `Ctrl+F` | Focus search box |

### Rename Mode

Quando si preme **F2** o il bottone **Rename** su un preset utente:

1. La **preview box** si trasforma in **rename input box**
2. Il testo del nome corrente appare nel campo
3. Si può digitare il nuovo nome
4. **Enter** conferma la rinomina
5. **Escape** annulla

```
┌─────────────────────────────────────────────────────────────────┐
│  Rename preset:                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ my_new_preset_name█                                      │   │
│  └─────────────────────────────────────────────────────────┘   │
│  Press Enter to confirm, Escape to cancel                       │
└─────────────────────────────────────────────────────────────────┘
```

## Integration Points

### ItemEditorScreen

```java
// Field
private PresetSelectorOverlay presetSelectorOverlay;

// In init()
this.presetSelectorOverlay = new PresetSelectorOverlay();
this.presetSelectorOverlay.setContext(getActiveItemType());
this.presetSelectorOverlay.onClose(this::closeOverlay);
this.presetSelectorOverlay.onApply(this::applyPreset);
this.presetSelectorOverlay.onDelete(this::deletePreset);
this.presetSelectorOverlay.onRename(this::renamePreset);  // NEW
this.presetSelectorOverlay.onSaveCurrent(this::saveCurrentAsPreset);

// In render() when PRESETS overlay active
presetSelectorOverlay.render(graphics, font, width, height, mouseX, mouseY);
```

#### Rename Handler

```java
private void renamePreset(ItemEditorDataManager.PresetData preset, String newName) {
    if (preset == null || preset.name == null || newName == null || newName.isBlank()) return;
    String oldName = preset.name;

    // Update the preset with new name
    preset.name = newName.trim();
    ItemEditorDataManager.INSTANCE.deletePreset(oldName);
    ItemEditorDataManager.INSTANCE.savePreset(preset);
    ItemEditorDataManager.INSTANCE.addHistoryEntry("preset_rename", ...);

    if (presetSelectorOverlay != null) {
        presetSelectorOverlay.refreshPresets();
    }
}
```

### MultiEditPanel

```java
private List<ItemEditorDataManager.PresetData> availablePresets() {
    List<ItemEditorDataManager.PresetData> result = new ArrayList<>();
    HashSet<String> seenNames = new HashSet<>();

    // 1. Load from PresetRegistry (hierarchical - higher priority)
    List<PresetRegistry.RegistryPreset> registryPresets =
        PresetRegistry.getInstance().getPresetsForCategory(type);
    for (var rp : registryPresets) {
        result.add(PresetBridge.toPresetData(rp));
    }

    // 2. Load user presets from ItemEditorDataManager
    List<ItemEditorDataManager.PresetData> userPresets =
        ItemEditorDataManager.INSTANCE.getPresetsForItemType(type);
    // ... merge avoiding duplicates

    return result;
}
```

### DevMod Bootstrap

```java
// In DevMod constructor, after ModIntegrationManager.init()
PresetRegistry.init();
PresetRegistry.getInstance().loadFromConfig();
```

## Bundled Presets

6 preset inclusi di default, copiati in `config/devmod/presets/global/` al primo avvio:

| Preset | Category | Description |
|--------|----------|-------------|
| `vanilla_default` | weapon | Valori vanilla-like |
| `balanced_pvp` | weapon | Bilanciato per PvP |
| `overpowered_debug` | weapon | Valori estremi per testing |
| `tank_build` | armor | Alta difesa |
| `glass_cannon` | armor | Alta offesa, bassa difesa |
| `diamond_tier` | weapon | Standard tier diamante |

## Legacy Compatibility

Il sistema mantiene **backward compatibility** con `ItemEditorDataManager.PresetData`:

- User presets esistenti in `config/devmod/presets.json` continuano a funzionare
- `PresetBridge` converte tra i due formati
- UI unificata mostra entrambi i tipi

## Stato Implementazione

| Componente | File | Stato |
|------------|------|-------|
| PresetScope (sealed interface) | `systems/PresetScope.java` | ✅ Completo |
| ModpackDetector | `systems/ModpackDetector.java` | ✅ Completo |
| PresetRegistry | `systems/PresetRegistry.java` | ✅ Completo |
| PresetBridge | `systems/PresetBridge.java` | ✅ Completo |
| PresetSelectorOverlay | `systems/PresetSelectorOverlay.java` | ✅ Completo |
| Bundled presets JSON | `resources/data/devmod/presets/` | ✅ Completo |
| ItemEditorScreen integration | `ItemEditorScreen.java` | ✅ Completo |
| MultiEditPanel integration | `systems/MultiEditPanel.java` | ✅ Completo |
| DevMod bootstrap | `DevMod.java` | ✅ Completo |
| **Rename inline** | `PresetSelectorOverlay.java` | ✅ Completo |
| **Test funzionali** | `testing/DevModPresetTestCases.java` | ✅ Completo |

## Test Funzionali

I test funzionali per il sistema preset sono definiti in `DevModPresetTestCases.java` e vengono eseguiti automaticamente tramite il framework QA Testing.

### Test Categories

| Test ID | Nome | Priorità |
|---------|------|----------|
| `preset_scope_hierarchy` | PresetScope Hierarchy | HIGH |
| `preset_scope_labels` | PresetScope Labels | MEDIUM |
| `preset_registry_singleton` | PresetRegistry Singleton | CRITICAL |
| `preset_registry_bundled_presets` | Bundled Presets Loaded | HIGH |
| `preset_registry_category_filter` | Category Filtering | HIGH |
| `preset_bridge_to_preset_data` | PresetBridge toPresetData | HIGH |
| `preset_bridge_to_registry_preset` | PresetBridge toRegistryPreset | HIGH |
| `preset_bridge_id_generation` | PresetBridge ID Generation | MEDIUM |
| `modpack_detector_init` | ModpackDetector Initialization | MEDIUM |
| `user_preset_save` | User Preset Save | HIGH |
| `user_preset_delete` | User Preset Delete | HIGH |
| `user_preset_rename` | User Preset Rename | MEDIUM |
| `preset_overlay_data_flow` | PresetSelectorOverlay Data Flow | HIGH |
| `preset_priority_resolution` | Preset Priority Resolution | HIGH |

### Running Tests

I test sono integrati nel `DynamicTestGenerator` e vengono eseguiti come parte dei DevMod Core Tests:

```java
// In DynamicTestGenerator.DevModCoreTestTemplate
tests.addAll(DevModPresetTestCases.generateTestCases());
```

## Future Evolutions (P3)

1. **Import/Export** - Importare/esportare preset come file JSON
2. **Preview diff** - Mostrare differenze prima di applicare
3. **Preset sharing** - Condividere preset tramite URL/codice
4. **Cloud sync** - Sincronizzazione preset cross-instance
5. **Modpack preset packs** - Preset bundle scaricabili per modpack popolari
