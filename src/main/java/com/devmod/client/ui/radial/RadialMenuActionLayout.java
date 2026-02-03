package com.devmod.client.ui.radial;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.RadialAction;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.radial.config.VisibilitySupplierRegistry;
import com.devmod.client.ui.radial.model.MacroCategory;

/**
 * Builds radial categories from ActionRegistry menuPath metadata.
 * This keeps the UI aligned with action definitions across the project.
 */
public final class RadialMenuActionLayout {
    private static final int MAX_SUBCATEGORY_DEPTH = 2;

    private static final Set<String> EXCLUDED_ACTIONS = Set.of(
        ActionIds.UI_RADIAL_OPEN
    );

    // Curated radial menu: mirrors docs/radial/RADIAL_MENU_MAP_2026-01-31.md.
    private static final Set<String> CURATED_ACTIONS = Set.of(
        // ANALYZE - Debug/Overlays
        ActionIds.DEBUG_OVERLAY_TOGGLE,
        ActionIds.DEBUG_BODY_PARTS_TOGGLE,
        ActionIds.DEBUG_OVERLAYS_ENABLE_ALL,
        ActionIds.DEBUG_OVERLAYS_DISABLE_ALL,
        // ANALYZE - Debug/AI
        ActionIds.DEBUG_PATHFINDING_TOGGLE,
        ActionIds.DEBUG_LOS_TOGGLE,
        ActionIds.DEBUG_AGGRO_RANGE_TOGGLE,
        // ANALYZE - Debug/Light
        ActionIds.DEBUG_LIGHT_OVERLAY_TOGGLE,
        // ANALYZE - Debug/Native
        ActionIds.DEBUG_NATIVE_ENTITY_PATHING_TOGGLE,
        ActionIds.DEBUG_NATIVE_ENTITY_GOALS_TOGGLE,
        ActionIds.DEBUG_NATIVE_ENTITY_BRAINS_TOGGLE,
        ActionIds.DEBUG_NATIVE_POI_TOGGLE,
        ActionIds.DEBUG_NATIVE_RAIDS_TOGGLE,
        ActionIds.DEBUG_NATIVE_BEES_TOGGLE,
        ActionIds.DEBUG_NATIVE_GAME_EVENTS_TOGGLE,
        ActionIds.DEBUG_NATIVE_STRUCTURES_TOGGLE,
        // ANALYZE - Debug/Context
        ActionIds.DEBUG_COMBAT_RESET,
        ActionIds.DEBUG_CONTEXT_STATUS,
        // ANALYZE - Spatial
        ActionIds.DEBUG_ROOM_BOUNDS_TOGGLE,
        ActionIds.DEBUG_ROOM_BOUNDS_GAPS_TOGGLE,
        ActionIds.DEBUG_VERTICAL_LEVELS_TOGGLE,
        ActionIds.DEBUG_SAFE_SPOTS_TOGGLE,
        ActionIds.DEBUG_SPAWNABILITY_TOGGLE,
        // ANALYZE - Performance
        ActionIds.DEBUG_FPS_TRACKER_TOGGLE,
        ActionIds.DEBUG_PROFILER_TOGGLE,
        ActionIds.DEBUG_ENTITY_DENSITY_TOGGLE,
        ActionIds.DEBUG_CHUNK_PERF_TOGGLE,
        ActionIds.DEBUG_ATTRIBUTE_MONITOR_TOGGLE,
        // COMBAT - Diagnostics
        ActionIds.DEBUG_BOSS_PHASE_TOGGLE,
        ActionIds.DEBUG_SKILL_EFFICACY_TOGGLE,
        ActionIds.DEBUG_ECONOMY_TOGGLE,
        ActionIds.DEBUG_ECONOMY_VIEW_CYCLE,
        ActionIds.DEBUG_ECONOMY_SORT_CYCLE,
        // COMBAT - Heatmaps
        ActionIds.DEBUG_HEATMAP_TOGGLE,
        ActionIds.DEBUG_HEATMAP_CYCLE,
        ActionIds.DEBUG_HEATMAP_DEATH_TOGGLE,
        ActionIds.DEBUG_HEATMAP_MOVEMENT_TOGGLE,
        ActionIds.DEBUG_HEATMAP_CAMPING_TOGGLE,
        ActionIds.DEBUG_HEATMAP_STUCK_TOGGLE,
        ActionIds.DEBUG_HEATMAP_AGGRO_DROP_TOGGLE,
        ActionIds.DEBUG_HEATMAP_KITING_TOGGLE,
        ActionIds.DEBUG_HEATMAP_LIGHT_SPAWNABLE_TOGGLE,
        ActionIds.DEBUG_HEATMAP_LIGHT_DARK_TOGGLE,
        ActionIds.DEBUG_HEATMAP_CLEAR_CURRENT,
        ActionIds.DEBUG_HEATMAP_CLEAR_ALL,
        // PLAY - HUD
        ActionIds.HUD_IMPACT_TOGGLE,
        ActionIds.HUD_IMPACT_PRESET_DETAILED,
        ActionIds.HUD_IMPACT_PRESET_MINIMAL,
        ActionIds.HUD_IMPACT_PRESET_TRAINING,
        ActionIds.HUD_IMPACT_DISPLAY_MODE_CYCLE,
        ActionIds.HUD_IMPACT_3D_TOGGLE,
        ActionIds.HUD_IMPACT_CONTROLLER_TOGGLE,
        ActionIds.HUD_IMPACT_SHOW_RECAP,
        ActionIds.HUD_ENDURANCE_TOGGLE,
        ActionIds.HUD_QUEST_TOGGLE,
        ActionIds.HUD_ENDURANCE_DETAILS_TOGGLE,
        ActionIds.HUD_PARTY_TOGGLE,
        ActionIds.ARENA_HUD_TOGGLE,
        ActionIds.HUD_QUICK_HELP_TOGGLE,
        // PLAY - Entry points
        ActionIds.UI_ENDURANCE_SCREEN_OPEN,
        ActionIds.UI_PARTY_OPEN,
        // TELEMETRY
        ActionIds.UI_TELEMETRY_DASHBOARD_OPEN,
        // TOOLS
        ActionIds.UI_EDITOR_HUB_OPEN,
        ActionIds.UI_TESTING_HUB_OPEN,
        ActionIds.UI_VOXELLAB_OPEN,
        ActionIds.UI_SETTINGS_OPEN,
        ActionIds.UI_KEYBINDS_OPEN,
        ActionIds.UI_RADIAL_SETTINGS_OPEN
    );

    private static final Map<String, String> ACTION_MENU_OVERRIDES = Map.ofEntries(
        Map.entry(ActionIds.UI_SETTINGS_OPEN, "Root/Settings"),
        Map.entry(ActionIds.UI_TELEMETRY_DASHBOARD_OPEN, "Root/Telemetry/Dashboard"),
        Map.entry(ActionIds.UI_TESTING_HUB_OPEN, "Root/Testing"),
        Map.entry(ActionIds.UI_QA_TESTING_OPEN, "Root/Testing/QA Suite"),
        Map.entry(ActionIds.UI_EDITOR_HUB_OPEN, "Root/Editors"),
        Map.entry(ActionIds.UI_NOTIFICATION_CENTER_OPEN, "Root/Comms"),
        Map.entry(ActionIds.UI_PLAY_HUB_OPEN, "Root/Play"),
        Map.entry(ActionIds.UI_ARENA_HUB_OPEN, "Root/Arena"),
        Map.entry(ActionIds.UI_NEXUS_HUB_OPEN, "Root/Nexus"),
        Map.entry(ActionIds.UI_VOXELLAB_OPEN, "Root/Labs/VoxelLab")
    );

    private static final Map<String, String> ACTION_VISIBILITY_SUPPLIERS = Map.ofEntries(
        Map.entry(ActionIds.UI_ITEM_EDITOR_OPEN_WEAPON, "isWeaponItem"),
        Map.entry(ActionIds.UI_ITEM_EDITOR_OPEN_ARMOR, "isArmorItem"),
        Map.entry(ActionIds.UI_ITEM_EDITOR_OPEN_SHIELD, "isShieldItem"),
        Map.entry(ActionIds.UI_ITEM_EDITOR_OPEN_GENERAL, "isGeneralItem"),
        Map.entry(ActionIds.UI_ITEM_EDITOR_OPEN_FOOD, "isFoodItem"),
        Map.entry(ActionIds.UI_ITEM_EDITOR_OPEN_FUEL, "isFuelItem"),
        Map.entry(ActionIds.UI_ITEM_EDITOR_OPEN_USABLE, "isUsableItem")
    );

    private static final Map<String, Integer> ACTION_CUSTOM_COLORS = Map.of(
        ActionIds.UI_ITEM_EDITOR_OPEN_SHIELD, DesignTokens.Radial.COMBAT_SHIELD
    );

    private static final Map<MacroCategory, List<String>> CATEGORY_ORDER = Map.of(
        MacroCategory.ANALYZE, List.of("Debug", "Spatial", "Performance"),
        MacroCategory.TELEMETRY, List.of("Dashboard", "Server Ops", "Exports", "Data Dumps", "Analysis", "Dungeon"),
        MacroCategory.COMBAT, List.of("Diagnostics", "Heatmaps"),
        MacroCategory.ARENA, List.of("Arena", "Ops", "Templates", "Force", "Autosmoke", "HUD", "Bounds"),
        MacroCategory.PLAY, List.of("Play", "HUD", "Endurance", "Party"),
        MacroCategory.TOOLS, List.of("Editors", "Labs", "Settings", "Testing", "Comms", "Nexus")
    );

    private RadialMenuActionLayout() {}

    public static Map<MacroCategory, List<RadialCategory>> build(@Nullable Supplier<RadialMenuItem> mobEditorSupplier) {
        Map<MacroCategory, List<RadialCategory>> result = initMap();

        Collection<RadialAction> actions = ActionRegistry.listActions();
        if (actions.isEmpty()) {
            return result;
        }

        List<RadialAction> sortedActions = new ArrayList<>(actions);
        sortedActions.sort(Comparator
            .comparing(RadialMenuActionLayout::safeMenuPath, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(RadialAction::getId));

        Map<MacroCategory, Map<String, RadialCategory>> categoryIndex = new EnumMap<>(MacroCategory.class);
        Map<MacroCategory, Map<String, RadialCategory>> subcategoryIndex = new EnumMap<>(MacroCategory.class);
        for (MacroCategory macro : MacroCategory.values()) {
            categoryIndex.put(macro, new LinkedHashMap<>());
            subcategoryIndex.put(macro, new HashMap<>());
        }

        for (RadialAction action : sortedActions) {
            if (action == null) {
                continue;
            }
            String actionId = action.getId();
            if (actionId == null || EXCLUDED_ACTIONS.contains(actionId)) {
                continue;
            }
            if (!CURATED_ACTIONS.contains(actionId)) {
                continue;
            }

            MenuTarget target = resolveTarget(action);
            // Maps are pre-populated for all MacroCategory values, so get() is never null
            RadialCategory category = ensureCategory(
                Objects.requireNonNull(result.get(target.macro), "result category list"),
                Objects.requireNonNull(categoryIndex.get(target.macro), "category index"),
                target);

            RadialCategory insertTarget = category;
            if (!target.subcategories.isEmpty()) {
                insertTarget = ensureSubcategories(
                    Objects.requireNonNull(subcategoryIndex.get(target.macro), "subcategory index"),
                    category, target);
            }

            RadialMenuItem item = buildItem(actionId, mobEditorSupplier);
            if (item == null) {
                continue;
            }
            insertTarget.addItem(item);
        }

        applyDefaultOrdering(result);
        return result;
    }

    public static void applyDefaultOrdering(Map<MacroCategory, List<RadialCategory>> result) {
        if (result == null || result.isEmpty()) {
            return;
        }
        sortCategories(result);
        reorderByUsage(result);
    }

    private static Map<MacroCategory, List<RadialCategory>> initMap() {
        Map<MacroCategory, List<RadialCategory>> result = new EnumMap<>(MacroCategory.class);
        for (MacroCategory macro : MacroCategory.values()) {
            result.put(macro, new ArrayList<>());
        }
        return result;
    }

    private static void sortCategories(Map<MacroCategory, List<RadialCategory>> result) {
        for (var entry : result.entrySet()) {
            MacroCategory macro = entry.getKey();
            List<RadialCategory> categories = entry.getValue();
            List<String> order = CATEGORY_ORDER.getOrDefault(macro, List.of());
            categories.sort((a, b) -> {
                int aIdx = indexOf(order, a.getName());
                int bIdx = indexOf(order, b.getName());
                if (aIdx != bIdx) {
                    return Integer.compare(aIdx, bIdx);
                }
                return a.getName().compareToIgnoreCase(b.getName());
            });
        }
    }

    private static void reorderByUsage(Map<MacroCategory, List<RadialCategory>> result) {
        reorderHudCategory(result);
        reorderAnalyzeCategories(result);
        reorderCombatCategories(result);
        reorderTelemetryCategories(result);
        reorderToolsCategories(result);
    }

    private static void reorderHudCategory(Map<MacroCategory, List<RadialCategory>> result) {
        RadialCategory hudCategory = findCategory(result, MacroCategory.PLAY, "HUD");
        if (hudCategory == null) {
            return;
        }

        reorderCategoryItems(hudCategory, List.of(
            "sub:Impact HUD",
            "sub:Endurance HUD",
            "sub:Party HUD",
            "sub:Arena HUD",
            "sub:Help"
        ));

        for (RadialMenuItem item : new ArrayList<>(hudCategory.getItems())) {
            if (!item.isSubcategoryLink()) {
                continue;
            }
            RadialCategory sub = item.getLinkedSubcategory();
            if (sub == null) {
                continue;
            }
            switch (sub.getName()) {
                case "Impact HUD" -> {
                    reorderCategoryItems(sub, List.of(
                        ActionIds.HUD_IMPACT_TOGGLE,
                        "sub:Presets",
                        ActionIds.HUD_IMPACT_DISPLAY_MODE_CYCLE,
                        ActionIds.HUD_IMPACT_3D_TOGGLE,
                        ActionIds.HUD_IMPACT_CONTROLLER_TOGGLE,
                        ActionIds.HUD_IMPACT_SHOW_RECAP
                    ));
                    reorderSubcategoryByAction(sub, "Presets", List.of(
                        ActionIds.HUD_IMPACT_PRESET_DETAILED,
                        ActionIds.HUD_IMPACT_PRESET_MINIMAL,
                        ActionIds.HUD_IMPACT_PRESET_TRAINING
                    ));
                }
                case "Endurance HUD" -> reorderCategoryItems(sub, List.of(
                    ActionIds.HUD_ENDURANCE_TOGGLE,
                    ActionIds.HUD_QUEST_TOGGLE,
                    ActionIds.HUD_ENDURANCE_DETAILS_TOGGLE
                ));
                case "Party HUD" -> reorderCategoryItems(sub, List.of(
                    ActionIds.HUD_PARTY_TOGGLE
                ));
                case "Arena HUD" -> reorderCategoryItems(sub, List.of(
                    ActionIds.ARENA_HUD_TOGGLE
                ));
                case "Help" -> reorderCategoryItems(sub, List.of(
                    ActionIds.HUD_QUICK_HELP_TOGGLE
                ));
                default -> { }
            }
        }
    }

    private static void reorderAnalyzeCategories(Map<MacroCategory, List<RadialCategory>> result) {
        RadialCategory debug = findCategory(result, MacroCategory.ANALYZE, "Debug");
        if (debug != null) {
            reorderCategoryItems(debug, List.of(
                "sub:Overlays",
                "sub:AI",
                "sub:Light",
                "sub:Native",
                "sub:Context"
            ));

            reorderSubcategoryByAction(debug, "Overlays", List.of(
                ActionIds.DEBUG_OVERLAY_TOGGLE,
                ActionIds.DEBUG_BODY_PARTS_TOGGLE,
                ActionIds.DEBUG_OVERLAYS_ENABLE_ALL,
                ActionIds.DEBUG_OVERLAYS_DISABLE_ALL
            ));
            reorderSubcategoryByAction(debug, "AI", List.of(
                ActionIds.DEBUG_PATHFINDING_TOGGLE,
                ActionIds.DEBUG_LOS_TOGGLE,
                ActionIds.DEBUG_AGGRO_RANGE_TOGGLE
            ));
            reorderSubcategoryByAction(debug, "Light", List.of(
                ActionIds.DEBUG_LIGHT_OVERLAY_TOGGLE
            ));
            reorderSubcategoryByAction(debug, "Native", List.of(
                ActionIds.DEBUG_NATIVE_ENTITY_PATHING_TOGGLE,
                ActionIds.DEBUG_NATIVE_ENTITY_GOALS_TOGGLE,
                ActionIds.DEBUG_NATIVE_ENTITY_BRAINS_TOGGLE,
                ActionIds.DEBUG_NATIVE_POI_TOGGLE,
                ActionIds.DEBUG_NATIVE_RAIDS_TOGGLE,
                ActionIds.DEBUG_NATIVE_BEES_TOGGLE,
                ActionIds.DEBUG_NATIVE_GAME_EVENTS_TOGGLE,
                ActionIds.DEBUG_NATIVE_STRUCTURES_TOGGLE
            ));
            reorderSubcategoryByAction(debug, "Context", List.of(
                ActionIds.DEBUG_COMBAT_RESET,
                ActionIds.DEBUG_CONTEXT_STATUS
            ));
        }

        RadialCategory spatial = findCategory(result, MacroCategory.ANALYZE, "Spatial");
        if (spatial != null) {
            reorderCategoryItems(spatial, List.of(
                ActionIds.DEBUG_ROOM_BOUNDS_TOGGLE,
                ActionIds.DEBUG_ROOM_BOUNDS_GAPS_TOGGLE,
                ActionIds.DEBUG_VERTICAL_LEVELS_TOGGLE,
                ActionIds.DEBUG_SAFE_SPOTS_TOGGLE,
                ActionIds.DEBUG_SPAWNABILITY_TOGGLE
            ));
        }

        RadialCategory performance = findCategory(result, MacroCategory.ANALYZE, "Performance");
        if (performance != null) {
            reorderCategoryItems(performance, List.of(
                ActionIds.DEBUG_FPS_TRACKER_TOGGLE,
                ActionIds.DEBUG_PROFILER_TOGGLE,
                ActionIds.DEBUG_ENTITY_DENSITY_TOGGLE,
                ActionIds.DEBUG_CHUNK_PERF_TOGGLE,
                ActionIds.DEBUG_ATTRIBUTE_MONITOR_TOGGLE
            ));
        }
    }

    private static void reorderCombatCategories(Map<MacroCategory, List<RadialCategory>> result) {
        RadialCategory diagnostics = findCategory(result, MacroCategory.COMBAT, "Diagnostics");
        if (diagnostics != null) {
            reorderCategoryItems(diagnostics, List.of(
                ActionIds.DEBUG_BOSS_PHASE_TOGGLE,
                ActionIds.DEBUG_SKILL_EFFICACY_TOGGLE,
                ActionIds.DEBUG_ECONOMY_TOGGLE,
                ActionIds.DEBUG_ECONOMY_VIEW_CYCLE,
                ActionIds.DEBUG_ECONOMY_SORT_CYCLE
            ));
        }

        RadialCategory heatmaps = findCategory(result, MacroCategory.COMBAT, "Heatmaps");
        if (heatmaps != null) {
            reorderCategoryItems(heatmaps, List.of(
                ActionIds.DEBUG_HEATMAP_TOGGLE,
                ActionIds.DEBUG_HEATMAP_CYCLE,
                "sub:Types",
                ActionIds.DEBUG_HEATMAP_CLEAR_CURRENT,
                ActionIds.DEBUG_HEATMAP_CLEAR_ALL
            ));
            reorderSubcategoryByAction(heatmaps, "Types", List.of(
                ActionIds.DEBUG_HEATMAP_DEATH_TOGGLE,
                ActionIds.DEBUG_HEATMAP_MOVEMENT_TOGGLE,
                ActionIds.DEBUG_HEATMAP_CAMPING_TOGGLE,
                ActionIds.DEBUG_HEATMAP_STUCK_TOGGLE,
                ActionIds.DEBUG_HEATMAP_AGGRO_DROP_TOGGLE,
                ActionIds.DEBUG_HEATMAP_KITING_TOGGLE,
                ActionIds.DEBUG_HEATMAP_LIGHT_SPAWNABLE_TOGGLE,
                ActionIds.DEBUG_HEATMAP_LIGHT_DARK_TOGGLE
            ));
        }
    }

    private static void reorderTelemetryCategories(Map<MacroCategory, List<RadialCategory>> result) {
        RadialCategory dashboard = findCategory(result, MacroCategory.TELEMETRY, "Dashboard");
        if (dashboard != null) {
            reorderCategoryItems(dashboard, List.of(
                ActionIds.UI_TELEMETRY_DASHBOARD_OPEN,
                ActionIds.TELEMETRY_DASHBOARD_SERVER_OPEN,
                ActionIds.TELEMETRY_DASHBOARD_SERVER_START,
                ActionIds.TELEMETRY_DASHBOARD_SERVER_STOP,
                ActionIds.TELEMETRY_DASHBOARD_SERVER_STATUS
            ));
        }

        RadialCategory serverOps = findCategory(result, MacroCategory.TELEMETRY, "Server Ops");
        if (serverOps != null) {
            reorderCategoryItems(serverOps, List.of(
                ActionIds.TELEMETRY_RELOAD
            ));
        }

        RadialCategory exports = findCategory(result, MacroCategory.TELEMETRY, "Exports");
        if (exports != null) {
            reorderCategoryItems(exports, List.of(
                ActionIds.TELEMETRY_EXPORT_HEATMAPS,
                "sub:Heatmaps",
                ActionIds.TELEMETRY_EXPORT_PNG,
                ActionIds.TELEMETRY_EXPORT_CSV,
                ActionIds.TELEMETRY_EXPORT_JSON,
                ActionIds.TELEMETRY_EXPORT_ALL,
                ActionIds.TELEMETRY_EXPORT_DAMAGE_STATS
            ));
            reorderSubcategoryByAction(exports, "Heatmaps", List.of(
                ActionIds.TELEMETRY_EXPORT_HEATMAP_DEATH,
                ActionIds.TELEMETRY_EXPORT_HEATMAP_MOVEMENT,
                ActionIds.TELEMETRY_EXPORT_HEATMAP_CAMPING,
                ActionIds.TELEMETRY_EXPORT_HEATMAP_STUCK,
                ActionIds.TELEMETRY_EXPORT_HEATMAP_AGGRO_DROP,
                ActionIds.TELEMETRY_EXPORT_HEATMAP_KITING,
                ActionIds.TELEMETRY_EXPORT_HEATMAP_CHOKE_POINTS,
                ActionIds.TELEMETRY_EXPORT_HEATMAP_PARKOUR_FALLS
            ));
        }

        RadialCategory dataDumps = findCategory(result, MacroCategory.TELEMETRY, "Data Dumps");
        if (dataDumps != null) {
            reorderCategoryItems(dataDumps, List.of(
                ActionIds.TELEMETRY_DUMP_WEAPONS,
                ActionIds.TELEMETRY_DUMP_ROOMS,
                ActionIds.TELEMETRY_DUMP_FIGHTS,
                ActionIds.TELEMETRY_DUMP_MINIONS,
                ActionIds.TELEMETRY_DUNGEONS_DUMP
            ));
        }

        RadialCategory analysis = findCategory(result, MacroCategory.TELEMETRY, "Analysis");
        if (analysis != null) {
            reorderCategoryItems(analysis, List.of(
                ActionIds.TELEMETRY_SCAN_LIGHT_ALL,
                ActionIds.TELEMETRY_SCAN_LIGHT_ROOM,
                ActionIds.TELEMETRY_SPAWNABILITY,
                ActionIds.TELEMETRY_DESIRELINES_DUMP,
                ActionIds.TELEMETRY_DESIRELINES_ANALYZE,
                ActionIds.TELEMETRY_BACKTRACKING_DUMP,
                ActionIds.TELEMETRY_BACKTRACKING_CONFUSING,
                ActionIds.TELEMETRY_DUNGEONS_STATS
            ));
        }

        RadialCategory dungeon = findCategory(result, MacroCategory.TELEMETRY, "Dungeon");
        if (dungeon != null) {
            reorderCategoryItems(dungeon, List.of(
                ActionIds.DUNGEON_HELP,
                ActionIds.DUNGEON_START,
                ActionIds.DUNGEON_END,
                ActionIds.DUNGEON_STATUS
            ));
        }
    }

    private static void reorderToolsCategories(Map<MacroCategory, List<RadialCategory>> result) {
        RadialCategory editors = findCategory(result, MacroCategory.TOOLS, "Editors");
        if (editors != null) {
            reorderCategoryItems(editors, List.of(
                ActionIds.UI_EDITOR_HUB_OPEN
            ));
        }

        RadialCategory labs = findCategory(result, MacroCategory.TOOLS, "Labs");
        if (labs != null) {
            reorderCategoryItems(labs, List.of(
                ActionIds.UI_TESTING_HUB_OPEN,
                ActionIds.UI_VOXELLAB_OPEN
            ));
        }

        RadialCategory settings = findCategory(result, MacroCategory.TOOLS, "Settings");
        if (settings != null) {
            reorderCategoryItems(settings, List.of(
                ActionIds.UI_SETTINGS_OPEN,
                ActionIds.UI_KEYBINDS_OPEN,
                ActionIds.UI_RADIAL_SETTINGS_OPEN
            ));
        }
    }

    @Nullable
    private static RadialCategory findCategory(Map<MacroCategory, List<RadialCategory>> result,
                                               MacroCategory macro, String name) {
        List<RadialCategory> categories = result.get(macro);
        if (categories == null) {
            return null;
        }
        for (RadialCategory category : categories) {
            if (name.equalsIgnoreCase(category.getName())) {
                return category;
            }
        }
        return null;
    }

    private static void reorderSubcategoryByAction(RadialCategory category, String subcategoryName, List<String> order) {
        for (RadialMenuItem item : new ArrayList<>(category.getItems())) {
            if (!item.isSubcategoryLink()) {
                continue;
            }
            if (!subcategoryName.equalsIgnoreCase(item.getName())) {
                continue;
            }
            RadialCategory sub = item.getLinkedSubcategory();
            if (sub != null) {
                reorderCategoryItems(sub, order);
            }
            return;
        }
    }

    private static void reorderCategoryItems(RadialCategory category, List<String> preferredOrder) {
        if (category == null || preferredOrder == null || preferredOrder.isEmpty()) {
            return;
        }
        List<RadialMenuItem> items = category.getItems();
        if (items == null || items.size() <= 1) {
            return;
        }

        List<RadialMenuItem> ordered = new ArrayList<>();
        Set<RadialMenuItem> used = new HashSet<>();

        for (String token : preferredOrder) {
            RadialMenuItem match = findItem(items, token);
            if (match != null && used.add(match)) {
                ordered.add(match);
            }
        }

        for (RadialMenuItem item : items) {
            if (!used.contains(item)) {
                ordered.add(item);
            }
        }

        items.clear();
        items.addAll(ordered);
    }

    @Nullable
    private static RadialMenuItem findItem(List<RadialMenuItem> items, String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String trimmed = token.trim();
        if (trimmed.startsWith("sub:")) {
            String name = trimmed.substring(4);
            for (RadialMenuItem item : items) {
                if (item.isSubcategoryLink() && name.equalsIgnoreCase(item.getName())) {
                    return item;
                }
            }
            return null;
        }

        String actionId = trimmed.startsWith("action:") ? trimmed.substring(7) : trimmed;
        for (RadialMenuItem item : items) {
            String registryId = item.getAction().getRegistryId();
            if (registryId != null && registryId.equals(actionId)) {
                return item;
            }
        }
        return null;
    }

    private static int indexOf(List<String> order, String name) {
        int idx = order.indexOf(name);
        return idx == -1 ? Integer.MAX_VALUE : idx;
    }

    private static String safeMenuPath(RadialAction action) {
        String path = menuPathFor(action);
        if (path == null || path.isBlank()) {
            return "ZZZ/" + action.getId();
        }
        return path;
    }

    private static RadialCategory ensureCategory(List<RadialCategory> categories,
                                                 Map<String, RadialCategory> index,
                                                 MenuTarget target) {
        String key = target.categoryId;
        RadialCategory existing = index.get(key);
        if (existing != null) {
            return existing;
        }

        RadialCategory category = new RadialCategory(
            key,
            target.categoryName,
            target.color,
            target.icon
        );
        categories.add(category);
        index.put(key, category);
        return category;
    }

    private static RadialCategory ensureSubcategories(Map<String, RadialCategory> index,
                                                      RadialCategory root,
                                                      MenuTarget target) {
        RadialCategory current = root;
        StringBuilder path = new StringBuilder(root.getId());
        for (String name : target.subcategories) {
            path.append('/').append(sanitizeId(name));
            String key = path.toString();
            RadialCategory existing = index.get(key);
            if (existing != null) {
                current = existing;
                continue;
            }
            current = current.addSubcategory(key, name, target.color, iconFromName(name));
            index.put(key, current);
        }
        return current;
    }

    @Nullable
    private static RadialMenuItem buildItem(String actionId, @Nullable Supplier<RadialMenuItem> mobEditorSupplier) {
        if (ActionIds.UI_MOB_CONFIG_OPEN.equals(actionId) && mobEditorSupplier != null) {
            return mobEditorSupplier.get();
        }

        RadialMenuItem item = RadialMenuItem.registry(actionId);
        if (item == null) {
            return null;
        }

        String supplierId = ACTION_VISIBILITY_SUPPLIERS.get(actionId);
        if (supplierId != null) {
            BooleanSupplier supplier = VisibilitySupplierRegistry.get(supplierId);
            if (supplier != null) {
                item.setVisibilitySupplier(supplier);
            }
        }

        Integer color = ACTION_CUSTOM_COLORS.get(actionId);
        if (color != null) {
            item.setCustomColor(color);
        }
        return item;
    }

    private static MenuTarget resolveTarget(RadialAction action) {
        String path = menuPathFor(action);
        List<String> segments = splitPath(path);

        String root = segments.isEmpty() ? "" : segments.get(0);
        List<String> rest = segments.size() > 1 ? segments.subList(1, segments.size()) : List.of();

        MacroCategory macro = macroFromRoot(root, action.getCategory());
        CategoryTarget catTarget = categoryFromRoot(root, rest);

        String categoryName = catTarget.categoryName;
        List<String> subcategories = trimSubcategories(catTarget.subcategories);
        int color = colorForCategory(macro, categoryName);
        String icon = iconFromName(categoryName);

        return new MenuTarget(macro, sanitizeId(categoryName), categoryName, icon, color, subcategories);
    }

    @Nullable
    private static String menuPathFor(RadialAction action) {
        if (action == null) {
            return null;
        }
        String actionId = action.getId();
        if (actionId != null) {
            String override = ACTION_MENU_OVERRIDES.get(actionId);
            if (override != null) {
                return override;
            }
        }
        return action.getMenuPath();
    }

    private static List<String> splitPath(@Nullable String path) {
        if (path == null || path.isBlank()) {
            return List.of();
        }
        // Use -1 limit to avoid StringSplitter warning about trailing empty strings
        String[] raw = path.split("/", -1);
        List<String> segments = new ArrayList<>();
        for (String part : raw) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if ("Root".equalsIgnoreCase(part)) {
                continue;
            }
            segments.add(part.trim());
        }
        return segments;
    }

    private static MacroCategory macroFromRoot(String root, @Nullable ActionCategory category) {
        return switch (root) {
            case "Debug" -> MacroCategory.ANALYZE;
            case "Telemetry" -> MacroCategory.TELEMETRY;
            case "Combat" -> MacroCategory.COMBAT;
            case "Arena" -> MacroCategory.ARENA;
            case "Play", "Endurance" -> MacroCategory.PLAY;
            case "HUD" -> MacroCategory.PLAY;
            case "Tools", "Config", "Testing", "Developer", "Help", "Admin", "Nexus" -> MacroCategory.TOOLS;
            default -> fallbackMacro(category);
        };
    }

    private static MacroCategory fallbackMacro(@Nullable ActionCategory category) {
        if (category == null) {
            return MacroCategory.TOOLS;
        }
        return switch (category) {
            case COMBAT -> MacroCategory.COMBAT;
            case ENDURANCE, PARTY -> MacroCategory.PLAY;
            case ARENA -> MacroCategory.ARENA;
            case TELEMETRY -> MacroCategory.TELEMETRY;
            case DEBUG -> MacroCategory.ANALYZE;
            default -> MacroCategory.TOOLS;
        };
    }

    private static CategoryTarget categoryFromRoot(String root, List<String> rest) {
        if (rest.isEmpty()) {
            return new CategoryTarget(toTitle(root), List.of());
        }

        return switch (root) {
            case "Endurance" -> new CategoryTarget("Endurance", restMinusLast(rest));
            case "Play" -> {
                String first = rest.get(0);
                String category = switch (first) {
                    case "Endurance" -> "Endurance";
                    case "Quest Flow" -> "Quest Flow";
                    case "Quest Tools" -> "Quest Tools";
                    case "Endurance HUD" -> "Endurance HUD";
                    case "Party" -> "Party";
                    case "Leaderboards" -> "Leaderboards";
                    default -> "Play";
                };
                boolean dropFirst = !category.equals("Play");
                yield new CategoryTarget(category, restAfter(rest, dropFirst));
            }
            case "HUD" -> new CategoryTarget("HUD", restMinusLast(rest));
            case "Tools" -> {
                String first = rest.get(0);
                String category = switch (first) {
                    case "Labs" -> "Labs";
                    case "Editors" -> "Editors";
                    case "Item Editor" -> "Item Editor";
                    case "Mob Config", "Mob Editor" -> "Mob Editor";
                    case "Settings" -> "Settings";
                    case "Testing" -> "Testing";
                    case "Commands" -> "Commands";
                    case "Admin" -> "Admin";
                    case "Nexus" -> "Nexus";
                    default -> "Settings";
                };
                boolean dropFirst = category.equals(first) || ("Mob Editor".equals(category) && "Mob Config".equals(first));
                yield new CategoryTarget(category, restAfter(rest, dropFirst));
            }
            case "Admin" -> new CategoryTarget("Admin", restMinusLast(rest));
            case "Config" -> new CategoryTarget("Settings", restMinusLast(rest));
            case "Testing" -> new CategoryTarget("Testing", restMinusLast(rest));
            case "Developer" -> {
                List<String> sub = new ArrayList<>();
                sub.add("Developer");
                sub.addAll(restMinusLast(rest));
                yield new CategoryTarget("Testing", sub);
            }
            case "Help" -> new CategoryTarget("Settings", restMinusLast(rest));
            case "Nexus" -> new CategoryTarget("Nexus", restMinusLast(rest));
            case "Telemetry" -> {
                String first = rest.get(0);
                String category = switch (first) {
                    case "Dashboard" -> "Dashboard";
                    case "Export" -> "Exports";
                    case "Scan", "Analysis" -> "Analysis";
                    case "Dump" -> "Data Dumps";
                    case "Admin", "Ops", "Server Ops" -> "Server Ops";
                    case "Dungeon" -> "Dungeon";
                    default -> "Dashboard";
                };
                boolean dropFirst = switch (first) {
                    case "Dashboard", "Export", "Scan", "Analysis", "Dump", "Admin", "Ops", "Server Ops", "Dungeon" -> true;
                    default -> false;
                };
                yield new CategoryTarget(category, restAfter(rest, dropFirst));
            }
            case "Combat" -> {
                String first = rest.get(0);
                String category = switch (first) {
                    case "Diagnostics" -> "Diagnostics";
                    case "Heatmaps" -> "Heatmaps";
                    default -> "Diagnostics";
                };
                yield new CategoryTarget(category, restAfter(rest, category.equals(first)));
            }
            case "Arena" -> {
                String first = rest.get(0);
                String category = switch (first) {
                    case "Templates" -> "Templates";
                    case "Autosmoke" -> "Autosmoke";
                    case "Force" -> "Force";
                    case "HUD" -> "HUD";
                    case "Bounds" -> "Bounds";
                    case "Help", "Status", "Create", "Ops", "Quick Test Wizard" -> "Ops";
                    default -> "Ops";
                };
                yield new CategoryTarget(category, restAfter(rest, category.equals(first)));
            }
            case "Debug" -> {
                String first = rest.get(0);
                if ("Spatial".equals(first)) {
                    yield new CategoryTarget("Spatial", restAfter(rest, true));
                }
                if ("Perf".equals(first) || "Performance".equals(first)) {
                    yield new CategoryTarget("Performance", restAfter(rest, true));
                }
                List<String> subcategories = new ArrayList<>();
                if (!"Debug".equalsIgnoreCase(first)) {
                    subcategories.add(first);
                }
                subcategories.addAll(restAfter(rest, true));
                yield new CategoryTarget("Debug", subcategories);
            }
            default -> new CategoryTarget(toTitle(root), restMinusLast(rest));
        };
    }

    private static List<String> restMinusLast(List<String> rest) {
        if (rest.size() <= 1) {
            return List.of();
        }
        return new ArrayList<>(rest.subList(0, rest.size() - 1));
    }

    private static List<String> restAfter(List<String> rest, boolean dropFirst) {
        List<String> base = dropFirst ? rest.subList(1, rest.size()) : rest;
        if (base.size() <= 1) {
            return List.of();
        }
        return new ArrayList<>(base.subList(0, base.size() - 1));
    }

    private static List<String> trimSubcategories(List<String> categories) {
        if (categories.isEmpty()) {
            return List.of();
        }
        List<String> trimmed = new ArrayList<>(categories);
        if (trimmed.size() > MAX_SUBCATEGORY_DEPTH) {
            trimmed = trimmed.subList(0, MAX_SUBCATEGORY_DEPTH);
        }
        return trimmed;
    }

    private static int colorForCategory(MacroCategory macro, String categoryName) {
        return switch (macro) {
            case ANALYZE -> switch (categoryName) {
                case "Debug" -> DesignTokens.Radial.ANALYZE_DEBUG;
                case "Spatial" -> DesignTokens.Radial.ANALYZE_SPATIAL;
                case "Performance" -> DesignTokens.Radial.ANALYZE_PERFORMANCE;
                default -> DesignTokens.Radial.ANALYZE_DEBUG;
            };
            case TELEMETRY -> switch (categoryName) {
                case "Server Ops", "Dungeon" -> DesignTokens.Radial.TELEMETRY_OPS;
                case "Exports" -> DesignTokens.Radial.TELEMETRY_EXPORT;
                case "Analysis" -> DesignTokens.Radial.TELEMETRY_SCAN;
                case "Data Dumps" -> DesignTokens.Radial.TELEMETRY_DATA;
                case "Dashboard" -> DesignTokens.Radial.TELEMETRY_DASHBOARD;
                default -> DesignTokens.Radial.TELEMETRY_DASHBOARD;
            };
            case COMBAT -> switch (categoryName) {
                case "Diagnostics" -> DesignTokens.Radial.COMBAT_DEBUG;
                case "Heatmaps" -> DesignTokens.Radial.COMBAT_ACTIONS;
                default -> DesignTokens.Radial.COMBAT_ACTIONS;
            };
            case ARENA -> switch (categoryName) {
                case "Templates" -> DesignTokens.Radial.ARENA_TEMPLATES;
                case "Autosmoke" -> DesignTokens.Radial.ARENA_SPAWNING;
                case "Force" -> DesignTokens.Radial.ARENA_HAZARDS;
                case "HUD" -> DesignTokens.Radial.ARENA_REWARDS;
                case "Bounds" -> DesignTokens.Radial.ARENA_MANAGE;
                default -> DesignTokens.Radial.ARENA_MANAGE;
            };
            case PLAY -> switch (categoryName) {
                case "HUD" -> DesignTokens.Radial.PLAY_COMMS;
                case "Endurance", "Endurance HUD" -> DesignTokens.Radial.PLAY_SEASON;
                case "Quest Flow", "Quest Tools" -> DesignTokens.Radial.PLAY_QUESTS;
                case "Leaderboards" -> DesignTokens.Radial.PLAY_LEADERBOARD;
                case "Party" -> DesignTokens.Radial.PLAY_PARTY;
                default -> DesignTokens.Radial.PLAY_QUESTS;
            };
            case TOOLS -> switch (categoryName) {
                case "Labs" -> DesignTokens.Radial.TOOLS_TESTING;
                case "Editors" -> DesignTokens.Radial.TOOLS_EDITOR;
                case "Settings" -> DesignTokens.Radial.TOOLS_SETTINGS;
                case "Testing" -> DesignTokens.Radial.TOOLS_TESTING;
                case "Commands" -> DesignTokens.Radial.TOOLS_COMMANDS;
                case "Comms" -> DesignTokens.Radial.TOOLS_MAILBOX;
                case "Admin" -> DesignTokens.Radial.TOOLS_MAILBOX;
                case "Nexus" -> DesignTokens.Radial.TOOLS_PRIMARY;
                default -> DesignTokens.Radial.TOOLS_PRIMARY;
            };
        };
    }

    private static String iconFromName(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        return name.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private static String sanitizeId(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
    }

    private static String toTitle(String name) {
        if (name == null || name.isBlank()) {
            return "Misc";
        }
        String trimmed = name.trim();
        return trimmed.substring(0, 1).toUpperCase(Locale.ROOT) + trimmed.substring(1);
    }

    private record CategoryTarget(String categoryName, List<String> subcategories) {}

    private record MenuTarget(MacroCategory macro, String categoryId, String categoryName,
                               String icon, int color, List<String> subcategories) {}
}
