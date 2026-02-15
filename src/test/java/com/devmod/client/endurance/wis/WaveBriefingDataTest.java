package com.devmod.client.endurance.wis;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class WaveBriefingDataTest {

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", path);
    }

    @Test
    void builder_createsValidData() {
        WaveBriefingData data = WaveBriefingData.builder()
            .waveNumber(3)
            .totalWaves(10)
            .totalMobCount(20)
            .eliteCount(2)
            .threatLevel(WaveBriefingData.ThreatLevel.HIGH)
            .build();

        assertEquals(3, data.waveNumber());
        assertEquals(10, data.totalWaves());
        assertEquals(20, data.totalMobCount());
        assertEquals(2, data.eliteCount());
        assertEquals(WaveBriefingData.ThreatLevel.HIGH, data.threatLevel());
    }

    @Test
    void builder_defaultValues() {
        WaveBriefingData data = WaveBriefingData.builder().build();

        assertEquals(0, data.waveNumber());
        assertEquals(0, data.totalWaves());
        assertTrue(data.mobComposition().isEmpty());
        assertEquals(0, data.totalMobCount());
        assertEquals(0, data.eliteCount());
        assertTrue(data.activeModifiers().isEmpty());
        assertNull(data.objective());
        assertEquals(WaveBriefingData.ThreatLevel.MEDIUM, data.threatLevel());
        assertTrue(data.warnings().isEmpty());
        assertNull(data.playerStatus());
        assertNull(data.comparison());
    }

    @Test
    void hasBoss_returnsTrueWhenBossPresent() {
        var boss = new WaveBriefingData.MobComposition(
            rl("wither"), "Wither", 1, 0, 300f, 8f, true
        );
        WaveBriefingData data = WaveBriefingData.builder()
            .mobComposition(List.of(boss))
            .build();

        assertTrue(data.hasBoss());
    }

    @Test
    void hasBoss_returnsFalseWhenNoBoss() {
        var zombie = new WaveBriefingData.MobComposition(
            rl("zombie"), "Zombie", 10, 0, 20f, 3f, false
        );
        WaveBriefingData data = WaveBriefingData.builder()
            .mobComposition(List.of(zombie))
            .build();

        assertFalse(data.hasBoss());
    }

    @Test
    void hasBoss_returnsFalseWhenEmpty() {
        WaveBriefingData data = WaveBriefingData.builder().build();
        assertFalse(data.hasBoss());
    }

    @Test
    void getTotalEstimatedHP_sumsAllMobs() {
        var zombie = new WaveBriefingData.MobComposition(
            rl("zombie"), "Zombie", 10, 0, 20f, 3f, false
        );
        var skeleton = new WaveBriefingData.MobComposition(
            rl("skeleton"), "Skeleton", 5, 1, 20f, 4f, false
        );
        WaveBriefingData data = WaveBriefingData.builder()
            .mobComposition(List.of(zombie, skeleton))
            .build();

        // 10 * 20 + 5 * 20 = 300
        assertEquals(300f, data.getTotalEstimatedHP(), 0.01f);
    }

    @Test
    void getTotalEstimatedHP_zeroWhenEmpty() {
        WaveBriefingData data = WaveBriefingData.builder().build();
        assertEquals(0f, data.getTotalEstimatedHP(), 0.01f);
    }

    @Test
    void getPrimaryMobType_returnsMostCommon() {
        var zombie = new WaveBriefingData.MobComposition(
            rl("zombie"), "Zombie", 15, 0, 20f, 3f, false
        );
        var skeleton = new WaveBriefingData.MobComposition(
            rl("skeleton"), "Skeleton", 5, 0, 20f, 4f, false
        );
        WaveBriefingData data = WaveBriefingData.builder()
            .mobComposition(List.of(zombie, skeleton))
            .build();

        var primary = data.getPrimaryMobType();
        assertNotNull(primary);
        assertEquals("Zombie", primary.displayName());
        assertEquals(15, primary.count());
    }

    @Test
    void getPrimaryMobType_returnsNullWhenEmpty() {
        WaveBriefingData data = WaveBriefingData.builder().build();
        assertNull(data.getPrimaryMobType());
    }

    @Test
    void builder_setsModifiers() {
        WaveBriefingData data = WaveBriefingData.builder()
            .activeModifiers(Set.of("speed_boost", "extra_hp"))
            .build();

        assertEquals(2, data.activeModifiers().size());
        assertTrue(data.activeModifiers().contains("speed_boost"));
    }

    @Test
    void builder_setsObjective() {
        var objective = new WaveBriefingData.ObjectiveInfo("elite_hunt", "Kill all elites", 3, 120);
        WaveBriefingData data = WaveBriefingData.builder()
            .objective(objective)
            .build();

        assertNotNull(data.objective());
        assertEquals("elite_hunt", data.objective().type());
        assertEquals(3, data.objective().targetCount());
        assertEquals(120, data.objective().timeLimit());
    }

    @Test
    void builder_setsPlayerStatus() {
        var status = new WaveBriefingData.PlayerStatus(18f, 20f, 10f, 2, 5, "B");
        WaveBriefingData data = WaveBriefingData.builder()
            .playerStatus(status)
            .build();

        assertNotNull(data.playerStatus());
        assertEquals(18f, data.playerStatus().health());
        assertEquals(20f, data.playerStatus().maxHealth());
    }

    @Test
    void builder_setsComparison() {
        var comparison = new WaveBriefingData.WaveComparison(
            1.1f, 1.2f, 5, true, List.of("fire_resistant")
        );
        WaveBriefingData data = WaveBriefingData.builder()
            .comparison(comparison)
            .build();

        assertNotNull(data.comparison());
        assertEquals(5, data.comparison().mobCountChange());
        assertTrue(data.comparison().newModifiers());
    }

    @Test
    void builder_setsWarnings() {
        WaveBriefingData data = WaveBriefingData.builder()
            .warnings(List.of("Boss incoming!", "Low health"))
            .build();

        assertEquals(2, data.warnings().size());
    }

    // ========== ThreatLevel enum ==========

    @Test
    void threatLevel_hasFourValues() {
        assertEquals(4, WaveBriefingData.ThreatLevel.values().length);
    }

    @Test
    void threatLevel_displayNames() {
        assertEquals("Low", WaveBriefingData.ThreatLevel.LOW.getDisplayName());
        assertEquals("Medium", WaveBriefingData.ThreatLevel.MEDIUM.getDisplayName());
        assertEquals("High", WaveBriefingData.ThreatLevel.HIGH.getDisplayName());
        assertEquals("Extreme", WaveBriefingData.ThreatLevel.EXTREME.getDisplayName());
    }

    @Test
    void threatLevel_colorsAreNonZero() {
        for (WaveBriefingData.ThreatLevel level : WaveBriefingData.ThreatLevel.values()) {
            assertNotEquals(0, level.getColor(), level + " should have a non-zero color");
        }
    }

    // ========== MobComposition record ==========

    @Test
    void mobComposition_fieldsAccessible() {
        var mob = new WaveBriefingData.MobComposition(
            rl("creeper"), "Creeper", 8, 1, 20f, 30f, false
        );
        assertEquals("Creeper", mob.displayName());
        assertEquals(8, mob.count());
        assertEquals(1, mob.eliteCount());
        assertEquals(20f, mob.estimatedHealth());
        assertEquals(30f, mob.estimatedDamage());
        assertFalse(mob.isBoss());
    }

    // ========== ObjectiveInfo record ==========

    @Test
    void objectiveInfo_noTimeLimit() {
        var obj = new WaveBriefingData.ObjectiveInfo("survive", "Survive the wave", 0, -1);
        assertEquals(-1, obj.timeLimit());
    }
}
