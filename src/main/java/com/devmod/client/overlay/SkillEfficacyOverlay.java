package com.devmod.client.overlay;

import java.util.List;
import java.util.Objects;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.devmod.DevMod;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.overlay.OverlayTheme;
import com.devmod.telemetry.skills.SkillTrackingService;

@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)

public class SkillEfficacyOverlay {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "skill_efficacy");

    // === UI Colors (delegating to OverlayTheme) ===
    private static final int PANEL_BG = OverlayTheme.Panel.BG_STANDARD;
    private static final int PANEL_BORDER = OverlayTheme.Border.ACCENT;
    private static final int TEXT_TITLE = OverlayTheme.Text.TITLE;
    private static final int TEXT_NORMAL = OverlayTheme.Text.PRIMARY;
    private static final int TEXT_VALUE = OverlayTheme.Text.VALUE;
    private static final int TEXT_WARNING = OverlayTheme.Text.WARNING;
    private static final int TEXT_DANGER = OverlayTheme.Text.DANGER;
    private static final int TEXT_MUTED = OverlayTheme.Text.MUTED;

    // === Dimensions ===
    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_PADDING = 6;
    private static final int LINE_HEIGHT = 10;
    private static final int MAX_SKILLS_SHOWN = 5;

    // === State ===
    private static boolean enabled = false;

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            Objects.requireNonNull(VanillaGuiLayers.HOTBAR),
            Objects.requireNonNull(LAYER_ID),
            SkillEfficacyOverlay::render
        );
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        UIScaleManager.update();
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;

        Font font = mc.font;
        int lineHeight = UIScaleManager.getScaledLineHeight(font, LINE_HEIGHT);
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        // Position: right side, below center
        int panelWidth = Math.min(UIScaleManager.scale(PANEL_WIDTH), UIScaleManager.getSafeWidth());
        int panelX = screenWidth - panelWidth - UIScaleManager.scale(10);
        int panelY = screenHeight / 2;
        int panelHeight = calculatePanelHeight(recentSkillsCount(), lineHeight);
        int safeLeft = UIScaleManager.getSafeLeft();
        int safeRight = UIScaleManager.getSafeRight();
        int safeTop = UIScaleManager.getSafeTop();
        int safeBottom = UIScaleManager.getSafeBottom();
        panelX = Math.max(safeLeft, Math.min(panelX, safeRight - panelWidth));
        panelY = Math.max(safeTop, Math.min(panelY, safeBottom - panelHeight));

        renderSkillPanel(graphics, font, panelX, panelY, panelWidth, lineHeight);
    }

    private static void renderSkillPanel(GuiGraphics graphics, Font font, int x, int y, int panelWidth, int lineHeight) {
        List<SkillTrackingService.SkillStats> recentSkills =
            SkillTrackingService.INSTANCE.getRecentSkills(MAX_SKILLS_SHOWN);

        int panelHeight = calculatePanelHeight(recentSkills.size(), lineHeight);
        int panelPadding = UIScaleManager.scale(PANEL_PADDING);

        // Background
        graphics.fill(x, y, x + panelWidth, y + panelHeight, PANEL_BG);

        // Border
        drawBorder(graphics, x, y, panelWidth, panelHeight, PANEL_BORDER);

        int textX = x + panelPadding;
        int textY = y + panelPadding;

        // Title
        var safeFont = Objects.requireNonNull(font);
        UIScaleManager.drawScaledString(graphics, safeFont, "Skill Efficacy", textX, textY, TEXT_TITLE, false);
        textY += lineHeight + UIScaleManager.scale(2);

        // Separator
        graphics.fill(x + 4, textY, x + panelWidth - 4, textY + 1,
            OverlayTheme.withAlpha(PANEL_BORDER, OverlayTheme.Alpha.GHOST));
        textY += 4;

        if (recentSkills.isEmpty()) {
            UIScaleManager.drawScaledString(graphics, safeFont, "No skills used yet", textX, textY, TEXT_MUTED, false);
            return;
        }

        // Skill entries
        for (SkillTrackingService.SkillStats skill : recentSkills) {
            renderSkillEntry(graphics, font, textX, textY, skill, lineHeight);
            textY += lineHeight * 2 + UIScaleManager.scale(2);
        }
    }

    private static void renderSkillEntry(GuiGraphics graphics, Font font, int x, int y,
                                          SkillTrackingService.SkillStats skill, int lineHeight) {
        var safeFont = Objects.requireNonNull(font);
        // Skill name
        String name = truncateName(skill.skillId(), 15);
        UIScaleManager.drawScaledString(graphics, safeFont, "[" + name + "]", x, y, TEXT_NORMAL, false);

        // Hit rate
        float hitRate = skill.getHitRate();
        int hitColor = hitRate >= 0.7f ? TEXT_VALUE : hitRate >= 0.4f ? TEXT_WARNING : TEXT_DANGER;
        String hitText = String.format("Hit: %d/%d (%.0f%%)",
            skill.hits(), skill.uses(), hitRate * 100);
        UIScaleManager.drawScaledString(graphics, safeFont, hitText, x + UIScaleManager.scale(100), y, hitColor, false);

        y += lineHeight;

        // Damage
        String dmgText = String.format("Dmg: %.1f", skill.totalDamage());
        UIScaleManager.drawScaledString(graphics, safeFont, dmgText, x + UIScaleManager.scale(10), y, TEXT_MUTED, false);

        // Cooldown or last use
        long lastUseMs = skill.lastUseMs();
        long elapsedSec = (System.currentTimeMillis() - lastUseMs) / 1000;
        String timeText = elapsedSec < 60 ? elapsedSec + "s ago" : (elapsedSec / 60) + "m ago";
        UIScaleManager.drawScaledString(graphics, safeFont, timeText, x + UIScaleManager.scale(100), y, TEXT_MUTED, false);
    }

    private static String truncateName(String name, int maxLen) {
        // Remove common prefixes
        if (name.startsWith("spell_")) {
            name = name.substring(6);
        } else if (name.startsWith("skill_")) {
            name = name.substring(6);
        } else if (name.startsWith("effect_")) {
            name = name.substring(7);
        }

        // Capitalize first letter
        if (!name.isEmpty()) {
            name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }

        // Truncate if needed (keep at least 6 chars for readability)
        if (name.length() > maxLen) {
            int minChars = Math.min(6, maxLen - 3);
            int truncateAt = Math.max(minChars, maxLen - 3);
            name = name.substring(0, truncateAt) + "...";
        }

        return name;
    }

    private static int calculatePanelHeight(int skillCount, int lineHeight) {
        int padding = UIScaleManager.scale(PANEL_PADDING);
        int height = padding * 2;
        height += lineHeight + 2;  // Title
        height += 4;                // Separator

        if (skillCount == 0) {
            height += lineHeight;  // "No skills" message
        } else {
            height += skillCount * (lineHeight * 2 + 2);
        }

        return height;
    }

    private static int recentSkillsCount() {
        List<SkillTrackingService.SkillStats> recentSkills =
            SkillTrackingService.INSTANCE.getRecentSkills(MAX_SKILLS_SHOWN);
        return recentSkills != null ? recentSkills.size() : 0;
    }

    private static void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);                    // Top
        graphics.fill(x, y + height - 1, x + width, y + height, color);  // Bottom
        graphics.fill(x, y, x + 1, y + height, color);                   // Left
        graphics.fill(x + width - 1, y, x + width, y + height, color);   // Right
    }

    // === Public API ===

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void toggle() {
        enabled = !enabled;
    }
}
