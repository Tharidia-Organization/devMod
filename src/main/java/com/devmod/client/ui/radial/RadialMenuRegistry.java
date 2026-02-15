package com.devmod.client.ui.radial;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;

import com.devmod.actions.ActionIds;
import com.devmod.client.ui.editor.WeaponTypeDetector;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.radial.model.MacroCategory;
import com.devmod.config.handler.impl.ArmorConfigHandler;

public final class RadialMenuRegistry {

    private RadialMenuRegistry() {} // Utility class

    /**
     * Creates an ItemStack with explicit null-safety check.
     * Minecraft Items are guaranteed non-null at runtime, but the type system
     * cannot verify this. This helper provides explicit null assertion.
     */
    private static ItemStack stack(net.minecraft.world.item.Item item) {
        return new ItemStack(Objects.requireNonNull(item, "item"));
    }

    @Nonnull
    private static List<RadialCategory> requireCategories(Map<MacroCategory, List<RadialCategory>> map,
            MacroCategory macro) {
        return Objects.requireNonNull(map.get(macro), "categories for " + macro);
    }

    private static ItemStack getHeldItem() {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return ItemStack.EMPTY;
        ItemStack held = player.getMainHandItem();
        return held.isEmpty() ? ItemStack.EMPTY : held.copy();
    }

    private static boolean isWeaponItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var detection = WeaponTypeDetector.detectDetailed(stack);
        return WeaponTypeDetector.isMelee(detection.type()) || WeaponTypeDetector.isRanged(detection.type());
    }

    private static boolean isArmorItem(ItemStack stack) {
        return stack != null && ArmorConfigHandler.isArmor(stack);
    }

    private static boolean isGeneralItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var detection = WeaponTypeDetector.detectDetailed(stack);
        return !isWeaponItem(stack) && !isArmorItem(stack) && detection.type() != WeaponTypeDetector.WeaponType.SHIELD;
    }

    private static boolean isShieldItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var detection = WeaponTypeDetector.detectDetailed(stack);
        return detection.type() == WeaponTypeDetector.WeaponType.SHIELD;
    }

    private static boolean isFoodItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        @Nonnull DataComponentType<?> foodComponent = Objects.requireNonNull(
            net.minecraft.core.component.DataComponents.FOOD, "food component");
        return stack.getItem().components().has(foodComponent);
    }

    private static boolean isFuelItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        // Check if item has a burn time
        @Nonnull RecipeType<?> smeltingType = Objects.requireNonNull(RecipeType.SMELTING, "smelting recipe type");
        return stack.getBurnTime(smeltingType) > 0;
    }

    private static boolean isUsableItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        @Nonnull var item = Objects.requireNonNull(stack.getItem(), "item");
        // Throwable items
        if (item instanceof net.minecraft.world.item.SnowballItem ||
            item instanceof net.minecraft.world.item.EggItem ||
            item instanceof net.minecraft.world.item.EnderpearlItem ||
            item instanceof net.minecraft.world.item.ThrowablePotionItem ||
            item instanceof net.minecraft.world.item.InstrumentItem) {
            return true;
        }
        // Items with use duration (potions, food already handled separately)
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        return item.getUseDuration(stack, player) > 0 && !isFoodItem(stack);
    }

    /**
     * Provides category definitions for each macro-category.
     * Categories are built lazily to allow proper initialization order.
     */
    public interface CategoryProvider {
        List<RadialCategory> getCategories(MacroCategory macro);
    }

    /**
     * Creates the default category provider with all 24 categories.
     *
     * @param mobEditorItemSupplier Supplier for mob editor item (needs screen context)
     * @return Map of macro-category to list of categories
     */
    public static Map<MacroCategory, List<RadialCategory>> createDefaultCategories(
            @Nullable Supplier<RadialMenuItem> mobEditorItemSupplier) {

        Map<MacroCategory, List<RadialCategory>> map = new EnumMap<>(MacroCategory.class);

        for (MacroCategory macro : MacroCategory.values()) {
            map.put(macro, new ArrayList<>());
        }

        // Build all categories
        buildAnalyzeCategories(map);
        buildTelemetryCategories(map);
        buildCombatCategories(map);
        buildArenaCategories(map);
        buildPlayCategories(map);
        buildToolsCategories(map, mobEditorItemSupplier);

        return map;
    }

    // ================================================================
    // MACRO: ANALYZE - Debug, visualization, analysis tools
    // ================================================================

    private static void buildAnalyzeCategories(Map<MacroCategory, List<RadialCategory>> map) {
        List<RadialCategory> categories = requireCategories(map, MacroCategory.ANALYZE);

        // Category 1: Debug
        RadialCategory debug = RadialCategory.builder("debug")
            .name("Debug")
            .color(DesignTokens.Radial.ANALYZE_DEBUG)
            .icon("")
            .iconStack(stack(Items.ENDER_EYE))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_OVERLAY_TOGGLE))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_BODY_PARTS_TOGGLE))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_OVERLAYS_ENABLE_ALL))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_OVERLAYS_DISABLE_ALL))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_LOS_TOGGLE))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_AGGRO_RANGE_TOGGLE))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_PATHFINDING_TOGGLE))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_COMMAND_HELP))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_COMMAND_LIST))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_COMMAND_TOGGLE))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_COMMAND_OFF))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_SCREEN_SHAKE_TEST))
            .build();

        RadialCategory nativeDebug = debug.addSubcategory("native_debug", "Native Debug", DesignTokens.Radial.ANALYZE_DEBUG,
            stack(Items.DEBUG_STICK));
        nativeDebug.addItems(
            RadialMenuItem.registry(ActionIds.DEBUG_NATIVE_ENTITY_PATHING_TOGGLE),
            RadialMenuItem.registry(ActionIds.DEBUG_NATIVE_ENTITY_GOALS_TOGGLE),
            RadialMenuItem.registry(ActionIds.DEBUG_NATIVE_ENTITY_BRAINS_TOGGLE),
            RadialMenuItem.registry(ActionIds.DEBUG_NATIVE_POI_TOGGLE),
            RadialMenuItem.registry(ActionIds.DEBUG_NATIVE_RAIDS_TOGGLE),
            RadialMenuItem.registry(ActionIds.DEBUG_NATIVE_BEES_TOGGLE),
            RadialMenuItem.registry(ActionIds.DEBUG_NATIVE_GAME_EVENTS_TOGGLE),
            RadialMenuItem.registry(ActionIds.DEBUG_NATIVE_STRUCTURES_TOGGLE)
        );

        categories.add(debug);

        // Category 2: Spatial
        RadialCategory spatial = RadialCategory.builder("spatial")
            .name("Spatial")
            .color(DesignTokens.Radial.ANALYZE_SPATIAL)
            .icon("")
            .iconStack(stack(Items.STRUCTURE_BLOCK))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_ROOM_BOUNDS_TOGGLE))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_ROOM_BOUNDS_RELOAD))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_ROOM_BOUNDS_CLEAR))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_ROOM_BOUNDS_GAPS_TOGGLE))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_LIGHT_OVERLAY_TOGGLE))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_VERTICAL_LEVELS_TOGGLE))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_SAFE_SPOTS_TOGGLE))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_SPAWNABILITY_TOGGLE))
            .build();

        RadialCategory roomBounds = spatial.addSubcategory("room_bounds", "Room Bounds", DesignTokens.Radial.ANALYZE_SPATIAL,
            stack(Items.STRUCTURE_BLOCK));
        roomBounds.addItems(
            RadialMenuItem.registry(ActionIds.UI_ROOM_BOUNDS_EDITOR_OPEN),
            RadialMenuItem.registry(ActionIds.UI_ROOM_BOUNDS_POINT_A),
            RadialMenuItem.registry(ActionIds.UI_ROOM_BOUNDS_POINT_B),
            RadialMenuItem.registry(ActionIds.UI_ROOM_BOUNDS_SAVE),
            RadialMenuItem.registry(ActionIds.UI_ROOM_BOUNDS_DELETE_LAST)
        );

        categories.add(spatial);

        // Category 3: Performance
        categories.add(RadialCategory.builder("performance")
            .name("Performance")
            .color(DesignTokens.Radial.ANALYZE_PERFORMANCE)
            .icon("")
            .iconStack(stack(Items.CLOCK))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_FPS_TRACKER_TOGGLE))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_PROFILER_TOGGLE))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_CHUNK_PERF_TOGGLE))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_ENTITY_DENSITY_TOGGLE))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_ATTRIBUTE_MONITOR_TOGGLE))
            .build());
    }

    // ================================================================
    // MACRO: TELEMETRY - Dashboards, exports, scans
    // ================================================================

    private static void buildTelemetryCategories(Map<MacroCategory, List<RadialCategory>> map) {
        List<RadialCategory> categories = requireCategories(map, MacroCategory.TELEMETRY);

        // Category 1: Telemetry Ops
        categories.add(RadialCategory.builder("telemetry_ops")
            .name("Ops")
            .color(DesignTokens.Radial.TELEMETRY_OPS)
            .icon("")
            .iconStack(stack(Items.MAP))
            .item(RadialMenuItem.registry(ActionIds.TELEMETRY_DASHBOARD_SERVER_START))
            .item(RadialMenuItem.registry(ActionIds.TELEMETRY_DASHBOARD_SERVER_STOP))
            .item(RadialMenuItem.registry(ActionIds.TELEMETRY_RELOAD))
            .item(RadialMenuItem.registry(ActionIds.DUNGEON_HELP))
            .item(RadialMenuItem.registry(ActionIds.DUNGEON_START))
            .item(RadialMenuItem.registry(ActionIds.DUNGEON_END))
            .item(RadialMenuItem.registry(ActionIds.DUNGEON_STATUS))
            .build());

        // Category 2: Telemetry Data
        RadialCategory telemetryData = RadialCategory.builder("telemetry_data")
            .name("Data")
            .color(DesignTokens.Radial.TELEMETRY_DATA)
            .icon("")
            .iconStack(stack(Items.PAPER))
            .item(RadialMenuItem.registry(ActionIds.TELEMETRY_DUMP_WEAPONS))
            .item(RadialMenuItem.registry(ActionIds.TELEMETRY_DUMP_ROOMS))
            .item(RadialMenuItem.registry(ActionIds.TELEMETRY_DUMP_FIGHTS))
            .item(RadialMenuItem.registry(ActionIds.TELEMETRY_DUMP_MINIONS))
            .item(RadialMenuItem.registry(ActionIds.TELEMETRY_DUNGEONS_DUMP))
            .item(RadialMenuItem.registry(ActionIds.TELEMETRY_EXPORT_HEATMAPS))
            .item(RadialMenuItem.registry(ActionIds.TELEMETRY_EXPORT_PNG))
            .item(RadialMenuItem.registry(ActionIds.TELEMETRY_EXPORT_CSV))
            .item(RadialMenuItem.registry(ActionIds.TELEMETRY_EXPORT_JSON))
            .item(RadialMenuItem.registry(ActionIds.TELEMETRY_EXPORT_ALL))
            .build();

        RadialCategory heatmapExports = telemetryData.addSubcategory("telemetry_heatmap_exports",
            "Heatmap Exports", DesignTokens.Radial.TELEMETRY_DATA, stack(Items.MAP));
        heatmapExports.addItems(
            RadialMenuItem.registry(ActionIds.TELEMETRY_EXPORT_HEATMAP_DEATH),
            RadialMenuItem.registry(ActionIds.TELEMETRY_EXPORT_HEATMAP_MOVEMENT),
            RadialMenuItem.registry(ActionIds.TELEMETRY_EXPORT_HEATMAP_CAMPING),
            RadialMenuItem.registry(ActionIds.TELEMETRY_EXPORT_HEATMAP_STUCK),
            RadialMenuItem.registry(ActionIds.TELEMETRY_EXPORT_HEATMAP_AGGRO_DROP),
            RadialMenuItem.registry(ActionIds.TELEMETRY_EXPORT_HEATMAP_KITING),
            RadialMenuItem.registry(ActionIds.TELEMETRY_EXPORT_HEATMAP_CHOKE_POINTS),
            RadialMenuItem.registry(ActionIds.TELEMETRY_EXPORT_HEATMAP_PARKOUR_FALLS),
            RadialMenuItem.registry(ActionIds.TELEMETRY_EXPORT_DAMAGE_STATS)
        );

        categories.add(telemetryData);

        // Category 3: Telemetry Scan
        categories.add(RadialCategory.builder("telemetry_scan")
            .name("Scan")
            .color(DesignTokens.Radial.TELEMETRY_SCAN)
            .icon("")
            .iconStack(stack(Items.SPYGLASS))
            .item(RadialMenuItem.registry(ActionIds.TELEMETRY_SCAN_LIGHT_ALL))
            .item(RadialMenuItem.registry(ActionIds.TELEMETRY_SCAN_LIGHT_ROOM))
            .item(RadialMenuItem.registry(ActionIds.TELEMETRY_SPAWNABILITY))
            .item(RadialMenuItem.registry(ActionIds.TELEMETRY_DESIRELINES_DUMP))
            .item(RadialMenuItem.registry(ActionIds.TELEMETRY_DESIRELINES_ANALYZE))
            .item(RadialMenuItem.registry(ActionIds.TELEMETRY_BACKTRACKING_DUMP))
            .item(RadialMenuItem.registry(ActionIds.TELEMETRY_BACKTRACKING_CONFUSING))
            .item(RadialMenuItem.registry(ActionIds.TELEMETRY_DUNGEONS_STATS))
            .build());

        // Category 4: Dashboard
        categories.add(RadialCategory.builder("dashboard")
            .name("Dash")
            .color(DesignTokens.Radial.TELEMETRY_DASH)
            .icon("")
            .iconStack(stack(Items.WRITABLE_BOOK))
            .item(RadialMenuItem.registry(ActionIds.UI_TELEMETRY_DASHBOARD_OPEN))
            .item(RadialMenuItem.registry(ActionIds.TELEMETRY_DASHBOARD_SERVER_OPEN))
            .item(RadialMenuItem.registry(ActionIds.TELEMETRY_DASHBOARD_SERVER_STATUS))
            .build());
    }

    // ================================================================
    // MACRO: COMBAT - Combat analysis and tools
    // ================================================================

    private static void buildCombatCategories(Map<MacroCategory, List<RadialCategory>> map) {
        List<RadialCategory> categories = requireCategories(map, MacroCategory.COMBAT);

        // Category 1: Combat HUD
        RadialCategory combatHud = RadialCategory.builder("combat_hud")
            .name("HUD")
            .color(DesignTokens.Radial.COMBAT_ACTIONS)
            .icon("")
            .iconStack(stack(Items.DIAMOND_SWORD))
            .item(RadialMenuItem.registry(ActionIds.HUD_IMPACT_TOGGLE))
            .item(RadialMenuItem.registry(ActionIds.HUD_IMPACT_CONTROLLER_TOGGLE))
            .item(RadialMenuItem.registry(ActionIds.HUD_IMPACT_3D_TOGGLE))
            .item(RadialMenuItem.registry(ActionIds.HUD_IMPACT_DISPLAY_MODE_CYCLE))
            .item(RadialMenuItem.registry(ActionIds.HUD_IMPACT_SHOW_RECAP))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_IMPACT_DISMISS))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_BOSS_PHASE_TOGGLE))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_SKILL_EFFICACY_TOGGLE))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_ECONOMY_TOGGLE))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_ECONOMY_VIEW_CYCLE))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_ECONOMY_SORT_CYCLE))
            .build();

        // Impact HUD Presets subcategory
        RadialCategory impactPresets = combatHud.addSubcategory("impact_presets", "Impact Presets", DesignTokens.Radial.COMBAT_DEBUG,
            stack(Items.COMPARATOR));
        impactPresets.addItems(
            RadialMenuItem.registry(ActionIds.HUD_IMPACT_PRESET_MINIMAL),
            RadialMenuItem.registry(ActionIds.HUD_IMPACT_PRESET_DETAILED),
            RadialMenuItem.registry(ActionIds.HUD_IMPACT_PRESET_TRAINING)
        );

        categories.add(combatHud);

        // Category 2: Heatmaps
        RadialCategory heatmaps = RadialCategory.builder("heatmaps")
            .name("Heatmaps")
            .color(DesignTokens.Radial.COMBAT_DAMAGE)
            .icon("")
            .iconStack(stack(Items.BLAZE_POWDER))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_HEATMAP_CYCLE))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_HEATMAP_TOGGLE))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_HEATMAP_CLEAR_CURRENT))
            .item(RadialMenuItem.registry(ActionIds.DEBUG_HEATMAP_CLEAR_ALL))
            .build();

        RadialCategory heatmapTypes = heatmaps.addSubcategory("heatmap_types", "Heatmap Types", DesignTokens.Radial.COMBAT_DAMAGE,
            stack(Items.FILLED_MAP));
        heatmapTypes.addItems(
            RadialMenuItem.registry(ActionIds.DEBUG_HEATMAP_DEATH_TOGGLE),
            RadialMenuItem.registry(ActionIds.DEBUG_HEATMAP_MOVEMENT_TOGGLE),
            RadialMenuItem.registry(ActionIds.DEBUG_HEATMAP_CAMPING_TOGGLE),
            RadialMenuItem.registry(ActionIds.DEBUG_HEATMAP_STUCK_TOGGLE),
            RadialMenuItem.registry(ActionIds.DEBUG_HEATMAP_AGGRO_DROP_TOGGLE),
            RadialMenuItem.registry(ActionIds.DEBUG_HEATMAP_KITING_TOGGLE),
            RadialMenuItem.registry(ActionIds.DEBUG_HEATMAP_LIGHT_SPAWNABLE_TOGGLE),
            RadialMenuItem.registry(ActionIds.DEBUG_HEATMAP_LIGHT_DARK_TOGGLE)
        );

        categories.add(heatmaps);

        // Category 3: Abilities
        categories.add(RadialCategory.builder("abilities")
            .name("Abilities")
            .color(DesignTokens.Radial.COMBAT_WEAPON)
            .icon("")
            .iconStack(stack(Items.FEATHER))
            .item(RadialMenuItem.registry(ActionIds.ABILITY_DASH))
            .item(RadialMenuItem.registry(ActionIds.ABILITY_DODGE))
            .build());

    }

    // ================================================================
    // MACRO: ARENA - Arena systems and operations
    // ================================================================

    private static void buildArenaCategories(Map<MacroCategory, List<RadialCategory>> map) {
        List<RadialCategory> categories = requireCategories(map, MacroCategory.ARENA);

        // Category 1: Arena Ops
        categories.add(RadialCategory.builder("arena_ops")
            .name("Ops")
            .color(DesignTokens.Radial.ARENA_MANAGE)
            .icon("")
            .iconStack(stack(Items.COMMAND_BLOCK))
            .item(RadialMenuItem.registry(ActionIds.UI_ARENA_HUB_OPEN))
            .item(RadialMenuItem.registry(ActionIds.ARENA_HELP))
            .item(RadialMenuItem.registry(ActionIds.ARENA_CREATE))
            .item(RadialMenuItem.registry(ActionIds.ARENA_QUICK_TEST_WIZARD_OPEN))
            .item(RadialMenuItem.registry(ActionIds.ARENA_STATUS))
            .build());

        // Category 2: Arena Templates
        categories.add(RadialCategory.builder("arena_templates")
            .name("Templates")
            .color(DesignTokens.Radial.ARENA_TEMPLATES)
            .icon("")
            .iconStack(stack(Items.MAP))
            .item(RadialMenuItem.registry(ActionIds.ARENA_TEMPLATE_LIST))
            .item(RadialMenuItem.registry(ActionIds.ARENA_TEMPLATE_INFO))
            .item(RadialMenuItem.registry(ActionIds.ARENA_TEMPLATE_STATUS))
            .item(RadialMenuItem.registry(ActionIds.ARENA_TEMPLATE_RELOAD))
            .item(RadialMenuItem.registry(ActionIds.ARENA_VALIDATE))
            .item(RadialMenuItem.registry(ActionIds.ARENA_METRICS))
            .build());

        // Category 3: Arena Force
        categories.add(RadialCategory.builder("arena_force")
            .name("Force")
            .color(DesignTokens.Radial.ARENA_SPAWNING)
            .icon("")
            .iconStack(stack(Items.NAME_TAG))
            .item(RadialMenuItem.registry(ActionIds.ARENA_FORCE))
            .item(RadialMenuItem.registry(ActionIds.ARENA_FORCE_CLEAR))
            .item(RadialMenuItem.registry(ActionIds.ARENA_FORCE_STATUS))
            .build());

        // Category 4: Arena Autosmoke
        categories.add(RadialCategory.builder("arena_autosmoke")
            .name("Autosmoke")
            .color(DesignTokens.Radial.ARENA_HAZARDS)
            .icon("")
            .iconStack(stack(Items.CAMPFIRE))
            .item(RadialMenuItem.registry(ActionIds.ARENA_AUTOSMOKE_RUN))
            .item(RadialMenuItem.registry(ActionIds.ARENA_AUTOSMOKE_STATUS))
            .item(RadialMenuItem.registry(ActionIds.ARENA_AUTOSMOKE_SCHEDULE_STATUS))
            .build());

        // Category 5: Arena HUD
        categories.add(RadialCategory.builder("arena_hud")
            .name("HUD")
            .color(DesignTokens.Radial.ARENA_REWARDS)
            .icon("")
            .iconStack(stack(Items.REDSTONE_TORCH))
            .item(RadialMenuItem.registry(ActionIds.ARENA_HUD_TOGGLE))
            .item(RadialMenuItem.registry(ActionIds.ARENA_HUD_ON))
            .item(RadialMenuItem.registry(ActionIds.ARENA_HUD_OFF))
            .item(RadialMenuItem.registry(ActionIds.ARENA_HUD_STATUS))
            .build());
    }

    // ================================================================
    // MACRO: TOOLS - Configuration and utility tools
    // ================================================================

    private static void buildToolsCategories(Map<MacroCategory, List<RadialCategory>> map,
            @Nullable Supplier<RadialMenuItem> mobEditorItemSupplier) {
        List<RadialCategory> categories = requireCategories(map, MacroCategory.TOOLS);

        // Category 1: Settings
        RadialCategory settings = RadialCategory.builder("settings")
            .name("Settings")
            .color(DesignTokens.Radial.TOOLS_PRIMARY)
            .icon("")
            .iconStack(stack(Items.COMPARATOR))
            .item(RadialMenuItem.registry(ActionIds.UI_SETTINGS_OPEN))
            .item(RadialMenuItem.registry(ActionIds.UI_RADIAL_SETTINGS_OPEN))
            .item(RadialMenuItem.registry(ActionIds.UI_KEYBINDS_OPEN))
            .item(RadialMenuItem.registry(ActionIds.HUD_QUICK_HELP_TOGGLE))
            .item(RadialMenuItem.registry(ActionIds.UI_WELCOME_OPEN))
            .item(RadialMenuItem.registry(ActionIds.UI_ONBOARDING_START))
            .item(RadialMenuItem.registry(ActionIds.UI_ONBOARDING_SKIP))
            .build();

        RadialCategory hudConfig = settings.addSubcategory("config_hud", "HUD Config", DesignTokens.Radial.TOOLS_PRIMARY,
            stack(Items.ITEM_FRAME));
        hudConfig.addItems(
            RadialMenuItem.registry(ActionIds.CONFIG_IMPACT_HUD_HISTORY_TOGGLE),
            RadialMenuItem.registry(ActionIds.CONFIG_IMPACT_HUD_DPS_TOGGLE)
        );

        RadialCategory hudPosition = hudConfig.addSubcategory("hud_position", "Position", DesignTokens.Radial.TOOLS_PRIMARY,
            stack(Items.COMPASS));
        hudPosition.addItems(
            RadialMenuItem.registry(ActionIds.CONFIG_IMPACT_HUD_POSITION_TOP_LEFT),
            RadialMenuItem.registry(ActionIds.CONFIG_IMPACT_HUD_POSITION_TOP_RIGHT),
            RadialMenuItem.registry(ActionIds.CONFIG_IMPACT_HUD_POSITION_CENTER_LEFT),
            RadialMenuItem.registry(ActionIds.CONFIG_IMPACT_HUD_POSITION_CENTER_RIGHT),
            RadialMenuItem.registry(ActionIds.CONFIG_IMPACT_HUD_POSITION_BOTTOM_LEFT),
            RadialMenuItem.registry(ActionIds.CONFIG_IMPACT_HUD_POSITION_BOTTOM_RIGHT)
        );

        RadialCategory hudOffset = hudConfig.addSubcategory("hud_offset", "Offset", DesignTokens.Radial.TOOLS_PRIMARY,
            stack(Items.ARROW));
        hudOffset.addItems(
            RadialMenuItem.registry(ActionIds.CONFIG_IMPACT_HUD_OFFSET_X_MINUS),
            RadialMenuItem.registry(ActionIds.CONFIG_IMPACT_HUD_OFFSET_X_PLUS),
            RadialMenuItem.registry(ActionIds.CONFIG_IMPACT_HUD_OFFSET_Y_MINUS),
            RadialMenuItem.registry(ActionIds.CONFIG_IMPACT_HUD_OFFSET_Y_PLUS)
        );

        RadialCategory hudPresets = hudConfig.addSubcategory("hud_presets", "Presets", DesignTokens.Radial.TOOLS_PRIMARY,
            stack(Items.WRITABLE_BOOK));
        hudPresets.addItems(
            RadialMenuItem.registry(ActionIds.CONFIG_IMPACT_HUD_PRESET_EXPORT),
            RadialMenuItem.registry(ActionIds.CONFIG_IMPACT_HUD_PRESET_IMPORT),
            RadialMenuItem.registry(ActionIds.CONFIG_IMPACT_HUD_RESET_DEFAULTS)
        );

        RadialCategory effectsConfig = settings.addSubcategory("config_effects", "Effects", DesignTokens.Radial.TOOLS_PRIMARY,
            stack(Items.BLAZE_POWDER));
        RadialCategory impactVfx = effectsConfig.addSubcategory("impact_vfx", "Impact VFX", DesignTokens.Radial.TOOLS_PRIMARY,
            stack(Items.BLAZE_POWDER));
        impactVfx.addItems(
            RadialMenuItem.registry(ActionIds.CONFIG_IMPACT_VFX_TOGGLE),
            RadialMenuItem.registry(ActionIds.CONFIG_IMPACT_VFX_VORTEX_TOGGLE),
            RadialMenuItem.registry(ActionIds.CONFIG_IMPACT_VFX_SLASH_TOGGLE),
            RadialMenuItem.registry(ActionIds.CONFIG_IMPACT_VFX_LINES_TOGGLE),
            RadialMenuItem.registry(ActionIds.CONFIG_IMPACT_VFX_GLYPHS_TOGGLE),
            RadialMenuItem.registry(ActionIds.CONFIG_IMPACT_VFX_GLYPHS_EXCLUSIVE_TOGGLE),
            RadialMenuItem.registry(ActionIds.CONFIG_IMPACT_VFX_RESET_DEFAULTS)
        );

        RadialCategory vfxIntensity = impactVfx.addSubcategory("impact_vfx_intensity", "Intensity", DesignTokens.Radial.TOOLS_PRIMARY,
            stack(Items.GLOWSTONE_DUST));
        vfxIntensity.addItems(
            RadialMenuItem.registry(ActionIds.CONFIG_IMPACT_VFX_INTENSITY_LOW),
            RadialMenuItem.registry(ActionIds.CONFIG_IMPACT_VFX_INTENSITY_MED),
            RadialMenuItem.registry(ActionIds.CONFIG_IMPACT_VFX_INTENSITY_HIGH),
            RadialMenuItem.registry(ActionIds.CONFIG_IMPACT_VFX_INTENSITY_MAX)
        );

        RadialCategory vfxPresets = impactVfx.addSubcategory("impact_vfx_presets", "Presets", DesignTokens.Radial.TOOLS_PRIMARY,
            stack(Items.ENCHANTED_BOOK));
        vfxPresets.addItems(
            RadialMenuItem.registry(ActionIds.CONFIG_IMPACT_VFX_PRESET_COMBAT_LANGUAGE),
            RadialMenuItem.registry(ActionIds.CONFIG_IMPACT_VFX_PRESET_COMBAT_LANGUAGE_LINES)
        );

        effectsConfig.addItems(
            RadialMenuItem.registry(ActionIds.CONFIG_SCREEN_SHAKE_TOGGLE),
            RadialMenuItem.registry(ActionIds.CONFIG_PROJECTILE_TRAILS_TOGGLE),
            RadialMenuItem.registry(ActionIds.CONFIG_BADGE_POPUPS_TOGGLE)
        );

        RadialCategory telemetryConfig = settings.addSubcategory("config_telemetry", "Telemetry Config", DesignTokens.Radial.TOOLS_PRIMARY,
            stack(Items.MAP));
        telemetryConfig.addItems(
            RadialMenuItem.registry(ActionIds.CONFIG_TELEMETRY_TOGGLE),
            RadialMenuItem.registry(ActionIds.CONFIG_TELEMETRY_HITS_TOGGLE),
            RadialMenuItem.registry(ActionIds.CONFIG_TELEMETRY_DEATHS_TOGGLE),
            RadialMenuItem.registry(ActionIds.CONFIG_TELEMETRY_SPAWNS_TOGGLE)
        );

        RadialCategory combatConfig = settings.addSubcategory("config_combat", "Combat Config", DesignTokens.Radial.TOOLS_PRIMARY,
            stack(Items.TARGET));
        combatConfig.addItems(
            RadialMenuItem.registry(ActionIds.CONFIG_BODY_PART_DETECTION_TOGGLE)
        );

        // Game Design Config subcategory
        RadialCategory gameDesign = settings.addSubcategory("config_gamedesign", "Game Design", DesignTokens.Radial.TOOLS_GAMEDESIGN,
            stack(Items.ENDER_CHEST));
        gameDesign.addItems(
            RadialMenuItem.registry(ActionIds.CONFIG_GAMEDESIGN_RELOAD),
            RadialMenuItem.registry(ActionIds.CONFIG_GAMEDESIGN_SAVE),
            RadialMenuItem.registry(ActionIds.CONFIG_GAMEDESIGN_RESET)
        );

        RadialCategory gameDesignSystems = gameDesign.addSubcategory("gamedesign_systems", "Systems", DesignTokens.Radial.TOOLS_GAMEDESIGN,
            stack(Items.REDSTONE));
        gameDesignSystems.addItems(
            RadialMenuItem.registry(ActionIds.CONFIG_RESONANCE_TOGGLE),
            RadialMenuItem.registry(ActionIds.CONFIG_CONTRACTS_TOGGLE),
            RadialMenuItem.registry(ActionIds.CONFIG_SIGNATURE_WEAPONS_TOGGLE),
            RadialMenuItem.registry(ActionIds.CONFIG_NEMESIS_TOGGLE),
            RadialMenuItem.registry(ActionIds.CONFIG_TIDE_TOGGLE)
        );

        RadialCategory gameDesignPresets = gameDesign.addSubcategory("gamedesign_presets", "Presets", DesignTokens.Radial.TOOLS_GAMEDESIGN,
            stack(Items.ENCHANTED_BOOK));
        gameDesignPresets.addItems(
            RadialMenuItem.registry(ActionIds.CONFIG_GAMEDESIGN_PRESET_EASY),
            RadialMenuItem.registry(ActionIds.CONFIG_GAMEDESIGN_PRESET_HARD),
            RadialMenuItem.registry(ActionIds.CONFIG_GAMEDESIGN_PRESET_CHAOS),
            RadialMenuItem.registry(ActionIds.CONFIG_GAMEDESIGN_PRESET_TUTORIAL),
            RadialMenuItem.registry(ActionIds.CONFIG_GAMEDESIGN_PRESET_SPEEDRUN)
        );

        categories.add(settings);

        // Category 2: Testing
        RadialCategory testing = RadialCategory.builder("testing")
            .name("Testing")
            .color(DesignTokens.Radial.TOOLS_EDITOR)
            .icon("")
            .iconStack(stack(Items.BREWING_STAND))
            .item(RadialMenuItem.registry(ActionIds.UI_TESTING_HUB_OPEN))
            .item(RadialMenuItem.registry(ActionIds.UI_QA_TESTING_OPEN))
            .item(RadialMenuItem.registry(ActionIds.UI_RESPONSIVENESS_TEST_OPEN))
            .item(RadialMenuItem.registry(ActionIds.UI_QUICK_TEST_WIZARD_OPEN))
            .item(RadialMenuItem.registry(ActionIds.UI_BADGE_TESTS_OPEN))
            .item(RadialMenuItem.registry(ActionIds.UI_VOXELLAB_OPEN))
            .item(RadialMenuItem.registry(ActionIds.UI_VOXELLAB_UI_TESTS_OPEN))
            .build();

        RadialCategory hudTests = testing.addSubcategory("testing_hud", "HUD", DesignTokens.Radial.TOOLS_EDITOR,
            stack(Items.ITEM_FRAME));
        hudTests.addItems(
            RadialMenuItem.registry(ActionIds.TEST_HUD_ON),
            RadialMenuItem.registry(ActionIds.TEST_HUD_OFF),
            RadialMenuItem.registry(ActionIds.TEST_HUD_TOGGLE),
            RadialMenuItem.registry(ActionIds.TEST_HUD_EXPORT),
            RadialMenuItem.registry(ActionIds.TEST_HUD_IMPORT)
        );

        RadialCategory panelTests = testing.addSubcategory("testing_panels", "Panels", DesignTokens.Radial.TOOLS_EDITOR,
            stack(Items.GLASS_PANE));
        panelTests.addItems(
            RadialMenuItem.registry(ActionIds.TEST_PANEL_ON),
            RadialMenuItem.registry(ActionIds.TEST_PANEL_OFF),
            RadialMenuItem.registry(ActionIds.TEST_PANEL_TOGGLE),
            RadialMenuItem.registry(ActionIds.TEST_PANELCLEAR)
        );

        RadialCategory debugTests = testing.addSubcategory("testing_debug", "Debug", DesignTokens.Radial.TOOLS_EDITOR,
            stack(Items.REDSTONE));
        debugTests.addItems(
            RadialMenuItem.registry(ActionIds.TEST_DEBUG_ON),
            RadialMenuItem.registry(ActionIds.TEST_DEBUG_OFF),
            RadialMenuItem.registry(ActionIds.TEST_DEBUG_TOGGLE),
            RadialMenuItem.registry(ActionIds.TEST_DEBUGBOX),
            RadialMenuItem.registry(ActionIds.TEST_DEBUGCLEAR)
        );

        RadialCategory enduranceTests = testing.addSubcategory("testing_endurance", "Endurance", DesignTokens.Radial.TOOLS_EDITOR,
            stack(Items.TOTEM_OF_UNDYING));
        enduranceTests.addItems(
            RadialMenuItem.registry(ActionIds.TEST_ENDURANCE_STATS),
            RadialMenuItem.registry(ActionIds.TEST_ENDURANCE_PERKS),
            RadialMenuItem.registry(ActionIds.TEST_ENDURANCE_SMOKE),
            RadialMenuItem.registry(ActionIds.TEST_ENDURANCE_KILLWAVE),
            RadialMenuItem.registry(ActionIds.TEST_ENDURANCE_DIE),
            RadialMenuItem.registry(ActionIds.TEST_ENDURANCE_SKIP_WAVE),
            RadialMenuItem.registry(ActionIds.TEST_ENDURANCE_EXPORT_TABLE),
            RadialMenuItem.registry(ActionIds.TEST_ENDURANCE_EXPORT_ALL),
            RadialMenuItem.registry(ActionIds.TEST_ENDURANCE_AUTOSMOKE)
        );

        RadialCategory miscTests = testing.addSubcategory("testing_misc", "Misc", DesignTokens.Radial.TOOLS_EDITOR,
            stack(Items.BOOK));
        miscTests.addItems(
            RadialMenuItem.registry(ActionIds.TEST_INFO),
            RadialMenuItem.registry(ActionIds.TEST_BODYPART_INFO),
            RadialMenuItem.registry(ActionIds.TEST_QA_OPEN)
        );

        RadialCategory qaTests = testing.addSubcategory("testing_qa", "QA", DesignTokens.Radial.TOOLS_EDITOR,
            stack(Items.NOTE_BLOCK));
        qaTests.addItems(
            RadialMenuItem.registry(ActionIds.QA_SESSION_START),
            RadialMenuItem.registry(ActionIds.QA_SESSION_RESUME),
            RadialMenuItem.registry(ActionIds.QA_REPORT_SAVE),
            RadialMenuItem.registry(ActionIds.QA_REPORT_COPY),
            RadialMenuItem.registry(ActionIds.QA_TEST_PASS),
            RadialMenuItem.registry(ActionIds.QA_TEST_FAIL),
            RadialMenuItem.registry(ActionIds.QA_TEST_SKIP),
            RadialMenuItem.registry(ActionIds.QA_TEST_AUTO)
        );

        categories.add(testing);

        // Category 3: Mob Editor
        RadialCategory.Builder mobEditorBuilder = RadialCategory.builder("mobeditor")
            .name("Mob Edit")
            .color(DesignTokens.Radial.TOOLS_SECONDARY)
            .icon("")
            .iconStack(stack(Items.LEAD));
        if (mobEditorItemSupplier != null) {
            mobEditorBuilder.item(mobEditorItemSupplier.get());
        }
        mobEditorBuilder.item(RadialMenuItem.registry(ActionIds.UI_MOB_EQUIPMENT_OPEN));
        mobEditorBuilder.item(RadialMenuItem.registry(ActionIds.UI_ROOM_BOUNDS_EDITOR_OPEN));
        categories.add(mobEditorBuilder.build());

        // Category 4: Item Editors
        // Note: Using dynamic visibility suppliers and fresh getHeldItem() in actions
        // so the menu responds to item changes in real-time
        RadialMenuItem autoEditor = RadialMenuItem.registry(ActionIds.UI_ITEM_EDITOR_OPEN_AUTO);

        RadialMenuItem weaponEditor = RadialMenuItem.registry(ActionIds.UI_ITEM_EDITOR_OPEN_WEAPON)
            .setVisibilitySupplier(() -> isWeaponItem(getHeldItem()));

        RadialMenuItem armorEditor = RadialMenuItem.registry(ActionIds.UI_ITEM_EDITOR_OPEN_ARMOR)
            .setVisibilitySupplier(() -> isArmorItem(getHeldItem()));

        RadialMenuItem shieldEditor = RadialMenuItem.registry(ActionIds.UI_ITEM_EDITOR_OPEN_SHIELD)
            .setCustomColor(DesignTokens.Radial.COMBAT_SHIELD)
            .setVisibilitySupplier(() -> isShieldItem(getHeldItem()));

        RadialMenuItem generalEditor = RadialMenuItem.registry(ActionIds.UI_ITEM_EDITOR_OPEN_GENERAL)
            .setVisibilitySupplier(() -> isGeneralItem(getHeldItem()));

        RadialMenuItem recipeEditor = RadialMenuItem.registry(ActionIds.UI_ITEM_EDITOR_OPEN_RECIPE);

        RadialMenuItem foodEditor = RadialMenuItem.registry(ActionIds.UI_ITEM_EDITOR_OPEN_FOOD)
            .setVisibilitySupplier(() -> isFoodItem(getHeldItem()));

        RadialMenuItem fuelEditor = RadialMenuItem.registry(ActionIds.UI_ITEM_EDITOR_OPEN_FUEL)
            .setVisibilitySupplier(() -> isFuelItem(getHeldItem()));

        RadialMenuItem usableEditor = RadialMenuItem.registry(ActionIds.UI_ITEM_EDITOR_OPEN_USABLE)
            .setVisibilitySupplier(() -> isUsableItem(getHeldItem()));

        categories.add(RadialCategory.builder("itemeditors")
            .name("Items")
            .color(DesignTokens.Radial.TOOLS_UTILITY)
            .icon("")
            .iconStack(stack(Items.DIAMOND_SWORD))
            .item(RadialMenuItem.registry(ActionIds.UI_EDITOR_HUB_OPEN))
            .item(autoEditor)
            .item(weaponEditor)
            .item(armorEditor)
            .item(shieldEditor)
            .item(generalEditor)
            .item(recipeEditor)
            .item(foodEditor)
            .item(fuelEditor)
            .item(usableEditor)
            .build());

        // Category 5: Commands
        RadialCategory commands = RadialCategory.builder("commands")
            .name("Commands")
            .color(DesignTokens.Radial.TOOLS_COMMANDS)
            .icon("")
            .iconStack(stack(Items.COMMAND_BLOCK))
            .item(RadialMenuItem.registry(ActionIds.COMMAND_GAMEMODE_CREATIVE))
            .item(RadialMenuItem.registry(ActionIds.COMMAND_GAMEMODE_SURVIVAL))
            .item(RadialMenuItem.registry(ActionIds.COMMAND_HEAL))
            .item(RadialMenuItem.registry(ActionIds.COMMAND_TIME_DAY))
            .item(RadialMenuItem.registry(ActionIds.COMMAND_TIME_NIGHT))
            .item(RadialMenuItem.registry(ActionIds.COMMAND_WEATHER_CLEAR))
            .build();

        RadialCategory nexus = commands.addSubcategory("nexus", "Nexus", DesignTokens.Radial.TOOLS_COMMANDS,
            stack(Items.NETHER_STAR));
        nexus.addItems(
            RadialMenuItem.registry(ActionIds.UI_NEXUS_HUB_OPEN),
            RadialMenuItem.registry(ActionIds.COMMAND_NEXUS_HELP),
            RadialMenuItem.registry(ActionIds.COMMAND_NEXUS_STATUS),
            RadialMenuItem.registry(ActionIds.COMMAND_NEXUS_ZONES)
        );

        RadialCategory nexusAccess = nexus.addSubcategory("nexus_access", "Access", DesignTokens.Radial.TOOLS_COMMANDS,
            stack(Items.ENDER_PEARL));
        nexusAccess.addItems(
            RadialMenuItem.registry(ActionIds.COMMAND_NEXUS_ENTER),
            RadialMenuItem.registry(ActionIds.COMMAND_NEXUS_RETURN)
        );

        RadialCategory nexusCombat = nexus.addSubcategory("nexus_combat", "Combat", DesignTokens.Radial.TOOLS_COMMANDS,
            stack(Items.IRON_SWORD));
        nexusCombat.addItems(
            RadialMenuItem.registry(ActionIds.COMMAND_NEXUS_TP_HUB),
            RadialMenuItem.registry(ActionIds.COMMAND_NEXUS_TP_COMBAT_LAB),
            RadialMenuItem.registry(ActionIds.COMMAND_NEXUS_TP_ABILITIES_LAB),
            RadialMenuItem.registry(ActionIds.COMMAND_NEXUS_TP_BOSS_ARENA),
            RadialMenuItem.registry(ActionIds.COMMAND_NEXUS_TP_COLLISION_LAB)
        );

        RadialCategory nexusSystems = nexus.addSubcategory("nexus_systems", "Systems", DesignTokens.Radial.TOOLS_COMMANDS,
            stack(Items.REDSTONE_LAMP));
        nexusSystems.addItems(
            RadialMenuItem.registry(ActionIds.COMMAND_NEXUS_TP_PORTAL_LAB),
            RadialMenuItem.registry(ActionIds.COMMAND_NEXUS_TP_NPC_LAB),
            RadialMenuItem.registry(ActionIds.COMMAND_NEXUS_TP_QUEST_TESTING),
            RadialMenuItem.registry(ActionIds.COMMAND_NEXUS_TP_ARENA_BUILDER)
        );

        RadialCategory nexusTools = nexus.addSubcategory("nexus_tools", "Tools", DesignTokens.Radial.TOOLS_COMMANDS,
            stack(Items.COMPARATOR));
        nexusTools.addItems(
            RadialMenuItem.registry(ActionIds.COMMAND_NEXUS_TP_VFX_STUDIO),
            RadialMenuItem.registry(ActionIds.COMMAND_NEXUS_TP_ITEM_WORKSHOP),
            RadialMenuItem.registry(ActionIds.COMMAND_NEXUS_TP_CONFIG_ROOM),
            RadialMenuItem.registry(ActionIds.COMMAND_NEXUS_TP_HUD_TESTING)
        );

        RadialCategory nexusMisc = nexus.addSubcategory("nexus_misc", "Misc", DesignTokens.Radial.TOOLS_COMMANDS,
            stack(Items.MAP));
        nexusMisc.addItems(
            RadialMenuItem.registry(ActionIds.COMMAND_NEXUS_TP_SANDBOX),
            RadialMenuItem.registry(ActionIds.COMMAND_NEXUS_TP_ADMIN_TOOLS)
        );

        RadialCategory nexusAdmin = nexus.addSubcategory("nexus_admin", "Admin", DesignTokens.Radial.TOOLS_COMMANDS,
            stack(Items.BARRIER));
        nexusAdmin.addItems(
            RadialMenuItem.registry(ActionIds.COMMAND_NEXUS_REBUILD),
            RadialMenuItem.registry(ActionIds.COMMAND_NEXUS_LOCK),
            RadialMenuItem.registry(ActionIds.COMMAND_NEXUS_UNLOCK)
        );

        RadialCategory nexusAvatar = nexus.addSubcategory("nexus_avatar", "Avatar", DesignTokens.Radial.TOOLS_COMMANDS,
            stack(Items.PLAYER_HEAD));
        nexusAvatar.addItems(
            RadialMenuItem.registry(ActionIds.COMMAND_NEXUS_AVATAR_STATUS),
            RadialMenuItem.registry(ActionIds.COMMAND_NEXUS_AVATAR_SPAWN),
            RadialMenuItem.registry(ActionIds.COMMAND_NEXUS_AVATAR_REMOVE)
        );

        RadialCategory nexusPortals = nexus.addSubcategory("nexus_portals", "Portals", DesignTokens.Radial.TOOLS_COMMANDS,
            stack(Items.ENDER_EYE));
        nexusPortals.addItems(
            RadialMenuItem.registry(ActionIds.COMMAND_NEXUS_RIFTSTAMP)
        );

        categories.add(commands);
    }

    // ================================================================
    // MACRO: PLAY - Gameplay features, quests, multiplayer
    // ================================================================

    private static void buildPlayCategories(Map<MacroCategory, List<RadialCategory>> map) {
        List<RadialCategory> categories = requireCategories(map, MacroCategory.PLAY);

        // Category 1: Endurance
        categories.add(RadialCategory.builder("endurance_core")
            .name("Endurance")
            .color(DesignTokens.Radial.PLAY_PARTY)
            .icon("")
            .iconStack(stack(Items.TOTEM_OF_UNDYING))
            .item(RadialMenuItem.registry(ActionIds.UI_ENDURANCE_SCREEN_OPEN))
            .item(RadialMenuItem.registry(ActionIds.UI_ENDURANCE_SHOP_OPEN))
            .item(RadialMenuItem.registry(ActionIds.UI_SEASON_PASS_OPEN))
            .item(RadialMenuItem.registry(ActionIds.ENDURANCE_QUEST_START))
            .item(RadialMenuItem.registry(ActionIds.ENDURANCE_QUEST_CONTINUE))
            .item(RadialMenuItem.registry(ActionIds.ENDURANCE_QUEST_EXIT))
            .build());

        // Category 2: Quests
        categories.add(RadialCategory.builder("quest_tools")
            .name("Quests")
            .color(DesignTokens.Radial.PLAY_SOCIAL)
            .icon("")
            .iconStack(stack(Items.FEATHER))
            .item(RadialMenuItem.registry(ActionIds.UI_QUEST_EDITOR_OPEN))
            .item(RadialMenuItem.registry(ActionIds.UI_ENDURANCE_EDITOR_OPEN))
            .item(RadialMenuItem.registry(ActionIds.UI_ENDURANCE_SETTINGS_OPEN))
            .item(RadialMenuItem.registry(ActionIds.QUEST_TASK_COMPLETE))
            .item(RadialMenuItem.registry(ActionIds.UI_PERK_SELECTION_OPEN))
            .item(RadialMenuItem.registry(ActionIds.UI_WAVE_CHECKPOINT_OPEN))
            .item(RadialMenuItem.registry(ActionIds.UI_QUEST_DEATH_OPEN))
            .item(RadialMenuItem.registry(ActionIds.UI_QUEST_COMPLETION_OPEN))
            .build());

        // Category 3: Endurance HUD
        categories.add(RadialCategory.builder("endurance_hud")
            .name("HUD")
            .color(DesignTokens.Radial.PLAY_QUESTS)
            .icon("")
            .iconStack(stack(Items.MAP))
            .item(RadialMenuItem.registry(ActionIds.HUD_ENDURANCE_TOGGLE))
            .item(RadialMenuItem.registry(ActionIds.HUD_ENDURANCE_DETAILS_TOGGLE))
            .item(RadialMenuItem.registry(ActionIds.HUD_QUEST_TOGGLE))
            .build());

        // Category 4: Party
        categories.add(RadialCategory.builder("party")
            .name("Party")
            .color(DesignTokens.Radial.PLAY_COMMS)
            .icon("")
            .iconStack(stack(Items.PLAYER_HEAD))
            .item(RadialMenuItem.registry(ActionIds.HUD_PARTY_TOGGLE))
            .item(RadialMenuItem.registry(ActionIds.UI_PARTY_OPEN))
            .item(RadialMenuItem.registry(ActionIds.UI_PARTY_INVITE_POPUP_OPEN))
            .item(RadialMenuItem.registry(ActionIds.UI_NOTIFICATION_CENTER_OPEN))
            .item(RadialMenuItem.registry(ActionIds.UI_NOTIFICATION_SETTINGS_OPEN))
            .build());
    }
}
