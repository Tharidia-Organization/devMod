package com.devmod.hologram.client.renderer;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;

import com.devmod.arena.registry.ArenaTemplate;

/**
 * Generates a 3D preview mesh from an ArenaTemplate specification.
 * Unlike HologramMesh which scans actual blocks, this generates geometry
 * from the template configuration.
 *
 * <p>Renders floor, walls outline, and hazard zones with color coding.
 */
public class ArenaPreviewMesh {
    private final List<Quad> quads = new ArrayList<>();
    private final int width;
    private final int depth;
    private final int height;

    // Colors for different elements
    private static final float[] FLOOR_COLOR = {0.5f, 0.5f, 0.6f};     // Gray-blue
    private static final float[] WALL_COLOR = {0.3f, 0.3f, 0.4f};      // Dark gray
    private static final float[] HAZARD_COLOR = {0.8f, 0.3f, 0.2f};    // Red-orange
    private static final float[] SPAWN_COLOR = {0.2f, 0.7f, 0.3f};     // Green
    private static final float[] PLAYER_SPAWN_COLOR = {0.2f, 0.5f, 0.9f}; // Blue

    /**
     * Creates a preview mesh from an ArenaTemplate.
     *
     * @param template The arena template to preview
     */
    public ArenaPreviewMesh(@Nonnull ArenaTemplate template) {
        // Calculate dimensions
        int size = template.size();
        Integer sizeX = template.sizeX();
        Integer sizeZ = template.sizeZ();

        this.width = sizeX != null ? sizeX : size;
        this.depth = sizeZ != null ? sizeZ : size;
        this.height = template.walls() != null && template.walls().enabled()
            ? template.walls().height() : 10;

        // Generate geometry
        generateFloor(template);
        generateWalls(template);
        generateHazards(template);
        generateSpawnSlots(template);
    }

    /**
     * Creates a simplified preview mesh from TemplateSuggestion data.
     * Used on client-side where full ArenaTemplate is not available.
     *
     * @param arenaShape Shape name: "RECTANGULAR", "CIRCULAR", or "RING"
     * @param size Arena size (diameter/width)
     * @param hasWalls Whether the arena has walls
     * @param hasHazards Whether the arena has hazards
     * @param spawnSlotCount Number of spawn slots
     */
    public ArenaPreviewMesh(String arenaShape, int size, boolean hasWalls, boolean hasHazards, int spawnSlotCount) {
        this.width = size;
        this.depth = size;
        this.height = hasWalls ? 10 : 5;

        // Generate geometry based on simplified data
        boolean isCircular = "CIRCULAR".equals(arenaShape) || "RING".equals(arenaShape);
        boolean isRing = "RING".equals(arenaShape);

        if (isCircular) {
            generateCircularFloorSimplified(isRing);
        } else {
            generateRectangularFloor();
        }

        if (hasWalls) {
            if (isCircular) {
                generateCircularWalls(height);
            } else {
                generateRectangularWalls(height);
            }
        }

        if (hasHazards) {
            generateHazardRingSimplified();
        }

        if (spawnSlotCount > 0) {
            generateSpawnSlotsSimplified(spawnSlotCount, isCircular);
        }
    }

    /**
     * Generate circular floor for simplified constructor.
     */
    private void generateCircularFloorSimplified(boolean isRing) {
        int radius = width / 2;
        int innerRadius = isRing ? radius / 3 : 0;
        int segments = 32;

        for (int i = 0; i < segments; i++) {
            double angle1 = (2 * Math.PI * i) / segments;
            double angle2 = (2 * Math.PI * (i + 1)) / segments;

            float x1 = (float) (Math.cos(angle1) * radius);
            float z1 = (float) (Math.sin(angle1) * radius);
            float x2 = (float) (Math.cos(angle2) * radius);
            float z2 = (float) (Math.sin(angle2) * radius);

            if (innerRadius > 0) {
                float ix1 = (float) (Math.cos(angle1) * innerRadius);
                float iz1 = (float) (Math.sin(angle1) * innerRadius);
                float ix2 = (float) (Math.cos(angle2) * innerRadius);
                float iz2 = (float) (Math.sin(angle2) * innerRadius);

                Quad ring = new Quad(FLOOR_COLOR[0], FLOOR_COLOR[1], FLOOR_COLOR[2]);
                ring.v1 = new float[]{ix1, 0, iz1};
                ring.v2 = new float[]{x1, 0, z1};
                ring.v3 = new float[]{x2, 0, z2};
                ring.v4 = new float[]{ix2, 0, iz2};
                quads.add(ring);
            } else {
                Quad sector = new Quad(FLOOR_COLOR[0], FLOOR_COLOR[1], FLOOR_COLOR[2]);
                sector.v1 = new float[]{0, 0, 0};
                sector.v2 = new float[]{x1, 0, z1};
                sector.v3 = new float[]{x2, 0, z2};
                sector.v4 = new float[]{0, 0, 0};
                quads.add(sector);
            }
        }
    }

    /**
     * Generate hazard ring for simplified constructor.
     */
    private void generateHazardRingSimplified() {
        int radius = width / 2 - 2;
        int segments = 16;
        float ringWidth = 1.5f;

        for (int i = 0; i < segments; i++) {
            double angle1 = (2 * Math.PI * i) / segments;
            double angle2 = (2 * Math.PI * (i + 1)) / segments;

            float x1 = (float) (Math.cos(angle1) * radius);
            float z1 = (float) (Math.sin(angle1) * radius);
            float x2 = (float) (Math.cos(angle2) * radius);
            float z2 = (float) (Math.sin(angle2) * radius);

            float ix1 = (float) (Math.cos(angle1) * (radius - ringWidth));
            float iz1 = (float) (Math.sin(angle1) * (radius - ringWidth));
            float ix2 = (float) (Math.cos(angle2) * (radius - ringWidth));
            float iz2 = (float) (Math.sin(angle2) * (radius - ringWidth));

            Quad hazardRing = new Quad(HAZARD_COLOR[0], HAZARD_COLOR[1], HAZARD_COLOR[2]);
            hazardRing.v1 = new float[]{ix1, 0.15f, iz1};
            hazardRing.v2 = new float[]{x1, 0.15f, z1};
            hazardRing.v3 = new float[]{x2, 0.15f, z2};
            hazardRing.v4 = new float[]{ix2, 0.15f, iz2};
            quads.add(hazardRing);
        }
    }

    /**
     * Generate spawn slot markers for simplified constructor.
     */
    private void generateSpawnSlotsSimplified(int count, boolean isCircular) {
        float radius = width / 2.0f * 0.6f;
        float size = 1.0f;

        for (int i = 0; i < count; i++) {
            float x, z;
            if (isCircular || count > 4) {
                // Distribute around a circle
                double angle = (2 * Math.PI * i) / count;
                x = (float) (Math.cos(angle) * radius);
                z = (float) (Math.sin(angle) * radius);
            } else {
                // Corners for rectangular
                int halfW = width / 2 - 3;
                int halfD = depth / 2 - 3;
                switch (i % 4) {
                    case 0 -> { x = -halfW; z = -halfD; }
                    case 1 -> { x = halfW; z = -halfD; }
                    case 2 -> { x = halfW; z = halfD; }
                    default -> { x = -halfW; z = halfD; }
                }
            }

            float[] color = (i == 0) ? PLAYER_SPAWN_COLOR : SPAWN_COLOR;
            Quad spawn = new Quad(color[0], color[1], color[2]);
            spawn.v1 = new float[]{x - size/2, 0.2f, z - size/2};
            spawn.v2 = new float[]{x + size/2, 0.2f, z - size/2};
            spawn.v3 = new float[]{x + size/2, 0.2f, z + size/2};
            spawn.v4 = new float[]{x - size/2, 0.2f, z + size/2};
            quads.add(spawn);
        }
    }

    /**
     * Generate floor quads based on arena shape.
     */
    private void generateFloor(@Nonnull ArenaTemplate template) {
        ArenaTemplate.ArenaShape shape = template.arenaShape();

        if (shape == ArenaTemplate.ArenaShape.CIRCULAR || shape == ArenaTemplate.ArenaShape.RING) {
            generateCircularFloor(template, shape == ArenaTemplate.ArenaShape.RING);
        } else {
            // Default to square/rectangular
            generateRectangularFloor();
        }
    }

    /**
     * Generate a rectangular floor.
     */
    private void generateRectangularFloor() {
        int halfW = width / 2;
        int halfD = depth / 2;

        Quad floor = new Quad(FLOOR_COLOR[0], FLOOR_COLOR[1], FLOOR_COLOR[2]);
        floor.v1 = new float[]{-halfW, 0, -halfD};
        floor.v2 = new float[]{halfW, 0, -halfD};
        floor.v3 = new float[]{halfW, 0, halfD};
        floor.v4 = new float[]{-halfW, 0, halfD};
        quads.add(floor);
    }

    /**
     * Generate a circular floor using segments.
     */
    private void generateCircularFloor(@Nonnull ArenaTemplate template, boolean isRing) {
        int radius = width / 2;
        int innerRadius = isRing && template.ringInnerRadius() != null
            ? template.ringInnerRadius() : 0;
        int segments = 32;

        for (int i = 0; i < segments; i++) {
            double angle1 = (2 * Math.PI * i) / segments;
            double angle2 = (2 * Math.PI * (i + 1)) / segments;

            float x1 = (float) (Math.cos(angle1) * radius);
            float z1 = (float) (Math.sin(angle1) * radius);
            float x2 = (float) (Math.cos(angle2) * radius);
            float z2 = (float) (Math.sin(angle2) * radius);

            if (innerRadius > 0) {
                // Ring shape - create quad between inner and outer edge
                float ix1 = (float) (Math.cos(angle1) * innerRadius);
                float iz1 = (float) (Math.sin(angle1) * innerRadius);
                float ix2 = (float) (Math.cos(angle2) * innerRadius);
                float iz2 = (float) (Math.sin(angle2) * innerRadius);

                Quad ring = new Quad(FLOOR_COLOR[0], FLOOR_COLOR[1], FLOOR_COLOR[2]);
                ring.v1 = new float[]{ix1, 0, iz1};
                ring.v2 = new float[]{x1, 0, z1};
                ring.v3 = new float[]{x2, 0, z2};
                ring.v4 = new float[]{ix2, 0, iz2};
                quads.add(ring);
            } else {
                // Solid circle - create triangle fan as quads from center
                Quad sector = new Quad(FLOOR_COLOR[0], FLOOR_COLOR[1], FLOOR_COLOR[2]);
                sector.v1 = new float[]{0, 0, 0};
                sector.v2 = new float[]{x1, 0, z1};
                sector.v3 = new float[]{x2, 0, z2};
                sector.v4 = new float[]{0, 0, 0}; // Degenerate quad (triangle)
                quads.add(sector);
            }
        }
    }

    /**
     * Generate wall outline as vertical quads.
     */
    private void generateWalls(@Nonnull ArenaTemplate template) {
        if (template.walls() == null || !template.walls().enabled()) {
            return;
        }

        ArenaTemplate.ArenaShape shape = template.arenaShape();
        int wallHeight = template.walls().height();

        if (shape == ArenaTemplate.ArenaShape.CIRCULAR || shape == ArenaTemplate.ArenaShape.RING) {
            generateCircularWalls(wallHeight);
        } else {
            generateRectangularWalls(wallHeight);
        }
    }

    /**
     * Generate rectangular wall outlines.
     */
    private void generateRectangularWalls(int wallHeight) {
        int halfW = width / 2;
        int halfD = depth / 2;

        // North wall
        Quad north = new Quad(WALL_COLOR[0], WALL_COLOR[1], WALL_COLOR[2]);
        north.v1 = new float[]{-halfW, 0, -halfD};
        north.v2 = new float[]{halfW, 0, -halfD};
        north.v3 = new float[]{halfW, wallHeight, -halfD};
        north.v4 = new float[]{-halfW, wallHeight, -halfD};
        quads.add(north);

        // South wall
        Quad south = new Quad(WALL_COLOR[0], WALL_COLOR[1], WALL_COLOR[2]);
        south.v1 = new float[]{halfW, 0, halfD};
        south.v2 = new float[]{-halfW, 0, halfD};
        south.v3 = new float[]{-halfW, wallHeight, halfD};
        south.v4 = new float[]{halfW, wallHeight, halfD};
        quads.add(south);

        // East wall
        Quad east = new Quad(WALL_COLOR[0], WALL_COLOR[1], WALL_COLOR[2]);
        east.v1 = new float[]{halfW, 0, -halfD};
        east.v2 = new float[]{halfW, 0, halfD};
        east.v3 = new float[]{halfW, wallHeight, halfD};
        east.v4 = new float[]{halfW, wallHeight, -halfD};
        quads.add(east);

        // West wall
        Quad west = new Quad(WALL_COLOR[0], WALL_COLOR[1], WALL_COLOR[2]);
        west.v1 = new float[]{-halfW, 0, halfD};
        west.v2 = new float[]{-halfW, 0, -halfD};
        west.v3 = new float[]{-halfW, wallHeight, -halfD};
        west.v4 = new float[]{-halfW, wallHeight, halfD};
        quads.add(west);
    }

    /**
     * Generate circular wall outline.
     */
    private void generateCircularWalls(int wallHeight) {
        int radius = width / 2;
        int segments = 32;

        for (int i = 0; i < segments; i++) {
            double angle1 = (2 * Math.PI * i) / segments;
            double angle2 = (2 * Math.PI * (i + 1)) / segments;

            float x1 = (float) (Math.cos(angle1) * radius);
            float z1 = (float) (Math.sin(angle1) * radius);
            float x2 = (float) (Math.cos(angle2) * radius);
            float z2 = (float) (Math.sin(angle2) * radius);

            Quad segment = new Quad(WALL_COLOR[0], WALL_COLOR[1], WALL_COLOR[2]);
            segment.v1 = new float[]{x1, 0, z1};
            segment.v2 = new float[]{x2, 0, z2};
            segment.v3 = new float[]{x2, wallHeight, z2};
            segment.v4 = new float[]{x1, wallHeight, z1};
            quads.add(segment);
        }
    }

    /**
     * Generate hazard zone indicators.
     * Hazards are rendered as colored markers at their Y position.
     */
    private void generateHazards(@Nonnull ArenaTemplate template) {
        if (template.hazards() == null || template.hazards().isEmpty()) {
            return;
        }

        // For hazards, we'll render a simple indicator ring near the edge
        // since hazard bounds are stored in params map which varies by type
        int hazardCount = template.hazards().size();
        if (hazardCount > 0) {
            // Draw hazard indicator ring around arena perimeter
            int radius = width / 2 - 2;
            int segments = 16;
            float ringWidth = 1.5f;

            for (int i = 0; i < segments; i++) {
                double angle1 = (2 * Math.PI * i) / segments;
                double angle2 = (2 * Math.PI * (i + 1)) / segments;

                float x1 = (float) (Math.cos(angle1) * radius);
                float z1 = (float) (Math.sin(angle1) * radius);
                float x2 = (float) (Math.cos(angle2) * radius);
                float z2 = (float) (Math.sin(angle2) * radius);

                float ix1 = (float) (Math.cos(angle1) * (radius - ringWidth));
                float iz1 = (float) (Math.sin(angle1) * (radius - ringWidth));
                float ix2 = (float) (Math.cos(angle2) * (radius - ringWidth));
                float iz2 = (float) (Math.sin(angle2) * (radius - ringWidth));

                Quad hazardRing = new Quad(HAZARD_COLOR[0], HAZARD_COLOR[1], HAZARD_COLOR[2]);
                hazardRing.v1 = new float[]{ix1, 0.15f, iz1};
                hazardRing.v2 = new float[]{x1, 0.15f, z1};
                hazardRing.v3 = new float[]{x2, 0.15f, z2};
                hazardRing.v4 = new float[]{ix2, 0.15f, iz2};
                quads.add(hazardRing);
            }
        }
    }

    /**
     * Generate spawn slot indicators.
     */
    private void generateSpawnSlots(@Nonnull ArenaTemplate template) {
        if (template.spawnSlots() == null || template.spawnSlots().isEmpty()) {
            return;
        }

        for (ArenaTemplate.SpawnSlot slot : template.spawnSlots()) {
            int[] pos = slot.pos();
            if (pos == null || pos.length < 3) continue;

            float x = pos[0];
            float z = pos[2];
            float size = 1.0f;

            // Determine color based on tags
            float[] color = SPAWN_COLOR;
            if (slot.tags() != null && slot.tags().contains("player")) {
                color = PLAYER_SPAWN_COLOR;
            }

            // Small elevated square for spawn point
            Quad spawn = new Quad(color[0], color[1], color[2]);
            spawn.v1 = new float[]{x - size/2, 0.2f, z - size/2};
            spawn.v2 = new float[]{x + size/2, 0.2f, z - size/2};
            spawn.v3 = new float[]{x + size/2, 0.2f, z + size/2};
            spawn.v4 = new float[]{x - size/2, 0.2f, z + size/2};
            quads.add(spawn);
        }
    }

    /**
     * Render all quads into the given buffer.
     */
    public void render(@Nonnull BufferBuilder builder, @Nonnull PoseStack poseStack) {
        Matrix4f pose = poseStack.last().pose();
        for (Quad quad : quads) {
            quad.render(builder, pose);
        }
    }

    /**
     * Get the number of quads in this mesh.
     */
    public int getQuadCount() {
        return quads.size();
    }

    /**
     * Check if this mesh has no quads.
     */
    public boolean isEmpty() {
        return quads.isEmpty();
    }

    /**
     * Get the width of this preview.
     */
    public int getWidth() {
        return width;
    }

    /**
     * Get the depth of this preview.
     */
    public int getDepth() {
        return depth;
    }

    /**
     * A quad with four vertices and a color.
     */
    private static class Quad {
        float[] v1, v2, v3, v4;
        final float r, g, b;

        Quad(float r, float g, float b) {
            this.r = r;
            this.g = g;
            this.b = b;
        }

        void render(BufferBuilder builder, Matrix4f pose) {
            builder.addVertex(pose, v1[0], v1[1], v1[2]).setColor(r, g, b, 1.0f);
            builder.addVertex(pose, v2[0], v2[1], v2[2]).setColor(r, g, b, 1.0f);
            builder.addVertex(pose, v3[0], v3[1], v3[2]).setColor(r, g, b, 1.0f);
            builder.addVertex(pose, v4[0], v4[1], v4[2]).setColor(r, g, b, 1.0f);
        }
    }
}
