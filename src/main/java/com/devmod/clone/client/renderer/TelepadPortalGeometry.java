package com.devmod.clone.client.renderer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Shared portal geometry for telepad rendering and occlusion checks.
 */
public final class TelepadPortalGeometry {
    public static final float PORTAL_WIDTH = 2.457f;
    public static final float PORTAL_HEIGHT = 3.51f;
    public static final float PORTAL_Y_OFFSET = 1.0f;

    private TelepadPortalGeometry() {
    }

    public static float getHalfWidth() {
        return PORTAL_WIDTH / 2.0f;
    }

    public static float getHalfHeight() {
        return PORTAL_HEIGHT / 2.0f;
    }

    public static Vec3 getCenter(BlockPos pos) {
        return new Vec3(
            pos.getX() + 0.5,
            pos.getY() + PORTAL_Y_OFFSET + getHalfHeight(),
            pos.getZ() + 0.5
        );
    }

    public static float getFacingYawDegrees(Direction facing) {
        return switch (facing) {
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            case EAST -> -90.0f;
            default -> 0.0f;
        };
    }

    public static Vec3 rotateY(Vec3 vec, double degrees) {
        double rad = Math.toRadians(degrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double x = vec.x * cos + vec.z * sin;
        double z = -vec.x * sin + vec.z * cos;
        return new Vec3(x, vec.y, z);
    }

    public static Vec3 toLocal(Vec3 worldOffset, Direction facing) {
        return rotateY(worldOffset, -getFacingYawDegrees(facing));
    }

    public static Vec3 getNormal(Direction facing) {
        return rotateY(new Vec3(0.0, 0.0, 1.0), getFacingYawDegrees(facing));
    }

    public static boolean isInsidePortalEllipse(Vec3 localOffset) {
        double halfWidth = getHalfWidth();
        double halfHeight = getHalfHeight();
        double x = localOffset.x;
        double y = localOffset.y;
        double nx = (x * x) / (halfWidth * halfWidth);
        double ny = (y * y) / (halfHeight * halfHeight);
        return nx + ny <= 1.0;
    }
}
