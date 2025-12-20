package com.frenkvs.devmod.ui.radial;

import com.frenkvs.devmod.ModConfig;
import com.frenkvs.devmod.hud.*;
import com.frenkvs.devmod.rendering.*;
import com.frenkvs.devmod.telemetry.FpsTracker;
import com.frenkvs.devmod.ArmorConfigManager;
import com.frenkvs.devmod.ui.editor.ItemEditorScreen;
import com.frenkvs.devmod.ui.editor.EditorStartTab;
import com.frenkvs.devmod.ui.editor.WeaponTypeDetector;
import com.frenkvs.devmod.ui.radial.model.MacroCategory;
import com.frenkvs.devmod.ui.testing.VoxelLabUiTestScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.*;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Registry for radial menu category definitions.
 *
 * This class centralizes all 24 category definitions (6 per macro-category)
 * to keep RadialMenuScreenV3 focused on UI logic.
 *
 * Structure:
 * - ANALYZE (6): Debug, Spatial, Perf, Light, Paths, Entity
 * - COMBAT (6): Stats, Editors, Heatmaps, Boss, Economy, Attrs
 * - TOOLS (6): Settings, Dashboard, Testing, Mob Edit, Items, Commands
 * - PLAY (6): Quests, Endurance, Party, Achieve, Challenges, Leaders
 */
public final class RadialMenuRegistry {

    private RadialMenuRegistry() {} // Utility class

    /**
     * Creates an ItemStack with explicit null-safety check.
     * Minecraft Items are guaranteed non-null at runtime, but the type system
     * cannot verify this. This helper provides explicit null assertion.
     */
    private static ItemStack stack(net.minecraft.world.item.Item item) {
        return new ItemStack(Objects.requireNonNull(item));
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
        return stack != null && ArmorConfigManager.isArmor(stack);
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
        return stack.getItem().components().has(Objects.requireNonNull(net.minecraft.core.component.DataComponents.FOOD));
    }

    private static boolean isFuelItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        // Check if item has a burn time
        return stack.getBurnTime(Objects.requireNonNull(RecipeType.SMELTING)) > 0;
    }

    private static boolean isUsableItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var item = stack.getItem();
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
        if (player != null && item.getUseDuration(stack, player) > 0 && !isFoodItem(stack)) {
            return true;
        }
        return false;
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
            Supplier<RadialMenuItem> mobEditorItemSupplier) {

        Map<MacroCategory, List<RadialCategory>> map = new EnumMap<>(MacroCategory.class);

        for (MacroCategory macro : MacroCategory.values()) {
            map.put(macro, new ArrayList<>());
        }

        // Build all categories
        buildAnalyzeCategories(map);
        buildCombatCategories(map);
        buildToolsCategories(map, mobEditorItemSupplier);
        buildPlayCategories(map);

        return map;
    }

    // ================================================================
    // MACRO: ANALYZE - Debug, visualization, analysis tools
    // ================================================================

    private static void buildAnalyzeCategories(Map<MacroCategory, List<RadialCategory>> map) {
        List<RadialCategory> categories = map.get(MacroCategory.ANALYZE);

        // Category 1: Debug Overlays
        categories.add(RadialCategory.builder("debug")
            .name("Debug")
            .color(0xFF4488FF)
            .icon("\uD83D\uDC41") // eye
            .iconStack(stack(Items.ENDER_EYE))
            .item(RadialMenuItem.toggle("Body Parts", "\uD83C\uDFAF",
                stack(Items.ARMOR_STAND),
                () -> ModConfig.showBodyPartBoxes,
                v -> ModConfig.showBodyPartBoxes = v,
                "Show body part hitboxes on entities"))
            .item(RadialMenuItem.toggle("Mob Debug", "\uD83D\uDC7E",
                stack(Items.ZOMBIE_HEAD),
                () -> DebugRenderer.INSTANCE.isEnabled(),
                v -> DebugRenderer.INSTANCE.setEnabled(v),
                "Show mob stats, hitboxes & aggro ranges"))
            .item(RadialMenuItem.toggle("Line of Sight", "\uD83D\uDC40",
                stack(Items.SPYGLASS),
                () -> LineOfSightVisualizer.INSTANCE.isEnabled(),
                v -> LineOfSightVisualizer.INSTANCE.setEnabled(v),
                "Show mob vision cones"))
            .build());

        // Category 2: Spatial Analysis
        categories.add(RadialCategory.builder("spatial")
            .name("Spatial")
            .color(0xFF66AAFF)
            .icon("\uD83D\uDDFA") // map
            .iconStack(stack(Items.FILLED_MAP))
            .item(RadialMenuItem.toggle("Room Bounds", "\uD83C\uDFE0",
                stack(Items.STRUCTURE_BLOCK),
                () -> RoomBoundsVisualizer.INSTANCE.isEnabled(),
                v -> RoomBoundsVisualizer.INSTANCE.setEnabled(v),
                "Detect and highlight room boundaries"))
            .item(RadialMenuItem.toggle("Vertical Levels", "\uD83D\uDCF6",
                stack(Items.LADDER),
                () -> VerticalLevelsVisualizer.INSTANCE.isEnabled(),
                v -> VerticalLevelsVisualizer.INSTANCE.setEnabled(v),
                "Show Y-level zone layers"))
            .item(RadialMenuItem.toggle("Safe Spots", "\uD83D\uDEE1",
                stack(Items.SHIELD),
                () -> SafeSpotVisualizer.INSTANCE.isEnabled(),
                v -> SafeSpotVisualizer.INSTANCE.setEnabled(v),
                "Highlight camping positions"))
            .build());

        // Category 3: Performance
        categories.add(RadialCategory.builder("perf")
            .name("Perf")
            .color(0xFF88CCFF)
            .icon("\uD83D\uDCCA") // chart
            .iconStack(stack(Items.CLOCK))
            .item(RadialMenuItem.toggle("FPS Tracker", "\uD83C\uDFAE",
                stack(Items.CLOCK),
                () -> FpsTracker.INSTANCE.isEnabled(),
                v -> FpsTracker.INSTANCE.setEnabled(v),
                "Display FPS graph and statistics"))
            .item(RadialMenuItem.toggle("Entity Density", "\uD83D\uDC65",
                stack(Items.VILLAGER_SPAWN_EGG),
                () -> EntityDensityOverlay.isEnabled(),
                v -> EntityDensityOverlay.setEnabled(v),
                "Show entity count per area"))
            .item(RadialMenuItem.toggle("Profiler", "\u23F1",
                stack(Items.REDSTONE),
                () -> com.frenkvs.devmod.telemetry.PerformanceProfiler.INSTANCE.isEnabled(),
                v -> com.frenkvs.devmod.telemetry.PerformanceProfiler.INSTANCE.setEnabled(v),
                "Performance profiling overlay"))
            .build());

        // Category 4: Light & Spawn
        categories.add(RadialCategory.builder("lightspawn")
            .name("Light")
            .color(0xFFAADDFF)
            .icon("\uD83D\uDCA1") // light bulb
            .iconStack(stack(Items.TORCH))
            .item(RadialMenuItem.toggle("Light Levels", "\uD83D\uDCA1",
                stack(Items.TORCH),
                () -> LightLevelOverlay.INSTANCE.isEnabled(),
                v -> LightLevelOverlay.INSTANCE.setEnabled(v),
                "Display spawn light levels on blocks"))
            .item(RadialMenuItem.toggle("Spawnability", "\uD83D\uDC7E",
                stack(Items.SPAWNER),
                () -> SpawnabilityOverlay.INSTANCE.isEnabled(),
                v -> SpawnabilityOverlay.INSTANCE.setEnabled(v),
                "Mark potential mob spawn zones"))
            .item(RadialMenuItem.toggle("Chunk Perf", "\uD83D\uDCE6",
                stack(Items.CHEST),
                () -> ChunkPerformanceVisualizer.INSTANCE.isEnabled(),
                v -> ChunkPerformanceVisualizer.INSTANCE.setEnabled(v),
                "Show chunk render performance"))
            .build());

        // Category 5: Pathfinding
        categories.add(RadialCategory.builder("pathfinding")
            .name("Paths")
            .color(0xFFCCEEFF)
            .icon("\uD83D\uDEE4") // rail
            .iconStack(stack(Items.RAIL))
            .item(RadialMenuItem.toggle("Pathfinding", "\uD83D\uDEE4",
                stack(Items.RAIL),
                () -> PathfindingDebugger.INSTANCE.isEnabled(),
                v -> PathfindingDebugger.INSTANCE.setEnabled(v),
                "Visualize mob pathfinding routes"))
            .build());

        // Category 6: Entity Debug
        categories.add(RadialCategory.builder("entitydebug")
            .name("Entity")
            .color(0xFFEEFFFF)
            .icon("\uD83D\uDD0D") // magnifying glass
            .iconStack(stack(Items.SPECTRAL_ARROW))
            .item(RadialMenuItem.toggle("Attr Monitor", "\uD83D\uDCC8",
                stack(Items.EXPERIENCE_BOTTLE),
                () -> com.frenkvs.devmod.attributes.AttributeMonitoringSystem.INSTANCE.isEnabled(),
                v -> com.frenkvs.devmod.attributes.AttributeMonitoringSystem.INSTANCE.setEnabled(v),
                "Track entity attributes in real-time"))
            .build());
    }

    // ================================================================
    // MACRO: COMBAT - Combat analysis and tools
    // ================================================================

    private static void buildCombatCategories(Map<MacroCategory, List<RadialCategory>> map) {
        List<RadialCategory> categories = map.get(MacroCategory.COMBAT);

        // Category 1: Combat Stats
        categories.add(RadialCategory.builder("combatstats")
            .name("Stats")
            .color(0xFFFF4444)
            .icon("\u2694") // crossed swords
            .iconStack(stack(Items.DIAMOND_SWORD))
            .item(RadialMenuItem.toggle("Impact HUD", "\uD83D\uDCA5",
                stack(Items.NETHERITE_SWORD),
                () -> ImpactHudOverlay.isEnabled(),
                v -> ImpactHudOverlay.setEnabled(v),
                "Show real-time damage breakdown on hit"))
            .item(RadialMenuItem.toggle("Boss Phases", "\uD83D\uDC79",
                stack(Items.WITHER_SKELETON_SKULL),
                () -> BossPhaseOverlay.isEnabled(),
                v -> BossPhaseOverlay.setEnabled(v),
                "Display boss phase information"))
            .item(RadialMenuItem.toggle("Skill Efficacy", "\u2728",
                stack(Items.ENCHANTED_BOOK),
                () -> SkillEfficacyOverlay.isEnabled(),
                v -> SkillEfficacyOverlay.setEnabled(v),
                "Track skill effectiveness"))
            .build());

        // Category 2: Heatmaps
        categories.add(RadialCategory.builder("heatmaps")
            .name("Heatmaps")
            .color(0xFFFF8888)
            .icon("\uD83D\uDD25") // fire
            .iconStack(stack(Items.BLAZE_POWDER))
            .item(RadialMenuItem.toggle("Death Heatmap", "\uD83D\uDC80",
                stack(Items.BONE),
                () -> HeatmapVisualizer.INSTANCE.hasActiveHeatmaps(),
                v -> HeatmapVisualizer.INSTANCE.toggle(HeatmapVisualizer.HeatmapType.DEATH),
                "Toggle death heatmap visualization"))
            .build());

        // Category 3: Boss Tools
        categories.add(RadialCategory.builder("bosstools")
            .name("Boss")
            .color(0xFFFFAAAA)
            .icon("\uD83D\uDC79") // ogre
            .iconStack(stack(Items.DRAGON_HEAD))
            .build());

        // Category 4: Economy
        categories.add(RadialCategory.builder("economy")
            .name("Economy")
            .color(0xFFFFCCCC)
            .icon("\uD83D\uDCB0") // money bag
            .iconStack(stack(Items.GOLD_INGOT))
            .item(RadialMenuItem.toggle("Economy", "\uD83D\uDCB0",
                stack(Items.GOLD_INGOT),
                () -> EconomyOverlay.isEnabled(),
                v -> EconomyOverlay.setEnabled(v),
                "Show loot and gold statistics"))
            .build());

        // Category 5: Attributes
        categories.add(RadialCategory.builder("attributes")
            .name("Attrs")
            .color(0xFFFFEEEE)
            .icon("\uD83D\uDCCA") // chart
            .iconStack(stack(Items.NETHER_STAR))
            .build());

        // Category 6: Combat Analysis
        categories.add(RadialCategory.builder("combatanalysis")
            .name("Analysis")
            .color(0xFFFFFFFF)
            .icon("\uD83D\uDD0D") // magnifying glass
            .iconStack(stack(Items.SPYGLASS))
            .build());
    }

    // ================================================================
    // MACRO: TOOLS - Configuration and utility tools
    // ================================================================

    private static void buildToolsCategories(Map<MacroCategory, List<RadialCategory>> map,
            Supplier<RadialMenuItem> mobEditorItemSupplier) {
        List<RadialCategory> categories = map.get(MacroCategory.TOOLS);

        // Category 1: Settings
        categories.add(RadialCategory.builder("settings")
            .name("Settings")
            .color(0xFFFFAA00)
            .icon("\u2699") // gear
            .iconStack(stack(Items.COMPARATOR))
            .item(RadialMenuItem.screen("Settings", "\u2699",
                stack(Items.COMPARATOR),
                () -> new com.frenkvs.devmod.ui.unified.UnifiedSettingsScreen(null),
                "Open DevMod configuration"))
            .item(RadialMenuItem.toggle("Quick Help", "\u2753",
                stack(Items.KNOWLEDGE_BOOK),
                () -> QuickHelpOverlay.isEnabled(),
                v -> QuickHelpOverlay.setEnabled(v),
                "Show keybind help overlay"))
            .build());

        // Category 2: Dashboard
        categories.add(RadialCategory.builder("dashboard")
            .name("Dashboard")
            .color(0xFFFFBB33)
            .icon("\uD83D\uDCCB") // clipboard
            .iconStack(stack(Items.WRITABLE_BOOK))
            .item(RadialMenuItem.screen("Dashboard", "\uD83D\uDCCB",
                stack(Items.WRITABLE_BOOK),
                () -> new com.frenkvs.devmod.TelemetryDashboardScreen(null),
                "Telemetry analytics dashboard"))
            .build());

        // Category 3: Testing
        categories.add(RadialCategory.builder("testing")
            .name("Testing")
            .color(0xFFFFCC66)
            .icon("\uD83E\uDDEA") // test tube
            .iconStack(stack(Items.BREWING_STAND))
            .item(RadialMenuItem.screen("Testing Hub", "\uD83E\uDDEA",
                stack(Items.BREWING_STAND),
                () -> new com.frenkvs.devmod.ui.hub.TestingHub(),
                "QA testing and validation tools"))
            .item(RadialMenuItem.screen("Quick Test", "\u26A1",
                stack(Items.LIGHTNING_ROD),
                () -> new com.frenkvs.devmod.ui.wizard.QuickTestWizard(),
                "Guided workflow to start testing"))
            .item(RadialMenuItem.screen("Badge Tests", "\uD83C\uDFC6",
                stack(Items.NETHER_STAR),
                () -> new com.frenkvs.devmod.testing.BadgeTestScreen(),
                "Test badge popup animations & sounds"))
            .item(RadialMenuItem.screen("UI Test Lab", "\uD83D\uDD8C",
                stack(Items.GLOW_ITEM_FRAME),
                VoxelLabUiTestScreen::new,
                "Voxel Lab: mostra e interagisci con tutte le varianti di EditorButton"))
            .build());

        // Category 4: Mob Editor
        RadialCategory.Builder mobEditorBuilder = RadialCategory.builder("mobeditor")
            .name("Mob Edit")
            .color(0xFFFFDD99)
            .icon("\uD83D\uDC3E") // paw
            .iconStack(stack(Items.LEAD));
        if (mobEditorItemSupplier != null) {
            mobEditorBuilder.item(mobEditorItemSupplier.get());
        }
        categories.add(mobEditorBuilder.build());

        // Category 5: Item Editors
        // Note: Using dynamic visibility suppliers and fresh getHeldItem() in actions
        // so the menu responds to item changes in real-time
        RadialMenuItem weaponEditor = RadialMenuItem.screen("Weapon Editor", "\uD83D\uDDE1",
            stack(Items.DIAMOND_SWORD),
            () -> new ItemEditorScreen(getHeldItem(), EditorStartTab.WEAPON),
            "Edit weapon stats and body part multipliers")
            .setVisibilitySupplier(() -> isWeaponItem(getHeldItem()));

        RadialMenuItem armorEditor = RadialMenuItem.screen("Armor Editor", "\uD83D\uDEE1",
            stack(Items.DIAMOND_CHESTPLATE),
            () -> new ItemEditorScreen(getHeldItem(), EditorStartTab.ARMOR),
            "Edit armor protection and attributes")
            .setVisibilitySupplier(() -> isArmorItem(getHeldItem()));

        RadialMenuItem shieldEditor = RadialMenuItem.screen("Shield Editor", "盾",
            stack(Items.SHIELD),
            () -> new ItemEditorScreen(getHeldItem(), EditorStartTab.ARMOR),
            "Edit shield block/reflect")
            .setCustomColor(0xFFDDDDDD)
            .setVisibilitySupplier(() -> isShieldItem(getHeldItem()));

        RadialMenuItem generalEditor = RadialMenuItem.screen("Item Editor", "\u2699",
            stack(Items.BOOK),
            () -> new ItemEditorScreen(getHeldItem(), EditorStartTab.GENERAL),
            "Edit generic item data")
            .setVisibilitySupplier(() -> isGeneralItem(getHeldItem()));

        RadialMenuItem recipeEditor = RadialMenuItem.screen("Recipe Editor", "\uD83D\uDCD6",
            stack(Items.CRAFTING_TABLE),
            () -> new ItemEditorScreen(getHeldItem(), EditorStartTab.RECIPE),
            "Create and edit crafting recipes");

        RadialMenuItem foodEditor = RadialMenuItem.screen("Food Editor", "\uD83C\uDF56",
            stack(Items.COOKED_BEEF),
            () -> new ItemEditorScreen(getHeldItem(), EditorStartTab.FOOD),
            "Edit nutrition, saturation, and effects")
            .setVisibilitySupplier(() -> isFoodItem(getHeldItem()));

        RadialMenuItem fuelEditor = RadialMenuItem.screen("Fuel Editor", "\uD83D\uDD25",
            stack(Items.COAL),
            () -> new ItemEditorScreen(getHeldItem(), EditorStartTab.FUEL),
            "Edit burn time and efficiency")
            .setVisibilitySupplier(() -> isFuelItem(getHeldItem()));

        RadialMenuItem usableEditor = RadialMenuItem.screen("Usable Editor", "\u23F1",
            stack(Items.SNOWBALL),
            () -> new ItemEditorScreen(getHeldItem(), EditorStartTab.USABLE),
            "Edit throwables, cooldowns, and use duration")
            .setVisibilitySupplier(() -> isUsableItem(getHeldItem()));

        categories.add(RadialCategory.builder("itemeditors")
            .name("Items")
            .color(0xFFFFEECC)
            .icon("\uD83D\uDDE1") // dagger
            .iconStack(stack(Items.DIAMOND_SWORD))
            .item(weaponEditor)
            .item(armorEditor)
            .item(shieldEditor)
            .item(generalEditor)
            .item(recipeEditor)
            .item(foodEditor)
            .item(fuelEditor)
            .item(usableEditor)
            .build());

        // Category 6: Commands
        categories.add(RadialCategory.builder("commands")
            .name("Commands")
            .color(0xFFFFFFEE)
            .icon("\uD83D\uDCDC") // scroll
            .iconStack(stack(Items.COMMAND_BLOCK))
            .item(RadialMenuItem.command("Gamemode Creative", "\uD83C\uDFA8",
                stack(Items.GRASS_BLOCK), "/gamemode creative", "Switch to creative mode"))
            .item(RadialMenuItem.command("Gamemode Survival", "\u2694",
                stack(Items.IRON_SWORD), "/gamemode survival", "Switch to survival mode"))
            .item(RadialMenuItem.command("Heal", "\u2764",
                stack(Items.GOLDEN_APPLE), "/heal", "Restore full health"))
            .item(RadialMenuItem.command("Time Day", "\u2600",
                stack(Items.SUNFLOWER), "/time set day", "Set time to day"))
            .item(RadialMenuItem.command("Time Night", "\uD83C\uDF19",
                stack(Items.CLOCK), "/time set night", "Set time to night"))
            .item(RadialMenuItem.command("Weather Clear", "\uD83C\uDF24",
                stack(Items.FEATHER), "/weather clear", "Clear the weather"))
            .build());
    }

    // ================================================================
    // MACRO: PLAY - Gameplay features, quests, multiplayer
    // ================================================================

    private static void buildPlayCategories(Map<MacroCategory, List<RadialCategory>> map) {
        List<RadialCategory> categories = map.get(MacroCategory.PLAY);

        // Category 1: Quest System
        categories.add(RadialCategory.builder("questsystem")
            .name("Quests")
            .color(0xFF44FF88)
            .icon("\uD83D\uDCDC") // scroll
            .iconStack(stack(Items.PAPER))
            .item(RadialMenuItem.toggle("Quest HUD", "\uD83D\uDCCB",
                stack(Items.MAP),
                () -> com.frenkvs.devmod.quest.QuestHudOverlay.isEnabled(),
                v -> com.frenkvs.devmod.quest.QuestHudOverlay.setEnabled(v),
                "Quest tracker overlay"))
            .item(RadialMenuItem.screen("Quest Editor", "\u270F",
                stack(Items.FEATHER),
                () -> new com.frenkvs.devmod.quest.QuestEditorScreen(),
                "Create and edit quests"))
            .build());

        // Category 2: Endurance Mode
        categories.add(RadialCategory.builder("endurance")
            .name("Endurance")
            .color(0xFF66FFAA)
            .icon("\uD83D\uDCAA") // flexed bicep
            .iconStack(stack(Items.IRON_CHESTPLATE))
            .item(RadialMenuItem.toggle("Endurance HUD", "\uD83D\uDCAA",
                stack(Items.IRON_CHESTPLATE),
                () -> EnduranceQuestOverlay.isEnabled(),
                v -> EnduranceQuestOverlay.setEnabled(v),
                "Endurance quest overlay"))
            .item(RadialMenuItem.screen("Endurance", "\uD83C\uDFC6",
                stack(Items.GOLDEN_HELMET),
                () -> new com.frenkvs.devmod.endurance.EnduranceQuestScreen(),
                "Start endurance quest mode"))
            .build());

        // Category 3: Multiplayer
        categories.add(RadialCategory.builder("multiplayer")
            .name("Party")
            .color(0xFF88FFCC)
            .icon("\uD83D\uDC65") // busts
            .iconStack(stack(Items.PLAYER_HEAD))
            .item(RadialMenuItem.screen("Party", "\uD83D\uDC65",
                stack(Items.PLAYER_HEAD),
                () -> new com.frenkvs.devmod.party.PartyScreen(),
                "Party & multiplayer quests"))
            .build());

        // Category 4: Achievements
        categories.add(RadialCategory.builder("achievements")
            .name("Achieve")
            .color(0xFFAAFFDD)
            .icon("\uD83C\uDFC5") // medal
            .iconStack(stack(Items.GOLDEN_APPLE))
            .build());

        // Category 5: Challenges
        categories.add(RadialCategory.builder("challenges")
            .name("Challenges")
            .color(0xFFCCFFEE)
            .icon("\u26A1") // lightning
            .iconStack(stack(Items.DRAGON_BREATH))
            .build());

        // Category 6: Leaderboards
        categories.add(RadialCategory.builder("leaderboards")
            .name("Leaders")
            .color(0xFFEEFFFF)
            .icon("\uD83C\uDFC6") // trophy
            .iconStack(stack(Items.GOLD_BLOCK))
            .build());
    }
}
