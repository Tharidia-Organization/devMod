package com.devmod.config.component;

import javax.annotation.Nullable;

import com.devmod.config.handler.IConfigComponent;

/**
 * A float-valued config component with min/max bounds.
 */
public class FloatComponent implements IConfigComponent<Float> {

    private final String id;
    private final String displayName;
    private final String category;
    private final float defaultValue;
    private final float minValue;
    private final float maxValue;

    public FloatComponent(String id, String displayName, String category,
                          float defaultValue, float minValue, float maxValue) {
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
    public Float defaultValue() {
        return defaultValue;
    }

    @Override
    public Class<Float> valueType() {
        return Float.class;
    }

    @Override
    @Nullable
    public String validate(Float value) {
        if (value == null) {
            return "Value cannot be null";
        }
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return "Value must be a valid number";
        }
        if (value < minValue || value > maxValue) {
            return String.format("Value must be between %.2f and %.2f", minValue, maxValue);
        }
        return null;
    }

    @Override
    public Float clamp(Float value) {
        if (value == null || Float.isNaN(value) || Float.isInfinite(value)) {
            return defaultValue;
        }
        return Math.max(minValue, Math.min(maxValue, value));
    }

    public float getMinValue() {
        return minValue;
    }

    public float getMaxValue() {
        return maxValue;
    }
}
