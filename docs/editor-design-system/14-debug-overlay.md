# Debug Overlay System
## F9 Toggle, Grid/Bounds Visualization, Overflow Detection

> **Sezione 2.18** del Design System - Debug overlay per sviluppo e troubleshooting

---

## Overview

Debug overlay attivabile per sviluppo e troubleshooting. **Requisito fondamentale** per uno strumento di sviluppo.

## Keyboard Shortcuts

| Tasto | Funzione | Descrizione |
|-------|----------|-------------|
| `F9` | **Master Toggle** | Attiva/disattiva debug mode |
| `F10` | Grid Overlay | Mostra 4px grid + zone boundaries |
| `F11` | Bounds Overlay | Mostra bounding box componenti |
| `F9` + `Shift` | Cycle Detail | Low → Medium → High → Off |

## Visual Reference

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

## Debug Info Panel

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

## Overlay Layers

```java
/**
 * Debug overlay rendering layers.
 */
public final class DebugOverlay {

    private static boolean enabled = false;
    private static boolean showGrid = false;
    private static boolean showBounds = false;
    private static DetailLevel detailLevel = DetailLevel.MEDIUM;

    public enum DetailLevel {
        LOW,    // Only warnings
        MEDIUM, // Warnings + bounds + basic info
        HIGH    // Everything including grid + coordinates
    }

    // Colors
    private static final int COLOR_GRID = 0x40FFFFFF;        // White 25%
    private static final int COLOR_ZONE_BOUNDARY = 0x80FFFF00; // Yellow 50%
    private static final int COLOR_BBOX = 0x8000FFFF;        // Cyan 50%
    private static final int COLOR_BBOX_HOVERED = 0xC000FFFF; // Cyan 75%
    private static final int COLOR_WARNING = 0xFFFF4444;     // Red solid
    private static final int COLOR_OVERFLOW = 0x80FF0000;    // Red 50%
    private static final int COLOR_INFO_BG = 0xE0000000;     // Black 88%
    private static final int COLOR_INFO_TEXT = 0xFFCCCCCC;   // Light gray

    /**
     * Toggle master debug mode.
     */
    public static void toggle() {
        enabled = !enabled;
        if (!enabled) {
            showGrid = false;
            showBounds = false;
        }
    }

    /**
     * Cycle detail level with Shift+F9.
     */
    public static void cycleDetailLevel() {
        detailLevel = switch (detailLevel) {
            case LOW -> DetailLevel.MEDIUM;
            case MEDIUM -> DetailLevel.HIGH;
            case HIGH -> DetailLevel.LOW;
        };
    }

    /**
     * Render debug overlay on top of editor.
     */
    public static void render(GuiGraphics graphics, EditorScreen editor, int mouseX, int mouseY) {
        if (!enabled) return;

        // Layer 1: Grid (if enabled)
        if (showGrid || detailLevel == DetailLevel.HIGH) {
            renderGrid(graphics, editor);
        }

        // Layer 2: Zone boundaries
        renderZoneBoundaries(graphics, editor);

        // Layer 3: Component bounds (if enabled)
        if (showBounds || detailLevel != DetailLevel.LOW) {
            renderComponentBounds(graphics, editor, mouseX, mouseY);
        }

        // Layer 4: Warnings (always when debug is on)
        renderWarnings(graphics, editor);

        // Layer 5: Info panel
        renderInfoPanel(graphics, editor, mouseX, mouseY);
    }
}
```

## Grid Rendering

```java
/**
 * Render 4px grid overlay.
 */
private static void renderGrid(GuiGraphics graphics, EditorScreen editor) {
    int startX = editor.getPanelX();
    int startY = editor.getPanelY();
    int endX = startX + editor.getPanelWidth();
    int endY = startY + editor.getPanelHeight();

    // Vertical lines
    for (int x = startX; x <= endX; x += 4) {
        int color = (x % 16 == 0) ? 0x60FFFFFF : COLOR_GRID;
        graphics.vLine(x, startY, endY, color);
    }

    // Horizontal lines
    for (int y = startY; y <= endY; y += 4) {
        int color = (y % 16 == 0) ? 0x60FFFFFF : COLOR_GRID;
        graphics.hLine(startX, endX, y, color);
    }
}
```

## Zone Boundaries

```java
/**
 * Render zone boundaries (header, left, content, footer).
 */
private static void renderZoneBoundaries(GuiGraphics graphics, EditorScreen editor) {
    int px = editor.getPanelX();
    int py = editor.getPanelY();

    // Header boundary
    int headerBottom = py + ScaledCoord.headerHeight();
    graphics.hLine(px, px + editor.getPanelWidth(), headerBottom, COLOR_ZONE_BOUNDARY);

    // Left column boundary
    int leftRight = px + ScaledCoord.leftColumnWidth();
    graphics.vLine(leftRight, headerBottom, py + editor.getPanelHeight() - ScaledCoord.footerHeight(), COLOR_ZONE_BOUNDARY);

    // Footer boundary
    int footerTop = py + editor.getPanelHeight() - ScaledCoord.footerHeight();
    graphics.hLine(px, px + editor.getPanelWidth(), footerTop, COLOR_ZONE_BOUNDARY);

    // Labels
    if (detailLevel == DetailLevel.HIGH) {
        graphics.drawString(font(), "HEADER", px + 4, py + 4, COLOR_INFO_TEXT);
        graphics.drawString(font(), "LEFT", px + 4, headerBottom + 4, COLOR_INFO_TEXT);
        graphics.drawString(font(), "CONTENT", leftRight + 4, headerBottom + 4, COLOR_INFO_TEXT);
        graphics.drawString(font(), "FOOTER", px + 4, footerTop + 4, COLOR_INFO_TEXT);
    }
}
```

## Component Bounds Registration

```java
/**
 * Components must register their bounds for debug overlay.
 */
public record DebugBounds(
    String name,
    int x, int y,
    int width, int height
) {
    public boolean contains(int mx, int my) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }
}

/**
 * Interface for components that report debug info.
 */
public interface DebugReporter {
    /**
     * Register bounding box for debug overlay.
     */
    DebugBounds getDebugBounds();

    /**
     * Report any warnings (overflow, truncation, etc).
     */
    default List<DebugWarning> getDebugWarnings() {
        return List.of();
    }
}
```

## Overflow Detection

```java
/**
 * Utility for detecting rendering issues.
 */
public final class OverflowDetector {

    /**
     * Check if text will be truncated at given width.
     */
    public static Optional<DebugWarning> checkTextTruncation(
            String text, int x, int y, int maxWidth, Font font) {
        int textWidth = font.width(text);
        if (textWidth > maxWidth) {
            return Optional.of(new DebugWarning(
                WarningType.TRUNCATED,
                "Text",
                "\"" + text.substring(0, 10) + "...\" exceeds by " + (textWidth - maxWidth) + "px",
                x, y, maxWidth, font.lineHeight
            ));
        }
        return Optional.empty();
    }

    /**
     * Check if component exceeds viewport bounds.
     */
    public static Optional<DebugWarning> checkViewportOverflow(
            String component, int x, int y, int width, int height,
            int viewportX, int viewportY, int viewportW, int viewportH) {

        int overflowRight = (x + width) - (viewportX + viewportW);
        int overflowBottom = (y + height) - (viewportY + viewportH);

        if (overflowRight > 0 || overflowBottom > 0) {
            String msg = "";
            if (overflowRight > 0) msg += "right +" + overflowRight + "px ";
            if (overflowBottom > 0) msg += "bottom +" + overflowBottom + "px";

            return Optional.of(new DebugWarning(
                WarningType.OVERFLOW,
                component,
                msg.trim(),
                x, y, width, height
            ));
        }
        return Optional.empty();
    }

    /**
     * Check if coordinate is aligned to 4px grid.
     */
    public static Optional<DebugWarning> checkAlignment(
            String component, int x, int y, int width, int height) {

        List<String> misaligned = new ArrayList<>();
        if (x % 4 != 0) misaligned.add("x=" + x);
        if (y % 4 != 0) misaligned.add("y=" + y);
        if (width % 4 != 0) misaligned.add("w=" + width);
        if (height % 4 != 0) misaligned.add("h=" + height);

        if (!misaligned.isEmpty()) {
            return Optional.of(new DebugWarning(
                WarningType.MISALIGNED,
                component,
                String.join(", ", misaligned) + " not on 4px grid",
                x, y, width, height
            ));
        }
        return Optional.empty();
    }
}
```

## Warning Rendering

```java
/**
 * Render warnings for overflow, truncation, misalignment.
 */
private static void renderWarnings(GuiGraphics graphics, EditorScreen editor) {
    for (DebugWarning warning : editor.getDebugWarnings()) {
        // Red overlay on problem area
        graphics.fill(
            warning.x(), warning.y(),
            warning.x() + warning.width(), warning.y() + warning.height(),
            COLOR_OVERFLOW
        );

        // Warning icon and message
        String icon = switch (warning.type()) {
            case OVERFLOW -> "⚠ OVERFLOW";
            case TRUNCATED -> "✂ TRUNCATED";
            case MISALIGNED -> "⊠ MISALIGNED";
            case OUT_OF_VIEWPORT -> "◐ OUT OF VIEW";
        };

        graphics.drawString(font(), icon + ": " + warning.message(),
            warning.x(), warning.y() - 10, COLOR_WARNING);
    }
}
```

## Info Panel

```java
/**
 * Render debug info panel in corner.
 */
private static void renderInfoPanel(GuiGraphics graphics, EditorScreen editor, int mouseX, int mouseY) {
    DebugInfo info = editor.getDebugInfo();

    List<String> lines = new ArrayList<>();
    lines.add("Grid: " + info.gridSize() + "px │ Scale: " + info.scale() + "x │ FPS: " + info.fps());
    lines.add("Scroll: " + (int)info.scrollOffset() + "/" + (int)info.maxScroll() +
              " │ Sections: " + info.visibleSections() + "/" + info.totalSections());
    lines.add("Mouse: " + mouseX + "," + mouseY + " │ Hovered: " + info.hoveredComponent());

    if (detailLevel == DetailLevel.HIGH) {
        lines.add("Frame: " + info.frameTimeMs() + "ms │ Draws: " + info.renderCalls());
        lines.add("Focused: " + info.focusedComponent());
    }

    if (!info.warnings().isEmpty()) {
        lines.add("⚠ Warnings: " + info.warnings().size());
    }

    // Calculate panel size
    int panelWidth = 280;
    int panelHeight = lines.size() * 12 + 8;
    int panelX = editor.getPanelX() + editor.getPanelWidth() - panelWidth - 8;
    int panelY = editor.getPanelY() + editor.getPanelHeight() - ScaledCoord.footerHeight() - panelHeight - 8;

    // Background
    graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, COLOR_INFO_BG);

    // Text
    int y = panelY + 4;
    for (String line : lines) {
        graphics.drawString(font(), line, panelX + 4, y, COLOR_INFO_TEXT);
        y += 12;
    }
}
```

## Config Options

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

## Implementation Tasks

### P0 - Core System
- [ ] Implementare `DebugOverlay` class con toggle F9
- [ ] Creare `DebugInfo` e `DebugWarning` records
- [ ] Implementare rendering layers (grid, bounds, warnings)

### P1 - Detection
- [ ] Implementare `OverflowDetector` utilities
- [ ] Aggiungere `DebugReporter` interface per componenti
- [ ] Integrare con sistema di grid alignment

### P2 - UI Integration
- [ ] Aggiungere debug info panel in corner
- [ ] Implementare keyboard shortcuts (F9, F10, F11)
- [ ] Integrare con sistema di scaling UI

### P3 - Advanced
- [ ] Aggiungere performance profiling
- [ ] Implementare component bounds registration
- [ ] Creare config options per debug overlay

---

**Riferimenti:**
- [13-grid-spacing.md](13-grid-spacing.md) - Sistema di griglia 4px
- [12-ui-scaling.md](12-ui-scaling.md) - Sistema di scaling UI
- [04-debug-system.md](04-debug-system.md) - Debug panel principale