package com.devmod.actions.legacy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates the KPI targets from the V2 migration project plan.
 *
 * <p>KPIs tracked:
 * <ol>
 *   <li>DevModClientActions line reduction >= 70%</li>
 *   <li>Module coupling reduction >= 40%</li>
 *   <li>Zero orphan actions</li>
 *   <li>Zero crash/regressions</li>
 *   <li>P95 invoke latency improvement >= 20%</li>
 * </ol>
 *
 * <p>Each KPI is evaluated with a target and actual value, producing a pass/fail result.
 */
public final class KpiValidator {

    private KpiValidator() {}

    /**
     * A single KPI measurement.
     *
     * @param name        human-readable KPI name
     * @param target      the target value description
     * @param actual      the actual measured value description
     * @param targetValue the numeric target
     * @param actualValue the actual numeric value
     * @param passed      true if the KPI target is met
     */
    public record KpiResult(
        String name,
        String target,
        String actual,
        double targetValue,
        double actualValue,
        boolean passed
    ) {}

    /**
     * Aggregated KPI report.
     *
     * @param results     individual KPI results
     * @param allPassed   true if every KPI passed
     * @param passCount   number of KPIs that passed
     * @param totalCount  total number of KPIs
     * @param generatedAt when this report was generated
     */
    public record KpiReport(
        List<KpiResult> results,
        boolean allPassed,
        int passCount,
        int totalCount,
        Instant generatedAt
    ) {
        public KpiReport {
            results = List.copyOf(results);
        }

        /**
         * Returns a human-readable summary.
         */
        public String toSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== KPI Validation Report ===\n");
            sb.append(String.format("Result: %s (%d/%d passed)%n",
                allPassed ? "ALL PASS" : "SOME FAILED", passCount, totalCount));
            sb.append("\n");

            for (KpiResult r : results) {
                sb.append(r.passed() ? "[PASS] " : "[FAIL] ");
                sb.append(r.name()).append("\n");
                sb.append("  Target: ").append(r.target()).append("\n");
                sb.append("  Actual: ").append(r.actual()).append("\n");
            }

            return sb.toString();
        }
    }

    /**
     * Input data for KPI validation.
     *
     * @param originalClientActionsLines  original line count of DevModClientActions
     * @param currentClientActionsLines   current line count after migration
     * @param originalOutboundImports     original number of outbound imports from actions/
     * @param currentOutboundImports      current number of outbound imports from actions/
     * @param orphanActionCount           number of orphan actions detected
     * @param crashRegressionCount        number of crash/regression incidents
     * @param v1P95LatencyMs              V1 P95 invoke latency in ms
     * @param v2P95LatencyMs              V2 P95 invoke latency in ms
     */
    public record KpiInput(
        int originalClientActionsLines,
        int currentClientActionsLines,
        int originalOutboundImports,
        int currentOutboundImports,
        int orphanActionCount,
        int crashRegressionCount,
        double v1P95LatencyMs,
        double v2P95LatencyMs
    ) {}

    /**
     * Validates all KPIs against their targets.
     *
     * @param input the measured KPI input data
     * @return the KPI report
     */
    public static KpiReport validate(KpiInput input) {
        List<KpiResult> results = new ArrayList<>();

        // KPI 1: DevModClientActions reduction >= 70%
        double lineReduction = input.originalClientActionsLines() > 0
            ? ((input.originalClientActionsLines() - input.currentClientActionsLines()) * 100.0)
                / input.originalClientActionsLines()
            : 0.0;
        results.add(new KpiResult(
            "DevModClientActions Line Reduction",
            ">= 70% reduction",
            String.format("%.1f%% reduction (%d -> %d lines)",
                lineReduction, input.originalClientActionsLines(), input.currentClientActionsLines()),
            70.0,
            lineReduction,
            lineReduction >= 70.0
        ));

        // KPI 2: Module coupling reduction >= 40%
        double couplingReduction = input.originalOutboundImports() > 0
            ? ((input.originalOutboundImports() - input.currentOutboundImports()) * 100.0)
                / input.originalOutboundImports()
            : 0.0;
        results.add(new KpiResult(
            "Module Coupling Reduction",
            ">= 40% reduction in outbound imports",
            String.format("%.1f%% reduction (%d -> %d imports)",
                couplingReduction, input.originalOutboundImports(), input.currentOutboundImports()),
            40.0,
            couplingReduction,
            couplingReduction >= 40.0
        ));

        // KPI 3: Zero orphan actions
        results.add(new KpiResult(
            "Zero Orphan Actions",
            "0 orphan actions",
            input.orphanActionCount() + " orphan actions",
            0.0,
            input.orphanActionCount(),
            input.orphanActionCount() == 0
        ));

        // KPI 4: Zero crash/regressions
        results.add(new KpiResult(
            "Zero Crash/Regressions",
            "0 crash or regression incidents",
            input.crashRegressionCount() + " incidents",
            0.0,
            input.crashRegressionCount(),
            input.crashRegressionCount() == 0
        ));

        // KPI 5: P95 invoke latency improvement >= 20%
        double latencyImprovement = input.v1P95LatencyMs() > 0
            ? ((input.v1P95LatencyMs() - input.v2P95LatencyMs()) * 100.0)
                / input.v1P95LatencyMs()
            : 0.0;
        results.add(new KpiResult(
            "P95 Invoke Latency Improvement",
            ">= 20% improvement",
            String.format("%.1f%% improvement (%.2fms -> %.2fms)",
                latencyImprovement, input.v1P95LatencyMs(), input.v2P95LatencyMs()),
            20.0,
            latencyImprovement,
            latencyImprovement >= 20.0
        ));

        int passCount = (int) results.stream().filter(KpiResult::passed).count();
        boolean allPassed = passCount == results.size();

        return new KpiReport(results, allPassed, passCount, results.size(), Instant.now());
    }
}
