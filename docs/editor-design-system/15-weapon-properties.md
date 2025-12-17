# Weapon Properties Architecture
## Data Components, Attributes, Type Detection, Modded Support

> **Sezioni 2.20-2.21** del Design System - Architettura proprietà armi e supporto modded

---

## Design Principles

| Principio | Decisione | Rationale |
|-----------|-----------|-----------|
| **Source of Truth** | `devmod:*` namespace | Controllo totale, nessuna dipendenza esterna |
| **Pufferfish Compat** | Mapping opzionale | Compatibilità senza hard dependency |
| **Tool Rules** | Editable (Tool tab) | UI applica componente `tool`, clamp server |
| **Damage Types** | Predefined bonuses MVP | Custom types = combat framework, troppo complesso |
| **Ranged Weapons** | Fase 2 | Stabilizzare melee first, ranged ha più edge cases |

## Property Tiers

### TIER 1: Vanilla Core (Data Components)

| Property | Attribute ID | Default | Range | Tab |
|----------|--------------|---------|-------|-----|
| **Base Damage** | `minecraft:attack_damage` | 1 | 0–2048 | STATS |
| **Attack Speed** | `minecraft:attack_speed` | 4 | 0–1024 | STATS |
| **Attack Knockback** | `minecraft:attack_knockback` | 0 | 0–5 | STATS |
| **Entity Reach** | `minecraft:entity_interaction_range` | 2.5 | 0–64 | STATS |
| **Sweeping Ratio** | `minecraft:sweeping_damage_ratio` | 0 | 0–1 | STATS |

### TIER 2: Vanilla Item Properties

| Property | Component | Default | Note | Tab |
|----------|-----------|---------|------|-----|
| **Max Durability** | `minecraft:max_damage` | varies | Per-instance override | DURABILITY |
| **Current Damage** | `minecraft:damage` | 0 | Current wear | DURABILITY |
| **Repair Cost** | `minecraft:repair_cost` | 0 | Anvil penalty | DURABILITY |
| **Unbreakable** | `minecraft:unbreakable` | false | Infinite durability | DURABILITY |
| **Tool Rules** | `minecraft:tool` | - | Mining speed per block tag (editable: default speed, damage/block, 3 rules) | ADVANCED |

### TIER 3: DevMod Custom Attributes

| Property | Attribute ID | Default | Range | Tab |
|----------|--------------|---------|-------|-----|
| **Crit Chance** | `devmod:crit_chance` | 0 | 0–100 | COMBAT |
| **Crit Multiplier** | `devmod:crit_multiplier` | 1.5 | 1.0–5.0 | COMBAT |
| **Armor Shred** | `devmod:armor_shred` | 0 | 0–66 | COMBAT |
| **Life Steal** | `devmod:life_steal` | 0 | 0–100 | COMBAT |
| **Damage Bonus** | `devmod:damage_bonus` | 0 | 0–100 | STATS |

### TIER 4: Damage Type Bonuses

| Property | Target/Tag | Default | Range | Tab |
|----------|------------|---------|-------|-----|
| **vs Undead** | Entity type check | 0 | 0–200% | DAMAGE TYPES |
| **vs Arthropods** | Entity type check | 0 | 0–200% | DAMAGE TYPES |
| **vs Players** | Player entity | 0 | 0–200% | DAMAGE TYPES |
| **Fire Bonus** | `#minecraft:is_fire` | 0 | 0–200% | DAMAGE TYPES |
| **True Damage** | `#bypasses_armor` | 0 | 0–100% | DAMAGE TYPES |

## Attribute Registration

```java
/**
 * DevMod custom attributes for weapons.
 * Source of truth: devmod namespace.
 */
public final class ModAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES =
        DeferredRegister.create(Registries.ATTRIBUTE, DevMod.MODID);

    // Combat attributes
    public static final DeferredHolder<Attribute, Attribute> CRIT_CHANCE = ATTRIBUTES.register("crit_chance",
        () -> new RangedAttribute("attribute.devmod.crit_chance", 0.0D, 0.0D, 100.0D)
            .setSyncable(true));

    public static final DeferredHolder<Attribute, Attribute> CRIT_MULTIPLIER = ATTRIBUTES.register("crit_multiplier",
        () -> new RangedAttribute("attribute.devmod.crit_multiplier", 1.5D, 1.0D, 5.0D)
            .setSyncable(true));

    public static final DeferredHolder<Attribute, Attribute> ARMOR_SHRED = ATTRIBUTES.register("armor_shred",
        () -> new RangedAttribute("attribute.devmod.armor_shred", 0.0D, 0.0D, 66.0D)
            .setSyncable(true));

    public static final DeferredHolder<Attribute, Attribute> LIFE_STEAL = ATTRIBUTES.register("life_steal",
        () -> new RangedAttribute("attribute.devmod.life_steal", 0.0D, 0.0D, 100.0D)
            .setSyncable(true));

    public static final DeferredHolder<Attribute, Attribute> DAMAGE_BONUS = ATTRIBUTES.register("damage_bonus",
        () -> new RangedAttribute("attribute.devmod.damage_bonus", 0.0D, 0.0D, 100.0D)
            .setSyncable(true));

    // Damage type bonuses
    public static final DeferredHolder<Attribute, Attribute> DAMAGE_VS_UNDEAD = ATTRIBUTES.register("damage_vs_undead",
        () -> new RangedAttribute("attribute.devmod.damage_vs_undead", 0.0D, 0.0D, 200.0D)
            .setSyncable(true));

    public static final DeferredHolder<Attribute, Attribute> DAMAGE_VS_ARTHROPODS = ATTRIBUTES.register("damage_vs_arthropods",
        () -> new RangedAttribute("attribute.devmod.damage_vs_arthropods", 0.0D, 0.0D, 200.0D)
            .setSyncable(true));

    public static final DeferredHolder<Attribute, Attribute> DAMAGE_VS_PLAYERS = ATTRIBUTES.register("damage_vs_players",
        () -> new RangedAttribute("attribute.devmod.damage_vs_players", 0.0D, 0.0D, 200.0D)
            .setSyncable(true));

    public static final DeferredHolder<Attribute, Attribute> TRUE_DAMAGE_PERCENT = ATTRIBUTES.register("true_damage_percent",
        () -> new RangedAttribute("attribute.devmod.true_damage_percent", 0.0D, 0.0D, 100.0D)
            .setSyncable(true));
}
```

**Implementazione attuale:** gli attributi sopra sono registrati in `ModAttributes` e iscritti nel mod event bus (`DevMod`). Il routing editor → server usa `WeaponStatsPayload` (canale 7) con clamping server-side (`PacketSecurityService`) per tutte le proprietà elencate; il payload legacy rimane solo per compatibilità. Se è presente il mod `puffish_attributes` (Pufferfish’s Attributes), gli attributi sovrapponibili vengono mappati automaticamente (`armor_shred`, `life_steal`) verso `puffish_attributes:*` tramite `PufferfishCompat`.

**Runtime:** `DamageHandler` applica armor shred, damage bonus, bonus vs undead/arthropods/player (via tag/instance), fire/magic bonus, true-damage percentuale, lifesteal e durabilità. Il tab Tool Rules ora scrive il componente `minecraft:tool` (default speed, damage per block, fino a 3 regole tag + drop flag); la toggle "Clear Tool Rules" rimuove il componente `tool` dal dato item quando attivata.

## Validation Rules per Custom Attributes

Il sistema utilizza un approccio di validazione a più livelli per garantire la coerenza dei dati, una buona esperienza utente e la sicurezza contro la manipolazione dei dati.

### 1. Livello Dati: `RangedAttribute`

Alla base, ogni attributo custom viene registrato con un range di valori leciti (min/max) direttamente nella sua definizione, utilizzando `RangedAttribute` di Minecraft.

**Esempio:**
```java
// In ModAttributes.java
public static final DeferredHolder<Attribute, Attribute> CRIT_CHANCE = ATTRIBUTES.register("crit_chance",
    () -> new RangedAttribute("attribute.devmod.crit_chance", 0.0D, 0.0D, 100.0D) // min: 0.0, max: 100.0
        .setSyncable(true));
```

### 2. Livello UI: Client-Side Clamping

Per un feedback immediato all'utente, i componenti della UI come gli slider (`EditorSlider`) e i campi di testo (`EditorTextField`) eseguono un "clamping" del valore inserito, forzandolo a rimanere nel range valido.

**Esempio:**
```java
// In EditorSlider.java
public void setValue(float newValue) {
    // Il valore viene bloccato tra il minimo e il massimo dello slider
    this.value = Mth.clamp(newValue, min, max);
    if (onValueChange != null) {
        onValueChange.accept(this.value);
    }
}
```

### 3. Livello Rete: Server-Side Validation

Questo è il livello di sicurezza più importante. Quando i dati dell'editor vengono inviati al server tramite pacchetti di rete, il `PacketSecurityService` intercetta e valida ogni valore. Qualsiasi valore che non rientri nei limiti di sicurezza definiti sul server viene bloccato ("clamped") a un valore sicuro prima di essere applicato. Questo previene il cheating.

**Esempio:**
```java
// In NetworkHandler.java (quando riceve un pacchetto)
double maxHealth = security.validateHealth(payload.maxHealth());
...
AttributeInstance healthAttr = mob.getAttribute(Attributes.MAX_HEALTH);
healthAttr.setBaseValue(maxHealth);

// In PacketSecurityService.java
public double validateHealth(double health) {
    // Applica i limiti di sicurezza del server
    return clamp(health, MIN_ATTRIBUTE_VALUE, MAX_HEALTH);
}

private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
}
```

## Conflict Resolution tra Mod Diversi

L'architettura è progettata per coesistere con altri mod che modificano le armi, utilizzando una gerarchia di strategie per determinare il comportamento corretto di un'arma e risolvere i conflitti.

### 1. Rilevamento Automatico tramite `WeaponTypeDetector`

Per le armi moddate non riconosciute esplicitamente, il `WeaponTypeDetector` usa un sistema a priorità per classificarle. Questo permette un supporto generico senza bisogno di integrazioni dirette.

**Gerarchia di Rilevamento:**
1.  **Config Blacklist/Whitelist**: File JSON che forzano o escludono un item (massima priorità).
2.  **Java Class**: Controlla se l'item estende classi note (es. `SwordItem`).
3.  **Item Tags**: Controlla la presenza di tag standard o custom (es. `devmod:editable_melee_weapons`).
4.  **Attribute Heuristics**: Analizza gli attributi dell'item. La presenza di `minecraft:attack_damage` suggerisce un'arma melee.

**Esempio:**
```java
// In WeaponTypeDetector.java
public static DetectionResult detect(ItemStack stack) {
    // ...
    // PRIORITY 3: Java class hierarchy (instanceof)
    DetectionResult classResult = detectByClass(item);
    if (classResult != null) return classResult;

    // PRIORITY 4: Item tags (data-driven)
    DetectionResult tagResult = detectByTags(stack);
    if (tagResult != null) return tagResult;

    // PRIORITY 5: Attribute/Component heuristics
    DetectionResult attrResult = detectByAttributes(stack);
    if (attrResult != null) return attrResult;
    // ...
}
```

### 2. Layer di Compatibilità Specifica

Per mod popolari come Pufferfish's Attributes, viene usato un layer di compatibilità esplicito. Questo codice viene eseguito solo se il mod in questione è caricato, evitando hard dependencies.

**Esempio:**
```java
// In PufferfishCompat.java
public final class PufferfishCompat {
    private static final boolean PUFFERFISH_LOADED = ModList.get().isLoaded("puffish_attributes");

    public static boolean isCompatEnabled() {
        return PUFFERFISH_LOADED && Config.SERVER.pufferfishCompat.get();
    }

    public static Holder<Attribute> getEffectiveAttribute(Holder<Attribute> devmodAttr) {
        if (!isCompatEnabled()) {
            return devmodAttr;
        }
        // ... logica per mappare l'attributo DevMod a quello di Pufferfish
    }
}
```

### 3. Override Manuale tramite Whitelist/Blacklist

Gli amministratori di server possono risolvere manualmente i conflitti o forzare il comportamento di un item moddato tramite file di configurazione JSON. Una `blacklist` escluderà un item dall'editor, mentre una `whitelist` lo forzerà a essere trattato come un certo tipo di arma.

**Esempio:**
```json
// config/devmod/weapon_whitelist.json
{
  "_comment": "Items to explicitly treat as weapons (overrides detection)",
  "melee": [
    "somemod:custom_sword",
    "anothermod:battle_axe"
  ]
}

// config/devmod/weapon_blacklist.json
{
  "_comment": "Items to explicitly exclude from weapon editor",
  "items": [
    "somemod:decorative_sword"
  ]
}
```

## Considerazioni sulle Performance

Le performance sono una considerazione chiave nel design del sistema di attributi, per evitare impatti negativi sul gameplay, specialmente durante il combattimento.

### 1. Caching dei Calcoli Derivati

Calcoli costosi che dipendono da più attributi (come DPS o EHP) non vengono eseguiti a ogni frame. Vengono invece cachati tramite un `EditorCache` centralizzato.

*   **`EditorCache.getOrCompute()`**: Questo metodo viene usato per ottenere un valore dalla cache. Se il valore non è presente o è scaduto (logica TTL), viene calcolato tramite una `Supplier` e il risultato viene memorizzato nella cache.
*   **Invalidazione**: La cache viene invalidata in modo granulare quando i dati di un item cambiano, garantendo che i valori visualizzati siano sempre aggiornati senza ricalcoli inutili.

**Esempio:**
```java
// In WeaponModule.java
float dps = EditorCache.getInstance().getOrCompute(
    EditorCache.Types.DPS,
    item.toString(),
    () -> stats.attackDamage * stats.attackSpeed // Calcolo costoso
);
```

### 2. Sincronizzazione degli Attributi

Tutti gli attributi custom di DevMod sono registrati con `.setSyncable(true)`. Questo fa sì che il loro valore venga sincronizzato automaticamente dal server al client, rendendo il lookup lato client estremamente rapido (una semplice lettura dalla mappa degli attributi dell'entità) ed evitando la necessità di pacchetti di rete custom per ogni accesso.

### 3. Caching Specifico per Feature

Oltre al cache principale, sistemi specifici implementano le proprie strategie di caching. Un esempio è `HitHelper`, che usa una cache con TTL per i calcoli delle parti del corpo colpite, riducendo drasticamente l'overhead dei raycast durante il combattimento.

### 4. Profiling Integrato

Il mod include un `PerformanceProfiler` che permette agli sviluppatori di monitorare l'impatto di diversi sottosistemi, incluso l'attribute lookup, per identificare e risolvere colli di bottiglia. Questo dimostra un approccio proattivo alla gestione delle performance.

## WeaponStats Model

```java
/**
 * Mutable model for weapon stats editing.
 * Maps to/from ItemStack components and attributes.
 * Note: This is a class with public fields, not a record, to allow in-place editing.
 */
public class WeaponStats {
    // Tier 1: Vanilla Core
    public float attackDamage = 0.0f;
    public float attackSpeed = 0.0f;
    public float attackKnockback = 0.0f;
    public float attackReach = 0.0f;
    public float sweepingRatio = 0.0f;  // AoE damage multiplier (0-1)

    // Tier 2: Durability
    public int maxDurability = 0;
    public int currentDamage = 0;
    public int repairCost = 0;
    public boolean unbreakable = false;

    // Tier 3: DevMod Custom
    public float critChance = 0.0f;
    public float critDamage = 1.5f;  // Crit multiplier
    public float armorShred = 0.0f;
    public float lifesteal = 0.0f;
    public float damageBonus = 0.0f;  // Direct damage bonus (0-1)

    // Tier 4: Damage Type Bonuses
    public float damageVsUndead = 0.0f;
    public float damageVsArthropods = 0.0f;
    public float damageVsPlayers = 0.0f;
    public float fireDamageBonus = 0.0f;
    public float trueDamagePercent = 0.0f;

    // Tool rules
    public boolean clearToolRules = false;
    public float toolDefaultMiningSpeed = 1.0f;
    public int toolDamagePerBlock = 1;
    public List<ToolRuleData> toolRules = new ArrayList<>();
}
```

## Weapon Type Detection

### Support Matrix

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

### Detection Algorithm

```java
/**
 * Weapon type detection with fallback chain.
 * Priority: Class → Tags → Attributes → Config → Fallback
 */
public final class WeaponTypeDetector {

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
        SWORD, AXE, MACE, TRIDENT, PICKAXE_COMBAT, GENERIC_MELEE,
        // Ranged
        BOW, CROSSBOW, GENERIC_RANGED,
        // Defense
        SHIELD,
        // Unknown
        UNKNOWN, NOT_A_WEAPON
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

## Tab Structure

### WeaponModule Tabs

```
┌─────────────────────────────────────────────────────────────────┐
│ [STATS] [COMBAT] [DURABILITY] [DAMAGE TYPES] [DEBUG]            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  TAB: STATS (Vanilla Core)                                      │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ BASE COMBAT                                              │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Base Damage       [━━━━━━━━━━] 7.0    DPS: 11.2         │   │
│  │ Attack Speed      [━━━━━━━━━━] 1.6    attacks/sec       │   │
│  │ Attack Knockback  [━━━━━━━━━━] 0.0                      │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ REACH & AREA                                             │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Entity Reach      [━━━━━━━━━━] 2.5    blocks            │   │
│  │ Sweeping Ratio    [━━━━━━━━━━] 0%     AoE multiplier    │   │
│  │ Damage Bonus      [━━━━━━━━━━] 0%     direct bonus      │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  TAB: COMBAT (DevMod Custom)                                    │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ CRITICAL HITS                                            │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Crit Chance       [━━━━━━━━━━] 5%     (replaces jump)   │   │
│  │ Crit Multiplier   [━━━━━━━━━━] 1.5x   damage bonus      │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ PENETRATION & SUSTAIN                                    │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Armor Shred       [━━━━━━━━━━] 0%     ignores armor     │   │
│  │ Life Steal        [━━━━━━━━━━] 0%     heal on hit       │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  TAB: DAMAGE TYPES (Predefined Bonuses)                         │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ TARGET BONUSES                                           │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ vs Undead         [━━━━━━━━━━] +0%                      │   │
│  │ vs Arthropods     [━━━━━━━━━━] +0%                      │   │
│  │ vs Players        [━━━━━━━━━━] +0%                      │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ DAMAGE CONVERSION                                        │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Fire Damage       [━━━━━━━━━━] +0%    (sets on fire)    │   │
│  │ True Damage       [━━━━━━━━━━] 0%     (bypasses armor)  │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  TAB: DURABILITY (Vanilla Item Properties)                       │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ Durability                                               │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Max Durability    [━━━━━━━━━━] 1561                      │   │
│  │ Current Damage    [━━━━━━━━━━]  120                      │   │
│  │ Repair Cost       [━━━━━━━━━━]    3                      │   │
│  │ Unbreakable       [ ]                                     │   │
│  │ Tool Rules        (editable: default speed, drops)        │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Weapon-Specific Tabs

#### MACE Tab (Phase 1)

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

#### TRIDENT Tab (Phase 2)

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

## Modded Support

### Tag System

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

### Config Whitelist/Blacklist

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

## Pufferfish Compatibility

```java
/**
 * Optional compatibility mapping for Pufferfish's Attributes.
 * Enabled via config when mod is present.
 */
public final class PufferfishCompat {

    private static final boolean PUFFERFISH_LOADED = ModList.get().isLoaded("puffish_attributes");

    // Mapping: DevMod ID -> Pufferfish ID
    private static final Map<ResourceLocation, ResourceLocation> COMPAT_MAP = Map.of(
        DevMod.rl("armor_shred"), ResourceLocation.parse("puffish_attributes:armor_shred"),
        DevMod.rl("life_steal"), ResourceLocation.parse("puffish_attributes:life_steal"),
        DevMod.rl("crit_chance"), ResourceLocation.parse("puffish_attributes:crit_chance")
        // Note: crit_multiplier non esiste in Pufferfish
    );

    /**
     * Check if compat mode is enabled.
     */
    public static boolean isCompatEnabled() {
        return PUFFERFISH_LOADED && Config.SERVER.pufferfishCompat.get();
    }

    /**
     * Get effective attribute holder, using Pufferfish if compat enabled.
     */
    public static Holder<Attribute> getEffectiveAttribute(Holder<Attribute> devmodAttr) {
        if (!isCompatEnabled()) {
            return devmodAttr;
        }

        ResourceLocation devmodId = devmodAttr.unwrapKey()
            .map(ResourceKey::location)
            .orElse(null);

        if (devmodId != null && COMPAT_MAP.containsKey(devmodId)) {
            ResourceLocation pufferfishId = COMPAT_MAP.get(devmodId);
            // Lookup Pufferfish attribute from registry
            return BuiltInRegistries.ATTRIBUTE.getHolder(pufferfishId).orElse(devmodAttr);
        }

        return devmodAttr;
    }
}
```

## Implementation Phases

| Phase | Scope | Priority | Status |
|-------|-------|----------|--------|
| **MVP** | STATS + COMBAT + DURABILITY tabs | P0 | ✅ Complete |
| **Phase 1** | DAMAGE TYPES tab (predefined bonuses) | P1 | ✅ Complete |
| **Phase 2** | DEBUG tab (raw component viewer) | P1 | ✅ Complete |
| **Phase 3** | ADVANCED/Tool tab (tool rules editing + clear toggle) | P1 | ✅ Complete |
| **Phase 4** | Ranged weapons module | P3 | ✅ Complete (see [16-ranged-weapons.md](16-ranged-weapons.md)) |
| **Phase 5** | Weapon variants (MACE, TRIDENT) | P2 | ✅ Complete |
| **Future** | Custom damage type creator | P4+ | ⏳ Planned |

## Config Options

```toml
# config/devmod-server.toml

[weapons]
# Enable Pufferfish's Attributes compatibility mapping
pufferfishCompat = true

# Maximum allowed values (server-side validation)
maxCritChance = 100.0
maxCritMultiplier = 5.0
maxArmorShred = 66.0
maxLifeSteal = 100.0
maxDamageBonus = 200.0
maxTrueDamage = 100.0

# Allow editing vanilla attributes
allowVanillaAttributeEditing = true

# Allow editing durability components
allowDurabilityEditing = true

[weapons.detection]
# Enable attribute-based heuristic detection for unknown items
enableHeuristicDetection = true

# Minimum confidence level to show editor without warning
minConfidenceForAutoEdit = 0.8

# Treat pickaxes as weapons (shows in weapon editor)
treatPickaxeAsWeapon = false

# Log detection results for debugging
logDetectionResults = true
```

## Implementation Tasks

### P0 - Core System
- [x] Implementare `ModAttributes` registration
- [x] Creare `WeaponStats` record con tutti i tier
- [x] Implementare `WeaponTypeDetector` con priority chain

### Stato implementazione (snapshot - Updated 2025-01)
- ✅ `WeaponComponents.WEAPON_STATS` data component registrato e usato come source of truth.
- ✅ `WeaponModStats` legacy migrato automaticamente; ricostruito dai modifiers DevMod se assente, poi persistito.
- ✅ Salvataggio armi applica attribute modifiers (vanilla + DevMod, con mapping Pufferfish) clampati via `PacketSecurityService`, prunando valori zero e mantenendo altri modifiers invariati.
- ✅ Global/specific materializzano component + modifiers sullo stack.
- ✅ `WeaponStatsPayloadV2` typed record + StreamCodec; payload invia sia tag legacy sia component.
- ✅ Preview applica component + attribute modifiers per rendering corretto.
- ✅ Lettura editor preferisce il component; variant data viene letta anche dal component.
- ✅ Import/export copre tutti i campi avanzati: `sweepingRatio`, `damage_bonus`, `armor_shred`, `vs-*`, `true_damage`, `clear_tool_rules`.
- ✅ `DamageHandler` applica: armor shred (L155), vs-* bonuses (L184-197), fire/magic (L198-203), true damage (L204-208), lifesteal (L281-284).
- ✅ Tool rules enforcement con clear toggle funzionante (equip/breakspeed/drop events).
- ✅ `WeaponModule` UI tabs: STATS, COMBAT, DAMAGE TYPES, DURABILITY, TOOL RULES, MACE, TRIDENT, DEBUG.
- ✅ Value-source prefixes `[DEV]/[NBT]/[VANILLA]` su tutti gli slider.

### Gaps & next steps (doc15)
- ⏳ GameTests: serialization/migrazione `weapon_stats`, calc armor shred + true damage + vs-*, tool rules/clear toggle.
  - Nota: `DamageCalculationTest.java` e `EnvironmentalDamageTest.java` esistono ma necessitano espansione.
  - JUnit placeholder esiste ma è disabilitato; servono GameTests con runtime MC per copertura reale.
- ⏳ Validazione attribute modifier ranges su equip/apply (non solo payload clamp) - LOW PRIORITY.
- ✅ Datapack export: include campi avanzati per evitare stacking/partial overrides.

### Datapack compatibilità modpack
- I datapack `devmod` generati possono essere inclusi in un modpack: DevMod li carica come datapack vanilla e applica gli override (component + modifier) senza logica speciale.
- Per evitare conflitti con altri datapack che toccano gli stessi ID, mantenere un solo datapack per attributo/voce DevMod o assicurarsi che l’ordine di caricamento sia corretto (ultimo wins). `pack.mcmeta` usa `pack_format` 48 e una descrizione generica; se serve priorità, rinominare il pack per caricarlo dopo gli altri.

### P1 - Tab Structure
- [x] Creare STATS tab con vanilla attributes
- [x] Creare COMBAT tab con DevMod attributes
- [x] Creare DURABILITY tab con data components
- [x] Creare DAMAGE TYPES tab con predefined bonuses

### P2 - Modded Support
- [x] Implementare tag system per weapon detection
- [x] Creare config whitelist/blacklist system
- [x] Aggiungere Pufferfish compatibility layer

### P3 - Advanced Features
- [x] Implementare weapon-specific tabs (MACE, TRIDENT)
- [x] Aggiungere tool rules editing nel tab dedicato (con clear toggle)
- [x] Creare low confidence warning UI

---

**Riferimenti:**
- [16-ranged-weapons.md](16-ranged-weapons.md) - Proprietà armi a distanza
- [06-persistence-storage.md](06-persistence-storage.md) - Storage per weapon stats
- [10-unified-architecture.md](10-unified-architecture.md) - WeaponModule integration
