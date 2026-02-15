package com.devmod.template.data;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.devmod.TestBootstrap;

import static org.junit.jupiter.api.Assertions.*;

class ZoneTemplateTest {

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }

    @ParameterizedTest
    @EnumSource(ZoneTemplate.class)
    void allValuesHaveNonNullSerializedName(ZoneTemplate zone) {
        assertNotNull(zone.getSerializedName());
        assertFalse(zone.getSerializedName().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(ZoneTemplate.class)
    void allValuesHaveNonNullPadBlock(ZoneTemplate zone) {
        assertNotNull(zone.getPadBlock());
    }

    @ParameterizedTest
    @EnumSource(ZoneTemplate.class)
    void allValuesHaveNonNullAccentBlock(ZoneTemplate zone) {
        assertNotNull(zone.getAccentBlock());
    }

    @ParameterizedTest
    @EnumSource(ZoneTemplate.class)
    void roundTripThroughSerializedName(ZoneTemplate zone) {
        assertEquals(zone, ZoneTemplate.fromString(zone.getSerializedName()));
    }

    @Test
    void fromStringDefaultsToSpawnForUnknown() {
        assertEquals(ZoneTemplate.SPAWN, ZoneTemplate.fromString("nonexistent"));
        assertEquals(ZoneTemplate.SPAWN, ZoneTemplate.fromString(""));
    }

    @Test
    void fromStringReturnsKnownZones() {
        assertEquals(ZoneTemplate.SPAWN, ZoneTemplate.fromString("spawn"));
        assertEquals(ZoneTemplate.TUTORIAL, ZoneTemplate.fromString("tutorial"));
        assertEquals(ZoneTemplate.WAR_ARENA, ZoneTemplate.fromString("war_arena"));
        assertEquals(ZoneTemplate.DM_MOD, ZoneTemplate.fromString("dm_mod"));
    }

    @Test
    void getColorReturnsExpectedValues() {
        assertEquals(0xFFFFFF, ZoneTemplate.SPAWN.getColor());
        assertEquals(0x4488FF, ZoneTemplate.TUTORIAL.getColor());
        assertEquals(0xFF4444, ZoneTemplate.WAR_HUB.getColor());
    }

    @Test
    void getColorWithAlphaPrependsOpaqueByte() {
        assertEquals(0xFFFFFFFF, ZoneTemplate.SPAWN.getColorWithAlpha());
        assertEquals(0xFF4488FF, ZoneTemplate.TUTORIAL.getColorWithAlpha());
    }

    @Test
    void hasExpectedValueCount() {
        assertEquals(32, ZoneTemplate.values().length);
    }
}
