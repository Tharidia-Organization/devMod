package com.devmod.ui.testing.pages;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Shared utility methods for VoxelLab pages.
 * Provides safe config value access with null-safety and exception handling.
 */
public final class PageUtils {

    private PageUtils() {
        // Utility class - no instantiation
    }

    // ═══════════════════════════════════════════════════════════════
    // SAFE CONFIG GETTERS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Safely gets a boolean config value.
     * Returns false if config is not yet loaded or throws.
     */
    public static boolean safeGetBool(ModConfigSpec.BooleanValue config) {
        try {
            return config.get();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Safely gets an integer config value.
     * Returns default if config is not yet loaded or throws.
     */
    public static int safeGetInt(ModConfigSpec.IntValue config, int defaultValue) {
        try {
            return config.get();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Safely gets a double config value.
     * Returns default if config is not yet loaded or throws.
     */
    public static double safeGetDouble(ModConfigSpec.DoubleValue config, double defaultValue) {
        try {
            return config.get();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // NON-NULL WRAPPERS (for slider suppliers)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Returns a non-null Double for use in slider value suppliers.
     * Required because SliderPanel expects Supplier<Double> not primitive.
     */
    @Nonnull
    public static Double nonNullDouble(ModConfigSpec.DoubleValue config, double defaultValue) {
        return Objects.requireNonNull(Double.valueOf(safeGetDouble(config, defaultValue)));
    }

    /**
     * Returns a non-null Integer for use in slider value suppliers.
     */
    @Nonnull
    public static Integer nonNullInt(ModConfigSpec.IntValue config, int defaultValue) {
        return Objects.requireNonNull(Integer.valueOf(safeGetInt(config, defaultValue)));
    }

    // ═══════════════════════════════════════════════════════════════
    // ENUM CONFIG HELPERS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Safely gets an enum config value.
     * Returns default if config is not yet loaded or throws.
     */
    public static <T extends Enum<T>> T safeGetEnum(ModConfigSpec.EnumValue<T> config, T defaultValue) {
        try {
            return config.get();
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
