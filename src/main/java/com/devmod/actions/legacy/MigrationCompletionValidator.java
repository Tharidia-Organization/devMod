package com.devmod.actions.legacy;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.devmod.actions.ActionIds;
import com.devmod.actions.bindings.ActionBinding;
import com.devmod.actions.bindings.BindingRegistry;
import com.devmod.actions.catalog.ActionCatalog;
import com.devmod.actions.catalog.ActionCatalogValidator;
import com.devmod.actions.catalog.ActionCatalogValidator.CatalogValidationReport;
import com.devmod.actions.catalog.ActionSpec;
import com.devmod.actions.catalog.BaselineActionInventory;
import com.devmod.actions.domains.HandlerRegistry;

/**
 * Validates that the V2 migration is complete and safe to cutover.
 *
 * <p>Checks performed:
 * <ol>
 *   <li>Every ActionIds constant has a corresponding ActionSpec in the catalog</li>
 *   <li>Every ActionSpec has a registered handler in HandlerRegistry</li>
 *   <li>No orphan bindings (all bindings reference valid specs)</li>
 *   <li>Catalog validation passes with zero ERRORs</li>
 *   <li>Shadow mode mismatch rate below threshold</li>
 *   <li>All baseline actions accounted for</li>
 * </ol>
 */
public final class MigrationCompletionValidator {

    /** Default shadow mismatch threshold: 1% */
    public static final double DEFAULT_MISMATCH_THRESHOLD = 0.01;

    private final ActionCatalog catalog;
    private final HandlerRegistry handlerRegistry;
    private final BindingRegistry bindingRegistry;
    private final double mismatchThreshold;

    /**
     * Creates a validator with the default mismatch threshold (1%).
     */
    public MigrationCompletionValidator(ActionCatalog catalog,
                                         HandlerRegistry handlerRegistry,
                                         BindingRegistry bindingRegistry) {
        this(catalog, handlerRegistry, bindingRegistry, DEFAULT_MISMATCH_THRESHOLD);
    }

    /**
     * Creates a validator with a custom mismatch threshold.
     *
     * @param catalog          the action catalog
     * @param handlerRegistry  the handler registry
     * @param bindingRegistry  the binding registry
     * @param mismatchThreshold max allowed mismatch rate (0.0 to 1.0)
     */
    public MigrationCompletionValidator(ActionCatalog catalog,
                                         HandlerRegistry handlerRegistry,
                                         BindingRegistry bindingRegistry,
                                         double mismatchThreshold) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.handlerRegistry = Objects.requireNonNull(handlerRegistry, "handlerRegistry");
        this.bindingRegistry = Objects.requireNonNull(bindingRegistry, "bindingRegistry");
        this.mismatchThreshold = mismatchThreshold;
    }

    /**
     * Runs all validation checks and returns a readiness report.
     */
    public MigrationReadinessReport validate() {
        List<String> missingActions = new ArrayList<>();
        List<String> missingHandlers = new ArrayList<>();
        List<String> orphanBindings = new ArrayList<>();
        List<String> validationErrors = new ArrayList<>();

        // 1. Check every ActionIds constant has a catalog entry
        Set<String> allActionIds = extractActionIdConstants();
        for (String actionId : allActionIds) {
            if (!catalog.contains(actionId)) {
                missingActions.add(actionId);
            }
        }

        // 2. Check every catalog entry has a handler
        for (ActionSpec spec : catalog.getAll()) {
            if (!handlerRegistry.hasHandler(spec.id())) {
                missingHandlers.add(spec.id());
            }
        }

        // 3. Check for orphan bindings
        List<ActionBinding> allBindings = bindingRegistry.getBindingsOfType(ActionBinding.class);
        for (ActionBinding binding : allBindings) {
            if (!catalog.contains(binding.actionId())) {
                orphanBindings.add(binding.actionId() + " (" + binding.origin() + ")");
            }
        }

        // 4. Catalog validation
        CatalogValidationReport catalogReport = ActionCatalogValidator.validate(catalog, handlerRegistry);
        if (!catalogReport.isValid()) {
            for (var violation : catalogReport.getBySeverity(ActionCatalogValidator.Severity.ERROR)) {
                validationErrors.add(violation.rule() + ": " + violation.message());
            }
        }

        // 5. Shadow mode mismatch rate
        ShadowModeComparator.ShadowModeReport shadowReport = ShadowModeComparator.generateReport();
        double mismatchRate = 0.0;
        boolean shadowCheckPassed = true;
        if (shadowReport.totalComparisons() > 0) {
            mismatchRate = (double) shadowReport.totalMismatches() / shadowReport.totalComparisons();
            shadowCheckPassed = mismatchRate <= mismatchThreshold;
        }

        // 6. Coverage calculation
        int totalBaseline = allActionIds.size();
        int covered = totalBaseline - missingActions.size();
        double coveragePercent = totalBaseline > 0
            ? (covered * 100.0) / totalBaseline
            : 100.0;

        // Determine readiness
        boolean ready = missingActions.isEmpty()
            && missingHandlers.isEmpty()
            && orphanBindings.isEmpty()
            && validationErrors.isEmpty()
            && shadowCheckPassed;

        return new MigrationReadinessReport(
            ready,
            coveragePercent,
            totalBaseline,
            covered,
            List.copyOf(missingActions),
            List.copyOf(missingHandlers),
            List.copyOf(orphanBindings),
            List.copyOf(validationErrors),
            mismatchRate,
            shadowCheckPassed,
            mismatchThreshold
        );
    }

    /**
     * Extracts all String constant values from ActionIds via reflection.
     */
    static Set<String> extractActionIdConstants() {
        Set<String> ids = new HashSet<>();
        for (Field field : ActionIds.class.getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers())
                    && Modifier.isStatic(field.getModifiers())
                    && Modifier.isFinal(field.getModifiers())
                    && field.getType() == String.class) {
                try {
                    String value = (String) field.get(null);
                    if (value != null && value.startsWith("devmod.")) {
                        ids.add(value);
                    }
                } catch (IllegalAccessException e) {
                    // skip
                }
            }
        }
        return ids;
    }

    // =========================================================================
    // Report record
    // =========================================================================

    /**
     * Report describing migration readiness.
     *
     * @param ready             true if all checks pass and cutover is safe
     * @param coveragePercent   percentage of baseline actions with V2 specs
     * @param totalBaseline     total number of action IDs in ActionIds.java
     * @param coveredCount      number of baseline actions with V2 specs
     * @param missingActions    action IDs without catalog entries
     * @param missingHandlers   action IDs without registered handlers
     * @param orphanBindings    bindings referencing non-existent specs
     * @param validationErrors  catalog validation errors
     * @param mismatchRate      shadow mode mismatch rate (0.0 to 1.0)
     * @param shadowCheckPassed true if mismatch rate is below threshold
     * @param mismatchThreshold the configured mismatch threshold
     */
    public record MigrationReadinessReport(
        boolean ready,
        double coveragePercent,
        int totalBaseline,
        int coveredCount,
        List<String> missingActions,
        List<String> missingHandlers,
        List<String> orphanBindings,
        List<String> validationErrors,
        double mismatchRate,
        boolean shadowCheckPassed,
        double mismatchThreshold
    ) {
        public MigrationReadinessReport {
            missingActions = List.copyOf(missingActions);
            missingHandlers = List.copyOf(missingHandlers);
            orphanBindings = List.copyOf(orphanBindings);
            validationErrors = List.copyOf(validationErrors);
        }

        /**
         * Returns a human-readable summary of the report.
         */
        public String toSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Migration Readiness Report ===\n");
            sb.append("Ready: ").append(ready ? "YES" : "NO").append("\n");
            sb.append(String.format("Coverage: %.1f%% (%d/%d actions)%n",
                coveragePercent, coveredCount, totalBaseline));

            if (!missingActions.isEmpty()) {
                sb.append("Missing actions: ").append(missingActions.size()).append("\n");
                for (String id : missingActions) {
                    sb.append("  - ").append(id).append("\n");
                }
            }
            if (!missingHandlers.isEmpty()) {
                sb.append("Missing handlers: ").append(missingHandlers.size()).append("\n");
                for (String id : missingHandlers) {
                    sb.append("  - ").append(id).append("\n");
                }
            }
            if (!orphanBindings.isEmpty()) {
                sb.append("Orphan bindings: ").append(orphanBindings.size()).append("\n");
                for (String desc : orphanBindings) {
                    sb.append("  - ").append(desc).append("\n");
                }
            }
            if (!validationErrors.isEmpty()) {
                sb.append("Validation errors: ").append(validationErrors.size()).append("\n");
                for (String err : validationErrors) {
                    sb.append("  - ").append(err).append("\n");
                }
            }
            sb.append(String.format("Shadow mismatch rate: %.2f%% (threshold: %.2f%%)%n",
                mismatchRate * 100, mismatchThreshold * 100));
            sb.append("Shadow check: ").append(shadowCheckPassed ? "PASSED" : "FAILED").append("\n");

            return sb.toString();
        }
    }
}
