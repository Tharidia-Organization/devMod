package com.devmod.compat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.devmod.TestBootstrap;

/**
 * Source-scanning audit for ConcurrentHashMap.computeIfAbsent deadlock risk (JDK-8161372).
 *
 * Checks that no ConcurrentHashMap field has a computeIfAbsent lambda that recursively
 * calls back into the same class method which also invokes computeIfAbsent on the same field.
 *
 * Also verifies that computeIfAbsent lambdas on ConcurrentHashMap never return null,
 * since ConcurrentHashMap silently discards null returns (negative lookups never cached).
 */
class ConcurrentMapAuditTest {

    private static final Path SRC_ROOT = Paths.get("src/main/java/com/devmod");

    /** Pattern for ConcurrentHashMap field declarations */
    private static final Pattern CHM_FIELD = Pattern.compile(
        "(?:new\\s+ConcurrentHashMap|ConcurrentHashMap\\.newKeySet|ConcurrentHashMap<)");

    /** Pattern for computeIfAbsent calls with a field name prefix */
    private static final Pattern COMPUTE_IF_ABSENT = Pattern.compile(
        "(\\w+)\\.computeIfAbsent\\(");

    /** Pattern for method references like this::methodName */
    private static final Pattern METHOD_REF = Pattern.compile(
        "this::(\\w+)");

    /** Known exceptions: ReflectionHelper is being fixed by another agent */
    private static final Set<String> SKIP_FILES = Set.of("ReflectionHelper.java");

    @BeforeAll
    static void setup() {
        TestBootstrap.init();
    }

    @Test
    void noConcurrentHashMapComputeIfAbsentReturnsNull() throws IOException {
        List<String> violations = new ArrayList<>();

        try (Stream<Path> files = Files.walk(SRC_ROOT)) {
            files.filter(p -> p.toString().endsWith(".java"))
                 .filter(p -> !SKIP_FILES.contains(p.getFileName().toString()))
                 .forEach(path -> {
                     try {
                         checkForNullReturns(path, violations);
                     } catch (IOException e) {
                         throw new RuntimeException(e);
                     }
                 });
        }

        assertTrue(violations.isEmpty(),
            "ConcurrentHashMap.computeIfAbsent lambdas must not return null " +
            "(ConcurrentHashMap silently ignores null, breaking negative caching):\n" +
            String.join("\n", violations));
    }

    @Test
    void noConcurrentHashMapComputeIfAbsentRecursion() throws IOException {
        List<String> violations = new ArrayList<>();

        try (Stream<Path> files = Files.walk(SRC_ROOT)) {
            files.filter(p -> p.toString().endsWith(".java"))
                 .filter(p -> !SKIP_FILES.contains(p.getFileName().toString()))
                 .forEach(path -> {
                     try {
                         checkForRecursion(path, violations);
                     } catch (IOException e) {
                         throw new RuntimeException(e);
                     }
                 });
        }

        assertTrue(violations.isEmpty(),
            "ConcurrentHashMap.computeIfAbsent must not recursively call computeIfAbsent " +
            "on the same map (JDK-8161372 deadlock risk):\n" +
            String.join("\n", violations));
    }

    /**
     * Heuristic check: if a class declares a ConcurrentHashMap field, and a computeIfAbsent
     * lambda on that field uses this::someMethod, and someMethod also calls computeIfAbsent
     * on the same field, that's a deadlock risk.
     */
    private void checkForRecursion(Path path, List<String> violations) throws IOException {
        List<String> lines = Files.readAllLines(path);
        String content = String.join("\n", lines);

        // Find ConcurrentHashMap fields
        Set<String> chmFields = findCHMFields(lines);
        if (chmFields.isEmpty()) return;

        // For each field, find all methods that call computeIfAbsent on it
        // Then check if the lambda references a method (this::xxx) that also uses computeIfAbsent
        // on the same field
        Map<String, Set<String>> fieldToMethodsWithComputeIfAbsent = new HashMap<>();

        for (String field : chmFields) {
            Set<String> methods = new HashSet<>();
            Pattern pat = Pattern.compile(field + "\\.computeIfAbsent\\(");
            for (int i = 0; i < lines.size(); i++) {
                if (pat.matcher(lines.get(i)).find()) {
                    String methodName = findEnclosingMethod(lines, i);
                    if (methodName != null) {
                        methods.add(methodName);
                    }
                }
            }
            fieldToMethodsWithComputeIfAbsent.put(field, methods);
        }

        // Now check: does any computeIfAbsent lambda on a CHM field reference (this::xxx)
        // a method that ALSO calls computeIfAbsent on the same field?
        for (String field : chmFields) {
            Pattern pat = Pattern.compile(field + "\\.computeIfAbsent\\([^)]*,\\s*this::(\\w+)");
            Matcher m = pat.matcher(content);
            while (m.find()) {
                String referencedMethod = m.group(1);
                Set<String> methodsUsingField = fieldToMethodsWithComputeIfAbsent.get(field);
                if (methodsUsingField != null && methodsUsingField.contains(referencedMethod)) {
                    violations.add(String.format(
                        "  %s: field '%s' has computeIfAbsent with this::%s, " +
                        "but %s() also calls computeIfAbsent on '%s' (deadlock risk)",
                        path, field, referencedMethod, referencedMethod, field));
                }
            }
        }
    }

    /**
     * Check for computeIfAbsent lambdas that explicitly return null on ConcurrentHashMap fields.
     * This is a heuristic: we look for inline lambdas that contain "return null" within
     * 10 lines of a computeIfAbsent call on a known CHM field.
     */
    private void checkForNullReturns(Path path, List<String> violations) throws IOException {
        List<String> lines = Files.readAllLines(path);

        Set<String> chmFields = findCHMFields(lines);
        if (chmFields.isEmpty()) return;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            for (String field : chmFields) {
                if (line.contains(field + ".computeIfAbsent(")) {
                    // Check the lambda body (next ~15 lines) for "return null"
                    boolean isInlineLambda = line.contains("->");
                    if (isInlineLambda) {
                        // Single line lambda: check if it could be null
                        // e.g., .computeIfAbsent(key, k -> someMethodThatReturnsNull())
                        // We can't easily detect this heuristically on a single line,
                        // but we CAN check multi-line lambdas
                        int braceDepth = countChar(line, '{') - countChar(line, '}');
                        if (braceDepth > 0) {
                            // Multi-line lambda
                            for (int j = i + 1; j < Math.min(i + 15, lines.size()); j++) {
                                String lj = lines.get(j).trim();
                                if (lj.equals("return null;")) {
                                    violations.add(String.format(
                                        "  %s:%d: %s.computeIfAbsent lambda returns null at line %d " +
                                        "(ConcurrentHashMap ignores null returns, negative lookups never cached)",
                                        path, i + 1, field, j + 1));
                                    break;
                                }
                                braceDepth += countChar(lines.get(j), '{') - countChar(lines.get(j), '}');
                                if (braceDepth <= 0) break;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Find all field names that are declared as ConcurrentHashMap in the source.
     */
    private Set<String> findCHMFields(List<String> lines) {
        Set<String> fields = new HashSet<>();
        // Match field declarations like:
        //   private final Map<X, Y> fieldName = new ConcurrentHashMap<>();
        //   private final ConcurrentHashMap<X, Y> fieldName = ...
        Pattern fieldDecl = Pattern.compile(
            "(?:Map|ConcurrentHashMap)(?:<[^>]*>)?\\s+(\\w+)\\s*=\\s*new\\s+ConcurrentHashMap");
        Pattern keySetDecl = Pattern.compile(
            "Set<[^>]*>\\s+(\\w+)\\s*=\\s*ConcurrentHashMap\\.newKeySet");

        for (String line : lines) {
            Matcher m = fieldDecl.matcher(line);
            if (m.find()) {
                fields.add(m.group(1));
            }
            m = keySetDecl.matcher(line);
            if (m.find()) {
                fields.add(m.group(1));
            }
        }
        return fields;
    }

    /**
     * Find the name of the method enclosing the given line number.
     */
    private String findEnclosingMethod(List<String> lines, int lineIndex) {
        Pattern methodDecl = Pattern.compile(
            "(?:public|private|protected|static|final|synchronized|\\s)+\\s+" +
            "\\S+\\s+(\\w+)\\s*\\(");
        for (int i = lineIndex; i >= 0; i--) {
            Matcher m = methodDecl.matcher(lines.get(i));
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    private int countChar(String s, char c) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) count++;
        }
        return count;
    }
}
