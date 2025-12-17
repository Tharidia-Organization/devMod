# Armor Properties Architecture
## Data Components, Damage Reduction, Shield Mechanics, Source Tracking

> **Sezione 2.22** del Design System - Architettura proprietà armature e scudi

---

## Design Principles

| Principio | Decisione | Rationale |
|-----------|-----------|-----------|
| **Source of Truth** | `devmod:armor_stats` component | Controllo totale, no NBT legacy |
| **Modifier Overwrite** | Remove-then-add per attributo | Evita stacking accidentale |
| **Reduction Model** | Per-damage-type percentuale | Massima flessibilità vs vanilla flat armor |
| **Shield Integration** | ArmorModule con variant tab | UI unificata, logica separata |

## Property Tiers

### TIER 1: Vanilla Core (Attribute Modifiers)

| Property | Attribute ID | Default | Range | Tab |
|----------|--------------|---------|-------|-----|
| **Armor Bonus** | `minecraft:armor` | varies | 0–30 | STATS |
| **Toughness Bonus** | `minecraft:armor_toughness` | varies | 0–20 | STATS |
| **Knockback Resistance** | `minecraft:knockback_resistance` | 0 | 0–100% | STATS |

### TIER 2: DevMod Damage Reductions

| Property | Internal Field | Default | Range | Tab |
|----------|----------------|---------|-------|-----|
| **Physical Reduction** | `physicalReduction` | 0 | 0–80% | REDUCTION |
| **Fire Reduction** | `fireReduction` | 0 | 0–80% | REDUCTION |
| **Magic Reduction** | `magicReduction` | 0 | 0–80% | REDUCTION |
| **Explosion Reduction** | `explosionReduction` | 0 | 0–80% | REDUCTION |
| **Projectile Reduction** | `projectileReduction` | 0 | 0–80% | REDUCTION |

### TIER 3: Special Properties

| Property | Internal Field | Default | Range | Tab |
|----------|----------------|---------|-------|-----|
| **Thorns Reflect** | `thornsReflect` | false | toggle | SPECIAL |
| **Thorns Damage** | `thornsPercent` | 0 | 0–50% | SPECIAL |

### TIER 4: Shield Properties (Shield Variant Only)

| Property | Internal Field | Default | Range | Tab |
|----------|----------------|---------|-------|-----|
| **Block Strength** | `shieldBlockStrength` | 1.0 | 0–1.0 | SHIELD |
| **Reflect Projectiles** | `shieldReflectProjectiles` | false | toggle | SHIELD |
| **Recovery Speed** | `shieldRecoverySpeed` | 1.0 | 0–2.0 | SHIELD |

## Data Component Registration

```java
/**
 * Data components for armor-related persistent data.
 * Mirrors WeaponComponents to provide a typed container for armor stats.
 */
public final class ArmorComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, DevMod.MODID);

    /**
     * Serialized ArmorStats payload (replacement for legacy "ArmorModStats" NBT).
     * Stored as CompoundTag for flexibility while migrating off CustomData.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> ARMOR_STATS =
        COMPONENTS.register("armor_stats", () -> DataComponentType.<CompoundTag>builder()
            .persistent(CompoundTag.CODEC)
            .networkSynchronized(ARMOR_TAG_STREAM_CODEC)
            .build());

    /**
     * Returns the registered armor_stats component when bound, otherwise a local
     * fallback instance so JVM tests can still store/read data without registry binding.
     */
    public static DataComponentType<CompoundTag> armorStatsComponent() {
        try {
            if (ARMOR_STATS.isBound()) {
                return ARMOR_STATS.get();
            }
        } catch (Exception ignored) {}

        if (Boolean.getBoolean("devmod.allowFallbackComponents")) {
            return fallbackArmorStats();
        }
        throw new IllegalStateException("armor_stats component is not bound");
    }
}
```

## ArmorStats Model

```java
/**
 * Transient model for armor stats editing.
 * Maps to/from ItemStack components and attributes.
 */
public class ArmorStats {
    // TIER 1: Vanilla attributes
    public float armorBonus = 0;
    public float toughnessBonus = 0;
    public float knockbackResistance = 0;

    // TIER 2: Damage reductions (0.0 - 0.8 = 0% - 80%)
    public float physicalReduction = 0;
    public float fireReduction = 0;
    public float magicReduction = 0;
    public float explosionReduction = 0;
    public float projectileReduction = 0;

    // TIER 3: Special
    public boolean thornsReflect = false;
    public float thornsPercent = 0;

    // TIER 4: Shield (only for ShieldItem)
    public float shieldBlockStrength = 1.0f;
    public boolean shieldReflectProjectiles = false;
    public float shieldRecoverySpeed = 1.0f;

    /**
     * Save stats to CompoundTag for component storage.
     */
    public void save(CompoundTag tag) {
        tag.putFloat("armorBonus", armorBonus);
        tag.putFloat("toughnessBonus", toughnessBonus);
        tag.putFloat("knockbackResistance", knockbackResistance);
        tag.putFloat("physicalReduction", physicalReduction);
        tag.putFloat("fireReduction", fireReduction);
        tag.putFloat("magicReduction", magicReduction);
        tag.putFloat("explosionReduction", explosionReduction);
        tag.putFloat("projectileReduction", projectileReduction);
        tag.putBoolean("thornsReflect", thornsReflect);
        tag.putFloat("thornsPercent", thornsPercent);
        tag.putFloat("shieldBlockStrength", shieldBlockStrength);
        tag.putBoolean("shieldReflectProjectiles", shieldReflectProjectiles);
        tag.putFloat("shieldRecoverySpeed", shieldRecoverySpeed);
    }

    /**
     * Load stats from CompoundTag.
     */
    public static ArmorStats load(CompoundTag tag) {
        ArmorStats stats = new ArmorStats();
        stats.armorBonus = tag.getFloat("armorBonus");
        stats.toughnessBonus = tag.getFloat("toughnessBonus");
        // ... load all fields
        return stats;
    }
}
```

## Network Payload

```java
/**
 * Typed armor stats payload (v2) that mirrors WeaponStatsPayloadV2.
 * Carries the serialized stats tag plus the target slot for specific applications.
 */
public record ArmorStatsPayloadV2(
    @Nonnull ItemStack item,
    @Nonnull CompoundTag statsTag,
    boolean isGlobal,
    int slot // -1 when global or unspecified; 0-3 for HEAD/CHEST/LEGS/FEET
) implements CustomPacketPayload {

    public static final Type<ArmorStatsPayloadV2> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("devmod", "armor_stats_v2")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ArmorStatsPayloadV2> STREAM_CODEC = StreamCodec.of(
        (buffer, val) -> {
            ItemStack.STREAM_CODEC.encode(buffer, val.item());
            ByteBufCodecs.COMPOUND_TAG.encode(buffer, val.statsTag());
            ByteBufCodecs.BOOL.encode(buffer, val.isGlobal());
            buffer.writeVarInt(val.slot());
        },
        buffer -> new ArmorStatsPayloadV2(
            ItemStack.STREAM_CODEC.decode(buffer),
            ByteBufCodecs.COMPOUND_TAG.decode(buffer),
            ByteBufCodecs.BOOL.decode(buffer),
            buffer.readVarInt()
        )
    );
}
```

## Validation Rules

### 1. Server-Side Clamping (PacketSecurityService)

```java
/**
 * Validate and clamp armor stats values on server.
 */
public ArmorStats clampArmorStats(ArmorStats stats) {
    ArmorStats clamped = new ArmorStats();

    // Damage reductions: 0% - 80%
    clamped.physicalReduction = Mth.clamp(stats.physicalReduction, 0f, 0.8f);
    clamped.fireReduction = Mth.clamp(stats.fireReduction, 0f, 0.8f);
    clamped.magicReduction = Mth.clamp(stats.magicReduction, 0f, 0.8f);
    clamped.explosionReduction = Mth.clamp(stats.explosionReduction, 0f, 0.8f);
    clamped.projectileReduction = Mth.clamp(stats.projectileReduction, 0f, 0.8f);

    // Shield properties
    clamped.shieldBlockStrength = Mth.clamp(stats.shieldBlockStrength, 0f, 1f);
    clamped.shieldRecoverySpeed = Mth.clamp(stats.shieldRecoverySpeed, 0f, 2f);

    // Thorns
    clamped.thornsPercent = Mth.clamp(stats.thornsPercent, 0f, 0.5f);

    // Vanilla attributes
    clamped.armorBonus = Mth.clamp(stats.armorBonus, 0f, 30f);
    clamped.toughnessBonus = Mth.clamp(stats.toughnessBonus, 0f, 20f);
    clamped.knockbackResistance = Mth.clamp(stats.knockbackResistance, 0f, 1f);

    return clamped;
}
```

### 2. UI-Side Clamping (EditorSlider)

All sliders in ArmorModule are configured with appropriate min/max values that match server-side limits.

## Attribute Modifier Application

```java
/**
 * Apply armor stats as attribute modifiers.
 * Uses remove-then-add strategy to prevent stacking.
 */
public static void applyAttributeModifiers(ItemStack stack, ArmorStats stats) {
    ItemAttributeModifiers current = stack.getOrDefault(
        DataComponents.ATTRIBUTE_MODIFIERS,
        ItemAttributeModifiers.EMPTY
    );

    // Remove existing DevMod modifiers
    List<ItemAttributeModifiers.Entry> filtered = current.modifiers().stream()
        .filter(e -> !isDevModModifier(e.modifier().id()))
        .toList();

    // Add new DevMod modifiers
    List<ItemAttributeModifiers.Entry> newEntries = new ArrayList<>(filtered);

    if (stats.armorBonus != 0) {
        newEntries.add(createModifier(Attributes.ARMOR, stats.armorBonus, "devmod.armor_bonus"));
    }
    if (stats.toughnessBonus != 0) {
        newEntries.add(createModifier(Attributes.ARMOR_TOUGHNESS, stats.toughnessBonus, "devmod.toughness_bonus"));
    }
    if (stats.knockbackResistance != 0) {
        newEntries.add(createModifier(Attributes.KNOCKBACK_RESISTANCE, stats.knockbackResistance, "devmod.kb_resist"));
    }

    stack.set(DataComponents.ATTRIBUTE_MODIFIERS,
        new ItemAttributeModifiers(newEntries, current.showInTooltip()));
}

private static boolean isDevModModifier(ResourceLocation id) {
    return id.getNamespace().equals("devmod");
}
```

## Shield Mechanics Integration

Shield mechanics are handled in `DamageHandler.applyShieldBlock()`:

```java
/**
 * Apply shield block mechanics from ArmorStats.
 * Called when player is blocking with shield.
 */
public static float applyShieldBlock(Player player, ItemStack shield, float damage, DamageSource source) {
    ArmorStats stats = ArmorConfigManager.getStats(shield);

    // Block strength reduces damage (0-100%)
    float blocked = damage * stats.shieldBlockStrength;
    float remaining = damage - blocked;

    // Projectile reflection
    if (stats.shieldReflectProjectiles && source.getDirectEntity() instanceof Projectile projectile) {
        Vec3 velocity = projectile.getDeltaMovement();
        projectile.setDeltaMovement(velocity.reverse());
        projectile.setOwner(player);
    }

    // Apply custom cooldown based on recovery speed
    // Higher recovery speed = shorter cooldown
    int baseCooldown = 5; // ticks
    int actualCooldown = Math.max(1, (int)(baseCooldown / stats.shieldRecoverySpeed));
    player.getCooldowns().addCooldown(shield.getItem(), actualCooldown);

    return remaining;
}
```

## Source Badge System

ArmorModule tracks the origin of stats for UI display:

```java
/**
 * Source types for armor stat values.
 */
public enum Source {
    DEV("DEV", CYAN, "Modified by DevMod"),      // From component or global config
    NBT("NBT", ORANGE, "From item NBT data"),    // From legacy NBT
    VANILLA("VAN", MUTED, "Vanilla default"),    // No modification
    MODIFIED("MOD", YELLOW, "Unsaved changes");  // Changed but not saved
}

/**
 * Determine source based on data origin.
 */
private SourceBadge.Source determineSource() {
    if (hasComponentData || hasGlobalConfig) {
        return SourceBadge.Source.DEV;
    }
    if (hasNbtData) {
        return SourceBadge.Source.NBT;
    }
    return SourceBadge.Source.VANILLA;
}
```

All sliders and toggles display inline source badges:

```java
physicalReductionSlider = new EditorSlider("physRed", "Physical Reduction", 0f, 80f, 0f)
    .step(1f)
    .format("%.0f")
    .suffix("%")
    .trackColor(UIConstants.SliderColors.DEFENSE)
    .source(determineSource())  // <-- Badge shown inline after label
    .onChange(v -> { stats.physicalReduction = v / 100f; markDirty("Physical reduction"); });
```

## Tab Structure

### ArmorModule Tabs

```
+---------------------------------------------------------------------+
| [REDUCTION] [STATS] [SPECIAL] [SHIELD*] [DEBUG]                      |
+---------------------------------------------------------------------+
|                                                                      |
|  TAB: REDUCTION (Damage Type Reductions)                             |
|  +---------------------------------------------------------------+  |
|  | DAMAGE REDUCTION                                    EHP: 185  |  |
|  +---------------------------------------------------------------+  |
|  | Physical Reduction  [DEV] [================] 50%              |  |
|  | Fire Reduction      [VAN] [================] 0%               |  |
|  | Magic Reduction     [VAN] [================] 0%               |  |
|  | Explosion Reduction [VAN] [================] 0%               |  |
|  | Projectile Reduction[VAN] [================] 0%               |  |
|  +---------------------------------------------------------------+  |
|                                                                      |
|  TAB: STATS (Vanilla Attributes)                                     |
|  +---------------------------------------------------------------+  |
|  | ARMOR BONUSES                                                  |  |
|  +---------------------------------------------------------------+  |
|  | Armor Bonus         [DEV] [================] +5                |  |
|  | Toughness Bonus     [VAN] [================] +0.0              |  |
|  | Knockback Resist    [VAN] [================] 0%                |  |
|  +---------------------------------------------------------------+  |
|                                                                      |
|  TAB: SPECIAL (Thorns)                                               |
|  +---------------------------------------------------------------+  |
|  | THORNS                                                         |  |
|  +---------------------------------------------------------------+  |
|  | Thorns Reflect      [VAN] [ ]                                  |  |
|  | Thorns Damage       [VAN] [================] 0%                |  |
|  +---------------------------------------------------------------+  |
|                                                                      |
|  TAB: SHIELD (Shield Items Only)                                     |
|  +---------------------------------------------------------------+  |
|  | BLOCK MECHANICS                                                |  |
|  +---------------------------------------------------------------+  |
|  | Block Strength      [DEV] [================] 0.75              |  |
|  | Reflect Projectiles [VAN] [ ]                                  |  |
|  | Recovery Speed      [VAN] [================] 1.0x              |  |
|  +---------------------------------------------------------------+  |
|                                                                      |
+---------------------------------------------------------------------+
```

*SHIELD tab only visible when editing ShieldItem

## Datapack Export/Import

### Export Format (JSON)

```json
// datapacks/<packname>/data/devmod/item_modifiers/armor/minecraft_diamond_chestplate.json
{
  "item": "minecraft:diamond_chestplate",
  "version": 2,
  "values": {
    "armor_bonus": 5.0,
    "toughness_bonus": 2.0,
    "knockback_resistance": 0.1,
    "physical_reduction": 0.5,
    "fire_reduction": 0.2,
    "magic_reduction": 0.3,
    "explosion_reduction": 0.25,
    "projectile_reduction": 0.15,
    "thorns_reflect": false,
    "thorns_percent": 0.0
  }
}
```

### Shield Export Format

```json
// datapacks/<packname>/data/devmod/item_modifiers/armor/minecraft_shield.json
{
  "item": "minecraft:shield",
  "version": 2,
  "values": {
    "shield_block_strength": 0.75,
    "shield_reflect_projectiles": true,
    "shield_recovery_speed": 1.5
  }
}
```

## Testing

### TestingHub Test Cases

DevMod includes automated test cases for armor functionality:

| Test ID | Category | Description | Priority |
|---------|----------|-------------|----------|
| `devmod_armor_component_roundtrip` | Component | Verify save/load round-trip | HIGH |
| `devmod_armor_component_migration` | Component | Legacy NBT auto-migration | MEDIUM |
| `devmod_shield_block_strength` | Shield | Block strength damage reduction | HIGH |
| `devmod_shield_reflect` | Shield | Projectile reflection | MEDIUM |
| `devmod_shield_cooldown` | Shield | Recovery speed cooldown scaling | LOW |
| `devmod_armor_datapack_export` | Datapack | Export to datapack JSON | MEDIUM |
| `devmod_armor_datapack_import` | Datapack | Import from datapack JSON | MEDIUM |
| `devmod_armor_clamp_override` | Validation | Out-of-range value clamping | MEDIUM |
| `devmod_armor_modifier_overwrite` | Validation | No modifier stacking | HIGH |
| `devmod_armor_phys_reduction` | Reduction | Physical damage reduction calc | HIGH |
| `devmod_armor_magic_reduction` | Reduction | Magic damage reduction calc | MEDIUM |
| `devmod_armor_multi_reduction` | Reduction | Multiple reduction types | MEDIUM |
| `devmod_armor_source_badge` | UI | Source badge detection | LOW |

### Running Tests

Tests are generated by `DevModArmorTestCases` and integrated into the TestingHub via `DynamicTestGenerator`.

```java
// In TestingHub, armor tests appear under "DevMod - Armor" category
DynamicTestGenerator.INSTANCE.generateAllTests();
List<TestCase> armorTests = DynamicTestGenerator.INSTANCE.getTestsForMod("devmod");
```

## Implementation Status

### Completed (P0/P1)

- [x] `ArmorComponents.ARMOR_STATS` data component with fallback
- [x] `ArmorStatsPayloadV2` typed network payload with StreamCodec
- [x] `PacketSecurityService` validation for all armor/shield values
- [x] Component-first storage with attribute modifier overwrite
- [x] Legacy NBT (`ArmorModStats`) auto-migration on `getStats()`
- [x] Equip change sanitize/clamp in `ArmorConfigManager.clampStats()`
- [x] `DatapackIO.writeArmor()` / `parseArmor()` full field support
- [x] `SourceBadge` component for DEV/NBT/VAN/MOD indicators
- [x] `EditorSlider.source()` / `EditorToggle.source()` builders
- [x] Shield mechanics in `DamageHandler.applyShieldBlock()`
- [x] Tab structure: REDUCTION, STATS, SPECIAL, SHIELD (variant), DEBUG

### Pending (P2)

- [ ] GameTests for armor reduction calculations
- [ ] Additional TestingHub validation scenarios

---

**Riferimenti:**
- [15-weapon-properties.md](15-weapon-properties.md) - Weapon properties (reference architecture)
- [06-persistence-storage.md](06-persistence-storage.md) - Storage patterns
- [10-unified-architecture.md](10-unified-architecture.md) - ArmorModule integration
