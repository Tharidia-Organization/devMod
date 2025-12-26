# 23 - Architecture Comparison: Editor System vs Panel System

## Overview

DevMod utilizza **due sistemi UI paralleli** per scopi diversi. Questo documento spiega quando usare ciascuno.

---

## System Comparison

```
Editor System (ItemEditorScreen)          Panel System (VoxelLab)
─────────────────────────────────         ─────────────────────────
EditorButton/Slider/Toggle                EditorButton (shared)
        ↓                                         ↓
SectionAdapter                            ButtonRow
        ↓                                         ↓
EditorSection (sealed)                    SectionPanel/SliderPanel
        ↓                                         ↓
ModuleTab                                 PanelContainer
        ↓                                         ↓
AbstractEditorModule                      AbstractVoxelLabPage
        ↓                                         ↓
ItemEditorScreen                          VoxelLabScreen
```

---

## Editor System

### Scopo
Editing complesso di proprietà item con persistenza, undo/redo, e sincronizzazione server.

### Caratteristiche
| Feature | Descrizione |
|---------|-------------|
| **Undo/Redo** | Stack fino a 50 stati per modulo |
| **Dirty Tracking** | Rileva modifiche non salvate |
| **Payload Building** | Genera network payloads per sync server |
| **Source Badges** | Mostra origine dati (DEV/NBT/VANILLA) |
| **Variant System** | Supporto varianti (STANDARD/MACE/TRIDENT) |
| **Multi-Edit** | Modifica batch di item multipli |

### Componenti Chiave

#### EditorSection (sealed interface)
```java
public sealed interface EditorSection permits
    SliderSection,    // Numeric values
    ToggleSection,    // Boolean values
    InputSection,     // Text input
    ListSection,      // Selection lists
    HeaderSection,    // Section headers
    SpacerSection,    // Layout spacing
    CustomSection     // Complex custom content
```

#### AbstractEditorModule
```java
public abstract class AbstractEditorModule implements EditorModule {
    // Tab management
    protected List<ModuleTab> tabs;
    protected int activeTabIndex;

    // State management
    protected UndoRedoStack undoStack;
    protected DirtyState dirtyState;

    // Lifecycle
    public abstract void init(ResponsiveLayout layout);
    public abstract void tick();
    public abstract FriendlyByteBuf buildPayload();
}
```

### Quando Usare
- Editing proprietà item (danni, armatura, durabilità)
- Serve undo/redo
- Serve sync con server
- Serve tracking origine dati
- Editing complesso multi-tab

### File Principali
```
ui/editor/
├── ItemEditorScreen.java          # Screen principale
├── AbstractEditorModule.java      # Base class moduli
├── EditorModule.java              # Interface
├── EditorSection.java             # Sealed interface sections
├── modules/
│   ├── WeaponModule.java          # ⭐⭐⭐⭐⭐ Reference
│   ├── ArmorModule.java           # ⭐⭐⭐⭐⭐ Reference
│   ├── RangedModule.java          # ⭐⭐⭐⭐
│   ├── RecipeModule.java          # ⭐⭐⭐⭐
│   └── GeneralModule.java         # ⭐⭐ → Navigation Hub
├── sections/
│   ├── SliderSectionAdapter.java
│   ├── ToggleSectionAdapter.java
│   └── ...
└── components/
    ├── EditorSlider.java
    ├── EditorToggle.java
    └── EditorTextField.java
```

---

## Panel System

### Scopo
Settings screens, dashboard views, config toggles. UI più semplice senza persistenza complessa.

### Caratteristiche
| Feature | Descrizione |
|---------|-------------|
| **Composizione** | Panels contengono panels |
| **Builder Pattern** | API fluent per costruzione |
| **Factory Methods** | `SliderPanel.of()`, `StatusPanel.builder()` |
| **Template Method** | AbstractVoxelLabPage.buildPanels() |
| **Config Binding** | Binding diretto a ModConfigSpec |

### Componenti Chiave

#### UIPanel (sealed interface)
```java
public sealed interface UIPanel permits
    HeaderPanel,      // Titoli sezione
    SectionPanel,     // Gruppi di controlli
    CollapsiblePanel, // Sezioni espandibili
    StatusPanel,      // Indicatori stato
    SliderPanel,      // Slider wrapper
    GridPanel,        // Layout griglia
    SpacerPanel,      // Spacing
    CompositePanel    // Container nested
```

#### AbstractVoxelLabPage
```java
public abstract class AbstractVoxelLabPage implements VoxelLabPage {
    protected PanelContainer panelContainer;

    // Template method
    protected abstract void buildPanels();

    // Lifecycle
    public void init(int x, int y, int w, int h);
    public void tick();
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta);
}
```

### Quando Usare
- Settings screens (VoxelLab)
- Dashboard/overview views
- Config toggles semplici
- Non serve undo/redo
- Non serve sync server
- UI read-mostly

### File Principali
```
ui/testing/
├── VoxelLabScreen.java            # Screen principale
├── VoxelLabPage.java              # Interface pagine
├── AbstractVoxelLabPage.java      # Base class
├── VoxelLabTab.java               # Enum tab
├── pages/
│   ├── OverviewPage.java
│   ├── DebugOverlaysPage.java
│   ├── HudSystemsPage.java
│   ├── TelemetryPage.java
│   ├── EffectsPage.java
│   ├── CombatPage.java
│   └── ComponentShowcasePage.java
└── panel/
    ├── UIPanel.java               # Sealed interface
    ├── PanelContainer.java        # Container scrollabile
    ├── HeaderPanel.java
    ├── SectionPanel.java
    ├── SliderPanel.java
    └── ...
```

---

## Decision Matrix

| Requisito | Editor System | Panel System |
|-----------|:-------------:|:------------:|
| Undo/Redo | ✅ | ❌ |
| Server Sync | ✅ | ❌ |
| Source Badges | ✅ | ❌ |
| Config Binding | ❌ | ✅ |
| Multi-Tab Complex | ✅ | ⚠️ |
| Simple Toggles | ⚠️ | ✅ |
| Dashboard View | ❌ | ✅ |
| Payload Building | ✅ | ❌ |

**Legenda:** ✅ = Ottimo | ⚠️ = Possibile ma non ideale | ❌ = Non supportato

---

## Integration Points

### Componenti Condivisi
Entrambi i sistemi usano:
- `EditorButton` - Bottoni con stili, toggle, icons
- `UIConstants` - Colori e spacing
- `ResponsiveLayout.Rect` - Bounds management

### Come CombatPage Integra Entrambi
```java
// CombatPage usa Panel System per layout
public class CombatPage extends AbstractVoxelLabPage {

    // Ma crea EditorButton (componente Editor System)
    private EditorButton bodyPartToggle;

    @Override
    protected void buildPanels() {
        // Wrap EditorButton in SectionPanel
        panelContainer.addPanel(
            SectionPanel.builder("section-bodypart", "Body Part Detection")
                .addButton(bodyPartToggle)  // EditorButton in Panel
                .build()
        );
    }
}
```

---

## Migration Patterns

### Da Editor a Panel (Semplificazione)
Se un modulo non ha bisogno di undo/redo/payload:
1. Convertire `EditorSection` → `UIPanel`
2. Sostituire `AbstractEditorModule` → `AbstractVoxelLabPage`
3. Rimuovere dirty tracking
4. Bind diretto a Config

### Da Panel a Editor (Complessificazione)
Se serve persistenza/undo:
1. Creare nuovo modulo che estende `AbstractEditorModule`
2. Convertire panels in sections
3. Implementare `buildPayload()`
4. Aggiungere dirty tracking

---

## Best Practices

### Editor System
1. **Sempre implementare `buildPayload()`** - Mai ritornare null
2. **Usare source badges** per mostrare origine dati
3. **Implementare variant detection** se item ha varianti
4. **Aggiungere Debug tab** per sviluppo

### Panel System
1. **Usare `AbstractVoxelLabPage`** - Non implementare VoxelLabPage direttamente
2. **Estrarre utility methods** - `safeGetBool()` in classe condivisa
3. **Preferire builder pattern** - `SectionPanel.builder()`
4. **Usare CollapsiblePanel** per sezioni opzionali

---

## Related Documents
- [00-overview.md](00-overview.md) - Overview generale
- [24-component-library.md](24-component-library.md) - Componenti UI
- [25-panel-system.md](25-panel-system.md) - Panel system dettagli
- [26-module-evolution-guide.md](26-module-evolution-guide.md) - Maturity levels
- [27-general-module-hub.md](27-general-module-hub.md) - GeneralModule redesign
