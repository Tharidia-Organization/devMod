package com.devmod;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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

        public float getBaseDamageBonus() { return baseDamageBonus; }
        public void setBaseDamageBonus(float v) { baseDamageBonus = v; }
        public float getHeadMult() { return headMult; }
        public void setHeadMult(float v) { headMult = v; }
        public float getBodyMult() { return bodyMult; }
        public void setBodyMult(float v) { bodyMult = v; }
        public float getArmsMult() { return armsMult; }
        public void setArmsMult(float v) { armsMult = v; }
        public float getLegsMult() { return legsMult; }
        public void setLegsMult(float v) { legsMult = v; }
        public float getArmorPenetration() { return armorPenetration; }
        public void setArmorPenetration(float v) { armorPenetration = v; }
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
        List<Future<?>> futures = new ArrayList<>(PLAYER_COUNT);

        for (int i = 0; i < PLAYER_COUNT; i++) {
            final int playerId = i;
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();

                    for (int action = 0; action < ACTIONS_PER_PLAYER; action++) {
                        MockWeaponStats stats = new MockWeaponStats();
                        stats.setBaseDamageBonus(playerId * 0.1f);
                        stats.setHeadMult(2.0f + (action * 0.01f));
                        stats.setBodyMult(1.0f);
                        stats.setArmsMult(0.75f);
                        stats.setLegsMult(0.5f);
                        stats.setArmorPenetration(0.1f * (playerId % 10));

                        if (stats.getHeadMult() < stats.getBodyMult()) {
                            hasRaceCondition.set(true);
                            errors.add("Player " + playerId + ": headMult < bodyMult race condition");
                        }

                        if (action % 10 == 0) {
                            LockSupport.parkNanos(1L);
                        }

                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    errors.add("Player " + playerId + ": " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            }));
        }

        startLatch.countDown();
        boolean completed = endLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();
        awaitFutures(futures);

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
        List<Future<?>> futures = new ArrayList<>(PLAYER_COUNT);

        for (int i = 0; i < PLAYER_COUNT; i++) {
            final int playerId = i;
            futures.add(executor.submit(() -> {
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
            }));
        }

        startLatch.countDown();
        boolean completed = endLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();
        awaitFutures(futures);

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
        List<Future<?>> futures = new ArrayList<>(PLAYER_COUNT);

        for (int i = 0; i < PLAYER_COUNT; i++) {
            final int playerId = i;
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();

                    for (int action = 0; action < ACTIONS_PER_PLAYER; action++) {
                        String weaponId = "weapon_" + (playerId % 20);
                        int targetId = (playerId * 100) + action;

                        weaponConfigs.computeIfAbsent(weaponId, k -> {
                            MockWeaponStats stats = new MockWeaponStats();
                            stats.setBaseDamageBonus(playerId * 0.5f);
                            return stats;
                        });

                        MockWeaponStats stats = weaponConfigs.get(weaponId);
                        if (stats != null) {
                            float dmg = stats.getBaseDamageBonus();
                            if (dmg < 0) {
                                errorCount.incrementAndGet();
                                errors.add("Player " + playerId + ": negative baseDamageBonus");
                            }
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
            }));
        }

        startLatch.countDown();
        boolean completed = endLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();
        awaitFutures(futures);

        assertTrue(completed, "Test timed out");
        assertEquals(0, errorCount.get(), "Errors occurred: " + errors);
        assertTrue(weaponConfigs.size() <= 20, "Should have max 20 unique weapons");
        assertTrue(pendingAttacks.size() > 0, "Pending attacks should be recorded");
        assertTrue(confirmedHits.size() > 0, "Confirmed hits should be recorded");
    }

    @Test
    @DisplayName("100 player concurrent CopyOnWriteArrayList operations")
    void testConcurrentListOperations() throws InterruptedException {
        CopyOnWriteArrayList<String> achievements = new CopyOnWriteArrayList<>();
        CopyOnWriteArrayList<String> logHistory = new CopyOnWriteArrayList<>();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(PLAYER_COUNT);
        List<Future<?>> futures = new ArrayList<>(PLAYER_COUNT);

        for (int i = 0; i < PLAYER_COUNT; i++) {
            final int playerId = i;
            futures.add(executor.submit(() -> {
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

                        List<String> snapshot = new ArrayList<>(achievements);
                        if (snapshot.size() > 10) {
                            errorCount.incrementAndGet();
                            errors.add("Achievement snapshot exceeded expected size");
                        }

                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    errors.add("Player " + playerId + ": " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            }));
        }

        startLatch.countDown();
        boolean completed = endLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();
        awaitFutures(futures);

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
        List<Future<?>> futures = new ArrayList<>(PLAYER_COUNT);

        for (int i = 0; i < PLAYER_COUNT; i++) {
            final int playerId = i;
            futures.add(executor.submit(() -> {
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
                                    float dmg = s.getBaseDamageBonus();
                                    if (dmg < 0) {
                                        errorCount.incrementAndGet();
                                        errors.add("Player " + playerId + ": negative baseDamageBonus");
                                    }
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
                                int xpVal = totalXP.get();
                                int lvl = level.get();
                                int achievementCount = achievements.size();
                                int pendingCount = pendingAttacks.size();
                                if (xpVal < 0 || lvl < 0 || achievementCount < 0 || pendingCount < 0) {
                                    errorCount.incrementAndGet();
                                    errors.add("Player " + playerId + ": invalid counters");
                                }
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
            }));
        }

        startLatch.countDown();
        boolean completed = endLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        executor.shutdown();
        awaitFutures(futures);

        assertTrue(completed, "Test timed out");
        assertEquals(0, errorCount.get(), "Errors occurred: " + errors);
        assertEquals(PLAYER_COUNT * ACTIONS_PER_PLAYER, successCount.get(),
            "All operations should complete successfully");
    }

    private static void awaitFutures(List<Future<?>> futures) {
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrupted while waiting for worker tasks", e);
            } catch (ExecutionException e) {
                fail("Worker task failed", e.getCause());
            }
        }
    }
}
