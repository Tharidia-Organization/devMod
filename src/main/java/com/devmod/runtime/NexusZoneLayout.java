package com.devmod.runtime;

import net.minecraft.core.BlockPos;

public final class NexusZoneLayout {
    private static final int HUB_HALF_SIZE = 96;
    private static final int CENTER_HALF_SIZE = 24;
    private static final int WALL_HEIGHT = 8;

    public enum Zone {
        HUB,
        COMBAT,
        ARENA,
        UI,
        TELEMETRY,
        SHOWCASE,
        INTEGRATION,
        SANDBOX,
        MECHANICS,
        AFK,
        QUIET,
        PERFORMANCE,
        OVERVIEW,
        OUTSIDE
    }

    private NexusZoneLayout() {}

    public static Zone resolveZone(BlockPos pos, BlockPos origin) {
        if (pos == null || origin == null) {
            return Zone.OUTSIDE;
        }

        Layout layout = Layout.from(origin);
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        BlockPos overviewOffset = NexusSpawnManager.Zone.OVERVIEW.offset();
        int deckCenterX = layout.originX + overviewOffset.getX();
        int deckCenterZ = layout.originZ + overviewOffset.getZ();
        int deckY = layout.originY + overviewOffset.getY();
        int deckHalf = 7;
        if (y >= deckY && y <= deckY + 2
            && inRect(x, z, deckCenterX - deckHalf, deckCenterZ - deckHalf,
            deckCenterX + deckHalf, deckCenterZ + deckHalf)) {
            return Zone.OVERVIEW;
        }

        int afkMinX = layout.centerMinX + 2;
        int afkMinZ = layout.centerMinZ + 10;
        int afkMaxX = afkMinX + 4;
        int afkMaxZ = afkMinZ + 4;
        if (inRect(x, z, afkMinX, afkMinZ, afkMaxX, afkMaxZ)) {
            return Zone.AFK;
        }

        int northSplitZ = splitCoordinate(layout.roomMinZ1, layout.roomMaxZ1);
        int northTopMax = northSplitZ - 2;
        int northBottomMin = northSplitZ + 2;

        int westSplitX = splitCoordinate(layout.roomMinX1, layout.roomMaxX1);
        int westLeftMax = westSplitX - 2;
        int westRightMin = westSplitX + 2;

        int eastSplitX = splitCoordinate(layout.roomMinX2, layout.roomMaxX2);
        int eastLeftMax = eastSplitX - 2;
        int eastRightMin = eastSplitX + 2;

        if (inRect(x, z, layout.roomMinX1, layout.roomMinZ1, layout.roomMaxX1, northTopMax)) {
            return Zone.COMBAT;
        }
        if (inRect(x, z, layout.roomMinX1, northBottomMin, layout.roomMaxX1, layout.roomMaxZ1)) {
            return Zone.ARENA;
        }
        if (inRect(x, z, layout.roomMinX2, layout.roomMinZ1, layout.roomMaxX2, northTopMax)) {
            return Zone.UI;
        }
        if (inRect(x, z, layout.roomMinX2, northBottomMin, layout.roomMaxX2, layout.roomMaxZ1)) {
            int quietMinX = layout.roomMaxX2 - 8;
            int quietMinZ = layout.roomMaxZ1 - 8;
            int quietMaxX = quietMinX + 4;
            int quietMaxZ = quietMinZ + 4;
            if (inRect(x, z, quietMinX, quietMinZ, quietMaxX, quietMaxZ)) {
                return Zone.QUIET;
            }
            return Zone.TELEMETRY;
        }
        if (inRect(x, z, layout.roomMinX1, layout.roomMinZ2, westLeftMax, layout.roomMaxZ2)) {
            return Zone.SHOWCASE;
        }
        if (inRect(x, z, westRightMin, layout.roomMinZ2, layout.roomMaxX1, layout.roomMaxZ2)) {
            return Zone.INTEGRATION;
        }
        if (inRect(x, z, layout.roomMinX2, layout.roomMinZ2, eastLeftMax, layout.roomMaxZ2)) {
            int sandboxMaxX = eastLeftMax;
            int sandboxMinZ = layout.roomMinZ2;
            int plotMaxX = sandboxMaxX - 3;
            int plotMinZ = sandboxMinZ + 3;
            int perfMinX = plotMaxX - 7;
            int perfMaxX = plotMaxX - 1;
            int perfMinZ = plotMinZ + 1;
            int perfMaxZ = plotMinZ + 7;
            if (inRect(x, z, perfMinX, perfMinZ, perfMaxX, perfMaxZ)) {
                return Zone.PERFORMANCE;
            }
            return Zone.SANDBOX;
        }
        if (inRect(x, z, eastRightMin, layout.roomMinZ2, layout.roomMaxX2, layout.roomMaxZ2)) {
            return Zone.MECHANICS;
        }

        if (inRect(x, z, layout.minX, layout.minZ, layout.maxX, layout.maxZ)
            && y >= layout.originY - 4
            && y <= layout.originY + WALL_HEIGHT + 16) {
            return Zone.HUB;
        }

        return Zone.OUTSIDE;
    }

    public static boolean isBuildAllowed(BlockPos pos, BlockPos origin) {
        if (pos == null || origin == null) {
            return false;
        }
        Layout layout = Layout.from(origin);
        int x = pos.getX();
        int z = pos.getZ();

        int sandboxMinX = layout.roomMinX2;
        int sandboxMaxX = splitCoordinate(layout.roomMinX2, layout.roomMaxX2) - 2;
        int sandboxMinZ = layout.roomMinZ2;
        int sandboxMaxZ = layout.roomMaxZ2;
        int plotMinX = sandboxMinX + 3;
        int plotMaxX = sandboxMaxX - 3;
        int plotMinZ = sandboxMinZ + 3;
        int plotMaxZ = sandboxMaxZ - 3;

        return inRect(x, z, plotMinX, plotMinZ, plotMaxX, plotMaxZ);
    }

    private static boolean inRect(int x, int z, int minX, int minZ, int maxX, int maxZ) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    private static int splitCoordinate(int min, int max) {
        return min + (max - min) / 2;
    }

    private static final class Layout {
        private final int originX;
        private final int originY;
        private final int originZ;
        private final int minX;
        private final int maxX;
        private final int minZ;
        private final int maxZ;
        private final int centerMinX;
        private final int centerMaxX;
        private final int centerMinZ;
        private final int centerMaxZ;
        private final int roomMinX1;
        private final int roomMaxX1;
        private final int roomMinX2;
        private final int roomMaxX2;
        private final int roomMinZ1;
        private final int roomMaxZ1;
        private final int roomMinZ2;
        private final int roomMaxZ2;

        private Layout(BlockPos origin) {
            originX = origin.getX();
            originY = origin.getY();
            originZ = origin.getZ();

            minX = originX - HUB_HALF_SIZE;
            maxX = originX + HUB_HALF_SIZE - 1;
            minZ = originZ - HUB_HALF_SIZE;
            maxZ = originZ + HUB_HALF_SIZE - 1;

            centerMinX = originX - CENTER_HALF_SIZE;
            centerMaxX = originX + CENTER_HALF_SIZE - 1;
            centerMinZ = originZ - CENTER_HALF_SIZE;
            centerMaxZ = originZ + CENTER_HALF_SIZE - 1;

            roomMinX1 = minX;
            roomMaxX1 = centerMinX - 1;
            roomMinX2 = centerMaxX + 1;
            roomMaxX2 = maxX;

            roomMinZ1 = minZ;
            roomMaxZ1 = centerMinZ - 1;
            roomMinZ2 = centerMaxZ + 1;
            roomMaxZ2 = maxZ;
        }

        private static Layout from(BlockPos origin) {
            return new Layout(origin);
        }
    }
}
