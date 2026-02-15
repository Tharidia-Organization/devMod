package com.devmod.client.endurance.wis;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

class CombatEventTest {

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", path);
    }

    // ========== KillEvent ==========

    @Test
    void killEvent_storesAllFields() {
        UUID mobId = UUID.randomUUID();
        ResourceLocation mobType = rl("zombie");
        BlockPos pos = new BlockPos(10, 64, 20);

        var event = new CombatEvent.KillEvent(
            100, mobId, mobType, pos, 8.5f, "diamond_sword", true, true, 5, 1500
        );

        assertEquals(100, event.ticksSinceWaveStart());
        assertEquals(mobId, event.mobId());
        assertEquals(mobType, event.mobType());
        assertEquals(pos, event.position());
        assertEquals(8.5f, event.damageDealt());
        assertEquals("diamond_sword", event.weaponUsed());
        assertTrue(event.isElite());
        assertTrue(event.isCritical());
        assertEquals(5, event.comboCount());
        assertEquals(1500, event.timeToKillMs());
    }

    @Test
    void killEvent_implementsCombatEvent() {
        var event = new CombatEvent.KillEvent(
            0, UUID.randomUUID(), rl("zombie"), BlockPos.ZERO, 5f, "sword", false, false, 0, 100
        );
        assertInstanceOf(CombatEvent.class, event);
    }

    // ========== DamageDealtEvent ==========

    @Test
    void damageDealtEvent_storesFields() {
        var event = new CombatEvent.DamageDealtEvent(
            50, UUID.randomUUID(), rl("skeleton"), 6f, "arrow", true, new BlockPos(1, 2, 3)
        );

        assertEquals(50, event.ticksSinceWaveStart());
        assertEquals(6f, event.damage());
        assertEquals("arrow", event.damageSource());
        assertTrue(event.isCritical());
    }

    // ========== DamageTakenEvent ==========

    @Test
    void damageTakenEvent_storesFields() {
        var event = new CombatEvent.DamageTakenEvent(
            75, UUID.randomUUID(), rl("creeper"), 12f, "explosion", 8f, new BlockPos(5, 64, 5)
        );

        assertEquals(75, event.ticksSinceWaveStart());
        assertEquals(12f, event.damage());
        assertEquals(8f, event.remainingHealth());
    }

    @Test
    void damageTakenEvent_allowsNullSource() {
        var event = new CombatEvent.DamageTakenEvent(
            10, null, null, 4f, "fall", 16f, BlockPos.ZERO
        );
        assertNull(event.sourceId());
        assertNull(event.sourceType());
    }

    // ========== ComboEvent ==========

    @Test
    void comboEvent_milestone() {
        var event = new CombatEvent.ComboEvent(
            200, CombatEvent.ComboEvent.ComboEventType.MILESTONE_10, 10, 300, "B"
        );

        assertEquals(CombatEvent.ComboEvent.ComboEventType.MILESTONE_10, event.type());
        assertEquals(10, event.comboCount());
        assertEquals(300, event.styleScore());
        assertEquals("B", event.rankName());
    }

    @Test
    void comboEventType_hasMilestones() {
        var types = CombatEvent.ComboEvent.ComboEventType.values();
        assertEquals(8, types.length);
    }

    // ========== PositionSnapshot ==========

    @Test
    void positionSnapshot_storesFields() {
        BlockPos pos = new BlockPos(100, 64, 200);
        var event = new CombatEvent.PositionSnapshot(150, pos, 18f, 5, true);

        assertEquals(150, event.ticksSinceWaveStart());
        assertEquals(pos, event.position());
        assertEquals(18f, event.health());
        assertEquals(5, event.mobsNearby());
        assertTrue(event.inCombat());
    }

    // ========== SpecialActionEvent ==========

    @Test
    void specialActionEvent_storesFields() {
        var event = new CombatEvent.SpecialActionEvent(
            80, "perfect_parry", 25, 15, new BlockPos(3, 64, 7)
        );

        assertEquals("perfect_parry", event.actionType());
        assertEquals(25, event.pointsEarned());
        assertEquals(15, event.styleEarned());
    }

    // ========== MobSpawnEvent ==========

    @Test
    void mobSpawnEvent_storesFields() {
        var event = new CombatEvent.MobSpawnEvent(
            0, UUID.randomUUID(), rl("wither_skeleton"), new BlockPos(20, 64, 30),
            true, "fire_aura"
        );

        assertTrue(event.isElite());
        assertEquals("fire_aura", event.affix());
    }

    // ========== AbilityUsedEvent ==========

    @Test
    void abilityUsedEvent_storesFields() {
        var event = new CombatEvent.AbilityUsedEvent(
            120, "fireball", UUID.randomUUID(), new BlockPos(15, 64, 25), 30f, true
        );

        assertEquals("fireball", event.abilityId());
        assertEquals(30f, event.manaCost());
        assertTrue(event.successful());
    }

    @Test
    void abilityUsedEvent_allowsNullTarget() {
        var event = new CombatEvent.AbilityUsedEvent(
            120, "heal", null, BlockPos.ZERO, 20f, true
        );
        assertNull(event.targetId());
    }

    // ========== Sealed interface ==========

    @Test
    void sealedInterface_allPermittedClassesCovered() {
        // The sealed interface should have all these implementations
        CombatEvent killEvent = new CombatEvent.KillEvent(
            0, UUID.randomUUID(), rl("zombie"), BlockPos.ZERO, 5f, "sword", false, false, 0, 100
        );
        CombatEvent damageDealt = new CombatEvent.DamageDealtEvent(
            0, UUID.randomUUID(), rl("zombie"), 5f, "melee", false, BlockPos.ZERO
        );
        CombatEvent damageTaken = new CombatEvent.DamageTakenEvent(
            0, null, null, 5f, "fall", 15f, BlockPos.ZERO
        );
        CombatEvent combo = new CombatEvent.ComboEvent(
            0, CombatEvent.ComboEvent.ComboEventType.COMBO_BREAK, 0, 0, "D"
        );
        CombatEvent position = new CombatEvent.PositionSnapshot(
            0, BlockPos.ZERO, 20f, 0, false
        );
        CombatEvent special = new CombatEvent.SpecialActionEvent(
            0, "dodge", 10, 5, BlockPos.ZERO
        );
        CombatEvent spawn = new CombatEvent.MobSpawnEvent(
            0, UUID.randomUUID(), rl("zombie"), BlockPos.ZERO, false, ""
        );
        CombatEvent ability = new CombatEvent.AbilityUsedEvent(
            0, "test", null, BlockPos.ZERO, 0f, true
        );

        // All should implement CombatEvent
        assertNotNull(killEvent);
        assertNotNull(damageDealt);
        assertNotNull(damageTaken);
        assertNotNull(combo);
        assertNotNull(position);
        assertNotNull(special);
        assertNotNull(spawn);
        assertNotNull(ability);
    }
}
