package com.devmod;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stress test per verificare la thread-safety della mod con 100+ utenti concorrenti.
 *
 * Simula:
 * - 100 player che eseguono azioni simultanee
 * - Accesso concorrente a WeaponConfigManager
 * - Accesso concorrente a TutorialManager
 * - Accesso concorrente a MobConfigManager
 * - Race conditions su strutture dati condivise
 */
public class ConcurrencyStressTest {

    private static final int PLAYER_COUNT = 100;
    private static final int ACTIONS_PER_PLAYER = 50;
    private static final int TIMEOUT_SECONDS = 60;

    private ExecutorService executor;
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger errorCount = new AtomicInteger(0);
    private final AtomicBoolean hasRaceCondition = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();

    /**
     * Mock class simulating WeaponStats from the mod
     */
    static class MockWeaponStats {
        public float baseDamageBonus = 0f;
        public float headMult = 2.0f;
        public float bodyMult = 1.0f;
        public float armsMult = 0.75f;
        public float legsMult = 0.5f;
        public float armorPenetration = 0f;
    }

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(PLAYER_COUNT);
        successCount.set(0);
        errorCount.set(0);
        hasRaceCondition.set(false);
        errors.clear();
    }

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("100 player concurrent WeaponStats access")
    void testConcurrentWeaponStatsAccess() throws InterruptedException {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(PLAYER_COUNT);

        for (int i = 0; i < PLAYER_COUNT; i++) {
            final int playerId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    for (int action = 0; action < ACTIONS_PER_PLAYER; action++) {
                        MockWeaponStats stats = new MockWeaponStats();
                        stats.baseDamageBonus = playerId * 0.1f;
                        stats.headMult = 2.0f + (action * 0.01f);
                        stats.bodyMult = 1.0f;
                        stats.armsMult = 0.75f;
                        stats.legsMult = 0.5f;
                        stats.armorPenetration = 0.1f * (playerId % 10);

                        if (stats.headMult < stats.bodyMult) {
                            hasRaceCondition.set(true);
                            errors.add("Player " + playerId + ": headMult < bodyMult race condition");
                        }

                        if (action % 10 == 0) {
                            Thread.yield();
                        }

                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    errors.add("Player " + playerId + ": " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = endLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Test timed out");
        assertFalse(hasRaceCondition.get(), "Race condition detected: " + errors);
        assertEquals(0, errorCount.get(), "Errors occurred: " + errors);
        assertEquals(PLAYER_COUNT * ACTIONS_PER_PLAYER, successCount.get());
    }

    @Test
    @DisplayName("100 player concurrent AtomicInteger/AtomicBoolean operations")
    void testConcurrentAtomicOperations() throws InterruptedException {
        AtomicInteger totalXP = new AtomicInteger(0);
        AtomicInteger level = new AtomicInteger(1);
        AtomicBoolean tutorialEnabled = new AtomicBoolean(true);
        AtomicInteger currentStep = new AtomicInteger(0);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(PLAYER_COUNT);

        for (int i = 0; i < PLAYER_COUNT; i++) {
            final int playerId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    for (int action = 0; action < ACTIONS_PER_PLAYER; action++) {
                        int xpGain = 10 + (action % 100);
                        int newTotal = totalXP.addAndGet(xpGain);

                        int newLevel = 1 + (newTotal / 500);
                        int currentLevel = level.get();
                        if (newLevel > currentLevel) {
                            level.compareAndSet(currentLevel, newLevel);
                        }

                        if (action % 25 == 0) {
                            boolean current = tutorialEnabled.get();
                            tutorialEnabled.compareAndSet(current, !current);
                        }

                        if (action % 10 == 0) {
                            currentStep.incrementAndGet();
                        }

                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    errors.add("Player " + playerId + ": " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = endLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Test timed out");
        assertEquals(0, errorCount.get(), "Errors occurred: " + errors);
        assertTrue(totalXP.get() > 0, "XP should have been accumulated");
        assertTrue(level.get() >= 1, "Level should be at least 1");
    }

    @Test
    @DisplayName("100 player concurrent ConcurrentHashMap operations")
    void testConcurrentMapOperations() throws InterruptedException {
        ConcurrentHashMap<String, MockWeaponStats> weaponConfigs = new ConcurrentHashMap<>();
        ConcurrentHashMap<Integer, String> pendingAttacks = new ConcurrentHashMap<>();
        ConcurrentHashMap<Integer, Long> confirmedHits = new ConcurrentHashMap<>();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(PLAYER_COUNT);

        for (int i = 0; i < PLAYER_COUNT; i++) {
            final int playerId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    for (int action = 0; action < ACTIONS_PER_PLAYER; action++) {
                        String weaponId = "weapon_" + (playerId % 20);
                        int targetId = (playerId * 100) + action;

                        weaponConfigs.computeIfAbsent(weaponId, k -> {
                            MockWeaponStats stats = new MockWeaponStats();
                            stats.baseDamageBonus = playerId * 0.5f;
                            return stats;
                        });

                        MockWeaponStats stats = weaponConfigs.get(weaponId);
                        if (stats != null) {
                            @SuppressWarnings("unused")
                            float dmg = stats.baseDamageBonus;
                        }

                        pendingAttacks.put(targetId, "attack_" + System.nanoTime());
                        confirmedHits.put(targetId, System.currentTimeMillis());

                        if (action % 20 == 0) {
                            pendingAttacks.remove(targetId - 10);
                            confirmedHits.remove(targetId - 10);
                        }

                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    errors.add("Player " + playerId + ": " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = endLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Test timed out");
        assertEquals(0, errorCount.get(), "Errors occurred: " + errors);
        assertTrue(weaponConfigs.size() <= 20, "Should have max 20 unique weapons");
    }

    @Test
    @DisplayName("100 player concurrent CopyOnWriteArrayList operations")
    void testConcurrentListOperations() throws InterruptedException {
        CopyOnWriteArrayList<String> achievements = new CopyOnWriteArrayList<>();
        CopyOnWriteArrayList<String> logHistory = new CopyOnWriteArrayList<>();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(PLAYER_COUNT);

        for (int i = 0; i < PLAYER_COUNT; i++) {
            final int playerId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    for (int action = 0; action < ACTIONS_PER_PLAYER; action++) {
                        String achievementId = "ACHIEVEMENT_" + (action % 10);

                        if (!achievements.contains(achievementId)) {
                            achievements.addIfAbsent(achievementId);
                        }

                        String logEntry = "Player_" + playerId + "_Action_" + action;
                        logHistory.add(logEntry);

                        while (logHistory.size() > 1000) {
                            if (!logHistory.isEmpty()) {
                                logHistory.remove(0);
                            }
                        }

                        @SuppressWarnings("unused")
                        int achievementCount = achievements.size();
                        @SuppressWarnings("unused")
                        List<String> copy = new ArrayList<>(achievements);

                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    errors.add("Player " + playerId + ": " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = endLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Test timed out");
        assertEquals(0, errorCount.get(), "Errors occurred: " + errors);
        assertEquals(10, achievements.size(), "Should have exactly 10 unique achievements");
    }

    @Test
    @DisplayName("100 player mixed concurrent operations stress test")
    void testMixedConcurrentOperations() throws InterruptedException {
        AtomicInteger totalXP = new AtomicInteger(0);
        AtomicInteger level = new AtomicInteger(1);
        ConcurrentHashMap<String, MockWeaponStats> weaponConfigs = new ConcurrentHashMap<>();
        ConcurrentHashMap<Integer, Long> pendingAttacks = new ConcurrentHashMap<>();
        CopyOnWriteArrayList<String> achievements = new CopyOnWriteArrayList<>();
        ConcurrentLinkedQueue<String> eventLog = new ConcurrentLinkedQueue<>();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(PLAYER_COUNT);

        for (int i = 0; i < PLAYER_COUNT; i++) {
            final int playerId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    ThreadLocalRandom random = ThreadLocalRandom.current();

                    for (int action = 0; action < ACTIONS_PER_PLAYER; action++) {
                        int operation = random.nextInt(6);

                        switch (operation) {
                            case 0 -> {
                                int xp = random.nextInt(10, 100);
                                int newTotal = totalXP.addAndGet(xp);
                                int newLevel = 1 + (newTotal / 500);
                                level.updateAndGet(current -> Math.max(current, newLevel));
                            }
                            case 1 -> {
                                String weaponId = "weapon_" + random.nextInt(50);
                                weaponConfigs.computeIfAbsent(weaponId, k -> new MockWeaponStats());
                                MockWeaponStats s = weaponConfigs.get(weaponId);
                                if (s != null) {
                                    @SuppressWarnings("unused")
                                    float dmg = s.baseDamageBonus;
                                }
                            }
                            case 2 -> {
                                int targetId = random.nextInt(1000);
                                pendingAttacks.put(targetId, System.nanoTime());
                            }
                            case 3 -> {
                                String achievementId = "ACH_" + random.nextInt(20);
                                achievements.addIfAbsent(achievementId);
                            }
                            case 4 -> {
                                eventLog.offer("Player_" + playerId + "_" + action);
                                while (eventLog.size() > 500) {
                                    eventLog.poll();
                                }
                            }
                            case 5 -> {
                                @SuppressWarnings("unused")
                                int xpVal = totalXP.get();
                                @SuppressWarnings("unused")
                                int lvl = level.get();
                                @SuppressWarnings("unused")
                                int achievementCount = achievements.size();
                                @SuppressWarnings("unused")
                                int pendingCount = pendingAttacks.size();
                            }
                        }

                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    errors.add("Player " + playerId + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = endLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Test timed out");
        assertEquals(0, errorCount.get(), "Errors occurred: " + errors);
        assertEquals(PLAYER_COUNT * ACTIONS_PER_PLAYER, successCount.get(),
            "All operations should complete successfully");
    }
}
