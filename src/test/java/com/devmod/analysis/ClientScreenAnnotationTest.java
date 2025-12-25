package com.devmod.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Static analysis test to enforce @OnlyIn(Dist.CLIENT) on client Screen classes.
 */
public class ClientScreenAnnotationTest {

    private static final Path SOURCE_ROOT = Paths.get("src/main/java/com/devmod/client");
    private static final Pattern CLASS_EXTENDS_PATTERN = Pattern.compile(
        "(?m)^[\\t ]*(?:public|protected|private|abstract|final|static|sealed|non-sealed|[\\t ]+)*class[\\t ]+(\\w+)[\\t ]+extends[\\t ]+([\\w.]+)");

    @Test
    @DisplayName("Client screen classes should be annotated with @OnlyIn(Dist.CLIENT)")
    void screensRequireOnlyInAnnotation() throws IOException {
        if (!Files.exists(SOURCE_ROOT)) {
            System.out.println("Source root not found, skipping test: " + SOURCE_ROOT);
            return;
        }

        List<Violation> violations = new ArrayList<>();

        Files.walkFileTree(SOURCE_ROOT, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!file.toString().endsWith(".java")) return FileVisitResult.CONTINUE;
                if (file.getFileName().toString().equals("package-info.java")) return FileVisitResult.CONTINUE;

                List<String> lines = Files.readAllLines(file);
                String content = String.join("\n", lines);
                Matcher matcher = CLASS_EXTENDS_PATTERN.matcher(content);

                while (matcher.find()) {
                    String className = matcher.group(1);
                    String baseType = matcher.group(2);
                    String simpleBase = baseType.substring(baseType.lastIndexOf('.') + 1);
                    if (!simpleBase.endsWith("Screen")) {
                        continue;
                    }
                    int lineNumber = getLineNumber(content, matcher.start());
                    if (!hasOnlyInAnnotation(lines, lineNumber - 1)) {
                        violations.add(new Violation(file, className, lineNumber));
                    }
                }

                return FileVisitResult.CONTINUE;
            }
        });

        if (!violations.isEmpty()) {
            StringBuilder message = new StringBuilder();
            message.append("Found ").append(violations.size())
                   .append(" client Screen classes missing @OnlyIn(Dist.CLIENT):\n\n");
            for (Violation v : violations) {
                message.append("  ").append(v).append("\n");
            }
            message.append("\nFix by adding @OnlyIn(Dist.CLIENT) above the class declaration.\n");
            fail(message.toString());
        }
    }

    private boolean hasOnlyInAnnotation(List<String> lines, int classLineIndex) {
        int start = Math.max(0, classLineIndex - 20);
        for (int i = classLineIndex - 1; i >= start; i--) {
            String trimmed = lines.get(i).trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.contains("@OnlyIn(Dist.CLIENT)")) {
                return true;
            }
            if (trimmed.startsWith("@")) {
                continue;
            }
            if (trimmed.startsWith("/**") || trimmed.startsWith("*") || trimmed.startsWith("*/")
                || trimmed.startsWith("//")) {
                continue;
            }
            break;
        }
        return false;
    }

    private int getLineNumber(String content, int charIndex) {
        int line = 1;
        for (int i = 0; i < charIndex && i < content.length(); i++) {
            if (content.charAt(i) == '\n') line++;
        }
        return line;
    }

    private record Violation(Path file, String className, int line) {
        @Override
        public String toString() {
            return file.getFileName() + ":" + line + " - " + className;
        }
    }
}
