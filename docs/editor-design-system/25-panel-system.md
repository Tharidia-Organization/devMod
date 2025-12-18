# 25 - Panel System

## Overview

Il Panel System è progettato per settings screens, dashboard views e config toggles. È più semplice dell'Editor System e non richiede undo/redo o payload building.

**Location:** `src/main/java/com/frenkvs/devmod/ui/testing/panel/`

---

## UIPanel Interface

Sealed interface che definisce tutti i tipi di panel permessi.

```java
public sealed interface UIPanel permits
    HeaderPanel,
    SectionPanel,
    CollapsiblePanel,
    StatusPanel,
    SliderPanel,
    GridPanel,
    SpacerPanel,
    CompositePanel {

    String id();
    String title();
    int getHeight(int availableWidth);
    void render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY);

    default boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
    default boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) { return false; }
    default boolean mouseReleased(double mouseX, double mouseY, int button) { return false; }
    default boolean mouseScrolled(double mouseX, double mouseY, double delta) { return false; }
    default void tick() {}
    default void init(int x, int y, int width, int height) {}
    default boolean isVisible() { return true; }
}
```

---

## Panel Types

### HeaderPanel

Titolo sezione con separatore opzionale.

```java
// Simple header
new HeaderPanel("SYSTEM SETTINGS")

// Header with custom color
new HeaderPanel("header-combat", "COMBAT MECHANICS", 0xFF3D5AFE)

// Header with separator
HeaderPanel.withSeparator("VISUAL EFFECTS")
```

**Layout:**
```
═══════════════════════════════════════
  SYSTEM SETTINGS
═══════════════════════════════════════
```

---

### SectionPanel

Gruppo di controlli con titolo, descrizione e bottoni.

```java
// Using builder
SectionPanel.builder("section-debug", "Debug Systems")
    .description("Debug rendering and overlays")
    .addButton(debugToggle)
    .addButton(overlayToggle)
    .build()

// With button row
SectionPanel.builder("section-hud", "HUD Systems")
    .description("On-screen displays")
    .addRow(hudToggle1, hudToggle2, hudToggle3)
    .build()

// With separator
SectionPanel.builder("section-effects", "Effects")
    .addButton(vfxToggle)
    .separator(true)
    .build()
```

**Layout:**
```
┌─────────────────────────────────────────┐
│ Debug Systems                           │
│ Debug rendering and overlays            │
│                                         │
│ [Debug Overlay]  [Show Boxes]           │
└─────────────────────────────────────────┘
```

---

### CollapsiblePanel

Sezione espandibile/comprimibile.

```java
// Basic collapsible
new CollapsiblePanel(
    "collapsible-mult",
    "Damage Multipliers",
    contentPanel,  // UIPanel da mostrare quando espanso
    0xFFFF5500     // Header accent color
)

// With nested content
new CollapsiblePanel(
    "collapsible-armor",
    "Armor Penetration",
    CompositePanel.of("armor-content", "Armor Settings",
        SliderPanel.of("slider-mult", "Penetration Multiplier", ...),
        SliderPanel.of("slider-flat", "Flat Bonus", ...)
    ),
    0xFF00AAFF
)
```

**Layout (collapsed):**
```
┌─────────────────────────────────────────┐
│ ▶ Damage Multipliers                    │
└─────────────────────────────────────────┘
```

**Layout (expanded):**
```
┌─────────────────────────────────────────┐
│ ▼ Damage Multipliers                    │
├─────────────────────────────────────────┤
│   Head Multiplier          ════●════    │
│   Body Multiplier          ════●════    │
│   Arms Multiplier          ════●════    │
└─────────────────────────────────────────┘
```

---

### StatusPanel

Indicatori di stato multipli.

```java
StatusPanel.builder("status-overview")
    .addStatus("Debug", () -> DebugRenderer.isEnabled())
    .addStatus("HUD", () -> ImpactHudOverlay.isEnabled())
    .addStatus("VFX", () -> Config.IMPACT_VFX_ENABLED.get())
    .addStatus("Recording", () -> Config.TELEMETRY_ENABLED.get())
    .messageSupplier(() -> "FPS: " + mc.getFps())
    .build()
```

**Layout:**
```
┌─────────────────────────────────────────┐
│ Debug: ● ON   HUD: ● ON   VFX: ○ OFF    │
│ Recording: ● ON                          │
│ FPS: 60 | Entities: 42 | Memory: 1.2GB  │
└─────────────────────────────────────────┘
```

---

### SliderPanel

Wrapper per slider con label.

```java
// Using factory method
SliderPanel.of(
    "slider-intensity",
    "VFX Intensity",
    () -> Config.IMPACT_VFX_INTENSITY.get(),  // getter
    v -> Config.IMPACT_VFX_INTENSITY.set(v),   // setter
    0.1, 2.0, 0.1,                             // min, max, step
    "%.1fx"                                    // format
)

// With color
SliderPanel.of("slider-damage", "Head Multiplier", ...)
    .color(UIConstants.SliderColors.DAMAGE)
```

**Layout:**
```
┌─────────────────────────────────────────┐
│ VFX Intensity                    1.5x   │
│ ════════════════●═══════════════════    │
└─────────────────────────────────────────┘
```

---

### GridPanel

Layout a griglia per child panels.

```java
// 2 columns
GridPanel.of("grid-toggles", 2,
    togglePanel1,
    togglePanel2,
    togglePanel3,
    togglePanel4
)

// 3 columns
GridPanel.of("grid-presets", 3,
    presetLow,
    presetMed,
    presetHigh
)
```

**Layout (2 columns):**
```
┌─────────────────┬─────────────────┐
│   [Toggle 1]    │   [Toggle 2]    │
├─────────────────┼─────────────────┤
│   [Toggle 3]    │   [Toggle 4]    │
└─────────────────┴─────────────────┘
```

---

### SpacerPanel

Spaziatura fissa tra panels.

```java
// 8px spacer
new SpacerPanel("spacer-status", 8)

// 16px spacer
new SpacerPanel("spacer-section", 16)
```

---

### CompositePanel

Container per panels annidati.

```java
CompositePanel.of("composite-settings", "Settings",
    new HeaderPanel("General"),
    SliderPanel.of("slider-1", ...),
    SliderPanel.of("slider-2", ...),
    new SpacerPanel("spacer", 8),
    new HeaderPanel("Advanced"),
    SliderPanel.of("slider-3", ...)
)
```

---

## PanelContainer

Container scrollabile che gestisce una lista di panels.

```java
public class PanelContainer {
    private final List<UIPanel> panels = new ArrayList<>();
    private int scrollOffset = 0;

    public void addPanel(UIPanel panel) { ... }
    public void clear() { ... }
    public void setBounds(int x, int y, int width, int height) { ... }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) { ... }
    public boolean mouseClicked(double mouseX, double mouseY, int button) { ... }
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) { ... }
    public void tick() { ... }
}
```

### Usage in Page

```java
public class MyPage extends AbstractVoxelLabPage {
    @Override
    protected void buildPanels() {
        panelContainer.addPanel(new HeaderPanel("MY PAGE"));
        panelContainer.addPanel(
            SectionPanel.builder("section-main", "Main Settings")
                .addButton(toggle1)
                .build()
        );
        panelContainer.addPanel(new SpacerPanel("spacer", 8));
        panelContainer.addPanel(
            SliderPanel.of("slider-value", "Value", ...)
        );
    }
}
```

---

## AbstractVoxelLabPage

Template base per pagine VoxelLab.

```java
public abstract class AbstractVoxelLabPage implements VoxelLabPage {
    protected final VoxelLabTab tab;
    protected final PanelContainer panelContainer;

    protected AbstractVoxelLabPage(VoxelLabTab tab) {
        this.tab = tab;
        this.panelContainer = new PanelContainer();
    }

    // Template method - override this
    protected abstract void buildPanels();

    // Optional hook for per-tick updates
    protected void onTick() {}

    // Rebuild panels (call when config changes externally)
    protected void rebuildPanels() {
        panelContainer.clear();
        buildPanels();
    }

    // VoxelLabPage interface delegated to panelContainer
    @Override
    public void init(int x, int y, int width, int height) {
        panelContainer.setBounds(x, y, width, height);
        rebuildPanels();
    }

    @Override
    public void tick() {
        onTick();
        panelContainer.tick();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        panelContainer.render(g, mouseX, mouseY);
    }
}
```

---

## ButtonRow

Helper per layout bottoni in riga.

```java
// Three types
public sealed interface ButtonRow permits
    ButtonRow.FullWidth,    // Single button, full width
    ButtonRow.EqualWidth,   // Multiple buttons, equal width
    ButtonRow.Spacer        // Empty space
```

### Usage in SectionPanel

```java
SectionPanel.builder("section", "Title")
    // Single full-width button
    .addButton(mainButton)

    // Multiple equal-width buttons in row
    .addRow(btn1, btn2, btn3)

    .build()
```

---

## Creating a New Page

### Step 1: Extend AbstractVoxelLabPage

```java
public class MyNewPage extends AbstractVoxelLabPage {

    private EditorButton myToggle;

    public MyNewPage() {
        super(VoxelLabTab.MY_TAB);
    }

    @Override
    protected void buildPanels() {
        createButtons();

        panelContainer.addPanel(new HeaderPanel("MY PAGE TITLE"));

        panelContainer.addPanel(
            SectionPanel.builder("section-main", "Main Section")
                .description("Configure main settings")
                .addButton(myToggle)
                .build()
        );

        panelContainer.addPanel(
            SliderPanel.of("slider-value", "Some Value",
                () -> safeGetDouble(Config.MY_VALUE, 1.0),
                v -> Config.MY_VALUE.set(v),
                0.0, 10.0, 0.1, "%.1f")
        );
    }

    private void createButtons() {
        myToggle = new EditorButton("toggle-my", "My Feature")
            .toggleable(true)
            .toggled(safeGetBool(Config.MY_FEATURE_ENABLED))
            .style(EditorButton.Style.PRIMARY)
            .onToggle(v -> Config.MY_FEATURE_ENABLED.set(v));
    }

    @Override
    protected void onTick() {
        // Sync button states with config
        myToggle.toggled(safeGetBool(Config.MY_FEATURE_ENABLED));
    }

    // Utility methods (TODO: extract to PageUtils)
    private static boolean safeGetBool(BooleanValue config) {
        try { return config.get(); }
        catch (Exception e) { return false; }
    }

    private static double safeGetDouble(DoubleValue config, double def) {
        try { return config.get(); }
        catch (Exception e) { return def; }
    }
}
```

### Step 2: Register in VoxelLabTab

```java
public enum VoxelLabTab {
    // ... existing tabs ...
    MY_TAB("My Tab", "★", "Description of my tab");
}
```

### Step 3: Add to VoxelLabScreen

```java
private VoxelLabPage createPage(VoxelLabTab tab) {
    return switch (tab) {
        // ... existing cases ...
        case MY_TAB -> new MyNewPage();
    };
}
```

---

## Best Practices

### 1. Use Builder Pattern
```java
// Good
SectionPanel.builder("id", "Title")
    .description("...")
    .addButton(btn)
    .build()

// Avoid
new SectionPanel("id", "Title", "...", List.of(btn), false)
```

### 2. Use Factory Methods
```java
// Good
SliderPanel.of("id", "Label", getter, setter, min, max, step, format)

// Avoid
new SliderPanel("id", "Label", new EditorSlider(...))
```

### 3. Group Related Controls
```java
// Good - logical grouping
new CollapsiblePanel("damage", "Damage Settings",
    CompositePanel.of(...damageSliders...))

// Avoid - flat list of unrelated controls
panelContainer.addPanel(slider1);
panelContainer.addPanel(slider2);
panelContainer.addPanel(toggle1);
```

### 4. Sync State in onTick()
```java
@Override
protected void onTick() {
    // Keep UI in sync with external config changes
    myToggle.toggled(Config.MY_FEATURE.get());
}
```

### 5. Use Semantic Spacing
```java
// Good - spacer before status section
panelContainer.addPanel(new SpacerPanel("spacer", 8));
panelContainer.addPanel(statusPanel);

// Avoid - arbitrary magic numbers
```

---

## Related Documents
- [23-architecture-comparison.md](23-architecture-comparison.md) - When to use Panel vs Editor system
- [24-component-library.md](24-component-library.md) - EditorButton, Slider components
- [26-module-evolution-guide.md](26-module-evolution-guide.md) - Module maturity levels
