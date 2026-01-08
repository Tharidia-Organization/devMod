package com.devmod.config.component;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.devmod.config.handler.IConfigComponent;

/**
 * A list-valued config component.
 *
 * @param <E> the element type
 */
public class ListComponent<E> implements IConfigComponent<List<E>> {

    private final String id;
    private final String displayName;
    private final String category;
    private final Class<E> elementType;
    private final int maxSize;

    public ListComponent(String id, String displayName, String category,
                         Class<E> elementType, int maxSize) {
        this.id = id;
        this.displayName = displayName;
        this.category = category;
        this.elementType = elementType;
        this.maxSize = maxSize;
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
    public List<E> defaultValue() {
        return new ArrayList<>();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<List<E>> valueType() {
        return (Class<List<E>>) (Class<?>) List.class;
    }

    @Override
    @Nullable
    public String validate(List<E> value) {
        if (value == null) {
            return "List cannot be null";
        }
        if (value.size() > maxSize) {
            return String.format("List cannot exceed %d elements", maxSize);
        }
        return null;
    }

    @Override
    public List<E> clamp(List<E> value) {
        if (value == null) {
            return new ArrayList<>();
        }
        if (value.size() > maxSize) {
            return new ArrayList<>(value.subList(0, maxSize));
        }
        return new ArrayList<>(value);
    }

    public Class<E> getElementType() {
        return elementType;
    }

    public int getMaxSize() {
        return maxSize;
    }
}
