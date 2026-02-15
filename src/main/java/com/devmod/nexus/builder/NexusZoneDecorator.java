package com.devmod.nexus.builder;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredHolder;

import com.devmod.area.AreaBlocks;
import com.devmod.clone.CloneBlocks;
import com.devmod.debug.block.DebugBlocks;
import com.devmod.hologram.HologramBlocks;
import com.devmod.nexus.NexusDecorBlocks;
import com.devmod.nexus.data.ZoneSlot;
import com.devmod.nexus.data.ZoneSlotRegistry;
import com.devmod.portal.PortalBlocks;
import com.devmod.transport.TransportBlocks;
import com.devmod.zone.ZoneBlocks;

/**
 * Decorates Nexus zones with themed furniture, equipment, and interactive blocks.
 * Each zone receives purposeful decorations matching its testing function,
 * using DevMod's own blocks: clone machines, hologram projectors, transport cores,
 * entity scanners, admin terminals, and 180+ nexus decor blocks.
 *
 * <p>Called from {@link NexusEntitySpawner#postBuildEntities} after hub construction.
 */
public final class NexusZoneDecorator {
    private static final Logger LOGGER = LoggerFactory.getLogger(NexusZoneDecorator.class);

    public static final NexusZoneDecorator INSTANCE = new NexusZoneDecorator();

    /** Block placement flags: NOTIFY_CLIENTS | NO_NEIGHBOR_DROPS | PREVENT_REBUILD_RENDER */
    private static final int FLAGS = 2 | 16 | 64;

    private NexusZoneDecorator() {}

    /**
     * Decorate all Nexus zones with themed equipment and furniture.
     *
     * @param level the Nexus server level
     */
    public void decorateAllZones(@Nonnull ServerLevel level) {
        var server = level.getServer();
        if (server == null) return;

        ZoneSlotRegistry registry = ZoneSlotRegistry.get(server);
        int count = 0;

        count += decorate(level, registry, "spawn", this::decorateSpawn);
        count += decorate(level, registry, "combat_lab", this::decorateCombatLab);
        count += decorate(level, registry, "abilities_lab", this::decorateAbilitiesLab);
        count += decorate(level, registry, "boss_arena", this::decorateBossArena);
        count += decorate(level, registry, "portal_lab", this::decoratePortalLab);
        count += decorate(level, registry, "npc_lab", this::decorateNpcLab);
        count += decorate(level, registry, "vfx_studio", this::decorateVfxStudio);
        count += decorate(level, registry, "collision_lab", this::decorateCollisionLab);
        count += decorate(level, registry, "arena_builder", this::decorateArenaBuilder);
        count += decorate(level, registry, "item_workshop", this::decorateItemWorkshop);
        count += decorate(level, registry, "config_room", this::decorateConfigRoom);
        count += decorate(level, registry, "hud_testing", this::decorateHudTesting);
        count += decorate(level, registry, "quest_testing", this::decorateQuestTesting);
        count += decorate(level, registry, "sandbox", this::decorateSandbox);
        count += decorate(level, registry, "admin_tools", this::decorateAdminTools);

        LOGGER.info("[NexusDecorator] Decorated {} zones", count);
    }

    @FunctionalInterface
    private interface ZoneDecorator {
        void decorate(ServerLevel level, BlockPos center);
    }

    private int decorate(ServerLevel level, ZoneSlotRegistry registry,
                         String slotId, ZoneDecorator decorator) {
        Optional<ZoneSlot> slot = registry.getSlot(slotId);
        if (slot.isEmpty()) {
            LOGGER.warn("[NexusDecorator] Slot '{}' not found, skipping", slotId);
            return 0;
        }
        try {
            BlockPos center = slot.get().bounds().floorCenter();
            decorator.decorate(level, center);
            LOGGER.debug("[NexusDecorator] Decorated '{}'", slotId);
            return 1;
        } catch (Exception e) {
            LOGGER.warn("[NexusDecorator] Failed to decorate '{}': {}", slotId, e.getMessage());
            return 0;
        }
    }

    // ========================================================================
    // SPAWN - Hub Center (64x64)
    // Hub overview with hologram projector showing 3D terrain scan
    // ========================================================================

    private void decorateSpawn(ServerLevel level, BlockPos center) {
        // Hologram projector on the center pillar for 3D hub overview
        place(level, center.above(4), HologramBlocks.HOLOGRAM_PROJECTOR);
    }

    // ========================================================================
    // COMBAT_LAB - North (96x96)
    // Combat testing: weapon displays, armor mannequins, arena boundary
    // ========================================================================

    private void decorateCombatLab(ServerLevel level, BlockPos center) {
        int y = center.getY() + 1;
        int cx = center.getX();
        int cz = center.getZ();

        // Arena boundary: hazard strips in a 24x24 square
        for (int i = -12; i <= 12; i++) {
            place(level, new BlockPos(cx + i, y, cz - 12), NexusDecorBlocks.NEXUS_HAZARD);
            place(level, new BlockPos(cx + i, y, cz + 12), NexusDecorBlocks.NEXUS_HAZARD);
            if (i == -12 || i == 12) {
                for (int j = -11; j <= 11; j++) {
                    place(level, new BlockPos(cx + i, y, cz + j), NexusDecorBlocks.NEXUS_HAZARD);
                }
            }
        }

        // Corner floor lights
        place(level, new BlockPos(cx - 12, y, cz - 12), NexusDecorBlocks.NEXUS_FLOOR_LIGHT_CYAN);
        place(level, new BlockPos(cx + 12, y, cz - 12), NexusDecorBlocks.NEXUS_FLOOR_LIGHT_CYAN);
        place(level, new BlockPos(cx - 12, y, cz + 12), NexusDecorBlocks.NEXUS_FLOOR_LIGHT_CYAN);
        place(level, new BlockPos(cx + 12, y, cz + 12), NexusDecorBlocks.NEXUS_FLOOR_LIGHT_CYAN);

        // East side: armor display mannequins
        place(level, new BlockPos(cx + 10, y, cz - 4), CloneBlocks.NEUROCELL_MANNEQUIN);
        place(level, new BlockPos(cx + 10, y, cz), CloneBlocks.NEUROCELL_MANNEQUIN);
        place(level, new BlockPos(cx + 10, y, cz + 4), CloneBlocks.NEUROCELL_MANNEQUIN);

        // West side: weapon display items
        place(level, new BlockPos(cx - 10, y, cz - 3), CloneBlocks.NEUROCELL_ITEM);
        place(level, new BlockPos(cx - 10, y, cz), CloneBlocks.NEUROCELL_ITEM);
        place(level, new BlockPos(cx - 10, y, cz + 3), CloneBlocks.NEUROCELL_ITEM);

        // South: entity scanner for damage analysis
        place(level, new BlockPos(cx, y, cz + 10), DebugBlocks.ENTITY_SCANNER);

        // North: hologram projector for combat preview
        place(level, new BlockPos(cx, y, cz - 10), HologramBlocks.HOLOGRAM_PROJECTOR);

        // Neon accent strips along cardinal edges
        for (int i = -8; i <= 8; i += 4) {
            place(level, new BlockPos(cx + i, y, cz - 13), NexusDecorBlocks.NEXUS_GLOW_STRIP_CYAN);
            place(level, new BlockPos(cx + i, y, cz + 13), NexusDecorBlocks.NEXUS_GLOW_STRIP_CYAN);
        }
    }

    // ========================================================================
    // ABILITIES_LAB - East (96x96)
    // Movement testing: parkour platforms, dash markers, dodge zones
    // ========================================================================

    private void decorateAbilitiesLab(ServerLevel level, BlockPos center) {
        int y = center.getY();
        int cx = center.getX();
        int cz = center.getZ();

        // Parkour course: 6 platforms at increasing heights (east-west line)
        for (int i = 0; i < 6; i++) {
            int px = cx - 12 + (i * 5);
            int py = y + 1 + i;  // rising from 1 to 6 blocks high

            // Platform base (3x3)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    place(level, new BlockPos(px + dx, py, cz + dz), NexusDecorBlocks.NEXUS_AZURE);
                }
            }
            // Glow strip on top edge
            place(level, new BlockPos(px, py, cz - 1), NexusDecorBlocks.NEXUS_GLOW_STRIP_CYAN);
        }

        // Dash distance markers on floor (south side) - 5, 10, 15 blocks
        for (int dist : new int[]{5, 10, 15, 20}) {
            for (int dx = -1; dx <= 1; dx++) {
                place(level, new BlockPos(cx + dx, y + 1, cz + dist), NexusDecorBlocks.NEXUS_SIGNAL);
            }
        }

        // Dodge zone markers (west side) - alternating cyan floor lights
        for (int i = 0; i < 4; i++) {
            place(level, new BlockPos(cx - 15, y + 1, cz - 8 + (i * 4)), NexusDecorBlocks.NEXUS_FLOOR_LIGHT_CYAN);
            // Small platform next to each marker
            place(level, new BlockPos(cx - 14, y + 1, cz - 8 + (i * 4)), NexusDecorBlocks.NEXUS_AZURE);
            place(level, new BlockPos(cx - 16, y + 1, cz - 8 + (i * 4)), NexusDecorBlocks.NEXUS_AZURE);
        }

        // Entity scanner for movement data
        place(level, new BlockPos(cx, y + 1, cz - 8), DebugBlocks.ENTITY_SCANNER);

        // Hologram projector for ability reference
        place(level, new BlockPos(cx + 15, y + 1, cz), HologramBlocks.HOLOGRAM_PROJECTOR);
    }

    // ========================================================================
    // BOSS_ARENA - South (96x96)
    // Boss fight testing: arena ring, phase markers, wave indicators
    // ========================================================================

    private void decorateBossArena(ServerLevel level, BlockPos center) {
        int y = center.getY() + 1;
        int cx = center.getX();
        int cz = center.getZ();

        // Circular arena ring (radius 16) using reactor blocks
        int radius = 16;
        for (int angle = 0; angle < 360; angle += 5) {
            double rad = Math.toRadians(angle);
            int rx = cx + (int) (radius * Math.cos(rad));
            int rz = cz + (int) (radius * Math.sin(rad));
            place(level, new BlockPos(rx, y, rz), NexusDecorBlocks.NEXUS_REACTOR);
        }

        // Phase boundary markers at cardinal points
        place(level, new BlockPos(cx, y, cz - radius - 1), NexusDecorBlocks.NEXUS_GLOW_STRIP_CYAN);
        place(level, new BlockPos(cx, y, cz + radius + 1), NexusDecorBlocks.NEXUS_GLOW_STRIP_CYAN);
        place(level, new BlockPos(cx - radius - 1, y, cz), NexusDecorBlocks.NEXUS_GLOW_STRIP_CYAN);
        place(level, new BlockPos(cx + radius + 1, y, cz), NexusDecorBlocks.NEXUS_GLOW_STRIP_CYAN);

        // Hologram projectors for boss phase display (east and west)
        place(level, new BlockPos(cx - radius - 3, y, cz), HologramBlocks.HOLOGRAM_PROJECTOR);
        place(level, new BlockPos(cx + radius + 3, y, cz), HologramBlocks.HOLOGRAM_PROJECTOR);

        // Entrance hazard markers (north)
        for (int i = -3; i <= 3; i++) {
            place(level, new BlockPos(cx + i, y, cz - radius - 2), NexusDecorBlocks.NEXUS_HAZARD);
        }

        // Neurocell_L for large boss preview (south side)
        place(level, new BlockPos(cx, y, cz + radius + 3), CloneBlocks.NEUROCELL_L);

        // Floor lights inside arena (4 quadrant lights)
        place(level, new BlockPos(cx - 8, y, cz - 8), NexusDecorBlocks.NEXUS_FLOOR_LIGHT);
        place(level, new BlockPos(cx + 8, y, cz - 8), NexusDecorBlocks.NEXUS_FLOOR_LIGHT);
        place(level, new BlockPos(cx - 8, y, cz + 8), NexusDecorBlocks.NEXUS_FLOOR_LIGHT);
        place(level, new BlockPos(cx + 8, y, cz + 8), NexusDecorBlocks.NEXUS_FLOOR_LIGHT);
    }

    // ========================================================================
    // PORTAL_LAB - West (96x96)
    // Portal/transport testing: telepads, warp core, rune display, portals
    // ========================================================================

    private void decoratePortalLab(ServerLevel level, BlockPos center) {
        int y = center.getY() + 1;
        int cx = center.getX();
        int cz = center.getZ();

        // 4 telepads in compass formation (10 blocks from center)
        place(level, new BlockPos(cx, y, cz - 10), CloneBlocks.TELEPAD);
        place(level, new BlockPos(cx, y, cz + 10), CloneBlocks.TELEPAD);
        place(level, new BlockPos(cx - 10, y, cz), CloneBlocks.TELEPAD);
        place(level, new BlockPos(cx + 10, y, cz), CloneBlocks.TELEPAD);

        // Telepad pedestals (nexus_telepad_core under each)
        place(level, new BlockPos(cx, y - 1, cz - 10), NexusDecorBlocks.NEXUS_TELEPAD_CORE);
        place(level, new BlockPos(cx, y - 1, cz + 10), NexusDecorBlocks.NEXUS_TELEPAD_CORE);
        place(level, new BlockPos(cx - 10, y - 1, cz), NexusDecorBlocks.NEXUS_TELEPAD_CORE);
        place(level, new BlockPos(cx + 10, y - 1, cz), NexusDecorBlocks.NEXUS_TELEPAD_CORE);

        // Center: Warp core with transport modules in a ring
        place(level, new BlockPos(cx, y, cz), TransportBlocks.WARP_CORE);
        place(level, new BlockPos(cx + 1, y, cz), TransportBlocks.RANGE_AMPLIFIER);
        place(level, new BlockPos(cx - 1, y, cz), TransportBlocks.DIMENSIONAL_GATE);
        place(level, new BlockPos(cx, y, cz + 1), TransportBlocks.FLUX_CAPACITOR);
        place(level, new BlockPos(cx, y, cz - 1), TransportBlocks.NETWORK_RELAY);
        place(level, new BlockPos(cx + 1, y, cz + 1), TransportBlocks.MEMORY_CORE);
        place(level, new BlockPos(cx - 1, y, cz - 1), TransportBlocks.PARTY_BEACON);
        place(level, new BlockPos(cx + 1, y, cz - 1), TransportBlocks.CHROMATIC_LENS);

        // Frame segments around warp core
        for (int dx = -2; dx <= 2; dx++) {
            place(level, new BlockPos(cx + dx, y, cz - 2), TransportBlocks.FRAME_SEGMENT);
            place(level, new BlockPos(cx + dx, y, cz + 2), TransportBlocks.FRAME_SEGMENT);
        }

        // East: Rune block display (all 5 types in a row on nexus_plating pedestals)
        place(level, new BlockPos(cx + 15, y, cz - 4), NexusDecorBlocks.NEXUS_PLATING);
        place(level, new BlockPos(cx + 15, y + 1, cz - 4), PortalBlocks.RUNE_HASTE);
        place(level, new BlockPos(cx + 15, y, cz - 2), NexusDecorBlocks.NEXUS_PLATING);
        place(level, new BlockPos(cx + 15, y + 1, cz - 2), PortalBlocks.RUNE_GATE);
        place(level, new BlockPos(cx + 15, y, cz), NexusDecorBlocks.NEXUS_PLATING);
        place(level, new BlockPos(cx + 15, y + 1, cz), PortalBlocks.RUNE_ENHANCER);
        place(level, new BlockPos(cx + 15, y, cz + 2), NexusDecorBlocks.NEXUS_PLATING);
        place(level, new BlockPos(cx + 15, y + 1, cz + 2), PortalBlocks.RUNE_STRONG_ENHANCER);
        place(level, new BlockPos(cx + 15, y, cz + 4), NexusDecorBlocks.NEXUS_PLATING);
        place(level, new BlockPos(cx + 15, y + 1, cz + 4), PortalBlocks.RUNE_INFINITY);

        // Connecting glow strips between telepads
        for (int i = -9; i <= 9; i++) {
            if (Math.abs(i) > 1) {
                place(level, new BlockPos(cx + i, y, cz), NexusDecorBlocks.NEXUS_GLOW_STRIP_CYAN);
                place(level, new BlockPos(cx, y, cz + i), NexusDecorBlocks.NEXUS_GLOW_STRIP_CYAN);
            }
        }
    }

    // ========================================================================
    // NPC_LAB - North-East (96x96)
    // Clone/NPC testing: processing chain, mannequins, bio equipment
    // ========================================================================

    private void decorateNpcLab(ServerLevel level, BlockPos center) {
        int y = center.getY() + 1;
        int cx = center.getX();
        int cz = center.getZ();

        // Clone processing chain (east-west line):
        // imprinter -> neurolink -> neurocell -> neurolink -> reformer
        place(level, new BlockPos(cx - 8, y, cz), CloneBlocks.IMPRINTER);
        place(level, new BlockPos(cx - 4, y, cz), CloneBlocks.NEUROLINK);
        place(level, new BlockPos(cx, y, cz), CloneBlocks.NEUROCELL);
        place(level, new BlockPos(cx + 4, y, cz), CloneBlocks.NEUROLINK);
        place(level, new BlockPos(cx + 8, y, cz), CloneBlocks.REFORMER);

        // Neurolink cables connecting the chain
        place(level, new BlockPos(cx - 6, y, cz), CloneBlocks.NEUROLINK);
        place(level, new BlockPos(cx - 2, y, cz), CloneBlocks.NEUROLINK);
        place(level, new BlockPos(cx + 2, y, cz), CloneBlocks.NEUROLINK);
        place(level, new BlockPos(cx + 6, y, cz), CloneBlocks.NEUROLINK);

        // South: Neurocell_L for large entity cloning
        place(level, new BlockPos(cx, y, cz + 8), CloneBlocks.NEUROCELL_L);

        // North: Mannequin display row
        place(level, new BlockPos(cx - 6, y, cz - 8), CloneBlocks.NEUROCELL_MANNEQUIN);
        place(level, new BlockPos(cx - 2, y, cz - 8), CloneBlocks.NEUROCELL_MANNEQUIN);
        place(level, new BlockPos(cx + 2, y, cz - 8), CloneBlocks.NEUROCELL_MANNEQUIN);
        place(level, new BlockPos(cx + 6, y, cz - 8), CloneBlocks.NEUROCELL_MANNEQUIN);

        // Item displays flanking the mannequins
        place(level, new BlockPos(cx - 8, y, cz - 8), CloneBlocks.NEUROCELL_ITEM);
        place(level, new BlockPos(cx + 8, y, cz - 8), CloneBlocks.NEUROCELL_ITEM);

        // Entity scanner for NPC analysis
        place(level, new BlockPos(cx + 12, y, cz), DebugBlocks.ENTITY_SCANNER);

        // Hologram projector for NPC preview
        place(level, new BlockPos(cx - 12, y, cz), HologramBlocks.HOLOGRAM_PROJECTOR);

        // Bioscanner chest (south-east)
        placeChest(level, new BlockPos(cx + 10, y, cz + 6), Direction.WEST);

        // Floor accents: nexus_data strip along the processing chain
        for (int i = -9; i <= 9; i++) {
            place(level, new BlockPos(cx + i, y - 1, cz), NexusDecorBlocks.NEXUS_DATA);
        }
    }

    // ========================================================================
    // VFX_STUDIO - North-West (96x96)
    // Effects testing: dark room, VFX targets, hologram previews
    // ========================================================================

    private void decorateVfxStudio(ServerLevel level, BlockPos center) {
        int y = center.getY() + 1;
        int cx = center.getX();
        int cz = center.getZ();

        // Dark room floor ring (radius 14) for VFX contrast
        for (int dx = -14; dx <= 14; dx++) {
            for (int dz = -14; dz <= 14; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist >= 12 && dist <= 14) {
                    place(level, new BlockPos(cx + dx, y - 1, cz + dz), NexusDecorBlocks.NEXUS_VOID);
                }
            }
        }

        // Inner ring of neon strips for atmospheric lighting (radius 10)
        for (int angle = 0; angle < 360; angle += 45) {
            double rad = Math.toRadians(angle);
            int rx = cx + (int) (10 * Math.cos(rad));
            int rz = cz + (int) (10 * Math.sin(rad));
            place(level, new BlockPos(rx, y, rz), NexusDecorBlocks.NEXUS_NEON);
        }

        // Hologram projectors at 4 corners for effect previewing
        place(level, new BlockPos(cx - 8, y, cz - 8), HologramBlocks.HOLOGRAM_PROJECTOR);
        place(level, new BlockPos(cx + 8, y, cz - 8), HologramBlocks.HOLOGRAM_PROJECTOR);
        place(level, new BlockPos(cx - 8, y, cz + 8), HologramBlocks.HOLOGRAM_PROJECTOR);
        place(level, new BlockPos(cx + 8, y, cz + 8), HologramBlocks.HOLOGRAM_PROJECTOR);

        // Center: entity scanner for VFX target analysis
        place(level, new BlockPos(cx, y, cz), DebugBlocks.ENTITY_SCANNER);

        // Accent glow strips at cardinal directions
        for (int i = -6; i <= 6; i += 2) {
            place(level, new BlockPos(cx + i, y, cz - 7), NexusDecorBlocks.NEXUS_GLOW_STRIP_WHITE);
            place(level, new BlockPos(cx + i, y, cz + 7), NexusDecorBlocks.NEXUS_GLOW_STRIP_WHITE);
        }

        // Clone machines for visual richness (animated GeckoLib models)
        place(level, new BlockPos(cx - 6, y, cz - 4), CloneBlocks.CLONE_LASER_ARM);
        place(level, new BlockPos(cx + 6, y, cz - 4), CloneBlocks.CLONE_PROCESSOR);
    }

    // ========================================================================
    // COLLISION_LAB - South-East (96x96)
    // Hitbox testing: grid floor, measurement markers, scanners
    // ========================================================================

    private void decorateCollisionLab(ServerLevel level, BlockPos center) {
        int y = center.getY();
        int cx = center.getX();
        int cz = center.getZ();

        // Grid floor patch (16x16) for measurement reference
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                if (dx % 4 == 0 || dz % 4 == 0) {
                    place(level, new BlockPos(cx + dx, y, cz + dz), NexusDecorBlocks.NEXUS_GRID);
                }
            }
        }

        // Distance markers: glow strips at 5-block intervals from center
        for (int dist : new int[]{5, 10, 15}) {
            place(level, new BlockPos(cx + dist, y + 1, cz), NexusDecorBlocks.NEXUS_SIGNAL);
            place(level, new BlockPos(cx - dist, y + 1, cz), NexusDecorBlocks.NEXUS_SIGNAL);
            place(level, new BlockPos(cx, y + 1, cz + dist), NexusDecorBlocks.NEXUS_SIGNAL);
            place(level, new BlockPos(cx, y + 1, cz - dist), NexusDecorBlocks.NEXUS_SIGNAL);
        }

        // Entity scanners near each dummy position
        place(level, new BlockPos(cx - 6, y + 1, cz + 3), DebugBlocks.ENTITY_SCANNER);
        place(level, new BlockPos(cx, y + 1, cz + 3), DebugBlocks.ENTITY_SCANNER);
        place(level, new BlockPos(cx + 6, y + 1, cz + 3), DebugBlocks.ENTITY_SCANNER);

        // Hologram projector for hitbox visualization
        place(level, new BlockPos(cx, y + 1, cz - 10), HologramBlocks.HOLOGRAM_PROJECTOR);

        // Corner markers with floor lights
        place(level, new BlockPos(cx - 8, y + 1, cz - 8), NexusDecorBlocks.NEXUS_FLOOR_LIGHT);
        place(level, new BlockPos(cx + 8, y + 1, cz - 8), NexusDecorBlocks.NEXUS_FLOOR_LIGHT);
        place(level, new BlockPos(cx - 8, y + 1, cz + 8), NexusDecorBlocks.NEXUS_FLOOR_LIGHT);
        place(level, new BlockPos(cx + 8, y + 1, cz + 8), NexusDecorBlocks.NEXUS_FLOOR_LIGHT);
    }

    // ========================================================================
    // ARENA_BUILDER - South-West (96x96)
    // Area/zone building tools: editors, markers, template preview
    // ========================================================================

    private void decorateArenaBuilder(ServerLevel level, BlockPos center) {
        int y = center.getY() + 1;
        int cx = center.getX();
        int cz = center.getZ();

        // Center: area editor block
        place(level, new BlockPos(cx, y, cz), AreaBlocks.AREA_EDITOR);

        // Zone markers in a square (8 block sides) demonstrating zone bounds
        place(level, new BlockPos(cx - 8, y, cz - 8), ZoneBlocks.ZONE_MARKER);
        place(level, new BlockPos(cx + 8, y, cz - 8), ZoneBlocks.ZONE_MARKER);
        place(level, new BlockPos(cx - 8, y, cz + 8), ZoneBlocks.ZONE_MARKER);
        place(level, new BlockPos(cx + 8, y, cz + 8), ZoneBlocks.ZONE_MARKER);

        // Nexus editor central block
        place(level, new BlockPos(cx, y, cz - 5), AreaBlocks.NEXUS_EDITOR_CENTRAL);

        // Hologram projector for template previews
        place(level, new BlockPos(cx, y, cz + 5), HologramBlocks.HOLOGRAM_PROJECTOR);

        // Block palette showcase: row of different nexus decor samples (west wall)
        List<DeferredHolder<Block, ?>> showcase = List.of(
            NexusDecorBlocks.NEXUS_PANEL, NexusDecorBlocks.NEXUS_TILE,
            NexusDecorBlocks.NEXUS_GRID, NexusDecorBlocks.NEXUS_PLATING,
            NexusDecorBlocks.NEXUS_CONDUIT, NexusDecorBlocks.NEXUS_CIRCUIT,
            NexusDecorBlocks.NEXUS_STEEL, NexusDecorBlocks.NEXUS_CARBON
        );
        for (int i = 0; i < showcase.size(); i++) {
            place(level, new BlockPos(cx - 10, y, cz - 4 + i), showcase.get(i));
            place(level, new BlockPos(cx - 10, y + 1, cz - 4 + i), showcase.get(i));
        }

        // Glow block palette showcase (east wall)
        List<DeferredHolder<Block, ?>> glowShowcase = List.of(
            NexusDecorBlocks.NEXUS_AZURE, NexusDecorBlocks.NEXUS_PLASMA,
            NexusDecorBlocks.NEXUS_MATRIX, NexusDecorBlocks.NEXUS_ENERGY,
            NexusDecorBlocks.NEXUS_CRYSTAL, NexusDecorBlocks.NEXUS_REACTOR,
            NexusDecorBlocks.NEXUS_NEON, NexusDecorBlocks.NEXUS_QUANTUM
        );
        for (int i = 0; i < glowShowcase.size(); i++) {
            place(level, new BlockPos(cx + 10, y, cz - 4 + i), glowShowcase.get(i));
            place(level, new BlockPos(cx + 10, y + 1, cz - 4 + i), glowShowcase.get(i));
        }

        // Floor accent connecting markers
        for (int i = -7; i <= 7; i++) {
            place(level, new BlockPos(cx + i, y - 1, cz - 8), NexusDecorBlocks.NEXUS_GLOW_STRIP_CYAN);
            place(level, new BlockPos(cx + i, y - 1, cz + 8), NexusDecorBlocks.NEXUS_GLOW_STRIP_CYAN);
        }
    }

    // ========================================================================
    // ITEM_WORKSHOP - East Outer (72x72)
    // Item/equipment testing: crafting stations, displays, mannequins
    // ========================================================================

    private void decorateItemWorkshop(ServerLevel level, BlockPos center) {
        int y = center.getY() + 1;
        int cx = center.getX();
        int cz = center.getZ();

        // Crafting station row (north side)
        place(level, new BlockPos(cx - 6, y, cz - 6), Blocks.CRAFTING_TABLE);
        place(level, new BlockPos(cx - 3, y, cz - 6), Blocks.ANVIL);
        place(level, new BlockPos(cx, y, cz - 6), Blocks.SMITHING_TABLE);
        place(level, new BlockPos(cx + 3, y, cz - 6), Blocks.ENCHANTING_TABLE);
        place(level, new BlockPos(cx + 6, y, cz - 6), Blocks.GRINDSTONE);

        // Mannequin display row (south side)
        for (int i = -6; i <= 6; i += 4) {
            place(level, new BlockPos(cx + i, y, cz + 6), CloneBlocks.NEUROCELL_MANNEQUIN);
        }

        // Item displays (east wall)
        for (int i = -4; i <= 4; i += 2) {
            place(level, new BlockPos(cx + 10, y, cz + i), CloneBlocks.NEUROCELL_ITEM);
        }

        // Clone machines for item processing display
        place(level, new BlockPos(cx - 10, y, cz - 2), CloneBlocks.CLONE_FOUNDRY);
        place(level, new BlockPos(cx - 10, y, cz + 2), CloneBlocks.CLONE_ASSEMBLER);
        place(level, new BlockPos(cx - 10, y, cz + 5), CloneBlocks.CLONE_SMELTER);

        // Entity scanner
        place(level, new BlockPos(cx + 10, y, cz - 6), DebugBlocks.ENTITY_SCANNER);
    }

    // ========================================================================
    // CONFIG_ROOM - East-South Outer (72x72)
    // Config management: admin terminal, hologram, terminal desk
    // ========================================================================

    private void decorateConfigRoom(ServerLevel level, BlockPos center) {
        int y = center.getY() + 1;
        int cx = center.getX();
        int cz = center.getZ();

        // Center: admin terminal
        place(level, new BlockPos(cx, y, cz), CloneBlocks.ADMIN_TERMINAL);

        // Terminal desk (U-shaped nexus_terminal blocks around admin terminal)
        for (int dx = -3; dx <= 3; dx++) {
            place(level, new BlockPos(cx + dx, y, cz - 2), NexusDecorBlocks.NEXUS_TERMINAL);
        }
        place(level, new BlockPos(cx - 3, y, cz - 1), NexusDecorBlocks.NEXUS_TERMINAL);
        place(level, new BlockPos(cx + 3, y, cz - 1), NexusDecorBlocks.NEXUS_TERMINAL);
        place(level, new BlockPos(cx - 3, y, cz), NexusDecorBlocks.NEXUS_TERMINAL);
        place(level, new BlockPos(cx + 3, y, cz), NexusDecorBlocks.NEXUS_TERMINAL);

        // Hologram projector for config overview
        place(level, new BlockPos(cx, y, cz + 5), HologramBlocks.HOLOGRAM_PROJECTOR);

        // Display blocks along walls for visual context
        for (int i = -4; i <= 4; i += 2) {
            place(level, new BlockPos(cx + i, y, cz - 8), NexusDecorBlocks.NEXUS_DISPLAY);
        }

        // Floor lights for ambiance
        place(level, new BlockPos(cx - 6, y - 1, cz), NexusDecorBlocks.NEXUS_FLOOR_LIGHT);
        place(level, new BlockPos(cx + 6, y - 1, cz), NexusDecorBlocks.NEXUS_FLOOR_LIGHT);

        // Clone processor for config processing visual
        place(level, new BlockPos(cx - 6, y, cz + 3), CloneBlocks.CLONE_PROCESSOR);
        place(level, new BlockPos(cx + 6, y, cz + 3), CloneBlocks.CLONE_STORAGE_UNIT);
    }

    // ========================================================================
    // HUD_TESTING - West-South Outer (72x72)
    // HUD overlay testing: contrast areas, varied lighting, display blocks
    // ========================================================================

    private void decorateHudTesting(ServerLevel level, BlockPos center) {
        int y = center.getY();
        int cx = center.getX();
        int cz = center.getZ();

        // Light zone (north half): bright floor
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 0; dz++) {
                place(level, new BlockPos(cx + dx, y, cz + dz), NexusDecorBlocks.NEXUS_LIGHT);
            }
        }

        // Dark zone (south half): void floor
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = 1; dz <= 8; dz++) {
                place(level, new BlockPos(cx + dx, y, cz + dz), NexusDecorBlocks.NEXUS_VOID);
            }
        }

        // Contrast border between light and dark
        for (int dx = -8; dx <= 8; dx++) {
            place(level, new BlockPos(cx + dx, y + 1, cz), NexusDecorBlocks.NEXUS_GLOW_STRIP_WHITE);
        }

        // Colored accent blocks for HUD color testing
        place(level, new BlockPos(cx - 6, y + 1, cz - 4), NexusDecorBlocks.NEXUS_AZURE);
        place(level, new BlockPos(cx - 4, y + 1, cz - 4), NexusDecorBlocks.NEXUS_PLASMA);
        place(level, new BlockPos(cx - 2, y + 1, cz - 4), NexusDecorBlocks.NEXUS_MATRIX);
        place(level, new BlockPos(cx, y + 1, cz - 4), NexusDecorBlocks.NEXUS_ENERGY);
        place(level, new BlockPos(cx + 2, y + 1, cz - 4), NexusDecorBlocks.NEXUS_REACTOR);
        place(level, new BlockPos(cx + 4, y + 1, cz - 4), NexusDecorBlocks.NEXUS_CRYSTAL);
        place(level, new BlockPos(cx + 6, y + 1, cz - 4), NexusDecorBlocks.NEXUS_NEON);

        // Entity scanner for HUD data
        place(level, new BlockPos(cx, y + 1, cz - 6), DebugBlocks.ENTITY_SCANNER);

        // Hologram projector
        place(level, new BlockPos(cx, y + 1, cz + 6), HologramBlocks.HOLOGRAM_PROJECTOR);
    }

    // ========================================================================
    // QUEST_TESTING - South-West Outer (72x72)
    // Quest system testing: quest items, NPC spots, leaderboard area
    // ========================================================================

    private void decorateQuestTesting(ServerLevel level, BlockPos center) {
        int y = center.getY() + 1;
        int cx = center.getX();
        int cz = center.getZ();

        // Quest item chest
        placeChest(level, new BlockPos(cx - 4, y, cz), Direction.EAST);

        // Entity scanner for quest entity analysis
        place(level, new BlockPos(cx, y, cz), DebugBlocks.ENTITY_SCANNER);

        // Hologram projector for leaderboard display
        place(level, new BlockPos(cx + 4, y, cz), HologramBlocks.HOLOGRAM_PROJECTOR);

        // NPC spawn markers (glow strips where NPCs would stand)
        place(level, new BlockPos(cx - 6, y, cz - 6), NexusDecorBlocks.NEXUS_FLOOR_LIGHT_CYAN);
        place(level, new BlockPos(cx, y, cz - 6), NexusDecorBlocks.NEXUS_FLOOR_LIGHT_CYAN);
        place(level, new BlockPos(cx + 6, y, cz - 6), NexusDecorBlocks.NEXUS_FLOOR_LIGHT_CYAN);

        // Signal blocks at quest waypoints
        place(level, new BlockPos(cx - 8, y, cz + 4), NexusDecorBlocks.NEXUS_SIGNAL);
        place(level, new BlockPos(cx, y, cz + 8), NexusDecorBlocks.NEXUS_SIGNAL);
        place(level, new BlockPos(cx + 8, y, cz + 4), NexusDecorBlocks.NEXUS_SIGNAL);

        // Clone machines for quest reward processing
        place(level, new BlockPos(cx - 8, y, cz - 3), CloneBlocks.CLONE_ASSEMBLER);
        place(level, new BlockPos(cx + 8, y, cz - 3), CloneBlocks.CLONE_STORAGE_UNIT);
    }

    // ========================================================================
    // SANDBOX - South-East Outer (72x72)
    // Free creative area: basic tools, building blocks chest
    // ========================================================================

    private void decorateSandbox(ServerLevel level, BlockPos center) {
        int y = center.getY() + 1;
        int cx = center.getX();
        int cz = center.getZ();

        // Crafting workbench
        place(level, new BlockPos(cx - 3, y, cz), Blocks.CRAFTING_TABLE);
        place(level, new BlockPos(cx - 2, y, cz), Blocks.ANVIL);

        // Building blocks chest (south)
        BlockPos chestPos = new BlockPos(cx + 3, y, cz);
        placeChest(level, chestPos, Direction.WEST);
        if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            // Fill with assorted nexus building blocks (as items)
            chest.setItem(0, new ItemStack(NexusDecorBlocks.NEXUS_PANEL.get().asItem(), 64));
            chest.setItem(1, new ItemStack(NexusDecorBlocks.NEXUS_TILE.get().asItem(), 64));
            chest.setItem(2, new ItemStack(NexusDecorBlocks.NEXUS_GRID.get().asItem(), 64));
            chest.setItem(3, new ItemStack(NexusDecorBlocks.NEXUS_PLATING.get().asItem(), 64));
            chest.setItem(4, new ItemStack(NexusDecorBlocks.NEXUS_STEEL.get().asItem(), 64));
            chest.setItem(5, new ItemStack(NexusDecorBlocks.NEXUS_CARBON.get().asItem(), 64));
            chest.setItem(6, new ItemStack(NexusDecorBlocks.NEXUS_LIGHT.get().asItem(), 64));
            chest.setItem(7, new ItemStack(NexusDecorBlocks.NEXUS_AZURE.get().asItem(), 64));
            chest.setItem(8, new ItemStack(NexusDecorBlocks.NEXUS_GLOW_STRIP_CYAN.get().asItem(), 64));
            chest.setItem(9, new ItemStack(NexusDecorBlocks.NEXUS_GLASS_CYAN.get().asItem(), 64));
        }

        // Area editor for sandbox zone manipulation
        place(level, new BlockPos(cx, y, cz - 5), AreaBlocks.AREA_EDITOR);

        // Zone marker
        place(level, new BlockPos(cx, y, cz + 5), ZoneBlocks.ZONE_MARKER);
    }

    // ========================================================================
    // ADMIN_TOOLS - South Outer Center (72x72)
    // Admin command center: terminals, scanners, hologram dashboard
    // ========================================================================

    private void decorateAdminTools(ServerLevel level, BlockPos center) {
        int y = center.getY() + 1;
        int cx = center.getX();
        int cz = center.getZ();

        // Central admin terminal
        place(level, new BlockPos(cx, y, cz), CloneBlocks.ADMIN_TERMINAL);

        // Command center desk (L-shaped nexus_terminal blocks)
        for (int dx = -4; dx <= 4; dx++) {
            place(level, new BlockPos(cx + dx, y, cz - 2), NexusDecorBlocks.NEXUS_TERMINAL);
        }
        for (int dz = -1; dz <= 3; dz++) {
            place(level, new BlockPos(cx - 4, y, cz + dz), NexusDecorBlocks.NEXUS_TERMINAL);
            place(level, new BlockPos(cx + 4, y, cz + dz), NexusDecorBlocks.NEXUS_TERMINAL);
        }

        // Display wall behind desk
        for (int dx = -3; dx <= 3; dx++) {
            place(level, new BlockPos(cx + dx, y, cz - 4), NexusDecorBlocks.NEXUS_DISPLAY);
            place(level, new BlockPos(cx + dx, y + 1, cz - 4), NexusDecorBlocks.NEXUS_DISPLAY);
        }

        // Entity scanner
        place(level, new BlockPos(cx - 6, y, cz + 2), DebugBlocks.ENTITY_SCANNER);

        // Hologram projector for dashboard display
        place(level, new BlockPos(cx, y, cz + 6), HologramBlocks.HOLOGRAM_PROJECTOR);

        // Clone machines for admin functions
        place(level, new BlockPos(cx - 8, y, cz), CloneBlocks.CLONE_REACTOR);
        place(level, new BlockPos(cx + 8, y, cz), CloneBlocks.CLONE_PROCESSOR);
        place(level, new BlockPos(cx - 8, y, cz + 4), CloneBlocks.CLONE_BATTERY);
        place(level, new BlockPos(cx + 8, y, cz + 4), CloneBlocks.CLONE_SOLAR_PANEL);

        // Floor accent
        for (int dx = -3; dx <= 3; dx++) {
            place(level, new BlockPos(cx + dx, y - 1, cz), NexusDecorBlocks.NEXUS_GLOW_STRIP_CYAN);
        }
    }

    // ========================================================================
    // Block Placement Helpers
    // ========================================================================

    private void place(ServerLevel level, BlockPos pos, DeferredHolder<Block, ?> block) {
        place(level, pos, block.get().defaultBlockState());
    }

    private void place(ServerLevel level, BlockPos pos, Block block) {
        place(level, pos, block.defaultBlockState());
    }

    private void place(ServerLevel level, BlockPos pos, BlockState state) {
        if (!level.isLoaded(pos)) {
            level.getChunk(pos);
        }
        level.setBlock(pos, state, FLAGS);
    }

    private void placeChest(ServerLevel level, BlockPos pos, Direction facing) {
        BlockState chestState = Blocks.CHEST.defaultBlockState()
            .setValue(ChestBlock.FACING, facing);
        place(level, pos, chestState);
    }
}
