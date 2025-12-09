package com.frenkvs.devmod.ui.radial;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuration system for the Radial Menu.
 * Supports customizable colors, behavior options, and theme presets.
 */
public class RadialMenuConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "config/devmod/radial_menu.json";

    public static final RadialMenuConfig INSTANCE = new RadialMenuConfig();

    // === BEHAVIOR OPTIONS ===
    public boolean releaseToSelect = true;          // Release key to activate selection
    public boolean rightClickToEdit = true;         // Right-click opens edit mode
    public boolean enableAnimations = true;         // Enable smooth animations
    public boolean enableSounds = true;             // Enable feedback sounds
    public boolean showTooltips = true;             // Show item descriptions
    public boolean closeOnToggle = false;           // Close menu after toggling item
    public boolean showKeyHints = true;             // Show keyboard shortcuts

    // === COLOR THEME ===
    public ColorTheme theme = new ColorTheme();

    // === LAYOUT OPTIONS ===
    public int innerRadius = 55;
    public int outerRadius = 130;
    public int itemRadius = 95;
    public int centerButtonRadius = 40;
    public float openAnimationSpeed = 0.18f;
    public float closeAnimationSpeed = 0.25f;

    // === ICON OPTIONS ===
    public IconMode iconMode = IconMode.AUTO;  // AUTO, EMOJI, ITEMSTACK

    public enum IconMode {
        AUTO,       // Use ItemStack if available, else emoji
        EMOJI,      // Always use emoji
        ITEMSTACK   // Always use ItemStack
    }

    /**
     * Color theme configuration with preset support
     */
    public static class ColorTheme {
        public String presetName = "default";

        // Background colors
        public int bgDark = 0xE6101020;
        public int bgLight = 0xCC1a1a35;

        // Selection colors
        public int selected = 0xDD2a2a55;
        public int hover = 0xEE353566;

        // Status colors
        public int active = 0xFF00FF88;
        public int activeGlow = 0x4400FF88;
        public int inactive = 0xFFAAAAAA;

        // Text colors
        public int textPrimary = 0xFFFFFFFF;
        public int textSecondary = 0xFFAAAAAA;
        public int textHighlight = 0xFF88CCFF;

        // Border colors
        public int border = 0xFF505080;
        public int borderGlow = 0x40FFFFFF;

        // Category accent colors (per-category override)
        public int[] categoryColors = {
            0xFF00DDFF,  // Debug - Cyan
            0xFFFFDD00,  // Spatial - Yellow
            0xFF00FF88,  // Perf - Green
            0xFFFF4466,  // Combat - Red
            0xFFFF9900,  // Tools - Orange
            0xFFCC44FF   // Quest - Purple
        };

        /**
         * Apply a preset theme
         */
        public void applyPreset(ThemePreset preset) {
            this.presetName = preset.name;
            this.bgDark = preset.bgDark;
            this.bgLight = preset.bgLight;
            this.selected = preset.selected;
            this.hover = preset.hover;
            this.active = preset.active;
            this.activeGlow = preset.activeGlow;
            this.border = preset.border;
            this.textPrimary = preset.textPrimary;
            this.textSecondary = preset.textSecondary;
        }
    }

    /**
     * Predefined theme presets
     */
    public enum ThemePreset {
        DEFAULT("default",
            0xE6101020, 0xCC1a1a35, 0xDD2a2a55, 0xEE353566,
            0xFF00FF88, 0x4400FF88, 0xFF505080, 0xFFFFFFFF, 0xFFAAAAAA),

        NEON("neon",
            0xE6000510, 0xCC0a0a20, 0xDD1a1a40, 0xEE2525aa,
            0xFF00FFFF, 0x4400FFFF, 0xFF0088FF, 0xFFFFFFFF, 0xFF88FFFF),

        CRIMSON("crimson",
            0xE6200808, 0xCC351010, 0xDD552020, 0xEE663030,
            0xFFFF4444, 0x44FF4444, 0xFFFF6666, 0xFFFFFFFF, 0xFFFFAAAA),

        FOREST("forest",
            0xE6081808, 0xCC103510, 0xDD205520, 0xEE306630,
            0xFF44FF44, 0x4444FF44, 0xFF66FF66, 0xFFFFFFFF, 0xFFAAFFAA),

        GOLD("gold",
            0xE6181408, 0xCC352810, 0xDD554420, 0xEE665530,
            0xFFFFCC00, 0x44FFCC00, 0xFFFFDD44, 0xFFFFFFFF, 0xFFFFEEAA),

        MIDNIGHT("midnight",
            0xF0050510, 0xDD080820, 0xCC151540, 0xBB202060,
            0xFF6666FF, 0x446666FF, 0xFF4444AA, 0xFFCCCCFF, 0xFF8888CC),

        MINIMAL("minimal",
            0xE6181818, 0xCC282828, 0xDD383838, 0xEE484848,
            0xFFFFFFFF, 0x44FFFFFF, 0xFF606060, 0xFFFFFFFF, 0xFFAAAAAA);

        public final String name;
        public final int bgDark, bgLight, selected, hover;
        public final int active, activeGlow, border;
        public final int textPrimary, textSecondary;

        ThemePreset(String name, int bgDark, int bgLight, int selected, int hover,
                    int active, int activeGlow, int border, int textPrimary, int textSecondary) {
            this.name = name;
            this.bgDark = bgDark;
            this.bgLight = bgLight;
            this.selected = selected;
            this.hover = hover;
            this.active = active;
            this.activeGlow = activeGlow;
            this.border = border;
            this.textPrimary = textPrimary;
            this.textSecondary = textSecondary;
        }

        public static ThemePreset fromName(String name) {
            for (ThemePreset preset : values()) {
                if (preset.name.equalsIgnoreCase(name)) {
                    return preset;
                }
            }
            return DEFAULT;
        }
    }

    /**
     * Load configuration from file
     */
    public void load() {
        try {
            Path configPath = getConfigPath();
            if (Files.exists(configPath)) {
                try (Reader reader = Files.newBufferedReader(configPath)) {
                    RadialMenuConfig loaded = GSON.fromJson(reader, RadialMenuConfig.class);
                    if (loaded != null) {
                        copyFrom(loaded);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[DevMod] Failed to load radial menu config: " + e.getMessage());
        }
    }

    /**
     * Save configuration to file
     */
    public void save() {
        try {
            Path configPath = getConfigPath();
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            System.err.println("[DevMod] Failed to save radial menu config: " + e.getMessage());
        }
    }

    private Path getConfigPath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(CONFIG_FILE);
    }

    private void copyFrom(RadialMenuConfig other) {
        this.releaseToSelect = other.releaseToSelect;
        this.rightClickToEdit = other.rightClickToEdit;
        this.enableAnimations = other.enableAnimations;
        this.enableSounds = other.enableSounds;
        this.showTooltips = other.showTooltips;
        this.closeOnToggle = other.closeOnToggle;
        this.showKeyHints = other.showKeyHints;
        this.theme = other.theme;
        this.innerRadius = other.innerRadius;
        this.outerRadius = other.outerRadius;
        this.itemRadius = other.itemRadius;
        this.centerButtonRadius = other.centerButtonRadius;
        this.openAnimationSpeed = other.openAnimationSpeed;
        this.closeAnimationSpeed = other.closeAnimationSpeed;
        this.iconMode = other.iconMode;
    }

    /**
     * Apply a theme preset by name
     */
    public void setTheme(String presetName) {
        theme.applyPreset(ThemePreset.fromName(presetName));
        save();
    }

    /**
     * Cycle to the next theme preset
     */
    public void cycleTheme() {
        ThemePreset[] presets = ThemePreset.values();
        ThemePreset current = ThemePreset.fromName(theme.presetName);
        int nextIndex = (current.ordinal() + 1) % presets.length;
        theme.applyPreset(presets[nextIndex]);
        save();
    }

    /**
     * Get category color with fallback
     */
    public int getCategoryColor(int index) {
        if (index >= 0 && index < theme.categoryColors.length) {
            return theme.categoryColors[index];
        }
        return theme.active;
    }
}
