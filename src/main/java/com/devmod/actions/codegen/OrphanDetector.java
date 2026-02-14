package com.devmod.actions.codegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.devmod.actions.catalog.ActionSpec;

/**
 * Detects drift between the declarative catalog (ActionSpec list), the runtime registry
 * (ActionRegistry), and the keybind registry (ActionKeybindRegistry).
 *
 * <p>Operates at source-code level to avoid requiring Minecraft runtime,
 * following the same pattern as RadialOrphanCheckTest and ActionBaselineValidationTest.
 */
public final class OrphanDetector {

    private OrphanDetector() {}

    /**
     * Full orphan detection report.
     *
     * @param catalogOrphans   action IDs in catalog but not registered at runtime
     * @param registryOrphans  action IDs registered at runtime but not in catalog
     * @param handlerOrphans   action IDs with no handler implementation
     * @param keybindOrphans   keybind action IDs with no corresponding action in catalog
     */
    public record OrphanReport(
        List<String> catalogOrphans,
        List<String> registryOrphans,
        List<String> handlerOrphans,
        List<String> keybindOrphans
    ) {
        public OrphanReport {
            Objects.requireNonNull(catalogOrphans, "catalogOrphans");
            Objects.requireNonNull(registryOrphans, "registryOrphans");
            Objects.requireNonNull(handlerOrphans, "handlerOrphans");
            Objects.requireNonNull(keybindOrphans, "keybindOrphans");
            catalogOrphans = List.copyOf(catalogOrphans);
            registryOrphans = List.copyOf(registryOrphans);
            handlerOrphans = List.copyOf(handlerOrphans);
            keybindOrphans = List.copyOf(keybindOrphans);
        }

        /**
         * Returns true if no orphans were found in any category.
         */
        public boolean isClean() {
            return catalogOrphans.isEmpty()
                && registryOrphans.isEmpty()
                && handlerOrphans.isEmpty()
                && keybindOrphans.isEmpty();
        }

        /**
         * Returns the total number of orphans across all categories.
         */
        public int totalOrphans() {
            return catalogOrphans.size()
                + registryOrphans.size()
                + handlerOrphans.size()
                + keybindOrphans.size();
        }
    }

    /**
     * Detects catalog orphans: actions defined in the catalog but not referenced
     * in any registration source file.
     *
     * @param catalogIds           action IDs from the catalog
     * @param registrationSources  source code strings of all registration files
     * @return list of orphan action IDs
     */
    public static List<String> catalogOrphans(
            Collection<String> catalogIds,
            Collection<String> registrationSources) {

        Set<String> referencedIds = extractActionIdStrings(registrationSources);

        return catalogIds.stream()
            .filter(id -> !referencedIds.contains(id))
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * Detects registry orphans: action IDs referenced in registration source files
     * but not present in the catalog.
     *
     * @param catalogIds           action IDs from the catalog
     * @param registrationSources  source code strings of all registration files
     * @return list of orphan action IDs
     */
    public static List<String> registryOrphans(
            Collection<String> catalogIds,
            Collection<String> registrationSources) {

        Set<String> catalogSet = new HashSet<>(catalogIds);
        Set<String> referencedIds = extractActionIdStrings(registrationSources);

        return referencedIds.stream()
            .filter(id -> !catalogSet.contains(id))
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * Detects handler orphans: actions in the catalog that have no handler implementation.
     * An action has a handler if its handlerRef is non-null.
     *
     * @param specs the action specifications
     * @return list of action IDs with no handler
     */
    public static List<String> handlerOrphans(Collection<ActionSpec> specs) {
        return specs.stream()
            .filter(spec -> spec.handlerRef() == null
                && spec.bindingMeta().commandHint() == null)
            .map(ActionSpec::id)
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * Detects keybind orphans: keybind registrations that reference action IDs
     * not present in the catalog.
     *
     * @param catalogIds      action IDs from the catalog
     * @param keybindSource   source code of ActionKeybindRegistry registration
     * @return list of orphan keybind action IDs
     */
    public static List<String> keybindOrphans(
            Collection<String> catalogIds,
            String keybindSource) {

        Set<String> catalogSet = new HashSet<>(catalogIds);
        Set<String> keybindActionIds = extractKeybindActionIds(keybindSource);

        return keybindActionIds.stream()
            .filter(id -> !catalogSet.contains(id))
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * Runs a full orphan detection pass from source files on disk.
     *
     * @param catalogIds action IDs from the catalog (e.g. from ActionSpec list)
     * @param specs      the full action specifications (for handler orphan check)
     * @return complete OrphanReport
     * @throws IOException if source files cannot be read
     */
    public static OrphanReport detectAll(
            Collection<String> catalogIds,
            Collection<ActionSpec> specs) throws IOException {

        List<String> regSources = readRegistrationSources();
        String keybindSource = readKeybindSource();

        return new OrphanReport(
            catalogOrphans(catalogIds, regSources),
            registryOrphans(catalogIds, regSources),
            handlerOrphans(specs),
            keybindOrphans(catalogIds, keybindSource)
        );
    }

    // =========================================================================
    // Source extraction helpers
    // =========================================================================

    /** Pattern to match quoted action ID strings like "devmod.ui.radial.open" */
    private static final Pattern QUOTED_ACTION_ID = Pattern.compile(
        "\"(devmod\\.[a-z0-9_.]+)\"");

    /** Pattern to match ActionIds.FIELD references */
    private static final Pattern ACTION_IDS_REF = Pattern.compile(
        "ActionIds\\.(\\w+)");

    /**
     * Extracts action ID string literals from source code.
     * Matches patterns like "devmod.something.something".
     */
    static Set<String> extractActionIdStrings(Collection<String> sources) {
        Set<String> ids = new HashSet<>();
        for (String source : sources) {
            Matcher matcher = QUOTED_ACTION_ID.matcher(source);
            while (matcher.find()) {
                ids.add(matcher.group(1));
            }
        }
        return ids;
    }

    /**
     * Extracts action IDs from keybind registration source.
     * Looks for ActionIds.FIELD_NAME patterns inside register() calls.
     */
    static Set<String> extractKeybindActionIds(String keybindSource) {
        Set<String> ids = new HashSet<>();
        Matcher matcher = QUOTED_ACTION_ID.matcher(keybindSource);
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        return ids;
    }

    private static final Path[] REGISTRATION_SOURCE_PATHS = {
        Paths.get("src/main/java/com/devmod/actions/DevModActions.java"),
        Paths.get("src/main/java/com/devmod/actions/client/DevModClientActions.java"),
        Paths.get("src/main/java/com/devmod/actions/client/ClientUIActions.java"),
        Paths.get("src/main/java/com/devmod/actions/client/ClientDebugActions.java"),
        Paths.get("src/main/java/com/devmod/actions/client/ClientConfigActions.java"),
        Paths.get("src/main/java/com/devmod/actions/client/ClientGameplayActions.java"),
        Paths.get("src/main/java/com/devmod/arena/command/ArenaActionRegistry.java"),
        Paths.get("src/main/java/com/devmod/debug/DebugCommand.java"),
        Paths.get("src/main/java/com/devmod/telemetry/TelemetryReloadCommand.java"),
        Paths.get("src/main/java/com/devmod/telemetry/dashboard/DashboardCommand.java"),
        Paths.get("src/main/java/com/devmod/telemetry/dungeon/DungeonCommand.java"),
        Paths.get("src/main/java/com/devmod/gametest/TestHarnessCommands.java"),
        Paths.get("src/main/java/com/devmod/mailbox/admin/MailboxCommands.java"),
        Paths.get("src/main/java/com/devmod/endurance/LeaderboardCommandEvents.java"),
    };

    private static List<String> readRegistrationSources() throws IOException {
        List<String> sources = new ArrayList<>();
        for (Path path : REGISTRATION_SOURCE_PATHS) {
            if (Files.exists(path)) {
                sources.add(Files.readString(path));
            }
        }
        return sources;
    }

    private static String readKeybindSource() throws IOException {
        Path path = Paths.get(
            "src/main/java/com/devmod/actions/client/ActionKeybindRegistry.java");
        return Files.exists(path) ? Files.readString(path) : "";
    }
}
