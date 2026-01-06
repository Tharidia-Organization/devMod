package com.devmod.blocks;

import net.minecraft.util.StringRepresentable;

public enum NexusPortalColor implements StringRepresentable {
    COMBAT("combat"),
    ARENA("arena"),
    UI("ui"),
    TELEMETRY("telemetry"),
    SHOWCASE("showcase"),
    INTEGRATION("integration"),
    SANDBOX("sandbox"),
    MECHANICS("mechanics");

    private final String id;

    NexusPortalColor(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
