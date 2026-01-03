package com.devmod.util;

import com.devmod.shared.SharedColorTokens;

/**
 * Shared bit masks for color manipulation (kept aligned with SharedColorTokens.Mask).
 */
public final class ColorMasks {
    public static final int RGB = SharedColorTokens.Mask.RGB;
    public static final int ALPHA = SharedColorTokens.Mask.ALPHA;
    public static final int NONE = SharedColorTokens.Mask.NONE;

    private ColorMasks() {}
}
