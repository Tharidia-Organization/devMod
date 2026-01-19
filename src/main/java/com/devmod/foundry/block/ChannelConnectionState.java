package com.devmod.foundry.block;

import net.minecraft.util.StringRepresentable;

import javax.annotation.Nonnull;

/**
 * Tri-state enum for channel connections.
 * <p>
 * NONE - No fluid flow in this direction (valve closed)
 * IN   - Fluid can only flow INTO this channel from this direction
 * OUT  - Fluid can only flow OUT of this channel in this direction
 */
public enum ChannelConnectionState implements StringRepresentable {
    NONE("none", false, false),
    IN("in", true, false),
    OUT("out", false, true);

    private final String name;
    private final boolean allowsInput;
    private final boolean allowsOutput;

    ChannelConnectionState(String name, boolean allowsInput, boolean allowsOutput) {
        this.name = name;
        this.allowsInput = allowsInput;
        this.allowsOutput = allowsOutput;
    }

    @Override
    @Nonnull
    public String getSerializedName() {
        return name;
    }

    /**
     * Whether this state allows fluid to flow INTO the channel from this direction.
     */
    public boolean allowsInput() {
        return allowsInput;
    }

    /**
     * Whether this state allows fluid to flow OUT of the channel in this direction.
     */
    public boolean allowsOutput() {
        return allowsOutput;
    }

    /**
     * Cycle to the next state: NONE -> IN -> OUT -> NONE
     */
    public ChannelConnectionState cycle() {
        return switch (this) {
            case NONE -> IN;
            case IN -> OUT;
            case OUT -> NONE;
        };
    }

    /**
     * Cycle backwards: NONE -> OUT -> IN -> NONE
     */
    public ChannelConnectionState cycleReverse() {
        return switch (this) {
            case NONE -> OUT;
            case OUT -> IN;
            case IN -> NONE;
        };
    }

    /**
     * Get the opposite state for connection matching.
     * IN <-> OUT, NONE stays NONE
     */
    public ChannelConnectionState getOpposite() {
        return switch (this) {
            case IN -> OUT;
            case OUT -> IN;
            case NONE -> NONE;
        };
    }

    /**
     * Check if two states can connect (IN connects to OUT, and vice versa).
     */
    public boolean canConnectTo(ChannelConnectionState other) {
        if (this == NONE || other == NONE) {
            return false;
        }
        // IN connects to OUT, OUT connects to IN
        return (this == IN && other == OUT) || (this == OUT && other == IN);
    }

    /**
     * Encode a state to 2 bits for storage.
     */
    public int encode() {
        return ordinal();
    }

    /**
     * Decode a state from 2 bits.
     */
    public static ChannelConnectionState decode(int value) {
        return switch (value & 0x3) {
            case 1 -> IN;
            case 2 -> OUT;
            default -> NONE;
        };
    }
}
