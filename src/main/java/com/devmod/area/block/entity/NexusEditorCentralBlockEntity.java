package com.devmod.area.block.entity;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.area.AreaBlockEntities;

/**
 * Block entity for the Nexus Editor Central block.
 * Currently minimal - may be extended for animation or additional state.
 */
public class NexusEditorCentralBlockEntity extends BlockEntity {

    public NexusEditorCentralBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        super(AreaBlockEntities.NEXUS_EDITOR_CENTRAL.get(), pos, state);
    }
}
