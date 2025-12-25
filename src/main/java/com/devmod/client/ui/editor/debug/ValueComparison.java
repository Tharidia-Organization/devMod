package com.devmod.ui.editor.debug;

/**
 * Represents an attribute comparison row for the debug viewer.
 *
 * @param attributeName Name shown in the UI.
 * @param originalValue Value captured when the editor opened.
 * @param currentValue Current value controlled by the sliders.
 * @param serverValue Value read from the item/server/config when building the comparison.
 * @param isModified True when the current value differs from the original.
 * @param hasMismatch True when the server value disagrees with the current value.
 */
public record ValueComparison(
    String attributeName,
    double originalValue,
    double currentValue,
    double serverValue,
    boolean isModified,
    boolean hasMismatch
) {}
