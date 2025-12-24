package com.devmod.client.overlay;

import com.devmod.DevMod;
import com.devmod.telemetry.boss.BossPhaseService;
import com.devmod.telemetry.boss.UnifiedBossDetector;
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

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * HUD Overlay for displaying boss phase information.
 *
 * Shows:
 * - Boss name and type
 * - Current phase (if tracked)
 * - HP percentage and threshold for next phase
 * - Phase duration timer
 *
 * Toggle: B key
 * Position: Below boss bar
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)

public class BossPhaseOverlay {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "boss_phase");

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

        // Position: centered, below boss bar
        int panelX = (screenWidth - PANEL_WIDTH) / 2;
        int panelY = 32; // Below vanilla boss bar

        renderBossPanel(graphics, font, panelX, panelY);
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

    private static void renderBossPanel(GuiGraphics graphics, Font font, int x, int y) {
        var safeFont = Objects.requireNonNull(font);
        int panelHeight = calculatePanelHeight();

        // Background
        graphics.fill(x, y, x + PANEL_WIDTH, y + panelHeight, PANEL_BG);

        // Border
        drawBorder(graphics, x, y, PANEL_WIDTH, panelHeight, PANEL_BORDER);

        int textX = x + PANEL_PADDING;
        int textY = y + PANEL_PADDING;

        // Title
        graphics.drawString(safeFont, "Boss Analysis", textX, textY, TEXT_TITLE, false);
        textY += LINE_HEIGHT + 2;

        // Separator
        graphics.fill(x + 4, textY, x + PANEL_WIDTH - 4, textY + 1, PANEL_BORDER & 0x55FFFFFF);
        textY += 4;

        // Boss name
        graphics.drawString(safeFont, cachedBossName, textX, textY, TEXT_NORMAL, false);
        textY += LINE_HEIGHT;

        // HP Bar visual
        int barWidth = PANEL_WIDTH - PANEL_PADDING * 2;
        int barHeight = 6;
        int barX = textX;
        int barY = textY;

        // Background
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);

        // HP fill
        int fillWidth = (int) (barWidth * cachedBossHpPercent);
        int hpColor = getHpColor(cachedBossHpPercent);
        graphics.fill(barX, barY, barX + fillWidth, barY + barHeight, hpColor);

        // Border
        drawBorder(graphics, barX, barY, barWidth, barHeight, 0xFF555555);
        textY += barHeight + 4;

        // HP percentage
        String hpText = String.format("HP: %.1f%%", cachedBossHpPercent * 100);
        int hpTextColor = cachedBossHpPercent > 0.5 ? TEXT_VALUE :
                          cachedBossHpPercent > 0.25 ? TEXT_WARNING : TEXT_DANGER;
        graphics.drawString(safeFont, hpText, textX, textY, hpTextColor, false);
        textY += LINE_HEIGHT;

        // Phase info (if tracked)
        LivingEntity boss = cachedBoss;
        if (boss != null) {
            Optional<String> phase = BossPhaseService.INSTANCE.getCurrentPhase(boss.getUUID());
            if (phase.isPresent()) {
                graphics.drawString(safeFont, "Phase: " + phase.get(), textX, textY, TEXT_VALUE, false);
                textY += LINE_HEIGHT;
            }
        }

        // Phase thresholds hint
        String thresholdHint = getPhaseThresholdHint(cachedBossHpPercent);
        if (!thresholdHint.isEmpty()) {
            graphics.drawString(safeFont, thresholdHint, textX, textY, TEXT_MUTED, false);
        }
    }

    private static int calculatePanelHeight() {
        int height = PANEL_PADDING * 2;
        height += LINE_HEIGHT + 2;  // Title
        height += 4;                // Separator
        height += LINE_HEIGHT;      // Boss name
        height += 6 + 4;            // HP bar
        height += LINE_HEIGHT;      // HP percentage
        height += LINE_HEIGHT;      // Phase or threshold hint
        return height;
    }

    private static int getHpColor(float percent) {
        if (percent > 0.5f) {
            return 0xFF00FF00; // Green
        } else if (percent > 0.25f) {
            return 0xFFFFFF00; // Yellow
        } else {
            return 0xFFFF4444; // Red
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
