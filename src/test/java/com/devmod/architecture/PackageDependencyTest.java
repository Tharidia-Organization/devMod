package com.devmod.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Detects circular package dependencies between top-level packages under
 * {@code com.devmod}. Bidirectional dependencies (A imports B AND B imports A)
 * are flagged as cycles, except for explicitly allowed pairs.
 *
 * <p>A top-level package is the first segment after {@code com.devmod}, e.g.
 * {@code combat}, {@code collision}, {@code endurance}. Imports to sub-packages
 * are mapped to their top-level parent.
 *
 * <p>Hub packages that naturally depend on many others (client, actions, events,
 * mixin, network) are excluded from cycle detection since they are integration
 * points by design.
 */
class PackageDependencyTest {

    private static final Path SRC_ROOT = Paths.get("src/main/java/com/devmod");
    private static final String BASE_PACKAGE = "com.devmod.";
    private static final Pattern IMPORT_PATTERN =
            Pattern.compile("^import\\s+(?:static\\s+)?com\\.devmod\\.(\\w+)\\.", Pattern.MULTILINE);

    /**
     * Hub packages excluded from cycle detection. These are integration/wiring
     * packages that naturally import from many domains.
     */
    private static final Set<String> HUB_PACKAGES = Set.of(
            "client", "actions", "events", "mixin", "network", "core", "config"
    );

    /**
     * Allowed bidirectional dependencies. Each entry is "pkgA<->pkgB" (alphabetical order).
     * Add entries here when a cycle is intentional and well-documented.
     * Remove entries as cycles are broken -- this list should only shrink over time.
     */
    private static final Set<String> ALLOWED_CYCLES = Set.of(
            // --- Pre-existing cycles (baseline from 2026-02-15) ---
            // These represent known architectural debt. Remove as they are fixed.
            "abilities<->endurance",
            "abilities<->telemetry",
            "area<->runtime",
            "arena<->endurance",
            "arena<->runtime",
            "arena<->telemetry",
            "clone<->entity",
            "clone<->npc",
            "clone<->runtime",
            "combat<->damage",       // HitHelper.BodyPart shared, but DamageBreakdown coupling remains
            "compat<->endurance",
            "endurance<->mailbox",
            "endurance<->party",
            "endurance<->runtime",
            "endurance<->stats",
            "endurance<->telemetry",
            "hologram<->nexus",
            "hologram<->runtime",
            "nexus<->runtime",
            "nexus<->transport",
            "portal<->runtime",
            "portal<->transport"
    );

    @Test
    void noBidirectionalDependenciesBetweenDomainPackages() throws IOException {
        // 1. Build the dependency graph
        Map<String, Set<String>> graph = buildPackageDependencyGraph();

        // 2. Find bidirectional edges (cycles of length 2)
        Set<String> cycles = new TreeSet<>();
        for (var entry : graph.entrySet()) {
            String from = entry.getKey();
            if (HUB_PACKAGES.contains(from)) continue;

            for (String to : entry.getValue()) {
                if (HUB_PACKAGES.contains(to)) continue;
                if (from.equals(to)) continue;

                // Check reverse edge
                Set<String> reverseEdges = graph.getOrDefault(to, Set.of());
                if (reverseEdges.contains(from)) {
                    // Normalize: alphabetical order
                    String a = from.compareTo(to) < 0 ? from : to;
                    String b = from.compareTo(to) < 0 ? to : from;
                    String cycleKey = a + "<->" + b;

                    if (!ALLOWED_CYCLES.contains(cycleKey)) {
                        cycles.add(cycleKey);
                    }
                }
            }
        }

        // 3. Report
        if (!cycles.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(cycles.size())
                    .append(" bidirectional package dependency cycle(s):\n");
            for (String cycle : cycles) {
                sb.append("  - ").append(cycle).append("\n");
            }
            sb.append("\nTo fix: extract shared types to com.devmod.shared, use events, or add to ALLOWED_CYCLES if intentional.");
            assertTrue(false, sb.toString());
        }
    }

    @Test
    void collisionDoesNotImportCombat() throws IOException {
        assertNoImport("collision", "combat",
                "collision must not import combat directly; use com.devmod.shared types instead");
    }

    @Test
    void notificationDoesNotImportEndurance() throws IOException {
        assertNoImport("notification", "endurance",
                "notification must not import endurance; pass primitive params at the API boundary");
    }

    // ==================== Helpers ====================

    private Map<String, Set<String>> buildPackageDependencyGraph() throws IOException {
        Map<String, Set<String>> graph = new HashMap<>();

        if (!Files.isDirectory(SRC_ROOT)) {
            return graph;
        }

        try (Stream<Path> files = Files.walk(SRC_ROOT)) {
            files.filter(p -> p.toString().endsWith(".java"))
                    .forEach(path -> {
                        String topLevel = extractTopLevelPackage(path);
                        if (topLevel == null) return;

                        try {
                            String content = Files.readString(path);
                            Set<String> imports = extractImportedPackages(content);
                            imports.remove(topLevel); // Remove self-references

                            graph.computeIfAbsent(topLevel, k -> new LinkedHashSet<>())
                                    .addAll(imports);
                        } catch (IOException e) {
                            // Skip unreadable files
                        }
                    });
        }

        return graph;
    }

    private String extractTopLevelPackage(Path javaFile) {
        Path relative = SRC_ROOT.relativize(javaFile);
        if (relative.getNameCount() < 2) {
            return null; // File directly in com.devmod, no sub-package
        }
        return relative.getName(0).toString();
    }

    private Set<String> extractImportedPackages(String source) {
        Set<String> packages = new HashSet<>();
        Matcher matcher = IMPORT_PATTERN.matcher(source);
        while (matcher.find()) {
            packages.add(matcher.group(1));
        }
        return packages;
    }

    private void assertNoImport(String fromPackage, String toPackage, String message) throws IOException {
        if (!Files.isDirectory(SRC_ROOT)) return;

        Path packageDir = SRC_ROOT.resolve(fromPackage);
        if (!Files.isDirectory(packageDir)) return;

        List<String> violations = new ArrayList<>();
        Pattern targetImport = Pattern.compile(
                "^import\\s+(?:static\\s+)?com\\.devmod\\." + toPackage + "\\.",
                Pattern.MULTILINE);

        try (Stream<Path> files = Files.walk(packageDir)) {
            files.filter(p -> p.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path);
                            Matcher m = targetImport.matcher(content);
                            while (m.find()) {
                                violations.add(SRC_ROOT.relativize(path) + ": " + m.group().trim());
                            }
                        } catch (IOException e) {
                            // Skip
                        }
                    });
        }

        assertTrue(violations.isEmpty(),
                message + "\nViolations:\n  " + String.join("\n  ", violations));
    }
}
