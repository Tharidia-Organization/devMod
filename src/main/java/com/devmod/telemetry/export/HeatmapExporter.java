package com.devmod.telemetry.export;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;

import com.devmod.telemetry.spatial.HeatmapService;
import com.devmod.util.ConfigPaths;
import com.devmod.util.PathSanitizer;

public class HeatmapExporter {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Image settings
    private static final int CELL_SIZE = 4; // Pixels per block
    private static final int PADDING = 20; // Border padding
    private static final int LEGEND_WIDTH = 100; // Width of legend bar

    // Color gradient for heatmap (cold to hot)
    private static final Color[] GRADIENT = {
        new Color(0, 0, 139),    // Dark blue (cold)
        new Color(0, 100, 255),  // Blue
        new Color(0, 200, 200),  // Cyan
        new Color(0, 255, 0),    // Green
        new Color(200, 255, 0),  // Yellow-green
        new Color(255, 255, 0),  // Yellow
        new Color(255, 165, 0),  // Orange
        new Color(255, 69, 0),   // Red-orange
        new Color(255, 0, 0),    // Red (hot)
        new Color(139, 0, 0)     // Dark red (very hot)
    };

    /**
     * Exports all heatmaps to PNG files.
     * @return Number of files exported
     */
    public static int exportAll() {
        int count = 0;
        String timestamp = LocalDateTime.now(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

        count += exportHeatmap("death", HeatmapService.INSTANCE.getDeathHeatmap(), timestamp);
        count += exportHeatmap("movement", HeatmapService.INSTANCE.getMovementHeatmap(), timestamp);
        count += exportHeatmap("stuck", HeatmapService.INSTANCE.getStuckHeatmap(), timestamp);
        count += exportHeatmap("camping", HeatmapService.INSTANCE.getCampingHeatmap(), timestamp);
        count += exportHeatmap("aggro_drop", HeatmapService.INSTANCE.getAggroDropHeatmap(), timestamp);
        count += exportHeatmap("kiting", HeatmapService.INSTANCE.getKitingHeatmap(), timestamp);
        count += exportHeatmap("choke_point", HeatmapService.INSTANCE.getChokePointHeatmap(), timestamp);
        count += exportHeatmap("invisible_collision", HeatmapService.INSTANCE.getInvisibleCollisionHeatmap(), timestamp);
        count += exportHeatmap("parkour_fall", HeatmapService.INSTANCE.getParkourFallHeatmap(), timestamp);

        LOGGER.info("[DevMod] Exported {} heatmap images", count);
        return count;
    }

    /**
     * Exports a single heatmap type to PNG files (one per room).
     * @return Number of files exported
     */
    public static int exportHeatmap(String name, Map<String, Map<BlockPos, Integer>> heatmap, String timestamp) {
        if (heatmap.isEmpty()) {
            return 0;
        }

        int count = 0;
        Path exportDir = ConfigPaths.getTelemetryExportDir().resolve("exports");

        try {
            Files.createDirectories(exportDir);
        } catch (IOException e) {
            LOGGER.error("[DevMod] Failed to create export directory: {}", e.getMessage());
            return 0;
        }

        for (Map.Entry<String, Map<BlockPos, Integer>> entry : heatmap.entrySet()) {
            String room = entry.getKey();
            Map<BlockPos, Integer> data = entry.getValue();

            if (data.isEmpty()) continue;

            @Nullable BufferedImage image = renderHeatmap(name, room, data);
            if (image == null) continue;

            // Sanitize room name for filename
            String safeRoom = room.replaceAll("[^a-zA-Z0-9_-]", "_");
            String filename = String.format("heatmap_%s_%s_%s.png", name, safeRoom, timestamp);
            Path outputPath = exportDir.resolve(filename);

            try {
                Path safePath = PathSanitizer.sanitizeForWrite(outputPath);
                if (safePath == null) {
                    LOGGER.warn("[DevMod] Skipping heatmap export; unsafe path: {}", outputPath);
                    continue;
                }
                ImageIO.write(image, "PNG", safePath.toFile());
                count++;
                LOGGER.debug("[DevMod] Exported heatmap: {}", filename);
            } catch (IOException e) {
                LOGGER.error("[DevMod] Failed to write heatmap image: {}", e.getMessage());
            }
        }

        return count;
    }

    /**
     * Renders a heatmap to a BufferedImage.
     */
    private static @Nullable BufferedImage renderHeatmap(String name, String room, Map<BlockPos, Integer> data) {
        if (data.isEmpty()) return null;

        // Find bounds
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        int maxCount = 0;

        for (Map.Entry<BlockPos, Integer> entry : data.entrySet()) {
            BlockPos pos = entry.getKey();
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
            maxCount = Math.max(maxCount, entry.getValue());
        }

        if (maxCount == 0) return null;

        int width = (maxX - minX + 1) * CELL_SIZE + PADDING * 2 + LEGEND_WIDTH;
        int height = (maxZ - minZ + 1) * CELL_SIZE + PADDING * 2 + 60; // Extra for title

        // Limit image size
        if (width > 4096 || height > 4096) {
            LOGGER.warn("[DevMod] Heatmap too large, limiting to 4096x4096");
            width = Math.min(width, 4096);
            height = Math.min(height, 4096);
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();

        // Enable anti-aliasing
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Background
        g.setColor(new Color(30, 30, 40));
        g.fillRect(0, 0, width, height);

        // Title
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        String title = String.format("Heatmap: %s | Room: %s", name.toUpperCase(Locale.ROOT), room);
        g.drawString(title, PADDING, 25);

        // Subtitle with bounds
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(Color.GRAY);
        String subtitle = String.format("Bounds: X[%d, %d] Z[%d, %d] | Max: %d", minX, maxX, minZ, maxZ, maxCount);
        g.drawString(subtitle, PADDING, 45);

        // Draw grid
        int offsetY = 55;
        g.setColor(new Color(50, 50, 60));
        for (int x = minX; x <= maxX; x += 16) {
            int px = (x - minX) * CELL_SIZE + PADDING;
            g.drawLine(px, offsetY, px, offsetY + (maxZ - minZ + 1) * CELL_SIZE);
        }
        for (int z = minZ; z <= maxZ; z += 16) {
            int py = (z - minZ) * CELL_SIZE + offsetY;
            g.drawLine(PADDING, py, PADDING + (maxX - minX + 1) * CELL_SIZE, py);
        }

        // Draw heatmap cells
        for (Map.Entry<BlockPos, Integer> entry : data.entrySet()) {
            BlockPos pos = entry.getKey();
            int count = entry.getValue();

            int px = (pos.getX() - minX) * CELL_SIZE + PADDING;
            int pz = (pos.getZ() - minZ) * CELL_SIZE + offsetY;

            // Map count to color (0-1 range, logarithmic for better visualization)
            double normalized = Math.log1p(count) / Math.log1p(maxCount);
            Color color = getGradientColor(normalized);

            g.setColor(color);
            g.fillRect(px, pz, CELL_SIZE, CELL_SIZE);
        }

        // Draw legend
        int legendX = width - LEGEND_WIDTH - PADDING + 10;
        int legendY = offsetY;
        int legendHeight = Math.min(200, height - offsetY - 40);

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g.drawString("Activity", legendX, legendY - 5);

        // Legend gradient
        for (int i = 0; i < legendHeight; i++) {
            double t = 1.0 - (double) i / legendHeight;
            g.setColor(getGradientColor(t));
            g.fillRect(legendX, legendY + i, 20, 1);
        }

        // Legend labels
        g.setColor(Color.WHITE);
        g.drawString(String.valueOf(maxCount), legendX + 25, legendY + 10);
        g.drawString("0", legendX + 25, legendY + legendHeight);

        // Border
        g.setColor(new Color(80, 80, 100));
        g.drawRect(PADDING - 1, offsetY - 1, (maxX - minX + 1) * CELL_SIZE + 2, (maxZ - minZ + 1) * CELL_SIZE + 2);

        g.dispose();
        return image;
    }

    /**
     * Gets a color from the gradient based on normalized value (0-1).
     */
    private static Color getGradientColor(double t) {
        t = Math.max(0, Math.min(1, t));

        // Map t to gradient index
        double scaled = t * (GRADIENT.length - 1);
        int idx1 = (int) scaled;
        int idx2 = Math.min(idx1 + 1, GRADIENT.length - 1);
        double frac = scaled - idx1;

        // Interpolate between colors
        Color c1 = GRADIENT[idx1];
        Color c2 = GRADIENT[idx2];

        int r = (int) (c1.getRed() + frac * (c2.getRed() - c1.getRed()));
        int g = (int) (c1.getGreen() + frac * (c2.getGreen() - c1.getGreen()));
        int b = (int) (c1.getBlue() + frac * (c2.getBlue() - c1.getBlue()));

        return new Color(r, g, b, 200); // Semi-transparent
    }

    /**
     * Gets the export directory path.
     */
    public static Path getExportDir() {
        return ConfigPaths.getTelemetryExportDir().resolve("exports");
    }
}
