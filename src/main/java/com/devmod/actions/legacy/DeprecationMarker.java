package com.devmod.actions.legacy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility that identifies code to be cleaned up post-cutover.
 *
 * <p>After V2 cutover is confirmed stable, a number of legacy classes and
 * methods can be removed. This class provides an inventory of what can go,
 * when it can go, and the estimated code reduction.
 */
public final class DeprecationMarker {

    private DeprecationMarker() {}

    /**
     * When a deprecated class can be removed.
     */
    public enum RemovalPhase {
        /** After V2 cutover is stable (no rollbacks for 2 weeks) */
        POST_CUTOVER_STABLE,
        /** After V2_ONLY mode confirmed (no V1 fallback needed) */
        POST_V2_ONLY,
        /** Keep for emergency rollback capability */
        KEEP_FOR_ROLLBACK
    }

    /**
     * A class or method that can be deprecated/removed post-cutover.
     *
     * @param className       fully qualified class name
     * @param description     what the class does and why it's deprecated
     * @param estimatedLines  approximate lines of code
     * @param removalPhase    when it can safely be removed
     */
    public record DeprecatedEntry(
        String className,
        String description,
        int estimatedLines,
        RemovalPhase removalPhase
    ) {}

    /**
     * Returns the list of classes that can be removed after V2 is stable.
     */
    public static List<DeprecatedEntry> getDeprecatedClasses() {
        List<DeprecatedEntry> entries = new ArrayList<>();

        // Legacy compatibility bridge - remove after V2_ONLY confirmed
        entries.add(new DeprecatedEntry(
            "com.devmod.actions.legacy.LegacyCompatBridge",
            "Routes between V1 and V2 based on feature flags. "
                + "No longer needed when V2_ONLY is the sole path.",
            230,
            RemovalPhase.POST_V2_ONLY
        ));

        // Shadow mode comparator - remove after cutover stable
        entries.add(new DeprecatedEntry(
            "com.devmod.actions.legacy.ShadowModeComparator",
            "Compares V1 and V2 results during shadow mode. "
                + "No longer needed once shadow mode is permanently disabled.",
            370,
            RemovalPhase.POST_CUTOVER_STABLE
        ));

        // Legacy action adapter - keep for emergency rollback
        entries.add(new DeprecatedEntry(
            "com.devmod.actions.legacy.LegacyActionAdapter",
            "Adapts V1 RadialAction to V2 ActionSpec for migration. "
                + "Keep until rollback capability is no longer needed.",
            150,
            RemovalPhase.KEEP_FOR_ROLLBACK
        ));

        // Old registration methods in DevModClientActions
        entries.add(new DeprecatedEntry(
            "com.devmod.actions.client.DevModClientActions (legacy registration methods)",
            "registerUiActions(), registerDebugActions(), registerConfigActions(), "
                + "registerTelemetryActions() - replaced by DomainRegistrars.",
            2700,
            RemovalPhase.POST_V2_ONLY
        ));

        // Old server-side registration in DevModActions
        entries.add(new DeprecatedEntry(
            "com.devmod.actions.DevModActions (registerCommandActions)",
            "Server-side command registration now handled by domain registrars.",
            600,
            RemovalPhase.POST_V2_ONLY
        ));

        // ArenaActionRegistry (replaced by ArenaDomainRegistrar)
        entries.add(new DeprecatedEntry(
            "com.devmod.arena.command.ArenaActionRegistry",
            "Arena action registration replaced by ArenaDomainRegistrar.",
            250,
            RemovalPhase.POST_V2_ONLY
        ));

        // TestHarnessCommands registration (replaced by TestingDomainRegistrar)
        entries.add(new DeprecatedEntry(
            "com.devmod.gametest.TestHarnessCommands (registerActions)",
            "Test harness action registration replaced by TestingDomainRegistrar.",
            350,
            RemovalPhase.POST_V2_ONLY
        ));

        // TelemetryReloadCommand registration (replaced by TelemetryDomainRegistrar)
        entries.add(new DeprecatedEntry(
            "com.devmod.telemetry.TelemetryReloadCommand (registerActions)",
            "Telemetry action registration replaced by TelemetryDomainRegistrar.",
            400,
            RemovalPhase.POST_V2_ONLY
        ));

        // MailboxCommands registration (replaced by AdminDomainRegistrar)
        entries.add(new DeprecatedEntry(
            "com.devmod.mailbox.admin.MailboxCommands (registerActions)",
            "Mailbox/news action registration replaced by AdminDomainRegistrar.",
            200,
            RemovalPhase.POST_V2_ONLY
        ));

        // LeaderboardCommandEvents registration (replaced by AdminDomainRegistrar)
        entries.add(new DeprecatedEntry(
            "com.devmod.endurance.LeaderboardCommandEvents (registerActions)",
            "Leaderboard action registration replaced by AdminDomainRegistrar.",
            150,
            RemovalPhase.POST_V2_ONLY
        ));

        // Migration-specific utilities
        entries.add(new DeprecatedEntry(
            "com.devmod.actions.legacy.MigrationCompletionValidator",
            "Migration validation utility. No longer needed after cutover is stable.",
            200,
            RemovalPhase.POST_CUTOVER_STABLE
        ));

        entries.add(new DeprecatedEntry(
            "com.devmod.actions.legacy.CutoverOrchestrator",
            "Cutover orchestration utility. No longer needed after cutover is stable.",
            200,
            RemovalPhase.POST_CUTOVER_STABLE
        ));

        entries.add(new DeprecatedEntry(
            "com.devmod.actions.legacy.DeprecationMarker",
            "This utility itself. Remove after cleanup is complete.",
            250,
            RemovalPhase.POST_V2_ONLY
        ));

        return List.copyOf(entries);
    }

    /**
     * Returns a human-readable markdown report of what to clean up and when.
     */
    public static String getDeprecationReport() {
        List<DeprecatedEntry> entries = getDeprecatedClasses();

        StringBuilder sb = new StringBuilder();
        sb.append("# V2 Migration Deprecation Report\n\n");
        sb.append("Generated: ").append(Instant.now()).append("\n\n");

        // Group by removal phase
        for (RemovalPhase phase : RemovalPhase.values()) {
            List<DeprecatedEntry> phaseEntries = entries.stream()
                .filter(e -> e.removalPhase() == phase)
                .toList();

            if (phaseEntries.isEmpty()) continue;

            sb.append("## ").append(phaseLabel(phase)).append("\n\n");
            for (DeprecatedEntry entry : phaseEntries) {
                sb.append("- **").append(entry.className()).append("** (~")
                  .append(entry.estimatedLines()).append(" LOC)\n");
                sb.append("  ").append(entry.description()).append("\n\n");
            }
        }

        CodeReductionEstimate estimate = estimateCodeReduction();
        sb.append("## Summary\n\n");
        sb.append("- Total deprecated classes: ").append(entries.size()).append("\n");
        sb.append("- Estimated LOC reduction (post-cutover stable): ")
          .append(estimate.postCutoverStableLines()).append("\n");
        sb.append("- Estimated LOC reduction (post-V2-only): ")
          .append(estimate.postV2OnlyLines()).append("\n");
        sb.append("- Total potential LOC reduction: ")
          .append(estimate.totalRemovableLines()).append("\n");
        sb.append("- Lines kept for rollback: ")
          .append(estimate.keptForRollbackLines()).append("\n");

        return sb.toString();
    }

    /**
     * Estimates the code reduction from removing deprecated code.
     */
    public static CodeReductionEstimate estimateCodeReduction() {
        List<DeprecatedEntry> entries = getDeprecatedClasses();

        int postCutoverStable = 0;
        int postV2Only = 0;
        int keptForRollback = 0;

        for (DeprecatedEntry entry : entries) {
            switch (entry.removalPhase()) {
                case POST_CUTOVER_STABLE -> postCutoverStable += entry.estimatedLines();
                case POST_V2_ONLY -> postV2Only += entry.estimatedLines();
                case KEEP_FOR_ROLLBACK -> keptForRollback += entry.estimatedLines();
            }
        }

        int totalRemovable = postCutoverStable + postV2Only;

        return new CodeReductionEstimate(
            postCutoverStable,
            postV2Only,
            totalRemovable,
            keptForRollback
        );
    }

    private static String phaseLabel(RemovalPhase phase) {
        return switch (phase) {
            case POST_CUTOVER_STABLE -> "Phase 1: After Cutover Stable (2+ weeks)";
            case POST_V2_ONLY -> "Phase 2: After V2-Only Confirmed";
            case KEEP_FOR_ROLLBACK -> "Keep: Emergency Rollback Capability";
        };
    }

    /**
     * Estimate of code that can be removed.
     *
     * @param postCutoverStableLines LOC removable after cutover is stable
     * @param postV2OnlyLines        LOC removable after V2_ONLY confirmed
     * @param totalRemovableLines    total LOC that can eventually be removed
     * @param keptForRollbackLines   LOC kept for emergency rollback
     */
    public record CodeReductionEstimate(
        int postCutoverStableLines,
        int postV2OnlyLines,
        int totalRemovableLines,
        int keptForRollbackLines
    ) {}
}
