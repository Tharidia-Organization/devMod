package com.devmod.config.component;

import javax.annotation.Nullable;

import com.devmod.config.handler.IConfigComponent;

/**
 * A boolean-valued config component.
 */
public class BooleanComponent implements IConfigComponent<Boolean> {

    private final String id;
    private final String displayName;
    private final String category;
    private final boolean defaultValue;

    public BooleanComponent(String id, String displayName, String category, boolean defaultValue) {
        this.id = id;
        this.displayName = displayName;
        this.category = category;
        this.defaultValue = defaultValue;
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
    public Boolean defaultValue() {
        return defaultValue;
    }

    @Override
    public Class<Boolean> valueType() {
        return Boolean.class;
    }

    @Override
    @Nullable
    public String validate(Boolean value) {
        if (value == null) {
            return "Value cannot be null";
        }
        return null;
    }

    @Override
    public Boolean clamp(Boolean value) {
        return value != null ? value : defaultValue;
    }
}
