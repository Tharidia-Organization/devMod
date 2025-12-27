# Shared Components

> Last updated: 2025-12-26
> Status: HISTORICAL (design system snapshot)

Questo documento descrive i componenti condivisi tra ArmorEditor e WeaponEditor.

> **Nota**: Tutti i componenti devono seguire le convenzioni del [Component System](01-layout-specifications.md#component-system) e usare `UIConstants`, `EditorSpacing`, e `ScaledCoord.scaleDim()` per consistenza.

## Slot Selector System

> **File**: `components/SlotSelector.java`

Il Slot Selector gestisce la selezione degli slot equipment. Una singola classe gestisce sia armor che weapon tramite `SlotType` enum.

### Layout Visivo

```
┌─────────────────────────────────────────┐
│            ARMOR (SlotType.ARMOR)        │
├─────────┬─────────┬─────────┬───────────┤
│   🪖    │   🦺    │   👖    │    👢     │
│  HEAD   │  CHEST  │  LEGS   │   FEET    │
└─────────┴─────────┴─────────┴───────────┘
   30px      30px      30px       30px

┌─────────────────────────────┐
│    WEAPON (SlotType.WEAPON)  │
├──────────────┬──────────────┤
│      ⚔       │      🛡      │
│  MAIN HAND   │   OFF HAND   │
└──────────────┴──────────────┘
      30px           30px
```

### Specifiche

| Proprietà | Valore |
|-----------|--------|
| Slot Size | 32x32px (`EditorDimensions.SLOT_SIZE`) - allineato a griglia 4px |
| Slot Gap | 5px |
| Height totale | 70px |
| Border Active | `UIConstants.Border.ACCENT` (Cyan) |
| Border Hover | `UIConstants.Border.HOVER` |
| Border Normal | `UIConstants.Border.DEFAULT` |
| BG Active | `UIConstants.Background.ACTIVE` |
| BG Hover | `UIConstants.Background.HOVER` |

### Placeholder Emoji

| Slot | Emoji |
|------|-------|
| HEAD | 🪖 |
| CHEST | 🦺 |
| LEGS | 👖 |
| FEET | 👢 |
| MAINHAND | ⚔ |
| OFFHAND | 🛡 |

### Implementazione

```java
public final class SlotSelector {

    public enum SlotType { ARMOR, WEAPON }

    public record SlotInfo(
        EquipmentSlot slot,
        String label,
        String shortLabel,
        ItemStack item
    ) {}

    private SlotType type = SlotType.WEAPON;
    private final List<SlotInfo> slots = new ArrayList<>();
    private int selectedIndex = 0;
    private int hoveredIndex = -1;  // Per tooltip

    // Cambia tipo (riconfigura slots)
    public void setType(SlotType type);

    // Seleziona slot programmaticamente
    public void selectSlot(EquipmentSlot slot);

    // Callback quando slot viene selezionato
    public SlotSelector onSelect(Consumer<SlotInfo> callback);

    // Input handling
    public boolean mouseClicked(double mouseX, double mouseY, int button);
    public boolean keyPressed(int keyCode, int scanCode, int modifiers);

    // Getters per tooltip
    public int getHoveredIndex();
    public SlotInfo getHoveredSlot();
}
```

### Keyboard Navigation

| Tasto | Azione |
|-------|--------|
| **←** (LEFT) | Slot precedente |
| **→** (RIGHT) | Slot successivo |

### Interazione

- **Click**: Seleziona slot, trigger `onSelect` callback
- **Hover**: Evidenzia slot, aggiorna `hoveredIndex` per tooltip
- **Arrow Keys**: Naviga tra slots con feedback sonoro

## Mode Badge

> **File**: `components/ModeBadge.java`

ModeBadge supporta due tipi di badge tramite `BadgeType` enum: **SCOPE** (GLOBAL/SPECIFIC) e **MODE** (PREVIEW/APPLY).

### Layout Visivo

```
SCOPE Badge:
┌─────────────────────┐
│  ●GLOBAL        ▼   │  ← Arancione
└─────────────────────┘
┌─────────────────────┐
│  ●SPECIFIC      ▼   │  ← Verde
└─────────────────────┘

MODE Badge:
┌─────────────────────┐
│  👁 PREVIEW     ▼   │  ← Giallo
└─────────────────────┘
┌─────────────────────┐
│  ⚡ APPLY       ▼   │  ← Verde
└─────────────────────┘
```

### Specifiche Dimensioni

| Proprietà | Valore |
|-----------|--------|
| Width | 100px |
| Height | 20px |
| Position | Top-right, 10px da bordo |

### Colori per Tipo

| Badge | Variante | Border | Background |
|-------|----------|--------|------------|
| SCOPE | GLOBAL | `UIConstants.Mode.GLOBAL_BORDER` (0xFFFF9800) | `UIConstants.Mode.GLOBAL_BG` |
| SCOPE | SPECIFIC | `UIConstants.Mode.SPECIFIC_BORDER` (0xFF4CAF50) | `UIConstants.Mode.SPECIFIC_BG` |
| MODE | PREVIEW | `UIConstants.Mode.PREVIEW_BORDER` (0xFFFFEB3B) | `UIConstants.Mode.PREVIEW_BG` |
| MODE | APPLY | `UIConstants.Mode.APPLY_BORDER` (0xFF4CAF50) | `UIConstants.Mode.APPLY_BG` |

### Implementazione

```java
public class ModeBadge {

    public enum BadgeType { SCOPE, MODE }

    public enum Scope {
        GLOBAL("●GLOBAL", "⬤", GLOBAL_BORDER, GLOBAL_BG),
        SPECIFIC("●SPECIFIC", "◉", SPECIFIC_BORDER, SPECIFIC_BG);
    }

    public enum Mode {
        PREVIEW("PREVIEW", "👁", PREVIEW_BORDER, PREVIEW_BG),
        APPLY("APPLY", "⚡", APPLY_BORDER, APPLY_BG);
    }

    private BadgeType badgeType = BadgeType.SCOPE;
    private Scope scope = Scope.GLOBAL;
    private Mode mode = Mode.PREVIEW;
    private boolean showDropdown = false;

    // Fluent API
    public ModeBadge badgeType(BadgeType type);
    public ModeBadge scope(Scope scope);
    public ModeBadge mode(Mode mode);
    public ModeBadge clickable(boolean clickable);
    public ModeBadge onScopeChange(Consumer<Scope> callback);
    public ModeBadge onModeChange(Consumer<Mode> callback);
    public ModeBadge onSetDefaultMode(Runnable callback);

    // Rendering (seconda pass per dropdown overlay)
    public int render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY);
    public void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY);

    // Toggle per F5 shortcut
    public void toggle();

    // Utility
    public boolean isPreviewMode();
    public boolean isApplyMode();
    public boolean isGlobalScope();
    public boolean isSpecificScope();
    public String getTooltipText();  // Ritorna tooltip se hovered
}
```

### Dropdown

Cliccando sul badge si apre un dropdown con:
- Opzioni disponibili (GLOBAL/SPECIFIC o PREVIEW/APPLY)
- Indicatore selezione corrente (barra laterale colorata)
- **"Set current as default"** (solo per MODE badge, se `onSetDefaultMode` è configurato)

### Tooltip

| Badge | Variante | Tooltip |
|-------|----------|---------|
| SCOPE | GLOBAL | "Changes will apply to ALL items of this type" |
| SCOPE | SPECIFIC | "Changes will apply only to THIS specific item" |
| MODE | PREVIEW | "Preview: local-only changes (no server sync)" |
| MODE | APPLY | "Apply: changes will be persisted and sent to server" |

## Dirty State System

**CRITICO PER I TESTER** - Il dirty state è gestito da `ItemInfoPanel` per la visualizzazione.

> **File**: `components/ItemInfoPanel.java` (visualizzazione)

### Stato in ItemInfoPanel

```java
private int pendingChanges = 0;        // Contatore modifiche
private long lastSaveTimestamp = 0;    // Timestamp ultimo salvataggio
```

### API

```java
// Imposta numero modifiche pendenti
public ItemInfoPanel pendingChanges(int count);

// Imposta timestamp ultimo salvataggio
public ItemInfoPanel lastSaved(long timestamp);

// Verifica se ci sono modifiche
public boolean isDirty();

// Marca come salvato (reset pending, update timestamp)
public void markSaved();

// Reset completo stato dirty
public void resetDirtyState();
```

### Trigger per Incrementare pendingChanges

L'editor deve incrementare `pendingChanges` quando:
- Slider value cambia
- Toggle viene switchato
- Enchantment aggiunto/rimosso/modificato
- Attribute aggiunto/rimosso/modificato
- Qualsiasi valore numerico cambia

### UI Indicator

```
Posizione: Bottom dell'ItemInfoPanel, centrato
Formato quando dirty:    "● 3 unsaved changes"  (colore: ORANGE)
Formato quando saved:    "✓ Saved just now"     (colore: GREEN)
                         "✓ Saved 2m ago"
                         "✓ Saved 1h ago"
                         "✓ Saved yesterday"
Formato quando fresh:    (nessun indicatore)
```

### Formattazione Tempo

| Intervallo | Testo (ItemInfoPanel) | Testo (DirtyState) |
|------------|----------------------|-------------------|
| < 60 secondi | "just now" | "just now" |
| < 60 minuti | "Xm ago" | "X min ago" |
| < 24 ore | "Xh ago" | "over 1h ago" |
| ≥ 24 ore | "yesterday" | "over 1h ago" |

> **Nota**: `ItemInfoPanel` e `DirtyState` hanno formattazioni leggermente diverse. `ItemInfoPanel` usa formato compatto per spazio limitato.

## Confirmation Dialog

Dialog modale per azioni distruttive. Identico in entrambi gli editor.

> **Nota implementazione**: ConfirmDialog usa il sistema di componenti EditorButton per i bottoni, garantendo consistenza visiva con il resto dell'UI.

### Trigger
1. **Close con modifiche pending** → "Discard unsaved changes?"
2. **Switch slot con modifiche pending** → "Discard changes to [slot_name]?"
3. **Reset** → "Reset all values to default?"
4. **Load preset** → "This will overwrite current values. Continue?"
5. **Delete preset** → "Delete preset '[name]'? This cannot be undone."

### Layout
```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│                     ⚠️  UNSAVED CHANGES                         │
│                                                                 │
│     You have 3 unsaved changes that will be lost.              │
│     Are you sure you want to close?                            │
│                                                                 │
│                                                                 │
│            [ Discard ]              [ Cancel ]                  │
│         EditorButton               EditorButton                 │
│         Style.DANGER               Style.NORMAL                 │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

Dimensioni: 320px × 140px, centrato nello schermo
Background: UIConstants.Background.PANEL_SOLID con overlay scuro (0x80000000)
```

### Factory Methods

ConfirmDialog fornisce factory methods per scenari comuni:

```java
// Modifiche non salvate
ConfirmDialog.unsavedChanges(int changeCount, Runnable onDiscard, Runnable onCancel)

// Reset ai valori default
ConfirmDialog.resetToDefault(Runnable onReset, Runnable onCancel)

// Switch slot con modifiche pendenti
ConfirmDialog.switchSlot(String slotName, int changeCount, Runnable onSwitch, Runnable onCancel)

// Eliminazione preset
ConfirmDialog.deletePreset(String presetName, Runnable onDelete, Runnable onCancel)

// Switch da Apply a Preview con modifiche
ConfirmDialog.switchModeToPreview(int changeCount, Runnable onDiscard, Runnable onCancel)
```

### Implementazione Bottoni

```java
// ConfirmDialog usa EditorButton per i bottoni
private final EditorButton confirmButton;
private final EditorButton cancelButton;

public ConfirmDialog(...) {
    // Stile dinamico basato sul tipo di azione
    EditorButton.Style confirmStyle = EditorButton.Style.PRIMARY;
    if (confirmColor == UIConstants.Accent.RED) {
        confirmStyle = EditorButton.Style.DANGER;
    } else if (confirmColor == UIConstants.Accent.ORANGE) {
        confirmStyle = EditorButton.Style.DANGER;
    } else if (confirmColor == UIConstants.Accent.GREEN) {
        confirmStyle = EditorButton.Style.SUCCESS;
    }

    this.confirmButton = new EditorButton("confirm", confirmText)
        .style(confirmStyle)
        .size(EditorButton.Size.MEDIUM)
        .onClick(() -> { hide(); onConfirm.run(); });

    this.cancelButton = new EditorButton("cancel", cancelText)
        .style(EditorButton.Style.NORMAL)
        .size(EditorButton.Size.MEDIUM)
        .onClick(() -> { hide(); onCancel.run(); });
}
```

### Keyboard Shortcuts

| Tasto | Azione |
|-------|--------|
| **ESC** | Cancel/Close |
| **Enter** | Confirm |

### Candidato per BaseOverlay

ConfirmDialog è un candidato ideale per estendere `BaseOverlay` (vedi Fase 4 in TODO.md), che fornisce:
- Rendering automatico del backdrop scuro
- Centratura del pannello
- Gestione ESC per chiusura
- Click fuori dal pannello per chiudere

## Item Info Panel

> **File**: `components/ItemInfoPanel.java`

Mostra informazioni sull'item correntemente in editing con sistema di stats flessibile.

### Layout Visivo

```
┌────────────────────────────┐
│  Diamond Sword             │  ← Item name (truncated se lungo)
├────────────────────────────┤
│  Attack:           +7.0    │  ← Stats con colorazione automatica
│  Speed:            +1.6    │     Verde = positivo
│  Durability:        85%    │     Rosso = negativo
│                            │
│  ● 3 unsaved changes       │  ← Dirty indicator (bottom)
└────────────────────────────┘
        Height: 100px
```

### Specifiche

| Proprietà | Valore |
|-----------|--------|
| Height | 100px (`UIConstants.PanelDimensions.INFO_PANEL_HEIGHT`) |
| Padding | 8px |
| Line Height | 12px |
| Background | `UIConstants.Background.INPUT` |
| Border | `UIConstants.Border.DEFAULT` |

### Implementazione

```java
public class ItemInfoPanel {

    public record StatLine(String label, String value, int valueColor) {
        public StatLine(String label, String value) {
            this(label, value, UIConstants.Text.VALUE);
        }
    }

    private ItemStack item = ItemStack.EMPTY;
    private String itemName = "";
    private final List<StatLine> stats = new ArrayList<>();
    private int pendingChanges = 0;
    private long lastSaveTimestamp = 0;

    // Fluent API
    public ItemInfoPanel item(ItemStack item);
    public ItemInfoPanel itemName(String name);
    public ItemInfoPanel clearStats();
    public ItemInfoPanel addStat(String label, String value);
    public ItemInfoPanel addStat(String label, String value, int valueColor);
    public ItemInfoPanel addStat(String label, float value, String format);  // Colorazione auto
    public ItemInfoPanel pendingChanges(int count);
    public ItemInfoPanel lastSaved(long timestamp);

    // Rendering
    public int render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY);
}
```

### Colorazione Automatica Valori

Usando `addStat(String, float, String)`:
- **Valore > 0**: Verde (`UIConstants.Accent.GREEN`) con prefisso "+"
- **Valore < 0**: Rosso (`UIConstants.Accent.RED`)
- **Valore = 0**: Neutro (`UIConstants.Text.VALUE`)

### Esempio Uso

```java
itemInfoPanel
    .item(currentItem)
    .clearStats()
    .addStat("Attack", attackDamage, "%.1f")    // +7.0 (verde)
    .addStat("Speed", attackSpeed, "%.1f")      // +1.6 (verde)
    .addStat("Durability", durability + "%")
    .addStat("Enchants", String.valueOf(enchantCount))
    .pendingChanges(dirtyCount);
```

### Text Truncation

Nomi item troppo lunghi vengono troncati con "..." per stare nella larghezza disponibile.

## Dual-Mode System (PREVIEW / APPLY)

### Concetto

```
┌─────────────────────────────────────────────────────────────────┐
│  ● GLOBAL    [👁 PREVIEW MODE]                            [X]   │
│              ─────────────────                                  │
│              Modifiche visibili solo in questo client.          │
│              Nessun dato inviato al server.                     │
│              Reset automatico alla chiusura.                    │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  ● SPECIFIC  [⚡ APPLY MODE]                              [X]   │
│              ──────────────                                     │
│              Modifiche inviate al server.                       │
│              Persistono dopo chiusura.                          │
│              Richiedono conferma per applicare.                 │
└─────────────────────────────────────────────────────────────────┘
```

### Modalità

| Modalità | Icona | Descrizione | Comportamento |
|----------|-------|-------------|---------------|
| **PREVIEW** | 👁 | Solo visualizzazione | Client-only, nessun packet, reset on close |
| **APPLY** | ⚡ | Applica modifiche | Invia al server, persiste, richiede conferma |

### Layout Mode Toggle

```
Posizione: Accanto al Mode Badge (GLOBAL/SPECIFIC)
Dimensioni: 120px × 20px

┌──────────────────────────────────────────────────┐
│  ● GLOBAL   [👁 PREVIEW ▼]              [X]      │
│              ↑                                    │
│              Click per toggle dropdown            │
└──────────────────────────────────────────────────┘

Dropdown:
┌─────────────────┐
│ 👁 PREVIEW      │  ← Attualmente selezionato
├─────────────────┤
│ ⚡ APPLY        │
└─────────────────┘
```

### Specifiche Visive

| Modalità | Background Badge | Border | Text Color |
|----------|-----------------|--------|------------|
| PREVIEW | `0x40FFFF00` (Giallo trasparente) | `UIConstants.Accent.YELLOW` | `0xFFFFFF00` |
| APPLY | `0x4000FF00` (Verde trasparente) | `UIConstants.Accent.GREEN` | `0xFF00FF00` |

### Keyboard Shortcuts

| Shortcut | Azione | Note |
|----------|--------|------|
| **F5** | Toggle PREVIEW ↔ APPLY mode | Funziona sempre |
| **Ctrl+Enter** | Quick Apply | Solo in APPLY mode con dirty |
| **Ctrl+S** | Apply changes | Alternativa a Ctrl+Enter |
| **Ctrl+Z** | Undo | Annulla ultima modifica |
| **Ctrl+Y** | Redo | Ripristina modifica annullata |
| **Ctrl+Shift+Z** | Redo | Alternativa a Ctrl+Y |

### Gestione Errori Shortcut

```
Ctrl+Enter in PREVIEW mode → Status: "Preview mode: cannot apply" (Orange)
Ctrl+Enter senza modifiche → Status: "No changes to apply" (Orange)
```

---

## Componenti Non Documentati (Esistenti nel Codice)

Questi componenti esistono nell'implementazione ma non erano precedentemente documentati:

### FooterComponent

> **File**: `components/FooterComponent.java`

Footer dell'editor con:
- **Undo/Redo** buttons con contatore history
- **Actions row** scrollabile (History, Export, Import, Presets, Templates, Recipe, Reset, Cancel)
- **Apply button** con indicatore dirty e contatore modifiche
- Scroll orizzontale con frecce quando le actions non entrano

### HeaderComponent

> **File**: `components/HeaderComponent.java`

Header dell'editor con:
- **Tab bar** per navigazione sezioni
- **Mode Badge** (Scope + Mode)
- **Close button**
- Gestione keyboard shortcuts (F5 toggle)

### Typography

> **File**: `core/Typography.java`

Utility per text rendering con scaling:
- `buttonScale()` - Scale per testo bottoni
- `valueScale()` - Scale per valori
- `sectionHeaderScale()` - Scale per titoli sezione
- `drawText(graphics, font, text, x, y, color, scale)`

### EditorSounds

> **File**: `core/EditorSounds.java`

Feedback sonoro per interazioni:
- `playButtonClick()` - Click su bottone
- `playSlotSelect()` - Selezione slot
- `playTabSwitch()` - Cambio tab/mode
- `playSuccess()` - Operazione completata
- `playError()` - Errore

---

## Component System Reference

Tutti i componenti condivisi devono seguire le convenzioni del sistema UI.

### Componenti Utilizzati

| Componente | File | Uso in Shared Components |
|------------|------|--------------------------|
| `EditorButton` | `components/EditorButton.java` | ConfirmDialog, tutti i dialogs modali |
| `BaseOverlay` | `core/BaseOverlay.java` | Template per dialogs modali |
| `ButtonRow` | `components/ButtonRow.java` | Layout bottoni in dialogs |

### Pattern per Dialogs Modali

Tutti i dialog modali (ConfirmDialog, TemplateOverlay, etc.) dovrebbero:

1. **Usare EditorButton** per tutti i bottoni
2. **Estendere BaseOverlay** per funzionalità comuni
3. **Usare UIConstants** per tutti i colori
4. **Usare EditorSpacing** per padding/margin

```java
// Pattern consigliato per un dialog modale
public class MyDialog extends BaseOverlay {

    private final EditorButton confirmButton;
    private final EditorButton cancelButton;
    private final ButtonRow buttonRow;

    public MyDialog() {
        this.confirmButton = new EditorButton("confirm", "OK")
            .style(EditorButton.Style.PRIMARY)
            .onClick(this::onConfirm);

        this.cancelButton = new EditorButton("cancel", "Cancel")
            .style(EditorButton.Style.NORMAL)
            .onClick(this::hide);

        this.buttonRow = new ButtonRow()
            .add(cancelButton)
            .add(confirmButton)
            .gap(EditorSpacing.S)
            .alignment(ButtonRow.Alignment.RIGHT);
    }

    @Override
    protected int getPanelWidth() { return 350; }

    @Override
    protected int getPanelHeight() { return 150; }

    @Override
    protected void renderContent(GuiGraphics g, Font font,
                                 int x, int y, int w, int h,
                                 int mouseX, int mouseY) {
        // Render titolo, messaggio, etc.
        // ...

        // Render bottoni con ButtonRow
        int buttonY = y + h - EditorSpacing.L - 22;
        buttonRow.render(g, x + EditorSpacing.M, buttonY,
                        w - EditorSpacing.M * 2, mouseX, mouseY);
    }
}
```

### Stili Bottoni per Azioni

| Tipo Azione | Stile | Colore |
|-------------|-------|--------|
| Conferma positiva | `Style.SUCCESS` | Verde |
| Azione primaria | `Style.PRIMARY` | Teal |
| Azione distruttiva | `Style.DANGER` | Rosso |
| Azione secondaria | `Style.NORMAL` | Grigio |
| Azione minore/link | `Style.GHOST` | Trasparente |

### Riferimenti

- Vedi [01-layout-specifications.md](01-layout-specifications.md#component-system) per la gerarchia completa dei componenti
- Vedi [TODO.md](TODO.md) per lo stato del refactoring
