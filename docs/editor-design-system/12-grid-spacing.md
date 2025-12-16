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
 */
public final class EditorSpacing {
    private EditorSpacing() {}

    // Base unit
    public static final int UNIT = 4;

    // Spacing tokens
    public static final int XS  = 4;   // Intra-component (icon↔text, input padding)
    public static final int S   = 8;   // Component padding, small gaps
    public static final int M   = 12;  // Section padding, medium gaps
    public static final int L   = 16;  // Zone padding, large gaps
    public static final int XL  = 24;  // Panel margins, extra large gaps

    // Derived values (all multiples of 4)
    public static final int COMPONENT_GAP = S;      // 8px between components in row
    public static final int SECTION_GAP = M;        // 12px between sections
    public static final int ROW_GAP = S;            // 8px between rows in section
    public static final int CONTENT_PADDING = S;    // 8px content area padding
    public static final int BUTTON_PADDING_H = S;   // 8px horizontal button padding
    public static final int BUTTON_PADDING_V = XS;  // 4px vertical button padding

    /**
     * Validate a value is on the 4px grid.
     * Use in debug builds to catch errors early.
     */
    public static boolean isOnGrid(int value) {
        return value % UNIT == 0;
    }

    /**
     * Snap a value to nearest grid point.
     */
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
 */
public final class EditorDimensions {
    private EditorDimensions() {}

    // Buttons
    public static final int BTN_HEIGHT_SMALL = 20;   // 5 units
    public static final int BTN_HEIGHT_NORMAL = 24;  // 6 units
    public static final int BTN_HEIGHT_LARGE = 32;   // 8 units
    public static final int BTN_MIN_WIDTH = 48;      // 12 units

    // Inputs
    public static final int INPUT_HEIGHT = 20;       // 5 units
    public static final int INPUT_MIN_WIDTH = 60;    // 15 units

    // Sliders
    public static final int SLIDER_HEIGHT = 20;      // 5 units
    public static final int SLIDER_TRACK_HEIGHT = 4; // 1 unit
    public static final int SLIDER_THUMB_SIZE = 12;  // 3 units

    // Toggles
    public static final int TOGGLE_WIDTH = 36;       // 9 units
    public static final int TOGGLE_HEIGHT = 20;      // 5 units

    // Tabs
    public static final int TAB_HEIGHT = 24;         // 6 units
    public static final int TAB_MIN_WIDTH = 64;      // 16 units

    // Sections
    public static final int SECTION_HEADER_HEIGHT = 24;  // 6 units
    public static final int SECTION_MIN_HEIGHT = 48;     // 12 units

    // Scrollbar
    public static final int SCROLLBAR_WIDTH = 8;     // 2 units

    // Icons
    public static final int ICON_SMALL = 12;         // 3 units
    public static final int ICON_NORMAL = 16;        // 4 units
    public static final int ICON_LARGE = 24;         // 6 units
}
```

## Row Layout Helper

```java
/**
 * Helper for laying out components in a row with consistent spacing.
 */
public final class RowLayout {
    private final int startX;
    private final int y;
    private final int gap;
    private int currentX;

    public RowLayout(int x, int y, int gap) {
        this.startX = x;
        this.y = y;
        this.gap = EditorSpacing.snapToGrid(gap);
        this.currentX = x;
    }

    public RowLayout(int x, int y) {
        this(x, y, EditorSpacing.COMPONENT_GAP);
    }

    /**
     * Add a component and return its X position.
     * Automatically advances currentX for next component.
     */
    public int add(int width) {
        int x = currentX;
        currentX += EditorSpacing.snapToGrid(width) + gap;
        return x;
    }

    /**
     * Add flexible space (for right-aligned components).
     */
    public void addSpace(int space) {
        currentX += EditorSpacing.snapToGrid(space);
    }

    /**
     * Get current X position.
     */
    public int getX() {
        return currentX;
    }

    /**
     * Get Y position (constant for row).
     */
    public int getY() {
        return y;
    }

    /**
     * Get total width used so far.
     */
    public int getWidth() {
        return currentX - startX - gap; // Subtract trailing gap
    }
}
```

## Section Layout Helper

```java
/**
 * Helper for laying out sections vertically with consistent spacing.
 */
public final class SectionLayout {
    private final int x;
    private final int startY;
    private final int width;
    private int currentY;

    public SectionLayout(int x, int y, int width) {
        this.x = x;
        this.startY = y;
        this.width = EditorSpacing.snapToGrid(width);
        this.currentY = y;
    }

    /**
     * Add a section header and return its Y position.
     */
    public int addHeader(String title) {
        int y = currentY;
        currentY += EditorDimensions.SECTION_HEADER_HEIGHT;
        currentY += EditorSpacing.S; // Padding after header
        return y;
    }

    /**
     * Add a row and return its Y position.
     */
    public int addRow(int height) {
        int y = currentY;
        currentY += EditorSpacing.snapToGrid(height);
        currentY += EditorSpacing.ROW_GAP;
        return y;
    }

    /**
     * End current section and add section gap.
     */
    public void endSection() {
        currentY -= EditorSpacing.ROW_GAP; // Remove last row gap
        currentY += EditorSpacing.SECTION_GAP;
    }

    /**
     * Get current Y position.
     */
    public int getY() {
        return currentY;
    }

    /**
     * Get total height used so far.
     */
    public int getHeight() {
        return currentY - startY;
    }

    /**
     * Get content X (with padding).
     */
    public int getContentX() {
        return x + EditorSpacing.CONTENT_PADDING;
    }

    /**
     * Get content width (minus padding).
     */
    public int getContentWidth() {
        return width - (EditorSpacing.CONTENT_PADDING * 2);
    }
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

```java
/**
 * Scaled spacing values - use these in render code.
 */
public final class ScaledSpacing {

    public static int xs()  { return ScaledCoord.scale(EditorSpacing.XS); }
    public static int s()   { return ScaledCoord.scale(EditorSpacing.S); }
    public static int m()   { return ScaledCoord.scale(EditorSpacing.M); }
    public static int l()   { return ScaledCoord.scale(EditorSpacing.L); }
    public static int xl()  { return ScaledCoord.scale(EditorSpacing.XL); }

    public static int componentGap() { return ScaledCoord.scale(EditorSpacing.COMPONENT_GAP); }
    public static int sectionGap()   { return ScaledCoord.scale(EditorSpacing.SECTION_GAP); }
    public static int rowGap()       { return ScaledCoord.scale(EditorSpacing.ROW_GAP); }
}
```