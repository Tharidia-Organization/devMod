package com.frenkvs.devmod.hud;

import com.frenkvs.devmod.DevMod;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HUD Overlay for displaying entity density information.
 *
 * Shows:
 * - Current room name (if defined)
 * - Total entity count in room/area
 * - Breakdown by category (hostile, passive, players)
 * - Density warning if threshold exceeded
 *
 * Toggle: E key (configurable)
 * Position: Top-left corner
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)
@SuppressWarnings("null")
public class EntityDensityOverlay {

    private static final ResourceLocation LAYER_ID =
        ResourceLocation.fromNamespaceAndPath("devmod", "entity_density");

    // === UI Colors (consistent with ImpactHudOverlay) ===
    private static final int PANEL_BG = 0xCC1A1A2E;           // Dark blue 80% opacity
    private static final int PANEL_BORDER = 0xFF3D5AFE;       // Electric blue
    private static final int TEXT_TITLE = 0xFF00FFFF;         // Cyan
    private static final int TEXT_VALUE = 0xFF00FF00;         // Green
    private static final int TEXT_WARNING = 0xFFFFFF00;       // Yellow
    private static final int TEXT_DANGER = 0xFFFF4444;        // Red
    private static final int TEXT_MUTED = 0xFFAAAAAA;         // Gray

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
            VanillaGuiLayers.HOTBAR,
            LAYER_ID,
            EntityDensityOverlay::render
        );
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
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
        int panelX = mc.getWindow().getGuiScaledWidth() - PANEL_WIDTH - 10;
        int panelY = 100; // Below FpsTracker area

        renderDensityPanel(graphics, font, panelX, panelY);
    }

    private static void updateEntityScan(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;

        // Try to find current room
        cachedRoomName = "Open World";

        // Scan area around player
        AABB scanBox = mc.player.getBoundingBox().inflate(SCAN_RADIUS);
        List<Entity> entities = mc.level.getEntities(mc.player, scanBox);

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

    private static void renderDensityPanel(GuiGraphics graphics, Font font, int x, int y) {
        int panelHeight = calculatePanelHeight();

        // Background
        graphics.fill(x, y, x + PANEL_WIDTH, y + panelHeight, PANEL_BG);

        // Border
        drawBorder(graphics, x, y, PANEL_WIDTH, panelHeight, PANEL_BORDER);

        int textX = x + PANEL_PADDING;
        int textY = y + PANEL_PADDING;

        // Title
        graphics.drawString(font, "Entity Density", textX, textY, TEXT_TITLE, false);
        textY += LINE_HEIGHT + 2;

        // Separator
        graphics.fill(x + 4, textY, x + PANEL_WIDTH - 4, textY + 1, PANEL_BORDER & 0x55FFFFFF);
        textY += 4;

        // Room name
        graphics.drawString(font, "Area: " + cachedRoomName, textX, textY, TEXT_MUTED, false);
        textY += LINE_HEIGHT + 2;

        // Total count with color based on density
        int totalColor = getTotalColor(cachedTotalEntities);
        String totalText = String.format("Total: %d entities", cachedTotalEntities);
        graphics.drawString(font, totalText, textX, textY, totalColor, false);
        textY += LINE_HEIGHT + 4;

        // Breakdown
        if (cachedHostileCount > 0) {
            graphics.drawString(font, String.format("  Hostile: %d", cachedHostileCount),
                textX, textY, TEXT_DANGER, false);
            textY += LINE_HEIGHT;
        }

        if (cachedPassiveCount > 0) {
            graphics.drawString(font, String.format("  Passive: %d", cachedPassiveCount),
                textX, textY, TEXT_VALUE, false);
            textY += LINE_HEIGHT;
        }

        if (cachedPlayerCount > 0) {
            graphics.drawString(font, String.format("  Players: %d", cachedPlayerCount),
                textX, textY, TEXT_TITLE, false);
            textY += LINE_HEIGHT;
        }

        if (cachedOtherCount > 0) {
            graphics.drawString(font, String.format("  Other: %d", cachedOtherCount),
                textX, textY, TEXT_MUTED, false);
            textY += LINE_HEIGHT;
        }

        // Warning message if high density
        if (cachedTotalEntities >= DENSITY_DANGER_THRESHOLD) {
            textY += 2;
            graphics.drawString(font, "HIGH DENSITY!", textX, textY, TEXT_DANGER, false);
        } else if (cachedTotalEntities >= DENSITY_WARN_THRESHOLD) {
            textY += 2;
            graphics.drawString(font, "Moderate density", textX, textY, TEXT_WARNING, false);
        }
    }

    private static int calculatePanelHeight() {
        int height = PANEL_PADDING * 2;
        height += LINE_HEIGHT + 2;  // Title
        height += 4;                // Separator
        height += LINE_HEIGHT + 2;  // Room name
        height += LINE_HEIGHT + 4;  // Total count

        // Category rows
        if (cachedHostileCount > 0) height += LINE_HEIGHT;
        if (cachedPassiveCount > 0) height += LINE_HEIGHT;
        if (cachedPlayerCount > 0) height += LINE_HEIGHT;
        if (cachedOtherCount > 0) height += LINE_HEIGHT;

        // Warning message
        if (cachedTotalEntities >= DENSITY_WARN_THRESHOLD) {
            height += LINE_HEIGHT + 2;
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
