package com.devmod.runtime;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import com.devmod.blocks.ModBlocks;
import com.devmod.blocks.NexusPortalBlock;
import com.devmod.blocks.NexusPortalColor;
import com.devmod.compat.Compat;
import com.devmod.compat.CompatRegistry;
import com.devmod.compat.mods.dummmmmmy.DummmmmmyCompat;
import com.devmod.config.Config;
import com.devmod.zone.data.ZoneDefinition;
import com.devmod.zone.data.ZonePresets;

/**
 * Builds the Nexus development hub layout.
 */
@SuppressWarnings("UnusedMethod") // Retained builder components for future layout variants.
public final class NexusHubBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(NexusHubBuilder.class);
    private static final int PLACEMENT_FLAGS = 2 | 16 | 64;

    private static final int HUB_HALF_SIZE = 96; // 192x192
    private static final int CENTER_HALF_SIZE = 32; // 64x64
    private static final int WALL_HEIGHT = 8;
    private static final int DOOR_WIDTH = 10;
    private static final int DOOR_HEIGHT = 5;
    private static final int CORRIDOR_HALF_WIDTH = 2;

    @Nonnull private static BlockState FLOOR = Objects.requireNonNull(Blocks.DEEPSLATE_TILES.defaultBlockState());
    @Nonnull private static BlockState FLOOR_ALT = Objects.requireNonNull(Blocks.POLISHED_DEEPSLATE.defaultBlockState());
    @Nonnull private static BlockState FLOOR_HUB = Objects.requireNonNull(Blocks.DEEPSLATE_BRICKS.defaultBlockState());
    @Nonnull private static BlockState GRID = Objects.requireNonNull(Blocks.CYAN_CONCRETE.defaultBlockState());
    @Nonnull private static BlockState ACCENT = Objects.requireNonNull(Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState());
    @Nonnull private static BlockState WALL = Objects.requireNonNull(Blocks.CYAN_STAINED_GLASS.defaultBlockState());
    @Nonnull private static BlockState WALL_FRAME = Objects.requireNonNull(Blocks.DEEPSLATE_BRICKS.defaultBlockState());
    @Nonnull private static BlockState BORDER = Objects.requireNonNull(Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
    @Nonnull private static BlockState TRIM = Objects.requireNonNull(Blocks.QUARTZ_PILLAR.defaultBlockState());
    @Nonnull private static BlockState RING = Objects.requireNonNull(Blocks.SMOOTH_QUARTZ.defaultBlockState());
    @Nonnull private static BlockState CORE = Objects.requireNonNull(Blocks.QUARTZ_BLOCK.defaultBlockState());
    @Nonnull private static BlockState CORE_GLASS = Objects.requireNonNull(Blocks.CYAN_STAINED_GLASS.defaultBlockState());
    @Nonnull private static BlockState LIGHT = Objects.requireNonNull(Blocks.SEA_LANTERN.defaultBlockState());
    @Nonnull private static BlockState LIGHT_WARM = Objects.requireNonNull(Blocks.SHROOMLIGHT.defaultBlockState());
    @Nonnull private static BlockState SCREEN = Objects.requireNonNull(Blocks.BLUE_STAINED_GLASS.defaultBlockState());
    @Nonnull private static BlockState SCREEN_ALT = Objects.requireNonNull(Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
    @Nonnull private static BlockState SCREEN_FRAME = Objects.requireNonNull(Blocks.BLACK_CONCRETE.defaultBlockState());
    @Nonnull private static BlockState WINDOW = Objects.requireNonNull(Blocks.CYAN_STAINED_GLASS.defaultBlockState());
    @Nonnull private static BlockState WINDOW_DARK = Objects.requireNonNull(Blocks.BLUE_STAINED_GLASS.defaultBlockState());
    @Nonnull private static BlockState RAIL = Objects.requireNonNull(Blocks.CYAN_STAINED_GLASS_PANE.defaultBlockState());
    @Nonnull private static BlockState TRUSS = Objects.requireNonNull(Blocks.IRON_BARS.defaultBlockState());
    @Nonnull private static BlockState METAL = Objects.requireNonNull(Blocks.IRON_BLOCK.defaultBlockState());
    @Nonnull private static BlockState DATA_LINE = Objects.requireNonNull(Blocks.LIME_STAINED_GLASS.defaultBlockState());
    @Nonnull private static BlockState CONSOLE = Objects.requireNonNull(Blocks.GRAY_CONCRETE.defaultBlockState());
    @Nonnull private static BlockState CONSOLE_TOP = Objects.requireNonNull(Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
    @Nonnull private static BlockState COMBAT_RING = Objects.requireNonNull(Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
    @Nonnull private static BlockState COMBAT_RING_INNER = Objects.requireNonNull(Blocks.RED_NETHER_BRICKS.defaultBlockState());
    @Nonnull private static BlockState COMBAT_POST = Objects.requireNonNull(Blocks.POLISHED_BASALT.defaultBlockState());
    @Nonnull private static BlockState COMBAT_TARGET = Objects.requireNonNull(Blocks.TARGET.defaultBlockState());
    @Nonnull private static BlockState UI_ACCENT = Objects.requireNonNull(Blocks.BLUE_CONCRETE.defaultBlockState());
    @Nonnull private static BlockState TELEMETRY_ACCENT = Objects.requireNonNull(Blocks.LIME_CONCRETE.defaultBlockState());
    @Nonnull private static BlockState TELEMETRY_GLASS = Objects.requireNonNull(Blocks.GREEN_STAINED_GLASS.defaultBlockState());
    @Nonnull private static BlockState ARENA_RING = Objects.requireNonNull(Blocks.POLISHED_BASALT.defaultBlockState());
    @Nonnull private static BlockState ARENA_RING_INNER = Objects.requireNonNull(Blocks.BLACKSTONE.defaultBlockState());
    @Nonnull private static BlockState ARENA_BARRIER = Objects.requireNonNull(Blocks.BLACKSTONE_WALL.defaultBlockState());
    @Nonnull private static BlockState PORTAL_FRAME = Objects.requireNonNull(Blocks.OBSIDIAN.defaultBlockState());
    @Nonnull private static BlockState PORTAL_GLASS = Objects.requireNonNull(Blocks.LIGHT_GRAY_STAINED_GLASS.defaultBlockState());
    @Nonnull private static BlockState PAD_BLUE = Objects.requireNonNull(Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState());
    @Nonnull private static BlockState PAD_GREEN = Objects.requireNonNull(Blocks.LIME_CONCRETE.defaultBlockState());
    @Nonnull private static BlockState PAD_ORANGE = Objects.requireNonNull(Blocks.ORANGE_CONCRETE.defaultBlockState());
    @Nonnull private static BlockState PAD_RED = Objects.requireNonNull(Blocks.RED_CONCRETE.defaultBlockState());
    @Nonnull private static BlockState PAD_YELLOW = Objects.requireNonNull(Blocks.YELLOW_CONCRETE.defaultBlockState());
    @Nonnull private static BlockState PAD_PURPLE = Objects.requireNonNull(Blocks.PURPLE_CONCRETE.defaultBlockState());
    @Nonnull private static BlockState AIR = Objects.requireNonNull(Blocks.AIR.defaultBlockState());

    private static final Map<String, BlockPos> LEGACY_SPAWN_OFFSETS = loadLegacySpawnOffsets();

    private NexusHubBuilder() {}

    public static void refreshPalette() {
        applyPalette(NexusPalette.load());
    }

    public static List<NexusBuildStep> buildSteps(ServerLevel level, BlockPos origin) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(origin, "origin");

        refreshPalette();

        Layout layout = Layout.from(origin);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        return List.of(
            new NexusBuildStep("base", () -> {
                preloadChunks(level, layout.minX, layout.minZ, layout.maxX, layout.maxZ);
                buildBase(level, layout, pos);
            }),
            new NexusBuildStep("outer_shell", () -> buildOuterShell(level, layout, pos)),
            new NexusBuildStep("room_shells", () -> buildRoomShells(level, layout, pos)),
            new NexusBuildStep("hub_decor", () -> decorateHubCore(level, layout, pos)),
            new NexusBuildStep("room_decor", () -> decorateRooms(level, layout, pos))
        );
    }

    public static void build(ServerLevel level, BlockPos origin) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(origin, "origin");

        refreshPalette();

        Layout layout = Layout.from(origin);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        preloadChunks(level, layout.minX, layout.minZ, layout.maxX, layout.maxZ);

        buildBase(level, layout, pos);
        buildOuterShell(level, layout, pos);
        buildRoomShells(level, layout, pos);
        decorateHubCore(level, layout, pos);
        decorateRooms(level, layout, pos);
    }

    public static void postBuildEntities(ServerLevel level, BlockPos origin) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(origin, "origin");
        spawnCombatDummies(level, origin);
        // NOTE: NexusAvatarManager.spawn() removed - hub now starts empty.
        // Use NeurocellNpc item to spawn NPCs with the new NPC system.
    }

    public static void applyOptionalOverlay(ServerLevel level, BlockPos origin) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(origin, "origin");

        String templateId = Config.NEXUS_LAYOUT_OVERLAY_TEMPLATE.get();
        if (templateId == null || templateId.isBlank()) {
            return;
        }
        ResourceLocation location = ResourceLocation.tryParse(nn(templateId.trim(), "templateId"));
        if (location == null) {
            LOGGER.warn("[Nexus] Invalid overlay template id: {}", templateId);
            return;
        }

        StructureTemplate template = resolveTemplate(level, location);
        if (template == null) {
            LOGGER.warn("[Nexus] Overlay template not found: {}", location);
            return;
        }

        StructurePlaceSettings settings = new StructurePlaceSettings();
        settings.setIgnoreEntities(true);
        template.placeInWorld(level, origin, origin, settings, nn(level.getRandom(), "random"), PLACEMENT_FLAGS);
        LOGGER.info("[Nexus] Applied overlay template {}", location);
    }

    private static void applyPalette(NexusPalette palette) {
        FLOOR = nn(palette.get(NexusPalette.Key.FLOOR), "FLOOR");
        FLOOR_ALT = nn(palette.get(NexusPalette.Key.FLOOR_ALT), "FLOOR_ALT");
        FLOOR_HUB = nn(palette.get(NexusPalette.Key.FLOOR_HUB), "FLOOR_HUB");
        GRID = nn(palette.get(NexusPalette.Key.GRID), "GRID");
        ACCENT = nn(palette.get(NexusPalette.Key.ACCENT), "ACCENT");
        WALL = nn(palette.get(NexusPalette.Key.WALL), "WALL");
        WALL_FRAME = nn(palette.get(NexusPalette.Key.WALL_FRAME), "WALL_FRAME");
        BORDER = nn(palette.get(NexusPalette.Key.BORDER), "BORDER");
        TRIM = nn(palette.get(NexusPalette.Key.TRIM), "TRIM");
        RING = nn(palette.get(NexusPalette.Key.RING), "RING");
        CORE = nn(palette.get(NexusPalette.Key.CORE), "CORE");
        CORE_GLASS = nn(palette.get(NexusPalette.Key.CORE_GLASS), "CORE_GLASS");
        LIGHT = nn(palette.get(NexusPalette.Key.LIGHT), "LIGHT");
        LIGHT_WARM = nn(palette.get(NexusPalette.Key.LIGHT_WARM), "LIGHT_WARM");
        SCREEN = nn(palette.get(NexusPalette.Key.SCREEN), "SCREEN");
        SCREEN_ALT = nn(palette.get(NexusPalette.Key.SCREEN_ALT), "SCREEN_ALT");
        SCREEN_FRAME = nn(palette.get(NexusPalette.Key.SCREEN_FRAME), "SCREEN_FRAME");
        WINDOW = nn(palette.get(NexusPalette.Key.WINDOW), "WINDOW");
        WINDOW_DARK = nn(palette.get(NexusPalette.Key.WINDOW_DARK), "WINDOW_DARK");
        RAIL = nn(palette.get(NexusPalette.Key.RAIL), "RAIL");
        TRUSS = nn(palette.get(NexusPalette.Key.TRUSS), "TRUSS");
        METAL = nn(palette.get(NexusPalette.Key.METAL), "METAL");
        DATA_LINE = nn(palette.get(NexusPalette.Key.DATA_LINE), "DATA_LINE");
        CONSOLE = nn(palette.get(NexusPalette.Key.CONSOLE), "CONSOLE");
        CONSOLE_TOP = nn(palette.get(NexusPalette.Key.CONSOLE_TOP), "CONSOLE_TOP");
        COMBAT_RING = nn(palette.get(NexusPalette.Key.COMBAT_RING), "COMBAT_RING");
        COMBAT_RING_INNER = nn(palette.get(NexusPalette.Key.COMBAT_RING_INNER), "COMBAT_RING_INNER");
        COMBAT_POST = nn(palette.get(NexusPalette.Key.COMBAT_POST), "COMBAT_POST");
        COMBAT_TARGET = nn(palette.get(NexusPalette.Key.COMBAT_TARGET), "COMBAT_TARGET");
        UI_ACCENT = nn(palette.get(NexusPalette.Key.UI_ACCENT), "UI_ACCENT");
        TELEMETRY_ACCENT = nn(palette.get(NexusPalette.Key.TELEMETRY_ACCENT), "TELEMETRY_ACCENT");
        TELEMETRY_GLASS = nn(palette.get(NexusPalette.Key.TELEMETRY_GLASS), "TELEMETRY_GLASS");
        ARENA_RING = nn(palette.get(NexusPalette.Key.ARENA_RING), "ARENA_RING");
        ARENA_RING_INNER = nn(palette.get(NexusPalette.Key.ARENA_RING_INNER), "ARENA_RING_INNER");
        ARENA_BARRIER = nn(palette.get(NexusPalette.Key.ARENA_BARRIER), "ARENA_BARRIER");
        PORTAL_FRAME = nn(palette.get(NexusPalette.Key.PORTAL_FRAME), "PORTAL_FRAME");
        PORTAL_GLASS = nn(palette.get(NexusPalette.Key.PORTAL_GLASS), "PORTAL_GLASS");
        PAD_BLUE = nn(palette.get(NexusPalette.Key.PAD_BLUE), "PAD_BLUE");
        PAD_GREEN = nn(palette.get(NexusPalette.Key.PAD_GREEN), "PAD_GREEN");
        PAD_ORANGE = nn(palette.get(NexusPalette.Key.PAD_ORANGE), "PAD_ORANGE");
        PAD_RED = nn(palette.get(NexusPalette.Key.PAD_RED), "PAD_RED");
        PAD_YELLOW = nn(palette.get(NexusPalette.Key.PAD_YELLOW), "PAD_YELLOW");
        PAD_PURPLE = nn(palette.get(NexusPalette.Key.PAD_PURPLE), "PAD_PURPLE");
        AIR = nn(palette.get(NexusPalette.Key.AIR), "AIR");
    }

    private static void buildBase(ServerLevel level, Layout layout, BlockPos.MutableBlockPos pos) {
        buildBaseFloorPattern(level, layout.minX, layout.minZ, layout.maxX, layout.maxZ, layout.originY, pos);
        fillRect(level, layout.centerMinX, layout.centerMinZ, layout.centerMaxX, layout.centerMaxZ,
            layout.originY, FLOOR_HUB, pos);

        drawRectOutline(level, layout.minX, layout.minZ, layout.maxX, layout.maxZ, layout.originY, BORDER, pos);
        drawRectOutline(level, layout.centerMinX, layout.centerMinZ, layout.centerMaxX, layout.centerMaxZ,
            layout.originY, GRID, pos);
        drawRectOutline(level, layout.roomMinX1, layout.roomMinZ1, layout.roomMaxX1, layout.roomMaxZ1,
            layout.originY, GRID, pos);
        drawRectOutline(level, layout.roomMinX2, layout.roomMinZ1, layout.roomMaxX2, layout.roomMaxZ1,
            layout.originY, GRID, pos);
        drawRectOutline(level, layout.roomMinX1, layout.roomMinZ2, layout.roomMaxX1, layout.roomMaxZ2,
            layout.originY, GRID, pos);
        drawRectOutline(level, layout.roomMinX2, layout.roomMinZ2, layout.roomMaxX2, layout.roomMaxZ2,
            layout.originY, GRID, pos);
        drawAxisLineX(level, layout.minX, layout.maxX, layout.originZ, layout.originY, GRID, pos);
        drawAxisLineZ(level, layout.minZ, layout.maxZ, layout.originX, layout.originY, GRID, pos);

        decorateCorridors(level, layout.minX, layout.maxX, layout.minZ, layout.maxZ,
            layout.originX, layout.originZ, layout.originY, pos);

        decorateArrivalDeck(level, layout, pos);
    }

    private static void buildOuterShell(ServerLevel level, Layout layout, BlockPos.MutableBlockPos pos) {
        buildWalls(level, layout.minX, layout.minZ, layout.maxX, layout.maxZ,
            layout.originY + 1, layout.originY + WALL_HEIGHT, WALL, pos);
        buildWalls(level, layout.minX, layout.minZ, layout.maxX, layout.maxZ,
            layout.originY + 1, layout.originY + 1, WALL_FRAME, pos);
        buildWalls(level, layout.minX, layout.minZ, layout.maxX, layout.maxZ,
            layout.originY + WALL_HEIGHT, layout.originY + WALL_HEIGHT, WALL_FRAME, pos);
        buildWalls(level, layout.minX, layout.minZ, layout.maxX, layout.maxZ,
            layout.originY + 3, layout.originY + 3, BORDER, pos);
        placeOuterWindows(level, layout.minX, layout.minZ, layout.maxX, layout.maxZ,
            layout.originY + 4, layout.originY + 5, pos);
        placeCornerPillar(level, layout.minX + 1, layout.minZ + 1,
            layout.originY + 1, layout.originY + WALL_HEIGHT + 1, pos);
        placeCornerPillar(level, layout.maxX - 1, layout.minZ + 1,
            layout.originY + 1, layout.originY + WALL_HEIGHT + 1, pos);
        placeCornerPillar(level, layout.minX + 1, layout.maxZ - 1,
            layout.originY + 1, layout.originY + WALL_HEIGHT + 1, pos);
        placeCornerPillar(level, layout.maxX - 1, layout.maxZ - 1,
            layout.originY + 1, layout.originY + WALL_HEIGHT + 1, pos);
    }

    private static void buildRoomShells(ServerLevel level, Layout layout, BlockPos.MutableBlockPos pos) {
        buildRoomShell(level, layout.roomMinX1, layout.roomMinZ1, layout.roomMaxX1, layout.roomMaxZ1,
            layout.originY, pos);
        buildRoomShell(level, layout.roomMinX2, layout.roomMinZ1, layout.roomMaxX2, layout.roomMaxZ1,
            layout.originY, pos);
        buildRoomShell(level, layout.roomMinX1, layout.roomMinZ2, layout.roomMaxX1, layout.roomMaxZ2,
            layout.originY, pos);
        buildRoomShell(level, layout.roomMinX2, layout.roomMinZ2, layout.roomMaxX2, layout.roomMaxZ2,
            layout.originY, pos);

        clearRoomDoors(level, layout.roomMinX1, layout.roomMaxX1, layout.roomMinZ1, layout.roomMaxZ1,
            layout.originY, pos, true, false, false, false);
        clearRoomDoors(level, layout.roomMinX2, layout.roomMaxX2, layout.roomMinZ1, layout.roomMaxZ1,
            layout.originY, pos, false, false, true, false);
        clearRoomDoors(level, layout.roomMinX1, layout.roomMaxX1, layout.roomMinZ2, layout.roomMaxZ2,
            layout.originY, pos, false, false, false, true);
        clearRoomDoors(level, layout.roomMinX2, layout.roomMaxX2, layout.roomMinZ2, layout.roomMaxZ2,
            layout.originY, pos, false, true, false, false);
    }

    private static void decorateHubCore(ServerLevel level, Layout layout, BlockPos.MutableBlockPos pos) {
        decorateHub(level, layout.originX, layout.originZ, layout.originY, pos);
        placeHubEntryMarkers(level, layout, pos);
    }

    private static void placeHubEntryMarkers(ServerLevel level, Layout layout, BlockPos.MutableBlockPos pos) {
        int northZ = layout.centerMinZ - 1;
        int southZ = layout.centerMaxZ + 1;
        int westX = layout.centerMinX - 1;
        int eastX = layout.centerMaxX + 1;
        int centerX = layout.originX;
        int centerZ = layout.originZ;
        placePad(level, centerX, northZ, layout.originY, ACCENT, LIGHT_WARM, pos);
        placePad(level, centerX, southZ, layout.originY, ACCENT, LIGHT_WARM, pos);
        placePad(level, westX, centerZ, layout.originY, ACCENT, LIGHT_WARM, pos);
        placePad(level, eastX, centerZ, layout.originY, ACCENT, LIGHT_WARM, pos);
    }

    private static void decorateRooms(ServerLevel level, Layout layout, BlockPos.MutableBlockPos pos) {
        decorateWingEastWest(level, layout.roomMinX1, layout.roomMaxX1, layout.roomMinZ1, layout.roomMaxZ1,
            layout.originY, WingRoom.COMBAT, WingRoom.ARENA, pos);
        decorateWingEastWest(level, layout.roomMinX2, layout.roomMaxX2, layout.roomMinZ1, layout.roomMaxZ1,
            layout.originY, WingRoom.UI, WingRoom.TELEMETRY, pos);

        decorateWingNorthSouth(level, layout.roomMinX1, layout.roomMaxX1, layout.roomMinZ2, layout.roomMaxZ2,
            layout.originY, WingRoom.SHOWCASE, WingRoom.INTEGRATION, pos);
        decorateWingNorthSouth(level, layout.roomMinX2, layout.roomMaxX2, layout.roomMinZ2, layout.roomMaxZ2,
            layout.originY, WingRoom.SANDBOX, WingRoom.MECHANICS, pos);

        placeZoneSpawnPads(level, layout, pos);
    }

    private enum WingRoom {
        COMBAT,
        ARENA,
        UI,
        TELEMETRY,
        SHOWCASE,
        INTEGRATION,
        SANDBOX,
        MECHANICS
    }

    private static void decorateWingEastWest(ServerLevel level, int minX, int maxX, int minZ, int maxZ,
                                             int floorY, WingRoom northRoom, WingRoom southRoom,
                                             BlockPos.MutableBlockPos pos) {
        int corridorCenterZ = doorCenter(minZ, maxZ);
        int corridorMinZ = corridorCenterZ - CORRIDOR_HALF_WIDTH;
        int corridorMaxZ = corridorCenterZ + CORRIDOR_HALF_WIDTH;

        fillRect(level, minX + 1, corridorMinZ, maxX - 1, corridorMaxZ, floorY, FLOOR_HUB, pos);
        buildInternalWallZ(level, minX + 1, maxX - 1, corridorMinZ - 1, floorY, pos);
        buildInternalWallZ(level, minX + 1, maxX - 1, corridorMaxZ + 1, floorY, pos);

        int doorCenterX = doorCenter(minX, maxX);
        carveDoorZ(level, doorCenterX, corridorMinZ - 1, floorY, pos);
        carveDoorZ(level, doorCenterX, corridorMaxZ + 1, floorY, pos);

        int portalCenterX = resolvePortalCenter(minX, maxX, doorCenterX);
        placePortalDisplayZ(level, portalCenterX, corridorMinZ - 1, floorY, Direction.NORTH,
            resolvePortalGlass(), resolvePortalColor(northRoom), pos);
        placePortalDisplayZ(level, portalCenterX, corridorMaxZ + 1, floorY, Direction.SOUTH,
            resolvePortalGlass(), resolvePortalColor(southRoom), pos);

        int northRoomMaxZ = corridorMinZ - 2;
        int southRoomMinZ = corridorMaxZ + 2;
        decorateZone(level, minX + 1, minZ + 1, maxX - 1, northRoomMaxZ, floorY, northRoom, pos);
        decorateZone(level, minX + 1, southRoomMinZ, maxX - 1, maxZ - 1, floorY, southRoom, pos);
    }

    private static void decorateWingNorthSouth(ServerLevel level, int minX, int maxX, int minZ, int maxZ,
                                               int floorY, WingRoom westRoom, WingRoom eastRoom,
                                               BlockPos.MutableBlockPos pos) {
        int corridorCenterX = doorCenter(minX, maxX);
        int corridorMinX = corridorCenterX - CORRIDOR_HALF_WIDTH;
        int corridorMaxX = corridorCenterX + CORRIDOR_HALF_WIDTH;

        fillRect(level, corridorMinX, minZ + 1, corridorMaxX, maxZ - 1, floorY, FLOOR_HUB, pos);
        buildInternalWallX(level, minZ + 1, maxZ - 1, corridorMinX - 1, floorY, pos);
        buildInternalWallX(level, minZ + 1, maxZ - 1, corridorMaxX + 1, floorY, pos);

        int doorCenterZ = doorCenter(minZ, maxZ);
        carveDoorX(level, doorCenterZ, corridorMinX - 1, floorY, pos);
        carveDoorX(level, doorCenterZ, corridorMaxX + 1, floorY, pos);

        int portalCenterZ = resolvePortalCenter(minZ, maxZ, doorCenterZ);
        placePortalDisplayX(level, portalCenterZ, corridorMinX - 1, floorY, Direction.WEST,
            resolvePortalGlass(), resolvePortalColor(westRoom), pos);
        placePortalDisplayX(level, portalCenterZ, corridorMaxX + 1, floorY, Direction.EAST,
            resolvePortalGlass(), resolvePortalColor(eastRoom), pos);

        int westRoomMaxX = corridorMinX - 2;
        int eastRoomMinX = corridorMaxX + 2;
        decorateZone(level, minX + 1, minZ + 1, westRoomMaxX, maxZ - 1, floorY, westRoom, pos);
        decorateZone(level, eastRoomMinX, minZ + 1, maxX - 1, maxZ - 1, floorY, eastRoom, pos);
    }

    private static void buildInternalWallZ(ServerLevel level, int minX, int maxX, int z, int floorY,
                                           BlockPos.MutableBlockPos pos) {
        for (int x = minX; x <= maxX; x++) {
            fillColumn(level, x, z, floorY + 1, floorY + WALL_HEIGHT - 1, WALL_FRAME, pos);
        }
    }

    private static void buildInternalWallX(ServerLevel level, int minZ, int maxZ, int x, int floorY,
                                           BlockPos.MutableBlockPos pos) {
        for (int z = minZ; z <= maxZ; z++) {
            fillColumn(level, x, z, floorY + 1, floorY + WALL_HEIGHT - 1, WALL_FRAME, pos);
        }
    }

    private static void carveDoorZ(ServerLevel level, int centerX, int z, int floorY,
                                   BlockPos.MutableBlockPos pos) {
        int doorHalf = 3;
        for (int x = centerX - doorHalf; x <= centerX + doorHalf; x++) {
            for (int y = floorY + 1; y <= floorY + DOOR_HEIGHT; y++) {
                pos.set(x, y, z);
                level.setBlock(pos, AIR, PLACEMENT_FLAGS);
            }
        }
    }

    private static void carveDoorX(ServerLevel level, int centerZ, int x, int floorY,
                                   BlockPos.MutableBlockPos pos) {
        int doorHalf = 3;
        for (int z = centerZ - doorHalf; z <= centerZ + doorHalf; z++) {
            for (int y = floorY + 1; y <= floorY + DOOR_HEIGHT; y++) {
                pos.set(x, y, z);
                level.setBlock(pos, AIR, PLACEMENT_FLAGS);
            }
        }
    }

    private static int resolvePortalCenter(int min, int max, int doorCenter) {
        int offset = 8;
        int candidate = doorCenter + offset;
        if (candidate + 2 <= max - 2) {
            return candidate;
        }
        candidate = doorCenter - offset;
        return Math.max(min + 3, Math.min(max - 3, candidate));
    }

    private static void decorateZone(ServerLevel level, int minX, int minZ, int maxX, int maxZ,
                                     int floorY, WingRoom room, BlockPos.MutableBlockPos pos) {
        switch (room) {
            case COMBAT -> decorateCombatRoom(level, minX, minZ, maxX, maxZ, floorY, pos);
            case ARENA -> decorateArenaRoom(level, minX, minZ, maxX, maxZ, floorY, pos);
            case UI -> decorateUiRoom(level, minX, minZ, maxX, maxZ, floorY, pos);
            case TELEMETRY -> decorateTelemetryRoom(level, minX, minZ, maxX, maxZ, floorY, pos);
            case SHOWCASE -> decorateShowcaseRoom(level, minX, minZ, maxX, maxZ, floorY, pos);
            case INTEGRATION -> decorateIntegrationRoom(level, minX, minZ, maxX, maxZ, floorY, pos);
            case SANDBOX -> decorateSandboxRoom(level, minX, minZ, maxX, maxZ, floorY, pos);
            case MECHANICS -> decorateMechanicsRoom(level, minX, minZ, maxX, maxZ, floorY, pos);
        }
    }

    private static BlockState resolvePortalGlass() {
        return PORTAL_GLASS;
    }

    private static NexusPortalColor resolvePortalColor(WingRoom room) {
        return switch (room) {
            case COMBAT -> NexusPortalColor.COMBAT;
            case ARENA -> NexusPortalColor.ARENA;
            case UI -> NexusPortalColor.UI;
            case TELEMETRY -> NexusPortalColor.TELEMETRY;
            case SHOWCASE -> NexusPortalColor.SHOWCASE;
            case INTEGRATION -> NexusPortalColor.INTEGRATION;
            case SANDBOX -> NexusPortalColor.SANDBOX;
            case MECHANICS -> NexusPortalColor.MECHANICS;
        };
    }

    private static void placePortalDisplayZ(ServerLevel level, int centerX, int wallZ, int floorY,
                                            Direction roomSide, BlockState glass, NexusPortalColor color,
                                            BlockPos.MutableBlockPos pos) {
        int width = 4;
        int height = 5;
        int startX = centerX - 1;
        int endX = startX + width - 1;
        int startY = floorY + 1;
        int endY = startY + height - 1;

        int portalZ = wallZ + roomSide.getStepZ();
        int roomGlassZ = portalZ + roomSide.getStepZ();
        BlockState portalState = ModBlocks.NEXUS_PORTAL.get().defaultBlockState()
            .setValue(NexusPortalBlock.AXIS, Direction.Axis.X)
            .setValue(NexusPortalBlock.COLOR, color);
        BlockState frameState = PORTAL_FRAME;

        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                boolean frame = x == startX || x == endX || y == startY || y == endY;
                pos.set(x, y, wallZ);
                level.setBlock(pos, frame ? WALL_FRAME : glass, PLACEMENT_FLAGS);
                pos.set(x, y, portalZ);
                level.setBlock(pos, frame ? frameState : portalState, PLACEMENT_FLAGS);
                pos.set(x, y, roomGlassZ);
                level.setBlock(pos, frame ? WALL_FRAME : glass, PLACEMENT_FLAGS);
            }
        }
    }

    private static void placePortalDisplayX(ServerLevel level, int centerZ, int wallX, int floorY,
                                            Direction roomSide, BlockState glass, NexusPortalColor color,
                                            BlockPos.MutableBlockPos pos) {
        int width = 4;
        int height = 5;
        int startZ = centerZ - 1;
        int endZ = startZ + width - 1;
        int startY = floorY + 1;
        int endY = startY + height - 1;

        int portalX = wallX + roomSide.getStepX();
        int roomGlassX = portalX + roomSide.getStepX();
        BlockState portalState = ModBlocks.NEXUS_PORTAL.get().defaultBlockState()
            .setValue(NexusPortalBlock.AXIS, Direction.Axis.Z)
            .setValue(NexusPortalBlock.COLOR, color);
        BlockState frameState = PORTAL_FRAME;

        for (int z = startZ; z <= endZ; z++) {
            for (int y = startY; y <= endY; y++) {
                boolean frame = z == startZ || z == endZ || y == startY || y == endY;
                pos.set(wallX, y, z);
                level.setBlock(pos, frame ? WALL_FRAME : glass, PLACEMENT_FLAGS);
                pos.set(portalX, y, z);
                level.setBlock(pos, frame ? frameState : portalState, PLACEMENT_FLAGS);
                pos.set(roomGlassX, y, z);
                level.setBlock(pos, frame ? WALL_FRAME : glass, PLACEMENT_FLAGS);
            }
        }
    }

    private static void buildCeilingAndSky(ServerLevel level, Layout layout, BlockPos.MutableBlockPos pos) {
        int ceilingY = layout.originY + WALL_HEIGHT + 1;
        fillRect(level, layout.minX + 1, layout.minZ + 1, layout.maxX - 1, layout.maxZ - 1, ceilingY, WALL_FRAME, pos);
        placeCeilingGrid(level, layout.minX + 3, layout.minZ + 3,
            layout.maxX - 3, layout.maxZ - 3, ceilingY, 8, pos);
    }

    private static final class Layout {
        private final int originX;
        private final int originY;
        private final int originZ;
        private final int minX;
        private final int maxX;
        private final int minZ;
        private final int maxZ;
        private final int centerMinX;
        private final int centerMaxX;
        private final int centerMinZ;
        private final int centerMaxZ;
        private final int roomMinX1;
        private final int roomMaxX1;
        private final int roomMinX2;
        private final int roomMaxX2;
        private final int roomMinZ1;
        private final int roomMaxZ1;
        private final int roomMinZ2;
        private final int roomMaxZ2;

        private Layout(BlockPos origin) {
            originX = origin.getX();
            originY = origin.getY();
            originZ = origin.getZ();

            minX = originX - HUB_HALF_SIZE;
            maxX = originX + HUB_HALF_SIZE - 1;
            minZ = originZ - HUB_HALF_SIZE;
            maxZ = originZ + HUB_HALF_SIZE - 1;

            centerMinX = originX - CENTER_HALF_SIZE;
            centerMaxX = originX + CENTER_HALF_SIZE - 1;
            centerMinZ = originZ - CENTER_HALF_SIZE;
            centerMaxZ = originZ + CENTER_HALF_SIZE - 1;

            roomMinX1 = minX;
            roomMaxX1 = centerMinX - 1;
            roomMinX2 = centerMaxX + 1;
            roomMaxX2 = maxX;

            roomMinZ1 = minZ;
            roomMaxZ1 = centerMinZ - 1;
            roomMinZ2 = centerMaxZ + 1;
            roomMaxZ2 = maxZ;
        }

        private static Layout from(BlockPos origin) {
            return new Layout(origin);
        }
    }

    private static void preloadChunks(ServerLevel level, int minX, int minZ, int maxX, int maxZ) {
        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                level.getChunk(cx, cz);
            }
        }
    }

    private static void buildBaseFloorPattern(ServerLevel level, int minX, int minZ, int maxX, int maxZ,
                                              int y, BlockPos.MutableBlockPos pos) {
        int gridStep = 10;
        int tileSize = 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int relX = x - minX;
                int relZ = z - minZ;
                boolean grid = relX % gridStep == 0 || relZ % gridStep == 0;
                boolean tile = ((relX / tileSize) + (relZ / tileSize)) % 2 == 0;
                BlockState state;
                if (grid) {
                    state = GRID;
                } else if (tile) {
                    state = FLOOR;
                } else {
                    state = FLOOR_ALT;
                }
                pos.set(x, y, z);
                level.setBlock(pos, state, PLACEMENT_FLAGS);
            }
        }
    }

    private static void buildRoomShell(ServerLevel level, int minX, int minZ, int maxX, int maxZ,
                                       int floorY, BlockPos.MutableBlockPos pos) {
        buildWalls(level, minX, minZ, maxX, maxZ, floorY + 1, floorY + WALL_HEIGHT, WALL, pos);
        buildWalls(level, minX, minZ, maxX, maxZ, floorY + 1, floorY + 1, WALL_FRAME, pos);
        buildWalls(level, minX, minZ, maxX, maxZ, floorY + WALL_HEIGHT, floorY + WALL_HEIGHT, WALL_FRAME, pos);
        placeCornerPillar(level, minX, minZ, floorY + 1, floorY + WALL_HEIGHT + 1, pos);
        placeCornerPillar(level, maxX, minZ, floorY + 1, floorY + WALL_HEIGHT + 1, pos);
        placeCornerPillar(level, minX, maxZ, floorY + 1, floorY + WALL_HEIGHT + 1, pos);
        placeCornerPillar(level, maxX, maxZ, floorY + 1, floorY + WALL_HEIGHT + 1, pos);
    }

    private static void decorateCorridors(ServerLevel level, int minX, int maxX, int minZ, int maxZ,
                                          int centerX, int centerZ, int floorY, BlockPos.MutableBlockPos pos) {
        int halfWidth = 3;
        fillRect(level, centerX - halfWidth, minZ, centerX + halfWidth, maxZ, floorY, FLOOR_HUB, pos);
        fillRect(level, minX, centerZ - halfWidth, maxX, centerZ + halfWidth, floorY, FLOOR_HUB, pos);

        int edgeOffset = halfWidth + 1;
        drawAxisLineX(level, minX, maxX, centerZ - edgeOffset, floorY, ACCENT, pos);
        drawAxisLineX(level, minX, maxX, centerZ + edgeOffset, floorY, ACCENT, pos);
        drawAxisLineZ(level, minZ, maxZ, centerX - edgeOffset, floorY, ACCENT, pos);
        drawAxisLineZ(level, minZ, maxZ, centerX + edgeOffset, floorY, ACCENT, pos);

        placeCorridorLights(level, minX, maxX, minZ, maxZ, centerX, centerZ, floorY, pos);
    }

    private static void placeCorridorLights(ServerLevel level, int minX, int maxX, int minZ, int maxZ,
                                            int centerX, int centerZ, int floorY, BlockPos.MutableBlockPos pos) {
        int spacing = 14;
        for (int x = minX + 4; x <= maxX - 4; x += spacing) {
            pos.set(x, floorY, centerZ);
            level.setBlock(pos, LIGHT_WARM, PLACEMENT_FLAGS);
        }
        for (int z = minZ + 4; z <= maxZ - 4; z += spacing) {
            pos.set(centerX, floorY, z);
            level.setBlock(pos, LIGHT_WARM, PLACEMENT_FLAGS);
        }
    }

    private static void buildConcourseRing(ServerLevel level, Layout layout, BlockPos.MutableBlockPos pos) {
        int ringOuter = CENTER_HALF_SIZE - 2;
        int ringInner = ringOuter - 2;
        int y = layout.originY;

        buildRing(level, layout.originX, layout.originZ, y, ringOuter, ringInner, RING, pos);
        buildRing(level, layout.originX, layout.originZ, y, ringInner - 1, ringInner - 2, TRIM, pos);

        placeConcourseBeacon(level, layout.originX, layout.originZ - ringOuter, y, pos);
        placeConcourseBeacon(level, layout.originX, layout.originZ + ringOuter, y, pos);
        placeConcourseBeacon(level, layout.originX - ringOuter, layout.originZ, y, pos);
        placeConcourseBeacon(level, layout.originX + ringOuter, layout.originZ, y, pos);
    }

    private static void placeConcourseBeacon(ServerLevel level, int x, int z, int floorY,
                                             BlockPos.MutableBlockPos pos) {
        fillRect(level, x - 1, z - 1, x + 1, z + 1, floorY, ACCENT, pos);
        fillColumn(level, x, z, floorY + 1, floorY + 4, TRIM, pos);
        pos.set(x, floorY + 5, z);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
    }

    private static void placeWayfindingLanes(ServerLevel level, int minX, int maxX, int minZ, int maxZ,
                                             int centerX, int centerZ, int floorY, BlockPos.MutableBlockPos pos) {
        int laneOffset = 3;
        int startZ = minZ + 3;
        int endZ = maxZ - 3;
        int startX = minX + 3;
        int endX = maxX - 3;

        for (int z = startZ; z <= centerZ - CENTER_HALF_SIZE - 2; z++) {
            pos.set(centerX - laneOffset, floorY, z);
            level.setBlock(pos, PAD_RED, PLACEMENT_FLAGS);
            pos.set(centerX + laneOffset, floorY, z);
            level.setBlock(pos, PAD_BLUE, PLACEMENT_FLAGS);
        }
        for (int z = centerZ + CENTER_HALF_SIZE + 2; z <= endZ; z++) {
            pos.set(centerX - laneOffset, floorY, z);
            level.setBlock(pos, PAD_GREEN, PLACEMENT_FLAGS);
            pos.set(centerX + laneOffset, floorY, z);
            level.setBlock(pos, PAD_ORANGE, PLACEMENT_FLAGS);
        }
        for (int x = startX; x <= centerX - CENTER_HALF_SIZE - 2; x++) {
            pos.set(x, floorY, centerZ - laneOffset);
            level.setBlock(pos, PAD_RED, PLACEMENT_FLAGS);
            pos.set(x, floorY, centerZ + laneOffset);
            level.setBlock(pos, PAD_GREEN, PLACEMENT_FLAGS);
        }
        for (int x = centerX + CENTER_HALF_SIZE + 2; x <= endX; x++) {
            pos.set(x, floorY, centerZ - laneOffset);
            level.setBlock(pos, PAD_BLUE, PLACEMENT_FLAGS);
            pos.set(x, floorY, centerZ + laneOffset);
            level.setBlock(pos, PAD_ORANGE, PLACEMENT_FLAGS);
        }
    }

    private static void decorateArrivalDeck(ServerLevel level, Layout layout, BlockPos.MutableBlockPos pos) {
        BlockPos offset = legacySpawnOffset("hub");
        int deckX = layout.originX + offset.getX();
        int deckZ = layout.originZ + offset.getZ();
        int deckY = layout.originY;

        int padHalf = 7;
        fillRect(level, deckX - padHalf, deckZ - padHalf, deckX + padHalf, deckZ + padHalf, deckY, FLOOR_HUB, pos);
        drawRectOutline(level, deckX - padHalf, deckZ - padHalf, deckX + padHalf, deckZ + padHalf, deckY, BORDER, pos);
        pos.set(deckX, deckY, deckZ);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);

        drawAxisLineZ(level, layout.centerMaxZ + 1, deckZ + padHalf, deckX, deckY, ACCENT, pos);
        placeArrivalSupplies(level, deckX, deckZ, deckY, pos);
        placeArrivalTools(level, deckX, deckZ, deckY, pos);
        placeAiProjector(level, deckX, deckZ, deckY, pos);
    }

    private static void placeArrivalSupplies(ServerLevel level, int deckX, int deckZ, int deckY,
                                             BlockPos.MutableBlockPos pos) {
        BlockState[] tools = {
            Blocks.BARREL.defaultBlockState(),
            Blocks.CHEST.defaultBlockState(),
            Blocks.ANVIL.defaultBlockState(),
            Blocks.GRINDSTONE.defaultBlockState(),
            Blocks.SMITHING_TABLE.defaultBlockState(),
            Blocks.ENDER_CHEST.defaultBlockState()
        };
        int spacing = 2;
        int startX = deckX - ((tools.length - 1) * spacing) / 2;
        int z = deckZ + 5;
        int x = startX;
        for (BlockState tool : tools) {
            pos.set(x, deckY, z);
            level.setBlock(pos, nn(tool, "tool"), PLACEMENT_FLAGS);
            x += spacing;
        }
    }

    private static void placeArrivalTools(ServerLevel level, int deckX, int deckZ, int deckY,
                                          BlockPos.MutableBlockPos pos) {
        placeReturnPad(level, deckX + 10, deckZ + 8, deckY, pos);
        placeBugKiosk(level, deckX - 10, deckZ + 8, deckY, pos);
    }

    private static void placeAiProjector(ServerLevel level, int centerX, int centerZ, int floorY,
                                         BlockPos.MutableBlockPos pos) {
        fillRect(level, centerX - 1, centerZ - 1, centerX + 1, centerZ + 1, floorY, CORE, pos);
        drawRectOutline(level, centerX - 2, centerZ - 2, centerX + 2, centerZ + 2, floorY, TRIM, pos);
        fillColumn(level, centerX, centerZ, floorY + 1, floorY + 3, CORE_GLASS, pos);
        pos.set(centerX, floorY + 4, centerZ);
        level.setBlock(pos, LIGHT_WARM, PLACEMENT_FLAGS);
    }

    private static void placeTutorialTrail(ServerLevel level, Layout layout, int deckX, int deckZ, int floorY,
                                           BlockPos.MutableBlockPos pos) {
        int[] markers = {deckZ - 6, deckZ - 16, deckZ - 26};
        for (int z : markers) {
            if (z <= layout.centerMinZ + 2) {
                continue;
            }
            placeGuideNode(level, deckX, z, floorY, pos);
        }
    }

    private static void placeGuideNode(ServerLevel level, int x, int z, int floorY,
                                       BlockPos.MutableBlockPos pos) {
        fillRect(level, x - 1, z - 1, x + 1, z + 1, floorY, ACCENT, pos);
        fillColumn(level, x, z, floorY + 1, floorY + 3, TRIM, pos);
        pos.set(x, floorY + 4, z);
        level.setBlock(pos, LIGHT_WARM, PLACEMENT_FLAGS);
    }

    private static void placeReturnPad(ServerLevel level, int centerX, int centerZ, int floorY,
                                       BlockPos.MutableBlockPos pos) {
        fillRect(level, centerX - 2, centerZ - 2, centerX + 2, centerZ + 2, floorY, RING, pos);
        drawRectOutline(level, centerX - 2, centerZ - 2, centerX + 2, centerZ + 2, floorY, BORDER, pos);
        pos.set(centerX, floorY, centerZ);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
        pos.set(centerX, floorY + 1, centerZ);
        level.setBlock(pos, nn(Blocks.RESPAWN_ANCHOR.defaultBlockState(), "RESPAWN_ANCHOR"), PLACEMENT_FLAGS);
    }

    private static void placeBugKiosk(ServerLevel level, int centerX, int centerZ, int floorY,
                                      BlockPos.MutableBlockPos pos) {
        fillRect(level, centerX - 2, centerZ - 2, centerX + 2, centerZ + 2, floorY, CONSOLE, pos);
        drawRectOutline(level, centerX - 2, centerZ - 2, centerX + 2, centerZ + 2, floorY, BORDER, pos);
        pos.set(centerX, floorY + 1, centerZ);
        level.setBlock(pos, nn(Blocks.LECTERN.defaultBlockState(), "LECTERN"), PLACEMENT_FLAGS);
        pos.set(centerX, floorY + 2, centerZ);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
    }

    private static void buildArch(ServerLevel level, int centerX, int z, int floorY, BlockPos.MutableBlockPos pos) {
        int height = 6;
        int halfWidth = 4;
        for (int y = floorY + 1; y <= floorY + height; y++) {
            pos.set(centerX - halfWidth, y, z);
            level.setBlock(pos, TRIM, PLACEMENT_FLAGS);
            pos.set(centerX + halfWidth, y, z);
            level.setBlock(pos, TRIM, PLACEMENT_FLAGS);
        }
        for (int x = centerX - halfWidth; x <= centerX + halfWidth; x++) {
            pos.set(x, floorY + height, z);
            level.setBlock(pos, BORDER, PLACEMENT_FLAGS);
        }
        pos.set(centerX, floorY + height - 1, z);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
    }

    private static void placeEntryPads(ServerLevel level, Layout layout, BlockPos.MutableBlockPos pos) {
        int northZ = layout.roomMaxZ1 + 3;
        int southZ = layout.roomMinZ2 - 3;
        int westCenterX = doorCenter(layout.roomMinX1, layout.roomMaxX1);
        int eastCenterX = doorCenter(layout.roomMinX2, layout.roomMaxX2);

        placePad(level, westCenterX, northZ, layout.originY, PAD_RED, LIGHT, pos);
        placePad(level, eastCenterX, northZ, layout.originY, PAD_BLUE, LIGHT, pos);
        placePad(level, westCenterX, southZ, layout.originY, PAD_YELLOW, LIGHT, pos);
        placePad(level, eastCenterX, southZ, layout.originY, PAD_PURPLE, LIGHT, pos);

        placeRoomTotem(level, layout.roomMinX1 + 5, layout.roomMinZ1 + 5, layout.originY, PAD_RED, COMBAT_TARGET, pos);
        placeRoomTotem(level, layout.roomMaxX2 - 5, layout.roomMinZ1 + 5, layout.originY, PAD_BLUE, SCREEN_ALT, pos);
        placeRoomTotem(level, layout.roomMinX1 + 5, layout.roomMaxZ2 - 5, layout.originY, PAD_YELLOW, nn(Blocks.CHEST.defaultBlockState(), "CHEST"), pos);
        placeRoomTotem(level, layout.roomMaxX2 - 5, layout.roomMaxZ2 - 5, layout.originY, PAD_PURPLE, nn(Blocks.ENDER_CHEST.defaultBlockState(), "ENDER_CHEST"), pos);
    }

    private static void placeRoomGatePads(ServerLevel level, Layout layout, BlockPos.MutableBlockPos pos) {
        int westDoorX = doorCenter(layout.roomMinX1, layout.roomMaxX1);
        int eastDoorX = doorCenter(layout.roomMinX2, layout.roomMaxX2);
        int northDoorZ = layout.roomMaxZ1 - 1;
        int southDoorZ = layout.roomMinZ2 + 1;

        placePad(level, westDoorX, northDoorZ, layout.originY, PAD_RED, LIGHT, pos);
        placePad(level, eastDoorX, northDoorZ, layout.originY, PAD_BLUE, LIGHT, pos);
        placePad(level, westDoorX, southDoorZ, layout.originY, PAD_GREEN, LIGHT, pos);
        placePad(level, eastDoorX, southDoorZ, layout.originY, PAD_ORANGE, LIGHT, pos);
    }

    private static void placeZoneGateArches(ServerLevel level, Layout layout, BlockPos.MutableBlockPos pos) {
        int westDoorX = doorCenter(layout.roomMinX1, layout.roomMaxX1);
        int eastDoorX = doorCenter(layout.roomMinX2, layout.roomMaxX2);
        int northGateZ = layout.roomMaxZ1 + 1;
        int southGateZ = layout.roomMinZ2 - 1;

        placeGateArchZ(level, westDoorX, northGateZ, layout.originY, PAD_RED, pos);
        placeGateArchZ(level, eastDoorX, northGateZ, layout.originY, PAD_BLUE, pos);
        placeGateArchZ(level, westDoorX, southGateZ, layout.originY, PAD_GREEN, pos);
        placeGateArchZ(level, eastDoorX, southGateZ, layout.originY, PAD_ORANGE, pos);
    }

    private static void placeDirectionalArrows(ServerLevel level, Layout layout, BlockPos.MutableBlockPos pos) {
        int westDoorX = doorCenter(layout.roomMinX1, layout.roomMaxX1);
        int eastDoorX = doorCenter(layout.roomMinX2, layout.roomMaxX2);
        int northGateZ = layout.roomMaxZ1 + 1;
        int southGateZ = layout.roomMinZ2 - 1;

        placeArrow(level, westDoorX, northGateZ - 3, layout.originY, Direction.NORTH, PAD_RED, pos);
        placeArrow(level, eastDoorX, northGateZ - 3, layout.originY, Direction.NORTH, PAD_BLUE, pos);
        placeArrow(level, westDoorX, southGateZ + 3, layout.originY, Direction.SOUTH, PAD_GREEN, pos);
        placeArrow(level, eastDoorX, southGateZ + 3, layout.originY, Direction.SOUTH, PAD_ORANGE, pos);

        placeArrow(level, layout.centerMinX - 3, layout.originZ, layout.originY, Direction.WEST, PAD_RED, pos);
        placeArrow(level, layout.centerMaxX + 3, layout.originZ, layout.originY, Direction.EAST, PAD_BLUE, pos);
    }

    private static void placeZoneSpawnPads(ServerLevel level, Layout layout, BlockPos.MutableBlockPos pos) {
        placeZoneSpawnPad(level, layout, legacySpawnOffset("combat"), PAD_RED, pos);
        placeZoneSpawnPad(level, layout, legacySpawnOffset("arena"), PAD_ORANGE, pos);
        placeZoneSpawnPad(level, layout, legacySpawnOffset("ui"), PAD_BLUE, pos);
        placeZoneSpawnPad(level, layout, legacySpawnOffset("telemetry"), PAD_GREEN, pos);
        placeZoneSpawnPad(level, layout, legacySpawnOffset("showcase"), PAD_YELLOW, pos);
        placeZoneSpawnPad(level, layout, legacySpawnOffset("integration"), PAD_PURPLE, pos);
        placeZoneSpawnPad(level, layout, legacySpawnOffset("sandbox"), GRID, pos);
        placeZoneSpawnPad(level, layout, legacySpawnOffset("mechanics"), METAL, pos);
    }

    private static void placeZoneSpawnPad(ServerLevel level, Layout layout, BlockPos offset, @Nonnull BlockState padState,
                                          BlockPos.MutableBlockPos pos) {
        int x = layout.originX + offset.getX();
        int z = layout.originZ + offset.getZ();
        int y = layout.originY + offset.getY();
        placePad(level, x, z, y, padState, LIGHT, pos);
    }

    private static void placeArrow(ServerLevel level, int centerX, int centerZ, int floorY, Direction direction,
                                   @Nonnull BlockState state, BlockPos.MutableBlockPos pos) {
        int[][] points = {
            {0, 0},
            {0, -1},
            {0, -2},
            {0, -3},
            {-1, -2},
            {1, -2}
        };
        for (int[] point : points) {
            int dx = point[0];
            int dz = point[1];
            int rx;
            int rz;
            switch (direction) {
                case SOUTH -> {
                    rx = -dx;
                    rz = -dz;
                }
                case EAST -> {
                    rx = -dz;
                    rz = dx;
                }
                case WEST -> {
                    rx = dz;
                    rz = -dx;
                }
                default -> {
                    rx = dx;
                    rz = dz;
                }
            }
            pos.set(centerX + rx, floorY, centerZ + rz);
            level.setBlock(pos, state, PLACEMENT_FLAGS);
        }
    }

    private static void placeGateArchZ(ServerLevel level, int centerX, int z, int floorY,
                                       @Nonnull BlockState accent, BlockPos.MutableBlockPos pos) {
        int archHalf = Math.max(4, (DOOR_WIDTH / 2) - 1);
        int pillarTop = floorY + 5;
        for (int y = floorY + 1; y <= pillarTop; y++) {
            pos.set(centerX - archHalf, y, z);
            level.setBlock(pos, TRIM, PLACEMENT_FLAGS);
            pos.set(centerX + archHalf, y, z);
            level.setBlock(pos, TRIM, PLACEMENT_FLAGS);
        }
        int capY = pillarTop + 1;
        for (int x = centerX - archHalf; x <= centerX + archHalf; x++) {
            pos.set(x, capY, z);
            level.setBlock(pos, accent, PLACEMENT_FLAGS);
        }
        pos.set(centerX, capY + 1, z);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
    }

    private static void placeRoomTotem(ServerLevel level, int x, int z, int floorY,
                                       @Nonnull BlockState accent, @Nonnull BlockState icon, BlockPos.MutableBlockPos pos) {
        fillColumn(level, x, z, floorY + 1, floorY + 5, accent, pos);
        pos.set(x, floorY + 6, z);
        level.setBlock(pos, icon, PLACEMENT_FLAGS);
        pos.set(x, floorY + 7, z);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
    }

    private static void buildCentralCrown(ServerLevel level, int centerX, int centerZ, int y,
                                          BlockPos.MutableBlockPos pos) {
        buildRing(level, centerX, centerZ, y, 7, 6, TRIM, pos);
        pos.set(centerX + 7, y + 1, centerZ);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
        pos.set(centerX - 7, y + 1, centerZ);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
        pos.set(centerX, y + 1, centerZ + 7);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
        pos.set(centerX, y + 1, centerZ - 7);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
    }

    private static void placeOverviewDeck(ServerLevel level, Layout layout, BlockPos.MutableBlockPos pos) {
        BlockPos offset = legacySpawnOffset("overview");
        int centerX = layout.originX + offset.getX();
        int centerZ = layout.originZ + offset.getZ();
        int deckY = layout.originY + offset.getY();
        int half = 7;
        int minX = centerX - half;
        int maxX = centerX + half;
        int minZ = centerZ - half;
        int maxZ = centerZ + half;

        fillRect(level, minX, minZ, maxX, maxZ, deckY, TRIM, pos);
        drawRectOutline(level, minX, minZ, maxX, maxZ, deckY, BORDER, pos);
        placeDeckRail(level, minX, minZ, maxX, maxZ, deckY + 1, pos);
        placeZoneSpawnPad(level, layout, offset, ACCENT, pos);

        pos.set(minX, deckY + 1, minZ);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
        pos.set(maxX, deckY + 1, minZ);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
        pos.set(minX, deckY + 1, maxZ);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
        pos.set(maxX, deckY + 1, maxZ);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);

        int accessX = centerX;
        int accessZ = centerZ + half - 1;
        fillColumn(level, accessX, accessZ, layout.originY, deckY, nn(Blocks.SCAFFOLDING.defaultBlockState(), "SCAFFOLDING"), pos);
        placePad(level, accessX, accessZ, layout.originY, ACCENT, LIGHT, pos);
    }

    private static void placeDeckRail(ServerLevel level, int minX, int minZ, int maxX, int maxZ, int y,
                                      BlockPos.MutableBlockPos pos) {
        for (int x = minX; x <= maxX; x++) {
            pos.set(x, y, minZ);
            level.setBlock(pos, RAIL, PLACEMENT_FLAGS);
            pos.set(x, y, maxZ);
            level.setBlock(pos, RAIL, PLACEMENT_FLAGS);
        }
        for (int z = minZ; z <= maxZ; z++) {
            pos.set(minX, y, z);
            level.setBlock(pos, RAIL, PLACEMENT_FLAGS);
            pos.set(maxX, y, z);
            level.setBlock(pos, RAIL, PLACEMENT_FLAGS);
        }
    }

    private static int splitCoordinate(int min, int max) {
        return min + (max - min) / 2;
    }

    private static void drawDividerZ(ServerLevel level, int minX, int maxX, int z,
                                     int floorY, BlockPos.MutableBlockPos pos) {
        fillRect(level, minX + 1, z, maxX - 1, z, floorY, BORDER, pos);
    }

    private static void drawDividerX(ServerLevel level, int minZ, int maxZ, int x,
                                     int floorY, BlockPos.MutableBlockPos pos) {
        fillRect(level, x, minZ + 1, x, maxZ - 1, floorY, BORDER, pos);
    }

    private static void placeOuterWindows(ServerLevel level, int minX, int minZ, int maxX, int maxZ,
                                          int yMin, int yMax, BlockPos.MutableBlockPos pos) {
        for (int x = minX + 4; x <= maxX - 4; x += 6) {
            int x2 = Math.min(x + 2, maxX - 4);
            fillVolume(level, x, yMin, minZ, x2, yMax, minZ, WINDOW, pos);
            fillVolume(level, x, yMin, maxZ, x2, yMax, maxZ, WINDOW_DARK, pos);
        }
        for (int z = minZ + 4; z <= maxZ - 4; z += 6) {
            int z2 = Math.min(z + 2, maxZ - 4);
            fillVolume(level, minX, yMin, z, minX, yMax, z2, WINDOW_DARK, pos);
            fillVolume(level, maxX, yMin, z, maxX, yMax, z2, WINDOW, pos);
        }
    }

    private static void placeCeilingGrid(ServerLevel level, int minX, int minZ, int maxX, int maxZ,
                                         int y, int step, BlockPos.MutableBlockPos pos) {
        for (int x = minX; x <= maxX; x += step) {
            for (int z = minZ; z <= maxZ; z += step) {
                pos.set(x, y, z);
                level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
            }
        }
    }

    private static void decorateSkyline(ServerLevel level, int minX, int minZ, int maxX, int maxZ,
                                        int floorY, BlockPos.MutableBlockPos pos) {
        int baseY = floorY + WALL_HEIGHT + 2;
        int topY = baseY + 6;

        placeSkyPylon(level, minX + 2, minZ + 2, baseY, topY, pos);
        placeSkyPylon(level, maxX - 2, minZ + 2, baseY, topY, pos);
        placeSkyPylon(level, minX + 2, maxZ - 2, baseY, topY, pos);
        placeSkyPylon(level, maxX - 2, maxZ - 2, baseY, topY, pos);

        buildSkyFrame(level, minX + 2, minZ + 2, maxX - 2, maxZ - 2, topY, pos);
    }

    private static void placeSkyPylon(ServerLevel level, int x, int z, int yStart, int yEnd,
                                      BlockPos.MutableBlockPos pos) {
        fillColumn(level, x, z, yStart, yEnd, METAL, pos);
        for (int y = yStart + 1; y <= yEnd; y += 3) {
            pos.set(x, y, z);
            level.setBlock(pos, WINDOW_DARK, PLACEMENT_FLAGS);
        }
        pos.set(x, yEnd + 1, z);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
    }

    private static void buildSkyFrame(ServerLevel level, int minX, int minZ, int maxX, int maxZ,
                                      int y, BlockPos.MutableBlockPos pos) {
        for (int x = minX; x <= maxX; x++) {
            pos.set(x, y, minZ);
            level.setBlock(pos, TRUSS, PLACEMENT_FLAGS);
            pos.set(x, y, maxZ);
            level.setBlock(pos, TRUSS, PLACEMENT_FLAGS);
        }
        for (int z = minZ; z <= maxZ; z++) {
            pos.set(minX, y, z);
            level.setBlock(pos, TRUSS, PLACEMENT_FLAGS);
            pos.set(maxX, y, z);
            level.setBlock(pos, TRUSS, PLACEMENT_FLAGS);
        }
    }

    private static void buildHaloRing(ServerLevel level, int centerX, int centerZ, int y, int radius,
                                      BlockPos.MutableBlockPos pos) {
        buildRing(level, centerX, centerZ, y, radius, radius - 1, BORDER, pos);
        buildRing(level, centerX, centerZ, y + 1, radius - 1, radius - 2, TRIM, pos);
        placeTrussCross(level, centerX, centerZ, y, radius - 3, pos);

        int diag = (int) Math.floor(radius / Math.sqrt(2));
        placeHaloLight(level, centerX + radius, centerZ, y + 1, pos);
        placeHaloLight(level, centerX - radius, centerZ, y + 1, pos);
        placeHaloLight(level, centerX, centerZ + radius, y + 1, pos);
        placeHaloLight(level, centerX, centerZ - radius, y + 1, pos);
        placeHaloLight(level, centerX + diag, centerZ + diag, y + 1, pos);
        placeHaloLight(level, centerX - diag, centerZ + diag, y + 1, pos);
        placeHaloLight(level, centerX + diag, centerZ - diag, y + 1, pos);
        placeHaloLight(level, centerX - diag, centerZ - diag, y + 1, pos);
    }

    private static void placeHaloLight(ServerLevel level, int x, int z, int y, BlockPos.MutableBlockPos pos) {
        pos.set(x, y, z);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
    }

    private static void buildCentralSpire(ServerLevel level, int centerX, int centerZ, int startY, int endY,
                                          BlockPos.MutableBlockPos pos) {
        fillVolume(level, centerX - 1, startY, centerZ - 1, centerX + 1, endY, centerZ + 1, CORE_GLASS, pos);
        for (int y = startY; y <= endY; y += 2) {
            pos.set(centerX, y, centerZ);
            level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
        }
        drawRectOutline(level, centerX - 2, centerZ - 2, centerX + 2, centerZ + 2, endY + 1, TRIM, pos);
        pos.set(centerX, endY + 2, centerZ);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
    }

    private static void decorateHub(ServerLevel level, int centerX, int centerZ, int floorY,
                                    BlockPos.MutableBlockPos pos) {
        int hubInset = CENTER_HALF_SIZE - 3;
        int hubMinX = centerX - hubInset;
        int hubMaxX = centerX + hubInset;
        int hubMinZ = centerZ - hubInset;
        int hubMaxZ = centerZ + hubInset;

        fillRect(level, hubMinX, hubMinZ, hubMaxX, hubMaxZ, floorY, FLOOR_HUB, pos);
        drawRectOutline(level, hubMinX, hubMinZ, hubMaxX, hubMaxZ, floorY, BORDER, pos);

        fillRect(level, centerX - 2, centerZ - 2, centerX + 2, centerZ + 2, floorY, CORE, pos);
        pos.set(centerX, floorY, centerZ);
        level.setBlock(pos, LIGHT_WARM, PLACEMENT_FLAGS);

        placeConsoleRow(level, hubMinX + 3, hubMaxX - 3, hubMinZ + 2, floorY, pos);
        placeConsoleRow(level, hubMinX + 3, hubMaxX - 3, hubMaxZ - 2, floorY, pos);

        placeOpsBench(level, hubMaxX - 2, centerZ - 4, floorY, pos);
        placeGuideLectern(level, hubMinX + 2, centerZ, floorY, pos);
        placeCalmPlanters(level, centerX, centerZ, floorY, pos);
    }

    private static void placeConsoleRow(ServerLevel level, int minX, int maxX, int z, int floorY,
                                        BlockPos.MutableBlockPos pos) {
        for (int x = minX; x <= maxX; x += 2) {
            pos.set(x, floorY, z);
            level.setBlock(pos, CONSOLE, PLACEMENT_FLAGS);
            pos.set(x, floorY + 1, z);
            level.setBlock(pos, SCREEN_FRAME, PLACEMENT_FLAGS);
        }
    }

    private static void placeOpsBench(ServerLevel level, int x, int startZ, int floorY,
                                      BlockPos.MutableBlockPos pos) {
        BlockState[] bench = {
            Blocks.CRAFTING_TABLE.defaultBlockState(),
            Blocks.ANVIL.defaultBlockState(),
            Blocks.GRINDSTONE.defaultBlockState(),
            Blocks.STONECUTTER.defaultBlockState(),
            Blocks.SMITHING_TABLE.defaultBlockState(),
            Blocks.ENDER_CHEST.defaultBlockState()
        };
        int z = startZ;
        for (BlockState state : bench) {
            pos.set(x, floorY, z);
            level.setBlock(pos, state, PLACEMENT_FLAGS);
            z += 2;
        }
    }

    private static void placeGuideLectern(ServerLevel level, int x, int z, int floorY,
                                          BlockPos.MutableBlockPos pos) {
        fillRect(level, x - 1, z - 1, x + 1, z + 1, floorY, CONSOLE, pos);
        pos.set(x, floorY + 1, z);
        level.setBlock(pos, Blocks.LECTERN.defaultBlockState(), PLACEMENT_FLAGS);
    }

    private static void placeCalmPlanters(ServerLevel level, int centerX, int centerZ, int floorY,
                                          BlockPos.MutableBlockPos pos) {
        int hubInset = CENTER_HALF_SIZE - 3;
        int hubMinX = centerX - hubInset;
        int hubMaxX = centerX + hubInset;
        int hubMinZ = centerZ - hubInset;
        int hubMaxZ = centerZ + hubInset;

        int stripHalf = 5;
        int depth = 3;
        int offset = 6;

        placePlanterStripZ(level, centerX - stripHalf, centerX + stripHalf, hubMinZ + offset, depth, floorY, pos);
        placePlanterStripZ(level, centerX - stripHalf, centerX + stripHalf,
            hubMaxZ - offset - (depth - 1), depth, floorY, pos);

        placePlanterStripX(level, centerZ - stripHalf, centerZ + stripHalf, hubMinX + offset, depth, floorY, pos);
        placePlanterStripX(level, centerZ - stripHalf, centerZ + stripHalf,
            hubMaxX - offset - (depth - 1), depth, floorY, pos);
    }

    private static void placePlanterStripZ(ServerLevel level, int startX, int endX, int startZ, int depth,
                                           int floorY, BlockPos.MutableBlockPos pos) {
        int endZ = startZ + depth - 1;
        BlockState frame = TRIM;
        BlockState soil = Blocks.MOSS_BLOCK.defaultBlockState();
        BlockState shrubA = Blocks.AZALEA.defaultBlockState();
        BlockState shrubB = Blocks.FLOWERING_AZALEA.defaultBlockState();

        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                boolean edge = x == startX || x == endX || z == startZ || z == endZ;
                pos.set(x, floorY, z);
                level.setBlock(pos, edge ? frame : soil, PLACEMENT_FLAGS);
            }
        }

        int plantZ = startZ + depth / 2;
        int plantIndex = 0;
        for (int x = startX + 1; x <= endX - 1; x += 3) {
            pos.set(x, floorY + 1, plantZ);
            level.setBlock(pos, (plantIndex++ & 1) == 0 ? shrubA : shrubB, PLACEMENT_FLAGS);
        }
    }

    private static void placePlanterStripX(ServerLevel level, int startZ, int endZ, int startX, int depth,
                                           int floorY, BlockPos.MutableBlockPos pos) {
        int endX = startX + depth - 1;
        BlockState frame = TRIM;
        BlockState soil = Blocks.MOSS_BLOCK.defaultBlockState();
        BlockState shrubA = Blocks.AZALEA.defaultBlockState();
        BlockState shrubB = Blocks.FLOWERING_AZALEA.defaultBlockState();

        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                boolean edge = x == startX || x == endX || z == startZ || z == endZ;
                pos.set(x, floorY, z);
                level.setBlock(pos, edge ? frame : soil, PLACEMENT_FLAGS);
            }
        }

        int plantX = startX + depth / 2;
        int plantIndex = 0;
        for (int z = startZ + 1; z <= endZ - 1; z += 3) {
            pos.set(plantX, floorY + 1, z);
            level.setBlock(pos, (plantIndex++ & 1) == 0 ? shrubA : shrubB, PLACEMENT_FLAGS);
        }
    }

    private static void placeHubToolPods(ServerLevel level, int centerX, int centerZ, int floorY,
                                         BlockPos.MutableBlockPos pos) {
        int offset = CENTER_HALF_SIZE - 6;
        placeToolPod(level, centerX - offset, centerZ - offset, floorY, pos);
        placeToolPod(level, centerX + offset, centerZ - offset, floorY, pos);
        placeToolPod(level, centerX - offset, centerZ + offset, floorY, pos);
        placeToolPod(level, centerX + offset, centerZ + offset, floorY, pos);
    }

    private static void placeToolPod(ServerLevel level, int centerX, int centerZ, int floorY,
                                     BlockPos.MutableBlockPos pos) {
        fillRect(level, centerX - 1, centerZ - 1, centerX + 1, centerZ + 1, floorY, METAL, pos);
        pos.set(centerX, floorY, centerZ);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
        pos.set(centerX - 1, floorY, centerZ - 1);
        level.setBlock(pos, nn(Blocks.CRAFTING_TABLE.defaultBlockState(), "CRAFTING_TABLE"), PLACEMENT_FLAGS);
        pos.set(centerX + 1, floorY, centerZ - 1);
        level.setBlock(pos, nn(Blocks.ANVIL.defaultBlockState(), "ANVIL"), PLACEMENT_FLAGS);
        pos.set(centerX - 1, floorY, centerZ + 1);
        level.setBlock(pos, nn(Blocks.GRINDSTONE.defaultBlockState(), "GRINDSTONE"), PLACEMENT_FLAGS);
        pos.set(centerX + 1, floorY, centerZ + 1);
        level.setBlock(pos, nn(Blocks.STONECUTTER.defaultBlockState(), "STONECUTTER"), PLACEMENT_FLAGS);
        pos.set(centerX - 1, floorY, centerZ);
        level.setBlock(pos, nn(Blocks.SMITHING_TABLE.defaultBlockState(), "SMITHING_TABLE"), PLACEMENT_FLAGS);
        pos.set(centerX + 1, floorY, centerZ);
        level.setBlock(pos, nn(Blocks.ENCHANTING_TABLE.defaultBlockState(), "ENCHANTING_TABLE"), PLACEMENT_FLAGS);
        pos.set(centerX, floorY, centerZ - 1);
        level.setBlock(pos, nn(Blocks.CHEST.defaultBlockState(), "CHEST"), PLACEMENT_FLAGS);
        pos.set(centerX, floorY, centerZ + 1);
        level.setBlock(pos, nn(Blocks.ENDER_CHEST.defaultBlockState(), "ENDER_CHEST"), PLACEMENT_FLAGS);
    }

    private static void placeWorkflowNodes(ServerLevel level, int centerX, int centerZ, int floorY,
                                           BlockPos.MutableBlockPos pos) {
        placeWorkflowNode(level, centerX, centerZ - 12, floorY, PAD_BLUE, nn(Blocks.CHEST.defaultBlockState(), "CHEST"), pos);
        placeWorkflowNode(level, centerX + 12, centerZ, floorY, PAD_ORANGE, nn(Blocks.ANVIL.defaultBlockState(), "ANVIL"), pos);
        placeWorkflowNode(level, centerX, centerZ + 12, floorY, PAD_GREEN, nn(Blocks.LECTERN.defaultBlockState(), "LECTERN"), pos);
        placeWorkflowNode(level, centerX - 12, centerZ, floorY, PAD_RED, COMBAT_TARGET, pos);
    }

    private static void placeWorkflowNode(ServerLevel level, int centerX, int centerZ, int floorY,
                                          @Nonnull BlockState padState, @Nonnull BlockState icon, BlockPos.MutableBlockPos pos) {
        fillRect(level, centerX - 1, centerZ - 1, centerX + 1, centerZ + 1, floorY, padState, pos);
        pos.set(centerX, floorY + 1, centerZ);
        level.setBlock(pos, icon, PLACEMENT_FLAGS);
        pos.set(centerX, floorY + 2, centerZ);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
    }

    private static void placeHubUtilityRow(ServerLevel level, int centerX, int centerZ, int floorY,
                                           BlockPos.MutableBlockPos pos) {
        BlockState[] utilities = {
            Blocks.BREWING_STAND.defaultBlockState(),
            Blocks.FURNACE.defaultBlockState(),
            Blocks.BLAST_FURNACE.defaultBlockState(),
            Blocks.SMOKER.defaultBlockState(),
            Blocks.CAULDRON.defaultBlockState(),
            Blocks.LECTERN.defaultBlockState()
        };
        int spacing = 2;
        int startX = centerX - ((utilities.length - 1) * spacing) / 2;
        int z = centerZ + 11;
        int x = startX;
        for (BlockState utility : utilities) {
            pos.set(x, floorY, z);
            level.setBlock(pos, nn(utility, "utility"), PLACEMENT_FLAGS);
            x += spacing;
        }
    }

    private static void placeAfkAlcove(ServerLevel level, Layout layout, BlockPos.MutableBlockPos pos) {
        int minX = layout.centerMinX + 2;
        int maxX = minX + 4;
        int minZ = layout.centerMinZ + 10;
        int maxZ = minZ + 4;
        int floorY = layout.originY;
        @Nonnull BlockState floor = nn(Blocks.GRAY_WOOL.defaultBlockState(), "GRAY_WOOL");
        @Nonnull BlockState wall = nn(Blocks.BLACK_CONCRETE.defaultBlockState(), "BLACK_CONCRETE");

        fillRect(level, minX, minZ, maxX, maxZ, floorY, floor, pos);
        for (int x = minX; x <= maxX; x++) {
            fillColumn(level, x, minZ, floorY + 1, floorY + 3, wall, pos);
        }
        for (int z = minZ; z <= maxZ; z++) {
            fillColumn(level, minX, z, floorY + 1, floorY + 3, wall, pos);
            fillColumn(level, maxX, z, floorY + 1, floorY + 3, wall, pos);
        }
        fillRect(level, minX, minZ, maxX, maxZ, floorY + 4, wall, pos);
        pos.set((minX + maxX) / 2, floorY + 1, (minZ + maxZ) / 2);
        level.setBlock(pos, nn(Blocks.SOUL_LANTERN.defaultBlockState(), "SOUL_LANTERN"), PLACEMENT_FLAGS);
        pos.set(minX + 1, floorY + 1, maxZ - 1);
        level.setBlock(pos, nn(Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), "SMOOTH_STONE_SLAB"), PLACEMENT_FLAGS);
    }

    private static void decorateCombatRoom(ServerLevel level, int minX, int minZ, int maxX, int maxZ,
                                           int floorY, BlockPos.MutableBlockPos pos) {
        decorateRoomFloor(level, minX, minZ, maxX, maxZ, floorY, PAD_RED, pos);
        int centerX = (minX + maxX) / 2;
        int splitZ = splitCoordinate(minZ, maxZ);
        int rangeMinZ = minZ + 4;
        int rangeMaxZ = splitZ - 3;
        int meleeMinZ = splitZ + 3;
        int meleeMaxZ = maxZ - 4;

        decorateCombatRangeZone(level, minX + 3, rangeMinZ, maxX - 3, rangeMaxZ, floorY, pos);
        decorateCombatMeleeZone(level, minX + 3, meleeMinZ, maxX - 3, meleeMaxZ, floorY, pos);
        placePad(level, centerX, splitZ, floorY, PAD_RED, LIGHT, pos);
    }

    private static void decorateCombatRangeZone(ServerLevel level, int minX, int minZ, int maxX, int maxZ,
                                                int floorY, BlockPos.MutableBlockPos pos) {
        int centerX = (minX + maxX) / 2;
        int laneSpacing = Math.max(6, (maxX - minX) / 3);
        int laneMinZ = minZ + 1;
        int laneMaxZ = maxZ - 1;

        placeTargetWall(level, minX + 1, maxX - 1, floorY + 2, minZ, pos);
        placeRangeLane(level, centerX - laneSpacing, laneMinZ, laneMaxZ, floorY, PAD_RED, GRID, pos);
        placeRangeLane(level, centerX + laneSpacing, laneMinZ, laneMaxZ, floorY, PAD_RED, GRID, pos);

        placeDummyPads(level, centerX, maxZ - 2, floorY, pos);
        placeUtilityBenchRow(level, minX + 1, maxZ - 1, maxX - 1, floorY, pos);
    }

    private static void decorateCombatMeleeZone(ServerLevel level, int minX, int minZ, int maxX, int maxZ,
                                                int floorY, BlockPos.MutableBlockPos pos) {
        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;
        int zoneSize = Math.min(maxX - minX, maxZ - minZ);
        int ringOuter = Math.max(6, zoneSize / 3);
        int ringInner = Math.max(3, ringOuter - 2);

        buildRing(level, centerX, centerZ, floorY, ringOuter, ringInner, COMBAT_RING, pos);
        placeTrainingPost(level, centerX - ringInner, centerZ, floorY, pos);
        placeTrainingPost(level, centerX + ringInner, centerZ, floorY, pos);
    }

    private static void placeRangeLane(ServerLevel level, int x, int minZ, int maxZ, int floorY,
                                       @Nonnull BlockState laneState, @Nonnull BlockState markerState, BlockPos.MutableBlockPos pos) {
        for (int z = minZ; z <= maxZ; z++) {
            pos.set(x, floorY, z);
            level.setBlock(pos, laneState, PLACEMENT_FLAGS);
        }
        for (int z = minZ; z <= maxZ; z += 4) {
            pos.set(x, floorY, z);
            level.setBlock(pos, markerState, PLACEMENT_FLAGS);
        }
    }

    private static void placeDummyPads(ServerLevel level, int centerX, int z, int floorY,
                                       BlockPos.MutableBlockPos pos) {
        placePad(level, centerX - 4, z, floorY, PAD_RED, LIGHT, pos);
        placePad(level, centerX, z, floorY, PAD_RED, LIGHT, pos);
        placePad(level, centerX + 4, z, floorY, PAD_RED, LIGHT, pos);
    }

    private static void spawnCombatDummies(ServerLevel level, BlockPos origin) {
        BlockPos center = origin.offset(legacySpawnOffset("combat"));
        int floorY = center.getY();
        int centerX = center.getX();
        int dummyZ = center.getZ() - 8;
        int[] offsets = {-4, 0, 4};
        String[] ids = {
            "nexus_dummy_west",
            "nexus_dummy_center",
            "nexus_dummy_east"
        };
        for (int i = 0; i < offsets.length; i++) {
            spawnDummy(level, new BlockPos(centerX + offsets[i], floorY + 1, dummyZ), ids[i]);
        }
    }

    private static void spawnDummy(ServerLevel level, BlockPos pos, String id) {
        DummmmmmyCompat.removeDummy(level, id);
        DummmmmmyCompat.spawnDummy(level, pos, id);
    }

    private static Map<String, BlockPos> loadLegacySpawnOffsets() {
        Map<String, BlockPos> offsets = new HashMap<>();
        for (ZoneDefinition zone : ZonePresets.createLegacyZones(BlockPos.ZERO)) {
            BlockPos spawnOffset = zone.spawnOffset();
            if (spawnOffset != null) {
                offsets.put(zone.zoneId(), spawnOffset);
            }
        }
        return offsets;
    }

    @Nonnull
    private static BlockPos legacySpawnOffset(@Nonnull String zoneId) {
        BlockPos offset = LEGACY_SPAWN_OFFSETS.get(zoneId);
        if (offset == null) {
            LOGGER.warn("[NexusHubBuilder] Missing legacy spawn offset for zone '{}'", zoneId);
            return BlockPos.ZERO;
        }
        return offset;
    }

    private static void decorateUiRoom(ServerLevel level, int minX, int minZ, int maxX, int maxZ,
                                       int floorY, BlockPos.MutableBlockPos pos) {
        decorateRoomFloor(level, minX, minZ, maxX, maxZ, floorY, UI_ACCENT, pos);
        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;
        int wallMinX = minX + 4;
        int wallMaxX = maxX - 4;

        placeScreenPanelZ(level, wallMinX, wallMaxX, floorY + 2, floorY + 5, minZ,
            SCREEN_FRAME, SCREEN, SCREEN_FRAME, -1, pos);
        placeConsoleTableNS(level, centerX - 6, centerZ, floorY, true, pos);
        placeConsoleTableNS(level, centerX + 2, centerZ, floorY, true, pos);
        placePad(level, centerX, centerZ, floorY, PAD_BLUE, LIGHT, pos);
    }

    private static void decorateTelemetryRoom(ServerLevel level, int minX, int minZ, int maxX, int maxZ,
                                              int floorY, BlockPos.MutableBlockPos pos) {
        decorateRoomFloor(level, minX, minZ, maxX, maxZ, floorY, TELEMETRY_ACCENT, pos);
        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;

        placeServerRackRow(level, minX + 2, maxX - 2, minZ + 2, floorY, pos);
        placeScreenPanelZ(level, minX + 3, maxX - 3, floorY + 2, floorY + 5, maxZ,
            SCREEN_FRAME, SCREEN_ALT, SCREEN_FRAME, 1, pos);
        placeConsoleTableEW(level, centerX - 6, centerZ, floorY, true, pos);
        placePad(level, centerX, centerZ, floorY, PAD_GREEN, LIGHT, pos);
    }

    private static void decorateArenaRoom(ServerLevel level, int minX, int minZ, int maxX, int maxZ,
                                          int floorY, BlockPos.MutableBlockPos pos) {
        decorateRoomFloor(level, minX, minZ, maxX, maxZ, floorY, PAD_ORANGE, pos);
        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;
        int zoneSize = Math.min(maxX - minX, maxZ - minZ);
        int ringHalf = Math.max(8, zoneSize / 3);

        drawRectOutline(level, centerX - ringHalf, centerZ - ringHalf, centerX + ringHalf, centerZ + ringHalf,
            floorY, ARENA_RING, pos);
        drawRectOutline(level, centerX - ringHalf + 1, centerZ - ringHalf + 1, centerX + ringHalf - 1,
            centerZ + ringHalf - 1, floorY, ARENA_RING_INNER, pos);
        placePad(level, centerX, centerZ, floorY, PAD_ORANGE, LIGHT, pos);
        placeUtilityBenchRow(level, minX + 2, maxZ - 1, maxX - 2, floorY, pos);
    }

    private static void decorateArenaBowl(ServerLevel level, int minX, int minZ, int maxX, int maxZ,
                                          int floorY, BlockPos.MutableBlockPos pos) {
        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;
        int zoneSize = Math.min(maxX - minX, maxZ - minZ);
        int ringHalf = Math.max(6, zoneSize / 3);
        int ringMinX = centerX - ringHalf;
        int ringMaxX = centerX + ringHalf;
        int ringMinZ = centerZ - ringHalf;
        int ringMaxZ = centerZ + ringHalf;

        drawRectOutline(level, ringMinX, ringMinZ, ringMaxX, ringMaxZ, floorY + 1, ARENA_RING, pos);
        drawRectOutline(level, ringMinX + 1, ringMinZ + 1, ringMaxX - 1, ringMaxZ - 1, floorY + 1,
            ARENA_RING_INNER, pos);
        placeArenaBarrier(level, ringMinX - 1, ringMinZ - 1, ringMaxX + 1, ringMaxZ + 1, floorY + 1, pos);

        placeCornerPillar(level, ringMinX, ringMinZ, floorY + 1, floorY + 4, pos);
        placeCornerPillar(level, ringMaxX, ringMinZ, floorY + 1, floorY + 4, pos);
        placeCornerPillar(level, ringMinX, ringMaxZ, floorY + 1, floorY + 4, pos);
        placeCornerPillar(level, ringMaxX, ringMaxZ, floorY + 1, floorY + 4, pos);

        fillRect(level, centerX - 1, centerZ - 1, centerX + 1, centerZ + 1, floorY, PAD_ORANGE, pos);
    }

    private static void decorateArenaStaging(ServerLevel level, int minX, int minZ, int maxX, int maxZ,
                                             int floorY, BlockPos.MutableBlockPos pos) {
        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;

        drawAxisLineX(level, minX + 1, maxX - 1, centerZ, floorY, ARENA_RING_INNER, pos);
        placePad(level, centerX, centerZ, floorY, PAD_ORANGE, LIGHT, pos);
        placeConsoleTableNS(level, centerX - 9, centerZ - 2, floorY, false, pos);
        placeConsoleTableNS(level, centerX + 6, centerZ - 2, floorY, false, pos);
        placeArenaBriefingWall(level, centerX, minZ + 1, floorY, pos);
        placeUtilityBenchRow(level, minX + 2, maxZ - 1, maxX - 2, floorY, pos);
    }

    private static void placeArenaBarrier(ServerLevel level, int minX, int minZ, int maxX, int maxZ,
                                          int y, BlockPos.MutableBlockPos pos) {
        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;
        int gap = 2;
        for (int x = minX; x <= maxX; x++) {
            if (Math.abs(x - centerX) <= gap) {
                continue;
            }
            pos.set(x, y, minZ);
            level.setBlock(pos, ARENA_BARRIER, PLACEMENT_FLAGS);
            pos.set(x, y, maxZ);
            level.setBlock(pos, ARENA_BARRIER, PLACEMENT_FLAGS);
        }
        for (int z = minZ; z <= maxZ; z++) {
            if (Math.abs(z - centerZ) <= gap) {
                continue;
            }
            pos.set(minX, y, z);
            level.setBlock(pos, ARENA_BARRIER, PLACEMENT_FLAGS);
            pos.set(maxX, y, z);
            level.setBlock(pos, ARENA_BARRIER, PLACEMENT_FLAGS);
        }
    }

    private static void placeArenaBriefingWall(ServerLevel level, int centerX, int z, int floorY,
                                               BlockPos.MutableBlockPos pos) {
        int minX = centerX - 5;
        int maxX = centerX + 5;
        placeScreenPanelZ(level, minX, maxX, floorY + 2, floorY + 4, z,
            SCREEN_FRAME, SCREEN_ALT, SCREEN_FRAME, 1, pos);
        pos.set(centerX, floorY + 1, z + 1);
        level.setBlock(pos, nn(Blocks.LECTERN.defaultBlockState(), "LECTERN"), PLACEMENT_FLAGS);
        pos.set(centerX - 2, floorY + 1, z + 1);
        level.setBlock(pos, nn(Blocks.BARREL.defaultBlockState(), "BARREL"), PLACEMENT_FLAGS);
        pos.set(centerX + 2, floorY + 1, z + 1);
        level.setBlock(pos, nn(Blocks.CHEST.defaultBlockState(), "CHEST"), PLACEMENT_FLAGS);
    }

    private static void decorateShowcaseRoom(ServerLevel level, int minX, int minZ, int maxX, int maxZ,
                                             int floorY, BlockPos.MutableBlockPos pos) {
        decorateRoomFloor(level, minX, minZ, maxX, maxZ, floorY, PAD_YELLOW, pos);
        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;
        int spacing = 8;
        for (int x = centerX - spacing; x <= centerX + spacing; x += spacing) {
            for (int z = centerZ - spacing; z <= centerZ + spacing; z += spacing) {
                placeShowcasePlinth(level, x, z, floorY, pos);
            }
        }

        placeShowcaseCase(level, centerX - 6, minZ + 4, floorY, pos);
        placeShowcaseCase(level, centerX + 6, minZ + 4, floorY, pos);
        placeUtilityBenchRow(level, minX + 2, maxZ - 1, maxX - 2, floorY, pos);
        placePad(level, centerX, centerZ, floorY, PAD_YELLOW, LIGHT, pos);
    }

    private static void decorateIntegrationRoom(ServerLevel level, int minX, int minZ, int maxX, int maxZ,
                                                int floorY, BlockPos.MutableBlockPos pos) {
        decorateRoomFloor(level, minX, minZ, maxX, maxZ, floorY, PAD_PURPLE, pos);
        int centerX = (minX + maxX) / 2;
        int rowZ = minZ + 4;
        BlockState[] stations = {
            Blocks.SMITHING_TABLE.defaultBlockState(),
            Blocks.ANVIL.defaultBlockState(),
            Blocks.ENCHANTING_TABLE.defaultBlockState(),
            Blocks.GRINDSTONE.defaultBlockState(),
            Blocks.CRAFTING_TABLE.defaultBlockState()
        };
        int startX = centerX - ((stations.length - 1) * 3) / 2;
        int x = startX;
        for (BlockState state : stations) {
            pos.set(x, floorY, rowZ);
            level.setBlock(pos, state, PLACEMENT_FLAGS);
            x += 3;
        }
        placeUtilityBenchRow(level, minX + 2, maxZ - 1, maxX - 2, floorY, pos);
        placePad(level, centerX, (minZ + maxZ) / 2, floorY, PAD_PURPLE, LIGHT, pos);
    }

    private static void decorateSandboxRoom(ServerLevel level, int minX, int minZ, int maxX, int maxZ,
                                            int floorY, BlockPos.MutableBlockPos pos) {
        decorateRoomFloor(level, minX, minZ, maxX, maxZ, floorY, GRID, pos);
        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;
        placePad(level, centerX, centerZ, floorY, ACCENT, LIGHT, pos);
        placeMaterialBayRow(level, minX + 2, minZ + 2, maxX - 2, floorY, pos);
    }

    private static void decorateMechanicsRoom(ServerLevel level, int minX, int minZ, int maxX, int maxZ,
                                              int floorY, BlockPos.MutableBlockPos pos) {
        decorateRoomFloor(level, minX, minZ, maxX, maxZ, floorY, ACCENT, pos);
        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;
        placeMechanicsBenchRow(level, minX + 2, minZ + 2, maxX - 2, floorY, pos);
        placePad(level, centerX, centerZ, floorY, ACCENT, LIGHT, pos);
    }

    private static void decorateRoomFloor(ServerLevel level, int minX, int minZ, int maxX, int maxZ,
                                          int floorY, @Nonnull BlockState accent, BlockPos.MutableBlockPos pos) {
        fillRect(level, minX, minZ, maxX, maxZ, floorY, FLOOR, pos);
        drawRectOutline(level, minX, minZ, maxX, maxZ, floorY, GRID, pos);
        int band = Math.max(2, Math.min(3, (maxZ - minZ) / 6));
        fillRect(level, minX + 1, minZ + 1, maxX - 1, minZ + band, floorY, accent, pos);
    }

    private static void placeShowcasePlinth(ServerLevel level, int x, int z, int floorY,
                                            BlockPos.MutableBlockPos pos) {
        fillRect(level, x - 1, z - 1, x + 1, z + 1, floorY, PAD_YELLOW, pos);
        fillRect(level, x - 1, z - 1, x + 1, z + 1, floorY + 1, TRIM, pos);
        pos.set(x, floorY + 2, z);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
    }

    private static void placeShowcaseCase(ServerLevel level, int x, int z, int floorY,
                                          BlockPos.MutableBlockPos pos) {
        fillRect(level, x - 1, z - 1, x + 1, z + 1, floorY, PAD_YELLOW, pos);
        fillRect(level, x - 1, z - 1, x + 1, z + 1, floorY + 1, TRIM, pos);
        fillVolume(level, x - 1, floorY + 2, z - 1, x + 1, floorY + 3, z + 1,
            nn(Blocks.GLASS.defaultBlockState(), "GLASS"), pos);
        pos.set(x, floorY + 4, z);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
    }

    private static void placeShowcaseCaseXL(ServerLevel level, int x, int z, int floorY,
                                            BlockPos.MutableBlockPos pos) {
        fillRect(level, x - 2, z - 2, x + 2, z + 2, floorY, PAD_YELLOW, pos);
        drawRectOutline(level, x - 2, z - 2, x + 2, z + 2, floorY + 1, TRIM, pos);
        fillVolume(level, x - 2, floorY + 2, z - 2, x + 2, floorY + 4, z + 2,
            nn(Blocks.GLASS.defaultBlockState(), "GLASS"), pos);
        pos.set(x, floorY + 5, z);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
    }

    private static void placeShowcaseTestPad(ServerLevel level, int x, int z, int floorY,
                                             BlockPos.MutableBlockPos pos) {
        fillRect(level, x - 1, z - 1, x + 1, z + 1, floorY, PAD_GREEN, pos);
        pos.set(x, floorY + 1, z);
        level.setBlock(pos, COMBAT_TARGET, PLACEMENT_FLAGS);
    }

    private static void placePhotoBay(ServerLevel level, int startX, int startZ, int floorY,
                                      BlockPos.MutableBlockPos pos) {
        int width = 5;
        int depth = 5;
        int endX = startX + width - 1;
        int endZ = startZ + depth - 1;
        fillRect(level, startX, startZ, endX, endZ, floorY, nn(Blocks.SMOOTH_QUARTZ.defaultBlockState(), "SMOOTH_QUARTZ"), pos);
        fillVolume(level, endX, floorY + 1, startZ, endX, floorY + 4, endZ,
            nn(Blocks.QUARTZ_BLOCK.defaultBlockState(), "QUARTZ_BLOCK"), pos);
        pos.set(startX + 2, floorY + 4, startZ + 2);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
    }

    private static void placeUtilityBenchRow(ServerLevel level, int startX, int z, int endX,
                                             int floorY, BlockPos.MutableBlockPos pos) {
        BlockState[] benches = {
            Blocks.CRAFTING_TABLE.defaultBlockState(),
            Blocks.SMITHING_TABLE.defaultBlockState(),
            Blocks.ANVIL.defaultBlockState(),
            Blocks.GRINDSTONE.defaultBlockState(),
            Blocks.STONECUTTER.defaultBlockState(),
            Blocks.ENCHANTING_TABLE.defaultBlockState(),
            Blocks.BREWING_STAND.defaultBlockState()
        };
        int x = startX;
        for (BlockState bench : benches) {
            if (x > endX) {
                break;
            }
            pos.set(x, floorY, z);
            level.setBlock(pos, nn(bench, "bench"), PLACEMENT_FLAGS);
            x += 2;
        }
    }

    private static void placeIntegrationPod(ServerLevel level, int centerX, int centerZ, int floorY,
                                            @Nonnull BlockState icon, boolean active, BlockPos.MutableBlockPos pos) {
        @Nonnull BlockState iconState = active ? icon : nn(Blocks.GRAY_CONCRETE.defaultBlockState(), "GRAY_CONCRETE");
        @Nonnull BlockState glassState = active ? CORE_GLASS : WINDOW_DARK;
        @Nonnull BlockState lightState = active ? LIGHT : nn(Blocks.REDSTONE_LAMP.defaultBlockState(), "REDSTONE_LAMP");

        fillRect(level, centerX - 2, centerZ - 2, centerX + 2, centerZ + 2, floorY, METAL, pos);
        fillRect(level, centerX - 1, centerZ - 1, centerX + 1, centerZ + 1, floorY + 1, glassState, pos);
        pos.set(centerX, floorY + 2, centerZ);
        level.setBlock(pos, iconState, PLACEMENT_FLAGS);
        pos.set(centerX, floorY + 3, centerZ);
        level.setBlock(pos, lightState, PLACEMENT_FLAGS);
    }

    private static List<IntegrationPodSpec> buildIntegrationPods() {
        List<IntegrationPodSpec> pods = new ArrayList<>();
        boolean hideMissing = Config.NEXUS_HIDE_MISSING_MOD_PODS.get();
        Set<String> seen = new HashSet<>();

        List<IntegrationPodSpec> base = List.of(
            new IntegrationPodSpec(Compat.Mods.EPIC_FIGHT, Blocks.ANVIL.defaultBlockState()),
            new IntegrationPodSpec(Compat.Mods.IRONS_SPELLBOOKS, Blocks.ENCHANTING_TABLE.defaultBlockState()),
            new IntegrationPodSpec(Compat.Mods.SPELL_ENGINE, Blocks.BEACON.defaultBlockState()),
            new IntegrationPodSpec(Compat.Mods.CURIOS, Blocks.CHEST.defaultBlockState()),
            new IntegrationPodSpec(Compat.Mods.ACCESSORIES, Blocks.ENDER_CHEST.defaultBlockState()),
            new IntegrationPodSpec(Compat.Mods.RANGED_WEAPON_API, Blocks.DISPENSER.defaultBlockState()),
            new IntegrationPodSpec(Compat.Mods.SPARK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
            new IntegrationPodSpec(Compat.Mods.DUMMMMMMY, Blocks.TARGET.defaultBlockState())
        );

        for (IntegrationPodSpec spec : base) {
            boolean active = Compat.isLoaded(spec.modId());
            seen.add(spec.modId());
            if (active || !hideMissing) {
                pods.add(new IntegrationPodSpec(spec.modId(), spec.icon(), active));
            }
        }

        if (Config.NEXUS_DYNAMIC_MOD_PODS.get()) {
            List<String> dynamicIds = new ArrayList<>(CompatRegistry.getActiveModIds());
            dynamicIds.sort(String::compareTo);
            for (String modId : dynamicIds) {
                if (seen.contains(modId)) {
                    continue;
                }
                pods.add(new IntegrationPodSpec(modId, Blocks.LODESTONE.defaultBlockState(), true));
            }
        }

        return pods;
    }

    private record IntegrationPodSpec(String modId, BlockState icon, boolean active) {
        private IntegrationPodSpec(String modId, BlockState icon) {
            this(modId, icon, false);
        }
    }

    private static void buildSandboxBoundary(ServerLevel level, int minX, int minZ, int maxX, int maxZ,
                                             int y, BlockPos.MutableBlockPos pos) {
        for (int x = minX; x <= maxX; x++) {
            pos.set(x, y, minZ);
            level.setBlock(pos, RAIL, PLACEMENT_FLAGS);
            pos.set(x, y, maxZ);
            level.setBlock(pos, RAIL, PLACEMENT_FLAGS);
        }
        for (int z = minZ; z <= maxZ; z++) {
            pos.set(minX, y, z);
            level.setBlock(pos, RAIL, PLACEMENT_FLAGS);
            pos.set(maxX, y, z);
            level.setBlock(pos, RAIL, PLACEMENT_FLAGS);
        }
    }

    private static void placeSandboxGrid(ServerLevel level, int minX, int minZ, int maxX, int maxZ,
                                         int floorY, BlockPos.MutableBlockPos pos) {
        for (int x = minX; x <= maxX; x += 4) {
            for (int z = minZ; z <= maxZ; z += 4) {
                pos.set(x, floorY, z);
                level.setBlock(pos, ACCENT, PLACEMENT_FLAGS);
            }
        }
    }

    private static void placePerformanceBay(ServerLevel level, int minX, int minZ, int maxX, int maxZ,
                                            int floorY, BlockPos.MutableBlockPos pos) {
        drawRectOutline(level, minX, minZ, maxX, maxZ, floorY, nn(Blocks.RED_CONCRETE.defaultBlockState(), "RED_CONCRETE"), pos);
        if (minX + 1 <= maxX - 1 && minZ + 1 <= maxZ - 1) {
            fillRect(level, minX + 1, minZ + 1, maxX - 1, maxZ - 1, floorY, nn(Blocks.GRAY_CONCRETE.defaultBlockState(), "GRAY_CONCRETE"), pos);
        }
        pos.set((minX + maxX) / 2, floorY, (minZ + maxZ) / 2);
        level.setBlock(pos, nn(Blocks.REDSTONE_BLOCK.defaultBlockState(), "REDSTONE_BLOCK"), PLACEMENT_FLAGS);
        pos.set(minX + 1, floorY + 1, minZ + 1);
        level.setBlock(pos, nn(Blocks.REPEATER.defaultBlockState(), "REPEATER"), PLACEMENT_FLAGS);
    }

    private static void placeMaterialBayRow(ServerLevel level, int startX, int z, int endX,
                                            int floorY, BlockPos.MutableBlockPos pos) {
        BlockState[] bays = {
            Blocks.CHEST.defaultBlockState(),
            Blocks.BARREL.defaultBlockState(),
            Blocks.CRAFTING_TABLE.defaultBlockState(),
            Blocks.STONECUTTER.defaultBlockState(),
            Blocks.SMITHING_TABLE.defaultBlockState(),
            Blocks.ANVIL.defaultBlockState(),
            Blocks.GRINDSTONE.defaultBlockState(),
            Blocks.FURNACE.defaultBlockState(),
            Blocks.BLAST_FURNACE.defaultBlockState(),
            Blocks.SMOKER.defaultBlockState(),
            Blocks.BREWING_STAND.defaultBlockState()
        };
        int x = startX;
        for (BlockState bay : bays) {
            if (x > endX) {
                break;
            }
            pos.set(x, floorY, z);
            level.setBlock(pos, nn(bay, "bay"), PLACEMENT_FLAGS);
            x += 2;
        }
    }

    private static void placeMechanicsRig(ServerLevel level, int x, int z, int floorY,
                                          BlockPos.MutableBlockPos pos) {
        fillRect(level, x - 1, z - 1, x + 1, z + 1, floorY, METAL, pos);
        pos.set(x, floorY + 1, z);
        level.setBlock(pos, nn(Blocks.STICKY_PISTON.defaultBlockState(), "STICKY_PISTON"), PLACEMENT_FLAGS);
        pos.set(x, floorY + 1, z + 1);
        level.setBlock(pos, nn(Blocks.SLIME_BLOCK.defaultBlockState(), "SLIME_BLOCK"), PLACEMENT_FLAGS);
        pos.set(x, floorY + 1, z - 1);
        level.setBlock(pos, nn(Blocks.OBSERVER.defaultBlockState(), "OBSERVER"), PLACEMENT_FLAGS);
    }

    private static void placeMechanicsBenchRow(ServerLevel level, int startX, int z, int endX,
                                               int floorY, BlockPos.MutableBlockPos pos) {
        BlockState[] benches = {
            Blocks.PISTON.defaultBlockState(),
            Blocks.STICKY_PISTON.defaultBlockState(),
            Blocks.OBSERVER.defaultBlockState(),
            Blocks.REPEATER.defaultBlockState(),
            Blocks.COMPARATOR.defaultBlockState(),
            Blocks.DISPENSER.defaultBlockState(),
            Blocks.DROPPER.defaultBlockState(),
            Blocks.REDSTONE_LAMP.defaultBlockState(),
            Blocks.NOTE_BLOCK.defaultBlockState(),
            Blocks.LEVER.defaultBlockState(),
            Blocks.STONE_BUTTON.defaultBlockState(),
            Blocks.OAK_BUTTON.defaultBlockState(),
            Blocks.HOPPER.defaultBlockState(),
            Blocks.REDSTONE_TORCH.defaultBlockState(),
            Blocks.DAYLIGHT_DETECTOR.defaultBlockState(),
            Blocks.TARGET.defaultBlockState(),
            Blocks.POWERED_RAIL.defaultBlockState()
        };
        int x = startX;
        for (BlockState bench : benches) {
            if (x > endX) {
                break;
            }
            pos.set(x, floorY, z);
            level.setBlock(pos, nn(bench, "bench"), PLACEMENT_FLAGS);
            x += 2;
        }
    }

    private static void placeEnergySpire(ServerLevel level, int x, int z, int floorY, BlockPos.MutableBlockPos pos) {
        fillColumn(level, x, z, floorY + 1, floorY + 6, METAL, pos);
        pos.set(x, floorY + 7, z);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
    }

    private static void placeTrussCross(ServerLevel level, int centerX, int centerZ, int y,
                                        int arm, BlockPos.MutableBlockPos pos) {
        for (int x = centerX - arm; x <= centerX + arm; x++) {
            pos.set(x, y, centerZ);
            level.setBlock(pos, TRUSS, PLACEMENT_FLAGS);
        }
        for (int z = centerZ - arm; z <= centerZ + arm; z++) {
            pos.set(centerX, y, z);
            level.setBlock(pos, TRUSS, PLACEMENT_FLAGS);
        }
    }

    private static void placeTargetWall(ServerLevel level, int minX, int maxX, int y, int z,
                                        BlockPos.MutableBlockPos pos) {
        for (int x = minX; x <= maxX; x += 4) {
            fillColumn(level, x, z, y - 1, y, COMBAT_POST, pos);
            pos.set(x, y + 1, z);
            level.setBlock(pos, COMBAT_TARGET, PLACEMENT_FLAGS);
        }
    }

    private static void placeServerRackRow(ServerLevel level, int minX, int maxX, int z,
                                           int floorY, BlockPos.MutableBlockPos pos) {
        for (int x = minX; x <= maxX; x += 4) {
            if (x + 1 > maxX) {
                break;
            }
            placeServerRack(level, x, z, floorY, pos);
        }
    }

    private static void placeServerRack(ServerLevel level, int x, int z, int floorY, BlockPos.MutableBlockPos pos) {
        fillRect(level, x, z, x + 1, z, floorY, SCREEN_FRAME, pos);
        fillVolume(level, x, floorY + 1, z, x + 1, floorY + 4, z, TELEMETRY_GLASS, pos);
        fillRect(level, x, z, x + 1, z, floorY + 5, LIGHT, pos);
    }

    private static void placeTrainingPost(ServerLevel level, int x, int z, int floorY, BlockPos.MutableBlockPos pos) {
        fillColumn(level, x, z, floorY + 1, floorY + 3, COMBAT_POST, pos);
        pos.set(x, floorY + 4, z);
        level.setBlock(pos, COMBAT_TARGET, PLACEMENT_FLAGS);
    }

    private static void placeDataTower(ServerLevel level, int x, int z, int floorY, BlockPos.MutableBlockPos pos) {
        fillRect(level, x, z, x + 1, z + 1, floorY, SCREEN_FRAME, pos);
        fillVolume(level, x, floorY + 1, z, x + 1, floorY + 4, z + 1, TELEMETRY_GLASS, pos);
        fillRect(level, x, z, x + 1, z + 1, floorY + 5, LIGHT, pos);
    }

    private static void placeTelemetryHeatmap(ServerLevel level, int centerX, int centerZ, int floorY,
                                              BlockPos.MutableBlockPos pos) {
        BlockState[] gradient = {
            Blocks.GREEN_CONCRETE.defaultBlockState(),
            Blocks.LIME_CONCRETE.defaultBlockState(),
            Blocks.YELLOW_CONCRETE.defaultBlockState(),
            Blocks.ORANGE_CONCRETE.defaultBlockState(),
            Blocks.RED_CONCRETE.defaultBlockState()
        };
        int radius = 3;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int distance = Math.max(Math.abs(dx), Math.abs(dz));
                int index = Math.min(distance, gradient.length - 1);
                pos.set(centerX + dx, floorY, centerZ + dz);
                level.setBlock(pos, nn(gradient[index], "gradient"), PLACEMENT_FLAGS);
            }
        }
    }

    private static void placeTelemetryDashboard(ServerLevel level, int wallX, int centerZ, int floorY,
                                                BlockPos.MutableBlockPos pos) {
        int minZ = centerZ - 4;
        int maxZ = centerZ + 4;
        placeScreenPanelX(level, wallX, floorY + 2, floorY + 5, minZ, maxZ, SCREEN_FRAME, SCREEN, pos);
        pos.set(wallX - 1, floorY + 1, centerZ);
        level.setBlock(pos, nn(Blocks.LECTERN.defaultBlockState(), "LECTERN"), PLACEMENT_FLAGS);
    }

    private static void placeLogDesk(ServerLevel level, int startX, int startZ, int floorY,
                                     BlockPos.MutableBlockPos pos) {
        fillRect(level, startX, startZ, startX + 2, startZ + 1, floorY, CONSOLE, pos);
        fillRect(level, startX, startZ, startX + 2, startZ + 1, floorY + 1, CONSOLE_TOP, pos);
        pos.set(startX + 1, floorY + 1, startZ);
        level.setBlock(pos, nn(Blocks.LECTERN.defaultBlockState(), "LECTERN"), PLACEMENT_FLAGS);
        pos.set(startX + 1, floorY + 1, startZ + 1);
        level.setBlock(pos, nn(Blocks.BOOKSHELF.defaultBlockState(), "BOOKSHELF"), PLACEMENT_FLAGS);
    }

    private static void placeQuietBay(ServerLevel level, int startX, int startZ, int floorY,
                                      BlockPos.MutableBlockPos pos) {
        int width = 5;
        int depth = 5;
        int endX = startX + width - 1;
        int endZ = startZ + depth - 1;
        @Nonnull BlockState wall = nn(Blocks.BLACK_CONCRETE.defaultBlockState(), "BLACK_CONCRETE");
        @Nonnull BlockState floor = nn(Blocks.GRAY_WOOL.defaultBlockState(), "GRAY_WOOL");

        fillRect(level, startX, startZ, endX, endZ, floorY, floor, pos);
        for (int x = startX; x <= endX; x++) {
            fillColumn(level, x, startZ, floorY + 1, floorY + 3, wall, pos);
            fillColumn(level, x, endZ, floorY + 1, floorY + 3, wall, pos);
        }
        for (int z = startZ; z <= endZ; z++) {
            fillColumn(level, startX, z, floorY + 1, floorY + 3, wall, pos);
            fillColumn(level, endX, z, floorY + 1, floorY + 3, wall, pos);
        }
        fillVolume(level, startX + 2, floorY + 1, endZ, startX + 2, floorY + 2, endZ, AIR, pos);
        fillRect(level, startX, startZ, endX, endZ, floorY + 4, wall, pos);
        pos.set(startX + 2, floorY + 1, startZ + 1);
        level.setBlock(pos, nn(Blocks.SOUL_LANTERN.defaultBlockState(), "SOUL_LANTERN"), PLACEMENT_FLAGS);
        pos.set(startX + 2, floorY + 1, endZ - 1);
        level.setBlock(pos, nn(Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), "SMOOTH_STONE_SLAB"), PLACEMENT_FLAGS);
    }

    private static void placeConsoleTableNS(ServerLevel level, int startX, int startZ, int floorY,
                                            boolean screenNorth, BlockPos.MutableBlockPos pos) {
        fillRect(level, startX, startZ, startX + 2, startZ + 1, floorY, CONSOLE, pos);
        fillRect(level, startX, startZ, startX + 2, startZ + 1, floorY + 1, CONSOLE_TOP, pos);
        pos.set(startX + 1, floorY + 1, startZ);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
        int screenZ = screenNorth ? startZ - 1 : startZ + 2;
        placeScreenPanelZ(level, startX, startX + 2, floorY + 2, floorY + 3, screenZ,
            SCREEN_FRAME, SCREEN, pos);
    }

    private static void placeConsoleTableEW(ServerLevel level, int startX, int startZ, int floorY,
                                            boolean screenWest, BlockPos.MutableBlockPos pos) {
        fillRect(level, startX, startZ, startX + 1, startZ + 2, floorY, CONSOLE, pos);
        fillRect(level, startX, startZ, startX + 1, startZ + 2, floorY + 1, CONSOLE_TOP, pos);
        pos.set(startX, floorY + 1, startZ + 1);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
        int screenX = screenWest ? startX - 1 : startX + 2;
        placeScreenPanelX(level, screenX, floorY + 2, floorY + 3, startZ, startZ + 2,
            SCREEN_FRAME, SCREEN, pos);
    }

    private static void placeScreenPanelZ(ServerLevel level, int minX, int maxX, int minY, int maxY, int z,
                                          @Nonnull BlockState frame, @Nonnull BlockState screen, BlockPos.MutableBlockPos pos) {
        placeScreenPanelZ(level, minX, maxX, minY, maxY, z, frame, screen, AIR, 0, pos);
    }

    private static void placeScreenPanelZ(ServerLevel level, int minX, int maxX, int minY, int maxY, int z,
                                          @Nonnull BlockState frame, @Nonnull BlockState screen,
                                          @Nonnull BlockState backing, int backOffset,
                                          BlockPos.MutableBlockPos pos) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                @Nonnull BlockState state = nn((x == minX || x == maxX || y == minY || y == maxY) ? frame : screen, "screenState");
                pos.set(x, y, z);
                level.setBlock(pos, state, PLACEMENT_FLAGS);
                if (backOffset != 0 && backing != AIR) {
                    pos.set(x, y, z + backOffset);
                    level.setBlock(pos, backing, PLACEMENT_FLAGS);
                }
            }
        }
    }

    private static void placeScreenPanelX(ServerLevel level, int x, int minY, int maxY, int minZ, int maxZ,
                                          @Nonnull BlockState frame, @Nonnull BlockState screen, BlockPos.MutableBlockPos pos) {
        placeScreenPanelX(level, x, minY, maxY, minZ, maxZ, frame, screen, AIR, 0, pos);
    }

    private static void placeScreenPanelX(ServerLevel level, int x, int minY, int maxY, int minZ, int maxZ,
                                          @Nonnull BlockState frame, @Nonnull BlockState screen,
                                          @Nonnull BlockState backing, int backOffset,
                                          BlockPos.MutableBlockPos pos) {
        for (int z = minZ; z <= maxZ; z++) {
            for (int y = minY; y <= maxY; y++) {
                @Nonnull BlockState state = nn((z == minZ || z == maxZ || y == minY || y == maxY) ? frame : screen, "screenState");
                pos.set(x, y, z);
                level.setBlock(pos, state, PLACEMENT_FLAGS);
                if (backOffset != 0 && backing != AIR) {
                    pos.set(x + backOffset, y, z);
                    level.setBlock(pos, backing, PLACEMENT_FLAGS);
                }
            }
        }
    }

    private static void placeCeilingLight(ServerLevel level, int x, int z, int floorY, BlockPos.MutableBlockPos pos) {
        pos.set(x, floorY + WALL_HEIGHT + 1, z);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
    }

    private static void placeCornerPillar(ServerLevel level, int x, int z, int yStart, int yEnd,
                                          BlockPos.MutableBlockPos pos) {
        fillColumn(level, x, z, yStart, yEnd, TRIM, pos);
        pos.set(x, yEnd + 1, z);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
    }

    private static void drawAxisLineX(ServerLevel level, int minX, int maxX, int z, int y,
                                      @Nonnull BlockState state, BlockPos.MutableBlockPos pos) {
        for (int x = minX; x <= maxX; x++) {
            pos.set(x, y, z);
            level.setBlock(pos, state, PLACEMENT_FLAGS);
        }
    }

    private static void drawAxisLineZ(ServerLevel level, int minZ, int maxZ, int x, int y,
                                      @Nonnull BlockState state, BlockPos.MutableBlockPos pos) {
        for (int z = minZ; z <= maxZ; z++) {
            pos.set(x, y, z);
            level.setBlock(pos, state, PLACEMENT_FLAGS);
        }
    }

    private static void placeLightLineX(ServerLevel level, int minX, int maxX, int z, int y,
                                        int step, BlockPos.MutableBlockPos pos) {
        for (int x = minX; x <= maxX; x += step) {
            pos.set(x, y, z);
            level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
        }
    }

    private static void placeLightLineZ(ServerLevel level, int minZ, int maxZ, int x, int y,
                                        int step, BlockPos.MutableBlockPos pos) {
        for (int z = minZ; z <= maxZ; z += step) {
            pos.set(x, y, z);
            level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
        }
    }

    private static void fillRect(ServerLevel level, int x1, int z1, int x2, int z2,
                                 int y, @Nonnull BlockState state, BlockPos.MutableBlockPos pos) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                pos.set(x, y, z);
                level.setBlock(pos, state, PLACEMENT_FLAGS);
            }
        }
    }

    private static void drawRectOutline(ServerLevel level, int x1, int z1, int x2, int z2,
                                        int y, @Nonnull BlockState state, BlockPos.MutableBlockPos pos) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);
        for (int x = minX; x <= maxX; x++) {
            pos.set(x, y, minZ);
            level.setBlock(pos, state, PLACEMENT_FLAGS);
            pos.set(x, y, maxZ);
            level.setBlock(pos, state, PLACEMENT_FLAGS);
        }
        for (int z = minZ; z <= maxZ; z++) {
            pos.set(minX, y, z);
            level.setBlock(pos, state, PLACEMENT_FLAGS);
            pos.set(maxX, y, z);
            level.setBlock(pos, state, PLACEMENT_FLAGS);
        }
    }

    private static void buildWalls(ServerLevel level, int x1, int z1, int x2, int z2,
                                   int yStart, int yEnd, @Nonnull BlockState state, BlockPos.MutableBlockPos pos) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);
        for (int x = minX; x <= maxX; x++) {
            fillColumn(level, x, minZ, yStart, yEnd, state, pos);
            fillColumn(level, x, maxZ, yStart, yEnd, state, pos);
        }
        for (int z = minZ; z <= maxZ; z++) {
            fillColumn(level, minX, z, yStart, yEnd, state, pos);
            fillColumn(level, maxX, z, yStart, yEnd, state, pos);
        }
    }

    private static void fillColumn(ServerLevel level, int x, int z, int yStart, int yEnd,
                                   @Nonnull BlockState state, BlockPos.MutableBlockPos pos) {
        int minY = Math.min(yStart, yEnd);
        int maxY = Math.max(yStart, yEnd);
        for (int y = minY; y <= maxY; y++) {
            pos.set(x, y, z);
            level.setBlock(pos, state, PLACEMENT_FLAGS);
        }
    }

    private static void clearRoomDoors(ServerLevel level, int minX, int maxX, int minZ, int maxZ,
                                       int floorY, BlockPos.MutableBlockPos pos,
                                       boolean doorEast, boolean doorSouth,
                                       boolean doorWest, boolean doorNorth) {
        int doorYMin = floorY + 1;
        int doorYMax = floorY + DOOR_HEIGHT;
        int doorMinX = doorMin(minX, maxX);
        int doorMaxX = doorMax(minX, maxX);
        int doorMinZ = doorMin(minZ, maxZ);
        int doorMaxZ = doorMax(minZ, maxZ);

        if (doorEast) {
            fillVolume(level, maxX, doorYMin, doorMinZ, maxX, doorYMax, doorMaxZ, AIR, pos);
        }
        if (doorWest) {
            fillVolume(level, minX, doorYMin, doorMinZ, minX, doorYMax, doorMaxZ, AIR, pos);
        }
        if (doorSouth) {
            fillVolume(level, doorMinX, doorYMin, maxZ, doorMaxX, doorYMax, maxZ, AIR, pos);
        }
        if (doorNorth) {
            fillVolume(level, doorMinX, doorYMin, minZ, doorMaxX, doorYMax, minZ, AIR, pos);
        }
    }

    private static int doorMin(int min, int max) {
        int center = (min + max) / 2;
        return center - (DOOR_WIDTH / 2);
    }

    private static int doorMax(int min, int max) {
        return doorMin(min, max) + DOOR_WIDTH - 1;
    }

    private static int doorCenter(int min, int max) {
        int minDoor = doorMin(min, max);
        int maxDoor = doorMax(min, max);
        return (minDoor + maxDoor) / 2;
    }

    private static void fillVolume(ServerLevel level,
                                   int x1, int y1, int z1,
                                   int x2, int y2, int z2,
                                   @Nonnull BlockState state,
                                   BlockPos.MutableBlockPos pos) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);
                    level.setBlock(pos, state, PLACEMENT_FLAGS);
                }
            }
        }
    }

    private static void buildRing(ServerLevel level, int centerX, int centerZ, int y,
                                  int outerRadius, int innerRadius, @Nonnull BlockState state,
                                  BlockPos.MutableBlockPos pos) {
        int outerSq = outerRadius * outerRadius;
        int innerSq = innerRadius * innerRadius;
        for (int dx = -outerRadius; dx <= outerRadius; dx++) {
            for (int dz = -outerRadius; dz <= outerRadius; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq <= outerSq && distSq >= innerSq) {
                    pos.set(centerX + dx, y, centerZ + dz);
                    level.setBlock(pos, state, PLACEMENT_FLAGS);
                }
            }
        }
    }

    private static void placePad(ServerLevel level, int centerX, int centerZ, int floorY,
                                 @Nonnull BlockState padState, @Nonnull BlockState lightState, BlockPos.MutableBlockPos pos) {
        int startX = centerX - 1;
        int endX = centerX + 1;
        int startZ = centerZ - 1;
        int endZ = centerZ + 1;
        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                pos.set(x, floorY, z);
                level.setBlock(pos, padState, PLACEMENT_FLAGS);
            }
        }
        pos.set(centerX, floorY, centerZ);
        level.setBlock(pos, lightState, PLACEMENT_FLAGS);
    }

    // ===================== PORTAL PEDESTALS =====================

    /**
     * Place spectacular portal structures around the hub center.
     * Each zone has a unique themed portal design.
     */
    private static void placePortalPedestals(ServerLevel level, int centerX, int centerZ, int floorY,
                                              BlockPos.MutableBlockPos pos) {
        int radius = 12;

        // Combat Portal (NW) - Fiery war gate with nether bricks and chains
        placeCombatPortal(level, centerX - radius, centerZ - radius, floorY, pos);

        // Arena Portal (W) - Gladiator colosseum arch
        placeArenaPortal(level, centerX - radius, centerZ, floorY, pos);

        // UI Portal (NE) - Holographic tech gateway with blue glass
        placeUiPortal(level, centerX + radius, centerZ - radius, floorY, pos);

        // Telemetry Portal (E) - Data stream matrix with green circuits
        placeTelemetryPortal(level, centerX + radius, centerZ, floorY, pos);

        // Showcase Portal (SW) - Golden exhibition archway
        placeShowcasePortal(level, centerX - radius, centerZ + radius, floorY, pos);

        // Integration Portal (S) - Mystical enchantment circle
        placeIntegrationPortal(level, centerX, centerZ + radius, floorY, pos);

        // Sandbox Portal (SE-ish) - Creative builder's frame
        placeSandboxPortal(level, centerX + radius / 2, centerZ + radius, floorY, pos);

        // Mechanics Portal (SE) - Industrial engineering gateway
        placeMechanicsPortal(level, centerX + radius, centerZ + radius, floorY, pos);
    }

    /**
     * Combat Portal - Fiery war gate with aggressive red/black design
     */
    private static void placeCombatPortal(ServerLevel level, int x, int z, int floorY,
                                           BlockPos.MutableBlockPos pos) {
        BlockState base = COMBAT_RING;
        BlockState accent = COMBAT_RING_INNER;
        BlockState pillar = COMBAT_POST;
        @Nonnull BlockState glow = nn(Blocks.SHROOMLIGHT.defaultBlockState(), "SHROOMLIGHT");
        @Nonnull BlockState chain = nn(Blocks.CHAIN.defaultBlockState(), "CHAIN");

        // Circular base platform with inner ring
        buildRing(level, x, z, floorY, 3, 0, base, pos);
        buildRing(level, x, z, floorY, 2, 1, accent, pos);
        pos.set(x, floorY, z);
        level.setBlock(pos, glow, PLACEMENT_FLAGS);

        // Four imposing pillars with chains
        int[][] pillarPos = {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}};
        for (int[] p : pillarPos) {
            fillColumn(level, x + p[0], z + p[1], floorY + 1, floorY + 5, pillar, pos);
            pos.set(x + p[0], z + p[1], floorY + 6);
            level.setBlock(pos, accent, PLACEMENT_FLAGS);
            pos.set(x + p[0], z + p[1], floorY + 7);
            level.setBlock(pos, glow, PLACEMENT_FLAGS);
            // Hanging chains
            for (int dy = 3; dy <= 5; dy++) {
                if (p[0] < 0) {
                    pos.set(x + p[0] + 1, floorY + dy, z + p[1]);
                    level.setBlock(pos, chain, PLACEMENT_FLAGS);
                }
            }
        }

        // Connecting war beams with spikes
        for (int dx = -1; dx <= 1; dx++) {
            pos.set(x + dx, floorY + 6, z - 2);
            level.setBlock(pos, accent, PLACEMENT_FLAGS);
            pos.set(x + dx, floorY + 6, z + 2);
            level.setBlock(pos, accent, PLACEMENT_FLAGS);
        }
        for (int dz = -1; dz <= 1; dz++) {
            pos.set(x - 2, floorY + 6, z + dz);
            level.setBlock(pos, accent, PLACEMENT_FLAGS);
            pos.set(x + 2, floorY + 6, z + dz);
            level.setBlock(pos, accent, PLACEMENT_FLAGS);
        }

        // Crown with flames effect (soul lanterns)
        pos.set(x, floorY + 7, z);
        level.setBlock(pos, nn(Blocks.SOUL_LANTERN.defaultBlockState(), "SOUL_LANTERN"), PLACEMENT_FLAGS);
    }

    /**
     * Arena Portal - Gladiator colosseum style with dramatic arch
     */
    private static void placeArenaPortal(ServerLevel level, int x, int z, int floorY,
                                          BlockPos.MutableBlockPos pos) {
        @Nonnull BlockState base = ARENA_RING;
        @Nonnull BlockState inner = ARENA_RING_INNER;
        @Nonnull BlockState pillar = nn(Blocks.POLISHED_BASALT.defaultBlockState(), "POLISHED_BASALT");

        // Octagonal base
        fillRect(level, x - 2, z - 1, x + 2, z + 1, floorY, base, pos);
        fillRect(level, x - 1, z - 2, x + 1, z + 2, floorY, base, pos);
        buildRing(level, x, z, floorY, 2, 1, inner, pos);
        pos.set(x, floorY, z);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);

        // Twin pillars forming arch
        fillColumn(level, x - 2, z, floorY + 1, floorY + 6, pillar, pos);
        fillColumn(level, x + 2, z, floorY + 1, floorY + 6, pillar, pos);

        // Decorative brackets
        pos.set(x - 2, floorY + 4, z - 1);
        level.setBlock(pos, inner, PLACEMENT_FLAGS);
        pos.set(x - 2, floorY + 4, z + 1);
        level.setBlock(pos, inner, PLACEMENT_FLAGS);
        pos.set(x + 2, floorY + 4, z - 1);
        level.setBlock(pos, inner, PLACEMENT_FLAGS);
        pos.set(x + 2, floorY + 4, z + 1);
        level.setBlock(pos, inner, PLACEMENT_FLAGS);

        // Arch top
        for (int dx = -2; dx <= 2; dx++) {
            pos.set(x + dx, floorY + 7, z);
            level.setBlock(pos, base, PLACEMENT_FLAGS);
        }
        pos.set(x, floorY + 8, z);
        level.setBlock(pos, nn(Blocks.ORANGE_CONCRETE.defaultBlockState(), "ORANGE_CONCRETE"), PLACEMENT_FLAGS);
        pos.set(x, floorY + 9, z);
        level.setBlock(pos, LIGHT_WARM, PLACEMENT_FLAGS);
    }

    /**
     * UI Portal - Futuristic holographic tech gateway
     */
    private static void placeUiPortal(ServerLevel level, int x, int z, int floorY,
                                       BlockPos.MutableBlockPos pos) {
        @Nonnull BlockState frame = SCREEN_FRAME;
        @Nonnull BlockState glass = nn(Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(), "LIGHT_BLUE_STAINED_GLASS");
        @Nonnull BlockState pane = nn(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE.defaultBlockState(), "LIGHT_BLUE_STAINED_GLASS_PANE");
        @Nonnull BlockState light = nn(Blocks.SEA_LANTERN.defaultBlockState(), "SEA_LANTERN");
        BlockState accent = UI_ACCENT;

        // Tech platform base
        fillRect(level, x - 2, z - 2, x + 2, z + 2, floorY, frame, pos);
        buildRing(level, x, z, floorY, 2, 1, accent, pos);
        pos.set(x, floorY, z);
        level.setBlock(pos, light, PLACEMENT_FLAGS);

        // Holographic frame pillars
        int[][] corners = {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}};
        for (int[] c : corners) {
            fillColumn(level, x + c[0], z + c[1], floorY + 1, floorY + 4, frame, pos);
            pos.set(x + c[0], z + c[1], floorY + 5);
            level.setBlock(pos, glass, PLACEMENT_FLAGS);
            pos.set(x + c[0], z + c[1], floorY + 6);
            level.setBlock(pos, light, PLACEMENT_FLAGS);
        }

        // Floating glass panels (hologram effect)
        for (int dx = -1; dx <= 1; dx++) {
            pos.set(x + dx, floorY + 5, z - 2);
            level.setBlock(pos, pane, PLACEMENT_FLAGS);
            pos.set(x + dx, floorY + 5, z + 2);
            level.setBlock(pos, pane, PLACEMENT_FLAGS);
        }
        for (int dz = -1; dz <= 1; dz++) {
            pos.set(x - 2, floorY + 5, z + dz);
            level.setBlock(pos, pane, PLACEMENT_FLAGS);
            pos.set(x + 2, floorY + 5, z + dz);
            level.setBlock(pos, pane, PLACEMENT_FLAGS);
        }

        // Central data beacon
        pos.set(x, floorY + 1, z);
        level.setBlock(pos, glass, PLACEMENT_FLAGS);
        pos.set(x, floorY + 2, z);
        level.setBlock(pos, light, PLACEMENT_FLAGS);
    }

    /**
     * Telemetry Portal - Data matrix with green circuit patterns
     */
    private static void placeTelemetryPortal(ServerLevel level, int x, int z, int floorY,
                                              BlockPos.MutableBlockPos pos) {
        @Nonnull BlockState circuit = TELEMETRY_ACCENT;
        @Nonnull BlockState glass = TELEMETRY_GLASS;
        @Nonnull BlockState frame = SCREEN_FRAME;
        @Nonnull BlockState data = DATA_LINE;

        // Circuit board base with data lines
        fillRect(level, x - 2, z - 2, x + 2, z + 2, floorY, frame, pos);
        pos.set(x, floorY, z);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
        // Data lines in cross pattern
        for (int d = -2; d <= 2; d++) {
            if (d != 0) {
                pos.set(x + d, floorY, z);
                level.setBlock(pos, data, PLACEMENT_FLAGS);
                pos.set(x, floorY, z + d);
                level.setBlock(pos, data, PLACEMENT_FLAGS);
            }
        }

        // Server rack style pillars
        int[][] racks = {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}};
        for (int[] r : racks) {
            for (int dy = 1; dy <= 5; dy++) {
                pos.set(x + r[0], floorY + dy, z + r[1]);
                level.setBlock(pos, nn(dy % 2 == 0 ? glass : frame, "rackBlock"), PLACEMENT_FLAGS);
            }
            pos.set(x + r[0], floorY + 6, z + r[1]);
            level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
        }

        // Connecting data beams at top
        for (int dx = -1; dx <= 1; dx++) {
            pos.set(x + dx, floorY + 5, z - 2);
            level.setBlock(pos, circuit, PLACEMENT_FLAGS);
            pos.set(x + dx, floorY + 5, z + 2);
            level.setBlock(pos, circuit, PLACEMENT_FLAGS);
        }
        for (int dz = -1; dz <= 1; dz++) {
            pos.set(x - 2, floorY + 5, z + dz);
            level.setBlock(pos, circuit, PLACEMENT_FLAGS);
            pos.set(x + 2, floorY + 5, z + dz);
            level.setBlock(pos, circuit, PLACEMENT_FLAGS);
        }

        // Floating data core
        pos.set(x, floorY + 4, z);
        level.setBlock(pos, glass, PLACEMENT_FLAGS);
    }

    /**
     * Showcase Portal - Golden exhibition archway
     */
    private static void placeShowcasePortal(ServerLevel level, int x, int z, int floorY,
                                             BlockPos.MutableBlockPos pos) {
        @Nonnull BlockState gold = nn(Blocks.GOLD_BLOCK.defaultBlockState(), "GOLD_BLOCK");
        @Nonnull BlockState goldLight = nn(Blocks.RAW_GOLD_BLOCK.defaultBlockState(), "RAW_GOLD_BLOCK");
        @Nonnull BlockState quartz = TRIM;
        @Nonnull BlockState glass = nn(Blocks.YELLOW_STAINED_GLASS.defaultBlockState(), "YELLOW_STAINED_GLASS");
        @Nonnull BlockState yellowConcrete = PAD_YELLOW;

        // Luxurious base
        buildRing(level, x, z, floorY, 3, 0, quartz, pos);
        buildRing(level, x, z, floorY, 2, 1, yellowConcrete, pos);
        pos.set(x, floorY, z);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);

        // Golden pillars with quartz trim
        int[][] pillars = {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}};
        for (int[] p : pillars) {
            pos.set(x + p[0], floorY + 1, z + p[1]);
            level.setBlock(pos, quartz, PLACEMENT_FLAGS);
            fillColumn(level, x + p[0], z + p[1], floorY + 2, floorY + 5, gold, pos);
            pos.set(x + p[0], floorY + 6, z + p[1]);
            level.setBlock(pos, quartz, PLACEMENT_FLAGS);
            pos.set(x + p[0], floorY + 7, z + p[1]);
            level.setBlock(pos, goldLight, PLACEMENT_FLAGS);
        }

        // Display case glass walls
        for (int dz = -1; dz <= 1; dz++) {
            pos.set(x - 2, floorY + 3, z + dz);
            level.setBlock(pos, glass, PLACEMENT_FLAGS);
            pos.set(x + 2, floorY + 3, z + dz);
            level.setBlock(pos, glass, PLACEMENT_FLAGS);
        }

        // Crown with item frame spot
        pos.set(x, floorY + 6, z);
        level.setBlock(pos, gold, PLACEMENT_FLAGS);
        pos.set(x, floorY + 7, z);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
    }

    /**
     * Integration Portal - Mystical enchantment circle with purple magic
     */
    private static void placeIntegrationPortal(ServerLevel level, int x, int z, int floorY,
                                                BlockPos.MutableBlockPos pos) {
        @Nonnull BlockState obsidian = nn(Blocks.CRYING_OBSIDIAN.defaultBlockState(), "CRYING_OBSIDIAN");
        @Nonnull BlockState purpur = nn(Blocks.PURPUR_BLOCK.defaultBlockState(), "PURPUR_BLOCK");
        @Nonnull BlockState purpurPillar = nn(Blocks.PURPUR_PILLAR.defaultBlockState(), "PURPUR_PILLAR");
        @Nonnull BlockState endRod = nn(Blocks.END_ROD.defaultBlockState(), "END_ROD");
        @Nonnull BlockState purple = PAD_PURPLE;

        // Mystical rune circle base
        buildRing(level, x, z, floorY, 3, 2, obsidian, pos);
        buildRing(level, x, z, floorY, 2, 1, purple, pos);
        pos.set(x, floorY, z);
        level.setBlock(pos, nn(Blocks.AMETHYST_BLOCK.defaultBlockState(), "AMETHYST_BLOCK"), PLACEMENT_FLAGS);

        // Floating end rods in circle
        int[][] rodPos = {{0, -3}, {3, 0}, {0, 3}, {-3, 0}};
        for (int[] r : rodPos) {
            pos.set(x + r[0], floorY + 1, z + r[1]);
            level.setBlock(pos, purpur, PLACEMENT_FLAGS);
            pos.set(x + r[0], floorY + 2, z + r[1]);
            level.setBlock(pos, endRod, PLACEMENT_FLAGS);
        }

        // Corner purpur pillars
        int[][] corners = {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}};
        for (int[] c : corners) {
            fillColumn(level, x + c[0], z + c[1], floorY + 1, floorY + 5, purpurPillar, pos);
            pos.set(x + c[0], floorY + 6, z + c[1]);
            level.setBlock(pos, endRod, PLACEMENT_FLAGS);
        }

        // Enchanting table aesthetic
        pos.set(x, floorY + 1, z);
        level.setBlock(pos, nn(Blocks.ENCHANTING_TABLE.defaultBlockState(), "ENCHANTING_TABLE"), PLACEMENT_FLAGS);

        // Floating amethyst crown
        pos.set(x, floorY + 5, z);
        level.setBlock(pos, nn(Blocks.AMETHYST_CLUSTER.defaultBlockState(), "AMETHYST_CLUSTER"), PLACEMENT_FLAGS);
    }

    /**
     * Sandbox Portal - Creative builder's frame with scaffolding
     */
    private static void placeSandboxPortal(ServerLevel level, int x, int z, int floorY,
                                            BlockPos.MutableBlockPos pos) {
        @Nonnull BlockState scaffold = nn(Blocks.SCAFFOLDING.defaultBlockState(), "SCAFFOLDING");
        @Nonnull BlockState copper = nn(Blocks.WAXED_COPPER_BLOCK.defaultBlockState(), "WAXED_COPPER_BLOCK");
        @Nonnull BlockState glass = nn(Blocks.CYAN_STAINED_GLASS.defaultBlockState(), "CYAN_STAINED_GLASS");
        @Nonnull BlockState cyan = GRID;

        // Work platform base
        fillRect(level, x - 2, z - 2, x + 2, z + 2, floorY, copper, pos);
        buildRing(level, x, z, floorY, 2, 1, cyan, pos);
        pos.set(x, floorY, z);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);

        // Scaffolding frame (builder aesthetic)
        int[][] scaffoldPos = {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}};
        for (int[] s : scaffoldPos) {
            fillColumn(level, x + s[0], z + s[1], floorY + 1, floorY + 6, scaffold, pos);
        }

        // Horizontal scaffold beams
        for (int dx = -1; dx <= 1; dx++) {
            pos.set(x + dx, floorY + 4, z - 2);
            level.setBlock(pos, scaffold, PLACEMENT_FLAGS);
            pos.set(x + dx, floorY + 4, z + 2);
            level.setBlock(pos, scaffold, PLACEMENT_FLAGS);
        }
        for (int dz = -1; dz <= 1; dz++) {
            pos.set(x - 2, floorY + 4, z + dz);
            level.setBlock(pos, scaffold, PLACEMENT_FLAGS);
            pos.set(x + 2, floorY + 4, z + dz);
            level.setBlock(pos, scaffold, PLACEMENT_FLAGS);
        }

        // Glass work light
        pos.set(x, floorY + 5, z);
        level.setBlock(pos, glass, PLACEMENT_FLAGS);
        pos.set(x, floorY + 6, z);
        level.setBlock(pos, LIGHT, PLACEMENT_FLAGS);
    }

    /**
     * Mechanics Portal - Industrial engineering gateway with pistons and gears
     */
    private static void placeMechanicsPortal(ServerLevel level, int x, int z, int floorY,
                                              BlockPos.MutableBlockPos pos) {
        @Nonnull BlockState iron = METAL;
        @Nonnull BlockState piston = nn(Blocks.PISTON.defaultBlockState(), "PISTON");
        @Nonnull BlockState observer = nn(Blocks.OBSERVER.defaultBlockState(), "OBSERVER");
        @Nonnull BlockState hopper = nn(Blocks.HOPPER.defaultBlockState(), "HOPPER");
        @Nonnull BlockState bars = TRUSS;

        // Industrial base
        fillRect(level, x - 2, z - 2, x + 2, z + 2, floorY, iron, pos);
        pos.set(x, floorY, z);
        level.setBlock(pos, nn(Blocks.REDSTONE_LAMP.defaultBlockState(), "REDSTONE_LAMP"), PLACEMENT_FLAGS);

        // Gear-like corner pieces with pistons
        int[][] mechs = {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}};
        for (int i = 0; i < mechs.length; i++) {
            int[] m = mechs[i];
            pos.set(x + m[0], floorY + 1, z + m[1]);
            level.setBlock(pos, piston, PLACEMENT_FLAGS);
            fillColumn(level, x + m[0], z + m[1], floorY + 2, floorY + 4, iron, pos);
            pos.set(x + m[0], floorY + 5, z + m[1]);
            level.setBlock(pos, nn(i % 2 == 0 ? observer : hopper, "mechBlock"), PLACEMENT_FLAGS);
            pos.set(x + m[0], floorY + 6, z + m[1]);
            level.setBlock(pos, nn(Blocks.REDSTONE_LAMP.defaultBlockState(), "REDSTONE_LAMP"), PLACEMENT_FLAGS);
        }

        // Iron bar framework
        for (int dx = -1; dx <= 1; dx++) {
            pos.set(x + dx, floorY + 5, z - 2);
            level.setBlock(pos, bars, PLACEMENT_FLAGS);
            pos.set(x + dx, floorY + 5, z + 2);
            level.setBlock(pos, bars, PLACEMENT_FLAGS);
        }
        for (int dz = -1; dz <= 1; dz++) {
            pos.set(x - 2, floorY + 5, z + dz);
            level.setBlock(pos, bars, PLACEMENT_FLAGS);
            pos.set(x + 2, floorY + 5, z + dz);
            level.setBlock(pos, bars, PLACEMENT_FLAGS);
        }

        // Central mechanism
        pos.set(x, floorY + 1, z);
        level.setBlock(pos, nn(Blocks.DAYLIGHT_DETECTOR.defaultBlockState(), "DAYLIGHT_DETECTOR"), PLACEMENT_FLAGS);
    }

    private static StructureTemplate resolveTemplate(ServerLevel level, ResourceLocation location) {
        Object manager = level.getStructureManager();
        try {
            Method getOrCreate = manager.getClass().getMethod("getOrCreate", ResourceLocation.class);
            Object result = getOrCreate.invoke(manager, location);
            if (result instanceof StructureTemplate template) {
                return template;
            }
        } catch (ReflectiveOperationException ignored) {
            // Optional API path, fall back below.
        }
        try {
            Method get = manager.getClass().getMethod("get", ResourceLocation.class);
            Object result = get.invoke(manager, location);
            if (result instanceof Optional<?> opt && opt.isPresent() && opt.get() instanceof StructureTemplate template) {
                return template;
            }
            if (result instanceof StructureTemplate template) {
                return template;
            }
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
        return null;
    }

    @javax.annotation.Nonnull
    private static <T> T nn(T value, String context) {
        return Objects.requireNonNull(value, context);
    }
}
