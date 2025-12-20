package com.frenkvs.devmod.hud;

import com.frenkvs.devmod.DevMod;
import com.frenkvs.devmod.telemetry.skills.SkillTrackingService;
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

import java.util.List;
import java.util.Objects;

/**
 * HUD Overlay for displaying skill efficacy information.
 *
 * Shows:
 * - Last 5 skills used
 * - Hit rate percentage
 * - Total damage dealt
 * - Cooldown status
 *
 * Toggle: F5 key (configurable)
 * Position: Right side, below Impact HUD
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)

public class SkillEfficacyOverlay {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "skill_efficacy");

    // === UI Colors (consistent with ImpactHudOverlay) ===
    private static final int PANEL_BG = 0xCC1A1A2E;           // Dark blue 80% opacity
    private static final int PANEL_BORDER = 0xFF3D5AFE;       // Electric blue
    private static final int TEXT_TITLE = 0xFF00FFFF;         // Cyan
    private static final int TEXT_NORMAL = 0xFFFFFFFF;        // White
    private static final int TEXT_VALUE = 0xFF00FF00;         // Green
    private static final int TEXT_WARNING = 0xFFFFFF00;       // Yellow
    private static final int TEXT_DANGER = 0xFFFF4444;        // Red
    private static final int TEXT_MUTED = 0xFFAAAAAA;         // Gray

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
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;

        Font font = mc.font;
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        // Position: right side, below center
        int panelX = screenWidth - PANEL_WIDTH - 10;
        int panelY = screenHeight / 2;

        renderSkillPanel(graphics, font, panelX, panelY);
    }

    private static void renderSkillPanel(GuiGraphics graphics, Font font, int x, int y) {
        List<SkillTrackingService.SkillStats> recentSkills =
            SkillTrackingService.INSTANCE.getRecentSkills(MAX_SKILLS_SHOWN);

        int panelHeight = calculatePanelHeight(recentSkills.size());

        // Background
        graphics.fill(x, y, x + PANEL_WIDTH, y + panelHeight, PANEL_BG);

        // Border
        drawBorder(graphics, x, y, PANEL_WIDTH, panelHeight, PANEL_BORDER);

        int textX = x + PANEL_PADDING;
        int textY = y + PANEL_PADDING;

        // Title
        var safeFont = Objects.requireNonNull(font);
        graphics.drawString(safeFont, "Skill Efficacy", textX, textY, TEXT_TITLE, false);
        textY += LINE_HEIGHT + 2;

        // Separator
        graphics.fill(x + 4, textY, x + PANEL_WIDTH - 4, textY + 1, PANEL_BORDER & 0x55FFFFFF);
        textY += 4;

        if (recentSkills.isEmpty()) {
            graphics.drawString(safeFont, "No skills used yet", textX, textY, TEXT_MUTED, false);
            return;
        }

        // Skill entries
        for (SkillTrackingService.SkillStats skill : recentSkills) {
            renderSkillEntry(graphics, font, textX, textY, skill);
            textY += LINE_HEIGHT * 2 + 2;
        }
    }

    private static void renderSkillEntry(GuiGraphics graphics, Font font, int x, int y,
                                          SkillTrackingService.SkillStats skill) {
        var safeFont = Objects.requireNonNull(font);
        // Skill name
        String name = truncateName(skill.skillId(), 15);
        graphics.drawString(safeFont, "[" + name + "]", x, y, TEXT_NORMAL, false);

        // Hit rate
        float hitRate = skill.getHitRate();
        int hitColor = hitRate >= 0.7f ? TEXT_VALUE : hitRate >= 0.4f ? TEXT_WARNING : TEXT_DANGER;
        String hitText = String.format("Hit: %d/%d (%.0f%%)",
            skill.hits(), skill.uses(), hitRate * 100);
        graphics.drawString(safeFont, hitText, x + 100, y, hitColor, false);

        y += LINE_HEIGHT;

        // Damage
        String dmgText = String.format("Dmg: %.1f", skill.totalDamage());
        graphics.drawString(safeFont, dmgText, x + 10, y, TEXT_MUTED, false);

        // Cooldown or last use
        long lastUseMs = skill.lastUseMs();
        long elapsedSec = (System.currentTimeMillis() - lastUseMs) / 1000;
        String timeText = elapsedSec < 60 ? elapsedSec + "s ago" : (elapsedSec / 60) + "m ago";
        graphics.drawString(safeFont, timeText, x + 100, y, TEXT_MUTED, false);
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

    private static int calculatePanelHeight(int skillCount) {
        int height = PANEL_PADDING * 2;
        height += LINE_HEIGHT + 2;  // Title
        height += 4;                // Separator

        if (skillCount == 0) {
            height += LINE_HEIGHT;  // "No skills" message
        } else {
            height += skillCount * (LINE_HEIGHT * 2 + 2);
        }

        return height;
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
