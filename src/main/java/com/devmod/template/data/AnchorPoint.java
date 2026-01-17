package com.devmod.template.data;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.util.StringRepresentable;

/**
 * Anchor points for positioning components relative to room bounds.
 */
public enum AnchorPoint implements StringRepresentable {
    CENTER("center"),
    NORTH_WALL("north_wall"),
    SOUTH_WALL("south_wall"),
    EAST_WALL("east_wall"),
    WEST_WALL("west_wall"),
    NW_CORNER("nw_corner"),
    NE_CORNER("ne_corner"),
    SW_CORNER("sw_corner"),
    SE_CORNER("se_corner"),
    NORTH_CENTER("north_center"),
    SOUTH_CENTER("south_center"),
    EAST_CENTER("east_center"),
    WEST_CENTER("west_center");

    private final String name;

    AnchorPoint(String name) {
        this.name = name;
    }

    @Override
    @Nonnull
    public String getSerializedName() {
        return Objects.requireNonNull(name);
    }

    public static AnchorPoint fromString(String name) {
        for (AnchorPoint anchor : values()) {
            if (anchor.name.equals(name)) {
                return anchor;
            }
        }
        return CENTER;
    }
}
