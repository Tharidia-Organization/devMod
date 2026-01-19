package com.devmod.nexus.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.core.BlockPos;

import com.devmod.config.Config;
import com.devmod.portal.PortalColor;
import com.devmod.zone.data.ZoneBounds;

/**
 * Default zone slot definitions for the Nexus hub.
 * Based on the 100-chunk (1600x1600) blueprint design.
 *
 * <p>Layout overview:
 * <pre>
 * Blueprint Scale: 100 chunks = 1600x1600 blocks
 *
 *                      NORTH
 *            +---------+-------+---------+
 *            | QUEST   | TUTOR | QUEST   |
 *            | NW      | IAL   | NE      |
 *            +---------+-------+---------+
 *            | BUILD   |       | BUILD   |
 *            | W       |       | E       |
 *     WEST   +---------+ SPAWN +---------+  EAST
 *            | ECON    |       | ECON    |
 *            | W       |       | E       |
 *            +---------+-------+---------+
 *            | WAR     | CLASS | WAR     |
 *            | W       | ES    | E       |
 *            +---------+-------+---------+
 *            | TOWN    | DM    | EVENTI  |
 *            | MGMT    | MOD   |         |
 *            +---------+-------+---------+
 *                      SOUTH
 * </pre>
 */
public final class ZoneSlotPresets {

    private ZoneSlotPresets() {}

    // ========================================================================
    // Layout Constants (defaults - should match Config.NEXUS_* values)
    // ========================================================================

    /** Total hub size in blocks (24 chunks). See Config.NEXUS_HUB_SIZE */
    public static final int HUB_SIZE = 384;


    /** Center spawn area size. See Config.NEXUS_CENTER_SIZE */
    public static final int CENTER_SIZE = 64;

    /** Standard zone size (width/length). See Config.NEXUS_ZONE_SIZE */
    public static final int ZONE_SIZE = 96;

    /** Corridor width between zones. See Config.NEXUS_CORRIDOR_WIDTH */
    public static final int CORRIDOR_WIDTH = 16;

    /** Floor Y level. See Config.NEXUS_FLOOR_Y */
    public static final int FLOOR_Y = 64;

    /** Default zone height. See Config.NEXUS_ZONE_HEIGHT */
    public static final int ZONE_HEIGHT = 64;

    // Derived values are computed per-layout to respect config overrides.

    // ========================================================================
    // Config-aware Getters (use at runtime when Config is loaded)
    // ========================================================================

    /** Get hub size from Config, with fallback to default */
    public static int getHubSize() {
        try {
            return Config.NEXUS_HUB_SIZE.get();
        } catch (Exception e) {
            return HUB_SIZE;
        }
    }

    /** Get center size from Config, with fallback to default */
    public static int getCenterSize() {
        try {
            return Config.NEXUS_CENTER_SIZE.get();
        } catch (Exception e) {
            return CENTER_SIZE;
        }
    }

    /** Get zone size from Config, with fallback to default */
    public static int getZoneSize() {
        try {
            return Config.NEXUS_ZONE_SIZE.get();
        } catch (Exception e) {
            return ZONE_SIZE;
        }
    }

    /** Get corridor width from Config, with fallback to default */
    public static int getCorridorWidth() {
        try {
            return Config.NEXUS_CORRIDOR_WIDTH.get();
        } catch (Exception e) {
            return CORRIDOR_WIDTH;
        }
    }

    /** Get floor Y from Config, with fallback to default */
    public static int getFloorY() {
        try {
            return Config.NEXUS_FLOOR_Y.get();
        } catch (Exception e) {
            return FLOOR_Y;
        }
    }

    /** Get zone height from Config, with fallback to default */
    public static int getZoneHeight() {
        try {
            return Config.NEXUS_ZONE_HEIGHT.get();
        } catch (Exception e) {
            return ZONE_HEIGHT;
        }
    }

    /** Check if auto-create zones is enabled in Config */
    public static boolean isAutoCreateZonesEnabled() {
        try {
            return Config.NEXUS_AUTO_CREATE_ZONES.get();
        } catch (Exception e) {
            return true;
        }
    }

    /** Check if auto-create portals is enabled in Config */
    public static boolean isAutoCreatePortalsEnabled() {
        try {
            return Config.NEXUS_AUTO_CREATE_PORTALS.get();
        } catch (Exception e) {
            return true;
        }
    }

    private record Layout(
        int hubSize,
        int hubHalf,
        int centerSize,
        int centerHalf,
        int zoneSize,
        int corridorWidth,
        int floorY,
        int zoneHeight,
        int outerZoneSize
    ) {}

    private static Layout layout() {
        int hubSize = getHubSize();
        int centerSize = getCenterSize();
        int zoneSize = getZoneSize();
        int corridorWidth = getCorridorWidth();
        int floorY = getFloorY();
        int zoneHeight = getZoneHeight();
        int centerHalf = centerSize / 2;
        int hubHalf = hubSize / 2;
        int outerZoneSize = Math.max(64, (zoneSize * 3) / 4);
        return new Layout(
            hubSize,
            hubHalf,
            centerSize,
            centerHalf,
            zoneSize,
            corridorWidth,
            floorY,
            zoneHeight,
            outerZoneSize
        );
    }

    // ========================================================================
    // Preset Creation
    // ========================================================================

    /**
     * Create all default slots for the Nexus hub.
     * Total: 15 slots (1 center + 14 surrounding zones)
     *
     * @return list of default ZoneSlot definitions
     */
    public static List<ZoneSlot> createDefaultSlots() {
        Layout layout = layout();
        List<ZoneSlot> slots = new ArrayList<>();

        // === CENTER (Ring 0) ===
        slots.add(createSpawnSlot(layout));

        // === INNER RING (Ring 1) - 8 cardinal/diagonal directions ===
        slots.add(createTutorialSlot(layout));      // North
        slots.add(createGateProgressionSlot(layout)); // East
        slots.add(createClassesSlot(layout));       // South
        slots.add(createBuildingSlotWest(layout));  // West

        slots.add(createQuestSlotNE(layout));       // North-East
        slots.add(createQuestSlotNW(layout));       // North-West
        slots.add(createWarHubSlotSE(layout));      // South-East
        slots.add(createWarHubSlotSW(layout));      // South-West

        // === OUTER RING (Ring 2) ===
        slots.add(createBuildingSlotEast(layout));  // East outer
        slots.add(createEconomiaSlotEast(layout));  // East-South
        slots.add(createEconomiaSlotWest(layout));  // West-South
        slots.add(createTownManagementSlot(layout)); // South-West outer
        slots.add(createEventiSlot(layout));        // South-East outer
        slots.add(createDmModSlot(layout));         // South outer

        return slots;
    }

    // ========================================================================
    // Individual Slot Definitions
    // ========================================================================

    /**
     * SPAWN - Center platform (FIXED, non-modifiable)
     */
    private static ZoneSlot createSpawnSlot(Layout layout) {
        ZoneBounds bounds = ZoneBounds.fromCenterAndSize(
            new BlockPos(0, layout.floorY(), 0),
            layout.centerHalf(), layout.zoneHeight() / 2, layout.centerHalf()
        );
        return ZoneSlot.createFull(
            "spawn",
            "Spawn",
            bounds,
            SlotType.FIXED,
            new BlockPos(layout.centerHalf(), 1, layout.centerHalf()), // Portal at center
            PortalColor.WHITE,
            null, // No template - always built by foundation
            100   // Highest priority
        );
    }

    /**
     * TUTORIAL - North of center (RESTRICTED)
     * Movement, combat, stamina tutorial area.
     */
    private static ZoneSlot createTutorialSlot(Layout layout) {
        ZoneBounds bounds = Objects.requireNonNull(createBoundsNorth(layout, 1));
        return ZoneSlot.createFull(
            "tutorial",
            "Tutorial",
            bounds,
            SlotType.RESTRICTED,
            new BlockPos(layout.zoneSize() / 2, 1, layout.zoneSize() - 16), // Portal facing center
            PortalColor.LIGHT_BLUE,
            "tutorial_movement",
            50
        );
    }

    /**
     * GATE_PROGRESSION - East of center (RESTRICTED)
     * Chapter portals, world boss gates.
     */
    private static ZoneSlot createGateProgressionSlot(Layout layout) {
        ZoneBounds bounds = Objects.requireNonNull(createBoundsEast(layout, 1));
        return ZoneSlot.createFull(
            "gate_progression",
            "Gate Progression",
            bounds,
            SlotType.RESTRICTED,
            new BlockPos(16, 1, layout.zoneSize() / 2), // Portal facing center
            PortalColor.PURPLE,
            "gate_progression_standard",
            50
        );
    }

    /**
     * CLASSES_SYSTEM - South of center (EDITABLE)
     * Class masters, crafting, minigames.
     */
    private static ZoneSlot createClassesSlot(Layout layout) {
        ZoneBounds bounds = Objects.requireNonNull(createBoundsSouth(layout, 1));
        return ZoneSlot.createFull(
            "classes",
            "Classes System",
            bounds,
            SlotType.EDITABLE,
            new BlockPos(layout.zoneSize() / 2, 1, 16), // Portal facing center
            PortalColor.CYAN,
            "class_master_hall",
            40
        );
    }

    /**
     * BUILDING West - West of center (EDITABLE)
     * Claim system, building area.
     */
    private static ZoneSlot createBuildingSlotWest(Layout layout) {
        ZoneBounds bounds = Objects.requireNonNull(createBoundsWest(layout, 1));
        return ZoneSlot.createFull(
            "building_west",
            "Building Zone West",
            bounds,
            SlotType.EDITABLE,
            new BlockPos(layout.zoneSize() - 16, 1, layout.zoneSize() / 2), // Portal facing center
            PortalColor.GREEN,
            "building_claim",
            30
        );
    }

    /**
     * BUILDING East - East outer ring (EDITABLE)
     */
    private static ZoneSlot createBuildingSlotEast(Layout layout) {
        ZoneBounds bounds = Objects.requireNonNull(createBoundsEast(layout, 2));
        return ZoneSlot.createFull(
            "building_east",
            "Building Zone East",
            bounds,
            SlotType.EDITABLE,
            new BlockPos(16, 1, layout.zoneSize() / 2),
            PortalColor.GREEN,
            "building_claim",
            30
        );
    }

    /**
     * QUEST Hub North-East (EDITABLE)
     */
    private static ZoneSlot createQuestSlotNE(Layout layout) {
        ZoneBounds bounds = Objects.requireNonNull(createBoundsNorthEast(layout));
        return ZoneSlot.createFull(
            "quest_northeast",
            "Quest Hub NE",
            bounds,
            SlotType.EDITABLE,
            new BlockPos(16, 1, layout.zoneSize() - 16), // Portal facing center
            PortalColor.YELLOW,
            "quest_hub_standard",
            35
        );
    }

    /**
     * QUEST Hub North-West (EDITABLE)
     */
    private static ZoneSlot createQuestSlotNW(Layout layout) {
        ZoneBounds bounds = Objects.requireNonNull(createBoundsNorthWest(layout));
        return ZoneSlot.createFull(
            "quest_northwest",
            "Quest Hub NW",
            bounds,
            SlotType.EDITABLE,
            new BlockPos(layout.zoneSize() - 16, 1, layout.zoneSize() - 16),
            PortalColor.YELLOW,
            "quest_hub_standard",
            35
        );
    }

    /**
     * WAR_HUB South-East (EDITABLE)
     * Arena, siege content.
     */
    private static ZoneSlot createWarHubSlotSE(Layout layout) {
        ZoneBounds bounds = Objects.requireNonNull(createBoundsSouthEast(layout));
        return ZoneSlot.createFull(
            "war_hub_east",
            "War Hub East",
            bounds,
            SlotType.EDITABLE,
            new BlockPos(16, 1, 16),
            PortalColor.RED,
            "war_hub_standard",
            35
        );
    }

    /**
     * WAR_HUB South-West (EDITABLE)
     */
    private static ZoneSlot createWarHubSlotSW(Layout layout) {
        ZoneBounds bounds = Objects.requireNonNull(createBoundsSouthWest(layout));
        return ZoneSlot.createFull(
            "war_hub_west",
            "War Hub West",
            bounds,
            SlotType.EDITABLE,
            new BlockPos(layout.zoneSize() - 16, 1, 16),
            PortalColor.RED,
            "war_hub_standard",
            35
        );
    }

    /**
     * ECONOMIA East (EDITABLE)
     * Shop, bank, auction.
     * Positioned east of war_hub_east with a corridor gap.
     */
    private static ZoneSlot createEconomiaSlotEast(Layout layout) {
        // X starts after war_hub_east (center + corridor + zone), plus corridor gap
        int xOffset = layout.centerHalf() + layout.corridorWidth() + layout.zoneSize() + layout.corridorWidth();
        int zOffset = layout.centerHalf() + layout.corridorWidth();
        ZoneBounds bounds = new ZoneBounds(
            xOffset, xOffset + layout.zoneSize(),
            layout.floorY(), layout.floorY() + layout.zoneHeight(),
            zOffset, zOffset + layout.zoneSize()
        );
        return ZoneSlot.createFull(
            "economia_east",
            "Economia East",
            bounds,
            SlotType.EDITABLE,
            new BlockPos(16, 1, layout.zoneSize() / 2),
            PortalColor.ORANGE,
            "economy_shop_standard",
            30
        );
    }

    /**
     * ECONOMIA West (EDITABLE)
     * Positioned west of war_hub_west with a corridor gap.
     */
    private static ZoneSlot createEconomiaSlotWest(Layout layout) {
        // X ends before war_hub_west (center + corridor + zone), plus corridor gap
        int xOffset = -(layout.centerHalf() + layout.corridorWidth() + layout.zoneSize()
            + layout.corridorWidth() + layout.zoneSize());
        int zOffset = layout.centerHalf() + layout.corridorWidth();
        ZoneBounds bounds = new ZoneBounds(
            xOffset, xOffset + layout.zoneSize(),
            layout.floorY(), layout.floorY() + layout.zoneHeight(),
            zOffset, zOffset + layout.zoneSize()
        );
        return ZoneSlot.createFull(
            "economia_west",
            "Economia West",
            bounds,
            SlotType.EDITABLE,
            new BlockPos(layout.zoneSize() - 16, 1, 16),
            PortalColor.ORANGE,
            "economy_shop_standard",
            30
        );
    }

    /**
     * TOWN_TEST / GESTIONALE / POLITICA - South-West outer (EDITABLE)
     */
    private static ZoneSlot createTownManagementSlot(Layout layout) {
        int xOffset = -(layout.centerHalf() + layout.corridorWidth() + layout.outerZoneSize());
        int zOffset = layout.centerHalf() + layout.corridorWidth() + layout.zoneSize() + layout.corridorWidth();
        ZoneBounds bounds = new ZoneBounds(
            xOffset, xOffset + layout.outerZoneSize(),
            layout.floorY(), layout.floorY() + layout.zoneHeight(),
            zOffset, zOffset + layout.outerZoneSize()
        );
        return ZoneSlot.createFull(
            "town_management",
            "Town & Politics",
            bounds,
            SlotType.EDITABLE,
            new BlockPos(layout.outerZoneSize() - 16, 1, 16),
            PortalColor.BROWN,
            "town_politica_factions",
            20
        );
    }

    /**
     * EVENTI_PERIODICI - South-East outer (EDITABLE)
     * Tournaments, events.
     */
    private static ZoneSlot createEventiSlot(Layout layout) {
        int xOffset = layout.centerHalf() + layout.corridorWidth() + layout.zoneSize();
        int zOffset = layout.centerHalf() + layout.corridorWidth() + layout.zoneSize() + layout.corridorWidth();
        ZoneBounds bounds = new ZoneBounds(
            xOffset, xOffset + layout.outerZoneSize(),
            layout.floorY(), layout.floorY() + layout.zoneHeight(),
            zOffset, zOffset + layout.outerZoneSize()
        );
        return ZoneSlot.createFull(
            "eventi",
            "Eventi Periodici",
            bounds,
            SlotType.EDITABLE,
            new BlockPos(16, 1, 16),
            PortalColor.MAGENTA,
            "eventi_torneo_arena",
            20
        );
    }

    /**
     * DM_MOD - South outer center (RESTRICTED)
     * Admin commands, HUD testing.
     * Uses CENTER_HALF for X width to avoid overlapping with town_management.
     */
    private static ZoneSlot createDmModSlot(Layout layout) {
        int zOffset = layout.centerHalf() + layout.corridorWidth() + layout.zoneSize() + layout.corridorWidth();
        ZoneBounds bounds = new ZoneBounds(
            -layout.centerHalf(), layout.centerHalf(),  // Narrower X to avoid overlap with town_management
            layout.floorY(), layout.floorY() + layout.zoneHeight(),
            zOffset, zOffset + layout.outerZoneSize()
        );
        return ZoneSlot.createFull(
            "dm_mod",
            "DM Mod Testing",
            bounds,
            SlotType.RESTRICTED,
            new BlockPos(layout.centerHalf(), 1, 16),  // Updated portal position
            PortalColor.GRAY,
            "dm_mod_control_room",
            25
        );
    }

    // ========================================================================
    // Bounds Helper Methods
    // ========================================================================

    /**
     * Create bounds for North zone at given ring (1 = adjacent to center).
     * Uses centerHalf for X width to avoid overlapping with diagonal zones.
     */
    private static ZoneBounds createBoundsNorth(Layout layout, int ring) {
        int distance = layout.centerHalf() + layout.corridorWidth()
            + (ring - 1) * (layout.zoneSize() + layout.corridorWidth());
        return new ZoneBounds(
            -layout.centerHalf(), layout.centerHalf(),  // Narrower X to avoid diagonal overlap
            layout.floorY(), layout.floorY() + layout.zoneHeight(),
            -(distance + layout.zoneSize()), -distance
        );
    }

    /**
     * Create bounds for South zone at given ring.
     * Uses centerHalf for X width to avoid overlapping with diagonal zones.
     */
    private static ZoneBounds createBoundsSouth(Layout layout, int ring) {
        int distance = layout.centerHalf() + layout.corridorWidth()
            + (ring - 1) * (layout.zoneSize() + layout.corridorWidth());
        return new ZoneBounds(
            -layout.centerHalf(), layout.centerHalf(),  // Narrower X to avoid diagonal overlap
            layout.floorY(), layout.floorY() + layout.zoneHeight(),
            distance, distance + layout.zoneSize()
        );
    }

    /**
     * Create bounds for East zone at given ring.
     * Uses centerHalf for Z width to avoid overlapping with diagonal zones.
     */
    private static ZoneBounds createBoundsEast(Layout layout, int ring) {
        int distance = layout.centerHalf() + layout.corridorWidth()
            + (ring - 1) * (layout.zoneSize() + layout.corridorWidth());
        return new ZoneBounds(
            distance, distance + layout.zoneSize(),
            layout.floorY(), layout.floorY() + layout.zoneHeight(),
            -layout.centerHalf(), layout.centerHalf()  // Narrower Z to avoid diagonal overlap
        );
    }

    /**
     * Create bounds for West zone at given ring.
     * Uses centerHalf for Z width to avoid overlapping with diagonal zones.
     */
    private static ZoneBounds createBoundsWest(Layout layout, int ring) {
        int distance = layout.centerHalf() + layout.corridorWidth()
            + (ring - 1) * (layout.zoneSize() + layout.corridorWidth());
        return new ZoneBounds(
            -(distance + layout.zoneSize()), -distance,
            layout.floorY(), layout.floorY() + layout.zoneHeight(),
            -layout.centerHalf(), layout.centerHalf()  // Narrower Z to avoid diagonal overlap
        );
    }

    /**
     * Create bounds for North-East diagonal zone.
     */
    private static ZoneBounds createBoundsNorthEast(Layout layout) {
        int offset = layout.centerHalf() + layout.corridorWidth();
        return new ZoneBounds(
            offset, offset + layout.zoneSize(),
            layout.floorY(), layout.floorY() + layout.zoneHeight(),
            -(offset + layout.zoneSize()), -offset
        );
    }

    /**
     * Create bounds for North-West diagonal zone.
     */
    private static ZoneBounds createBoundsNorthWest(Layout layout) {
        int offset = layout.centerHalf() + layout.corridorWidth();
        return new ZoneBounds(
            -(offset + layout.zoneSize()), -offset,
            layout.floorY(), layout.floorY() + layout.zoneHeight(),
            -(offset + layout.zoneSize()), -offset
        );
    }

    /**
     * Create bounds for South-East diagonal zone.
     */
    private static ZoneBounds createBoundsSouthEast(Layout layout) {
        int offset = layout.centerHalf() + layout.corridorWidth();
        return new ZoneBounds(
            offset, offset + layout.zoneSize(),
            layout.floorY(), layout.floorY() + layout.zoneHeight(),
            offset, offset + layout.zoneSize()
        );
    }

    /**
     * Create bounds for South-West diagonal zone.
     */
    private static ZoneBounds createBoundsSouthWest(Layout layout) {
        int offset = layout.centerHalf() + layout.corridorWidth();
        return new ZoneBounds(
            -(offset + layout.zoneSize()), -offset,
            layout.floorY(), layout.floorY() + layout.zoneHeight(),
            offset, offset + layout.zoneSize()
        );
    }
}
