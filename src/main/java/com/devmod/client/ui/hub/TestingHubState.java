package com.devmod.client.ui.hub;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.devmod.client.testing.ActiveTestHudOverlay;
import com.devmod.client.testing.TestingSession;
import com.devmod.client.testing.TutorialManager;
import com.devmod.testing.TestCase;

public class TestingHubState {

    public static final TestingHubState INSTANCE = new TestingHubState();

    // Current state
    @Nullable
    private TestCase currentTest = null;
    @Nullable
    private String selectedCategory = null;
    private boolean isMinimized = false;
    private Set<ToolType> activeTools = EnumSet.noneOf(ToolType.class);

    // Scroll offsets for persistence
    private int categoryScrollOffset = 0;
    private int testScrollOffset = 0;

    // Filters
    private String searchQuery = "";
    private Set<TestCase.TestStatus> activeFilters = EnumSet.allOf(TestCase.TestStatus.class);

    // Session timestamp
    private long sessionStartTime = 0;

    private TestingHubState() {
        // Initialize filters (exclude IN_PROGRESS by default for clarity)
        activeFilters.remove(TestCase.TestStatus.IN_PROGRESS);
    }

    // === TEST MANAGEMENT ===

    @Nullable
    public TestCase getCurrentTest() {
        return currentTest;
    }

    public void setCurrentTest(@Nullable TestCase test) {
        this.currentTest = test;
        if (test != null) {
            this.selectedCategory = test.getCategory();
            // Update HUD if minimized
            if (isMinimized) {
                ActiveTestHudOverlay.setActiveTest(test);
            }
        }
    }

    public boolean hasActiveTest() {
        return currentTest != null;
    }

    /**
     * Advance to the next pending test in the same category,
     * or to the first category with pending tests.
     */
    public void advanceToNextTest() {
        TestCase next = findNextPendingTest();
        if (next != null) {
            setCurrentTest(next);
        } else {
            currentTest = null;
            if (isMinimized) {
                ActiveTestHudOverlay.clearActiveTest();
            }
        }
    }

    @Nullable
    private TestCase findNextPendingTest() {
        Map<String, List<TestCase>> categories = TestingSession.INSTANCE.getCategorizedTests();

        // First search in the current category
        if (selectedCategory != null && categories.containsKey(selectedCategory)) {
            List<TestCase> tests = categories.get(selectedCategory);
            boolean foundCurrent = (currentTest == null);

            for (TestCase test : tests) {
                if (!foundCurrent) {
                    if (test.equals(currentTest)) {
                        foundCurrent = true;
                    }
                    continue;
                }
                if (test.getStatus() == TestCase.TestStatus.PENDING) {
                    return test;
                }
            }
        }

        // Then search in other categories
        for (Map.Entry<String, List<TestCase>> entry : categories.entrySet()) {
            if (entry.getKey().equals(selectedCategory)) continue;

            for (TestCase test : entry.getValue()) {
                if (test.getStatus() == TestCase.TestStatus.PENDING) {
                    return test;
                }
            }
        }

        // Finally search from the start of the current category
        if (selectedCategory != null && categories.containsKey(selectedCategory)) {
            for (TestCase test : categories.get(selectedCategory)) {
                if (test.getStatus() == TestCase.TestStatus.PENDING) {
                    return test;
                }
            }
        }

        return null;
    }

    // === CATEGORY MANAGEMENT ===

    @Nullable
    public String getSelectedCategory() {
        return selectedCategory;
    }

    public void setSelectedCategory(String category) {
        this.selectedCategory = category;
        this.testScrollOffset = 0; // Reset scroll when changing category
    }

    // === MINIMIZATION ===

    public boolean isMinimized() {
        return isMinimized;
    }

    public void setMinimized(boolean minimized) {
        this.isMinimized = minimized;
        if (minimized && currentTest != null) {
            ActiveTestHudOverlay.setActiveTest(currentTest);
            ActiveTestHudOverlay.setEnabled(true);
        } else {
            ActiveTestHudOverlay.setEnabled(false);
        }
    }

    // === TOOL MANAGEMENT ===

    public boolean isToolEnabled(ToolType tool) {
        return tool.isEnabled();
    }

    public void setToolEnabled(ToolType tool, boolean enabled) {
        tool.setEnabled(enabled);
        if (enabled) {
            activeTools.add(tool);
        } else {
            activeTools.remove(tool);
        }
    }

    public void toggleTool(ToolType tool) {
        setToolEnabled(tool, !tool.isEnabled());
    }

    /**
     * Returns the tools required for the current test.
     * Analyzes test instructions to determine which tools are needed.
     */
    public Set<ToolType> getRequiredToolsForCurrentTest() {
        if (currentTest == null) {
            return EnumSet.noneOf(ToolType.class);
        }
        return inferRequiredTools(currentTest);
    }

    /**
     * Infers required tools by analyzing test instructions.
     */
    public static Set<ToolType> inferRequiredTools(TestCase test) {
        Set<ToolType> required = EnumSet.noneOf(ToolType.class);
        String instructions = test.getInstructions().toLowerCase();
        String description = test.getDescription().toLowerCase();
        String combined = instructions + " " + description;

        // Search for tool references in instructions
        if (combined.contains("debug") || combined.contains("press g")) {
            required.add(ToolType.DEBUG);
        }
        if (combined.contains("light level") || combined.contains("press l")) {
            required.add(ToolType.LIGHT_LEVEL);
        }
        if (combined.contains("heatmap") || combined.contains("press h")) {
            required.add(ToolType.HEATMAP);
        }
        if (combined.contains("room bound") || combined.contains("press r")) {
            required.add(ToolType.ROOM_BOUNDS);
        }
        if (combined.contains("pathfinding") || combined.contains("press p")) {
            required.add(ToolType.PATHFINDING);
        }
        if (combined.contains("line of sight") || combined.contains("press v")) {
            required.add(ToolType.LINE_OF_SIGHT);
        }
        if (combined.contains("vertical level") || combined.contains("press y")) {
            required.add(ToolType.VERTICAL_LEVELS);
        }
        if (combined.contains("safe spot") || combined.contains("press c")) {
            required.add(ToolType.SAFE_SPOTS);
        }

        return required;
    }

    /**
     * Check if all required tools are active.
     */
    public boolean areAllRequiredToolsEnabled() {
        Set<ToolType> required = getRequiredToolsForCurrentTest();
        return required.stream().allMatch(ToolType::isEnabled);
    }

    // === SCROLL PERSISTENCE ===

    public int getCategoryScrollOffset() {
        return categoryScrollOffset;
    }

    public void setCategoryScrollOffset(int offset) {
        this.categoryScrollOffset = Math.max(0, offset);
    }

    public int getTestScrollOffset() {
        return testScrollOffset;
    }

    public void setTestScrollOffset(int offset) {
        this.testScrollOffset = Math.max(0, offset);
    }

    // === FILTERS ===

    public String getSearchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(String query) {
        this.searchQuery = query != null ? query : "";
    }

    public Set<TestCase.TestStatus> getActiveFilters() {
        return activeFilters;
    }

    public void toggleFilter(TestCase.TestStatus status) {
        if (activeFilters.contains(status)) {
            activeFilters.remove(status);
        } else {
            activeFilters.add(status);
        }
    }

    public boolean isFilterActive(TestCase.TestStatus status) {
        return activeFilters.contains(status);
    }

    // === SESSION ===

    public void startSession(String testerName) {
        if (!TestingSession.INSTANCE.isSessionActive()) {
            TestingSession.INSTANCE.startSession(testerName);
        }
        sessionStartTime = System.currentTimeMillis();

        // Select first category and first test if not already selected
        if (selectedCategory == null) {
            List<String> categories = TestingSession.INSTANCE.getCategories();
            if (!categories.isEmpty()) {
                selectedCategory = categories.get(0);
            }
        }
    }

    public void resumeSession() {
        if (TestingSession.INSTANCE.hasExistingSession()) {
            TestingSession.INSTANCE.resumeSession(TestingSession.INSTANCE.getTesterName());
        }
        // If we don't have sessionStartTime, calculate from session start date
        if (sessionStartTime == 0 && TestingSession.INSTANCE.getSessionStarted() != null) {
            sessionStartTime = TestingSession.INSTANCE.getSessionStarted().toEpochMilli();
        } else if (sessionStartTime == 0) {
            sessionStartTime = System.currentTimeMillis();
        }
    }

    public long getSessionDuration() {
        if (sessionStartTime == 0) {
            // Fallback: calculate from start date if available
            if (TestingSession.INSTANCE.getSessionStarted() != null) {
                return System.currentTimeMillis() - TestingSession.INSTANCE.getSessionStarted().toEpochMilli();
            }
            return 0;
        }
        return System.currentTimeMillis() - sessionStartTime;
    }

    public boolean hasExistingSession() {
        return TestingSession.INSTANCE.hasExistingSession() &&
               TestingSession.INSTANCE.getCompletedTests() > 0;
    }

    public boolean isSessionActive() {
        return TestingSession.INSTANCE.isSessionActive();
    }

    // === VERDICT ===

    /**
     * Apply a verdict to the current test (used by global hotkeys).
     */
    public void giveVerdict(Verdict verdict) {
        if (currentTest == null) return;

        switch (verdict) {
            case PASS -> {
                currentTest.markPassed("Via Testing Hub hotkey");
                TutorialManager.INSTANCE.awardTestXP(currentTest, true);
            }
            case FAIL -> {
                currentTest.markFailed("Via Testing Hub hotkey", "Manual failure");
                TutorialManager.INSTANCE.awardTestXP(currentTest, false);
            }
            case SKIP -> currentTest.skip("Via Testing Hub hotkey");
        }

        TestingSession.INSTANCE.markDirty();
        advanceToNextTest();
    }

    // === STATE PERSISTENCE ===

    public void saveState() {
        // Save scroll offsets and other transient states
        // Could be extended to save to JSON file
    }

    public void loadState() {
        // Load previous state if exists
    }

    // === RESET ===

    public void reset() {
        currentTest = null;
        selectedCategory = null;
        isMinimized = false;
        activeTools.clear();
        categoryScrollOffset = 0;
        testScrollOffset = 0;
        searchQuery = "";
        activeFilters = EnumSet.allOf(TestCase.TestStatus.class);
        activeFilters.remove(TestCase.TestStatus.IN_PROGRESS);
        sessionStartTime = 0;
    }
}
