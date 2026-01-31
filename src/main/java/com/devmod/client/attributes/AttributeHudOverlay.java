package com.devmod.client.attributes;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

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
import com.devmod.attributes.AttributeLogEntry;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.overlay.OverlayTheme;
import com.devmod.util.I18n;

@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)

public class AttributeHudOverlay {
    public static final AttributeHudOverlay INSTANCE = new AttributeHudOverlay();

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "attribute_monitor");

    // === UI Colors (Impact UI Style) ===
    private static final int PANEL_BG = OverlayTheme.Attribute.PANEL_BG;
    private static final int PANEL_BORDER = OverlayTheme.Attribute.BORDER;
    private static final int BORDER_GLOW = OverlayTheme.Attribute.BORDER_GLOW;
    private static final int TITLE_COLOR = OverlayTheme.Attribute.TITLE;
    private static final int TEXT_WHITE = OverlayTheme.Attribute.TEXT;
    private static final int TEXT_GREEN = OverlayTheme.Attribute.VALUE_GREEN;
    private static final int TEXT_YELLOW = OverlayTheme.Attribute.VALUE_YELLOW;
    private static final int TEXT_RED = OverlayTheme.Attribute.VALUE_RED;
    private static final int TEXT_GRAY = OverlayTheme.Attribute.VALUE_GRAY;
    private static final int TEXT_ORANGE = OverlayTheme.Attribute.VALUE_ORANGE;

    // === Layout ===
    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_MARGIN = 10;
    private static final int PADDING = 6;
    private static final int LINE_HEIGHT = 11;
    private static final int SECTION_GAP = 8;

    private AttributeHudOverlay() {}

    /**
     * Registers the GUI layer.
     */
    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            Objects.requireNonNull(VanillaGuiLayers.CROSSHAIR),
            Objects.requireNonNull(LAYER_ID),
            AttributeHudOverlay::render
        );
    }

    /**
     * Renders the HUD overlay.
     * Called every frame when the layer is active.
     */
    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!AttributeMonitoringSystem.INSTANCE.isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        // Update UIScaleManager for responsive scaling
        UIScaleManager.update();

        Font font = Objects.requireNonNull(mc.font);
        int screenWidth = mc.getWindow().getGuiScaledWidth();

        // Scaled layout values
        int sPanelWidth = UIScaleManager.scale(PANEL_WIDTH);
        int panelWidth = Math.min(sPanelWidth, UIScaleManager.getSafeWidth());
        int sPanelMargin = UIScaleManager.scale(PANEL_MARGIN);
        int sPadding = UIScaleManager.scale(PADDING);
        int sLineHeight = UIScaleManager.scale(LINE_HEIGHT);
        int sSectionGap = UIScaleManager.scale(SECTION_GAP);

        // Calculate dynamic height
        int panelHeight = calculatePanelHeight(sLineHeight, sPadding, sSectionGap);

        // Panel position (right side)
        int panelX = screenWidth - panelWidth - sPanelMargin;
        int panelY = sPanelMargin;
        int safeLeft = UIScaleManager.getSafeLeft();
        int safeRight = UIScaleManager.getSafeRight();
        int safeTop = UIScaleManager.getSafeTop();
        int safeBottom = UIScaleManager.getSafeBottom();
        panelX = Math.max(safeLeft, Math.min(panelX, safeRight - panelWidth));
        panelY = Math.max(safeTop, Math.min(panelY, safeBottom - panelHeight));

        // === BACKGROUND ===
        renderBackground(graphics, panelX, panelY, panelWidth, panelHeight);

        // === CONTENT ===
        int y = panelY + sPadding;
        int textX = panelX + sPadding;
        int contentWidth = panelWidth - sPadding * 2;

        // TITLE
        String title = truncateToWidth(font, I18n.translate("devmod.attribute_monitor.title").getString(), contentWidth);
        UIScaleManager.drawScaledString(graphics, font, title, textX, y, TITLE_COLOR, false);
        y += sLineHeight + UIScaleManager.scale(2);

        // Separator line
        graphics.fill(textX, y, textX + contentWidth, y + 1, PANEL_BORDER);
        y += sSectionGap;

        // === TARGET PRIMARIO ===
        TrackedEntity target = AttributeMonitoringSystem.INSTANCE.getPrimaryTarget();
        if (target != null && target.isValid()) {
            y = renderTargetSection(graphics, font, textX, y, contentWidth, target, sLineHeight, sSectionGap);
        } else {
            String noTarget = truncateToWidth(font, I18n.translate("devmod.attribute_monitor.no_target").getString(), contentWidth);
            UIScaleManager.drawScaledString(graphics, font, noTarget, textX, y, TEXT_GRAY, false);
            y += sLineHeight + sSectionGap;
        }

        // Separator
        graphics.fill(textX, y, textX + contentWidth, y + 1,
            OverlayTheme.withAlpha(PANEL_BORDER, DesignTokens.Alpha.A47));
        y += sSectionGap;

        // === TRACKED ENTITIES ===
        y = renderTrackedListSection(graphics, font, textX, y, contentWidth, sLineHeight, sSectionGap);

        // Separator
        graphics.fill(textX, y, textX + contentWidth, y + 1,
            OverlayTheme.withAlpha(PANEL_BORDER, DesignTokens.Alpha.A47));
        y += sSectionGap;

        // === LOG HISTORY ===
        renderLogSection(graphics, font, textX, y, contentWidth, sLineHeight);
    }

    private static int calculatePanelHeight(int sLineHeight, int sPadding, int sSectionGap) {
        int height = sPadding * 2 + sLineHeight + UIScaleManager.scale(2) + sSectionGap; // Title

        TrackedEntity target = AttributeMonitoringSystem.INSTANCE.getPrimaryTarget();
        if (target != null && target.isValid()) {
            height += sLineHeight * 9 + sSectionGap; // Target section
        } else {
            height += sLineHeight + sSectionGap;
        }

        height += sSectionGap; // Separator

        // Tracked list (max 5)
        int trackedCount = Math.min(AttributeMonitoringSystem.INSTANCE.getTrackedCount(), 5);
        height += sLineHeight + (sLineHeight * trackedCount) + sSectionGap;

        height += sSectionGap; // Separator

        // Log (max 8 entries)
        int logCount = Math.min(AttributeMonitoringSystem.INSTANCE.getLogHistory().size(), 8);
        height += sLineHeight + (sLineHeight * Math.max(logCount, 1)) + sSectionGap;

        return height;
    }

    private static int renderTargetSection(GuiGraphics graphics, @Nonnull Font font, int x, int y, int width, TrackedEntity target, int sLineHeight, int sSectionGap) {
        // Target name
        String nameStr = "\u00A7f" + target.getEntityName();
        String losTag = target.hasLineOfSight()
            ? I18n.translate("devmod.attribute_monitor.los").getString()
            : I18n.translate("devmod.attribute_monitor.blocked").getString();
        nameStr += " " + losTag;
        nameStr = truncateToWidth(font, nameStr, width);
        UIScaleManager.drawScaledString(graphics, font, nameStr, x, y, TEXT_WHITE, false);
        y += sLineHeight;

        // Health bar
        float healthPercent = target.getHealthPercent();
        int healthColor = healthPercent > 50 ? TEXT_GREEN : (healthPercent > 25 ? TEXT_YELLOW : TEXT_RED);
        String healthStr = I18n.translate("devmod.attribute_monitor.health",
            target.getCurrentHealth(), target.getMaxHealth(), healthPercent).getString();
        healthStr = truncateToWidth(font, healthStr, width);
        UIScaleManager.drawScaledString(graphics, font, healthStr, x, y, healthColor, false);
        y += sLineHeight;

        // Graphical health bar
        int barWidth = width - 4;
        int barHeight = UIScaleManager.scale(4);
        int barX = x + 2;
        graphics.fill(barX, y, barX + barWidth, y + barHeight, OverlayTheme.Progress.BG);
        int filledWidth = (int) (barWidth * (healthPercent / 100f));
        graphics.fill(barX, y, barX + filledWidth, y + barHeight, healthColor); // Fill
        y += barHeight + UIScaleManager.scale(4);

        // Armor
        String armorStr = I18n.translate("devmod.attribute_monitor.armor",
            target.getArmorValue(), target.getArmorToughness()).getString();
        armorStr = truncateToWidth(font, armorStr, width);
        UIScaleManager.drawScaledString(graphics, font, armorStr, x, y, TEXT_GRAY, false);
        y += sLineHeight;

        // Attack stats
        String attackStr = I18n.translate("devmod.attribute_monitor.attack",
            target.getAttackDamage(), target.getAttackSpeed()).getString();
        attackStr = truncateToWidth(font, attackStr, width);
        UIScaleManager.drawScaledString(graphics, font, attackStr, x, y, TEXT_ORANGE, false);
        y += sLineHeight;

        // Movement
        String moveStr = I18n.translate("devmod.attribute_monitor.movement",
            target.getMovementSpeed(), target.getKnockbackResistance() * 100).getString();
        moveStr = truncateToWidth(font, moveStr, width);
        UIScaleManager.drawScaledString(graphics, font, moveStr, x, y, TEXT_GRAY, false);
        y += sLineHeight;

        // Distance
        double dist = target.getDistanceToPlayer();
        String distStr = I18n.translate("devmod.attribute_monitor.distance", dist).getString();
        distStr = truncateToWidth(font, distStr, width);
        UIScaleManager.drawScaledString(graphics, font, distStr, x, y, TEXT_GRAY, false);
        y += sLineHeight;

        // Pehkui (if present)
        if (target.hasPehkuiModification()) {
            Float scale = target.getPehkuiScale();
            String scaleStr = I18n.translate("devmod.attribute_monitor.pehkui_scale", scale != null ? scale : 1f).getString();
            scaleStr = truncateToWidth(font, scaleStr, width);
            UIScaleManager.drawScaledString(graphics, font, scaleStr, x, y, OverlayTheme.Attribute.SCALE, false);
            y += sLineHeight;
        }

        // Health delta
        float delta = target.getHealthDelta();
        if (Math.abs(delta) > 0.1f) {
            String deltaKey = delta > 0
                ? "devmod.attribute_monitor.health_delta.positive"
                : "devmod.attribute_monitor.health_delta.negative";
            String deltaStr = I18n.translate(deltaKey, delta).getString();
            deltaStr = truncateToWidth(font, deltaStr, width);
            UIScaleManager.drawScaledString(graphics, font, deltaStr, x, y, delta > 0 ? TEXT_GREEN : TEXT_RED, false);
            y += sLineHeight;
        }

        return y + sSectionGap;
    }

    private static int renderTrackedListSection(GuiGraphics graphics, @Nonnull Font font, int x, int y, int width, int sLineHeight, int sSectionGap) {
        List<TrackedEntity> tracked = AttributeMonitoringSystem.INSTANCE.getTrackedEntities();

        String header = I18n.translate("devmod.attribute_monitor.tracked_entities", tracked.size()).getString();
        header = truncateToWidth(font, header, width);
        UIScaleManager.drawScaledString(graphics, font, header, x, y, TEXT_YELLOW, false);
        y += sLineHeight;

        if (tracked.isEmpty()) {
            String noneText = truncateToWidth(font, I18n.translate("devmod.attribute_monitor.none").getString(), width - UIScaleManager.scale(4));
            UIScaleManager.drawScaledString(graphics, font, noneText, x + UIScaleManager.scale(4), y, TEXT_GRAY, false);
            y += sLineHeight;
        } else {
            TrackedEntity primary = AttributeMonitoringSystem.INSTANCE.getPrimaryTarget();
            int shown = 0;
            for (TrackedEntity entity : tracked) {
                if (shown >= 5) break;

                boolean isPrimary = entity == primary;
                String prefix = isPrimary ? "\u00A7b> " : "\u00A77  ";
                String losIndicator = entity.hasLineOfSight() ? "\u00A7a●" : "\u00A7c●";

                String entryStr = String.format("%s%s %s \u00A77(%.0f%%)",
                    prefix, losIndicator, entity.getEntityName(), entity.getHealthPercent());
                entryStr = truncateToWidth(font, entryStr, width);
                UIScaleManager.drawScaledString(graphics, font, entryStr, x, y, TEXT_WHITE, false);
                y += sLineHeight;
                shown++;
            }

            if (tracked.size() > 5) {
                String moreText = I18n.translate("devmod.attribute_monitor.more", tracked.size() - 5).getString();
                moreText = truncateToWidth(font, moreText, width);
                UIScaleManager.drawScaledString(graphics, font, moreText, x, y, TEXT_GRAY, false);
                y += sLineHeight;
            }
        }

        return y + sSectionGap;
    }

    private static void renderLogSection(GuiGraphics graphics, @Nonnull Font font, int x, int y, int width, int sLineHeight) {
        List<AttributeLogEntry> logs = AttributeMonitoringSystem.INSTANCE.getLogHistory();

        String logTitle = truncateToWidth(font, I18n.translate("devmod.attribute_monitor.log_history").getString(), width);
        UIScaleManager.drawScaledString(graphics, font, logTitle, x, y, TEXT_GRAY, false);
        y += sLineHeight;

        if (logs.isEmpty()) {
            String noneText = truncateToWidth(font, I18n.translate("devmod.attribute_monitor.log.none").getString(), width - UIScaleManager.scale(4));
            UIScaleManager.drawScaledString(graphics, font, noneText, x + UIScaleManager.scale(4), y,
                OverlayTheme.Attribute.EMPTY_LOG, false);
        } else {
            int shown = 0;
            for (AttributeLogEntry log : logs) {
                if (shown >= 8) break;

                // Calculate alpha for fade
                float alpha = log.getAlpha(30000); // 30 secondi max
                if (alpha < 0.1f) continue;

                int color = applyAlpha(log.type().getColor(), alpha);
                String timeStr = I18n.translate("devmod.attribute_monitor.log.age", log.getFormattedAge()).getString();
                String fullStr = timeStr + log.getFormattedMessage();

                // Truncate if too long
                fullStr = truncateToWidth(font, fullStr, width);
                UIScaleManager.drawScaledString(graphics, font, fullStr, x, y, color, false);
                y += sLineHeight;
                shown++;
            }
        }
    }

    private static String truncateToWidth(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (maxWidth <= 0 || UIScaleManager.getScaledStringWidth(font, text) <= maxWidth) {
            return text;
        }
        int ellipsisWidth = UIScaleManager.getScaledStringWidth(font, "...");
        int allowed = Math.max(0, maxWidth - ellipsisWidth);
        if (allowed <= 0) {
            return "...";
        }
        float textScale = UIScaleManager.getTextScale();
        int allowedUnscaled = textScale > 0.0f ? Math.max(0, Math.round(allowed / textScale)) : allowed;
        return font.plainSubstrByWidth(text, allowedUnscaled) + "...";
    }

    private static void renderBackground(GuiGraphics graphics, int x, int y, int width, int height) {
        // Outer glow (Impact UI style)
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, BORDER_GLOW);

        // Background
        graphics.fill(x, y, x + width, y + height, PANEL_BG);

        // Border
        graphics.fill(x, y, x + width, y + 1, PANEL_BORDER); // Top
        graphics.fill(x, y + height - 1, x + width, y + height, PANEL_BORDER); // Bottom
        graphics.fill(x, y, x + 1, y + height, PANEL_BORDER); // Left
        graphics.fill(x + width - 1, y, x + width, y + height, PANEL_BORDER); // Right
    }

    private static int applyAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        return (a << 24) | (color & DesignTokens.Mask.RGB);
    }
}
