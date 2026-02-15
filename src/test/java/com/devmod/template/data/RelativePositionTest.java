package com.devmod.template.data;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import com.devmod.TestBootstrap;

import static org.junit.jupiter.api.Assertions.*;

class RelativePositionTest {

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }

    // Room bounds: minX=0, minZ=0, maxX=20, maxZ=20, floorY=64
    // Center: (10, 10), Width=20, Depth=20

    @Test
    void constructorRejectsNullAnchor() {
        assertThrows(NullPointerException.class, () ->
                new RelativePosition(null, 0, 0, 0));
    }

    @Test
    void centerFactoryMethod() {
        RelativePosition pos = RelativePosition.center();
        assertEquals(AnchorPoint.CENTER, pos.anchor());
        assertEquals(0, pos.offsetX());
        assertEquals(0, pos.offsetY());
        assertEquals(0, pos.offsetZ());
    }

    @Test
    void centerFactoryWithOffsets() {
        RelativePosition pos = RelativePosition.center(3, -5);
        assertEquals(AnchorPoint.CENTER, pos.anchor());
        assertEquals(3, pos.offsetX());
        assertEquals(0, pos.offsetY());
        assertEquals(-5, pos.offsetZ());
    }

    @Test
    void northWallFactory() {
        RelativePosition pos = RelativePosition.northWall(2);
        assertEquals(AnchorPoint.NORTH_WALL, pos.anchor());
        assertEquals(0, pos.offsetX());
        assertEquals(0, pos.offsetY());
        assertEquals(2, pos.offsetZ());
    }

    @Test
    void southWallFactory() {
        RelativePosition pos = RelativePosition.southWall(2);
        assertEquals(AnchorPoint.SOUTH_WALL, pos.anchor());
        assertEquals(0, pos.offsetX());
        assertEquals(0, pos.offsetY());
        assertEquals(-2, pos.offsetZ());
    }

    @Test
    void eastWallFactory() {
        RelativePosition pos = RelativePosition.eastWall(2);
        assertEquals(AnchorPoint.EAST_WALL, pos.anchor());
        assertEquals(-2, pos.offsetX());
        assertEquals(0, pos.offsetY());
        assertEquals(0, pos.offsetZ());
    }

    @Test
    void westWallFactory() {
        RelativePosition pos = RelativePosition.westWall(2);
        assertEquals(AnchorPoint.WEST_WALL, pos.anchor());
        assertEquals(2, pos.offsetX());
        assertEquals(0, pos.offsetY());
        assertEquals(0, pos.offsetZ());
    }

    @Test
    void resolveCenterAnchor() {
        RelativePosition pos = new RelativePosition(AnchorPoint.CENTER, 0, 0, 0);
        BlockPos result = pos.resolve(0, 0, 20, 20, 64);
        assertEquals(new BlockPos(10, 64, 10), result);
    }

    @Test
    void resolveCenterAnchorWithOffset() {
        RelativePosition pos = new RelativePosition(AnchorPoint.CENTER, 3, 2, -5);
        BlockPos result = pos.resolve(0, 0, 20, 20, 64);
        assertEquals(new BlockPos(13, 66, 5), result);
    }

    @Test
    void resolveNorthWall() {
        RelativePosition pos = new RelativePosition(AnchorPoint.NORTH_WALL, 0, 0, 0);
        BlockPos result = pos.resolve(0, 0, 20, 20, 64);
        // North wall: centerX=10, minZ=0
        assertEquals(new BlockPos(10, 64, 0), result);
    }

    @Test
    void resolveSouthWall() {
        RelativePosition pos = new RelativePosition(AnchorPoint.SOUTH_WALL, 0, 0, 0);
        BlockPos result = pos.resolve(0, 0, 20, 20, 64);
        // South wall: centerX=10, maxZ=20
        assertEquals(new BlockPos(10, 64, 20), result);
    }

    @Test
    void resolveEastWall() {
        RelativePosition pos = new RelativePosition(AnchorPoint.EAST_WALL, 0, 0, 0);
        BlockPos result = pos.resolve(0, 0, 20, 20, 64);
        // East wall: maxX=20, centerZ=10
        assertEquals(new BlockPos(20, 64, 10), result);
    }

    @Test
    void resolveWestWall() {
        RelativePosition pos = new RelativePosition(AnchorPoint.WEST_WALL, 0, 0, 0);
        BlockPos result = pos.resolve(0, 0, 20, 20, 64);
        // West wall: minX=0, centerZ=10
        assertEquals(new BlockPos(0, 64, 10), result);
    }

    @Test
    void resolveNWCorner() {
        RelativePosition pos = new RelativePosition(AnchorPoint.NW_CORNER, 0, 0, 0);
        BlockPos result = pos.resolve(0, 0, 20, 20, 64);
        assertEquals(new BlockPos(0, 64, 0), result);
    }

    @Test
    void resolveNECorner() {
        RelativePosition pos = new RelativePosition(AnchorPoint.NE_CORNER, 0, 0, 0);
        BlockPos result = pos.resolve(0, 0, 20, 20, 64);
        assertEquals(new BlockPos(20, 64, 0), result);
    }

    @Test
    void resolveSWCorner() {
        RelativePosition pos = new RelativePosition(AnchorPoint.SW_CORNER, 0, 0, 0);
        BlockPos result = pos.resolve(0, 0, 20, 20, 64);
        assertEquals(new BlockPos(0, 64, 20), result);
    }

    @Test
    void resolveSECorner() {
        RelativePosition pos = new RelativePosition(AnchorPoint.SE_CORNER, 0, 0, 0);
        BlockPos result = pos.resolve(0, 0, 20, 20, 64);
        assertEquals(new BlockPos(20, 64, 20), result);
    }

    @Test
    void resolveNorthCenter() {
        RelativePosition pos = new RelativePosition(AnchorPoint.NORTH_CENTER, 0, 0, 0);
        BlockPos result = pos.resolve(0, 0, 20, 20, 64);
        // North center: centerX=10, minZ + depth/4 = 0 + 20/4 = 5
        assertEquals(new BlockPos(10, 64, 5), result);
    }

    @Test
    void resolveSouthCenter() {
        RelativePosition pos = new RelativePosition(AnchorPoint.SOUTH_CENTER, 0, 0, 0);
        BlockPos result = pos.resolve(0, 0, 20, 20, 64);
        // South center: centerX=10, maxZ - depth/4 = 20 - 5 = 15
        assertEquals(new BlockPos(10, 64, 15), result);
    }

    @Test
    void resolveEastCenter() {
        RelativePosition pos = new RelativePosition(AnchorPoint.EAST_CENTER, 0, 0, 0);
        BlockPos result = pos.resolve(0, 0, 20, 20, 64);
        // East center: maxX - width/4 = 20 - 5 = 15, centerZ=10
        assertEquals(new BlockPos(15, 64, 10), result);
    }

    @Test
    void resolveWestCenter() {
        RelativePosition pos = new RelativePosition(AnchorPoint.WEST_CENTER, 0, 0, 0);
        BlockPos result = pos.resolve(0, 0, 20, 20, 64);
        // West center: minX + width/4 = 0 + 5 = 5, centerZ=10
        assertEquals(new BlockPos(5, 64, 10), result);
    }

    @Test
    void resolveWithAsymmetricBounds() {
        RelativePosition pos = RelativePosition.center();
        BlockPos result = pos.resolve(10, 20, 50, 60, 100);
        // Center: (30, 100, 40)
        assertEquals(new BlockPos(30, 100, 40), result);
    }
}
