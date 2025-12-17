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

L'implementazione attuale usa la config client NeoForge (`config/devmod-client.toml`) con fallback su system property/env durante l'early init:

```java
// EditorConfig.java
private static final String UI_SCALE_PROP = "devmod.editor.uiScale";
private static final String UI_SCALE_ENV = "DEVMOD_EDITOR_UISCALE";

public static String getUiScaleSetting() {
    try {
        EditorClientConfig.EditorUiScale scale = EditorClientConfig.EDITOR_UI_SCALE.get();
        if (scale != null) return scale.getValue();
    } catch (Exception ignored) {
        // Config may not be loaded yet
    }

    String sys = System.getProperty(UI_SCALE_PROP);
    if (sys != null && !sys.isBlank()) return sys.trim();

    String env = System.getenv(UI_SCALE_ENV);
    if (env != null && !env.isBlank()) return env.trim();

    return "auto";
}
```

**Valori accettati:** `"auto"`, `"1.0"`, `"1.25"`, `"1.5"`, `"2.0"`

## Auto Scale Algorithm

```java
// EditorScaleCalculator.java - Implementazione attuale

public final class EditorScaleCalculator {

    private static final float[] SCALE_OPTIONS = {1.0f, 1.25f, 1.5f, 2.0f};
    private static final int SCREEN_MARGIN = 24; // px margin from screen edges

    /** Calculate auto scale based on available screen size. */
    public static float calculateAutoScale(int screenWidth, int screenHeight) {
        float maxScale = SCALE_OPTIONS[0];
        for (float scale : SCALE_OPTIONS) {
            int scaledWidth = Math.round(UIConstants.PanelDimensions.PANEL_WIDTH * scale) + SCREEN_MARGIN * 2;
            int scaledHeight = Math.round(UIConstants.PanelDimensions.PANEL_HEIGHT * scale) + SCREEN_MARGIN * 2;
            if (scaledWidth <= screenWidth && scaledHeight <= screenHeight) {
                maxScale = scale;
            } else {
                break; // options are ordered
            }
        }
        return maxScale;
    }

    /**
     * Resolve effective scale from a config string ("auto", "1.0", etc.).
     * Falls back to auto on invalid input.
     */
    public static float getEffectiveScale(int screenWidth, int screenHeight, String configValue) {
        if (configValue == null || "auto".equalsIgnoreCase(configValue)) {
            return calculateAutoScale(screenWidth, screenHeight);
        }
        try {
            float requested = Float.parseFloat(configValue);
            for (float allowed : SCALE_OPTIONS) {
                if (Math.abs(requested - allowed) < 0.01f) {
                    return allowed;
                }
            }
        } catch (NumberFormatException ignored) {
            // fall through to auto
        }
        return calculateAutoScale(screenWidth, screenHeight);
    }
}
```

## Coordinate Scaling Rules

```java
// ScaledCoord.java - Implementazione attuale

public record ScaledCoord(int x, int y) {

    private static float currentScale = 1.0f;

    /** Aligns a value to the nearest 4px grid unit. */
    public static int alignTo4(int value) {
        return Math.round(value / 4.0f) * 4;
    }

    public static void setScale(float scale) { currentScale = scale; }
    public static float getScale() { return currentScale; }

    /** Scales a value using the current scale and aligns it to the 4px grid. */
    public static int scale(int value) {
        return alignTo4(Math.round(value * currentScale));
    }

    /** Scales a dimension using the current scale and aligns it to the 4px grid. */
    public static int scaleDim(int dimension) {
        return scale(dimension);
    }

    /** Scales a dimension and aligns it to the 4px grid (explicit scale). */
    public static int scaleDim(int dimension, float scale) {
        return alignTo4(Math.round(dimension * scale));
    }

    public ScaledCoord add(int dx, int dy) {
        return new ScaledCoord(x + dx, y + dy);
    }

    // ─────────────────────────────────────────────────────────────────
    // Pre-scaled constants for common panel dimensions
    // Uses UIConstants for base values, auto-aligned to 4px grid
    // ─────────────────────────────────────────────────────────────────

    public static int panelWidth() { return scaleDim(UIConstants.PanelDimensions.PANEL_WIDTH); }       // 550
    public static int panelHeight() { return scaleDim(UIConstants.PanelDimensions.PANEL_HEIGHT); }     // 420
    public static int headerHeight() { return scaleDim(UIConstants.Size.HEADER_HEIGHT); }              // 28
    public static int footerHeight() { return scaleDim(UIConstants.Size.FOOTER_HEIGHT); }              // 60
    public static int leftColumnWidth() { return scaleDim(UIConstants.PanelDimensions.LEFT_COLUMN_WIDTH); } // 140
    public static int contentWidth() { return scaleDim(UIConstants.PanelDimensions.CONTENT_WIDTH); }   // 390
    public static int previewSize() { return scaleDim(UIConstants.PanelDimensions.PREVIEW_SIZE); }     // 130
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
// EditorScaleCalculator.java - ScreenFitResult implementation

public record ScreenFitResult(
    int panelX,
    int panelY,
    int panelWidth,
    int panelHeight,
    boolean contentNeedsExtraScroll
) {}

/**
 * Calculate panel placement after applying scale and clamp rules.
 * Header/footer are never shrunk; clamp occurs on the overall panel bounds.
 */
public static ScreenFitResult calculateFit(int screenWidth, int screenHeight, float scale) {
    int scaledWidth = ScaledCoord.scaleDim(UIConstants.PanelDimensions.PANEL_WIDTH, scale);
    int scaledHeight = ScaledCoord.scaleDim(UIConstants.PanelDimensions.PANEL_HEIGHT, scale);

    int panelX = ScaledCoord.alignTo4((screenWidth - scaledWidth) / 2);
    int panelY = ScaledCoord.alignTo4((screenHeight - scaledHeight) / 2);

    boolean needsClamp = false;

    // Horizontal clamp
    if (scaledWidth + SCREEN_MARGIN * 2 > screenWidth) {
        scaledWidth = screenWidth - SCREEN_MARGIN * 2;
        panelX = SCREEN_MARGIN;
        needsClamp = true;
    }

    // Vertical clamp (content must add scroll if clamped)
    if (scaledHeight + SCREEN_MARGIN * 2 > screenHeight) {
        scaledHeight = screenHeight - SCREEN_MARGIN * 2;
        panelY = SCREEN_MARGIN;
        needsClamp = true;
    }

    // Align to grid for final bounds
    return new ScreenFitResult(
        ScaledCoord.alignTo4(panelX),
        ScaledCoord.alignTo4(panelY),
        ScaledCoord.alignTo4(scaledWidth),
        ScaledCoord.alignTo4(scaledHeight),
        needsClamp
    );
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

UI disponibile nella `UnifiedSettingsScreen` (tab Editor), accessibile con il keybind `K` o dal Radial Menu, con preview live della scala. La registrazione nel menu opzioni Minecraft resta da fare.

### UI Attuale

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

### Requisiti Implementazione

1. UI editor settings nella `UnifiedSettingsScreen` (implementato, accesso via `K`)
2. Config NeoForge client (`devmod-client.toml`) per la persistenza (implementato)
3. Mostrare preview live dell'effetto scale (implementato)
4. Registrare via `RegisterMenuScreensEvent` o equivalente NeoForge (da implementare)

---

## Changelog

| Data | Modifica |
|------|----------|
| 2025-12-17 | Aggiornata documentazione per riflettere config client e UI settings effettiva. |
