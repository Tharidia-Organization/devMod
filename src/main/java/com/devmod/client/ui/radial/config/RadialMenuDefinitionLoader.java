package com.devmod.client.ui.radial.config;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.devmod.client.ui.radial.RadialCategory;
import com.devmod.client.ui.radial.RadialMenuActionLayout;
import com.devmod.client.ui.radial.RadialMenuItem;
import com.devmod.client.ui.radial.RadialMenuRegistry;
import com.devmod.client.ui.radial.config.RadialMenuDefinitionConfig.CategoryConfig;
import com.devmod.client.ui.radial.config.RadialMenuDefinitionConfig.MacroCategoryConfig;
import com.devmod.client.ui.radial.config.RadialMenuDefinitionConfig.MenuItemConfig;
import com.devmod.client.ui.radial.config.RadialMenuDefinitionConfig.RootConfig;
import com.devmod.client.ui.radial.config.RadialMenuDefinitionConfig.SubcategoryConfig;
import com.devmod.client.ui.radial.config.RadialMenuDefinitionConfig.ValidationResult;
import com.devmod.client.ui.radial.model.MacroCategory;

/**
 * Loads radial menu definitions from JSON configuration.
 * Bridges static JSON config with dynamic visibility suppliers.
 *
 * <p>Loading priority:
 * <ol>
 *   <li>External config file (config/devmod/radial_menu_definitions.json)</li>
 *   <li>Fallback to embedded defaults (RadialMenuRegistry.createDefaultCategories)</li>
 * </ol>
 * </p>
 */
public final class RadialMenuDefinitionLoader {

    private static final Logger LOGGER = LogManager.getLogger(RadialMenuDefinitionLoader.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_PATH = "config/devmod/radial_menu_definitions.json";

    private RadialMenuDefinitionLoader() {} // Utility class

    /**
     * Load radial menu definitions from config or fall back to defaults.
     *
     * @param mobEditorItemSupplier Supplier for mob editor item (may be null)
     * @return Map of macro category to list of categories
     */
    public static Map<MacroCategory, List<RadialCategory>> load(
            @Nullable java.util.function.Supplier<RadialMenuItem> mobEditorItemSupplier) {

        Path configFile = getConfigPath();

        if (!Files.exists(configFile)) {
            LOGGER.info("[RadialMenuLoader] No config file found, using defaults");
            return loadEmbeddedDefaults(mobEditorItemSupplier);
        }

        try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            RootConfig config = GSON.fromJson(reader, RootConfig.class);

            // Validate
            ValidationResult validation = RadialMenuConfigValidator.validate(config);
            if (!validation.valid()) {
                LOGGER.error("[RadialMenuLoader] Config validation failed:");
                validation.errors().forEach(e -> LOGGER.error("  - {}", e));
                return loadEmbeddedDefaults(mobEditorItemSupplier);
            }

            // Log warnings
            validation.warnings().forEach(w -> LOGGER.warn("[RadialMenuLoader] {}", w));

            // Build categories from config
            Map<MacroCategory, List<RadialCategory>> result = buildFromConfig(config, mobEditorItemSupplier);
            RadialMenuActionLayout.applyDefaultOrdering(result);
            LOGGER.info("[RadialMenuLoader] Loaded {} macro categories from config", result.size());

            return result;

        } catch (Exception e) {
            LOGGER.error("[RadialMenuLoader] Failed to load config: {}", e.getMessage());
            return loadEmbeddedDefaults(mobEditorItemSupplier);
        }
    }

    /**
     * Get the path to the config file.
     */
    private static Path getConfigPath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(CONFIG_PATH);
    }

    /**
     * Build categories from loaded config.
     */
    private static Map<MacroCategory, List<RadialCategory>> buildFromConfig(
            RootConfig config,
            @Nullable java.util.function.Supplier<RadialMenuItem> mobEditorItemSupplier) {

        Map<MacroCategory, List<RadialCategory>> result = new EnumMap<>(MacroCategory.class);

        // Initialize empty lists for all macros
        for (MacroCategory macro : MacroCategory.values()) {
            result.put(macro, new ArrayList<>());
        }

        // Build categories from config
        for (MacroCategoryConfig macroCfg : config.macroCategories()) {
            try {
                MacroCategory macro = MacroCategory.valueOf(macroCfg.id());
                List<RadialCategory> categories = Objects.requireNonNull(
                    result.get(macro),
                    "Missing categories for macro " + macro
                );

                for (CategoryConfig catCfg : macroCfg.categories()) {
                    RadialCategory category = buildCategory(catCfg, mobEditorItemSupplier);
                    if (category != null) {
                        categories.add(category);
                    }
                }
            } catch (IllegalArgumentException e) {
                LOGGER.warn("[RadialMenuLoader] Unknown macro category: {}", macroCfg.id());
            }
        }

        return result;
    }

    /**
     * Build a category from config.
     */
    @Nullable
    private static RadialCategory buildCategory(
            CategoryConfig cfg,
            @Nullable java.util.function.Supplier<RadialMenuItem> mobEditorItemSupplier) {

        int color = resolveColor(cfg.color(), cfg.colorToken());
        ItemStack iconStack = resolveIconItem(cfg.iconItem());

        RadialCategory.Builder builder = RadialCategory.builder(cfg.id())
            .name(cfg.name() != null ? cfg.name() : cfg.id())
            .color(color)
            .icon(cfg.icon() != null ? cfg.icon() : "");

        if (iconStack != null && !iconStack.isEmpty()) {
            builder.iconStack(iconStack);
        }

        // Add items
        for (MenuItemConfig itemCfg : cfg.items()) {
            RadialMenuItem item = buildMenuItem(itemCfg, mobEditorItemSupplier);
            if (item != null) {
                builder.item(item);
            }
        }

        RadialCategory category = builder.build();

        // Add subcategories
        List<SubcategoryConfig> subcats = cfg.subcategories();
        if (subcats != null) {
            for (SubcategoryConfig subCfg : subcats) {
                buildSubcategory(category, subCfg, mobEditorItemSupplier);
            }
        }

        return category;
    }

    /**
     * Build a subcategory and add it to parent.
     */
    private static void buildSubcategory(
            RadialCategory parent,
            SubcategoryConfig cfg,
            @Nullable java.util.function.Supplier<RadialMenuItem> mobEditorItemSupplier) {

        int color = resolveColor(cfg.color(), cfg.colorToken());
        ItemStack iconStack = resolveIconItem(cfg.iconItem());
        String icon = cfg.icon() != null ? cfg.icon() : "";

        RadialCategory sub = iconStack != null && !iconStack.isEmpty()
            ? parent.addSubcategory(cfg.id(), cfg.name() != null ? cfg.name() : cfg.id(), color, iconStack)
            : parent.addSubcategory(cfg.id(), cfg.name() != null ? cfg.name() : cfg.id(), color, icon);

        // Add items
        for (MenuItemConfig itemCfg : cfg.items()) {
            RadialMenuItem item = buildMenuItem(itemCfg, mobEditorItemSupplier);
            if (item != null) {
                sub.addItem(item);
            }
        }

        // Recursive subcategories
        List<SubcategoryConfig> nestedSubs = cfg.subcategories();
        if (nestedSubs != null) {
            for (SubcategoryConfig nestedCfg : nestedSubs) {
                buildSubcategory(sub, nestedCfg, mobEditorItemSupplier);
            }
        }
    }

    /**
     * Build a menu item from config.
     */
    @Nullable
    private static RadialMenuItem buildMenuItem(
            MenuItemConfig cfg,
            @Nullable java.util.function.Supplier<RadialMenuItem> mobEditorItemSupplier) {

        // Special case: mob editor item needs context supplier
        if ("MOB_EDITOR_SUPPLIER".equals(cfg.actionId()) && mobEditorItemSupplier != null) {
            return mobEditorItemSupplier.get();
        }

        RadialMenuItem item = RadialMenuItem.registry(cfg.actionId());
        if (item == null) {
            LOGGER.warn("[RadialMenuLoader] Unknown action ID: {}", cfg.actionId());
            return null;
        }

        // Apply visibility supplier if specified
        if (cfg.visibilitySupplier() != null) {
            BooleanSupplier supplier = VisibilitySupplierRegistry.get(cfg.visibilitySupplier());
            if (supplier != null) {
                item.setVisibilitySupplier(supplier);
            } else {
                LOGGER.warn("[RadialMenuLoader] Unknown visibility supplier: {}", cfg.visibilitySupplier());
            }
        }

        // Apply custom color if specified
        if (cfg.customColor() != null) {
            int color = ColorTokenResolver.resolveColorOrToken(cfg.customColor());
            item.setCustomColor(color);
        }

        return item;
    }

    /**
     * Resolve color from hex string or token name.
     */
    private static int resolveColor(@Nullable String hexColor, @Nullable String tokenRef) {
        if (tokenRef != null && !tokenRef.isEmpty()) {
            return ColorTokenResolver.resolve(tokenRef);
        }
        if (hexColor != null && !hexColor.isEmpty()) {
            return ColorTokenResolver.resolveColorOrToken(hexColor);
        }
        return ColorTokenResolver.DEFAULT_COLOR;
    }

    /**
     * Resolve an item ID to an ItemStack.
     */
    @Nullable
    private static ItemStack resolveIconItem(@Nullable String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return null;
        }

        ResourceLocation loc = ResourceLocation.tryParse(itemId);
        if (loc == null) {
            LOGGER.warn("[RadialMenuLoader] Invalid item ID: {}", itemId);
            return null;
        }

        var item = BuiltInRegistries.ITEM.get(loc);
        if (item == Items.AIR) {
            LOGGER.warn("[RadialMenuLoader] Unknown item: {}", itemId);
            return null;
        }

        return new ItemStack(item);
    }

    /**
     * Load embedded defaults (fallback).
     */
    private static Map<MacroCategory, List<RadialCategory>> loadEmbeddedDefaults(
            @Nullable java.util.function.Supplier<RadialMenuItem> mobEditorItemSupplier) {
        LOGGER.info("[RadialMenuLoader] Using embedded default categories");
        Map<MacroCategory, List<RadialCategory>> layout = RadialMenuActionLayout.build(mobEditorItemSupplier);
        if (hasAnyCategories(layout)) {
            LOGGER.info("[RadialMenuLoader] Built defaults from action menuPath metadata");
            return layout;
        }
        LOGGER.warn("[RadialMenuLoader] Action layout empty, using legacy defaults");
        return RadialMenuRegistry.createDefaultCategories(mobEditorItemSupplier);
    }

    private static boolean hasAnyCategories(Map<MacroCategory, List<RadialCategory>> layout) {
        for (List<RadialCategory> categories : layout.values()) {
            if (categories != null && !categories.isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
