# 2.16 Scroll Policy: Rigid Layout

Tutte le tab condividono lo **stesso layout rigido**. Lo scroll è consentito **solo** nel content area.

## Layout Zones

```
┌─────────────────────────────────────────────────────────────────┐
│ [Tab1] [Tab2] [Tab3] [Tab4] [Tab5]               [MODE]    [X]  │  HEADER: FIXED
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌────────────┐   ┌─────────────────────────────────────────┐  │
│  │            │   │                                         │  │
│  │  PREVIEW   │   │                                         │  │
│  │   FIXED    │   │       SCROLLABLE CONTENT AREA           │  │
│  │            │   │                                         │  │
│  └────────────┘   │  ┌─────────────────────────────────┐    │  │
│                   │  │ Section 1                       │    │  │  LEFT: FIXED
│  ┌────────────┐   │  │ Section 2                       │    │  │
│  │   SLOTS    │   │  │ Section 3                       │◄───┼──┼── SCROLL
│  │   FIXED    │   │  │ Section 4                       │    │  │   ONLY HERE
│  └────────────┘   │  │ Section 5                       │    │  │
│                   │  │ ...                             │    │  │
│  ┌────────────┐   │  └─────────────────────────────────┘    │  │
│  │   INFO     │   │                                         │  │
│  │   FIXED    │   └─────────────────────────────────────────┘  │
│  └────────────┘                                                │
├─────────────────────────────────────────────────────────────────┤
│  [Undo][Redo] │ [History][Export][Presets] │ [Apply]            │  FOOTER: FIXED
└─────────────────────────────────────────────────────────────────┘
```

## Zone Behavior Table

| Zona | Scroll | Dimensioni | Contenuto |
|------|--------|------------|-----------|
| **Header** | ❌ FIXED | 28px height | Tab bar, mode badge, close button |
| **Left Column** | ❌ FIXED | 140px width × 280px height | Preview, slots, item info |
| **Content Area** | ✅ SCROLL | 390px width × 280px viewport | Tab-specific sections |
| **Footer** | ❌ FIXED | 60px height | Action buttons |

## Scroll Implementation

```java
/**
 * Scrollable content area for tab content.
 * All modules render into this area, never outside.
 */
public final class ScrollableContentArea {
    // Viewport dimensions (visible area)
    public static final int VIEWPORT_X = 150;
    public static final int VIEWPORT_Y = 35;
    public static final int VIEWPORT_WIDTH = 390;
    public static final int VIEWPORT_HEIGHT = 280;

    // Scroll state
    private float scrollOffset = 0;
    private float maxScrollOffset = 0;
    private float scrollVelocity = 0;

    // Scroll settings
    private static final float SCROLL_SPEED = 15.0f;
    private static final float SCROLL_SMOOTHING = 0.85f;
    private static final int SCROLLBAR_WIDTH = 6;

    /**
     * Render content with scissor clipping.
     */
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Enable scissor to clip content outside viewport
        graphics.enableScissor(
            VIEWPORT_X,
            VIEWPORT_Y,
            VIEWPORT_X + VIEWPORT_WIDTH,
            VIEWPORT_Y + VIEWPORT_HEIGHT
        );

        // Translate for scroll offset
        graphics.pose().pushPose();
        graphics.pose().translate(0, -scrollOffset, 0);

        // Render all sections from current module
        int yOffset = VIEWPORT_Y;
        for (EditorSection section : currentModule.getSections()) {
            yOffset = section.render(graphics, VIEWPORT_X, yOffset, mouseX, mouseY);
        }

        // Calculate max scroll
        int contentHeight = yOffset - VIEWPORT_Y;
        maxScrollOffset = Math.max(0, contentHeight - VIEWPORT_HEIGHT);

        graphics.pose().popPose();
        graphics.disableScissor();

        // Render scrollbar if needed
        if (maxScrollOffset > 0) {
            renderScrollbar(graphics);
        }
    }

    /**
     * Handle mouse scroll.
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isMouseOverViewport(mouseX, mouseY)) {
            scrollVelocity -= delta * SCROLL_SPEED;
            return true;
        }
        return false;
    }

    /**
     * Smooth scroll animation tick.
     */
    public void tick() {
        scrollOffset += scrollVelocity;
        scrollVelocity *= SCROLL_SMOOTHING;

        // Clamp scroll
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScrollOffset);

        // Stop tiny velocities
        if (Math.abs(scrollVelocity) < 0.1f) {
            scrollVelocity = 0;
        }
    }

    /**
     * Scroll to ensure a specific Y position is visible.
     */
    public void scrollToVisible(int targetY) {
        int relativeY = targetY - VIEWPORT_Y;

        if (relativeY < scrollOffset) {
            // Target above viewport, scroll up
            scrollOffset = relativeY;
        } else if (relativeY > scrollOffset + VIEWPORT_HEIGHT - 30) {
            // Target below viewport, scroll down
            scrollOffset = relativeY - VIEWPORT_HEIGHT + 30;
        }
    }

    /**
     * Render scrollbar indicator.
     */
    private void renderScrollbar(GuiGraphics graphics) {
        int scrollbarX = VIEWPORT_X + VIEWPORT_WIDTH - SCROLLBAR_WIDTH - 2;
        int scrollbarHeight = VIEWPORT_HEIGHT;

        // Background track
        graphics.fill(
            scrollbarX, VIEWPORT_Y,
            scrollbarX + SCROLLBAR_WIDTH, VIEWPORT_Y + scrollbarHeight,
            UIConstants.Background.DARKER
        );

        // Thumb
        float thumbRatio = VIEWPORT_HEIGHT / (float)(maxScrollOffset + VIEWPORT_HEIGHT);
        int thumbHeight = Math.max(20, (int)(scrollbarHeight * thumbRatio));
        int thumbY = VIEWPORT_Y + (int)((scrollbarHeight - thumbHeight) * (scrollOffset / maxScrollOffset));

        graphics.fill(
            scrollbarX, thumbY,
            scrollbarX + SCROLLBAR_WIDTH, thumbY + thumbHeight,
            UIConstants.Border.ACCENT
        );
    }
}
```

## Keyboard Navigation

| Tasto | Azione |
|-------|--------|
| `Page Up` | Scroll up di VIEWPORT_HEIGHT |
| `Page Down` | Scroll down di VIEWPORT_HEIGHT |
| `Home` | Scroll to top (offset = 0) |
| `End` | Scroll to bottom (offset = max) |
| `↑` / `↓` | Scroll di 20px |

## Rules for Module Authors

1. **MAI** posizionare elementi fuori dal content area
2. **MAI** implementare scroll custom nei moduli
3. **SEMPRE** usare `EditorSection` per strutturare il contenuto
4. **SEMPRE** calcolare l'altezza totale correttamente per max scroll
5. Le sezioni ricevono solo Y offset relativo, mai coordinate assolute

## Content Height Calculation

```java
/**
 * Modules must implement this to report total content height.
 */
public interface EditorModule {
    // ... other methods ...

    /**
     * Calculate total height of all sections.
     * Used by ScrollableContentArea to set maxScrollOffset.
     */
    default int calculateContentHeight() {
        int height = 0;
        for (EditorSection section : getSections()) {
            height += section.getHeight();
            height += SECTION_GAP; // 8px between sections
        }
        return height;
    }
}
```