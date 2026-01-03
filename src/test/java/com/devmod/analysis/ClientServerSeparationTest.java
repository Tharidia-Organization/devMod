package com.devmod.analysis;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ClientServerSeparationTest {

    private static final Path SOURCE_ROOT = Paths.get("src/main/java/com/devmod");

    // Client-only packages that should not be imported in common code
    private static final List<String> CLIENT_PACKAGES = List.of(
        "com.devmod.client.",
        "net.minecraft.client."
    );

    // Common/server packages that must not reference client code
    private static final List<String> COMMON_PACKAGES = List.of(
        "com.devmod.combat",
        "com.devmod.collision",
        "com.devmod.config",
        "com.devmod.endurance",
        "com.devmod.events",
        "com.devmod.gametest",
        "com.devmod.integration",
        "com.devmod.network",
        "com.devmod.party",
        "com.devmod.quest",
        "com.devmod.stats",
        "com.devmod.telemetry",
        "com.devmod.testing",
        "com.devmod.util"
    );

    // Client singletons that should never be directly accessed from common code
    private static final List<String> CLIENT_SINGLETONS = List.of(
        "DebugRenderer.INSTANCE",
        "ShakeManager.INSTANCE",
        "TrailManager.INSTANCE",
        "Impact3DPanelManager.INSTANCE",
        "IntegratedTestSession.INSTANCE",
        "QANotificationSystem.INSTANCE",
        "ImpactHudOverlay.isEnabled",
        "ImpactHudOverlay.setEnabled",
        "Minecraft.getInstance()"
    );

    // Client-only package prefixes for singleton access checks
    private static final List<String> CLIENT_INSTANCE_PREFIXES = List.of(
        "com.devmod.client.",
        "com.devmod.debug.client.",
        "com.devmod.mixin.client."
    );

    // Allowed patterns (properly guarded code)
    private static final List<String> ALLOWED_PATTERNS = List.of(
        "FMLEnvironment.dist.isClient()",  // Proper guard
        "FMLEnvironment.dist != Dist.CLIENT", // Proper guard (early return pattern)
        "DistExecutor.safeRunWhenOn",       // Proper guard
        "@OnlyIn(Dist.CLIENT)",             // Annotation guard
        "TestHarnessClientDelegate"          // Our delegate pattern
    );

    // Files to skip (mixins, etc.)
    private static final List<String> SKIP_PATHS = List.of(
        "/mixin/",
        "/test/",
        "package-info.java"
    );

    @Test
    @DisplayName("Common packages should not import client classes directly")
    void testNoClientImportsInCommonPackages() throws IOException {
        if (!Files.exists(SOURCE_ROOT)) {
            System.out.println("Source root not found, skipping test: " + SOURCE_ROOT);
            return;
        }

        List<Violation> violations = new ArrayList<>();
        Pattern importPattern = Pattern.compile("^import\\s+([\\w.]+);", Pattern.MULTILINE);

        Files.walkFileTree(SOURCE_ROOT, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!file.toString().endsWith(".java")) return FileVisitResult.CONTINUE;
                if (shouldSkip(file)) return FileVisitResult.CONTINUE;
                if (!isInCommonPackage(file)) return FileVisitResult.CONTINUE;

                String content = Files.readString(file);

                // Check for client imports
                Matcher matcher = importPattern.matcher(content);
                while (matcher.find()) {
                    String importedClass = matcher.group(1);
                    for (String clientPkg : CLIENT_PACKAGES) {
                        if (importedClass.startsWith(clientPkg)) {
                            // Check if this is properly guarded
                            if (!hasProperGuard(content)) {
                                violations.add(new Violation(
                                    file,
                                    "Imports client class: " + importedClass,
                                    getLineNumber(content, matcher.start())
                                ));
                            }
                        }
                    }
                }

                return FileVisitResult.CONTINUE;
            }
        });

        if (!violations.isEmpty()) {
            StringBuilder message = new StringBuilder();
            message.append("Found ").append(violations.size())
                   .append(" client/server separation violations:\n\n");
            for (Violation v : violations) {
                message.append("  ").append(v).append("\n");
            }
            message.append("\nFix these by:\n");
            message.append("1. Moving client code to com.devmod.client.* package\n");
            message.append("2. Using FMLEnvironment.dist.isClient() guards\n");
            message.append("3. Creating a client delegate class (see TestHarnessClientDelegate)\n");

            fail(message.toString());
        }
    }

    @Test
    @DisplayName("Common packages should not reference client singletons directly")
    void testNoClientSingletonsInCommonPackages() throws IOException {
        if (!Files.exists(SOURCE_ROOT)) {
            System.out.println("Source root not found, skipping test: " + SOURCE_ROOT);
            return;
        }

        List<Violation> violations = new ArrayList<>();

        Files.walkFileTree(SOURCE_ROOT, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!file.toString().endsWith(".java")) return FileVisitResult.CONTINUE;
                if (shouldSkip(file)) return FileVisitResult.CONTINUE;
                if (!isInCommonPackage(file)) return FileVisitResult.CONTINUE;

                String content = Files.readString(file);

                for (String singleton : CLIENT_SINGLETONS) {
                    int index = content.indexOf(singleton);
                    if (index >= 0) {
                        // Check if properly guarded
                        if (!hasProperGuard(content)) {
                            violations.add(new Violation(
                                file,
                                "References client singleton: " + singleton,
                                getLineNumber(content, index)
                            ));
                        }
                    }
                }

                // Only check for client instance violations if not properly guarded
                if (!hasProperGuard(content)) {
                    violations.addAll(findClientInstanceViolations(file, content));
                }

                return FileVisitResult.CONTINUE;
            }
        });

        if (!violations.isEmpty()) {
            StringBuilder message = new StringBuilder();
            message.append("Found ").append(violations.size())
                   .append(" direct client singleton references:\n\n");
            for (Violation v : violations) {
                message.append("  ").append(v).append("\n");
            }
            message.append("\nFix these by using a client delegate class.\n");

            fail(message.toString());
        }
    }

    private boolean shouldSkip(Path file) {
        String path = normalizePath(file);
        for (String skip : SKIP_PATHS) {
            if (path.contains(skip)) return true;
        }
        return false;
    }

    private boolean isInCommonPackage(Path file) {
        String path = normalizePath(file);
        // Files in client/ are not common
        if (path.contains("/client/")) return false;
        // Check if in a known common package
        for (String pkg : COMMON_PACKAGES) {
            String pkgPath = pkg.replace('.', '/');
            if (path.contains("/" + pkgPath.substring(pkgPath.lastIndexOf('/') + 1) + "/")) {
                return true;
            }
        }
        return false;
    }

    private String normalizePath(Path file) {
        return file.toString().replace('\\', '/');
    }

    private boolean hasProperGuard(String content) {
        for (String pattern : ALLOWED_PATTERNS) {
            if (content.contains(pattern)) return true;
        }
        return false;
    }

    private List<Violation> findClientInstanceViolations(Path file, String content) {
        List<Violation> violations = new ArrayList<>();
        Pattern importPattern = Pattern.compile("^import\\s+([\\w.]+);", Pattern.MULTILINE);
        Matcher matcher = importPattern.matcher(content);
        List<String> clientImports = new ArrayList<>();
        boolean hasClientWildcard = false;

        while (matcher.find()) {
            String importedClass = matcher.group(1);
            for (String prefix : CLIENT_INSTANCE_PREFIXES) {
                if (importedClass.startsWith(prefix)) {
                    if (importedClass.endsWith(".*")) {
                        hasClientWildcard = true;
                    } else {
                        clientImports.add(importedClass.substring(importedClass.lastIndexOf('.') + 1));
                    }
                }
            }
        }

        if (hasClientWildcard && content.contains(".INSTANCE")) {
            violations.add(new Violation(
                file,
                "Uses .INSTANCE with client wildcard import",
                getLineNumber(content, content.indexOf(".INSTANCE"))
            ));
        }

        for (String simpleName : clientImports) {
            String token = simpleName + ".INSTANCE";
            int index = content.indexOf(token);
            if (index >= 0) {
                violations.add(new Violation(
                    file,
                    "Uses client singleton: " + token,
                    getLineNumber(content, index)
                ));
            }
        }

        Pattern fqcnPattern = Pattern.compile(
            "com\\.devmod\\.(?:client|debug\\.client|mixin\\.client)\\.[\\w.]+\\.INSTANCE");
        Matcher fqcnMatcher = fqcnPattern.matcher(content);
        while (fqcnMatcher.find()) {
            violations.add(new Violation(
                file,
                "Uses fully-qualified client singleton: " + fqcnMatcher.group(),
                getLineNumber(content, fqcnMatcher.start())
            ));
        }

        return violations;
    }

    private int getLineNumber(String content, int charIndex) {
        int line = 1;
        for (int i = 0; i < charIndex && i < content.length(); i++) {
            if (content.charAt(i) == '\n') line++;
        }
        return line;
    }

    private record Violation(Path file, String message, int line) {
        @Override
        public String toString() {
            return file.getFileName() + ":" + line + " - " + message;
        }
    }
}
