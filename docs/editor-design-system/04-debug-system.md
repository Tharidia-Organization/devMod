# Debug System

## Debug Panel (PRIORITÀ MASSIMA)

> **NOTA:** Questa è la feature più importante per la fase attuale di sviluppo.
> Deve essere implementata PRIMA di altre feature "nice-to-have".

### Obiettivo
Fornire informazioni di debug immediate per diagnosticare problemi con item/armor/weapon.

### Layout
```
┌─────────────────────────────────────────────────────────────────┐
│  DEBUG INFO                                            [Copy]   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ITEM DATA                                                      │
│  ─────────────────────────────────────────────────────────────  │
│  Registry: minecraft:diamond_sword                              │
│  Stack Size: 1                                                  │
│  Damage: 0/1561                                                 │
│  NBT Tags: 3                                                    │
│                                                                 │
│  VALUE COMPARISONS                                              │
│  ─────────────────────────────────────────────────────────────  │
│  attack_damage:     7.0  →  12.0  [MISMATCH]                    │
│  attack_speed:      1.6  →  1.6                                 │
│  durability:        1561 →  2000  [MODIFIED]                    │
│                                                                 │
│  RECENT CHANGES (this session)                                  │
│  ─────────────────────────────────────────────────────────────  │
│  14:32:05  Set attack_damage = 12.0                             │
│  14:32:08  Applied to server                                    │
│  14:32:08  Server confirmed                                     │
│                                                                 │
│  NBT VIEWER                                                     │
│  ─────────────────────────────────────────────────────────────  │
│  {                                                              │
│    Damage: 0,                                                   │
│    Enchantments: [...],                                         │
│    WeaponModStats: {...}                                        │
│  }                                                              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Definition of Done (DEBUG tab)
- Item identification: registry, stack size, damage/maxDamage, NBT tag count, flag `hasCustomData`.
- Value comparisons: original → current, con badge `[MODIFIED]` o `[MISMATCH]` (mismatch se `serverValue` differisce). Colori: giallo per modified, rosso per mismatch.
- Session log: cronologico, include set/apply/server confirm/error, mostra almeno 1 entry o placeholder “(no entries)”.
- NBT viewer: dump leggibile del `CustomData` (`WeaponModStats` / `ArmorModStats` inclusi) con indentazione base.
- Copy-to-clipboard: bottone “Copy Debug” esporta header + values + session log + NBT.

### Shortcut & Overlay (P0)
| Tasto | Azione |
|-------|--------|
| `F9` | Toggle Debug Overlay |
| `F10` | Mostra griglia nel Debug Overlay |
| `F11` | Mostra bounds/performance nel Debug Overlay |
| `F3 + D` | Toggle Dev Panel (debug tab) |
| `F1` | Mostra/Nasconde Help overlay |
| `Ctrl+Z / Ctrl+Shift+Z` | Undo / Redo |
| `Ctrl+S` | Apply |
| `Ctrl+Enter` | Quick Apply (solo APPLY mode) |
| `F5` | Toggle Preview/Apply |
| `Ctrl+E / Ctrl+I` | Export / Import |
| `Ctrl+P / Ctrl+F / Delete` | Preset overlay: apri / focus search / elimina preset hover |

### Implementazione Concreta

L'implementazione del debug panel è realizzata principalmente attraverso la classe `DebugInfoSection.java`, che agisce come una `EditorSection` custom all'interno dell'editor. Questa sezione riceve tutti i dati necessari e si occupa del rendering completo del pannello come specificato.

Un'altra classe, `DebugPanel.java`, esiste ma serve come un overlay di debug più generico e leggero, utilizzato in altre parti dell'interfaccia, e non contiene tutte le feature qui descritte.

#### Struttura di `DebugInfoSection.java`

La classe è una `EditorSection.CustomSection` che riceve i dati di debug nel suo costruttore e li renderizza.

```java
// src/main/java/com/frenkvs/devmod/ui/editor/debug/DebugInfoSection.java

public final class DebugInfoSection implements EditorSection.CustomSection {

    private final ItemDebugInfo debugInfo;
    private final List<ValueComparison> comparisons;
    private final List<String> changeLog;
    private final List<String> nbtLines;
    private final Runnable onCopy;

    public DebugInfoSection(...) {
        // ...
    }

    @Override
    public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
        // ...
        contentY = renderComparisonBlock(graphics, font, indentX, contentY);
        // ...
        contentY = renderHistoryBlock(graphics, font, indentX, contentY);
        // ...
        contentY = renderNbtBlock(graphics, font, indentX, contentY);
        // ...
    }
}
```

#### Value Comparisons

Il confronto dei valori è implementato nel metodo `renderComparisonBlock`, che formatta e colora le linee in base allo stato (`isModified`, `hasMismatch`).

```java
// src/main/java/com/frenkvs/devmod/ui/editor/debug/DebugInfoSection.java

private int renderComparisonBlock(GuiGraphics graphics, Font font, int x, int y) {
    // ...
    for (ValueComparison comp : comparisons) {
        int color = comp.hasMismatch() ? UIConstants.Accent.RED :
                    comp.isModified() ? UIConstants.Accent.YELLOW :
                    // ...
        String line = String.format("%-18s orig:%7s srv:%7s cur:%7s%s",
            comp.attributeName(), orig, srv, cur, suffix);
        graphics.drawString(safeFont, line, x, y, color, false);
        y += LINE_HEIGHT;
    }
    return y;
}
```

#### NBT Viewer

Il viewer NBT è gestito da `renderNbtBlock` e da un metodo statico `formatNbtLines` che formatta ricorsivamente il `CompoundTag` per la visualizzazione.

```java
// src/main/java/com/frenkvs/devmod/ui/editor/debug/DebugInfoSection.java

public static List<String> formatNbtLines(CompoundTag tag, int maxLines) {
    // ...
    formatTag(lines, tag, "", maxLines, count);
    return lines;
}

private static void formatTag(List<String> lines, CompoundTag tag, String indent, int maxLines, int[] count) {
    for (String key : tag.getAllKeys()) {
        // ...
        if (value instanceof CompoundTag nested) {
            lines.add(prefix + "{");
            formatTag(lines, nested, safeIndent + "  ", maxLines, count);
            lines.add(safeIndent + "}");
        } else {
            lines.add(prefix + value);
        }
    }
}
```

#### Copy to Clipboard

La funzione di copia è gestita tramite una `Runnable` passata al costruttore, che viene invocata quando il bottone "Copy Debug" viene premuto.

```java
// src/main/java/com/frenkvs/devmod/ui/editor/debug/DebugInfoSection.java

@Override
public boolean mouseClicked(double mouseX, double mouseY, int button) {
    if (button != 0) return false;
    if (copyButtonBounds.contains(mouseX, mouseY) && onCopy != null) {
        onCopy.run();
        return true;
    }
    return false;
}
```

### Posizione
- **Tab dedicata "DEBUG"** - Ultima tab in entrambi gli editor
- Oppure **Panel collapsabile** nella left column

### Dati da Mostrare

#### 1. Item Identification
```java
record ItemDebugInfo(
    String registryName,      // "minecraft:diamond_sword"
    int stackSize,            // 1
    int currentDamage,        // 0
    int maxDamage,            // 1561
    int nbtTagCount,          // Numero di tag NBT
    boolean hasCustomData     // true se ha dati DevMod
) {}
```

#### 2. Value Comparison (Expected vs Actual)
```java
record ValueComparison(
    String attributeName,
    double originalValue,     // Valore vanilla o da ultima load
    double currentValue,      // Valore attuale nell'editor
    double serverValue,       // Valore salvato sul server (da config)
    boolean isModified,       // currentValue != originalValue
    boolean hasMismatch       // serverValue != currentValue (BUG!)
) {}

private void renderValueComparison(GuiGraphics graphics, int x, int y, ValueComparison comp) {
    int color = comp.hasMismatch() ? UIConstants.Accent.RED :
                comp.isModified() ? UIConstants.Accent.YELLOW :
                UIConstants.Text.SECONDARY;

    String line = String.format("%-20s %8.1f → %8.1f",
        comp.attributeName(), comp.originalValue(), comp.currentValue());
    graphics.drawString(font, line, x, y, color, false);

    if (comp.hasMismatch()) {
        graphics.drawString(font, "[MISMATCH!]", x + 250, y, UIConstants.Accent.RED, false);
    } else if (comp.isModified()) {
        graphics.drawString(font, "[MODIFIED]", x + 250, y, UIConstants.Accent.YELLOW, false);
    }
}
```

#### 3. Change Log (Session)
```java
record ChangeLogEntry(
    long timestamp,
    String action,           // "Set", "Applied", "Server confirmed", "Error"
    String detail,           // "attack_damage = 12.0"
    boolean isError          // true se errore
) {}

private final List<ChangeLogEntry> sessionLog = new ArrayList<>();

private void logChange(String action, String detail) {
    sessionLog.add(new ChangeLogEntry(System.currentTimeMillis(), action, detail, false));
}

private void logError(String action, String detail) {
    sessionLog.add(new ChangeLogEntry(System.currentTimeMillis(), action, detail, true));
}
```

#### 4. NBT Viewer
```java
private void renderNbtViewer(GuiGraphics graphics, int x, int y, ItemStack stack) {
    CompoundTag tag = stack.getTag();
    if (tag == null) {
        graphics.drawString(font, "(no NBT data)", x, y, UIConstants.Text.MUTED, false);
        return;
    }

    // Render formatted JSON-like structure
    String nbtString = formatNbtForDisplay(tag, 0);
    int lineY = y;
    for (String line : nbtString.split("\n")) {
        graphics.drawString(font, line, x, lineY, UIConstants.Text.FORMULA, false);
        lineY += 10;
    }
}

private String formatNbtForDisplay(CompoundTag tag, int indent) {
    StringBuilder sb = new StringBuilder();
    String prefix = "  ".repeat(indent);

    sb.append(prefix).append("{\n");
    for (String key : tag.getAllKeys()) {
        sb.append(prefix).append("  ").append(key).append(": ");
        Tag value = tag.get(key);
        if (value instanceof CompoundTag compound) {
            sb.append("\n").append(formatNbtForDisplay(compound, indent + 1));
        } else {
            sb.append(value.toString()).append(",\n");
        }
    }
    sb.append(prefix).append("}\n");

    return sb.toString();
}
```

### Copy to Clipboard Button
```java
private void copyDebugInfoToClipboard() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== DEVMOD DEBUG INFO ===\n");
    sb.append("Item: ").append(getRegistryName()).append("\n");
    sb.append("Timestamp: ").append(LocalDateTime.now()).append("\n\n");

    sb.append("--- VALUES ---\n");
    for (ValueComparison comp : getValueComparisons()) {
        sb.append(String.format("%s: %.2f → %.2f %s\n",
            comp.attributeName(), comp.originalValue(), comp.currentValue(),
            comp.hasMismatch() ? "[MISMATCH]" : comp.isModified() ? "[MOD]" : ""));
    }

    sb.append("\n--- SESSION LOG ---\n");
    for (ChangeLogEntry entry : sessionLog) {
        sb.append(formatTimestamp(entry.timestamp())).append(" ");
        sb.append(entry.action()).append(": ").append(entry.detail()).append("\n");
    }

    sb.append("\n--- NBT ---\n");
    sb.append(formatNbtForDisplay(stack.getTag(), 0));

    Minecraft.getInstance().keyboardHandler.setClipboard(sb.toString());
    showStatus("Debug info copied!", UIConstants.Accent.GREEN);
}
```

### Come leggere la Debug tab (per tester)
1. **Item Data**: controlla nome registry, damage e `hasCustomData` (se `WeaponModStats`/`ArmorModStats` presenti).
2. **Value comparisons**: giallo = modificato (non ancora applicato), rosso = mismatch con server/config (`hasMismatch=true`).
3. **Session log**: verifica se l'apply è partito/riuscito o se c'è un errore (placeholder se vuoto).
4. **NBT viewer**: controlla che il blocco `WeaponModStats`/`ArmorModStats` contenga i valori attesi e timestamp `modifiedAt`.
5. **Copy Debug**: usa il bottone per incollare in chat/issue e allegare log completo.

### Integrazione
| Editor | Tab Index | Shortcut |
|--------|-----------|----------|
| Weapon | Tab 5 (COMPONENTS → DEBUG) | F3 |
| Armor | Tab 5 (EFFECTS → DEBUG) o Tab 6 | F3 |

### Colori Status
| Stato | Colore | Significato |
|-------|--------|-------------|
| Normal | `Text.SECONDARY` | Valore non modificato |
| Modified | `Accent.YELLOW` | Modificato ma non ancora salvato |
| Saved | `Accent.GREEN` | Salvato con successo |
| Mismatch | `Accent.RED` | Server ha valore diverso (BUG!) |
| Error | `Accent.RED` | Errore di comunicazione |

## Debug Overlay

Debug overlay attivabile per sviluppo e troubleshooting. **Requisito fondamentale** per uno strumento di sviluppo.

### Keyboard Shortcuts

| Tasto | Funzione | Descrizione |
|-------|----------|-------------|
| `F9` | **Master Toggle** | Attiva/disattiva debug mode |
| `F10` | Grid Overlay | Mostra 4px grid + zone boundaries |
| `F11` | Bounds Overlay | Mostra bounding box componenti |
| `F9` + `Shift` | Cycle Detail | Low → Medium → High → Off |

### Visual Reference

```
┌─────────────────────────────────────────────────────────────────┐
│ [Tab1] [Tab2] [Tab3]                    [DEBUG ON]  [MODE] [X]  │
├┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┤
│ ┌··········────────────┐   ┌─────────────────────────────────┐  │
│ : PREVIEW  :100×100    :   │ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │  │
│ :   ┼───┼  :           :   │ ║ Section 1          ║ h:45    │  │
│ :   │ ● │  :           :   │ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │  │
│ └··········────────────┘   │ ║ Section 2          ║ h:80    │  │
│ ┌──────────────────────┐   │ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │  │
│ │ SLOTS    │ 140×70    │   │ ║ ⚠ OVERFLOW +12px  ║ h:92    │  │
│ └──────────────────────┘   │ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │  │
│ ┌──────────────────────┐   └─────────────────────────────────┘  │
│ │ INFO     │ 140×100   │                                        │
│ └──────────────────────┘   ┌─────────────────────────────────┐  │
│                            │ Grid:4px │ Scale:1.5x │ FPS:60  │  │
│                            │ Scroll:45/280 │ Sections:4      │  │
│                            │ Mouse: 234,156 │ Hovered: Slider │  │
│                            └─────────────────────────────────┘  │
├─────────────────────────────────────────────────────────────────┤
│  [Undo][Redo]  │  [F9:Debug] [F10:Grid] [F11:Bounds]  │ [Apply] │
└─────────────────────────────────────────────────────────────────┘

Legend:
  ····  = Component bounding box (cyan)
  ▓▓▓▓  = Section divider
  ⚠     = Overflow/clipping warning (red)
  ┼───┼ = Grid alignment markers
```

### Debug Info Panel

```java
/**
 * Debug information displayed in overlay.
 */
public record DebugInfo(
    // Layout
    float scale,
    int gridSize,
    int panelWidth,
    int panelHeight,

    // Scroll
    float scrollOffset,
    float maxScroll,
    int visibleSections,
    int totalSections,

    // Performance
    int fps,
    long frameTimeMs,
    int renderCalls,

    // Interaction
    int mouseX,
    int mouseY,
    String hoveredComponent,
    String focusedComponent,

    // Warnings
    List<DebugWarning> warnings
) {}

public record DebugWarning(
    WarningType type,
    String component,
    String message,
    int x, int y, int width, int height
) {
    public enum WarningType {
        OVERFLOW,       // Content exceeds bounds
        TRUNCATED,      // Text was truncated
        MISALIGNED,     // Not on 4px grid
        OUT_OF_VIEWPORT // Rendered outside visible area
    }
}
```

### Config Toggle

```toml
# config/devmod-client.toml

[debug]
# Enable debug overlay by default (can toggle with F9)
debugOverlayEnabled = false

# Default detail level: "low", "medium", "high"
debugDetailLevel = "medium"

# Show grid by default when debug is on
debugShowGrid = false

# Show component bounds by default when debug is on
debugShowBounds = true
```
