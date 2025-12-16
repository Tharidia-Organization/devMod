# Weapon Type Detection & Modded Support

Supporto per armi non-standard (asce, tridenti, archi, balestre) e rilevamento automatico armi moddate.

## Weapon Type Support Matrix

| Weapon Type | Java Class | Module | Tab Speciale | Phase |
|-------------|------------|--------|--------------|-------|
| **Sword** | `SwordItem` | WeaponModule | - | MVP |
| **Axe** | `AxeItem` | WeaponModule | - | MVP |
| **Pickaxe** (combat) | `PickaxeItem` | WeaponModule | - | MVP |
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
 * Priority: Class → Tags → Attributes → Config → Fallback
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
        CLASS_INSTANCEOF,      // Highest confidence
        ITEM_TAG,              // High confidence
        ATTRIBUTE_HEURISTIC,   // Medium confidence
        CONFIG_WHITELIST,      // Explicit override
        CONFIG_BLACKLIST,      // Explicit exclusion
        FALLBACK_GENERIC       // Lowest confidence
    }

    /**
     * Detect weapon type for an ItemStack.
     */
    public static DetectionResult detect(ItemStack stack) {
        if (stack.isEmpty()) {
            return new DetectionResult(WeaponType.NOT_A_WEAPON, null, 1.0f, null);
        }

        Item item = stack.getItem();

        // PRIORITY 1: Config blacklist (explicit exclusion)
        if (isBlacklisted(item)) {
            return new DetectionResult(WeaponType.NOT_A_WEAPON,
                DetectionMethod.CONFIG_BLACKLIST, 1.0f, null);
        }

        // PRIORITY 2: Config whitelist (explicit override)
        WeaponType whitelistType = getWhitelistType(item);
        if (whitelistType != null) {
            return new DetectionResult(whitelistType,
                DetectionMethod.CONFIG_WHITELIST, 1.0f, null);
        }

        // PRIORITY 3: Java class hierarchy (instanceof)
        DetectionResult classResult = detectByClass(item);
        if (classResult != null) {
            return classResult;
        }

        // PRIORITY 4: Item tags (data-driven)
        DetectionResult tagResult = detectByTags(stack);
        if (tagResult != null) {
            return tagResult;
        }

        // PRIORITY 5: Attribute/Component heuristics
        DetectionResult attrResult = detectByAttributes(stack);
        if (attrResult != null) {
            return attrResult;
        }

        // FALLBACK: Not a weapon
        return new DetectionResult(WeaponType.NOT_A_WEAPON, null, 1.0f, null);
    }
}
```

## Tag Definitions

```java
/**
 * DevMod item tags for weapon detection.
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
            return TagKey.create(Registries.ITEM, DevMod.rl(name));
        }
    }
}
```

## Default Tag JSON Files

```
data/devmod/tags/item/
├── editable_melee_weapons.json    # Empty, for modders to populate
├── editable_ranged_weapons.json   # Empty, for modders to populate
├── melee_weapons.json             # Includes #c:tools/swords, #c:tools/axes
├── ranged_weapons.json            # Includes #c:ranged_weapons
└── not_editable.json              # Items to exclude from editor
```

```json
// data/devmod/tags/item/melee_weapons.json
{
  "replace": false,
  "values": [
    "#c:tools/swords",
    "#c:tools/axes",
    "#forge:tools/melee_weapon",
    "#neoforge:melee_weapons"
  ]
}
```

## Config Whitelist/Blacklist

```json
// config/devmod/weapon_whitelist.json
{
  "_comment": "Items to explicitly treat as weapons (overrides detection)",
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
  "_comment": "Items to explicitly exclude from weapon editor",
  "items": [
    "minecraft:stick",
    "somemod:decorative_sword"
  ]
}
```

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

## Module Selection Logic (stato attuale)

- Le varianti `WeaponVariant` e `RangedVariant` sono supportate a livello di selezione e tab placeholder (mace/trident/bow/crossbow).
- La selezione modulo in `ItemEditorScreen` sceglie la variante in base alla detection; shield usa ancora `ArmorModule` standard con tab “Shield” placeholder.
- Low-confidence: warning/dialog quando la detection è heuristica sotto la soglia config.

## Low Confidence Warning UI

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
| **MVP** | Sword, Axe, Generic Melee | Class + Tags + Attributes |
| **Phase 1** | + Mace | + MACE tab |
| **Phase 2** | + Trident, Bow, Crossbow | + RangedModule |
| **Phase 3** | + Shield | + Shield in ArmorModule |
| **Future** | Custom weapon types | + Plugin API |

## Config Options

```toml
# config/devmod-server.toml

[editor]
# Enable attribute-based heuristic detection for unknown items
weaponDetectionHeuristic = true

# Minimum confidence level to show editor without warning
weaponDetectionMinConfidence = 0.8

# Treat pickaxes as weapons (shows in weapon editor)
treatPickaxeAsWeapon = false

# Log detection results for debugging
weaponDetectionLog = true
```
