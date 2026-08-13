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
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
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
        } catch (Throwable t) {
            LOGGER.error("[NexusDecorator] Failed to decorate '{}': {}", slotId, t.getMessage(), t);
            return 0;
        }
    }

    // ========================================================================
    // SPAWN - Hub Center (64x64)
    // Welcome hub: info lecterns, direction pillars, gear chest, lighting
    // ========================================================================

    private void decorateSpawn(ServerLevel level, BlockPos center) {
        int y = center.getY() + 1;
        int cx = center.getX();
        int cz = center.getZ();

        // Central hologram projector on pillar
        place(level, new BlockPos(cx, y, cz), NexusDecorBlocks.NEXUS_CORE);
        place(level, new BlockPos(cx, y + 1, cz), NexusDecorBlocks.NEXUS_CORE);
        place(level, new BlockPos(cx, y + 2, cz), NexusDecorBlocks.NEXUS_REACTOR);
        place(level, new BlockPos(cx, y + 3, cz), HologramBlocks.HOLOGRAM_PROJECTOR);

        // Four quartz pillars at corners (6 blocks from center)
        for (int[] offset : new int[][]{{-6, -6}, {6, -6}, {-6, 6}, {6, 6}}) {
            for (int dy = 0; dy < 4; dy++) {
                place(level, new BlockPos(cx + offset[0], y + dy, cz + offset[1]),
                    Blocks.QUARTZ_PILLAR);
            }
            place(level, new BlockPos(cx + offset[0], y + 4, cz + offset[1]),
                Blocks.SEA_LANTERN);
        }

        // Information lecterns at cardinal points
        place(level, new BlockPos(cx, y, cz - 4), Blocks.LECTERN);
        place(level, new BlockPos(cx, y, cz + 4), Blocks.LECTERN);
        place(level, new BlockPos(cx - 4, y, cz), Blocks.LECTERN);
        place(level, new BlockPos(cx + 4, y, cz), Blocks.LECTERN);

        // Bookshelves behind each lectern
        place(level, new BlockPos(cx - 1, y, cz - 5), Blocks.BOOKSHELF);
        place(level, new BlockPos(cx + 1, y, cz - 5), Blocks.BOOKSHELF);
        place(level, new BlockPos(cx - 1, y, cz + 5), Blocks.BOOKSHELF);
        place(level, new BlockPos(cx + 1, y, cz + 5), Blocks.BOOKSHELF);
        place(level, new BlockPos(cx - 5, y, cz - 1), Blocks.BOOKSHELF);
        place(level, new BlockPos(cx - 5, y, cz + 1), Blocks.BOOKSHELF);
        place(level, new BlockPos(cx + 5, y, cz - 1), Blocks.BOOKSHELF);
        place(level, new BlockPos(cx + 5, y, cz + 1), Blocks.BOOKSHELF);

        // Floor pattern: decorative ring
        for (int angle = 0; angle < 360; angle += 10) {
            double rad = Math.toRadians(angle);
            int rx = cx + (int) (8 * Math.cos(rad));
            int rz = cz + (int) (8 * Math.sin(rad));
            place(level, new BlockPos(rx, y - 1, rz), NexusDecorBlocks.NEXUS_GLOW_STRIP_CYAN);
        }

        // Starter gear chest (north-east)
        BlockPos starterChest = new BlockPos(cx + 3, y, cz - 3);
        placeChest(level, starterChest, Direction.SOUTH);
        fillChest(level, starterChest,
            new ItemStack(Items.DIAMOND_SWORD),
            new ItemStack(Items.DIAMOND_PICKAXE),
            new ItemStack(Items.DIAMOND_AXE),
            new ItemStack(Items.DIAMOND_SHOVEL),
            new ItemStack(Items.BOW),
            new ItemStack(Items.ARROW, 64),
            new ItemStack(Items.SHIELD),
            new ItemStack(Items.GOLDEN_APPLE, 16),
            new ItemStack(Items.ENDER_PEARL, 16),
            new ItemStack(Items.TORCH, 64));

        // Decorative lanterns
        placeLantern(level, new BlockPos(cx - 3, y, cz - 3));
        placeLantern(level, new BlockPos(cx + 3, y, cz + 3));
        placeLantern(level, new BlockPos(cx - 3, y, cz + 3));

        // Entity scanner
        place(level, new BlockPos(cx, y, cz - 8), DebugBlocks.ENTITY_SCANNER);
    }

    // ========================================================================
    // COMBAT_LAB - North (96x96)
    // Full combat workstation: arena, weapon/armor chests, enchanting,
    // brewing, target range, training dummies
    // ========================================================================

    private void decorateCombatLab(ServerLevel level, BlockPos center) {
        int y = center.getY() + 1;
        int cx = center.getX();
        int cz = center.getZ();

        // === ARENA FLOOR (24x24 hazard boundary) ===
        for (int i = -12; i <= 12; i++) {
            place(level, new BlockPos(cx + i, y, cz - 12), NexusDecorBlocks.NEXUS_HAZARD);
            place(level, new BlockPos(cx + i, y, cz + 12), NexusDecorBlocks.NEXUS_HAZARD);
            if (i == -12 || i == 12) {
                for (int j = -11; j <= 11; j++) {
                    place(level, new BlockPos(cx + i, y, cz + j), NexusDecorBlocks.NEXUS_HAZARD);
                }
            }
        }

        // === CORNER PILLARS with sea lanterns ===
        for (int[] corner : new int[][]{{-12, -12}, {12, -12}, {-12, 12}, {12, 12}}) {
            for (int dy = 0; dy < 3; dy++) {
                place(level, new BlockPos(cx + corner[0], y + dy, cz + corner[1]),
                    NexusDecorBlocks.NEXUS_STEEL);
            }
            place(level, new BlockPos(cx + corner[0], y + 3, cz + corner[1]),
                Blocks.SEA_LANTERN);
        }

        // === EAST: ARMOR DISPLAY WALL ===
        // Back wall
        for (int dz = -4; dz <= 4; dz++) {
            place(level, new BlockPos(cx + 16, y, cz + dz), NexusDecorBlocks.NEXUS_PANEL);
            place(level, new BlockPos(cx + 16, y + 1, cz + dz), NexusDecorBlocks.NEXUS_PANEL);
            place(level, new BlockPos(cx + 16, y + 2, cz + dz), NexusDecorBlocks.NEXUS_DISPLAY);
        }
        // Mannequins in front of wall
        place(level, new BlockPos(cx + 15, y, cz - 3), CloneBlocks.NEUROCELL_MANNEQUIN);
        place(level, new BlockPos(cx + 15, y, cz), CloneBlocks.NEUROCELL_MANNEQUIN);
        place(level, new BlockPos(cx + 15, y, cz + 3), CloneBlocks.NEUROCELL_MANNEQUIN);
        // Armor chest
        BlockPos armorChest = new BlockPos(cx + 14, y, cz - 5);
        placeChest(level, armorChest, Direction.WEST);
        fillChest(level, armorChest,
            new ItemStack(Items.DIAMOND_HELMET),
            new ItemStack(Items.DIAMOND_CHESTPLATE),
            new ItemStack(Items.DIAMOND_LEGGINGS),
            new ItemStack(Items.DIAMOND_BOOTS),
            new ItemStack(Items.IRON_HELMET),
            new ItemStack(Items.IRON_CHESTPLATE),
            new ItemStack(Items.IRON_LEGGINGS),
            new ItemStack(Items.IRON_BOOTS),
            new ItemStack(Items.NETHERITE_CHESTPLATE),
            new ItemStack(Items.SHIELD));

        // === WEST: WEAPON RACK WALL ===
        for (int dz = -4; dz <= 4; dz++) {
            place(level, new BlockPos(cx - 16, y, cz + dz), NexusDecorBlocks.NEXUS_PANEL);
            place(level, new BlockPos(cx - 16, y + 1, cz + dz), NexusDecorBlocks.NEXUS_PANEL);
            place(level, new BlockPos(cx - 16, y + 2, cz + dz), NexusDecorBlocks.NEXUS_DISPLAY);
        }
        // Weapon item displays
        place(level, new BlockPos(cx - 15, y, cz - 3), CloneBlocks.NEUROCELL_ITEM);
        place(level, new BlockPos(cx - 15, y, cz), CloneBlocks.NEUROCELL_ITEM);
        place(level, new BlockPos(cx - 15, y, cz + 3), CloneBlocks.NEUROCELL_ITEM);
        // Weapon chest
        BlockPos weaponChest = new BlockPos(cx - 14, y, cz - 5);
        placeChest(level, weaponChest, Direction.EAST);
        fillChest(level, weaponChest,
            new ItemStack(Items.DIAMOND_SWORD),
            new ItemStack(Items.NETHERITE_SWORD),
            new ItemStack(Items.IRON_SWORD),
            new ItemStack(Items.BOW),
            new ItemStack(Items.CROSSBOW),
            new ItemStack(Items.TRIDENT),
            new ItemStack(Items.ARROW, 64),
            new ItemStack(Items.SPECTRAL_ARROW, 32),
            new ItemStack(Items.TIPPED_ARROW, 16),
            new ItemStack(Items.MACE));

        // === NORTH: ENCHANTING CORNER ===
        place(level, new BlockPos(cx, y, cz - 16), Blocks.ENCHANTING_TABLE);
        // Bookshelves surrounding enchanting table (3 sides)
        for (int dx = -2; dx <= 2; dx++) {
            place(level, new BlockPos(cx + dx, y, cz - 18), Blocks.BOOKSHELF);
            place(level, new BlockPos(cx + dx, y + 1, cz - 18), Blocks.BOOKSHELF);
        }
        place(level, new BlockPos(cx - 2, y, cz - 17), Blocks.BOOKSHELF);
        place(level, new BlockPos(cx + 2, y, cz - 17), Blocks.BOOKSHELF);
        place(level, new BlockPos(cx - 2, y + 1, cz - 17), Blocks.BOOKSHELF);
        place(level, new BlockPos(cx + 2, y + 1, cz - 17), Blocks.BOOKSHELF);
        // Anvil and grindstone
        place(level, new BlockPos(cx - 4, y, cz - 16), Blocks.ANVIL);
        place(level, new BlockPos(cx + 4, y, cz - 16), Blocks.GRINDSTONE);
        placeLantern(level, new BlockPos(cx - 4, y, cz - 18));
        placeLantern(level, new BlockPos(cx + 4, y, cz - 18));

        // === SOUTH: BREWING & POTIONS ===
        place(level, new BlockPos(cx - 2, y, cz + 16), Blocks.BREWING_STAND);
        place(level, new BlockPos(cx + 2, y, cz + 16), Blocks.BREWING_STAND);
        place(level, new BlockPos(cx, y, cz + 16), Blocks.CAULDRON);
        // Potion ingredient chests
        BlockPos potionChest = new BlockPos(cx - 4, y, cz + 16);
        placeChest(level, potionChest, Direction.NORTH);
        fillChest(level, potionChest,
            new ItemStack(Items.NETHER_WART, 32),
            new ItemStack(Items.BLAZE_POWDER, 32),
            new ItemStack(Items.GLOWSTONE_DUST, 32),
            new ItemStack(Items.REDSTONE, 32),
            new ItemStack(Items.SPIDER_EYE, 16),
            new ItemStack(Items.GOLDEN_CARROT, 16),
            new ItemStack(Items.GHAST_TEAR, 8),
            new ItemStack(Items.GLASS_BOTTLE, 64),
            new ItemStack(Items.MAGMA_CREAM, 16),
            new ItemStack(Items.PHANTOM_MEMBRANE, 8));
        // Barrel for extra storage
        place(level, new BlockPos(cx + 4, y, cz + 16), Blocks.BARREL);

        // === TARGET RANGE (south-east) ===
        for (int dz = -2; dz <= 2; dz++) {
            place(level, new BlockPos(cx + 20, y, cz + dz), Blocks.TARGET);
            place(level, new BlockPos(cx + 20, y + 1, cz + dz), Blocks.TARGET);
        }

        // === CENTER: entity scanner + hologram ===
        place(level, new BlockPos(cx, y, cz + 10), DebugBlocks.ENTITY_SCANNER);
        place(level, new BlockPos(cx, y, cz - 10), HologramBlocks.HOLOGRAM_PROJECTOR);

        // Neon accent strips along outer edges
        for (int i = -8; i <= 8; i += 4) {
            place(level, new BlockPos(cx + i, y, cz - 13), NexusDecorBlocks.NEXUS_GLOW_STRIP_CYAN);
            place(level, new BlockPos(cx + i, y, cz + 13), NexusDecorBlocks.NEXUS_GLOW_STRIP_CYAN);
        }
    }

    // ========================================================================
    // ABILITIES_LAB - East (96x96)
    // Movement testing: parkour, obstacles, ice, soul sand, water, ladders
    // ========================================================================

    private void decorateAbilitiesLab(ServerLevel level, BlockPos center) {
        int y = center.getY();
        int cx = center.getX();
        int cz = center.getZ();

        // === PARKOUR COURSE (east side): 8 platforms at increasing heights ===
        for (int i = 0; i < 8; i++) {
            int px = cx - 16 + (i * 4);
            int py = y + 1 + i;
            // Platform base (3x3) with alternating materials
            Block mat = (i % 2 == 0) ? NexusDecorBlocks.NEXUS_AZURE.get()
                                     : NexusDecorBlocks.NEXUS_PANEL.get();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    place(level, new BlockPos(px + dx, py, cz - 12 + dz), mat);
                }
            }
            // Glow marker on leading edge
            place(level, new BlockPos(px, py, cz - 13), NexusDecorBlocks.NEXUS_GLOW_STRIP_CYAN);
        }

        // === ICE TRACK (north side) - slippery movement testing ===
        for (int dx = -8; dx <= 8; dx++) {
            place(level, new BlockPos(cx + dx, y + 1, cz - 18), Blocks.PACKED_ICE);
            place(level, new BlockPos(cx + dx, y + 1, cz - 19), Blocks.BLUE_ICE);
            place(level, new BlockPos(cx + dx, y + 1, cz - 20), Blocks.PACKED_ICE);
        }

        // === SOUL SAND STRIP (center-north) - slow movement ===
        for (int dx = -6; dx <= 6; dx++) {
            place(level, new BlockPos(cx + dx, y + 1, cz - 6), Blocks.SOUL_SAND);
            place(level, new BlockPos(cx + dx, y + 1, cz - 7), Blocks.SOUL_SAND);
        }

        // === HONEY BLOCK SECTION (south) - sticky jump testing ===
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                place(level, new BlockPos(cx + dx, y + 1, cz + 12 + dz), Blocks.HONEY_BLOCK);
            }
        }
        // Slime blocks for bounce testing next to honey
        for (int dx = -3; dx <= 3; dx++) {
            place(level, new BlockPos(cx + dx, y + 1, cz + 16), Blocks.SLIME_BLOCK);
        }

        // === LADDER WALL (west) - climbing test ===
        for (int dy = 0; dy < 8; dy++) {
            place(level, new BlockPos(cx - 20, y + 1 + dy, cz), NexusDecorBlocks.NEXUS_STEEL);
            place(level, new BlockPos(cx - 20, y + 1 + dy, cz + 1), NexusDecorBlocks.NEXUS_STEEL);
            place(level, new BlockPos(cx - 20, y + 1 + dy, cz - 1), NexusDecorBlocks.NEXUS_STEEL);
        }
        // Landing platform at top
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                place(level, new BlockPos(cx - 19 + dx, y + 8, cz + dz), NexusDecorBlocks.NEXUS_AZURE);
            }
        }

        // === DASH DISTANCE MARKERS on floor (south side) ===
        for (int dist : new int[]{5, 10, 15, 20}) {
            for (int dx = -1; dx <= 1; dx++) {
                place(level, new BlockPos(cx + dx, y + 1, cz + dist), NexusDecorBlocks.NEXUS_SIGNAL);
            }
        }

        // Entity scanner + hologram
        place(level, new BlockPos(cx, y + 1, cz - 8), DebugBlocks.ENTITY_SCANNER);
        place(level, new BlockPos(cx + 15, y + 1, cz), HologramBlocks.HOLOGRAM_PROJECTOR);

        // Potion chest for movement buffs
        BlockPos potionChest = new BlockPos(cx + 12, y + 1, cz - 8);
        placeChest(level, potionChest, Direction.SOUTH);
        fillChest(level, potionChest,
            new ItemStack(Items.GOLDEN_APPLE, 16),
            new ItemStack(Items.ENDER_PEARL, 16),
            new ItemStack(Items.ELYTRA),
            new ItemStack(Items.FIREWORK_ROCKET, 64),
            new ItemStack(Items.FEATHER, 64));
    }

    // ========================================================================
    // BOSS_ARENA - South (96x96)
    // Boss fight testing: arena ring, spectator area, healing station
    // ========================================================================

    private void decorateBossArena(ServerLevel level, BlockPos center) {
        int y = center.getY() + 1;
        int cx = center.getX();
        int cz = center.getZ();

        // === CIRCULAR ARENA RING (radius 16) ===
        int radius = 16;
        for (int angle = 0; angle < 360; angle += 5) {
            double rad = Math.toRadians(angle);
            int rx = cx + (int) (radius * Math.cos(rad));
            int rz = cz + (int) (radius * Math.sin(rad));
            place(level, new BlockPos(rx, y, rz), NexusDecorBlocks.NEXUS_REACTOR);
        }
        // Ring wall (2 blocks high)
        for (int angle = 0; angle < 360; angle += 5) {
            double rad = Math.toRadians(angle);
            int rx = cx + (int) (radius * Math.cos(rad));
            int rz = cz + (int) (radius * Math.sin(rad));
            place(level, new BlockPos(rx, y + 1, rz), Blocks.IRON_BARS);
        }

        // === CARDINAL PILLARS with lighting ===
        for (int[] dir : new int[][]{{0, -radius - 1}, {0, radius + 1},
                                      {-radius - 1, 0}, {radius + 1, 0}}) {
            for (int dy = 0; dy < 4; dy++) {
                place(level, new BlockPos(cx + dir[0], y + dy, cz + dir[1]),
                    NexusDecorBlocks.NEXUS_COBALT);
            }
            place(level, new BlockPos(cx + dir[0], y + 4, cz + dir[1]),
                Blocks.SEA_LANTERN);
        }

        // === SPECTATOR SEATING (north side) ===
        for (int row = 0; row < 3; row++) {
            for (int dx = -6; dx <= 6; dx++) {
                // Deepslate brick stairs as seats
                place(level, new BlockPos(cx + dx, y + row, cz - radius - 3 - row),
                    Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.SOUTH));
            }
        }

        // === HEALING STATION (south, outside arena) ===
        place(level, new BlockPos(cx - 2, y, cz + radius + 3), Blocks.BREWING_STAND);
        place(level, new BlockPos(cx + 2, y, cz + radius + 3), Blocks.CAULDRON);
        BlockPos healChest = new BlockPos(cx, y, cz + radius + 4);
        placeChest(level, healChest, Direction.NORTH);
        fillChest(level, healChest,
            new ItemStack(Items.GOLDEN_APPLE, 32),
            new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 4),
            new ItemStack(Items.COOKED_BEEF, 64),
            new ItemStack(Items.TOTEM_OF_UNDYING),
            new ItemStack(Items.SHIELD),
            new ItemStack(Items.DIAMOND_SWORD),
            new ItemStack(Items.NETHERITE_CHESTPLATE),
            new ItemStack(Items.ARROW, 64));

        // Entrance hazard markers (north, gap in ring)
        for (int i = -3; i <= 3; i++) {
            place(level, new BlockPos(cx + i, y, cz - radius - 2), NexusDecorBlocks.NEXUS_HAZARD);
        }

        // Hologram projectors for boss phase display
        place(level, new BlockPos(cx - radius - 3, y, cz), HologramBlocks.HOLOGRAM_PROJECTOR);
        place(level, new BlockPos(cx + radius + 3, y, cz), HologramBlocks.HOLOGRAM_PROJECTOR);

        // Floor lights inside arena (4 quadrant lights)
        place(level, new BlockPos(cx - 8, y, cz - 8), NexusDecorBlocks.NEXUS_FLOOR_LIGHT);
        place(level, new BlockPos(cx + 8, y, cz - 8), NexusDecorBlocks.NEXUS_FLOOR_LIGHT);
        place(level, new BlockPos(cx - 8, y, cz + 8), NexusDecorBlocks.NEXUS_FLOOR_LIGHT);
        place(level, new BlockPos(cx + 8, y, cz + 8), NexusDecorBlocks.NEXUS_FLOOR_LIGHT);
    }

    // ========================================================================
    // PORTAL_LAB - West (96x96)
    // Portal/transport testing: telepads, warp core, rune display
    // ========================================================================

    private void decoratePortalLab(ServerLevel level, BlockPos center) {
        int y = center.getY() + 1;
        int cx = center.getX();
        int cz = center.getZ();

        // 4 telepads in compass formation with pedestals
        for (int[] tp : new int[][]{{0, -10}, {0, 10}, {-10, 0}, {10, 0}}) {
            place(level, new BlockPos(cx + tp[0], y, cz + tp[1]), CloneBlocks.TELEPAD);
            place(level, new BlockPos(cx + tp[0], y - 1, cz + tp[1]), NexusDecorBlocks.NEXUS_TELEPAD_CORE);
            // Decorative ring around each telepad
            for (int[] ring : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                place(level, new BlockPos(cx + tp[0] + ring[0], y - 1, cz + tp[1] + ring[1]),
                    NexusDecorBlocks.NEXUS_TELEPAD_RING);
            }
        }

        // Center: Warp core cluster
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

        // East: Rune block display wall with shelving
        for (int dz = -5; dz <= 5; dz++) {
            place(level, new BlockPos(cx + 16, y, cz + dz), NexusDecorBlocks.NEXUS_PANEL_DARK);
            place(level, new BlockPos(cx + 16, y + 1, cz + dz), NexusDecorBlocks.NEXUS_PANEL_DARK);
            place(level, new BlockPos(cx + 16, y + 2, cz + dz), NexusDecorBlocks.NEXUS_DISPLAY);
        }
        // Rune pedestals
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
        // Lanterns beside rune wall
        placeLantern(level, new BlockPos(cx + 15, y, cz - 6));
        placeLantern(level, new BlockPos(cx + 15, y, cz + 6));

        // Glow strip paths connecting telepads to center
        for (int i = -9; i <= 9; i++) {
            if (Math.abs(i) > 1) {
                place(level, new BlockPos(cx + i, y, cz), NexusDecorBlocks.NEXUS_GLOW_STRIP_CYAN);
                place(level, new BlockPos(cx, y, cz + i), NexusDecorBlocks.NEXUS_GLOW_STRIP_CYAN);
            }
        }

        // Info lectern
        place(level, new BlockPos(cx - 12, y, cz), Blocks.LECTERN);
    }

    // ========================================================================
    // NPC_LAB - North-East (96x96)
    // Clone/NPC testing: full processing chain, dialog office, bio storage
    // ========================================================================

    private void decorateNpcLab(ServerLevel level, BlockPos center) {
        int y = center.getY() + 1;
        int cx = center.getX();
        int cz = center.getZ();

        // === CLONE PROCESSING CHAIN (center, east-west) ===
        place(level, new BlockPos(cx - 8, y, cz), CloneBlocks.IMPRINTER);
        place(level, new BlockPos(cx - 6, y, cz), CloneBlocks.NEUROLINK);
        place(level, new BlockPos(cx - 4, y, cz), CloneBlocks.NEUROLINK);
        place(level, new BlockPos(cx - 2, y, cz), CloneBlocks.NEUROLINK);
        place(level, new BlockPos(cx, y, cz), CloneBlocks.NEUROCELL);
        place(level, new BlockPos(cx + 2, y, cz), CloneBlocks.NEUROLINK);
        place(level, new BlockPos(cx + 4, y, cz), CloneBlocks.NEUROLINK);
        place(level, new BlockPos(cx + 6, y, cz), CloneBlocks.NEUROLINK);
        place(level, new BlockPos(cx + 8, y, cz), CloneBlocks.REFORMER);
        // Data strip under chain
        for (int i = -9; i <= 9; i++) {
            place(level, new BlockPos(cx + i, y - 1, cz), NexusDecorBlocks.NEXUS_DATA);
        }

        // === MANNEQUIN SHOWCASE (north wall) ===
        // Back wall with display panels
        for (int dx = -8; dx <= 8; dx++) {
            place(level, new BlockPos(cx + dx, y, cz - 12), NexusDecorBlocks.NEXUS_PANEL);
            place(level, new BlockPos(cx + dx, y + 1, cz - 12), NexusDecorBlocks.NEXUS_DISPLAY);
            place(level, new BlockPos(cx + dx, y + 2, cz - 12), NexusDecorBlocks.NEXUS_PANEL);
        }
        // Mannequins in front of wall
        for (int dx = -6; dx <= 6; dx += 3) {
            place(level, new BlockPos(cx + dx, y, cz - 10), CloneBlocks.NEUROCELL_MANNEQUIN);
        }
        // Item displays flanking
        place(level, new BlockPos(cx - 8, y, cz - 10), CloneBlocks.NEUROCELL_ITEM);
        place(level, new BlockPos(cx + 8, y, cz - 10), CloneBlocks.NEUROCELL_ITEM);

        // === DIALOG OFFICE (south-west) ===
        // Desk
        for (int dx = -6; dx <= -3; dx++) {
            place(level, new BlockPos(cx + dx, y, cz + 8), NexusDecorBlocks.NEXUS_TERMINAL);
        }
        // Bookshelves behind desk
        for (int dx = -7; dx <= -2; dx++) {
            place(level, new BlockPos(cx + dx, y, cz + 10), Blocks.BOOKSHELF);
            place(level, new BlockPos(cx + dx, y + 1, cz + 10), Blocks.BOOKSHELF);
        }
        // Lecterns for dialog trees
        place(level, new BlockPos(cx - 5, y, cz + 6), Blocks.LECTERN);
        place(level, new BlockPos(cx - 3, y, cz + 6), Blocks.LECTERN);
        placeLantern(level, new BlockPos(cx - 7, y, cz + 6));

        // === BIO STORAGE (south-east) ===
        // Barrels and chests for bioscanner data
        place(level, new BlockPos(cx + 6, y, cz + 8), Blocks.BARREL);
        place(level, new BlockPos(cx + 7, y, cz + 8), Blocks.BARREL);
        place(level, new BlockPos(cx + 8, y, cz + 8), Blocks.BARREL);
        BlockPos bioChest = new BlockPos(cx + 6, y, cz + 10);
        placeChest(level, bioChest, Direction.NORTH);

        // Entity scanner + hologram
        place(level, new BlockPos(cx + 12, y, cz), DebugBlocks.ENTITY_SCANNER);
        place(level, new BlockPos(cx - 12, y, cz), HologramBlocks.HOLOGRAM_PROJECTOR);

        // Lighting
        placeLantern(level, new BlockPos(cx - 8, y, cz - 8));
        placeLantern(level, new BlockPos(cx + 8, y, cz - 8));
        placeLantern(level, new BlockPos(cx + 12, y, cz + 8));
    }

    // ========================================================================
    // VFX_STUDIO - North-West (96x96)
    // Effects testing: dark room, contrast walls, VFX targets, clone machines
    // ========================================================================

    private void decorateVfxStudio(ServerLevel level, BlockPos center) {
        int y = center.getY() + 1;
        int cx = center.getX();
        int cz = center.getZ();

        // === DARK ROOM: void floor ring ===
        for (int dx = -14; dx <= 14; dx++) {
            for (int dz = -14; dz <= 14; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist >= 12 && dist <= 14) {
                    place(level, new BlockPos(cx + dx, y - 1, cz + dz), NexusDecorBlocks.NEXUS_VOID);
                }
            }
        }

        // === CONTRAST WALLS (3 sides, 3 blocks high) ===
        // South wall: pure white for light VFX
        for (int dx = -8; dx <= 8; dx++) {
            for (int dy = 0; dy < 3; dy++) {
                place(level, new BlockPos(cx + dx, y + dy, cz + 10), Blocks.WHITE_CONCRETE);
            }
        }
        // North wall: black for dark VFX
        for (int dx = -8; dx <= 8; dx++) {
            for (int dy = 0; dy < 3; dy++) {
                place(level, new BlockPos(cx + dx, y + dy, cz - 10), Blocks.BLACK_CONCRETE);
            }
        }
        // West wall: mixed gray gradient
        for (int dz = -9; dz <= 9; dz++) {
            place(level, new BlockPos(cx - 10, y, cz + dz), Blocks.GRAY_CONCRETE);
            place(level, new BlockPos(cx - 10, y + 1, cz + dz), Blocks.LIGHT_GRAY_CONCRETE);
            place(level, new BlockPos(cx - 10, y + 2, cz + dz), Blocks.WHITE_CONCRETE);
        }

        // === NEON ACCENT RING ===
        for (int angle = 0; angle < 360; angle += 45) {
            double rad = Math.toRadians(angle);
            int rx = cx + (int) (10 * Math.cos(rad));
            int rz = cz + (int) (10 * Math.sin(rad));
            place(level, new BlockPos(rx, y, rz), NexusDecorBlocks.NEXUS_NEON);
        }

        // Hologram projectors at 4 corners
        place(level, new BlockPos(cx - 8, y, cz - 8), HologramBlocks.HOLOGRAM_PROJECTOR);
        place(level, new BlockPos(cx + 8, y, cz - 8), HologramBlocks.HOLOGRAM_PROJECTOR);
        place(level, new BlockPos(cx - 8, y, cz + 8), HologramBlocks.HOLOGRAM_PROJECTOR);
        place(level, new BlockPos(cx + 8, y, cz + 8), HologramBlocks.HOLOGRAM_PROJECTOR);

        // Center: entity scanner
        place(level, new BlockPos(cx, y, cz), DebugBlocks.ENTITY_SCANNER);

        // Clone machines for visual richness
        place(level, new BlockPos(cx + 6, y, cz - 4), CloneBlocks.CLONE_LASER_ARM);
        place(level, new BlockPos(cx - 6, y, cz - 4), CloneBlocks.CLONE_PROCESSOR);
        place(level, new BlockPos(cx + 6, y, cz + 4), CloneBlocks.CLONE_DRILL);

        // Glow strip accents
        for (int i = -6; i <= 6; i += 2) {
            place(level, new BlockPos(cx + i, y, cz - 7), NexusDecorBlocks.NEXUS_GLOW_STRIP_WHITE);
            place(level, new BlockPos(cx + i, y, cz + 7), NexusDecorBlocks.NEXUS_GLOW_STRIP_WHITE);
        }
    }

    // ========================================================================
    // COLLISION_LAB - South-East (96x96)
    // Hitbox testing: grid floor, measurement walls, varied block heights
    // ========================================================================

    private void decorateCollisionLab(ServerLevel level, BlockPos center) {
        int y = center.getY();
        int cx = center.getX();
        int cz = center.getZ();

        // === MEASUREMENT GRID FLOOR (16x16) ===
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                if (dx % 4 == 0 || dz % 4 == 0) {
                    place(level, new BlockPos(cx + dx, y, cz + dz), NexusDecorBlocks.NEXUS_GRID);
                }
            }
        }

        // === HEIGHT TEST BLOCKS (east side) - various block heights ===
        // Slabs
        place(level, new BlockPos(cx + 12, y + 1, cz - 4), Blocks.STONE_BRICK_SLAB);
        place(level, new BlockPos(cx + 12, y + 1, cz - 2), Blocks.STONE_BRICK_SLAB.defaultBlockState()
            .setValue(SlabBlock.TYPE, SlabType.TOP));
        // Stairs
        place(level, new BlockPos(cx + 12, y + 1, cz), Blocks.STONE_BRICK_STAIRS);
        // Fences (1.5 height)
        place(level, new BlockPos(cx + 12, y + 1, cz + 2), Blocks.OAK_FENCE);
        // Walls (1.5 height)
        place(level, new BlockPos(cx + 12, y + 1, cz + 4), Blocks.STONE_BRICK_WALL);

        // === MEASUREMENT WALL (west) with graduated heights ===
        for (int height = 1; height <= 5; height++) {
            for (int dy = 0; dy < height; dy++) {
                place(level, new BlockPos(cx - 12, y + 1 + dy, cz - 4 + (height * 2)),
                    NexusDecorBlocks.NEXUS_STEEL);
            }
        }

        // Distance markers
        for (int dist : new int[]{5, 10, 15}) {
            place(level, new BlockPos(cx + dist, y + 1, cz), NexusDecorBlocks.NEXUS_SIGNAL);
            place(level, new BlockPos(cx - dist, y + 1, cz), NexusDecorBlocks.NEXUS_SIGNAL);
            place(level, new BlockPos(cx, y + 1, cz + dist), NexusDecorBlocks.NEXUS_SIGNAL);
            place(level, new BlockPos(cx, y + 1, cz - dist), NexusDecorBlocks.NEXUS_SIGNAL);
        }

        // Entity scanners
        place(level, new BlockPos(cx - 6, y + 1, cz + 3), DebugBlocks.ENTITY_SCANNER);
        place(level, new BlockPos(cx, y + 1, cz + 3), DebugBlocks.ENTITY_SCANNER);
        place(level, new BlockPos(cx + 6, y + 1, cz + 3), DebugBlocks.ENTITY_SCANNER);

        // Hologram + corner lights
        place(level, new BlockPos(cx, y + 1, cz - 10), HologramBlocks.HOLOGRAM_PROJECTOR);
        place(level, new BlockPos(cx - 8, y + 1, cz - 8), Blocks.SEA_LANTERN);
        place(level, new BlockPos(cx + 8, y + 1, cz - 8), Blocks.SEA_LANTERN);
        place(level, new BlockPos(cx - 8, y + 1, cz + 8), Blocks.SEA_LANTERN);
        place(level, new BlockPos(cx + 8, y + 1, cz + 8), Blocks.SEA_LANTERN);
    }

    // ========================================================================
    // ARENA_BUILDER - South-West (96x96)
    // Area/zone building: editors, block palette, template preview
    // ========================================================================

    private void decorateArenaBuilder(ServerLevel level, BlockPos center) {
        int y = center.getY() + 1;
        int cx = center.getX();
        int cz = center.getZ();

        // Center: area editor
        place(level, new BlockPos(cx, y, cz), AreaBlocks.AREA_EDITOR);
        place(level, new BlockPos(cx, y, cz - 5), AreaBlocks.NEXUS_EDITOR_CENTRAL);

        // Zone markers in a square (8 block sides)
        for (int[] m : new int[][]{{-8, -8}, {8, -8}, {-8, 8}, {8, 8}}) {
            place(level, new BlockPos(cx + m[0], y, cz + m[1]), ZoneBlocks.ZONE_MARKER);
        }

        // Hologram projector for template previews
        place(level, new BlockPos(cx, y, cz + 5), HologramBlocks.HOLOGRAM_PROJECTOR);

        // === BLOCK PALETTE SHOWCASE: West wall (solid blocks) ===
        List<DeferredHolder<Block, ?>> solidPalette = List.of(
            NexusDecorBlocks.NEXUS_PANEL, NexusDecorBlocks.NEXUS_PANEL_DARK,
            NexusDecorBlocks.NEXUS_TILE, NexusDecorBlocks.NEXUS_TILE_PURPLE,
            NexusDecorBlocks.NEXUS_GRID, NexusDecorBlocks.NEXUS_GRID_GOLD,
            NexusDecorBlocks.NEXUS_PLATING, NexusDecorBlocks.NEXUS_CONDUIT,
            NexusDecorBlocks.NEXUS_CIRCUIT, NexusDecorBlocks.NEXUS_CIRCUIT_GOLD,
            NexusDecorBlocks.NEXUS_STEEL, NexusDecorBlocks.NEXUS_CARBON,
            NexusDecorBlocks.NEXUS_BRONZE, NexusDecorBlocks.NEXUS_COPPER,
            NexusDecorBlocks.NEXUS_COBALT, NexusDecorBlocks.NEXUS_ONYX
        );
        for (int i = 0; i < solidPalette.size(); i++) {
            int row = i / 8;
            int col = i % 8;
            place(level, new BlockPos(cx - 12, y + row, cz - 4 + col), solidPalette.get(i));
        }

        // === BLOCK PALETTE SHOWCASE: East wall (glow/special blocks) ===
        List<DeferredHolder<Block, ?>> glowPalette = List.of(
            NexusDecorBlocks.NEXUS_AZURE, NexusDecorBlocks.NEXUS_AZURE_DARK,
            NexusDecorBlocks.NEXUS_PLASMA, NexusDecorBlocks.NEXUS_PLASMA_DARK,
            NexusDecorBlocks.NEXUS_MATRIX, NexusDecorBlocks.NEXUS_MATRIX_LIGHT,
            NexusDecorBlocks.NEXUS_ENERGY, NexusDecorBlocks.NEXUS_ENERGY_LIGHT,
            NexusDecorBlocks.NEXUS_CRYSTAL, NexusDecorBlocks.NEXUS_CRYSTAL_LIGHT,
            NexusDecorBlocks.NEXUS_REACTOR, NexusDecorBlocks.NEXUS_REACTOR_LIGHT,
            NexusDecorBlocks.NEXUS_NEON, NexusDecorBlocks.NEXUS_NEON_BLUE,
            NexusDecorBlocks.NEXUS_QUANTUM, NexusDecorBlocks.NEXUS_HOLO
        );
        for (int i = 0; i < glowPalette.size(); i++) {
            int row = i / 8;
            int col = i % 8;
            place(level, new BlockPos(cx + 12, y + row, cz - 4 + col), glowPalette.get(i));
        }

        // === BUILDING BLOCKS CHEST ===
        BlockPos buildChest = new BlockPos(cx + 4, y, cz - 4);
        placeChest(level, buildChest, Direction.SOUTH);
        fillChest(level, buildChest,
            new ItemStack(NexusDecorBlocks.NEXUS_PANEL.get().asItem(), 64),
            new ItemStack(NexusDecorBlocks.NEXUS_TILE.get().asItem(), 64),
            new ItemStack(NexusDecorBlocks.NEXUS_STEEL.get().asItem(), 64),
            new ItemStack(NexusDecorBlocks.NEXUS_AZURE.get().asItem(), 64),
            new ItemStack(NexusDecorBlocks.NEXUS_GLOW_STRIP_CYAN.get().asItem(), 64),
            new ItemStack(NexusDecorBlocks.NEXUS_GLASS_CYAN.get().asItem(), 64),
            new ItemStack(NexusDecorBlocks.NEXUS_LIGHT.get().asItem(), 64),
            new ItemStack(NexusDecorBlocks.NEXUS_VOID.get().asItem(), 64));

        // Floor accents
        for (int i = -7; i <= 7; i++) {
            place(level, new BlockPos(cx + i, y - 1, cz - 8), NexusDecorBlocks.NEXUS_GLOW_STRIP_CYAN);
            place(level, new BlockPos(cx + i, y - 1, cz + 8), NexusDecorBlocks.NEXUS_GLOW_STRIP_CYAN);
        }
    }

    // ========================================================================
    // ITEM_WORKSHOP - East Outer (72x72)
    // Full crafting workshop: all stations, filled item chests, displays
    // ========================================================================

    private void decorateItemWorkshop(ServerLevel level, BlockPos center) {
        int y = center.getY() + 1;
        int cx = center.getX();
        int cz = center.getZ();

        // === CRAFTING STATIONS (north wall) ===
        // Deepslate brick workbench counter
        for (int dx = -8; dx <= 8; dx++) {
            place(level, new BlockPos(cx + dx, y, cz - 8), Blocks.DEEPSLATE_BRICKS);
        }
        // Stations on counter
        place(level, new BlockPos(cx - 7, y + 1, cz - 8), Blocks.CRAFTING_TABLE);
        place(level, new BlockPos(cx - 5, y + 1, cz - 8), Blocks.STONECUTTER);
        place(level, new BlockPos(cx - 3, y + 1, cz - 8), Blocks.SMITHING_TABLE);
        place(level, new BlockPos(cx - 1, y + 1, cz - 8), Blocks.ANVIL);
        place(level, new BlockPos(cx + 1, y + 1, cz - 8), Blocks.GRINDSTONE);
        place(level, new BlockPos(cx + 3, y + 1, cz - 8), Blocks.LOOM);
        place(level, new BlockPos(cx + 5, y + 1, cz - 8), Blocks.CARTOGRAPHY_TABLE);
        place(level, new BlockPos(cx + 7, y + 1, cz - 8), Blocks.ENCHANTING_TABLE);
        // Bookshelves behind
        for (int dx = -8; dx <= 8; dx++) {
            place(level, new BlockPos(cx + dx, y, cz - 10), Blocks.BOOKSHELF);
            place(level, new BlockPos(cx + dx, y + 1, cz - 10), Blocks.BOOKSHELF);
        }
        // Lighting
        place(level, new BlockPos(cx - 8, y + 2, cz - 10), Blocks.SEA_LANTERN);
        place(level, new BlockPos(cx, y + 2, cz - 10), Blocks.SEA_LANTERN);
        place(level, new BlockPos(cx + 8, y + 2, cz - 10), Blocks.SEA_LANTERN);

        // === WEAPON CHEST (west) ===
        BlockPos weaponChest = new BlockPos(cx - 8, y, cz - 3);
        placeChest(level, weaponChest, Direction.EAST);
        fillChest(level, weaponChest,
            new ItemStack(Items.WOODEN_SWORD),
            new ItemStack(Items.STONE_SWORD),
            new ItemStack(Items.IRON_SWORD),
            new ItemStack(Items.GOLDEN_SWORD),
            new ItemStack(Items.DIAMOND_SWORD),
            new ItemStack(Items.NETHERITE_SWORD),
            new ItemStack(Items.BOW),
            new ItemStack(Items.CROSSBOW),
            new ItemStack(Items.TRIDENT),
            new ItemStack(Items.MACE));

        // === ARMOR CHEST (west, below weapons) ===
        BlockPos armorChest = new BlockPos(cx - 8, y, cz - 1);
        placeChest(level, armorChest, Direction.EAST);
        fillChest(level, armorChest,
            new ItemStack(Items.DIAMOND_HELMET),
            new ItemStack(Items.DIAMOND_CHESTPLATE),
            new ItemStack(Items.DIAMOND_LEGGINGS),
            new ItemStack(Items.DIAMOND_BOOTS),
            new ItemStack(Items.NETHERITE_HELMET),
            new ItemStack(Items.NETHERITE_CHESTPLATE),
            new ItemStack(Items.NETHERITE_LEGGINGS),
            new ItemStack(Items.NETHERITE_BOOTS),
            new ItemStack(Items.SHIELD),
            new ItemStack(Items.ELYTRA));

        // === TOOLS CHEST (west) ===
        BlockPos toolChest = new BlockPos(cx - 8, y, cz + 1);
        placeChest(level, toolChest, Direction.EAST);
        fillChest(level, toolChest,
            new ItemStack(Items.DIAMOND_PICKAXE),
            new ItemStack(Items.DIAMOND_AXE),
            new ItemStack(Items.DIAMOND_SHOVEL),
            new ItemStack(Items.DIAMOND_HOE),
            new ItemStack(Items.FISHING_ROD),
            new ItemStack(Items.SHEARS),
            new ItemStack(Items.FLINT_AND_STEEL),
            new ItemStack(Items.SPYGLASS),
            new ItemStack(Items.COMPASS),
            new ItemStack(Items.CLOCK));

        // === FOOD CHEST (east) ===
        BlockPos foodChest = new BlockPos(cx + 8, y, cz - 3);
        placeChest(level, foodChest, Direction.WEST);
        fillChest(level, foodChest,
            new ItemStack(Items.GOLDEN_APPLE, 32),
            new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 8),
            new ItemStack(Items.COOKED_BEEF, 64),
            new ItemStack(Items.GOLDEN_CARROT, 64),
            new ItemStack(Items.BREAD, 64),
            new ItemStack(Items.PUMPKIN_PIE, 32),
            new ItemStack(Items.CAKE),
            new ItemStack(Items.HONEY_BOTTLE, 16));

        // === MATERIALS CHEST (east) ===
        BlockPos matChest = new BlockPos(cx + 8, y, cz - 1);
        placeChest(level, matChest, Direction.WEST);
        fillChest(level, matChest,
            new ItemStack(Items.DIAMOND, 64),
            new ItemStack(Items.EMERALD, 64),
            new ItemStack(Items.GOLD_INGOT, 64),
            new ItemStack(Items.IRON_INGOT, 64),
            new ItemStack(Items.NETHERITE_INGOT, 16),
            new ItemStack(Items.LAPIS_LAZULI, 64),
            new ItemStack(Items.REDSTONE, 64),
            new ItemStack(Items.QUARTZ, 64),
            new ItemStack(Items.LEATHER, 64),
            new ItemStack(Items.STRING, 64));

        // === SPECIAL ITEMS CHEST (east) ===
        BlockPos specialChest = new BlockPos(cx + 8, y, cz + 1);
        placeChest(level, specialChest, Direction.WEST);
        fillChest(level, specialChest,
            new ItemStack(Items.ENDER_PEARL, 16),
            new ItemStack(Items.TOTEM_OF_UNDYING),
            new ItemStack(Items.NAME_TAG, 8),
            new ItemStack(Items.EXPERIENCE_BOTTLE, 64),
            new ItemStack(Items.NETHER_STAR),
            new ItemStack(Items.END_CRYSTAL, 4),
            new ItemStack(Items.TNT, 16),
            new ItemStack(Items.FIREWORK_ROCKET, 64));

        // === MANNEQUIN DISPLAY (south side) ===
        for (int i = -6; i <= 6; i += 3) {
            place(level, new BlockPos(cx + i, y, cz + 6), CloneBlocks.NEUROCELL_MANNEQUIN);
        }

        // === ITEM DISPLAYS (east wall inner) ===
        for (int i = -4; i <= 4; i += 2) {
            place(level, new BlockPos(cx + 10, y, cz + i), CloneBlocks.NEUROCELL_ITEM);
        }

        // Clone machines for item processing
        place(level, new BlockPos(cx - 10, y, cz + 2), CloneBlocks.CLONE_FOUNDRY);
        place(level, new BlockPos(cx - 10, y, cz + 5), CloneBlocks.CLONE_ASSEMBLER);
        place(level, new BlockPos(cx - 10, y, cz - 5), CloneBlocks.CLONE_SMELTER);

        // Barrels for extra storage along south wall
        for (int dx = -4; dx <= 4; dx += 2) {
            place(level, new BlockPos(cx + dx, y, cz + 8), Blocks.BARREL);
        }

        // Entity scanner
        place(level, new BlockPos(cx + 10, y, cz - 6), DebugBlocks.ENTITY_SCANNER);

        // Lanterns
        placeLantern(level, new BlockPos(cx - 5, y, cz));
        placeLantern(level, new BlockPos(cx + 5, y, cz));
    }

    // ========================================================================
    // CONFIG_ROOM - East-South Outer (72x72)
    // Config management: command center desk, server rack, hologram
    // ========================================================================

    private void decorateConfigRoom(ServerLevel level, BlockPos center) {
        int y = center.getY() + 1;
        int cx = center.getX();
        int cz = center.getZ();

        // Central admin terminal
        place(level, new BlockPos(cx, y, cz), CloneBlocks.ADMIN_TERMINAL);

        // === U-SHAPED DESK around terminal ===
        for (int dx = -3; dx <= 3; dx++) {
            place(level, new BlockPos(cx + dx, y, cz - 2), NexusDecorBlocks.NEXUS_TERMINAL);
        }
        for (int dz = -1; dz <= 2; dz++) {
            place(level, new BlockPos(cx - 3, y, cz + dz), NexusDecorBlocks.NEXUS_TERMINAL);
            place(level, new BlockPos(cx + 3, y, cz + dz), NexusDecorBlocks.NEXUS_TERMINAL);
        }

        // === SERVER RACK (north wall) ===
        for (int dx = -4; dx <= 4; dx++) {
            place(level, new BlockPos(cx + dx, y, cz - 6), NexusDecorBlocks.NEXUS_CIRCUIT);
            place(level, new BlockPos(cx + dx, y + 1, cz - 6), NexusDecorBlocks.NEXUS_DISPLAY);
            place(level, new BlockPos(cx + dx, y + 2, cz - 6), NexusDecorBlocks.NEXUS_CIRCUIT);
        }
        // Display screens above
        for (int dx = -3; dx <= 3; dx++) {
            place(level, new BlockPos(cx + dx, y + 3, cz - 6), NexusDecorBlocks.NEXUS_TERMINAL_GREEN);
        }

        // === SIDE EQUIPMENT ===
        place(level, new BlockPos(cx - 6, y, cz + 3), CloneBlocks.CLONE_PROCESSOR);
        place(level, new BlockPos(cx + 6, y, cz + 3), CloneBlocks.CLONE_STORAGE_UNIT);
        place(level, new BlockPos(cx - 6, y, cz), CloneBlocks.CLONE_REACTOR);
        place(level, new BlockPos(cx + 6, y, cz), CloneBlocks.CLONE_BATTERY);

        // Hologram projector for config overview
        place(level, new BlockPos(cx, y, cz + 5), HologramBlocks.HOLOGRAM_PROJECTOR);

        // Bookshelves + lectern for documentation
        place(level, new BlockPos(cx - 6, y, cz - 4), Blocks.BOOKSHELF);
        place(level, new BlockPos(cx - 6, y + 1, cz - 4), Blocks.BOOKSHELF);
        place(level, new BlockPos(cx - 6, y, cz - 3), Blocks.LECTERN);

        // Entity scanner
        place(level, new BlockPos(cx + 6, y, cz - 4), DebugBlocks.ENTITY_SCANNER);

        // Floor lights
        place(level, new BlockPos(cx - 4, y - 1, cz), NexusDecorBlocks.NEXUS_FLOOR_LIGHT);
        place(level, new BlockPos(cx + 4, y - 1, cz), NexusDecorBlocks.NEXUS_FLOOR_LIGHT);

        // Floor accent under desk
        for (int dx = -3; dx <= 3; dx++) {
            place(level, new BlockPos(cx + dx, y - 1, cz), NexusDecorBlocks.NEXUS_GLOW_STRIP_CYAN);
        }
    }

    // ========================================================================
    // HUD_TESTING - West-South Outer (72x72)
    // HUD overlay testing: contrast zones, colored walls, varied lighting
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

        // === COLOR TEST WALLS (east side) ===
        Block[] colors = {
            Blocks.RED_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.YELLOW_CONCRETE,
            Blocks.LIME_CONCRETE, Blocks.CYAN_CONCRETE, Blocks.BLUE_CONCRETE,
            Blocks.PURPLE_CONCRETE, Blocks.MAGENTA_CONCRETE
        };
        for (int i = 0; i < colors.length; i++) {
            for (int dy = 0; dy < 2; dy++) {
                place(level, new BlockPos(cx + 10, y + 1 + dy, cz - 4 + i), colors[i]);
            }
        }

        // Nexus colored blocks on north-side
        place(level, new BlockPos(cx - 6, y + 1, cz - 4), NexusDecorBlocks.NEXUS_AZURE);
        place(level, new BlockPos(cx - 4, y + 1, cz - 4), NexusDecorBlocks.NEXUS_PLASMA);
        place(level, new BlockPos(cx - 2, y + 1, cz - 4), NexusDecorBlocks.NEXUS_MATRIX);
        place(level, new BlockPos(cx, y + 1, cz - 4), NexusDecorBlocks.NEXUS_ENERGY);
        place(level, new BlockPos(cx + 2, y + 1, cz - 4), NexusDecorBlocks.NEXUS_REACTOR);
        place(level, new BlockPos(cx + 4, y + 1, cz - 4), NexusDecorBlocks.NEXUS_CRYSTAL);
        place(level, new BlockPos(cx + 6, y + 1, cz - 4), NexusDecorBlocks.NEXUS_NEON);

        // Entity scanner + hologram
        place(level, new BlockPos(cx, y + 1, cz - 6), DebugBlocks.ENTITY_SCANNER);
        place(level, new BlockPos(cx, y + 1, cz + 6), HologramBlocks.HOLOGRAM_PROJECTOR);

        // Lanterns for light section
        placeLantern(level, new BlockPos(cx - 6, y + 1, cz - 6));
        placeLantern(level, new BlockPos(cx + 6, y + 1, cz - 6));
        // Soul lanterns for dark section
        placeSoulLantern(level, new BlockPos(cx - 6, y + 1, cz + 6));
        placeSoulLantern(level, new BlockPos(cx + 6, y + 1, cz + 6));
    }

    // ========================================================================
    // QUEST_TESTING - South-West Outer (72x72)
    // Quest system testing: quest giver area, reward table, leaderboard
    // ========================================================================

    private void decorateQuestTesting(ServerLevel level, BlockPos center) {
        int y = center.getY() + 1;
        int cx = center.getX();
        int cz = center.getZ();

        // === QUEST GIVER AREA (north side) ===
        // Desk with lecterns for dialog
        for (int dx = -4; dx <= 4; dx++) {
            place(level, new BlockPos(cx + dx, y, cz - 6), NexusDecorBlocks.NEXUS_TERMINAL);
        }
        place(level, new BlockPos(cx - 2, y + 1, cz - 6), Blocks.LECTERN);
        place(level, new BlockPos(cx + 2, y + 1, cz - 6), Blocks.LECTERN);
        // Bookshelves behind
        for (int dx = -4; dx <= 4; dx++) {
            place(level, new BlockPos(cx + dx, y, cz - 8), Blocks.BOOKSHELF);
            place(level, new BlockPos(cx + dx, y + 1, cz - 8), Blocks.BOOKSHELF);
        }
        // NPC spawn markers
        place(level, new BlockPos(cx - 4, y, cz - 4), NexusDecorBlocks.NEXUS_FLOOR_LIGHT_CYAN);
        place(level, new BlockPos(cx, y, cz - 4), NexusDecorBlocks.NEXUS_FLOOR_LIGHT_CYAN);
        place(level, new BlockPos(cx + 4, y, cz - 4), NexusDecorBlocks.NEXUS_FLOOR_LIGHT_CYAN);

        // === REWARD TABLE (center-south) ===
        for (int dx = -3; dx <= 3; dx++) {
            place(level, new BlockPos(cx + dx, y, cz + 2), Blocks.DEEPSLATE_BRICKS);
        }
        // Reward chests on table
        BlockPos rewardChest1 = new BlockPos(cx - 2, y + 1, cz + 2);
        placeChest(level, rewardChest1, Direction.SOUTH);
        fillChest(level, rewardChest1,
            new ItemStack(Items.DIAMOND, 16),
            new ItemStack(Items.EMERALD, 32),
            new ItemStack(Items.EXPERIENCE_BOTTLE, 64),
            new ItemStack(Items.GOLDEN_APPLE, 8),
            new ItemStack(Items.NAME_TAG, 4));
        BlockPos rewardChest2 = new BlockPos(cx + 2, y + 1, cz + 2);
        placeChest(level, rewardChest2, Direction.SOUTH);
        fillChest(level, rewardChest2,
            new ItemStack(Items.DIAMOND_SWORD),
            new ItemStack(Items.DIAMOND_CHESTPLATE),
            new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 2),
            new ItemStack(Items.TOTEM_OF_UNDYING),
            new ItemStack(Items.NETHER_STAR));

        // === LEADERBOARD AREA (south) ===
        place(level, new BlockPos(cx, y, cz + 6), HologramBlocks.HOLOGRAM_PROJECTOR);
        // Signal blocks as waypoints
        place(level, new BlockPos(cx - 8, y, cz + 4), NexusDecorBlocks.NEXUS_SIGNAL);
        place(level, new BlockPos(cx + 8, y, cz + 4), NexusDecorBlocks.NEXUS_SIGNAL);

        // Entity scanner
        place(level, new BlockPos(cx, y, cz), DebugBlocks.ENTITY_SCANNER);

        // Clone machines for quest processing
        place(level, new BlockPos(cx - 8, y, cz - 3), CloneBlocks.CLONE_ASSEMBLER);
        place(level, new BlockPos(cx + 8, y, cz - 3), CloneBlocks.CLONE_STORAGE_UNIT);

        // Lanterns
        placeLantern(level, new BlockPos(cx - 6, y, cz - 2));
        placeLantern(level, new BlockPos(cx + 6, y, cz - 2));
    }

    // ========================================================================
    // SANDBOX - South-East Outer (72x72)
    // Free creative area: full toolset, building block chests, zone editor
    // ========================================================================

    private void decorateSandbox(ServerLevel level, BlockPos center) {
        int y = center.getY() + 1;
        int cx = center.getX();
        int cz = center.getZ();

        // === WORKBENCH AREA (north-west) ===
        place(level, new BlockPos(cx - 5, y, cz - 4), Blocks.CRAFTING_TABLE);
        place(level, new BlockPos(cx - 3, y, cz - 4), Blocks.ANVIL);
        place(level, new BlockPos(cx - 1, y, cz - 4), Blocks.SMITHING_TABLE);
        place(level, new BlockPos(cx + 1, y, cz - 4), Blocks.STONECUTTER);

        // === BUILDING BLOCKS CHEST ===
        BlockPos buildChest = new BlockPos(cx + 5, y, cz - 4);
        placeChest(level, buildChest, Direction.SOUTH);
        fillChest(level, buildChest,
            new ItemStack(NexusDecorBlocks.NEXUS_PANEL.get().asItem(), 64),
            new ItemStack(NexusDecorBlocks.NEXUS_TILE.get().asItem(), 64),
            new ItemStack(NexusDecorBlocks.NEXUS_GRID.get().asItem(), 64),
            new ItemStack(NexusDecorBlocks.NEXUS_PLATING.get().asItem(), 64),
            new ItemStack(NexusDecorBlocks.NEXUS_STEEL.get().asItem(), 64),
            new ItemStack(NexusDecorBlocks.NEXUS_CARBON.get().asItem(), 64),
            new ItemStack(NexusDecorBlocks.NEXUS_LIGHT.get().asItem(), 64),
            new ItemStack(NexusDecorBlocks.NEXUS_AZURE.get().asItem(), 64),
            new ItemStack(NexusDecorBlocks.NEXUS_GLOW_STRIP_CYAN.get().asItem(), 64),
            new ItemStack(NexusDecorBlocks.NEXUS_GLASS_CYAN.get().asItem(), 64));

        // === GLOW BLOCKS CHEST ===
        BlockPos glowChest = new BlockPos(cx + 5, y, cz - 2);
        placeChest(level, glowChest, Direction.SOUTH);
        fillChest(level, glowChest,
            new ItemStack(NexusDecorBlocks.NEXUS_NEON.get().asItem(), 64),
            new ItemStack(NexusDecorBlocks.NEXUS_PLASMA.get().asItem(), 64),
            new ItemStack(NexusDecorBlocks.NEXUS_MATRIX.get().asItem(), 64),
            new ItemStack(NexusDecorBlocks.NEXUS_ENERGY.get().asItem(), 64),
            new ItemStack(NexusDecorBlocks.NEXUS_CRYSTAL.get().asItem(), 64),
            new ItemStack(NexusDecorBlocks.NEXUS_REACTOR.get().asItem(), 64),
            new ItemStack(NexusDecorBlocks.NEXUS_QUANTUM.get().asItem(), 64),
            new ItemStack(NexusDecorBlocks.NEXUS_VOID.get().asItem(), 64));

        // Area editor for sandbox zone manipulation
        place(level, new BlockPos(cx, y, cz), AreaBlocks.AREA_EDITOR);
        place(level, new BlockPos(cx, y, cz + 5), ZoneBlocks.ZONE_MARKER);

        // Barrels for additional storage
        place(level, new BlockPos(cx - 5, y, cz + 4), Blocks.BARREL);
        place(level, new BlockPos(cx - 3, y, cz + 4), Blocks.BARREL);
        place(level, new BlockPos(cx - 1, y, cz + 4), Blocks.BARREL);
    }

    // ========================================================================
    // ADMIN_TOOLS - South Outer Center (72x72)
    // Admin command center: terminals, server rack, scanners, dashboard
    // ========================================================================

    private void decorateAdminTools(ServerLevel level, BlockPos center) {
        int y = center.getY() + 1;
        int cx = center.getX();
        int cz = center.getZ();

        // Central admin terminal
        place(level, new BlockPos(cx, y, cz), CloneBlocks.ADMIN_TERMINAL);

        // === COMMAND CENTER DESK (U-shaped) ===
        for (int dx = -4; dx <= 4; dx++) {
            place(level, new BlockPos(cx + dx, y, cz - 2), NexusDecorBlocks.NEXUS_TERMINAL);
        }
        for (int dz = -1; dz <= 3; dz++) {
            place(level, new BlockPos(cx - 4, y, cz + dz), NexusDecorBlocks.NEXUS_TERMINAL);
            place(level, new BlockPos(cx + 4, y, cz + dz), NexusDecorBlocks.NEXUS_TERMINAL);
        }

        // === DISPLAY WALL (north) ===
        for (int dx = -5; dx <= 5; dx++) {
            place(level, new BlockPos(cx + dx, y, cz - 5), NexusDecorBlocks.NEXUS_CIRCUIT);
            place(level, new BlockPos(cx + dx, y + 1, cz - 5), NexusDecorBlocks.NEXUS_DISPLAY);
            place(level, new BlockPos(cx + dx, y + 2, cz - 5), NexusDecorBlocks.NEXUS_TERMINAL_GREEN);
            place(level, new BlockPos(cx + dx, y + 3, cz - 5), NexusDecorBlocks.NEXUS_DISPLAY);
        }

        // === SERVER RACKS (sides) ===
        for (int dz = -3; dz <= 3; dz++) {
            // West rack
            place(level, new BlockPos(cx - 8, y, cz + dz), NexusDecorBlocks.NEXUS_CIRCUIT);
            place(level, new BlockPos(cx - 8, y + 1, cz + dz), NexusDecorBlocks.NEXUS_DATA);
            place(level, new BlockPos(cx - 8, y + 2, cz + dz), NexusDecorBlocks.NEXUS_CIRCUIT);
            // East rack
            place(level, new BlockPos(cx + 8, y, cz + dz), NexusDecorBlocks.NEXUS_CIRCUIT);
            place(level, new BlockPos(cx + 8, y + 1, cz + dz), NexusDecorBlocks.NEXUS_DATA);
            place(level, new BlockPos(cx + 8, y + 2, cz + dz), NexusDecorBlocks.NEXUS_CIRCUIT);
        }

        // Clone machines (power and processing)
        place(level, new BlockPos(cx - 6, y, cz - 3), CloneBlocks.CLONE_REACTOR);
        place(level, new BlockPos(cx + 6, y, cz - 3), CloneBlocks.CLONE_PROCESSOR);
        place(level, new BlockPos(cx - 6, y, cz + 3), CloneBlocks.CLONE_BATTERY);
        place(level, new BlockPos(cx + 6, y, cz + 3), CloneBlocks.CLONE_SOLAR_PANEL);

        // Entity scanner
        place(level, new BlockPos(cx - 6, y, cz), DebugBlocks.ENTITY_SCANNER);

        // Hologram projector for dashboard
        place(level, new BlockPos(cx, y, cz + 6), HologramBlocks.HOLOGRAM_PROJECTOR);

        // Floor accent
        for (int dx = -3; dx <= 3; dx++) {
            place(level, new BlockPos(cx + dx, y - 1, cz), NexusDecorBlocks.NEXUS_GLOW_STRIP_CYAN);
        }

        // Lectern for command reference
        place(level, new BlockPos(cx + 6, y, cz), Blocks.LECTERN);

        // Lighting
        placeLantern(level, new BlockPos(cx - 2, y, cz + 4));
        placeLantern(level, new BlockPos(cx + 2, y, cz + 4));
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
        try {
            if (!level.isLoaded(pos)) {
                level.getChunk(pos);
            }
            level.setBlock(pos, state, FLAGS);
        } catch (Throwable t) {
            LOGGER.warn("[NexusDecorator] Failed to place {} at {}: {}",
                state.getBlock().getClass().getSimpleName(), pos, t.getMessage());
        }
    }

    private void placeChest(ServerLevel level, BlockPos pos, Direction facing) {
        BlockState chestState = Blocks.CHEST.defaultBlockState()
            .setValue(ChestBlock.FACING, facing);
        place(level, pos, chestState);
    }

    private void fillChest(ServerLevel level, BlockPos pos, ItemStack... items) {
        if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            for (int i = 0; i < items.length && i < 27; i++) {
                chest.setItem(i, items[i]);
            }
        }
    }

    private void placeLantern(ServerLevel level, BlockPos pos) {
        place(level, pos, Blocks.LANTERN.defaultBlockState()
            .setValue(LanternBlock.HANGING, false));
    }

    private void placeSoulLantern(ServerLevel level, BlockPos pos) {
        place(level, pos, Blocks.SOUL_LANTERN.defaultBlockState()
            .setValue(LanternBlock.HANGING, false));
    }
}
