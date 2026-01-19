package com.devmod.foundry.structure;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Result of a foundry multiblock detection pass.
 */
public record FoundryStructureResult(
    @Nullable FoundryStructure structure,
    @Nullable Component error,
    @Nullable BlockPos errorPos
) {
    public boolean isValid() {
        return structure != null;
    }
}
