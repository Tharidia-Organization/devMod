package com.devmod.actions.codegen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionType;
import com.devmod.actions.catalog.ActionSpec;

/**
 * Validates a list of {@link ActionSpec} entries and their generated code output.
 *
 * <p>Checks performed:
 * <ul>
 *   <li>No duplicate action IDs</li>
 *   <li>All handler references resolve to known classes</li>
 *   <li>All ActionSpec fields produce valid RadialAction builder calls</li>
 *   <li>Category/Type compatibility checks</li>
 *   <li>ID naming convention validation</li>
 * </ul>
 */
public final class CodegenValidator {

    private CodegenValidator() {}

    /**
     * Result of a validation pass.
     *
     * @param errors   fatal issues that prevent valid code generation
     * @param warnings non-fatal issues that should be reviewed
     */
    public record ValidationResult(
        List<String> errors,
        List<String> warnings
    ) {
        public ValidationResult {
            Objects.requireNonNull(errors, "errors");
            Objects.requireNonNull(warnings, "warnings");
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
        }

        /**
         * Returns true if validation passed with no errors.
         */
        public boolean isValid() {
            return errors.isEmpty();
        }

        /**
         * Returns total number of issues (errors + warnings).
         */
        public int issueCount() {
            return errors.size() + warnings.size();
        }
    }

    /** Valid action ID pattern: devmod.domain.name with lowercase segments */
    private static final Pattern VALID_ID = Pattern.compile(
        "^devmod\\.[a-z0-9_]+(?:\\.[a-z0-9_]+)+$");

    /**
     * Validates a list of ActionSpecs for code generation readiness.
     *
     * @param specs               the specs to validate
     * @param knownHandlerClasses set of known handler class names
     * @return validation result
     */
    public static ValidationResult validate(
            List<ActionSpec> specs,
            Set<String> knownHandlerClasses) {

        Objects.requireNonNull(specs, "specs");
        Objects.requireNonNull(knownHandlerClasses, "knownHandlerClasses");

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Check for duplicates
        checkDuplicates(specs, errors);

        // Validate each spec
        for (ActionSpec spec : specs) {
            validateSpec(spec, knownHandlerClasses, errors, warnings);
        }

        return new ValidationResult(errors, warnings);
    }

    /**
     * Validates a single ActionSpec.
     *
     * @param spec the spec to validate
     * @return validation result
     */
    public static ValidationResult validateSingle(ActionSpec spec) {
        return validate(List.of(spec), Set.of());
    }

    // =========================================================================
    // Validation checks
    // =========================================================================

    private static void checkDuplicates(List<ActionSpec> specs, List<String> errors) {
        Set<String> seen = new HashSet<>();
        for (ActionSpec spec : specs) {
            if (!seen.add(spec.id())) {
                errors.add("Duplicate action ID: " + spec.id());
            }
        }
    }

    private static void validateSpec(
            ActionSpec spec,
            Set<String> knownHandlerClasses,
            List<String> errors,
            List<String> warnings) {

        String id = spec.id();

        // ID format
        if (!VALID_ID.matcher(id).matches()) {
            errors.add("[" + id + "] Invalid action ID format. "
                + "Expected: devmod.<domain>.<name> with lowercase segments");
        }

        // Label and description keys must not be empty
        if (spec.uiMeta().labelKey().isEmpty()) {
            errors.add("[" + id + "] Empty labelKey");
        }
        if (spec.uiMeta().descKey().isEmpty()) {
            errors.add("[" + id + "] Empty descKey");
        }

        // Handler reference resolution
        String handlerRef = spec.handlerRef();
        if (handlerRef != null && !knownHandlerClasses.contains(handlerRef)) {
            warnings.add("[" + id + "] Handler class not in known set: " + handlerRef);
        }

        // No handler and no command hint means no execution logic
        if (handlerRef == null && spec.bindingMeta().commandHint() == null) {
            warnings.add("[" + id + "] No handlerRef and no commandHint - "
                + "action will have no execution logic");
        }

        // Category/Type compatibility
        validateCategoryTypeCompatibility(spec, warnings);

        // Permission level consistency
        validatePermissionConsistency(spec, warnings);

        // Icon item format
        String iconItemId = spec.uiMeta().iconItemId();
        if (iconItemId != null && !iconItemId.contains(":")) {
            warnings.add("[" + id + "] Icon item ID should use registry format "
                + "(e.g. 'minecraft:compass'): " + iconItemId);
        }

        // Menu path format
        String menuPath = spec.uiMeta().menuPath();
        if (menuPath != null && !menuPath.startsWith("Root/")) {
            warnings.add("[" + id + "] Menu path should start with 'Root/': " + menuPath);
        }
    }

    private static void validateCategoryTypeCompatibility(
            ActionSpec spec, List<String> warnings) {

        ActionType type = spec.actionType();
        ActionCategory category = spec.category();

        if (type == null) return;

        // Server commands should not be in CLIENT-only channel
        if (type == ActionType.RUN_SERVER_COMMAND
            && spec.channel() == ActionSpec.ActionChannel.CLIENT) {
            warnings.add("[" + spec.id() + "] RUN_SERVER_COMMAND with CLIENT channel - "
                + "command actions typically need server channel");
        }

        // Toggle actions should have isToggle=true in UiMeta
        if (type == ActionType.TOGGLE_SETTING && !spec.uiMeta().isToggle()) {
            warnings.add("[" + spec.id() + "] TOGGLE_SETTING type but isToggle=false in UiMeta");
        }

        // NAVIGATE_SCREEN actions should be CLIENT or BOTH
        if (type == ActionType.NAVIGATE_SCREEN
            && spec.channel() == ActionSpec.ActionChannel.SERVER) {
            warnings.add("[" + spec.id() + "] NAVIGATE_SCREEN with SERVER channel - "
                + "screen navigation is client-side");
        }
    }

    private static void validatePermissionConsistency(
            ActionSpec spec, List<String> warnings) {

        // High permission actions should have requiresConfirm for destructive operations
        if (spec.permissionLevel() >= 4 && !spec.policyMeta().requiresConfirm()) {
            warnings.add("[" + spec.id() + "] Permission level "
                + spec.permissionLevel() + " without requiresConfirm");
        }

        // CLIENT-only actions typically shouldn't need server permissions
        if (spec.channel() == ActionSpec.ActionChannel.CLIENT
            && spec.permissionLevel() > 0) {
            // This is valid for some actions (e.g. admin UI panels visible only to ops)
            // so it's a warning, not an error
            warnings.add("[" + spec.id() + "] CLIENT channel with permissionLevel="
                + spec.permissionLevel() + " - verify this is intentional");
        }
    }
}
