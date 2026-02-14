package com.devmod.actions.catalog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.devmod.actions.ActionOrigin;
import com.devmod.actions.catalog.ActionSpec.ActionChannel;
import com.devmod.actions.domains.HandlerRegistry;

/**
 * Validates the entire action catalog for consistency, correctness, and completeness.
 * Returns a {@link CatalogValidationReport} with violations grouped by severity.
 */
public final class ActionCatalogValidator {

    /** Pattern for valid action IDs. */
    private static final Pattern ID_PATTERN = Pattern.compile(
        "^devmod\\.[a-z0-9_]+(?:\\.[a-z0-9_]+)+$");

    private ActionCatalogValidator() {}

    /**
     * Runs all validation rules against the catalog and returns a report.
     *
     * @param catalog         the catalog to validate
     * @param handlerRegistry optional handler registry for handler-ref validation (may be null)
     * @return validation report with all violations
     */
    public static CatalogValidationReport validate(ActionCatalog catalog,
                                                    @Nullable HandlerRegistry handlerRegistry) {
        Objects.requireNonNull(catalog, "catalog");
        List<Violation> violations = new ArrayList<>();

        Collection<ActionSpec> allSpecs = catalog.getAll();

        checkUniqueIds(allSpecs, violations);
        checkOriginCompatibility(allSpecs, violations);
        checkPermissionLevels(allSpecs, violations);
        checkNamingConvention(allSpecs, violations);
        checkUiMeta(allSpecs, violations);

        if (handlerRegistry != null) {
            checkHandlerRefs(allSpecs, handlerRegistry, violations);
        }

        return new CatalogValidationReport(violations);
    }

    /**
     * Validates that all IDs are unique (should be enforced by catalog, but double-check).
     */
    private static void checkUniqueIds(Collection<ActionSpec> specs, List<Violation> violations) {
        Set<String> seen = new HashSet<>();
        for (ActionSpec spec : specs) {
            if (!seen.add(spec.id())) {
                violations.add(new Violation(
                    Severity.ERROR, "uniqueIds",
                    "Duplicate action ID: " + spec.id(), spec.id()));
            }
        }
    }

    /**
     * Validates origin compatibility: CLIENT actions must not allow NETWORK origin.
     */
    private static void checkOriginCompatibility(Collection<ActionSpec> specs,
                                                  List<Violation> violations) {
        for (ActionSpec spec : specs) {
            if (spec.channel() == ActionChannel.CLIENT
                    && spec.allowsOrigin(ActionOrigin.NETWORK)) {
                violations.add(new Violation(
                    Severity.ERROR, "originCompatibility",
                    "CLIENT action allows NETWORK origin: " + spec.id(), spec.id()));
            }
        }
    }

    /**
     * Validates permission levels are in 0-4 range (already enforced by constructor,
     * but check for robustness).
     */
    private static void checkPermissionLevels(Collection<ActionSpec> specs,
                                               List<Violation> violations) {
        for (ActionSpec spec : specs) {
            if (spec.permissionLevel() < 0 || spec.permissionLevel() > 4) {
                violations.add(new Violation(
                    Severity.ERROR, "permissionLevel",
                    "Permission level out of range (0-4): " + spec.permissionLevel()
                        + " for " + spec.id(), spec.id()));
            }
        }
    }

    /**
     * Validates naming convention: IDs should match devmod.domain.name pattern.
     */
    private static void checkNamingConvention(Collection<ActionSpec> specs,
                                               List<Violation> violations) {
        for (ActionSpec spec : specs) {
            if (!ID_PATTERN.matcher(spec.id()).matches()) {
                violations.add(new Violation(
                    Severity.WARNING, "namingConvention",
                    "Action ID doesn't follow naming convention: " + spec.id(), spec.id()));
            }
        }
    }

    /**
     * Validates that UiMeta has non-empty label and description keys.
     */
    private static void checkUiMeta(Collection<ActionSpec> specs, List<Violation> violations) {
        for (ActionSpec spec : specs) {
            if (spec.uiMeta().labelKey().isEmpty()) {
                violations.add(new Violation(
                    Severity.WARNING, "uiMeta",
                    "Empty labelKey for action: " + spec.id(), spec.id()));
            }
            if (spec.uiMeta().descKey().isEmpty()) {
                violations.add(new Violation(
                    Severity.INFO, "uiMeta",
                    "Empty descKey for action: " + spec.id(), spec.id()));
            }
        }
    }

    /**
     * Validates that handler references resolve to registered handlers.
     */
    private static void checkHandlerRefs(Collection<ActionSpec> specs,
                                          HandlerRegistry handlerRegistry,
                                          List<Violation> violations) {
        for (ActionSpec spec : specs) {
            if (!handlerRegistry.hasHandler(spec.id())) {
                violations.add(new Violation(
                    Severity.ERROR, "handlerRef",
                    "No handler registered for action: " + spec.id(), spec.id()));
            }
        }
    }

    // =========================================================================
    // Inner types
    // =========================================================================

    /**
     * Severity levels for validation violations.
     */
    public enum Severity {
        /** Must be fixed before deployment. */
        ERROR,
        /** Should be fixed, but not blocking. */
        WARNING,
        /** Informational, may be intentional. */
        INFO
    }

    /**
     * A single validation violation.
     */
    public record Violation(
        Severity severity,
        String rule,
        String message,
        @Nullable String actionId
    ) {
        public Violation {
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(rule, "rule");
            Objects.requireNonNull(message, "message");
        }
    }

    /**
     * Report containing all validation violations from a catalog check.
     */
    public record CatalogValidationReport(List<Violation> violations) {

        public CatalogValidationReport {
            violations = List.copyOf(violations);
        }

        /** Returns true if no errors were found. */
        public boolean isValid() {
            return violations.stream().noneMatch(v -> v.severity() == Severity.ERROR);
        }

        /** Returns violations of a specific severity. */
        public List<Violation> getBySeverity(Severity severity) {
            return violations.stream()
                .filter(v -> v.severity() == severity)
                .toList();
        }

        /** Returns the total number of violations. */
        public int totalViolations() {
            return violations.size();
        }

        /** Returns the number of ERROR violations. */
        public int errorCount() {
            return getBySeverity(Severity.ERROR).size();
        }

        /** Returns the number of WARNING violations. */
        public int warningCount() {
            return getBySeverity(Severity.WARNING).size();
        }
    }
}
