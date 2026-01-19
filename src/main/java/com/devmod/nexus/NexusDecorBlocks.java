package com.devmod.nexus;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
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
        DeferredRegister.create(Objects.requireNonNull(Registries.CREATIVE_MODE_TAB), DevMod.MODID);

    // === BASE PROPERTIES ===
    @Nonnull
    private static BlockBehaviour.Properties baseProps() {
        return Objects.requireNonNull(BlockBehaviour.Properties.of()
            .strength(1.5f, 6.0f)
            .sound(Objects.requireNonNull(SoundType.METAL))
            .requiresCorrectToolForDrops());
    }

    @Nonnull
    private static BlockBehaviour.Properties lightProps() {
        return Objects.requireNonNull(baseProps().lightLevel(state -> 12));
    }

    @Nonnull
    private static BlockItem blockItem(DeferredHolder<Block, ? extends Block> blockHolder) {
        return new BlockItem(Objects.requireNonNull(blockHolder.get()), new Item.Properties());
    }

    private static void acceptItem(CreativeModeTab.Output output, DeferredHolder<Item, ? extends Item> itemHolder) {
        output.accept(Objects.requireNonNull(itemHolder.get()));
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

    // === NEW BLOCKS (37-72) ===
    public static final DeferredHolder<Block, Block> NEXUS_COBALT = BLOCKS.register("nexus_cobalt", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_COBALT_SLAB = BLOCKS.register("nexus_cobalt_slab", () -> new SlabBlock(baseProps()));
    public static final DeferredHolder<Block, Block> NEXUS_MINT = BLOCKS.register("nexus_mint", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_MINT_SLAB = BLOCKS.register("nexus_mint_slab", () -> new SlabBlock(baseProps()));
    public static final DeferredHolder<Block, Block> NEXUS_ROSE = BLOCKS.register("nexus_rose", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_ROSE_SLAB = BLOCKS.register("nexus_rose_slab", () -> new SlabBlock(baseProps()));
    public static final DeferredHolder<Block, Block> NEXUS_ONYX = BLOCKS.register("nexus_onyx", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_ONYX_SLAB = BLOCKS.register("nexus_onyx_slab", () -> new SlabBlock(baseProps()));
    public static final DeferredHolder<Block, Block> NEXUS_COPPER = BLOCKS.register("nexus_copper", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_COPPER_SLAB = BLOCKS.register("nexus_copper_slab", () -> new SlabBlock(baseProps()));
    public static final DeferredHolder<Block, Block> NEXUS_GOLD = BLOCKS.register("nexus_gold", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_GOLD_SLAB = BLOCKS.register("nexus_gold_slab", () -> new SlabBlock(baseProps()));
    public static final DeferredHolder<Block, Block> NEXUS_SILVER = BLOCKS.register("nexus_silver", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_SILVER_SLAB = BLOCKS.register("nexus_silver_slab", () -> new SlabBlock(baseProps()));
    public static final DeferredHolder<Block, Block> NEXUS_BRONZE = BLOCKS.register("nexus_bronze", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_BRONZE_SLAB = BLOCKS.register("nexus_bronze_slab", () -> new SlabBlock(baseProps()));
    public static final DeferredHolder<Block, Block> NEXUS_HEX = BLOCKS.register("nexus_hex", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_HEX_SLAB = BLOCKS.register("nexus_hex_slab", () -> new SlabBlock(baseProps()));
    public static final DeferredHolder<Block, Block> NEXUS_DOTS = BLOCKS.register("nexus_dots", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_DOTS_SLAB = BLOCKS.register("nexus_dots_slab", () -> new SlabBlock(lightProps()));
    public static final DeferredHolder<Block, Block> NEXUS_WAVE = BLOCKS.register("nexus_wave", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_WAVE_SLAB = BLOCKS.register("nexus_wave_slab", () -> new SlabBlock(baseProps()));
    public static final DeferredHolder<Block, Block> NEXUS_CHECKER = BLOCKS.register("nexus_checker", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_CHECKER_SLAB = BLOCKS.register("nexus_checker_slab", () -> new SlabBlock(baseProps()));
    public static final DeferredHolder<Block, Block> NEXUS_DIAMOND = BLOCKS.register("nexus_diamond", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_DIAMOND_SLAB = BLOCKS.register("nexus_diamond_slab", () -> new SlabBlock(baseProps()));
    public static final DeferredHolder<Block, Block> NEXUS_CROSS = BLOCKS.register("nexus_cross", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_CROSS_SLAB = BLOCKS.register("nexus_cross_slab", () -> new SlabBlock(lightProps()));
    public static final DeferredHolder<Block, Block> NEXUS_SPIRAL = BLOCKS.register("nexus_spiral", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_SPIRAL_SLAB = BLOCKS.register("nexus_spiral_slab", () -> new SlabBlock(baseProps()));
    public static final DeferredHolder<Block, Block> NEXUS_BRICK = BLOCKS.register("nexus_brick", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_BRICK_SLAB = BLOCKS.register("nexus_brick_slab", () -> new SlabBlock(baseProps()));
    public static final DeferredHolder<Block, Block> NEXUS_SCALES = BLOCKS.register("nexus_scales", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_SCALES_SLAB = BLOCKS.register("nexus_scales_slab", () -> new SlabBlock(baseProps()));
    public static final DeferredHolder<Block, Block> NEXUS_MESH = BLOCKS.register("nexus_mesh", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_MESH_SLAB = BLOCKS.register("nexus_mesh_slab", () -> new SlabBlock(baseProps()));
    public static final DeferredHolder<Block, Block> NEXUS_AZURE_LIGHT = BLOCKS.register("nexus_azure_light", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_AZURE_LIGHT_SLAB = BLOCKS.register("nexus_azure_light_slab", () -> new SlabBlock(lightProps()));
    public static final DeferredHolder<Block, Block> NEXUS_AZURE_DARK = BLOCKS.register("nexus_azure_dark", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_AZURE_DARK_SLAB = BLOCKS.register("nexus_azure_dark_slab", () -> new SlabBlock(baseProps()));
    public static final DeferredHolder<Block, Block> NEXUS_PLASMA_LIGHT = BLOCKS.register("nexus_plasma_light", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_PLASMA_LIGHT_SLAB = BLOCKS.register("nexus_plasma_light_slab", () -> new SlabBlock(lightProps()));
    public static final DeferredHolder<Block, Block> NEXUS_PLASMA_DARK = BLOCKS.register("nexus_plasma_dark", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_PLASMA_DARK_SLAB = BLOCKS.register("nexus_plasma_dark_slab", () -> new SlabBlock(baseProps()));
    public static final DeferredHolder<Block, Block> NEXUS_MATRIX_LIGHT = BLOCKS.register("nexus_matrix_light", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_MATRIX_LIGHT_SLAB = BLOCKS.register("nexus_matrix_light_slab", () -> new SlabBlock(lightProps()));
    public static final DeferredHolder<Block, Block> NEXUS_ENERGY_LIGHT = BLOCKS.register("nexus_energy_light", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_ENERGY_LIGHT_SLAB = BLOCKS.register("nexus_energy_light_slab", () -> new SlabBlock(lightProps()));
    public static final DeferredHolder<Block, Block> NEXUS_REACTOR_LIGHT = BLOCKS.register("nexus_reactor_light", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_REACTOR_LIGHT_SLAB = BLOCKS.register("nexus_reactor_light_slab", () -> new SlabBlock(lightProps()));
    public static final DeferredHolder<Block, Block> NEXUS_CRYSTAL_LIGHT = BLOCKS.register("nexus_crystal_light", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_CRYSTAL_LIGHT_SLAB = BLOCKS.register("nexus_crystal_light_slab", () -> new SlabBlock(lightProps()));
    public static final DeferredHolder<Block, Block> NEXUS_NEON_BLUE = BLOCKS.register("nexus_neon_blue", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_NEON_BLUE_SLAB = BLOCKS.register("nexus_neon_blue_slab", () -> new SlabBlock(lightProps()));
    public static final DeferredHolder<Block, Block> NEXUS_CIRCUIT_GOLD = BLOCKS.register("nexus_circuit_gold", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_CIRCUIT_GOLD_SLAB = BLOCKS.register("nexus_circuit_gold_slab", () -> new SlabBlock(lightProps()));
    public static final DeferredHolder<Block, Block> NEXUS_PANEL_DARK = BLOCKS.register("nexus_panel_dark", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_PANEL_DARK_SLAB = BLOCKS.register("nexus_panel_dark_slab", () -> new SlabBlock(baseProps()));
    public static final DeferredHolder<Block, Block> NEXUS_GRID_GOLD = BLOCKS.register("nexus_grid_gold", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_GRID_GOLD_SLAB = BLOCKS.register("nexus_grid_gold_slab", () -> new SlabBlock(lightProps()));
    public static final DeferredHolder<Block, Block> NEXUS_VENT_RED = BLOCKS.register("nexus_vent_red", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_VENT_RED_SLAB = BLOCKS.register("nexus_vent_red_slab", () -> new SlabBlock(baseProps()));
    public static final DeferredHolder<Block, Block> NEXUS_TERMINAL_GREEN = BLOCKS.register("nexus_terminal_green", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_TERMINAL_GREEN_SLAB = BLOCKS.register("nexus_terminal_green_slab", () -> new SlabBlock(lightProps()));
    public static final DeferredHolder<Block, Block> NEXUS_DISPLAY_RED = BLOCKS.register("nexus_display_red", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_DISPLAY_RED_SLAB = BLOCKS.register("nexus_display_red_slab", () -> new SlabBlock(lightProps()));
    public static final DeferredHolder<Block, Block> NEXUS_CONDUIT_YELLOW = BLOCKS.register("nexus_conduit_yellow", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_CONDUIT_YELLOW_SLAB = BLOCKS.register("nexus_conduit_yellow_slab", () -> new SlabBlock(baseProps()));
    public static final DeferredHolder<Block, Block> NEXUS_FRAME_WHITE = BLOCKS.register("nexus_frame_white", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_FRAME_WHITE_SLAB = BLOCKS.register("nexus_frame_white_slab", () -> new SlabBlock(lightProps()));
    public static final DeferredHolder<Block, Block> NEXUS_TILE_PURPLE = BLOCKS.register("nexus_tile_purple", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_TILE_PURPLE_SLAB = BLOCKS.register("nexus_tile_purple_slab", () -> new SlabBlock(baseProps()));

    // === SEAMLESS BLOCKS (no visible borders for smooth surfaces) ===
    public static final DeferredHolder<Block, Block> NEXUS_WHITE_PURE = BLOCKS.register("nexus_white_pure", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_WHITE_PURE_SLAB = BLOCKS.register("nexus_white_pure_slab", () -> new SlabBlock(lightProps()));
    public static final DeferredHolder<Block, Block> NEXUS_LIGHT_SEAMLESS = BLOCKS.register("nexus_light_seamless", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_LIGHT_SEAMLESS_SLAB = BLOCKS.register("nexus_light_seamless_slab", () -> new SlabBlock(lightProps()));
    public static final DeferredHolder<Block, Block> NEXUS_GRAY_SEAMLESS = BLOCKS.register("nexus_gray_seamless", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_GRAY_SEAMLESS_SLAB = BLOCKS.register("nexus_gray_seamless_slab", () -> new SlabBlock(baseProps()));
    public static final DeferredHolder<Block, Block> NEXUS_DARK_SEAMLESS = BLOCKS.register("nexus_dark_seamless", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_DARK_SEAMLESS_SLAB = BLOCKS.register("nexus_dark_seamless_slab", () -> new SlabBlock(baseProps()));
    public static final DeferredHolder<Block, Block> NEXUS_WHITE_GRID = BLOCKS.register("nexus_white_grid", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_WHITE_GRID_SLAB = BLOCKS.register("nexus_white_grid_slab", () -> new SlabBlock(lightProps()));
    public static final DeferredHolder<Block, Block> NEXUS_CYAN_GLOW = BLOCKS.register("nexus_cyan_glow", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_CYAN_GLOW_SLAB = BLOCKS.register("nexus_cyan_glow_slab", () -> new SlabBlock(lightProps()));
    public static final DeferredHolder<Block, Block> NEXUS_STEEL_SEAMLESS = BLOCKS.register("nexus_steel_seamless", () -> new Block(baseProps()));
    public static final DeferredHolder<Block, SlabBlock> NEXUS_STEEL_SEAMLESS_SLAB = BLOCKS.register("nexus_steel_seamless_slab", () -> new SlabBlock(baseProps()));

    // === STAIRS (for smooth transitions and tech look) ===
    public static final DeferredHolder<Block, StairBlock> NEXUS_WHITE_PURE_STAIRS = BLOCKS.register("nexus_white_pure_stairs",
        () -> new StairBlock(Objects.requireNonNull(NEXUS_WHITE_PURE.get().defaultBlockState()), lightProps()));
    public static final DeferredHolder<Block, StairBlock> NEXUS_LIGHT_SEAMLESS_STAIRS = BLOCKS.register("nexus_light_seamless_stairs",
        () -> new StairBlock(Objects.requireNonNull(NEXUS_LIGHT_SEAMLESS.get().defaultBlockState()), lightProps()));
    public static final DeferredHolder<Block, StairBlock> NEXUS_GRAY_SEAMLESS_STAIRS = BLOCKS.register("nexus_gray_seamless_stairs",
        () -> new StairBlock(Objects.requireNonNull(NEXUS_GRAY_SEAMLESS.get().defaultBlockState()), baseProps()));
    public static final DeferredHolder<Block, StairBlock> NEXUS_DARK_SEAMLESS_STAIRS = BLOCKS.register("nexus_dark_seamless_stairs",
        () -> new StairBlock(Objects.requireNonNull(NEXUS_DARK_SEAMLESS.get().defaultBlockState()), baseProps()));
    public static final DeferredHolder<Block, StairBlock> NEXUS_STEEL_SEAMLESS_STAIRS = BLOCKS.register("nexus_steel_seamless_stairs",
        () -> new StairBlock(Objects.requireNonNull(NEXUS_STEEL_SEAMLESS.get().defaultBlockState()), baseProps()));
    public static final DeferredHolder<Block, StairBlock> NEXUS_CYAN_GLOW_STAIRS = BLOCKS.register("nexus_cyan_glow_stairs",
        () -> new StairBlock(Objects.requireNonNull(NEXUS_CYAN_GLOW.get().defaultBlockState()), glowStairProps()));

    // === COLORED CORRIDOR STAIRS (for smooth transitions with matching colors) ===
    public static final DeferredHolder<Block, StairBlock> NEXUS_NEON_BLUE_STAIRS = BLOCKS.register("nexus_neon_blue_stairs",
        () -> new StairBlock(Objects.requireNonNull(NEXUS_NEON_BLUE.get().defaultBlockState()), lightProps()));
    public static final DeferredHolder<Block, StairBlock> NEXUS_CIRCUIT_GOLD_STAIRS = BLOCKS.register("nexus_circuit_gold_stairs",
        () -> new StairBlock(Objects.requireNonNull(NEXUS_CIRCUIT_GOLD.get().defaultBlockState()), lightProps()));
    public static final DeferredHolder<Block, StairBlock> NEXUS_CIRCUIT_STAIRS = BLOCKS.register("nexus_circuit_stairs",
        () -> new StairBlock(Objects.requireNonNull(NEXUS_CIRCUIT.get().defaultBlockState()), lightProps()));
    public static final DeferredHolder<Block, StairBlock> NEXUS_EMBER_STAIRS = BLOCKS.register("nexus_ember_stairs",
        () -> new StairBlock(Objects.requireNonNull(NEXUS_EMBER.get().defaultBlockState()), baseProps()));
    public static final DeferredHolder<Block, StairBlock> NEXUS_TILE_PURPLE_STAIRS = BLOCKS.register("nexus_tile_purple_stairs",
        () -> new StairBlock(Objects.requireNonNull(NEXUS_TILE_PURPLE.get().defaultBlockState()), baseProps()));
    public static final DeferredHolder<Block, StairBlock> NEXUS_CONDUIT_YELLOW_STAIRS = BLOCKS.register("nexus_conduit_yellow_stairs",
        () -> new StairBlock(Objects.requireNonNull(NEXUS_CONDUIT_YELLOW.get().defaultBlockState()), baseProps()));
    public static final DeferredHolder<Block, StairBlock> NEXUS_CONDUIT_STAIRS = BLOCKS.register("nexus_conduit_stairs",
        () -> new StairBlock(Objects.requireNonNull(NEXUS_CONDUIT.get().defaultBlockState()), baseProps()));
    public static final DeferredHolder<Block, StairBlock> NEXUS_WHITE_GRID_STAIRS = BLOCKS.register("nexus_white_grid_stairs",
        () -> new StairBlock(Objects.requireNonNull(NEXUS_WHITE_GRID.get().defaultBlockState()), lightProps()));

    @Nonnull
    private static BlockBehaviour.Properties glowStairProps() {
        return Objects.requireNonNull(BlockBehaviour.Properties.of()
            .strength(1.0f, 4.0f)
            .sound(Objects.requireNonNull(SoundType.METAL))
            .lightLevel(state -> 12));
    }

    // === HUB SPECIALTY BLOCKS (glass, glow strips, telepad, floor lights) ===
    @Nonnull
    private static BlockBehaviour.Properties glassProps() {
        return Objects.requireNonNull(BlockBehaviour.Properties.of()
            .strength(0.5f, 3.0f)
            .sound(Objects.requireNonNull(SoundType.GLASS))
            .noOcclusion()
            .lightLevel(state -> 8));
    }

    @Nonnull
    private static BlockBehaviour.Properties glowProps() {
        return Objects.requireNonNull(BlockBehaviour.Properties.of()
            .strength(1.0f, 4.0f)
            .sound(Objects.requireNonNull(SoundType.METAL))
            .lightLevel(state -> 15));
    }

    // Glass blocks (semi-transparent, for walkways and walls)
    public static final DeferredHolder<Block, Block> NEXUS_GLASS_CYAN = BLOCKS.register("nexus_glass_cyan", () -> new Block(glassProps()));
    public static final DeferredHolder<Block, Block> NEXUS_GLASS_WHITE = BLOCKS.register("nexus_glass_white", () -> new Block(glassProps()));
    public static final DeferredHolder<Block, Block> NEXUS_GLASS_DARK = BLOCKS.register("nexus_glass_dark", () -> new Block(glassProps()));

    // Glow strip blocks (for neon accent lines)
    public static final DeferredHolder<Block, Block> NEXUS_GLOW_STRIP_CYAN = BLOCKS.register("nexus_glow_strip_cyan", () -> new Block(glowProps()));
    public static final DeferredHolder<Block, Block> NEXUS_GLOW_STRIP_WHITE = BLOCKS.register("nexus_glow_strip_white", () -> new Block(glowProps()));

    // Floor light blocks (embedded floor lights)
    public static final DeferredHolder<Block, Block> NEXUS_FLOOR_LIGHT = BLOCKS.register("nexus_floor_light", () -> new Block(glowProps()));
    public static final DeferredHolder<Block, Block> NEXUS_FLOOR_LIGHT_CYAN = BLOCKS.register("nexus_floor_light_cyan", () -> new Block(glowProps()));

    // Telepad blocks (core and ring)
    public static final DeferredHolder<Block, Block> NEXUS_TELEPAD_CORE = BLOCKS.register("nexus_telepad_core", () -> new Block(glowProps()));
    public static final DeferredHolder<Block, Block> NEXUS_TELEPAD_RING = BLOCKS.register("nexus_telepad_ring", () -> new Block(glowProps()));

    // Ring panel blocks (for concentric rings)
    public static final DeferredHolder<Block, Block> NEXUS_RING_PANEL = BLOCKS.register("nexus_ring_panel", () -> new Block(lightProps()));
    public static final DeferredHolder<Block, Block> NEXUS_RING_ACCENT = BLOCKS.register("nexus_ring_accent", () -> new Block(lightProps()));

    // Center floor block (dark with pattern)
    public static final DeferredHolder<Block, Block> NEXUS_CENTER_FLOOR = BLOCKS.register("nexus_center_floor", () -> new Block(baseProps()));

    // === BLOCK ITEMS ===

    public static final DeferredHolder<Item, BlockItem> NEXUS_PANEL_ITEM = ITEMS.register(
        "nexus_panel", () -> blockItem(NEXUS_PANEL));
    public static final DeferredHolder<Item, BlockItem> NEXUS_PANEL_SLAB_ITEM = ITEMS.register(
        "nexus_panel_slab", () -> blockItem(NEXUS_PANEL_SLAB));

    public static final DeferredHolder<Item, BlockItem> NEXUS_TILE_ITEM = ITEMS.register(
        "nexus_tile", () -> blockItem(NEXUS_TILE));
    public static final DeferredHolder<Item, BlockItem> NEXUS_TILE_SLAB_ITEM = ITEMS.register(
        "nexus_tile_slab", () -> blockItem(NEXUS_TILE_SLAB));

    public static final DeferredHolder<Item, BlockItem> NEXUS_GRID_ITEM = ITEMS.register(
        "nexus_grid", () -> blockItem(NEXUS_GRID));
    public static final DeferredHolder<Item, BlockItem> NEXUS_GRID_SLAB_ITEM = ITEMS.register(
        "nexus_grid_slab", () -> blockItem(NEXUS_GRID_SLAB));

    public static final DeferredHolder<Item, BlockItem> NEXUS_PLATING_ITEM = ITEMS.register(
        "nexus_plating", () -> blockItem(NEXUS_PLATING));
    public static final DeferredHolder<Item, BlockItem> NEXUS_PLATING_SLAB_ITEM = ITEMS.register(
        "nexus_plating_slab", () -> blockItem(NEXUS_PLATING_SLAB));

    public static final DeferredHolder<Item, BlockItem> NEXUS_CORE_ITEM = ITEMS.register(
        "nexus_core", () -> blockItem(NEXUS_CORE));
    public static final DeferredHolder<Item, BlockItem> NEXUS_CORE_SLAB_ITEM = ITEMS.register(
        "nexus_core_slab", () -> blockItem(NEXUS_CORE_SLAB));

    public static final DeferredHolder<Item, BlockItem> NEXUS_FRAME_ITEM = ITEMS.register(
        "nexus_frame", () -> blockItem(NEXUS_FRAME));
    public static final DeferredHolder<Item, BlockItem> NEXUS_FRAME_SLAB_ITEM = ITEMS.register(
        "nexus_frame_slab", () -> blockItem(NEXUS_FRAME_SLAB));

    public static final DeferredHolder<Item, BlockItem> NEXUS_CONDUIT_ITEM = ITEMS.register(
        "nexus_conduit", () -> blockItem(NEXUS_CONDUIT));
    public static final DeferredHolder<Item, BlockItem> NEXUS_CONDUIT_SLAB_ITEM = ITEMS.register(
        "nexus_conduit_slab", () -> blockItem(NEXUS_CONDUIT_SLAB));

    public static final DeferredHolder<Item, BlockItem> NEXUS_TERMINAL_ITEM = ITEMS.register(
        "nexus_terminal", () -> blockItem(NEXUS_TERMINAL));
    public static final DeferredHolder<Item, BlockItem> NEXUS_TERMINAL_SLAB_ITEM = ITEMS.register(
        "nexus_terminal_slab", () -> blockItem(NEXUS_TERMINAL_SLAB));

    public static final DeferredHolder<Item, BlockItem> NEXUS_VENT_ITEM = ITEMS.register(
        "nexus_vent", () -> blockItem(NEXUS_VENT));
    public static final DeferredHolder<Item, BlockItem> NEXUS_VENT_SLAB_ITEM = ITEMS.register(
        "nexus_vent_slab", () -> blockItem(NEXUS_VENT_SLAB));

    public static final DeferredHolder<Item, BlockItem> NEXUS_CIRCUIT_ITEM = ITEMS.register(
        "nexus_circuit", () -> blockItem(NEXUS_CIRCUIT));
    public static final DeferredHolder<Item, BlockItem> NEXUS_CIRCUIT_SLAB_ITEM = ITEMS.register(
        "nexus_circuit_slab", () -> blockItem(NEXUS_CIRCUIT_SLAB));

    public static final DeferredHolder<Item, BlockItem> NEXUS_SMOOTH_ITEM = ITEMS.register(
        "nexus_smooth", () -> blockItem(NEXUS_SMOOTH));
    public static final DeferredHolder<Item, BlockItem> NEXUS_SMOOTH_SLAB_ITEM = ITEMS.register(
        "nexus_smooth_slab", () -> blockItem(NEXUS_SMOOTH_SLAB));

    public static final DeferredHolder<Item, BlockItem> NEXUS_LIGHT_ITEM = ITEMS.register(
        "nexus_light", () -> blockItem(NEXUS_LIGHT));
    public static final DeferredHolder<Item, BlockItem> NEXUS_LIGHT_SLAB_ITEM = ITEMS.register(
        "nexus_light_slab", () -> blockItem(NEXUS_LIGHT_SLAB));

    // Bold block items (13-36)
    public static final DeferredHolder<Item, BlockItem> NEXUS_AZURE_ITEM = ITEMS.register(
        "nexus_azure", () -> blockItem(NEXUS_AZURE));
    public static final DeferredHolder<Item, BlockItem> NEXUS_AZURE_SLAB_ITEM = ITEMS.register(
        "nexus_azure_slab", () -> blockItem(NEXUS_AZURE_SLAB));

    public static final DeferredHolder<Item, BlockItem> NEXUS_PLASMA_ITEM = ITEMS.register(
        "nexus_plasma", () -> blockItem(NEXUS_PLASMA));
    public static final DeferredHolder<Item, BlockItem> NEXUS_PLASMA_SLAB_ITEM = ITEMS.register(
        "nexus_plasma_slab", () -> blockItem(NEXUS_PLASMA_SLAB));

    public static final DeferredHolder<Item, BlockItem> NEXUS_SIGNAL_ITEM = ITEMS.register(
        "nexus_signal", () -> blockItem(NEXUS_SIGNAL));
    public static final DeferredHolder<Item, BlockItem> NEXUS_SIGNAL_SLAB_ITEM = ITEMS.register(
        "nexus_signal_slab", () -> blockItem(NEXUS_SIGNAL_SLAB));

    public static final DeferredHolder<Item, BlockItem> NEXUS_MATRIX_ITEM = ITEMS.register(
        "nexus_matrix", () -> blockItem(NEXUS_MATRIX));
    public static final DeferredHolder<Item, BlockItem> NEXUS_MATRIX_SLAB_ITEM = ITEMS.register(
        "nexus_matrix_slab", () -> blockItem(NEXUS_MATRIX_SLAB));

    public static final DeferredHolder<Item, BlockItem> NEXUS_ENERGY_ITEM = ITEMS.register(
        "nexus_energy", () -> blockItem(NEXUS_ENERGY));
    public static final DeferredHolder<Item, BlockItem> NEXUS_ENERGY_SLAB_ITEM = ITEMS.register(
        "nexus_energy_slab", () -> blockItem(NEXUS_ENERGY_SLAB));

    public static final DeferredHolder<Item, BlockItem> NEXUS_DATA_ITEM = ITEMS.register(
        "nexus_data", () -> blockItem(NEXUS_DATA));
    public static final DeferredHolder<Item, BlockItem> NEXUS_DATA_SLAB_ITEM = ITEMS.register(
        "nexus_data_slab", () -> blockItem(NEXUS_DATA_SLAB));

    public static final DeferredHolder<Item, BlockItem> NEXUS_CRYSTAL_ITEM = ITEMS.register(
        "nexus_crystal", () -> blockItem(NEXUS_CRYSTAL));
    public static final DeferredHolder<Item, BlockItem> NEXUS_CRYSTAL_SLAB_ITEM = ITEMS.register(
        "nexus_crystal_slab", () -> blockItem(NEXUS_CRYSTAL_SLAB));

    public static final DeferredHolder<Item, BlockItem> NEXUS_HOLO_ITEM = ITEMS.register(
        "nexus_hologram", () -> blockItem(NEXUS_HOLO));
    public static final DeferredHolder<Item, BlockItem> NEXUS_HOLO_SLAB_ITEM = ITEMS.register(
        "nexus_hologram_slab", () -> blockItem(NEXUS_HOLO_SLAB));

    public static final DeferredHolder<Item, BlockItem> NEXUS_REACTOR_ITEM = ITEMS.register(
        "nexus_reactor", () -> blockItem(NEXUS_REACTOR));
    public static final DeferredHolder<Item, BlockItem> NEXUS_REACTOR_SLAB_ITEM = ITEMS.register(
        "nexus_reactor_slab", () -> blockItem(NEXUS_REACTOR_SLAB));

    public static final DeferredHolder<Item, BlockItem> NEXUS_PULSE_ITEM = ITEMS.register(
        "nexus_pulse", () -> blockItem(NEXUS_PULSE));
    public static final DeferredHolder<Item, BlockItem> NEXUS_PULSE_SLAB_ITEM = ITEMS.register(
        "nexus_pulse_slab", () -> blockItem(NEXUS_PULSE_SLAB));

    public static final DeferredHolder<Item, BlockItem> NEXUS_GRID2_ITEM = ITEMS.register(
        "nexus_grid2", () -> blockItem(NEXUS_GRID2));
    public static final DeferredHolder<Item, BlockItem> NEXUS_GRID2_SLAB_ITEM = ITEMS.register(
        "nexus_grid2_slab", () -> blockItem(NEXUS_GRID2_SLAB));

    public static final DeferredHolder<Item, BlockItem> NEXUS_TECH_ITEM = ITEMS.register(
        "nexus_tech", () -> blockItem(NEXUS_TECH));
    public static final DeferredHolder<Item, BlockItem> NEXUS_TECH_SLAB_ITEM = ITEMS.register(
        "nexus_tech_slab", () -> blockItem(NEXUS_TECH_SLAB));

    public static final DeferredHolder<Item, BlockItem> NEXUS_STRIPES_ITEM = ITEMS.register(
        "nexus_stripes", () -> blockItem(NEXUS_STRIPES));
    public static final DeferredHolder<Item, BlockItem> NEXUS_STRIPES_SLAB_ITEM = ITEMS.register(
        "nexus_stripes_slab", () -> blockItem(NEXUS_STRIPES_SLAB));

    public static final DeferredHolder<Item, BlockItem> NEXUS_DISPLAY_ITEM = ITEMS.register(
        "nexus_display", () -> blockItem(NEXUS_DISPLAY));
    public static final DeferredHolder<Item, BlockItem> NEXUS_DISPLAY_SLAB_ITEM = ITEMS.register(
        "nexus_display_slab", () -> blockItem(NEXUS_DISPLAY_SLAB));

    public static final DeferredHolder<Item, BlockItem> NEXUS_NEON_ITEM = ITEMS.register(
        "nexus_neon", () -> blockItem(NEXUS_NEON));
    public static final DeferredHolder<Item, BlockItem> NEXUS_NEON_SLAB_ITEM = ITEMS.register(
        "nexus_neon_slab", () -> blockItem(NEXUS_NEON_SLAB));

    public static final DeferredHolder<Item, BlockItem> NEXUS_CARBON_ITEM = ITEMS.register(
        "nexus_carbon", () -> blockItem(NEXUS_CARBON));
    public static final DeferredHolder<Item, BlockItem> NEXUS_CARBON_SLAB_ITEM = ITEMS.register(
        "nexus_carbon_slab", () -> blockItem(NEXUS_CARBON_SLAB));

    public static final DeferredHolder<Item, BlockItem> NEXUS_QUANTUM_ITEM = ITEMS.register(
        "nexus_quantum", () -> blockItem(NEXUS_QUANTUM));
    public static final DeferredHolder<Item, BlockItem> NEXUS_QUANTUM_SLAB_ITEM = ITEMS.register(
        "nexus_quantum_slab", () -> blockItem(NEXUS_QUANTUM_SLAB));

    public static final DeferredHolder<Item, BlockItem> NEXUS_BINARY_ITEM = ITEMS.register(
        "nexus_binary", () -> blockItem(NEXUS_BINARY));
    public static final DeferredHolder<Item, BlockItem> NEXUS_BINARY_SLAB_ITEM = ITEMS.register(
        "nexus_binary_slab", () -> blockItem(NEXUS_BINARY_SLAB));

    public static final DeferredHolder<Item, BlockItem> NEXUS_STEEL_ITEM = ITEMS.register(
        "nexus_steel", () -> blockItem(NEXUS_STEEL));
    public static final DeferredHolder<Item, BlockItem> NEXUS_STEEL_SLAB_ITEM = ITEMS.register(
        "nexus_steel_slab", () -> blockItem(NEXUS_STEEL_SLAB));

    public static final DeferredHolder<Item, BlockItem> NEXUS_VOID_ITEM = ITEMS.register(
        "nexus_void", () -> blockItem(NEXUS_VOID));
    public static final DeferredHolder<Item, BlockItem> NEXUS_VOID_SLAB_ITEM = ITEMS.register(
        "nexus_void_slab", () -> blockItem(NEXUS_VOID_SLAB));

    public static final DeferredHolder<Item, BlockItem> NEXUS_GLOW_ITEM = ITEMS.register(
        "nexus_glow", () -> blockItem(NEXUS_GLOW));
    public static final DeferredHolder<Item, BlockItem> NEXUS_GLOW_SLAB_ITEM = ITEMS.register(
        "nexus_glow_slab", () -> blockItem(NEXUS_GLOW_SLAB));

    public static final DeferredHolder<Item, BlockItem> NEXUS_HAZARD_ITEM = ITEMS.register(
        "nexus_hazard", () -> blockItem(NEXUS_HAZARD));
    public static final DeferredHolder<Item, BlockItem> NEXUS_HAZARD_SLAB_ITEM = ITEMS.register(
        "nexus_hazard_slab", () -> blockItem(NEXUS_HAZARD_SLAB));

    public static final DeferredHolder<Item, BlockItem> NEXUS_IRIS_ITEM = ITEMS.register(
        "nexus_iris", () -> blockItem(NEXUS_IRIS));
    public static final DeferredHolder<Item, BlockItem> NEXUS_IRIS_SLAB_ITEM = ITEMS.register(
        "nexus_iris_slab", () -> blockItem(NEXUS_IRIS_SLAB));

    public static final DeferredHolder<Item, BlockItem> NEXUS_EMBER_ITEM = ITEMS.register(
        "nexus_ember", () -> blockItem(NEXUS_EMBER));
    public static final DeferredHolder<Item, BlockItem> NEXUS_EMBER_SLAB_ITEM = ITEMS.register(
        "nexus_ember_slab", () -> blockItem(NEXUS_EMBER_SLAB));

    // New block items (37-72)
    public static final DeferredHolder<Item, BlockItem> NEXUS_COBALT_ITEM = ITEMS.register("nexus_cobalt", () -> blockItem(NEXUS_COBALT));
    public static final DeferredHolder<Item, BlockItem> NEXUS_COBALT_SLAB_ITEM = ITEMS.register("nexus_cobalt_slab", () -> blockItem(NEXUS_COBALT_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_MINT_ITEM = ITEMS.register("nexus_mint", () -> blockItem(NEXUS_MINT));
    public static final DeferredHolder<Item, BlockItem> NEXUS_MINT_SLAB_ITEM = ITEMS.register("nexus_mint_slab", () -> blockItem(NEXUS_MINT_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_ROSE_ITEM = ITEMS.register("nexus_rose", () -> blockItem(NEXUS_ROSE));
    public static final DeferredHolder<Item, BlockItem> NEXUS_ROSE_SLAB_ITEM = ITEMS.register("nexus_rose_slab", () -> blockItem(NEXUS_ROSE_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_ONYX_ITEM = ITEMS.register("nexus_onyx", () -> blockItem(NEXUS_ONYX));
    public static final DeferredHolder<Item, BlockItem> NEXUS_ONYX_SLAB_ITEM = ITEMS.register("nexus_onyx_slab", () -> blockItem(NEXUS_ONYX_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_COPPER_ITEM = ITEMS.register("nexus_copper", () -> blockItem(NEXUS_COPPER));
    public static final DeferredHolder<Item, BlockItem> NEXUS_COPPER_SLAB_ITEM = ITEMS.register("nexus_copper_slab", () -> blockItem(NEXUS_COPPER_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_GOLD_ITEM = ITEMS.register("nexus_gold", () -> blockItem(NEXUS_GOLD));
    public static final DeferredHolder<Item, BlockItem> NEXUS_GOLD_SLAB_ITEM = ITEMS.register("nexus_gold_slab", () -> blockItem(NEXUS_GOLD_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_SILVER_ITEM = ITEMS.register("nexus_silver", () -> blockItem(NEXUS_SILVER));
    public static final DeferredHolder<Item, BlockItem> NEXUS_SILVER_SLAB_ITEM = ITEMS.register("nexus_silver_slab", () -> blockItem(NEXUS_SILVER_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_BRONZE_ITEM = ITEMS.register("nexus_bronze", () -> blockItem(NEXUS_BRONZE));
    public static final DeferredHolder<Item, BlockItem> NEXUS_BRONZE_SLAB_ITEM = ITEMS.register("nexus_bronze_slab", () -> blockItem(NEXUS_BRONZE_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_HEX_ITEM = ITEMS.register("nexus_hex", () -> blockItem(NEXUS_HEX));
    public static final DeferredHolder<Item, BlockItem> NEXUS_HEX_SLAB_ITEM = ITEMS.register("nexus_hex_slab", () -> blockItem(NEXUS_HEX_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_DOTS_ITEM = ITEMS.register("nexus_dots", () -> blockItem(NEXUS_DOTS));
    public static final DeferredHolder<Item, BlockItem> NEXUS_DOTS_SLAB_ITEM = ITEMS.register("nexus_dots_slab", () -> blockItem(NEXUS_DOTS_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_WAVE_ITEM = ITEMS.register("nexus_wave", () -> blockItem(NEXUS_WAVE));
    public static final DeferredHolder<Item, BlockItem> NEXUS_WAVE_SLAB_ITEM = ITEMS.register("nexus_wave_slab", () -> blockItem(NEXUS_WAVE_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_CHECKER_ITEM = ITEMS.register("nexus_checker", () -> blockItem(NEXUS_CHECKER));
    public static final DeferredHolder<Item, BlockItem> NEXUS_CHECKER_SLAB_ITEM = ITEMS.register("nexus_checker_slab", () -> blockItem(NEXUS_CHECKER_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_DIAMOND_ITEM = ITEMS.register("nexus_diamond", () -> blockItem(NEXUS_DIAMOND));
    public static final DeferredHolder<Item, BlockItem> NEXUS_DIAMOND_SLAB_ITEM = ITEMS.register("nexus_diamond_slab", () -> blockItem(NEXUS_DIAMOND_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_CROSS_ITEM = ITEMS.register("nexus_cross", () -> blockItem(NEXUS_CROSS));
    public static final DeferredHolder<Item, BlockItem> NEXUS_CROSS_SLAB_ITEM = ITEMS.register("nexus_cross_slab", () -> blockItem(NEXUS_CROSS_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_SPIRAL_ITEM = ITEMS.register("nexus_spiral", () -> blockItem(NEXUS_SPIRAL));
    public static final DeferredHolder<Item, BlockItem> NEXUS_SPIRAL_SLAB_ITEM = ITEMS.register("nexus_spiral_slab", () -> blockItem(NEXUS_SPIRAL_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_BRICK_ITEM = ITEMS.register("nexus_brick", () -> blockItem(NEXUS_BRICK));
    public static final DeferredHolder<Item, BlockItem> NEXUS_BRICK_SLAB_ITEM = ITEMS.register("nexus_brick_slab", () -> blockItem(NEXUS_BRICK_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_SCALES_ITEM = ITEMS.register("nexus_scales", () -> blockItem(NEXUS_SCALES));
    public static final DeferredHolder<Item, BlockItem> NEXUS_SCALES_SLAB_ITEM = ITEMS.register("nexus_scales_slab", () -> blockItem(NEXUS_SCALES_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_MESH_ITEM = ITEMS.register("nexus_mesh", () -> blockItem(NEXUS_MESH));
    public static final DeferredHolder<Item, BlockItem> NEXUS_MESH_SLAB_ITEM = ITEMS.register("nexus_mesh_slab", () -> blockItem(NEXUS_MESH_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_AZURE_LIGHT_ITEM = ITEMS.register("nexus_azure_light", () -> blockItem(NEXUS_AZURE_LIGHT));
    public static final DeferredHolder<Item, BlockItem> NEXUS_AZURE_LIGHT_SLAB_ITEM = ITEMS.register("nexus_azure_light_slab", () -> blockItem(NEXUS_AZURE_LIGHT_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_AZURE_DARK_ITEM = ITEMS.register("nexus_azure_dark", () -> blockItem(NEXUS_AZURE_DARK));
    public static final DeferredHolder<Item, BlockItem> NEXUS_AZURE_DARK_SLAB_ITEM = ITEMS.register("nexus_azure_dark_slab", () -> blockItem(NEXUS_AZURE_DARK_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_PLASMA_LIGHT_ITEM = ITEMS.register("nexus_plasma_light", () -> blockItem(NEXUS_PLASMA_LIGHT));
    public static final DeferredHolder<Item, BlockItem> NEXUS_PLASMA_LIGHT_SLAB_ITEM = ITEMS.register("nexus_plasma_light_slab", () -> blockItem(NEXUS_PLASMA_LIGHT_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_PLASMA_DARK_ITEM = ITEMS.register("nexus_plasma_dark", () -> blockItem(NEXUS_PLASMA_DARK));
    public static final DeferredHolder<Item, BlockItem> NEXUS_PLASMA_DARK_SLAB_ITEM = ITEMS.register("nexus_plasma_dark_slab", () -> blockItem(NEXUS_PLASMA_DARK_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_MATRIX_LIGHT_ITEM = ITEMS.register("nexus_matrix_light", () -> blockItem(NEXUS_MATRIX_LIGHT));
    public static final DeferredHolder<Item, BlockItem> NEXUS_MATRIX_LIGHT_SLAB_ITEM = ITEMS.register("nexus_matrix_light_slab", () -> blockItem(NEXUS_MATRIX_LIGHT_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_ENERGY_LIGHT_ITEM = ITEMS.register("nexus_energy_light", () -> blockItem(NEXUS_ENERGY_LIGHT));
    public static final DeferredHolder<Item, BlockItem> NEXUS_ENERGY_LIGHT_SLAB_ITEM = ITEMS.register("nexus_energy_light_slab", () -> blockItem(NEXUS_ENERGY_LIGHT_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_REACTOR_LIGHT_ITEM = ITEMS.register("nexus_reactor_light", () -> blockItem(NEXUS_REACTOR_LIGHT));
    public static final DeferredHolder<Item, BlockItem> NEXUS_REACTOR_LIGHT_SLAB_ITEM = ITEMS.register("nexus_reactor_light_slab", () -> blockItem(NEXUS_REACTOR_LIGHT_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_CRYSTAL_LIGHT_ITEM = ITEMS.register("nexus_crystal_light", () -> blockItem(NEXUS_CRYSTAL_LIGHT));
    public static final DeferredHolder<Item, BlockItem> NEXUS_CRYSTAL_LIGHT_SLAB_ITEM = ITEMS.register("nexus_crystal_light_slab", () -> blockItem(NEXUS_CRYSTAL_LIGHT_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_NEON_BLUE_ITEM = ITEMS.register("nexus_neon_blue", () -> blockItem(NEXUS_NEON_BLUE));
    public static final DeferredHolder<Item, BlockItem> NEXUS_NEON_BLUE_SLAB_ITEM = ITEMS.register("nexus_neon_blue_slab", () -> blockItem(NEXUS_NEON_BLUE_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_CIRCUIT_GOLD_ITEM = ITEMS.register("nexus_circuit_gold", () -> blockItem(NEXUS_CIRCUIT_GOLD));
    public static final DeferredHolder<Item, BlockItem> NEXUS_CIRCUIT_GOLD_SLAB_ITEM = ITEMS.register("nexus_circuit_gold_slab", () -> blockItem(NEXUS_CIRCUIT_GOLD_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_PANEL_DARK_ITEM = ITEMS.register("nexus_panel_dark", () -> blockItem(NEXUS_PANEL_DARK));
    public static final DeferredHolder<Item, BlockItem> NEXUS_PANEL_DARK_SLAB_ITEM = ITEMS.register("nexus_panel_dark_slab", () -> blockItem(NEXUS_PANEL_DARK_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_GRID_GOLD_ITEM = ITEMS.register("nexus_grid_gold", () -> blockItem(NEXUS_GRID_GOLD));
    public static final DeferredHolder<Item, BlockItem> NEXUS_GRID_GOLD_SLAB_ITEM = ITEMS.register("nexus_grid_gold_slab", () -> blockItem(NEXUS_GRID_GOLD_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_VENT_RED_ITEM = ITEMS.register("nexus_vent_red", () -> blockItem(NEXUS_VENT_RED));
    public static final DeferredHolder<Item, BlockItem> NEXUS_VENT_RED_SLAB_ITEM = ITEMS.register("nexus_vent_red_slab", () -> blockItem(NEXUS_VENT_RED_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_TERMINAL_GREEN_ITEM = ITEMS.register("nexus_terminal_green", () -> blockItem(NEXUS_TERMINAL_GREEN));
    public static final DeferredHolder<Item, BlockItem> NEXUS_TERMINAL_GREEN_SLAB_ITEM = ITEMS.register("nexus_terminal_green_slab", () -> blockItem(NEXUS_TERMINAL_GREEN_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_DISPLAY_RED_ITEM = ITEMS.register("nexus_display_red", () -> blockItem(NEXUS_DISPLAY_RED));
    public static final DeferredHolder<Item, BlockItem> NEXUS_DISPLAY_RED_SLAB_ITEM = ITEMS.register("nexus_display_red_slab", () -> blockItem(NEXUS_DISPLAY_RED_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_CONDUIT_YELLOW_ITEM = ITEMS.register("nexus_conduit_yellow", () -> blockItem(NEXUS_CONDUIT_YELLOW));
    public static final DeferredHolder<Item, BlockItem> NEXUS_CONDUIT_YELLOW_SLAB_ITEM = ITEMS.register("nexus_conduit_yellow_slab", () -> blockItem(NEXUS_CONDUIT_YELLOW_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_FRAME_WHITE_ITEM = ITEMS.register("nexus_frame_white", () -> blockItem(NEXUS_FRAME_WHITE));
    public static final DeferredHolder<Item, BlockItem> NEXUS_FRAME_WHITE_SLAB_ITEM = ITEMS.register("nexus_frame_white_slab", () -> blockItem(NEXUS_FRAME_WHITE_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_TILE_PURPLE_ITEM = ITEMS.register("nexus_tile_purple", () -> blockItem(NEXUS_TILE_PURPLE));
    public static final DeferredHolder<Item, BlockItem> NEXUS_TILE_PURPLE_SLAB_ITEM = ITEMS.register("nexus_tile_purple_slab", () -> blockItem(NEXUS_TILE_PURPLE_SLAB));

    // Seamless block items
    public static final DeferredHolder<Item, BlockItem> NEXUS_WHITE_PURE_ITEM = ITEMS.register("nexus_white_pure", () -> blockItem(NEXUS_WHITE_PURE));
    public static final DeferredHolder<Item, BlockItem> NEXUS_WHITE_PURE_SLAB_ITEM = ITEMS.register("nexus_white_pure_slab", () -> blockItem(NEXUS_WHITE_PURE_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_LIGHT_SEAMLESS_ITEM = ITEMS.register("nexus_light_seamless", () -> blockItem(NEXUS_LIGHT_SEAMLESS));
    public static final DeferredHolder<Item, BlockItem> NEXUS_LIGHT_SEAMLESS_SLAB_ITEM = ITEMS.register("nexus_light_seamless_slab", () -> blockItem(NEXUS_LIGHT_SEAMLESS_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_GRAY_SEAMLESS_ITEM = ITEMS.register("nexus_gray_seamless", () -> blockItem(NEXUS_GRAY_SEAMLESS));
    public static final DeferredHolder<Item, BlockItem> NEXUS_GRAY_SEAMLESS_SLAB_ITEM = ITEMS.register("nexus_gray_seamless_slab", () -> blockItem(NEXUS_GRAY_SEAMLESS_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_DARK_SEAMLESS_ITEM = ITEMS.register("nexus_dark_seamless", () -> blockItem(NEXUS_DARK_SEAMLESS));
    public static final DeferredHolder<Item, BlockItem> NEXUS_DARK_SEAMLESS_SLAB_ITEM = ITEMS.register("nexus_dark_seamless_slab", () -> blockItem(NEXUS_DARK_SEAMLESS_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_WHITE_GRID_ITEM = ITEMS.register("nexus_white_grid", () -> blockItem(NEXUS_WHITE_GRID));
    public static final DeferredHolder<Item, BlockItem> NEXUS_WHITE_GRID_SLAB_ITEM = ITEMS.register("nexus_white_grid_slab", () -> blockItem(NEXUS_WHITE_GRID_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_CYAN_GLOW_ITEM = ITEMS.register("nexus_cyan_glow", () -> blockItem(NEXUS_CYAN_GLOW));
    public static final DeferredHolder<Item, BlockItem> NEXUS_CYAN_GLOW_SLAB_ITEM = ITEMS.register("nexus_cyan_glow_slab", () -> blockItem(NEXUS_CYAN_GLOW_SLAB));
    public static final DeferredHolder<Item, BlockItem> NEXUS_STEEL_SEAMLESS_ITEM = ITEMS.register("nexus_steel_seamless", () -> blockItem(NEXUS_STEEL_SEAMLESS));
    public static final DeferredHolder<Item, BlockItem> NEXUS_STEEL_SEAMLESS_SLAB_ITEM = ITEMS.register("nexus_steel_seamless_slab", () -> blockItem(NEXUS_STEEL_SEAMLESS_SLAB));

    // Hub specialty block items
    public static final DeferredHolder<Item, BlockItem> NEXUS_GLASS_CYAN_ITEM = ITEMS.register("nexus_glass_cyan", () -> blockItem(NEXUS_GLASS_CYAN));
    public static final DeferredHolder<Item, BlockItem> NEXUS_GLASS_WHITE_ITEM = ITEMS.register("nexus_glass_white", () -> blockItem(NEXUS_GLASS_WHITE));
    public static final DeferredHolder<Item, BlockItem> NEXUS_GLASS_DARK_ITEM = ITEMS.register("nexus_glass_dark", () -> blockItem(NEXUS_GLASS_DARK));
    public static final DeferredHolder<Item, BlockItem> NEXUS_GLOW_STRIP_CYAN_ITEM = ITEMS.register("nexus_glow_strip_cyan", () -> blockItem(NEXUS_GLOW_STRIP_CYAN));
    public static final DeferredHolder<Item, BlockItem> NEXUS_GLOW_STRIP_WHITE_ITEM = ITEMS.register("nexus_glow_strip_white", () -> blockItem(NEXUS_GLOW_STRIP_WHITE));
    public static final DeferredHolder<Item, BlockItem> NEXUS_FLOOR_LIGHT_ITEM = ITEMS.register("nexus_floor_light", () -> blockItem(NEXUS_FLOOR_LIGHT));
    public static final DeferredHolder<Item, BlockItem> NEXUS_FLOOR_LIGHT_CYAN_ITEM = ITEMS.register("nexus_floor_light_cyan", () -> blockItem(NEXUS_FLOOR_LIGHT_CYAN));
    public static final DeferredHolder<Item, BlockItem> NEXUS_TELEPAD_CORE_ITEM = ITEMS.register("nexus_telepad_core", () -> blockItem(NEXUS_TELEPAD_CORE));
    public static final DeferredHolder<Item, BlockItem> NEXUS_TELEPAD_RING_ITEM = ITEMS.register("nexus_telepad_ring", () -> blockItem(NEXUS_TELEPAD_RING));
    public static final DeferredHolder<Item, BlockItem> NEXUS_RING_PANEL_ITEM = ITEMS.register("nexus_ring_panel", () -> blockItem(NEXUS_RING_PANEL));
    public static final DeferredHolder<Item, BlockItem> NEXUS_RING_ACCENT_ITEM = ITEMS.register("nexus_ring_accent", () -> blockItem(NEXUS_RING_ACCENT));
    public static final DeferredHolder<Item, BlockItem> NEXUS_CENTER_FLOOR_ITEM = ITEMS.register("nexus_center_floor", () -> blockItem(NEXUS_CENTER_FLOOR));

    // Stair block items
    public static final DeferredHolder<Item, BlockItem> NEXUS_WHITE_PURE_STAIRS_ITEM = ITEMS.register("nexus_white_pure_stairs", () -> blockItem(NEXUS_WHITE_PURE_STAIRS));
    public static final DeferredHolder<Item, BlockItem> NEXUS_LIGHT_SEAMLESS_STAIRS_ITEM = ITEMS.register("nexus_light_seamless_stairs", () -> blockItem(NEXUS_LIGHT_SEAMLESS_STAIRS));
    public static final DeferredHolder<Item, BlockItem> NEXUS_GRAY_SEAMLESS_STAIRS_ITEM = ITEMS.register("nexus_gray_seamless_stairs", () -> blockItem(NEXUS_GRAY_SEAMLESS_STAIRS));
    public static final DeferredHolder<Item, BlockItem> NEXUS_DARK_SEAMLESS_STAIRS_ITEM = ITEMS.register("nexus_dark_seamless_stairs", () -> blockItem(NEXUS_DARK_SEAMLESS_STAIRS));
    public static final DeferredHolder<Item, BlockItem> NEXUS_STEEL_SEAMLESS_STAIRS_ITEM = ITEMS.register("nexus_steel_seamless_stairs", () -> blockItem(NEXUS_STEEL_SEAMLESS_STAIRS));
    public static final DeferredHolder<Item, BlockItem> NEXUS_CYAN_GLOW_STAIRS_ITEM = ITEMS.register("nexus_cyan_glow_stairs", () -> blockItem(NEXUS_CYAN_GLOW_STAIRS));

    // Colored corridor stairs items
    public static final DeferredHolder<Item, BlockItem> NEXUS_NEON_BLUE_STAIRS_ITEM = ITEMS.register("nexus_neon_blue_stairs", () -> blockItem(NEXUS_NEON_BLUE_STAIRS));
    public static final DeferredHolder<Item, BlockItem> NEXUS_CIRCUIT_GOLD_STAIRS_ITEM = ITEMS.register("nexus_circuit_gold_stairs", () -> blockItem(NEXUS_CIRCUIT_GOLD_STAIRS));
    public static final DeferredHolder<Item, BlockItem> NEXUS_CIRCUIT_STAIRS_ITEM = ITEMS.register("nexus_circuit_stairs", () -> blockItem(NEXUS_CIRCUIT_STAIRS));
    public static final DeferredHolder<Item, BlockItem> NEXUS_EMBER_STAIRS_ITEM = ITEMS.register("nexus_ember_stairs", () -> blockItem(NEXUS_EMBER_STAIRS));
    public static final DeferredHolder<Item, BlockItem> NEXUS_TILE_PURPLE_STAIRS_ITEM = ITEMS.register("nexus_tile_purple_stairs", () -> blockItem(NEXUS_TILE_PURPLE_STAIRS));
    public static final DeferredHolder<Item, BlockItem> NEXUS_CONDUIT_YELLOW_STAIRS_ITEM = ITEMS.register("nexus_conduit_yellow_stairs", () -> blockItem(NEXUS_CONDUIT_YELLOW_STAIRS));
    public static final DeferredHolder<Item, BlockItem> NEXUS_CONDUIT_STAIRS_ITEM = ITEMS.register("nexus_conduit_stairs", () -> blockItem(NEXUS_CONDUIT_STAIRS));
    public static final DeferredHolder<Item, BlockItem> NEXUS_WHITE_GRID_STAIRS_ITEM = ITEMS.register("nexus_white_grid_stairs", () -> blockItem(NEXUS_WHITE_GRID_STAIRS));

    // === CREATIVE TAB ===
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> NEXUS_TAB = CREATIVE_TABS.register(
        "nexus_blocks", () -> CreativeModeTab.builder()
            .title(Objects.requireNonNull(Component.translatable("itemGroup.devmod.nexus_blocks")))
            .icon(() -> new ItemStack(Objects.requireNonNull(NEXUS_PANEL.get())))
            .displayItems((params, output) -> {
                // Original blocks (1-12)
                acceptItem(output, NEXUS_PANEL_ITEM);
                acceptItem(output, NEXUS_TILE_ITEM);
                acceptItem(output, NEXUS_GRID_ITEM);
                acceptItem(output, NEXUS_PLATING_ITEM);
                acceptItem(output, NEXUS_CORE_ITEM);
                acceptItem(output, NEXUS_FRAME_ITEM);
                acceptItem(output, NEXUS_CONDUIT_ITEM);
                acceptItem(output, NEXUS_TERMINAL_ITEM);
                acceptItem(output, NEXUS_VENT_ITEM);
                acceptItem(output, NEXUS_CIRCUIT_ITEM);
                acceptItem(output, NEXUS_SMOOTH_ITEM);
                acceptItem(output, NEXUS_LIGHT_ITEM);
                // Bold blocks (13-36)
                acceptItem(output, NEXUS_AZURE_ITEM);
                acceptItem(output, NEXUS_PLASMA_ITEM);
                acceptItem(output, NEXUS_SIGNAL_ITEM);
                acceptItem(output, NEXUS_MATRIX_ITEM);
                acceptItem(output, NEXUS_ENERGY_ITEM);
                acceptItem(output, NEXUS_DATA_ITEM);
                acceptItem(output, NEXUS_CRYSTAL_ITEM);
                acceptItem(output, NEXUS_HOLO_ITEM);
                acceptItem(output, NEXUS_REACTOR_ITEM);
                acceptItem(output, NEXUS_PULSE_ITEM);
                acceptItem(output, NEXUS_GRID2_ITEM);
                acceptItem(output, NEXUS_TECH_ITEM);
                acceptItem(output, NEXUS_STRIPES_ITEM);
                acceptItem(output, NEXUS_DISPLAY_ITEM);
                acceptItem(output, NEXUS_NEON_ITEM);
                acceptItem(output, NEXUS_CARBON_ITEM);
                acceptItem(output, NEXUS_QUANTUM_ITEM);
                acceptItem(output, NEXUS_BINARY_ITEM);
                acceptItem(output, NEXUS_STEEL_ITEM);
                acceptItem(output, NEXUS_VOID_ITEM);
                acceptItem(output, NEXUS_GLOW_ITEM);
                acceptItem(output, NEXUS_HAZARD_ITEM);
                acceptItem(output, NEXUS_IRIS_ITEM);
                acceptItem(output, NEXUS_EMBER_ITEM);
                // New blocks (37-72)
                acceptItem(output, NEXUS_COBALT_ITEM);
                acceptItem(output, NEXUS_MINT_ITEM);
                acceptItem(output, NEXUS_ROSE_ITEM);
                acceptItem(output, NEXUS_ONYX_ITEM);
                acceptItem(output, NEXUS_COPPER_ITEM);
                acceptItem(output, NEXUS_GOLD_ITEM);
                acceptItem(output, NEXUS_SILVER_ITEM);
                acceptItem(output, NEXUS_BRONZE_ITEM);
                acceptItem(output, NEXUS_HEX_ITEM);
                acceptItem(output, NEXUS_DOTS_ITEM);
                acceptItem(output, NEXUS_WAVE_ITEM);
                acceptItem(output, NEXUS_CHECKER_ITEM);
                acceptItem(output, NEXUS_DIAMOND_ITEM);
                acceptItem(output, NEXUS_CROSS_ITEM);
                acceptItem(output, NEXUS_SPIRAL_ITEM);
                acceptItem(output, NEXUS_BRICK_ITEM);
                acceptItem(output, NEXUS_SCALES_ITEM);
                acceptItem(output, NEXUS_MESH_ITEM);
                acceptItem(output, NEXUS_AZURE_LIGHT_ITEM);
                acceptItem(output, NEXUS_AZURE_DARK_ITEM);
                acceptItem(output, NEXUS_PLASMA_LIGHT_ITEM);
                acceptItem(output, NEXUS_PLASMA_DARK_ITEM);
                acceptItem(output, NEXUS_MATRIX_LIGHT_ITEM);
                acceptItem(output, NEXUS_ENERGY_LIGHT_ITEM);
                acceptItem(output, NEXUS_REACTOR_LIGHT_ITEM);
                acceptItem(output, NEXUS_CRYSTAL_LIGHT_ITEM);
                acceptItem(output, NEXUS_NEON_BLUE_ITEM);
                acceptItem(output, NEXUS_CIRCUIT_GOLD_ITEM);
                acceptItem(output, NEXUS_PANEL_DARK_ITEM);
                acceptItem(output, NEXUS_GRID_GOLD_ITEM);
                acceptItem(output, NEXUS_VENT_RED_ITEM);
                acceptItem(output, NEXUS_TERMINAL_GREEN_ITEM);
                acceptItem(output, NEXUS_DISPLAY_RED_ITEM);
                acceptItem(output, NEXUS_CONDUIT_YELLOW_ITEM);
                acceptItem(output, NEXUS_FRAME_WHITE_ITEM);
                acceptItem(output, NEXUS_TILE_PURPLE_ITEM);
                // Seamless blocks
                acceptItem(output, NEXUS_WHITE_PURE_ITEM);
                acceptItem(output, NEXUS_LIGHT_SEAMLESS_ITEM);
                acceptItem(output, NEXUS_GRAY_SEAMLESS_ITEM);
                acceptItem(output, NEXUS_DARK_SEAMLESS_ITEM);
                acceptItem(output, NEXUS_WHITE_GRID_ITEM);
                acceptItem(output, NEXUS_CYAN_GLOW_ITEM);
                acceptItem(output, NEXUS_STEEL_SEAMLESS_ITEM);
                // Hub specialty blocks
                acceptItem(output, NEXUS_GLASS_CYAN_ITEM);
                acceptItem(output, NEXUS_GLASS_WHITE_ITEM);
                acceptItem(output, NEXUS_GLASS_DARK_ITEM);
                acceptItem(output, NEXUS_GLOW_STRIP_CYAN_ITEM);
                acceptItem(output, NEXUS_GLOW_STRIP_WHITE_ITEM);
                acceptItem(output, NEXUS_FLOOR_LIGHT_ITEM);
                acceptItem(output, NEXUS_FLOOR_LIGHT_CYAN_ITEM);
                acceptItem(output, NEXUS_TELEPAD_CORE_ITEM);
                acceptItem(output, NEXUS_TELEPAD_RING_ITEM);
                acceptItem(output, NEXUS_RING_PANEL_ITEM);
                acceptItem(output, NEXUS_RING_ACCENT_ITEM);
                acceptItem(output, NEXUS_CENTER_FLOOR_ITEM);
                // Original slabs
                acceptItem(output, NEXUS_PANEL_SLAB_ITEM);
                acceptItem(output, NEXUS_TILE_SLAB_ITEM);
                acceptItem(output, NEXUS_GRID_SLAB_ITEM);
                acceptItem(output, NEXUS_PLATING_SLAB_ITEM);
                acceptItem(output, NEXUS_CORE_SLAB_ITEM);
                acceptItem(output, NEXUS_FRAME_SLAB_ITEM);
                acceptItem(output, NEXUS_CONDUIT_SLAB_ITEM);
                acceptItem(output, NEXUS_TERMINAL_SLAB_ITEM);
                acceptItem(output, NEXUS_VENT_SLAB_ITEM);
                acceptItem(output, NEXUS_CIRCUIT_SLAB_ITEM);
                acceptItem(output, NEXUS_SMOOTH_SLAB_ITEM);
                acceptItem(output, NEXUS_LIGHT_SLAB_ITEM);
                // Bold slabs
                acceptItem(output, NEXUS_AZURE_SLAB_ITEM);
                acceptItem(output, NEXUS_PLASMA_SLAB_ITEM);
                acceptItem(output, NEXUS_SIGNAL_SLAB_ITEM);
                acceptItem(output, NEXUS_MATRIX_SLAB_ITEM);
                acceptItem(output, NEXUS_ENERGY_SLAB_ITEM);
                acceptItem(output, NEXUS_DATA_SLAB_ITEM);
                acceptItem(output, NEXUS_CRYSTAL_SLAB_ITEM);
                acceptItem(output, NEXUS_HOLO_SLAB_ITEM);
                acceptItem(output, NEXUS_REACTOR_SLAB_ITEM);
                acceptItem(output, NEXUS_PULSE_SLAB_ITEM);
                acceptItem(output, NEXUS_GRID2_SLAB_ITEM);
                acceptItem(output, NEXUS_TECH_SLAB_ITEM);
                acceptItem(output, NEXUS_STRIPES_SLAB_ITEM);
                acceptItem(output, NEXUS_DISPLAY_SLAB_ITEM);
                acceptItem(output, NEXUS_NEON_SLAB_ITEM);
                acceptItem(output, NEXUS_CARBON_SLAB_ITEM);
                acceptItem(output, NEXUS_QUANTUM_SLAB_ITEM);
                acceptItem(output, NEXUS_BINARY_SLAB_ITEM);
                acceptItem(output, NEXUS_STEEL_SLAB_ITEM);
                acceptItem(output, NEXUS_VOID_SLAB_ITEM);
                acceptItem(output, NEXUS_GLOW_SLAB_ITEM);
                acceptItem(output, NEXUS_HAZARD_SLAB_ITEM);
                acceptItem(output, NEXUS_IRIS_SLAB_ITEM);
                acceptItem(output, NEXUS_EMBER_SLAB_ITEM);
                // New slabs (37-72)
                acceptItem(output, NEXUS_COBALT_SLAB_ITEM);
                acceptItem(output, NEXUS_MINT_SLAB_ITEM);
                acceptItem(output, NEXUS_ROSE_SLAB_ITEM);
                acceptItem(output, NEXUS_ONYX_SLAB_ITEM);
                acceptItem(output, NEXUS_COPPER_SLAB_ITEM);
                acceptItem(output, NEXUS_GOLD_SLAB_ITEM);
                acceptItem(output, NEXUS_SILVER_SLAB_ITEM);
                acceptItem(output, NEXUS_BRONZE_SLAB_ITEM);
                acceptItem(output, NEXUS_HEX_SLAB_ITEM);
                acceptItem(output, NEXUS_DOTS_SLAB_ITEM);
                acceptItem(output, NEXUS_WAVE_SLAB_ITEM);
                acceptItem(output, NEXUS_CHECKER_SLAB_ITEM);
                acceptItem(output, NEXUS_DIAMOND_SLAB_ITEM);
                acceptItem(output, NEXUS_CROSS_SLAB_ITEM);
                acceptItem(output, NEXUS_SPIRAL_SLAB_ITEM);
                acceptItem(output, NEXUS_BRICK_SLAB_ITEM);
                acceptItem(output, NEXUS_SCALES_SLAB_ITEM);
                acceptItem(output, NEXUS_MESH_SLAB_ITEM);
                acceptItem(output, NEXUS_AZURE_LIGHT_SLAB_ITEM);
                acceptItem(output, NEXUS_AZURE_DARK_SLAB_ITEM);
                acceptItem(output, NEXUS_PLASMA_LIGHT_SLAB_ITEM);
                acceptItem(output, NEXUS_PLASMA_DARK_SLAB_ITEM);
                acceptItem(output, NEXUS_MATRIX_LIGHT_SLAB_ITEM);
                acceptItem(output, NEXUS_ENERGY_LIGHT_SLAB_ITEM);
                acceptItem(output, NEXUS_REACTOR_LIGHT_SLAB_ITEM);
                acceptItem(output, NEXUS_CRYSTAL_LIGHT_SLAB_ITEM);
                acceptItem(output, NEXUS_NEON_BLUE_SLAB_ITEM);
                acceptItem(output, NEXUS_CIRCUIT_GOLD_SLAB_ITEM);
                acceptItem(output, NEXUS_PANEL_DARK_SLAB_ITEM);
                acceptItem(output, NEXUS_GRID_GOLD_SLAB_ITEM);
                acceptItem(output, NEXUS_VENT_RED_SLAB_ITEM);
                acceptItem(output, NEXUS_TERMINAL_GREEN_SLAB_ITEM);
                acceptItem(output, NEXUS_DISPLAY_RED_SLAB_ITEM);
                acceptItem(output, NEXUS_CONDUIT_YELLOW_SLAB_ITEM);
                acceptItem(output, NEXUS_FRAME_WHITE_SLAB_ITEM);
                acceptItem(output, NEXUS_TILE_PURPLE_SLAB_ITEM);
                // Seamless slabs
                acceptItem(output, NEXUS_WHITE_PURE_SLAB_ITEM);
                acceptItem(output, NEXUS_LIGHT_SEAMLESS_SLAB_ITEM);
                acceptItem(output, NEXUS_GRAY_SEAMLESS_SLAB_ITEM);
                acceptItem(output, NEXUS_DARK_SEAMLESS_SLAB_ITEM);
                acceptItem(output, NEXUS_WHITE_GRID_SLAB_ITEM);
                acceptItem(output, NEXUS_CYAN_GLOW_SLAB_ITEM);
                acceptItem(output, NEXUS_STEEL_SEAMLESS_SLAB_ITEM);
                // Stairs (for smooth transitions)
                acceptItem(output, NEXUS_WHITE_PURE_STAIRS_ITEM);
                acceptItem(output, NEXUS_LIGHT_SEAMLESS_STAIRS_ITEM);
                acceptItem(output, NEXUS_GRAY_SEAMLESS_STAIRS_ITEM);
                acceptItem(output, NEXUS_DARK_SEAMLESS_STAIRS_ITEM);
                acceptItem(output, NEXUS_STEEL_SEAMLESS_STAIRS_ITEM);
                acceptItem(output, NEXUS_CYAN_GLOW_STAIRS_ITEM);
                // Colored corridor stairs
                acceptItem(output, NEXUS_NEON_BLUE_STAIRS_ITEM);
                acceptItem(output, NEXUS_CIRCUIT_GOLD_STAIRS_ITEM);
                acceptItem(output, NEXUS_CIRCUIT_STAIRS_ITEM);
                acceptItem(output, NEXUS_EMBER_STAIRS_ITEM);
                acceptItem(output, NEXUS_TILE_PURPLE_STAIRS_ITEM);
                acceptItem(output, NEXUS_CONDUIT_YELLOW_STAIRS_ITEM);
                acceptItem(output, NEXUS_CONDUIT_STAIRS_ITEM);
                acceptItem(output, NEXUS_WHITE_GRID_STAIRS_ITEM);
            })
            .build()
    );

    /**
     * Register blocks and items on the mod event bus.
     */
    public static void register(IEventBus eventBus) {
        BLOCKS.register(Objects.requireNonNull(eventBus));
        ITEMS.register(Objects.requireNonNull(eventBus));
        CREATIVE_TABS.register(Objects.requireNonNull(eventBus));
        DevMod.LOGGER.info("[Nexus] Decorative blocks registered");
    }
}
