package com.devmod.client.overlay;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.devmod.DevMod;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.overlay.OverlayTheme;
import com.devmod.telemetry.boss.BossPhaseService;
import com.devmod.telemetry.boss.UnifiedBossDetector;

@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)

public class BossPhaseOverlay {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "boss_phase");

    // === UI Colors (delegating to OverlayTheme) ===
    private static final int PANEL_BG = OverlayTheme.Panel.BG_STANDARD;
    private static final int PANEL_BORDER = OverlayTheme.Combat.IMPACT;
    private static final int TEXT_TITLE = OverlayTheme.Text.TITLE;
    private static final int TEXT_NORMAL = OverlayTheme.Text.PRIMARY;
    private static final int TEXT_VALUE = OverlayTheme.Text.VALUE;
    private static final int TEXT_WARNING = OverlayTheme.Text.WARNING;
    private static final int TEXT_DANGER = OverlayTheme.Text.DANGER;
    private static final int TEXT_MUTED = OverlayTheme.Text.MUTED;

    // === Dimensions ===
    private static final int PANEL_WIDTH = 200;
    private static final int PANEL_PADDING = 6;
    private static final int LINE_HEIGHT = 10;

    // === State ===
    private static boolean enabled = false;
    private static long lastBossCheckMs = 0;
    private static final long BOSS_CHECK_INTERVAL_MS = 500;

    @Nullable
    private static LivingEntity cachedBoss = null;
    private static String cachedBossName = "";
    private static float cachedBossHpPercent = 1.0f;

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            Objects.requireNonNull(VanillaGuiLayers.BOSS_OVERLAY),
            Objects.requireNonNull(LAYER_ID),
            BossPhaseOverlay::render
        );
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        UIScaleManager.update();
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;

        // Update boss cache periodically
        long now = System.currentTimeMillis();
        if (now - lastBossCheckMs > BOSS_CHECK_INTERVAL_MS) {
            updateBossCache(mc);
            lastBossCheckMs = now;
        }

        LivingEntity boss = cachedBoss;
        if (boss == null || !boss.isAlive()) {
            cachedBoss = null;
            return;
        }

        // Update HP
        cachedBossHpPercent = boss.getHealth() / boss.getMaxHealth();

        Font font = mc.font;
        int screenWidth = graphics.guiWidth();

        // Scale dimensions
        int sPanelWidth = UIScaleManager.scale(PANEL_WIDTH);
        int panelWidth = Math.min(sPanelWidth, UIScaleManager.getSafeWidth());
        int sPanelPadding = UIScaleManager.scale(PANEL_PADDING);
        int sLineHeight = UIScaleManager.getScaledLineHeight(font, LINE_HEIGHT);
        int panelHeight = calculatePanelHeight(sPanelPadding, sLineHeight);

        // Position: centered, below boss bar
        int panelX = (screenWidth - panelWidth) / 2;
        int panelY = UIScaleManager.scale(32); // Below vanilla boss bar
        int safeLeft = UIScaleManager.getSafeLeft();
        int safeRight = UIScaleManager.getSafeRight();
        int safeTop = UIScaleManager.getSafeTop();
        int safeBottom = UIScaleManager.getSafeBottom();
        panelX = Math.max(safeLeft, Math.min(panelX, safeRight - panelWidth));
        panelY = Math.max(safeTop, Math.min(panelY, safeBottom - panelHeight));

        renderBossPanel(graphics, font, panelX, panelY, panelWidth, sPanelPadding, sLineHeight);
    }

    private static void updateBossCache(Minecraft mc) {
        var player = mc.player;
        var level = mc.level;
        if (level == null || player == null) return;

        // Search for boss entities near player (64 block radius)
        AABB searchBox = Objects.requireNonNull(player.getBoundingBox().inflate(64));
        List<LivingEntity> nearbyEntities = level.getEntitiesOfClass(
            LivingEntity.class, searchBox,
            e -> isBossEntity(e) && e.isAlive()
        );

        if (!nearbyEntities.isEmpty()) {
            // Pick closest boss
            cachedBoss = nearbyEntities.stream()
                .min((a, b) -> Double.compare(
                    a.distanceToSqr(player),
                    b.distanceToSqr(player)
                ))
                .orElse(null);

            LivingEntity boss = cachedBoss;
            if (boss != null) {
                cachedBossName = boss.getName().getString();
                cachedBossHpPercent = boss.getHealth() / boss.getMaxHealth();
            }
        } else {
            cachedBoss = null;
        }
    }

    private static boolean isBossEntity(LivingEntity entity) {
        // Use unified boss detection logic for consistency with telemetry
        return UnifiedBossDetector.INSTANCE.isBoss(entity);
    }

    private static void renderBossPanel(GuiGraphics graphics, Font font, int x, int y,
                                         int sPanelWidth, int sPanelPadding, int sLineHeight) {
        var safeFont = Objects.requireNonNull(font);
        int panelHeight = calculatePanelHeight(sPanelPadding, sLineHeight);
        int contentWidth = Math.max(0, sPanelWidth - sPanelPadding * 2);

        // Background
        graphics.fill(x, y, x + sPanelWidth, y + panelHeight, PANEL_BG);

        // Border
        drawBorder(graphics, x, y, sPanelWidth, panelHeight, PANEL_BORDER);

        int textX = x + sPanelPadding;
        int textY = y + sPanelPadding;

        // Title
        String title = truncateToWidth(safeFont, "Boss Analysis", contentWidth);
        UIScaleManager.drawScaledString(graphics, safeFont, title, textX, textY, TEXT_TITLE, false);
        textY += sLineHeight + UIScaleManager.scale(2);

        // Separator
        graphics.fill(x + UIScaleManager.scale(4), textY, x + sPanelWidth - UIScaleManager.scale(4), textY + 1, OverlayTheme.Border.divider(PANEL_BORDER));
        textY += UIScaleManager.scale(4);

        // Boss name
        String bossName = truncateToWidth(safeFont, cachedBossName, contentWidth);
        UIScaleManager.drawScaledString(graphics, safeFont, bossName, textX, textY, TEXT_NORMAL, false);
        textY += sLineHeight;

        // HP Bar visual
        int barWidth = sPanelWidth - sPanelPadding * 2;
        int barHeight = UIScaleManager.scale(6);
        int barX = textX;
        int barY = textY;

        // Background
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, OverlayTheme.Progress.BG);

        // HP fill
        int fillWidth = (int) (barWidth * cachedBossHpPercent);
        int hpColor = getHpColor(cachedBossHpPercent);
        graphics.fill(barX, barY, barX + fillWidth, barY + barHeight, hpColor);

        // Border
        drawBorder(graphics, barX, barY, barWidth, barHeight, OverlayTheme.Border.MUTED);
        textY += barHeight + UIScaleManager.scale(4);

        // HP percentage
        String hpText = String.format("HP: %.1f%%", cachedBossHpPercent * 100);
        int hpTextColor = cachedBossHpPercent > 0.5 ? TEXT_VALUE :
                          cachedBossHpPercent > 0.25 ? TEXT_WARNING : TEXT_DANGER;
        hpText = truncateToWidth(safeFont, hpText, contentWidth);
        UIScaleManager.drawScaledString(graphics, safeFont, hpText, textX, textY, hpTextColor, false);
        textY += sLineHeight;

        // Phase info (if tracked)
        LivingEntity boss = cachedBoss;
        if (boss != null) {
            Optional<String> phase = BossPhaseService.INSTANCE.getCurrentPhase(boss.getUUID());
            if (phase.isPresent()) {
                String phaseText = truncateToWidth(safeFont, "Phase: " + phase.get(), contentWidth);
                UIScaleManager.drawScaledString(graphics, safeFont, phaseText, textX, textY, TEXT_VALUE, false);
                textY += sLineHeight;
            }
        }

        // Phase thresholds hint
        String thresholdHint = getPhaseThresholdHint(cachedBossHpPercent);
        if (!thresholdHint.isEmpty()) {
            thresholdHint = truncateToWidth(safeFont, thresholdHint, contentWidth);
            UIScaleManager.drawScaledString(graphics, safeFont, thresholdHint, textX, textY, TEXT_MUTED, false);
        }
    }

    private static String truncateToWidth(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (maxWidth <= 0 || font.width(text) <= maxWidth) {
            return text;
        }
        int ellipsisWidth = font.width("...");
        int allowed = Math.max(0, maxWidth - ellipsisWidth);
        if (allowed <= 0) {
            return "...";
        }
        return font.plainSubstrByWidth(text, allowed) + "...";
    }

    private static int calculatePanelHeight(int sPanelPadding, int sLineHeight) {
        int height = sPanelPadding * 2;
        height += sLineHeight + UIScaleManager.scale(2);  // Title
        height += UIScaleManager.scale(4);                // Separator
        height += sLineHeight;      // Boss name
        height += UIScaleManager.scale(6) + UIScaleManager.scale(4);  // HP bar
        height += sLineHeight;      // HP percentage
        height += sLineHeight;      // Phase or threshold hint
        return height;
    }

    private static int getHpColor(float percent) {
        if (percent > 0.5f) {
            return TEXT_VALUE;
        } else if (percent > 0.25f) {
            return TEXT_WARNING;
        } else {
            return TEXT_DANGER;
        }
    }

    private static String getPhaseThresholdHint(float hpPercent) {
        // Common boss phase thresholds
        if (hpPercent > 0.75f) {
            return "Next phase: ~75% HP";
        } else if (hpPercent > 0.50f) {
            return "Next phase: ~50% HP";
        } else if (hpPercent > 0.25f) {
            return "Next phase: ~25% HP";
        } else {
            return "Final phase";
        }
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
