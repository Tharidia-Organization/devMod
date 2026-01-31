package com.devmod.client.overlay;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import com.devmod.DevMod;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.overlay.OverlayTheme;

@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)

public class EntityDensityOverlay {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "entity_density");

    // === UI Colors (delegating to OverlayTheme) ===
    private static final int PANEL_BG = OverlayTheme.Panel.BG_STANDARD;
    private static final int PANEL_BORDER = OverlayTheme.Border.ACCENT;
    private static final int TEXT_TITLE = OverlayTheme.Text.TITLE;
    private static final int TEXT_VALUE = OverlayTheme.Text.VALUE;
    private static final int TEXT_WARNING = OverlayTheme.Text.WARNING;
    private static final int TEXT_DANGER = OverlayTheme.Text.DANGER;
    private static final int TEXT_MUTED = OverlayTheme.Text.MUTED;

    // === Dimensions ===
    private static final int PANEL_WIDTH = 180;
    private static final int PANEL_PADDING = 6;
    private static final int LINE_HEIGHT = 10;

    // === Configuration ===
    private static final int DENSITY_WARN_THRESHOLD = 20;
    private static final int DENSITY_DANGER_THRESHOLD = 40;
    private static final int SCAN_RADIUS = 32;

    // === State ===
    private static boolean enabled = false;
    private static long lastScanMs = 0;
    private static final long SCAN_INTERVAL_MS = 500;

    // Cached results
    private static String cachedRoomName = "Unknown";
    private static int cachedTotalEntities = 0;
    private static int cachedHostileCount = 0;
    private static int cachedPassiveCount = 0;
    private static int cachedPlayerCount = 0;
    private static int cachedOtherCount = 0;

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
            Objects.requireNonNull(VanillaGuiLayers.HOTBAR),
            Objects.requireNonNull(LAYER_ID),
            EntityDensityOverlay::render
        );
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        UIScaleManager.update();
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;

        // Update scan periodically
        long now = System.currentTimeMillis();
        if (now - lastScanMs > SCAN_INTERVAL_MS) {
            updateEntityScan(mc);
            lastScanMs = now;
        }

        Font font = mc.font;
        // Position: right side of screen to avoid FpsTracker overlap (5,5)-(145,90)
        int panelWidth = Math.min(UIScaleManager.scale(PANEL_WIDTH), UIScaleManager.getSafeWidth());
        int panelHeight = calculatePanelHeight();
        int panelX = mc.getWindow().getGuiScaledWidth() - panelWidth - UIScaleManager.scale(10);
        int panelY = UIScaleManager.scale(100); // Below FpsTracker area
        int safeLeft = UIScaleManager.getSafeLeft();
        int safeRight = UIScaleManager.getSafeRight();
        int safeTop = UIScaleManager.getSafeTop();
        int safeBottom = UIScaleManager.getSafeBottom();
        panelX = Math.max(safeLeft, Math.min(panelX, safeRight - panelWidth));
        panelY = Math.max(safeTop, Math.min(panelY, safeBottom - panelHeight));

        renderDensityPanel(graphics, font, panelX, panelY, panelWidth);
    }

    private static void updateEntityScan(Minecraft mc) {
        var player = mc.player;
        var level = mc.level;
        if (level == null || player == null) return;

        // Try to find current room
        cachedRoomName = "Open World";

        // Scan area around player
        AABB scanBox = Objects.requireNonNull(player.getBoundingBox().inflate(SCAN_RADIUS));
        List<Entity> entities = level.getEntities(player, scanBox);

        // Reset counts
        cachedHostileCount = 0;
        cachedPassiveCount = 0;
        cachedPlayerCount = 0;
        cachedOtherCount = 0;

        for (Entity entity : entities) {
            if (entity instanceof Monster) {
                cachedHostileCount++;
            } else if (entity instanceof Animal) {
                cachedPassiveCount++;
            } else if (entity instanceof Player) {
                cachedPlayerCount++;
            } else if (entity instanceof LivingEntity) {
                cachedOtherCount++;
            }
        }

        cachedTotalEntities = cachedHostileCount + cachedPassiveCount + cachedPlayerCount + cachedOtherCount;
    }

    private static void renderDensityPanel(GuiGraphics graphics, Font font, int x, int y, int panelWidth) {
        var safeFont = Objects.requireNonNull(font);
        int panelHeight = calculatePanelHeight();
        int panelPadding = UIScaleManager.scale(PANEL_PADDING);
        int lineHeight = UIScaleManager.scale(LINE_HEIGHT);

        // Background
        graphics.fill(x, y, x + panelWidth, y + panelHeight, PANEL_BG);

        // Border
        drawBorder(graphics, x, y, panelWidth, panelHeight, PANEL_BORDER);

        int textX = x + panelPadding;
        int textY = y + panelPadding;

        // Title
        UIScaleManager.drawScaledString(graphics, safeFont, "Entity Density", textX, textY, TEXT_TITLE, false);
        textY += lineHeight + 2;

        // Separator
        graphics.fill(x + 4, textY, x + panelWidth - 4, textY + 1,
            OverlayTheme.withAlpha(PANEL_BORDER, OverlayTheme.Alpha.GHOST));
        textY += 4;

        // Room name
        UIScaleManager.drawScaledString(graphics, safeFont, "Area: " + cachedRoomName, textX, textY, TEXT_MUTED, false);
        textY += lineHeight + 2;

        // Total count with color based on density
        int totalColor = getTotalColor(cachedTotalEntities);
        String totalText = String.format("Total: %d entities", cachedTotalEntities);
        UIScaleManager.drawScaledString(graphics, safeFont, totalText, textX, textY, totalColor, false);
        textY += lineHeight + 4;

        // Breakdown
        if (cachedHostileCount > 0) {
            UIScaleManager.drawScaledString(graphics, safeFont, String.format("  Hostile: %d", cachedHostileCount),
                textX, textY, TEXT_DANGER, false);
            textY += lineHeight;
        }

        if (cachedPassiveCount > 0) {
            UIScaleManager.drawScaledString(graphics, safeFont, String.format("  Passive: %d", cachedPassiveCount),
                textX, textY, TEXT_VALUE, false);
            textY += lineHeight;
        }

        if (cachedPlayerCount > 0) {
            UIScaleManager.drawScaledString(graphics, safeFont, String.format("  Players: %d", cachedPlayerCount),
                textX, textY, TEXT_TITLE, false);
            textY += lineHeight;
        }

        if (cachedOtherCount > 0) {
            UIScaleManager.drawScaledString(graphics, safeFont, String.format("  Other: %d", cachedOtherCount),
                textX, textY, TEXT_MUTED, false);
            textY += lineHeight;
        }

        // Warning message if high density
        if (cachedTotalEntities >= DENSITY_DANGER_THRESHOLD) {
            textY += 2;
            UIScaleManager.drawScaledString(graphics, safeFont, "HIGH DENSITY!", textX, textY, TEXT_DANGER, false);
        } else if (cachedTotalEntities >= DENSITY_WARN_THRESHOLD) {
            textY += 2;
            UIScaleManager.drawScaledString(graphics, safeFont, "Moderate density", textX, textY, TEXT_WARNING, false);
        }
    }

    private static int calculatePanelHeight() {
        int padding = UIScaleManager.scale(PANEL_PADDING);
        int lineHeight = UIScaleManager.scale(LINE_HEIGHT);
        int height = padding * 2;
        height += lineHeight + 2;  // Title
        height += 4;                // Separator
        height += lineHeight + 2;  // Room name
        height += lineHeight + 4;  // Total count

        // Category rows
        if (cachedHostileCount > 0) height += lineHeight;
        if (cachedPassiveCount > 0) height += lineHeight;
        if (cachedPlayerCount > 0) height += lineHeight;
        if (cachedOtherCount > 0) height += lineHeight;

        // Warning message
        if (cachedTotalEntities >= DENSITY_WARN_THRESHOLD) {
            height += lineHeight + 2;
        }

        return height;
    }

    private static int getTotalColor(int count) {
        if (count >= DENSITY_DANGER_THRESHOLD) {
            return TEXT_DANGER;
        } else if (count >= DENSITY_WARN_THRESHOLD) {
            return TEXT_WARNING;
        } else {
            return TEXT_VALUE;
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

    /**
     * Get the current entity counts for external use.
     */
    public static Map<String, Integer> getEntityCounts() {
        Map<String, Integer> counts = new HashMap<>();
        counts.put("total", cachedTotalEntities);
        counts.put("hostile", cachedHostileCount);
        counts.put("passive", cachedPassiveCount);
        counts.put("players", cachedPlayerCount);
        counts.put("other", cachedOtherCount);
        return counts;
    }
}
