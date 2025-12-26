# Grid & Spacing System

Tutte le coordinate e dimensioni devono rispettare una **griglia 4px** con **padding tokens fissi**.

## Base Unit

```
BASE UNIT = 4px

Tutti i valori devono essere multipli di 4:
  ✓ 4, 8, 12, 16, 20, 24, 28, 32...
  ✗ 5, 6, 7, 9, 10, 11, 13, 14, 15...
```

## Spacing Tokens

```java
/**
 * Spacing tokens - ONLY use these values for padding/gap/margin.
 * Never use arbitrary pixel values.
 *
 * @see EDITOR_DESIGN_SYSTEM.md#16-grid--spacing-system
 */
public final class EditorSpacing {
    private EditorSpacing() {}

    // Runtime validation toggle (debug-only)
    public static boolean ENABLE_GRID_VALIDATION = false;

    // Base unit
    public static final int UNIT = 4;

    // Named spacing tokens
    public static final int XS  = 4;   // Intra-component (icon-text)
    public static final int S   = 8;   // Component padding, small gaps
    public static final int M   = 12;  // Section padding, medium gaps
    public static final int L   = 16;  // Zone padding, large gaps
    public static final int XL  = 24;  // Panel margins

    // Semantic aliases
    public static final int COMPONENT_GAP = S;      // 8px between components
    public static final int SECTION_GAP = M;        // 12px between sections
    public static final int ROW_GAP = S;            // 8px between rows
    public static final int CONTENT_PADDING = S;    // 8px content area padding
    public static final int BUTTON_PADDING_H = S;   // 8px horizontal button padding
    public static final int BUTTON_PADDING_V = XS;  // 4px vertical button padding

    /** Validate value is on 4px grid */
    public static boolean isOnGrid(int value) {
        return value % UNIT == 0;
    }

    /** Snap value to nearest grid point */
    public static int snapToGrid(int value) {
        return ((value + 2) / UNIT) * UNIT;
    }
}
```

## Usage Matrix

| Context | Padding | Gap | Token |
|---------|---------|-----|-------|
| **Button** | 8×4 (H×V) | - | S, XS |
| **Input field** | 4px all | - | XS |
| **Slider** label↔track | 8px | - | S |
| **Toggle** label↔switch | 8px | - | S |
| **Components** in row | - | 8px | S |
| **Rows** in section | - | 8px | S |
| **Sections** in content | - | 12px | M |
| **Section** header↔content | 8px | - | S |
| **Content area** padding | 8px | - | S |
| **Left column** padding | 8px | - | S |
| **Footer** padding | 8px | - | S |
| **Panel** screen margin | 24px | - | XL |

## Visual Reference

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│  ←24px→┌─────────────────────────────────────────────┐←24px→   │  XL: Panel margin
│        │                                             │         │
│        │  ←8px→ CONTENT AREA ←8px→                   │         │  S: Content padding
│        │        ┌─────────────────────────────┐      │         │
│        │        │ SECTION HEADER              │      │         │
│        │        │ ←8px padding→               │      │         │  S: Section padding
│        │        ├─────────────────────────────┤      │         │
│        │        │                             │      │         │
│        │        │  [Label]←8px→[━━━━━━━━━━]   │      │         │  S: Label↔control
│        │        │       ↑                     │      │         │
│        │        │      8px ROW_GAP            │      │         │  S: Row gap
│        │        │       ↓                     │      │         │
│        │        │  [Label]←8px→[━━━━━━━━━━]   │      │         │
│        │        │                             │      │         │
│        │        └─────────────────────────────┘      │         │
│        │              ↑                              │         │
│        │             12px SECTION_GAP                │         │  M: Section gap
│        │              ↓                              │         │
│        │        ┌─────────────────────────────┐      │         │
│        │        │ NEXT SECTION                │      │         │
│        │        └─────────────────────────────┘      │         │
│        │                                             │         │
│        └─────────────────────────────────────────────┘         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Component Dimensions (all on 4px grid)

```java
/**
 * Standard component dimensions - all multiples of 4.
 *
 * @see EDITOR_DESIGN_SYSTEM.md#16-grid--spacing-system
 */
public final class EditorDimensions {
    private EditorDimensions() {}

    // =========================================================================
    // BUTTONS
    // =========================================================================

    public static final int BTN_HEIGHT_SMALL = 20;   // 5 units
    public static final int BTN_HEIGHT_NORMAL = 24;  // 6 units
    public static final int BTN_HEIGHT_LARGE = 32;   // 8 units
    public static final int BTN_MIN_WIDTH = 48;      // 12 units

    // =========================================================================
    // INPUTS
    // =========================================================================

    public static final int INPUT_HEIGHT = 20;       // 5 units
    public static final int INPUT_MIN_WIDTH = 60;    // 15 units

    // =========================================================================
    // SLIDERS
    // =========================================================================

    public static final int SLIDER_HEIGHT = 20;      // 5 units
    public static final int SLIDER_TRACK_HEIGHT = 4; // 1 unit
    public static final int SLIDER_THUMB_SIZE = 12;  // 3 units
    public static final int SLIDER_LABEL_WIDTH = 100;// 25 units

    // =========================================================================
    // TOGGLES
    // =========================================================================

    public static final int TOGGLE_WIDTH = 36;       // 9 units
    public static final int TOGGLE_HEIGHT = 20;      // 5 units

    // =========================================================================
    // TABS
    // =========================================================================

    public static final int TAB_HEIGHT = 24;         // 6 units
    public static final int TAB_MIN_WIDTH = 72;      // 18 units
    public static final int TAB_GAP = 4;             // 1 unit

    // =========================================================================
    // SECTIONS
    // =========================================================================

    public static final int SECTION_HEADER_HEIGHT = 24;  // 6 units
    public static final int SECTION_MIN_HEIGHT = 48;     // 12 units

    // =========================================================================
    // OTHER
    // =========================================================================

    public static final int SCROLLBAR_WIDTH = 8;     // 2 units
    public static final int ICON_SMALL = 12;         // 3 units
    public static final int ICON_NORMAL = 16;        // 4 units
    public static final int ICON_LARGE = 24;         // 6 units
    public static final int SLOT_SIZE = 32;          // 8 units, aligned to 4px grid
}
```

## Row Layout Helper

```java
/**
 * Helper for laying out components in a row with consistent spacing.
 * Uses ScaledSpacing for scaled gap values and ScaledCoord.alignTo4() for grid alignment.
 *
 * @see ScaledSpacing#componentGap()
 */
public final class RowLayout {
    private final int startX;
    private final int y;
    private final int gap;
    private int currentX;

    /** Creates a row layout with scaled default component gap. */
    public RowLayout(int x, int y) {
        this(x, y, ScaledSpacing.componentGap());
    }

    /** Creates a row layout with custom gap. */
    public RowLayout(int x, int y, int gap) {
        this.startX = ScaledCoord.alignTo4(x);
        this.y = ScaledCoord.alignTo4(y);
        this.gap = ScaledCoord.alignTo4(gap);
        this.currentX = this.startX;
    }

    /** Add a component width and return its X position. */
    public int add(int width) {
        int x = currentX;
        currentX += ScaledCoord.alignTo4(width) + gap;
        return x;
    }

    /** Add flexible space. */
    public void addSpace(int space) {
        currentX += ScaledCoord.alignTo4(space);
    }

    public int getX() { return currentX; }
    public int getY() { return y; }
    public int getWidth() { return currentX - startX - gap; }
}
```

## Section Layout Helper

```java
/**
 * Helper for laying out vertical sections with consistent spacing.
 * Uses ScaledSpacing for scaled spacing values and ScaledCoord.alignTo4() for grid alignment.
 *
 * @see ScaledSpacing#sectionGap()
 * @see ScaledSpacing#rowGap()
 */
public final class SectionLayout {
    private final int x;
    private final int startY;
    private final int width;
    private int currentY;

    public SectionLayout(int x, int y, int width) {
        this.x = ScaledCoord.alignTo4(x);
        this.startY = ScaledCoord.alignTo4(y);
        this.width = ScaledCoord.alignTo4(width);
        this.currentY = this.startY;
    }

    /** Add a header row and return its Y position (uses scaled header height). */
    public int addHeader() {
        int y = currentY;
        currentY += ScaledCoord.scale(EditorDimensions.SECTION_HEADER_HEIGHT);
        currentY += ScaledSpacing.s(); // padding after header
        return y;
    }

    /** Add a row of specified height and return its Y position. */
    public int addRow(int height) {
        int y = currentY;
        currentY += ScaledCoord.alignTo4(height);
        currentY += ScaledSpacing.rowGap();
        return y;
    }

    /** End current section and add section gap. */
    public void endSection() {
        currentY -= ScaledSpacing.rowGap();    // remove last row gap
        currentY += ScaledSpacing.sectionGap();
    }

    public int getY() { return currentY; }
    public int getHeight() { return currentY - startY; }
    public int getContentX() { return x + ScaledSpacing.contentPadding(); }
    public int getContentWidth() { return width - ScaledSpacing.contentPadding() * 2; }
}
```

## Enforcement Rules

| Rule | Enforcement | Level |
|------|-------------|-------|
| Coordinates on 4px grid | `ScaledCoord.alignTo4()` | Compile-time (use helper) |
| Dimensions on 4px grid | `EditorDimensions` constants | Compile-time (use constants) |
| Spacing from tokens only | `EditorSpacing` constants | Code review |
| No magic numbers | Static analysis / linter | CI |
| Grid violations | Debug overlay `MISALIGNED` warning | Runtime (dev) |

## Anti-Patterns

```java
// ❌ BAD: Magic numbers
int x = 137;
int padding = 5;
graphics.fill(x, y, x + 73, y + 19, color);

// ✓ GOOD: Grid-aligned constants
int x = ScaledCoord.scale(136);  // Snaps to 136
int padding = EditorSpacing.XS;  // 4px
graphics.fill(x, y, x + EditorDimensions.BTN_MIN_WIDTH, y + EditorDimensions.BTN_HEIGHT_SMALL, color);

// ❌ BAD: Arbitrary gap
int gap = 6;
renderComponent(x, y);
renderComponent(x + width + gap, y);

// ✓ GOOD: Token-based gap
RowLayout row = new RowLayout(x, y);
renderComponent(row.add(width1), row.getY());
renderComponent(row.add(width2), row.getY());
```

## Integration with Scaling

`ScaledSpacing` fornisce metodi per ottenere spacing tokens già scalati e allineati alla griglia 4px.
I layout helpers (`RowLayout`, `SectionLayout`) usano `ScaledSpacing` internamente.

```java
/**
 * Scaled spacing values for use in render code.
 * All methods return spacing tokens scaled by the current UI scale
 * and aligned to the 4px grid.
 *
 * @see EditorSpacing for base unscaled values
 * @see ScaledCoord#scale(int) for the scaling logic
 */
public final class ScaledSpacing {
    private ScaledSpacing() {}

    // Named spacing tokens (scaled)
    public static int xs()  { return ScaledCoord.scale(EditorSpacing.XS); }
    public static int s()   { return ScaledCoord.scale(EditorSpacing.S); }
    public static int m()   { return ScaledCoord.scale(EditorSpacing.M); }
    public static int l()   { return ScaledCoord.scale(EditorSpacing.L); }
    public static int xl()  { return ScaledCoord.scale(EditorSpacing.XL); }

    // Semantic aliases (scaled)
    public static int componentGap()  { return ScaledCoord.scale(EditorSpacing.COMPONENT_GAP); }
    public static int sectionGap()    { return ScaledCoord.scale(EditorSpacing.SECTION_GAP); }
    public static int rowGap()        { return ScaledCoord.scale(EditorSpacing.ROW_GAP); }
    public static int contentPadding(){ return ScaledCoord.scale(EditorSpacing.CONTENT_PADDING); }
    public static int buttonPaddingH(){ return ScaledCoord.scale(EditorSpacing.BUTTON_PADDING_H); }
    public static int buttonPaddingV(){ return ScaledCoord.scale(EditorSpacing.BUTTON_PADDING_V); }
}
```

---

## Implementation Status (2025-01)

| Component | File | Status |
|-----------|------|--------|
| `EditorSpacing` | `ui/editor/core/EditorSpacing.java` | ✅ Implemented |
| `EditorDimensions` | `ui/editor/core/EditorDimensions.java` | ✅ Implemented |
| `RowLayout` | `ui/editor/core/RowLayout.java` | ✅ Implemented (uses ScaledSpacing) |
| `SectionLayout` | `ui/editor/core/SectionLayout.java` | ✅ Implemented (uses ScaledSpacing) |
| `ScaledSpacing` | `ui/editor/core/ScaledSpacing.java` | ✅ Implemented |
| `ScaledCoord.alignTo4()` | `ui/editor/core/ScaledCoord.java` | ✅ Implemented |

**Notes:**
- `RowLayout` e `SectionLayout` usano `ScaledSpacing` per gap/padding scalati
- `ScaledCoord.alignTo4()` usato per allineamento coordinate alla griglia 4px
- `SectionLayout.addHeader()` non richiede parametro `title` (rendering del titolo gestito esternamente)
- `EditorDimensions` include costanti aggiuntive: `SLIDER_LABEL_WIDTH`, `TAB_GAP`, `SLOT_SIZE`
- `TAB_MIN_WIDTH` aumentato da 64 a 72 per miglior resa visiva