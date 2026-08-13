package com.devmod.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Enforces the server-client boundary: server-side classes must NOT import
 * client-only classes from {@code com.devmod.client.*}.
 *
 * <p>On a dedicated server, client classes are stripped by NeoForge. Any server
 * code that references them will crash with {@link NoClassDefFoundError}.
 *
 * <p>Exceptions:
 * <ul>
 *   <li>Files inside {@code /client/} packages are client-only themselves</li>
 *   <li>Files inside {@code /mixin/} packages are OK (handled by Mixin framework)</li>
 *   <li>Compile-time constants ({@code static final} primitives/Strings) are inlined
 *       by the compiler per JLS 13.1 and never create a runtime dependency</li>
 *   <li>Specific whitelisted files documented below</li>
 * </ul>
 */
class ServerClientBoundaryTest {

    private static final Path SRC_ROOT = Paths.get("src/main/java/com/devmod");

    /**
     * Pattern matching import statements for client classes.
     */
    private static final Pattern CLIENT_IMPORT = Pattern.compile(
        "^import\\s+(static\\s+)?com\\.devmod\\.client\\.(.+);\\s*$"
    );

    /**
     * Whitelisted server-side files that are known-safe despite importing client classes.
     *
     * <p>Each entry must have a documented justification:
     * <ul>
     *   <li>{@code shared/SharedColorTokens.java} - imports DesignTokens for
     *       {@code static final int} compile-time constants only (JLS 13.1 inlining)</li>
     *   <li>{@code npc/NpcState.java} - imports DesignTokens for
     *       {@code static final int} compile-time constants only</li>
     *   <li>{@code area/aesthetic/EditorState.java} - imports DesignTokens for
     *       {@code static final int} compile-time constants only</li>
     *   <li>{@code actions/domains/ConfigDomainRegistrar.java} - annotated @OnlyIn(Dist.CLIENT),
     *       guarded by FMLEnvironment.dist.isClient() in DomainRegistryBootstrap</li>
     *   <li>{@code actions/domains/DebugDomainRegistrar.java} - annotated @OnlyIn(Dist.CLIENT),
     *       guarded by FMLEnvironment.dist.isClient() in DomainRegistryBootstrap</li>
     *   <li>{@code actions/domains/UiDomainRegistrar.java} - annotated @OnlyIn(Dist.CLIENT),
     *       guarded by FMLEnvironment.dist.isClient() in DomainRegistryBootstrap</li>
     * </ul>
     */
    private static final Set<String> WHITELIST = Set.of(
        "shared/SharedColorTokens.java",
        "npc/NpcState.java",
        "area/aesthetic/EditorState.java",
        "actions/domains/ConfigDomainRegistrar.java",
        "actions/domains/DebugDomainRegistrar.java",
        "actions/domains/UiDomainRegistrar.java"
    );

    @Test
    void serverCodeMustNotImportClientClasses() throws IOException {
        assertTrue(Files.isDirectory(SRC_ROOT),
            "Source root not found: " + SRC_ROOT.toAbsolutePath());

        List<String> violations = new ArrayList<>();

        try (Stream<Path> walk = Files.walk(SRC_ROOT)) {
            walk.filter(p -> p.toString().endsWith(".java"))
                .filter(ServerClientBoundaryTest::isServerSideFile)
                .filter(ServerClientBoundaryTest::isNotWhitelisted)
                .forEach(path -> checkFile(path, violations));
        }

        assertTrue(violations.isEmpty(),
            "Found " + violations.size() + " server->client import violation(s):\n"
                + String.join("\n", violations));
    }

    /**
     * A file is server-side if it is NOT inside a /client/ or /mixin/ directory.
     */
    private static boolean isServerSideFile(Path path) {
        String normalized = path.toString().replace('\\', '/');
        return !normalized.contains("/client/") && !normalized.contains("/mixin/");
    }

    /**
     * Check if the file's relative path (from SRC_ROOT) is in the whitelist.
     */
    private static boolean isNotWhitelisted(Path path) {
        String relative = SRC_ROOT.relativize(path).toString().replace('\\', '/');
        return !WHITELIST.contains(relative);
    }

    private static void checkFile(Path path, List<String> violations) {
        try {
            List<String> lines = Files.readAllLines(path);
            String relative = SRC_ROOT.relativize(path).toString().replace('\\', '/');
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                Matcher m = CLIENT_IMPORT.matcher(line);
                if (m.matches()) {
                    violations.add(String.format(
                        "  %s:%d -> %s", relative, i + 1, line));
                }
            }
        } catch (IOException e) {
            violations.add("  Failed to read: " + path + " (" + e.getMessage() + ")");
        }
    }
}
