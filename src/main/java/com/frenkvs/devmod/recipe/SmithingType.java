package com.frenkvs.devmod.recipe;

/**
 * Type of smithing recipe.
 */
public enum SmithingType {
    TRANSFORM("transform", "minecraft:smithing_transform"),
    TRIM("trim", "minecraft:smithing_trim");

    private final String id;
    private final String minecraftType;

    SmithingType(String id, String minecraftType) {
        this.id = id;
        this.minecraftType = minecraftType;
    }

    public String getId() {
        return id;
    }

    public String getMinecraftType() {
        return minecraftType;
    }

    public static SmithingType fromMinecraftType(String type) {
        if (type == null) return TRANSFORM;
        if (type.contains("trim")) return TRIM;
        return TRANSFORM;
    }
}
