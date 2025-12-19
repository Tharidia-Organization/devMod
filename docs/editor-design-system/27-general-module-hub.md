# 27 - GeneralModule: Navigation Hub Specification

## Status: ✅ IMPLEMENTED

> **Implemented in:** GeneralModule.java (378 lines), ModuleCardSection.java, ModuleSummarySection.java
> **Date:** December 2024

---

## Problem Statement (RESOLVED)

Il GeneralModule precedente (180 righe) era un fallback minimale che:
- ❌ Non guidava l'utente verso il modulo corretto
- ❌ Non mostrava quali moduli sono applicabili all'item
- ❌ Non forniva overview cross-module
- ❌ Aveva `buildPayload()` che ritornava null

**Soluzione implementata:** GeneralModule trasformato in **Navigation Hub** che aiuta l'utente a scegliere dove modificare l'item.

---

## Implemented Architecture

### Tab Structure

| Tab | Scopo | Status |
|-----|-------|--------|
| **Overview** | Navigation hub con module cards | ✅ Implementato |
| **Quick Settings** | Durability, stack size, unbreakable | ✅ Implementato |
| **Status** | Cross-module summaries con source badges | ✅ Implementato |
| **Info** | Read-only metadata + capabilities | ✅ Implementato |

---

## Tab 1: Overview (Navigation Hub)

### Implementazione Attuale

```java
private List<EditorSection> getOverviewSections() {
    List<EditorSection> sections = new ArrayList<>();

    // Header section
    sections.add(new InfoListSection("overview-header", "Item Editing", List.of(
        "Select a module below to edit specific properties.",
        "Current item: " + item.getHoverName().getString()
    )));

    // Module cards based on item capabilities
    if (isWeaponItem()) {
        sections.add(ModuleCardSection.weapon(callback));
    }
    if (isArmorItem()) {
        sections.add(ModuleCardSection.armor(callback));
    }
    sections.add(ModuleCardSection.recipe(callback));

    return sections;
}
```

### ModuleCardSection (NEW FILE)

File: `ui/editor/sections/ModuleCardSection.java`

```java
public final class ModuleCardSection implements EditorSection.CustomSection {
    private final String id;
    private final String icon;
    private final String title;
    private final String description;
    private final int accentColor;
    private final EditorStartTab targetTab;
    private final Consumer<EditorStartTab> switchCallback;

    // Factory methods
    public static ModuleCardSection weapon(Consumer<EditorStartTab> callback);
    public static ModuleCardSection armor(Consumer<EditorStartTab> callback);
    public static ModuleCardSection recipe(Consumer<EditorStartTab> callback);
}
```

**Features:**
- Card cliccabile con icona, titolo, descrizione
- Hover states con colori accent
- Click handler che invoca `switchCallback`
- Factory methods per moduli predefiniti

---

## Tab 2: Quick Settings

### Implementazione Attuale

```java
private List<EditorSection> getQuickSettingsSections() {
    List<EditorSection> sections = new ArrayList<>();

    sections.add(new SliderSectionAdapter(stackSizeSlider));      // 1-64
    sections.add(new ToggleSectionAdapter(unbreakableToggle));   // true/false

    if (item.isDamageableItem()) {
        sections.add(new SliderSectionAdapter(durabilitySlider)); // 0-maxDamage
        sections.add(new SliderSectionAdapter(repairCostSlider)); // 0-40
    }

    return sections;
}
```

---

## Tab 3: Status (Cross-Module Summaries)

### ModuleSummarySection (NEW FILE)

File: `ui/editor/sections/ModuleSummarySection.java`

```java
public final class ModuleSummarySection implements EditorSection.CustomSection {
    private final String id;
    private final String title;
    private final int accentColor;
    private final List<StatEntry> stats;

    public record StatEntry(
        String label,
        double value,
        String format,
        int color,
        String source  // "VAN", "DEV", "NBT"
    ) {}

    // Builder pattern
    public static Builder builder(String id, String title);
}
```

### Summaries Implementati

```java
private ModuleSummarySection buildWeaponSummary() {
    // Extract from SwordItem/TieredItem
    return ModuleSummarySection.builder("summary-weapon", "Weapon Stats")
        .accentColor(UIConstants.Accent.RED())
        .addStat("Base Damage", damage, "%.1f", UIConstants.Accent.RED(), "VAN")
        .addStat("Attack Speed", speed, "%.2f/s", UIConstants.Text.PRIMARY(), "VAN")
        .addStat("DPS", dps, "%.1f", UIConstants.Accent.ORANGE(), null)
        .build();
}

private ModuleSummarySection buildArmorSummary() {
    // Extract from ArmorItem
    return ModuleSummarySection.builder("summary-armor", "Armor Stats")
        .accentColor(UIConstants.Accent.BLUE())
        .addStat("Defense", defense, "%.0f", UIConstants.Accent.BLUE(), "VAN")
        .addStat("Toughness", toughness, "%.1f", UIConstants.Text.PRIMARY(), "VAN")
        .build();
}

private ModuleSummarySection buildGeneralSummary() {
    // Stack size + durability percentage
    return ModuleSummarySection.builder("summary-general", "Item Properties")
        .accentColor(UIConstants.Accent.INFO())
        .addStat("Max Stack", stackSize, "%.0f", ...)
        .addStat("Durability", durabilityPercent, "%.0f%%", colorByPercent, null)
        .build();
}
```

---

## Tab 4: Info (Read-Only Metadata)

### Implementazione Attuale

```java
private List<EditorSection> getInfoSections() {
    List<String> info = new ArrayList<>();

    info.add("Name: " + item.getHoverName().getString());
    info.add("Type: " + item.getItem().getClass().getSimpleName());
    info.add("Registry: " + item.getItem().builtInRegistryHolder().key().location());
    info.add("Rarity: " + item.getRarity().name());
    info.add("Max Stack: " + item.getMaxStackSize());

    if (item.isDamageableItem()) {
        info.add("Max Durability: " + item.getMaxDamage());
        info.add("Current Damage: " + item.getDamageValue());
    }

    // Capabilities detection
    List<String> capabilities = new ArrayList<>();
    if (isWeaponItem()) capabilities.add("Weapon");
    if (isArmorItem()) capabilities.add("Armor");
    if (item.isEnchantable()) capabilities.add("Enchantable");
    if (item.isDamageableItem()) capabilities.add("Damageable");

    return List.of(new InfoListSection("info", "Item Information", info));
}
```

---

## Module Switching Integration

### EditorModule Interface (UPDATED)

```java
// Added to EditorModule.java
default void setModuleSwitchCallback(Consumer<EditorStartTab> callback) {}
default List<EditorStartTab> getAvailableModules() { return List.of(); }
```

### ItemEditorScreen Integration (IMPLEMENTED)

```java
// In init()
activeModule.setModuleSwitchCallback(this::switchModule);

// New method
private void switchModule(EditorStartTab targetTab) {
    if (targetTab == null) return;

    // Check for unsaved changes
    if (activeModule != null && activeModule.hasUnsavedChanges()) {
        showStatus("Unsaved changes - save or discard first", UIConstants.Accent.ORANGE());
        return;
    }

    // Close current module
    if (activeModule != null) {
        activeModule.onClose();
    }

    // Switch to new module
    activeModule = resolveModule(item, targetTab);
    activeModule.setStatusConsumer((msg, color) -> showStatus(msg, color));
    activeModule.setModuleSwitchCallback(this::switchModule);
    activeModule.setItem(item);
    activeModule.init(layout);
    activeModule.setDirtyTrackingEnabled(true);
    activeModule.clearDirty();

    configureHeader();
    configureLeftColumn();
    scrollArea.setScrollOffset(0);

    showStatus("Switched to " + activeModule.getTitle(), UIConstants.Accent.INFO());
}
```

---

## Detection Logic (IMPLEMENTED)

```java
// In GeneralModule.java

private boolean isWeaponItem() {
    return item != null && (
        item.getItem() instanceof SwordItem ||
        item.getItem() instanceof TieredItem ||
        item.getItem() instanceof ProjectileWeaponItem
    );
}

private boolean isArmorItem() {
    return item != null && item.getItem() instanceof ArmorItem;
}

private boolean hasRecipe() {
    return item != null; // All items can have recipes
}

@Override
public List<EditorStartTab> getAvailableModules() {
    List<EditorStartTab> modules = new ArrayList<>();
    if (isWeaponItem()) modules.add(EditorStartTab.WEAPON);
    if (isArmorItem()) modules.add(EditorStartTab.ARMOR);
    if (hasRecipe()) modules.add(EditorStartTab.RECIPE);
    modules.add(EditorStartTab.GENERAL);
    return modules;
}
```

---

## Files Created/Modified

### New Files ✅
| File | Description |
|------|-------------|
| `ui/editor/sections/ModuleCardSection.java` | Clickable card for module navigation |
| `ui/editor/sections/ModuleSummarySection.java` | Cross-module stats display with badges |

### Modified Files ✅
| File | Changes |
|------|---------|
| `ui/editor/EditorModule.java` | Added `setModuleSwitchCallback()`, `getAvailableModules()` |
| `ui/editor/ItemEditorScreen.java` | Added `switchModule()` method |
| `ui/editor/modules/GeneralModule.java` | Complete rewrite (180 → 378 lines) |

---

## Success Criteria

- [x] Overview tab mostra module cards
- [x] Clicking card switches to correct module
- [x] Item type detection works (Weapon/Armor)
- [x] Status tab shows cross-module summaries
- [x] Source badges visible in summaries (VAN)
- [x] Quick Settings tab functional
- [ ] `buildPayload()` ritorna payload funzionale (FUTURE)

---

## Related Documents
- [23-architecture-comparison.md](23-architecture-comparison.md) - System comparison
- [15-weapon-properties.md](15-weapon-properties.md) - WeaponModule reference
- [16-armor-properties.md](16-armor-properties.md) - ArmorModule reference
