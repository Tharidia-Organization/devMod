package com.devmod.actions.codegen;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionType;
import com.devmod.actions.catalog.ActionSpec;

/**
 * Deterministic code generator that produces RadialAction registration source code
 * from a list of {@link ActionSpec} declarations.
 *
 * <p>Output is deterministic: the same input always produces the same output,
 * with actions sorted by ID. This allows reliable snapshot comparison and
 * diff-based change detection.
 *
 * <p>Supports manual handler overrides: if a handler class name is specified
 * in the ActionSpec's handlerRef, the generated code references that class
 * instead of generating a stub lambda.
 */
public final class ActionRegistrationGenerator {

    private ActionRegistrationGenerator() {}

    /**
     * Generates Java source code for registering all given ActionSpecs as RadialActions.
     *
     * @param specs       the action specifications to generate from
     * @param packageName the Java package for the generated class
     * @param className   the class name for the generated registration class
     * @return the generation output with source, action IDs, and any warnings
     */
    public static RegistrationOutput generate(
            List<ActionSpec> specs,
            String packageName,
            String className) {
        return generate(specs, packageName, className, Set.of());
    }

    /**
     * Generates Java source code with manual handler overrides.
     *
     * @param specs              the action specifications to generate from
     * @param packageName        the Java package for the generated class
     * @param className          the class name for the generated registration class
     * @param knownHandlerClasses set of fully qualified class names that exist as handler implementations
     * @return the generation output with source, action IDs, and any warnings
     */
    public static RegistrationOutput generate(
            List<ActionSpec> specs,
            String packageName,
            String className,
            Set<String> knownHandlerClasses) {

        Objects.requireNonNull(specs, "specs");
        Objects.requireNonNull(packageName, "packageName");
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(knownHandlerClasses, "knownHandlerClasses");

        List<ActionSpec> sorted = new ArrayList<>(specs);
        sorted.sort(Comparator.comparing(ActionSpec::id));

        List<String> actionIds = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        StringBuilder body = new StringBuilder();

        for (ActionSpec spec : sorted) {
            if (!seenIds.add(spec.id())) {
                warnings.add("Duplicate action ID in input: " + spec.id());
                continue;
            }
            actionIds.add(spec.id());
            body.append(generateRegistration(spec, knownHandlerClasses, warnings));
            body.append('\n');
        }

        String source = buildSourceFile(packageName, className, body.toString(), sorted);

        return new RegistrationOutput(source, actionIds, warnings, Instant.now());
    }

    /**
     * Generates a single RadialAction.builder() registration block for one ActionSpec.
     * Useful for previewing the output of a single spec without a full class wrapper.
     *
     * @param spec the action specification
     * @return the builder call as a Java code string
     */
    public static String generateSingleRegistration(ActionSpec spec) {
        return generateRegistration(spec, Set.of(), new ArrayList<>());
    }

    // =========================================================================
    // Internal generation
    // =========================================================================

    private static String buildSourceFile(
            String packageName,
            String className,
            String registrationBody,
            List<ActionSpec> specs) {

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(";\n\n");

        // Imports
        sb.append("import com.devmod.actions.ActionCategory;\n");
        sb.append("import com.devmod.actions.ActionPreconditions;\n");
        sb.append("import com.devmod.actions.ActionRegistry;\n");
        sb.append("import com.devmod.actions.ActionType;\n");
        sb.append("import com.devmod.actions.RadialAction;\n");

        // Collect icon imports
        boolean hasIcons = specs.stream()
            .anyMatch(s -> s.uiMeta().iconItemId() != null);
        if (hasIcons) {
            sb.append("import net.minecraft.world.item.Items;\n");
        }

        sb.append('\n');

        // Class header
        sb.append("/**\n");
        sb.append(" * Generated action registrations.\n");
        sb.append(" * DO NOT EDIT - generated by ActionRegistrationGenerator.\n");
        sb.append(" *\n");
        sb.append(" * <p>Actions: ").append(specs.size()).append('\n');
        sb.append(" */\n");
        sb.append("public final class ").append(className).append(" {\n\n");
        sb.append("    private ").append(className).append("() {}\n\n");
        sb.append("    public static void registerAll() {\n");
        sb.append(registrationBody);
        sb.append("    }\n");
        sb.append("}\n");

        return sb.toString();
    }

    private static String generateRegistration(
            ActionSpec spec,
            Set<String> knownHandlerClasses,
            List<String> warnings) {

        StringBuilder sb = new StringBuilder();
        String indent = "        ";

        sb.append(indent).append("ActionRegistry.register(RadialAction.builder(\"")
            .append(escapeJava(spec.id())).append("\")\n");

        // labelKey
        sb.append(indent).append("    .labelKey(\"")
            .append(escapeJava(spec.uiMeta().labelKey())).append("\")\n");

        // descriptionKey
        sb.append(indent).append("    .descriptionKey(\"")
            .append(escapeJava(spec.uiMeta().descKey())).append("\")\n");

        // category
        sb.append(indent).append("    .category(ActionCategory.")
            .append(spec.category().name()).append(")\n");

        // actionType (optional)
        ActionType actionType = spec.actionType();
        if (actionType != null) {
            sb.append(indent).append("    .actionType(ActionType.")
                .append(actionType.name()).append(")\n");
        }

        // menuPath (optional)
        String menuPath = spec.uiMeta().menuPath();
        if (menuPath != null) {
            sb.append(indent).append("    .menuPath(\"")
                .append(escapeJava(menuPath)).append("\")\n");
        }

        // icon (optional)
        String iconItemId = spec.uiMeta().iconItemId();
        if (iconItemId != null) {
            String itemsField = toItemsField(iconItemId);
            if (itemsField != null) {
                sb.append(indent).append("    .icon(Items.")
                    .append(itemsField).append(")\n");
            } else {
                warnings.add("Unknown icon item for " + spec.id() + ": " + iconItemId);
            }
        }

        // permissionLevel
        if (spec.permissionLevel() > 0) {
            sb.append(indent).append("    .permissionLevel(")
                .append(spec.permissionLevel()).append(")\n");

            // precondition from permission
            sb.append(indent).append("    .precondition(ActionPreconditions.requiresPermissionOrClient(")
                .append(spec.permissionLevel()).append("))\n");
        } else if (spec.policyMeta().preconditionRef() != null) {
            // Named precondition reference
            sb.append(indent).append("    .precondition(ActionPreconditions.")
                .append(spec.policyMeta().preconditionRef()).append("())\n");
        }

        // commandHint (optional)
        String commandHint = spec.bindingMeta().commandHint();
        if (commandHint != null) {
            sb.append(indent).append("    .commandHint(\"")
                .append(escapeJava(commandHint)).append("\")\n");
        }

        // requiresConfirm
        if (spec.policyMeta().requiresConfirm()) {
            sb.append(indent).append("    .requiresConfirm(true)\n");
        }

        // toggle
        if (spec.uiMeta().isToggle()) {
            sb.append(indent).append("    .toggle(ctx -> false) // TODO: wire active predicate\n");
            warnings.add("Toggle action needs active predicate wiring: " + spec.id());
        }

        // uiFeedback - only emit if non-default
        if (spec.channel() == ActionSpec.ActionChannel.SERVER
            || spec.actionType() == ActionType.RUN_SERVER_COMMAND) {
            sb.append(indent).append("    .uiFeedback(RadialAction.UIFeedback.CHAT)\n");
        }

        // handler
        String handlerRef = spec.handlerRef();
        if (handlerRef != null && knownHandlerClasses.contains(handlerRef)) {
            sb.append(indent).append("    .handler(")
                .append(handlerRef).append("::handle)\n");
        } else if (handlerRef != null) {
            warnings.add("Handler class not found for " + spec.id()
                + ": " + handlerRef + " (generating stub)");
            sb.append(indent).append("    .handler(ctx -> { /* TODO: ").append(handlerRef)
                .append(" */ })\n");
        } else if (commandHint != null) {
            // Command actions get executeCommand handler
            sb.append(indent).append("    .handler(ctx -> ctx.executeCommand(\"")
                .append(escapeJava(commandHint)).append("\"))\n");
        } else {
            sb.append(indent).append("    .handler(ctx -> { /* TODO: implement */ })\n");
            warnings.add("No handler specified for action: " + spec.id());
        }

        sb.append(indent).append("    .build());\n");

        return sb.toString();
    }

    /**
     * Converts a Minecraft item registry name (e.g. "minecraft:compass") to an Items.FIELD_NAME
     * (e.g. "COMPASS"). Returns null if the format is unrecognized.
     */
    @Nullable
    static String toItemsField(String itemId) {
        if (itemId == null) return null;
        String name = itemId;
        if (name.startsWith("minecraft:")) {
            name = name.substring("minecraft:".length());
        }
        if (name.isEmpty()) return null;
        return name.toUpperCase(java.util.Locale.ROOT);
    }

    private static String escapeJava(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
