package com.devmod.client.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Compliance tests for the unified UI scaling system.
 *
 * <h2>Purpose</h2>
 * Ensures all UI components use {@code UIScaleManager} for responsive layouts
 * instead of hardcoded pixel values that don't adapt to screen size changes.
 *
 * <h2>What Gets Checked</h2>
 * <ul>
 *   <li>Screen classes must import UIScaleManager</li>
 *   <li>Overlay classes must import UIScaleManager</li>
 *   <li>No hardcoded layout dimensions (width/height > 50px)</li>
 *   <li>No direct width/height assignments without scaling</li>
 * </ul>
 *
 * <h2>Exemptions</h2>
 * Some files are exempted (e.g., test files, abstract base classes, data classes).
 *
 * <h2>Enforcement</h2>
 * Tests FAIL if ANY UI component doesn't use the unified scaling system.
 * All violations must be fixed before merging.
 */
@DisplayName("L1: UI Scaling Compliance")
class UIScalingComplianceTest {

    // Path to main source
    private static final Path SRC_PATH = Paths.get("src/main/java/com/devmod/client");

    // UIScaleManager import pattern
    private static final String SCALE_MANAGER_IMPORT = "import com.devmod.client.ui.core.UIScaleManager";
    private static final String RADIAL_SCALER_IMPORT = "import com.devmod.client.ui.radial.config.RadialMenuScaler";

    // Patterns for detecting potential hardcoded values
    private static final Pattern MATH_MIN_HARDCODED = Pattern.compile(
        "Math\\.min\\s*\\(\\s*(\\d{3,})\\s*,",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DIRECT_PIXEL_LAYOUT = Pattern.compile(
        "\\b(new\\s+)?(?:int|float)\\s+(\\w*(?:Width|Height|Radius|Size))\\s*=\\s*(\\d{2,})\\s*;",
        Pattern.CASE_INSENSITIVE
    );

    // Files/patterns to exempt from checks
    private static final Set<String> EXEMPT_FILES = Set.of(
        "UIScaleManager.java",           // The manager itself
        "RadialMenuScaler.java",         // Radial-specific scaler
        "ResponsiveOverlay.java",        // Base class
        "DesignTokens.java",             // Design system (colors, not layout)
        "OverlayTheme.java",             // Theme definitions
        "RadialMenuConstants.java",      // Constants file (reference values)
        "RadialMenuThemeDefaults.java",  // Theme defaults
        // 3D world-space renderers (use PoseStack, not GuiGraphics)
        "Impact3DPanelManager.java",     // 3D panel manager
        "Impact3DRenderer.java",         // 3D world renderer
        "ImpactVFX.java",                // 3D VFX renderer
        "SlashRenderer.java",            // 3D VFX - slash effect
        "LinesRenderer.java",            // 3D VFX - lines effect
        "VortexRenderer.java",           // 3D VFX - vortex effect
        "GlyphRenderer.java"             // 3D VFX - glyph effect
    );

    private static final Set<String> EXEMPT_PACKAGES = Set.of(
        "animation",  // Animation utilities
        "model",      // Data models
        "config"      // Configuration (values are scaled at runtime)
    );

    // ============================================================
    // L1-08: Screen Classes Compliance
    // ============================================================

    @Nested
    @DisplayName("L1-08: Screen Classes Must Use UIScaleManager")
    class ScreenComplianceTests {

        @Test
        @DisplayName("All Screen classes import UIScaleManager or RadialMenuScaler")
        void screensImportScaleManager() throws IOException {
            List<Path> screenFiles = findScreenFiles();
            List<String> violations = new ArrayList<>();

            for (Path file : screenFiles) {
                if (isExempt(file)) continue;

                String content = Files.readString(file);

                // Skip if it's an interface or abstract without render method
                if (content.contains("interface ") && !content.contains("void render(")) {
                    continue;
                }

                boolean hasScaleManager = content.contains(SCALE_MANAGER_IMPORT);
                boolean hasRadialScaler = content.contains(RADIAL_SCALER_IMPORT);

                if (!hasScaleManager && !hasRadialScaler) {
                    // Check if file actually has layout code
                    if (hasLayoutCode(content)) {
                        violations.add(file.getFileName().toString());
                    }
                }
            }

            if (!violations.isEmpty()) {
                fail("Screen classes missing UIScaleManager import:\n  - " +
                    String.join("\n  - ", violations) +
                    "\n\nAdd: import com.devmod.client.ui.core.UIScaleManager;");
            }
        }

        @Test
        @DisplayName("Screen classes don't have excessive hardcoded dimensions")
        void screensNoHardcodedDimensions() throws IOException {
            List<Path> screenFiles = findScreenFiles();
            Map<String, List<String>> violations = new HashMap<>();

            for (Path file : screenFiles) {
                if (isExempt(file)) continue;

                String content = Files.readString(file);
                List<String> issues = findHardcodedDimensions(content);

                if (!issues.isEmpty()) {
                    violations.put(file.getFileName().toString(), issues);
                }
            }

            if (!violations.isEmpty()) {
                StringBuilder msg = new StringBuilder("Hardcoded dimensions found (use UIScaleManager.scale()):\n");
                violations.forEach((file, issues) -> {
                    msg.append("\n  ").append(file).append(":\n");
                    issues.forEach(issue -> msg.append("    - ").append(issue).append("\n"));
                });
                msg.append("\nExample fix: int panelWidth = UIScaleManager.scale(200);");
                fail(msg.toString());
            }
        }

        private List<Path> findScreenFiles() throws IOException {
            Path uiPath = SRC_PATH.resolve("ui");
            if (!Files.exists(uiPath)) {
                return List.of();
            }

            try (Stream<Path> stream = Files.walk(uiPath)) {
                return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith("Screen.java"))
                    .collect(Collectors.toList());
            }
        }
    }

    // ============================================================
    // L1-09: Overlay Classes Compliance
    // ============================================================

    @Nested
    @DisplayName("L1-09: Overlay Classes Must Use UIScaleManager")
    class OverlayComplianceTests {

        @Test
        @DisplayName("All Overlay classes import UIScaleManager")
        void overlaysImportScaleManager() throws IOException {
            List<Path> overlayFiles = findOverlayFiles();
            List<String> violations = new ArrayList<>();

            for (Path file : overlayFiles) {
                if (isExempt(file)) continue;

                String content = Files.readString(file);

                // Skip data classes (no render methods)
                if (!content.contains("void render(") && !content.contains("GuiGraphics")) {
                    continue;
                }

                boolean hasScaleManager = content.contains(SCALE_MANAGER_IMPORT);

                if (!hasScaleManager && hasLayoutCode(content)) {
                    violations.add(file.getFileName().toString());
                }
            }

            if (!violations.isEmpty()) {
                fail("Overlay classes missing UIScaleManager import:\n  - " +
                    String.join("\n  - ", violations) +
                    "\n\nAdd: import com.devmod.client.ui.core.UIScaleManager;");
            }
        }

        @Test
        @DisplayName("Overlay classes call UIScaleManager.update()")
        void overlaysCallUpdate() throws IOException {
            List<Path> overlayFiles = findOverlayFiles();
            List<String> violations = new ArrayList<>();

            for (Path file : overlayFiles) {
                if (isExempt(file)) continue;

                String content = Files.readString(file);

                // Only check files that import UIScaleManager
                if (!content.contains(SCALE_MANAGER_IMPORT)) continue;

                // Check if they call update()
                boolean callsUpdate = content.contains("UIScaleManager.update()");
                boolean usesScale = content.contains("UIScaleManager.scale(") ||
                                   content.contains("UIScaleManager.scaleF(");

                if (usesScale && !callsUpdate) {
                    // Could be a helper class that doesn't need to call update
                    if (content.contains("void render(")) {
                        violations.add(file.getFileName().toString());
                    }
                }
            }

            if (!violations.isEmpty()) {
                fail("Overlay classes using UIScaleManager without calling update():\n  - " +
                    String.join("\n  - ", violations) +
                    "\n\nAdd UIScaleManager.update() at start of render method.");
            }
        }

        private List<Path> findOverlayFiles() throws IOException {
            Path overlayPath = SRC_PATH.resolve("overlay");
            if (!Files.exists(overlayPath)) {
                return List.of();
            }

            try (Stream<Path> stream = Files.walk(overlayPath)) {
                return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.toString().contains("Overlay") ||
                                p.toString().contains("Hud"))
                    .collect(Collectors.toList());
            }
        }
    }

    // ============================================================
    // L1-10: RadialMenu Specific Compliance
    // ============================================================

    @Nested
    @DisplayName("L1-10: RadialMenu Uses RadialMenuScaler")
    class RadialMenuComplianceTests {

        @Test
        @DisplayName("RadialMenuScreen uses RadialMenuScaler")
        void radialMenuUsesScaler() throws IOException {
            Path radialPath = SRC_PATH.resolve("ui/radial/RadialMenuScreen.java");
            if (!Files.exists(radialPath)) {
                return; // File doesn't exist, skip test
            }

            String content = Files.readString(radialPath);

            assertTrue(content.contains(RADIAL_SCALER_IMPORT),
                "RadialMenuScreen should import RadialMenuScaler");

            assertTrue(content.contains("RadialMenuScaler.update") ||
                      content.contains("RadialMenuScaler.getInnerRadius"),
                "RadialMenuScreen should use RadialMenuScaler methods");

            // Should NOT have direct hardcoded radius assignments
            assertFalse(content.matches("(?s).*innerRadius\\s*=\\s*\\d{2,}\\s*;.*") &&
                       !content.contains("RadialMenuScaler"),
                "RadialMenuScreen should not have hardcoded innerRadius");
        }

        @Test
        @DisplayName("RadialMenuScaler exists and has required methods")
        void radialMenuScalerExists() throws IOException {
            Path scalerPath = SRC_PATH.resolve("ui/radial/config/RadialMenuScaler.java");

            assertTrue(Files.exists(scalerPath),
                "RadialMenuScaler.java should exist");

            String content = Files.readString(scalerPath);

            // Check for essential methods
            assertTrue(content.contains("getInnerRadius()"),
                "RadialMenuScaler should have getInnerRadius()");
            assertTrue(content.contains("getOuterRadius()"),
                "RadialMenuScaler should have getOuterRadius()");
            assertTrue(content.contains("getCenterX()"),
                "RadialMenuScaler should have getCenterX()");
            assertTrue(content.contains("update()") ||
                      content.contains("updateWithConfig("),
                "RadialMenuScaler should have an update method");
        }
    }

    // ============================================================
    // L1-11: UIScaleManager API Completeness
    // ============================================================

    @Nested
    @DisplayName("L1-11: UIScaleManager API Completeness")
    class UIScaleManagerApiTests {

        @Test
        @DisplayName("UIScaleManager exists and has required methods")
        void scaleManagerExists() throws IOException {
            Path managerPath = SRC_PATH.resolve("ui/core/UIScaleManager.java");

            assertTrue(Files.exists(managerPath),
                "UIScaleManager.java should exist in client/ui/core/");

            String content = Files.readString(managerPath);

            // Core scaling methods
            assertTrue(content.contains("public static int scale("),
                "UIScaleManager should have scale(int) method");
            assertTrue(content.contains("public static float scaleF("),
                "UIScaleManager should have scaleF(float) method");
            assertTrue(content.contains("public static void update()"),
                "UIScaleManager should have update() method");

            // Screen info methods
            assertTrue(content.contains("getScaledWidth()"),
                "UIScaleManager should have getScaledWidth()");
            assertTrue(content.contains("getScaledHeight()"),
                "UIScaleManager should have getScaledHeight()");
            assertTrue(content.contains("getCenterX()"),
                "UIScaleManager should have getCenterX()");
            assertTrue(content.contains("getCenterY()"),
                "UIScaleManager should have getCenterY()");

            // Grid alignment
            assertTrue(content.contains("GRID_UNIT"),
                "UIScaleManager should define GRID_UNIT");
            assertTrue(content.contains("snap("),
                "UIScaleManager should have snap() method");
        }

        @Test
        @DisplayName("UIScaleManager has responsive breakpoints")
        void scaleManagerHasBreakpoints() throws IOException {
            Path managerPath = SRC_PATH.resolve("ui/core/UIScaleManager.java");
            if (!Files.exists(managerPath)) {
                fail("UIScaleManager.java not found");
                return;
            }

            String content = Files.readString(managerPath);

            assertTrue(content.contains("enum ScreenSize") ||
                      content.contains("ScreenSize.SMALL"),
                "UIScaleManager should have ScreenSize breakpoints");

            assertTrue(content.contains("responsive(") ||
                      content.contains("getScreenSize()"),
                "UIScaleManager should have responsive breakpoint methods");
        }
    }

    // ============================================================
    // Helper Methods
    // ============================================================

    private boolean isExempt(Path file) {
        String fileName = file.getFileName().toString();

        // Exempt specific files
        if (EXEMPT_FILES.contains(fileName)) {
            return true;
        }

        // Exempt test files
        if (fileName.contains("Test")) {
            return true;
        }

        // Exempt by package
        String pathStr = file.toString().replace('\\', '/');
        for (String pkg : EXEMPT_PACKAGES) {
            if (pathStr.contains("/" + pkg + "/")) {
                return true;
            }
        }

        return false;
    }

    private boolean hasLayoutCode(String content) {
        // Check if file has layout-related code
        return content.contains("panelWidth") ||
               content.contains("panelHeight") ||
               content.contains("graphics.fill(") ||
               content.contains("graphics.drawString(") ||
               content.contains("width / 2") ||
               content.contains("height / 2") ||
               content.contains("centerX") ||
               content.contains("centerY");
    }

    private List<String> findHardcodedDimensions(String content) {
        List<String> issues = new ArrayList<>();

        // Skip if file imports and uses scale manager (compliant)
        if (content.contains(SCALE_MANAGER_IMPORT) &&
            content.contains("UIScaleManager.scale(")) {
            return issues;
        }

        // Skip files extending BaseDevModScreen - they handle responsiveness through update()
        if (content.contains("extends BaseDevModScreen")) {
            return issues;
        }

        // Skip files that call UIScaleManager.update() - they're using dynamic responsiveness
        if (content.contains("UIScaleManager.update()")) {
            return issues;
        }

        // Skip abstract classes (no concrete render implementation)
        if (content.contains("public abstract class")) {
            return issues;
        }

        // Check for Math.min with hardcoded large values
        Matcher mathMinMatcher = MATH_MIN_HARDCODED.matcher(content);
        while (mathMinMatcher.find()) {
            int value = Integer.parseInt(mathMinMatcher.group(1));
            if (value >= 100) {
                // Check if it's already using scale
                int start = Math.max(0, mathMinMatcher.start() - 30);
                String context = content.substring(start, mathMinMatcher.start());
                if (!context.contains("UIScaleManager.scale") &&
                    !context.contains("RadialMenuScaler")) {
                    issues.add("Math.min(" + value + ", ...) - consider UIScaleManager.scale(" + value + ")");
                }
            }
        }

        // Check for direct pixel assignments to layout variables
        Matcher directMatcher = DIRECT_PIXEL_LAYOUT.matcher(content);
        while (directMatcher.find()) {
            int value = Integer.parseInt(directMatcher.group(3));
            if (value >= 50) {
                String varName = directMatcher.group(2);
                issues.add(varName + " = " + value + " - consider UIScaleManager.scale(" + value + ")");
            }
        }

        return issues;
    }

    // ============================================================
    // L1-12: Coverage Report (Informational)
    // ============================================================

    @Nested
    @DisplayName("L1-12: Scaling Coverage Report")
    class CoverageReportTests {

        @Test
        @DisplayName("Generate scaling compliance report")
        void generateCoverageReport() throws IOException {
            if (!Files.exists(SRC_PATH)) {
                System.out.println("Source path not found, skipping coverage report");
                return;
            }

            int totalScreens = 0;
            int compliantScreens = 0;
            int totalOverlays = 0;
            int compliantOverlays = 0;

            // Check screens
            Path uiPath = SRC_PATH.resolve("ui");
            if (Files.exists(uiPath)) {
                try (Stream<Path> stream = Files.walk(uiPath)) {
                    List<Path> screens = stream
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith("Screen.java"))
                        .filter(p -> !isExempt(p))
                        .toList();

                    for (Path screen : screens) {
                        totalScreens++;
                        String content = Files.readString(screen);
                        if (content.contains(SCALE_MANAGER_IMPORT) ||
                            content.contains(RADIAL_SCALER_IMPORT)) {
                            compliantScreens++;
                        }
                    }
                }
            }

            // Check overlays
            Path overlayPath = SRC_PATH.resolve("overlay");
            if (Files.exists(overlayPath)) {
                try (Stream<Path> stream = Files.walk(overlayPath)) {
                    List<Path> overlays = stream
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".java"))
                        .filter(p -> !isExempt(p))
                        .filter(p -> {
                            try {
                                String content = Files.readString(p);
                                return content.contains("void render(") ||
                                       content.contains("GuiGraphics");
                            } catch (IOException e) {
                                return false;
                            }
                        })
                        .toList();

                    for (Path overlay : overlays) {
                        totalOverlays++;
                        String content = Files.readString(overlay);
                        if (content.contains(SCALE_MANAGER_IMPORT)) {
                            compliantOverlays++;
                        }
                    }
                }
            }

            // Print report
            System.out.println("\n========================================");
            System.out.println("UI SCALING COMPLIANCE REPORT");
            System.out.println("========================================");
            System.out.printf("Screens: %d/%d compliant (%.0f%%)%n",
                compliantScreens, totalScreens,
                totalScreens > 0 ? (100.0 * compliantScreens / totalScreens) : 100.0);
            System.out.printf("Overlays: %d/%d compliant (%.0f%%)%n",
                compliantOverlays, totalOverlays,
                totalOverlays > 0 ? (100.0 * compliantOverlays / totalOverlays) : 100.0);
            System.out.println("========================================\n");

            // This test always passes - it's informational
            assertTrue(true, "Coverage report generated");
        }
    }
}
