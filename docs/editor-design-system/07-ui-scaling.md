# Resolution & UI Scaling

Base 1080p con UI scale dedicato, indipendente dalla GUI scale di Minecraft.

## Scale Factors (Discreti)

| Scale | Panel Size | Target Resolution | Note |
|-------|------------|-------------------|------|
| **1.0x** | 550×420 | 1080p (1920×1080) | Base reference |
| **1.25x** | 688×525 | 1080p large / 1440p small | |
| **1.5x** | 825×630 | 1440p (2560×1440) | |
| **2.0x** | 1100×840 | 4K (3840×2160) | |

**NO scale intermedi** (no 1.33x, 1.75x) - solo valori discreti per evitare artefatti.

## Config Option

```toml
# config/devmod-client.toml

[editor]
# UI Scale for Item Editor
# Values: "auto", "1.0", "1.25", "1.5", "2.0"
# Auto selects largest scale that fits screen with margin
uiScale = "auto"
```

## Auto Scale Algorithm

```java
/**
 * UI Scale calculator for editor.
 * Independent from Minecraft GUI scale.
 */
public final class EditorScaleCalculator {

    private static final float[] SCALE_OPTIONS = {1.0f, 1.25f, 1.5f, 2.0f};
    private static final int SCREEN_MARGIN = 24; // px margin from screen edges

    // Base dimensions (1080p reference)
    public static final int BASE_WIDTH = 550;
    public static final int BASE_HEIGHT = 420;

    /**
     * Calculate optimal scale factor.
     * Returns largest scale that keeps panel within screen bounds.
     */
    public static float calculateAutoScale(int screenWidth, int screenHeight) {
        float maxScale = 1.0f;

        for (float scale : SCALE_OPTIONS) {
            int scaledWidth = Math.round(BASE_WIDTH * scale);
            int scaledHeight = Math.round(BASE_HEIGHT * scale);

            // Check if fits with margin
            if (scaledWidth + (SCREEN_MARGIN * 2) <= screenWidth &&
                scaledHeight + (SCREEN_MARGIN * 2) <= screenHeight) {
                maxScale = scale;
            } else {
                break; // Scales are ordered, stop at first that doesn't fit
            }
        }

        return maxScale;
    }

    /**
     * Get scale from config, resolving "auto" if needed.
     */
    public static float getEffectiveScale(int screenWidth, int screenHeight) {
        String configValue = Config.CLIENT.editorUiScale.get();

        if ("auto".equals(configValue)) {
            return calculateAutoScale(screenWidth, screenHeight);
        }

        try {
            float scale = Float.parseFloat(configValue);
            // Validate against allowed values
            for (float allowed : SCALE_OPTIONS) {
                if (Math.abs(scale - allowed) < 0.01f) {
                    return allowed;
                }
            }
        } catch (NumberFormatException e) {
            // Fallback
        }

        return 1.0f; // Default fallback
    }
}
```

## Coordinate Scaling Rules

```java
/**
 * All coordinates must be scaled through this utility.
 * Ensures alignment to 4px grid after scaling.
 */
public final class ScaledCoord {

    private static float currentScale = 1.0f;

    public static void setScale(float scale) {
        currentScale = scale;
    }

    /**
     * Scale a coordinate and align to 4px grid.
     */
    public static int scale(int base) {
        return alignTo4(Math.round(base * currentScale));
    }

    /**
     * Scale a dimension (width/height) and align to 4px grid.
     */
    public static int scaleDim(int base) {
        return alignTo4(Math.round(base * currentScale));
    }

    /**
     * Align value to nearest multiple of 4.
     */
    private static int alignTo4(int value) {
        return ((value + 2) / 4) * 4;
    }

    // Pre-scaled constants for common values
    public static int panelWidth() { return scaleDim(550); }
    public static int panelHeight() { return scaleDim(420); }
    public static int headerHeight() { return scaleDim(28); }
    public static int footerHeight() { return scaleDim(60); }
    public static int leftColumnWidth() { return scaleDim(140); }
    public static int contentWidth() { return scaleDim(390); }
    public static int previewSize() { return scaleDim(100); }
}
```

## 4 Regole Fondamentali

| # | Regola | Dettaglio |
|---|--------|-----------|
| 1 | **Scale discreti only** | 1.0 / 1.25 / 1.5 / 2.0 - mai valori intermedi |
| 2 | **Auto = max che entra** | Pannello + 24px margine deve stare nello schermo |
| 3 | **Clamp, non shrink** | Se non entra → scroll nel content, header/footer fissi |
| 4 | **Allineamento 4px** | Tutte le coordinate arrotondate a multipli di 4 |

## Screen Fit Validation

```java
/**
 * Validate panel fits in screen, apply clamp if needed.
 */
public static class ScreenFitResult {
    public final int panelX;
    public final int panelY;
    public final int panelWidth;
    public final int panelHeight;
    public final boolean contentNeedsExtraScroll;

    public static ScreenFitResult calculate(int screenWidth, int screenHeight, float scale) {
        int scaledWidth = ScaledCoord.scaleDim(BASE_WIDTH);
        int scaledHeight = ScaledCoord.scaleDim(BASE_HEIGHT);

        // Center panel
        int panelX = (screenWidth - scaledWidth) / 2;
        int panelY = (screenHeight - scaledHeight) / 2;

        boolean needsClamp = false;

        // Horizontal clamp
        if (scaledWidth > screenWidth - SCREEN_MARGIN * 2) {
            scaledWidth = screenWidth - SCREEN_MARGIN * 2;
            panelX = SCREEN_MARGIN;
            needsClamp = true;
        }

        // Vertical clamp - never shrink header/footer
        if (scaledHeight > screenHeight - SCREEN_MARGIN * 2) {
            scaledHeight = screenHeight - SCREEN_MARGIN * 2;
            panelY = SCREEN_MARGIN;
            needsClamp = true;
            // Content area gets reduced, scroll compensates
        }

        return new ScreenFitResult(
            ScaledCoord.alignTo4(panelX),
            ScaledCoord.alignTo4(panelY),
            ScaledCoord.alignTo4(scaledWidth),
            ScaledCoord.alignTo4(scaledHeight),
            needsClamp
        );
    }
}
```

## Font Scaling

| Element | Base Size | 1.25x | 1.5x | 2.0x |
|---------|-----------|-------|------|------|
| Tab label | 9px | 11px | 14px | 18px |
| Section header | 10px | 12px | 15px | 20px |
| Value text | 8px | 10px | 12px | 16px |
| Button text | 9px | 11px | 14px | 18px |

```java
/**
 * Get scaled font for text rendering.
 * Uses Minecraft's font with scale matrix.
 */
public static void drawScaledText(GuiGraphics graphics, String text, int x, int y, int color, float textScale) {
    float effectiveScale = currentScale * textScale;
    graphics.pose().pushPose();
    graphics.pose().scale(effectiveScale, effectiveScale, 1.0f);
    graphics.drawString(
        Minecraft.getInstance().font,
        text,
        Math.round(x / effectiveScale),
        Math.round(y / effectiveScale),
        color
    );
    graphics.pose().popPose();
}
```

## In-Game Settings UI

```
┌─────────────────────────────────────────────────────────────────┐
│  DEVMOD SETTINGS                                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Editor UI Scale:                                               │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ [Auto] │ [1.0x] │ [1.25x] │ [1.5x] │ [2.0x]             │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  Current: Auto → 1.5x (detected 1440p)                          │
│  Panel size: 825×630px                                          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```