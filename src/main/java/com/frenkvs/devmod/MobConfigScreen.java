package com.frenkvs.devmod;

import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.UIConstants;
import com.frenkvs.devmod.util.I18n;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nonnull;

/**
 * Advanced Mob Configuration Screen with:
 * - 3D animated mob preview with mouse rotation
 * - Visual sliders with real-time feedback
 * - Preset system (Tank, Glass Cannon, Balanced, etc.)
 * - Before/After comparison
 * - Tabbed interface (Stats/Combat/AI)
 * - Smooth animations
 */
@SuppressWarnings("null")
public class MobConfigScreen extends Screen {

    // Layout constants
    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 340;
    private static final int PREVIEW_SIZE = 140;
    private static final int SLIDER_WIDTH = 160;
    private static final int SLIDER_HEIGHT = 12;
    private static final int ROW_HEIGHT = 26;
    private static final int TAB_WIDTH = 70;
    private static final int TAB_HEIGHT = 22;

    // Slider max values - configurable for different use cases
    // These are generous maximums; actual mob values rarely exceed half
    private static final double MAX_HEALTH = 500.0;      // Boss mobs can have 200-300+ HP
    private static final double MAX_ARMOR = 30.0;        // Vanilla max is 30
    private static final double MAX_SPEED = 1.0;         // 1.0 = 100%, very fast
    private static final double MAX_DAMAGE = 100.0;      // Boss mobs can hit hard
    private static final double MAX_ATTACK_RANGE = 16.0; // Extended melee range
    private static final double MAX_FOLLOW_RANGE = 128.0;// Long-range tracking
    private static final double MAX_KNOCKBACK_RES = 1.0; // 0-1 scale (100% = immune)

    // Persist mode preference
    private static boolean lastGlobalMode = false;

    private final Mob mob;
    private boolean isGlobalMode = lastGlobalMode;

    // Current values (editable)
    private double health, armor, damage, followRange, attackRange;
    private double speed, knockbackResist;

    // Original values (for comparison)
    private final double origHealth, origArmor, origDamage, origFollowRange, origAttackRange;
    private final double origSpeed, origKnockbackResist;

    // UI State
    private int selectedTab = 0; // 0=Stats, 1=Combat, 2=AI
    private int activeSlider = -1;
    private int mouseX, mouseY;

    // Mob preview rotation
    private float rotationX = 0;
    private float rotationY = 0;
    private float targetRotationY = -30;
    private boolean isDraggingPreview = false;
    private int dragStartX, dragStartY;
    private float dragStartRotX, dragStartRotY;
    private float previewZoom = 1.0f; // Zoom level for preview (0.5 - 2.0)

    // Animation
    private float animationProgress = 0;
    private long lastFrameTime = 0;
    private float[] sliderAnimations = new float[7]; // Animation progress for each slider

    // Direct numeric input state
    private int editingSlider = -1;  // Which slider is being edited (-1 = none)
    private StringBuilder inputBuffer = new StringBuilder();
    private long inputCursorBlink = 0;

    // Presets
    private static final String[] PRESET_NAMES = {"Tank", "Glass Cannon", "Speedy", "Balanced", "Boss", "Custom"};
    private static final String[] PRESET_DESCRIPTIONS = {
        "High HP & armor, low speed/damage",    // Tank
        "2.5x damage, 0.5x health, no armor",   // Glass Cannon
        "2x speed, extended follow range",      // Speedy
        "Reset to original mob stats",          // Balanced
        "3x HP, 2x damage, full knockback res", // Boss
        "Your custom configuration"             // Custom
    };
    private static final int[][] PRESET_COLORS = {
        {UIConstants.Accent.CYAN, UIConstants.Accent.BLUE},      // Tank
        {UIConstants.Accent.RED, UIConstants.Accent.ORANGE},     // Glass Cannon
        {UIConstants.Accent.GREEN, UIConstants.Accent.YELLOW},   // Speedy
        {0xFFAAAAAA, 0xFF888888},                                 // Balanced
        {UIConstants.Accent.PURPLE, UIConstants.Accent.GOLD},    // Boss
        {UIConstants.Border.DEFAULT, UIConstants.Border.LIGHT}   // Custom
    };
    private int selectedPreset = 5; // Default to Custom
    private int hoveredPreset = -1; // For tooltip display
    private int hoveredUserPreset = -1; // For user presets
    private boolean showSavePresetDialog = false;
    private StringBuilder presetNameInput = new StringBuilder();
    private String deleteConfirmPreset = null; // Name of preset pending deletion

    // Blur control
    private int originalBlurValue = 0;

    public MobConfigScreen(Mob mob) {
        super(I18n.translate("devmod.screen.mob_config", mob.getName().getString()));
        this.mob = mob;

        // Store original values
        this.origHealth = getVal(Attributes.MAX_HEALTH);
        this.origArmor = getVal(Attributes.ARMOR);
        this.origDamage = getVal(Attributes.ATTACK_DAMAGE);
        this.origFollowRange = getVal(Attributes.FOLLOW_RANGE);
        this.origSpeed = getVal(Attributes.MOVEMENT_SPEED);
        this.origKnockbackResist = getVal(Attributes.KNOCKBACK_RESISTANCE);

        double reach = getVal(Attributes.ENTITY_INTERACTION_RANGE);
        if (reach <= 0.1) {
            reach = mob.getBbWidth() * 2.0 + 1.0;
        }
        this.origAttackRange = reach;

        // Initialize current values
        this.health = origHealth;
        this.armor = origArmor;
        this.damage = origDamage;
        this.followRange = origFollowRange;
        this.attackRange = origAttackRange;
        this.speed = origSpeed;
        this.knockbackResist = origKnockbackResist;

        // Disable blur
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            OptionInstance<Integer> blurOption = mc.options.menuBackgroundBlurriness();
            originalBlurValue = blurOption.get();
            blurOption.set(0);
        }

        lastFrameTime = System.currentTimeMillis();
    }

    @Override
    protected void init() {
        // No widgets needed - we render everything manually for full control
    }

    private double getVal(@Nonnull Holder<Attribute> attr) {
        var attribute = mob.getAttribute(attr);
        if (attribute != null) {
            return mob.getAttributeValue(attr);
        }
        return 0;
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Check if mob died while screen is open
        if (!mob.isAlive()) {
            // Show warning message to player before closing
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.displayClientMessage(
                    I18n.translate("devmod.mobconfig.target_died"),
                    false  // Show in chat for visibility
                );
                // Play warning sound
                minecraft.player.playSound(net.minecraft.sounds.SoundEvents.VILLAGER_NO, 0.8f, 1.0f);
            }
            this.onClose();
            return;
        }

        this.mouseX = mouseX;
        this.mouseY = mouseY;

        // Update animations
        updateAnimations();

        // Background
        AxiomRenderer.drawScreenBackground(graphics, this.width, this.height);

        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        // Main panel with gradient header
        drawMainPanel(graphics, panelX, panelY);

        // Global mode warning banner (above panel when active)
        if (isGlobalMode) {
            drawGlobalModeWarning(graphics, panelX, panelY - 28);
        }

        // Tab bar
        drawTabs(graphics, panelX, panelY);

        // Content area
        int contentY = panelY + UIConstants.Spacing.HEADER_HEIGHT + TAB_HEIGHT + 8;

        // Left side: Mob preview
        int previewX = panelX + 15;
        int previewY = contentY + 10;
        drawMobPreview(graphics, previewX, previewY);

        // Right side: Sliders based on selected tab
        int slidersX = panelX + PREVIEW_SIZE + 40;
        int slidersY = contentY + 5;
        drawSliders(graphics, slidersX, slidersY);

        // Bottom: Mode toggle, presets, and buttons
        int bottomY = panelY + PANEL_HEIGHT - 70;
        drawBottomSection(graphics, panelX, bottomY);

        // Tooltips (render last, before dialog)
        drawTooltips(graphics);

        // Confirmation dialog (render on top of everything)
        if (showingConfirmDialog) {
            drawConfirmDialog(graphics);
        }

        // Save preset dialog
        if (showSavePresetDialog) {
            drawSavePresetDialog(graphics);
        }

        // Delete confirmation dialog
        if (deleteConfirmPreset != null) {
            drawDeletePresetDialog(graphics);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawMainPanel(GuiGraphics graphics, int panelX, int panelY) {
        // Panel background with subtle gradient
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, UIConstants.Background.PANEL_SOLID);

        // Gradient header
        int headerH = UIConstants.Spacing.HEADER_HEIGHT;
        for (int i = 0; i < headerH; i++) {
            float t = (float) i / headerH;
            int color = UIConstants.lerp(0xFF2A2A42, UIConstants.Background.HEADER, t);
            graphics.fill(panelX, panelY + i, panelX + PANEL_WIDTH, panelY + i + 1, color);
        }

        // Border with glow effect
        int glowColor = UIConstants.setAlpha(UIConstants.Border.DEFAULT, 80);
        graphics.fill(panelX - 2, panelY - 2, panelX + PANEL_WIDTH + 2, panelY, glowColor);
        graphics.fill(panelX - 2, panelY + PANEL_HEIGHT, panelX + PANEL_WIDTH + 2, panelY + PANEL_HEIGHT + 2, glowColor);
        graphics.fill(panelX - 2, panelY, panelX, panelY + PANEL_HEIGHT, glowColor);
        graphics.fill(panelX + PANEL_WIDTH, panelY, panelX + PANEL_WIDTH + 2, panelY + PANEL_HEIGHT, glowColor);

        AxiomRenderer.drawBorder(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, UIConstants.Border.DEFAULT);

        // Title with mob name
        String title = "§l" + mob.getName().getString();
        graphics.drawString(font, title, panelX + 12, panelY + 6, UIConstants.Text.PRIMARY, false);

        // Mob type (smaller, muted)
        String type = mob.getType().getDescriptionId().replace("entity.minecraft.", "").replace("_", " ");
        type = Character.toUpperCase(type.charAt(0)) + type.substring(1);
        graphics.drawString(font, "(" + type + ")", panelX + 14 + font.width(title), panelY + 6, UIConstants.Text.MUTED, false);

        // Health indicator in header
        float healthPercent = mob.getHealth() / mob.getMaxHealth();
        int healthBarX = panelX + PANEL_WIDTH - 80;
        int healthBarY = panelY + 5;
        drawMiniHealthBar(graphics, healthBarX, healthBarY, 70, 10, healthPercent);
    }

    private void drawMiniHealthBar(GuiGraphics graphics, int x, int y, int w, int h, float percent) {
        graphics.fill(x, y, x + w, y + h, UIConstants.Background.INPUT);
        int fillW = (int) (w * percent);
        int color = percent > 0.5f ? UIConstants.Accent.GREEN :
                    (percent > 0.25f ? UIConstants.Accent.YELLOW : UIConstants.Accent.RED);
        if (fillW > 0) {
            graphics.fill(x, y, x + fillW, y + h, color);
        }
        AxiomRenderer.drawBorder(graphics, x, y, w, h, UIConstants.Border.MUTED);
        // Heart icon placeholder
        graphics.drawString(font, "§c❤", x + 2, y + 1, 0xFFFFFFFF, false);
    }

    private void drawTabs(GuiGraphics graphics, int panelX, int panelY) {
        String[] tabs = {"Stats", "Combat", "AI"};
        int tabsX = panelX + 10;
        int tabsY = panelY + UIConstants.Spacing.HEADER_HEIGHT;

        for (int i = 0; i < tabs.length; i++) {
            int tx = tabsX + i * (TAB_WIDTH + 4);
            boolean selected = (selectedTab == i);
            boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, tx, tabsY, TAB_WIDTH, TAB_HEIGHT);

            // Tab background
            int bgColor = selected ? UIConstants.Background.ACTIVE :
                          (hovered ? UIConstants.Background.HOVER : UIConstants.Background.HEADER);
            graphics.fill(tx, tabsY, tx + TAB_WIDTH, tabsY + TAB_HEIGHT, bgColor);

            // Selected indicator
            if (selected) {
                graphics.fill(tx, tabsY + TAB_HEIGHT - 2, tx + TAB_WIDTH, tabsY + TAB_HEIGHT, UIConstants.Border.ACCENT);
            }

            // Tab text
            int textColor = selected ? UIConstants.Text.ACCENT : UIConstants.Text.PRIMARY;
            int textW = font.width(tabs[i]);
            graphics.drawString(font, tabs[i], tx + (TAB_WIDTH - textW) / 2, tabsY + 7, textColor, false);
        }

        // Separator line under tabs
        AxiomRenderer.drawSeparator(graphics, panelX + 10, tabsY + TAB_HEIGHT + 4, PANEL_WIDTH - 20);
    }

    private void drawMobPreview(GuiGraphics graphics, int x, int y) {
        // Preview box
        int boxW = PREVIEW_SIZE;
        int boxH = PREVIEW_SIZE + 20;

        // Background gradient
        for (int i = 0; i < boxH; i++) {
            float t = (float) i / boxH;
            int color = UIConstants.lerp(0xFF151525, 0xFF1A1A2E, t);
            graphics.fill(x, y + i, x + boxW, y + i + 1, color);
        }

        // Border
        AxiomRenderer.drawBorder(graphics, x, y, boxW, boxH, UIConstants.Border.MUTED);

        // Corner accents
        int accentLen = 10;
        int accentColor = UIConstants.Border.ACCENT;
        // Top-left
        graphics.fill(x, y, x + accentLen, y + 2, accentColor);
        graphics.fill(x, y, x + 2, y + accentLen, accentColor);
        // Top-right
        graphics.fill(x + boxW - accentLen, y, x + boxW, y + 2, accentColor);
        graphics.fill(x + boxW - 2, y, x + boxW, y + accentLen, accentColor);
        // Bottom-left
        graphics.fill(x, y + boxH - 2, x + accentLen, y + boxH, accentColor);
        graphics.fill(x, y + boxH - accentLen, x + 2, y + boxH, accentColor);
        // Bottom-right
        graphics.fill(x + boxW - accentLen, y + boxH - 2, x + boxW, y + boxH, accentColor);
        graphics.fill(x + boxW - 2, y + boxH - accentLen, x + boxW, y + boxH, accentColor);

        // Smooth rotation
        rotationY = Mth.lerp(0.1f, rotationY, targetRotationY);

        // Render the mob
        int centerX = x + boxW / 2;
        int centerY = y + boxH - 20;

        // Calculate scale based on mob size and zoom level
        float mobHeight = mob.getBbHeight();
        float mobWidth = mob.getBbWidth();
        float maxDim = Math.max(mobHeight, mobWidth);
        int scale = (int) (Math.min(50, 100 / maxDim) * previewZoom);

        // Create rotation quaternion
        Quaternionf rotation = new Quaternionf()
            .rotateY((float) Math.toRadians(-rotationY))
            .rotateX((float) Math.toRadians(rotationX));

        try {
            // Render entity
            InventoryScreen.renderEntityInInventory(
                graphics,
                centerX, centerY,
                scale,
                new Vector3f(0, 0, 0),
                rotation,
                null,
                mob
            );
        } catch (Exception e) {
            // Fallback if rendering fails
            graphics.drawString(font, "Preview", centerX - 20, centerY - 40, UIConstants.Text.MUTED, false);
        }

        // Drag/scroll hint
        boolean hovering = AxiomRenderer.isMouseOver(mouseX, mouseY, x, y, boxW, boxH);
        if (hovering && !isDraggingPreview) {
            graphics.drawString(font, "§8Drag: rotate | Scroll: zoom", x + 4, y + boxH - 12, UIConstants.Text.MUTED, false);
        } else if (isDraggingPreview) {
            graphics.drawString(font, "§a↻ Rotating...", x + 4, y + boxH - 12, UIConstants.Accent.GREEN, false);
        }

        // Show zoom level if not default
        if (Math.abs(previewZoom - 1.0f) > 0.05f) {
            String zoomText = String.format("%.0f%%", previewZoom * 100);
            graphics.drawString(font, zoomText, x + boxW - font.width(zoomText) - 4, y + 4, UIConstants.Text.MUTED, false);
        }
    }

    private void drawSliders(GuiGraphics graphics, int x, int y) {
        String[] labels;
        double[] values;
        double[] originals;
        double[] maxValues;
        int[] colors;

        switch (selectedTab) {
            case 0: // Stats
                labels = new String[]{"Max Health", "Armor", "Speed"};
                values = new double[]{health, armor, speed * 100}; // Speed scaled for display
                originals = new double[]{origHealth, origArmor, origSpeed * 100};
                maxValues = new double[]{MAX_HEALTH, MAX_ARMOR, MAX_SPEED * 100}; // Use configurable max values
                colors = new int[]{UIConstants.Accent.RED, UIConstants.Accent.CYAN, UIConstants.Accent.GREEN};
                break;
            case 1: // Combat
                labels = new String[]{"Attack Damage", "Attack Reach", "Knockback Resist"};
                values = new double[]{damage, attackRange, knockbackResist * 100};
                originals = new double[]{origDamage, origAttackRange, origKnockbackResist * 100};
                maxValues = new double[]{MAX_DAMAGE, MAX_ATTACK_RANGE, MAX_KNOCKBACK_RES * 100};
                colors = new int[]{UIConstants.Accent.ORANGE, UIConstants.Accent.YELLOW, UIConstants.Accent.PURPLE};
                break;
            case 2: // AI
                labels = new String[]{"Follow Range"};
                values = new double[]{followRange};
                originals = new double[]{origFollowRange};
                maxValues = new double[]{MAX_FOLLOW_RANGE};
                colors = new int[]{UIConstants.Accent.BLUE};
                break;
            default:
                return;
        }

        for (int i = 0; i < labels.length; i++) {
            int rowY = y + i * (ROW_HEIGHT + 16);
            drawSliderRow(graphics, x, rowY, labels[i], values[i], originals[i], maxValues[i], colors[i], selectedTab * 10 + i);
        }
    }

    private void drawSliderRow(GuiGraphics graphics, int x, int y, String label, double value,
                               double original, double maxVal, int accentColor, int sliderId) {
        // Label
        graphics.drawString(font, label, x, y, UIConstants.Text.PRIMARY, false);

        // Value display area coordinates
        int valueX = x + SLIDER_WIDTH + 10;
        int valueW = 35;
        int valueH = 12;

        // Check if this slider is in edit mode
        if (editingSlider == sliderId) {
            // Draw input box
            graphics.fill(valueX - 2, y - 2, valueX + valueW + 2, y + valueH, UIConstants.Background.INPUT);
            AxiomRenderer.drawBorder(graphics, valueX - 2, y - 2, valueW + 4, valueH + 2, UIConstants.Accent.GOLD);

            // Draw input text with blinking cursor
            String inputText = inputBuffer.toString();
            graphics.drawString(font, inputText, valueX, y, UIConstants.Text.WHITE, false);

            // Blinking cursor
            if ((System.currentTimeMillis() - inputCursorBlink) % 1000 < 500) {
                int cursorX = valueX + font.width(inputText);
                graphics.fill(cursorX, y, cursorX + 1, y + 9, UIConstants.Text.WHITE);
            }
        } else {
            // Normal value display (clickable)
            String valueStr = String.format("%.1f", value);
            boolean valueHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, valueX - 2, y - 2, valueW + 4, valueH + 2);

            // Highlight when hovered to indicate clickability
            if (valueHovered) {
                graphics.fill(valueX - 2, y - 2, valueX + valueW + 2, y + valueH, UIConstants.Background.HOVER);
                AxiomRenderer.drawBorder(graphics, valueX - 2, y - 2, valueW + 4, valueH + 2, UIConstants.Border.LIGHT);
            }

            int valueColor = Math.abs(value - original) > 0.01 ? UIConstants.Accent.GOLD : UIConstants.Text.SECONDARY;
            graphics.drawString(font, valueStr, valueX, y, valueColor, false);
        }

        // Change indicator (always shown, right of value box)
        if (Math.abs(value - original) > 0.01) {
            double change = value - original;
            String changeStr = String.format("%+.1f", change);
            int changeColor = change > 0 ? UIConstants.Accent.GREEN : UIConstants.Accent.RED;
            graphics.drawString(font, changeStr, x + SLIDER_WIDTH + 50, y, changeColor, false);
        }

        // Slider track
        int sliderY = y + 12;
        int trackX = x;
        int trackW = SLIDER_WIDTH;
        int trackH = SLIDER_HEIGHT;

        // Track background
        graphics.fill(trackX, sliderY, trackX + trackW, sliderY + trackH, UIConstants.Background.INPUT);

        // Original value marker
        float origPercent = (float) (original / maxVal);
        int origMarkerX = trackX + (int) (trackW * Math.min(1, origPercent));
        graphics.fill(origMarkerX - 1, sliderY, origMarkerX + 1, sliderY + trackH, 0x80FFFFFF);

        // Value fill
        float percent = (float) (value / maxVal);
        int fillW = (int) (trackW * Math.min(1, percent));
        if (fillW > 0) {
            // Gradient fill
            for (int i = 0; i < fillW; i++) {
                float t = (float) i / trackW;
                int color = UIConstants.lerp(UIConstants.darken(accentColor, 0.3f), accentColor, t);
                graphics.fill(trackX + i, sliderY, trackX + i + 1, sliderY + trackH, color);
            }
        }

        // Slider handle
        int handleX = trackX + fillW - 3;
        int handleW = 6;
        boolean sliderHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, trackX - 5, sliderY - 5, trackW + 10, trackH + 10);

        if (sliderHovered || activeSlider == sliderId) {
            // Glow effect
            graphics.fill(handleX - 2, sliderY - 2, handleX + handleW + 2, sliderY + trackH + 2,
                UIConstants.setAlpha(accentColor, 80));
        }

        // Handle
        graphics.fill(handleX, sliderY - 1, handleX + handleW, sliderY + trackH + 1, accentColor);
        graphics.fill(handleX + 1, sliderY, handleX + handleW - 1, sliderY + trackH, UIConstants.lighten(accentColor, 0.3f));

        // Border
        AxiomRenderer.drawBorder(graphics, trackX, sliderY, trackW, trackH, UIConstants.Border.MUTED);
    }

    private void drawBottomSection(GuiGraphics graphics, int panelX, int y) {
        // Mode toggle (left side)
        int modeX = panelX + 15;
        boolean modeHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, modeX, y, 140, 20);

        // Mode background
        if (modeHovered) {
            graphics.fill(modeX - 2, y - 2, modeX + 142, y + 22, UIConstants.Background.HOVER);
        }

        graphics.drawString(font, "Mode:", modeX, y + 2, UIConstants.Text.SECONDARY, false);

        String modeText = isGlobalMode ? "§6GLOBAL" : "SPECIFIC";
        graphics.drawString(font, modeText, modeX + 40, y + 2, UIConstants.Text.PRIMARY, false);

        String modeHint = isGlobalMode ? "(All spawns)" : "(This mob)";
        graphics.drawString(font, modeHint, modeX + 40, y + 12, UIConstants.Text.MUTED, false);

        // Presets (center)
        drawPresets(graphics, panelX + 165, y - 5);

        // Action buttons (right side)
        int btnW = 70;
        int btnH = 22;
        int btnGap = 8;
        int btnsX = panelX + PANEL_WIDTH - 160;

        // Apply button
        boolean applyHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, btnsX, y + 20, btnW, btnH);
        drawStyledButton(graphics, btnsX, y + 20, btnW, btnH, "Apply", applyHovered, UIConstants.Accent.GREEN);

        // Equipment button
        int equipX = btnsX + btnW + btnGap;
        boolean equipHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, equipX, y + 20, btnW, btnH);
        drawStyledButton(graphics, equipX, y + 20, btnW, btnH, "Equip...", equipHovered, UIConstants.Accent.BLUE);

        // Reset button (small, above)
        int resetX = btnsX + (btnW * 2 + btnGap - 50) / 2;
        boolean resetHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, resetX, y - 2, 50, 16);
        if (resetHovered) {
            graphics.fill(resetX, y - 2, resetX + 50, y + 14, UIConstants.Background.HOVER);
        }
        graphics.drawString(font, "§7[Reset]", resetX + 5, y, resetHovered ? UIConstants.Text.PRIMARY : UIConstants.Text.MUTED, false);
    }

    private void drawPresets(GuiGraphics graphics, int x, int y) {
        graphics.drawString(font, "Presets:", x, y, UIConstants.Text.SECONDARY, false);

        int presetY = y + 12;
        int presetW = 55;
        int presetH = 16;
        int gap = 4;

        hoveredPreset = -1; // Reset hover state
        hoveredUserPreset = -1;

        // Built-in presets (2 rows of 3)
        for (int i = 0; i < PRESET_NAMES.length; i++) {
            int row = i / 3;
            int col = i % 3;
            int px = x + col * (presetW + gap);
            int py = presetY + row * (presetH + gap);

            boolean selected = (selectedPreset == i);
            boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, px, py, presetW, presetH);

            if (hovered) {
                hoveredPreset = i;
            }

            // Background
            int bgColor = selected ? PRESET_COLORS[i][0] : (hovered ? UIConstants.Background.HOVER : UIConstants.Background.INPUT);
            if (selected) {
                bgColor = UIConstants.setAlpha(bgColor, 180);
            }
            graphics.fill(px, py, px + presetW, py + presetH, bgColor);

            // Border
            int borderColor = selected ? PRESET_COLORS[i][1] : UIConstants.Border.MUTED;
            AxiomRenderer.drawBorder(graphics, px, py, presetW, presetH, borderColor);

            // Text
            String name = PRESET_NAMES[i];
            int textW = font.width(name);
            int textColor = selected ? UIConstants.Text.WHITE : UIConstants.Text.SECONDARY;
            graphics.drawString(font, name, px + (presetW - textW) / 2, py + 4, textColor, false);
        }

        // User presets section (below built-in)
        String[] userPresetNames = MobPresetManager.getPresetNames();
        if (userPresetNames.length > 0 || MobPresetManager.canAddPreset()) {
            int userY = presetY + 2 * (presetH + gap) + 6;

            // "My Presets" label with save button
            graphics.drawString(font, "§7My:", x, userY, UIConstants.Text.MUTED, false);

            // Save button (small +)
            int saveBtnX = x + 20;
            boolean saveHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, saveBtnX, userY - 1, 12, 10);
            if (MobPresetManager.canAddPreset()) {
                int saveColor = saveHovered ? UIConstants.Accent.GREEN : UIConstants.Text.MUTED;
                graphics.drawString(font, "[+]", saveBtnX, userY, saveColor, false);
            }

            // User preset buttons (compact, single row scrollable)
            int userPresetW = 45;
            int userPresetH = 14;
            int userStartX = x + 40;
            int maxVisible = 3;

            for (int i = 0; i < Math.min(userPresetNames.length, maxVisible); i++) {
                int px = userStartX + i * (userPresetW + 2);
                int py = userY - 2;

                boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, px, py, userPresetW, userPresetH);
                if (hovered) {
                    hoveredUserPreset = i;
                }

                // Background
                int bgColor = hovered ? UIConstants.Background.HOVER : UIConstants.Background.INPUT;
                graphics.fill(px, py, px + userPresetW, py + userPresetH, bgColor);

                // Border (gold for user presets)
                int borderColor = hovered ? UIConstants.Accent.GOLD : UIConstants.Border.MUTED;
                AxiomRenderer.drawBorder(graphics, px, py, userPresetW, userPresetH, borderColor);

                // Name (truncated)
                String name = userPresetNames[i];
                if (font.width(name) > userPresetW - 4) {
                    while (font.width(name + "..") > userPresetW - 4 && name.length() > 1) {
                        name = name.substring(0, name.length() - 1);
                    }
                    name = name + "..";
                }
                graphics.drawString(font, name, px + 2, py + 3, UIConstants.Text.SECONDARY, false);
            }

            // Show "..." if more presets exist
            if (userPresetNames.length > maxVisible) {
                int moreX = userStartX + maxVisible * (userPresetW + 2);
                graphics.drawString(font, "+" + (userPresetNames.length - maxVisible), moreX, userY, UIConstants.Text.MUTED, false);
            }
        }
    }

    private void drawStyledButton(GuiGraphics graphics, int x, int y, int w, int h, String text, boolean hovered, int accentColor) {
        // Background
        int bgColor = hovered ? UIConstants.setAlpha(accentColor, 60) : UIConstants.Background.PANEL;
        graphics.fill(x, y, x + w, y + h, bgColor);

        // Accent bar on left
        graphics.fill(x, y, x + 3, y + h, accentColor);

        // Border
        int borderColor = hovered ? accentColor : UIConstants.Border.MUTED;
        AxiomRenderer.drawBorder(graphics, x, y, w, h, borderColor);

        // Text
        int textW = font.width(text);
        int textX = x + (w - textW) / 2;
        int textY = y + (h - 8) / 2;
        graphics.drawString(font, text, textX, textY, hovered ? UIConstants.Text.WHITE : UIConstants.Text.PRIMARY, false);
    }

    /**
     * Draws a prominent warning banner when Global Mode is active.
     * This warns users that changes will affect ALL future spawns of this mob type.
     */
    private void drawGlobalModeWarning(GuiGraphics graphics, int x, int y) {
        int bannerWidth = PANEL_WIDTH;
        int bannerHeight = 24;

        // Warning colors - amber/orange theme
        int bgColor = 0xEE8B4513; // Dark orange/brown
        int borderColor = 0xFFFF8C00; // Dark orange
        int textColor = 0xFFFFD700; // Gold

        // Pulsing effect for attention
        long time = System.currentTimeMillis();
        float pulse = (float) (0.8 + 0.2 * Math.sin(time / 300.0));
        int pulseAlpha = (int) (255 * pulse);
        int pulseBorder = (pulseAlpha << 24) | (borderColor & 0x00FFFFFF);

        // Background
        graphics.fill(x, y, x + bannerWidth, y + bannerHeight, bgColor);

        // Pulsing border
        graphics.fill(x, y, x + bannerWidth, y + 1, pulseBorder); // Top
        graphics.fill(x, y + bannerHeight - 1, x + bannerWidth, y + bannerHeight, pulseBorder); // Bottom
        graphics.fill(x, y, x + 1, y + bannerHeight, pulseBorder); // Left
        graphics.fill(x + bannerWidth - 1, y, x + bannerWidth, y + bannerHeight, pulseBorder); // Right

        // Warning icon and text
        String warningText = "\u26A0 GLOBAL MODE: Changes affect ALL future spawns of this mob type!";
        int textW = font.width(warningText);
        int textX = x + (bannerWidth - textW) / 2;
        int textY = y + (bannerHeight - 8) / 2;

        graphics.drawString(font, warningText, textX, textY, textColor, true);
    }

    private void drawTooltips(GuiGraphics graphics) {
        // Don't show tooltips when dialog is open
        if (showingConfirmDialog) return;

        // Mode tooltip
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;
        int bottomY = panelY + PANEL_HEIGHT - 70;

        if (AxiomRenderer.isMouseOver(mouseX, mouseY, panelX + 15, bottomY, 140, 20)) {
            String tip = isGlobalMode ?
                "Changes apply to all future spawns of this mob type" :
                "Changes apply only to this specific mob";
            AxiomRenderer.drawTooltip(graphics, font, mouseX + 10, mouseY - 20, tip);
        }

        // Preset tooltip
        if (hoveredPreset >= 0 && hoveredPreset < PRESET_DESCRIPTIONS.length) {
            String presetTip = "§e" + PRESET_NAMES[hoveredPreset] + "§r: " + PRESET_DESCRIPTIONS[hoveredPreset];
            AxiomRenderer.drawTooltip(graphics, font, mouseX + 10, mouseY + 15, presetTip);
        }
    }

    // Confirmation dialog dimensions
    private static final int DIALOG_WIDTH = 260;
    private static final int DIALOG_HEIGHT = 100;

    /**
     * Draws a confirmation dialog asking user to save/discard changes.
     */
    private void drawConfirmDialog(GuiGraphics graphics) {
        // Dim background
        graphics.fill(0, 0, this.width, this.height, 0xA0000000);

        // Dialog box centered
        int dx = (this.width - DIALOG_WIDTH) / 2;
        int dy = (this.height - DIALOG_HEIGHT) / 2;

        // Dialog background
        graphics.fill(dx, dy, dx + DIALOG_WIDTH, dy + DIALOG_HEIGHT, UIConstants.Background.PANEL_SOLID);
        AxiomRenderer.drawBorder(graphics, dx, dy, DIALOG_WIDTH, DIALOG_HEIGHT, UIConstants.Accent.ORANGE);

        // Warning icon and title
        String title = "§e\u26A0 Unsaved Changes";
        int titleW = font.width(title);
        graphics.drawString(font, title, dx + (DIALOG_WIDTH - titleW) / 2, dy + 10, UIConstants.Text.PRIMARY, false);

        // Message
        String msg1 = "You have unsaved changes.";
        String msg2 = "Do you want to discard them?";
        graphics.drawString(font, msg1, dx + (DIALOG_WIDTH - font.width(msg1)) / 2, dy + 28, UIConstants.Text.SECONDARY, false);
        graphics.drawString(font, msg2, dx + (DIALOG_WIDTH - font.width(msg2)) / 2, dy + 40, UIConstants.Text.SECONDARY, false);

        // Buttons
        int btnW = 80;
        int btnH = 22;
        int btnGap = 20;
        int btnsX = dx + (DIALOG_WIDTH - (btnW * 2 + btnGap)) / 2;
        int btnsY = dy + DIALOG_HEIGHT - 35;

        // Discard button (left)
        boolean discardHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, btnsX, btnsY, btnW, btnH);
        drawStyledButton(graphics, btnsX, btnsY, btnW, btnH, "Discard", discardHovered, UIConstants.Accent.RED);

        // Cancel button (right)
        int cancelX = btnsX + btnW + btnGap;
        boolean cancelHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, cancelX, btnsY, btnW, btnH);
        drawStyledButton(graphics, cancelX, btnsY, btnW, btnH, "Cancel", cancelHovered, UIConstants.Accent.BLUE);
    }

    /**
     * Draws the save preset dialog with text input.
     */
    private void drawSavePresetDialog(GuiGraphics graphics) {
        // Dim background
        graphics.fill(0, 0, this.width, this.height, 0xA0000000);

        int dw = 200;
        int dh = 90;
        int dx = (this.width - dw) / 2;
        int dy = (this.height - dh) / 2;

        // Dialog background
        graphics.fill(dx, dy, dx + dw, dy + dh, UIConstants.Background.PANEL_SOLID);
        AxiomRenderer.drawBorder(graphics, dx, dy, dw, dh, UIConstants.Accent.GOLD);

        // Title
        String title = "Save Preset";
        graphics.drawString(font, title, dx + (dw - font.width(title)) / 2, dy + 8, UIConstants.Accent.GOLD, false);

        // Input box
        int inputX = dx + 10;
        int inputY = dy + 28;
        int inputW = dw - 20;
        int inputH = 16;

        graphics.fill(inputX, inputY, inputX + inputW, inputY + inputH, UIConstants.Background.INPUT);
        AxiomRenderer.drawBorder(graphics, inputX, inputY, inputW, inputH, UIConstants.Border.LIGHT);

        // Input text with cursor
        String inputText = presetNameInput.toString();
        graphics.drawString(font, inputText, inputX + 4, inputY + 4, UIConstants.Text.WHITE, false);

        // Blinking cursor
        if ((System.currentTimeMillis() / 500) % 2 == 0) {
            int cursorX = inputX + 4 + font.width(inputText);
            graphics.fill(cursorX, inputY + 3, cursorX + 1, inputY + 13, UIConstants.Text.WHITE);
        }

        // Hint text
        if (inputText.isEmpty()) {
            graphics.drawString(font, "§7Enter preset name...", inputX + 4, inputY + 4, UIConstants.Text.MUTED, false);
        }

        // Buttons
        int btnW = 60;
        int btnH = 18;
        int btnGap = 10;
        int btnsX = dx + (dw - (btnW * 2 + btnGap)) / 2;
        int btnsY = dy + dh - 28;

        boolean saveHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, btnsX, btnsY, btnW, btnH);
        boolean cancelHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, btnsX + btnW + btnGap, btnsY, btnW, btnH);

        drawStyledButton(graphics, btnsX, btnsY, btnW, btnH, "Save", saveHovered, UIConstants.Accent.GREEN);
        drawStyledButton(graphics, btnsX + btnW + btnGap, btnsY, btnW, btnH, "Cancel", cancelHovered, UIConstants.Accent.RED);

        // Remaining slots hint
        int remaining = 10 - MobPresetManager.getPresetCount();
        graphics.drawString(font, "§7" + remaining + " slots left", dx + dw - 50, dy + dh - 10, UIConstants.Text.MUTED, false);
    }

    /**
     * Draws delete confirmation dialog for user presets.
     */
    private void drawDeletePresetDialog(GuiGraphics graphics) {
        // Dim background
        graphics.fill(0, 0, this.width, this.height, 0xA0000000);

        int dw = 220;
        int dh = 80;
        int dx = (this.width - dw) / 2;
        int dy = (this.height - dh) / 2;

        // Dialog background
        graphics.fill(dx, dy, dx + dw, dy + dh, UIConstants.Background.PANEL_SOLID);
        AxiomRenderer.drawBorder(graphics, dx, dy, dw, dh, UIConstants.Accent.RED);

        // Title
        String title = "§cDelete Preset?";
        graphics.drawString(font, title, dx + (dw - font.width(title)) / 2, dy + 8, UIConstants.Accent.RED, false);

        // Message
        String msg = "Delete \"" + deleteConfirmPreset + "\"?";
        graphics.drawString(font, msg, dx + (dw - font.width(msg)) / 2, dy + 28, UIConstants.Text.SECONDARY, false);

        // Buttons
        int btnW = 60;
        int btnH = 18;
        int btnGap = 10;
        int btnsX = dx + (dw - (btnW * 2 + btnGap)) / 2;
        int btnsY = dy + dh - 28;

        boolean deleteHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, btnsX, btnsY, btnW, btnH);
        boolean cancelHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, btnsX + btnW + btnGap, btnsY, btnW, btnH);

        drawStyledButton(graphics, btnsX, btnsY, btnW, btnH, "Delete", deleteHovered, UIConstants.Accent.RED);
        drawStyledButton(graphics, btnsX + btnW + btnGap, btnsY, btnW, btnH, "Cancel", cancelHovered, UIConstants.Accent.BLUE);
    }

    private void updateAnimations() {
        long now = System.currentTimeMillis();
        float delta = (now - lastFrameTime) / 1000f;
        lastFrameTime = now;

        // Opening animation
        if (animationProgress < 1) {
            animationProgress = Math.min(1, animationProgress + delta * 3);
        }

        // Auto-rotate preview when not dragging
        if (!isDraggingPreview) {
            targetRotationY += delta * 20; // Slow rotation
            if (targetRotationY > 180) targetRotationY -= 360;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;

        // Handle save preset dialog
        if (showSavePresetDialog) {
            int dw = 200;
            int dh = 90;
            int dx = (this.width - dw) / 2;
            int dy = (this.height - dh) / 2;

            int btnW = 60;
            int btnH = 18;
            int btnGap = 10;
            int btnsX = dx + (dw - (btnW * 2 + btnGap)) / 2;
            int btnsY = dy + dh - 28;

            // Save button
            if (button == 0 && AxiomRenderer.isMouseOver(mx, my, btnsX, btnsY, btnW, btnH)) {
                if (presetNameInput.length() > 0) {
                    saveCurrentAsPreset(presetNameInput.toString());
                    showSavePresetDialog = false;
                    presetNameInput.setLength(0);
                }
                return true;
            }

            // Cancel button
            if (button == 0 && AxiomRenderer.isMouseOver(mx, my, btnsX + btnW + btnGap, btnsY, btnW, btnH)) {
                showSavePresetDialog = false;
                presetNameInput.setLength(0);
                return true;
            }

            return true; // Consume all clicks
        }

        // Handle delete preset dialog
        if (deleteConfirmPreset != null) {
            int dw = 220;
            int dh = 80;
            int dx = (this.width - dw) / 2;
            int dy = (this.height - dh) / 2;

            int btnW = 60;
            int btnH = 18;
            int btnGap = 10;
            int btnsX = dx + (dw - (btnW * 2 + btnGap)) / 2;
            int btnsY = dy + dh - 28;

            // Delete button
            if (button == 0 && AxiomRenderer.isMouseOver(mx, my, btnsX, btnsY, btnW, btnH)) {
                MobPresetManager.deletePreset(deleteConfirmPreset);
                if (minecraft != null) {
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                        SoundEvents.ITEM_PICKUP, 0.5f, 0.8f));
                }
                deleteConfirmPreset = null;
                return true;
            }

            // Cancel button
            if (button == 0 && AxiomRenderer.isMouseOver(mx, my, btnsX + btnW + btnGap, btnsY, btnW, btnH)) {
                deleteConfirmPreset = null;
                return true;
            }

            return true; // Consume all clicks
        }

        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        // Handle confirmation dialog clicks first
        if (showingConfirmDialog) {
            int dx = (this.width - DIALOG_WIDTH) / 2;
            int dy = (this.height - DIALOG_HEIGHT) / 2;
            int btnW = 80;
            int btnH = 22;
            int btnGap = 20;
            int btnsX = dx + (DIALOG_WIDTH - (btnW * 2 + btnGap)) / 2;
            int btnsY = dy + DIALOG_HEIGHT - 35;

            // Discard button
            if (AxiomRenderer.isMouseOver(mx, my, btnsX, btnsY, btnW, btnH)) {
                confirmedDiscard = true;
                showingConfirmDialog = false;
                this.onClose();
                return true;
            }

            // Cancel button
            int cancelX = btnsX + btnW + btnGap;
            if (AxiomRenderer.isMouseOver(mx, my, cancelX, btnsY, btnW, btnH)) {
                showingConfirmDialog = false;
                return true;
            }

            // Click outside dialog = cancel
            if (!AxiomRenderer.isMouseOver(mx, my, dx, dy, DIALOG_WIDTH, DIALOG_HEIGHT)) {
                showingConfirmDialog = false;
                return true;
            }

            return true; // Consume all clicks when dialog is open
        }

        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        // Tab clicks
        int tabsX = panelX + 10;
        int tabsY = panelY + UIConstants.Spacing.HEADER_HEIGHT;
        for (int i = 0; i < 3; i++) {
            int tx = tabsX + i * (TAB_WIDTH + 4);
            if (AxiomRenderer.isMouseOver(mx, my, tx, tabsY, TAB_WIDTH, TAB_HEIGHT)) {
                selectedTab = i;
                return true;
            }
        }

        // Mob preview drag
        int previewX = panelX + 15;
        int previewY = panelY + UIConstants.Spacing.HEADER_HEIGHT + TAB_HEIGHT + 8 + 10;
        if (AxiomRenderer.isMouseOver(mx, my, previewX, previewY, PREVIEW_SIZE, PREVIEW_SIZE + 20)) {
            isDraggingPreview = true;
            dragStartX = mx;
            dragStartY = my;
            dragStartRotX = rotationX;
            dragStartRotY = targetRotationY;
            return true;
        }

        // Slider interactions
        int slidersX = panelX + PREVIEW_SIZE + 40;
        int contentY = panelY + UIConstants.Spacing.HEADER_HEIGHT + TAB_HEIGHT + 8 + 5;
        int numSliders = selectedTab == 2 ? 1 : 3;

        for (int i = 0; i < numSliders; i++) {
            int rowY = contentY + i * (ROW_HEIGHT + 16);
            int sliderY = rowY + 12;
            int sliderId = selectedTab * 10 + i;

            // Check click on value text (to enter edit mode)
            int valueX = slidersX + SLIDER_WIDTH + 10;
            int valueW = 35;
            int valueH = 12;
            if (AxiomRenderer.isMouseOver(mx, my, valueX - 2, rowY - 2, valueW + 4, valueH + 2)) {
                // Start editing this slider's value
                if (editingSlider != sliderId) {
                    // Commit previous edit if any
                    commitNumericInput();
                    editingSlider = sliderId;
                    inputBuffer.setLength(0);
                    inputBuffer.append(String.format("%.1f", getSliderValue(sliderId)));
                    inputCursorBlink = System.currentTimeMillis();
                }
                return true;
            }

            // Check click on slider track
            if (AxiomRenderer.isMouseOver(mx, my, slidersX - 5, sliderY - 5, SLIDER_WIDTH + 10, SLIDER_HEIGHT + 10)) {
                // Cancel any active editing
                if (editingSlider >= 0) {
                    commitNumericInput();
                }
                activeSlider = sliderId;
                updateSliderValue(mx, slidersX);
                return true;
            }
        }

        // Click elsewhere cancels editing
        if (editingSlider >= 0) {
            commitNumericInput();
        }

        // Mode toggle
        int bottomY = panelY + PANEL_HEIGHT - 70;
        if (AxiomRenderer.isMouseOver(mx, my, panelX + 15, bottomY, 140, 20)) {
            isGlobalMode = !isGlobalMode;
            lastGlobalMode = isGlobalMode;
            return true;
        }

        // Preset buttons
        int presetX = panelX + 165;
        int presetY = bottomY - 5 + 12;
        int presetW = 55;
        int presetH = 16;
        int gap = 4;

        for (int i = 0; i < PRESET_NAMES.length; i++) {
            int row = i / 3;
            int col = i % 3;
            int px = presetX + col * (presetW + gap);
            int py = presetY + row * (presetH + gap);

            if (AxiomRenderer.isMouseOver(mx, my, px, py, presetW, presetH)) {
                applyPreset(i);
                return true;
            }
        }

        // User presets section
        String[] userPresetNames = MobPresetManager.getPresetNames();
        int userY = presetY + 2 * (presetH + gap) + 6;

        // Save button click (the [+] button)
        int saveBtnX = presetX + 20;
        if (MobPresetManager.canAddPreset() && AxiomRenderer.isMouseOver(mx, my, saveBtnX, userY - 1, 12, 10)) {
            showSavePresetDialog = true;
            presetNameInput.setLength(0);
            return true;
        }

        // User preset button clicks
        int userPresetW = 45;
        int userPresetH = 14;
        int userStartX = presetX + 40;
        int maxVisible = 3;

        for (int i = 0; i < Math.min(userPresetNames.length, maxVisible); i++) {
            int px = userStartX + i * (userPresetW + 2);
            int py = userY - 2;

            if (AxiomRenderer.isMouseOver(mx, my, px, py, userPresetW, userPresetH)) {
                if (button == 1) {
                    // Right-click to delete
                    deleteConfirmPreset = userPresetNames[i];
                } else {
                    // Left-click to apply
                    applyUserPreset(userPresetNames[i]);
                }
                return true;
            }
        }

        // Action buttons
        int btnW = 70;
        int btnH = 22;
        int btnGap = 8;
        int btnsX = panelX + PANEL_WIDTH - 160;

        // Apply
        if (AxiomRenderer.isMouseOver(mx, my, btnsX, bottomY + 20, btnW, btnH)) {
            save();
            return true;
        }

        // Equipment
        if (AxiomRenderer.isMouseOver(mx, my, btnsX + btnW + btnGap, bottomY + 20, btnW, btnH)) {
            Minecraft.getInstance().setScreen(new MobEquipmentScreen(mob, this));
            return true;
        }

        // Reset
        int resetX = btnsX + (btnW * 2 + btnGap - 50) / 2;
        if (AxiomRenderer.isMouseOver(mx, my, resetX, bottomY - 2, 50, 16)) {
            resetToOriginal();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingPreview && button == 0) {
            int mx = (int) mouseX;
            int my = (int) mouseY;
            // Improved sensitivity: 0.8 for Y rotation, 0.5 for X tilt
            targetRotationY = dragStartRotY + (mx - dragStartX) * 0.8f;
            rotationX = Mth.clamp(dragStartRotX + (my - dragStartY) * 0.5f, -45, 45);
            return true;
        }

        if (activeSlider >= 0 && button == 0) {
            int panelX = (this.width - PANEL_WIDTH) / 2;
            int slidersX = panelX + PREVIEW_SIZE + 40;
            updateSliderValue((int) mouseX, slidersX);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            isDraggingPreview = false;
            activeSlider = -1;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Scroll on preview area to zoom in/out
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;
        int previewX = panelX + 15;
        int previewY = panelY + 50;

        if (mouseX >= previewX && mouseX < previewX + PREVIEW_SIZE &&
            mouseY >= previewY && mouseY < previewY + PREVIEW_SIZE) {
            previewZoom = Mth.clamp(previewZoom + (float) scrollY * 0.1f, 0.5f, 2.0f);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void updateSliderValue(int mouseX, int slidersX) {
        float percent = (mouseX - slidersX) / (float) SLIDER_WIDTH;
        percent = Mth.clamp(percent, 0, 1);

        int localSlider = activeSlider % 10;

        switch (selectedTab) {
            case 0: // Stats
                switch (localSlider) {
                    case 0 -> health = percent * MAX_HEALTH;
                    case 1 -> armor = percent * MAX_ARMOR;
                    case 2 -> speed = percent * MAX_SPEED;
                }
                break;
            case 1: // Combat
                switch (localSlider) {
                    case 0 -> damage = percent * MAX_DAMAGE;
                    case 1 -> attackRange = percent * MAX_ATTACK_RANGE;
                    case 2 -> knockbackResist = percent * MAX_KNOCKBACK_RES;
                }
                break;
            case 2: // AI
                switch (localSlider) {
                    case 0, 1 -> followRange = percent * MAX_FOLLOW_RANGE;
                }
                break;
        }

        // Mark as custom preset
        selectedPreset = 5;
    }

    /**
     * Get the current value of a slider by its ID.
     */
    private double getSliderValue(int sliderId) {
        int tab = sliderId / 10;
        int local = sliderId % 10;
        return switch (tab) {
            case 0 -> switch (local) {
                case 0 -> health;
                case 1 -> armor;
                case 2 -> speed * 100; // Display as percentage
                default -> 0;
            };
            case 1 -> switch (local) {
                case 0 -> damage;
                case 1 -> attackRange;
                case 2 -> knockbackResist * 100; // Display as percentage
                default -> 0;
            };
            case 2 -> switch (local) {
                case 0 -> followRange;
                default -> 0;
            };
            default -> 0;
        };
    }

    /**
     * Set a slider value directly (from numeric input).
     */
    private void setSliderValue(int sliderId, double value) {
        int tab = sliderId / 10;
        int local = sliderId % 10;
        switch (tab) {
            case 0 -> {
                switch (local) {
                    case 0 -> health = Mth.clamp(value, 0, MAX_HEALTH);
                    case 1 -> armor = Mth.clamp(value, 0, MAX_ARMOR);
                    case 2 -> speed = Mth.clamp(value / 100, 0, MAX_SPEED); // Input is percentage
                }
            }
            case 1 -> {
                switch (local) {
                    case 0 -> damage = Mth.clamp(value, 0, MAX_DAMAGE);
                    case 1 -> attackRange = Mth.clamp(value, 0, MAX_ATTACK_RANGE);
                    case 2 -> knockbackResist = Mth.clamp(value / 100, 0, MAX_KNOCKBACK_RES); // Input is percentage
                }
            }
            case 2 -> {
                if (local == 0) followRange = Mth.clamp(value, 0, MAX_FOLLOW_RANGE);
            }
        }
        selectedPreset = 5; // Mark as custom
    }

    /**
     * Commit the current numeric input to the slider value.
     */
    private void commitNumericInput() {
        if (editingSlider < 0) return;

        String input = inputBuffer.toString().trim();
        if (!input.isEmpty()) {
            try {
                double value = Double.parseDouble(input);
                setSliderValue(editingSlider, value);
            } catch (NumberFormatException ignored) {
                // Invalid input - keep old value
            }
        }
        editingSlider = -1;
        inputBuffer.setLength(0);
    }

    /**
     * Apply a user-created preset.
     */
    private void applyUserPreset(String name) {
        MobPresetManager.MobPreset preset = MobPresetManager.getPreset(name);
        if (preset == null) return;

        health = preset.health();
        armor = preset.armor();
        damage = preset.damage();
        speed = preset.speed();
        followRange = preset.followRange();
        attackRange = preset.attackRange();
        knockbackResist = preset.knockbackResist();
        selectedPreset = 5; // Mark as "Custom"

        // Play sound
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.2f));
        }
    }

    /**
     * Save current settings as a user preset.
     */
    private void saveCurrentAsPreset(String name) {
        if (name == null || name.trim().isEmpty()) return;

        MobPresetManager.MobPreset preset = MobPresetManager.MobPreset.create(
            name.trim(),
            health,
            armor,
            damage,
            speed,
            followRange,
            attackRange,
            knockbackResist
        );

        if (MobPresetManager.savePreset(name.trim(), preset)) {
            // Play success sound
            if (minecraft != null) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f));
            }
        }
    }

    private void applyPreset(int preset) {
        selectedPreset = preset;

        switch (preset) {
            case 0: // Tank
                health = Math.max(origHealth, 80);
                armor = 20;
                damage = origDamage * 0.7;
                speed = origSpeed * 0.6;
                knockbackResist = 0.8;
                break;
            case 1: // Glass Cannon
                health = origHealth * 0.5;
                armor = 0;
                damage = origDamage * 2.5;
                speed = origSpeed * 1.3;
                knockbackResist = 0;
                break;
            case 2: // Speedy
                health = origHealth;
                armor = origArmor;
                damage = origDamage;
                speed = origSpeed * 2;
                followRange = 48;
                knockbackResist = 0;
                break;
            case 3: // Balanced
                health = origHealth;
                armor = origArmor;
                damage = origDamage;
                speed = origSpeed;
                followRange = origFollowRange;
                knockbackResist = origKnockbackResist;
                break;
            case 4: // Boss
                health = Math.max(origHealth * 3, 200);
                armor = 15;
                damage = origDamage * 2;
                speed = origSpeed * 0.8;
                knockbackResist = 1.0;
                followRange = 48;
                attackRange = origAttackRange * 1.5;
                break;
            case 5: // Custom - do nothing
                break;
        }

        // Play sound feedback for preset selection
        playPresetSound(preset);
    }

    /**
     * Play appropriate sound feedback when a preset is selected.
     * Each preset has a distinct sound to help users identify their selection.
     */
    private void playPresetSound(int preset) {
        if (minecraft == null) return;

        switch (preset) {
            case 0: // Tank - heavy anvil sound
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    SoundEvents.ANVIL_PLACE, 0.6f, 0.5f));
                break;
            case 1: // Glass Cannon - sharp attack sound
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 1.2f));
                break;
            case 2: // Speedy - swoosh/wind sound
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    SoundEvents.ENDER_DRAGON_FLAP, 0.5f, 1.5f));
                break;
            case 3: // Balanced - neutral click
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f));
                break;
            case 4: // Boss - dramatic boss sound
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    SoundEvents.TOTEM_USE, 0.4f, 0.7f));
                break;
            case 5: // Custom - subtle sound
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, 1.2f));
                break;
        }
    }

    private void resetToOriginal() {
        health = origHealth;
        armor = origArmor;
        damage = origDamage;
        followRange = origFollowRange;
        attackRange = origAttackRange;
        speed = origSpeed;
        knockbackResist = origKnockbackResist;
        selectedPreset = 5;
    }

    private void save() {
        PacketDistributor.sendToServer(new UpdateMobStatsPayload(
            isGlobalMode, mob.getId(), followRange, damage, health, armor, attackRange));

        if (minecraft != null && minecraft.player != null) {
            // Show pending message - actual result will be shown by ClientConfigFeedback
            minecraft.player.displayClientMessage(
                I18n.translate("devmod.mobconfig.applying"),
                true
            );
        }

        // Close screen - feedback will be shown via ClientConfigFeedback
        // The server confirmation packet will display success/failure via action bar
        this.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Checks if there are unsaved changes compared to original values.
     */
    private boolean hasUnsavedChanges() {
        final double EPSILON = 0.01;
        return Math.abs(health - origHealth) > EPSILON ||
               Math.abs(armor - origArmor) > EPSILON ||
               Math.abs(damage - origDamage) > EPSILON ||
               Math.abs(followRange - origFollowRange) > EPSILON ||
               Math.abs(attackRange - origAttackRange) > EPSILON ||
               Math.abs(speed - origSpeed) > EPSILON ||
               Math.abs(knockbackResist - origKnockbackResist) > EPSILON;
    }

    // Track if user confirmed discard
    private boolean confirmedDiscard = false;
    // Track if confirmation dialog is showing
    private boolean showingConfirmDialog = false;

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Handle save preset dialog
        if (showSavePresetDialog) {
            if (keyCode == 256) { // ESC
                showSavePresetDialog = false;
                presetNameInput.setLength(0);
                return true;
            }
            if (keyCode == 257 || keyCode == 335) { // Enter
                if (presetNameInput.length() > 0) {
                    saveCurrentAsPreset(presetNameInput.toString());
                    showSavePresetDialog = false;
                    presetNameInput.setLength(0);
                }
                return true;
            }
            if (keyCode == 259 && presetNameInput.length() > 0) { // Backspace
                presetNameInput.deleteCharAt(presetNameInput.length() - 1);
                return true;
            }
            return true; // Consume all keys
        }

        // Handle delete preset dialog
        if (deleteConfirmPreset != null) {
            if (keyCode == 256) { // ESC
                deleteConfirmPreset = null;
                return true;
            }
            return true; // Consume all keys
        }

        // Handle numeric input mode
        if (editingSlider >= 0) {
            // Enter key - commit value
            if (keyCode == 257 || keyCode == 335) { // Enter or NumPad Enter
                commitNumericInput();
                return true;
            }
            // Escape key - cancel editing
            if (keyCode == 256) {
                editingSlider = -1;
                inputBuffer.setLength(0);
                return true;
            }
            // Backspace
            if (keyCode == 259 && inputBuffer.length() > 0) {
                inputBuffer.deleteCharAt(inputBuffer.length() - 1);
                return true;
            }
            // Tab key - move to next slider
            if (keyCode == 258) {
                commitNumericInput();
                // Move to next slider in current tab
                int numSliders = selectedTab == 2 ? 1 : 3;
                int localSlider = editingSlider % 10;
                int nextLocal = (localSlider + 1) % numSliders;
                editingSlider = selectedTab * 10 + nextLocal;
                inputBuffer.setLength(0);
                inputBuffer.append(String.format("%.1f", getSliderValue(editingSlider)));
                inputCursorBlink = System.currentTimeMillis();
                return true;
            }
            return true; // Consume all keys while editing
        }

        // ESC key (normal mode)
        if (keyCode == 256) {
            if (showingConfirmDialog) {
                // ESC during dialog = cancel (stay in screen)
                showingConfirmDialog = false;
                return true;
            }
            if (hasUnsavedChanges() && !confirmedDiscard) {
                showingConfirmDialog = true;
                return true; // Consume the key, don't close
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        // Handle preset name input
        if (showSavePresetDialog) {
            // Allow alphanumeric, space, and common symbols
            if (Character.isLetterOrDigit(codePoint) || codePoint == ' ' || codePoint == '_' || codePoint == '-') {
                if (presetNameInput.length() < 16) { // Limit name length
                    presetNameInput.append(codePoint);
                }
                return true;
            }
            return true; // Consume invalid characters
        }

        // Handle numeric input for sliders
        if (editingSlider >= 0) {
            // Allow digits, decimal point, and minus sign
            if (Character.isDigit(codePoint) || codePoint == '.' || codePoint == '-') {
                // Limit input length
                if (inputBuffer.length() < 8) {
                    // Only allow one decimal point
                    if (codePoint == '.' && inputBuffer.toString().contains(".")) {
                        return true;
                    }
                    // Only allow minus at start
                    if (codePoint == '-' && inputBuffer.length() > 0) {
                        return true;
                    }
                    inputBuffer.append(codePoint);
                }
                return true;
            }
            return true; // Consume invalid characters
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            mc.options.menuBackgroundBlurriness().set(originalBlurValue);
        }
        super.onClose();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // Disabled
    }

    @Override
    protected void renderMenuBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, 0xC0101010);
    }
}
