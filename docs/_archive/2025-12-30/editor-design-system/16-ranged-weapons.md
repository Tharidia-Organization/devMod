# Ranged Weapon Properties

> Last updated: 2025-12-26
> Status: HISTORICAL (design system snapshot)

## Bow/Crossbow Specifics, Value Sources, Component System

> **Sezione 2.22** del Design System - Proprietà per armi a distanza

---

## Critical Technical Constraints

### Source of Truth Problem

**IMPORTANTE**: Le proprietà ranged **non sono** Data Components standard come damage/durability.

| Property | Actual Source | Writable? | Note |
|----------|---------------|-----------|------|
| Draw Time | `BowItem` class hardcoded | ❌ Vanilla | Requires mixin/AT |
| Charge Time | `CrossbowItem` class | ❌ Vanilla | Requires mixin/AT |
| Projectile Speed | `AbstractArrow` entity spawn | ❌ Vanilla | Set at shoot time |
| Projectile Gravity | `AbstractArrow.getGravity()` | ❌ Vanilla | Entity property |
| Spread/Inaccuracy | `shoot()` method parameter | ❌ Vanilla | Passed at shoot |
| Arrow Damage | `AbstractArrow` entity | ⚠️ Partial | Power enchant modifies |
| Multishot | Enchantment effect | ❌ Vanilla | Enchant-driven |
| Piercing | Enchantment effect | ❌ Vanilla | Enchant-driven |

**Soluzione DevMod**: Custom Data Components + Event hooks per override.
Implementato: CustomData `RangedStats` + Data Components `devmod:draw_time_ticks`, `projectile_speed`, `projectile_gravity`, `projectile_spread`, `base_arrow_damage`, `multishot_count`, `piercing_level`, `ammo_tag_filter`. Runtime usa `RangedStats`/components per override danno/velocità/gravità/spread/crit/pierce/infinity e multishot (fan spawn best-effort). Value source wrapper (`SourcedValue`) disponibile e mostrato in UI (prefissi [VANILLA]/[DEV]/[NBT]); ammo tab mostra una short list di item del tag inserito.

## DevMod Ranged Components

```java
/**
 * DevMod ranged weapon components.
 * These OVERRIDE vanilla behavior when present.
 */
public final class RangedComponents {
    public static final DataComponentType<Float> DRAW_TIME_TICKS =
        register("draw_time_ticks", Codec.FLOAT);

    public static final DataComponentType<Float> PROJECTILE_SPEED =
        register("projectile_speed", Codec.FLOAT);

    public static final DataComponentType<Float> PROJECTILE_GRAVITY =
        register("projectile_gravity", Codec.FLOAT);

    public static final DataComponentType<Float> PROJECTILE_SPREAD =
        register("projectile_spread", Codec.FLOAT);

    public static final DataComponentType<Float> BASE_ARROW_DAMAGE =
        register("base_arrow_damage", Codec.FLOAT);

    public static final DataComponentType<Integer> MULTISHOT_COUNT =
        register("multishot_count", Codec.INT);

    public static final DataComponentType<Integer> PIERCING_LEVEL =
        register("piercing_level", Codec.INT);

    public static final DataComponentType<ResourceLocation> AMMO_TAG_FILTER =
        register("ammo_tag_filter", ResourceLocation.CODEC);
}
```

## Property Support Matrix

| Property | Bow | Crossbow | Trident | Source | Status |
|----------|-----|----------|---------|--------|--------|
| **Draw/Charge Time** | ✅ | ✅ | - | Class/DevMod | ✅ Implemented |
| **Projectile Speed** | ✅ | ✅ | ✅ | Entity/DevMod | ✅ Implemented |
| **Projectile Gravity** | ✅ | ✅ | ✅ | Entity/DevMod | ✅ Implemented |
| **Projectile Spread** | ✅ | ✅ | - | Shoot/DevMod | ✅ Implemented |
| **Base Damage** | ✅ | ✅ | ✅ | Entity/DevMod | ✅ Implemented |
| **Ammo Tag Filter** | ✅ | ✅ | - | DevMod only | ✅ Implemented |
| **Infinity Override** | ✅ | - | - | DevMod only | ✅ Implemented |
| **Multishot Count** | - | ✅ | - | Enchant/DevMod | ✅ Implemented |
| **Piercing Level** | - | ✅ | ✅ | Enchant/DevMod | ✅ Implemented |
| **Loyalty Speed** | - | - | ✅ | Enchant/DevMod | ✅ Implemented |
| **Riptide Distance** | - | - | ✅ | Enchant/DevMod | ✅ Implemented |
| **Riptide Requires Water** | - | - | ✅ | DevMod only | ✅ Implemented |
| **Channeling** | - | - | ✅ | DevMod toggle | ✅ Implemented |

## Value Source System

### Source Types

```java
/**
 * Indicates where a ranged property value comes from.
 */
public enum ValueSource {
    VANILLA_DEFAULT("Vanilla Default", 0x888888),      // Hardcoded in vanilla class
    DEVMOD_COMPONENT("DevMod Override", 0x00AAFF),     // devmod:* component on item
    ENCHANTMENT("Enchantment", 0xFFAA00),              // Modified by enchant
    ATTRIBUTE_MODIFIER("Attribute", 0x00FF00),         // Via attribute system
    COMPUTED("Computed", 0xAAAAFF),                    // Calculated from multiple sources
    UNKNOWN("Unknown", 0xFF0000);                      // Could not determine

    public final String displayName;
    public final int color;

    ValueSource(String displayName, int color) {
        this.displayName = displayName;
        this.color = color;
    }
}

/**
 * A ranged property value with source tracking.
 */
public record SourcedValue<T>(
    T value,
    ValueSource source,
    @Nullable String sourceDetail  // e.g., "Power V" for enchantment
) {
    public static <T> SourcedValue<T> vanillaDefault(T value) {
        return new SourcedValue<>(value, ValueSource.VANILLA_DEFAULT, null);
    }

    public static <T> SourcedValue<T> devmod(T value) {
        return new SourcedValue<>(value, ValueSource.DEVMOD_COMPONENT, null);
    }

    public static <T> SourcedValue<T> enchant(T value, String enchantName) {
        return new SourcedValue<>(value, ValueSource.ENCHANTMENT, enchantName);
    }
}
```

### Value Source Indicator (MVP)

```
┌─────────────────────────────────────────────────────────────────┐
│  TAB: PROJECTILE (Read-Only MVP)                                │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ PROJECTILE PHYSICS                                       │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Projectile Speed    3.0      [VANILLA DEFAULT]          │   │
│  │ Projectile Gravity  0.05     [VANILLA DEFAULT]          │   │
│  │ Projectile Spread   1.0      [VANILLA DEFAULT]          │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ DAMAGE                                                   │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Base Arrow Damage   6.0      [VANILLA DEFAULT]          │   │
│  │ Power Bonus         +2.5     [ENCHANTMENT: Power V]     │   │
│  │ Effective Damage    8.5      [COMPUTED]                 │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ℹ️ Values are read-only. Edit requires Phase 2 implementation. │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## RangedStats Model

```java
/**
 * Transient model for ranged weapon stats.
 * Tracks both effective values and their sources.
 */
public record RangedStats(
    // Mechanics
    SourcedValue<Float> drawTime,           // Bow: ticks to full draw
    SourcedValue<Float> chargeTime,         // Crossbow: ticks to load
    SourcedValue<Integer> multishotCount,   // Crossbow: projectiles per shot
    SourcedValue<Integer> piercingLevel,    // Crossbow/Trident: entities pierced

    // Projectile Physics
    SourcedValue<Float> projectileSpeed,    // blocks/tick
    SourcedValue<Float> projectileGravity,  // downward acceleration
    SourcedValue<Float> projectileSpread,   // inaccuracy factor

    // Damage
    SourcedValue<Float> baseDamage,         // Before enchants
    SourcedValue<Float> enchantBonus,       // From Power/etc
    float effectiveDamage,                  // Computed total

    // Ammo
    SourcedValue<ResourceLocation> ammoTagFilter,
    SourcedValue<Boolean> infinityOverride,

    // Trident-specific (Phase 3)
    SourcedValue<Float> throwDamage,
    SourcedValue<Float> loyaltySpeed,
    SourcedValue<Float> riptideDistance,
    SourcedValue<Boolean> requiresWater
) {
    /**
     * Extract stats from a ranged weapon ItemStack.
     */
    public static RangedStats fromItemStack(ItemStack stack) {
        Item item = stack.getItem();

        if (item instanceof BowItem) {
            return extractBowStats(stack);
        } else if (item instanceof CrossbowItem) {
            return extractCrossbowStats(stack);
        } else if (item instanceof TridentItem) {
            return extractTridentStats(stack);
        }

        return extractGenericRangedStats(stack);
    }

    private static RangedStats extractBowStats(ItemStack stack) {
        // Check for DevMod components first, fallback to vanilla defaults
        Float drawTime = stack.has(RangedComponents.DRAW_TIME_TICKS)
            ? stack.get(RangedComponents.DRAW_TIME_TICKS)
            : null;

        Float speed = stack.has(RangedComponents.PROJECTILE_SPEED)
            ? stack.get(RangedComponents.PROJECTILE_SPEED)
            : null;

        // ... extract all values with source tracking

        return new RangedStats(
            drawTime != null
                ? SourcedValue.devmod(drawTime)
                : SourcedValue.vanillaDefault(20.0f),  // Vanilla bow = 20 ticks
            // ... other fields
        );
    }
}
```

## Tab Structure: RangedModule

### BOW Tabs (Phase 2 Editing)

```
┌─────────────────────────────────────────────────────────────────┐
│ [STATS] [MECHANICS] [PROJECTILE] [AMMO] [DEBUG]                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  TAB: MECHANICS (Bow)                                           │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ DRAW MECHANICS                                           │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Draw Time           [━━━━━━━━━━] 20 ticks   [VANILLA]   │   │
│  │                     1.0 seconds to full draw             │   │
│  │                                                         │   │
│  │ Min Draw for Crit   [━━━━━━━━━━] 18 ticks   [VANILLA]   │   │
│  │                     90% draw = critical shot enabled     │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  TAB: PROJECTILE                                                │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ PHYSICS                                                  │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Speed               [━━━━━━━━━━] 3.0        [VANILLA]   │   │
│  │                     blocks/tick (60 blocks/sec)          │   │
│  │                                                         │   │
│  │ Gravity             [━━━━━━━━━━] 0.05       [VANILLA]   │   │
│  │                     downward accel per tick              │   │
│  │                                                         │   │
│  │ Spread              [━━━━━━━━━━] 1.0        [VANILLA]   │   │
│  │                     inaccuracy (0 = perfect)             │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ DAMAGE                                                   │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Base Damage         [━━━━━━━━━━] 6.0        [VANILLA]   │   │
│  │ Power Bonus                      +2.5       [POWER V]   │   │
│  │ ─────────────────────────────────────────────────────   │   │
│  │ Effective Damage                 8.5        [COMPUTED]  │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  TAB: AMMO                                                      │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ AMMO FILTER                                              │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Accepted Ammo Tag   [#minecraft:arrows           ▾]     │   │
│  │                                                         │   │
│  │ Matching Items (12 total):                      [scroll]│   │
│  │ ┌─────────────────────────────────────────────────────┐ │   │
│  │ │ 🏹 Arrow                                            │ │   │
│  │ │ 🏹 Spectral Arrow                                   │ │   │
│  │ │ 🏹 Tipped Arrow (Water Breathing)                   │ │   │
│  │ │ 🏹 Tipped Arrow (Fire Resistance)                   │ │   │
│  │ │ 🏹 Tipped Arrow (Healing)                           │ │   │
│  │ │ ... +7 more                                         │ │   │
│  │ └─────────────────────────────────────────────────────┘ │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ INFINITY                                                 │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Consumes Ammo       [✓]                     [VANILLA]   │   │
│  │ Infinity Override   [ ]         (force infinite ammo)   │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### CROSSBOW Tabs (Phase 2 Editing)

```
┌─────────────────────────────────────────────────────────────────┐
│ [STATS] [MECHANICS] [PROJECTILE] [AMMO] [DEBUG]                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  TAB: MECHANICS (Crossbow)                                      │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ CHARGE MECHANICS                                         │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Base Charge Time    [━━━━━━━━━━] 25 ticks   [VANILLA]   │   │
│  │                     1.25 seconds to load                 │   │
│  │                                                         │   │
│  │ Quick Charge Bonus  [━━━━━━━━━━] -5 ticks/level         │   │
│  │                     reduces charge time                   │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  TAB: PROJECTILE                                                │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ PHYSICS                                                  │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Speed               [━━━━━━━━━━] 3.15       [VANILLA]   │   │
│  │                     blocks/tick (63 blocks/sec)          │   │
│  │                                                         │   │
│  │ Gravity             [━━━━━━━━━━] 0.05       [VANILLA]   │   │
│  │                     downward accel per tick              │   │
│  │                                                         │   │
│  │ Spread              [━━━━━━━━━━] 0.0        [VANILLA]   │   │
│  │                     perfect accuracy                      │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ DAMAGE                                                   │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Base Damage         [━━━━━━━━━━] 9.0        [VANILLA]   │   │
│  │ Piercing Level      [━━━━━━━━━━] 0          [VANILLA]   │   │
│  │ ─────────────────────────────────────────────────────   │   │
│  │ Effective Damage                 9.0        [COMPUTED]  │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  TAB: MULTISHOT                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ MULTISHOT MECHANICS                                      │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Projectile Count    [━━━━━━━━━━] 1          [VANILLA]   │   │
│  │                     arrows fired per shot                │   │
│  │                                                         │   │
│  │ Spread Angle        [━━━━━━━━━━] 10°        [VANILLA]   │   │
│  │                     angle between projectiles            │   │
│  │                                                         │   │
│  │ Extra Ammo Cost     [ ]         (consumes per shot)     │   │
│  │                     uses 1 arrow per projectile          │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Event Hook System

```java
/**
 * Event hooks to override vanilla ranged weapon behavior.
 */
public final class RangedWeaponEvents {

    @SubscribeEvent
    public static void onArrowShoot(ArrowLooseEvent event) {
        ItemStack bow = event.getBow();
        if (!bow.has(RangedComponents.PROJECTILE_SPEED)) {
            return; // No override
        }

        float customSpeed = bow.get(RangedComponents.PROJECTILE_SPEED);
        
        // Modify arrow velocity
        AbstractArrow arrow = event.getProjectile();
        Vec3 motion = arrow.getDeltaMovement();
        Vec3 normalizedMotion = motion.normalize();
        arrow.setDeltaMovement(normalizedMotion.scale(customSpeed));
    }

    @SubscribeEvent
    public static void onCrossbowCharge(CrossbowChargeEvent event) {
        ItemStack crossbow = event.getCrossbow();
        if (!crossbow.has(RangedComponents.DRAW_TIME_TICKS)) {
            return; // No override
        }

        float customChargeTime = crossbow.get(RangedComponents.DRAW_TIME_TICKS);
        event.setChargeTime((int) customChargeTime);
    }

    @SubscribeEvent
    public static void onProjectileHit(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }

        // Check if arrow came from a DevMod-modified weapon
        ItemStack weapon = getSourceWeapon(arrow);
        if (weapon.isEmpty()) {
            return;
        }

        // Apply custom damage modifiers
        if (weapon.has(RangedComponents.BASE_ARROW_DAMAGE)) {
            float customDamage = weapon.get(RangedComponents.BASE_ARROW_DAMAGE);
            arrow.setBaseDamage(customDamage);
        }
    }
}
```

## Ammo System

```java
/**
 * Custom ammo filtering system for ranged weapons.
 */
public final class AmmoSystem {

    /**
     * Check if an item is valid ammo for a ranged weapon.
     */
    public static boolean isValidAmmo(ItemStack weapon, ItemStack ammo) {
        // Check for custom ammo filter
        if (weapon.has(RangedComponents.AMMO_TAG_FILTER)) {
            ResourceLocation tagId = weapon.get(RangedComponents.AMMO_TAG_FILTER);
            TagKey<Item> ammoTag = TagKey.create(Registries.ITEM, tagId);
            return ammo.is(ammoTag);
        }

        // Fallback to vanilla behavior
        if (weapon.getItem() instanceof BowItem) {
            return ammo.is(ItemTags.ARROWS);
        } else if (weapon.getItem() instanceof CrossbowItem) {
            return ammo.is(ItemTags.ARROWS) || ammo.is(Items.FIREWORK_ROCKET);
        }

        return false;
    }

    /**
     * Get all items that match the ammo filter.
     */
    public static List<ItemStack> getMatchingAmmo(ItemStack weapon) {
        if (!weapon.has(RangedComponents.AMMO_TAG_FILTER)) {
            return getVanillaAmmo(weapon);
        }

        ResourceLocation tagId = weapon.get(RangedComponents.AMMO_TAG_FILTER);
        TagKey<Item> ammoTag = TagKey.create(Registries.ITEM, tagId);

        return BuiltInRegistries.ITEM.getTagOrEmpty(ammoTag)
            .stream()
            .map(holder -> new ItemStack(holder.value()))
            .collect(Collectors.toList());
    }

    private static List<ItemStack> getVanillaAmmo(ItemStack weapon) {
        if (weapon.getItem() instanceof BowItem) {
            return List.of(
                new ItemStack(Items.ARROW),
                new ItemStack(Items.SPECTRAL_ARROW),
                new ItemStack(Items.TIPPED_ARROW)
            );
        } else if (weapon.getItem() instanceof CrossbowItem) {
            return List.of(
                new ItemStack(Items.ARROW),
                new ItemStack(Items.SPECTRAL_ARROW),
                new ItemStack(Items.TIPPED_ARROW),
                new ItemStack(Items.FIREWORK_ROCKET)
            );
        }
        return List.of();
    }
}
```

## Implementation Phases

| Phase | Scope | Status |
|-------|-------|--------|
| **MVP** | Read-only display with source indicators | ✅ Complete |
| **Phase 2** | Editable sliders + DevMod component system | ✅ Complete |
| **Phase 3** | Trident-specific properties | ✅ Complete |
| **Phase 4** | Advanced ammo system utility class | ✅ Complete |

## Implementation Tasks

### P0 - Data Model
- [x] Creare `RangedComponents` data component types
- [x] Implementare `SourcedValue` system per value tracking (`RangedWeaponModule.ValueSource`, `SourcedValue`)
- [x] Creare `RangedStats` class con tutti i campi (`RangedWeaponModule.RangedStats`)

### P1 - UI (Read-Only MVP)
- [x] Creare BOW tabs con value source indicators (`RangedModule.RangedVariant.BOW`)
- [x] Creare CROSSBOW tabs con multishot support (`RangedModule.RangedVariant.CROSSBOW`)
- [x] Implementare ammo filter display con item list (`RangedModule.AmmoListSection`)

### P2 - Event System
- [x] Runtime override via `RangedStats` components (lettura in `RangedWeaponModule.getStats()`)
- [x] Salvataggio via `RangedWeaponModule.applyStats()` + `RangedComponents`
- [x] Aggiungere support per infinity override (`infinityOverride` field)
- [x] Creare `AmmoSystem` utility class per custom ammo filtering (`com.devmod.ammo.AmmoSystem`)

### P3 - Advanced Features
- [x] Implementare TRIDENT-specific tabs e properties (`RangedModule.createTridentComponents()`, `getTridentSections()`)
- [x] Aggiungere editable sliders per Phase 2 (tutti i slider sono editabili)
- [x] Creare advanced ammo system con tag editor (`AmmoSystem` utility + `AmmoListSection` UI)

---

**Riferimenti:**
- [15-weapon-properties.md](15-weapon-properties.md) - Weapon properties base
- [06-persistence.md](06-persistence.md) - Storage per ranged stats
- [08-unified-architecture.md](08-unified-architecture.md) - RangedModule integration
