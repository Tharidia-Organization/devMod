package com.frenkvs.devmod.recipe;

/**
 * Type of smelting recipe.
 */
public enum SmeltingType {
    SMELTING("smelting", "minecraft:smelting", 200),
    BLASTING("blasting", "minecraft:blasting", 100),
    SMOKING("smoking", "minecraft:smoking", 100),
    CAMPFIRE("campfire", "minecraft:campfire_cooking", 600);

    private final String id;
    private final String minecraftType;
    private final int defaultCookingTime;

    SmeltingType(String id, String minecraftType, int defaultCookingTime) {
        this.id = id;
        this.minecraftType = minecraftType;
        this.defaultCookingTime = defaultCookingTime;
    }

    public String getId() {
        return id;
    }

    public String getMinecraftType() {
        return minecraftType;
    }

    /**
     * Default cooking time in ticks.
     */
    public int getDefaultCookingTime() {
        return defaultCookingTime;
    }

    /**
     * Default cooking time in seconds.
     */
    public float getDefaultCookingTimeSeconds() {
        return defaultCookingTime / 20.0f;
    }

    public static SmeltingType fromMinecraftType(String type) {
        if (type == null) return SMELTING;
        for (SmeltingType st : values()) {
            if (st.minecraftType.equals(type)) return st;
        }
        return SMELTING;
    }
}
