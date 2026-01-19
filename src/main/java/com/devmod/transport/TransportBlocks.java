package com.devmod.transport;

import java.util.Objects;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.neoforged.neoforge.registries.DeferredHolder;

import com.devmod.DevMod;
import com.devmod.transport.block.ChromaticTransportModuleBlock;
import com.devmod.transport.block.TransportCoreBlock;
import com.devmod.transport.block.TransportFrameBlock;
import com.devmod.transport.block.TransportModuleBlock;

/**
 * Transport module block registrations.
 * Registers all transport-related blocks (Warp Core system).
 *
 * <p>Block types:
 * <ul>
 *   <li>WARP_CORE - Main transport node (Warp Core)</li>
 *   <li>Module blocks - Enhancement modules (Chromatic Lens, Range Amplifier, etc.)</li>
 *   <li>FRAME_SEGMENT - Visual portal frame segments</li>
 * </ul>
 */
public final class TransportBlocks {
    private TransportBlocks() {}

    // === Warp Core ===

    /**
     * The Warp Core block - main transport node.
     * Players stand on it to charge and teleport.
     */
    public static final DeferredHolder<Block, TransportCoreBlock> WARP_CORE = DevMod.BLOCKS.register(
        "warp_core",
        () -> new TransportCoreBlock(BlockBehaviour.Properties.of()
            .strength(3.0F, 8.0F)
            .sound(Objects.requireNonNull(SoundType.METAL))
            .lightLevel(TransportCoreBlock::getLightEmission)
            .noOcclusion())
    );

    // === Module Blocks ===

    /**
     * Chromatic Lens module - allows color customization.
     */
    public static final DeferredHolder<Block, TransportModuleBlock> CHROMATIC_LENS = DevMod.BLOCKS.register(
        "chromatic_lens",
        () -> new ChromaticTransportModuleBlock(BlockBehaviour.Properties.of()
            .strength(2.0F, 6.0F)
            .sound(Objects.requireNonNull(SoundType.GLASS))
            .lightLevel(TransportModuleBlock::getLightEmission)
            .noOcclusion())
    );

    /**
     * Range Amplifier module - increases teleport range by 100 blocks.
     */
    public static final DeferredHolder<Block, TransportModuleBlock> RANGE_AMPLIFIER = DevMod.BLOCKS.register(
        "range_amplifier",
        () -> new TransportModuleBlock(TransportEnhancement.RANGE_AMPLIFIER, BlockBehaviour.Properties.of()
            .strength(2.0F, 6.0F)
            .sound(Objects.requireNonNull(SoundType.METAL))
            .lightLevel(TransportModuleBlock::getLightEmission)
            .noOcclusion())
    );

    /**
     * Dimensional Gate module - enables cross-dimensional transport.
     */
    public static final DeferredHolder<Block, TransportModuleBlock> DIMENSIONAL_GATE = DevMod.BLOCKS.register(
        "dimensional_gate",
        () -> new TransportModuleBlock(TransportEnhancement.GATE, BlockBehaviour.Properties.of()
            .strength(3.0F, 8.0F)
            .sound(Objects.requireNonNull(SoundType.AMETHYST))
            .lightLevel(TransportModuleBlock::getLightEmission)
            .noOcclusion())
    );

    /**
     * Flux Capacitor module - instant charging (0 charge time).
     */
    public static final DeferredHolder<Block, TransportModuleBlock> FLUX_CAPACITOR = DevMod.BLOCKS.register(
        "flux_capacitor",
        () -> new TransportModuleBlock(TransportEnhancement.FLUX_CAPACITOR, BlockBehaviour.Properties.of()
            .strength(2.5F, 7.0F)
            .sound(Objects.requireNonNull(SoundType.COPPER))
            .lightLevel(TransportModuleBlock::getLightEmission)
            .noOcclusion())
    );

    /**
     * Network Relay module - enables network mode.
     */
    public static final DeferredHolder<Block, TransportModuleBlock> NETWORK_RELAY = DevMod.BLOCKS.register(
        "network_relay",
        () -> new TransportModuleBlock(TransportEnhancement.NETWORK_RELAY, BlockBehaviour.Properties.of()
            .strength(2.0F, 6.0F)
            .sound(Objects.requireNonNull(SoundType.METAL))
            .lightLevel(TransportModuleBlock::getLightEmission)
            .noOcclusion())
    );

    /**
     * Memory Core module - auto-save return points.
     */
    public static final DeferredHolder<Block, TransportModuleBlock> MEMORY_CORE = DevMod.BLOCKS.register(
        "memory_core",
        () -> new TransportModuleBlock(TransportEnhancement.MEMORY_CORE, BlockBehaviour.Properties.of()
            .strength(2.5F, 7.0F)
            .sound(Objects.requireNonNull(SoundType.SCULK))
            .lightLevel(TransportModuleBlock::getLightEmission)
            .noOcclusion())
    );

    /**
     * Party Beacon module - enables party synchronization.
     */
    public static final DeferredHolder<Block, TransportModuleBlock> PARTY_BEACON = DevMod.BLOCKS.register(
        "party_beacon",
        () -> new TransportModuleBlock(TransportEnhancement.PARTY_BEACON, BlockBehaviour.Properties.of()
            .strength(2.5F, 7.0F)
            .sound(Objects.requireNonNull(SoundType.AMETHYST_CLUSTER))
            .lightLevel(TransportModuleBlock::getLightEmission)
            .noOcclusion())
    );

    // === Frame Segments ===

    /**
     * Frame Segment block - visual portal frame.
     * Stack above Warp Core for visual progression.
     */
    public static final DeferredHolder<Block, TransportFrameBlock> FRAME_SEGMENT = DevMod.BLOCKS.register(
        "frame_segment",
        () -> new TransportFrameBlock(BlockBehaviour.Properties.of()
            .strength(2.0F, 6.0F)
            .sound(Objects.requireNonNull(SoundType.METAL))
            .lightLevel(TransportFrameBlock::getLightEmission)
            .noOcclusion())
    );

    /**
     * Called during mod initialization to ensure blocks are registered.
     */
    public static void init() {
        DevMod.LOGGER.debug("[Transport] Transport blocks initialized");
    }
}
