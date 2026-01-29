package com.devmod.client.overlay;

import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.client.ui.overlay.OverlayTheme;
import com.devmod.combat.HitHelper.BodyPart;

/**
 * Combat Recap Screen - Visual analytics for combat sessions.
 * Shows damage statistics, DPS graph, body part distribution, and top targets.
 */
@OnlyIn(Dist.CLIENT)
public class CombatRecapScreen extends Screen {

    // === Layout Constants ===
    private static final int PANEL_PADDING = 16;
    private static final int SECTION_GAP = 12;
    private static final int LINE_HEIGHT = 14;

    // === Colors ===
    private static final int BG_COLOR = OverlayTheme.CombatRecap.BG;
    private static final int PANEL_BG = OverlayTheme.CombatRecap.PANEL_BG;
    private static final int BORDER_COLOR = OverlayTheme.Border.ACCENT;
    private static final int ACCENT_COLOR = OverlayTheme.CombatRecap.ACCENT;
    private static final int TEXT_PRIMARY = OverlayTheme.CombatRecap.TEXT_PRIMARY;
    private static final int TEXT_SECONDARY = OverlayTheme.CombatRecap.TEXT_SECONDARY;
    private static final int TEXT_HIGHLIGHT = OverlayTheme.CombatRecap.TEXT_HIGHLIGHT;

    // === Body Part Colors ===
    private static final int COLOR_HEAD = OverlayTheme.BodyPart.HEAD;
    private static final int COLOR_BODY = OverlayTheme.BodyPart.BODY;
    private static final int COLOR_ARMS = OverlayTheme.BodyPart.ARMS;
    private static final int COLOR_LEGS = OverlayTheme.BodyPart.LEGS;

    // === Bar Colors ===
    private static final int BAR_DAMAGE = OverlayTheme.CombatRecap.BAR_DAMAGE;
    private static final int BAR_CRIT = OverlayTheme.CombatRecap.BAR_CRIT;
    private static final int BAR_HEADSHOT = OverlayTheme.CombatRecap.BAR_HEADSHOT;
    private static final int BAR_DPS = OverlayTheme.CombatRecap.BAR_DPS;

    // === Animation ===
    private long openTime;
    private static final long ANIM_DURATION = 400;

    // === Data Cache ===
    private final CombatSessionTracker tracker;

    public CombatRecapScreen() {
        super(Component.translatable("devmod.screen.combat_recap"));
        this.tracker = CombatSessionTracker.INSTANCE;
        this.openTime = System.currentTimeMillis();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Calculate animation progress
        float animProgress = getAnimProgress();

        // Background with fade
        int bgAlpha = (int) (DesignTokens.Alpha.A94 * animProgress);
        graphics.fill(0, 0, width, height, OverlayTheme.withAlpha(BG_COLOR, bgAlpha));

        // Main content panel
        int panelWidth = Math.min(600, width - 40);
        int panelHeight = Math.min(450, height - 40);
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;

        // Slide-in animation
        int offsetY = (int) ((1 - animProgress) * 50);
        panelY += offsetY;

        // Panel background with glow effect
        renderPanelBackground(graphics, panelX, panelY, panelWidth, panelHeight, animProgress);

        // Content
        if (animProgress > 0.3f) {
            float contentAlpha = (animProgress - 0.3f) / 0.7f;
            renderContent(graphics, panelX, panelY, panelWidth, panelHeight, contentAlpha);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private float getAnimProgress() {
        long elapsed = System.currentTimeMillis() - openTime;
        return Math.min(1f, elapsed / (float) ANIM_DURATION);
    }

    private void renderPanelBackground(GuiGraphics graphics, int x, int y, int w, int h, float alpha) {
        int bgAlpha = (int) (DesignTokens.Alpha.A88 * alpha);
        int borderAlpha = (int) (DesignTokens.Alpha.A100 * alpha);

        // Main background
        graphics.fill(x, y, x + w, y + h, OverlayTheme.withAlpha(PANEL_BG, bgAlpha));

        // Glow border
        int glowColor = OverlayTheme.withAlpha(BORDER_COLOR, borderAlpha);
        graphics.fill(x - 1, y - 1, x + w + 1, y, glowColor);  // Top
        graphics.fill(x - 1, y + h, x + w + 1, y + h + 1, glowColor);  // Bottom
        graphics.fill(x - 1, y, x, y + h, glowColor);  // Left
        graphics.fill(x + w, y, x + w + 1, y + h, glowColor);  // Right

        // Inner accent line
        graphics.fill(x + 2, y + 2, x + w - 2, y + 3, withAlpha(BORDER_COLOR, alpha));
    }

    private void renderContent(GuiGraphics graphics, int panelX, int panelY, int panelW, int panelH,
                               float alpha) {
        int x = panelX + PANEL_PADDING;
        int y = panelY + PANEL_PADDING;
        int contentW = panelW - PANEL_PADDING * 2;

        // Title
        String title = "COMBAT RECAP";
        int titleWidth = font.width(title);
        graphics.drawString(font, title, x + (contentW - titleWidth) / 2, y,
            withAlpha(TEXT_HIGHLIGHT, alpha), false);
        y += LINE_HEIGHT + 8;

        // Session info line
        String sessionInfo = formatSessionInfo();
        graphics.drawString(font, sessionInfo, x + (contentW - font.width(sessionInfo)) / 2, y,
            withAlpha(TEXT_SECONDARY, alpha), false);
        y += LINE_HEIGHT + SECTION_GAP;

        // Divider
        graphics.fill(x, y, x + contentW, y + 1, withAlpha(OverlayTheme.CombatRecap.DIVIDER, alpha));
        y += SECTION_GAP;

        // Two-column layout
        int colWidth = (contentW - SECTION_GAP) / 2;
        int leftX = x;
        int rightX = x + colWidth + SECTION_GAP;

        // Left column: Stats
        int leftY = renderStatsSection(graphics, leftX, y, colWidth, alpha);

        // Right column: Body part pie chart
        int rightY = renderBodyPartChart(graphics, rightX, y, colWidth, alpha);

        y = Math.max(leftY, rightY) + SECTION_GAP;

        // DPS Timeline graph
        renderDpsGraph(graphics, x, y, contentW, 80, alpha);
        y += 80 + SECTION_GAP;

        // Top targets
        renderTopTargets(graphics, x, y, contentW, alpha);

        // Close hint
        String hint = "[ESC] Close";
        graphics.drawString(font, hint, panelX + panelW - font.width(hint) - PANEL_PADDING,
            panelY + panelH - LINE_HEIGHT - 8, withAlpha(TEXT_SECONDARY, alpha), false);
    }

    private String formatSessionInfo() {
        long durationMs = tracker.getSessionDurationMs();
        int seconds = (int) (durationMs / 1000);
        int minutes = seconds / 60;
        seconds = seconds % 60;

        if (minutes > 0) {
            return String.format("Duration: %dm %ds | %d hits", minutes, seconds, tracker.getTotalHits());
        }
        return String.format("Duration: %ds | %d hits", seconds, tracker.getTotalHits());
    }

    private int renderStatsSection(GuiGraphics graphics, int x, int y, int width, float alpha) {
        // Section title
        graphics.drawString(font, "Statistics", x, y, withAlpha(ACCENT_COLOR, alpha), false);
        y += LINE_HEIGHT + 4;

        // Stats bars
        y = renderStatBar(graphics, x, y, width, "Total Damage",
            String.format("%.1f", tracker.getTotalDamage()), BAR_DAMAGE, 1.0f, alpha);
        y = renderStatBar(graphics, x, y, width, "Average DPS",
            String.format("%.1f/s", tracker.getSessionDps()), BAR_DPS,
            Math.min(1f, tracker.getSessionDps() / 50f), alpha);
        y = renderStatBar(graphics, x, y, width, "Crit Rate",
            String.format("%.0f%%", tracker.getCritRate() * 100), BAR_CRIT,
            tracker.getCritRate(), alpha);
        y = renderStatBar(graphics, x, y, width, "Headshot Rate",
            String.format("%.0f%%", tracker.getHeadshotRate() * 100), BAR_HEADSHOT,
            tracker.getHeadshotRate(), alpha);

        // Best hit
        y += 4;
        graphics.drawString(font, "Best Hit:", x, y, withAlpha(TEXT_SECONDARY, alpha), false);
        String bestHit = String.format("%.1f on %s", tracker.getHighestHit(), tracker.getHighestHitTarget());
        graphics.drawString(font, bestHit, x + font.width("Best Hit: "), y,
            withAlpha(TEXT_HIGHLIGHT, alpha), false);

        return y + LINE_HEIGHT;
    }

    private int renderStatBar(GuiGraphics graphics, int x, int y, int width, String label,
                              String value, int color, float fill, float alpha) {
        // Label
        graphics.drawString(font, label, x, y, withAlpha(TEXT_PRIMARY, alpha), false);

        // Value (right-aligned)
        int valueWidth = font.width(value);
        graphics.drawString(font, value, x + width - valueWidth, y, withAlpha(color, alpha), false);
        y += LINE_HEIGHT;

        // Bar background
        int barWidth = width - 4;
        int barHeight = 6;
        graphics.fill(x, y, x + barWidth, y + barHeight, withAlpha(OverlayTheme.CombatRecap.BAR_BG, alpha));

        // Bar fill with animation
        float animFill = fill * getAnimProgress();
        int fillWidth = (int) (barWidth * animFill);
        if (fillWidth > 0) {
            graphics.fill(x, y, x + fillWidth, y + barHeight, withAlpha(color, alpha));

            // Glow effect on top
            int glowColor = withAlpha((color & DesignTokens.Mask.RGB) | (DesignTokens.Alpha.A50 << 24),
                alpha * 0.5f);
            graphics.fill(x, y, x + fillWidth, y + 2, glowColor);
        }

        return y + barHeight + 8;
    }

    private int renderBodyPartChart(GuiGraphics graphics, int x, int y, int width, float alpha) {
        // Section title
        graphics.drawString(font, "Body Part Distribution", x, y, withAlpha(ACCENT_COLOR, alpha), false);
        y += LINE_HEIGHT + 8;

        Map<BodyPart, Integer> distribution = tracker.getBodyPartDistribution();
        int total = distribution.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0) {
            graphics.drawString(font, "No hits recorded", x, y, withAlpha(TEXT_SECONDARY, alpha), false);
            return y + LINE_HEIGHT;
        }

        // Pie chart center
        int chartSize = Math.min(width - 80, 100);
        int chartX = x + chartSize / 2 + 10;
        int chartY = y + chartSize / 2;
        int radius = chartSize / 2;

        // Draw pie segments
        float startAngle = -90f;
        int[] colors = {COLOR_HEAD, COLOR_BODY, COLOR_ARMS, COLOR_LEGS};
        BodyPart[] parts = {BodyPart.HEAD, BodyPart.BODY, BodyPart.ARMS, BodyPart.LEGS};

        for (int i = 0; i < parts.length; i++) {
            int count = distribution.getOrDefault(parts[i], 0);
            if (count > 0) {
                float percentage = (float) count / total;
                float sweepAngle = percentage * 360f;
                renderPieSegment(graphics, chartX, chartY, radius, startAngle, sweepAngle,
                    withAlpha(colors[i], alpha));
                startAngle += sweepAngle;
            }
        }

        // Legend
        int legendX = x + chartSize + 20;
        int legendY = y;
        String[] labels = {"Head", "Body", "Arms", "Legs"};

        for (int i = 0; i < parts.length; i++) {
            int count = distribution.getOrDefault(parts[i], 0);
            float percentage = total > 0 ? (float) count / total * 100 : 0;

            // Color box
            graphics.fill(legendX, legendY + 2, legendX + 10, legendY + 12, withAlpha(colors[i], alpha));

            // Label and percentage
            String text = String.format("%s: %.0f%%", labels[i], percentage);
            graphics.drawString(font, text, legendX + 14, legendY + 2,
                withAlpha(TEXT_PRIMARY, alpha), false);

            legendY += LINE_HEIGHT;
        }

        return y + chartSize;
    }

    private void renderPieSegment(GuiGraphics graphics, int cx, int cy, int radius,
                                   float startAngle, float sweepAngle, int color) {
        // Simple pie segment rendering using filled triangles
        int segments = Math.max(4, (int) (sweepAngle / 10));
        float angleStep = sweepAngle / segments;

        for (int i = 0; i < segments; i++) {
            float angle1 = (float) Math.toRadians(startAngle + i * angleStep);
            float angle2 = (float) Math.toRadians(startAngle + (i + 1) * angleStep);

            int x1 = cx + (int) (Math.cos(angle1) * radius);
            int y1 = cy + (int) (Math.sin(angle1) * radius);
            int x2 = cx + (int) (Math.cos(angle2) * radius);
            int y2 = cy + (int) (Math.sin(angle2) * radius);

            // Draw filled triangle (cx,cy) -> (x1,y1) -> (x2,y2)
            // Using multiple small rectangles as approximation
            drawTriangle(graphics, cx, cy, x1, y1, x2, y2, color);
        }
    }

    private void drawTriangle(GuiGraphics graphics, int cx, int cy, int x1, int y1, int x2, int y2, int color) {
        // Simple approximation: draw lines radiating from center
        int steps = 10;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int px = (int) Mth.lerp(t, x1, x2);
            int py = (int) Mth.lerp(t, y1, y2);
            drawLine(graphics, cx, cy, px, py, color);
        }
    }

    private void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        // Bresenham-ish line drawing
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int steps = Math.max(dx, dy);
        if (steps == 0) {
            graphics.fill(x1, y1, x1 + 1, y1 + 1, color);
            return;
        }

        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int px = (int) Mth.lerp(t, x1, x2);
            int py = (int) Mth.lerp(t, y1, y2);
            graphics.fill(px, py, px + 1, py + 1, color);
        }
    }

    private void renderDpsGraph(GuiGraphics graphics, int x, int y, int width, int height, float alpha) {
        // Section title
        graphics.drawString(font, "DPS Timeline", x, y, withAlpha(ACCENT_COLOR, alpha), false);
        y += LINE_HEIGHT + 4;

        int graphHeight = height - LINE_HEIGHT - 4;

        // Graph background
        graphics.fill(x, y, x + width, y + graphHeight, withAlpha(OverlayTheme.CombatRecap.GRAPH_BG, alpha));

        // Grid lines
        for (int i = 1; i < 4; i++) {
            int lineY = y + (graphHeight * i / 4);
            graphics.fill(x, lineY, x + width, lineY + 1, withAlpha(OverlayTheme.CombatRecap.BAR_BG, alpha));
        }

        List<CombatSessionTracker.DpsSnapshot> timeline = tracker.getDpsTimeline();
        if (timeline.size() < 2) {
            String noData = "Not enough data";
            graphics.drawString(font, noData, x + (width - font.width(noData)) / 2,
                y + graphHeight / 2 - 4, withAlpha(TEXT_SECONDARY, alpha), false);
            return;
        }

        // Find max DPS for scaling
        float maxDps = timeline.stream().map(s -> s.dps()).max(Float::compare).orElse(1f);
        maxDps = Math.max(maxDps, 10f); // Minimum scale

        // Draw DPS line
        int prevX = -1, prevY = -1;
        for (int i = 0; i < timeline.size(); i++) {
            float t = i / (float) (timeline.size() - 1);
            int px = x + (int) (width * t);
            int py = y + graphHeight - (int) ((timeline.get(i).dps() / maxDps) * graphHeight);

            if (prevX >= 0) {
                // Draw line segment
                int lineColor = withAlpha(BAR_DPS, alpha);
                drawLine(graphics, prevX, prevY, px, py, lineColor);

                // Fill below line
                int fillColor = withAlpha((BAR_DPS & DesignTokens.Mask.RGB) | (DesignTokens.Alpha.A25 << 24),
                    alpha * 0.3f);
                for (int fx = prevX; fx < px; fx++) {
                    float ft = (fx - prevX) / (float) (px - prevX);
                    int fy = (int) Mth.lerp(ft, prevY, py);
                    graphics.fill(fx, fy, fx + 1, y + graphHeight, fillColor);
                }
            }

            prevX = px;
            prevY = py;
        }

        // Y-axis label
        String maxLabel = String.format("%.0f", maxDps);
        graphics.drawString(font, maxLabel, x + 2, y + 2, withAlpha(TEXT_SECONDARY, alpha), false);
    }

    private void renderTopTargets(GuiGraphics graphics, int x, int y, int width, float alpha) {
        graphics.drawString(font, "Top Targets", x, y, withAlpha(ACCENT_COLOR, alpha), false);
        y += LINE_HEIGHT + 4;

        List<CombatSessionTracker.TargetStats> targets = tracker.getTopTargets(3);

        if (targets.isEmpty()) {
            graphics.drawString(font, "No targets", x, y, withAlpha(TEXT_SECONDARY, alpha), false);
            return;
        }

        for (int i = 0; i < targets.size(); i++) {
            var target = targets.get(i);
            String rank = String.format("%d.", i + 1);
            String name = target.getName();
            String stats = String.format("%.1f dmg | %d hits | %.0f%% crit",
                target.getTotalDamage(), target.getHits(), target.getCritRate() * 100);

            int rankColor = i == 0 ? TEXT_HIGHLIGHT : TEXT_PRIMARY;
            graphics.drawString(font, rank, x, y, withAlpha(rankColor, alpha), false);
            graphics.drawString(font, name, x + 20, y, withAlpha(TEXT_PRIMARY, alpha), false);

            int statsWidth = font.width(stats);
            graphics.drawString(font, stats, x + width - statsWidth, y,
                withAlpha(TEXT_SECONDARY, alpha), false);

            y += LINE_HEIGHT;
        }
    }

    private int withAlpha(int color, float alpha) {
        int a = (int) (((color >> 24) & 0xFF) * alpha);
        return (a << 24) | (color & DesignTokens.Mask.RGB);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Opens the Combat Recap screen.
     */
    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) {
            mc.setScreen(new CombatRecapScreen());
        }
    }
}
