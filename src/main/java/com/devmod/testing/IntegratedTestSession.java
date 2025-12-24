package com.devmod.testing;

import com.devmod.endurance.EnduranceQuestRegistry;
import com.devmod.client.overlay.*;
import com.devmod.telemetry.TelemetryService;
import com.devmod.util.I18n;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.*;

/**
 * Unified service that integrates:
 * - TestingHub (test case management)
 * - EnduranceQuest (controlled test environment)
 * - TelemetryService (data collection)
 * - HUD Overlays (real-time feedback)
 *
 * This is the "collante" (glue) that connects all testing systems.
 *
 * Workflow:
 * 1. User selects test type via QuickTestWizard or TestingHub
 * 2. IntegratedTestSession configures overlays and starts telemetry
 * 3. EnduranceQuest provides controlled mob spawning
 * 4. TelemetryService collects combat data
 * 5. Test completion triggers auto-validation in TestingHub
 */
public class IntegratedTestSession {
    private static final Logger LOGGER = LoggerFactory.getLogger(IntegratedTestSession.class);

    public static final IntegratedTestSession INSTANCE = new IntegratedTestSession();

    // Session state
    private boolean sessionActive = false;
    private TestSessionType currentType = null;
    private String sessionId = null;
    private Instant sessionStart = null;

    // Quest tracking
    private ResourceLocation currentMob = null;
    private int targetWaves = 0;
    private int completedWaves = 0;

    // Test case linkage
    @Nullable
    private TestCase linkedTestCase = null;

    // Overlay configuration saved before session
    private final Map<String, Boolean> previousOverlayState = new HashMap<>();

    // Session results
    private final SessionResults results = new SessionResults();

    /**
     * Types of integrated test sessions.
     */
    public enum TestSessionType {
        COMBAT_BASIC("Combat Basic", "Test basic melee/ranged combat mechanics"),
        COMBAT_ADVANCED("Combat Advanced", "Test complex combat scenarios with multiple enemies"),
        BOSS_FIGHT("Boss Fight", "Test boss mechanics and phases"),
        SURVIVAL_WAVES("Survival Waves", "Survive multiple waves of enemies"),
        DAMAGE_VALIDATION("Damage Validation", "Validate damage calculations and hitboxes"),
        PERFORMANCE_STRESS("Performance Stress", "Stress test with many entities"),
        CUSTOM("Custom", "User-configured test session");

        private final String displayName;
        private final String description;

        TestSessionType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    private IntegratedTestSession() {}

    // ========== Session Lifecycle ==========

    /**
     * Start a new integrated test session.
     *
     * @param type Session type
     * @param mobId Target mob for the test
     * @param waves Number of waves (0 for endless)
     * @param testCase Optional linked TestCase for auto-completion
     */
    public void startSession(TestSessionType type, ResourceLocation mobId, int waves, @Nullable TestCase testCase) {
        if (sessionActive) {
            LOGGER.warn("[IntegratedTest] Session already active, ending previous session first");
            endSession(SessionOutcome.INTERRUPTED);
        }

        LOGGER.info("[IntegratedTest] Starting {} session with mob {} for {} waves",
            type.name(), mobId, waves);

        // Initialize session
        sessionActive = true;
        currentType = type;
        currentMob = mobId;
        targetWaves = waves;
        completedWaves = 0;
        linkedTestCase = testCase;
        sessionId = generateSessionId();
        sessionStart = Instant.now();
        results.reset();

        // Save current overlay states
        saveOverlayStates();

        // Configure overlays based on test type
        configureOverlaysForSession(type);

        // Start telemetry (client-side only - server telemetry handled by EnduranceQuestManager)
        Minecraft mc = Minecraft.getInstance();
        TelemetryService.INSTANCE.resumeRecording();

        // Enable status overlay
        TelemetryStatusOverlay.setEnabled(true);
        TelemetryStatusOverlay.resetTimer();

        // Mark linked test as in progress
        TestCase linkedTest = linkedTestCase;
        if (linkedTest != null && linkedTest.getStatus() == TestCase.TestStatus.PENDING) {
            linkedTest.startTest();
        }

        // Notify user
        Player player = mc.player;
        if (player != null) {
            player.displayClientMessage(
                I18n.translate("devmod.test.session_started", type.getDisplayName()),
                false
            );
        }
    }

    /**
     * Quick start with minimal configuration.
     */
    public void quickStart(TestSessionType type) {
        // Find a suitable mob for the test type
        ResourceLocation defaultMob = getDefaultMobForType(type);
        int defaultWaves = getDefaultWavesForType(type);
        startSession(type, defaultMob, defaultWaves, null);
    }

    /**
     * End the current session with a specific outcome.
     */
    public void endSession(SessionOutcome outcome) {
        if (!sessionActive) return;

        LOGGER.info("[IntegratedTest] Ending session with outcome: {}", outcome);

        // Calculate final results
        results.outcome = outcome;
        results.duration = sessionStart != null ?
            java.time.Duration.between(sessionStart, Instant.now()) : java.time.Duration.ZERO;
        results.wavesCompleted = completedWaves;

        // End telemetry (client-side only - pauses recording)
        TelemetryService.INSTANCE.pauseRecording();

        // Process linked test case
        TestCase testCase = linkedTestCase;
        if (testCase != null) {
            switch (outcome) {
                case COMPLETED, VICTORY -> testCase.markPassed("Completed via IntegratedTest session");
                case DEATH, FAILED -> testCase.markFailed("Failed via IntegratedTest", "Outcome: " + outcome);
                case ABANDONED, INTERRUPTED -> {} // Leave test in progress state
            }
        }

        // Restore overlay states
        restoreOverlayStates();

        // Notify user
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player != null) {
            String key = outcome.isSuccess() ? "devmod.test.session_ended_success" : "devmod.test.session_ended_fail";
            player.displayClientMessage(
                I18n.translate(key, outcome.getDisplayName()),
                false
            );

            // Show summary
            player.displayClientMessage(
                I18n.translate("devmod.test.session_summary",
                    formatDuration(results.duration),
                    completedWaves,
                    targetWaves,
                    results.totalKills
                ),
                false
            );
        }

        // Reset state
        sessionActive = false;
        currentType = null;
        currentMob = null;
        linkedTestCase = null;
        sessionId = null;
        sessionStart = null;
    }

    /**
     * Called when a wave is completed in EnduranceQuest.
     */
    public void onWaveCompleted(int waveNumber, int kills) {
        if (!sessionActive) return;

        completedWaves = waveNumber;
        results.totalKills += kills;

        LOGGER.debug("[IntegratedTest] Wave {} completed with {} kills", waveNumber, kills);

        // Check if target reached
        if (targetWaves > 0 && completedWaves >= targetWaves) {
            endSession(SessionOutcome.COMPLETED);
        }
    }

    /**
     * Called when player dies during test.
     */
    public void onPlayerDeath() {
        if (!sessionActive) return;

        results.deaths++;

        // For SURVIVAL tests, death ends the session
        if (currentType == TestSessionType.SURVIVAL_WAVES ||
            currentType == TestSessionType.BOSS_FIGHT) {
            endSession(SessionOutcome.DEATH);
        }
    }

    /**
     * Called when player abandons the quest.
     */
    public void onQuestAbandoned() {
        if (sessionActive) {
            endSession(SessionOutcome.ABANDONED);
        }
    }

    // ========== Overlay Management ==========

    private void saveOverlayStates() {
        previousOverlayState.clear();
        previousOverlayState.put("debug", com.devmod.ModConfig.showBodyPartBoxes);
        previousOverlayState.put("bossPhase", BossPhaseOverlay.isEnabled());
        previousOverlayState.put("telemetry", TelemetryStatusOverlay.isEnabled());
        previousOverlayState.put("entityDensity", EntityDensityOverlay.isEnabled());
        previousOverlayState.put("endurance", EnduranceQuestOverlay.isEnabled());
    }

    private void restoreOverlayStates() {
        if (previousOverlayState.containsKey("debug")) {
            com.devmod.ModConfig.showBodyPartBoxes = previousOverlayState.get("debug");
        }
        if (previousOverlayState.containsKey("bossPhase")) {
            BossPhaseOverlay.setEnabled(previousOverlayState.get("bossPhase"));
        }
        if (previousOverlayState.containsKey("telemetry")) {
            TelemetryStatusOverlay.setEnabled(previousOverlayState.get("telemetry"));
        }
        if (previousOverlayState.containsKey("entityDensity")) {
            EntityDensityOverlay.setEnabled(previousOverlayState.get("entityDensity"));
        }
        if (previousOverlayState.containsKey("endurance")) {
            EnduranceQuestOverlay.setEnabled(previousOverlayState.get("endurance"));
        }
    }

    private void configureOverlaysForSession(TestSessionType type) {
        // Disable all first
        com.devmod.ModConfig.showBodyPartBoxes = false;
        BossPhaseOverlay.setEnabled(false);
        EntityDensityOverlay.setEnabled(false);

        // Always enable quest HUD and telemetry
        EnduranceQuestOverlay.setEnabled(true);
        TelemetryStatusOverlay.setEnabled(true);

        // Enable specific overlays based on type
        switch (type) {
            case COMBAT_BASIC, COMBAT_ADVANCED, DAMAGE_VALIDATION -> {
                com.devmod.ModConfig.showBodyPartBoxes = true;
            }
            case BOSS_FIGHT -> {
                BossPhaseOverlay.setEnabled(true);
                com.devmod.ModConfig.showBodyPartBoxes = true;
            }
            case PERFORMANCE_STRESS -> {
                EntityDensityOverlay.setEnabled(true);
            }
            case SURVIVAL_WAVES -> {
                // Minimal overlays for immersion
            }
            case CUSTOM -> {
                // Keep user's previous settings
                restoreOverlayStates();
                EnduranceQuestOverlay.setEnabled(true);
                TelemetryStatusOverlay.setEnabled(true);
            }
        }
    }

    // ========== Helper Methods ==========

    private ResourceLocation getDefaultMobForType(TestSessionType type) {
        return switch (type) {
            case COMBAT_BASIC -> ResourceLocation.withDefaultNamespace("zombie");
            case COMBAT_ADVANCED -> ResourceLocation.withDefaultNamespace("skeleton");
            case BOSS_FIGHT -> {
                // Try to find a boss mob
                var bosses = EnduranceQuestRegistry.INSTANCE.getBossMobs();
                yield bosses.isEmpty() ?
                    ResourceLocation.withDefaultNamespace("wither") :
                    bosses.get(0).mobId;
            }
            case SURVIVAL_WAVES -> ResourceLocation.withDefaultNamespace("zombie");
            case DAMAGE_VALIDATION -> ResourceLocation.withDefaultNamespace("zombie");
            case PERFORMANCE_STRESS -> ResourceLocation.withDefaultNamespace("zombie");
            case CUSTOM -> ResourceLocation.withDefaultNamespace("zombie");
        };
    }

    private int getDefaultWavesForType(TestSessionType type) {
        return switch (type) {
            case COMBAT_BASIC -> 3;
            case COMBAT_ADVANCED -> 5;
            case BOSS_FIGHT -> 1;
            case SURVIVAL_WAVES -> 0; // Endless
            case DAMAGE_VALIDATION -> 2;
            case PERFORMANCE_STRESS -> 5;
            case CUSTOM -> 5;
        };
    }

    private String generateSessionId() {
        return "test_" + System.currentTimeMillis() + "_" +
               (currentType != null ? currentType.name().toLowerCase() : "unknown");
    }

    private String formatDuration(java.time.Duration duration) {
        long seconds = duration.getSeconds();
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    // ========== Getters ==========

    public boolean isSessionActive() { return sessionActive; }

    public TestSessionType getCurrentType() { return currentType; }

    public ResourceLocation getCurrentMob() { return currentMob; }

    public int getTargetWaves() { return targetWaves; }

    public int getCompletedWaves() { return completedWaves; }

    public String getSessionId() { return sessionId; }

    public Instant getSessionStart() { return sessionStart; }

    public SessionResults getResults() { return results; }

    @Nullable
    public TestCase getLinkedTestCase() { return linkedTestCase; }

    /**
     * Get progress as 0.0 - 1.0 for UI display.
     */
    public float getProgress() {
        if (!sessionActive || targetWaves <= 0) return 0f;
        return Math.min(1f, (float) completedWaves / targetWaves);
    }

    // ========== Inner Classes ==========

    public enum SessionOutcome {
        COMPLETED("Completed", true),
        VICTORY("Victory!", true),
        DEATH("Death", false),
        FAILED("Failed", false),
        ABANDONED("Abandoned", false),
        INTERRUPTED("Interrupted", false);

        private final String displayName;
        private final boolean success;

        SessionOutcome(String displayName, boolean success) {
            this.displayName = displayName;
            this.success = success;
        }

        public String getDisplayName() { return displayName; }
        public boolean isSuccess() { return success; }
    }

    public static class SessionResults {
        public SessionOutcome outcome = null;
        public java.time.Duration duration = java.time.Duration.ZERO;
        public int wavesCompleted = 0;
        public int totalKills = 0;
        public int deaths = 0;
        public float avgDps = 0f;
        public int damageTaken = 0;
        public int damageDealt = 0;

        public void reset() {
            outcome = null;
            duration = java.time.Duration.ZERO;
            wavesCompleted = 0;
            totalKills = 0;
            deaths = 0;
            avgDps = 0f;
            damageTaken = 0;
            damageDealt = 0;
        }
    }
}
