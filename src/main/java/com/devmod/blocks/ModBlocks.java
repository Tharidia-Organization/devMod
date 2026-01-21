package com.devmod.blocks;

import java.util.Objects;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.devmod.DevMod;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(Objects.requireNonNull(Registries.BLOCK), DevMod.MODID);

    public static final DeferredHolder<Block, NexusPortalBlock> NEXUS_PORTAL = BLOCKS.register(
        "nexus_portal",
        () -> new NexusPortalBlock(BlockBehaviour.Properties.of()
            .mapColor(Objects.requireNonNull(MapColor.COLOR_LIGHT_BLUE))
            .noCollission()
            .noOcclusion()
            .strength(-1.0F, 3600000.0F)
            .sound(Objects.requireNonNull(SoundType.GLASS))
            .lightLevel(state -> 11))
    );

    private ModBlocks() {}
}
