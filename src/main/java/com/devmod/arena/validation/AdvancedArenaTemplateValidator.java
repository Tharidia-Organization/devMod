package com.devmod.arena.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.arena.registry.ValidationResult;
public class AdvancedArenaTemplateValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdvancedArenaTemplateValidator.class);

    // DD40: Hard security limits
    public static final int MAX_ARENA_SIZE = 256;
    public static final int MAX_HAZARD_COUNT = 50;
    public static final int MAX_SPAWN_COUNT = 100;
    public static final int MAX_ENTITY_COUNT = 200;
    public static final int MAX_BLOCK_COUNT = 100_000;
    public static final int MAX_INHERITANCE_DEPTH = 5;

    private final List<ValidationRule> staticRules = new ArrayList<>();
    private final List<ValidationRule> runtimeRules = new ArrayList<>();

    public AdvancedArenaTemplateValidator() {
        registerDefaultRules();
    }

    /**
     * Registers the default validation rules.
     */
    private void registerDefaultRules() {
        // === Static Rules (schema validation) ===

        // Template ID required
        addStaticRuleInternal("template_id_required", ValidationSeverity.ERROR,
            ctx -> ctx.templateId() != null && !ctx.templateId().isBlank(),
            "Template ID is required");

        // Version must be positive
        addStaticRuleInternal("version_positive", ValidationSeverity.ERROR,
            ctx -> ctx.version() > 0,
            "Template version must be positive");

        // Bounds validation
        addStaticRuleInternal("bounds_defined", ValidationSeverity.ERROR,
            ctx -> ctx.bounds() != null,
            "Arena bounds must be defined");

        addStaticRuleInternal("bounds_size_limit", ValidationSeverity.ERROR,
            ctx -> {
                if (ctx.bounds() == null) return true; // checked by bounds_defined
                return ctx.bounds().sizeX() <= MAX_ARENA_SIZE
                    && ctx.bounds().sizeY() <= MAX_ARENA_SIZE
                    && ctx.bounds().sizeZ() <= MAX_ARENA_SIZE;
            },
            "Arena dimensions must not exceed " + MAX_ARENA_SIZE + " blocks per axis");

        addStaticRuleInternal("bounds_positive", ValidationSeverity.ERROR,
            ctx -> {
                if (ctx.bounds() == null) return true;
                return ctx.bounds().sizeX() > 0
                    && ctx.bounds().sizeY() > 0
                    && ctx.bounds().sizeZ() > 0;
            },
            "Arena dimensions must be positive");

        // Spawn slot validation
        addStaticRuleInternal("spawn_slots_defined", ValidationSeverity.ERROR,
            ctx -> ctx.spawnSlotCount() > 0,
            "At least one spawn slot must be defined");

        addStaticRuleInternal("spawn_count_limit", ValidationSeverity.ERROR,
            ctx -> ctx.spawnSlotCount() <= MAX_SPAWN_COUNT,
            "Spawn count must not exceed " + MAX_SPAWN_COUNT);

        // Hazard validation
        addStaticRuleInternal("hazard_count_limit", ValidationSeverity.ERROR,
            ctx -> ctx.hazardCount() <= MAX_HAZARD_COUNT,
            "Hazard count must not exceed " + MAX_HAZARD_COUNT);

        // Block count estimation
        addStaticRuleInternal("block_count_limit", ValidationSeverity.WARNING,
            ctx -> ctx.estimatedBlockCount() <= MAX_BLOCK_COUNT,
            "Estimated block count exceeds " + MAX_BLOCK_COUNT + ", build may be slow");

        // Inheritance depth
        addStaticRuleInternal("inheritance_depth_limit", ValidationSeverity.ERROR,
            ctx -> ctx.inheritanceDepth() <= MAX_INHERITANCE_DEPTH,
            "Inheritance depth must not exceed " + MAX_INHERITANCE_DEPTH);

        // === Static Rules (semantic validation) ===

        // Player spawn inside bounds
        addStaticRuleInternal("player_spawn_in_bounds", ValidationSeverity.ERROR,
            ctx -> {
                if (ctx.bounds() == null || ctx.playerSpawn() == null) return true;
                return ctx.bounds().contains(ctx.playerSpawn());
            },
            "Player spawn must be inside arena bounds");

        // Exit portal defined for closed arenas
        addStaticRuleInternal("exit_portal_defined", ValidationSeverity.WARNING,
            ctx -> {
                if (!ctx.isClosedArena()) return true;
                return ctx.hasExitPortal();
            },
            "Closed arenas should have an exit portal defined");

        // Wave spawner configuration
        addStaticRuleInternal("wave_spawner_config", ValidationSeverity.WARNING,
            ctx -> {
                if (!ctx.hasWaveSpawner()) return true;
                return ctx.waveSpawnerHasValidConfig();
            },
            "Wave spawner configuration may be invalid");
    }

    /**
     * Adds a static validation rule.
     */
    public void addStaticRule(String id, ValidationSeverity severity,
                               Predicate<ValidationContext> check, String message) {
        addStaticRuleInternal(id, severity, check, message);
    }

    private void addStaticRuleInternal(String id, ValidationSeverity severity,
                                        Predicate<ValidationContext> check, String message) {
        staticRules.add(new ValidationRule(id, severity, check, message, RulePhase.STATIC));
    }

    /**
     * Adds a runtime preflight rule.
     */
    public void addRuntimeRule(String id, ValidationSeverity severity,
                                Predicate<ValidationContext> check, String message) {
        runtimeRules.add(new ValidationRule(id, severity, check, message, RulePhase.RUNTIME));
    }

    /**
     * Performs static validation only.
     */
    public AdvancedValidationReport validateStatic(ValidationContext context) {
        return validate(context, staticRules, RulePhase.STATIC);
    }

    /**
     * Performs runtime preflight validation only.
     */
    public AdvancedValidationReport validateRuntime(ValidationContext context) {
        return validate(context, runtimeRules, RulePhase.RUNTIME);
    }

    /**
     * Performs full validation (static + runtime).
     */
    public AdvancedValidationReport validateFull(ValidationContext context) {
        List<ValidationRule> allRules = new ArrayList<>();
        allRules.addAll(staticRules);
        allRules.addAll(runtimeRules);
        return validate(context, allRules, RulePhase.FULL);
    }

    private AdvancedValidationReport validate(ValidationContext context,
                                               List<ValidationRule> rules,
                                               RulePhase phase) {
        List<ValidationIssue> issues = new ArrayList<>();
        int errorCount = 0;
        int warningCount = 0;

        for (ValidationRule rule : rules) {
            try {
                boolean passed = rule.check().test(context);
                if (!passed) {
                    ValidationIssue issue = new ValidationIssue(
                        rule.id(),
                        rule.severity(),
                        rule.message(),
                        rule.phase()
                    );
                    issues.add(issue);

                    if (rule.severity() == ValidationSeverity.ERROR) {
                        errorCount++;
                    } else {
                        warningCount++;
                    }

                    LOGGER.debug("Validation failed: {} - {}", rule.id(), rule.message());
                }
            } catch (Exception e) {
                // Rule execution failed - treat as error
                ValidationIssue issue = new ValidationIssue(
                    rule.id(),
                    ValidationSeverity.ERROR,
                    "Rule execution failed: " + e.getMessage(),
                    rule.phase()
                );
                issues.add(issue);
                errorCount++;

                LOGGER.warn("Validation rule {} threw exception", rule.id(), e);
            }
        }

        boolean valid = errorCount == 0;

        return new AdvancedValidationReport(
            context.templateId(),
            phase,
            valid,
            errorCount,
            warningCount,
            issues,
            System.currentTimeMillis()
        );
    }

    /**
     * Converts to legacy ValidationResult for backward compatibility.
     */
    public ValidationResult toLegacyResult(AdvancedValidationReport report) {
        List<String> errors = report.issues().stream()
            .filter(i -> i.severity() == ValidationSeverity.ERROR)
            .map(i -> "[" + i.ruleId() + "] " + i.message())
            .toList();

        List<String> warnings = report.issues().stream()
            .filter(i -> i.severity() == ValidationSeverity.WARNING)
            .map(i -> "[" + i.ruleId() + "] " + i.message())
            .toList();

        return new ValidationResult(report.valid(), errors, warnings);
    }

    // ========== Supporting Types ==========

    /**
     * Validation severity levels.
     */
    public enum ValidationSeverity {
        /** Blocks build - must be fixed */
        ERROR,
        /** Allows build with warning log */
        WARNING
    }

    /**
     * Rule execution phase.
     */
    public enum RulePhase {
        STATIC,   // Schema/structure validation
        RUNTIME,  // Preflight checks (chunk loaded, etc.)
        FULL      // Combined
    }

    /**
     * A single validation rule.
     */
    public record ValidationRule(
        String id,
        ValidationSeverity severity,
        Predicate<ValidationContext> check,
        String message,
        RulePhase phase
    ) {}

    /**
     * A validation issue found during validation.
     */
    public record ValidationIssue(
        String ruleId,
        ValidationSeverity severity,
        String message,
        RulePhase phase
    ) {}

    /**
     * Structured validation report.
     */
    public record AdvancedValidationReport(
        String templateId,
        RulePhase phase,
        boolean valid,
        int errorCount,
        int warningCount,
        List<ValidationIssue> issues,
        long timestampMs
    ) {
        /**
         * Returns true if there are any errors.
         */
        public boolean hasErrors() {
            return errorCount > 0;
        }

        /**
         * Returns true if there are any warnings.
         */
        public boolean hasWarnings() {
            return warningCount > 0;
        }

        /**
         * Returns issues filtered by severity.
         */
        public List<ValidationIssue> getIssuesBySeverity(ValidationSeverity severity) {
            return issues.stream()
                .filter(i -> i.severity() == severity)
                .toList();
        }

        /**
         * Returns a formatted summary string.
         */
        public String formatSummary() {
            if (valid && warningCount == 0) {
                return String.format("Template '%s' validation PASSED (%s phase)",
                    templateId, phase);
            } else if (valid) {
                return String.format("Template '%s' validation PASSED with %d warning(s) (%s phase)",
                    templateId, warningCount, phase);
            } else {
                return String.format("Template '%s' validation FAILED: %d error(s), %d warning(s) (%s phase)",
                    templateId, errorCount, warningCount, phase);
            }
        }

        /**
         * Returns detailed report as string.
         */
        public String formatDetailed() {
            StringBuilder sb = new StringBuilder();
            sb.append(formatSummary()).append("\n");

            if (!issues.isEmpty()) {
                sb.append("\nIssues:\n");
                for (ValidationIssue issue : issues) {
                    sb.append(String.format("  [%s] %s: %s\n",
                        issue.severity(), issue.ruleId(), issue.message()));
                }
            }

            return sb.toString();
        }
    }

    /**
     * Context for validation rules.
     * Implement this interface to provide template data to the validator.
     */
    public interface ValidationContext {
        String templateId();
        int version();

        // Bounds
        ArenaBounds bounds();

        // Spawn configuration
        int spawnSlotCount();
        Vec3i playerSpawn();

        // Hazards
        int hazardCount();

        // Block estimates
        int estimatedBlockCount();

        // Inheritance
        int inheritanceDepth();

        // Arena type
        boolean isClosedArena();
        boolean hasExitPortal();

        // Wave spawner
        boolean hasWaveSpawner();
        boolean waveSpawnerHasValidConfig();

        // Runtime context (optional, null for static-only validation)
        default RuntimeContext runtimeContext() {
            return null;
        }
    }

    /**
     * Simple bounds representation.
     */
    public interface ArenaBounds {
        int sizeX();
        int sizeY();
        int sizeZ();
        boolean contains(Vec3i pos);

        default long volume() {
            return (long) sizeX() * sizeY() * sizeZ();
        }
    }

    /**
     * Simple 3D integer vector.
     */
    public record Vec3i(int x, int y, int z) {}

    /**
     * Runtime context for preflight checks.
     */
    public interface RuntimeContext {
        boolean isChunkLoaded(int chunkX, int chunkZ);
        boolean isInstanceWorld();
        double currentMspt();
        int activeArenasInWorld();
    }
}
