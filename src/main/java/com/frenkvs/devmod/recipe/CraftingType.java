package com.frenkvs.devmod.recipe;

/**
 * Type of crafting recipe: shaped or shapeless.
 */
public enum CraftingType {
    SHAPED("shaped"),
    SHAPELESS("shapeless");

    private final String id;

    CraftingType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public String getMinecraftType() {
        return switch (this) {
            case SHAPED -> "minecraft:crafting_shaped";
            case SHAPELESS -> "minecraft:crafting_shapeless";
        };
    }

    public static CraftingType fromMinecraftType(String type) {
        if (type == null) return SHAPED;
        if (type.contains("shapeless")) return SHAPELESS;
        return SHAPED;
    }
}
