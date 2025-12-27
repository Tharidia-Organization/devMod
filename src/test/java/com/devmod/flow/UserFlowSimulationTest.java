package com.devmod.flow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

public class UserFlowSimulationTest {

    // ============================================
    // Simulation Classes (no Minecraft dependencies)
    // ============================================

    /**
     * Simulated QuestType enum matching production
     */
    enum SimQuestType {
        PVE_COOP(1, 6, true),
        RAID_BOSS(2, 10, false),
        EVENT(1, 20, true);

        final int minPlayers;
        final int maxPlayers;
        final boolean allowsSoloPlay;

        SimQuestType(int min, int max, boolean solo) {
            this.minPlayers = min;
            this.maxPlayers = max;
            this.allowsSoloPlay = solo;
        }
    }

    /**
     * Simulated MobDifficultyPreset enum
     */
    enum SimMobPreset {
        SWARM(0.3f, 0.8f, 1.2f),
        STANDARD(1.0f, 1.0f, 1.0f),
        TANK(2.5f, 0.7f, 1.5f),
        GLASS_CANNON(0.6f, 2.0f, 0.8f),
        BOSS_STYLE(5.0f, 1.5f, 2.0f);

        final float hpMult;
        final float damageMult;
        final float armorMult;

        SimMobPreset(float hp, float dmg, float armor) {
            this.hpMult = hp;
            this.damageMult = dmg;
            this.armorMult = armor;
        }
    }

    /**
     * Simulated PartyState enum
     */
    enum SimPartyState {
        FORMING, READY, IN_QUEST, DISBANDED
    }

    /**
     * Simulated ResourceLocation
     */
    record SimResourceLocation(String namespace, String path) {
        static SimResourceLocation of(String full) {
            int colonIndex = full.indexOf(':');
            return new SimResourceLocation(full.substring(0, colonIndex), full.substring(colonIndex + 1));
        }

        @Override
        public String toString() {
            return namespace + ":" + path;
        }
    }

    /**
     * Simulated PartyData - mirrors production logic
     */
    static class SimPartyData {
        private final UUID partyId;
        private UUID leaderId;
        private String leaderName;
        private final Set<UUID> members = ConcurrentHashMap.newKeySet();
        private final Map<UUID, String> memberNames = new ConcurrentHashMap<>();
        private final Map<UUID, Boolean> memberReady = new ConcurrentHashMap<>();
        private final Set<UUID> pendingInvites = ConcurrentHashMap.newKeySet();
        private SimQuestType questType;
        private @Nullable SimResourceLocation selectedMobId;
        private SimPartyState state;
        private @Nullable UUID instanceId;
        private SimMobPreset mobPreset = SimMobPreset.STANDARD;

        public SimPartyData(UUID leaderId, String leaderName, SimQuestType questType) {
            this.partyId = UUID.randomUUID();
            this.leaderId = leaderId;
            this.leaderName = leaderName;
            this.questType = questType;
            this.state = SimPartyState.FORMING;
            this.members.add(leaderId);
            this.memberNames.put(leaderId, leaderName);
            this.memberReady.put(leaderId, true);
        }

        public boolean addMember(UUID playerId, String name) {
            if (state != SimPartyState.FORMING) return false;
            if (members.size() >= questType.maxPlayers) return false;
            if (members.contains(playerId)) return false;
            members.add(playerId);
            memberNames.put(playerId, name);
            memberReady.put(playerId, false);
            pendingInvites.remove(playerId);
            return true;
        }

        public boolean removeMember(UUID playerId) {
            if (playerId.equals(leaderId)) return false;
            if (!members.contains(playerId)) return false;
            members.remove(playerId);
            memberNames.remove(playerId);
            memberReady.remove(playerId);
            return true;
        }

        public boolean kickMember(UUID requesterId, UUID targetId) {
            if (!requesterId.equals(leaderId)) return false;
            return removeMember(targetId);
        }

        public boolean setReady(UUID playerId, boolean ready) {
            if (!members.contains(playerId)) return false;
            memberReady.put(playerId, ready);
            updateState();
            return true;
        }

        public boolean isReady(UUID playerId) {
            return memberReady.getOrDefault(playerId, false);
        }

        public boolean allMembersReady() {
            return members.stream().allMatch(id -> memberReady.getOrDefault(id, false));
        }

        public boolean createInvite(UUID targetId) {
            if (state != SimPartyState.FORMING) return false;
            if (members.size() + pendingInvites.size() >= questType.maxPlayers) return false;
            if (members.contains(targetId)) return false;
            if (pendingInvites.contains(targetId)) return false;
            pendingInvites.add(targetId);
            return true;
        }

        public boolean canStartQuest() {
            if (state != SimPartyState.FORMING && state != SimPartyState.READY) return false;
            if (questType.allowsSoloPlay && members.size() == 1) return true;
            return members.size() >= questType.minPlayers && allMembersReady();
        }

        public boolean startQuest(UUID instanceId) {
            if (!canStartQuest()) return false;
            this.instanceId = instanceId;
            this.state = SimPartyState.IN_QUEST;
            return true;
        }

        public void finishQuest() {
            this.instanceId = null;
            this.state = SimPartyState.FORMING;
            for (UUID memberId : members) {
                if (!memberId.equals(leaderId)) {
                    memberReady.put(memberId, false);
                }
            }
        }

        public void disband() {
            this.state = SimPartyState.DISBANDED;
            pendingInvites.clear();
        }

        public boolean setQuestType(SimQuestType newType) {
            if (state != SimPartyState.FORMING) return false;
            if (members.size() > newType.maxPlayers) return false;
            this.questType = newType;
            return true;
        }

        public boolean setSelectedMobId(UUID requesterId, SimResourceLocation mobId) {
            if (!requesterId.equals(leaderId)) return false;
            if (state != SimPartyState.FORMING) return false;
            this.selectedMobId = mobId;
            return true;
        }

        public boolean setMobPreset(UUID requesterId, SimMobPreset preset) {
            if (!requesterId.equals(leaderId)) return false;
            if (state != SimPartyState.FORMING) return false;
            this.mobPreset = preset;
            return true;
        }

        private void updateState() {
            if (state == SimPartyState.FORMING && canStartQuest()) {
                state = SimPartyState.READY;
            } else if (state == SimPartyState.READY && !canStartQuest()) {
                state = SimPartyState.FORMING;
            }
        }

        public UUID getPartyId() { return partyId; }
        public UUID getLeaderId() { return leaderId; }
        public String getLeaderName() { return leaderName; }
        public Set<UUID> getMembers() { return Collections.unmodifiableSet(members); }
        public int getMemberCount() { return members.size(); }
        public @Nullable String getMemberName(UUID id) { return memberNames.get(id); }
        public SimQuestType getQuestType() { return questType; }
        public @Nullable SimResourceLocation getSelectedMobId() { return selectedMobId; }
        public SimMobPreset getMobPreset() { return mobPreset; }
        public SimPartyState getState() { return state; }
        public @Nullable UUID getInstanceId() { return instanceId; }
        public boolean isFull() { return members.size() >= questType.maxPlayers; }
        public int getRemainingSlots() { return Math.max(0, questType.maxPlayers - members.size() - pendingInvites.size()); }
    }

    /**
     * Simulated WaveManager scaling logic
     */
    static class SimWaveManager {
        public static float calculateScaledHealth(float baseHP, int playerCount, SimQuestType questType, SimMobPreset preset) {
            int effectivePlayers = Math.min(playerCount, questType.maxPlayers);
            float playerScale = 1.0f + (effectivePlayers - 1) * 0.35f;
            return baseHP * preset.hpMult * playerScale;
        }

        public static float calculateScaledDamage(float baseDmg, int playerCount, SimMobPreset preset) {
            float playerScale = 1.0f + (playerCount - 1) * 0.1f;
            return baseDmg * preset.damageMult * playerScale;
        }
    }

    // ============================================
    // Test Data
    // ============================================

    private UUID player1Id;
    private UUID player2Id;
    private UUID player3Id;
    private UUID player4Id;

    @BeforeEach
    void setup() {
        player1Id = UUID.randomUUID();
        player2Id = UUID.randomUUID();
        player3Id = UUID.randomUUID();
        player4Id = UUID.randomUUID();
    }

    // ============================================
    // Solo Player Flows
    // ============================================

    @Nested
    @DisplayName("Solo Player Flows")
    class SoloPlayerFlows {

        @Test
        @DisplayName("Solo player creates party and starts PVE_COOP quest")
        void testSoloPlayerPveCoopFlow() {
            // 1. Player creates party
            SimPartyData party = new SimPartyData(player1Id, "SoloPlayer", SimQuestType.PVE_COOP);

            assertEquals(SimPartyState.FORMING, party.getState());
            assertEquals(1, party.getMemberCount());
            assertTrue(party.isReady(player1Id), "Leader should be ready by default");

            // 2. Player selects mob type
            assertTrue(party.setSelectedMobId(player1Id, SimResourceLocation.of("minecraft:zombie")));
            assertEquals("minecraft:zombie", Objects.requireNonNull(party.getSelectedMobId()).toString());

            // 3. Player can start immediately (PVE_COOP allows solo)
            assertTrue(party.canStartQuest(), "PVE_COOP should allow solo play");

            // 4. Start quest
            UUID instanceId = UUID.randomUUID();
            assertTrue(party.startQuest(instanceId));
            assertEquals(SimPartyState.IN_QUEST, party.getState());
            assertEquals(instanceId, party.getInstanceId());

            // 5. Finish quest
            party.finishQuest();
            assertEquals(SimPartyState.FORMING, party.getState());
            assertNull(party.getInstanceId());
        }

        @Test
        @DisplayName("Solo player cannot start RAID_BOSS (requires 2+)")
        void testSoloPlayerCannotStartRaidBoss() {
            SimPartyData party = new SimPartyData(player1Id, "SoloPlayer", SimQuestType.RAID_BOSS);

            assertEquals(1, party.getMemberCount());
            assertFalse(party.canStartQuest(), "RAID_BOSS requires at least 2 players");
        }

        @Test
        @DisplayName("Solo player changes quest type before starting")
        void testSoloPlayerChangesQuestType() {
            SimPartyData party = new SimPartyData(player1Id, "SoloPlayer", SimQuestType.PVE_COOP);

            // Change to EVENT
            assertTrue(party.setQuestType(SimQuestType.EVENT));
            assertEquals(SimQuestType.EVENT, party.getQuestType());

            // Solo still works for EVENT
            assertTrue(party.canStartQuest());
        }
    }

    // ============================================
    // Multi-Player Party Flows
    // ============================================

    @Nested
    @DisplayName("Multi-Player Party Flows")
    class MultiPlayerFlows {

        @Test
        @DisplayName("Complete 2-player party formation and quest start")
        void testTwoPlayerPartyFlow() {
            // 1. Leader creates party
            SimPartyData party = new SimPartyData(player1Id, "Leader", SimQuestType.RAID_BOSS);
            assertEquals(SimPartyState.FORMING, party.getState());

            // 2. Leader invites player 2
            assertTrue(party.createInvite(player2Id));

            // 3. Player 2 joins
            assertTrue(party.addMember(player2Id, "Member2"));
            assertEquals(2, party.getMemberCount());

            // 4. Player 2 is not ready yet
            assertFalse(party.isReady(player2Id));
            assertFalse(party.canStartQuest());

            // 5. Player 2 sets ready
            assertTrue(party.setReady(player2Id, true));
            assertTrue(party.isReady(player2Id));
            assertTrue(party.allMembersReady());

            // 6. Now can start quest
            assertTrue(party.canStartQuest());
            assertEquals(SimPartyState.READY, party.getState());

            // 7. Start quest
            UUID instanceId = UUID.randomUUID();
            assertTrue(party.startQuest(instanceId));
            assertEquals(SimPartyState.IN_QUEST, party.getState());
        }

        @Test
        @DisplayName("Full 6-player PVE_COOP party")
        void testFullSixPlayerParty() {
            SimPartyData party = new SimPartyData(player1Id, "Leader", SimQuestType.PVE_COOP);

            // Add 5 more players
            List<UUID> additionalPlayers = new ArrayList<>();
            for (int i = 2; i <= 6; i++) {
                UUID playerId = UUID.randomUUID();
                additionalPlayers.add(playerId);
                assertTrue(party.addMember(playerId, "Player" + i), "Should add player " + i);
            }

            assertEquals(6, party.getMemberCount());
            assertTrue(party.isFull());
            assertEquals(0, party.getRemainingSlots());

            // Try to add 7th player
            UUID player7 = UUID.randomUUID();
            assertFalse(party.addMember(player7, "Player7"), "Should not add 7th player to 6-max party");

            // All players ready up
            for (UUID playerId : additionalPlayers) {
                party.setReady(playerId, true);
            }

            assertTrue(party.canStartQuest());
        }

        @Test
        @DisplayName("Member leaves during forming phase")
        void testMemberLeavesDuringForming() {
            SimPartyData party = new SimPartyData(player1Id, "Leader", SimQuestType.RAID_BOSS);
            party.addMember(player2Id, "Member2");
            party.addMember(player3Id, "Member3");

            assertEquals(3, party.getMemberCount());

            // Player 2 and 3 ready up
            party.setReady(player2Id, true);
            party.setReady(player3Id, true);
            assertTrue(party.canStartQuest());

            // Player 3 leaves
            assertTrue(party.removeMember(player3Id));
            assertEquals(2, party.getMemberCount());
            assertFalse(party.getMembers().contains(player3Id));

            // Still can start with 2 players
            assertTrue(party.canStartQuest());
        }

        @Test
        @DisplayName("Leader kicks member before start")
        void testLeaderKicksMember() {
            SimPartyData party = new SimPartyData(player1Id, "Leader", SimQuestType.RAID_BOSS);
            party.addMember(player2Id, "Member2");
            party.addMember(player3Id, "ToxicPlayer");

            // Leader kicks player 3
            assertTrue(party.kickMember(player1Id, player3Id));
            assertEquals(2, party.getMemberCount());
            assertFalse(party.getMembers().contains(player3Id));

            // Non-leader cannot kick
            assertFalse(party.kickMember(player2Id, player1Id), "Non-leader cannot kick");
        }
    }

    // ============================================
    // Mob Selection Flows
    // ============================================

    @Nested
    @DisplayName("Mob Selection Flows")
    class MobSelectionFlows {

        @Test
        @DisplayName("Leader changes mob selection during forming")
        void testMobSelectionDuringForming() {
            SimPartyData party = new SimPartyData(player1Id, "Leader", SimQuestType.PVE_COOP);

            // Initial: no mob selected
            assertNull(party.getSelectedMobId());

            // Leader selects zombie
            assertTrue(party.setSelectedMobId(player1Id, SimResourceLocation.of("minecraft:zombie")));
            assertEquals("minecraft:zombie", Objects.requireNonNull(party.getSelectedMobId()).toString());

            // Leader changes to skeleton
            assertTrue(party.setSelectedMobId(player1Id, SimResourceLocation.of("minecraft:skeleton")));
            assertEquals("minecraft:skeleton", party.getSelectedMobId().toString());

            // Leader changes to wither skeleton
            assertTrue(party.setSelectedMobId(player1Id, SimResourceLocation.of("minecraft:wither_skeleton")));
            assertEquals("minecraft:wither_skeleton", party.getSelectedMobId().toString());
        }

        @Test
        @DisplayName("Non-leader cannot change mob selection")
        void testNonLeaderCannotChangeMob() {
            SimPartyData party = new SimPartyData(player1Id, "Leader", SimQuestType.PVE_COOP);
            party.addMember(player2Id, "Member2");

            // Non-leader tries to change mob
            assertFalse(party.setSelectedMobId(player2Id, SimResourceLocation.of("minecraft:blaze")));
            assertNull(party.getSelectedMobId(), "Mob should remain null");
        }

        @Test
        @DisplayName("Cannot change mob after quest starts")
        void testCannotChangeMobAfterQuestStarts() {
            SimPartyData party = new SimPartyData(player1Id, "Leader", SimQuestType.PVE_COOP);
            party.setSelectedMobId(player1Id, SimResourceLocation.of("minecraft:zombie"));

            // Start quest
            party.startQuest(UUID.randomUUID());
            assertEquals(SimPartyState.IN_QUEST, party.getState());

            // Try to change mob
            assertFalse(party.setSelectedMobId(player1Id, SimResourceLocation.of("minecraft:blaze")));
            assertEquals("minecraft:zombie", Objects.requireNonNull(party.getSelectedMobId()).toString());
        }

        @Test
        @DisplayName("Mob selection persists after member joins")
        void testMobSelectionPersistsAfterMemberJoin() {
            SimPartyData party = new SimPartyData(player1Id, "Leader", SimQuestType.RAID_BOSS);

            // Leader selects mob before anyone joins
            party.setSelectedMobId(player1Id, SimResourceLocation.of("minecraft:blaze"));

            // Members join
            party.addMember(player2Id, "Member2");
            party.addMember(player3Id, "Member3");

            // Mob selection should persist
            assertEquals("minecraft:blaze", Objects.requireNonNull(party.getSelectedMobId()).toString());
        }
    }

    // ============================================
    // Mob Preset + Scaling Flows
    // ============================================

    @Nested
    @DisplayName("Mob Preset and Scaling Flows")
    class MobPresetFlows {

        @Test
        @DisplayName("Leader selects different mob presets")
        void testMobPresetSelection() {
            SimPartyData party = new SimPartyData(player1Id, "Leader", SimQuestType.RAID_BOSS);

            // Default is STANDARD
            assertEquals(SimMobPreset.STANDARD, party.getMobPreset());

            // Change to TANK
            assertTrue(party.setMobPreset(player1Id, SimMobPreset.TANK));
            assertEquals(SimMobPreset.TANK, party.getMobPreset());

            // Change to SWARM
            assertTrue(party.setMobPreset(player1Id, SimMobPreset.SWARM));
            assertEquals(SimMobPreset.SWARM, party.getMobPreset());
        }

        @Test
        @DisplayName("Scaling applies correctly for 4-player TANK preset")
        void testFourPlayerTankScaling() {
            SimPartyData party = new SimPartyData(player1Id, "Leader", SimQuestType.RAID_BOSS);
            party.addMember(player2Id, "P2");
            party.addMember(player3Id, "P3");
            party.addMember(player4Id, "P4");
            party.setMobPreset(player1Id, SimMobPreset.TANK);

            // Base HP = 100
            float scaledHP = SimWaveManager.calculateScaledHealth(100f, 4, SimQuestType.RAID_BOSS, SimMobPreset.TANK);
            // TANK: hpMult=2.5, 4 players: 1 + 3*0.35 = 2.05
            // Expected: 100 * 2.5 * 2.05 = 512.5
            assertEquals(512.5f, scaledHP, 0.1f);
        }

        @Test
        @DisplayName("Scaling applies correctly for 6-player SWARM preset")
        void testSixPlayerSwarmScaling() {
            float scaledHP = SimWaveManager.calculateScaledHealth(100f, 6, SimQuestType.PVE_COOP, SimMobPreset.SWARM);
            // SWARM: hpMult=0.3, 6 players: 1 + 5*0.35 = 2.75
            // Expected: 100 * 0.3 * 2.75 = 82.5
            assertEquals(82.5f, scaledHP, 0.1f);
        }

        @Test
        @DisplayName("Damage scaling for GLASS_CANNON 3-player")
        void testGlassCannonDamageScaling() {
            float scaledDmg = SimWaveManager.calculateScaledDamage(10f, 3, SimMobPreset.GLASS_CANNON);
            // GLASS_CANNON: damageMult=2.0, 3 players: 1 + 2*0.1 = 1.2
            // Expected: 10 * 2.0 * 1.2 = 24
            assertEquals(24f, scaledDmg, 0.1f);
        }
    }

    // ============================================
    // Quest Type Change Flows
    // ============================================

    @Nested
    @DisplayName("Quest Type Change Flows")
    class QuestTypeChangeFlows {

        @Test
        @DisplayName("Leader changes quest type during forming")
        void testQuestTypeChange() {
            SimPartyData party = new SimPartyData(player1Id, "Leader", SimQuestType.PVE_COOP);

            // Change to RAID_BOSS
            assertTrue(party.setQuestType(SimQuestType.RAID_BOSS));
            assertEquals(SimQuestType.RAID_BOSS, party.getQuestType());

            // Now solo play is not allowed
            assertFalse(party.canStartQuest());
        }

        @Test
        @DisplayName("Cannot change to type with smaller max if too many members")
        void testCannotChangeToSmallerMaxType() {
            SimPartyData party = new SimPartyData(player1Id, "Leader", SimQuestType.RAID_BOSS);

            // Add 8 members (total 9, under RAID_BOSS max of 10)
            for (int i = 0; i < 8; i++) {
                party.addMember(UUID.randomUUID(), "Player" + i);
            }
            assertEquals(9, party.getMemberCount());

            // Try to change to PVE_COOP (max 6)
            assertFalse(party.setQuestType(SimQuestType.PVE_COOP));
            assertEquals(SimQuestType.RAID_BOSS, party.getQuestType(), "Should remain RAID_BOSS");
        }

        @Test
        @DisplayName("Cannot change quest type after quest starts")
        void testCannotChangeTypeAfterStart() {
            SimPartyData party = new SimPartyData(player1Id, "Leader", SimQuestType.PVE_COOP);
            party.startQuest(UUID.randomUUID());

            assertFalse(party.setQuestType(SimQuestType.RAID_BOSS));
            assertEquals(SimQuestType.PVE_COOP, party.getQuestType());
        }
    }

    // ============================================
    // Complete End-to-End Flows
    // ============================================

    @Nested
    @DisplayName("Complete End-to-End Flows")
    class EndToEndFlows {

        @Test
        @DisplayName("Full 4-player RAID_BOSS flow with BOSS_STYLE preset")
        void testFullRaidBossFlow() {
            // 1. Leader creates party
            SimPartyData party = new SimPartyData(player1Id, "RaidLeader", SimQuestType.RAID_BOSS);

            // 2. Leader configures party
            party.setSelectedMobId(player1Id, SimResourceLocation.of("minecraft:wither_skeleton"));
            party.setMobPreset(player1Id, SimMobPreset.BOSS_STYLE);

            // 3. Invite and add 3 more players
            party.createInvite(player2Id);
            party.createInvite(player3Id);
            party.createInvite(player4Id);

            party.addMember(player2Id, "Tank");
            party.addMember(player3Id, "DPS");
            party.addMember(player4Id, "Healer");

            assertEquals(4, party.getMemberCount());

            // 4. Verify scaling before ready
            float scaledHP = SimWaveManager.calculateScaledHealth(100f, 4, SimQuestType.RAID_BOSS, SimMobPreset.BOSS_STYLE);
            // BOSS_STYLE: hpMult=5.0, 4 players: 1 + 3*0.35 = 2.05
            // Expected: 100 * 5.0 * 2.05 = 1025
            assertEquals(1025f, scaledHP, 0.1f);

            // 5. All members ready up
            party.setReady(player2Id, true);
            party.setReady(player3Id, true);
            party.setReady(player4Id, true);

            assertTrue(party.allMembersReady());
            assertEquals(SimPartyState.READY, party.getState());

            // 6. Start quest
            UUID instanceId = UUID.randomUUID();
            assertTrue(party.startQuest(instanceId));
            assertEquals(SimPartyState.IN_QUEST, party.getState());

            // 7. Verify configurations persist during quest
            assertEquals("minecraft:wither_skeleton", Objects.requireNonNull(party.getSelectedMobId()).toString());
            assertEquals(SimMobPreset.BOSS_STYLE, party.getMobPreset());

            // 8. Quest completes
            party.finishQuest();
            assertEquals(SimPartyState.FORMING, party.getState());

            // 9. Members need to ready up again
            assertFalse(party.isReady(player2Id));
            assertTrue(party.isReady(player1Id), "Leader stays ready");
        }

        @Test
        @DisplayName("Quest restart flow - party stays together")
        void testQuestRestartFlow() {
            SimPartyData party = new SimPartyData(player1Id, "Leader", SimQuestType.PVE_COOP);
            party.addMember(player2Id, "Partner");
            party.setSelectedMobId(player1Id, SimResourceLocation.of("minecraft:zombie"));

            // First quest
            party.setReady(player2Id, true);
            assertTrue(party.startQuest(UUID.randomUUID()));
            party.finishQuest();

            // Verify party intact
            assertEquals(2, party.getMemberCount());
            assertEquals(SimPartyState.FORMING, party.getState());

            // After finishQuest, member is not ready, we're in FORMING state
            assertFalse(party.isReady(player2Id), "Member should not be ready after quest finish");
            assertTrue(party.isReady(player1Id), "Leader stays ready");

            // Change mob for second quest BEFORE member readies (while in FORMING)
            assertTrue(party.setSelectedMobId(player1Id, SimResourceLocation.of("minecraft:skeleton")),
                "Should change mob while in FORMING state");

            // Now member re-readies
            party.setReady(player2Id, true);
            assertTrue(party.allMembersReady(), "All members should be ready");
            assertTrue(party.canStartQuest(), "Should be able to start with 2 ready members");

            // Start second quest
            assertTrue(party.startQuest(UUID.randomUUID()));
            assertEquals("minecraft:skeleton", Objects.requireNonNull(party.getSelectedMobId()).toString());
        }

        @Test
        @DisplayName("Party disband flow")
        void testDisbandFlow() {
            SimPartyData party = new SimPartyData(player1Id, "Leader", SimQuestType.RAID_BOSS);
            party.addMember(player2Id, "Member");
            party.createInvite(player3Id);

            // Disband
            party.disband();

            assertEquals(SimPartyState.DISBANDED, party.getState());
            assertFalse(party.canStartQuest());
        }
    }

    // ============================================
    // Error Scenarios
    // ============================================

    @Nested
    @DisplayName("Error Scenarios")
    class ErrorScenarios {

        @Test
        @DisplayName("Cannot add member to full party")
        void testCannotAddToFullParty() {
            SimPartyData party = new SimPartyData(player1Id, "Leader", SimQuestType.PVE_COOP);

            // Fill to max (6)
            for (int i = 0; i < 5; i++) {
                party.addMember(UUID.randomUUID(), "P" + i);
            }
            assertEquals(6, party.getMemberCount());
            assertTrue(party.isFull());

            // Try to add more
            assertFalse(party.addMember(UUID.randomUUID(), "Extra"));
        }

        @Test
        @DisplayName("Cannot add same player twice")
        void testCannotAddSamePlayerTwice() {
            SimPartyData party = new SimPartyData(player1Id, "Leader", SimQuestType.PVE_COOP);
            assertTrue(party.addMember(player2Id, "Member"));
            assertFalse(party.addMember(player2Id, "Member"), "Should not add duplicate");
        }

        @Test
        @DisplayName("Cannot remove leader via removeMember")
        void testCannotRemoveLeader() {
            SimPartyData party = new SimPartyData(player1Id, "Leader", SimQuestType.PVE_COOP);
            assertFalse(party.removeMember(player1Id));
        }

        @Test
        @DisplayName("Cannot start quest without minimum players")
        void testCannotStartWithoutMinPlayers() {
            SimPartyData party = new SimPartyData(player1Id, "Leader", SimQuestType.RAID_BOSS);
            // RAID_BOSS needs min 2 players
            assertFalse(party.canStartQuest());
        }

        @Test
        @DisplayName("Cannot start quest if not all ready")
        void testCannotStartIfNotAllReady() {
            SimPartyData party = new SimPartyData(player1Id, "Leader", SimQuestType.RAID_BOSS);
            party.addMember(player2Id, "Member");
            // Member not ready
            assertFalse(party.canStartQuest());
        }

        @Test
        @DisplayName("Cannot invite to party not in FORMING state")
        void testCannotInviteWhenNotForming() {
            SimPartyData party = new SimPartyData(player1Id, "Leader", SimQuestType.PVE_COOP);
            party.startQuest(UUID.randomUUID());

            assertFalse(party.createInvite(player2Id));
        }

        @Test
        @DisplayName("Cannot set ready for non-member")
        void testCannotSetReadyForNonMember() {
            SimPartyData party = new SimPartyData(player1Id, "Leader", SimQuestType.PVE_COOP);
            assertFalse(party.setReady(player2Id, true));
        }
    }

    // ============================================
    // Ready State Transitions
    // ============================================

    @Nested
    @DisplayName("Ready State Transitions")
    class ReadyStateTransitions {

        @Test
        @DisplayName("State transitions from FORMING to READY when all ready")
        void testFormingToReadyTransition() {
            SimPartyData party = new SimPartyData(player1Id, "Leader", SimQuestType.RAID_BOSS);
            party.addMember(player2Id, "Member");

            assertEquals(SimPartyState.FORMING, party.getState());

            party.setReady(player2Id, true);
            assertEquals(SimPartyState.READY, party.getState());
        }

        @Test
        @DisplayName("State transitions back to FORMING when member unreadies")
        void testReadyToFormingTransition() {
            SimPartyData party = new SimPartyData(player1Id, "Leader", SimQuestType.RAID_BOSS);
            party.addMember(player2Id, "Member");
            party.setReady(player2Id, true);
            assertEquals(SimPartyState.READY, party.getState());

            // Member unreadies
            party.setReady(player2Id, false);
            assertEquals(SimPartyState.FORMING, party.getState());
        }

        @Test
        @DisplayName("Cannot add member when party is in READY state")
        void testCannotAddMemberInReadyState() {
            SimPartyData party = new SimPartyData(player1Id, "Leader", SimQuestType.RAID_BOSS);
            party.addMember(player2Id, "Member");
            party.setReady(player2Id, true);
            assertEquals(SimPartyState.READY, party.getState());
            assertTrue(party.allMembersReady());

            // Try to add third member while in READY state
            // This should fail - can only add members while FORMING
            assertFalse(party.addMember(player3Id, "NewMember"),
                "Should not be able to add member in READY state");
            assertEquals(2, party.getMemberCount(), "Member count should remain 2");

            // Member 2 unreadies, state goes back to FORMING
            party.setReady(player2Id, false);
            assertEquals(SimPartyState.FORMING, party.getState());

            // NOW we can add the third member
            assertTrue(party.addMember(player3Id, "NewMember"),
                "Should add member in FORMING state");
            assertEquals(3, party.getMemberCount());

            // New member is not ready by default
            assertFalse(party.isReady(player3Id));
            assertFalse(party.allMembersReady());

            // All members ready up
            party.setReady(player2Id, true);
            party.setReady(player3Id, true);
            assertTrue(party.allMembersReady());
            assertTrue(party.canStartQuest());
        }
    }

    // ============================================
    // Parameterized Flow Tests
    // ============================================

    @Nested
    @DisplayName("Parameterized Flow Tests")
    class ParameterizedFlows {

        @ParameterizedTest
        @CsvSource({
            "PVE_COOP, 1, true",
            "PVE_COOP, 2, true",
            "PVE_COOP, 6, true",
            "RAID_BOSS, 1, false",
            "RAID_BOSS, 2, true",
            "RAID_BOSS, 10, true",
            "EVENT, 1, true",
            "EVENT, 10, true"
        })
        @DisplayName("Party can start with given player count")
        void testCanStartWithPlayerCount(String questTypeStr, int playerCount, boolean expectedCanStart) {
            SimQuestType questType = SimQuestType.valueOf(questTypeStr);
            SimPartyData party = new SimPartyData(player1Id, "Leader", questType);

            // Add additional players
            for (int i = 1; i < playerCount; i++) {
                UUID playerId = UUID.randomUUID();
                party.addMember(playerId, "P" + i);
                party.setReady(playerId, true);
            }

            assertEquals(expectedCanStart, party.canStartQuest(),
                String.format("QuestType=%s, players=%d", questTypeStr, playerCount));
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "minecraft:zombie",
            "minecraft:skeleton",
            "minecraft:wither_skeleton",
            "minecraft:blaze",
            "minecraft:enderman",
            "minecraft:vindicator"
        })
        @DisplayName("All standard mobs can be selected")
        void testAllStandardMobsSelectable(String mobId) {
            SimPartyData party = new SimPartyData(player1Id, "Leader", SimQuestType.PVE_COOP);

            assertTrue(party.setSelectedMobId(player1Id, SimResourceLocation.of(mobId)));
            assertEquals(mobId, Objects.requireNonNull(party.getSelectedMobId()).toString());
        }
    }
}
