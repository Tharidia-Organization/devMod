package com.devmod.client.state;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.client.state.stores.ConfigStateStore;
import com.devmod.client.state.stores.MailboxStateStore;
import com.devmod.client.state.stores.NotificationStateStore;
import com.devmod.client.state.stores.PartyStateStore;
import com.devmod.client.state.stores.QuestStateStore;
import com.devmod.endurance.ComboSystem;
import com.devmod.endurance.EnduranceQuestState;

/**
 * Tests for ClientStateManager and state stores.
 */
class ClientStateManagerTest {

    private ClientStateManager manager;

    @BeforeEach
    void setUp() {
        manager = ClientStateManager.INSTANCE;
        manager.clearAll();
    }

    @AfterEach
    void tearDown() {
        manager.clearAll();
    }

    // ============================================================================
    // CLIENT STATE MANAGER
    // ============================================================================

    @Nested
    @DisplayName("ClientStateManager")
    class ClientStateManagerTests {

        @Test
        @DisplayName("Should be singleton")
        void shouldBeSingleton() {
            assertSame(ClientStateManager.INSTANCE, manager);
            assertNotNull(manager);
        }

        @Test
        @DisplayName("Should provide access to all stores")
        void shouldProvideAccessToAllStores() {
            assertNotNull(manager.getQuestStore());
            assertNotNull(manager.getPartyStore());
            assertNotNull(manager.getConfigStore());
            assertNotNull(manager.getMailboxStore());
            assertNotNull(manager.getNotificationStore());
        }

        @Test
        @DisplayName("Should return same store instances")
        void shouldReturnSameStoreInstances() {
            assertSame(manager.getQuestStore(), manager.getQuestStore());
            assertSame(manager.getPartyStore(), manager.getPartyStore());
            assertSame(manager.getConfigStore(), manager.getConfigStore());
            assertSame(manager.getMailboxStore(), manager.getMailboxStore());
            assertSame(manager.getNotificationStore(), manager.getNotificationStore());
        }

        @Test
        @DisplayName("Should clear all stores")
        void shouldClearAllStores() {
            UUID partyId = UUID.randomUUID();
            manager.getQuestStore().setState(questState(5, 10, 0, EnduranceQuestState.IN_PROGRESS));
            manager.getPartyStore().setState(partyState(partyId, true, List.of()));

            manager.clearAll();

            assertNull(manager.getQuestStore().getState());
            assertNull(manager.getPartyStore().getState());
            assertEquals(0, manager.getQuestStore().getCurrentWave());
            assertFalse(manager.getPartyStore().isInParty());
        }

        @Test
        @DisplayName("Should handle onConnect lifecycle")
        void shouldHandleOnConnect() {
            assertDoesNotThrow(() -> manager.onConnect());
            assertTrue(manager.isConnected());
        }

        @Test
        @DisplayName("Should handle onDisconnect lifecycle")
        void shouldHandleOnDisconnect() {
            manager.getQuestStore().setState(questState(10, 10, 0, EnduranceQuestState.IN_PROGRESS));
            manager.onConnect();

            manager.onDisconnect();

            assertFalse(manager.isConnected());
            assertNull(manager.getQuestStore().getState());
        }

        @Test
        @DisplayName("Should generate diagnostics")
        void shouldGenerateDiagnostics() {
            String diagnostics = manager.getDiagnostics();

            assertNotNull(diagnostics);
            assertFalse(diagnostics.isEmpty());
            assertTrue(diagnostics.contains("ClientStateManager"));
        }
    }

    // ============================================================================
    // QUEST STATE STORE
    // ============================================================================

    @Nested
    @DisplayName("QuestStateStore")
    class QuestStateStoreTests {

        private QuestStateStore store;

        @BeforeEach
        void setUp() {
            store = manager.getQuestStore();
            store.clear();
        }

        @Test
        @DisplayName("Should handle empty state defaults")
        void shouldHandleEmptyStateDefaults() {
            assertNull(store.getState());
            assertEquals(0, store.getCurrentWave());
            assertEquals(EnduranceQuestState.AVAILABLE, store.getQuestState());
            assertFalse(store.hasActiveQuest());
        }

        @Test
        @DisplayName("Should update state")
        void shouldUpdateState() {
            store.setState(questState(5, 10, 25, EnduranceQuestState.IN_PROGRESS));

            QuestStateStore.QuestState updated = store.getState();
            assertNotNull(updated);
            assertEquals(5, updated.currentWave());
            assertEquals(10, updated.maxWaves());
            assertEquals(25, updated.mobsKilled());
        }

        @Test
        @DisplayName("Should notify subscribers on update")
        void shouldNotifySubscribers() {
            AtomicBoolean notified = new AtomicBoolean(false);
            AtomicInteger newWave = new AtomicInteger(-1);

            store.subscribe((oldState, newState) -> {
                notified.set(true);
                if (newState != null) {
                    newWave.set(newState.currentWave());
                }
            });

            store.setState(questState(7, 10, 0, EnduranceQuestState.IN_PROGRESS));

            assertTrue(notified.get());
            assertEquals(7, newWave.get());
        }

        @Test
        @DisplayName("Should provide hasActiveQuest helper")
        void shouldProvideHasActiveQuestHelper() {
            assertFalse(store.hasActiveQuest());

            store.setState(questState(1, 10, 0, EnduranceQuestState.IN_PROGRESS));

            assertTrue(store.hasActiveQuest());
        }

        @Test
        @DisplayName("Should clear state")
        void shouldClearState() {
            store.setState(questState(10, 10, 100, EnduranceQuestState.IN_PROGRESS));

            store.clear();

            assertNull(store.getState());
            assertEquals(0, store.getCurrentWave());
        }
    }

    // ============================================================================
    // PARTY STATE STORE
    // ============================================================================

    @Nested
    @DisplayName("PartyStateStore")
    class PartyStateStoreTests {

        private PartyStateStore store;

        @BeforeEach
        void setUp() {
            store = manager.getPartyStore();
            store.clear();
        }

        @Test
        @DisplayName("Should handle empty state defaults")
        void shouldHandleEmptyStateDefaults() {
            assertNull(store.getState());
            assertFalse(store.isInParty());
            assertFalse(store.isLeader());
        }

        @Test
        @DisplayName("Should track party membership")
        void shouldTrackPartyMembership() {
            UUID partyId = UUID.randomUUID();
            UUID memberId = UUID.randomUUID();
            PartyStateStore.PartyMember member = partyMember(memberId, "TestPlayer", true);

            store.setState(partyState(partyId, true, List.of(member)));

            PartyStateStore.PartyState state = store.getState();
            assertNotNull(state);
            assertEquals(partyId, state.partyId());
            assertEquals(1, state.members().size());
            assertTrue(state.isLeader());
        }

        @Test
        @DisplayName("Should provide inParty helper")
        void shouldProvideInPartyHelper() {
            assertFalse(store.isInParty());

            store.setState(partyState(UUID.randomUUID(), false, List.of()));

            assertTrue(store.isInParty());
        }
    }

    // ============================================================================
    // CONFIG STATE STORE
    // ============================================================================

    @Nested
    @DisplayName("ConfigStateStore")
    class ConfigStateStoreTests {

        private ConfigStateStore store;

        @BeforeEach
        void setUp() {
            store = manager.getConfigStore();
            store.clear();
        }

        @Test
        @DisplayName("Should handle empty state defaults")
        void shouldHandleEmptyStateDefaults() {
            assertNull(store.getState());
            assertEquals(2.0, store.getHeadMultiplier());
            assertFalse(store.isFeatureEnabled("feature.test"));
        }

        @Test
        @DisplayName("Should update mechanics config")
        void shouldUpdateMechanicsConfig() {
            ConfigStateStore.ConfigState updated = ConfigStateStore.ConfigState.builder()
                .mechanics(Map.of(
                    "combat.headMultiplier", 2.0,
                    "combat.bodyMultiplier", 1.5,
                    "combat.limbsMultiplier", 0.8,
                    "combat.bodyPartDetection", 1.0
                ))
                .build();

            store.setState(updated);

            assertEquals(2.0, store.getHeadMultiplier());
            assertEquals(1.5, store.getBodyMultiplier());
        }

        @Test
        @DisplayName("Should track feature flags")
        void shouldTrackFeatureFlags() {
            ConfigStateStore.ConfigState updated = ConfigStateStore.ConfigState.builder()
                .featureFlags(Map.of("feature.test", true, "feature.other", false))
                .build();

            store.setState(updated);

            assertTrue(store.isFeatureEnabled("feature.test"));
            assertFalse(store.isFeatureEnabled("feature.other"));
        }
    }

    // ============================================================================
    // MAILBOX STATE STORE
    // ============================================================================

    @Nested
    @DisplayName("MailboxStateStore")
    class MailboxStateStoreTests {

        private MailboxStateStore store;

        @BeforeEach
        void setUp() {
            store = manager.getMailboxStore();
            store.clear();
        }

        @Test
        @DisplayName("Should handle empty state defaults")
        void shouldHandleEmptyStateDefaults() {
            assertNull(store.getState());
            assertEquals(0, store.getUnreadCount());
            assertFalse(store.hasUnread());
        }

        @Test
        @DisplayName("Should track messages")
        void shouldTrackMessages() {
            MailboxStateStore.MailMessage msg = new MailboxStateStore.MailMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Sender",
                "Subject",
                "Body",
                System.currentTimeMillis(),
                false,
                false,
                null,
                MailboxStateStore.MessagePriority.NORMAL,
                MailboxStateStore.MessageType.PLAYER
            );

            MailboxStateStore.MailboxState state = new MailboxStateStore.MailboxState(
                List.of(msg),
                List.of(),
                1,
                0,
                1,
                100,
                System.currentTimeMillis()
            );

            store.setState(state);

            assertNotNull(store.getState());
            assertEquals(1, store.getState().messages().size());
            assertEquals(1, store.getUnreadCount());
        }

        @Test
        @DisplayName("Should provide hasUnread helper")
        void shouldProvideHasUnreadHelper() {
            MailboxStateStore.MailboxState state = new MailboxStateStore.MailboxState(
                List.of(),
                List.of(),
                3,
                0,
                3,
                100,
                System.currentTimeMillis()
            );

            store.setState(state);

            assertTrue(store.hasUnread());
        }
    }

    // ============================================================================
    // NOTIFICATION STATE STORE
    // ============================================================================

    @Nested
    @DisplayName("NotificationStateStore")
    class NotificationStateStoreTests {

        private NotificationStateStore store;

        @BeforeEach
        void setUp() {
            store = manager.getNotificationStore();
            store.clear();
        }

        @Test
        @DisplayName("Should handle empty state defaults")
        void shouldHandleEmptyStateDefaults() {
            assertNull(store.getState());
            assertEquals(0, store.getUnreadCount());
            assertFalse(store.hasUnread());
            assertFalse(store.hasPendingToasts());
        }

        @Test
        @DisplayName("Should queue notifications")
        void shouldQueueNotifications() {
            NotificationStateStore.Notification notif = NotificationStateStore.Notification.builder()
                .title("Test")
                .message("Test message")
                .build();

            store.queueToast(notif);

            assertTrue(store.hasPendingToasts());
            assertNotNull(store.pollNextToast());
        }
    }

    // ============================================================================
    // BASE STATE STORE
    // ============================================================================

    @Nested
    @DisplayName("BaseStateStore")
    class BaseStateStoreTests {

        @Test
        @DisplayName("Should support multiple subscribers")
        void shouldSupportMultipleSubscribers() {
            QuestStateStore store = manager.getQuestStore();
            store.clear();
            AtomicInteger callCount = new AtomicInteger(0);

            store.subscribe((old, current) -> callCount.incrementAndGet());
            store.subscribe((old, current) -> callCount.incrementAndGet());
            store.subscribe((old, current) -> callCount.incrementAndGet());

            store.setState(questState(1, 10, 0, EnduranceQuestState.IN_PROGRESS));

            assertEquals(3, callCount.get());
        }

        @Test
        @DisplayName("Should handle subscriber exception gracefully")
        void shouldHandleSubscriberException() {
            QuestStateStore store = manager.getQuestStore();
            store.clear();
            AtomicBoolean secondCalled = new AtomicBoolean(false);

            store.subscribe((old, current) -> {
                throw new RuntimeException("First subscriber fails");
            });
            store.subscribe((old, current) -> {
                secondCalled.set(true);
            });

            assertDoesNotThrow(() -> store.setState(questState(1, 10, 0, EnduranceQuestState.IN_PROGRESS)));

            assertTrue(secondCalled.get());
        }

        @Test
        @DisplayName("Should provide previous state to subscribers")
        void shouldProvidePreviousState() {
            QuestStateStore store = manager.getQuestStore();
            store.clear();
            AtomicInteger previousWave = new AtomicInteger(-1);
            AtomicInteger currentWave = new AtomicInteger(-1);

            store.setState(questState(5, 10, 0, EnduranceQuestState.IN_PROGRESS));

            store.subscribe((old, current) -> {
                if (old != null && current != null) {
                    previousWave.set(old.currentWave());
                    currentWave.set(current.currentWave());
                }
            });

            store.setState(questState(10, 10, 0, EnduranceQuestState.IN_PROGRESS));

            assertEquals(5, previousWave.get());
            assertEquals(10, currentWave.get());
        }
    }

    // ============================================================================
    // THREAD SAFETY
    // ============================================================================

    @Nested
    @DisplayName("Thread Safety")
    class ThreadSafetyTests {

        @Test
        @DisplayName("Should handle concurrent updates")
        void shouldHandleConcurrentUpdates() throws InterruptedException {
            QuestStateStore store = manager.getQuestStore();
            store.clear();
            store.setState(questState(0, 10, 0, EnduranceQuestState.IN_PROGRESS));

            int threadCount = 10;
            int updatesPerThread = 100;

            Thread[] threads = new Thread[threadCount];
            for (int i = 0; i < threadCount; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < updatesPerThread; j++) {
                        store.updateState(state -> withQuestState(
                            state,
                            state != null ? state.state() : EnduranceQuestState.IN_PROGRESS,
                            state != null ? state.currentWave() : 0,
                            state != null ? state.maxWaves() : 10,
                            (state != null ? state.mobsKilled() : 0) + 1
                        ));
                    }
                });
            }

            for (Thread thread : threads) {
                thread.start();
            }
            for (Thread thread : threads) {
                thread.join();
            }

            assertNotNull(store.getState());
            assertEquals(threadCount * updatesPerThread, store.getState().mobsKilled());
        }

        @Test
        @DisplayName("Should handle concurrent subscriptions")
        void shouldHandleConcurrentSubscriptions() throws InterruptedException {
            QuestStateStore store = manager.getQuestStore();
            store.clear();
            AtomicInteger subscriptionCount = new AtomicInteger(0);
            int threadCount = 10;

            Thread[] threads = new Thread[threadCount];
            for (int i = 0; i < threadCount; i++) {
                threads[i] = new Thread(() -> {
                    store.subscribe((old, current) -> subscriptionCount.incrementAndGet());
                });
            }

            for (Thread thread : threads) {
                thread.start();
            }
            for (Thread thread : threads) {
                thread.join();
            }

            store.setState(questState(1, 10, 0, EnduranceQuestState.IN_PROGRESS));

            assertEquals(threadCount, subscriptionCount.get());
        }
    }

    private QuestStateStore.QuestState questState(
        int currentWave,
        int maxWaves,
        int mobsKilled,
        EnduranceQuestState state
    ) {
        return new QuestStateStore.QuestState(
            "quest",
            "template",
            state,
            currentWave,
            maxWaves,
            mobsKilled,
            0,
            0,
            0.0,
            0.0,
            0,
            0,
            0,
            1.0,
            ComboSystem.StyleRank.D,
            0,
            0,
            List.of(),
            System.currentTimeMillis()
        );
    }

    private QuestStateStore.QuestState withQuestState(
        QuestStateStore.QuestState base,
        EnduranceQuestState state,
        int currentWave,
        int maxWaves,
        int mobsKilled
    ) {
        QuestStateStore.QuestState safe = base != null ? base : QuestStateStore.QuestState.empty();
        return new QuestStateStore.QuestState(
            safe.questId(),
            safe.templateId(),
            state,
            currentWave,
            maxWaves,
            mobsKilled,
            safe.mobsKilledInWave(),
            safe.mobsRemaining(),
            safe.damageDealt(),
            safe.damageTaken(),
            safe.deaths(),
            safe.currentStreak(),
            safe.bestStreak(),
            safe.comboMultiplier(),
            safe.styleRank(),
            safe.elapsedTimeMs(),
            safe.rewardPoints(),
            safe.objectives(),
            System.currentTimeMillis()
        );
    }

    private PartyStateStore.PartyMember partyMember(UUID playerId, String name, boolean isLeader) {
        return new PartyStateStore.PartyMember(
            playerId,
            name,
            isLeader,
            true,
            20.0f,
            20.0f,
            false,
            null,
            0,
            System.currentTimeMillis()
        );
    }

    private PartyStateStore.PartyState partyState(UUID partyId, boolean isLeader, List<PartyStateStore.PartyMember> members) {
        return new PartyStateStore.PartyState(
            partyId,
            "TestParty",
            UUID.randomUUID(),
            isLeader,
            members,
            List.of(),
            4,
            false,
            System.currentTimeMillis()
        );
    }
}
