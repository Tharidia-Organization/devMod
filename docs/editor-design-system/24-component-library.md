# 24 - Component Library

## Overview

Libreria componenti UI condivisi tra Editor System e Panel System.

**Location:** `src/main/java/com/frenkvs/devmod/ui/editor/components/`

---

## EditorButton

Bottone versatile con stili multipli, toggle state, icons e hotkey hints.

### Usage

```java
// Basic button
EditorButton basic = new EditorButton("btn-save", "Save");

// Styled button
EditorButton primary = new EditorButton("btn-apply", "Apply")
    .style(EditorButton.Style.PRIMARY);

// Toggle button with icon
EditorButton toggle = new EditorButton("btn-debug", "Debug Mode")
    .toggleable(true)
    .toggled(isDebugEnabled)
    .style(EditorButton.Style.SUCCESS)
    .icon("⚙")
    .onToggle(value -> setDebugEnabled(value));

// Button with hotkey hint
EditorButton hotkey = new EditorButton("btn-boxes", "Show Boxes")
    .toggleable(true)
    .hotkeyHint("[Shift+G]");
```

### Styles

| Style | Color | Use Case |
|-------|-------|----------|
| `NORMAL` | Gray | Default actions |
| `PRIMARY` | Blue | Main actions |
| `DANGER` | Red | Destructive actions, recording |
| `SUCCESS` | Green | Positive states, enabled |
| `GHOST` | Transparent | Secondary/grouped buttons |

### Sizes

| Size | Height | Use Case |
|------|--------|----------|
| `SMALL` | 16px | Inline buttons, rows |
| `MEDIUM` | 20px | Default |
| `LARGE` | 24px | Primary actions |

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `toggleable(boolean)` | Builder | Enable toggle mode |
| `toggled(boolean)` | Builder | Set toggle state |
| `style(Style)` | Builder | Set visual style |
| `size(Size)` | Builder | Set button size |
| `icon(String)` | Builder | Left-aligned icon glyph |
| `hotkeyHint(String)` | Builder | Right-aligned hint text |
| `accentColor(int)` | Builder | Override accent color |
| `onToggle(Consumer<Boolean>)` | Builder | Toggle callback |
| `onClick(Runnable)` | Builder | Click callback |

### Rendering

```
┌─────────────────────────────────┐
│ ⚙ Debug Mode         [Shift+G] │
└─────────────────────────────────┘
 ↑                            ↑
 icon                    hotkeyHint
```

---

## EditorSlider

Slider numerico con source badges, info buttons, default markers.

### Usage

```java
// Basic slider
EditorSlider damage = new EditorSlider("slider-damage", 0, 20)
    .value(7.0)
    .label("Attack Damage")
    .format("%.1f");

// Slider with source badge
EditorSlider withSource = new EditorSlider("slider-armor", 0, 30)
    .value(getArmorValue())
    .sourceBadge(SourceBadge.Source.DEV)
    .label("Armor Points")
    .color(UIConstants.SliderColors.DEFENSE);

// Slider with info button
EditorSlider withInfo = new EditorSlider("slider-speed", 0, 4)
    .value(1.6)
    .label("Attack Speed")
    .info("attack_speed_tooltip")
    .defaultValue(1.6)
    .showDefaultMarker(true);

// Percentage slider
EditorSlider percent = new EditorSlider("slider-crit", 0, 100)
    .value(25)
    .label("Critical Chance")
    .format("%.0f%%")
    .color(UIConstants.SliderColors.PERCENT);
```

### Colors

| Color Constant | Use Case |
|----------------|----------|
| `SliderColors.DAMAGE` | Offensive stats (red) |
| `SliderColors.DEFENSE` | Defensive stats (blue) |
| `SliderColors.SPEED` | Speed/performance (cyan) |
| `SliderColors.NEUTRAL` | Generic values (gray) |
| `SliderColors.SPECIAL` | Special effects (purple) |
| `SliderColors.PERCENT` | Percentage values (yellow) |
| `SliderColors.DURABILITY` | Durability (green) |

### Features

| Feature | Description |
|---------|-------------|
| **Drag** | Click and drag thumb to adjust |
| **Click-to-set** | Click anywhere on track |
| **Keyboard** | LEFT/RIGHT (step), HOME/END (bounds), BACKSPACE (reset) |
| **Scrollwheel** | Scroll to adjust when hovered |
| **Default marker** | Shows original value on track |
| **Source badge** | DEV/NBT/VANILLA indicator |
| **Info button** | (i) tooltip trigger |

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `value(double)` | Builder | Current value |
| `label(String)` | Builder | Display label |
| `format(String)` | Builder | Value format string |
| `color(int)` | Builder | Track fill color |
| `step(double)` | Builder | Increment step |
| `sourceBadge(Source)` | Builder | Origin indicator |
| `info(String)` | Builder | Info tooltip key |
| `defaultValue(double)` | Builder | Original/default value |
| `showDefaultMarker(boolean)` | Builder | Show marker on track |
| `onChange(Consumer<Double>)` | Builder | Value change callback |

### Layout

```
┌─────────────────────────────────────────────────────┐
│ Attack Damage                    [DEV] (i)    7.0   │
│ ════════════●═══════════════════════════════════    │
│             ↑                                       │
│         current (7.0)                               │
│                    ▲                                │
│              default marker                         │
└─────────────────────────────────────────────────────┘
```

---

## EditorToggle

Switch booleano con source tracking.

### Usage

```java
// Basic toggle
EditorToggle unbreakable = new EditorToggle("toggle-unbreakable", false)
    .label("Unbreakable");

// Toggle with source badge
EditorToggle withSource = new EditorToggle("toggle-thorns", true)
    .label("Thorns Reflect")
    .sourceBadge(SourceBadge.Source.NBT);

// Toggle with callback
EditorToggle bodyPart = new EditorToggle("toggle-bodypart", isEnabled)
    .label("Body Part Detection")
    .onChange(value -> Config.BODY_PART_DETECTION_ENABLED.set(value));
```

### States

| State | Visual |
|-------|--------|
| OFF | Gray track, handle left |
| ON | Green track, handle right |
| Hovered | Slight highlight |
| Disabled | Grayed out |

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `value(boolean)` | Builder | Current state |
| `label(String)` | Builder | Display label |
| `sourceBadge(Source)` | Builder | Origin indicator |
| `onChange(Consumer<Boolean>)` | Builder | State change callback |

### Layout

```
┌─────────────────────────────────────┐
│ Body Part Detection      [DEV] [●═] │
└─────────────────────────────────────┘
                                  ↑
                            toggle switch
```

---

## EditorTextField

Campo di input testo con validazione.

### Usage

```java
// Basic text field
EditorTextField name = new EditorTextField("field-name", "")
    .placeholder("Enter item name...");

// Numeric field with range
EditorTextField stackSize = EditorTextField.numeric("field-stack", 1, 99)
    .value("64")
    .label("Stack Size");

// Field with validation
EditorTextField tag = new EditorTextField("field-tag", "")
    .placeholder("minecraft:is_sword")
    .validator(text -> text.matches("[a-z_:]+"));

// Field with max length
EditorTextField desc = new EditorTextField("field-desc", "")
    .maxLength(100)
    .placeholder("Description...");
```

### Features

| Feature | Keyboard |
|---------|----------|
| Selection | Shift+Arrow, Ctrl+A |
| Clipboard | Ctrl+C, Ctrl+X, Ctrl+V |
| Navigation | Arrow keys, Home, End |
| Word jump | Ctrl+Arrow |
| Delete | Backspace, Delete |

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `value(String)` | Builder | Current text |
| `placeholder(String)` | Builder | Placeholder text |
| `maxLength(int)` | Builder | Max characters |
| `validator(Predicate<String>)` | Builder | Validation function |
| `numeric(String, min, max)` | Factory | Create numeric field |
| `onChange(Consumer<String>)` | Builder | Text change callback |

### Layout

```
┌─────────────────────────────────────────┐
│ Stack Size                              │
│ ┌─────────────────────────────────────┐ │
│ │ 64                                  │ │
│ └─────────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

---

## SourceBadge

Indicatore origine dati.

### Sources

| Source | Color | Meaning |
|--------|-------|---------|
| `DEV` | Blue | Modified by DevMod editor |
| `NBT` | Orange | Loaded from item NBT |
| `VANILLA` | Gray | Default Minecraft value |
| `MODIFIED` | Yellow | Changed from original |

### Usage

```java
// Render badge
SourceBadge.render(graphics, x, y, SourceBadge.Source.DEV);

// In slider
slider.sourceBadge(SourceBadge.Source.DEV);
```

### Layout

```
[DEV]   ← 32x12px badge
```

---

## InfoButton

Bottone (i) per tooltips informativi.

### Usage

```java
// Create info button
InfoButton info = new InfoButton("info-damage", "attack_damage_tooltip");

// Render
info.render(graphics, x, y, mouseX, mouseY);

// In slider
slider.info("attack_damage_tooltip");
```

### Behavior
- Click: Toggle tooltip visibility
- Hover: Show subtle highlight
- Tooltip: Displayed via TooltipManager

---

## UIConstants

Costanti centralizzate per colori e spacing.

### Colors

```java
// Background colors
UIConstants.Background.PANEL()      // Panel background
UIConstants.Background.INPUT()      // Input field background
UIConstants.Background.HOVER()      // Hover state
UIConstants.Background.ACTIVE()     // Active/pressed state

// Text colors
UIConstants.Text.PRIMARY()          // Main text
UIConstants.Text.SECONDARY()        // Subtitle/description
UIConstants.Text.MUTED()            // Disabled text
UIConstants.Text.TITLE()            // Headers

// Border colors
UIConstants.Border.DEFAULT()        // Normal borders
UIConstants.Border.ACCENT()         // Highlighted borders
UIConstants.Border.SUCCESS()        // Success state
UIConstants.Border.ERROR()          // Error state

// Accent colors
UIConstants.Accent.GREEN()          // Success, enabled
UIConstants.Accent.RED()            // Error, danger
UIConstants.Accent.YELLOW()         // Warning
UIConstants.Accent.BLUE()           // Primary actions
```

### Spacing

```java
UIConstants.Spacing.XS   // 2px
UIConstants.Spacing.SM   // 4px
UIConstants.Spacing.MD   // 8px
UIConstants.Spacing.LG   // 12px
UIConstants.Spacing.XL   // 16px
```

---

## Best Practices

### 1. Use Builder Pattern
```java
// Good
new EditorButton("id", "Label")
    .style(PRIMARY)
    .icon("⚔")
    .onClick(this::handleClick);

// Avoid
EditorButton btn = new EditorButton("id", "Label");
btn.setStyle(PRIMARY);
btn.setIcon("⚔");
```

### 2. Provide Callbacks
```java
// Good - immediate feedback
slider.onChange(value -> {
    stats.setDamage(value);
    markDirty();
});

// Avoid - polling
// checking slider value in tick()
```

### 3. Use Semantic Colors
```java
// Good
slider.color(UIConstants.SliderColors.DAMAGE);

// Avoid
slider.color(0xFFFF5555);  // Magic color
```

### 4. Always Set IDs
```java
// Good - unique, descriptive
new EditorButton("btn-apply-weapon", "Apply");

// Avoid
new EditorButton("button1", "Apply");
```

---

## Related Documents
- [23-architecture-comparison.md](23-architecture-comparison.md) - System comparison
- [25-panel-system.md](25-panel-system.md) - Panel wrappers for components
- [02-shared-components.md](02-shared-components.md) - Original component spec
