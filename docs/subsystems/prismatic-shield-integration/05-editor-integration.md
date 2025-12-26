# 05 - Editor Integration

## Obiettivo

Estendere `ArmorModule` nell'editor per esporre i nuovi parametri dello scudo energetico Prismatic.

## Nuovi Campi in ArmorStats

Prima di integrare nell'editor, aggiungere i nuovi campi:

```java
// ArmorStats.java - Shield Visual & Behavior Settings

// === Existing (already present) ===
public boolean shieldReflectProjectiles = false;
public float shieldBlockStrength = 0.5f;
public float shieldRecoverySpeed = 1.0f;

// === NEW: Visual Settings (Prismatic) ===
public int shieldColor = 0x3D5AFE;           // RGB color (Electric Blue)
public float shieldOpacity = 0.6f;            // Base opacity (0.0-1.0)
public boolean shieldGlowEnabled = true;      // Fresnel edge glow
public float shieldGlowIntensity = 1.0f;      // Glow strength (0.0-2.0)
public float shieldNoiseIntensity = 0.15f;    // Energy field noise (0.0-0.5)
public float shieldPulseSpeed = 1.0f;         // Animation speed (0.5-2.0)

// === NEW: Deflection Settings ===
public float shieldDeflectionSpread = 0.15f;  // Max angular spread (radians)
public boolean shieldDeflectToOwner = false;  // Deflect back to shooter
public float shieldDeflectSpeedMult = 0.8f;   // Speed after deflection (0.5-1.5)

// === NEW: Shatter Settings ===
public float shieldShatterThreshold = 10.0f;  // Damage threshold to trigger shatter
public boolean shieldAutoRegenerate = true;   // Auto-regen after shatter
public float shieldRegenDelay = 3.0f;         // Seconds before regen starts

// Helper method for shader
public Vec3 getShieldColorVec3() {
    int r = (shieldColor >> 16) & 0xFF;
    int g = (shieldColor >> 8) & 0xFF;
    int b = shieldColor & 0xFF;
    return new Vec3(r / 255.0, g / 255.0, b / 255.0);
}
```

## Modifiche ad ArmorModule

### Nuovo Section: Shield Visual

```java
// ArmorModule.java - aggiungere dopo la sezione esistente "Shield"

private void addShieldVisualSection(ScrollableContentArea content, ArmorStats stats) {
    content.addSection("Shield Visual", section -> {

        // === Color Picker ===
        section.addColorPicker(
            "Shield Color",
            stats.shieldColor,
            color -> stats.shieldColor = color,
            "Base color of the energy shield"
        );

        // === Opacity Slider ===
        section.addSlider(
            "Opacity",
            stats.shieldOpacity,
            0.1f, 1.0f,
            0.1f,
            val -> stats.shieldOpacity = val,
            "Base transparency of the shield"
        );

        // === Glow Toggle ===
        section.addToggle(
            "Edge Glow",
            stats.shieldGlowEnabled,
            val -> stats.shieldGlowEnabled = val,
            "Enable Fresnel edge glow effect"
        );

        // === Glow Intensity (only if glow enabled) ===
        if (stats.shieldGlowEnabled) {
            section.addSlider(
                "Glow Intensity",
                stats.shieldGlowIntensity,
                0.0f, 2.0f,
                0.1f,
                val -> stats.shieldGlowIntensity = val,
                "Strength of edge glow"
            );
        }

        // === Noise/Energy Effect ===
        section.addSlider(
            "Energy Intensity",
            stats.shieldNoiseIntensity,
            0.0f, 0.5f,
            0.05f,
            val -> stats.shieldNoiseIntensity = val,
            "Animated energy field intensity"
        );

        // === Animation Speed ===
        section.addSlider(
            "Animation Speed",
            stats.shieldPulseSpeed,
            0.5f, 2.0f,
            0.1f,
            val -> stats.shieldPulseSpeed = val,
            "Speed of shield animation"
        );
    });
}

private void addShieldDeflectionSection(ScrollableContentArea content, ArmorStats stats) {
    content.addSection("Shield Deflection", section -> {

        // === Deflection Spread ===
        section.addSlider(
            "Deflection Spread",
            (float) Math.toDegrees(stats.shieldDeflectionSpread),
            0.0f, 30.0f,
            1.0f,
            val -> stats.shieldDeflectionSpread = (float) Math.toRadians(val),
            "Max random angle for deflected projectiles (degrees)"
        );

        // === Deflect to Owner ===
        section.addToggle(
            "Return to Sender",
            stats.shieldDeflectToOwner,
            val -> stats.shieldDeflectToOwner = val,
            "Deflect projectiles back toward their shooter"
        );

        // === Speed Multiplier ===
        section.addSlider(
            "Deflect Speed",
            stats.shieldDeflectSpeedMult * 100f,
            50f, 150f,
            5f,
            val -> stats.shieldDeflectSpeedMult = val / 100f,
            "Projectile speed after deflection (%)"
        );
    });
}

private void addShieldShatterSection(ScrollableContentArea content, ArmorStats stats) {
    content.addSection("Shield Shatter", section -> {

        // === Shatter Threshold ===
        section.addSlider(
            "Shatter Threshold",
            stats.shieldShatterThreshold,
            5.0f, 50.0f,
            1.0f,
            val -> stats.shieldShatterThreshold = val,
            "Damage required to break shield"
        );

        // === Auto Regenerate ===
        section.addToggle(
            "Auto Regenerate",
            stats.shieldAutoRegenerate,
            val -> stats.shieldAutoRegenerate = val,
            "Shield regenerates after being shattered"
        );

        // === Regen Delay (only if auto-regen enabled) ===
        if (stats.shieldAutoRegenerate) {
            section.addSlider(
                "Regen Delay",
                stats.shieldRegenDelay,
                1.0f, 10.0f,
                0.5f,
                val -> stats.shieldRegenDelay = val,
                "Seconds before shield starts regenerating"
            );
        }
    });
}
```

### Integrazione nel Render Loop

```java
// ArmorModule.java - modificare buildContent()

@Override
protected void buildContent(ScrollableContentArea content) {
    ArmorStats stats = getCurrentStats();
    if (stats == null) return;

    // Existing sections
    addProtectionSection(content, stats);
    addResistanceSection(content, stats);
    addShieldSection(content, stats); // Existing basic shield settings

    // NEW: Only show Prismatic sections if shield is enabled
    if (stats.shieldBlockStrength > 0) {
        addShieldVisualSection(content, stats);
        if (stats.shieldReflectProjectiles) {
            addShieldDeflectionSection(content, stats);
        }
        addShieldShatterSection(content, stats);
    }
}
```

## ColorPicker Component

Se non esiste già un color picker, implementarlo:

```java
// Nuovo file: EditorColorPicker.java

package com.devmod.ui.editor.components;

import net.minecraft.client.gui.GuiGraphics;
import java.util.function.Consumer;

/**
 * Color picker component for the editor.
 * Shows current color + click to open palette.
 */
public class EditorColorPicker extends EditorComponent {

    private final int currentColor;
    private final Consumer<Integer> onChange;
    private boolean paletteOpen = false;

    // Predefined color palette
    private static final int[] PALETTE = {
        0xFF0000, // Red
        0xFF6600, // Orange
        0xFFFF00, // Yellow
        0x00FF00, // Green
        0x00FFFF, // Cyan
        0x3D5AFE, // Electric Blue (default)
        0x0000FF, // Blue
        0x9900FF, // Purple
        0xFF00FF, // Magenta
        0xFFFFFF, // White
        0x808080, // Gray
        0x000000  // Black
    };

    public EditorColorPicker(int x, int y, int width, int height,
                             int currentColor, Consumer<Integer> onChange, String tooltip) {
        super(x, y, width, height, tooltip);
        this.currentColor = currentColor;
        this.onChange = onChange;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Draw current color swatch
        int swatchSize = height - 4;
        graphics.fill(x + 2, y + 2, x + 2 + swatchSize, y + 2 + swatchSize,
                      0xFF000000 | currentColor);

        // Draw border
        graphics.renderOutline(x + 1, y + 1, swatchSize + 2, swatchSize + 2, 0xFFFFFFFF);

        // Draw hex value
        String hex = String.format("#%06X", currentColor);
        graphics.drawString(font, hex, x + swatchSize + 8, y + (height - 8) / 2, 0xFFFFFF);

        // Draw palette if open
        if (paletteOpen) {
            renderPalette(graphics, mouseX, mouseY);
        }
    }

    private void renderPalette(GuiGraphics graphics, int mouseX, int mouseY) {
        int paletteX = x;
        int paletteY = y + height + 2;
        int cols = 4;
        int swatchSize = 16;
        int padding = 2;

        // Background
        int paletteWidth = cols * (swatchSize + padding) + padding;
        int paletteHeight = (PALETTE.length / cols + 1) * (swatchSize + padding) + padding;
        graphics.fill(paletteX, paletteY, paletteX + paletteWidth, paletteY + paletteHeight, 0xDD000000);

        // Draw color swatches
        for (int i = 0; i < PALETTE.length; i++) {
            int col = i % cols;
            int row = i / cols;
            int sx = paletteX + padding + col * (swatchSize + padding);
            int sy = paletteY + padding + row * (swatchSize + padding);

            graphics.fill(sx, sy, sx + swatchSize, sy + swatchSize, 0xFF000000 | PALETTE[i]);

            // Highlight on hover
            if (mouseX >= sx && mouseX < sx + swatchSize &&
                mouseY >= sy && mouseY < sy + swatchSize) {
                graphics.renderOutline(sx - 1, sy - 1, swatchSize + 2, swatchSize + 2, 0xFFFFFFFF);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        // Check palette click
        if (paletteOpen) {
            int paletteX = x;
            int paletteY = y + height + 2;
            int cols = 4;
            int swatchSize = 16;
            int padding = 2;

            for (int i = 0; i < PALETTE.length; i++) {
                int col = i % cols;
                int row = i / cols;
                int sx = paletteX + padding + col * (swatchSize + padding);
                int sy = paletteY + padding + row * (swatchSize + padding);

                if (mouseX >= sx && mouseX < sx + swatchSize &&
                    mouseY >= sy && mouseY < sy + swatchSize) {
                    onChange.accept(PALETTE[i]);
                    paletteOpen = false;
                    return true;
                }
            }

            // Click outside palette closes it
            paletteOpen = false;
            return true;
        }

        // Check main swatch click
        if (isMouseOver((int) mouseX, (int) mouseY)) {
            paletteOpen = true;
            return true;
        }

        return false;
    }
}
```

## Preview nel 3D Renderer

Aggiungere preview dello scudo nell'editor quando si modificano i parametri:

```java
// PreviewRenderer.java - aggiungere

public void renderShieldPreview(PoseStack poseStack, MultiBufferSource bufferSource,
                                ArmorStats stats, float partialTick) {
    if (stats.shieldBlockStrength <= 0) return;

    // Get preview entity (usually armor stand or player model)
    LivingEntity previewEntity = getPreviewEntity();
    if (previewEntity == null) return;

    // Use the actual shield renderer
    EnergyShieldRenderer.renderShieldWithStats(
        poseStack,
        bufferSource,
        previewEntity,
        stats,
        partialTick
    );
}
```

## Presets per Shield

Aggiungere presets comuni in `PresetRegistry`:

```java
// PresetRegistry.java - aggiungere

public static void registerShieldPresets() {
    // Basic Energy Shield
    register("shield_basic", stats -> {
        stats.shieldBlockStrength = 0.5f;
        stats.shieldColor = 0x3D5AFE;
        stats.shieldOpacity = 0.6f;
        stats.shieldGlowEnabled = true;
    });

    // Fire Shield
    register("shield_fire", stats -> {
        stats.shieldBlockStrength = 0.6f;
        stats.shieldColor = 0xFF4500;
        stats.shieldOpacity = 0.7f;
        stats.shieldNoiseIntensity = 0.3f;
        stats.shieldPulseSpeed = 1.5f;
    });

    // Ice Shield
    register("shield_ice", stats -> {
        stats.shieldBlockStrength = 0.7f;
        stats.shieldColor = 0x00FFFF;
        stats.shieldOpacity = 0.5f;
        stats.shieldNoiseIntensity = 0.1f;
        stats.shieldPulseSpeed = 0.7f;
    });

    // Void Shield (dark)
    register("shield_void", stats -> {
        stats.shieldBlockStrength = 0.8f;
        stats.shieldColor = 0x4B0082;
        stats.shieldOpacity = 0.8f;
        stats.shieldGlowIntensity = 0.5f;
        stats.shieldDeflectToOwner = true;
    });

    // Reflector Shield
    register("shield_reflector", stats -> {
        stats.shieldReflectProjectiles = true;
        stats.shieldBlockStrength = 0.4f;
        stats.shieldDeflectionSpread = 0.05f;
        stats.shieldDeflectSpeedMult = 1.2f;
        stats.shieldColor = 0xC0C0C0;
    });
}
```

## Localizzazione

Aggiungere chiavi i18n per le nuove UI:

```json
// en_us.json
{
  "devmod.editor.armor.shield_visual": "Shield Visual",
  "devmod.editor.armor.shield_color": "Shield Color",
  "devmod.editor.armor.shield_opacity": "Opacity",
  "devmod.editor.armor.shield_glow": "Edge Glow",
  "devmod.editor.armor.shield_glow_intensity": "Glow Intensity",
  "devmod.editor.armor.shield_energy": "Energy Intensity",
  "devmod.editor.armor.shield_speed": "Animation Speed",

  "devmod.editor.armor.shield_deflection": "Shield Deflection",
  "devmod.editor.armor.deflect_spread": "Deflection Spread",
  "devmod.editor.armor.deflect_return": "Return to Sender",
  "devmod.editor.armor.deflect_speed": "Deflect Speed",

  "devmod.editor.armor.shield_shatter": "Shield Shatter",
  "devmod.editor.armor.shatter_threshold": "Shatter Threshold",
  "devmod.editor.armor.auto_regen": "Auto Regenerate",
  "devmod.editor.armor.regen_delay": "Regen Delay",

  "devmod.preset.shield_basic": "Basic Shield",
  "devmod.preset.shield_fire": "Fire Shield",
  "devmod.preset.shield_ice": "Ice Shield",
  "devmod.preset.shield_void": "Void Shield",
  "devmod.preset.shield_reflector": "Reflector Shield"
}
```

```json
// it_it.json
{
  "devmod.editor.armor.shield_visual": "Visuale Scudo",
  "devmod.editor.armor.shield_color": "Colore Scudo",
  "devmod.editor.armor.shield_opacity": "Opacità",
  "devmod.editor.armor.shield_glow": "Bagliore Bordi",
  "devmod.editor.armor.shield_glow_intensity": "Intensità Bagliore",
  "devmod.editor.armor.shield_energy": "Intensità Energia",
  "devmod.editor.armor.shield_speed": "Velocità Animazione",

  "devmod.editor.armor.shield_deflection": "Deflessione Scudo",
  "devmod.editor.armor.deflect_spread": "Dispersione Deflessione",
  "devmod.editor.armor.deflect_return": "Ritorno al Mittente",
  "devmod.editor.armor.deflect_speed": "Velocità Deflessione",

  "devmod.editor.armor.shield_shatter": "Frantumazione Scudo",
  "devmod.editor.armor.shatter_threshold": "Soglia Frantumazione",
  "devmod.editor.armor.auto_regen": "Rigenerazione Auto",
  "devmod.editor.armor.regen_delay": "Ritardo Rigenerazione",

  "devmod.preset.shield_basic": "Scudo Base",
  "devmod.preset.shield_fire": "Scudo di Fuoco",
  "devmod.preset.shield_ice": "Scudo di Ghiaccio",
  "devmod.preset.shield_void": "Scudo del Vuoto",
  "devmod.preset.shield_reflector": "Scudo Riflettore"
}
```
