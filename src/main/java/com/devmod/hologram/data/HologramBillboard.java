package com.devmod.hologram.data;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Display;

/**
 * Billboard modes for hologram orientation.
 * Maps to Minecraft's Display.BillboardConstraints.
 */
public enum HologramBillboard implements StringRepresentable {
    /**
     * Rotates to always face the player (default).
     */
    CENTER("center", Display.BillboardConstraints.CENTER),

    /**
     * Fixed orientation - does not rotate.
     */
    FIXED("fixed", Display.BillboardConstraints.FIXED),

    /**
     * Rotates only on the Y axis (vertical).
     */
    VERTICAL("vertical", Display.BillboardConstraints.VERTICAL),

    /**
     * Rotates only on the X axis (horizontal).
     */
    HORIZONTAL("horizontal", Display.BillboardConstraints.HORIZONTAL);

    public static final Codec<HologramBillboard> CODEC = StringRepresentable.fromEnum(HologramBillboard::values);

    private final String name;
    private final Display.BillboardConstraints constraint;

    HologramBillboard(String name, Display.BillboardConstraints constraint) {
        this.name = name;
        this.constraint = constraint;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /**
     * Returns the Minecraft billboard constraint for TextDisplay NBT.
     */
    public Display.BillboardConstraints getConstraint() {
        return constraint;
    }

    /**
     * Returns the NBT string value for this billboard mode.
     */
    public String getNbtValue() {
        return name;
    }
}
