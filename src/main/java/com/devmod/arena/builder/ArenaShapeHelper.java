package com.devmod.arena.builder;

import com.devmod.arena.registry.ArenaTemplate;

/**
 * Utility methods for arena shape calculations: size, offset, border, and containment checks.
 * Used by block, hazard, and lighting placers.
 */
final class ArenaShapeHelper {

    private ArenaShapeHelper() {}

    static int getSizeX(ArenaTemplate template) {
        Integer sx = template.sizeX();
        return sx != null ? sx.intValue() : template.size();
    }

    static int getSizeZ(ArenaTemplate template) {
        Integer sz = template.sizeZ();
        return sz != null ? sz.intValue() : template.size();
    }

    static int minOffsetX(ArenaTemplate template) {
        return -getSizeX(template) / 2;
    }

    static int maxOffsetX(ArenaTemplate template) {
        int sizeX = getSizeX(template);
        return sizeX - (sizeX / 2) - 1;
    }

    static int minOffsetZ(ArenaTemplate template) {
        return -getSizeZ(template) / 2;
    }

    static int maxOffsetZ(ArenaTemplate template) {
        int sizeZ = getSizeZ(template);
        return sizeZ - (sizeZ / 2) - 1;
    }

    /**
     * Checks if relative coordinates (dx, dz from center) are inside the arena shape.
     */
    static boolean isInArenaShape(int dx, int dz, ArenaTemplate template) {
        ArenaTemplate.ArenaShape shape = template.arenaShape();
        if (shape == null) {
            shape = ArenaTemplate.ArenaShape.RECTANGULAR;
        }

        int minX = minOffsetX(template);
        int maxX = maxOffsetX(template);
        int minZ = minOffsetZ(template);
        int maxZ = maxOffsetZ(template);
        int halfX = getSizeX(template) / 2;
        int halfZ = getSizeZ(template) / 2;

        return switch (shape) {
            case RECTANGULAR -> dx >= minX && dx <= maxX && dz >= minZ && dz <= maxZ;
            case CIRCULAR -> {
                int radius = Math.max(halfX, halfZ);
                yield (dx * dx + dz * dz) <= (radius * radius);
            }
            case RING -> {
                int outerRadius = Math.max(halfX, halfZ);
                Integer innerRadiusVal = template.ringInnerRadius();
                int innerRadius = innerRadiusVal != null ? innerRadiusVal : outerRadius / 2;
                int distSq = dx * dx + dz * dz;
                yield distSq <= (outerRadius * outerRadius) && distSq >= (innerRadius * innerRadius);
            }
        };
    }

    /**
     * Checks if relative coordinates are on the circular/ring border for wall placement.
     */
    static boolean isOnCircularBorder(int dx, int dz, ArenaTemplate template, int thickness) {
        ArenaTemplate.ArenaShape shape = template.arenaShape();
        if (shape == null || shape == ArenaTemplate.ArenaShape.RECTANGULAR) {
            return false;
        }

        int halfX = getSizeX(template) / 2;
        int halfZ = getSizeZ(template) / 2;
        int outerRadius = Math.max(halfX, halfZ);
        int distSq = dx * dx + dz * dz;

        int outerWallInnerSq = outerRadius * outerRadius;
        int outerWallOuterSq = (outerRadius + thickness) * (outerRadius + thickness);
        boolean onOuterWall = distSq >= outerWallInnerSq && distSq < outerWallOuterSq;

        if (shape == ArenaTemplate.ArenaShape.CIRCULAR) {
            return onOuterWall;
        }

        // RING shape: also has inner wall
        Integer innerRadiusVal = template.ringInnerRadius();
        int innerRadius = innerRadiusVal != null ? innerRadiusVal : outerRadius / 2;
        int innerWallOuterSq = innerRadius * innerRadius;
        int innerWallInnerSq = Math.max(0, (innerRadius - thickness) * (innerRadius - thickness));
        boolean onInnerWall = distSq <= innerWallOuterSq && distSq >= innerWallInnerSq;

        return onOuterWall || onInnerWall;
    }

    /**
     * Checks if a position is on the floor border for the given shape.
     */
    static boolean isOnFloorBorder(int dx, int dz, ArenaTemplate template, int borderWidth) {
        ArenaTemplate.ArenaShape shape = template.arenaShape();
        if (shape == null) {
            shape = ArenaTemplate.ArenaShape.RECTANGULAR;
        }

        int sizeX = getSizeX(template);
        int sizeZ = getSizeZ(template);
        int minX = minOffsetX(template);
        int minZ = minOffsetZ(template);

        return switch (shape) {
            case RECTANGULAR -> {
                int localX = dx - minX;
                int localZ = dz - minZ;
                boolean inBorderX = localX < borderWidth || localX >= sizeX - borderWidth;
                boolean inBorderZ = localZ < borderWidth || localZ >= sizeZ - borderWidth;
                yield inBorderX || inBorderZ;
            }
            case CIRCULAR -> {
                int radius = Math.max(sizeX, sizeZ) / 2;
                int distSq = dx * dx + dz * dz;
                int innerBorderSq = (radius - borderWidth) * (radius - borderWidth);
                yield distSq >= innerBorderSq;
            }
            case RING -> {
                int outerRadius = Math.max(sizeX, sizeZ) / 2;
                Integer innerRadiusVal = template.ringInnerRadius();
                int innerRadius = innerRadiusVal != null ? innerRadiusVal : outerRadius / 2;
                int distSq = dx * dx + dz * dz;
                int outerBorderSq = (outerRadius - borderWidth) * (outerRadius - borderWidth);
                int innerBorderSq = (innerRadius + borderWidth) * (innerRadius + borderWidth);
                yield distSq >= outerBorderSq || distSq <= innerBorderSq;
            }
        };
    }
}
