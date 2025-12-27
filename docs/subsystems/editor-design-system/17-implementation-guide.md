# 17. Implementation Guide

> Last updated: 2025-12-26
> Status: HISTORICAL (design system snapshot)

This guide provides a recommended implementation order, code templates, and common pitfalls for building components within the Editor Design System.

## General Philosophy

1.  **Data First**: Start by defining the data structures (records, plain classes) that represent the state of your component.
2.  **Build the Section**: Create a new class implementing the appropriate `EditorSection` sub-interface.
3.  **Render the View**: Implement the `render()` method to draw your component based on its state.
4.  **Handle Input**: Implement `mouseClicked()`, `keyPressed()`, etc., to make the component interactive.
5.  **Integrate into Module**: Add the new section to the list of sections returned by your `EditorModule`.

---

## Focus: Implementing the Debug Panel

This guide focuses on implementing the `DebugInfoSection` as the primary view for the "DEBUG" tab.

### 1. Recommended Implementation Order

1.  **Define Data Records**: Before creating the section itself, define the immutable records that will hold the debug data. This ensures a clean separation between data and rendering.
    *   `ItemDebugInfo`
    *   `ValueComparison`
    *   `ChangeLogEntry`

2.  **Create the `DebugInfoSection` class**: Create the main class that will implement `EditorSection.CustomSection`. It will take the data records in its constructor.

3.  **Implement the `render` method**: Break the rendering logic into smaller, manageable methods, one for each part of the panel.
    *   `renderHeader()` (for the title and copy button)
    *   `renderInfoBlock()` (for item identification)
    *   `renderComparisonBlock()` (for value comparisons)
    *   `renderHistoryBlock()` (for the session log)
    *   `renderNbtBlock()` (for the NBT viewer)

4.  **Implement Input Handling**: Implement `mouseClicked()` to handle clicks on the "Copy Debug" button.

5.  **Integrate with `copyDebugInfoToClipboard()`**: The `onCopy` runnable passed to the `DebugInfoSection` constructor should call a method in the module that gathers all the data and formats it for the clipboard.

### 2. Dependencies

The `DebugInfoSection` has the following key dependencies:

*   **`EditorSection.CustomSection`**: The interface it must implement.
*   **`ItemDebugInfo`, `ValueComparison`**: The data records it needs to render the information. These must be populated by the parent module.
*   **`UIConstants`**: Used for consistent colors, spacing, and dimensions.
*   **`AxiomRenderer`**: (Optional) Can be used for common rendering tasks like drawing borders.

### 3. Code Templates

#### Data Record Templates

```java
// In a new file: `ItemDebugInfo.java` or as a nested record
public record ItemDebugInfo(
    String registryName,
    int stackSize,
    int currentDamage,
    int maxDamage,
    int nbtTagCount,
    boolean hasCustomData
) {}

// In a new file: `ValueComparison.java` or as a nested record
public record ValueComparison(
    String attributeName,
    double originalValue,
    double currentValue,
    double serverValue,
    boolean isModified,
    boolean hasMismatch
) {}
```

#### `DebugInfoSection` Skeleton

```java
// In a new file: `DebugInfoSection.java`
public final class DebugInfoSection implements EditorSection.CustomSection {

    private final ItemDebugInfo debugInfo;
    private final List<ValueComparison> comparisons;
    private final List<String> changeLog;
    private final List<String> nbtLines;
    private final Runnable onCopy;

    public DebugInfoSection(
        ItemDebugInfo debugInfo,
        List<ValueComparison> comparisons,
        List<String> changeLog,
        List<String> nbtLines,
        Runnable onCopy
    ) {
        this.debugInfo = debugInfo;
        this.comparisons = comparisons;
        this.changeLog = changeLog;
        this.nbtLines = nbtLines;
        this.onCopy = onCopy;
    }

    @Override
    public String getId() { return "debug"; }

    @Override
    public String getLabel() { return "Debug Info"; }

    @Override
    public int getHeight() {
        // Calculate the total height based on the content
        return 300; // Placeholder
    }

    @Override
    public void render(GuiGraphics graphics, ResponsiveLayout.Rect bounds, int mouseX, int mouseY) {
        // Main rendering logic here
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle clicks on the copy button
        return false;
    }
}
```

### 4. Common Pitfalls and Solutions

*   **Pitfall**: The debug panel becomes too tall and overflows the content area.
    *   **Solution**: The `getHeight()` method must accurately calculate the total height required by all the content. Ensure it accounts for the number of lines in each sub-section (comparisons, logs, NBT). If the height becomes excessive, consider making sections collapsible.

*   **Pitfall**: NBT data is too large and causes performance issues or unreadable output.
    *   **Solution**: The NBT formatting logic (`formatNbtLines`) should have a `maxLines` parameter to prevent rendering excessively large tags. Truncate long lines with an ellipsis (...).

*   **Pitfall**: `ValueComparison` shows `[MISMATCH]` incorrectly because server data is not available.
    *   **Solution**: Ensure that `serverValue` is populated with a sentinel value (e.g., `Double.NaN`) when not available. The rendering logic should check for this and avoid showing a mismatch, instead showing a "server n/a" message.

*   **Pitfall**: The "Copy Debug" button doesn't work or copies incomplete data.
    *   **Solution**: The `onCopy` runnable must be implemented correctly in the parent module. It is the module's responsibility to gather all the necessary strings from the debug data, format them into a single string, and use `Minecraft.getInstance().keyboardHandler.setClipboard()` to copy it.
