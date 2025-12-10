package com.frenkvs.devmod.ui.radial;

import com.frenkvs.devmod.ModConfig;
import com.frenkvs.devmod.hud.*;
import com.frenkvs.devmod.rendering.*;
import com.frenkvs.devmod.telemetry.FpsTracker;
import com.frenkvs.devmod.ui.unified.persistence.SettingsManager;
import com.frenkvs.devmod.util.I18n;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * RADIAL MENU V3 - CLEAN & USABLE EDITION
 *
 * Focus on usability:
 * - Clean, readable layout with proper spacing
 * - Large clickable areas - no overlap
 * - Fast, non-distracting animations
 * - Fuzzy search (type to filter)
 * - Optional favorites in center ring
 * - Keyboard shortcuts for power users
 */
public class RadialMenuScreenV3 extends Screen {

    // === Configuration ===
    private final RadialMenuConfig config = RadialMenuConfig.INSTANCE;

    // === Layout Constants ===
    private int innerRadius;
    private int outerRadius;
    private int itemRadius;
    private int centerButtonRadius;
    private int favoritesRadius;

    // === Menu Structure ===
    private final List<RadialCategory> rootCategories = new ArrayList<>();
    private final Stack<RadialCategory> navigationStack = new Stack<>();
    private RadialCategory currentCategory = null;

    // === Selection State ===
    private int selectedCategoryIndex = -1;
    private int selectedItemIndex = -1;
    private int selectedFavoriteIndex = -1;
    private int prevSelectedCategory = -1;
    private int prevSelectedItem = -1;
    private int centerX, centerY;

    // === Animation State ===
    private float openAnimation = 0f;
    private float categoryHoverAnim = 0f;
    private float pulsePhase = 0f;
    private float rotationOffset = 0f;
    private float[] categoryAnimations;
    private float[] itemAnimations;
    private float[] favoriteAnimations;
    private boolean closing = false;
    private long openTime;

    // === Advanced Animation ===
    private float glowIntensity = 0f;
    private float targetGlowIntensity = 0.5f;
    private int glowColor = 0xFF00AAFF;
    private float wavePhase = 0f;
    private float morphProgress = 0f;
    private int morphFromCategory = -1;

    // === Particle System ===
    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();
    private long lastParticleSpawn = 0;

    // === Search System ===
    private boolean searchMode = false;
    private StringBuilder searchQuery = new StringBuilder();
    private List<SearchResult> searchResults = new ArrayList<>();
    private int selectedSearchResult = -1;
    private float searchBoxAnimation = 0f;

    // === Favorites System ===
    private final List<FavoriteItem> favorites = new ArrayList<>();
    private static final int MAX_FAVORITES = 8;

    // === Usage Statistics ===
    private final Map<String, Integer> usageStats = new HashMap<>();
    private List<RadialMenuItem> mostUsedItems = new ArrayList<>();

    // === Context Awareness ===
    private final List<RadialMenuItem> contextSuggestions = new ArrayList<>();
    private String currentContext = "";

    // === Gesture Recognition ===
    private final List<GesturePoint> gesturePoints = new ArrayList<>();
    private long lastGestureTime = 0;
    private boolean gestureActive = false;

    // === Edit Mode ===
    private boolean editMode = false;

    // === Cached Entity ===
    @Nullable
    private net.minecraft.world.entity.Entity cachedTargetEntity = null;

    public RadialMenuScreenV3() {
        super(Component.translatable("devmod.radial.title"));
        loadConfig();
        initializeCategories();
        loadUsageStats();
        loadFavorites();
        openTime = System.currentTimeMillis();
        cacheTargetEntity();
        // Removed: particles, gestures, context analysis for cleaner UX
    }

    private void loadConfig() {
        config.load();
        innerRadius = config.innerRadius;
        outerRadius = config.outerRadius;
        itemRadius = config.itemRadius;
        centerButtonRadius = config.centerButtonRadius;
        favoritesRadius = innerRadius - 15;
    }

    private void cacheTargetEntity() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult != null && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
            cachedTargetEntity = ((net.minecraft.world.phys.EntityHitResult) mc.hitResult).getEntity();
        }
    }

    // ================================================================
    // PARTICLE SYSTEM
    // ================================================================

    private void spawnOpeningParticles() {
        for (int i = 0; i < 30; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double speed = 2 + random.nextDouble() * 3;
            int color = config.theme.categoryColors[random.nextInt(config.theme.categoryColors.length)];
            particles.add(new Particle(
                centerX, centerY,
                (float)(Math.cos(angle) * speed),
                (float)(Math.sin(angle) * speed),
                color,
                0.8f + random.nextFloat() * 0.4f,
                3 + random.nextFloat() * 4
            ));
        }
    }

    private void spawnSelectionParticles(int x, int y, int color) {
        for (int i = 0; i < 8; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double speed = 1 + random.nextDouble() * 2;
            particles.add(new Particle(
                x, y,
                (float)(Math.cos(angle) * speed),
                (float)(Math.sin(angle) * speed),
                color,
                0.6f + random.nextFloat() * 0.3f,
                2 + random.nextFloat() * 2
            ));
        }
    }

    private void spawnTrailParticle(int x, int y, int color) {
        if (System.currentTimeMillis() - lastParticleSpawn > 30) {
            particles.add(new Particle(
                x + random.nextInt(10) - 5,
                y + random.nextInt(10) - 5,
                (random.nextFloat() - 0.5f) * 0.5f,
                (random.nextFloat() - 0.5f) * 0.5f - 0.3f,
                color,
                0.4f + random.nextFloat() * 0.2f,
                2 + random.nextFloat() * 2
            ));
            lastParticleSpawn = System.currentTimeMillis();
        }
    }

    private void updateParticles() {
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.update();
            if (p.isDead()) {
                it.remove();
            }
        }
    }

    // ================================================================
    // CONTEXT AWARENESS
    // ================================================================

    private void analyzeContext() {
        contextSuggestions.clear();
        Minecraft mc = Minecraft.getInstance();

        // Context: Looking at a mob
        if (cachedTargetEntity instanceof net.minecraft.world.entity.Mob) {
            currentContext = "mob_targeted";
            // Suggest mob-related tools
            addContextSuggestion("Mob Debug", "👾");
            addContextSuggestion("Line of Sight", "👀");
            addContextSuggestion("Pathfinding", "🛤");
        }
        // Context: In combat (low health or holding weapon)
        else if (mc.player != null && mc.player.getHealth() < mc.player.getMaxHealth() * 0.5f) {
            currentContext = "combat";
            addContextSuggestion("Boss Phases", "👹");
            addContextSuggestion("Safe Spots", "🛡");
        }
        // Context: Building (holding blocks)
        else if (mc.player != null && mc.player.getMainHandItem().getItem() instanceof net.minecraft.world.item.BlockItem) {
            currentContext = "building";
            addContextSuggestion("Light Levels", "💡");
            addContextSuggestion("Room Bounds", "🏠");
        }
        // Default context
        else {
            currentContext = "general";
            // Show most used
            updateMostUsed();
        }
    }

    private void addContextSuggestion(String itemName, String emoji) {
        for (RadialCategory cat : rootCategories) {
            for (RadialMenuItem item : cat.getItems()) {
                if (item.getName().equals(itemName)) {
                    contextSuggestions.add(item);
                    return;
                }
            }
        }
    }

    // ================================================================
    // SEARCH SYSTEM
    // ================================================================

    private void updateSearchResults() {
        searchResults.clear();
        if (searchQuery.length() == 0) return;

        String query = searchQuery.toString().toLowerCase();

        for (RadialCategory cat : rootCategories) {
            for (RadialMenuItem item : cat.getItems()) {
                String name = item.getName().toLowerCase();
                String desc = item.getDescription().toLowerCase();

                // Fuzzy match scoring
                int score = 0;
                if (name.startsWith(query)) score += 100;
                else if (name.contains(query)) score += 50;
                if (desc.contains(query)) score += 25;

                // Character-by-character fuzzy
                if (score == 0) {
                    int matchedChars = 0;
                    int lastIndex = -1;
                    for (char c : query.toCharArray()) {
                        int idx = name.indexOf(c, lastIndex + 1);
                        if (idx > lastIndex) {
                            matchedChars++;
                            lastIndex = idx;
                        }
                    }
                    if (matchedChars == query.length()) {
                        score = 10 + (10 - Math.min(10, name.length() - query.length()));
                    }
                }

                if (score > 0) {
                    searchResults.add(new SearchResult(item, cat, score));
                }
            }
        }

        // Sort by score descending
        searchResults.sort((a, b) -> b.score - a.score);

        // Limit results
        if (searchResults.size() > 8) {
            searchResults = new ArrayList<>(searchResults.subList(0, 8));
        }

        selectedSearchResult = searchResults.isEmpty() ? -1 : 0;
    }

    // ================================================================
    // USAGE STATISTICS
    // ================================================================

    private void loadUsageStats() {
        // In production, load from config file
        // For now, initialize empty
    }

    private void recordUsage(RadialMenuItem item) {
        String key = item.getName();
        usageStats.put(key, usageStats.getOrDefault(key, 0) + 1);
        updateMostUsed();
        // In production, save to config file
    }

    private void updateMostUsed() {
        mostUsedItems.clear();
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(usageStats.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());

        int count = 0;
        for (Map.Entry<String, Integer> entry : sorted) {
            if (count >= 5) break;
            for (RadialCategory cat : rootCategories) {
                for (RadialMenuItem item : cat.getItems()) {
                    if (item.getName().equals(entry.getKey())) {
                        mostUsedItems.add(item);
                        count++;
                        break;
                    }
                }
            }
        }
    }

    // ================================================================
    // FAVORITES SYSTEM
    // ================================================================

    private void loadFavorites() {
        // Would load from config in production
        favoriteAnimations = new float[MAX_FAVORITES];
    }

    private void toggleFavorite(RadialMenuItem item, RadialCategory category) {
        String key = category.getId() + ":" + item.getName();

        // Check if already favorite
        for (int i = 0; i < favorites.size(); i++) {
            if (favorites.get(i).key.equals(key)) {
                favorites.remove(i);
                playSound(0.7f);
                return;
            }
        }

        // Add to favorites
        if (favorites.size() < MAX_FAVORITES) {
            favorites.add(new FavoriteItem(key, item, category));
            playSound(1.3f);
            spawnSelectionParticles(centerX, centerY, 0xFFFFD700);
        }
    }

    private boolean isFavorite(RadialMenuItem item, RadialCategory category) {
        String key = category.getId() + ":" + item.getName();
        return favorites.stream().anyMatch(f -> f.key.equals(key));
    }

    // ================================================================
    // GESTURE RECOGNITION
    // ================================================================

    private void recordGesturePoint(int x, int y) {
        long now = System.currentTimeMillis();
        if (now - lastGestureTime > 500) {
            gesturePoints.clear();
        }
        gesturePoints.add(new GesturePoint(x, y, now));
        lastGestureTime = now;

        if (gesturePoints.size() > 50) {
            gesturePoints.remove(0);
        }

        // Detect gestures when enough points
        if (gesturePoints.size() >= 10) {
            detectGesture();
        }
    }

    private void detectGesture() {
        if (gesturePoints.size() < 10) return;

        // Calculate total direction
        GesturePoint first = gesturePoints.get(0);
        GesturePoint last = gesturePoints.get(gesturePoints.size() - 1);

        double dx = last.x - first.x;
        double dy = last.y - first.y;
        double distance = Math.sqrt(dx * dx + dy * dy);

        if (distance < 50) return; // Too short

        // Detect swipe direction
        double angle = Math.atan2(dy, dx);

        // Swipe right = next category
        if (Math.abs(angle) < Math.PI / 4 && distance > 100) {
            nextCategory();
            gesturePoints.clear();
        }
        // Swipe left = previous category
        else if (Math.abs(angle - Math.PI) < Math.PI / 4 || Math.abs(angle + Math.PI) < Math.PI / 4) {
            if (distance > 100) {
                prevCategory();
                gesturePoints.clear();
            }
        }
        // Swipe up = toggle search
        else if (angle < -Math.PI / 4 && angle > -3 * Math.PI / 4 && distance > 80) {
            searchMode = !searchMode;
            if (!searchMode) {
                searchQuery.setLength(0);
                searchResults.clear();
            }
            gesturePoints.clear();
            playSound(1.1f);
        }

        // Circle gesture = cycle theme
        if (isCircleGesture()) {
            config.cycleTheme();
            gesturePoints.clear();
            playSound(1.4f);
            spawnOpeningParticles();
        }
    }

    private boolean isCircleGesture() {
        if (gesturePoints.size() < 20) return false;

        // Calculate centroid
        double cx = 0, cy = 0;
        for (GesturePoint p : gesturePoints) {
            cx += p.x;
            cy += p.y;
        }
        cx /= gesturePoints.size();
        cy /= gesturePoints.size();

        // Check if points form a circle around centroid
        double avgRadius = 0;
        for (GesturePoint p : gesturePoints) {
            avgRadius += Math.sqrt((p.x - cx) * (p.x - cx) + (p.y - cy) * (p.y - cy));
        }
        avgRadius /= gesturePoints.size();

        if (avgRadius < 40) return false;

        // Check variance
        double variance = 0;
        for (GesturePoint p : gesturePoints) {
            double r = Math.sqrt((p.x - cx) * (p.x - cx) + (p.y - cy) * (p.y - cy));
            variance += (r - avgRadius) * (r - avgRadius);
        }
        variance /= gesturePoints.size();

        return variance < avgRadius * avgRadius * 0.2;
    }

    private void nextCategory() {
        selectedCategoryIndex = (selectedCategoryIndex + 1) % rootCategories.size();
        morphFromCategory = prevSelectedCategory;
        morphProgress = 0f;
        playSound(1.05f);
    }

    private void prevCategory() {
        selectedCategoryIndex = (selectedCategoryIndex - 1 + rootCategories.size()) % rootCategories.size();
        morphFromCategory = prevSelectedCategory;
        morphProgress = 0f;
        playSound(0.95f);
    }

    // ================================================================
    // CATEGORY INITIALIZATION (same as V2 but with favorites integration)
    // ================================================================

    private void initializeCategories() {
        RadialMenuConfig.ColorTheme theme = config.theme;

        // Category 1: Debug Overlays
        RadialCategory debug = RadialCategory.builder("debug")
            .name("Debug")
            .color(theme.categoryColors[0])
            .icon("👁")
            .iconStack(new ItemStack(Items.ENDER_EYE))
            .item(RadialMenuItem.toggle("Body Parts", "🎯",
                new ItemStack(Items.ARMOR_STAND),
                () -> ModConfig.showBodyPartBoxes,
                v -> ModConfig.showBodyPartBoxes = v,
                "Show body part hitboxes on entities"))
            .item(RadialMenuItem.toggle("Light Levels", "💡",
                new ItemStack(Items.TORCH),
                () -> LightLevelOverlay.INSTANCE.isEnabled(),
                v -> LightLevelOverlay.INSTANCE.setEnabled(v),
                "Display spawn light levels on blocks"))
            .item(RadialMenuItem.toggle("Pathfinding", "🛤",
                new ItemStack(Items.RAIL),
                () -> PathfindingDebugger.INSTANCE.isEnabled(),
                v -> PathfindingDebugger.INSTANCE.setEnabled(v),
                "Visualize mob pathfinding routes"))
            .item(RadialMenuItem.toggle("Line of Sight", "👀",
                new ItemStack(Items.SPYGLASS),
                () -> LineOfSightVisualizer.INSTANCE.isEnabled(),
                v -> LineOfSightVisualizer.INSTANCE.setEnabled(v),
                "Show mob vision cones"))
            .item(RadialMenuItem.toggle("Mob Debug", "👾",
                new ItemStack(Items.ZOMBIE_HEAD),
                () -> DebugRenderer.INSTANCE.isEnabled(),
                v -> DebugRenderer.INSTANCE.setEnabled(v),
                "Show mob stats, hitboxes & aggro ranges"))
            .build();
        rootCategories.add(debug);

        // Category 2: Spatial Analysis
        RadialCategory spatial = RadialCategory.builder("spatial")
            .name("Spatial")
            .color(theme.categoryColors[1])
            .icon("🗺")
            .iconStack(new ItemStack(Items.FILLED_MAP))
            .item(RadialMenuItem.toggle("Room Bounds", "🏠",
                new ItemStack(Items.STRUCTURE_BLOCK),
                () -> RoomBoundsVisualizer.INSTANCE.isEnabled(),
                v -> RoomBoundsVisualizer.INSTANCE.setEnabled(v),
                "Detect and highlight room boundaries"))
            .item(RadialMenuItem.toggle("Vertical Levels", "📶",
                new ItemStack(Items.LADDER),
                () -> VerticalLevelsVisualizer.INSTANCE.isEnabled(),
                v -> VerticalLevelsVisualizer.INSTANCE.setEnabled(v),
                "Show Y-level zone layers"))
            .item(RadialMenuItem.toggle("Safe Spots", "🛡",
                new ItemStack(Items.SHIELD),
                () -> SafeSpotVisualizer.INSTANCE.isEnabled(),
                v -> SafeSpotVisualizer.INSTANCE.setEnabled(v),
                "Highlight camping positions"))
            .item(RadialMenuItem.toggle("Spawnability", "👾",
                new ItemStack(Items.SPAWNER),
                () -> SpawnabilityOverlay.INSTANCE.isEnabled(),
                v -> SpawnabilityOverlay.INSTANCE.setEnabled(v),
                "Mark potential mob spawn zones"))
            .item(RadialMenuItem.toggle("Chunk Perf", "📦",
                new ItemStack(Items.CHEST),
                () -> ChunkPerformanceVisualizer.INSTANCE.isEnabled(),
                v -> ChunkPerformanceVisualizer.INSTANCE.setEnabled(v),
                "Show chunk render performance"))
            .build();
        rootCategories.add(spatial);

        // Category 3: Performance
        RadialCategory perf = RadialCategory.builder("perf")
            .name("Perf")
            .color(theme.categoryColors[2])
            .icon("📊")
            .iconStack(new ItemStack(Items.CLOCK))
            .item(RadialMenuItem.toggle("FPS Tracker", "🎮",
                new ItemStack(Items.CLOCK),
                () -> FpsTracker.INSTANCE.isEnabled(),
                v -> FpsTracker.INSTANCE.setEnabled(v),
                "Display FPS graph and statistics"))
            .item(RadialMenuItem.toggle("Entity Density", "👥",
                new ItemStack(Items.VILLAGER_SPAWN_EGG),
                () -> EntityDensityOverlay.isEnabled(),
                v -> EntityDensityOverlay.setEnabled(v),
                "Show entity count per area"))
            .item(RadialMenuItem.toggle("Heatmaps", "🔥",
                new ItemStack(Items.BLAZE_POWDER),
                () -> HeatmapVisualizer.INSTANCE.hasActiveHeatmaps(),
                v -> HeatmapVisualizer.INSTANCE.toggle(HeatmapVisualizer.HeatmapType.DEATH),
                "Toggle death heatmap visualization"))
            .item(RadialMenuItem.toggle("Profiler", "⏱",
                new ItemStack(Items.REDSTONE),
                () -> com.frenkvs.devmod.telemetry.PerformanceProfiler.INSTANCE.isEnabled(),
                v -> com.frenkvs.devmod.telemetry.PerformanceProfiler.INSTANCE.setEnabled(v),
                "Performance profiling overlay"))
            .item(RadialMenuItem.toggle("Attr Monitor", "📈",
                new ItemStack(Items.EXPERIENCE_BOTTLE),
                () -> com.frenkvs.devmod.attributes.AttributeMonitoringSystem.INSTANCE.isEnabled(),
                v -> com.frenkvs.devmod.attributes.AttributeMonitoringSystem.INSTANCE.setEnabled(v),
                "Track entity attributes in real-time"))
            .build();
        rootCategories.add(perf);

        // Category 4: Combat
        RadialCategory combat = RadialCategory.builder("combat")
            .name("Combat")
            .color(theme.categoryColors[3])
            .icon("⚔")
            .iconStack(new ItemStack(Items.DIAMOND_SWORD))
            .item(RadialMenuItem.toggle("Boss Phases", "👹",
                new ItemStack(Items.WITHER_SKELETON_SKULL),
                () -> BossPhaseOverlay.isEnabled(),
                v -> BossPhaseOverlay.setEnabled(v),
                "Display boss phase information"))
            .item(RadialMenuItem.toggle("Skill Efficacy", "✨",
                new ItemStack(Items.ENCHANTED_BOOK),
                () -> SkillEfficacyOverlay.isEnabled(),
                v -> SkillEfficacyOverlay.setEnabled(v),
                "Track skill effectiveness"))
            .item(RadialMenuItem.toggle("Economy", "💰",
                new ItemStack(Items.GOLD_INGOT),
                () -> EconomyOverlay.isEnabled(),
                v -> EconomyOverlay.setEnabled(v),
                "Show loot and gold statistics"))
            .build();
        rootCategories.add(combat);

        // Category 5: Tools
        RadialCategory tools = RadialCategory.builder("tools")
            .name("Tools")
            .color(theme.categoryColors[4])
            .icon("🔧")
            .iconStack(new ItemStack(Items.IRON_PICKAXE))
            .item(RadialMenuItem.screen("Settings", "⚙",
                new ItemStack(Items.COMPARATOR),
                () -> new com.frenkvs.devmod.ui.unified.UnifiedSettingsScreen(null),
                "Open DevMod configuration"))
            .item(createMobEditorItem())
            .item(RadialMenuItem.screen("Item Editor", "🗡",
                new ItemStack(Items.DIAMOND_SWORD),
                () -> new com.frenkvs.devmod.WeaponEditorScreen(),
                "Edit weapons and items"))
            .item(RadialMenuItem.screen("Dashboard", "📋",
                new ItemStack(Items.WRITABLE_BOOK),
                () -> new com.frenkvs.devmod.TelemetryDashboardScreen(null),
                "Telemetry analytics dashboard"))
            .item(RadialMenuItem.screen("Testing Hub", "🧪",
                new ItemStack(Items.BREWING_STAND),
                () -> new com.frenkvs.devmod.ui.hub.TestingHub(),
                "QA testing and validation tools"))
            .item(RadialMenuItem.screen("Quick Test", "⚡",
                new ItemStack(Items.LIGHTNING_ROD),
                () -> new com.frenkvs.devmod.ui.wizard.QuickTestWizard(),
                "Guided workflow to start testing"))
            .item(RadialMenuItem.toggle("Quick Help", "❓",
                new ItemStack(Items.KNOWLEDGE_BOOK),
                () -> QuickHelpOverlay.isEnabled(),
                v -> QuickHelpOverlay.setEnabled(v),
                "Show keybind help overlay"))
            .build();

        // Commands subcategory
        RadialCategory commands = tools.addSubcategory("commands", "Commands", 0xFFFFAA00, "📜");
        commands.addItem(RadialMenuItem.command("Gamemode Creative", "🎨",
            new ItemStack(Items.GRASS_BLOCK), "/gamemode creative", "Switch to creative mode"));
        commands.addItem(RadialMenuItem.command("Gamemode Survival", "⚔",
            new ItemStack(Items.IRON_SWORD), "/gamemode survival", "Switch to survival mode"));
        commands.addItem(RadialMenuItem.command("Heal", "❤",
            new ItemStack(Items.GOLDEN_APPLE), "/heal", "Restore full health"));
        commands.addItem(RadialMenuItem.command("Time Day", "☀",
            new ItemStack(Items.SUNFLOWER), "/time set day", "Set time to day"));
        commands.addItem(RadialMenuItem.command("Time Night", "🌙",
            new ItemStack(Items.CLOCK), "/time set night", "Set time to night"));
        commands.addItem(RadialMenuItem.command("Weather Clear", "🌤",
            new ItemStack(Items.FEATHER), "/weather clear", "Clear the weather"));

        rootCategories.add(tools);

        // Category 6: Quest
        RadialCategory quest = RadialCategory.builder("quest")
            .name("Quest")
            .color(theme.categoryColors[5])
            .icon("📜")
            .iconStack(new ItemStack(Items.PAPER))
            .item(RadialMenuItem.toggle("Quest HUD", "📋",
                new ItemStack(Items.MAP),
                () -> com.frenkvs.devmod.quest.QuestHudOverlay.isEnabled(),
                v -> com.frenkvs.devmod.quest.QuestHudOverlay.setEnabled(v),
                "Quest tracker overlay"))
            .item(RadialMenuItem.toggle("Endurance HUD", "💪",
                new ItemStack(Items.IRON_CHESTPLATE),
                () -> EnduranceQuestOverlay.isEnabled(),
                v -> EnduranceQuestOverlay.setEnabled(v),
                "Endurance quest overlay"))
            .item(RadialMenuItem.screen("Quest Editor", "✏",
                new ItemStack(Items.FEATHER),
                () -> new com.frenkvs.devmod.quest.QuestEditorScreen(),
                "Create and edit quests"))
            .item(RadialMenuItem.screen("Endurance", "🏆",
                new ItemStack(Items.GOLDEN_HELMET),
                () -> new com.frenkvs.devmod.endurance.EnduranceQuestScreen(),
                "Start endurance quest mode"))
            .build();
        rootCategories.add(quest);

        categoryAnimations = new float[rootCategories.size()];
        itemAnimations = new float[20];
    }

    private RadialMenuItem createMobEditorItem() {
        return new RadialMenuItem("Mob Editor",
            RadialAction.custom("Mob Editor",
                getMobEditorDescription(),
                "🐾",
                new ItemStack(Items.LEAD),
                this::openMobEditor),
            "🐾",
            new ItemStack(Items.LEAD)) {
            @Override
            public boolean isActive() {
                return cachedTargetEntity instanceof net.minecraft.world.entity.Mob;
            }
        };
    }

    private String getMobEditorDescription() {
        if (cachedTargetEntity instanceof net.minecraft.world.entity.Mob mob) {
            return "Edit: " + mob.getName().getString();
        }
        return "§cLook at a mob first, then open menu";
    }

    private void openMobEditor() {
        closing = true;
        Minecraft mc = Minecraft.getInstance();
        mc.tell(() -> {
            if (cachedTargetEntity instanceof net.minecraft.world.entity.Mob mob && mob.isAlive()) {
                mc.setScreen(new com.frenkvs.devmod.MobConfigScreen(mob));
            } else {
                showMessage(mc, "§c" + I18n.translate("devmod.message.look_at_mob_first").getString());
                mc.setScreen(null);
            }
        });
    }

    // ================================================================
    // SCREEN LIFECYCLE
    // ================================================================

    @Override
    protected void init() {
        centerX = width / 2;
        centerY = height / 2;
        OnboardingOverlay.onRadialMenuOpened();
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Update animations
        updateAnimations(partialTick);

        if (closing && openAnimation < 0.05f) {
            onClose();
            return;
        }

        updateSelection(mouseX, mouseY);

        // Render background
        renderBackground(graphics);

        var pose = graphics.pose();
        pose.pushPose();

        // Simple smooth scale animation (no elastic bounce, no rotation)
        float scale = easeOutQuad(openAnimation);
        pose.translate(centerX, centerY, 0);
        pose.scale(scale, scale, 1f);
        pose.translate(-centerX, -centerY, 0);

        // Subtle glow on selected category only
        if (selectedCategoryIndex >= 0) {
            renderCategoryGlow(graphics, selectedCategoryIndex);
        }

        renderCategories(graphics);
        renderCenterHub(graphics, mouseX, mouseY);

        if (selectedCategoryIndex >= 0 && selectedCategoryIndex < getActiveCategories().size()) {
            renderCategoryItems(graphics, getActiveCategories().get(selectedCategoryIndex));
        }

        if (config.showTooltips) {
            renderTooltip(graphics, mouseX, mouseY);
        }

        pose.popPose();

        // Search mode overlay
        if (searchMode) {
            renderSearchOverlay(graphics);
        }

        renderHelpText(graphics);
        renderBreadcrumb(graphics);

        if (editMode) {
            renderEditModeIndicator(graphics);
        }

        renderThemeIndicator(graphics);
    }

    // ================================================================
    // ANIMATION SYSTEM
    // ================================================================

    private void updateAnimations(float partialTick) {
        if (!config.enableAnimations) {
            openAnimation = closing ? 0f : 1f;
            return;
        }

        float delta = partialTick * 0.05f;
        float targetAnim = closing ? 0f : 1f;
        float animSpeed = closing ? config.closeAnimationSpeed : config.openAnimationSpeed;
        openAnimation = Mth.lerp(animSpeed, openAnimation, targetAnim);

        pulsePhase += delta * 3f;
        wavePhase += delta * 2f;
        if (pulsePhase > Math.PI * 2) pulsePhase -= (float)(Math.PI * 2);
        if (wavePhase > Math.PI * 2) wavePhase -= (float)(Math.PI * 2);

        // Morph animation
        if (morphProgress < 1f) {
            morphProgress = Math.min(1f, morphProgress + 0.08f);
        }

        // Search box animation
        float searchTarget = searchMode ? 1f : 0f;
        searchBoxAnimation = Mth.lerp(0.15f, searchBoxAnimation, searchTarget);

        List<RadialCategory> categories = getActiveCategories();
        for (int i = 0; i < categories.size(); i++) {
            float target = (i == selectedCategoryIndex) ? 1f : 0f;
            if (i < categoryAnimations.length) {
                categoryAnimations[i] = Mth.lerp(0.2f, categoryAnimations[i], target);
            }
        }

        if (selectedCategoryIndex >= 0 && selectedCategoryIndex < categories.size()) {
            RadialCategory cat = categories.get(selectedCategoryIndex);
            for (int i = 0; i < cat.getItems().size() && i < itemAnimations.length; i++) {
                float target = (i == selectedItemIndex) ? 1f : 0f;
                itemAnimations[i] = Mth.lerp(0.2f, itemAnimations[i], target);
            }
        }

        // Favorites animation
        for (int i = 0; i < favorites.size() && i < favoriteAnimations.length; i++) {
            float target = (i == selectedFavoriteIndex) ? 1f : 0f;
            favoriteAnimations[i] = Mth.lerp(0.2f, favoriteAnimations[i], target);
        }
    }

    // ================================================================
    // SELECTION
    // ================================================================

    private void updateSelection(int mouseX, int mouseY) {
        prevSelectedCategory = selectedCategoryIndex;
        prevSelectedItem = selectedItemIndex;

        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);

        selectedCategoryIndex = -1;
        selectedItemIndex = -1;

        // Center button area - no selection
        if (distance < centerButtonRadius) {
            return;
        }

        double angle = Math.atan2(dy, dx);
        if (angle < 0) angle += Math.PI * 2;

        List<RadialCategory> categories = getActiveCategories();
        int numCategories = categories.size();
        double segmentAngle = (Math.PI * 2) / numCategories;
        double startOffset = -Math.PI / 2 - segmentAngle / 2;

        double adjustedAngle = angle - startOffset;
        if (adjustedAngle < 0) adjustedAngle += Math.PI * 2;

        selectedCategoryIndex = (int)(adjustedAngle / segmentAngle) % numCategories;

        // Select item if mouse is beyond the outer ring
        if (distance > outerRadius && selectedCategoryIndex >= 0) {
            RadialCategory cat = categories.get(selectedCategoryIndex);
            int numItems = cat.getItems().size();
            if (numItems > 0) {
                double itemSegment = segmentAngle / numItems;
                double catStartAngle = startOffset + selectedCategoryIndex * segmentAngle;
                double relativeAngle = angle - catStartAngle;
                if (relativeAngle < 0) relativeAngle += Math.PI * 2;
                if (relativeAngle > Math.PI) relativeAngle -= Math.PI * 2;

                selectedItemIndex = Mth.clamp((int)(relativeAngle / itemSegment), 0, numItems - 1);
            }
        }

        // Play selection sound on category change
        if (config.enableSounds && selectedCategoryIndex != prevSelectedCategory && selectedCategoryIndex >= 0) {
            playSound(1.0f, 0.25f);
        }
    }

    private List<RadialCategory> getActiveCategories() {
        return rootCategories;
    }

    // ================================================================
    // RENDERING - PARTICLES
    // ================================================================

    private void renderParticles(GuiGraphics graphics) {
        for (Particle p : particles) {
            int alpha = (int)(p.alpha * 255);
            int color = (alpha << 24) | (p.color & 0x00FFFFFF);
            renderCircle(graphics, (int)p.x, (int)p.y, (int)p.size, color);
        }
    }

    // ================================================================
    // RENDERING - AMBIENT GLOW
    // ================================================================

    private void renderAmbientGlow(GuiGraphics graphics) {
        // Pulsing ambient glow around the menu
        float pulse = 0.7f + 0.3f * (float)Math.sin(pulsePhase);
        int glowAlpha = (int)(0x20 * glowIntensity * pulse);
        int color = (glowAlpha << 24) | (glowColor & 0x00FFFFFF);

        renderRadialGradient(graphics, centerX, centerY, outerRadius + 80, color, 0x00000000);

        // Wave effect
        for (int i = 0; i < 3; i++) {
            float waveOffset = (wavePhase + i * (float)(Math.PI * 2 / 3)) % (float)(Math.PI * 2);
            float waveRadius = outerRadius + 30 + 50 * (float)Math.sin(waveOffset);
            int waveAlpha = (int)(0x10 * (1 - Math.abs(Math.sin(waveOffset))));
            renderRing(graphics, centerX, centerY, (int)waveRadius - 2, (int)waveRadius + 2,
                (waveAlpha << 24) | (glowColor & 0x00FFFFFF));
        }
    }

    // ================================================================
    // RENDERING - FAVORITES RING
    // ================================================================

    private void renderFavoritesRing(GuiGraphics graphics) {
        if (favorites.isEmpty()) return;

        RadialMenuConfig.ColorTheme theme = config.theme;
        int numFavorites = favorites.size();
        double segmentAngle = (Math.PI * 2) / numFavorites;
        double startOffset = -Math.PI / 2;

        for (int i = 0; i < numFavorites; i++) {
            FavoriteItem fav = favorites.get(i);
            boolean selected = (i == selectedFavoriteIndex);
            float anim = i < favoriteAnimations.length ? favoriteAnimations[i] : 0;

            double midAngle = startOffset + i * segmentAngle;
            int favX = (int)(centerX + Math.cos(midAngle) * favoritesRadius);
            int favY = (int)(centerY + Math.sin(midAngle) * favoritesRadius);

            int size = 14 + (int)(4 * anim);

            // Star background
            int bgColor = selected ? 0xDDFFD700 : 0x88FFD700;
            renderCircle(graphics, favX, favY, size, bgColor);

            // Icon
            if (fav.item.getIconStack() != null) {
                graphics.pose().pushPose();
                graphics.pose().translate(favX - 8, favY - 8, 0);
                graphics.pose().scale(0.7f, 0.7f, 1f);
                graphics.renderItem(fav.item.getIconStack(), 0, 0);
                graphics.pose().popPose();
            } else {
                graphics.drawCenteredString(font, "★", favX, favY - 4, 0xFFFFD700);
            }
        }
    }

    // ================================================================
    // RENDERING - CONTEXT SUGGESTIONS
    // ================================================================

    private void renderContextSuggestions(GuiGraphics graphics) {
        RadialMenuConfig.ColorTheme theme = config.theme;

        String contextLabel = switch (currentContext) {
            case "mob_targeted" -> "§aMob Detected - Suggested:";
            case "combat" -> "§cIn Combat - Suggested:";
            case "building" -> "§eBuilding - Suggested:";
            default -> "§7Quick Access:";
        };

        int startY = centerY - outerRadius - 60;
        graphics.drawCenteredString(font, contextLabel, centerX, startY, 0xFFFFFFFF);

        int suggX = centerX - (contextSuggestions.size() * 25) / 2;
        for (int i = 0; i < Math.min(5, contextSuggestions.size()); i++) {
            RadialMenuItem item = contextSuggestions.get(i);
            int x = suggX + i * 50;
            int y = startY + 15;

            boolean isActive = item.isToggle() && item.isActive();
            int bgColor = isActive ? 0xAA00FF88 : 0xAA333355;
            renderCircle(graphics, x, y, 18, bgColor);

            if (item.getIconStack() != null) {
                graphics.renderItem(item.getIconStack(), x - 8, y - 8);
            } else {
                graphics.drawCenteredString(font, item.getIconEmoji(), x, y - 4, 0xFFFFFFFF);
            }
        }
    }

    // ================================================================
    // RENDERING - SEARCH OVERLAY
    // ================================================================

    private void renderSearchOverlay(GuiGraphics graphics) {
        if (searchBoxAnimation < 0.01f) return;

        RadialMenuConfig.ColorTheme theme = config.theme;

        // Darken background
        int overlayAlpha = (int)(0x80 * searchBoxAnimation);
        graphics.fill(0, 0, width, height, (overlayAlpha << 24));

        // Search box
        int boxWidth = (int)(300 * searchBoxAnimation);
        int boxHeight = 30;
        int boxX = centerX - boxWidth / 2;
        int boxY = 50;

        graphics.fill(boxX - 2, boxY - 2, boxX + boxWidth + 2, boxY + boxHeight + 2, theme.border);
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xEE101020);

        // Search icon and text
        String displayText = searchQuery.length() > 0 ? searchQuery.toString() : "§7Type to search...";
        graphics.drawString(font, "🔍 " + displayText, boxX + 10, boxY + 10, theme.textPrimary);

        // Blinking cursor
        if (searchQuery.length() > 0 && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cursorX = boxX + 10 + font.width("🔍 " + searchQuery.toString());
            graphics.fill(cursorX, boxY + 8, cursorX + 2, boxY + 22, theme.textPrimary);
        }

        // Results
        int resultY = boxY + boxHeight + 10;
        for (int i = 0; i < searchResults.size(); i++) {
            SearchResult result = searchResults.get(i);
            boolean selected = (i == selectedSearchResult);

            int resultBg = selected ? theme.selected : 0xCC101020;
            graphics.fill(boxX, resultY, boxX + boxWidth, resultY + 25, resultBg);

            String icon = result.item.getIconEmoji();
            String name = result.item.getName();
            String catName = "§7[" + result.category.getName() + "]";

            graphics.drawString(font, icon + " " + name + " " + catName, boxX + 10, resultY + 8, theme.textPrimary);

            if (result.item.isToggle() && result.item.isActive()) {
                graphics.drawString(font, "§a● ON", boxX + boxWidth - 40, resultY + 8, theme.active);
            }

            resultY += 28;
        }
    }

    // ================================================================
    // RENDERING - BACKGROUND & HUB (similar to V2)
    // ================================================================

    private void renderBackground(GuiGraphics graphics) {
        // Solid dark background - more opaque for visibility
        int alpha = (int)(0xE0 * openAnimation); // Much more opaque (224/255 vs 144/255)
        graphics.fill(0, 0, width, height, (alpha << 24) | 0x0D0D15);
    }

    private void renderCategoryGlow(GuiGraphics graphics, int categoryIndex) {
        List<RadialCategory> categories = getActiveCategories();
        if (categoryIndex >= categories.size()) return;

        RadialCategory cat = categories.get(categoryIndex);
        float anim = categoryIndex < categoryAnimations.length ? categoryAnimations[categoryIndex] : 0;

        double segmentAngle = (Math.PI * 2) / categories.size();
        double midAngle = -Math.PI / 2 + categoryIndex * segmentAngle;

        int glowX = (int)(centerX + Math.cos(midAngle) * itemRadius);
        int glowY = (int)(centerY + Math.sin(midAngle) * itemRadius);

        int glowRadius = (int)(60 * anim);
        int glowColorVal = (cat.getColor() & 0x00FFFFFF) | ((int)(0x40 * anim) << 24);
        renderRadialGradient(graphics, glowX, glowY, glowRadius, glowColorVal, 0x00000000);
    }

    private void renderCenterHub(GuiGraphics graphics, int mouseX, int mouseY) {
        RadialMenuConfig.ColorTheme theme = config.theme;

        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);
        boolean hovered = distance < centerButtonRadius;

        float hoverAnim = hovered ? Math.min(1f, categoryHoverAnim + 0.1f) : Math.max(0f, categoryHoverAnim - 0.05f);
        categoryHoverAnim = hoverAnim;

        // Solid opaque center button - structured look
        int bgColor = hovered ? 0xFF353555 : 0xF0252540;
        renderCircle(graphics, centerX, centerY, centerButtonRadius, bgColor);

        // Strong outer border (thicker)
        int borderColor = hovered ? 0xFF6080FF : 0xFF505070;
        renderRing(graphics, centerX, centerY, centerButtonRadius - 3, centerButtonRadius, borderColor);

        // Inner ring for depth
        renderRing(graphics, centerX, centerY, centerButtonRadius - 4, centerButtonRadius - 3, 0xFF404060);

        // Static icon (no bounce)
        String centerIcon = hovered ? "✕" : (searchMode ? "🔍" : "⚡");
        int iconColor = hovered ? 0xFFFF6666 : 0xFF8080FF;
        graphics.drawCenteredString(font, centerIcon, centerX, centerY - 4, iconColor);

        // Label - brighter
        String label = hovered ? "Close" : (searchMode ? "Search" : "DevMod");
        int labelColor = hovered ? 0xFFFFFFFF : 0xFFCCCCDD;
        graphics.drawCenteredString(font, label, centerX, centerY + 8, labelColor);
    }

    private void renderCategories(GuiGraphics graphics) {
        RadialMenuConfig.ColorTheme theme = config.theme;
        List<RadialCategory> categories = getActiveCategories();
        int numCategories = categories.size();
        double segmentAngle = (Math.PI * 2) / numCategories;
        double startOffset = -Math.PI / 2;

        // Draw inner ring border first (solid line around center)
        renderRing(graphics, centerX, centerY, innerRadius - 2, innerRadius, 0xFF303050);

        for (int i = 0; i < numCategories; i++) {
            RadialCategory cat = categories.get(i);
            boolean selected = (i == selectedCategoryIndex);
            float anim = i < categoryAnimations.length ? categoryAnimations[i] : 0;

            double startAngle = startOffset + (i - 0.5) * segmentAngle;
            double endAngle = startAngle + segmentAngle;

            // Solid segment fill - more opaque
            int baseColor = selected ? 0xEE252540 : 0xDD1a1a30;
            int segColor = selected ? blendColors(baseColor, cat.getColor(), 0.25f) : baseColor;
            renderArcSegment(graphics, centerX, centerY, innerRadius, outerRadius, startAngle, endAngle, segColor);

            // Strong outer border
            int borderCol = selected ? cat.getColor() : 0xFF404060;
            renderArcOutline(graphics, centerX, centerY, outerRadius, startAngle, endAngle, borderCol, 2);

            // Divider lines between segments
            double dividerAngle = startAngle;
            int divX1 = (int)(centerX + Math.cos(dividerAngle) * innerRadius);
            int divY1 = (int)(centerY + Math.sin(dividerAngle) * innerRadius);
            int divX2 = (int)(centerX + Math.cos(dividerAngle) * outerRadius);
            int divY2 = (int)(centerY + Math.sin(dividerAngle) * outerRadius);
            drawLine(graphics, divX1, divY1, divX2, divY2, 0xFF404060);

            double midAngle = startOffset + i * segmentAngle;
            int iconX = (int)(centerX + Math.cos(midAngle) * itemRadius);
            int iconY = (int)(centerY + Math.sin(midAngle) * itemRadius);

            // Render icon (no scaling animation)
            renderCategoryIcon(graphics, cat, iconX, iconY - 8, selected);

            // Category name - brighter text
            int textColor = selected ? 0xFFFFFFFF : 0xFFBBBBCC;
            graphics.drawCenteredString(font, cat.getName(), iconX, iconY + 6, textColor);

            // Active items badge
            int activeCount = cat.countActiveItems();
            if (activeCount > 0) {
                renderBadge(graphics, iconX + 20, iconY - 14, activeCount, theme.active);
            }
        }

        // Draw outer ring border (solid line around entire wheel)
        renderRing(graphics, centerX, centerY, outerRadius, outerRadius + 2, 0xFF505070);
    }

    private void renderCategoryIcon(GuiGraphics graphics, RadialCategory cat, int x, int y, boolean selected) {
        RadialMenuConfig.ColorTheme theme = config.theme;
        int iconColor = selected ? cat.getColor() : theme.textSecondary;

        boolean useItemStack = config.iconMode == RadialMenuConfig.IconMode.ITEMSTACK ||
            (config.iconMode == RadialMenuConfig.IconMode.AUTO && cat.getIconStack() != null);

        if (useItemStack && cat.getIconStack() != null) {
            graphics.renderItem(cat.getIconStack(), x - 8, y - 4);
        } else {
            graphics.drawCenteredString(font, cat.getIcon(), x, y, iconColor);
        }
    }

    private void renderCategoryItems(GuiGraphics graphics, RadialCategory category) {
        RadialMenuConfig.ColorTheme theme = config.theme;
        List<RadialMenuItem> items = category.getItems();
        int numItems = items.size();
        if (numItems == 0) return;

        List<RadialCategory> categories = getActiveCategories();
        double segmentAngle = (Math.PI * 2) / categories.size();
        double startOffset = -Math.PI / 2;
        double catStartAngle = startOffset + (selectedCategoryIndex - 0.5) * segmentAngle;
        double itemAngleStep = segmentAngle / numItems;

        // Larger radius for better spacing between items
        int baseRadius = outerRadius + 55;
        int itemSize = 34; // Larger clickable area

        for (int i = 0; i < numItems; i++) {
            RadialMenuItem item = items.get(i);
            if (!item.isVisible()) continue;

            boolean itemSelected = (i == selectedItemIndex);
            boolean isActive = item.isToggle() && item.isActive();
            float itemAnim = i < itemAnimations.length ? itemAnimations[i] : 0;

            double itemAngle = catStartAngle + (i + 0.5) * itemAngleStep;

            // Position with slight expansion on hover
            int itemX = (int)(centerX + Math.cos(itemAngle) * (baseRadius + 6 * itemAnim));
            int itemY = (int)(centerY + Math.sin(itemAngle) * (baseRadius + 6 * itemAnim));

            int itemRadiusSize = itemSize + (int)(4 * itemAnim);

            // Solid opaque background - structured look
            int bgColor = itemSelected ? 0xFF303050 : 0xF0202035;
            if (isActive) {
                bgColor = blendColors(bgColor, theme.active, 0.25f);
            }
            // Draw solid circle background
            renderCircle(graphics, itemX, itemY, itemRadiusSize, bgColor);

            // Strong visible border
            int borderColor = isActive ? theme.active : (itemSelected ? category.getColor() : 0xFF505070);
            int borderWidth = itemSelected ? 3 : 2;
            renderRing(graphics, itemX, itemY, itemRadiusSize - borderWidth, itemRadiusSize, borderColor);

            // Inner subtle highlight for depth
            if (itemSelected) {
                renderRing(graphics, itemX, itemY, itemRadiusSize - borderWidth - 1, itemRadiusSize - borderWidth,
                    blendColors(category.getColor(), 0xFFFFFFFF, 0.3f));
            }

            // Icon only (no emoji - just ItemStack for clarity, or simple dot)
            if (item.getIconStack() != null) {
                graphics.renderItem(item.getIconStack(), itemX - 8, itemY - 16);
            } else {
                int iconColor = itemSelected ? theme.textPrimary : theme.textSecondary;
                graphics.drawCenteredString(font, item.getIconEmoji(), itemX, itemY - 12, iconColor);
            }

            // Name - truncate if too long
            String name = item.getName();
            int maxWidth = 56;
            if (font.width(name) > maxWidth) {
                while (font.width(name + "..") > maxWidth && name.length() > 3) {
                    name = name.substring(0, name.length() - 1);
                }
                name += "..";
            }
            int nameColor = itemSelected ? theme.textPrimary : (isActive ? theme.active : theme.textSecondary);
            graphics.drawCenteredString(font, name, itemX, itemY + 4, nameColor);

            // Toggle status indicator - simple and clear
            if (item.isToggle()) {
                String status = isActive ? "ON" : "OFF";
                int statusColor = isActive ? theme.active : 0xFF666666;
                graphics.drawCenteredString(font, status, itemX, itemY + 16, statusColor);
            } else if (item.isSubcategoryLink()) {
                graphics.drawCenteredString(font, "▸", itemX, itemY + 16, theme.textSecondary);
            }
        }
    }

    private void renderItemIcon(GuiGraphics graphics, RadialMenuItem item, int x, int y, boolean selected) {
        RadialMenuConfig.ColorTheme theme = config.theme;
        int iconColor = selected ? theme.textPrimary : theme.textSecondary;

        boolean useItemStack = config.iconMode == RadialMenuConfig.IconMode.ITEMSTACK ||
            (config.iconMode == RadialMenuConfig.IconMode.AUTO && item.getIconStack() != null);

        if (useItemStack && item.getIconStack() != null) {
            graphics.renderItem(item.getIconStack(), x - 8, y - 4);
        } else {
            graphics.drawCenteredString(font, item.getIconEmoji(), x, y, iconColor);
        }
    }

    private void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        RadialMenuConfig.ColorTheme theme = config.theme;
        String tooltip = null;

        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        if (Math.sqrt(dx * dx + dy * dy) < centerButtonRadius) {
            tooltip = "§7Click to close §8| §7Scroll to search";
        } else if (selectedFavoriteIndex >= 0 && selectedFavoriteIndex < favorites.size()) {
            tooltip = "★ " + favorites.get(selectedFavoriteIndex).item.getName();
        } else if (selectedCategoryIndex >= 0 && selectedCategoryIndex < getActiveCategories().size()) {
            RadialCategory cat = getActiveCategories().get(selectedCategoryIndex);
            if (selectedItemIndex >= 0 && selectedItemIndex < cat.getItems().size()) {
                RadialMenuItem item = cat.getItems().get(selectedItemIndex);
                tooltip = item.getDescription();
                if (editMode) {
                    tooltip += " §8| §cShift+Click to favorite";
                }
            }
        }

        if (tooltip != null) {
            int tooltipWidth = font.width(tooltip);
            int tooltipX = centerX - tooltipWidth / 2;
            int tooltipY = centerY + outerRadius + 70;

            int padding = 6;
            graphics.fill(tooltipX - padding - 1, tooltipY - padding - 1,
                tooltipX + tooltipWidth + padding + 1, tooltipY + 10 + padding + 1,
                theme.border);
            graphics.fill(tooltipX - padding, tooltipY - padding,
                tooltipX + tooltipWidth + padding, tooltipY + 10 + padding,
                0xF0101020);
            graphics.drawString(font, tooltip, tooltipX, tooltipY, theme.textPrimary, false);
        }
    }

    private void renderHelpText(GuiGraphics graphics) {
        if (!config.showKeyHints) return;

        RadialMenuConfig.ColorTheme theme = config.theme;
        float helpAlpha = Math.min(1f, (System.currentTimeMillis() - openTime) / 200f);
        int textAlpha = (int)(0xAA * helpAlpha);
        int helpColor = (textAlpha << 24) | (theme.textSecondary & 0x00FFFFFF);

        String helpLine = searchMode ? "§eSearch §7- Type to filter, Enter to select, Esc to cancel" :
            "§7Click to select §8| §7[/] Search §8| §7[T] Theme";

        graphics.drawCenteredString(font, helpLine, width / 2, height - 25, helpColor);
    }

    private void renderBreadcrumb(GuiGraphics graphics) {
        if (navigationStack.isEmpty() && currentCategory == null) return;

        StringBuilder breadcrumb = new StringBuilder("§7");
        for (RadialCategory cat : navigationStack) {
            breadcrumb.append(cat.getName()).append(" > ");
        }
        if (currentCategory != null) {
            breadcrumb.append("§f").append(currentCategory.getName());
        }

        graphics.drawString(font, breadcrumb.toString(), 10, 10, 0xFFFFFFFF, true);
    }

    private void renderEditModeIndicator(GuiGraphics graphics) {
        String editText = "§c§l[EDIT MODE] §7Shift+Click to ★ favorite";
        int textWidth = font.width(editText);
        graphics.fill(width / 2 - textWidth / 2 - 5, 5, width / 2 + textWidth / 2 + 5, 20, 0xCC000000);
        graphics.drawCenteredString(font, editText, width / 2, 8, 0xFFFF4444);
    }

    private void renderThemeIndicator(GuiGraphics graphics) {
        if (System.currentTimeMillis() - openTime < 2000) {
            float alpha = 1f - Math.max(0, (System.currentTimeMillis() - openTime - 1000) / 1000f);
            if (alpha > 0) {
                int color = ((int)(alpha * 255) << 24) | 0xFFFFFF;
                String themeText = "Theme: " + config.theme.presetName;
                graphics.drawCenteredString(font, themeText, width / 2, 30, color);
            }
        }
    }

    private void renderBadge(GuiGraphics graphics, int x, int y, int count, int color) {
        float pulse = 0.8f + 0.2f * (float)Math.sin(pulsePhase * 2);
        int badgeColor = blendColors(color, 0xFFFFFFFF, pulse * 0.3f);

        graphics.fill(x - 6, y - 4, x + 6, y + 6, 0xDD000000);
        graphics.drawCenteredString(font, String.valueOf(count), x, y - 2, badgeColor);
    }

    // ================================================================
    // INPUT HANDLING
    // ================================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean shiftHeld = hasShiftDown();

        if (button == 0) {
            // Check favorites first
            if (selectedFavoriteIndex >= 0 && selectedFavoriteIndex < favorites.size()) {
                FavoriteItem fav = favorites.get(selectedFavoriteIndex);
                executeItem(fav.item, fav.category);
                return true;
            }

            // Shift+click to toggle favorite
            if (shiftHeld && selectedItemIndex >= 0 && selectedCategoryIndex >= 0) {
                RadialCategory cat = getActiveCategories().get(selectedCategoryIndex);
                RadialMenuItem item = cat.getItems().get(selectedItemIndex);
                toggleFavorite(item, cat);
                return true;
            }

            if (editMode && selectedItemIndex >= 0) {
                openItemEditor();
            } else {
                activateSelection();
            }
            return true;
        } else if (button == 1 && config.rightClickToEdit) {
            if (selectedItemIndex >= 0) {
                openItemEditor();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (searchMode && !searchResults.isEmpty()) {
            if (scrollY > 0) {
                selectedSearchResult = Math.max(0, selectedSearchResult - 1);
            } else {
                selectedSearchResult = Math.min(searchResults.size() - 1, selectedSearchResult + 1);
            }
            return true;
        }

        // Scroll to navigate categories
        if (scrollY > 0) {
            prevCategory();
        } else if (scrollY < 0) {
            nextCategory();
        }
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_G && config.releaseToSelect) {
            activateSelection();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Search mode input
        if (searchMode) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchMode = false;
                searchQuery.setLength(0);
                searchResults.clear();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER && selectedSearchResult >= 0) {
                SearchResult result = searchResults.get(selectedSearchResult);
                executeItem(result.item, result.category);
                searchMode = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && searchQuery.length() > 0) {
                searchQuery.deleteCharAt(searchQuery.length() - 1);
                updateSearchResults();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP) {
                selectedSearchResult = Math.max(0, selectedSearchResult - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                selectedSearchResult = Math.min(searchResults.size() - 1, selectedSearchResult + 1);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closing = true;
            return true;
        }

        // / or F to toggle search
        if (keyCode == GLFW.GLFW_KEY_SLASH || keyCode == GLFW.GLFW_KEY_F) {
            searchMode = !searchMode;
            if (!searchMode) {
                searchQuery.setLength(0);
                searchResults.clear();
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_LEFT_SHIFT || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            editMode = !editMode;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_T) {
            config.cycleTheme();
            playSound(1.2f);
            spawnOpeningParticles();
            return true;
        }

        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_6) {
            int num = keyCode - GLFW.GLFW_KEY_1;
            if (num < getActiveCategories().size()) {
                morphFromCategory = selectedCategoryIndex;
                morphProgress = 0f;
                selectedCategoryIndex = num;
            }
            return true;
        }

        // Left/Right arrows for categories
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            prevCategory();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            nextCategory();
            return true;
        }

        if (selectedCategoryIndex >= 0) {
            RadialCategory cat = getActiveCategories().get(selectedCategoryIndex);
            int itemNum = getItemKeyIndex(keyCode);

            if (itemNum >= 0 && itemNum < cat.getItems().size()) {
                if (editMode) {
                    selectedItemIndex = itemNum;
                    openItemEditor();
                } else {
                    executeItem(cat.getItems().get(itemNum), cat);
                }
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchMode && Character.isLetterOrDigit(chr) || chr == ' ') {
            searchQuery.append(chr);
            updateSearchResults();
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    private int getItemKeyIndex(int keyCode) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_Q -> 0;
            case GLFW.GLFW_KEY_W -> 1;
            case GLFW.GLFW_KEY_E -> 2;
            case GLFW.GLFW_KEY_R -> 3;
            case GLFW.GLFW_KEY_Y -> 4;
            case GLFW.GLFW_KEY_U -> 5;
            case GLFW.GLFW_KEY_I -> 6;
            case GLFW.GLFW_KEY_O -> 7;
            case GLFW.GLFW_KEY_P -> 8;
            default -> -1;
        };
    }

    private void activateSelection() {
        Minecraft mc = Minecraft.getInstance();
        double dx = mc.mouseHandler.xpos() * width / mc.getWindow().getWidth() - centerX;
        double dy = mc.mouseHandler.ypos() * height / mc.getWindow().getHeight() - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);

        if (distance < centerButtonRadius) {
            if (currentCategory != null && currentCategory.hasParent()) {
                navigateBack();
            } else {
                closing = true;
            }
            return;
        }

        // Check favorites
        if (selectedFavoriteIndex >= 0 && selectedFavoriteIndex < favorites.size()) {
            FavoriteItem fav = favorites.get(selectedFavoriteIndex);
            executeItem(fav.item, fav.category);
            return;
        }

        if (selectedCategoryIndex >= 0 && selectedCategoryIndex < getActiveCategories().size()) {
            RadialCategory cat = getActiveCategories().get(selectedCategoryIndex);
            if (selectedItemIndex >= 0 && selectedItemIndex < cat.getItems().size()) {
                RadialMenuItem item = cat.getItems().get(selectedItemIndex);

                if (item.isSubcategoryLink()) {
                    RadialCategory subcategory = item.getLinkedSubcategory();
                    if (subcategory != null) {
                        navigateTo(subcategory);
                        return;
                    }
                }

                executeItem(item, cat);
            }
        }
    }

    private void executeItem(RadialMenuItem item, RadialCategory category) {
        item.execute();
        recordUsage(item);

        SettingsManager.INSTANCE.syncFromSystems();
        SettingsManager.INSTANCE.save();

        OnboardingOverlay.onOverlayToggled();
        OnboardingOverlay.onCategorySelected();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && item.isToggle()) {
            String statusKey = item.isActive() ? "devmod.status.on" : "devmod.status.off";
            mc.player.displayClientMessage(
                I18n.translate("devmod.message.item_toggled", item.getName(),
                    I18n.translate(statusKey).getString()),
                true);
        }

        if (config.enableSounds) {
            float pitch = item.isToggle() ? (item.isActive() ? 1.2f : 0.8f) : 1.0f;
            playSound(pitch);
        }

        // Spawn particles on activation
        spawnSelectionParticles(centerX, centerY, category.getColor());

        if (config.closeOnToggle && !item.isToggle()) {
            closing = true;
        }
    }

    private void navigateTo(RadialCategory category) {
        if (currentCategory != null) {
            navigationStack.push(currentCategory);
        }
        currentCategory = category;
        selectedItemIndex = -1;
        playSound(1.1f);
    }

    private void navigateBack() {
        if (!navigationStack.isEmpty()) {
            currentCategory = navigationStack.pop();
        } else {
            currentCategory = null;
        }
        selectedItemIndex = -1;
        playSound(0.9f);
    }

    private void openItemEditor() {
        if (selectedCategoryIndex >= 0 && selectedItemIndex >= 0) {
            RadialCategory cat = getActiveCategories().get(selectedCategoryIndex);
            if (selectedItemIndex < cat.getItems().size()) {
                RadialMenuItem item = cat.getItems().get(selectedItemIndex);
                showMessage(Minecraft.getInstance(),
                    "§6[Edit] " + item.getName() + " - Shift+Click to toggle ★ favorite");
            }
        }
    }

    // ================================================================
    // UTILITY
    // ================================================================

    private void playSound(float pitch) {
        playSound(pitch, 1.0f);
    }

    private void playSound(float pitch, float volume) {
        if (config.enableSounds) {
            Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, volume));
        }
    }

    private void showMessage(Minecraft mc, String message) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message), true);
        }
    }

    private float easeOutQuad(float t) {
        return 1 - (1 - t) * (1 - t);
    }

    private float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        return (float)(1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2));
    }

    private float easeOutElastic(float t) {
        if (t == 0 || t == 1) return t;
        float p = 0.3f;
        return (float)(Math.pow(2, -10 * t) * Math.sin((t - p / 4) * (2 * Math.PI) / p) + 1);
    }

    private int blendColors(int color1, int color2, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int a1 = (color1 >> 24) & 0xFF, r1 = (color1 >> 16) & 0xFF, g1 = (color1 >> 8) & 0xFF, b1 = color1 & 0xFF;
        int a2 = (color2 >> 24) & 0xFF, r2 = (color2 >> 16) & 0xFF, g2 = (color2 >> 8) & 0xFF, b2 = color2 & 0xFF;
        return ((int)(a1 + (a2 - a1) * t) << 24) | ((int)(r1 + (r2 - r1) * t) << 16) |
            ((int)(g1 + (g2 - g1) * t) << 8) | (int)(b1 + (b2 - b1) * t);
    }

    // ================================================================
    // RENDERING PRIMITIVES
    // ================================================================

    private void renderRadialGradient(GuiGraphics graphics, int cx, int cy, int radius, int centerColor, int edgeColor) {
        int rings = 8;
        for (int ring = 0; ring < rings; ring++) {
            float t1 = (float)ring / rings;
            int r1 = (int)(radius * t1);
            int r2 = (int)(radius * (float)(ring + 1) / rings);
            int color = blendColors(centerColor, edgeColor, t1);
            renderRing(graphics, cx, cy, r1, r2, color);
        }
    }

    private void renderGradientCircle(GuiGraphics graphics, int cx, int cy, int radius, int innerColor, int outerColor) {
        int rings = 6;
        for (int ring = 0; ring < rings; ring++) {
            float t = (float)ring / rings;
            int r = (int)(radius * (1 - t));
            int color = blendColors(outerColor, innerColor, t);
            renderCircle(graphics, cx, cy, r, color);
        }
    }

    private void renderCircle(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        int segments = 24;
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = graphics.pose().last().pose();

        float a = ((color >> 24) & 0xFF) / 255f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        buffer.addVertex(matrix, cx, cy, 0).setColor(r, g, b, a);
        for (int i = 0; i <= segments; i++) {
            double angle = (Math.PI * 2 * i) / segments;
            float x = (float)(cx + Math.cos(angle) * radius);
            float y = (float)(cy + Math.sin(angle) * radius);
            buffer.addVertex(matrix, x, y, 0).setColor(r, g, b, a);
        }

        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
    }

    private void renderRing(GuiGraphics graphics, int cx, int cy, int innerRadius, int outerRadius, int color) {
        int segments = 32;
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = graphics.pose().last().pose();

        float a = ((color >> 24) & 0xFF) / 255f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        for (int i = 0; i <= segments; i++) {
            double angle = (Math.PI * 2 * i) / segments;
            float cos = (float)Math.cos(angle);
            float sin = (float)Math.sin(angle);
            buffer.addVertex(matrix, cx + cos * innerRadius, cy + sin * innerRadius, 0).setColor(r, g, b, a);
            buffer.addVertex(matrix, cx + cos * outerRadius, cy + sin * outerRadius, 0).setColor(r, g, b, a);
        }

        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
    }

    private void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = graphics.pose().last().pose();

        float a = ((color >> 24) & 0xFF) / 255f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        buffer.addVertex(matrix, x1, y1, 0).setColor(r, g, b, a);
        buffer.addVertex(matrix, x2, y2, 0).setColor(r, g, b, a);

        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
    }

    private void renderArcSegment(GuiGraphics graphics, int cx, int cy, int innerR, int outerR,
                                  double startAngle, double endAngle, int color) {
        int segments = 16;
        double angleStep = (endAngle - startAngle) / segments;

        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = graphics.pose().last().pose();

        float a = ((color >> 24) & 0xFF) / 255f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        for (int i = 0; i <= segments; i++) {
            double angle = startAngle + i * angleStep;
            float cos = (float)Math.cos(angle);
            float sin = (float)Math.sin(angle);
            buffer.addVertex(matrix, cx + cos * innerR, cy + sin * innerR, 0).setColor(r, g, b, a);
            buffer.addVertex(matrix, cx + cos * outerR, cy + sin * outerR, 0).setColor(r, g, b, a);
        }

        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
    }

    private void renderArcOutline(GuiGraphics graphics, int cx, int cy, int radius,
                                  double startAngle, double endAngle, int color, int width) {
        int segments = 16;
        double angleStep = (endAngle - startAngle) / segments;

        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(width);

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = graphics.pose().last().pose();

        float a = ((color >> 24) & 0xFF) / 255f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        for (int i = 0; i <= segments; i++) {
            double angle = startAngle + i * angleStep;
            float x = (float)(cx + Math.cos(angle) * radius);
            float y = (float)(cy + Math.sin(angle) * radius);
            buffer.addVertex(matrix, x, y, 0).setColor(r, g, b, a);
        }

        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.lineWidth(1);
        RenderSystem.disableBlend();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ================================================================
    // INNER CLASSES
    // ================================================================

    private static class Particle {
        float x, y;
        float vx, vy;
        int color;
        float alpha;
        float size;
        float decay;

        Particle(float x, float y, float vx, float vy, int color, float alpha, float size) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.color = color;
            this.alpha = alpha;
            this.size = size;
            this.decay = 0.02f + (float)Math.random() * 0.02f;
        }

        void update() {
            x += vx;
            y += vy;
            vx *= 0.98f;
            vy *= 0.98f;
            vy += 0.05f; // gravity
            alpha -= decay;
            size *= 0.98f;
        }

        boolean isDead() {
            return alpha <= 0 || size < 0.5f;
        }
    }

    private static class SearchResult {
        final RadialMenuItem item;
        final RadialCategory category;
        final int score;

        SearchResult(RadialMenuItem item, RadialCategory category, int score) {
            this.item = item;
            this.category = category;
            this.score = score;
        }
    }

    private static class FavoriteItem {
        final String key;
        final RadialMenuItem item;
        final RadialCategory category;

        FavoriteItem(String key, RadialMenuItem item, RadialCategory category) {
            this.key = key;
            this.item = item;
            this.category = category;
        }
    }

    private static class GesturePoint {
        final int x, y;
        final long time;

        GesturePoint(int x, int y, long time) {
            this.x = x;
            this.y = y;
            this.time = time;
        }
    }
}
