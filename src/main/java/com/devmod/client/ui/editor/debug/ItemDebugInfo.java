package com.devmod.client.ui.editor.debug;

/**
 * Holds lightweight identification data for the debug tab.
 */
public record ItemDebugInfo(
    String registryName,
    int stackSize,
    int currentDamage,
    int maxDamage,
    int nbtTagCount,
    boolean hasCustomData
) {}
