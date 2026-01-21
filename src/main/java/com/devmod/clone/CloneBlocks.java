package com.devmod.clone;

import java.util.Objects;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import net.neoforged.neoforge.registries.DeferredHolder;

import com.devmod.DevMod;
import com.devmod.clone.block.CentrifugeBlock;
import com.devmod.clone.block.CloneMachineBlock;
import com.devmod.clone.block.ClonePulverizerBlock;
import com.devmod.clone.block.ImprinterBlock;
import com.devmod.clone.block.NeurocellBlock;
import com.devmod.clone.block.NeurocellItemBlock;
import com.devmod.clone.block.NeurocellLBlock;
import com.devmod.clone.block.NeurocellMannequinBlock;
import com.devmod.clone.block.NeurolinkBlock;
import com.devmod.clone.block.ReformerBlock;
import com.devmod.clone.block.TelepadBlock;
import com.devmod.runtime.block.AdminTerminalBlock;

/**
 * Clone module block registrations.
 * Registers all clone-related blocks.
 */
public final class CloneBlocks {
    private CloneBlocks() {}

    /**
     * The telepad block.
     * Teleportation pad with charging mechanic.
     */
    public static final DeferredHolder<Block, TelepadBlock> TELEPAD = DevMod.BLOCKS.register(
        "telepad",
        () -> new TelepadBlock(BlockBehaviour.Properties.of()
            .mapColor(Objects.requireNonNull(MapColor.COLOR_CYAN))
            .strength(2.0F, 6.0F)
            .sound(Objects.requireNonNull(SoundType.METAL))
            .lightLevel(state -> 5)
            .noOcclusion())
    );

    /**
     * The imprinter block.
     * Automatic entity scanner that fills bioscanners.
     */
    public static final DeferredHolder<Block, ImprinterBlock> IMPRINTER = DevMod.BLOCKS.register(
        "imprinter",
        ImprinterBlock::new
    );

    /**
     * The neurocell block.
     * Cloning chamber that processes bioscan data.
     */
    public static final DeferredHolder<Block, NeurocellBlock> NEUROCELL = DevMod.BLOCKS.register(
        "neurocell",
        NeurocellBlock::new
    );

    /**
     * The neurolink block.
     * Connection cables between clone system blocks.
     */
    public static final DeferredHolder<Block, NeurolinkBlock> NEUROLINK = DevMod.BLOCKS.register(
        "neurolink",
        NeurolinkBlock::new
    );

    /**
     * The reformer block.
     * Spawns cloned entities from processed data.
     */
    public static final DeferredHolder<Block, ReformerBlock> REFORMER = DevMod.BLOCKS.register(
        "reformer",
        ReformerBlock::new
    );

    /**
     * The large neurocell block (3x3x3).
     * Cloning chamber for larger entities.
     */
    public static final DeferredHolder<Block, NeurocellLBlock> NEUROCELL_L = DevMod.BLOCKS.register(
        "neurocell_l",
        NeurocellLBlock::new
    );

    /**
     * The neurocell mannequin block.
     * Displays armor and equipment on a humanoid model.
     */
    public static final DeferredHolder<Block, NeurocellMannequinBlock> NEUROCELL_MANNEQUIN = DevMod.BLOCKS.register(
        "neurocell_mannequin",
        NeurocellMannequinBlock::new
    );

    /**
     * The neurocell item display block.
     * Displays a single item floating and rotating.
     */
    public static final DeferredHolder<Block, NeurocellItemBlock> NEUROCELL_ITEM = DevMod.BLOCKS.register(
        "neurocell_item",
        NeurocellItemBlock::new
    );

    /**
     * The centrifuge block.
     * Automatic crafting machine for the clone module.
     */
    public static final DeferredHolder<Block, CentrifugeBlock> CENTRIFUGE = DevMod.BLOCKS.register(
        "centrifuge",
        CentrifugeBlock::new
    );

    // ==================== CLONE MACHINE BLOCKS (Oritech-style) ====================

    /**
     * Clone Pulverizer - grinding machine with rotating blades.
     * Accepts items dropped from above, pulverizes them, and ejects results.
     * Based on Oritech pulverizer design.
     */
    public static final DeferredHolder<Block, ClonePulverizerBlock> CLONE_PULVERIZER = DevMod.BLOCKS.register(
        "clone_pulverizer",
        ClonePulverizerBlock::new
    );

    /**
     * Clone Foundry - foundry with tank and robotic arm.
     * Based on Oritech foundry design.
     */
    public static final DeferredHolder<Block, CloneMachineBlock> CLONE_FOUNDRY = DevMod.BLOCKS.register(
        "clone_foundry",
        CloneMachineBlock::new
    );

    /**
     * Clone Pump - tripod pump with swing arm.
     * Based on Oritech pump design.
     */
    public static final DeferredHolder<Block, CloneMachineBlock> CLONE_PUMP = DevMod.BLOCKS.register(
        "clone_pump",
        CloneMachineBlock::new
    );

    /**
     * Clone Steam Engine - piston-driven steam engine.
     * Based on Oritech steam engine design.
     */
    public static final DeferredHolder<Block, CloneMachineBlock> CLONE_STEAM_ENGINE = DevMod.BLOCKS.register(
        "clone_steam_engine",
        CloneMachineBlock::new
    );

    /**
     * Clone Lava Generator - generator with tanks and pipes.
     * Based on Oritech lava generator design.
     */
    public static final DeferredHolder<Block, CloneMachineBlock> CLONE_LAVA_GENERATOR = DevMod.BLOCKS.register(
        "clone_lava_generator",
        CloneMachineBlock::new
    );

    /**
     * Clone Refinery - large multi-tower distillation refinery.
     * Based on Oritech refinery design.
     */
    public static final DeferredHolder<Block, CloneMachineBlock> CLONE_REFINERY = DevMod.BLOCKS.register(
        "clone_refinery",
        CloneMachineBlock::new
    );

    /**
     * Clone Shrinker - compression machine with press mechanism.
     * Based on Oritech shrinker design.
     */
    public static final DeferredHolder<Block, CloneMachineBlock> CLONE_SHRINKER = DevMod.BLOCKS.register(
        "clone_shrinker",
        CloneMachineBlock::new
    );

    /**
     * Clone Treefeller - automated tree harvesting machine.
     * Based on Oritech treefeller design.
     */
    public static final DeferredHolder<Block, CloneMachineBlock> CLONE_TREEFELLER = DevMod.BLOCKS.register(
        "clone_treefeller",
        CloneMachineBlock::new
    );

    /**
     * Clone Tech Door - sliding sci-fi door.
     * Based on Oritech tech door design.
     */
    public static final DeferredHolder<Block, CloneMachineBlock> CLONE_TECH_DOOR = DevMod.BLOCKS.register(
        "clone_tech_door",
        CloneMachineBlock::new
    );

    /**
     * Clone Bio Generator - biomass-powered generator.
     * Based on Oritech bio generator design.
     */
    public static final DeferredHolder<Block, CloneMachineBlock> CLONE_BIO_GENERATOR = DevMod.BLOCKS.register(
        "clone_bio_generator",
        CloneMachineBlock::new
    );

    /**
     * Clone Assembler - automated assembly machine.
     * Based on Oritech assembler design.
     */
    public static final DeferredHolder<Block, CloneMachineBlock> CLONE_ASSEMBLER = DevMod.BLOCKS.register(
        "clone_assembler",
        CloneMachineBlock::new
    );

    /**
     * Clone Centrifuge L - large industrial centrifuge.
     * Based on Oritech centrifuge design.
     */
    public static final DeferredHolder<Block, CloneMachineBlock> CLONE_CENTRIFUGE_L = DevMod.BLOCKS.register(
        "clone_centrifuge_l",
        CloneMachineBlock::new
    );

    /**
     * Clone Fuel Generator - fuel-powered generator.
     * Based on Oritech fuel generator design.
     */
    public static final DeferredHolder<Block, CloneMachineBlock> CLONE_FUEL_GENERATOR = DevMod.BLOCKS.register(
        "clone_fuel_generator",
        CloneMachineBlock::new
    );

    /**
     * Clone Atomic Forge - advanced nuclear forge.
     * Based on Oritech atomic forge design.
     */
    public static final DeferredHolder<Block, CloneMachineBlock> CLONE_ATOMIC_FORGE = DevMod.BLOCKS.register(
        "clone_atomic_forge",
        CloneMachineBlock::new
    );

    /**
     * Clone Laser Arm - automated laser turret.
     * Based on Oritech laser arm design.
     */
    public static final DeferredHolder<Block, CloneMachineBlock> CLONE_LASER_ARM = DevMod.BLOCKS.register(
        "clone_laser_arm",
        CloneMachineBlock::new
    );

    /**
     * Clone Drill - automated mining drill.
     * Based on Oritech drill design.
     */
    public static final DeferredHolder<Block, CloneMachineBlock> CLONE_DRILL = DevMod.BLOCKS.register(
        "clone_drill",
        CloneMachineBlock::new
    );

    /**
     * Clone Fertilizer - automated fertilizer spreader.
     * Based on Oritech fertilizer design.
     */
    public static final DeferredHolder<Block, CloneMachineBlock> CLONE_FERTILIZER = DevMod.BLOCKS.register(
        "clone_fertilizer",
        CloneMachineBlock::new
    );

    /**
     * Clone Crusher - ore crushing machine.
     */
    public static final DeferredHolder<Block, CloneMachineBlock> CLONE_CRUSHER = DevMod.BLOCKS.register(
        "clone_crusher",
        CloneMachineBlock::new
    );

    /**
     * Clone Smelter - powered furnace.
     */
    public static final DeferredHolder<Block, CloneMachineBlock> CLONE_SMELTER = DevMod.BLOCKS.register(
        "clone_smelter",
        CloneMachineBlock::new
    );

    /**
     * Clone Storage Unit - item storage.
     */
    public static final DeferredHolder<Block, CloneMachineBlock> CLONE_STORAGE_UNIT = DevMod.BLOCKS.register(
        "clone_storage_unit",
        CloneMachineBlock::new
    );

    /**
     * Clone Energy Pipe - energy conduit.
     */
    public static final DeferredHolder<Block, CloneMachineBlock> CLONE_ENERGY_PIPE = DevMod.BLOCKS.register(
        "clone_energy_pipe",
        CloneMachineBlock::new
    );

    /**
     * Clone Tank - fluid storage.
     */
    public static final DeferredHolder<Block, CloneMachineBlock> CLONE_TANK = DevMod.BLOCKS.register(
        "clone_tank",
        CloneMachineBlock::new
    );

    /**
     * Clone Battery - energy storage.
     */
    public static final DeferredHolder<Block, CloneMachineBlock> CLONE_BATTERY = DevMod.BLOCKS.register(
        "clone_battery",
        CloneMachineBlock::new
    );

    public static final DeferredHolder<Block, CloneMachineBlock> CLONE_SOLAR_PANEL = DevMod.BLOCKS.register(
        "clone_solar_panel", CloneMachineBlock::new);

    public static final DeferredHolder<Block, CloneMachineBlock> CLONE_MOTOR = DevMod.BLOCKS.register(
        "clone_motor", CloneMachineBlock::new);

    public static final DeferredHolder<Block, CloneMachineBlock> CLONE_CONVEYOR = DevMod.BLOCKS.register(
        "clone_conveyor", CloneMachineBlock::new);

    public static final DeferredHolder<Block, CloneMachineBlock> CLONE_REACTOR = DevMod.BLOCKS.register(
        "clone_reactor", CloneMachineBlock::new);

    public static final DeferredHolder<Block, CloneMachineBlock> CLONE_PROCESSOR = DevMod.BLOCKS.register(
        "clone_processor", CloneMachineBlock::new);

    public static final DeferredHolder<Block, CloneMachineBlock> CLONE_CHARGER = DevMod.BLOCKS.register(
        "clone_charger", CloneMachineBlock::new);

    // ==================== ADMIN BLOCKS ====================

    /**
     * Admin Terminal - opens the instance control panel.
     * Requires admin permission to interact.
     */
    public static final DeferredHolder<Block, AdminTerminalBlock> ADMIN_TERMINAL = DevMod.BLOCKS.register(
        "admin_terminal", () -> new AdminTerminalBlock());

    /**
     * Called during mod initialization to ensure blocks are registered.
     */
    public static void init() {
        DevMod.LOGGER.debug("[Clone] Clone blocks initialized");
    }
}
