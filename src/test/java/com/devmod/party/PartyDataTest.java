package com.devmod.party;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.devmod.endurance.QuestType;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PartyData")
class PartyDataTest {

    private UUID leaderId;
    private PartyData party;

    @BeforeEach
    void setUp() {
        leaderId = UUID.randomUUID();
        party = new PartyData(leaderId, "Leader", QuestType.PVE_COOP);
    }

    // === Construction ===

    @Test
    @DisplayName("New party has leader as only member in FORMING state")
    void newPartyHasLeaderAsMember() {
        assertNotNull(party.getPartyId());
        assertEquals(leaderId, party.getLeaderId());
        assertEquals("Leader", party.getLeaderName());
        assertEquals(1, party.getMemberCount());
        assertTrue(party.hasMember(leaderId));
        assertTrue(party.isLeader(leaderId));
        assertTrue(party.isReady(leaderId)); // leader always ready
        assertEquals(PartyData.PartyState.FORMING, party.getState());
        assertEquals(QuestType.PVE_COOP, party.getQuestType());
    }

    // === Member Management ===

    @Nested
    @DisplayName("Member Management")
    class MemberManagement {

        @Test
        @DisplayName("Add member increases count and returns true")
        void addMemberSuccess() {
            UUID memberId = UUID.randomUUID();
            assertTrue(party.addMember(memberId, "Player2"));
            assertEquals(2, party.getMemberCount());
            assertTrue(party.hasMember(memberId));
            assertFalse(party.isReady(memberId)); // new member not ready
            assertEquals("Player2", party.getMemberName(memberId));
        }

        @Test
        @DisplayName("Cannot add same member twice")
        void cannotAddDuplicate() {
            UUID memberId = UUID.randomUUID();
            assertTrue(party.addMember(memberId, "Player2"));
            assertFalse(party.addMember(memberId, "Player2"));
            assertEquals(2, party.getMemberCount());
        }

        @Test
        @DisplayName("Cannot add member when party is full")
        void cannotAddWhenFull() {
            // PVE_COOP max is 6
            for (int i = 0; i < 5; i++) {
                assertTrue(party.addMember(UUID.randomUUID(), "P" + i));
            }
            assertEquals(6, party.getMemberCount());
            assertFalse(party.addMember(UUID.randomUUID(), "Overflow"));
        }

        @Test
        @DisplayName("Remove member succeeds for non-leader")
        void removeMemberSuccess() {
            UUID memberId = UUID.randomUUID();
            party.addMember(memberId, "Player2");
            assertTrue(party.removeMember(memberId));
            assertEquals(1, party.getMemberCount());
            assertFalse(party.hasMember(memberId));
        }

        @Test
        @DisplayName("Cannot remove leader via removeMember")
        void cannotRemoveLeader() {
            assertFalse(party.removeMember(leaderId));
            assertEquals(1, party.getMemberCount());
        }

        @Test
        @DisplayName("Remove non-existent member returns false")
        void removeNonExistentMember() {
            assertFalse(party.removeMember(UUID.randomUUID()));
        }

        @Test
        @DisplayName("Kick member only works for leader")
        void kickMemberOnlyLeader() {
            UUID memberId = UUID.randomUUID();
            party.addMember(memberId, "Player2");

            UUID nonLeader = UUID.randomUUID();
            assertFalse(party.kickMember(nonLeader, memberId));
            assertTrue(party.kickMember(leaderId, memberId));
            assertFalse(party.hasMember(memberId));
        }
    }

    // === Ready Status ===

    @Nested
    @DisplayName("Ready Status")
    class ReadyStatus {

        @Test
        @DisplayName("Set ready for existing member returns true")
        void setReadyForMember() {
            UUID memberId = UUID.randomUUID();
            party.addMember(memberId, "Player2");
            assertFalse(party.isReady(memberId));
            assertTrue(party.setReady(memberId, true));
            assertTrue(party.isReady(memberId));
        }

        @Test
        @DisplayName("Set ready for non-member returns false")
        void setReadyForNonMember() {
            assertFalse(party.setReady(UUID.randomUUID(), true));
        }

        @Test
        @DisplayName("allMembersReady when all ready")
        void allMembersReady() {
            // Leader is already ready. Add one more member.
            UUID memberId = UUID.randomUUID();
            party.addMember(memberId, "Player2");
            assertFalse(party.allMembersReady());
            party.setReady(memberId, true);
            assertTrue(party.allMembersReady());
        }

        @Test
        @DisplayName("isReady returns false for non-member UUID")
        void isReadyNonMember() {
            assertFalse(party.isReady(UUID.randomUUID()));
        }
    }

    // === Kit Management ===

    @Nested
    @DisplayName("Kit Management")
    class KitManagement {

        @Test
        @DisplayName("Default kit is STARTER")
        void defaultKitIsStarter() {
            assertEquals("STARTER", party.getMemberKitId(leaderId));
        }

        @Test
        @DisplayName("Set kit for member returns true")
        void setKitForMember() {
            assertTrue(party.setMemberKit(leaderId, "WARRIOR"));
            assertEquals("WARRIOR", party.getMemberKitId(leaderId));
        }

        @Test
        @DisplayName("Blank kit normalizes to STARTER")
        void blankKitNormalizesToStarter() {
            party.setMemberKit(leaderId, "WARRIOR");
            party.setMemberKit(leaderId, "");
            assertEquals("STARTER", party.getMemberKitId(leaderId));
        }

        @Test
        @DisplayName("Null kit normalizes to STARTER")
        void nullKitNormalizesToStarter() {
            party.setMemberKit(leaderId, "WARRIOR");
            party.setMemberKit(leaderId, null);
            assertEquals("STARTER", party.getMemberKitId(leaderId));
        }

        @Test
        @DisplayName("Changing kit resets ready status")
        void changingKitResetsReady() {
            UUID memberId = UUID.randomUUID();
            party.addMember(memberId, "Player2");
            party.setReady(memberId, true);
            assertTrue(party.isReady(memberId));
            party.setMemberKit(memberId, "MAGE");
            assertFalse(party.isReady(memberId));
        }

        @Test
        @DisplayName("Setting same kit does not reset ready")
        void settingSameKitKeepsReady() {
            UUID memberId = UUID.randomUUID();
            party.addMember(memberId, "Player2");
            party.setMemberKit(memberId, "WARRIOR");
            party.setReady(memberId, true);
            assertTrue(party.isReady(memberId));
            party.setMemberKit(memberId, "WARRIOR"); // same kit
            assertTrue(party.isReady(memberId));
        }

        @Test
        @DisplayName("Cannot set kit during IN_QUEST state")
        void cannotSetKitInQuest() {
            party.forceStartQuest(UUID.randomUUID());
            assertFalse(party.setMemberKit(leaderId, "MAGE"));
        }
    }

    // === Invite Management ===

    @Nested
    @DisplayName("Invite Management")
    class InviteManagement {

        @Test
        @DisplayName("canInvite true when forming and not full")
        void canInviteWhenForming() {
            assertTrue(party.canInvite());
        }

        @Test
        @DisplayName("Create invite returns non-null invite")
        void createInviteSuccess() {
            UUID targetId = UUID.randomUUID();
            PartyInvite invite = party.createInvite(targetId, "Target");
            assertNotNull(invite);
            assertEquals(1, party.getPendingInviteCount());
        }

        @Test
        @DisplayName("Cannot invite existing member")
        void cannotInviteExistingMember() {
            assertNull(party.createInvite(leaderId, "Leader"));
        }

        @Test
        @DisplayName("Adding invited member clears their pending invite")
        void addingMemberClearsPendingInvite() {
            UUID targetId = UUID.randomUUID();
            PartyInvite invite = party.createInvite(targetId, "Target");
            assertNotNull(invite);
            assertEquals(1, party.getPendingInviteCount());
            party.addMember(targetId, "Target");
            assertEquals(0, party.getPendingInviteCount());
        }

        @Test
        @DisplayName("Get invite by ID returns correct invite")
        void getInviteById() {
            UUID targetId = UUID.randomUUID();
            PartyInvite created = party.createInvite(targetId, "Target");
            assertNotNull(created);
            PartyInvite retrieved = party.getInvite(created.getInviteId());
            assertSame(created, retrieved);
        }

        @Test
        @DisplayName("removeInvite with cancel marks invite CANCELLED")
        void removeInviteWithCancel() {
            UUID targetId = UUID.randomUUID();
            PartyInvite invite = party.createInvite(targetId, "Target");
            assertNotNull(invite);
            PartyInvite removed = party.removeInvite(invite.getInviteId(), true);
            assertNotNull(removed);
            assertEquals(PartyInvite.InviteStatus.CANCELLED, removed.getStatus());
            assertEquals(0, party.getPendingInviteCount());
        }

        @Test
        @DisplayName("removeInvite with null ID returns null")
        void removeInviteNullId() {
            assertNull(party.removeInvite(null, false));
        }

        @Test
        @DisplayName("clearPendingInvites cancels all")
        void clearPendingInvites() {
            party.createInvite(UUID.randomUUID(), "A");
            party.createInvite(UUID.randomUUID(), "B");
            assertEquals(2, party.getPendingInviteCount());
            var cleared = party.clearPendingInvites();
            assertEquals(2, cleared.size());
            assertEquals(0, party.getPendingInviteCount());
            cleared.forEach(inv -> assertEquals(PartyInvite.InviteStatus.CANCELLED, inv.getStatus()));
        }

        @Test
        @DisplayName("clearPendingInvites on empty returns empty list")
        void clearPendingInvitesEmpty() {
            var cleared = party.clearPendingInvites();
            assertTrue(cleared.isEmpty());
        }

        @Test
        @DisplayName("cleanupExpiredInvites removes expired invites")
        void cleanupExpiredInvites() {
            UUID targetId = UUID.randomUUID();
            party.createInvite(targetId, "Target");
            assertEquals(1, party.getPendingInviteCount());
            // Simulate time passing beyond timeout
            long futureTime = System.currentTimeMillis() + PartyInvite.TIMEOUT_MS + 1000;
            var result = party.cleanupExpiredInvites(futureTime);
            assertEquals(1, result.expiredCount());
            assertEquals(0, party.getPendingInviteCount());
        }
    }

    // === Leadership ===

    @Nested
    @DisplayName("Leadership")
    class Leadership {

        @Test
        @DisplayName("Transfer leadership to member succeeds")
        void transferLeadershipSuccess() {
            UUID memberId = UUID.randomUUID();
            party.addMember(memberId, "NewLeader");
            assertTrue(party.transferLeadership(leaderId, memberId));
            assertEquals(memberId, party.getLeaderId());
            assertEquals("NewLeader", party.getLeaderName());
            assertTrue(party.isReady(memberId)); // new leader is auto-ready
        }

        @Test
        @DisplayName("Non-leader cannot transfer leadership")
        void nonLeaderCannotTransfer() {
            UUID memberId = UUID.randomUUID();
            UUID otherId = UUID.randomUUID();
            party.addMember(memberId, "M1");
            party.addMember(otherId, "M2");
            assertFalse(party.transferLeadership(memberId, otherId));
        }

        @Test
        @DisplayName("Cannot transfer to non-member")
        void cannotTransferToNonMember() {
            assertFalse(party.transferLeadership(leaderId, UUID.randomUUID()));
        }

        @Test
        @DisplayName("Cannot transfer to self")
        void cannotTransferToSelf() {
            assertFalse(party.transferLeadership(leaderId, leaderId));
        }
    }

    // === Quest State ===

    @Nested
    @DisplayName("Quest State")
    class QuestState {

        @Test
        @DisplayName("Solo play allowed for PVE_COOP")
        void soloPlayAllowed() {
            // Single leader in PVE_COOP can start
            assertTrue(party.canStartQuest());
        }

        @Test
        @DisplayName("startQuest transitions to IN_QUEST")
        void startQuestTransitions() {
            UUID instanceId = UUID.randomUUID();
            assertTrue(party.startQuest(instanceId));
            assertEquals(PartyData.PartyState.IN_QUEST, party.getState());
            assertEquals(instanceId, party.getInstanceId());
        }

        @Test
        @DisplayName("finishQuest returns to FORMING and resets non-leader ready")
        void finishQuestResetsState() {
            UUID memberId = UUID.randomUUID();
            party.addMember(memberId, "Player2");
            party.setReady(memberId, true);
            party.startQuest(UUID.randomUUID());

            party.finishQuest();
            assertEquals(PartyData.PartyState.FORMING, party.getState());
            assertNull(party.getInstanceId());
            assertFalse(party.isReady(memberId)); // reset
            assertTrue(party.isReady(leaderId)); // leader still ready
        }

        @Test
        @DisplayName("disband sets DISBANDED and cancels invites")
        void disbandSetsState() {
            party.createInvite(UUID.randomUUID(), "Target");
            party.disband();
            assertEquals(PartyData.PartyState.DISBANDED, party.getState());
            assertEquals(0, party.getPendingInviteCount());
        }

        @Test
        @DisplayName("forceStartQuest works in FORMING state")
        void forceStartQuest() {
            UUID instanceId = UUID.randomUUID();
            assertTrue(party.forceStartQuest(instanceId));
            assertEquals(PartyData.PartyState.IN_QUEST, party.getState());
        }

        @Test
        @DisplayName("forceStartQuest fails when DISBANDED")
        void forceStartQuestFails() {
            party.disband();
            assertFalse(party.forceStartQuest(UUID.randomUUID()));
        }

        @Test
        @DisplayName("Cannot add member when not FORMING")
        void cannotAddMemberWhenNotForming() {
            party.startQuest(UUID.randomUUID());
            assertFalse(party.addMember(UUID.randomUUID(), "Late"));
        }
    }

    // === Quest Type ===

    @Nested
    @DisplayName("Quest Type")
    class QuestTypeTests {

        @Test
        @DisplayName("Set quest type when forming succeeds")
        void setQuestTypeWhenForming() {
            assertTrue(party.setQuestType(QuestType.EVENT));
            assertEquals(QuestType.EVENT, party.getQuestType());
        }

        @Test
        @DisplayName("Cannot set quest type when not forming")
        void cannotSetQuestTypeWhenNotForming() {
            party.startQuest(UUID.randomUUID());
            assertFalse(party.setQuestType(QuestType.EVENT));
        }

        @Test
        @DisplayName("Cannot set quest type if current members exceed new max")
        void cannotSetQuestTypeIfTooManyMembers() {
            // PVE_COOP max is 6, fill to 6
            for (int i = 0; i < 5; i++) {
                party.addMember(UUID.randomUUID(), "P" + i);
            }
            assertEquals(6, party.getMemberCount());
            // RAID_BOSS min=4, max=10 -> would fit
            assertTrue(party.setQuestType(QuestType.RAID_BOSS));
        }
    }

    // === Getters ===

    @Test
    @DisplayName("isFull returns true when at capacity")
    void isFullAtCapacity() {
        assertFalse(party.isFull());
        for (int i = 0; i < 5; i++) {
            party.addMember(UUID.randomUUID(), "P" + i);
        }
        assertTrue(party.isFull());
    }

    @Test
    @DisplayName("getRemainingSlots accounts for members and pending invites")
    void getRemainingSlots() {
        // PVE_COOP max = 6, 1 leader already in
        assertEquals(5, party.getRemainingSlots());
        party.addMember(UUID.randomUUID(), "P1");
        assertEquals(4, party.getRemainingSlots());
        party.createInvite(UUID.randomUUID(), "Target");
        assertEquals(3, party.getRemainingSlots());
    }

    @Test
    @DisplayName("getEffectiveMobId returns default zombie when none selected")
    void effectiveMobIdDefault() {
        assertNotNull(party.getEffectiveMobId());
        assertEquals("zombie", party.getEffectiveMobId().getPath());
    }

    @Test
    @DisplayName("toString contains useful info")
    void toStringContainsInfo() {
        String str = party.toString();
        assertTrue(str.contains("PartyData"));
        assertTrue(str.contains("Leader"));
        assertTrue(str.contains("FORMING"));
    }
}
