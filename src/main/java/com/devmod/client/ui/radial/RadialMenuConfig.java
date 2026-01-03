package com.devmod.client.ui.radial;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.client.Minecraft;

import com.devmod.client.ui.radial.config.RadialMenuConstants;
import com.devmod.client.ui.radial.config.RadialMenuThemeDefaults;

public class RadialMenuConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(RadialMenuConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "config/devmod/radial_menu.json";
    private static final InputBindings DEFAULT_INPUT = new InputBindings();

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
    public float hoverInSpeed = RadialMenuConstants.HOVER_ANIM_IN;
    public float hoverOutSpeed = RadialMenuConstants.HOVER_ANIM_OUT;
    public float morphAnimationSpeed = RadialMenuConstants.MORPH_SPEED;
    public float searchBoxAnimationSpeed = RadialMenuConstants.SEARCH_BOX_LERP;

    // === ICON OPTIONS ===
    public IconMode iconMode = IconMode.ITEMSTACK;  // AUTO, EMOJI, ITEMSTACK

    // === INPUT BINDINGS ===
    public InputBindings input = new InputBindings();

    public enum IconMode {
        AUTO,       // Use ItemStack if available, else emoji
        EMOJI,      // Always use emoji
        ITEMSTACK   // Always use ItemStack
    }

    /**
     * Key bindings for radial menu shortcuts.
     */
    public static class InputBindings {
        public int keyMenuClose = GLFW.GLFW_KEY_ESCAPE;
        public int keyReleaseSelect = GLFW.GLFW_KEY_G;
        public int keySearchTogglePrimary = GLFW.GLFW_KEY_SLASH;
        public int keySearchToggleSecondary = GLFW.GLFW_KEY_F;
        public int keyEditModeToggleLeft = GLFW.GLFW_KEY_LEFT_SHIFT;
        public int keyEditModeToggleRight = GLFW.GLFW_KEY_RIGHT_SHIFT;
        public int keyThemeCycle = GLFW.GLFW_KEY_T;
        public int keyCategoryLeft = GLFW.GLFW_KEY_LEFT;
        public int keyCategoryRight = GLFW.GLFW_KEY_RIGHT;
        public int keySearchConfirm = GLFW.GLFW_KEY_ENTER;
        public int keySearchBackspace = GLFW.GLFW_KEY_BACKSPACE;
        public int keySearchUp = GLFW.GLFW_KEY_UP;
        public int keySearchDown = GLFW.GLFW_KEY_DOWN;

        public char searchQuerySpace = ' ';

        public int[] macroKeys = {
            GLFW.GLFW_KEY_1,
            GLFW.GLFW_KEY_2,
            GLFW.GLFW_KEY_3,
            GLFW.GLFW_KEY_4,
            GLFW.GLFW_KEY_5,
            GLFW.GLFW_KEY_6
        };

        public int[] categoryKeys = {
            GLFW.GLFW_KEY_7,
            GLFW.GLFW_KEY_8,
            GLFW.GLFW_KEY_9,
            GLFW.GLFW_KEY_0,
            GLFW.GLFW_KEY_MINUS,
            GLFW.GLFW_KEY_EQUAL
        };

        public int[] itemKeys = {
            GLFW.GLFW_KEY_Q,
            GLFW.GLFW_KEY_W,
            GLFW.GLFW_KEY_E,
            GLFW.GLFW_KEY_R,
            GLFW.GLFW_KEY_Y,
            GLFW.GLFW_KEY_U,
            GLFW.GLFW_KEY_I,
            GLFW.GLFW_KEY_O,
            GLFW.GLFW_KEY_P
        };
    }

    /**
     * Color theme configuration with preset support
     */
    public static class ColorTheme {
        public String presetName = "default";

        // Background colors
        public int bgDark = RadialMenuThemeDefaults.Base.BG_DARK;
        public int bgLight = RadialMenuThemeDefaults.Base.BG_LIGHT;

        // Selection colors
        public int selected = RadialMenuThemeDefaults.Base.SELECTED;
        public int hover = RadialMenuThemeDefaults.Base.HOVER;

        // Status colors
        public int active = RadialMenuThemeDefaults.Base.ACTIVE;
        public int activeGlow = RadialMenuThemeDefaults.Base.ACTIVE_GLOW;
        public int inactive = RadialMenuThemeDefaults.Base.INACTIVE;

        // Text colors
        public int textPrimary = RadialMenuThemeDefaults.Base.TEXT_PRIMARY;
        public int textSecondary = RadialMenuThemeDefaults.Base.TEXT_SECONDARY;
        public int textHighlight = RadialMenuThemeDefaults.Base.TEXT_HIGHLIGHT;

        // Border colors
        public int border = RadialMenuThemeDefaults.Base.BORDER;
        public int borderGlow = RadialMenuThemeDefaults.Base.BORDER_GLOW;

        // Category accent colors (per-category override)
        public int[] categoryColors = RadialMenuThemeDefaults.Base.categoryColors();

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
        DEFAULT("default", RadialMenuThemeDefaults.Presets.DEFAULT),
        NEON("neon", RadialMenuThemeDefaults.Presets.NEON),
        CRIMSON("crimson", RadialMenuThemeDefaults.Presets.CRIMSON),
        FOREST("forest", RadialMenuThemeDefaults.Presets.FOREST),
        GOLD("gold", RadialMenuThemeDefaults.Presets.GOLD),
        MIDNIGHT("midnight", RadialMenuThemeDefaults.Presets.MIDNIGHT),
        MINIMAL("minimal", RadialMenuThemeDefaults.Presets.MINIMAL);

        public final String name;
        public final int bgDark, bgLight, selected, hover;
        public final int active, activeGlow, border;
        public final int textPrimary, textSecondary;

        ThemePreset(String name, RadialMenuThemeDefaults.ThemePresetValues values) {
            this.name = name;
            this.bgDark = values.bgDark;
            this.bgLight = values.bgLight;
            this.selected = values.selected;
            this.hover = values.hover;
            this.active = values.active;
            this.activeGlow = values.activeGlow;
            this.border = values.border;
            this.textPrimary = values.textPrimary;
            this.textSecondary = values.textSecondary;
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
            LOGGER.warn("[RadialMenuConfig] Failed to load config: {}", e.getMessage());
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
            LOGGER.warn("[RadialMenuConfig] Failed to save config: {}", e.getMessage());
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
        this.hoverInSpeed = other.hoverInSpeed;
        this.hoverOutSpeed = other.hoverOutSpeed;
        this.morphAnimationSpeed = other.morphAnimationSpeed;
        this.searchBoxAnimationSpeed = other.searchBoxAnimationSpeed;
        this.iconMode = other.iconMode == IconMode.EMOJI ? IconMode.ITEMSTACK : other.iconMode;
        this.input = other.input != null ? other.input : new InputBindings();
        sanitizeInputBindings();
        save(); // persist sanitized binds so the JSON stays consistent
    }

    private void sanitizeInputBindings() {
        if (input == null) {
            input = new InputBindings();
            return;
        }

        input.keyMenuClose = sanitizeKey(input.keyMenuClose, DEFAULT_INPUT.keyMenuClose, "input.keyMenuClose");
        input.keyReleaseSelect = sanitizeKey(input.keyReleaseSelect, DEFAULT_INPUT.keyReleaseSelect,
            "input.keyReleaseSelect");
        input.keySearchTogglePrimary = sanitizeKey(input.keySearchTogglePrimary, DEFAULT_INPUT.keySearchTogglePrimary,
            "input.keySearchTogglePrimary");
        input.keySearchToggleSecondary = sanitizeKey(input.keySearchToggleSecondary, DEFAULT_INPUT.keySearchToggleSecondary,
            "input.keySearchToggleSecondary");
        input.keyEditModeToggleLeft = sanitizeKey(input.keyEditModeToggleLeft, DEFAULT_INPUT.keyEditModeToggleLeft,
            "input.keyEditModeToggleLeft");
        input.keyEditModeToggleRight = sanitizeKey(input.keyEditModeToggleRight, DEFAULT_INPUT.keyEditModeToggleRight,
            "input.keyEditModeToggleRight");
        input.keyThemeCycle = sanitizeKey(input.keyThemeCycle, DEFAULT_INPUT.keyThemeCycle, "input.keyThemeCycle");
        input.keyCategoryLeft = sanitizeKey(input.keyCategoryLeft, DEFAULT_INPUT.keyCategoryLeft, "input.keyCategoryLeft");
        input.keyCategoryRight = sanitizeKey(input.keyCategoryRight, DEFAULT_INPUT.keyCategoryRight, "input.keyCategoryRight");
        input.keySearchConfirm = sanitizeKey(input.keySearchConfirm, DEFAULT_INPUT.keySearchConfirm, "input.keySearchConfirm");
        input.keySearchBackspace = sanitizeKey(input.keySearchBackspace, DEFAULT_INPUT.keySearchBackspace,
            "input.keySearchBackspace");
        input.keySearchUp = sanitizeKey(input.keySearchUp, DEFAULT_INPUT.keySearchUp, "input.keySearchUp");
        input.keySearchDown = sanitizeKey(input.keySearchDown, DEFAULT_INPUT.keySearchDown, "input.keySearchDown");

        input.macroKeys = ensureKeyArray("input.macroKeys", input.macroKeys, DEFAULT_INPUT.macroKeys);
        input.categoryKeys = ensureKeyArray("input.categoryKeys", input.categoryKeys, DEFAULT_INPUT.categoryKeys);
        input.itemKeys = ensureKeyArray("input.itemKeys", input.itemKeys, DEFAULT_INPUT.itemKeys);

        if (input.searchQuerySpace == '\0') {
            input.searchQuerySpace = DEFAULT_INPUT.searchQuerySpace;
        }

        warnOnDuplicateKeys();
    }

    private int sanitizeKey(int value, int fallback, String name) {
        if (value <= 0) {
            LOGGER.warn("[RadialMenuConfig] Invalid key binding for {}, resetting to default", name);
            return fallback;
        }
        return value;
    }

    private void warnOnDuplicateKeys() {
        Map<Integer, List<String>> owners = new HashMap<>();
        addKeyOwner(owners, "input.keyMenuClose", input.keyMenuClose);
        addKeyOwner(owners, "input.keyReleaseSelect", input.keyReleaseSelect);
        addKeyOwner(owners, "input.keySearchTogglePrimary", input.keySearchTogglePrimary);
        addKeyOwner(owners, "input.keySearchToggleSecondary", input.keySearchToggleSecondary);
        addKeyOwner(owners, "input.keyEditModeToggleLeft", input.keyEditModeToggleLeft);
        addKeyOwner(owners, "input.keyEditModeToggleRight", input.keyEditModeToggleRight);
        addKeyOwner(owners, "input.keyThemeCycle", input.keyThemeCycle);
        addKeyOwner(owners, "input.keyCategoryLeft", input.keyCategoryLeft);
        addKeyOwner(owners, "input.keyCategoryRight", input.keyCategoryRight);
        addKeyOwner(owners, "input.keySearchConfirm", input.keySearchConfirm);
        addKeyOwner(owners, "input.keySearchBackspace", input.keySearchBackspace);
        addKeyOwner(owners, "input.keySearchUp", input.keySearchUp);
        addKeyOwner(owners, "input.keySearchDown", input.keySearchDown);
        addKeyArrayOwners(owners, "input.macroKeys", input.macroKeys);
        addKeyArrayOwners(owners, "input.categoryKeys", input.categoryKeys);
        addKeyArrayOwners(owners, "input.itemKeys", input.itemKeys);

        for (Map.Entry<Integer, List<String>> entry : owners.entrySet()) {
            List<String> values = entry.getValue();
            if (values.size() > 1) {
                LOGGER.warn("[RadialMenuConfig] Key binding conflict for key {} assigned to: {}",
                    entry.getKey(), String.join(", ", values));
            }
        }
    }

    private static void addKeyOwner(Map<Integer, List<String>> owners, String name, int key) {
        if (key <= 0) {
            return;
        }
        owners.computeIfAbsent(key, k -> new ArrayList<>()).add(name);
    }

    private static void addKeyArrayOwners(Map<Integer, List<String>> owners, String name, int[] keys) {
        for (int i = 0; i < keys.length; i++) {
            int key = keys[i];
            if (key <= 0) {
                continue;
            }
            owners.computeIfAbsent(key, k -> new ArrayList<>()).add(name + "[" + i + "]");
        }
    }

    private static int[] ensureKeyArray(String name, int[] candidate, int[] fallback) {
        if (candidate == null || candidate.length == 0) {
            return fallback.clone();
        }

        if (candidate.length != fallback.length) {
            LOGGER.warn("[RadialMenuConfig] Key binding array {} has length {} (expected {}). Truncating/expanding with defaults",
                name, candidate.length, fallback.length);
        }

        int[] normalized = new int[fallback.length];
        for (int i = 0; i < normalized.length; i++) {
            int value = (i < candidate.length) ? candidate[i] : 0;
            if (value <= 0) {
                LOGGER.warn("[RadialMenuConfig] Invalid key binding for {}[{}], resetting to default", name, i);
                normalized[i] = fallback[i];
            } else {
                normalized[i] = value;
            }
        }
        return normalized;
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
