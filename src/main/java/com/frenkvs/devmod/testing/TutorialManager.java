package com.frenkvs.devmod.testing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.frenkvs.devmod.util.I18n;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import com.frenkvs.devmod.util.ConfigPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages the gamification tutorial system for QA testers.
 *
 * Features:
 * - Onboarding flow for new testers
 * - Step-by-step tutorial progression
 * - Achievement unlocks for milestones
 * - Suggested next tests based on category completion
 * - XP/Level system for motivation
 *
 * Tutorial Phases:
 * 1. WELCOME - First time setup and introduction
 * 2. BASICS - Learn how to use the QA system
 * 3. FIRST_TEST - Complete first test with guidance
 * 4. EXPLORATION - Free exploration with hints
 * 5. ADVANCED - Unlocked after completing 50% tests
 * 6. MASTERY - Unlocked after completing all tests
 */
public class TutorialManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TutorialManager.class);
    public static final TutorialManager INSTANCE = new TutorialManager();

    // Lazy initialization to avoid NPE during class loading (FMLPaths not ready yet)
    private static Path getTutorialFile() {
        return ConfigPaths.getTutorialFile();
    }
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // === Tutorial State (thread-safe for 100-1000 concurrent users) ===
    private volatile TutorialPhase currentPhase = TutorialPhase.WELCOME;
    private final AtomicInteger currentStep = new AtomicInteger(0);
    private final AtomicBoolean tutorialEnabled = new AtomicBoolean(true);
    private final AtomicBoolean onboardingCompleted = new AtomicBoolean(false);

    // === Gamification Stats (thread-safe) ===
    private final AtomicInteger totalXP = new AtomicInteger(0);
    private final AtomicInteger level = new AtomicInteger(1);
    private final List<String> unlockedAchievements = new CopyOnWriteArrayList<>();

    // === XP Values ===
    private static final int XP_TEST_PASS = 100;
    private static final int XP_TEST_FAIL = 25;  // Still get some XP for testing
    private static final int XP_FIRST_TEST = 250;
    private static final int XP_CATEGORY_COMPLETE = 500;
    private static final int XP_ALL_COMPLETE = 2000;
    private static final int XP_PER_LEVEL = 500;

    // === Thread-safety for file I/O ===
    private final ReentrantReadWriteLock fileLock = new ReentrantReadWriteLock();
    private volatile long lastSaveTime = 0;
    private static final long SAVE_COOLDOWN_MS = 500;

    // Async executor for file I/O operations - prevents main thread blocking
    // NOT final - recreated after shutdown when player re-enters world
    private volatile ExecutorService saveExecutor;
    private final AtomicBoolean saveInProgress = new AtomicBoolean(false);
    private final Object executorLock = new Object();

    /**
     * Ensures the save executor is available and running.
     * Creates a new executor if null or shutdown.
     * Thread-safe with double-checked locking.
     */
    private void ensureExecutor() {
        if (saveExecutor == null || saveExecutor.isShutdown()) {
            synchronized (executorLock) {
                if (saveExecutor == null || saveExecutor.isShutdown()) {
                    saveExecutor = Executors.newSingleThreadExecutor(r -> {
                        Thread t = new Thread(r, "DevMod-Tutorial-Save");
                        t.setDaemon(true);
                        return t;
                    });
                    LOGGER.debug("[DevMod] Tutorial save executor created/recreated");
                }
            }
        }
    }

    public enum TutorialPhase {
        WELCOME("Welcome", "Get started with DevMod QA Testing"),
        BASICS("Basics", "Learn the QA Testing interface"),
        FIRST_TEST("First Test", "Complete your first test"),
        EXPLORATION("Exploration", "Test freely with guidance"),
        ADVANCED("Advanced", "Master the testing system"),
        MASTERY("Mastery", "Complete all tests");

        private final String displayName;
        private final String description;

        TutorialPhase(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    // === Tutorial Steps ===

    /**
     * Represents a single tutorial step with instructions.
     */
    public static class TutorialStep {
        private final String title;
        private final String description;
        private final String[] instructions;
        private final String hint;
        private final TutorialAction action;

        public TutorialStep(String title, String description, String[] instructions, String hint, TutorialAction action) {
            this.title = title;
            this.description = description;
            this.instructions = instructions;
            this.hint = hint;
            this.action = action;
        }

        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String[] getInstructions() { return instructions; }
        public String getHint() { return hint; }
        public TutorialAction getAction() { return action; }
    }

    public enum TutorialAction {
        NONE,
        OPEN_QA_SCREEN,
        SELECT_TEST,
        START_TEST,
        COMPLETE_TEST,
        OPEN_CATEGORY,
        VIEW_REPORT
    }

    // All tutorial steps organized by phase
    private static final TutorialStep[][] TUTORIAL_STEPS = {
        // WELCOME phase
        {
            new TutorialStep(
                "Welcome to DevMod QA!",
                "You've been selected as a tester for DevMod.",
                new String[]{
                    "Your mission: Test all mod features",
                    "Complete tests to earn XP and level up",
                    "Help improve the mod by finding bugs"
                },
                "Press N to open the QA Testing screen",
                TutorialAction.OPEN_QA_SCREEN
            ),
            new TutorialStep(
                "Enter Your Name",
                "Identify yourself as the tester.",
                new String[]{
                    "Type your name in the text field",
                    "This will appear in test reports",
                    "Click 'Start QA Session' when ready"
                },
                "Your name helps track who found which bugs",
                TutorialAction.NONE
            )
        },
        // BASICS phase
        {
            new TutorialStep(
                "The QA Interface",
                "Let's explore the testing interface.",
                new String[]{
                    "LEFT: Categories list - click to filter tests",
                    "CENTER: Test cards - shows all tests in category",
                    "RIGHT: Test details - appears when you click a test"
                },
                "Categories are color-coded by completion progress",
                TutorialAction.NONE
            ),
            new TutorialStep(
                "Test Cards",
                "Understanding test information.",
                new String[]{
                    "Colored bar = Priority (Red=Critical, Yellow=Medium)",
                    "Status shown: PENDING, IN_PROGRESS, PASSED, FAILED",
                    "[AUTO] tag = Test can auto-complete",
                    "Click a test to see full details"
                },
                "Start with CRITICAL priority tests first",
                TutorialAction.SELECT_TEST
            ),
            new TutorialStep(
                "Test Actions",
                "How to complete tests.",
                new String[]{
                    "PASS - Test worked correctly",
                    "FAIL - Found a bug or issue",
                    "SKIP - Can't test right now",
                    "AUTO-CHECK - Let the system verify"
                },
                "Keyboard shortcuts: 1=Pass, 2=Fail, 3=Skip",
                TutorialAction.NONE
            )
        },
        // FIRST_TEST phase
        {
            new TutorialStep(
                "Your First Test",
                "Let's complete a test together!",
                new String[]{
                    "Select the 'Basic Melee Damage' test",
                    "Read the instructions carefully",
                    "Click the test to start it"
                },
                "This test checks if damage displays work",
                TutorialAction.START_TEST
            ),
            new TutorialStep(
                "Follow Instructions",
                "Each test has step-by-step instructions.",
                new String[]{
                    "1. Get a sword from creative",
                    "2. Find or spawn a zombie",
                    "3. Attack the zombie",
                    "4. Watch for damage numbers"
                },
                "The in-game HUD shows your progress",
                TutorialAction.NONE
            ),
            new TutorialStep(
                "Submit Result",
                "Mark the test based on what you observed.",
                new String[]{
                    "Did damage numbers appear? -> PASS",
                    "No damage shown or error? -> FAIL",
                    "Add comments to describe issues"
                },
                "First test completed = +250 XP bonus!",
                TutorialAction.COMPLETE_TEST
            )
        },
        // EXPLORATION phase
        {
            new TutorialStep(
                "Free Testing",
                "You're ready to test on your own!",
                new String[]{
                    "Choose tests from any category",
                    "Complete them in any order",
                    "Focus on HIGH priority first"
                },
                "Some tests auto-complete as you play",
                TutorialAction.NONE
            )
        },
        // ADVANCED phase
        {
            new TutorialStep(
                "Advanced Testing",
                "You've reached 50% completion!",
                new String[]{
                    "Try the harder tests now",
                    "Test edge cases and rare scenarios",
                    "Write detailed bug reports"
                },
                "Completing categories gives bonus XP",
                TutorialAction.NONE
            )
        },
        // MASTERY phase
        {
            new TutorialStep(
                "QA Master!",
                "Congratulations! All tests complete!",
                new String[]{
                    "You've tested everything!",
                    "Generate your final report",
                    "Share results with the mod author"
                },
                "Thank you for your dedication!",
                TutorialAction.VIEW_REPORT
            )
        }
    };

    private TutorialManager() {
        ensureExecutor(); // Initialize executor first
        loadProgress();
    }

    // === Phase Management ===

    /**
     * Get current tutorial step, or null if phase has no more steps.
     */
    public TutorialStep getCurrentStep() {
        TutorialStep[] phaseSteps = TUTORIAL_STEPS[currentPhase.ordinal()];
        int step = currentStep.get();
        if (step < phaseSteps.length) {
            return phaseSteps[step];
        }
        return null;
    }

    /**
     * Advance to next step or phase.
     * Returns true if there was a step to advance to.
     */
    public boolean advanceStep() {
        TutorialStep[] phaseSteps = TUTORIAL_STEPS[currentPhase.ordinal()];

        int newStep = currentStep.incrementAndGet();
        if (newStep >= phaseSteps.length) {
            // Try to advance to next phase
            return advancePhase();
        }

        saveProgress();
        return true;
    }

    /**
     * Advance to next phase.
     */
    private boolean advancePhase() {
        int nextPhaseOrdinal = currentPhase.ordinal() + 1;
        if (nextPhaseOrdinal < TutorialPhase.values().length) {
            currentPhase = TutorialPhase.values()[nextPhaseOrdinal];
            currentStep.set(0);

            // Announce phase change
            notifyPhaseChange();

            saveProgress();
            return true;
        }
        return false;
    }

    /**
     * Check and update phase based on progress.
     */
    public void checkPhaseProgress() {
        float progress = TestingSession.INSTANCE.getProgressPercent();
        int completed = TestingSession.INSTANCE.getCompletedTests();

        // Auto-advance based on milestones
        if (currentPhase == TutorialPhase.FIRST_TEST && completed >= 1) {
            setPhase(TutorialPhase.EXPLORATION);
        } else if (currentPhase == TutorialPhase.EXPLORATION && progress >= 50) {
            setPhase(TutorialPhase.ADVANCED);
            unlockAchievement("HALFWAY_THERE");
        } else if (currentPhase == TutorialPhase.ADVANCED && progress >= 100) {
            setPhase(TutorialPhase.MASTERY);
            unlockAchievement("QA_MASTER");
        }
    }

    public void setPhase(TutorialPhase phase) {
        if (phase != currentPhase) {
            currentPhase = phase;
            currentStep.set(0);
            notifyPhaseChange();
            saveProgress();
        }
    }

    private void notifyPhaseChange() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(
                I18n.translate("devmod.qa.phase_change", currentPhase.getDisplayName(), currentPhase.getDescription()),
                false
            );
        }
    }

    // === Gamification ===

    /**
     * Award XP for completing a test.
     */
    public void awardTestXP(TestCase test, boolean passed) {
        int xp = passed ? XP_TEST_PASS : XP_TEST_FAIL;

        // First test bonus
        if (TestingSession.INSTANCE.getCompletedTests() == 1) {
            xp += XP_FIRST_TEST;
            unlockAchievement("FIRST_TEST");
        }

        addXP(xp);

        // Check category completion
        checkCategoryCompletion(test.getCategory());

        // Update tutorial phase
        checkPhaseProgress();
    }

    private void checkCategoryCompletion(String category) {
        List<TestCase> tests = TestingSession.INSTANCE.getCategorizedTests().get(category);
        if (tests == null) return;

        boolean allComplete = tests.stream().allMatch(t ->
            t.getStatus() == TestCase.TestStatus.PASSED ||
            t.getStatus() == TestCase.TestStatus.FAILED ||
            t.getStatus() == TestCase.TestStatus.SKIPPED
        );

        if (allComplete) {
            addXP(XP_CATEGORY_COMPLETE);
            unlockAchievement("CATEGORY_" + category.toUpperCase().replace(" ", "_"));

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                    I18n.translate("devmod.testing.category_complete", category),
                    false
                );
            }
        }

        // Check total completion
        if (TestingSession.INSTANCE.getProgressPercent() >= 100) {
            addXP(XP_ALL_COMPLETE);
            unlockAchievement("ALL_TESTS_COMPLETE");
        }
    }

    public void addXP(int amount) {
        int newTotal = totalXP.addAndGet(amount);

        int newLevel = 1 + (newTotal / XP_PER_LEVEL);
        int currentLevel = level.get();
        if (newLevel > currentLevel) {
            level.set(newLevel);
            onLevelUp();
        }

        saveProgress();
    }

    private void onLevelUp() {
        Minecraft mc = Minecraft.getInstance();
        int currentLevel = level.get();
        if (mc.player != null) {
            mc.player.displayClientMessage(
                I18n.translate("devmod.testing.level_up", currentLevel),
                false
            );
        }

        // Level milestones
        if (currentLevel == 5) unlockAchievement("LEVEL_5");
        if (currentLevel == 10) unlockAchievement("LEVEL_10");
        if (currentLevel == 20) unlockAchievement("TESTER_PRO");
    }

    public void unlockAchievement(String achievementId) {
        if (!unlockedAchievements.contains(achievementId)) {
            unlockedAchievements.add(achievementId);

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                String name = getAchievementName(achievementId);
                mc.player.displayClientMessage(
                    I18n.translate("devmod.testing.achievement_unlocked", name),
                    false
                );
            }

            saveProgress();
        }
    }

    private String getAchievementName(String id) {
        return switch (id) {
            case "FIRST_TEST" -> "First Steps";
            case "HALFWAY_THERE" -> "Halfway There";
            case "QA_MASTER" -> "QA Master";
            case "LEVEL_5" -> "Getting Started";
            case "LEVEL_10" -> "Dedicated Tester";
            case "TESTER_PRO" -> "Professional QA";
            case "ALL_TESTS_COMPLETE" -> "Completionist";
            default -> id.replace("CATEGORY_", "").replace("_", " ");
        };
    }

    // === Suggestions ===

    /**
     * Get suggested next test based on current progress.
     */
    public TestCase getSuggestedTest() {
        // Priority order:
        // 1. In-progress tests
        // 2. Critical priority pending tests
        // 3. High priority in incomplete categories
        // 4. Any pending test

        // In-progress first
        for (TestCase test : TestingSession.INSTANCE.getAllTests()) {
            if (test.getStatus() == TestCase.TestStatus.IN_PROGRESS) {
                return test;
            }
        }

        // Critical priority
        for (TestCase test : TestingSession.INSTANCE.getAllTests()) {
            if (test.getStatus() == TestCase.TestStatus.PENDING &&
                test.getPriority() == TestCase.TestPriority.CRITICAL) {
                return test;
            }
        }

        // High priority
        for (TestCase test : TestingSession.INSTANCE.getAllTests()) {
            if (test.getStatus() == TestCase.TestStatus.PENDING &&
                test.getPriority() == TestCase.TestPriority.HIGH) {
                return test;
            }
        }

        // Any pending
        return TestingSession.INSTANCE.getNextPendingTest();
    }

    /**
     * Get a hint message for the current situation.
     */
    public String getContextualHint() {
        TutorialStep step = getCurrentStep();
        if (step != null && tutorialEnabled.get()) {
            return step.getHint();
        }

        // Generic hints based on progress
        float progress = TestingSession.INSTANCE.getProgressPercent();
        if (progress < 10) {
            return "Start with the Critical priority tests in 'Damage System'";
        } else if (progress < 50) {
            return "Great progress! Focus on HIGH priority tests next";
        } else if (progress < 90) {
            return "Almost there! Check for any skipped tests";
        } else {
            return "Final stretch! Complete the remaining tests";
        }
    }

    // === Persistence ===

    public void saveProgress() {
        // Rate limiting: skip if saved too recently (prevents I/O bottleneck with 100-1000 users)
        long now = System.currentTimeMillis();
        if (now - lastSaveTime < SAVE_COOLDOWN_MS) {
            return;
        }

        // Skip if save is already in progress
        if (!saveInProgress.compareAndSet(false, true)) {
            return;
        }

        lastSaveTime = now;

        // Capture current state snapshot for async save (avoid race conditions)
        final String snapshotPhase = currentPhase.name();
        final int snapshotStep = currentStep.get();
        final boolean snapshotTutorialEnabled = tutorialEnabled.get();
        final boolean snapshotOnboardingCompleted = onboardingCompleted.get();
        final int snapshotTotalXP = totalXP.get();
        final int snapshotLevel = level.get();
        final List<String> snapshotAchievements = new ArrayList<>(unlockedAchievements);

        // Submit async save task - does NOT block main thread
        // Ensure executor is available (may have been shut down during logout, recreate if needed)
        ensureExecutor();

        saveExecutor.submit(() -> {
            fileLock.writeLock().lock();
            try {
                Files.createDirectories(getTutorialFile().getParent());

                JsonObject root = new JsonObject();
                root.addProperty("phase", snapshotPhase);
                root.addProperty("step", snapshotStep);
                root.addProperty("tutorialEnabled", snapshotTutorialEnabled);
                root.addProperty("onboardingCompleted", snapshotOnboardingCompleted);
                root.addProperty("totalXP", snapshotTotalXP);
                root.addProperty("level", snapshotLevel);

                com.google.gson.JsonArray achievements = new com.google.gson.JsonArray();
                for (String achievement : snapshotAchievements) {
                    achievements.add(achievement);
                }
                root.add("achievements", achievements);

                // Atomic write: write to temp file then rename
                Path tempFile = getTutorialFile().resolveSibling(getTutorialFile().getFileName() + ".tmp");
                Files.writeString(tempFile, GSON.toJson(root), StandardCharsets.UTF_8);
                Files.move(tempFile, getTutorialFile(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                           java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                LOGGER.error("Failed to save tutorial progress: {}", e.getMessage());
            } finally {
                fileLock.writeLock().unlock();
                saveInProgress.set(false);
            }
        });
    }

    public void loadProgress() {
        fileLock.readLock().lock();
        try {
            if (!Files.exists(getTutorialFile())) return;

            String content = Files.readString(getTutorialFile(), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(content).getAsJsonObject();

            if (root.has("phase")) {
                currentPhase = TutorialPhase.valueOf(root.get("phase").getAsString());
            }
            if (root.has("step")) {
                currentStep.set(root.get("step").getAsInt());
            }
            if (root.has("tutorialEnabled")) {
                tutorialEnabled.set(root.get("tutorialEnabled").getAsBoolean());
            }
            if (root.has("onboardingCompleted")) {
                onboardingCompleted.set(root.get("onboardingCompleted").getAsBoolean());
            }
            if (root.has("totalXP")) {
                totalXP.set(root.get("totalXP").getAsInt());
            }
            if (root.has("level")) {
                level.set(root.get("level").getAsInt());
            }
            if (root.has("achievements")) {
                unlockedAchievements.clear();
                for (var elem : root.getAsJsonArray("achievements")) {
                    unlockedAchievements.add(elem.getAsString());
                }
            }

            LOGGER.info("Tutorial progress loaded: Phase={}, Level={}, XP={}",
                currentPhase, level.get(), totalXP.get());
        } catch (Exception e) {
            LOGGER.error("Failed to load tutorial progress: {}", e.getMessage());
        } finally {
            fileLock.readLock().unlock();
        }
    }

    /**
     * Reset tutorial progress (for testing or new users).
     */
    public void resetProgress() {
        currentPhase = TutorialPhase.WELCOME;
        currentStep.set(0);
        tutorialEnabled.set(true);
        onboardingCompleted.set(false);
        totalXP.set(0);
        level.set(1);
        unlockedAchievements.clear();
        saveProgress();
    }

    /**
     * Alias for resetProgress() - used by global reset.
     */
    public void reset() {
        resetProgress();
    }

    /**
     * Shutdown the save executor gracefully.
     * Called on player logout to ensure pending saves complete.
     */
    public void shutdown() {
        LOGGER.info("[DevMod] Shutting down tutorial save executor...");
        // Force a final save bypassing rate limiting
        lastSaveTime = 0;
        saveProgress();

        // Wait for pending saves (max 2 seconds)
        saveExecutor.shutdown();
        try {
            if (!saveExecutor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
                LOGGER.warn("[DevMod] Tutorial save did not complete in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // === Getters ===

    public TutorialPhase getCurrentPhase() { return currentPhase; }
    public int getCurrentStepIndex() { return currentStep.get(); }
    public boolean isTutorialEnabled() { return tutorialEnabled.get(); }
    public void setTutorialEnabled(boolean enabled) {
        tutorialEnabled.set(enabled);
        saveProgress();
    }
    public boolean isOnboardingCompleted() { return onboardingCompleted.get(); }
    public void setOnboardingCompleted(boolean completed) {
        onboardingCompleted.set(completed);
        if (completed && currentPhase == TutorialPhase.WELCOME) {
            setPhase(TutorialPhase.BASICS);
        }
        saveProgress();
    }
    public int getTotalXP() { return totalXP.get(); }
    public int getLevel() { return level.get(); }
    public int getXPToNextLevel() { return XP_PER_LEVEL - (totalXP.get() % XP_PER_LEVEL); }
    public float getLevelProgress() { return (totalXP.get() % XP_PER_LEVEL) / (float) XP_PER_LEVEL; }
    public List<String> getUnlockedAchievements() { return new ArrayList<>(unlockedAchievements); }
    public int getAchievementCount() { return unlockedAchievements.size(); }
}
