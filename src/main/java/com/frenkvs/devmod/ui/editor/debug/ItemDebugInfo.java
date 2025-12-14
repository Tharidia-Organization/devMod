package com.frenkvs.devmod.ui.editor.debug;

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
