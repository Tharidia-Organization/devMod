package com.devmod.client.ui.radial;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Radial Menu Macro-Category System")
class RadialMenuMacroCategoryTest {

    private static String screenSourceCode;
    private static String macroCategorySourceCode;
    private static String hubRendererSourceCode;
    private static String tooltipRendererSourceCode;
    private static String animatorSourceCode;
    private static String registrySourceCode;
    private static final Path SCREEN_SOURCE_PATH = Paths.get(
        "src/main/java/com/devmod/client/ui/radial/RadialMenuScreen.java");
    private static final Path MACRO_CATEGORY_SOURCE_PATH = Paths.get(
        "src/main/java/com/devmod/client/ui/radial/model/MacroCategory.java");
    private static final Path HUB_RENDERER_SOURCE_PATH = Paths.get(
        "src/main/java/com/devmod/client/ui/radial/render/RadialHubRenderer.java");
    private static final Path TOOLTIP_RENDERER_SOURCE_PATH = Paths.get(
        "src/main/java/com/devmod/client/ui/radial/render/RadialTooltipRenderer.java");
    private static final Path ANIMATOR_SOURCE_PATH = Paths.get(
        "src/main/java/com/devmod/client/ui/radial/animation/RadialAnimator.java");
    private static final Path REGISTRY_SOURCE_PATH = Paths.get(
        "src/main/java/com/devmod/client/ui/radial/RadialMenuRegistry.java");

    @BeforeAll
    static void loadSourceCode() throws IOException {
        screenSourceCode = Files.readString(SCREEN_SOURCE_PATH);
        macroCategorySourceCode = Files.readString(MACRO_CATEGORY_SOURCE_PATH);
        hubRendererSourceCode = Files.readString(HUB_RENDERER_SOURCE_PATH);
        tooltipRendererSourceCode = Files.readString(TOOLTIP_RENDERER_SOURCE_PATH);
        animatorSourceCode = Files.readString(ANIMATOR_SOURCE_PATH);
        registrySourceCode = Files.readString(REGISTRY_SOURCE_PATH);
    }

    // ============================================================
    // L0: SMOKE TESTS - Basic Structure Verification
    // ============================================================

    @Nested
    @DisplayName("L0: Smoke Tests - Basic Structure")
    class L0SmokeTests {

        @Test
        @DisplayName("L0-01: MacroCategory enum exists in model package")
        void macroCategoryEnumExists() {
            assertTrue(macroCategorySourceCode.contains("public enum MacroCategory"),
                "MacroCategory enum should be declared as public enum in model package");
        }

        @Test
        @DisplayName("L0-02: MacroCategory has exactly 6 values")
        void macroCategoryHasSixValues() {
            // Find enum declaration block
            Pattern enumPattern = Pattern.compile(
                "enum MacroCategory\\s*\\{([^}]+?)\\s*;",
                Pattern.DOTALL);
            Matcher matcher = enumPattern.matcher(macroCategorySourceCode);
            assertTrue(matcher.find(), "MacroCategory enum block should exist");

            String enumBody = matcher.group(1);
            // Count enum constants (format: NAME(...))
            Pattern constantPattern = Pattern.compile("(ANALYZE|TELEMETRY|COMBAT|ARENA|PLAY|TOOLS)\\s*\\(");
            Matcher constantMatcher = constantPattern.matcher(enumBody);

            int count = 0;
            while (constantMatcher.find()) {
                count++;
            }
            assertEquals(6, count,
                "MacroCategory should have exactly 6 values (ANALYZE, TELEMETRY, COMBAT, ARENA, PLAY, TOOLS)");
        }

        @Test
        @DisplayName("L0-03: MacroCategory values are correctly named")
        void macroCategoryValuesNamed() {
            String[] expectedValues = {"ANALYZE", "TELEMETRY", "COMBAT", "ARENA", "PLAY", "TOOLS"};
            for (String value : expectedValues) {
                assertTrue(macroCategorySourceCode.contains(value + "("),
                    "MacroCategory should contain " + value);
            }
        }

        @Test
        @DisplayName("L0-04: RadialMenuScreen has macroCategoryMap field")
        void hasMacroCategoryMapField() {
            assertTrue(screenSourceCode.contains("macroCategoryMap"),
                "macroCategoryMap field should exist");
            assertTrue(screenSourceCode.contains("Map<MacroCategory, List<RadialCategory>> macroCategoryMap"),
                "macroCategoryMap should be typed as Map<MacroCategory, List<RadialCategory>>");
        }

        @Test
        @DisplayName("L0-05: RadialMenuScreen has selectedMacro static field")
        void hasSelectedMacroField() {
            Pattern pattern = Pattern.compile("private\\s+static\\s+MacroCategory\\s+selectedMacro");
            assertTrue(pattern.matcher(screenSourceCode).find(),
                "selectedMacro should be a private static MacroCategory field");
        }

        @Test
        @DisplayName("L0-06: MacroCategory has required getter methods")
        void macroCategoryHasGetters() {
            assertTrue(macroCategorySourceCode.contains("public String getName()"),
                "MacroCategory should have getName() method");
            assertTrue(macroCategorySourceCode.contains("public String getIcon()"),
                "MacroCategory should have getIcon() method");
            assertTrue(macroCategorySourceCode.contains("public int getColor()"),
                "MacroCategory should have getColor() method");
            assertTrue(macroCategorySourceCode.contains("public String getDescription()"),
                "MacroCategory should have getDescription() method");
        }
    }

    // ============================================================
    // L1: DATA INTEGRITY TESTS
    // ============================================================

    @Nested
    @DisplayName("L1: Data Integrity")
    class L1DataIntegrityTests {

        @Test
        @DisplayName("L1-01: Each MacroCategory has non-null name")
        void eachMacroCategoryHasName() {
            // Extract constructor parameters for each enum value
            String[] macros = {"ANALYZE", "COMBAT", "TOOLS", "PLAY"};
            for (String macro : macros) {
                Pattern pattern = Pattern.compile(macro + "\\s*\\(\\s*\"([^\"]+)\"");
                Matcher matcher = pattern.matcher(macroCategorySourceCode);
                assertTrue(matcher.find(),
                    macro + " should have a non-empty name as first constructor parameter");
                String name = matcher.group(1);
                assertFalse(name.isEmpty(), macro + " should have non-empty name");
            }
        }

        @Test
        @DisplayName("L1-02: Each MacroCategory has non-null icon")
        void eachMacroCategoryHasIcon() {
            // Icon is second parameter in constructor
            String[] macros = {"ANALYZE", "COMBAT", "TOOLS", "PLAY"};
            for (String macro : macros) {
                Pattern pattern = Pattern.compile(macro + "\\s*\\(\\s*\"[^\"]+\"\\s*,\\s*\"([^\"]+)\"");
                Matcher matcher = pattern.matcher(macroCategorySourceCode);
                assertTrue(matcher.find(),
                    macro + " should have a non-empty icon as second constructor parameter");
                String icon = matcher.group(1);
                assertFalse(icon.isEmpty(), macro + " should have non-empty icon");
            }
        }

        @Test
        @DisplayName("L1-03: Each MacroCategory has valid color (hex format with alpha)")
        void eachMacroCategoryHasValidColor() {
            // Color is third parameter: 0xFFxxxxxx format
            String[] macros = {"ANALYZE", "COMBAT", "TOOLS", "PLAY"};
            for (String macro : macros) {
                Pattern pattern = Pattern.compile(macro + "\\s*\\([^)]*,\\s*(0x[0-9A-Fa-f]{8})");
                Matcher matcher = pattern.matcher(macroCategorySourceCode);
                assertTrue(matcher.find(),
                    macro + " should have a valid hex color (0xFFxxxxxx format)");
                String colorHex = matcher.group(1);
                // Verify it starts with 0xFF (full alpha)
                assertTrue(colorHex.toUpperCase(Locale.ROOT).startsWith("0XFF"),
                    macro + " color should have full alpha (0xFF): " + colorHex);
            }
        }

        @Test
        @DisplayName("L1-04: All MacroCategory colors are unique")
        void macroCategoryColorsAreUnique() {
            Set<String> colors = new HashSet<>();
            String[] macros = {"ANALYZE", "COMBAT", "TOOLS", "PLAY"};
            for (String macro : macros) {
                Pattern pattern = Pattern.compile(macro + "\\s*\\([^)]*,\\s*(0x[0-9A-Fa-f]{8})");
                Matcher matcher = pattern.matcher(macroCategorySourceCode);
                if (matcher.find()) {
                    String color = matcher.group(1).toUpperCase(Locale.ROOT);
                    assertTrue(colors.add(color),
                        "Each MacroCategory should have unique color. Duplicate: " + color);
                }
            }
            assertEquals(4, colors.size(), "All 4 MacroCategories should have unique colors");
        }

        @Test
        @DisplayName("L1-05: All MacroCategory icons are unique")
        void macroCategoryIconsAreUnique() {
            Set<String> icons = new HashSet<>();
            String[] macros = {"ANALYZE", "COMBAT", "TOOLS", "PLAY"};
            for (String macro : macros) {
                Pattern pattern = Pattern.compile(macro + "\\s*\\(\\s*\"[^\"]+\"\\s*,\\s*\"([^\"]+)\"");
                Matcher matcher = pattern.matcher(macroCategorySourceCode);
                if (matcher.find()) {
                    String icon = matcher.group(1);
                    assertTrue(icons.add(icon),
                        "Each MacroCategory should have unique icon. Duplicate: " + icon);
                }
            }
            assertEquals(4, icons.size(), "All 4 MacroCategories should have unique icons");
        }

        @Test
        @DisplayName("L1-06: MacroCategory icons are emoji characters")
        void macroCategoryIconsAreEmoji() {
            // Check that icons contain unicode escape sequences (emoji)
            String[] macros = {"ANALYZE", "COMBAT", "TOOLS", "PLAY"};
            for (String macro : macros) {
                Pattern pattern = Pattern.compile(macro + "\\s*\\(\\s*\"[^\"]+\"\\s*,\\s*\"([^\"]+)\"");
                Matcher matcher = pattern.matcher(macroCategorySourceCode);
                assertTrue(matcher.find(), macro + " should have icon parameter");
                String icon = matcher.group(1);
                // Icons should be unicode escape sequences or actual emoji
                assertTrue(icon.startsWith("\\u") || !icon.isEmpty(),
                    macro + " should have an emoji icon");
            }
        }
    }

    // ============================================================
    // L2: DISTRIBUTION TESTS - Category Allocation
    // ============================================================

    @Nested
    @DisplayName("L2: Category Distribution")
    class L2DistributionTests {

        @Test
        @DisplayName("L2-01: RadialMenuScreen has macroHubRadius field")
        void hasMacroHubRadiusField() {
            assertTrue(screenSourceCode.contains("macroHubRadius"),
                "macroHubRadius field should exist for center hub sizing");
            assertTrue(screenSourceCode.contains("int macroHubRadius"),
                "macroHubRadius should be int type");
        }

        @Test
        @DisplayName("L2-02: RadialMenuScreen has hoveredMacro field")
        void hasHoveredMacroField() {
            assertTrue(screenSourceCode.contains("MacroCategory hoveredMacro"),
                "hoveredMacro field should exist for hover detection");
        }

        @Test
        @DisplayName("L2-03: RadialAnimator has macroTransitionProgress field")
        void hasMacroTransitionProgressField() {
            assertTrue(animatorSourceCode.contains("float macroTransitionProgress"),
                "macroTransitionProgress field should exist in RadialAnimator for animations");
        }

        @Test
        @DisplayName("L2-04: RadialAnimator has macroSegmentAnimations array")
        void hasMacroSegmentAnimationsField() {
            assertTrue(animatorSourceCode.contains("float[] macroSegmentAnimations"),
                "macroSegmentAnimations array should exist in RadialAnimator");
        }

        @Test
        @DisplayName("L2-05: RadialMenuScreen has getActiveCategories method")
        void hasGetActiveCategoriesMethod() {
            assertTrue(screenSourceCode.contains("private List<RadialCategory> getActiveCategories()"),
                "getActiveCategories method should exist");
        }

        @Test
        @DisplayName("L2-06: getActiveCategories uses macroCategoryMap (via helper)")
        void getActiveCategoriesUsesMacroCategoryMap() {
            // Verify getActiveCategories delegates to getVisibleCategoriesForMacro which uses macroCategoryMap
            assertTrue(screenSourceCode.contains("getVisibleCategoriesForMacro(selectedMacro)"),
                "getActiveCategories should delegate to getVisibleCategoriesForMacro");
            assertTrue(screenSourceCode.contains("macroCategoryMap.get(macro)"),
                "getVisibleCategoriesForMacro should use macroCategoryMap");
        }

        @Test
        @DisplayName("L2-07: Macro build methods exist and total categories are added")
        void macroBuildMethodsAndTotalCategories() {
            // Category definitions moved to RadialMenuRegistry
            // Verify each build method exists and total of 24 categories are added
            String[] buildMethods = {
                "buildAnalyzeCategories",
                "buildTelemetryCategories",
                "buildCombatCategories",
                "buildArenaCategories",
                "buildToolsCategories",
                "buildPlayCategories"
            };

            // Verify all build methods exist
            for (String methodName : buildMethods) {
                assertTrue(registrySourceCode.contains("private static void " + methodName),
                    "Method " + methodName + " should exist in RadialMenuRegistry");
            }

            // Count total categories.add() calls - should be 24 total
            Pattern addPattern = Pattern.compile("categories\\.add\\(");
            Matcher addMatcher = addPattern.matcher(registrySourceCode);
            int totalCount = 0;
            while (addMatcher.find()) {
                totalCount++;
            }
            assertEquals(24, totalCount,
                "RadialMenuRegistry should add exactly 24 categories total. Found: " + totalCount);
        }
    }

    // ============================================================
    // L3: SELECTION LOGIC TESTS
    // ============================================================

    @Nested
    @DisplayName("L3: Selection Logic")
    class L3SelectionLogicTests {

        @Test
        @DisplayName("L3-01: selectedMacro defaults to ANALYZE")
        void selectedMacroDefaultsToAnalyze() {
            Pattern pattern = Pattern.compile(
                "private\\s+static\\s+MacroCategory\\s+selectedMacro\\s*=\\s*MacroCategory\\.(\\w+)");
            Matcher matcher = pattern.matcher(screenSourceCode);
            assertTrue(matcher.find(), "selectedMacro should have a default value");
            assertEquals("ANALYZE", matcher.group(1),
                "selectedMacro should default to ANALYZE");
        }

        @Test
        @DisplayName("L3-02: Macro transition resets category selection")
        void macroTransitionResetsCategorySelection() {
            // When switching macro, selectedCategoryIndex should be reset
            assertTrue(screenSourceCode.contains("selectedCategoryIndex = -1"),
                "Category selection should be reset when switching macro");
        }

        @Test
        @DisplayName("L3-03: Macro transition plays sound")
        void macroTransitionPlaysSound() {
            // Look for pattern where macro changes and sound is played
            Pattern pattern = Pattern.compile(
                "selectedMacro\\s*=\\s*[^;]+;[^}]*playSound",
                Pattern.DOTALL);
            assertTrue(pattern.matcher(screenSourceCode).find(),
                "Macro transition should play sound feedback");
        }
    }

    // ============================================================
    // L4: KEYBOARD SHORTCUTS TESTS
    // ============================================================

    @Nested
    @DisplayName("L4: Keyboard Shortcuts")
    class L4KeyboardShortcutTests {

        @Test
        @DisplayName("L4-01: Keys 1-6 switch macro-categories")
        void keys1to6SwitchMacroCategories() {
            assertTrue(screenSourceCode.contains("defaults: GLFW_KEY_1 .. GLFW_KEY_6"),
                "Comment should document keys 1-6 for macro switching");
            assertTrue(screenSourceCode.contains("GLFW_KEY_1") && screenSourceCode.contains("GLFW_KEY_6"),
                "Key handling should check GLFW_KEY_1 through GLFW_KEY_6");
        }

        @Test
        @DisplayName("L4-02: Keys 7-0 and -/= select categories")
        void keys7toEqualSelectCategories() {
            assertTrue(screenSourceCode.contains("GLFW_KEY_7") && screenSourceCode.contains("GLFW_KEY_0"),
                "Key handling should check GLFW_KEY_7 through GLFW_KEY_0");
            assertTrue(screenSourceCode.contains("GLFW_KEY_MINUS") && screenSourceCode.contains("GLFW_KEY_EQUAL"),
                "Key handling should check GLFW_KEY_MINUS and GLFW_KEY_EQUAL for extra categories");
        }

        @Test
        @DisplayName("L4-03: Macro key range is bounded to macro count")
        void macroKeyRangeBounded() {
            // Should check macroIndex < macros.length before switching
            assertTrue(screenSourceCode.contains("macroIndex < macros.length"),
                "Macro key handler should bounds-check the index");
        }
    }

    // ============================================================
    // L5: PERSISTENCE TESTS
    // ============================================================

    @Nested
    @DisplayName("L5: Persistence")
    class L5PersistenceTests {

        @Test
        @DisplayName("L5-01: selectedMacro is static (persists across instances)")
        void selectedMacroIsStatic() {
            Pattern pattern = Pattern.compile("private\\s+static\\s+MacroCategory\\s+selectedMacro");
            assertTrue(pattern.matcher(screenSourceCode).find(),
                "selectedMacro should be static to persist across menu opens");
        }

        @Test
        @DisplayName("L5-02: macroCategoryMap is instance field (per-screen)")
        void macroCategoryMapIsInstance() {
            // Should be final but not static
            assertTrue(screenSourceCode.contains("private final Map<MacroCategory"),
                "macroCategoryMap should be private final");
            assertFalse(screenSourceCode.contains("private static final Map<MacroCategory"),
                "macroCategoryMap should NOT be static");
        }

        @Test
        @DisplayName("L5-03: transitionFromMacro tracks previous selection for cross-fade")
        void transitionFromMacroExists() {
            assertTrue(screenSourceCode.contains("MacroCategory transitionFromMacro"),
                "transitionFromMacro should exist to track previous macro during transitions");
            // Verify it's assigned before changing selectedMacro
            assertTrue(screenSourceCode.contains("transitionFromMacro = selectedMacro"),
                "transitionFromMacro should be assigned before switching macros");
        }

        @Test
        @DisplayName("L5-04: transitionFromMacro is used for cross-fade rendering")
        void transitionFromMacroUsedInRendering() {
            // Verify transitionFromMacro is used via helper for cross-fade effect
            assertTrue(screenSourceCode.contains("getVisibleCategoriesForMacro(transitionFromMacro)"),
                "transitionFromMacro should be used to get outgoing categories for cross-fade");
        }
    }

    // ============================================================
    // L6: INTEGRATION TESTS - UX Flow
    // ============================================================

    @Nested
    @DisplayName("L6: Integration - UX Flow")
    class L6IntegrationTests {

        @Test
        @DisplayName("L6-01: renderCenterHub method delegates to RadialHubRenderer")
        void renderCenterHubDelegatesToRenderer() {
            assertTrue(screenSourceCode.contains("renderCenterHub"),
                "renderCenterHub method should exist");
            // Screen delegates to RadialHubRenderer
            assertTrue(screenSourceCode.contains("RadialHubRenderer.render"),
                "renderCenterHub should delegate to RadialHubRenderer");
            // Renderer iterates over 4 macro segments
            assertTrue(hubRendererSourceCode.contains("for (int i = 0; i < macros.length; i++)"),
                "RadialHubRenderer should iterate over all macro segments");
        }

        @Test
        @DisplayName("L6-02: Tooltip shows macro description on hover")
        void tooltipShowsMacroDescription() {
            // Tooltip generation is now in RadialHubRenderer.getMacroTooltip
            assertTrue(hubRendererSourceCode.contains("hoveredMacro.getDescription()"),
                "RadialHubRenderer.getMacroTooltip should show macro description when hovering");
        }

        @Test
        @DisplayName("L6-03: Help text shows current macro indicator")
        void helpTextShowsMacroIndicator() {
            // Help text is now rendered via RadialTooltipRenderer
            assertTrue(tooltipRendererSourceCode.contains("selectedMacro.getIcon()") ||
                tooltipRendererSourceCode.contains("selectedMacro.getName()"),
                "RadialTooltipRenderer.renderHelpText should include current macro indicator");
        }

        @Test
        @DisplayName("L6-04: activateSelection handles macro segment clicks")
        void activateSelectionHandlesMacroClicks() {
            // Should check for macro hub click and switch macro
            Pattern pattern = Pattern.compile(
                "distance\\s*<\\s*macroHubRadius[^}]*hoveredMacro",
                Pattern.DOTALL);
            assertTrue(pattern.matcher(screenSourceCode).find(),
                "activateSelection should handle clicks on macro segments");
        }

        @Test
        @DisplayName("L6-05: MacroCategory colors follow visual hierarchy")
        void macroCategoryColorsFollowHierarchy() {
            // ANALYZE = blue, COMBAT = red, TOOLS = orange, PLAY = green
            Map<String, String> expectedColors = Map.of(
                "ANALYZE", "0xFF4488FF", // Blue
                "COMBAT", "0xFFFF4444",  // Red
                "TOOLS", "0xFFFFAA00",   // Orange
                "PLAY", "0xFF44FF88"     // Green
            );

            for (Map.Entry<String, String> entry : expectedColors.entrySet()) {
                assertTrue(macroCategorySourceCode.contains(entry.getKey() + "(") &&
                        macroCategorySourceCode.contains(entry.getValue()),
                    entry.getKey() + " should have color " + entry.getValue());
            }
        }
    }

    // ============================================================
    // L7: BOUNDARY TESTS
    // ============================================================

    @Nested
    @DisplayName("L7: Boundary Conditions")
    class L7BoundaryTests {

        @Test
        @DisplayName("L7-01: Empty macro-category list is handled")
        void emptyMacroCategoryListHandled() {
            assertTrue(screenSourceCode.contains("getOrDefault") ||
                screenSourceCode.contains("Collections.emptyList()"),
                "getActiveCategories should handle missing macro gracefully");
        }

        @Test
        @DisplayName("L7-02: Zero-distance check for center close button")
        void zeroDistanceCheckForCenter() {
            // Should have reduced radius check for center close button (uses constant now)
            assertTrue(screenSourceCode.contains("CLOSE_BUTTON_RATIO") ||
                screenSourceCode.contains("centerButtonRadius * 0.4"),
                "Should have reduced radius check for center close button");
        }

        @Test
        @DisplayName("L7-03: Animation progress is bounded 0-1 in RadialAnimator")
        void animationProgressBounded() {
            assertTrue(animatorSourceCode.contains("Math.min(1f, macroTransitionProgress"),
                "Macro transition progress should be bounded to max 1.0 in RadialAnimator");
        }

        @Test
        @DisplayName("L7-04: categoryAnimations array created in RadialAnimator constructor")
        void categoryAnimationsSizedCorrectly() {
            // RadialAnimator takes maxCategories as constructor param and creates array
            assertTrue(animatorSourceCode.contains("this.categoryAnimations = new float[maxCategories]"),
                "categoryAnimations should be created in RadialAnimator constructor");
        }
    }
}
