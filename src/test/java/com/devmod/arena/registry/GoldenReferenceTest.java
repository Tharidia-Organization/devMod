package com.devmod.arena.registry;

import org.junit.jupiter.api.Test;

import com.devmod.arena.builder.BuildDryRunCalculator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GoldenReferenceTest {

    @Test
    void defaultFlat64MatchesSpec() {
        GoldenReference ref = GoldenReference.defaultFlat64();
        ArenaTemplate template = ArenaTemplate.defaultTemplate();

        // Bounds computed like TemplateValidator
        int sizeX = resolveSize(template.sizeX(), template.size(), "sizeX");
        int sizeZ = resolveSize(template.sizeZ(), template.size(), "sizeZ");
        int originX = template.origin().x();
        int originZ = template.origin().z();
        int minX = originX - sizeX / 2;
        int maxX = originX + sizeX / 2 - 1;
        int minZ = originZ - sizeZ / 2;
        int maxZ = originZ + sizeZ / 2 - 1;

        assertEquals(ref.arenaBounds(), new Bounds(minX, template.floor().y(), minZ, maxX, template.ceiling().y(), maxZ, originX, originZ));
        assertEquals(ref.floorBounds(), new Bounds(minX, template.floor().y(), minZ, maxX, template.floor().y(), maxZ, originX, originZ));
        assertEquals(ref.ceilingBounds(), new Bounds(minX, template.ceiling().y(), minZ, maxX, template.ceiling().y(), maxZ, originX, originZ));

        assertEquals(4096, ref.floorBlocks());
        assertEquals(2268, ref.wallBlocks());
        assertEquals(4096, ref.ceilingBlocks());
        assertEquals(12288, ref.underfloorBlocks());
        assertEquals(0, ref.hazardBlocks());
        assertEquals(25, ref.lightingBlocks()); // 5x5 ambient grid
        assertEquals(22773, ref.totalBlocks()); // includes lighting

        assertEquals(15, ref.spawnSlots().size());
        assertArrayEquals(new int[]{0, 65, 0}, ref.spawnSlots().get(0));
        assertArrayEquals(new int[]{10, 65, 0}, ref.spawnSlots().get(1));
        assertArrayEquals(new int[]{-10, 65, 0}, ref.spawnSlots().get(2));
        assertArrayEquals(new int[]{0, 65, 10}, ref.spawnSlots().get(3));
        assertArrayEquals(new int[]{0, 65, -10}, ref.spawnSlots().get(4));
        assertArrayEquals(new int[]{20, 65, 20}, ref.spawnSlots().get(5));
        assertArrayEquals(new int[]{-20, 65, 20}, ref.spawnSlots().get(6));
        assertArrayEquals(new int[]{20, 65, -20}, ref.spawnSlots().get(7));
        assertArrayEquals(new int[]{-20, 65, -20}, ref.spawnSlots().get(8));
        assertArrayEquals(new int[]{25, 65, 0}, ref.spawnSlots().get(9));
        assertArrayEquals(new int[]{-25, 65, 0}, ref.spawnSlots().get(10));
        assertArrayEquals(new int[]{0, 65, 25}, ref.spawnSlots().get(11));
        assertArrayEquals(new int[]{0, 65, -25}, ref.spawnSlots().get(12));
        assertArrayEquals(new int[]{14, 65, -8}, ref.spawnSlots().get(13));
        assertArrayEquals(new int[]{-14, 65, 8}, ref.spawnSlots().get(14));
    }

    @Test
    void bossRing80MatchesSpec() {
        GoldenReference ref = GoldenReference.bossRing80();
        ArenaTemplate template = ArenaTemplate.bossRing80Template();

        int sizeX = resolveSize(template.sizeX(), template.size(), "sizeX");
        int sizeZ = resolveSize(template.sizeZ(), template.size(), "sizeZ");
        int originX = template.origin().x();
        int originZ = template.origin().z();
        int minX = originX - sizeX / 2;
        int maxX = originX + sizeX / 2 - 1;
        int minZ = originZ - sizeZ / 2;
        int maxZ = originZ + sizeZ / 2 - 1;

        assertEquals(ref.arenaBounds(), new Bounds(minX, template.floor().y(), minZ, maxX, template.ceiling().y(), maxZ, originX, originZ));
        assertEquals(ref.floorBounds(), new Bounds(minX, template.floor().y(), minZ, maxX, template.floor().y(), maxZ, originX, originZ));
        assertEquals(ref.ceilingBounds(), new Bounds(minX, template.ceiling().y(), minZ, maxX, template.ceiling().y(), maxZ, originX, originZ));

        var dryRun = BuildDryRunCalculator.calculate(template);
        assertEquals(ref.floorBlocks(), dryRun.floorBlocks());
        assertEquals(ref.wallBlocks(), dryRun.wallBlocks());
        assertEquals(ref.ceilingBlocks(), dryRun.ceilingBlocks());
        assertEquals(ref.underfloorBlocks(), dryRun.underfloorBlocks());
        assertEquals(ref.hazardBlocks(), dryRun.hazardBlocks());
        assertEquals(ref.lightingBlocks(), dryRun.lightingBlocks());
        assertEquals(ref.totalBlocks(), dryRun.totalBlocks());

        assertEquals(15, ref.spawnSlots().size());
        assertArrayEquals(new int[]{0, 65, 0}, ref.spawnSlots().get(0));       // center, player
        assertArrayEquals(new int[]{-25, 65, 0}, ref.spawnSlots().get(1));     // melee
        assertArrayEquals(new int[]{25, 65, 0}, ref.spawnSlots().get(2));      // ranged
        assertArrayEquals(new int[]{0, 65, -25}, ref.spawnSlots().get(3));     // melee
        assertArrayEquals(new int[]{20, 65, 20}, ref.spawnSlots().get(4));     // corner
        assertArrayEquals(new int[]{-20, 65, 20}, ref.spawnSlots().get(5));    // corner
        assertArrayEquals(new int[]{20, 65, -20}, ref.spawnSlots().get(6));    // corner
        assertArrayEquals(new int[]{-20, 65, -20}, ref.spawnSlots().get(7));   // corner
        assertArrayEquals(new int[]{22, 65, 12}, ref.spawnSlots().get(8));     // melee
        assertArrayEquals(new int[]{-22, 65, 12}, ref.spawnSlots().get(9));    // melee
        assertArrayEquals(new int[]{22, 65, -12}, ref.spawnSlots().get(10));   // ranged
        assertArrayEquals(new int[]{-22, 65, -12}, ref.spawnSlots().get(11));  // ranged
        assertArrayEquals(new int[]{12, 65, 22}, ref.spawnSlots().get(12));    // ranged
        assertArrayEquals(new int[]{-12, 65, 22}, ref.spawnSlots().get(13));   // ranged
        assertArrayEquals(new int[]{0, 65, 25}, ref.spawnSlots().get(14));     // boss
    }

    private static int resolveSize(Integer primary, Integer fallback, String label) {
        Integer value = primary != null ? primary : fallback;
        if (value == null) {
            throw new IllegalStateException(label + " is missing");
        }
        return value;
    }
}
