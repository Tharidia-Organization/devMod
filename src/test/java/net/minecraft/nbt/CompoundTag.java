package net.minecraft.nbt;

import java.util.HashMap;
import java.util.Map;

/**
 * Minimal stub for CompoundTag used in JVM tests.
 */
public class CompoundTag extends HashMap<String, Object> {
public static final com.mojang.serialization.Codec<CompoundTag> CODEC = new com.mojang.serialization.Codec<>();
    public void put(String key, CompoundTag value) {
        super.put(key, value);
    }

    public void putFloat(String key, float value) {
        super.put(key, value);
    }

    public void putBoolean(String key, boolean value) {
        super.put(key, value);
    }

    public void putString(String key, String value) {
        super.put(key, value);
    }

    public boolean contains(String key) {
        return super.containsKey(key);
    }

    public float getFloat(String key) {
        Object v = get(key);
        return v instanceof Number ? ((Number) v).floatValue() : 0f;
    }

    public boolean getBoolean(String key) {
        Object v = get(key);
        return v instanceof Boolean && (Boolean) v;
    }

    public String getString(String key) {
        Object v = get(key);
        return v == null ? "" : v.toString();
    }

    public CompoundTag getCompound(String key) {
        Object v = get(key);
        if (v instanceof CompoundTag tag) {
            return tag;
        }
        return new CompoundTag();
    }

    public CompoundTag copy() {
        CompoundTag copy = new CompoundTag();
        for (Map.Entry<String, Object> e : entrySet()) {
            String key = java.util.Objects.requireNonNull(e.getKey());
            Object val = e.getValue();
            if (val instanceof CompoundTag tag) {
                copy.put(key, java.util.Objects.requireNonNull(tag.copy()));
            } else {
                copy.put(key, val);
            }
        }
        return copy;
    }
}
