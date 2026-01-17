package com.devmod.template.runtime;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.DevMod;
import com.devmod.template.data.ComponentPlacement;
import com.devmod.template.data.ZoneTemplate;

/**
 * Builds room decoration components in the world.
 * Each component type has a registered builder function.
 */
public final class ComponentBuilder {
    private ComponentBuilder() {}

    // Component builder registry
    private static final Map<String, ComponentBuilderFunc> BUILDERS = new HashMap<>();

    @FunctionalInterface
    public interface ComponentBuilderFunc {
        void build(ServerLevel level, BlockPos anchor, ComponentPlacement placement,
                   ZoneTemplate zone, BlockPos.MutableBlockPos mutablePos);
    }

    static {
        // Register all component builders
        registerDisplayComponents();
        registerConsoleComponents();
        registerCombatComponents();
        registerStorageComponents();
        registerUtilityComponents();
        registerDecorativeComponents();
        registerFunctionalComponents();
        registerQuestComponents();
        registerShopComponents();
        registerTransportComponents();
    }

    /**
     * Build a component at the given position.
     */
    public static void buildComponent(@Nonnull ServerLevel level, @Nonnull BlockPos anchor,
                                      @Nonnull ComponentPlacement placement, @Nonnull ZoneTemplate zone) {
        ComponentBuilderFunc builder = BUILDERS.get(placement.componentId());
        if (builder == null) {
            DevMod.LOGGER.warn("[Template] Unknown component: {}", placement.componentId());
            return;
        }

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        builder.build(level, anchor, placement, zone, mutablePos);
    }

    /**
     * Register a component builder.
     */
    public static void register(String componentId, ComponentBuilderFunc builder) {
        BUILDERS.put(componentId, builder);
    }

    // =========================================================================
    // DISPLAY COMPONENTS
    // =========================================================================

    private static void registerDisplayComponents() {
        // Screen Panel Z-axis (wall mounted, facing north/south)
        register("display_screen_z", (level, anchor, placement, zone, pos) -> {
            BlockState frame = Blocks.IRON_BLOCK.defaultBlockState();
            BlockState screen = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();

            int width = placement.getIntParam("width", 4);
            int height = placement.getIntParam("height", 3);

            // Frame
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    pos.set(anchor.getX() - width/2 + x, anchor.getY() + y, anchor.getZ());
                    boolean isEdge = x == 0 || x == width - 1 || y == 0 || y == height - 1;
                    level.setBlock(pos, isEdge ? frame : screen, 3);
                }
            }
        });

        // Screen Panel X-axis (wall mounted, facing east/west)
        register("display_screen_x", (level, anchor, placement, zone, pos) -> {
            BlockState frame = Blocks.IRON_BLOCK.defaultBlockState();
            BlockState screen = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();

            int depth = placement.getIntParam("depth", 4);
            int height = placement.getIntParam("height", 3);

            for (int z = 0; z < depth; z++) {
                for (int y = 0; y < height; y++) {
                    pos.set(anchor.getX(), anchor.getY() + y, anchor.getZ() - depth/2 + z);
                    boolean isEdge = z == 0 || z == depth - 1 || y == 0 || y == height - 1;
                    level.setBlock(pos, isEdge ? frame : screen, 3);
                }
            }
        });

        // Hologram Display (placeholder marker)
        register("display_hologram", (level, anchor, placement, zone, pos) -> {
            BlockState base = zone.getAccentBlock().defaultBlockState();
            // 3x3 base with accent color
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    pos.set(anchor.getX() + x, anchor.getY(), anchor.getZ() + z);
                    level.setBlock(pos, base, 3);
                }
            }
            // Center light
            pos.set(anchor.getX(), anchor.getY(), anchor.getZ());
            level.setBlock(pos, Blocks.SEA_LANTERN.defaultBlockState(), 3);
        });
    }

    // =========================================================================
    // CONSOLE COMPONENTS
    // =========================================================================

    private static void registerConsoleComponents() {
        // Console North-South oriented
        register("console_ns", (level, anchor, placement, zone, pos) -> {
            BlockState console = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
            BlockState top = Blocks.CHISELED_DEEPSLATE.defaultBlockState();
            BlockState screen = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();

            // Base (3x2)
            for (int x = -1; x <= 1; x++) {
                for (int z = 0; z <= 1; z++) {
                    pos.set(anchor.getX() + x, anchor.getY(), anchor.getZ() + z);
                    level.setBlock(pos, console, 3);
                    pos.set(anchor.getX() + x, anchor.getY() + 1, anchor.getZ() + z);
                    level.setBlock(pos, z == 0 ? screen : top, 3);
                }
            }
        });

        // Console East-West oriented
        register("console_ew", (level, anchor, placement, zone, pos) -> {
            BlockState console = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
            BlockState top = Blocks.CHISELED_DEEPSLATE.defaultBlockState();
            BlockState screen = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();

            for (int z = -1; z <= 1; z++) {
                for (int x = 0; x <= 1; x++) {
                    pos.set(anchor.getX() + x, anchor.getY(), anchor.getZ() + z);
                    level.setBlock(pos, console, 3);
                    pos.set(anchor.getX() + x, anchor.getY() + 1, anchor.getZ() + z);
                    level.setBlock(pos, x == 0 ? screen : top, 3);
                }
            }
        });
    }

    // =========================================================================
    // COMBAT COMPONENTS
    // =========================================================================

    private static void registerCombatComponents() {
        // Target Wall
        register("combat_target_wall", (level, anchor, placement, zone, pos) -> {
            BlockState target = Blocks.TARGET.defaultBlockState();
            BlockState frame = Blocks.NETHER_BRICKS.defaultBlockState();

            int width = placement.getIntParam("width", 5);
            int height = placement.getIntParam("height", 3);

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    pos.set(anchor.getX() - width/2 + x, anchor.getY() + y, anchor.getZ());
                    boolean isTarget = y == 1 && (x == width/2 || x == width/2 - 1 || x == width/2 + 1);
                    level.setBlock(pos, isTarget ? target : frame, 3);
                }
            }
        });

        // Training Post
        register("combat_training_post", (level, anchor, placement, zone, pos) -> {
            BlockState post = Blocks.IRON_BARS.defaultBlockState();
            BlockState base = Blocks.SMOOTH_STONE.defaultBlockState();

            pos.set(anchor);
            level.setBlock(pos, base, 3);
            for (int y = 1; y <= 3; y++) {
                pos.set(anchor.getX(), anchor.getY() + y, anchor.getZ());
                level.setBlock(pos, post, 3);
            }
        });

        // Arena Ring
        register("combat_arena_ring", (level, anchor, placement, zone, pos) -> {
            BlockState outer = zone.getAccentBlock().defaultBlockState();
            BlockState inner = Blocks.POLISHED_ANDESITE.defaultBlockState();

            int outerRadius = placement.getIntParam("outerRadius", 4);
            int innerRadius = placement.getIntParam("innerRadius", 3);

            for (int x = -outerRadius; x <= outerRadius; x++) {
                for (int z = -outerRadius; z <= outerRadius; z++) {
                    double dist = Math.sqrt(x * x + z * z);
                    if (dist <= outerRadius && dist > innerRadius) {
                        pos.set(anchor.getX() + x, anchor.getY(), anchor.getZ() + z);
                        level.setBlock(pos, dist > innerRadius + 0.5 ? outer : inner, 3);
                    }
                }
            }
        });

        // Dummy Pad
        register("combat_dummy_pad", (level, anchor, placement, zone, pos) -> {
            BlockState pad = zone.getPadBlock().defaultBlockState();
            BlockState marker = Blocks.IRON_BLOCK.defaultBlockState();

            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    pos.set(anchor.getX() + x, anchor.getY(), anchor.getZ() + z);
                    level.setBlock(pos, (x == 0 && z == 0) ? marker : pad, 3);
                }
            }
        });
    }

    // =========================================================================
    // STORAGE COMPONENTS
    // =========================================================================

    private static void registerStorageComponents() {
        // Server Rack
        register("storage_server_rack", (level, anchor, placement, zone, pos) -> {
            BlockState base = Blocks.IRON_BLOCK.defaultBlockState();
            BlockState body = Blocks.OBSERVER.defaultBlockState();
            BlockState top = Blocks.DAYLIGHT_DETECTOR.defaultBlockState();

            for (int x = 0; x < 2; x++) {
                pos.set(anchor.getX() + x, anchor.getY(), anchor.getZ());
                level.setBlock(pos, base, 3);
                pos.set(anchor.getX() + x, anchor.getY() + 1, anchor.getZ());
                level.setBlock(pos, body, 3);
                pos.set(anchor.getX() + x, anchor.getY() + 2, anchor.getZ());
                level.setBlock(pos, top, 3);
            }
        });

        // Chest Row
        register("storage_chest_row", (level, anchor, placement, zone, pos) -> {
            BlockState chest = Blocks.CHEST.defaultBlockState();

            int count = placement.getIntParam("count", 4);
            for (int x = 0; x < count; x++) {
                pos.set(anchor.getX() - count/2 + x, anchor.getY(), anchor.getZ());
                level.setBlock(pos, chest, 3);
            }
        });
    }

    // =========================================================================
    // UTILITY COMPONENTS
    // =========================================================================

    private static void registerUtilityComponents() {
        // Landing Pad
        register("utility_landing_pad", (level, anchor, placement, zone, pos) -> {
            BlockState pad = zone.getPadBlock().defaultBlockState();
            BlockState light = Blocks.SEA_LANTERN.defaultBlockState();

            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    pos.set(anchor.getX() + x, anchor.getY(), anchor.getZ() + z);
                    level.setBlock(pos, (x == 0 && z == 0) ? light : pad, 3);
                }
            }
        });

        // Utility Bench Row
        register("utility_bench_row", (level, anchor, placement, zone, pos) -> {
            BlockState bench = Blocks.STONECUTTER.defaultBlockState();
            BlockState base = Blocks.SMOOTH_STONE.defaultBlockState();

            int count = placement.getIntParam("count", 3);
            int spacing = 2;

            for (int i = 0; i < count; i++) {
                int x = anchor.getX() - (count * spacing) / 2 + i * spacing;
                pos.set(x, anchor.getY(), anchor.getZ());
                level.setBlock(pos, base, 3);
                pos.set(x, anchor.getY() + 1, anchor.getZ());
                level.setBlock(pos, bench, 3);
            }
        });

        // Crafting Station
        register("utility_crafting_station", (level, anchor, placement, zone, pos) -> {
            BlockState table = Blocks.CRAFTING_TABLE.defaultBlockState();
            BlockState storage = Blocks.BARREL.defaultBlockState();

            // Center crafting table
            pos.set(anchor);
            level.setBlock(pos, table, 3);

            // Surrounding barrels
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                pos.set(anchor.relative(dir));
                level.setBlock(pos, storage, 3);
            }
        });
    }

    // =========================================================================
    // DECORATIVE COMPONENTS
    // =========================================================================

    private static void registerDecorativeComponents() {
        // Corner Pillar
        register("decorative_pillar", (level, anchor, placement, zone, pos) -> {
            BlockState pillar = Blocks.QUARTZ_PILLAR.defaultBlockState();
            BlockState cap = Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState();

            int height = placement.getIntParam("height", 4);
            for (int y = 0; y < height; y++) {
                pos.set(anchor.getX(), anchor.getY() + y, anchor.getZ());
                level.setBlock(pos, y == height - 1 ? cap : pillar, 3);
            }
        });

        // Display Plinth
        register("decorative_plinth", (level, anchor, placement, zone, pos) -> {
            BlockState base = zone.getAccentBlock().defaultBlockState();
            BlockState top = Blocks.SMOOTH_STONE_SLAB.defaultBlockState();

            // 3x3 base
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    pos.set(anchor.getX() + x, anchor.getY(), anchor.getZ() + z);
                    level.setBlock(pos, base, 3);
                }
            }
            // Top center
            pos.set(anchor.getX(), anchor.getY() + 1, anchor.getZ());
            level.setBlock(pos, top, 3);
        });

        // Banner (placeholder - uses wool)
        register("decorative_banner", (level, anchor, placement, zone, pos) -> {
            BlockState banner = switch (zone) {
                case WAR_HUB, WAR_ARENA -> Blocks.RED_WOOL.defaultBlockState();
                case ECONOMY_SHOP, QUEST_HUB -> Blocks.YELLOW_WOOL.defaultBlockState();
                case TUTORIAL -> Blocks.LIGHT_BLUE_WOOL.defaultBlockState();
                default -> Blocks.WHITE_WOOL.defaultBlockState();
            };

            for (int y = 0; y < 3; y++) {
                pos.set(anchor.getX(), anchor.getY() + y, anchor.getZ());
                level.setBlock(pos, banner, 3);
            }
        });
    }

    // =========================================================================
    // FUNCTIONAL COMPONENTS
    // =========================================================================

    private static void registerFunctionalComponents() {
        // Portal Frame
        register("functional_portal_frame", (level, anchor, placement, zone, pos) -> {
            BlockState frame = Blocks.CRYING_OBSIDIAN.defaultBlockState();
            BlockState corner = Blocks.OBSIDIAN.defaultBlockState();

            // 5x5 frame outline
            int size = 2;
            for (int x = -size; x <= size; x++) {
                for (int y = 0; y <= 4; y++) {
                    boolean isEdge = x == -size || x == size || y == 0 || y == 4;
                    boolean isCorner = (x == -size || x == size) && (y == 0 || y == 4);
                    if (isEdge) {
                        pos.set(anchor.getX() + x, anchor.getY() + y, anchor.getZ());
                        level.setBlock(pos, isCorner ? corner : frame, 3);
                    }
                }
            }
        });

        // Spawn Pad
        register("functional_spawn_pad", (level, anchor, placement, zone, pos) -> {
            BlockState pad = Blocks.WHITE_CONCRETE.defaultBlockState();
            BlockState accent = zone.getAccentBlock().defaultBlockState();
            BlockState light = Blocks.GLOWSTONE.defaultBlockState();

            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    pos.set(anchor.getX() + x, anchor.getY(), anchor.getZ() + z);
                    boolean isCorner = Math.abs(x) == 2 && Math.abs(z) == 2;
                    boolean isCenter = x == 0 && z == 0;
                    level.setBlock(pos, isCorner ? accent : (isCenter ? light : pad), 3);
                }
            }
        });
    }

    // =========================================================================
    // QUEST COMPONENTS
    // =========================================================================

    private static void registerQuestComponents() {
        // Quest Board
        register("quest_board", (level, anchor, placement, zone, pos) -> {
            BlockState board = Blocks.DARK_OAK_PLANKS.defaultBlockState();
            BlockState frame = Blocks.DARK_OAK_LOG.defaultBlockState();

            // 3x3 board
            for (int x = -1; x <= 1; x++) {
                for (int y = 0; y < 3; y++) {
                    pos.set(anchor.getX() + x, anchor.getY() + y, anchor.getZ());
                    boolean isEdge = x == -1 || x == 1 || y == 0 || y == 2;
                    level.setBlock(pos, isEdge ? frame : board, 3);
                }
            }
        });

        // Quest NPC Spot
        register("quest_npc_spot", (level, anchor, placement, zone, pos) -> {
            BlockState pad = zone.getPadBlock().defaultBlockState();

            for (int x = 0; x < 2; x++) {
                for (int z = 0; z < 2; z++) {
                    pos.set(anchor.getX() + x, anchor.getY(), anchor.getZ() + z);
                    level.setBlock(pos, pad, 3);
                }
            }
        });
    }

    // =========================================================================
    // SHOP COMPONENTS
    // =========================================================================

    private static void registerShopComponents() {
        // Vendor Stall
        register("shop_vendor_stall", (level, anchor, placement, zone, pos) -> {
            BlockState counter = Blocks.STRIPPED_OAK_LOG.defaultBlockState();
            BlockState roof = Blocks.OAK_SLAB.defaultBlockState();
            BlockState post = Blocks.OAK_FENCE.defaultBlockState();

            // Counter (4x2)
            for (int x = -1; x <= 2; x++) {
                for (int z = 0; z < 2; z++) {
                    pos.set(anchor.getX() + x, anchor.getY(), anchor.getZ() + z);
                    level.setBlock(pos, counter, 3);
                }
            }

            // Corner posts
            for (int x : new int[]{-1, 2}) {
                pos.set(anchor.getX() + x, anchor.getY() + 1, anchor.getZ());
                level.setBlock(pos, post, 3);
                pos.set(anchor.getX() + x, anchor.getY() + 2, anchor.getZ());
                level.setBlock(pos, post, 3);
            }

            // Roof
            for (int x = -1; x <= 2; x++) {
                pos.set(anchor.getX() + x, anchor.getY() + 3, anchor.getZ());
                level.setBlock(pos, roof, 3);
            }
        });

        // Auction Block
        register("shop_auction_block", (level, anchor, placement, zone, pos) -> {
            BlockState platform = Blocks.GOLD_BLOCK.defaultBlockState();
            BlockState base = Blocks.POLISHED_BLACKSTONE.defaultBlockState();

            // Base
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    pos.set(anchor.getX() + x, anchor.getY(), anchor.getZ() + z);
                    level.setBlock(pos, base, 3);
                }
            }
            // Platform center
            pos.set(anchor.getX(), anchor.getY() + 1, anchor.getZ());
            level.setBlock(pos, platform, 3);
        });

        // Bank Counter
        register("shop_bank_counter", (level, anchor, placement, zone, pos) -> {
            BlockState counter = Blocks.POLISHED_DIORITE.defaultBlockState();
            BlockState safe = Blocks.IRON_BLOCK.defaultBlockState();

            // Counter (5x2)
            for (int x = -2; x <= 2; x++) {
                for (int z = 0; z < 2; z++) {
                    pos.set(anchor.getX() + x, anchor.getY(), anchor.getZ() + z);
                    level.setBlock(pos, counter, 3);
                    pos.set(anchor.getX() + x, anchor.getY() + 1, anchor.getZ() + z);
                    level.setBlock(pos, z == 1 ? safe : counter, 3);
                }
            }
        });
    }

    // =========================================================================
    // TRANSPORT COMPONENTS
    // =========================================================================

    private static void registerTransportComponents() {
        // Transport Waypoint
        register("transport_waypoint", (level, anchor, placement, zone, pos) -> {
            BlockState base = zone.getPadBlock().defaultBlockState();
            BlockState pillar = Blocks.QUARTZ_PILLAR.defaultBlockState();
            BlockState light = Blocks.END_ROD.defaultBlockState();

            // 3x3 base
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    pos.set(anchor.getX() + x, anchor.getY(), anchor.getZ() + z);
                    level.setBlock(pos, base, 3);
                }
            }

            // Center pillar
            for (int y = 1; y <= 3; y++) {
                pos.set(anchor.getX(), anchor.getY() + y, anchor.getZ());
                level.setBlock(pos, y == 3 ? light : pillar, 3);
            }
        });

        // Caravan Stop
        register("transport_caravan_stop", (level, anchor, placement, zone, pos) -> {
            BlockState platform = Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
            BlockState edge = Blocks.SPRUCE_SLAB.defaultBlockState();

            // 5x5 platform
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    pos.set(anchor.getX() + x, anchor.getY(), anchor.getZ() + z);
                    boolean isEdge = Math.abs(x) == 2 || Math.abs(z) == 2;
                    level.setBlock(pos, isEdge ? edge : platform, 3);
                }
            }
        });
    }
}
