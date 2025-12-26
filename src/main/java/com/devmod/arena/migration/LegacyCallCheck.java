package com.devmod.arena.migration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class LegacyCallCheck {
    private static final String DEFAULT_SOURCE_ROOT = "src/main/java";
    private static final String DEFAULT_REPORT_PATH = "build/reports/legacy-usage.txt";

    private LegacyCallCheck() {}

    public static void main(String[] args) {
        Path sourceRoot = resolveSourceRoot(args);
        Path reportPath = resolveReportPath();
        int maxAllowed = parseInt(System.getProperty("devmod.legacy.max"), 0);

        WrapperAnalyzer analyzer = new WrapperAnalyzer(sourceRoot);
        WrapperAnalyzer.AnalysisResult result;
        try {
            result = analyzer.analyze();
        } catch (IOException e) {
            System.err.println("Legacy call scan failed: " + e.getMessage());
            System.exit(2);
            return;
        }

        try {
            writeReport(reportPath, sourceRoot, result);
        } catch (IOException e) {
            System.err.println("Failed to write legacy report: " + e.getMessage());
        }

        int count = result.getUsageCount();
        System.out.println("Legacy call scan: " + count + " findings in " + result.analysisTimeMs() + "ms");
        if (count > maxAllowed) {
            System.err.println("Legacy call check failed: " + count + " > " + maxAllowed);
            System.exit(3);
        }
    }

    private static Path resolveSourceRoot(String[] args) {
        if (args != null && args.length > 0 && args[0] != null && !args[0].isBlank()) {
            return Paths.get(args[0].trim());
        }
        String prop = System.getProperty("devmod.legacy.root");
        if (prop != null && !prop.isBlank()) {
            return Paths.get(prop.trim());
        }
        return Paths.get(DEFAULT_SOURCE_ROOT);
    }

    private static Path resolveReportPath() {
        String prop = System.getProperty("devmod.legacy.report");
        Path path = prop != null && !prop.isBlank()
            ? Paths.get(prop.trim())
            : Paths.get(DEFAULT_REPORT_PATH);
        Path parent = path.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException ignored) {
                // Non-fatal: report write will fail if directory is missing.
            }
        }
        return path;
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static void writeReport(Path reportPath, Path sourceRoot, WrapperAnalyzer.AnalysisResult result)
        throws IOException {
        StringBuilder report = new StringBuilder();
        report.append("Legacy call-site report\n");
        report.append("Source root: ").append(sourceRoot.toAbsolutePath()).append("\n");
        report.append("Total findings: ").append(result.getUsageCount()).append("\n\n");

        for (WrapperAnalyzer.LegacyUsage usage : result.usages()) {
            report.append(usage.patternName()).append(" @ ")
                .append(usage.filePath()).append(":").append(usage.lineNumber()).append("\n");
            report.append("  ").append(usage.lineContent()).append("\n");
            report.append("  ").append(usage.migrationAdvice()).append("\n\n");
        }

        Files.writeString(reportPath, report.toString(), StandardCharsets.UTF_8);
    }
}
