package com.devmod.foundry;

import java.util.Objects;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.neoforged.neoforge.registries.DeferredHolder;

import com.devmod.DevMod;
import com.devmod.foundry.block.FoundryCastingBasinBlock;
import com.devmod.foundry.block.FoundryCastingTableBlock;
import com.devmod.foundry.block.FoundryChannelBlock;
import com.devmod.foundry.block.FoundryChuteBlock;
import com.devmod.foundry.block.FoundryControllerBlock;
import com.devmod.foundry.block.FoundryDrainBlock;
import com.devmod.foundry.block.FoundryDuctBlock;
import com.devmod.foundry.block.FoundryFaucetBlock;
import com.devmod.foundry.block.FoundryPartBuilderBlock;
import com.devmod.foundry.block.FoundryTankBlock;
import com.devmod.foundry.block.FoundryToolAnvilBlock;
import com.devmod.foundry.block.FoundryToolStationBlock;

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

    public static final DeferredHolder<Block, Block> FOUNDRY_CRACKED_BRICKS = DevMod.BLOCKS.register(
        "foundry_cracked_bricks",
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

    public static final DeferredHolder<Block, Block> FOUNDRY_GLASS = DevMod.BLOCKS.register(
        "foundry_glass",
        () -> new Block(BlockBehaviour.Properties.of()
            .strength(3.0F, 6.0F)
            .sound(Objects.requireNonNull(SoundType.GLASS))
            .requiresCorrectToolForDrops()
            .noOcclusion())
    );

    public static final DeferredHolder<Block, Block> FOUNDRY_WINDOW = DevMod.BLOCKS.register(
        "foundry_window",
        () -> new Block(BlockBehaviour.Properties.of()
            .strength(3.0F, 6.0F)
            .sound(Objects.requireNonNull(SoundType.GLASS))
            .requiresCorrectToolForDrops()
            .noOcclusion())
    );

    public static final DeferredHolder<Block, FoundryTankBlock> FOUNDRY_GAUGE = DevMod.BLOCKS.register(
        "foundry_gauge",
        FoundryTankBlock::new
    );

    public static final DeferredHolder<Block, FoundryTankBlock> FOUNDRY_FUEL_TANK = DevMod.BLOCKS.register(
        "foundry_fuel_tank",
        FoundryTankBlock::new
    );

    public static final DeferredHolder<Block, FoundryFaucetBlock> FOUNDRY_FAUCET = DevMod.BLOCKS.register(
        "foundry_faucet",
        FoundryFaucetBlock::new
    );

    public static final DeferredHolder<Block, FoundryChannelBlock> FOUNDRY_CHANNEL = DevMod.BLOCKS.register(
        "foundry_channel",
        FoundryChannelBlock::new
    );

    public static final DeferredHolder<Block, FoundryDuctBlock> FOUNDRY_DUCT = DevMod.BLOCKS.register(
        "foundry_duct",
        FoundryDuctBlock::new
    );

    public static final DeferredHolder<Block, FoundryChuteBlock> FOUNDRY_CHUTE = DevMod.BLOCKS.register(
        "foundry_chute",
        FoundryChuteBlock::new
    );

    public static final DeferredHolder<Block, FoundryCastingTableBlock> FOUNDRY_CASTING_TABLE = DevMod.BLOCKS.register(
        "foundry_casting_table",
        FoundryCastingTableBlock::new
    );

    public static final DeferredHolder<Block, FoundryCastingBasinBlock> FOUNDRY_CASTING_BASIN = DevMod.BLOCKS.register(
        "foundry_casting_basin",
        FoundryCastingBasinBlock::new
    );

    public static final DeferredHolder<Block, FoundryPartBuilderBlock> FOUNDRY_PART_BUILDER = DevMod.BLOCKS.register(
        "foundry_part_builder",
        FoundryPartBuilderBlock::new
    );

    public static final DeferredHolder<Block, FoundryToolStationBlock> FOUNDRY_TOOL_STATION = DevMod.BLOCKS.register(
        "foundry_tool_station",
        FoundryToolStationBlock::new
    );

    public static final DeferredHolder<Block, FoundryToolAnvilBlock> FOUNDRY_TOOL_ANVIL = DevMod.BLOCKS.register(
        "foundry_tool_anvil",
        FoundryToolAnvilBlock::new
    );

    // Material storage blocks
    public static final DeferredHolder<Block, Block> STEEL_BLOCK = DevMod.BLOCKS.register(
        "steel_block",
        () -> new Block(BlockBehaviour.Properties.of()
            .strength(5.0F, 6.0F)
            .sound(Objects.requireNonNull(SoundType.METAL))
            .requiresCorrectToolForDrops())
    );

    public static final DeferredHolder<Block, Block> BRONZE_BLOCK = DevMod.BLOCKS.register(
        "bronze_block",
        () -> new Block(BlockBehaviour.Properties.of()
            .strength(4.0F, 6.0F)
            .sound(Objects.requireNonNull(SoundType.METAL))
            .requiresCorrectToolForDrops())
    );

    public static final DeferredHolder<Block, Block> COBALT_BLOCK = DevMod.BLOCKS.register(
        "cobalt_block",
        () -> new Block(BlockBehaviour.Properties.of()
            .strength(6.0F, 8.0F)
            .sound(Objects.requireNonNull(SoundType.METAL))
            .requiresCorrectToolForDrops())
    );

    public static final DeferredHolder<Block, Block> MANYULLYN_BLOCK = DevMod.BLOCKS.register(
        "manyullyn_block",
        () -> new Block(BlockBehaviour.Properties.of()
            .strength(7.0F, 10.0F)
            .sound(Objects.requireNonNull(SoundType.METAL))
            .requiresCorrectToolForDrops())
    );

    public static final DeferredHolder<Block, Block> TIN_BLOCK = DevMod.BLOCKS.register(
        "tin_block",
        () -> new Block(BlockBehaviour.Properties.of()
            .strength(3.0F, 5.0F)
            .sound(Objects.requireNonNull(SoundType.METAL))
            .requiresCorrectToolForDrops())
    );

    public static final DeferredHolder<Block, Block> LEAD_BLOCK = DevMod.BLOCKS.register(
        "lead_block",
        () -> new Block(BlockBehaviour.Properties.of()
            .strength(4.0F, 5.0F)
            .sound(Objects.requireNonNull(SoundType.METAL))
            .requiresCorrectToolForDrops())
    );

    public static final DeferredHolder<Block, Block> SILVER_BLOCK = DevMod.BLOCKS.register(
        "silver_block",
        () -> new Block(BlockBehaviour.Properties.of()
            .strength(4.0F, 5.0F)
            .sound(Objects.requireNonNull(SoundType.METAL))
            .requiresCorrectToolForDrops())
    );

    public static final DeferredHolder<Block, Block> NICKEL_BLOCK = DevMod.BLOCKS.register(
        "nickel_block",
        () -> new Block(BlockBehaviour.Properties.of()
            .strength(5.0F, 6.0F)
            .sound(Objects.requireNonNull(SoundType.METAL))
            .requiresCorrectToolForDrops())
    );

    public static final DeferredHolder<Block, Block> ELECTRUM_BLOCK = DevMod.BLOCKS.register(
        "electrum_block",
        () -> new Block(BlockBehaviour.Properties.of()
            .strength(4.0F, 5.0F)
            .sound(Objects.requireNonNull(SoundType.METAL))
            .requiresCorrectToolForDrops())
    );

    public static final DeferredHolder<Block, Block> INVAR_BLOCK = DevMod.BLOCKS.register(
        "invar_block",
        () -> new Block(BlockBehaviour.Properties.of()
            .strength(5.0F, 7.0F)
            .sound(Objects.requireNonNull(SoundType.METAL))
            .requiresCorrectToolForDrops())
    );

    public static final DeferredHolder<Block, Block> ARDITE_BLOCK = DevMod.BLOCKS.register(
        "ardite_block",
        () -> new Block(BlockBehaviour.Properties.of()
            .strength(6.0F, 8.0F)
            .sound(Objects.requireNonNull(SoundType.METAL))
            .requiresCorrectToolForDrops())
    );

    public static final DeferredHolder<Block, Block> VOID_METAL_BLOCK = DevMod.BLOCKS.register(
        "void_metal_block",
        () -> new Block(BlockBehaviour.Properties.of()
            .strength(8.0F, 12.0F)
            .sound(Objects.requireNonNull(SoundType.METAL))
            .requiresCorrectToolForDrops())
    );

    // Ore blocks - Overworld
    public static final DeferredHolder<Block, Block> TIN_ORE = DevMod.BLOCKS.register(
        "tin_ore",
        () -> new Block(BlockBehaviour.Properties.of()
            .strength(3.0F, 3.0F)
            .sound(Objects.requireNonNull(SoundType.STONE))
            .requiresCorrectToolForDrops())
    );

    public static final DeferredHolder<Block, Block> DEEPSLATE_TIN_ORE = DevMod.BLOCKS.register(
        "deepslate_tin_ore",
        () -> new Block(BlockBehaviour.Properties.of()
            .strength(4.5F, 3.0F)
            .sound(Objects.requireNonNull(SoundType.DEEPSLATE))
            .requiresCorrectToolForDrops())
    );

    public static final DeferredHolder<Block, Block> LEAD_ORE = DevMod.BLOCKS.register(
        "lead_ore",
        () -> new Block(BlockBehaviour.Properties.of()
            .strength(3.0F, 3.0F)
            .sound(Objects.requireNonNull(SoundType.STONE))
            .requiresCorrectToolForDrops())
    );

    public static final DeferredHolder<Block, Block> DEEPSLATE_LEAD_ORE = DevMod.BLOCKS.register(
        "deepslate_lead_ore",
        () -> new Block(BlockBehaviour.Properties.of()
            .strength(4.5F, 3.0F)
            .sound(Objects.requireNonNull(SoundType.DEEPSLATE))
            .requiresCorrectToolForDrops())
    );

    public static final DeferredHolder<Block, Block> SILVER_ORE = DevMod.BLOCKS.register(
        "silver_ore",
        () -> new Block(BlockBehaviour.Properties.of()
            .strength(3.0F, 3.0F)
            .sound(Objects.requireNonNull(SoundType.STONE))
            .requiresCorrectToolForDrops())
    );

    public static final DeferredHolder<Block, Block> DEEPSLATE_SILVER_ORE = DevMod.BLOCKS.register(
        "deepslate_silver_ore",
        () -> new Block(BlockBehaviour.Properties.of()
            .strength(4.5F, 3.0F)
            .sound(Objects.requireNonNull(SoundType.DEEPSLATE))
            .requiresCorrectToolForDrops())
    );

    public static final DeferredHolder<Block, Block> NICKEL_ORE = DevMod.BLOCKS.register(
        "nickel_ore",
        () -> new Block(BlockBehaviour.Properties.of()
            .strength(3.0F, 3.0F)
            .sound(Objects.requireNonNull(SoundType.STONE))
            .requiresCorrectToolForDrops())
    );

    public static final DeferredHolder<Block, Block> DEEPSLATE_NICKEL_ORE = DevMod.BLOCKS.register(
        "deepslate_nickel_ore",
        () -> new Block(BlockBehaviour.Properties.of()
            .strength(4.5F, 3.0F)
            .sound(Objects.requireNonNull(SoundType.DEEPSLATE))
            .requiresCorrectToolForDrops())
    );

    // Ore blocks - Nether
    public static final DeferredHolder<Block, Block> COBALT_ORE = DevMod.BLOCKS.register(
        "cobalt_ore",
        () -> new Block(BlockBehaviour.Properties.of()
            .strength(10.0F, 5.0F)
            .sound(Objects.requireNonNull(SoundType.NETHER_ORE))
            .requiresCorrectToolForDrops())
    );

    public static final DeferredHolder<Block, Block> ARDITE_ORE = DevMod.BLOCKS.register(
        "ardite_ore",
        () -> new Block(BlockBehaviour.Properties.of()
            .strength(10.0F, 5.0F)
            .sound(Objects.requireNonNull(SoundType.NETHER_ORE))
            .requiresCorrectToolForDrops())
    );

    public static void init() {
        // Static init only.
    }
}
