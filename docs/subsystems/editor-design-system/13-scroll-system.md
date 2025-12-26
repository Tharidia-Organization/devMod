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
| **Content Area** | ✅ SCROLL | Dinamico (calcolato da ResponsiveLayout) | Tab-specific sections |
| **Footer** | ❌ FIXED | 60px height | Action buttons |

## Scroll System Architecture

Il sistema di scroll è implementato attraverso tre classi complementari:

### 1. ScrollableContentArea (components/)
Componente principale per il rendering del contenuto scrollabile con scissor clipping.

```java
/**
 * Scrollable content area for module content.
 * Handles scrolling, scissoring, and scrollbar rendering.
 *
 * @see EDITOR_DESIGN_SYSTEM.md Section 2.6 (Content Area)
 */
public class ScrollableContentArea {

    private static final int SCROLLBAR_WIDTH = EditorDimensions.SCROLLBAR_WIDTH;  // 8px
    private static final int PADDING = EditorSpacing.S;  // 8px

    private ResponsiveLayout.Rect bounds = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect contentBounds = ResponsiveLayout.Rect.EMPTY;
    private ResponsiveLayout.Rect scrollbarBounds = ResponsiveLayout.Rect.EMPTY;

    private float scrollOffset = 0;
    private int contentHeight = 0;
    private boolean scrollbarHovered = false;
    private boolean scrollbarDragging = false;
    private final AdvancedScroll smoothScroll = new AdvancedScroll();

    /**
     * Functional interface for rendering content.
     */
    @FunctionalInterface
    public interface ContentRenderer {
        int render(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY);
    }

    /**
     * Render the scrollable content area.
     * Dimensioni viewport sono passate come parametri per supportare layout responsive.
     */
    public void render(GuiGraphics graphics, int x, int y, int width, int height,
                       int mouseX, int mouseY, float partialTick, ContentRenderer contentRenderer) {
        this.bounds = new ResponsiveLayout.Rect(x, y, width, height);

        // Calculate content area (minus scrollbar)
        int contentWidth = width - SCROLLBAR_WIDTH - PADDING;
        int contentX = x + PADDING;
        int contentY = y + PADDING;
        int viewportHeight = height - PADDING * 2;

        this.contentBounds = new ResponsiveLayout.Rect(contentX, contentY, contentWidth, viewportHeight);

        // Background
        graphics.fill(x, y, x + width, y + height, UIConstants.Background.CONTENT());

        // Enable scissoring to clip content
        graphics.enableScissor(contentX, contentY, contentX + contentWidth, contentY + viewportHeight);

        // Render content with scroll offset
        int scrolledY = contentY - (int) scrollOffset;
        this.contentHeight = contentRenderer.render(graphics, contentX, scrolledY, contentWidth, mouseX, mouseY);

        // Disable scissoring
        graphics.disableScissor();

        // Update scroll bounds with smooth animation
        float maxScroll = Math.max(0, contentHeight - viewportHeight);
        smoothScroll.setMaxScroll(maxScroll);
        smoothScroll.update();
        scrollOffset = Mth.clamp(smoothScroll.getOffset(), 0, maxScroll);

        // Render scrollbar (if needed)
        if (contentHeight > viewportHeight) {
            renderScrollbar(graphics, x + width - SCROLLBAR_WIDTH, y, SCROLLBAR_WIDTH, height,
                           viewportHeight, mouseX, mouseY);
        }
    }
}
```

### 2. AdvancedScroll (editor/)
Gestisce lo smooth scrolling con fisica realistica.

```java
public class AdvancedScroll {
    private float targetOffset = 0;
    private float currentOffset = 0;
    private float velocity = 0;
    private final float smoothing = 0.8f;
    private final float friction = 0.9f;

    public void update() {
        // Smooth interpolation
        float diff = targetOffset - currentOffset;
        velocity += diff * smoothing;
        velocity *= friction;
        currentOffset += velocity;

        // Stop micro-movements
        if (Math.abs(diff) < 0.1f && Math.abs(velocity) < 0.1f) {
            currentOffset = targetOffset;
            velocity = 0;
        }
    }

    public void scroll(double delta) {
        targetOffset = Math.max(0, targetOffset - (float)(delta * 20));
    }

    public void scrollTo(float offset) {
        targetOffset = Math.max(0, offset);
    }

    public boolean handleKeyPress(int keyCode) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> { scroll(3); return true; }       // ~60px
            case GLFW.GLFW_KEY_DOWN -> { scroll(-3); return true; }    // ~60px
            case GLFW.GLFW_KEY_PAGE_UP -> { scroll(10); return true; } // ~200px
            case GLFW.GLFW_KEY_PAGE_DOWN -> { scroll(-10); return true; }
            case GLFW.GLFW_KEY_HOME -> { scrollToTop(); return true; }
            case GLFW.GLFW_KEY_END -> { scrollTo(Float.MAX_VALUE); return true; }
        }
        return false;
    }

    public float getOffset() { return currentOffset; }
    public void setMaxScroll(float max) { targetOffset = Math.min(targetOffset, max); }
}
```

### 3. ScrollState (core/)
Utility class per gestione scroll state semplice (usato da VirtualizedList).

```java
public class ScrollState {
    private int offset = 0;
    private int maxOffset = 0;
    private int viewportHeight = 0;
    private int contentHeight = 0;

    public void update(int contentHeight, int viewportHeight) {
        this.contentHeight = contentHeight;
        this.viewportHeight = viewportHeight;
        this.maxOffset = Math.max(0, contentHeight - viewportHeight);
        this.offset = Math.max(0, Math.min(offset, maxOffset));
    }

    public int scroll(double delta, int scrollAmount) {
        int scrollPixels = (int) (delta * scrollAmount);
        offset = Math.max(0, Math.min(maxOffset, offset - scrollPixels));
        return offset;
    }

    public void scrollToItem(int index, int itemHeight) {
        int itemTop = index * itemHeight;
        int itemBottom = itemTop + itemHeight;

        if (itemTop < offset) {
            offset = itemTop;
        } else if (itemBottom > offset + viewportHeight) {
            offset = itemBottom - viewportHeight;
        }
        offset = Math.max(0, Math.min(maxOffset, offset));
    }

    public ScrollbarMetrics calculateScrollbar(int trackHeight) {
        if (!isScrollable() || trackHeight <= 0) {
            return new ScrollbarMetrics(0, trackHeight);
        }
        float viewportRatio = (float) viewportHeight / contentHeight;
        int thumbHeight = Math.max(20, (int) (trackHeight * viewportRatio));
        int availableTrack = trackHeight - thumbHeight;
        int thumbY = (int) (availableTrack * getScrollPercentage());
        return new ScrollbarMetrics(thumbY, thumbHeight);
    }

    public record ScrollbarMetrics(int thumbY, int thumbHeight) {}
}
```

## Keyboard Navigation

| Tasto | Azione | Delta |
|-------|--------|-------|
| `↑` | Scroll up | ~60px (scroll(3)) |
| `↓` | Scroll down | ~60px (scroll(-3)) |
| `Page Up` | Scroll up pagina | ~200px (scroll(10)) |
| `Page Down` | Scroll down pagina | ~200px (scroll(-10)) |
| `Home` | Scroll to top | offset = 0 |
| `End` | Scroll to bottom | offset = max |

## Mouse Input Handling

```java
// Mouse scroll
public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
    if (bounds.contains(mouseX, mouseY)) {
        float maxScroll = Math.max(0, contentHeight - contentBounds.height());
        smoothScroll.setMaxScroll(maxScroll);
        smoothScroll.scroll(scrollY * 20);  // 20px per scroll tick
        scrollOffset = Mth.clamp(smoothScroll.getOffset(), 0, maxScroll);
        return true;
    }
    return false;
}

// Scrollbar drag
public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
    if (scrollbarDragging) {
        float deltaY = (float) (mouseY - dragStartY);
        float trackHeight = bounds.height() - scrollbarBounds.height();
        float maxScroll = Math.max(0, contentHeight - contentBounds.height());

        if (trackHeight > 0) {
            float scrollDelta = (deltaY / trackHeight) * maxScroll;
            scrollOffset = Mth.clamp(dragStartOffset + scrollDelta, 0, maxScroll);
        }
        return true;
    }
    return false;
}

// Scrollbar track click (jump to position)
public boolean mouseClicked(double mouseX, double mouseY, int button) {
    if (bounds.contains(mouseX, mouseY) && mouseX >= bounds.x() + bounds.width() - SCROLLBAR_WIDTH) {
        float clickRatio = (float) (mouseY - bounds.y()) / bounds.height();
        float maxScroll = Math.max(0, contentHeight - contentBounds.height());
        scrollOffset = clickRatio * maxScroll;
        return true;
    }
    return false;
}
```

## Rules for Module Authors

1. **MAI** posizionare elementi fuori dal content area
2. **MAI** implementare scroll custom nei moduli
3. **SEMPRE** usare `EditorSection` per strutturare il contenuto
4. **SEMPRE** calcolare l'altezza totale correttamente tramite `section.getHeight()`
5. Le sezioni ricevono Y offset relativo (già adjustato per scroll), mai coordinate assolute
6. Usare `ResponsiveLayout` per calcoli dimensionali, non valori hardcoded

## Scrollbar Rendering

```java
private void renderScrollbar(GuiGraphics graphics, int x, int y, int width, int height,
                              int viewportHeight, int mouseX, int mouseY) {
    // Track background
    graphics.fill(x, y, x + width, y + height, UIConstants.Background.DARKER());

    // Calculate thumb size and position
    float visibleRatio = (float) viewportHeight / contentHeight;
    int thumbHeight = Math.max(20, (int) (height * visibleRatio));
    float scrollRatio = scrollOffset / Math.max(1, contentHeight - viewportHeight);
    int thumbY = y + (int) ((height - thumbHeight) * scrollRatio);

    // Update bounds for hit testing
    scrollbarBounds = new ResponsiveLayout.Rect(x, thumbY, width, thumbHeight);
    scrollbarHovered = scrollbarBounds.contains(mouseX, mouseY);

    // Thumb with state-based coloring
    int thumbColor = scrollbarDragging ? UIConstants.Slider.THUMB_DRAG :
                    (scrollbarHovered ? UIConstants.Slider.THUMB_HOVER : UIConstants.Slider.THUMB);
    graphics.fill(x + 1, thumbY, x + width - 1, thumbY + thumbHeight, thumbColor);

    // Accent border on hover/drag
    if (scrollbarHovered || scrollbarDragging) {
        AxiomRenderer.drawBorder(graphics, x, thumbY, width, thumbHeight, UIConstants.Border.ACCENT());
    }
}
```

## Scroll Control Methods

```java
// Scroll to top
public void scrollToTop() {
    smoothScroll.scrollTo(0);
    scrollOffset = 0;
}

// Scroll to bottom
public void scrollToBottom() {
    float maxScroll = Math.max(0, contentHeight - contentBounds.height());
    smoothScroll.scrollTo(maxScroll);
    scrollOffset = maxScroll;
}

// Scroll to make Y position visible
public void scrollToY(int targetY) {
    float maxScroll = Math.max(0, contentHeight - contentBounds.height());
    int visibleTop = (int) scrollOffset;
    int visibleBottom = visibleTop + contentBounds.height();

    if (targetY < visibleTop) {
        smoothScroll.scrollTo(targetY);
        scrollOffset = targetY;
    } else if (targetY > visibleBottom) {
        float target = targetY - contentBounds.height() + PADDING;
        smoothScroll.scrollTo(target);
        scrollOffset = target;
    }
    scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
}

// Reset scroll position
public void reset() {
    smoothScroll.scrollTo(0);
    scrollOffset = 0;
    contentHeight = 0;
}
```

## Usage Example

```java
// In ItemEditorScreen
private final ScrollableContentArea contentArea = new ScrollableContentArea();

@Override
protected void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    // ... header and left column rendering ...

    // Render scrollable content
    contentArea.render(graphics, contentX, contentY, contentWidth, contentHeight,
        mouseX, mouseY, partialTick,
        (g, x, y, w, mx, my) -> {
            int height = 0;
            for (EditorSection section : currentModule.getSections()) {
                height += section.render(g, x, y + height, w, mx, my);
                height += EditorSpacing.M;  // Gap between sections
            }
            return height;
        });
}

@Override
public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
    return contentArea.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
}
```
