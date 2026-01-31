package com.devmod.client.ui.screens;

import java.util.Objects;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.Mob;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.config.MobPresetManager;

public class MobConfigScreenRenderer {

    // Layout constants
    public static final int PANEL_WIDTH = 450;
    public static final int PANEL_HEIGHT = 410;
    public static final int PREVIEW_SIZE = 120;
    public static final int SLIDER_WIDTH = 180;
    public static final int SLIDER_HEIGHT = 12;
    public static final int ROW_HEIGHT = 26;
    public static final int TAB_WIDTH = 80;
    public static final int TAB_HEIGHT = 24;
    public static final int DIALOG_WIDTH = 260;
    public static final int DIALOG_HEIGHT = 100;

    private final MobConfigScreenState state;
    private final Font font;

    public MobConfigScreenRenderer(MobConfigScreenState state, Font font) {
        this.state = state;
        this.font = font;
    }

    // ═══════════════════════════════════════════════════════════════
    // MAIN PANEL
    // ═══════════════════════════════════════════════════════════════

    public void drawMainPanel(GuiGraphics graphics, int panelX, int panelY) {
        Mob mob = state.getMob();

        // Panel background with subtle gradient
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, DesignTokens.Background.PANEL_SOLID());

        // Gradient header
        int headerH = DesignTokens.Spacing.HEADER_HEIGHT;
        for (int i = 0; i < headerH; i++) {
            float t = (float) i / headerH;
            int color = DesignTokens.lerp(DesignTokens.MobConfig.HEADER_GRADIENT_START, DesignTokens.Background.HEADER(), t);
            graphics.fill(panelX, panelY + i, panelX + PANEL_WIDTH, panelY + i + 1, color);
        }

        // Border with glow effect
        int glowColor = DesignTokens.withAlpha(DesignTokens.Border.DEFAULT(), 80);
        graphics.fill(panelX - 2, panelY - 2, panelX + PANEL_WIDTH + 2, panelY, glowColor);
        graphics.fill(panelX - 2, panelY + PANEL_HEIGHT, panelX + PANEL_WIDTH + 2, panelY + PANEL_HEIGHT + 2, glowColor);
        graphics.fill(panelX - 2, panelY, panelX, panelY + PANEL_HEIGHT, glowColor);
        graphics.fill(panelX + PANEL_WIDTH, panelY, panelX + PANEL_WIDTH + 2, panelY + PANEL_HEIGHT, glowColor);

        AxiomRenderer.drawBorder(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, DesignTokens.Border.DEFAULT());

        // Title with mob name
        String title = Objects.requireNonNull("\u00A7l" + mob.getName().getString());
        var safeFont = Objects.requireNonNull(font);
        UIScaleManager.drawScaledString(graphics, safeFont, title, panelX + 12, panelY + 6, DesignTokens.Text.PRIMARY(), false);

        // Mob type (smaller, muted)
        String type = mob.getType().getDescriptionId().replace("entity.minecraft.", "").replace("_", " ");
        type = Objects.requireNonNull(Character.toUpperCase(type.charAt(0)) + type.substring(1));
        int titleWidth = UIScaleManager.getScaledStringWidth(safeFont, title);
        UIScaleManager.drawScaledString(graphics, safeFont, "(" + type + ")", panelX + 14 + titleWidth, panelY + 6, DesignTokens.Text.MUTED(), false);

        // Health indicator in header
        float healthPercent = mob.getHealth() / mob.getMaxHealth();
        int healthBarX = panelX + PANEL_WIDTH - 80;
        int healthBarY = panelY + 5;
        drawMiniHealthBar(graphics, healthBarX, healthBarY, 70, 10, healthPercent);
    }

    private void drawMiniHealthBar(GuiGraphics graphics, int x, int y, int w, int h, float percent) {
        graphics.fill(x, y, x + w, y + h, DesignTokens.Background.INPUT());
        int fillW = (int) (w * percent);
        int color = percent > 0.5f ? DesignTokens.Accent.GREEN() :
                    (percent > 0.25f ? DesignTokens.Accent.YELLOW() : DesignTokens.Accent.RED());
        if (fillW > 0) {
            graphics.fill(x, y, x + fillW, y + h, color);
        }
        AxiomRenderer.drawBorder(graphics, x, y, w, h, DesignTokens.Border.MUTED());
        UIScaleManager.drawScaledString(graphics, Objects.requireNonNull(font), "\u00A7c❤", x + 2, y + 1, DesignTokens.Text.WHITE, false);
    }

    // ═══════════════════════════════════════════════════════════════
    // TABS
    // ═══════════════════════════════════════════════════════════════

    public void drawTabs(GuiGraphics graphics, int panelX, int panelY, int mouseX, int mouseY) {
        String[] tabs = {"Stats", "Combat", "AI"};
        int tabsX = panelX + 10;
        int tabsY = panelY + DesignTokens.Spacing.HEADER_HEIGHT;

        for (int i = 0; i < tabs.length; i++) {
            int tx = tabsX + i * (TAB_WIDTH + 4);
            boolean selected = (state.selectedTab == i);
            boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, tx, tabsY, TAB_WIDTH, TAB_HEIGHT);

            int bgColor = selected ? DesignTokens.Background.ACTIVE() :
                          (hovered ? DesignTokens.Background.HOVER() : DesignTokens.Background.HEADER());
            graphics.fill(tx, tabsY, tx + TAB_WIDTH, tabsY + TAB_HEIGHT, bgColor);

            if (selected) {
                graphics.fill(tx, tabsY + TAB_HEIGHT - 2, tx + TAB_WIDTH, tabsY + TAB_HEIGHT, DesignTokens.Border.ACCENT());
            }

            int textColor = selected ? DesignTokens.Text.ACCENT() : DesignTokens.Text.PRIMARY();
            var safeFont = Objects.requireNonNull(font);
            String tabName = Objects.requireNonNull(tabs[i]);
            int textW = UIScaleManager.getScaledStringWidth(safeFont, tabName);
            UIScaleManager.drawScaledString(graphics, safeFont, tabName, tx + (TAB_WIDTH - textW) / 2, tabsY + 7, textColor, false);
        }

        AxiomRenderer.drawSeparator(graphics, panelX + 10, tabsY + TAB_HEIGHT + 4, PANEL_WIDTH - 20);
    }

    // ═══════════════════════════════════════════════════════════════
    // MOB PREVIEW
    // ═══════════════════════════════════════════════════════════════

    public void drawMobPreview(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        Mob mob = state.getMob();
        int boxW = PREVIEW_SIZE;
        int boxH = PREVIEW_SIZE + 20;

        // Background gradient
        for (int i = 0; i < boxH; i++) {
            float t = (float) i / boxH;
            int color = DesignTokens.lerp(DesignTokens.MobConfig.SECTION_GRADIENT_START, DesignTokens.MobConfig.SECTION_GRADIENT_END, t);
            graphics.fill(x, y + i, x + boxW, y + i + 1, color);
        }

        AxiomRenderer.drawBorder(graphics, x, y, boxW, boxH, DesignTokens.Border.MUTED());

        // Corner accents
        int accentLen = 10;
        int accentColor = DesignTokens.Border.ACCENT();
        graphics.fill(x, y, x + accentLen, y + 2, accentColor);
        graphics.fill(x, y, x + 2, y + accentLen, accentColor);
        graphics.fill(x + boxW - accentLen, y, x + boxW, y + 2, accentColor);
        graphics.fill(x + boxW - 2, y, x + boxW, y + accentLen, accentColor);
        graphics.fill(x, y + boxH - 2, x + accentLen, y + boxH, accentColor);
        graphics.fill(x, y + boxH - accentLen, x + 2, y + boxH, accentColor);
        graphics.fill(x + boxW - accentLen, y + boxH - 2, x + boxW, y + boxH, accentColor);
        graphics.fill(x + boxW - 2, y + boxH - accentLen, x + boxW, y + boxH, accentColor);

        int centerX = x + boxW / 2;
        int centerY = y + boxH - 20;

        float mobHeight = mob.getBbHeight();
        float mobWidth = mob.getBbWidth();
        float maxDim = Math.max(mobHeight, mobWidth);
        int scale = (int) (Math.min(50, 100 / maxDim) * state.previewZoom);

        Quaternionf rotation = new Quaternionf()
            .rotateY((float) Math.toRadians(state.rotationY))
            .rotateX((float) Math.toRadians(state.rotationX))
            .rotateZ((float) Math.PI);

        var safeFont = Objects.requireNonNull(font);
        try {
            InventoryScreen.renderEntityInInventory(
                graphics, centerX, centerY, scale,
                new Vector3f(0, 0, 0), Objects.requireNonNull(rotation, "rotation"), null, mob
            );
        } catch (Exception e) {
            UIScaleManager.drawScaledString(graphics, safeFont, "Preview", centerX - 20, centerY - 40, DesignTokens.Text.MUTED(), false);
        }

        boolean hovering = AxiomRenderer.isMouseOver(mouseX, mouseY, x, y, boxW, boxH);
        if (hovering && !state.isDraggingPreview) {
            UIScaleManager.drawScaledString(graphics, safeFont, "\u00A78Drag: rotate | Scroll: zoom", x + 4, y + boxH - 12, DesignTokens.Text.MUTED(), false);
        } else if (state.isDraggingPreview) {
            UIScaleManager.drawScaledString(graphics, safeFont, "\u00A7a↻ Rotating...", x + 4, y + boxH - 12, DesignTokens.Accent.GREEN(), false);
        }

        if (Math.abs(state.previewZoom - 1.0f) > 0.05f) {
            String zoomText = Objects.requireNonNull(String.format("%.0f%%", state.previewZoom * 100));
            int zoomWidth = UIScaleManager.getScaledStringWidth(safeFont, zoomText);
            UIScaleManager.drawScaledString(graphics, safeFont, zoomText, x + boxW - zoomWidth - 4, y + 4, DesignTokens.Text.MUTED(), false);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SLIDERS
    // ═══════════════════════════════════════════════════════════════

    public void drawSliders(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        String[] labels;
        double[] values;
        double[] originals;
        double[] maxValues;
        int[] colors;

        switch (state.selectedTab) {
            case 0 -> { // Stats
                labels = new String[]{"Max Health", "Armor", "Speed"};
                values = new double[]{state.health, state.armor, state.speed * 100};
                originals = new double[]{state.getOrigHealth(), state.getOrigArmor(), state.getOrigSpeed() * 100};
                maxValues = new double[]{MobConfigScreenState.MAX_HEALTH, MobConfigScreenState.MAX_ARMOR, MobConfigScreenState.MAX_SPEED * 100};
                colors = new int[]{DesignTokens.Accent.RED(), DesignTokens.Accent.CYAN(), DesignTokens.Accent.GREEN()};
            }
            case 1 -> { // Combat
                labels = new String[]{"Attack Damage", "Attack Reach", "Knockback Resist"};
                values = new double[]{state.damage, state.attackRange, state.knockbackResist * 100};
                originals = new double[]{state.getOrigDamage(), state.getOrigAttackRange(), state.getOrigKnockbackResist() * 100};
                maxValues = new double[]{MobConfigScreenState.MAX_DAMAGE, MobConfigScreenState.MAX_ATTACK_RANGE, MobConfigScreenState.MAX_KNOCKBACK_RES * 100};
                colors = new int[]{DesignTokens.Accent.ORANGE(), DesignTokens.Accent.YELLOW(), DesignTokens.Accent.PURPLE()};
            }
            case 2 -> { // AI
                labels = new String[]{"Follow Range"};
                values = new double[]{state.followRange};
                originals = new double[]{state.getOrigFollowRange()};
                maxValues = new double[]{MobConfigScreenState.MAX_FOLLOW_RANGE};
                colors = new int[]{DesignTokens.Accent.BLUE()};
            }
            default -> { return; }
        }

        for (int i = 0; i < labels.length; i++) {
            int rowY = y + i * (ROW_HEIGHT + 16);
            drawSliderRow(graphics, x, rowY, labels[i], values[i], originals[i], maxValues[i], colors[i],
                          state.selectedTab * 10 + i, mouseX, mouseY);
        }
    }

    private void drawSliderRow(GuiGraphics graphics, int x, int y, String label, double value,
                               double original, double maxVal, int accentColor, int sliderId, int mouseX, int mouseY) {
        var safeFont = Objects.requireNonNull(font);
        UIScaleManager.drawScaledString(graphics, safeFont, label, x, y, DesignTokens.Text.PRIMARY(), false);

        int valueX = x + SLIDER_WIDTH + 10;
        int valueW = 35;
        int valueH = 12;

        if (state.editingSlider == sliderId) {
            graphics.fill(valueX - 2, y - 2, valueX + valueW + 2, y + valueH, DesignTokens.Background.INPUT());
            AxiomRenderer.drawBorder(graphics, valueX - 2, y - 2, valueW + 4, valueH + 2, DesignTokens.Accent.GOLD());

            String inputText = state.inputBuffer.toString();
            if (inputText == null) inputText = "";
            UIScaleManager.drawScaledString(graphics, safeFont, inputText, valueX, y, DesignTokens.Text.WHITE(), false);

            if ((System.currentTimeMillis() - state.inputCursorBlink) % 1000 < 500) {
                int cursorX = valueX + safeFont.width(inputText);
                graphics.fill(cursorX, y, cursorX + 1, y + 9, DesignTokens.Text.WHITE());
            }
        } else {
            String valueStr = String.format("%.1f", value);
            boolean valueHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, valueX - 2, y - 2, valueW + 4, valueH + 2);

            if (valueHovered) {
                graphics.fill(valueX - 2, y - 2, valueX + valueW + 2, y + valueH, DesignTokens.Background.HOVER());
                AxiomRenderer.drawBorder(graphics, valueX - 2, y - 2, valueW + 4, valueH + 2, DesignTokens.Border.LIGHT());
            }

            int valueColor = Math.abs(value - original) > 0.01 ? DesignTokens.Accent.GOLD() : DesignTokens.Text.SECONDARY();
            UIScaleManager.drawScaledString(graphics, safeFont, valueStr, valueX, y, valueColor, false);
        }

        if (Math.abs(value - original) > 0.01) {
            double change = value - original;
            String changeStr = Objects.requireNonNull(String.format("%+.1f", change));
            int changeColor = change > 0 ? DesignTokens.Accent.GREEN() : DesignTokens.Accent.RED();
            UIScaleManager.drawScaledString(graphics, safeFont, changeStr, x + SLIDER_WIDTH + 50, y, changeColor, false);
        }

        int sliderY = y + 12;
        int trackX = x;
        int trackW = SLIDER_WIDTH;
        int trackH = SLIDER_HEIGHT;

        graphics.fill(trackX, sliderY, trackX + trackW, sliderY + trackH, DesignTokens.Background.INPUT());

        float origPercent = maxVal > 0 ? (float) (original / maxVal) : 0f;
        int origMarkerX = trackX + (int) (trackW * Math.min(1, origPercent));
        graphics.fill(origMarkerX - 1, sliderY, origMarkerX + 1, sliderY + trackH, DesignTokens.MobConfig.MARKER);

        float percent = maxVal > 0 ? (float) (value / maxVal) : 0f;
        int fillW = (int) (trackW * Math.min(1, percent));
        if (fillW > 0 && trackW > 0) {
            for (int i = 0; i < fillW; i++) {
                float t = (float) i / trackW;
                int color = DesignTokens.lerp(DesignTokens.darken(accentColor, 0.3f), accentColor, t);
                graphics.fill(trackX + i, sliderY, trackX + i + 1, sliderY + trackH, color);
            }
        }

        int handleX = trackX + fillW - 3;
        int handleW = 6;
        boolean sliderHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, trackX - 5, sliderY - 5, trackW + 10, trackH + 10);

        int localSlider = sliderId % 10;
        float pulseAnim = localSlider < state.sliderAnimations.length ? state.sliderAnimations[localSlider] : 0;
        int pulseExpand = (int) (pulseAnim * 4);

        if (sliderHovered || state.activeSlider == sliderId || pulseAnim > 0) {
            int glowAlpha = 80 + (int) (pulseAnim * 100);
            graphics.fill(handleX - 2 - pulseExpand, sliderY - 2 - pulseExpand,
                handleX + handleW + 2 + pulseExpand, sliderY + trackH + 2 + pulseExpand,
                DesignTokens.withAlpha(accentColor, glowAlpha));
        }

        graphics.fill(handleX, sliderY - 1, handleX + handleW, sliderY + trackH + 1, accentColor);
        graphics.fill(handleX + 1, sliderY, handleX + handleW - 1, sliderY + trackH, DesignTokens.lighten(accentColor, 0.3f));

        AxiomRenderer.drawBorder(graphics, trackX, sliderY, trackW, trackH, DesignTokens.Border.MUTED());
    }

    // ═══════════════════════════════════════════════════════════════
    // BOTTOM SECTION
    // ═══════════════════════════════════════════════════════════════

    public void drawBottomSection(GuiGraphics graphics, int panelX, int y, int mouseX, int mouseY) {
        graphics.fill(panelX + 10, y - 8, panelX + PANEL_WIDTH - 10, y - 7, DesignTokens.Border.SEPARATOR());

        int modeX = panelX + 15;
        boolean modeHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, modeX, y, 120, 22);

        int modeBg = modeHovered ? DesignTokens.Background.HOVER() : DesignTokens.Background.INPUT();
        graphics.fill(modeX, y, modeX + 120, y + 22, modeBg);
        AxiomRenderer.drawBorder(graphics, modeX, y, 120, 22, DesignTokens.Border.MUTED());

        int modeAccent = state.isGlobalMode ? DesignTokens.Accent.ORANGE() : DesignTokens.Accent.BLUE();
        graphics.fill(modeX, y, modeX + 3, y + 22, modeAccent);

        var safeFont = Objects.requireNonNull(font);
        UIScaleManager.drawScaledString(graphics, safeFont, "Mode:", modeX + 8, y + 3, DesignTokens.Text.SECONDARY(), false);
        String modeText = state.isGlobalMode ? "GLOBAL" : "SPECIFIC";
        int modeColor = state.isGlobalMode ? DesignTokens.Accent.ORANGE() : DesignTokens.Text.PRIMARY();
        UIScaleManager.drawScaledString(graphics, safeFont, modeText, modeX + 8, y + 12, modeColor, false);

        drawPresets(graphics, panelX + 150, y - 3, mouseX, mouseY);

        int btnY = y + 56;
        int btnW = 70;
        int btnH = 22;
        int btnGap = 8;

        int resetX = panelX + 15;
        boolean resetEnabled = state.hasStatChanges();
        boolean resetHovered = resetEnabled && AxiomRenderer.isMouseOver(mouseX, mouseY, resetX, btnY, 55, btnH);
        drawStyledButton(graphics, resetX, btnY, 55, btnH, "Reset", resetHovered, DesignTokens.Accent.RED(), resetEnabled);

        int applyX = panelX + PANEL_WIDTH - btnW - btnGap - btnW - 15;
        boolean applyEnabled = state.hasUnsavedChanges();
        boolean applyHovered = applyEnabled && AxiomRenderer.isMouseOver(mouseX, mouseY, applyX, btnY, btnW, btnH);
        drawStyledButton(graphics, applyX, btnY, btnW, btnH, "Apply", applyHovered, DesignTokens.Accent.GREEN(), applyEnabled);

        int equipX = applyX + btnW + btnGap;
        boolean equipHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, equipX, btnY, btnW, btnH);
        drawStyledButton(graphics, equipX, btnY, btnW, btnH, "Equip...", equipHovered, DesignTokens.Accent.BLUE(), true);
    }

    private void drawPresets(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        var safeFont = Objects.requireNonNull(font);
        UIScaleManager.drawScaledString(graphics, safeFont, "Presets:", x, y, DesignTokens.Text.SECONDARY(), false);

        int presetY = y + 12;
        int presetW = 55;
        int presetH = 16;
        int gap = 4;

        state.hoveredPreset = -1;
        state.hoveredUserPreset = -1;

        for (int i = 0; i < MobConfigScreenState.PRESET_NAMES.size(); i++) {
            int row = i / 3;
            int col = i % 3;
            int px = x + col * (presetW + gap);
            int py = presetY + row * (presetH + gap);

            boolean selected = (state.selectedPreset == i);
            boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, px, py, presetW, presetH);

            if (hovered) {
                state.hoveredPreset = i;
            }

            MobConfigScreenState.PresetColors colors = MobConfigScreenState.PRESET_COLORS.get(i);
            int bgColor = selected ? colors.background() : (hovered ? DesignTokens.Background.HOVER() : DesignTokens.Background.INPUT());
            if (selected) {
                bgColor = DesignTokens.withAlpha(bgColor, 180);
            }
            graphics.fill(px, py, px + presetW, py + presetH, bgColor);

            int borderColor = selected ? colors.border() : DesignTokens.Border.MUTED();
            AxiomRenderer.drawBorder(graphics, px, py, presetW, presetH, borderColor);

            String name = Objects.requireNonNull(MobConfigScreenState.PRESET_NAMES.get(i), "preset name");
            int textW = UIScaleManager.getScaledStringWidth(safeFont, name);
            int textColor = selected ? DesignTokens.Text.WHITE() : DesignTokens.Text.SECONDARY();
            UIScaleManager.drawScaledString(graphics, safeFont, name, px + (presetW - textW) / 2, py + 4, textColor, false);
        }

        // User presets
        String[] userPresetNames = MobPresetManager.getPresetNames();
        if (userPresetNames.length > 0 || MobPresetManager.canAddPreset()) {
            int userY = presetY + 2 * (presetH + gap) + 6;

            UIScaleManager.drawScaledString(graphics, safeFont, "\u00A77My:", x, userY, DesignTokens.Text.MUTED(), false);

            int saveBtnX = x + 20;
            boolean saveHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, saveBtnX, userY - 1, 12, 10);
            if (MobPresetManager.canAddPreset()) {
                int saveColor = saveHovered ? DesignTokens.Accent.GREEN() : DesignTokens.Text.MUTED();
                UIScaleManager.drawScaledString(graphics, safeFont, "[+]", saveBtnX, userY, saveColor, false);
            }

            int userPresetW = 45;
            int userPresetH = 14;
            int userStartX = x + 40;
            int maxVisible = 3;

            for (int i = 0; i < Math.min(userPresetNames.length, maxVisible); i++) {
                int px = userStartX + i * (userPresetW + 2);
                int py = userY - 2;

                boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, px, py, userPresetW, userPresetH);
                if (hovered) {
                    state.hoveredUserPreset = i;
                }

                int bgColor = hovered ? DesignTokens.Background.HOVER() : DesignTokens.Background.INPUT();
                graphics.fill(px, py, px + userPresetW, py + userPresetH, bgColor);

                int borderColor = hovered ? DesignTokens.Accent.GOLD() : DesignTokens.Border.MUTED();
                AxiomRenderer.drawBorder(graphics, px, py, userPresetW, userPresetH, borderColor);

                String name = userPresetNames[i];
                if (name == null) name = "";
                if (UIScaleManager.getScaledStringWidth(safeFont, name) > userPresetW - 4) {
                    String ellipsis = "..";
                    int minChars = Math.min(3, name.length());
                    while (UIScaleManager.getScaledStringWidth(safeFont, name + ellipsis) > userPresetW - 4 && name.length() > minChars) {
                        name = name.substring(0, name.length() - 1);
                    }
                    name = name + ellipsis;
                }
                UIScaleManager.drawScaledString(graphics, safeFont, name, px + 2, py + 3, DesignTokens.Text.SECONDARY(), false);
            }

            if (userPresetNames.length > maxVisible) {
                int moreX = userStartX + maxVisible * (userPresetW + 2);
                UIScaleManager.drawScaledString(graphics, safeFont, "+" + (userPresetNames.length - maxVisible), moreX, userY, DesignTokens.Text.MUTED(), false);
            }
        }
    }

    private void drawStyledButton(GuiGraphics graphics, int x, int y, int w, int h, String text,
                                  boolean hovered, int accentColor, boolean enabled) {
        int bgColor;
        int accent = accentColor;
        int borderColor;
        int textColor;

        if (!enabled) {
            bgColor = DesignTokens.Background.INPUT();
            accent = DesignTokens.Border.MUTED();
            borderColor = DesignTokens.Border.MUTED();
            textColor = DesignTokens.Text.MUTED();
        } else {
            bgColor = hovered ? DesignTokens.withAlpha(accentColor, 60) : DesignTokens.Background.PANEL();
            borderColor = hovered ? accentColor : DesignTokens.Border.MUTED();
            textColor = hovered ? DesignTokens.Text.WHITE() : DesignTokens.Text.PRIMARY();
        }
        graphics.fill(x, y, x + w, y + h, bgColor);

        graphics.fill(x, y, x + 3, y + h, accent);

        AxiomRenderer.drawBorder(graphics, x, y, w, h, borderColor);

        var safeFont = Objects.requireNonNull(font);
        String safeText = text;
        if (safeText == null) safeText = "";
        int textW = UIScaleManager.getScaledStringWidth(safeFont, safeText);
        int textX = x + (w - textW) / 2;
        int textY = y + (h - 8) / 2;
        UIScaleManager.drawScaledString(graphics, safeFont, safeText, textX, textY, textColor, false);
    }

    // ═══════════════════════════════════════════════════════════════
    // GLOBAL MODE WARNING
    // ═══════════════════════════════════════════════════════════════

    public void drawGlobalModeWarning(GuiGraphics graphics, int x, int y) {
        int bannerWidth = PANEL_WIDTH;
        int bannerHeight = 24;

        int bgColor = DesignTokens.withAlpha(DesignTokens.Accent.ORANGE(), DesignTokens.Alpha.A93);
        int borderColor = DesignTokens.Accent.ORANGE();
        int textColor = DesignTokens.Accent.GOLD();

        long time = System.currentTimeMillis();
        float pulse = (float) (0.8 + 0.2 * Math.sin(time / 300.0));
        int pulseAlpha = (int) (255 * pulse);
        int pulseBorder = (pulseAlpha << 24) | (borderColor & DesignTokens.Mask.RGB);

        graphics.fill(x, y, x + bannerWidth, y + bannerHeight, bgColor);

        graphics.fill(x, y, x + bannerWidth, y + 1, pulseBorder);
        graphics.fill(x, y + bannerHeight - 1, x + bannerWidth, y + bannerHeight, pulseBorder);
        graphics.fill(x, y, x + 1, y + bannerHeight, pulseBorder);
        graphics.fill(x + bannerWidth - 1, y, x + bannerWidth, y + bannerHeight, pulseBorder);

        String warningText = "\u26A0 GLOBAL MODE: Changes affect ALL future spawns of this mob type!";
        var safeFont = Objects.requireNonNull(font);
        int textW = UIScaleManager.getScaledStringWidth(safeFont, warningText);
        int textX = x + (bannerWidth - textW) / 2;
        int textY = y + (bannerHeight - 8) / 2;

        UIScaleManager.drawScaledString(graphics, safeFont, warningText, textX, textY, textColor, true);
    }

    // ═══════════════════════════════════════════════════════════════
    // TOOLTIPS
    // ═══════════════════════════════════════════════════════════════

    public void drawTooltips(GuiGraphics graphics, int panelX, int panelY, int mouseX, int mouseY) {
        if (state.showingConfirmDialog) return;

        var safeFont = Objects.requireNonNull(font);
        int bottomY = panelY + PANEL_HEIGHT - 70;

        if (AxiomRenderer.isMouseOver(mouseX, mouseY, panelX + 15, bottomY, 140, 20)) {
            String tip = state.isGlobalMode ?
                "Changes apply to all future spawns of this mob type" :
                "Changes apply only to this specific mob";
            AxiomRenderer.drawTooltip(graphics, safeFont, mouseX + 10, mouseY - 20, tip);
        }

        if (state.hoveredPreset >= 0 && state.hoveredPreset < MobConfigScreenState.PRESET_DESCRIPTIONS.size()) {
            String presetTip = "\u00A7e" + MobConfigScreenState.PRESET_NAMES.get(state.hoveredPreset)
                + "\u00A7r: " + MobConfigScreenState.PRESET_DESCRIPTIONS.get(state.hoveredPreset);
            AxiomRenderer.drawTooltip(graphics, safeFont, mouseX + 10, mouseY + 15, presetTip);
        }

        String[] userPresetNames = MobPresetManager.getPresetNames();
        if (state.hoveredUserPreset >= 0 && state.hoveredUserPreset < userPresetNames.length) {
            String userTip = "\u00A76" + userPresetNames[state.hoveredUserPreset] + "\u00A7r: Click to apply, Right-click to delete";
            AxiomRenderer.drawTooltip(graphics, safeFont, mouseX + 10, mouseY + 15, userTip);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // DIALOGS
    // ═══════════════════════════════════════════════════════════════

    public void drawConfirmDialog(GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        graphics.fill(0, 0, screenWidth, screenHeight, DesignTokens.MobConfig.OVERLAY);

        int dx = (screenWidth - DIALOG_WIDTH) / 2;
        int dy = (screenHeight - DIALOG_HEIGHT) / 2;

        graphics.fill(dx, dy, dx + DIALOG_WIDTH, dy + DIALOG_HEIGHT, DesignTokens.Background.PANEL_SOLID());
        AxiomRenderer.drawBorder(graphics, dx, dy, DIALOG_WIDTH, DIALOG_HEIGHT, DesignTokens.Accent.ORANGE());

        var safeFont = Objects.requireNonNull(font);
        String title = "\u00A7e\u26A0 Unsaved Changes";
        int titleW = UIScaleManager.getScaledStringWidth(safeFont, title);
        UIScaleManager.drawScaledString(graphics, safeFont, title, dx + (DIALOG_WIDTH - titleW) / 2, dy + 10, DesignTokens.Text.PRIMARY(), false);

        String msg1 = "You have unsaved changes.";
        String msg2 = "Do you want to discard them?";
        int msg1Width = UIScaleManager.getScaledStringWidth(safeFont, msg1);
        int msg2Width = UIScaleManager.getScaledStringWidth(safeFont, msg2);
        UIScaleManager.drawScaledString(graphics, safeFont, msg1, dx + (DIALOG_WIDTH - msg1Width) / 2, dy + 28, DesignTokens.Text.SECONDARY(), false);
        UIScaleManager.drawScaledString(graphics, safeFont, msg2, dx + (DIALOG_WIDTH - msg2Width) / 2, dy + 40, DesignTokens.Text.SECONDARY(), false);

        int btnW = 80;
        int btnH = 22;
        int btnGap = 20;
        int btnsX = dx + (DIALOG_WIDTH - (btnW * 2 + btnGap)) / 2;
        int btnsY = dy + DIALOG_HEIGHT - 35;

        boolean discardHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, btnsX, btnsY, btnW, btnH);
        drawStyledButton(graphics, btnsX, btnsY, btnW, btnH, "Discard", discardHovered, DesignTokens.Accent.RED(), true);

        int cancelX = btnsX + btnW + btnGap;
        boolean cancelHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, cancelX, btnsY, btnW, btnH);
        drawStyledButton(graphics, cancelX, btnsY, btnW, btnH, "Cancel", cancelHovered, DesignTokens.Accent.BLUE(), true);
    }

    public void drawSavePresetDialog(GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        graphics.fill(0, 0, screenWidth, screenHeight, DesignTokens.MobConfig.OVERLAY);

        int dw = 200;
        int dh = 90;
        int dx = (screenWidth - dw) / 2;
        int dy = (screenHeight - dh) / 2;

        graphics.fill(dx, dy, dx + dw, dy + dh, DesignTokens.Background.PANEL_SOLID());
        AxiomRenderer.drawBorder(graphics, dx, dy, dw, dh, DesignTokens.Accent.GOLD());

        var safeFont = Objects.requireNonNull(font);
        String title = "Save Preset";
        int saveTitleWidth = UIScaleManager.getScaledStringWidth(safeFont, title);
        UIScaleManager.drawScaledString(graphics, safeFont, title, dx + (dw - saveTitleWidth) / 2, dy + 8, DesignTokens.Accent.GOLD(), false);

        int inputX = dx + 10;
        int inputY = dy + 28;
        int inputW = dw - 20;
        int inputH = 16;

        graphics.fill(inputX, inputY, inputX + inputW, inputY + inputH, DesignTokens.Background.INPUT());
        AxiomRenderer.drawBorder(graphics, inputX, inputY, inputW, inputH, DesignTokens.Border.LIGHT());

        String inputText = state.presetNameInput.toString();
        if (inputText == null) inputText = "";
        UIScaleManager.drawScaledString(graphics, safeFont, inputText, inputX + 4, inputY + 4, DesignTokens.Text.WHITE(), false);

        if ((System.currentTimeMillis() / 500) % 2 == 0) {
            int cursorX = inputX + 4 + UIScaleManager.getScaledStringWidth(safeFont, inputText);
            graphics.fill(cursorX, inputY + 3, cursorX + 1, inputY + 13, DesignTokens.Text.WHITE());
        }

        if (inputText.isEmpty()) {
            UIScaleManager.drawScaledString(graphics, safeFont, "\u00A77Enter preset name...", inputX + 4, inputY + 4, DesignTokens.Text.MUTED(), false);
        }

        int btnW = 60;
        int btnH = 18;
        int btnGap = 10;
        int btnsX = dx + (dw - (btnW * 2 + btnGap)) / 2;
        int btnsY = dy + dh - 28;

        boolean saveHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, btnsX, btnsY, btnW, btnH);
        boolean cancelHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, btnsX + btnW + btnGap, btnsY, btnW, btnH);

        drawStyledButton(graphics, btnsX, btnsY, btnW, btnH, "Save", saveHovered, DesignTokens.Accent.GREEN(), true);
        drawStyledButton(graphics, btnsX + btnW + btnGap, btnsY, btnW, btnH, "Cancel", cancelHovered, DesignTokens.Accent.RED(), true);

        int remaining = 10 - MobPresetManager.getPresetCount();
        UIScaleManager.drawScaledString(graphics, safeFont, "\u00A77" + remaining + " slots left", dx + dw - 50, dy + dh - 10, DesignTokens.Text.MUTED(), false);
    }

    public void drawDeletePresetDialog(GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        graphics.fill(0, 0, screenWidth, screenHeight, DesignTokens.MobConfig.OVERLAY);

        int dw = 220;
        int dh = 80;
        int dx = (screenWidth - dw) / 2;
        int dy = (screenHeight - dh) / 2;

        graphics.fill(dx, dy, dx + dw, dy + dh, DesignTokens.Background.PANEL_SOLID());
        AxiomRenderer.drawBorder(graphics, dx, dy, dw, dh, DesignTokens.Accent.RED());

        var safeFont = Objects.requireNonNull(font);
        String title = "\u00A7cDelete Preset?";
        int deleteTitleWidth = UIScaleManager.getScaledStringWidth(safeFont, title);
        UIScaleManager.drawScaledString(graphics, safeFont, title, dx + (dw - deleteTitleWidth) / 2, dy + 8, DesignTokens.Accent.RED(), false);

        String msg = "Delete \"" + state.deleteConfirmPreset + "\"?";
        int deleteMsgWidth = UIScaleManager.getScaledStringWidth(safeFont, msg);
        UIScaleManager.drawScaledString(graphics, safeFont, msg, dx + (dw - deleteMsgWidth) / 2, dy + 28, DesignTokens.Text.SECONDARY(), false);

        int btnW = 60;
        int btnH = 18;
        int btnGap = 10;
        int btnsX = dx + (dw - (btnW * 2 + btnGap)) / 2;
        int btnsY = dy + dh - 28;

        boolean deleteHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, btnsX, btnsY, btnW, btnH);
        boolean cancelHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, btnsX + btnW + btnGap, btnsY, btnW, btnH);

        drawStyledButton(graphics, btnsX, btnsY, btnW, btnH, "Delete", deleteHovered, DesignTokens.Accent.RED(), true);
        drawStyledButton(graphics, btnsX + btnW + btnGap, btnsY, btnW, btnH, "Cancel", cancelHovered, DesignTokens.Accent.BLUE(), true);
    }
}
