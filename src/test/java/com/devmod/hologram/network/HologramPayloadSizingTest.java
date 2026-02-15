package com.devmod.hologram.network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.devmod.TestBootstrap;
import com.devmod.hologram.data.HologramDefinition;
import com.devmod.hologram.data.HologramOptions;
import com.devmod.hologram.data.HologramPosition;
import com.devmod.hologram.data.HologramStyle;
import com.devmod.hologram.data.HologramType;

@DisplayName("HologramPayloadSizing")
class HologramPayloadSizingTest {

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    private static final ResourceLocation OVERWORLD = Level.OVERWORLD.location();

    @Test
    @DisplayName("estimateStyleSize returns positive value")
    void styleSize() {
        long size = HologramPayloadSizing.estimateStyleSize(HologramStyle.DEFAULT);
        assertTrue(size > 0);
        // Style should be about 22 bytes (4+4+4+4+1+1+1+4 = ~23)
        assertTrue(size < 50);
    }

    @Test
    @DisplayName("estimateOptionsSize returns positive value for simple options")
    void optionsSizeSimple() {
        long size = HologramPayloadSizing.estimateOptionsSize(HologramOptions.DEFAULT);
        assertTrue(size > 0);
        assertTrue(size < 50);
    }

    @Test
    @DisplayName("estimateOptionsSize accounts for linked zone")
    void optionsSizeWithZone() {
        var withZone = new HologramOptions(true, false, 0, 0, "my_zone_name");
        var withoutZone = HologramOptions.DEFAULT;
        long sizeWithZone = HologramPayloadSizing.estimateOptionsSize(withZone);
        long sizeWithoutZone = HologramPayloadSizing.estimateOptionsSize(withoutZone);
        assertTrue(sizeWithZone > sizeWithoutZone);
    }

    @Test
    @DisplayName("estimatePositionSize returns positive value")
    void positionSize() {
        var pos = new HologramPosition(
            new BlockPos(10, 64, -5), Vec3.ZERO, OVERWORLD, 0, 0);
        long size = HologramPayloadSizing.estimatePositionSize(pos);
        assertTrue(size > 0);
    }

    @Test
    @DisplayName("estimateDefinitionSize returns positive value")
    void definitionSize() {
        var def = HologramDefinition.builder()
            .id(UUID.randomUUID())
            .label("Test")
            .type(HologramType.STATIC)
            .lines(List.of("Line 1", "Line 2"))
            .position(HologramPosition.atBlock(new BlockPos(0, 64, 0), OVERWORLD))
            .createdAt(1000L)
            .updatedAt(2000L)
            .build();
        long size = HologramPayloadSizing.estimateDefinitionSize(def);
        assertTrue(size > 0);
        // Should be a reasonable packet size
        assertTrue(size < 1024);
    }

    @Test
    @DisplayName("definition size grows with more lines")
    void definitionSizeGrowsWithLines() {
        var defSmall = HologramDefinition.builder()
            .id(UUID.randomUUID())
            .lines(List.of("one"))
            .createdAt(1000L)
            .updatedAt(2000L)
            .build();
        var defLarge = HologramDefinition.builder()
            .id(UUID.randomUUID())
            .lines(List.of("one", "two", "three", "four", "five"))
            .createdAt(1000L)
            .updatedAt(2000L)
            .build();
        assertTrue(HologramPayloadSizing.estimateDefinitionSize(defLarge) >
            HologramPayloadSizing.estimateDefinitionSize(defSmall));
    }

    @Test
    @DisplayName("definition size includes creator UUID when present")
    void definitionSizeWithCreator() {
        var withCreator = HologramDefinition.builder()
            .id(UUID.randomUUID())
            .creator(UUID.randomUUID())
            .createdAt(1000L)
            .updatedAt(2000L)
            .build();
        var withoutCreator = HologramDefinition.builder()
            .id(UUID.randomUUID())
            .createdAt(1000L)
            .updatedAt(2000L)
            .build();
        long sizeWith = HologramPayloadSizing.estimateDefinitionSize(withCreator);
        long sizeWithout = HologramPayloadSizing.estimateDefinitionSize(withoutCreator);
        assertEquals(16, sizeWith - sizeWithout); // UUID is 16 bytes
    }
}
