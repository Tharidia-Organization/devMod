package com.devmod.actions.catalog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.ActionType;
import com.devmod.actions.RadialAction;

/**
 * Generates a structured baseline report of all registered actions.
 * Used for inventory auditing, migration validation, and regression detection.
 */
public final class ActionBaselineReport {

    private ActionBaselineReport() {}

    /**
     * A single action's metadata snapshot for baseline comparison.
     *
     * @param id              the unique action identifier (e.g. "devmod.ui.radial.open")
     * @param labelKey        i18n label key
     * @param descriptionKey  i18n description key
     * @param category        the ActionCategory enum value
     * @param actionType      the ActionType enum value, or null if not set
     * @param menuPath        radial menu path, or null
     * @param hasIcon         whether an icon item is configured
     * @param hasVisibility   whether a visibility predicate is configured
     * @param hasPrecondition whether a non-trivial precondition is set
     * @param commandHint     the command hint string, or null
     * @param permissionLevel explicit permission level (-1 = none)
     * @param uiFeedback      the UIFeedback enum name
     * @param requiresConfirm whether action requires confirmation
     * @param isToggle        whether action is a toggle
     * @param isClientSafe    whether action is in the CLIENT_SAFE_ACTIONS whitelist
     */
    public record ActionBaselineEntry(
        String id,
        String labelKey,
        String descriptionKey,
        ActionCategory category,
        ActionType actionType,
        String menuPath,
        boolean hasIcon,
        boolean hasVisibility,
        boolean hasPrecondition,
        String commandHint,
        int permissionLevel,
        String uiFeedback,
        boolean requiresConfirm,
        boolean isToggle,
        boolean isClientSafe
    ) {

        /**
         * Returns a CSV header line for report export.
         */
        public static String csvHeader() {
            return "id,labelKey,descriptionKey,category,actionType,menuPath,hasIcon,"
                + "hasVisibility,hasPrecondition,commandHint,permissionLevel,uiFeedback,"
                + "requiresConfirm,isToggle,isClientSafe";
        }

        /**
         * Returns this entry as a CSV line.
         */
        public String toCsvLine() {
            return String.join(",",
                csvEscape(id),
                csvEscape(labelKey),
                csvEscape(descriptionKey),
                csvEscape(category != null ? category.name() : ""),
                csvEscape(actionType != null ? actionType.name() : ""),
                csvEscape(menuPath != null ? menuPath : ""),
                String.valueOf(hasIcon),
                String.valueOf(hasVisibility),
                String.valueOf(hasPrecondition),
                csvEscape(commandHint != null ? commandHint : ""),
                String.valueOf(permissionLevel),
                csvEscape(uiFeedback),
                String.valueOf(requiresConfirm),
                String.valueOf(isToggle),
                String.valueOf(isClientSafe)
            );
        }

        private static String csvEscape(String value) {
            if (value == null) return "";
            if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
                return "\"" + value.replace("\"", "\"\"") + "\"";
            }
            return value;
        }
    }

    /**
     * Introspects the live ActionRegistry and returns a snapshot of all registered actions.
     * Must be called after all registration methods have completed (e.g. post-init).
     *
     * @return sorted list of baseline entries (sorted by action id)
     */
    public static List<ActionBaselineEntry> generateReport() {
        Collection<RadialAction> actions = ActionRegistry.listActions();
        List<ActionBaselineEntry> entries = new ArrayList<>(actions.size());

        for (RadialAction action : actions) {
            entries.add(toEntry(action));
        }

        entries.sort(Comparator.comparing(ActionBaselineEntry::id));
        return List.copyOf(entries);
    }

    /**
     * Generates a full CSV report of all registered actions.
     *
     * @return CSV string with header and one line per action
     */
    public static String generateCsvReport() {
        List<ActionBaselineEntry> entries = generateReport();
        StringBuilder sb = new StringBuilder();
        sb.append(ActionBaselineEntry.csvHeader()).append('\n');
        for (ActionBaselineEntry entry : entries) {
            sb.append(entry.toCsvLine()).append('\n');
        }
        return sb.toString();
    }

    private static ActionBaselineEntry toEntry(RadialAction action) {
        return new ActionBaselineEntry(
            action.getId(),
            action.getLabelKey(),
            action.getDescriptionKey(),
            action.getCategory(),
            action.getActionType(),
            action.getMenuPath(),
            action.getIconStack() != null,
            action.getVisibilityPredicate() != null,
            /* hasPrecondition: always() returns true for any context, so we check
               the precondition class name to detect non-trivial ones */
            !action.getPrecondition().getClass().getName().contains("Lambda")
                || action.getPermissionLevel() > 0,
            action.getCommandHint(),
            action.getPermissionLevel(),
            action.getUIFeedback().name(),
            action.requiresConfirm(),
            action.isToggle(),
            ActionRegistry.isClientSafeAction(action.getId())
        );
    }
}
