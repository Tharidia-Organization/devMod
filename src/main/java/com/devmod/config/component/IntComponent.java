package com.devmod.config.component;

import javax.annotation.Nullable;

import com.devmod.config.handler.IConfigComponent;

/**
 * An integer-valued config component with min/max bounds.
 */
public class IntComponent implements IConfigComponent<Integer> {

    private final String id;
    private final String displayName;
    private final String category;
    private final int defaultValue;
    private final int minValue;
    private final int maxValue;

    public IntComponent(String id, String displayName, String category,
                        int defaultValue, int minValue, int maxValue) {
        this.id = id;
        this.displayName = displayName;
        this.category = category;
        this.defaultValue = defaultValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public String category() {
        return category;
    }

    @Override
    public Integer defaultValue() {
        return defaultValue;
    }

    @Override
    public Class<Integer> valueType() {
        return Integer.class;
    }

    @Override
    @Nullable
    public String validate(Integer value) {
        if (value == null) {
            return "Value cannot be null";
        }
        if (value < minValue || value > maxValue) {
            return String.format("Value must be between %d and %d", minValue, maxValue);
        }
        return null;
    }

    @Override
    public Integer clamp(Integer value) {
        if (value == null) {
            return defaultValue;
        }
        return Math.max(minValue, Math.min(maxValue, value));
    }

    public int getMinValue() {
        return minValue;
    }

    public int getMaxValue() {
        return maxValue;
    }
}
