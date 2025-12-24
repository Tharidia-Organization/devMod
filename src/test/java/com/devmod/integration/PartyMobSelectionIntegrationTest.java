package com.devmod.integration;

import com.devmod.endurance.EnduranceQuestRegistry;
import com.devmod.endurance.QuestType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * L2 Integration Tests: Party → Mob Selection → Quest Flow
 *
 * Tests the integration between:
 * 1. Party mob selection logic
 * 2. MobDifficultyPreset assignment
 * 3. Quest type compatibility
 * 4. Scaling formula integration
 *
 * NOTE: Uses simulation classes to avoid Minecraft dependencies (ResourceLocation, etc.)
 * This tests the logical flow and data integrity, not the actual Minecraft integration.
 */
@DisplayName("L2: Party Mob Selection Integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Execution(ExecutionMode.CONCURRENT)
public class PartyMobSelectionIntegrationTest {

    // =========================================================================
    // Simulation Classes (mirrors PartyData without Minecraft deps)
    // =========================================================================

    /**
     * Simulated ResourceLocation for testing.
     */
    static class SimResourceLocation {
        final String namespace;
        final String path;

        SimResourceLocation(String namespace, String path) {
            this.namespace = namespace;
            this.path = path;
        }

        static SimResourceLocation minecraft(String path) {
            return new SimResourceLocation("minecraft", path);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SimResourceLocation that = (SimResourceLocation) o;
            return namespace.equals(that.namespace) && path.equals(that.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(namespace, path);
        }

        @Override
        public String toString() {
            return namespace + ":" + path;
        }
    }

    /**
     * Simulated PartyData for testing party logic without Minecraft dependencies.
     * Mirrors the actual PartyData class logic.
     */
    static class SimPartyData {
        enum PartyState { FORMING, READY, IN_QUEST, DISBANDED }

        private final UUID partyId;
        private volatile UUID leaderId;
        private volatile String leaderName;
        private final Set<UUID> members = ConcurrentHashMap.newKeySet();
        private final Map<UUID, String> memberNames = new ConcurrentHashMap<>();
        private final Map<UUID, Boolean> memberReady = new ConcurrentHashMap<>();
        private volatile QuestType questType;
        private volatile SimResourceLocation selectedMobId;
        private volatile PartyState state = PartyState.FORMING;
        private volatile UUID instanceId;

        SimPartyData(UUID leaderId, String leaderName, QuestType questType) {
            this.partyId = UUID.randomUUID();
            this.leaderId = leaderId;
            this.leaderName = leaderName;
            this.questType = questType;
            this.members.add(leaderId);
            this.memberNames.put(leaderId, leaderName);
            this.memberReady.put(leaderId, true);
        }

        boolean addMember(UUID playerId, String playerName) {
            if (state != PartyState.FORMING) return false;
            if (members.size() >= questType.maxPlayers) return false;
            if (members.contains(playerId)) return false;
            members.add(playerId);
            memberNames.put(playerId, playerName);
            memberReady.put(playerId, false);
            return true;
        }

        boolean removeMember(UUID playerId) {
            if (playerId.equals(leaderId)) return false;
            if (!members.contains(playerId)) return false;
            members.remove(playerId);
            memberNames.remove(playerId);
            memberReady.remove(playerId);
            return true;
        }

        boolean setReady(UUID playerId, boolean ready) {
            if (!members.contains(playerId)) return false;
            memberReady.put(playerId, ready);
            updatePartyState();
            return true;
        }

        boolean setSelectedMobId(UUID requesterId, SimResourceLocation mobId) {
            if (!requesterId.equals(leaderId)) return false;
            if (state != PartyState.FORMING) return false;
            this.selectedMobId = mobId;
            return true;
        }

        SimResourceLocation getSelectedMobId() {
            return selectedMobId;
        }

        SimResourceLocation getEffectiveMobId() {
            return selectedMobId != null ? selectedMobId : SimResourceLocation.minecraft("zombie");
        }

        boolean canStartQuest() {
            if (state != PartyState.FORMING && state != PartyState.READY) return false;
            if (questType.allowsSoloPlay() && members.size() == 1) return true;
            return members.size() >= questType.minPlayers && allMembersReady();
        }

        boolean startQuest(UUID instanceId) {
            if (!canStartQuest()) return false;
            this.instanceId = instanceId;
            this.state = PartyState.IN_QUEST;
            return true;
        }

        void finishQuest() {
            this.instanceId = null;
            this.state = PartyState.FORMING;
            for (UUID memberId : members) {
                if (!memberId.equals(leaderId)) {
                    memberReady.put(memberId, false);
                }
            }
        }

        void disband() {
            this.state = PartyState.DISBANDED;
        }

        boolean setQuestType(QuestType newType) {
            if (state != PartyState.FORMING) return false;
            if (members.size() > newType.maxPlayers) return false;
            this.questType = newType;
            return true;
        }

        boolean transferLeadership(UUID requesterId, UUID newLeaderId) {
            if (!requesterId.equals(leaderId)) return false;
            if (!members.contains(newLeaderId)) return false;
            if (newLeaderId.equals(leaderId)) return false;
            this.leaderId = newLeaderId;
            this.leaderName = memberNames.get(newLeaderId);
            memberReady.put(newLeaderId, true);
            return true;
        }

        boolean isLeader(UUID playerId) {
            return leaderId.equals(playerId);
        }

        private boolean allMembersReady() {
            return members.stream().allMatch(id -> memberReady.getOrDefault(id, false));
        }

        private void updatePartyState() {
            if (state == PartyState.FORMING && canStartQuest()) {
                state = PartyState.READY;
            } else if (state == PartyState.READY && !canStartQuest()) {
                state = PartyState.FORMING;
            }
        }

        // Getters
        UUID getPartyId() { return partyId; }
        UUID getLeaderId() { return leaderId; }
        String getLeaderName() { return leaderName; }
        int getMemberCount() { return members.size(); }
        QuestType getQuestType() { return questType; }
        PartyState getState() { return state; }
        UUID getInstanceId() { return instanceId; }
    }

    // =========================================================================
    // L2-01: PartyData Mob Selection Basics
    // =========================================================================

    @Nested
    @DisplayName("L2-01: Party Mob Selection")
    class PartyMobSelectionTests {

        @Test
        @Order(1)
        @DisplayName("New party has null selected mob (uses default)")
        void newPartyHasNullMob() {
            UUID leaderId = UUID.randomUUID();
            SimPartyData party = new SimPartyData(leaderId, "TestLeader", QuestType.PVE_COOP);

            assertEquals("TestLeader", party.getLeaderName(), "Leader name should be set correctly");
            assertNull(party.getSelectedMobId(), "New party should have null mob selection");
        }

        @Test
        @Order(2)
        @DisplayName("getEffectiveMobId returns zombie as default")
        void effectiveMobIdReturnsDefault() {
            UUID leaderId = UUID.randomUUID();
            SimPartyData party = new SimPartyData(leaderId, "TestLeader", QuestType.PVE_COOP);

            SimResourceLocation effective = party.getEffectiveMobId();
            assertNotNull(effective, "Effective mob should never be null");
            assertEquals("minecraft", effective.namespace);
            assertEquals("zombie", effective.path);
        }

        @Test
        @Order(3)
        @DisplayName("Leader can set mob type while forming")
        void leaderCanSetMobType() {
            UUID leaderId = UUID.randomUUID();
            SimPartyData party = new SimPartyData(leaderId, "TestLeader", QuestType.PVE_COOP);

            SimResourceLocation creeper = SimResourceLocation.minecraft("creeper");
            boolean result = party.setSelectedMobId(leaderId, creeper);

            assertTrue(result, "Leader should be able to set mob type");
            assertEquals(creeper, party.getSelectedMobId());
            assertEquals(creeper, party.getEffectiveMobId());
        }

        @Test
        @Order(4)
        @DisplayName("Non-leader cannot set mob type")
        void nonLeaderCannotSetMobType() {
            UUID leaderId = UUID.randomUUID();
            UUID memberId = UUID.randomUUID();
            SimPartyData party = new SimPartyData(leaderId, "TestLeader", QuestType.PVE_COOP);
            party.addMember(memberId, "TestMember");

            SimResourceLocation skeleton = SimResourceLocation.minecraft("skeleton");
            boolean result = party.setSelectedMobId(memberId, skeleton);

            assertFalse(result, "Non-leader should not be able to set mob type");
            assertNull(party.getSelectedMobId());
        }

        @Test
        @Order(5)
        @DisplayName("Cannot change mob type while IN_QUEST")
        void cannotChangeMobDuringQuest() {
            UUID leaderId = UUID.randomUUID();
            SimPartyData party = new SimPartyData(leaderId, "TestLeader", QuestType.PVE_COOP);

            SimResourceLocation zombie = SimResourceLocation.minecraft("zombie");
            party.setSelectedMobId(leaderId, zombie);

            party.startQuest(UUID.randomUUID());

            SimResourceLocation spider = SimResourceLocation.minecraft("spider");
            boolean result = party.setSelectedMobId(leaderId, spider);

            assertFalse(result, "Should not be able to change mob during quest");
            assertEquals(zombie, party.getSelectedMobId());
        }
    }

    // =========================================================================
    // L2-02: Mob Selection with Party State Changes
    // =========================================================================

    @Nested
    @DisplayName("L2-02: Party State Integration")
    class PartyStateIntegrationTests {

        @Test
        @Order(1)
        @DisplayName("Mob selection persists through member joins")
        void mobSelectionPersistsThroughJoins() {
            UUID leaderId = UUID.randomUUID();
            SimPartyData party = new SimPartyData(leaderId, "Leader", QuestType.PVE_COOP);

            SimResourceLocation witch = SimResourceLocation.minecraft("witch");
            party.setSelectedMobId(leaderId, witch);

            for (int i = 0; i < 3; i++) {
                party.addMember(UUID.randomUUID(), "Member" + i);
            }

            assertEquals(witch, party.getSelectedMobId(), "Mob should persist through joins");
            assertEquals(4, party.getMemberCount());
        }

        @Test
        @Order(2)
        @DisplayName("Mob selection persists through member leaves")
        void mobSelectionPersistsThroughLeaves() {
            UUID leaderId = UUID.randomUUID();
            UUID member1 = UUID.randomUUID();
            UUID member2 = UUID.randomUUID();
            SimPartyData party = new SimPartyData(leaderId, "Leader", QuestType.PVE_COOP);
            party.addMember(member1, "Member1");
            party.addMember(member2, "Member2");

            SimResourceLocation blaze = SimResourceLocation.minecraft("blaze");
            party.setSelectedMobId(leaderId, blaze);

            party.removeMember(member1);

            assertEquals(blaze, party.getSelectedMobId(), "Mob should persist through leaves");
            assertEquals(2, party.getMemberCount());
        }

        @Test
        @Order(3)
        @DisplayName("Mob selection persists after quest finish")
        void mobSelectionAfterQuestFinish() {
            UUID leaderId = UUID.randomUUID();
            SimPartyData party = new SimPartyData(leaderId, "Leader", QuestType.PVE_COOP);

            SimResourceLocation guardian = SimResourceLocation.minecraft("guardian");
            party.setSelectedMobId(leaderId, guardian);

            party.startQuest(UUID.randomUUID());
            party.finishQuest();

            assertEquals(guardian, party.getSelectedMobId(),
                "Mob selection should persist after quest finish");
            assertEquals(SimPartyData.PartyState.FORMING, party.getState());
        }

        @Test
        @Order(4)
        @DisplayName("Mob can be changed after quest finish")
        void canChangeMobAfterQuestFinish() {
            UUID leaderId = UUID.randomUUID();
            SimPartyData party = new SimPartyData(leaderId, "Leader", QuestType.PVE_COOP);

            party.setSelectedMobId(leaderId, SimResourceLocation.minecraft("zombie"));
            party.startQuest(UUID.randomUUID());
            party.finishQuest();

            SimResourceLocation newMob = SimResourceLocation.minecraft("enderman");
            boolean result = party.setSelectedMobId(leaderId, newMob);

            assertTrue(result, "Should be able to change mob after quest finish");
            assertEquals(newMob, party.getSelectedMobId());
        }
    }

    // =========================================================================
    // L2-03: Quest Type Compatibility
    // =========================================================================

    @Nested
    @DisplayName("L2-03: Quest Type Compatibility")
    class QuestTypeCompatibilityTests {

        @Test
        @Order(1)
        @DisplayName("Mob selection works with all QuestTypes")
        void mobSelectionWorksWithAllQuestTypes() {
            for (QuestType questType : QuestType.values()) {
                UUID leaderId = UUID.randomUUID();
                SimPartyData party = new SimPartyData(leaderId, "Leader", questType);

                SimResourceLocation mob = SimResourceLocation.minecraft("zombie");
                boolean result = party.setSelectedMobId(leaderId, mob);

                assertTrue(result, "Mob selection should work with " + questType);
                assertEquals(mob, party.getSelectedMobId());
            }
        }

        @Test
        @Order(2)
        @DisplayName("Mob selection persists through quest type change")
        void mobSelectionPersistsThroughQuestTypeChange() {
            UUID leaderId = UUID.randomUUID();
            SimPartyData party = new SimPartyData(leaderId, "Leader", QuestType.PVE_COOP);

            SimResourceLocation mob = SimResourceLocation.minecraft("phantom");
            party.setSelectedMobId(leaderId, mob);

            party.setQuestType(QuestType.RAID_BOSS);

            assertEquals(mob, party.getSelectedMobId(), "Mob should persist through type change");
            assertEquals(QuestType.RAID_BOSS, party.getQuestType());
        }

        @Test
        @Order(3)
        @DisplayName("QuestType affects scaling but not mob selection")
        void questTypeAffectsScalingNotSelection() {
            UUID leaderId = UUID.randomUUID();

            SimPartyData coopParty = new SimPartyData(leaderId, "Leader", QuestType.PVE_COOP);
            SimPartyData raidParty = new SimPartyData(leaderId, "Leader", QuestType.RAID_BOSS);

            SimResourceLocation mob = SimResourceLocation.minecraft("vindicator");
            coopParty.setSelectedMobId(leaderId, mob);
            raidParty.setSelectedMobId(leaderId, mob);

            assertEquals(coopParty.getSelectedMobId(), raidParty.getSelectedMobId());
            assertTrue(QuestType.RAID_BOSS.difficultyMultiplier > QuestType.PVE_COOP.difficultyMultiplier);
        }
    }

    // =========================================================================
    // L2-04: MobDifficultyPreset Integration
    // =========================================================================

    @Nested
    @DisplayName("L2-04: Difficulty Preset Integration")
    class DifficultyPresetIntegrationTests {

        @Test
        @Order(1)
        @DisplayName("Different mob types have different presets")
        void differentMobsHaveDifferentPresets() {
            var zombiePreset = EnduranceQuestRegistry.MobDifficultyPreset.SWARM;
            var creeperPreset = EnduranceQuestRegistry.MobDifficultyPreset.GLASS_CANNON;
            var golemPreset = EnduranceQuestRegistry.MobDifficultyPreset.TANK;
            var wardenPreset = EnduranceQuestRegistry.MobDifficultyPreset.BOSS_STYLE;

            assertNotEquals(zombiePreset, creeperPreset);
            assertNotEquals(creeperPreset, golemPreset);
            assertNotEquals(golemPreset, wardenPreset);
        }

        @Test
        @Order(2)
        @DisplayName("Preset affects scaling calculations")
        void presetAffectsScaling() {
            int baseMobCount = 10;
            float baseHP = 20.0f;

            var swarm = EnduranceQuestRegistry.MobDifficultyPreset.SWARM;
            int swarmCount = (int) (baseMobCount * swarm.countMultiplier);
            float swarmHP = baseHP * swarm.hpMultiplier;

            var tank = EnduranceQuestRegistry.MobDifficultyPreset.TANK;
            int tankCount = (int) (baseMobCount * tank.countMultiplier);
            float tankHP = baseHP * tank.hpMultiplier;

            assertTrue(swarmCount > tankCount, "SWARM should have more mobs");
            assertTrue(swarmHP < tankHP, "SWARM should have less HP per mob");
            assertEquals(15, swarmCount);
            assertEquals(5, tankCount);
            assertEquals(14.0f, swarmHP, 0.1f);
            assertEquals(40.0f, tankHP, 0.1f);
        }

        @Test
        @Order(3)
        @DisplayName("STANDARD preset gives neutral scaling")
        void standardPresetIsNeutral() {
            var standard = EnduranceQuestRegistry.MobDifficultyPreset.STANDARD;

            int baseCount = 10;
            float baseHP = 20.0f;
            float baseDamage = 5.0f;

            assertEquals(baseCount, (int) (baseCount * standard.countMultiplier));
            assertEquals(baseHP, baseHP * standard.hpMultiplier, 0.001f);
            assertEquals(baseDamage, baseDamage * standard.damageMultiplier, 0.001f);
        }
    }

    // =========================================================================
    // L2-05: Full Flow Simulation
    // =========================================================================

    @Nested
    @DisplayName("L2-05: Full Quest Start Flow")
    class FullFlowSimulationTests {

        @Test
        @Order(1)
        @DisplayName("Complete flow: Create party → Select mob → Start quest")
        void completeFlow() {
            UUID leaderId = UUID.randomUUID();
            SimPartyData party = new SimPartyData(leaderId, "Leader", QuestType.PVE_COOP);

            UUID member1 = UUID.randomUUID();
            UUID member2 = UUID.randomUUID();
            party.addMember(member1, "Member1");
            party.addMember(member2, "Member2");

            SimResourceLocation selectedMob = SimResourceLocation.minecraft("creeper");
            assertTrue(party.setSelectedMobId(leaderId, selectedMob));

            party.setReady(member1, true);
            party.setReady(member2, true);

            assertTrue(party.canStartQuest(), "Should be able to start with 3 ready players");

            UUID instanceId = UUID.randomUUID();
            assertTrue(party.startQuest(instanceId));

            assertEquals(SimPartyData.PartyState.IN_QUEST, party.getState());
            assertEquals(instanceId, party.getInstanceId());
            assertEquals(selectedMob, party.getEffectiveMobId());
        }

        @Test
        @Order(2)
        @DisplayName("Solo quest flow with default mob")
        void soloQuestWithDefaultMob() {
            UUID leaderId = UUID.randomUUID();
            SimPartyData party = new SimPartyData(leaderId, "SoloPlayer", QuestType.PVE_COOP);

            assertNull(party.getSelectedMobId());
            assertTrue(party.canStartQuest());
            assertTrue(party.startQuest(UUID.randomUUID()));

            assertEquals("zombie", party.getEffectiveMobId().path);
        }

        @Test
        @Order(3)
        @DisplayName("Multiple quests maintain separate mob selections")
        void multiplePartiesSeparateMobs() {
            UUID leader1 = UUID.randomUUID();
            UUID leader2 = UUID.randomUUID();

            SimPartyData party1 = new SimPartyData(leader1, "Party1", QuestType.PVE_COOP);
            SimPartyData party2 = new SimPartyData(leader2, "Party2", QuestType.RAID_BOSS);

            party1.setSelectedMobId(leader1, SimResourceLocation.minecraft("zombie"));
            party2.setSelectedMobId(leader2, SimResourceLocation.minecraft("skeleton"));

            assertNotEquals(party1.getSelectedMobId(), party2.getSelectedMobId());
            assertEquals("zombie", party1.getEffectiveMobId().path);
            assertEquals("skeleton", party2.getEffectiveMobId().path);
        }
    }

    // =========================================================================
    // L2-06: Edge Cases
    // =========================================================================

    @Nested
    @DisplayName("L2-06: Edge Cases")
    class EdgeCaseTests {

        @Test
        @Order(1)
        @DisplayName("Null mob ID is handled gracefully")
        void nullMobIdHandled() {
            UUID leaderId = UUID.randomUUID();
            SimPartyData party = new SimPartyData(leaderId, "Leader", QuestType.PVE_COOP);

            party.setSelectedMobId(leaderId, SimResourceLocation.minecraft("zombie"));
            party.setSelectedMobId(leaderId, null);

            assertEquals("zombie", party.getEffectiveMobId().path);
        }

        @Test
        @Order(2)
        @DisplayName("Leadership transfer preserves mob selection")
        void leadershipTransferPreservesMob() {
            UUID leader1 = UUID.randomUUID();
            UUID leader2 = UUID.randomUUID();
            SimPartyData party = new SimPartyData(leader1, "Leader1", QuestType.PVE_COOP);
            party.addMember(leader2, "Leader2");

            SimResourceLocation mob = SimResourceLocation.minecraft("ravager");
            party.setSelectedMobId(leader1, mob);

            party.transferLeadership(leader1, leader2);

            assertEquals(mob, party.getSelectedMobId(), "Mob should persist after transfer");
            assertTrue(party.isLeader(leader2));

            SimResourceLocation newMob = SimResourceLocation.minecraft("pillager");
            assertTrue(party.setSelectedMobId(leader2, newMob));
            assertEquals(newMob, party.getSelectedMobId());
        }

        @Test
        @Order(3)
        @DisplayName("Party disband maintains data integrity")
        void disbandMaintainsIntegrity() {
            UUID leaderId = UUID.randomUUID();
            SimPartyData party = new SimPartyData(leaderId, "Leader", QuestType.PVE_COOP);
            party.setSelectedMobId(leaderId, SimResourceLocation.minecraft("wither_skeleton"));

            party.disband();

            assertEquals(SimPartyData.PartyState.DISBANDED, party.getState());
            assertNotNull(party.getEffectiveMobId());
        }

        @Test
        @Order(4)
        @DisplayName("Cannot add members when party is full")
        void cannotExceedMaxPlayers() {
            UUID leaderId = UUID.randomUUID();
            SimPartyData party = new SimPartyData(leaderId, "Leader", QuestType.PVE_COOP);

            // Add members up to max (PVE_COOP max is 6)
            for (int i = 0; i < 5; i++) {
                assertTrue(party.addMember(UUID.randomUUID(), "Member" + i));
            }

            assertEquals(6, party.getMemberCount());

            // Cannot add 7th
            assertFalse(party.addMember(UUID.randomUUID(), "ExtraMember"));
            assertEquals(6, party.getMemberCount());
        }
    }
}
