package com.frenkvs.devmod;

import com.frenkvs.devmod.ui.AxiomRenderer;
import com.frenkvs.devmod.ui.UIConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.Mob;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Rendering logic for MobConfigScreen.
 * Handles all drawing operations for panels, tabs, sliders, dialogs, and tooltips.
 */
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
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, UIConstants.Background.PANEL_SOLID());

        // Gradient header
        int headerH = UIConstants.Spacing.HEADER_HEIGHT;
        for (int i = 0; i < headerH; i++) {
            float t = (float) i / headerH;
            int color = UIConstants.lerp(0xFF2A2A42, UIConstants.Background.HEADER(), t);
            graphics.fill(panelX, panelY + i, panelX + PANEL_WIDTH, panelY + i + 1, color);
        }

        // Border with glow effect
        int glowColor = UIConstants.setAlpha(UIConstants.Border.DEFAULT(), 80);
        graphics.fill(panelX - 2, panelY - 2, panelX + PANEL_WIDTH + 2, panelY, glowColor);
        graphics.fill(panelX - 2, panelY + PANEL_HEIGHT, panelX + PANEL_WIDTH + 2, panelY + PANEL_HEIGHT + 2, glowColor);
        graphics.fill(panelX - 2, panelY, panelX, panelY + PANEL_HEIGHT, glowColor);
        graphics.fill(panelX + PANEL_WIDTH, panelY, panelX + PANEL_WIDTH + 2, panelY + PANEL_HEIGHT, glowColor);

        AxiomRenderer.drawBorder(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, UIConstants.Border.DEFAULT());

        // Title with mob name
        String title = "§l" + mob.getName().getString();
        graphics.drawString(font, title, panelX + 12, panelY + 6, UIConstants.Text.PRIMARY(), false);

        // Mob type (smaller, muted)
        String type = mob.getType().getDescriptionId().replace("entity.minecraft.", "").replace("_", " ");
        type = Character.toUpperCase(type.charAt(0)) + type.substring(1);
        graphics.drawString(font, "(" + type + ")", panelX + 14 + font.width(title), panelY + 6, UIConstants.Text.MUTED(), false);

        // Health indicator in header
        float healthPercent = mob.getHealth() / mob.getMaxHealth();
        int healthBarX = panelX + PANEL_WIDTH - 80;
        int healthBarY = panelY + 5;
        drawMiniHealthBar(graphics, healthBarX, healthBarY, 70, 10, healthPercent);
    }

    private void drawMiniHealthBar(GuiGraphics graphics, int x, int y, int w, int h, float percent) {
        graphics.fill(x, y, x + w, y + h, UIConstants.Background.INPUT());
        int fillW = (int) (w * percent);
        int color = percent > 0.5f ? UIConstants.Accent.GREEN() :
                    (percent > 0.25f ? UIConstants.Accent.YELLOW() : UIConstants.Accent.RED());
        if (fillW > 0) {
            graphics.fill(x, y, x + fillW, y + h, color);
        }
        AxiomRenderer.drawBorder(graphics, x, y, w, h, UIConstants.Border.MUTED());
        graphics.drawString(font, "§c❤", x + 2, y + 1, 0xFFFFFFFF, false);
    }

    // ═══════════════════════════════════════════════════════════════
    // TABS
    // ═══════════════════════════════════════════════════════════════

    public void drawTabs(GuiGraphics graphics, int panelX, int panelY, int mouseX, int mouseY) {
        String[] tabs = {"Stats", "Combat", "AI"};
        int tabsX = panelX + 10;
        int tabsY = panelY + UIConstants.Spacing.HEADER_HEIGHT;

        for (int i = 0; i < tabs.length; i++) {
            int tx = tabsX + i * (TAB_WIDTH + 4);
            boolean selected = (state.selectedTab == i);
            boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, tx, tabsY, TAB_WIDTH, TAB_HEIGHT);

            int bgColor = selected ? UIConstants.Background.ACTIVE() :
                          (hovered ? UIConstants.Background.HOVER() : UIConstants.Background.HEADER());
            graphics.fill(tx, tabsY, tx + TAB_WIDTH, tabsY + TAB_HEIGHT, bgColor);

            if (selected) {
                graphics.fill(tx, tabsY + TAB_HEIGHT - 2, tx + TAB_WIDTH, tabsY + TAB_HEIGHT, UIConstants.Border.ACCENT());
            }

            int textColor = selected ? UIConstants.Text.ACCENT() : UIConstants.Text.PRIMARY();
            int textW = font.width(tabs[i]);
            graphics.drawString(font, tabs[i], tx + (TAB_WIDTH - textW) / 2, tabsY + 7, textColor, false);
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
            int color = UIConstants.lerp(0xFF151525, 0xFF1A1A2E, t);
            graphics.fill(x, y + i, x + boxW, y + i + 1, color);
        }

        AxiomRenderer.drawBorder(graphics, x, y, boxW, boxH, UIConstants.Border.MUTED());

        // Corner accents
        int accentLen = 10;
        int accentColor = UIConstants.Border.ACCENT();
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

        try {
            InventoryScreen.renderEntityInInventory(
                graphics, centerX, centerY, scale,
                new Vector3f(0, 0, 0), rotation, null, mob
            );
        } catch (Exception e) {
            graphics.drawString(font, "Preview", centerX - 20, centerY - 40, UIConstants.Text.MUTED(), false);
        }

        boolean hovering = AxiomRenderer.isMouseOver(mouseX, mouseY, x, y, boxW, boxH);
        if (hovering && !state.isDraggingPreview) {
            graphics.drawString(font, "§8Drag: rotate | Scroll: zoom", x + 4, y + boxH - 12, UIConstants.Text.MUTED(), false);
        } else if (state.isDraggingPreview) {
            graphics.drawString(font, "§a↻ Rotating...", x + 4, y + boxH - 12, UIConstants.Accent.GREEN(), false);
        }

        if (Math.abs(state.previewZoom - 1.0f) > 0.05f) {
            String zoomText = String.format("%.0f%%", state.previewZoom * 100);
            graphics.drawString(font, zoomText, x + boxW - font.width(zoomText) - 4, y + 4, UIConstants.Text.MUTED(), false);
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
                originals = new double[]{state.origHealth, state.origArmor, state.origSpeed * 100};
                maxValues = new double[]{MobConfigScreenState.MAX_HEALTH, MobConfigScreenState.MAX_ARMOR, MobConfigScreenState.MAX_SPEED * 100};
                colors = new int[]{UIConstants.Accent.RED(), UIConstants.Accent.CYAN(), UIConstants.Accent.GREEN()};
            }
            case 1 -> { // Combat
                labels = new String[]{"Attack Damage", "Attack Reach", "Knockback Resist"};
                values = new double[]{state.damage, state.attackRange, state.knockbackResist * 100};
                originals = new double[]{state.origDamage, state.origAttackRange, state.origKnockbackResist * 100};
                maxValues = new double[]{MobConfigScreenState.MAX_DAMAGE, MobConfigScreenState.MAX_ATTACK_RANGE, MobConfigScreenState.MAX_KNOCKBACK_RES * 100};
                colors = new int[]{UIConstants.Accent.ORANGE(), UIConstants.Accent.YELLOW(), UIConstants.Accent.PURPLE()};
            }
            case 2 -> { // AI
                labels = new String[]{"Follow Range"};
                values = new double[]{state.followRange};
                originals = new double[]{state.origFollowRange};
                maxValues = new double[]{MobConfigScreenState.MAX_FOLLOW_RANGE};
                colors = new int[]{UIConstants.Accent.BLUE()};
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
        graphics.drawString(font, label, x, y, UIConstants.Text.PRIMARY(), false);

        int valueX = x + SLIDER_WIDTH + 10;
        int valueW = 35;
        int valueH = 12;

        if (state.editingSlider == sliderId) {
            graphics.fill(valueX - 2, y - 2, valueX + valueW + 2, y + valueH, UIConstants.Background.INPUT());
            AxiomRenderer.drawBorder(graphics, valueX - 2, y - 2, valueW + 4, valueH + 2, UIConstants.Accent.GOLD());

            String inputText = state.inputBuffer.toString();
            graphics.drawString(font, inputText, valueX, y, UIConstants.Text.WHITE(), false);

            if ((System.currentTimeMillis() - state.inputCursorBlink) % 1000 < 500) {
                int cursorX = valueX + font.width(inputText);
                graphics.fill(cursorX, y, cursorX + 1, y + 9, UIConstants.Text.WHITE());
            }
        } else {
            String valueStr = String.format("%.1f", value);
            boolean valueHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, valueX - 2, y - 2, valueW + 4, valueH + 2);

            if (valueHovered) {
                graphics.fill(valueX - 2, y - 2, valueX + valueW + 2, y + valueH, UIConstants.Background.HOVER());
                AxiomRenderer.drawBorder(graphics, valueX - 2, y - 2, valueW + 4, valueH + 2, UIConstants.Border.LIGHT());
            }

            int valueColor = Math.abs(value - original) > 0.01 ? UIConstants.Accent.GOLD() : UIConstants.Text.SECONDARY();
            graphics.drawString(font, valueStr, valueX, y, valueColor, false);
        }

        if (Math.abs(value - original) > 0.01) {
            double change = value - original;
            String changeStr = String.format("%+.1f", change);
            int changeColor = change > 0 ? UIConstants.Accent.GREEN() : UIConstants.Accent.RED();
            graphics.drawString(font, changeStr, x + SLIDER_WIDTH + 50, y, changeColor, false);
        }

        int sliderY = y + 12;
        int trackX = x;
        int trackW = SLIDER_WIDTH;
        int trackH = SLIDER_HEIGHT;

        graphics.fill(trackX, sliderY, trackX + trackW, sliderY + trackH, UIConstants.Background.INPUT());

        float origPercent = maxVal > 0 ? (float) (original / maxVal) : 0f;
        int origMarkerX = trackX + (int) (trackW * Math.min(1, origPercent));
        graphics.fill(origMarkerX - 1, sliderY, origMarkerX + 1, sliderY + trackH, 0x80FFFFFF);

        float percent = maxVal > 0 ? (float) (value / maxVal) : 0f;
        int fillW = (int) (trackW * Math.min(1, percent));
        if (fillW > 0 && trackW > 0) {
            for (int i = 0; i < fillW; i++) {
                float t = (float) i / trackW;
                int color = UIConstants.lerp(UIConstants.darken(accentColor, 0.3f), accentColor, t);
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
                UIConstants.setAlpha(accentColor, glowAlpha));
        }

        graphics.fill(handleX, sliderY - 1, handleX + handleW, sliderY + trackH + 1, accentColor);
        graphics.fill(handleX + 1, sliderY, handleX + handleW - 1, sliderY + trackH, UIConstants.lighten(accentColor, 0.3f));

        AxiomRenderer.drawBorder(graphics, trackX, sliderY, trackW, trackH, UIConstants.Border.MUTED());
    }

    // ═══════════════════════════════════════════════════════════════
    // BOTTOM SECTION
    // ═══════════════════════════════════════════════════════════════

    public void drawBottomSection(GuiGraphics graphics, int panelX, int y, int mouseX, int mouseY) {
        graphics.fill(panelX + 10, y - 8, panelX + PANEL_WIDTH - 10, y - 7, UIConstants.Border.SEPARATOR());

        int modeX = panelX + 15;
        boolean modeHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, modeX, y, 120, 22);

        int modeBg = modeHovered ? UIConstants.Background.HOVER() : UIConstants.Background.INPUT();
        graphics.fill(modeX, y, modeX + 120, y + 22, modeBg);
        AxiomRenderer.drawBorder(graphics, modeX, y, 120, 22, UIConstants.Border.MUTED());

        int modeAccent = state.isGlobalMode ? UIConstants.Accent.ORANGE() : UIConstants.Accent.BLUE();
        graphics.fill(modeX, y, modeX + 3, y + 22, modeAccent);

        graphics.drawString(font, "Mode:", modeX + 8, y + 3, UIConstants.Text.SECONDARY(), false);
        String modeText = state.isGlobalMode ? "GLOBAL" : "SPECIFIC";
        int modeColor = state.isGlobalMode ? UIConstants.Accent.ORANGE() : UIConstants.Text.PRIMARY();
        graphics.drawString(font, modeText, modeX + 8, y + 12, modeColor, false);

        drawPresets(graphics, panelX + 150, y - 3, mouseX, mouseY);

        int btnY = y + 56;
        int btnW = 70;
        int btnH = 22;
        int btnGap = 8;

        int resetX = panelX + 15;
        boolean resetHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, resetX, btnY, 55, btnH);
        drawStyledButton(graphics, resetX, btnY, 55, btnH, "Reset", resetHovered, UIConstants.Accent.RED());

        int applyX = panelX + PANEL_WIDTH - btnW - btnGap - btnW - 15;
        boolean applyHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, applyX, btnY, btnW, btnH);
        drawStyledButton(graphics, applyX, btnY, btnW, btnH, "Apply", applyHovered, UIConstants.Accent.GREEN());

        int equipX = applyX + btnW + btnGap;
        boolean equipHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, equipX, btnY, btnW, btnH);
        drawStyledButton(graphics, equipX, btnY, btnW, btnH, "Equip...", equipHovered, UIConstants.Accent.BLUE());
    }

    private void drawPresets(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        graphics.drawString(font, "Presets:", x, y, UIConstants.Text.SECONDARY(), false);

        int presetY = y + 12;
        int presetW = 55;
        int presetH = 16;
        int gap = 4;

        state.hoveredPreset = -1;
        state.hoveredUserPreset = -1;

        for (int i = 0; i < MobConfigScreenState.PRESET_NAMES.length; i++) {
            int row = i / 3;
            int col = i % 3;
            int px = x + col * (presetW + gap);
            int py = presetY + row * (presetH + gap);

            boolean selected = (state.selectedPreset == i);
            boolean hovered = AxiomRenderer.isMouseOver(mouseX, mouseY, px, py, presetW, presetH);

            if (hovered) {
                state.hoveredPreset = i;
            }

            int bgColor = selected ? MobConfigScreenState.PRESET_COLORS[i][0] : (hovered ? UIConstants.Background.HOVER() : UIConstants.Background.INPUT());
            if (selected) {
                bgColor = UIConstants.setAlpha(bgColor, 180);
            }
            graphics.fill(px, py, px + presetW, py + presetH, bgColor);

            int borderColor = selected ? MobConfigScreenState.PRESET_COLORS[i][1] : UIConstants.Border.MUTED();
            AxiomRenderer.drawBorder(graphics, px, py, presetW, presetH, borderColor);

            String name = MobConfigScreenState.PRESET_NAMES[i];
            int textW = font.width(name);
            int textColor = selected ? UIConstants.Text.WHITE() : UIConstants.Text.SECONDARY();
            graphics.drawString(font, name, px + (presetW - textW) / 2, py + 4, textColor, false);
        }

        // User presets
        String[] userPresetNames = MobPresetManager.getPresetNames();
        if (userPresetNames.length > 0 || MobPresetManager.canAddPreset()) {
            int userY = presetY + 2 * (presetH + gap) + 6;

            graphics.drawString(font, "§7My:", x, userY, UIConstants.Text.MUTED(), false);

            int saveBtnX = x + 20;
            boolean saveHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, saveBtnX, userY - 1, 12, 10);
            if (MobPresetManager.canAddPreset()) {
                int saveColor = saveHovered ? UIConstants.Accent.GREEN() : UIConstants.Text.MUTED();
                graphics.drawString(font, "[+]", saveBtnX, userY, saveColor, false);
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

                int bgColor = hovered ? UIConstants.Background.HOVER() : UIConstants.Background.INPUT();
                graphics.fill(px, py, px + userPresetW, py + userPresetH, bgColor);

                int borderColor = hovered ? UIConstants.Accent.GOLD() : UIConstants.Border.MUTED();
                AxiomRenderer.drawBorder(graphics, px, py, userPresetW, userPresetH, borderColor);

                String name = userPresetNames[i];
                if (font.width(name) > userPresetW - 4) {
                    String ellipsis = "..";
                    int minChars = Math.min(3, name.length());
                    while (font.width(name + ellipsis) > userPresetW - 4 && name.length() > minChars) {
                        name = name.substring(0, name.length() - 1);
                    }
                    name = name + ellipsis;
                }
                graphics.drawString(font, name, px + 2, py + 3, UIConstants.Text.SECONDARY(), false);
            }

            if (userPresetNames.length > maxVisible) {
                int moreX = userStartX + maxVisible * (userPresetW + 2);
                graphics.drawString(font, "+" + (userPresetNames.length - maxVisible), moreX, userY, UIConstants.Text.MUTED(), false);
            }
        }
    }

    public void drawStyledButton(GuiGraphics graphics, int x, int y, int w, int h, String text, boolean hovered, int accentColor) {
        int bgColor = hovered ? UIConstants.setAlpha(accentColor, 60) : UIConstants.Background.PANEL();
        graphics.fill(x, y, x + w, y + h, bgColor);

        graphics.fill(x, y, x + 3, y + h, accentColor);

        int borderColor = hovered ? accentColor : UIConstants.Border.MUTED();
        AxiomRenderer.drawBorder(graphics, x, y, w, h, borderColor);

        int textW = font.width(text);
        int textX = x + (w - textW) / 2;
        int textY = y + (h - 8) / 2;
        graphics.drawString(font, text, textX, textY, hovered ? UIConstants.Text.WHITE() : UIConstants.Text.PRIMARY(), false);
    }

    // ═══════════════════════════════════════════════════════════════
    // GLOBAL MODE WARNING
    // ═══════════════════════════════════════════════════════════════

    public void drawGlobalModeWarning(GuiGraphics graphics, int x, int y) {
        int bannerWidth = PANEL_WIDTH;
        int bannerHeight = 24;

        int bgColor = UIConstants.setAlpha(UIConstants.Accent.ORANGE(), 0xEE);
        int borderColor = UIConstants.Accent.ORANGE();
        int textColor = UIConstants.Accent.GOLD();

        long time = System.currentTimeMillis();
        float pulse = (float) (0.8 + 0.2 * Math.sin(time / 300.0));
        int pulseAlpha = (int) (255 * pulse);
        int pulseBorder = (pulseAlpha << 24) | (borderColor & 0x00FFFFFF);

        graphics.fill(x, y, x + bannerWidth, y + bannerHeight, bgColor);

        graphics.fill(x, y, x + bannerWidth, y + 1, pulseBorder);
        graphics.fill(x, y + bannerHeight - 1, x + bannerWidth, y + bannerHeight, pulseBorder);
        graphics.fill(x, y, x + 1, y + bannerHeight, pulseBorder);
        graphics.fill(x + bannerWidth - 1, y, x + bannerWidth, y + bannerHeight, pulseBorder);

        String warningText = "\u26A0 GLOBAL MODE: Changes affect ALL future spawns of this mob type!";
        int textW = font.width(warningText);
        int textX = x + (bannerWidth - textW) / 2;
        int textY = y + (bannerHeight - 8) / 2;

        graphics.drawString(font, warningText, textX, textY, textColor, true);
    }

    // ═══════════════════════════════════════════════════════════════
    // TOOLTIPS
    // ═══════════════════════════════════════════════════════════════

    public void drawTooltips(GuiGraphics graphics, int panelX, int panelY, int mouseX, int mouseY) {
        if (state.showingConfirmDialog) return;

        int bottomY = panelY + PANEL_HEIGHT - 70;

        if (AxiomRenderer.isMouseOver(mouseX, mouseY, panelX + 15, bottomY, 140, 20)) {
            String tip = state.isGlobalMode ?
                "Changes apply to all future spawns of this mob type" :
                "Changes apply only to this specific mob";
            AxiomRenderer.drawTooltip(graphics, font, mouseX + 10, mouseY - 20, tip);
        }

        if (state.hoveredPreset >= 0 && state.hoveredPreset < MobConfigScreenState.PRESET_DESCRIPTIONS.length) {
            String presetTip = "§e" + MobConfigScreenState.PRESET_NAMES[state.hoveredPreset] + "§r: " + MobConfigScreenState.PRESET_DESCRIPTIONS[state.hoveredPreset];
            AxiomRenderer.drawTooltip(graphics, font, mouseX + 10, mouseY + 15, presetTip);
        }

        String[] userPresetNames = MobPresetManager.getPresetNames();
        if (state.hoveredUserPreset >= 0 && state.hoveredUserPreset < userPresetNames.length) {
            String userTip = "§6" + userPresetNames[state.hoveredUserPreset] + "§r: Click to apply, Right-click to delete";
            AxiomRenderer.drawTooltip(graphics, font, mouseX + 10, mouseY + 15, userTip);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // DIALOGS
    // ═══════════════════════════════════════════════════════════════

    public void drawConfirmDialog(GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        graphics.fill(0, 0, screenWidth, screenHeight, 0xA0000000);

        int dx = (screenWidth - DIALOG_WIDTH) / 2;
        int dy = (screenHeight - DIALOG_HEIGHT) / 2;

        graphics.fill(dx, dy, dx + DIALOG_WIDTH, dy + DIALOG_HEIGHT, UIConstants.Background.PANEL_SOLID());
        AxiomRenderer.drawBorder(graphics, dx, dy, DIALOG_WIDTH, DIALOG_HEIGHT, UIConstants.Accent.ORANGE());

        String title = "§e\u26A0 Unsaved Changes";
        int titleW = font.width(title);
        graphics.drawString(font, title, dx + (DIALOG_WIDTH - titleW) / 2, dy + 10, UIConstants.Text.PRIMARY(), false);

        String msg1 = "You have unsaved changes.";
        String msg2 = "Do you want to discard them?";
        graphics.drawString(font, msg1, dx + (DIALOG_WIDTH - font.width(msg1)) / 2, dy + 28, UIConstants.Text.SECONDARY(), false);
        graphics.drawString(font, msg2, dx + (DIALOG_WIDTH - font.width(msg2)) / 2, dy + 40, UIConstants.Text.SECONDARY(), false);

        int btnW = 80;
        int btnH = 22;
        int btnGap = 20;
        int btnsX = dx + (DIALOG_WIDTH - (btnW * 2 + btnGap)) / 2;
        int btnsY = dy + DIALOG_HEIGHT - 35;

        boolean discardHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, btnsX, btnsY, btnW, btnH);
        drawStyledButton(graphics, btnsX, btnsY, btnW, btnH, "Discard", discardHovered, UIConstants.Accent.RED());

        int cancelX = btnsX + btnW + btnGap;
        boolean cancelHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, cancelX, btnsY, btnW, btnH);
        drawStyledButton(graphics, cancelX, btnsY, btnW, btnH, "Cancel", cancelHovered, UIConstants.Accent.BLUE());
    }

    public void drawSavePresetDialog(GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        graphics.fill(0, 0, screenWidth, screenHeight, 0xA0000000);

        int dw = 200;
        int dh = 90;
        int dx = (screenWidth - dw) / 2;
        int dy = (screenHeight - dh) / 2;

        graphics.fill(dx, dy, dx + dw, dy + dh, UIConstants.Background.PANEL_SOLID());
        AxiomRenderer.drawBorder(graphics, dx, dy, dw, dh, UIConstants.Accent.GOLD());

        String title = "Save Preset";
        graphics.drawString(font, title, dx + (dw - font.width(title)) / 2, dy + 8, UIConstants.Accent.GOLD(), false);

        int inputX = dx + 10;
        int inputY = dy + 28;
        int inputW = dw - 20;
        int inputH = 16;

        graphics.fill(inputX, inputY, inputX + inputW, inputY + inputH, UIConstants.Background.INPUT());
        AxiomRenderer.drawBorder(graphics, inputX, inputY, inputW, inputH, UIConstants.Border.LIGHT());

        String inputText = state.presetNameInput.toString();
        graphics.drawString(font, inputText, inputX + 4, inputY + 4, UIConstants.Text.WHITE(), false);

        if ((System.currentTimeMillis() / 500) % 2 == 0) {
            int cursorX = inputX + 4 + font.width(inputText);
            graphics.fill(cursorX, inputY + 3, cursorX + 1, inputY + 13, UIConstants.Text.WHITE());
        }

        if (inputText.isEmpty()) {
            graphics.drawString(font, "§7Enter preset name...", inputX + 4, inputY + 4, UIConstants.Text.MUTED(), false);
        }

        int btnW = 60;
        int btnH = 18;
        int btnGap = 10;
        int btnsX = dx + (dw - (btnW * 2 + btnGap)) / 2;
        int btnsY = dy + dh - 28;

        boolean saveHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, btnsX, btnsY, btnW, btnH);
        boolean cancelHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, btnsX + btnW + btnGap, btnsY, btnW, btnH);

        drawStyledButton(graphics, btnsX, btnsY, btnW, btnH, "Save", saveHovered, UIConstants.Accent.GREEN());
        drawStyledButton(graphics, btnsX + btnW + btnGap, btnsY, btnW, btnH, "Cancel", cancelHovered, UIConstants.Accent.RED());

        int remaining = 10 - MobPresetManager.getPresetCount();
        graphics.drawString(font, "§7" + remaining + " slots left", dx + dw - 50, dy + dh - 10, UIConstants.Text.MUTED(), false);
    }

    public void drawDeletePresetDialog(GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        graphics.fill(0, 0, screenWidth, screenHeight, 0xA0000000);

        int dw = 220;
        int dh = 80;
        int dx = (screenWidth - dw) / 2;
        int dy = (screenHeight - dh) / 2;

        graphics.fill(dx, dy, dx + dw, dy + dh, UIConstants.Background.PANEL_SOLID());
        AxiomRenderer.drawBorder(graphics, dx, dy, dw, dh, UIConstants.Accent.RED());

        String title = "§cDelete Preset?";
        graphics.drawString(font, title, dx + (dw - font.width(title)) / 2, dy + 8, UIConstants.Accent.RED(), false);

        String msg = "Delete \"" + state.deleteConfirmPreset + "\"?";
        graphics.drawString(font, msg, dx + (dw - font.width(msg)) / 2, dy + 28, UIConstants.Text.SECONDARY(), false);

        int btnW = 60;
        int btnH = 18;
        int btnGap = 10;
        int btnsX = dx + (dw - (btnW * 2 + btnGap)) / 2;
        int btnsY = dy + dh - 28;

        boolean deleteHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, btnsX, btnsY, btnW, btnH);
        boolean cancelHovered = AxiomRenderer.isMouseOver(mouseX, mouseY, btnsX + btnW + btnGap, btnsY, btnW, btnH);

        drawStyledButton(graphics, btnsX, btnsY, btnW, btnH, "Delete", deleteHovered, UIConstants.Accent.RED());
        drawStyledButton(graphics, btnsX + btnW + btnGap, btnsY, btnW, btnH, "Cancel", cancelHovered, UIConstants.Accent.BLUE());
    }
}
