# Weapon Type Detection & Modded Support

> Last updated: 2025-12-26
> Status: HISTORICAL (design system snapshot)

Supporto per armi non-standard (asce, tridenti, archi, balestre) e rilevamento automatico armi moddate.

## Weapon Type Support Matrix

| Weapon Type | Java Class | Module | Tab Speciale | Phase |
|-------------|------------|--------|--------------|-------|
| **Sword** | `SwordItem` | WeaponModule | - | MVP |
| **Axe** | `AxeItem` | WeaponModule | - | MVP |
| **Pickaxe** (combat) | `PickaxeItem` | WeaponModule | - | MVP (configurable) |
| **Mace** | `MaceItem` | WeaponModule | MACE | Phase 1 |
| **Trident** | `TridentItem` | WeaponModule | TRIDENT | Phase 2 |
| **Bow** | `BowItem` | RangedModule | BOW | Phase 2 |
| **Crossbow** | `CrossbowItem` | RangedModule | CROSSBOW | Phase 2 |
| **Shield** | `ShieldItem` | ArmorModule | SHIELD | Phase 3 |
| **Modded Melee** | Tag/Attribute | WeaponModule | GENERIC | MVP |
| **Modded Ranged** | Tag/Attribute | RangedModule | GENERIC | Phase 2 |

## Detection Priority Chain

```java
/**
 * Weapon type detection with fallback chain.
 * Priority: Blacklist → Whitelist → Class → Tags → Attributes → Fallback
 */
public final class WeaponTypeDetector {

    /**
     * Detected weapon type with confidence level.
     */
    public record DetectionResult(
        WeaponType type,
        DetectionMethod method,
        float confidence,  // 0.0 - 1.0
        @Nullable String warning
    ) {
        public boolean isHighConfidence() {
            return confidence >= 0.8f;
        }
    }

    public enum WeaponType {
        // Melee
        SWORD,
        AXE,
        MACE,
        TRIDENT,
        PICKAXE_COMBAT,
        GENERIC_MELEE,

        // Ranged
        BOW,
        CROSSBOW,
        GENERIC_RANGED,

        // Defense
        SHIELD,

        // Unknown
        UNKNOWN,
        NOT_A_WEAPON
    }

    public enum DetectionMethod {
        CLASS_INSTANCEOF,      // Highest confidence (1.0 for most, 0.9 for MaceItem)
        ITEM_TAG,              // High confidence (0.8)
        ATTRIBUTE_HEURISTIC,   // Medium confidence (0.4-0.55)
        CONFIG_WHITELIST,      // Explicit override (1.0)
        CONFIG_BLACKLIST,      // Explicit exclusion (1.0)
        FALLBACK_GENERIC       // Lowest confidence
    }

    /**
     * Detect weapon type with full details (method, confidence, warning).
     */
    public static DetectionResult detectDetailed(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new DetectionResult(WeaponType.NOT_A_WEAPON,
                DetectionMethod.FALLBACK_GENERIC, 1.0f, null);
        }

        Item item = stack.getItem();
        boolean heuristicsEnabled = EditorClientConfig.EDITOR_WEAPON_HEURISTIC_ENABLED.get();
        boolean allowPickaxe = EditorClientConfig.EDITOR_WEAPON_TREAT_PICKAXE.get();

        // PRIORITY 1: Config blacklist (explicit exclusion)
        if (ConfigWeaponLists.isBlacklisted(item)) {
            return new DetectionResult(WeaponType.NOT_A_WEAPON,
                DetectionMethod.CONFIG_BLACKLIST, 1.0f, null);
        }

        // PRIORITY 2: Config whitelist (explicit override)
        WeaponType whitelistType = ConfigWeaponLists.getWhitelist(item);
        if (whitelistType != null) {
            return new DetectionResult(whitelistType,
                DetectionMethod.CONFIG_WHITELIST, 1.0f, null);
        }

        // PRIORITY 3: Java class hierarchy (instanceof)
        DetectionResult classResult = detectByClass(item, allowPickaxe);
        if (classResult != null) return classResult;

        // PRIORITY 4: Item tags (data-driven)
        DetectionResult tagResult = detectByTags(stack);
        if (tagResult != null) return tagResult;

        // PRIORITY 5: Attribute/Component heuristics (if enabled)
        if (heuristicsEnabled) {
            DetectionResult attrResult = detectByAttributes(stack);
            if (attrResult != null) return attrResult;

            // Fallback generic melee if attack damage present
            if (hasAttackDamage(stack)) {
                return new DetectionResult(WeaponType.GENERIC_MELEE,
                    DetectionMethod.ATTRIBUTE_HEURISTIC, 0.5f,
                    "Detected via attack damage attribute");
            }
        }

        // FALLBACK: Not a weapon
        return new DetectionResult(WeaponType.NOT_A_WEAPON,
            DetectionMethod.FALLBACK_GENERIC, 1.0f, null);
    }

    /**
     * Backward-compatible detection returning only the type.
     */
    public static WeaponType detect(ItemStack stack) {
        return detectDetailed(stack).type();
    }

    // Helper methods
    public static boolean isRanged(WeaponType type) {
        return type == WeaponType.BOW || type == WeaponType.CROSSBOW
            || type == WeaponType.GENERIC_RANGED;
    }

    public static boolean isMelee(WeaponType type) {
        return switch (type) {
            case SWORD, AXE, PICKAXE_COMBAT, MACE, TRIDENT, GENERIC_MELEE -> true;
            default -> false;
        };
    }

    public static boolean isShield(WeaponType type) {
        return type == WeaponType.SHIELD;
    }
}
```

## Confidence Levels by Detection Method

| Method | Confidence | Description |
|--------|------------|-------------|
| CLASS_INSTANCEOF (SwordItem, AxeItem, etc.) | 1.0 | Direct class match |
| CLASS_INSTANCEOF (MaceItem) | 0.9 | Reflection-based match |
| CLASS_INSTANCEOF (PickaxeItem) | 0.6 | Configurable, lower confidence |
| ITEM_TAG | 0.8 | Data-driven tag match |
| ATTRIBUTE_HEURISTIC (name keywords) | 0.5-0.55 | Name + attack_damage |
| ATTRIBUTE_HEURISTIC (generic ranged) | 0.4 | Name keywords only |
| CONFIG_WHITELIST | 1.0 | Explicit user override |
| CONFIG_BLACKLIST | 1.0 | Explicit user exclusion |

## Tag Definitions

```java
/**
 * DevMod item tags for weapon detection.
 * Located in: com.devmod.tags.ModTags
 */
public final class ModTags {
    public static final class Items {
        // Explicit opt-in tags (modders can add their items)
        public static final TagKey<Item> EDITABLE_MELEE_WEAPONS =
            tag("editable_melee_weapons");
        public static final TagKey<Item> EDITABLE_RANGED_WEAPONS =
            tag("editable_ranged_weapons");
        public static final TagKey<Item> EDITABLE_SHIELDS =
            tag("editable_shields");

        // Generic category tags
        public static final TagKey<Item> MELEE_WEAPONS =
            tag("melee_weapons");
        public static final TagKey<Item> RANGED_WEAPONS =
            tag("ranged_weapons");

        // Exclusion tag
        public static final TagKey<Item> NOT_EDITABLE =
            tag("not_editable");

        private static TagKey<Item> tag(String name) {
            return TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(DevMod.MODID, name));
        }
    }
}
```

## Default Tag JSON Files

```
data/devmod/tags/item/
├── editable_melee_weapons.json    # Empty, for modders to populate
├── editable_ranged_weapons.json   # Empty, for modders to populate
├── editable_shields.json          # Empty, for modders to populate
├── melee_weapons.json             # Includes common convention tags
├── ranged_weapons.json            # Includes #c:ranged_weapons
└── not_editable.json              # Items to exclude from editor
```

```json
// data/devmod/tags/item/melee_weapons.json
{
  "replace": false,
  "values": [
    { "id": "#c:tools/swords", "required": false },
    { "id": "#c:tools/axes", "required": false },
    { "id": "#forge:tools/melee_weapon", "required": false },
    { "id": "#neoforge:melee_weapons", "required": false }
  ]
}
```

```json
// data/devmod/tags/item/ranged_weapons.json
{
  "replace": false,
  "values": [
    { "id": "#c:ranged_weapons", "required": false }
  ]
}
```

> **Nota:** Il formato `{ "id": "...", "required": false }` permette ai tag
> opzionali di non causare errori se la mod che li definisce non è presente.

## Config Whitelist/Blacklist

I file di whitelist/blacklist sono creati dinamicamente in `config/devmod/`:

```json
// config/devmod/weapon_whitelist.json
{
  "_comment": "Items explicitly whitelisted for the DevMod weapon editor",
  "melee": [
    "somemod:custom_sword",
    "anothermod:battle_axe"
  ],
  "ranged": [
    "somemod:magic_staff"
  ],
  "mace": [],
  "trident": []
}
```

```json
// config/devmod/weapon_blacklist.json
{
  "_comment": "Items explicitly excluded from weapon editor",
  "items": [
    "minecraft:stick",
    "somemod:decorative_sword"
  ]
}
```

### Aggiungere items via API

```java
// Add item to whitelist programmatically
WeaponTypeDetector.addToWhitelist(myItem, WeaponType.GENERIC_MELEE);

// Reload lists from disk
WeaponTypeDetector.reloadWeaponLists();
```

## Config Options

Le opzioni sono definite in `EditorClientConfig` (config **client-side**):

```toml
# config/devmod-client.toml

[editor]
# Enable attribute-based heuristic detection for unknown items
weaponDetectionHeuristic = true

# Minimum confidence level to show editor without warning (0.0 - 1.0)
weaponDetectionMinConfidence = 0.8

# Treat pickaxes as weapons (shows in weapon editor)
treatPickaxeAsWeapon = false

# Log detection results for debugging
weaponDetectionLog = false
```

| Config Key | Default | Description |
|------------|---------|-------------|
| `weaponDetectionHeuristic` | `true` | Abilita detection basata su attributi |
| `weaponDetectionMinConfidence` | `0.8` | Soglia minima per evitare warning |
| `treatPickaxeAsWeapon` | `false` | Include pickaxe nel weapon editor |
| `weaponDetectionLog` | `false` | Log detection per debug |

## Weapon-Specific Tabs

### MACE Tab (Phase 1)

```
┌─────────────────────────────────────────────────────────────────┐
│  TAB: MACE (MaceItem specific)                                  │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ SMASH ATTACK                                             │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Fall Damage Bonus    [━━━━━━━━━━] +3.0   per block      │   │
│  │ Max Bonus Damage     [━━━━━━━━━━] 150.0  cap            │   │
│  │ Fall Damage Negation [✓]                                │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ KNOCKBACK                                                │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Smash Knockback      [━━━━━━━━━━] 1.5    radius         │   │
│  │ Smash AOE Damage     [━━━━━━━━━━] 50%    of hit         │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### TRIDENT Tab (Phase 2)

```
┌─────────────────────────────────────────────────────────────────┐
│  TAB: TRIDENT (TridentItem specific)                            │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ THROW ATTACK                                             │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Throw Damage         [━━━━━━━━━━] 8.0                   │   │
│  │ Throw Speed          [━━━━━━━━━━] 2.5    blocks/tick    │   │
│  │ Return Speed         [━━━━━━━━━━] 1.5    (Loyalty)      │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ RIPTIDE                                                  │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Riptide Distance     [━━━━━━━━━━] 12.0   blocks         │   │
│  │ Riptide Damage       [━━━━━━━━━━] 6.0    on collision   │   │
│  │ Requires Water       [✓]                                │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Module Selection Logic

La selezione del modulo in `ItemEditorScreen.resolveModule()`:

```java
private EditorModule resolveModule(ItemStack stack, EditorStartTab requested) {
    return switch (requested) {
        case WEAPON -> {
            var detection = WeaponTypeDetector.detectDetailed(stack);
            if (detection.type() == WeaponType.NOT_A_WEAPON) {
                LOGGER.warn("[ItemEditor] Requested WEAPON but not a weapon; fallback to GENERAL.");
                yield new GeneralModule();
            }
            // Auto-select between melee and ranged
            if (WeaponTypeDetector.isRanged(detection.type())) {
                yield new RangedModule();
            }
            yield new WeaponModule();
        }
        case ARMOR -> new ArmorModule();
        case GENERAL -> {
            // Auto-detect if item is actually armor or weapon
            if (ArmorConfigManager.isArmor(stack)) {
                yield new ArmorModule();
            }
            var detection = WeaponTypeDetector.detectDetailed(stack);
            if (detection.type() != WeaponType.NOT_A_WEAPON) {
                if (WeaponTypeDetector.isRanged(detection.type())) {
                    yield new RangedModule();
                }
                yield new WeaponModule();
            }
            yield new GeneralModule();
        }
    };
}
```

## Low Confidence Warning UI

Quando la detection ha confidence < `weaponDetectionMinConfidence`:

```
┌─────────────────────────────────────────────────────────────────┐
│  ⚠️ LOW CONFIDENCE DETECTION                                    │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ Item: somemod:mystery_blade                             │   │
│  │ Detected as: GENERIC_MELEE                              │   │
│  │ Method: ATTRIBUTE_HEURISTIC                             │   │
│  │ Confidence: 50%                                         │   │
│  │                                                         │   │
│  │ This item was detected as a weapon based on its         │   │
│  │ attributes, but may not behave as expected.             │   │
│  │                                                         │   │
│  │ To improve detection:                                   │   │
│  │ • Add to #devmod:editable_melee_weapons tag             │   │
│  │ • Or add to config/devmod/weapon_whitelist.json         │   │
│  │                                                         │   │
│  │ [Continue Anyway]  [Add to Whitelist]  [Cancel]         │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Implementation Phases

| Phase | Weapon Types | Detection Methods |
|-------|--------------|-------------------|
| **MVP** | Sword, Axe, Pickaxe (opt-in), Generic Melee | Class + Tags + Attributes |
| **Phase 1** | + Mace | + MACE tab |
| **Phase 2** | + Trident, Bow, Crossbow | + RangedModule |
| **Phase 3** | + Shield | + Shield in ArmorModule |
| **Future** | Custom weapon types | + Plugin API |

## File Correlati

| File | Responsabilità |
|------|----------------|
| `WeaponTypeDetector.java` | Detection logic, whitelist/blacklist, helper methods |
| `ModTags.java` | Tag definitions |
| `EditorClientConfig.java` | Config options (client-side) |
| `ConfigPaths.java` | Whitelist/blacklist file paths |
| `ItemEditorScreen.java` | Module selection based on detection |
