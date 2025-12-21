package com.devmod.arena.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Analyzer for detecting legacy wrapper usage (DD66).
 *
 * <p>Detection methods:
 * <ul>
 *   <li>Grep: Pattern matching for known legacy APIs</li>
 *   <li>AST: Simple pattern-based detection (full AST requires external tools)</li>
 *   <li>Runtime: Telemetry for actual legacy calls</li>
 * </ul>
 */
public class WrapperAnalyzer {

    private static final Logger LOGGER = LoggerFactory.getLogger(WrapperAnalyzer.class);

    /**
     * Patterns for legacy API detection.
     */
    private static final List<LegacyPattern> LEGACY_PATTERNS = List.of(
        new LegacyPattern(
            "ArenaManager.getInstance().createArena",
            Pattern.compile("ArenaManager\\.getInstance\\(\\)\\.createArena\\s*\\("),
            "Use ArenaTemplate.createInstance() instead"
        ),
        new LegacyPattern(
            "ArenaManager.createArena",
            Pattern.compile("ArenaManager\\.createArena\\s*\\("),
            "Use TemplateArenaBuilder.build() instead"
        ),
        new LegacyPattern(
            "LegacyArenaConfig",
            Pattern.compile("new\\s+LegacyArenaConfig\\s*\\("),
            "Use ArenaConfig with ArenaTemplate instead"
        ),
        new LegacyPattern(
            "deprecated createArena",
            Pattern.compile("@SuppressWarnings\\s*\\(\\s*\"deprecation\"\\s*\\)"),
            "Review and migrate suppressed deprecation warnings"
        ),
        new LegacyPattern(
            "direct ArenaInstance creation",
            Pattern.compile("new\\s+ArenaInstance\\s*\\("),
            "Use ArenaTemplate.createInstance() instead"
        )
    );

    private final Path sourceRoot;

    public WrapperAnalyzer(Path sourceRoot) {
        this.sourceRoot = sourceRoot;
    }

    /**
     * Analyzes the source tree for legacy API usage.
     *
     * @return Analysis result with all findings
     */
    public AnalysisResult analyze() throws IOException {
        LOGGER.info("Starting wrapper analysis in {}", sourceRoot);
        long startTime = System.currentTimeMillis();

        List<LegacyUsage> usages = new ArrayList<>();

        try (Stream<Path> files = Files.walk(sourceRoot)) {
            files.filter(path -> path.toString().endsWith(".java"))
                 .filter(Files::isRegularFile)
                 .forEach(file -> analyzeFile(file, usages));
        }

        long duration = System.currentTimeMillis() - startTime;
        LOGGER.info("Wrapper analysis completed in {}ms: {} legacy usages found",
            duration, usages.size());

        return new AnalysisResult(usages, duration);
    }

    /**
     * Analyzes a single file for legacy patterns.
     */
    private void analyzeFile(Path file, List<LegacyUsage> usages) {
        try {
            String content = Files.readString(file);
            String[] lines = content.split("\n");

            for (LegacyPattern pattern : LEGACY_PATTERNS) {
                Matcher matcher = pattern.pattern().matcher(content);

                while (matcher.find()) {
                    int lineNumber = countLines(content, matcher.start());
                    String lineContent = lineNumber <= lines.length ? lines[lineNumber - 1].trim() : "";

                    usages.add(new LegacyUsage(
                        file.toString(),
                        lineNumber,
                        pattern.name(),
                        pattern.migration(),
                        lineContent
                    ));
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to analyze file: {}", file, e);
        }
    }

    /**
     * Counts lines up to the given position.
     */
    private int countLines(String content, int position) {
        int lines = 1;
        for (int i = 0; i < position && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    /**
     * Captures the caller chain at runtime (for telemetry).
     */
    public static CallerChain captureCallerChain() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        List<String> chain = new ArrayList<>();

        // Skip first 3 elements (getStackTrace, captureCallerChain, and caller)
        for (int i = 3; i < Math.min(stackTrace.length, 13); i++) {
            StackTraceElement element = stackTrace[i];
            chain.add(element.getClassName() + "." + element.getMethodName() +
                     "(" + element.getFileName() + ":" + element.getLineNumber() + ")");
        }

        return new CallerChain(chain);
    }

    // ========== Result Types ==========

    /**
     * Analysis result containing all legacy usages.
     */
    public record AnalysisResult(
        List<LegacyUsage> usages,
        long analysisTimeMs
    ) {
        public int getUsageCount() {
            return usages.size();
        }

        public boolean hasLegacyUsage() {
            return !usages.isEmpty();
        }

        public List<LegacyUsage> getUsagesByPattern(String patternName) {
            return usages.stream()
                .filter(u -> u.patternName().equals(patternName))
                .toList();
        }
    }

    /**
     * A detected legacy API usage.
     */
    public record LegacyUsage(
        String filePath,
        int lineNumber,
        String patternName,
        String migrationAdvice,
        String lineContent
    ) {}

    /**
     * A pattern for detecting legacy API usage.
     */
    public record LegacyPattern(
        String name,
        Pattern pattern,
        String migration
    ) {}

    /**
     * Captured caller chain for runtime telemetry.
     */
    public record CallerChain(List<String> frames) {
        public String getTopFrame() {
            return frames.isEmpty() ? "unknown" : frames.get(0);
        }

        public String toLogString() {
            return String.join(" <- ", frames);
        }
    }
}
