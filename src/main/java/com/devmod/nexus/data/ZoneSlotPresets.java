package com.devmod.nexus.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.core.BlockPos;

import com.devmod.config.Config;
import com.devmod.portal.PortalColor;
import com.devmod.zone.data.ZoneBounds;

/**
 * Default zone slot definitions for the Nexus testing lab.
 * Each zone maps to a DevMod module that testers need to verify.
 *
 * <p>Layout overview:
 * <pre>
 *                      NORTH
 *            +---------+-------+---------+
 *            | VFX     | COMBAT| NPC     |
 *            | STUDIO  | LAB   | LAB     |
 *            +---------+-------+---------+
 *            | PORTAL  |       | ABILIT  |
 *            | LAB     |       | IES LAB |
 *     WEST   +---------+ SPAWN +---------+  EAST
 *            | ARENA   |       | COLLIS  |
 *            | BUILDER |       | ION LAB |
 *            +---------+-------+---------+
 *            | HUD     | BOSS  | ITEM    |
 *            | TEST    | ARENA | WORKSHOP|
 *            +---------+-------+---------+
 *            | QUEST   | ADMIN | SANDBOX |
 *            | TESTING | TOOLS |         |
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
        slots.add(createCombatLabSlot(layout));       // North
        slots.add(createAbilitiesLabSlot(layout));    // East
        slots.add(createBossArenaSlot(layout));       // South
        slots.add(createPortalLabSlot(layout));       // West

        slots.add(createNpcLabSlot(layout));          // North-East
        slots.add(createVfxStudioSlot(layout));       // North-West
        slots.add(createCollisionLabSlot(layout));    // South-East
        slots.add(createArenaBuilderSlot(layout));    // South-West

        // === OUTER RING (Ring 2) ===
        slots.add(createItemWorkshopSlot(layout));    // East outer
        slots.add(createConfigRoomSlot(layout));      // East-South (uses economia_east position)
        slots.add(createHudTestingSlot(layout));      // West-South (uses economia_west position)
        slots.add(createQuestTestingSlot(layout));    // South-West outer
        slots.add(createSandboxSlot(layout));         // South-East outer
        slots.add(createAdminToolsSlot(layout));      // South outer

        return slots;
    }

    // ========================================================================
    // Individual Slot Definitions
    // ========================================================================

    /**
     * SPAWN - Center platform (FIXED, non-modifiable)
     * Hub center with telepad network and info holograms.
     */
    private static ZoneSlot createSpawnSlot(Layout layout) {
        ZoneBounds bounds = ZoneBounds.fromCenterAndSize(
            new BlockPos(0, layout.floorY(), 0),
            layout.centerHalf(), layout.zoneHeight() / 2, layout.centerHalf()
        );
        return ZoneSlot.createFull(
            "spawn",
            "Hub Center",
            bounds,
            SlotType.FIXED,
            new BlockPos(layout.centerHalf(), 1, layout.centerHalf()),
            PortalColor.WHITE,
            null,
            100
        );
    }

    /**
     * COMBAT_LAB - North of center (EDITABLE)
     * Combat system testing: training dummies, weapons, combo, style ranks.
     */
    private static ZoneSlot createCombatLabSlot(Layout layout) {
        ZoneBounds bounds = Objects.requireNonNull(createBoundsNorth(layout, 1));
        return ZoneSlot.createFull(
            "combat_lab",
            "Combat Lab",
            bounds,
            SlotType.EDITABLE,
            new BlockPos(layout.zoneSize() / 2, 1, layout.zoneSize() - 16),
            PortalColor.RED,
            null,
            50
        );
    }

    /**
     * ABILITIES_LAB - East of center (EDITABLE)
     * Stamina, dash, dodge, movement testing.
     */
    private static ZoneSlot createAbilitiesLabSlot(Layout layout) {
        ZoneBounds bounds = Objects.requireNonNull(createBoundsEast(layout, 1));
        return ZoneSlot.createFull(
            "abilities_lab",
            "Abilities Lab",
            bounds,
            SlotType.EDITABLE,
            new BlockPos(16, 1, layout.zoneSize() / 2),
            PortalColor.CYAN,
            null,
            50
        );
    }

    /**
     * BOSS_ARENA - South of center (EDITABLE)
     * Endurance quest, wave system, boss phases testing.
     */
    private static ZoneSlot createBossArenaSlot(Layout layout) {
        ZoneBounds bounds = Objects.requireNonNull(createBoundsSouth(layout, 1));
        return ZoneSlot.createFull(
            "boss_arena",
            "Boss Arena",
            bounds,
            SlotType.EDITABLE,
            new BlockPos(layout.zoneSize() / 2, 1, 16),
            PortalColor.PURPLE,
            null,
            40
        );
    }

    /**
     * PORTAL_LAB - West of center (EDITABLE)
     * Portal system, telepad network, transport nodes testing.
     */
    private static ZoneSlot createPortalLabSlot(Layout layout) {
        ZoneBounds bounds = Objects.requireNonNull(createBoundsWest(layout, 1));
        return ZoneSlot.createFull(
            "portal_lab",
            "Portal Lab",
            bounds,
            SlotType.EDITABLE,
            new BlockPos(layout.zoneSize() - 16, 1, layout.zoneSize() / 2),
            PortalColor.LIGHT_BLUE,
            null,
            30
        );
    }

    /**
     * NPC_LAB - North-East (EDITABLE)
     * NPC spawning, dialog trees, clone system testing.
     */
    private static ZoneSlot createNpcLabSlot(Layout layout) {
        ZoneBounds bounds = Objects.requireNonNull(createBoundsNorthEast(layout));
        return ZoneSlot.createFull(
            "npc_lab",
            "NPC Lab",
            bounds,
            SlotType.EDITABLE,
            new BlockPos(16, 1, layout.zoneSize() - 16),
            PortalColor.GREEN,
            null,
            35
        );
    }

    /**
     * VFX_STUDIO - North-West (EDITABLE)
     * Effekseer effects, impact VFX, particles testing.
     */
    private static ZoneSlot createVfxStudioSlot(Layout layout) {
        ZoneBounds bounds = Objects.requireNonNull(createBoundsNorthWest(layout));
        return ZoneSlot.createFull(
            "vfx_studio",
            "VFX Studio",
            bounds,
            SlotType.EDITABLE,
            new BlockPos(layout.zoneSize() - 16, 1, layout.zoneSize() - 16),
            PortalColor.MAGENTA,
            null,
            35
        );
    }

    /**
     * COLLISION_LAB - South-East (EDITABLE)
     * Hitbox visualization, OBB debug, body parts testing.
     */
    private static ZoneSlot createCollisionLabSlot(Layout layout) {
        ZoneBounds bounds = Objects.requireNonNull(createBoundsSouthEast(layout));
        return ZoneSlot.createFull(
            "collision_lab",
            "Collision Lab",
            bounds,
            SlotType.EDITABLE,
            new BlockPos(16, 1, 16),
            PortalColor.ORANGE,
            null,
            35
        );
    }

    /**
     * ARENA_BUILDER - South-West (EDITABLE)
     * Area building, templates, snapshots, zone management testing.
     */
    private static ZoneSlot createArenaBuilderSlot(Layout layout) {
        ZoneBounds bounds = Objects.requireNonNull(createBoundsSouthWest(layout));
        return ZoneSlot.createFull(
            "arena_builder",
            "Arena Builder",
            bounds,
            SlotType.EDITABLE,
            new BlockPos(layout.zoneSize() - 16, 1, 16),
            PortalColor.YELLOW,
            null,
            35
        );
    }

    /**
     * ITEM_WORKSHOP - East outer ring (EDITABLE)
     * Item editor, equipment, armor, crafting testing.
     */
    private static ZoneSlot createItemWorkshopSlot(Layout layout) {
        ZoneBounds bounds = Objects.requireNonNull(createBoundsEast(layout, 2));
        return ZoneSlot.createFull(
            "item_workshop",
            "Item Workshop",
            bounds,
            SlotType.EDITABLE,
            new BlockPos(16, 1, layout.zoneSize() / 2),
            PortalColor.BROWN,
            null,
            30
        );
    }

    /**
     * CONFIG_ROOM - East-South outer (EDITABLE)
     * Config hot-reload, gamedesign presets testing.
     */
    private static ZoneSlot createConfigRoomSlot(Layout layout) {
        int xOffset = layout.centerHalf() + layout.corridorWidth() + layout.zoneSize() + layout.corridorWidth();
        int zOffset = layout.centerHalf() + layout.corridorWidth();
        ZoneBounds bounds = new ZoneBounds(
            xOffset, xOffset + layout.zoneSize(),
            layout.floorY(), layout.floorY() + layout.zoneHeight(),
            zOffset, zOffset + layout.zoneSize()
        );
        return ZoneSlot.createFull(
            "config_room",
            "Config Room",
            bounds,
            SlotType.EDITABLE,
            new BlockPos(16, 1, layout.zoneSize() / 2),
            PortalColor.GRAY,
            null,
            30
        );
    }

    /**
     * HUD_TESTING - West-South outer (EDITABLE)
     * HUD overlays, radial menu, debug overlays testing.
     */
    private static ZoneSlot createHudTestingSlot(Layout layout) {
        int xOffset = -(layout.centerHalf() + layout.corridorWidth() + layout.zoneSize()
            + layout.corridorWidth() + layout.zoneSize());
        int zOffset = layout.centerHalf() + layout.corridorWidth();
        ZoneBounds bounds = new ZoneBounds(
            xOffset, xOffset + layout.zoneSize(),
            layout.floorY(), layout.floorY() + layout.zoneHeight(),
            zOffset, zOffset + layout.zoneSize()
        );
        return ZoneSlot.createFull(
            "hud_testing",
            "HUD Testing",
            bounds,
            SlotType.EDITABLE,
            new BlockPos(layout.zoneSize() - 16, 1, 16),
            PortalColor.LIGHT_BLUE,
            null,
            30
        );
    }

    /**
     * QUEST_TESTING - South-West outer (EDITABLE)
     * Quest system, leaderboards, challenges testing.
     */
    private static ZoneSlot createQuestTestingSlot(Layout layout) {
        int xOffset = -(layout.centerHalf() + layout.corridorWidth() + layout.outerZoneSize());
        int zOffset = layout.centerHalf() + layout.corridorWidth() + layout.zoneSize() + layout.corridorWidth();
        ZoneBounds bounds = new ZoneBounds(
            xOffset, xOffset + layout.outerZoneSize(),
            layout.floorY(), layout.floorY() + layout.zoneHeight(),
            zOffset, zOffset + layout.outerZoneSize()
        );
        return ZoneSlot.createFull(
            "quest_testing",
            "Quest Testing",
            bounds,
            SlotType.EDITABLE,
            new BlockPos(layout.outerZoneSize() - 16, 1, 16),
            PortalColor.YELLOW,
            null,
            20
        );
    }

    /**
     * SANDBOX - South-East outer (EDITABLE)
     * Free creative area for any testing.
     */
    private static ZoneSlot createSandboxSlot(Layout layout) {
        int xOffset = layout.centerHalf() + layout.corridorWidth() + layout.zoneSize();
        int zOffset = layout.centerHalf() + layout.corridorWidth() + layout.zoneSize() + layout.corridorWidth();
        ZoneBounds bounds = new ZoneBounds(
            xOffset, xOffset + layout.outerZoneSize(),
            layout.floorY(), layout.floorY() + layout.zoneHeight(),
            zOffset, zOffset + layout.outerZoneSize()
        );
        return ZoneSlot.createFull(
            "sandbox",
            "Sandbox",
            bounds,
            SlotType.EDITABLE,
            new BlockPos(16, 1, 16),
            PortalColor.WHITE,
            null,
            20
        );
    }

    /**
     * ADMIN_TOOLS - South outer center (EDITABLE)
     * Admin commands, telemetry, dashboard, mailbox testing.
     */
    private static ZoneSlot createAdminToolsSlot(Layout layout) {
        int zOffset = layout.centerHalf() + layout.corridorWidth() + layout.zoneSize() + layout.corridorWidth();
        ZoneBounds bounds = new ZoneBounds(
            -layout.centerHalf(), layout.centerHalf(),
            layout.floorY(), layout.floorY() + layout.zoneHeight(),
            zOffset, zOffset + layout.outerZoneSize()
        );
        return ZoneSlot.createFull(
            "admin_tools",
            "Admin Tools",
            bounds,
            SlotType.EDITABLE,
            new BlockPos(layout.centerHalf(), 1, 16),
            PortalColor.GRAY,
            null,
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
