package com.devmod.nexus;

import java.util.Objects;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.devmod.DevMod;

/**
 * Nexus decorative blocks - futuristic palette with neutral pastel tones.
 * Each block has a corresponding slab variant.
 */
public final class NexusDecorBlocks {
    private NexusDecorBlocks() {}

    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(Objects.requireNonNull(Registries.BLOCK), DevMod.MODID);

    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(Objects.requireNonNull(Registries.ITEM), DevMod.MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DevMod.MODID);

    // === BASE PROPERTIES ===
    private static BlockBehaviour.Properties baseProps() {
        return BlockBehaviour.Properties.of()
            .strength(1.5f, 6.0f)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops();
    }

    private static BlockBehaviour.Properties lightProps() {
        return baseProps().lightLevel(state -> 12);
    }

    // === BLOCKS ===

    // 1. Nexus Panel - smooth futuristic panel (gray-blue #8899AA)
    public static final DeferredHolder<Block, Block> NEXUS_PANEL = BLOCKS.register(
        "nexus_panel", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_PANEL_SLAB = BLOCKS.register(
        "nexus_panel_slab", () -> new SlabBlock(baseProps()));

    // 2. Nexus Tile - tiled pattern (muted teal #6B8E8E)
    public static final DeferredHolder<Block, Block> NEXUS_TILE = BLOCKS.register(
        "nexus_tile", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_TILE_SLAB = BLOCKS.register(
        "nexus_tile_slab", () -> new SlabBlock(baseProps()));

    // 3. Nexus Grid - grid pattern (soft steel #A0A8B0)
    public static final DeferredHolder<Block, Block> NEXUS_GRID = BLOCKS.register(
        "nexus_grid", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_GRID_SLAB = BLOCKS.register(
        "nexus_grid_slab", () -> new SlabBlock(baseProps()));

    // 4. Nexus Plating - industrial plating (dark slate #505860)
    public static final DeferredHolder<Block, Block> NEXUS_PLATING = BLOCKS.register(
        "nexus_plating", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_PLATING_SLAB = BLOCKS.register(
        "nexus_plating_slab", () -> new SlabBlock(baseProps()));

    // 5. Nexus Core - decorative core (dusty purple #8B7B8B)
    public static final DeferredHolder<Block, Block> NEXUS_CORE = BLOCKS.register(
        "nexus_core", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_CORE_SLAB = BLOCKS.register(
        "nexus_core_slab", () -> new SlabBlock(baseProps()));

    // 6. Nexus Frame - frame block (warm gray #9A9590)
    public static final DeferredHolder<Block, Block> NEXUS_FRAME = BLOCKS.register(
        "nexus_frame", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_FRAME_SLAB = BLOCKS.register(
        "nexus_frame_slab", () -> new SlabBlock(baseProps()));

    // 7. Nexus Conduit - tech conduit (muted cyan #7BA3A3)
    public static final DeferredHolder<Block, Block> NEXUS_CONDUIT = BLOCKS.register(
        "nexus_conduit", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_CONDUIT_SLAB = BLOCKS.register(
        "nexus_conduit_slab", () -> new SlabBlock(baseProps()));

    // 8. Nexus Terminal - terminal block (soft lavender #9090A0)
    public static final DeferredHolder<Block, Block> NEXUS_TERMINAL = BLOCKS.register(
        "nexus_terminal", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_TERMINAL_SLAB = BLOCKS.register(
        "nexus_terminal_slab", () -> new SlabBlock(baseProps()));

    // 9. Nexus Vent - ventilation pattern (dark gray #606060)
    public static final DeferredHolder<Block, Block> NEXUS_VENT = BLOCKS.register(
        "nexus_vent", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_VENT_SLAB = BLOCKS.register(
        "nexus_vent_slab", () -> new SlabBlock(baseProps()));

    // 10. Nexus Circuit - circuit pattern (teal accent #5A8080)
    public static final DeferredHolder<Block, Block> NEXUS_CIRCUIT = BLOCKS.register(
        "nexus_circuit", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_CIRCUIT_SLAB = BLOCKS.register(
        "nexus_circuit_slab", () -> new SlabBlock(baseProps()));

    // 11. Nexus Smooth - plain smooth (neutral gray #808080)
    public static final DeferredHolder<Block, Block> NEXUS_SMOOTH = BLOCKS.register(
        "nexus_smooth", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_SMOOTH_SLAB = BLOCKS.register(
        "nexus_smooth_slab", () -> new SlabBlock(baseProps()));

    // 12. Nexus Light - light-emitting panel (soft white #D0D0D8)
    public static final DeferredHolder<Block, Block> NEXUS_LIGHT = BLOCKS.register(
        "nexus_light", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_LIGHT_SLAB = BLOCKS.register(
        "nexus_light_slab", () -> new SlabBlock(lightProps()));

    // === BOLD BLOCKS (13-36) ===

    // 13. Azure - Bright cyan panel
    public static final DeferredHolder<Block, Block> NEXUS_AZURE = BLOCKS.register(
        "nexus_azure", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_AZURE_SLAB = BLOCKS.register(
        "nexus_azure_slab", () -> new SlabBlock(lightProps()));

    // 14. Plasma - Magenta energy panel
    public static final DeferredHolder<Block, Block> NEXUS_PLASMA = BLOCKS.register(
        "nexus_plasma", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_PLASMA_SLAB = BLOCKS.register(
        "nexus_plasma_slab", () -> new SlabBlock(lightProps()));

    // 15. Signal - Yellow indicator
    public static final DeferredHolder<Block, Block> NEXUS_SIGNAL = BLOCKS.register(
        "nexus_signal", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_SIGNAL_SLAB = BLOCKS.register(
        "nexus_signal_slab", () -> new SlabBlock(lightProps()));

    // 16. Matrix - Green circuit
    public static final DeferredHolder<Block, Block> NEXUS_MATRIX = BLOCKS.register(
        "nexus_matrix", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_MATRIX_SLAB = BLOCKS.register(
        "nexus_matrix_slab", () -> new SlabBlock(lightProps()));

    // 17. Energy - Orange power cell
    public static final DeferredHolder<Block, Block> NEXUS_ENERGY = BLOCKS.register(
        "nexus_energy", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_ENERGY_SLAB = BLOCKS.register(
        "nexus_energy_slab", () -> new SlabBlock(lightProps()));

    // 18. Data - Blue data panel
    public static final DeferredHolder<Block, Block> NEXUS_DATA = BLOCKS.register(
        "nexus_data", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_DATA_SLAB = BLOCKS.register(
        "nexus_data_slab", () -> new SlabBlock(baseProps()));

    // 19. Crystal - Purple crystal
    public static final DeferredHolder<Block, Block> NEXUS_CRYSTAL = BLOCKS.register(
        "nexus_crystal", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_CRYSTAL_SLAB = BLOCKS.register(
        "nexus_crystal_slab", () -> new SlabBlock(lightProps()));

    // 20. Hologram - Cyan holographic
    public static final DeferredHolder<Block, Block> NEXUS_HOLO = BLOCKS.register(
        "nexus_hologram", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_HOLO_SLAB = BLOCKS.register(
        "nexus_hologram_slab", () -> new SlabBlock(lightProps()));

    // 21. Reactor - Red reactor
    public static final DeferredHolder<Block, Block> NEXUS_REACTOR = BLOCKS.register(
        "nexus_reactor", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_REACTOR_SLAB = BLOCKS.register(
        "nexus_reactor_slab", () -> new SlabBlock(lightProps()));

    // 22. Pulse - Pulse pattern
    public static final DeferredHolder<Block, Block> NEXUS_PULSE = BLOCKS.register(
        "nexus_pulse", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_PULSE_SLAB = BLOCKS.register(
        "nexus_pulse_slab", () -> new SlabBlock(lightProps()));

    // 23. Grid2 - Tech grid cyan
    public static final DeferredHolder<Block, Block> NEXUS_GRID2 = BLOCKS.register(
        "nexus_grid2", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_GRID2_SLAB = BLOCKS.register(
        "nexus_grid2_slab", () -> new SlabBlock(baseProps()));

    // 24. Tech - Tech panel mixed
    public static final DeferredHolder<Block, Block> NEXUS_TECH = BLOCKS.register(
        "nexus_tech", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_TECH_SLAB = BLOCKS.register(
        "nexus_tech_slab", () -> new SlabBlock(baseProps()));

    // 25. Stripes - Diagonal stripes
    public static final DeferredHolder<Block, Block> NEXUS_STRIPES = BLOCKS.register(
        "nexus_stripes", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_STRIPES_SLAB = BLOCKS.register(
        "nexus_stripes_slab", () -> new SlabBlock(baseProps()));

    // 26. Display - Monitor display
    public static final DeferredHolder<Block, Block> NEXUS_DISPLAY = BLOCKS.register(
        "nexus_display", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_DISPLAY_SLAB = BLOCKS.register(
        "nexus_display_slab", () -> new SlabBlock(lightProps()));

    // 27. Neon - Neon glow
    public static final DeferredHolder<Block, Block> NEXUS_NEON = BLOCKS.register(
        "nexus_neon", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_NEON_SLAB = BLOCKS.register(
        "nexus_neon_slab", () -> new SlabBlock(lightProps()));

    // 28. Carbon - Carbon fiber
    public static final DeferredHolder<Block, Block> NEXUS_CARBON = BLOCKS.register(
        "nexus_carbon", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_CARBON_SLAB = BLOCKS.register(
        "nexus_carbon_slab", () -> new SlabBlock(baseProps()));

    // 29. Quantum - Quantum particle
    public static final DeferredHolder<Block, Block> NEXUS_QUANTUM = BLOCKS.register(
        "nexus_quantum", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_QUANTUM_SLAB = BLOCKS.register(
        "nexus_quantum_slab", () -> new SlabBlock(lightProps()));

    // 30. Binary - Binary code
    public static final DeferredHolder<Block, Block> NEXUS_BINARY = BLOCKS.register(
        "nexus_binary", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_BINARY_SLAB = BLOCKS.register(
        "nexus_binary_slab", () -> new SlabBlock(lightProps()));

    // 31. Steel - Brushed steel
    public static final DeferredHolder<Block, Block> NEXUS_STEEL = BLOCKS.register(
        "nexus_steel", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_STEEL_SLAB = BLOCKS.register(
        "nexus_steel_slab", () -> new SlabBlock(baseProps()));

    // 32. Void - Dark void
    public static final DeferredHolder<Block, Block> NEXUS_VOID = BLOCKS.register(
        "nexus_void", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_VOID_SLAB = BLOCKS.register(
        "nexus_void_slab", () -> new SlabBlock(baseProps()));

    // 33. Glow - Glow panel
    public static final DeferredHolder<Block, Block> NEXUS_GLOW = BLOCKS.register(
        "nexus_glow", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_GLOW_SLAB = BLOCKS.register(
        "nexus_glow_slab", () -> new SlabBlock(lightProps()));

    // 34. Hazard - Warning stripes
    public static final DeferredHolder<Block, Block> NEXUS_HAZARD = BLOCKS.register(
        "nexus_hazard", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_HAZARD_SLAB = BLOCKS.register(
        "nexus_hazard_slab", () -> new SlabBlock(baseProps()));

    // 35. Iris - Eye/lens pattern
    public static final DeferredHolder<Block, Block> NEXUS_IRIS = BLOCKS.register(
        "nexus_iris", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_IRIS_SLAB = BLOCKS.register(
        "nexus_iris_slab", () -> new SlabBlock(lightProps()));

    // 36. Ember - Glowing ember
    public static final DeferredHolder<Block, Block> NEXUS_EMBER = BLOCKS.register(
        "nexus_ember", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_EMBER_SLAB = BLOCKS.register(
        "nexus_ember_slab", () -> new SlabBlock(lightProps()));

    // === BLOCK ITEMS ===

    public static final DeferredHolder<Item, BlockItem> NEXUS_PANEL_ITEM = ITEMS.register(
        "nexus_panel", () -> new BlockItem(NEXUS_PANEL.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_PANEL_SLAB_ITEM = ITEMS.register(
        "nexus_panel_slab", () -> new BlockItem(NEXUS_PANEL_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> NEXUS_TILE_ITEM = ITEMS.register(
        "nexus_tile", () -> new BlockItem(NEXUS_TILE.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_TILE_SLAB_ITEM = ITEMS.register(
        "nexus_tile_slab", () -> new BlockItem(NEXUS_TILE_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> NEXUS_GRID_ITEM = ITEMS.register(
        "nexus_grid", () -> new BlockItem(NEXUS_GRID.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_GRID_SLAB_ITEM = ITEMS.register(
        "nexus_grid_slab", () -> new BlockItem(NEXUS_GRID_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> NEXUS_PLATING_ITEM = ITEMS.register(
        "nexus_plating", () -> new BlockItem(NEXUS_PLATING.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_PLATING_SLAB_ITEM = ITEMS.register(
        "nexus_plating_slab", () -> new BlockItem(NEXUS_PLATING_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> NEXUS_CORE_ITEM = ITEMS.register(
        "nexus_core", () -> new BlockItem(NEXUS_CORE.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_CORE_SLAB_ITEM = ITEMS.register(
        "nexus_core_slab", () -> new BlockItem(NEXUS_CORE_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> NEXUS_FRAME_ITEM = ITEMS.register(
        "nexus_frame", () -> new BlockItem(NEXUS_FRAME.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_FRAME_SLAB_ITEM = ITEMS.register(
        "nexus_frame_slab", () -> new BlockItem(NEXUS_FRAME_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> NEXUS_CONDUIT_ITEM = ITEMS.register(
        "nexus_conduit", () -> new BlockItem(NEXUS_CONDUIT.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_CONDUIT_SLAB_ITEM = ITEMS.register(
        "nexus_conduit_slab", () -> new BlockItem(NEXUS_CONDUIT_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> NEXUS_TERMINAL_ITEM = ITEMS.register(
        "nexus_terminal", () -> new BlockItem(NEXUS_TERMINAL.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_TERMINAL_SLAB_ITEM = ITEMS.register(
        "nexus_terminal_slab", () -> new BlockItem(NEXUS_TERMINAL_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> NEXUS_VENT_ITEM = ITEMS.register(
        "nexus_vent", () -> new BlockItem(NEXUS_VENT.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_VENT_SLAB_ITEM = ITEMS.register(
        "nexus_vent_slab", () -> new BlockItem(NEXUS_VENT_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> NEXUS_CIRCUIT_ITEM = ITEMS.register(
        "nexus_circuit", () -> new BlockItem(NEXUS_CIRCUIT.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_CIRCUIT_SLAB_ITEM = ITEMS.register(
        "nexus_circuit_slab", () -> new BlockItem(NEXUS_CIRCUIT_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> NEXUS_SMOOTH_ITEM = ITEMS.register(
        "nexus_smooth", () -> new BlockItem(NEXUS_SMOOTH.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_SMOOTH_SLAB_ITEM = ITEMS.register(
        "nexus_smooth_slab", () -> new BlockItem(NEXUS_SMOOTH_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> NEXUS_LIGHT_ITEM = ITEMS.register(
        "nexus_light", () -> new BlockItem(NEXUS_LIGHT.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_LIGHT_SLAB_ITEM = ITEMS.register(
        "nexus_light_slab", () -> new BlockItem(NEXUS_LIGHT_SLAB.get(), new Item.Properties()));

    // Bold block items (13-36)
    public static final DeferredHolder<Item, BlockItem> NEXUS_AZURE_ITEM = ITEMS.register(
        "nexus_azure", () -> new BlockItem(NEXUS_AZURE.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_AZURE_SLAB_ITEM = ITEMS.register(
        "nexus_azure_slab", () -> new BlockItem(NEXUS_AZURE_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> NEXUS_PLASMA_ITEM = ITEMS.register(
        "nexus_plasma", () -> new BlockItem(NEXUS_PLASMA.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_PLASMA_SLAB_ITEM = ITEMS.register(
        "nexus_plasma_slab", () -> new BlockItem(NEXUS_PLASMA_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> NEXUS_SIGNAL_ITEM = ITEMS.register(
        "nexus_signal", () -> new BlockItem(NEXUS_SIGNAL.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_SIGNAL_SLAB_ITEM = ITEMS.register(
        "nexus_signal_slab", () -> new BlockItem(NEXUS_SIGNAL_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> NEXUS_MATRIX_ITEM = ITEMS.register(
        "nexus_matrix", () -> new BlockItem(NEXUS_MATRIX.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_MATRIX_SLAB_ITEM = ITEMS.register(
        "nexus_matrix_slab", () -> new BlockItem(NEXUS_MATRIX_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> NEXUS_ENERGY_ITEM = ITEMS.register(
        "nexus_energy", () -> new BlockItem(NEXUS_ENERGY.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_ENERGY_SLAB_ITEM = ITEMS.register(
        "nexus_energy_slab", () -> new BlockItem(NEXUS_ENERGY_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> NEXUS_DATA_ITEM = ITEMS.register(
        "nexus_data", () -> new BlockItem(NEXUS_DATA.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_DATA_SLAB_ITEM = ITEMS.register(
        "nexus_data_slab", () -> new BlockItem(NEXUS_DATA_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> NEXUS_CRYSTAL_ITEM = ITEMS.register(
        "nexus_crystal", () -> new BlockItem(NEXUS_CRYSTAL.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_CRYSTAL_SLAB_ITEM = ITEMS.register(
        "nexus_crystal_slab", () -> new BlockItem(NEXUS_CRYSTAL_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> NEXUS_HOLO_ITEM = ITEMS.register(
        "nexus_hologram", () -> new BlockItem(NEXUS_HOLO.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_HOLO_SLAB_ITEM = ITEMS.register(
        "nexus_hologram_slab", () -> new BlockItem(NEXUS_HOLO_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> NEXUS_REACTOR_ITEM = ITEMS.register(
        "nexus_reactor", () -> new BlockItem(NEXUS_REACTOR.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_REACTOR_SLAB_ITEM = ITEMS.register(
        "nexus_reactor_slab", () -> new BlockItem(NEXUS_REACTOR_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> NEXUS_PULSE_ITEM = ITEMS.register(
        "nexus_pulse", () -> new BlockItem(NEXUS_PULSE.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_PULSE_SLAB_ITEM = ITEMS.register(
        "nexus_pulse_slab", () -> new BlockItem(NEXUS_PULSE_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> NEXUS_GRID2_ITEM = ITEMS.register(
        "nexus_grid2", () -> new BlockItem(NEXUS_GRID2.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_GRID2_SLAB_ITEM = ITEMS.register(
        "nexus_grid2_slab", () -> new BlockItem(NEXUS_GRID2_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> NEXUS_TECH_ITEM = ITEMS.register(
        "nexus_tech", () -> new BlockItem(NEXUS_TECH.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_TECH_SLAB_ITEM = ITEMS.register(
        "nexus_tech_slab", () -> new BlockItem(NEXUS_TECH_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> NEXUS_STRIPES_ITEM = ITEMS.register(
        "nexus_stripes", () -> new BlockItem(NEXUS_STRIPES.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_STRIPES_SLAB_ITEM = ITEMS.register(
        "nexus_stripes_slab", () -> new BlockItem(NEXUS_STRIPES_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> NEXUS_DISPLAY_ITEM = ITEMS.register(
        "nexus_display", () -> new BlockItem(NEXUS_DISPLAY.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_DISPLAY_SLAB_ITEM = ITEMS.register(
        "nexus_display_slab", () -> new BlockItem(NEXUS_DISPLAY_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> NEXUS_NEON_ITEM = ITEMS.register(
        "nexus_neon", () -> new BlockItem(NEXUS_NEON.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_NEON_SLAB_ITEM = ITEMS.register(
        "nexus_neon_slab", () -> new BlockItem(NEXUS_NEON_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> NEXUS_CARBON_ITEM = ITEMS.register(
        "nexus_carbon", () -> new BlockItem(NEXUS_CARBON.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_CARBON_SLAB_ITEM = ITEMS.register(
        "nexus_carbon_slab", () -> new BlockItem(NEXUS_CARBON_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> NEXUS_QUANTUM_ITEM = ITEMS.register(
        "nexus_quantum", () -> new BlockItem(NEXUS_QUANTUM.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_QUANTUM_SLAB_ITEM = ITEMS.register(
        "nexus_quantum_slab", () -> new BlockItem(NEXUS_QUANTUM_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> NEXUS_BINARY_ITEM = ITEMS.register(
        "nexus_binary", () -> new BlockItem(NEXUS_BINARY.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_BINARY_SLAB_ITEM = ITEMS.register(
        "nexus_binary_slab", () -> new BlockItem(NEXUS_BINARY_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> NEXUS_STEEL_ITEM = ITEMS.register(
        "nexus_steel", () -> new BlockItem(NEXUS_STEEL.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_STEEL_SLAB_ITEM = ITEMS.register(
        "nexus_steel_slab", () -> new BlockItem(NEXUS_STEEL_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> NEXUS_VOID_ITEM = ITEMS.register(
        "nexus_void", () -> new BlockItem(NEXUS_VOID.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_VOID_SLAB_ITEM = ITEMS.register(
        "nexus_void_slab", () -> new BlockItem(NEXUS_VOID_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> NEXUS_GLOW_ITEM = ITEMS.register(
        "nexus_glow", () -> new BlockItem(NEXUS_GLOW.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_GLOW_SLAB_ITEM = ITEMS.register(
        "nexus_glow_slab", () -> new BlockItem(NEXUS_GLOW_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> NEXUS_HAZARD_ITEM = ITEMS.register(
        "nexus_hazard", () -> new BlockItem(NEXUS_HAZARD.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_HAZARD_SLAB_ITEM = ITEMS.register(
        "nexus_hazard_slab", () -> new BlockItem(NEXUS_HAZARD_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> NEXUS_IRIS_ITEM = ITEMS.register(
        "nexus_iris", () -> new BlockItem(NEXUS_IRIS.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_IRIS_SLAB_ITEM = ITEMS.register(
        "nexus_iris_slab", () -> new BlockItem(NEXUS_IRIS_SLAB.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> NEXUS_EMBER_ITEM = ITEMS.register(
        "nexus_ember", () -> new BlockItem(NEXUS_EMBER.get(), new Item.Properties()));
    public static final DeferredHolder<Item, BlockItem> NEXUS_EMBER_SLAB_ITEM = ITEMS.register(
        "nexus_ember_slab", () -> new BlockItem(NEXUS_EMBER_SLAB.get(), new Item.Properties()));

    // === CREATIVE TAB ===
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> NEXUS_TAB = CREATIVE_TABS.register(
        "nexus_blocks", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.devmod.nexus_blocks"))
            .icon(() -> new ItemStack(NEXUS_PANEL.get()))
            .displayItems((params, output) -> {
                // Original blocks (1-12)
                output.accept(NEXUS_PANEL_ITEM.get());
                output.accept(NEXUS_TILE_ITEM.get());
                output.accept(NEXUS_GRID_ITEM.get());
                output.accept(NEXUS_PLATING_ITEM.get());
                output.accept(NEXUS_CORE_ITEM.get());
                output.accept(NEXUS_FRAME_ITEM.get());
                output.accept(NEXUS_CONDUIT_ITEM.get());
                output.accept(NEXUS_TERMINAL_ITEM.get());
                output.accept(NEXUS_VENT_ITEM.get());
                output.accept(NEXUS_CIRCUIT_ITEM.get());
                output.accept(NEXUS_SMOOTH_ITEM.get());
                output.accept(NEXUS_LIGHT_ITEM.get());
                // Bold blocks (13-36)
                output.accept(NEXUS_AZURE_ITEM.get());
                output.accept(NEXUS_PLASMA_ITEM.get());
                output.accept(NEXUS_SIGNAL_ITEM.get());
                output.accept(NEXUS_MATRIX_ITEM.get());
                output.accept(NEXUS_ENERGY_ITEM.get());
                output.accept(NEXUS_DATA_ITEM.get());
                output.accept(NEXUS_CRYSTAL_ITEM.get());
                output.accept(NEXUS_HOLO_ITEM.get());
                output.accept(NEXUS_REACTOR_ITEM.get());
                output.accept(NEXUS_PULSE_ITEM.get());
                output.accept(NEXUS_GRID2_ITEM.get());
                output.accept(NEXUS_TECH_ITEM.get());
                output.accept(NEXUS_STRIPES_ITEM.get());
                output.accept(NEXUS_DISPLAY_ITEM.get());
                output.accept(NEXUS_NEON_ITEM.get());
                output.accept(NEXUS_CARBON_ITEM.get());
                output.accept(NEXUS_QUANTUM_ITEM.get());
                output.accept(NEXUS_BINARY_ITEM.get());
                output.accept(NEXUS_STEEL_ITEM.get());
                output.accept(NEXUS_VOID_ITEM.get());
                output.accept(NEXUS_GLOW_ITEM.get());
                output.accept(NEXUS_HAZARD_ITEM.get());
                output.accept(NEXUS_IRIS_ITEM.get());
                output.accept(NEXUS_EMBER_ITEM.get());
                // Original slabs
                output.accept(NEXUS_PANEL_SLAB_ITEM.get());
                output.accept(NEXUS_TILE_SLAB_ITEM.get());
                output.accept(NEXUS_GRID_SLAB_ITEM.get());
                output.accept(NEXUS_PLATING_SLAB_ITEM.get());
                output.accept(NEXUS_CORE_SLAB_ITEM.get());
                output.accept(NEXUS_FRAME_SLAB_ITEM.get());
                output.accept(NEXUS_CONDUIT_SLAB_ITEM.get());
                output.accept(NEXUS_TERMINAL_SLAB_ITEM.get());
                output.accept(NEXUS_VENT_SLAB_ITEM.get());
                output.accept(NEXUS_CIRCUIT_SLAB_ITEM.get());
                output.accept(NEXUS_SMOOTH_SLAB_ITEM.get());
                output.accept(NEXUS_LIGHT_SLAB_ITEM.get());
                // Bold slabs
                output.accept(NEXUS_AZURE_SLAB_ITEM.get());
                output.accept(NEXUS_PLASMA_SLAB_ITEM.get());
                output.accept(NEXUS_SIGNAL_SLAB_ITEM.get());
                output.accept(NEXUS_MATRIX_SLAB_ITEM.get());
                output.accept(NEXUS_ENERGY_SLAB_ITEM.get());
                output.accept(NEXUS_DATA_SLAB_ITEM.get());
                output.accept(NEXUS_CRYSTAL_SLAB_ITEM.get());
                output.accept(NEXUS_HOLO_SLAB_ITEM.get());
                output.accept(NEXUS_REACTOR_SLAB_ITEM.get());
                output.accept(NEXUS_PULSE_SLAB_ITEM.get());
                output.accept(NEXUS_GRID2_SLAB_ITEM.get());
                output.accept(NEXUS_TECH_SLAB_ITEM.get());
                output.accept(NEXUS_STRIPES_SLAB_ITEM.get());
                output.accept(NEXUS_DISPLAY_SLAB_ITEM.get());
                output.accept(NEXUS_NEON_SLAB_ITEM.get());
                output.accept(NEXUS_CARBON_SLAB_ITEM.get());
                output.accept(NEXUS_QUANTUM_SLAB_ITEM.get());
                output.accept(NEXUS_BINARY_SLAB_ITEM.get());
                output.accept(NEXUS_STEEL_SLAB_ITEM.get());
                output.accept(NEXUS_VOID_SLAB_ITEM.get());
                output.accept(NEXUS_GLOW_SLAB_ITEM.get());
                output.accept(NEXUS_HAZARD_SLAB_ITEM.get());
                output.accept(NEXUS_IRIS_SLAB_ITEM.get());
                output.accept(NEXUS_EMBER_SLAB_ITEM.get());
            })
            .build()
    );

    /**
     * Register blocks and items on the mod event bus.
     */
    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
        CREATIVE_TABS.register(eventBus);
        DevMod.LOGGER.info("[Nexus] Decorative blocks registered");
    }
}
