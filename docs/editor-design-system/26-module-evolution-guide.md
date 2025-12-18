# 26 - Module Evolution Guide

## Overview

Guida per sviluppare e migliorare moduli editor. Definisce maturity levels e checklist per upgrade.

---

## Maturity Levels

### ⭐ Level 1 - Basic
**Requisiti minimi:**
- [ ] Estende `AbstractEditorModule`
- [ ] Almeno 1 tab funzionante
- [ ] Sliders per valori numerici base
- [ ] `buildPayload()` ritorna un payload valido (non null)

**Esempio:** Modulo base con durability slider

### ⭐⭐ Level 2 - Functional
**Aggiunge:**
- [ ] Toggles per valori booleani
- [ ] Info sections read-only
- [ ] Gestione errori base
- [ ] Multiple tabs organizzati

**Esempio:** GeneralModule attuale (pre-redesign)

### ⭐⭐⭐ Level 3 - Complete
**Aggiunge:**
- [ ] Source badges (DEV/NBT/VANILLA)
- [ ] Validazione input
- [ ] Default value markers su sliders
- [ ] Info buttons con tooltips
- [ ] Feedback visivo per modifiche

**Esempio:** RecipeModule

### ⭐⭐⭐⭐ Level 4 - Advanced
**Aggiunge:**
- [ ] Variant system (STANDARD/MACE/TRIDENT)
- [ ] Calculator/preview section (DPS, EHP)
- [ ] Delta tracking (original vs current)
- [ ] Custom sections complesse
- [ ] Keyboard shortcuts

**Esempio:** RangedModule

### ⭐⭐⭐⭐⭐ Level 5 - Reference
**Aggiunge:**
- [ ] Debug tab completo
- [ ] Undo/redo funzionante
- [ ] Export/clipboard diagnostics
- [ ] Variant-specific tabs
- [ ] Tool rules o sub-systems
- [ ] Full source tracking chain
- [ ] Value comparison tables

**Esempio:** WeaponModule, ArmorModule

---

## Current Module Status

| Module | Level | Lines | Key Features |
|--------|-------|-------|--------------|
| **WeaponModule** | ⭐⭐⭐⭐⭐ | 1,642 | 9 tabs, variants, DPS calc, tool rules, debug |
| **ArmorModule** | ⭐⭐⭐⭐⭐ | 1,052 | 8 tabs, shield variant, EHP calc, debug |
| **RangedModule** | ⭐⭐⭐⭐ | 673 | 5 tabs, bow/crossbow variants |
| **RecipeModule** | ⭐⭐⭐⭐ | 674 | 3 tabs, grid editor, item picker |
| **GeneralModule** | ⭐⭐ | 180 | 3 tabs basic, **→ Navigation Hub** |

---

## Upgrade Checklists

### Level 1 → Level 2

```java
// Add toggles
private EditorToggle unbreakableToggle;

unbreakableToggle = new EditorToggle("toggle-unbreakable", false)
    .label("Unbreakable")
    .onChange(this::onUnbreakableChanged);

// Add info section
sections.add(new InfoListSection("info-item",
    "Item ID: " + item.getItem().toString(),
    "Stack Size: " + item.getMaxStackSize()
));
```

### Level 2 → Level 3

```java
// Add source badges
slider.sourceBadge(determineSource(currentValue, nbtValue, vanillaValue));

private SourceBadge.Source determineSource(double current, double nbt, double vanilla) {
    if (hasDevModStats()) return SourceBadge.Source.DEV;
    if (Math.abs(current - vanilla) > 0.001) return SourceBadge.Source.NBT;
    return SourceBadge.Source.VANILLA;
}

// Add default markers
slider.defaultValue(vanillaDefault).showDefaultMarker(true);

// Add info buttons
slider.info("tooltip.damage_multiplier");
```

### Level 3 → Level 4

```java
// Add variant detection
public enum MyVariant { STANDARD, SPECIAL }

private MyVariant detectVariant() {
    if (item.is(ModTags.SPECIAL_ITEMS)) return MyVariant.SPECIAL;
    return MyVariant.STANDARD;
}

// Add calculator section
private EditorSection createCalculatorSection() {
    return new CustomSection("calc-preview", () -> {
        double dps = calculateDPS();
        return String.format("Estimated DPS: %.1f", dps);
    });
}

// Add variant-specific tabs
if (variant == MyVariant.SPECIAL) {
    tabs.add(ModuleTab.of("special", "Special", this::buildSpecialSections));
}
```

### Level 4 → Level 5

```java
// Add debug tab
tabs.add(ModuleTab.of("debug", "Debug", this::buildDebugSections));

private List<EditorSection> buildDebugSections() {
    return List.of(
        new SimpleHeaderSection("Value Comparison"),
        new ValueComparisonSection(
            "Original", originalStats,
            "Current", currentStats
        ),
        new SimpleHeaderSection("Raw Data"),
        new InfoListSection("debug-raw",
            "NBT: " + item.getTag(),
            "Components: " + item.getComponents()
        ),
        new CustomSection("debug-export", () -> {
            EditorButton export = new EditorButton("btn-export", "Copy Debug Info")
                .onClick(this::copyDebugToClipboard);
            return export;
        })
    );
}

// Full undo/redo
@Override
protected void onValueChanged() {
    undoStack.push(createSnapshot());
    markDirty();
}

public void undo() {
    if (undoStack.canUndo()) {
        restoreSnapshot(undoStack.undo());
    }
}
```

---

## WeaponModule Reference Patterns

### Variant System

```java
public enum WeaponVariant {
    STANDARD,
    MACE,
    TRIDENT
}

private WeaponVariant detectVariant() {
    Item item = this.item.getItem();
    if (item instanceof MaceItem) return WeaponVariant.MACE;
    if (item instanceof TridentItem) return WeaponVariant.TRIDENT;
    return WeaponVariant.STANDARD;
}

@Override
protected void initializeTabs() {
    // Common tabs
    tabs.add(ModuleTab.of("hitloc", "Hit Location", this::buildHitLocationSections));
    tabs.add(ModuleTab.of("stats", "Stats", this::buildStatsSections));

    // Variant-specific tabs
    switch (variant) {
        case MACE -> tabs.add(ModuleTab.of("mace", "Mace", this::buildMaceSections));
        case TRIDENT -> tabs.add(ModuleTab.of("trident", "Trident", this::buildTridentSections));
    }

    // Always last
    tabs.add(ModuleTab.of("debug", "Debug", this::buildDebugSections));
}
```

### Source Tracking Chain

```java
private SourceBadge.Source determineAttackDamageSource() {
    // Priority: DEV > NBT > VANILLA

    // Check for DevMod stats
    if (hasWeaponModStats()) {
        return SourceBadge.Source.DEV;
    }

    // Check for NBT modifications
    CompoundTag tag = item.getTag();
    if (tag != null && tag.contains("AttributeModifiers")) {
        return SourceBadge.Source.NBT;
    }

    // Default is vanilla
    return SourceBadge.Source.VANILLA;
}
```

### DPS Calculator

```java
private static final int CACHE_TTL_MS = 100;
private long lastCalcTime = 0;
private double cachedDPS = 0;

private double calculateDPS() {
    long now = System.currentTimeMillis();
    if (now - lastCalcTime < CACHE_TTL_MS) {
        return cachedDPS;
    }

    double damage = stats.getAttackDamage();
    double speed = stats.getAttackSpeed();
    double critMult = stats.getCriticalMultiplier();
    double critChance = stats.getCriticalChance();

    // DPS = base_damage * attack_speed * (1 + crit_bonus)
    double critBonus = critChance * (critMult - 1);
    cachedDPS = damage * speed * (1 + critBonus);

    lastCalcTime = now;
    return cachedDPS;
}
```

---

## Common Pitfalls

### 1. buildPayload() Returns Null
```java
// BAD - breaks apply functionality
@Override
public FriendlyByteBuf buildPayload() {
    return null;
}

// GOOD - always return valid payload
@Override
public FriendlyByteBuf buildPayload() {
    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
    buf.writeEnum(PayloadType.MY_MODULE);
    stats.toNetwork(buf);
    return buf;
}
```

### 2. Missing Source Tracking
```java
// BAD - no context for user
slider.value(getValue());

// GOOD - user knows data origin
slider.value(getValue())
      .sourceBadge(determineSource())
      .defaultValue(getVanillaDefault())
      .showDefaultMarker(true);
```

### 3. No Variant Handling
```java
// BAD - same UI for all item types
tabs.add(fixedTab1);
tabs.add(fixedTab2);

// GOOD - adapt to item type
if (isSpecialVariant()) {
    tabs.add(specialTab);
}
```

### 4. Hardcoded Magic Values
```java
// BAD
slider.color(0xFFFF5555);

// GOOD
slider.color(UIConstants.SliderColors.DAMAGE);
```

---

## Testing Checklist

### Level 1-2
- [ ] Sliders update values correctly
- [ ] Payload is sent on Apply
- [ ] No crashes on open/close

### Level 3
- [ ] Source badges show correct origin
- [ ] Info tooltips appear
- [ ] Default markers visible
- [ ] Validation prevents invalid input

### Level 4
- [ ] Variant detection works for all types
- [ ] Calculator updates in real-time
- [ ] Variant-specific tabs appear/hide correctly

### Level 5
- [ ] Undo/redo works across all changes
- [ ] Debug tab shows accurate data
- [ ] Export produces valid output
- [ ] Full round-trip (save → reload → values match)

---

## Related Documents
- [23-architecture-comparison.md](23-architecture-comparison.md) - System overview
- [15-weapon-properties.md](15-weapon-properties.md) - WeaponModule details
- [16-armor-properties.md](16-armor-properties.md) - ArmorModule details
- [27-general-module-hub.md](27-general-module-hub.md) - GeneralModule redesign
