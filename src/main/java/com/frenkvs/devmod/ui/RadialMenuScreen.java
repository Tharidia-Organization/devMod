package com.frenkvs.devmod.ui;

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
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Beautiful Radial Menu for DevMod - Single key access to all debug tools
 *
 * Features:
 * - Smooth cubic bezier animations
 * - Glow effects and gradients
 * - Pulsing active indicators
 * - Category icons
 * - Hover transitions
 *
 * Press G to open, move mouse to select, click or release G to activate.
 */
public class RadialMenuScreen extends Screen {

    // Layout constants
    private static final int INNER_RADIUS = 55;
    private static final int OUTER_RADIUS = 130;
    private static final int ITEM_RADIUS = 95;
    private static final int CENTER_BUTTON_RADIUS = 40;
    private static final int ITEM_OUTER_RADIUS = 175;

    // Color palette - Modern dark theme with accent colors
    private static final int COLOR_BG_DARK = 0xE6101020;
    private static final int COLOR_BG_LIGHT = 0xCC1a1a35;
    private static final int COLOR_SELECTED = 0xDD2a2a55;
    private static final int COLOR_HOVER = 0xEE353566;
    private static final int COLOR_ACTIVE = 0xFF00FF88;
    private static final int COLOR_ACTIVE_GLOW = 0x4400FF88;
    private static final int COLOR_INACTIVE = 0xFFAAAAAA;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_DIM = 0xFFAAAAAA;
    private static final int COLOR_BORDER = 0xFF505080;
    private static final int COLOR_BORDER_GLOW = 0x40FFFFFF;

    // Menu structure
    private final List<RadialCategory> categories = new ArrayList<>();
    private int selectedCategory = -1;
    private int selectedItem = -1;
    private int prevSelectedCategory = -1;
    private int prevSelectedItem = -1;
    private int centerX, centerY;

    // Animation state
    private float openAnimation = 0f;
    private float categoryHoverAnim = 0f;
    private float itemHoverAnim = 0f;
    private float pulsePhase = 0f;
    private float[] categoryAnimations;
    private float[] itemAnimations;
    private boolean closing = false;
    private long openTime;

    // Cached entity target (saved when menu opens, before mouse moves)
    @javax.annotation.Nullable
    private net.minecraft.world.entity.Entity cachedTargetEntity = null;

    // Category icons (Unicode symbols)
    private static final String[] CATEGORY_ICONS = {
        "👁", // Debug - Eye
        "🗺", // Spatial - Map
        "📊", // Perf - Chart
        "⚔", // Combat - Swords
        "🔧", // Tools - Wrench
        "📜"  // Quest - Scroll
    };

    public RadialMenuScreen() {
        super(I18n.ui("radial.title"));
        initializeCategories();
        categoryAnimations = new float[categories.size()];
        openTime = System.currentTimeMillis();

        // Cache the entity the player is looking at BEFORE the menu opens
        // (once the menu is open, hitResult changes to GUI coordinates)
        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult != null && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
            cachedTargetEntity = ((net.minecraft.world.phys.EntityHitResult) mc.hitResult).getEntity();
        }
    }

    private void initializeCategories() {
        // Category 1: Debug Overlays (visualization tools)
        RadialCategory debugOverlays = new RadialCategory("Debug", 0xFF00DDFF, "👁");
        debugOverlays.addItem(new RadialItem("Body Parts",
            () -> ModConfig.showBodyPartBoxes,
            v -> ModConfig.showBodyPartBoxes = v,
            "Show body part hitboxes on entities", "🎯"));
        debugOverlays.addItem(new RadialItem("Light Levels",
            () -> LightLevelOverlay.INSTANCE.isEnabled(),
            v -> LightLevelOverlay.INSTANCE.setEnabled(v),
            "Display spawn light levels on blocks", "💡"));
        debugOverlays.addItem(new RadialItem("Pathfinding",
            () -> PathfindingDebugger.INSTANCE.isEnabled(),
            v -> PathfindingDebugger.INSTANCE.setEnabled(v),
            "Visualize mob pathfinding routes", "🛤"));
        debugOverlays.addItem(new RadialItem("Line of Sight",
            () -> LineOfSightVisualizer.INSTANCE.isEnabled(),
            v -> LineOfSightVisualizer.INSTANCE.setEnabled(v),
            "Show mob vision cones", "👀"));
        debugOverlays.addItem(new RadialItem("Mob Debug",
            () -> DebugRenderer.INSTANCE.isEnabled(),
            v -> DebugRenderer.INSTANCE.setEnabled(v),
            "Show mob stats, hitboxes & aggro ranges when looking at mobs", "👾"));
        categories.add(debugOverlays);

        // Category 2: Spatial Analysis
        RadialCategory spatial = new RadialCategory("Spatial", 0xFFFFDD00, "🗺");
        spatial.addItem(new RadialItem("Room Bounds",
            () -> RoomBoundsVisualizer.INSTANCE.isEnabled(),
            v -> RoomBoundsVisualizer.INSTANCE.setEnabled(v),
            "Detect and highlight room boundaries", "🏠"));
        spatial.addItem(new RadialItem("Vertical Levels",
            () -> VerticalLevelsVisualizer.INSTANCE.isEnabled(),
            v -> VerticalLevelsVisualizer.INSTANCE.setEnabled(v),
            "Show Y-level zone layers", "📶"));
        spatial.addItem(new RadialItem("Safe Spots",
            () -> SafeSpotVisualizer.INSTANCE.isEnabled(),
            v -> SafeSpotVisualizer.INSTANCE.setEnabled(v),
            "Highlight camping positions", "🛡"));
        spatial.addItem(new RadialItem("Spawnability",
            () -> SpawnabilityOverlay.INSTANCE.isEnabled(),
            v -> SpawnabilityOverlay.INSTANCE.setEnabled(v),
            "Mark potential mob spawn zones", "👾"));
        spatial.addItem(new RadialItem("Chunk Perf",
            () -> ChunkPerformanceVisualizer.INSTANCE.isEnabled(),
            v -> ChunkPerformanceVisualizer.INSTANCE.setEnabled(v),
            "Show chunk render performance", "📦"));
        categories.add(spatial);

        // Category 3: Performance & Stats
        RadialCategory performance = new RadialCategory("Perf", 0xFF00FF88, "📊");
        performance.addItem(new RadialItem("FPS Tracker",
            () -> FpsTracker.INSTANCE.isEnabled(),
            v -> FpsTracker.INSTANCE.setEnabled(v),
            "Display FPS graph and statistics", "🎮"));
        performance.addItem(new RadialItem("Entity Density",
            () -> EntityDensityOverlay.isEnabled(),
            v -> EntityDensityOverlay.setEnabled(v),
            "Show entity count per area", "👥"));
        performance.addItem(new RadialItem("Heatmaps",
            () -> HeatmapVisualizer.INSTANCE.hasActiveHeatmaps(),
            v -> HeatmapVisualizer.INSTANCE.toggle(HeatmapVisualizer.HeatmapType.DEATH),
            "Toggle death heatmap visualization", "🔥"));
        performance.addItem(new RadialItem("Profiler",
            () -> com.frenkvs.devmod.telemetry.PerformanceProfiler.INSTANCE.isEnabled(),
            v -> com.frenkvs.devmod.telemetry.PerformanceProfiler.INSTANCE.setEnabled(v),
            "Performance profiling overlay", "⏱"));
        performance.addItem(new RadialItem("Attr Monitor",
            () -> com.frenkvs.devmod.attributes.AttributeMonitoringSystem.INSTANCE.isEnabled(),
            v -> com.frenkvs.devmod.attributes.AttributeMonitoringSystem.INSTANCE.setEnabled(v),
            "Track entity attributes in real-time", "📈"));
        categories.add(performance);

        // Category 4: Combat & Boss
        RadialCategory combat = new RadialCategory("Combat", 0xFFFF4466, "⚔");
        combat.addItem(new RadialItem("Boss Phases",
            () -> BossPhaseOverlay.isEnabled(),
            v -> BossPhaseOverlay.setEnabled(v),
            "Display boss phase information", "👹"));
        combat.addItem(new RadialItem("Skill Efficacy",
            () -> SkillEfficacyOverlay.isEnabled(),
            v -> SkillEfficacyOverlay.setEnabled(v),
            "Track skill effectiveness", "✨"));
        combat.addItem(new RadialItem("Economy",
            () -> EconomyOverlay.isEnabled(),
            v -> EconomyOverlay.setEnabled(v),
            "Show loot and gold statistics", "💰"));
        categories.add(combat);

        // Category 5: Screens & Tools
        RadialCategory tools = new RadialCategory("Tools", 0xFFFF9900, "🔧");
        tools.addItem(new RadialItem("Settings",
            () -> false,
            v -> openScreen(() -> new com.frenkvs.devmod.ui.unified.UnifiedSettingsScreen(null)),
            "Open DevMod configuration", true, "⚙"));
        tools.addItem(new RadialItem("Mob Editor",
            () -> cachedTargetEntity instanceof net.minecraft.world.entity.Mob, // Shows green if mob is targeted
            v -> openMobEditor(),
            getMobEditorTooltip(), true, "🐾"));
        tools.addItem(new RadialItem("Item Editor",
            () -> false,
            v -> openScreen(() -> new com.frenkvs.devmod.WeaponEditorScreen()),
            "Edit weapons and items", true, "🗡"));
        tools.addItem(new RadialItem("Dashboard",
            () -> false,
            v -> openScreen(() -> new com.frenkvs.devmod.TelemetryDashboardScreen(null)),
            "Telemetry analytics dashboard", true, "📋"));
        tools.addItem(new RadialItem("Testing Hub",
            () -> false,
            v -> openScreen(() -> new com.frenkvs.devmod.ui.hub.TestingHub()),
            "QA testing and validation tools", true, "🧪"));
        tools.addItem(new RadialItem("Quick Test",
            () -> false,
            v -> openScreen(() -> new com.frenkvs.devmod.ui.wizard.QuickTestWizard()),
            "Guided workflow to start integrated testing", true, "⚡"));
        tools.addItem(new RadialItem("Quick Help",
            () -> com.frenkvs.devmod.hud.QuickHelpOverlay.isEnabled(),
            v -> com.frenkvs.devmod.hud.QuickHelpOverlay.setEnabled(v),
            "Show keybind help overlay", "❓"));
        categories.add(tools);

        // Category 6: Quest System
        RadialCategory quest = new RadialCategory("Quest", 0xFFCC44FF, "📜");
        quest.addItem(new RadialItem("Quest HUD",
            () -> com.frenkvs.devmod.quest.QuestHudOverlay.isEnabled(),
            v -> com.frenkvs.devmod.quest.QuestHudOverlay.setEnabled(v),
            "Quest tracker overlay", "📋"));
        quest.addItem(new RadialItem("Endurance HUD",
            () -> EnduranceQuestOverlay.isEnabled(),
            v -> EnduranceQuestOverlay.setEnabled(v),
            "Endurance quest overlay", "💪"));
        quest.addItem(new RadialItem("Quest Editor",
            () -> false,
            v -> openScreen(() -> new com.frenkvs.devmod.quest.QuestEditorScreen()),
            "Create and edit quests", true, "✏"));
        quest.addItem(new RadialItem("Endurance",
            () -> false,
            v -> openScreen(() -> new com.frenkvs.devmod.endurance.EnduranceQuestScreen()),
            "Start endurance quest mode", true, "🏆"));
        categories.add(quest);
    }

    /**
     * Get dynamic tooltip for Mob Editor based on whether a mob is targeted.
     */
    private String getMobEditorTooltip() {
        if (cachedTargetEntity instanceof net.minecraft.world.entity.Mob mob) {
            return "Edit: " + mob.getName().getString();
        }
        return "§cLook at a mob first, then open menu";
    }

    private void openScreen(java.util.function.Supplier<Screen> screenSupplier) {
        closing = true;
        Minecraft.getInstance().tell(() -> {
            try {
                Minecraft.getInstance().setScreen(screenSupplier.get());
            } catch (Exception e) {
                Minecraft.getInstance().setScreen(null);
            }
        });
    }

    /**
     * Opens Mob Editor for the mob the player was looking at when the menu opened.
     * Uses cached target since hitResult changes once the GUI is open.
     */
    private void openMobEditor() {
        closing = true;
        Minecraft mc = Minecraft.getInstance();

        mc.tell(() -> {
            // Use cached entity from when menu opened (hitResult is invalid once GUI is open)
            if (cachedTargetEntity instanceof net.minecraft.world.entity.Mob mob && mob.isAlive()) {
                try {
                    mc.setScreen(new com.frenkvs.devmod.MobConfigScreen(mob));
                } catch (Exception e) {
                    showErrorMessage(mc, "Error opening Mob Editor");
                    mc.setScreen(null);
                }
            } else {
                // No mob was targeted when menu opened
                showErrorMessage(mc, I18n.translate("devmod.message.look_at_mob_first").getString());
                mc.setScreen(null);
            }
        });
    }

    private void showErrorMessage(Minecraft mc, String message) {
        if (mc.player != null) {
            mc.player.displayClientMessage(
                I18n.translate("devmod.message.error_prefix", message),
                true // action bar
            );
            // Play error sound
            mc.getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS, 0.5f));
        }
    }

    @Override
    protected void init() {
        centerX = width / 2;
        centerY = height / 2;
        itemAnimations = new float[20]; // Max items per category

        // Notify onboarding tutorial that radial menu was opened
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

        // Calculate selection
        updateSelection(mouseX, mouseY);

        // Render background blur effect
        renderBackground(graphics);

        var pose = graphics.pose();
        pose.pushPose();

        // Apply opening animation with elastic effect
        float scale = easeOutBack(openAnimation);
        pose.translate(centerX, centerY, 0);
        pose.scale(scale, scale, 1f);
        pose.translate(-centerX, -centerY, 0);

        // Render glow behind selected category
        if (selectedCategory >= 0) {
            renderCategoryGlow(graphics, selectedCategory);
        }

        // Render outer ring (categories)
        renderCategories(graphics);

        // Render center hub
        renderCenterHub(graphics, mouseX, mouseY);

        // Render expanded items if category selected
        if (selectedCategory >= 0 && selectedCategory < categories.size()) {
            renderCategoryItems(graphics, categories.get(selectedCategory));
        }

        // Render tooltip
        renderTooltip(graphics, mouseX, mouseY);

        pose.popPose();

        // Render help text with fade in (200ms for snappier feel)
        float helpAlpha = Math.min(1f, (System.currentTimeMillis() - openTime) / 200f);
        renderHelpText(graphics, helpAlpha);
    }

    private void updateAnimations(float partialTick) {
        float delta = partialTick * 0.05f;

        // Main open/close animation with smooth easing
        float targetAnim = closing ? 0f : 1f;
        float animSpeed = closing ? 0.25f : 0.18f;
        openAnimation = Mth.lerp(animSpeed, openAnimation, targetAnim);

        // Pulse animation for active items
        pulsePhase += delta * 3f;
        if (pulsePhase > (float)(Math.PI * 2)) pulsePhase -= (float)(Math.PI * 2);

        // Category hover animations
        for (int i = 0; i < categories.size(); i++) {
            float target = (i == selectedCategory) ? 1f : 0f;
            categoryAnimations[i] = Mth.lerp(0.2f, categoryAnimations[i], target);
        }

        // Item hover animation
        float itemTarget = (selectedItem >= 0) ? 1f : 0f;
        itemHoverAnim = Mth.lerp(0.25f, itemHoverAnim, itemTarget);

        // Individual item animations
        if (selectedCategory >= 0 && selectedCategory < categories.size()) {
            RadialCategory cat = categories.get(selectedCategory);
            for (int i = 0; i < cat.items.size(); i++) {
                float target = (i == selectedItem) ? 1f : 0f;
                itemAnimations[i] = Mth.lerp(0.2f, itemAnimations[i], target);
            }
        }
    }

    private void updateSelection(int mouseX, int mouseY) {
        prevSelectedCategory = selectedCategory;
        prevSelectedItem = selectedItem;

        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);

        selectedCategory = -1;
        selectedItem = -1;

        if (distance < CENTER_BUTTON_RADIUS) {
            return;
        }

        // Calculate angle
        double angle = Math.atan2(dy, dx);
        if (angle < 0) angle += Math.PI * 2;

        int numCategories = categories.size();
        double segmentAngle = (Math.PI * 2) / numCategories;
        double startOffset = -Math.PI / 2 - segmentAngle / 2;

        double adjustedAngle = angle - startOffset;
        if (adjustedAngle < 0) adjustedAngle += Math.PI * 2;

        selectedCategory = (int)(adjustedAngle / segmentAngle) % numCategories;

        // Check for item selection in outer ring
        if (distance > OUTER_RADIUS && selectedCategory >= 0) {
            RadialCategory cat = categories.get(selectedCategory);
            int numItems = cat.items.size();
            if (numItems > 0) {
                double itemSegment = segmentAngle / numItems;
                double catStartAngle = startOffset + selectedCategory * segmentAngle;
                double relativeAngle = angle - catStartAngle;
                if (relativeAngle < 0) relativeAngle += Math.PI * 2;
                if (relativeAngle > Math.PI) relativeAngle -= Math.PI * 2;

                selectedItem = Mth.clamp((int)(relativeAngle / itemSegment), 0, numItems - 1);
            }
        }

        // Play sound on selection change
        if (selectedCategory != prevSelectedCategory || selectedItem != prevSelectedItem) {
            if (selectedItem >= 0 || (selectedCategory >= 0 && prevSelectedCategory != selectedCategory)) {
                // Subtle tick sound for feedback
            }
        }
    }

    private void renderBackground(GuiGraphics graphics) {
        // Gradient background with animation
        int alpha = (int)(0x90 * openAnimation);
        graphics.fill(0, 0, width, height, (alpha << 24) | 0x000510);

        // Radial gradient effect around center
        renderRadialGradient(graphics, centerX, centerY, (int)(300 * openAnimation), 0x20FFFFFF, 0x00000000);
    }

    private void renderCategoryGlow(GuiGraphics graphics, int categoryIndex) {
        RadialCategory cat = categories.get(categoryIndex);
        float anim = categoryAnimations[categoryIndex];

        double segmentAngle = (Math.PI * 2) / categories.size();
        double midAngle = -Math.PI / 2 + categoryIndex * segmentAngle;

        int glowX = (int)(centerX + Math.cos(midAngle) * ITEM_RADIUS);
        int glowY = (int)(centerY + Math.sin(midAngle) * ITEM_RADIUS);

        int glowRadius = (int)(45 * anim);
        int glowColor = (cat.color & 0x00FFFFFF) | ((int)(0x30 * anim) << 24);
        renderRadialGradient(graphics, glowX, glowY, glowRadius, glowColor, 0x00000000);
    }

    private void renderCenterHub(GuiGraphics graphics, int mouseX, int mouseY) {
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);
        boolean hovered = distance < CENTER_BUTTON_RADIUS;
        float hoverAnim = hovered ? Math.min(1f, categoryHoverAnim + 0.1f) : Math.max(0f, categoryHoverAnim - 0.05f);
        categoryHoverAnim = hoverAnim;

        // Outer glow ring
        int glowAlpha = (int)(0x40 * (1 + 0.3f * Math.sin(pulsePhase)));
        renderRing(graphics, centerX, centerY, CENTER_BUTTON_RADIUS + 3, CENTER_BUTTON_RADIUS + 8,
                   (glowAlpha << 24) | 0x6080FF);

        // Main circle with gradient
        int innerColor = hovered ? 0xFF2a2a55 : COLOR_BG_DARK;
        int outerColor = hovered ? 0xFF3a3a6a : COLOR_BG_LIGHT;
        renderGradientCircle(graphics, centerX, centerY, CENTER_BUTTON_RADIUS, innerColor, outerColor);

        // Border
        int borderColor = hovered ? 0xFF8080FF : COLOR_BORDER;
        renderRing(graphics, centerX, centerY, CENTER_BUTTON_RADIUS - 1, CENTER_BUTTON_RADIUS + 1, borderColor);

        // Center icon/text
        String centerText = hovered ? "✕" : "⚡";
        int textColor = hovered ? 0xFFFF6666 : 0xFF88CCFF;
        graphics.drawCenteredString(font, centerText, centerX, centerY - 8, textColor);

        String label = hovered ? I18n.translate("devmod.radial.close").getString() : I18n.translate("devmod.radial.devmod").getString();
        graphics.drawCenteredString(font, label, centerX, centerY + 4, COLOR_TEXT_DIM);
    }

    private void renderCategories(GuiGraphics graphics) {
        int numCategories = categories.size();
        double segmentAngle = (Math.PI * 2) / numCategories;
        double startOffset = -Math.PI / 2;

        for (int i = 0; i < numCategories; i++) {
            RadialCategory cat = categories.get(i);
            boolean selected = (i == selectedCategory);
            float anim = categoryAnimations[i];

            double startAngle = startOffset + (i - 0.5) * segmentAngle;
            double endAngle = startAngle + segmentAngle;

            // Segment background with animation
            int baseColor = selected ? blendColors(COLOR_BG_LIGHT, cat.color, 0.15f) : COLOR_BG_DARK;
            int segColor = blendColors(baseColor, cat.color, anim * 0.2f);
            renderArcSegment(graphics, centerX, centerY, INNER_RADIUS, OUTER_RADIUS, startAngle, endAngle, segColor);

            // Animated outer border glow
            if (anim > 0.01f) {
                int glowColor = (cat.color & 0x00FFFFFF) | ((int)(0x60 * anim) << 24);
                renderArcOutline(graphics, centerX, centerY, OUTER_RADIUS + (int)(5 * anim), startAngle, endAngle, glowColor, 2);
            }

            // Border
            int borderCol = selected ? cat.color : COLOR_BORDER;
            renderArcOutline(graphics, centerX, centerY, OUTER_RADIUS, startAngle, endAngle, borderCol, 1);

            // Category icon and label
            double midAngle = startOffset + i * segmentAngle;
            int iconX = (int)(centerX + Math.cos(midAngle) * ITEM_RADIUS);
            int iconY = (int)(centerY + Math.sin(midAngle) * ITEM_RADIUS);

            // Icon with scale animation
            float iconScale = 1f + 0.15f * anim;
            var pose = graphics.pose();
            pose.pushPose();
            pose.translate(iconX, iconY - 6, 0);
            pose.scale(iconScale, iconScale, 1f);
            pose.translate(-iconX, -(iconY - 6), 0);

            int iconColor = selected ? cat.color : COLOR_TEXT_DIM;
            graphics.drawCenteredString(font, cat.icon, iconX, iconY - 10, iconColor);

            pose.popPose();

            // Category name
            int textColor = blendColors(COLOR_TEXT_DIM, cat.color, anim);
            graphics.drawCenteredString(font, cat.name, iconX, iconY + 4, textColor);

            // Active count badge
            int activeCount = countActiveItems(cat);
            if (activeCount > 0) {
                int badgeX = iconX + 18;
                int badgeY = iconY - 12;
                float pulse = 0.8f + 0.2f * (float)Math.sin(pulsePhase * 2);
                int badgeColor = blendColors(COLOR_ACTIVE, 0xFFFFFFFF, pulse * 0.3f);

                graphics.fill(badgeX - 6, badgeY - 4, badgeX + 6, badgeY + 6, 0xDD000000);
                graphics.drawCenteredString(font, String.valueOf(activeCount), badgeX, badgeY - 2, badgeColor);
            }
        }
    }

    private void renderCategoryItems(GuiGraphics graphics, RadialCategory category) {
        int numItems = category.items.size();
        if (numItems == 0) return;

        double segmentAngle = (Math.PI * 2) / categories.size();
        double startOffset = -Math.PI / 2;
        double catStartAngle = startOffset + (selectedCategory - 0.5) * segmentAngle;
        double itemAngleStep = segmentAngle / numItems;

        float categoryAnim = categoryAnimations[selectedCategory];

        for (int i = 0; i < numItems; i++) {
            RadialItem item = category.items.get(i);
            boolean itemSelected = (i == selectedItem);
            boolean isActive = !item.isAction && item.getter.getAsBoolean();
            float itemAnim = itemAnimations[i];

            double itemAngle = catStartAngle + (i + 0.5) * itemAngleStep;

            // Staggered appear animation
            float stagger = Math.min(1f, categoryAnim * 2f - (i * 0.15f));
            stagger = Math.max(0f, stagger);
            float appearScale = easeOutBack(stagger);

            int baseRadius = OUTER_RADIUS + 45;
            int itemX = (int)(centerX + Math.cos(itemAngle) * (baseRadius + 8 * itemAnim));
            int itemY = (int)(centerY + Math.sin(itemAngle) * (baseRadius + 8 * itemAnim));

            var pose = graphics.pose();
            pose.pushPose();
            pose.translate(itemX, itemY, 0);
            pose.scale(appearScale, appearScale, 1f);
            pose.translate(-itemX, -itemY, 0);

            int itemRadius = 30 + (int)(4 * itemAnim);

            // Glow for active items
            if (isActive) {
                float pulse = 0.6f + 0.4f * (float)Math.sin(pulsePhase + i * 0.5f);
                int glowAlpha = (int)(0x40 * pulse);
                renderRadialGradient(graphics, itemX, itemY, itemRadius + 10, (glowAlpha << 24) | 0x00FF88, 0x00000000);
            }

            // Item background
            int bgColor = itemSelected ? blendColors(COLOR_HOVER, category.color, 0.2f) : 0xCC151525;
            renderGradientCircle(graphics, itemX, itemY, itemRadius, bgColor,
                                 blendColors(bgColor, 0xFF000000, 0.3f));

            // Border with selection animation
            int borderColor = isActive ? COLOR_ACTIVE : (itemSelected ? category.color : COLOR_BORDER);
            int borderWidth = itemSelected ? 2 : 1;
            renderRing(graphics, itemX, itemY, itemRadius - borderWidth, itemRadius, borderColor);

            // Item icon
            String icon = item.icon;
            graphics.drawCenteredString(font, icon, itemX, itemY - 12,
                                        itemSelected ? COLOR_TEXT : COLOR_TEXT_DIM);

            // Item name (truncated)
            String name = item.name;
            if (font.width(name) > 50) {
                while (font.width(name + "..") > 50 && name.length() > 3) {
                    name = name.substring(0, name.length() - 1);
                }
                name += "..";
            }
            int nameColor = itemSelected ? COLOR_TEXT : (isActive ? COLOR_ACTIVE : COLOR_TEXT_DIM);
            graphics.drawCenteredString(font, name, itemX, itemY + 1, nameColor);

            // Status indicator
            if (!item.isAction) {
                String status = isActive ? "● ON" : "○ OFF";
                int statusColor = isActive ? COLOR_ACTIVE : 0xFF886666;
                graphics.drawCenteredString(font, status, itemX, itemY + 14, statusColor);
            } else {
                graphics.drawCenteredString(font, "▶", itemX, itemY + 14, COLOR_TEXT_DIM);
            }

            pose.popPose();
        }
    }

    private void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        String tooltip = null;

        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        if (Math.sqrt(dx * dx + dy * dy) < CENTER_BUTTON_RADIUS) {
            tooltip = I18n.translate("devmod.message.press_esc_or_click_to_close").getString();
        } else if (selectedCategory >= 0 && selectedCategory < categories.size()) {
            RadialCategory cat = categories.get(selectedCategory);
            if (selectedItem >= 0 && selectedItem < cat.items.size()) {
                tooltip = cat.items.get(selectedItem).description;
            }
        }

        if (tooltip != null) {
            int tooltipWidth = font.width(tooltip);
            int tooltipX = centerX - tooltipWidth / 2;
            int tooltipY = centerY + ITEM_OUTER_RADIUS + 25;

            // Tooltip background with border
            int padding = 6;
            graphics.fill(tooltipX - padding - 1, tooltipY - padding - 1,
                         tooltipX + tooltipWidth + padding + 1, tooltipY + 10 + padding + 1,
                         COLOR_BORDER);
            graphics.fill(tooltipX - padding, tooltipY - padding,
                         tooltipX + tooltipWidth + padding, tooltipY + 10 + padding,
                         0xF0101020);
            graphics.drawString(font, tooltip, tooltipX, tooltipY, COLOR_TEXT, false);
        }
    }

    private void renderHelpText(GuiGraphics graphics, float alpha) {
        int textAlpha = (int)(0xAA * alpha);
        int helpColor = (textAlpha << 24) | 0xAAAAAA;
        int helpAccentColor = (textAlpha << 24) | 0xDDDDDD;

        // Two-line help text for better readability
        String helpLine1 = I18n.translate("devmod.radial.help_line1").getString();
        String helpLine2 = I18n.translate("devmod.radial.help_line2").getString();

        graphics.drawCenteredString(font, helpLine1, width / 2, height - 35, helpColor);
        graphics.drawCenteredString(font, helpLine2, width / 2, height - 22, helpAccentColor);
    }

    private int countActiveItems(RadialCategory category) {
        int count = 0;
        for (RadialItem item : category.items) {
            if (!item.isAction && item.getter.getAsBoolean()) {
                count++;
            }
        }
        return count;
    }

    // === Input Handling ===

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            activateSelection();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        // Release G key to activate selection (hold G, move mouse, release to toggle)
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_G) {
            activateSelection();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            closing = true;
            return true;
        }

        // Number keys for quick category selection
        if (keyCode >= org.lwjgl.glfw.GLFW.GLFW_KEY_1 && keyCode <= org.lwjgl.glfw.GLFW.GLFW_KEY_6) {
            int num = keyCode - org.lwjgl.glfw.GLFW.GLFW_KEY_1;
            if (num < categories.size()) {
                selectedCategory = num;
            }
            return true;
        }

        // Q, W, E, R, T, Y, U, I, O, P for item selection within category (up to 10 items)
        // This allows quick selection of all items without moving mouse
        if (selectedCategory >= 0) {
            RadialCategory cat = categories.get(selectedCategory);
            int itemNum = -1;

            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_Q) {
                itemNum = 0;
            } else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_W) {
                itemNum = 1;
            } else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_E) {
                itemNum = 2;
            } else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_R) {
                itemNum = 3;
            } else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_T) {
                itemNum = 4;
            } else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_Y) {
                itemNum = 5;
            } else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_U) {
                itemNum = 6;
            } else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_I) {
                itemNum = 7;
            } else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_O) {
                itemNum = 8;
            } else if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_P) {
                itemNum = 9;
            }

            // Shift + 1-6 for secondary category access (categories 7-12 if they exist)
            boolean shiftHeld = (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0;
            if (shiftHeld && keyCode >= org.lwjgl.glfw.GLFW.GLFW_KEY_1 && keyCode <= org.lwjgl.glfw.GLFW.GLFW_KEY_6) {
                int secondaryCategory = 6 + (keyCode - org.lwjgl.glfw.GLFW.GLFW_KEY_1);
                if (secondaryCategory < categories.size()) {
                    selectedCategory = secondaryCategory;
                }
                return true;
            }

            if (itemNum >= 0 && itemNum < cat.items.size()) {
                toggleItem(cat.items.get(itemNum));
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void activateSelection() {
        double dx = minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getWidth() - centerX;
        double dy = minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getHeight() - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);

        if (distance < CENTER_BUTTON_RADIUS) {
            closing = true;
            return;
        }

        if (selectedCategory >= 0 && selectedCategory < categories.size()) {
            RadialCategory cat = categories.get(selectedCategory);
            if (selectedItem >= 0 && selectedItem < cat.items.size()) {
                toggleItem(cat.items.get(selectedItem));
            }
        }
    }

    private void toggleItem(RadialItem item) {
        if (item.isAction) {
            item.setter.accept(true);
            // Actions close menu immediately (handled by action itself)
        } else {
            boolean current = item.getter.getAsBoolean();
            boolean newState = !current;
            item.setter.accept(newState);

            // Persist overlay states to settings
            SettingsManager.INSTANCE.syncFromSystems();
            SettingsManager.INSTANCE.save();

            // Notify onboarding tutorial that an overlay was toggled
            OnboardingOverlay.onOverlayToggled();

            // Show brief feedback - menu stays open for rapid multi-toggle
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                String statusKey = newState ? "devmod.status.on" : "devmod.status.off";
                mc.player.displayClientMessage(
                    I18n.translate("devmod.message.item_toggled", item.name, I18n.translate(statusKey).getString()),
                    true // action bar
                );
            }

            // Play distinct sound for ON vs OFF state
            float pitch = newState ? 1.2f : 0.8f;
            Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, pitch));

            // NOTE: Menu stays open to allow rapid multi-toggle.
            // User can close with ESC or center button.
        }

        // Notify onboarding tutorial that a category item was selected
        OnboardingOverlay.onCategorySelected();

        // Only play generic click sound for actions (overlays have custom sound above)
        if (item.isAction) {
            Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }

    // === Drawing Helpers ===

    private float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        return (float)(1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2));
    }

    private int blendColors(int color1, int color2, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int)(a1 + (a2 - a1) * t);
        int r = (int)(r1 + (r2 - r1) * t);
        int g = (int)(g1 + (g2 - g1) * t);
        int b = (int)(b1 + (b2 - b1) * t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void renderRadialGradient(GuiGraphics graphics, int cx, int cy, int radius, int centerColor, int edgeColor) {
        int segments = 32;
        int rings = 8;

        for (int ring = 0; ring < rings; ring++) {
            float t1 = (float)ring / rings;
            float t2 = (float)(ring + 1) / rings;
            int r1 = (int)(radius * t1);
            int r2 = (int)(radius * t2);
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

    // === Data Classes ===

    private static class RadialCategory {
        final String name;
        final int color;
        final String icon;
        final List<RadialItem> items = new ArrayList<>();

        RadialCategory(String name, int color, String icon) {
            this.name = name;
            this.color = color;
            this.icon = icon;
        }

        void addItem(RadialItem item) {
            items.add(item);
        }
    }

    private static class RadialItem {
        final String name;
        final BooleanSupplier getter;
        final Consumer<Boolean> setter;
        final String description;
        final boolean isAction;
        final String icon;

        RadialItem(String name, BooleanSupplier getter, Consumer<Boolean> setter, String description, String icon) {
            this(name, getter, setter, description, false, icon);
        }

        RadialItem(String name, BooleanSupplier getter, Consumer<Boolean> setter, String description, boolean isAction, String icon) {
            this.name = name;
            this.getter = getter;
            this.setter = setter;
            this.description = description;
            this.isAction = isAction;
            this.icon = icon;
        }
    }
}
