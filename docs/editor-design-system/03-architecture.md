# Architecture

## Unified Editor Architecture

> **Architettura confermata:** Un solo `ItemEditorScreen` con moduli per tipo item.

### Filosofia

```
┌─────────────────────────────────────────────────────────────────┐
│  PROBLEMA: Due editor separati = duplicazione bug UI/layout    │
│  ─────────────────────────────────────────────────────────────  │
│                                                                 │
│  ✗ ArmorEditorScreen.java  ─┬─→ Bug layout qui                  │
│  ✗ WeaponEditorScreen.java ─┘   Bug layout anche qui            │
│                                                                 │
│  "Shared components" non basta: i bug nascono da LAYOUT e FLOW │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│  SOLUZIONE: Un solo ItemEditorScreen con Content Modules       │
│  ─────────────────────────────────────────────────────────────  │
│                                                                 │
│  ✓ ItemEditorScreen.java    ← Shell identico sempre            │
│      ├── ResponsiveLayout   ← Layout principale responsive     │
│      ├── EditorLayout       ← Calcolo bounds componenti        │
│      ├── WeaponModule       ← Fornisce sezioni, non coordinate  │
│      ├── ArmorModule        ← Fornisce sezioni, non coordinate  │
│      ├── RangedModule       ← Per armi a distanza               │
│      └── GeneralModule      ← Future: pozioni, enchant books... │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Regole Architetturali NON NEGOZIABILI

| Regola | Descrizione |
|--------|-------------|
| **R1: Zero coordinate nei moduli** | I moduli NON piazzano widget. Ritornano `List<EditorSection>` |
| **R2: Layout engine unico** | `EditorLayout` + `ResponsiveLayout` renderizzano tutto |
| **R3: Shell fisso** | Header/Footer/Left column identici per tutti i moduli |
| **R4: Tab auto-select** | Aprendo da armor → tab Armor attivo. Da weapon → tab Weapon |

---

## Gerarchia Classi

### ItemEditorScreen

```java
// Screen principale - UNICO entry point
public class ItemEditorScreen extends Screen {

    // Layout (doppio sistema)
    private final ResponsiveLayout layout;          // Layout responsive principale
    private final EditorLayout editorLayout;        // Calcolo bounds specifici

    // Modules
    private EditorModule activeModule;              // Modulo corrente

    // Shell components (sempre identici)
    private final HeaderComponent header;
    private final FooterComponent footer;
    private final LeftColumnComponent leftColumn;
    private final ScrollableContentArea scrollArea;

    // Overlays (sistema modale)
    private final HelpOverlay helpOverlay;
    private final CraftingInfoPanel craftingPanel;
    private ConfirmDialog activeDialog;
    private TemplateOverlay templateOverlay;
    private DebugPanel debugPanel;
    private MultiEditPanel multiEditPanel;

    // State
    private boolean isPreviewMode = true;
    private boolean isGlobalMode = false;

    public ItemEditorScreen(ItemStack item, EditorStartTab startTab) {
        // Auto-detect tipo e seleziona modulo
        this.activeModule = resolveModule(item, startTab);
    }

    @Override
    protected void init() {
        // 1. Calculate UI scale
        float uiScale = EditorScaleCalculator.getEffectiveScale(width, height, ...);
        ScaledCoord.setScale(uiScale);

        // 2. Calculate layout
        var screenFit = EditorScaleCalculator.calculateFit(width, height, uiScale);
        layout.calculate(width, height, screenFit, uiScale);
        editorLayout.computePositions(screenFit, uiScale);

        // 3. Initialize module
        activeModule.setItem(item);
        activeModule.init(layout);

        // 4. Initialize shell components
        header.init(layout);
        footer.init(layout);
        leftColumn.init(layout);
    }
}
```

---

## EditorModule Interface

```java
/**
 * Interface per i moduli di contenuto.
 * I moduli NON gestiscono coordinate - ritornano solo dati strutturati.
 */
public interface EditorModule {

    // ═══════════════════════════════════════════════════════════════
    // IDENTIFICATION
    // ═══════════════════════════════════════════════════════════════

    /** Unique module ID */
    String getId();

    /** Display title */
    String getTitle();

    /** Module icon (optional) */
    default ResourceLocation getIcon() { return null; }

    // ═══════════════════════════════════════════════════════════════
    // TABS
    // ═══════════════════════════════════════════════════════════════

    /** Get available tabs for this module */
    List<ModuleTab> getTabs();

    /** Get currently active tab index */
    int getActiveTabIndex();

    /** Set active tab */
    void setActiveTab(int index);

    // ═══════════════════════════════════════════════════════════════
    // CONTENT
    // ═══════════════════════════════════════════════════════════════

    /** Initialize with item to edit */
    void setItem(ItemStack item);

    /** Get the current item being edited */
    ItemStack getItem();

    /** Initialize module with layout info */
    void init(ResponsiveLayout layout);

    /** Get sections for current tab (called by content area) */
    List<EditorSection> getSections();

    /** Render content into the provided area */
    void renderContent(GuiGraphics graphics, ResponsiveLayout.Rect contentBounds,
                       int mouseX, int mouseY);

    /** Calculate total content height for scroll */
    int calculateContentHeight();

    // ═══════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════

    boolean mouseClicked(double mouseX, double mouseY, int button);
    boolean mouseReleased(double mouseX, double mouseY, int button);
    boolean mouseDragged(double mouseX, double mouseY, int button,
                         double dragX, double dragY);
    boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY);
    boolean keyPressed(int keyCode, int scanCode, int modifiers);
    boolean charTyped(char chr, int modifiers);

    // ═══════════════════════════════════════════════════════════════
    // DIRTY TRACKING
    // ═══════════════════════════════════════════════════════════════

    /** Check if module has unsaved changes */
    boolean hasUnsavedChanges();

    /** Get list of pending changes (for dirty indicator) */
    List<String> getPendingChanges();

    /** Mark a change as pending */
    void markDirty(String changeDescription);

    /** Clear dirty state (after save) */
    void clearDirty();

    /** Provide a status consumer for modules to show messages */
    default void setStatusConsumer(BiConsumer<String, Integer> statusConsumer) {}

    /** Get history entries */
    List<String> getHistoryEntries();

    /** Clear history entries */
    void clearHistory();

    /** Log a timeline entry (for debug/session log) */
    default void logEvent(String description) {}

    /** Dirty tracking can be enabled/disabled (e.g., preview mode) */
    default void setDirtyTrackingEnabled(boolean enabled) {}

    /** Detect if current state differs from original */
    default boolean hasPendingDiff() { return hasUnsavedChanges(); }

    // ═══════════════════════════════════════════════════════════════
    // PREVIEW SYSTEM
    // ═══════════════════════════════════════════════════════════════

    /** Provide preview copy of the item (optional) */
    default ItemStack getPreviewItem() { return null; }

    /** Clear any preview state */
    default void clearPreview() {}

    /** Apply changes locally (preview mode) */
    void applyPreview();

    // ═══════════════════════════════════════════════════════════════
    // UNDO/REDO
    // ═══════════════════════════════════════════════════════════════

    boolean canUndo();
    boolean canRedo();
    void undo();
    void redo();
    void saveUndoState();

    // ═══════════════════════════════════════════════════════════════
    // NETWORK
    // ═══════════════════════════════════════════════════════════════

    /** Build payload for server sync */
    CustomPacketPayload buildPayload(boolean isGlobal);

    /** Reset to original values */
    void resetToOriginal();

    // ═══════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    /** Called when the module is closed */
    default void onClose() {}

    /** Called each tick */
    default void tick() {}
}
```

---

## AbstractEditorModule

Classe base che implementa `EditorModule` con funzionalità comuni:

```java
/**
 * Abstract base class for editor modules.
 * Provides common functionality for tab management, undo/redo, and dirty tracking.
 */
public abstract class AbstractEditorModule implements EditorModule {

    protected final String id;
    protected final String title;
    protected ItemStack item = ItemStack.EMPTY;
    protected ItemStack originalItem = ItemStack.EMPTY;
    protected ResponsiveLayout layout;

    // Tabs
    protected final List<ModuleTab> tabs = new ArrayList<>();
    protected int activeTabIndex = 0;

    // Dirty tracking
    protected final List<String> pendingChanges = new ArrayList<>();
    protected final List<String> historyEntries = new ArrayList<>();
    private BiConsumer<String, Integer> statusConsumer;

    // Undo/Redo stacks
    protected final Stack<UndoState> undoStack = new Stack<>();
    protected final Stack<UndoState> redoStack = new Stack<>();
    private static final int MAX_UNDO_STATES = 50;

    protected AbstractEditorModule(String id, String title) { ... }

    // ═══════════════════════════════════════════════════════════════
    // ABSTRACT METHODS (must implement)
    // ═══════════════════════════════════════════════════════════════

    /** Called when the item is set. Initialize module state here. */
    protected abstract void onItemSet();

    /** Initialize tabs for this module. */
    protected abstract void initializeTabs();

    /** Build network payload. */
    @Override
    public abstract CustomPacketPayload buildPayload(boolean isGlobal);

    // ═══════════════════════════════════════════════════════════════
    // UNDO STATE RECORD
    // ═══════════════════════════════════════════════════════════════

    protected record UndoState(
        String description,
        byte[] itemData,    // Serialized ItemStack via NBT
        int tabIndex
    ) {}
}
```

**Moduli concreti:**
- `WeaponModule extends AbstractEditorModule` - Armi melee
- `ArmorModule extends AbstractEditorModule` - Armature
- `RangedModule extends AbstractEditorModule` - Armi a distanza
- `GeneralModule extends AbstractEditorModule` - Item generici

---

## EditorSection - Unità di contenuto

```java
/**
 * Sealed interface for editor content sections.
 * Each section type defines a specific kind of editable content.
 * Sections handle their own rendering and input.
 */
public sealed interface EditorSection permits
    EditorSection.SliderSection,
    EditorSection.ToggleSection,
    EditorSection.InputSection,
    EditorSection.ListSection,
    EditorSection.HeaderSection,
    EditorSection.SpacerSection,
    EditorSection.CustomSection {

    /** Get section identifier */
    String getId();

    /** Get section label */
    String getLabel();

    /** Calculate height needed for this section */
    int getHeight();

    /** Render this section */
    void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY);

    /** Handle mouse click */
    default boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }

    /** Handle mouse release */
    default boolean mouseReleased(double mouseX, double mouseY, int button) { return false; }

    /** Handle mouse drag */
    default boolean mouseDragged(double mouseX, double mouseY, int button,
                                  double dragX, double dragY) { return false; }

    /** Handle key press */
    default boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }

    /** Handle character typed */
    default boolean charTyped(char chr, int modifiers) { return false; }

    // ═══════════════════════════════════════════════════════════════
    // SECTION TYPES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Slider section for numeric value editing.
     */
    non-sealed interface SliderSection extends EditorSection {
        float getValue();
        void setValue(float value);
        float getMin();
        float getMax();
        float getStep();
        String getFormat();
        int getColor();
        boolean isDragging();
        void setDragging(boolean dragging);
    }

    /**
     * Toggle section for boolean values.
     */
    non-sealed interface ToggleSection extends EditorSection {
        boolean getValue();
        void setValue(boolean value);
    }

    /**
     * Text input section.
     */
    non-sealed interface InputSection extends EditorSection {
        String getText();
        void setText(String text);
        String getPlaceholder();
        boolean isNumeric();
    }

    /**
     * List selection section.
     */
    non-sealed interface ListSection extends EditorSection {
        java.util.List<String> getOptions();
        int getSelectedIndex();
        void setSelectedIndex(int index);
    }

    /**
     * Header/title section for grouping (collapsible).
     */
    non-sealed interface HeaderSection extends EditorSection {
        boolean isCollapsible();
        boolean isCollapsed();
        void setCollapsed(boolean collapsed);
    }

    /**
     * Spacer for layout purposes.
     */
    non-sealed interface SpacerSection extends EditorSection {
        @Override default String getLabel() { return ""; }
        @Override default void render(GuiGraphics g, ResponsiveLayout.Rect b, int mx, int my) {}
    }

    /**
     * Custom section for complex content (extend for special cases).
     */
    non-sealed interface CustomSection extends EditorSection { }
}
```

---

## EditorLayout - Calcolo Bounds

```java
/**
 * Centralized layout calculator for editor panel bounds.
 * Integrates with EditorScaleCalculator and UIConstants.
 */
public class EditorLayout {

    private Bounds panelBounds = Bounds.EMPTY;
    private Bounds headerBounds = Bounds.EMPTY;
    private Bounds footerBounds = Bounds.EMPTY;
    private Bounds leftColumnBounds = Bounds.EMPTY;
    private Bounds contentBounds = Bounds.EMPTY;

    private final Map<String, List<SectionBounds>> columnSections = new HashMap<>();

    /**
     * Compute all bounds using screen fit result and scale.
     */
    public void computePositions(EditorScaleCalculator.ScreenFitResult fit, float scale) {
        int panelWidth = fit.panelWidth();
        int panelHeight = fit.panelHeight();
        int headerHeight = ScaledCoord.scaleDim(UIConstants.Size.HEADER_HEIGHT, scale);
        int footerHeight = ScaledCoord.scaleDim(UIConstants.Size.FOOTER_HEIGHT, scale);
        int leftWidth = ScaledCoord.scaleDim(UIConstants.PanelDimensions.LEFT_COLUMN_WIDTH, scale);

        panelBounds = new Bounds(fit.panelX(), fit.panelY(), panelWidth, panelHeight);
        headerBounds = new Bounds(fit.panelX(), fit.panelY(), panelWidth, headerHeight);
        footerBounds = new Bounds(fit.panelX(), fit.panelY() + panelHeight - footerHeight,
                                   panelWidth, footerHeight);
        leftColumnBounds = new Bounds(fit.panelX(), fit.panelY() + headerHeight,
                                       leftWidth, panelHeight - headerHeight - footerHeight);
        contentBounds = new Bounds(fit.panelX() + leftWidth, fit.panelY() + headerHeight,
                                    panelWidth - leftWidth,
                                    panelHeight - headerHeight - footerHeight);

        // Validate 4px grid alignment
        validateBounds(panelBounds, "panelBounds");
        // ... etc
    }

    /**
     * Layout sections within a column area.
     */
    public void layoutSectionsInColumn(String columnId, List<EditorSection> sections,
                                        Bounds area, int padding, int gap) {
        List<SectionBounds> boundsList = new ArrayList<>();
        int currentY = area.y() + padding;
        int sectionWidth = area.width() - (2 * padding);

        for (EditorSection section : sections) {
            int sectionHeight = section.getHeight();
            Bounds sectionArea = new Bounds(area.x() + padding, currentY,
                                             sectionWidth, sectionHeight);
            boundsList.add(new SectionBounds(section.getId(), sectionArea));
            currentY += sectionHeight + gap;
        }

        columnSections.put(columnId, boundsList);
    }

    // Getters
    public Bounds getPanelBounds() { return panelBounds; }
    public Bounds getHeaderBounds() { return headerBounds; }
    public Bounds getFooterBounds() { return footerBounds; }
    public Bounds getLeftColumnBounds() { return leftColumnBounds; }
    public Bounds getContentBounds() { return contentBounds; }

    public Bounds getSectionBounds(String columnId, String sectionId) { ... }
    public List<SectionBounds> getSectionsForColumn(String columnId) { ... }
}
```

---

## Overlay System

L'editor usa un sistema di overlay modali basato su `BaseOverlay`:

```java
/**
 * Abstract base class for modal overlay panels.
 * Provides common functionality for backdrop, centering, ESC handling.
 */
public abstract class BaseOverlay {
    protected boolean visible = false;

    public void show() { visible = true; }
    public void hide() { visible = false; }
    public void toggle() { visible = !visible; }
    public boolean isVisible() { return visible; }

    // Template method pattern
    public final void render(GuiGraphics graphics, Font font,
                              int screenWidth, int screenHeight,
                              int mouseX, int mouseY) {
        if (!visible) return;
        renderBackdrop(graphics, screenWidth, screenHeight);
        // Calculate centered panel position
        int panelW = ScaledCoord.scaleDim(getPanelWidth());
        int panelH = usesDynamicHeight()
            ? ScaledCoord.scaleDim(getPanelHeight(screenHeight))
            : ScaledCoord.scaleDim(getPanelHeight());
        int x = (screenWidth - panelW) / 2;
        int y = (screenHeight - panelH) / 2;
        renderPanel(graphics, x, y, panelW, panelH);
        renderContent(graphics, font, x, y, panelW, panelH, mouseX, mouseY);
    }

    protected abstract void renderContent(...);
    protected abstract int getPanelWidth();
    protected abstract int getPanelHeight();

    // Dynamic height support
    protected int getPanelHeight(int screenHeight) { return getPanelHeight(); }
    protected boolean usesDynamicHeight() { return false; }

    // Input handling
    public boolean keyPressed(int keyCode) { ... }      // ESC to close
    public boolean mouseClicked(double mouseX, double mouseY, int screenW, int screenH) { ... }
    public boolean mouseScrolled(double mouseX, double mouseY, double delta, int screenW, int screenH) { ... }
    public boolean charTyped(char chr, int modifiers) { ... }
}
```

**Overlay implementati:**
- `ConfirmDialog extends BaseOverlay` - Dialog conferma/annulla
- `HelpOverlay extends BaseOverlay` - Pannello aiuto (F1)
- `TemplateOverlay extends BaseOverlay` - Selezione template
- `CraftingInfoPanel extends BaseOverlay` - Info ricette crafting (dynamic height)

---

## Entry Point: Radial Menu Integration

> **Entry point confermato:** Radial Menu con voci separate per tipo.

```
┌─────────────────────────────────────────────────────────────────┐
│  RADIAL MENU                                                    │
│  ─────────────────────────────────────────────────────────────  │
│                                                                 │
│  Voci SEPARATE per tipo, ma STESSO screen:                      │
│                                                                 │
│  [Edit Weapon]  ──→  ItemEditorScreen(item, WEAPON)             │
│  [Edit Armor]   ──→  ItemEditorScreen(item, ARMOR)              │
│  [Edit Ranged]  ──→  ItemEditorScreen(item, RANGED)             │
│  [Edit Item]    ──→  ItemEditorScreen(item, GENERAL)            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### EditorStartTab Enum

```java
public enum EditorStartTab {
    WEAPON,   // Apre WeaponModule
    ARMOR,    // Apre ArmorModule
    RANGED,   // Apre RangedModule
    GENERAL;  // Apre GeneralModule (fallback)
}
```

---

## Feature Avanzate

### Multi-Edit System

Permette di editare più item contemporaneamente:

```java
// In ItemEditorScreen
private MultiEditManager multiEditManager;
private MultiEditPanel multiEditPanel;
private boolean showMultiEditPanel = false;
```

### Preview Mode

Modalità per vedere le modifiche senza applicarle:

```java
private boolean isPreviewMode = true;  // Default: preview attivo
private boolean isGlobalMode = false;  // Applica a tutti gli item dello stesso tipo
```

### Performance Monitoring

```java
private final PerformanceMonitor perfMonitor = new PerformanceMonitor();
```

---

## Vantaggi dell'Architettura Unificata

| Aspetto | Prima (2 editor) | Dopo (1 editor + moduli) |
|---------|------------------|--------------------------|
| **Bug layout** | Fix in 2 posti | Fix in 1 posto |
| **Nuovi tipi item** | Nuovo Screen | Nuovo Module (~100-200 righe) |
| **Testing** | 2 screen da testare | 1 screen, moduli isolati |
| **Coerenza UI** | Dipende da disciplina | Garantita da architettura |
| **Coordinate** | Sparse ovunque | Solo in EditorLayout |
| **Undo/Redo** | Duplicato | In AbstractEditorModule |
| **Overlay** | Codice duplicato | BaseOverlay template |

---

## File Structure

```
ui/editor/
├── ItemEditorScreen.java           # Main screen
├── EditorModule.java               # Module interface
├── AbstractEditorModule.java       # Base implementation
├── EditorSection.java              # Section sealed interface
├── EditorStartTab.java             # Entry point enum
├── ModuleTab.java                  # Tab definition
├── PlaceholderModule.java          # Placeholder for unimplemented types
├── PerformanceMonitor.java         # FPS/frame time tracking
├── AdvancedScroll.java             # Advanced scroll behavior
├── WeaponTypeDetector.java         # Detect weapon types
├── RangedWeaponModule.java         # Legacy ranged module (use modules/RangedModule)
│
├── core/
│   ├── BaseOverlay.java            # Overlay base class
│   ├── EditorLayout.java           # Bounds calculator
│   ├── ResponsiveLayout.java       # Responsive layout
│   ├── ScaledCoord.java            # Coordinate scaling
│   ├── UIConstants.java            # Colors, sizes, spacing (themed)
│   ├── EditorSpacing.java          # 4px grid spacing
│   ├── EditorDimensions.java       # Component dimensions
│   ├── EditorScaleCalculator.java  # Scale computation
│   ├── Typography.java             # Text rendering
│   ├── EditorSounds.java           # Audio feedback
│   ├── EditorConstants.java        # General constants
│   ├── EditorConfig.java           # Editor configuration
│   ├── EditorCache.java            # Render caching
│   ├── Bounds.java                 # Bounds record
│   ├── SectionBounds.java          # Section bounds
│   ├── SectionLayout.java          # Section layout helper
│   ├── RowLayout.java              # Row layout helper
│   ├── LayoutManager.java          # Layout management
│   ├── GridValidator.java          # 4px grid validation
│   ├── FocusRing.java              # Focus indicator rendering
│   ├── OverlayInputGuard.java      # Overlay input protection
│   │
│   │  # Theme System (Phase 6)
│   ├── Theme.java                  # Theme interface
│   ├── DarkTheme.java              # Dark theme impl
│   ├── LightTheme.java             # Light theme impl
│   ├── ThemeManager.java           # Theme singleton
│   │
│   │  # Animation System (Phase 6)
│   ├── AnimationState.java         # Animation utilities
│   │
│   │  # Focus System (Phase 6)
│   ├── FocusManager.java           # Keyboard focus management
│   │
│   │  # Architecture P3
│   ├── EditorComponent.java        # Base component interface
│   ├── InputHandler.java           # Input handling interface
│   └── ScrollState.java            # Scroll state utility
│
├── components/
│   ├── EditorButton.java           # Button component
│   ├── EditorSlider.java           # Slider component
│   ├── EditorToggle.java           # Toggle component
│   ├── EditorTextField.java        # Text input
│   ├── EditorButtonWidget.java     # Minecraft widget wrapper
│   ├── ButtonRow.java              # Horizontal button layout
│   ├── HeaderComponent.java        # Screen header
│   ├── FooterComponent.java        # Screen footer
│   ├── LeftColumnComponent.java    # Left panel
│   ├── ScrollableContentArea.java  # Scrollable content
│   ├── SlotSelector.java           # Equipment slot selector
│   ├── ModeBadge.java              # Scope/Mode badge
│   ├── ItemInfoPanel.java          # Item info display
│   ├── PreviewRenderer.java        # 3D item preview
│   ├── SectionHeader.java          # Collapsible section header
│   └── VirtualizedList.java        # Virtualized list (Phase 5)
│
├── modules/
│   ├── WeaponModule.java           # Weapon editing
│   ├── ArmorModule.java            # Armor editing
│   ├── RangedModule.java           # Ranged weapon editing
│   └── GeneralModule.java          # Generic item editing
│
├── systems/
│   ├── ConfirmDialog.java          # Confirmation overlay
│   ├── HelpOverlay.java            # Help overlay (F1)
│   ├── TemplateOverlay.java        # Template selection
│   ├── CraftingInfoPanel.java      # Crafting info overlay
│   ├── DebugPanel.java             # Debug info panel
│   ├── MultiEditPanel.java         # Multi-edit UI
│   ├── MultiEditManager.java       # Multi-edit logic
│   ├── DirtyState.java             # Change tracking
│   ├── BatchEditResult.java        # Batch edit results
│   ├── Preset.java                 # Preset data class
│   ├── DataPreset.java             # Data preset
│   ├── PresetManager.java          # Preset management
│   ├── PresetRegistry.java         # Preset registry
│   ├── PresetScope.java            # Preset scope enum
│   ├── ItemEditorPresetManager.java # Item-specific presets
│   └── UndoRedoStack.java          # Undo/redo stack
│
├── debug/
│   ├── DebugOverlay.java           # Debug overlay
│   ├── DebugInfoSection.java       # Debug info
│   ├── ItemDebugInfo.java          # Item debug details
│   └── ValueComparison.java        # Value diff comparison
│
└── favorites/
    └── FavoritePresetStore.java    # Favorite presets storage
```

---

## Migration Path (COMPLETATO)

```
FASE 1: Crea struttura base ✅
─────────────────────────────
1. ✅ Crea EditorLayout.java
2. ✅ Crea EditorModule interface
3. ✅ Crea EditorSection sealed interface
4. ✅ Crea ItemEditorScreen shell

FASE 2: Migra WeaponEditor ✅
─────────────────────────────
1. ✅ Estrai logica in WeaponModule
2. ✅ Converti sliders in SliderSection
3. ✅ Testa che funzioni identico

FASE 3: Migra ArmorEditor ✅
─────────────────────────────
1. ✅ Estrai logica in ArmorModule
2. ✅ Converti sliders in SliderSection
3. ✅ Testa che funzioni identico

FASE 4: Rimuovi vecchi editor ✅
─────────────────────────────
1. ✅ Elimina WeaponEditorScreen.java
2. ✅ Elimina ArmorEditorScreen.java
3. ✅ Aggiorna tutti i riferimenti
```

---

## Changelog

### 2025-12-17 (Sessione 3)
- Aggiornata struttura file con TUTTI i file esistenti nel codebase:
  - Aggiunti file root: PlaceholderModule, PerformanceMonitor, AdvancedScroll, WeaponTypeDetector
  - Aggiunti core/: EditorSounds, EditorConstants, EditorConfig, EditorCache, Bounds, SectionBounds, etc.
  - Aggiunti core/ Theme System: Theme, DarkTheme, LightTheme, ThemeManager
  - Aggiunti core/ Animation: AnimationState
  - Aggiunti core/ Focus: FocusManager
  - Aggiunti core/ P3: EditorComponent, InputHandler, ScrollState
  - Aggiunti components/: SlotSelector, ModeBadge, ItemInfoPanel, PreviewRenderer, SectionHeader, VirtualizedList
  - Aggiunti systems/: BatchEditResult, Preset*, UndoRedoStack
  - Aggiunti debug/: ItemDebugInfo, ValueComparison
  - Aggiunta cartella favorites/

### 2025-12-17 (Sessione 1-2)
- Aggiornata documentazione per riflettere implementazione reale
- Documentato AbstractEditorModule e sistema Undo/Redo
- Documentato sistema overlay con BaseOverlay
- Aggiunto RangedModule alla lista moduli
- Documentate feature avanzate (Multi-Edit, Preview, Performance)
- Aggiornato EditorSection con tutti i tipi reali
- Documentata struttura file completa
- Migration path segnato come completato
