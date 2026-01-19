package com.devmod.foundry;

import java.util.Objects;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.neoforged.neoforge.registries.DeferredHolder;

import com.devmod.DevMod;
import com.devmod.foundry.block.FoundryCastingBasinBlock;
import com.devmod.foundry.block.FoundryCastingTableBlock;
import com.devmod.foundry.block.FoundryControllerBlock;
import com.devmod.foundry.block.FoundryDrainBlock;
import com.devmod.foundry.block.FoundryFaucetBlock;
import com.devmod.foundry.block.FoundryTankBlock;

/**
 * Foundry module block registrations.
 */
public final class FoundryBlocks {
    private FoundryBlocks() {}

    public static final DeferredHolder<Block, Block> FOUNDRY_BRICKS = DevMod.BLOCKS.register(
        "foundry_bricks",
        () -> new Block(BlockBehaviour.Properties.of()
            .strength(4.0F, 6.0F)
            .sound(Objects.requireNonNull(SoundType.STONE))
            .requiresCorrectToolForDrops())
    );

    public static final DeferredHolder<Block, FoundryControllerBlock> FOUNDRY_CONTROLLER = DevMod.BLOCKS.register(
        "foundry_controller",
        FoundryControllerBlock::new
    );

    public static final DeferredHolder<Block, FoundryDrainBlock> FOUNDRY_DRAIN = DevMod.BLOCKS.register(
        "foundry_drain",
        FoundryDrainBlock::new
    );

    public static final DeferredHolder<Block, FoundryTankBlock> FOUNDRY_TANK = DevMod.BLOCKS.register(
        "foundry_tank",
        FoundryTankBlock::new
    );

    public static final DeferredHolder<Block, FoundryFaucetBlock> FOUNDRY_FAUCET = DevMod.BLOCKS.register(
        "foundry_faucet",
        FoundryFaucetBlock::new
    );

    public static final DeferredHolder<Block, FoundryCastingTableBlock> FOUNDRY_CASTING_TABLE = DevMod.BLOCKS.register(
        "foundry_casting_table",
        FoundryCastingTableBlock::new
    );

    public static final DeferredHolder<Block, FoundryCastingBasinBlock> FOUNDRY_CASTING_BASIN = DevMod.BLOCKS.register(
        "foundry_casting_basin",
        FoundryCastingBasinBlock::new
    );

    public static void init() {
        // Static init only.
    }
}
