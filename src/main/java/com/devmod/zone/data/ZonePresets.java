package com.devmod.zone.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;

import com.devmod.portal.PortalColor;

/**
 * Provides preset zone definitions for legacy migration.
 * Converts hardcoded zones from NexusZoneLayout/NexusSpawnManager into ZoneDefinitions.
 */
public final class ZonePresets {
    private ZonePresets() {}

    // ========================================================================
    // Layout Constants (from NexusZoneLayout)
    // ========================================================================

    private static final int HUB_HALF_SIZE = 96;
    private static final int CENTER_HALF_SIZE = 24;
    private static final int WALL_HEIGHT = 8;

    // ========================================================================
    // Zone Colors (from impl-zone-marker BIBBIA ESTETICA)
    // ========================================================================

    public static final int COLOR_HUB = 0x00D4AA;
    public static final int COLOR_COMBAT = 0xFF4444;
    public static final int COLOR_ARENA = 0xFF8800;
    public static final int COLOR_UI = 0x4488FF;
    public static final int COLOR_TELEMETRY = 0x44FF44;
    public static final int COLOR_SHOWCASE = 0xFF44FF;
    public static final int COLOR_INTEGRATION = 0xAA44FF;
    public static final int COLOR_SANDBOX = 0x44FFFF;
    public static final int COLOR_MECHANICS = 0xAAAAAA;
    public static final int COLOR_OVERVIEW = 0xFFAA00;
    public static final int COLOR_SPECIAL = 0x666666;

    // ========================================================================
    // Public API
    // ========================================================================

    /**
     * Creates all legacy zone definitions relative to the hub origin.
     *
     * @param origin The hub origin position
     * @return List of zone definitions
     */
    @Nonnull
    public static List<ZoneDefinition> createLegacyZones(@Nonnull BlockPos origin) {
        Objects.requireNonNull(origin, "origin");
        Layout layout = new Layout(origin);
        List<ZoneDefinition> zones = new ArrayList<>();

        // Calculate split coordinates for room divisions
        int northSplitZ = splitCoordinate(layout.roomMinZ1, layout.roomMaxZ1);
        int northTopMax = northSplitZ - 2;
        int northBottomMin = northSplitZ + 2;

        int westSplitX = splitCoordinate(layout.roomMinX1, layout.roomMaxX1);
        int westLeftMax = westSplitX - 2;
        int westRightMin = westSplitX + 2;

        int eastSplitX = splitCoordinate(layout.roomMinX2, layout.roomMaxX2);
        int eastLeftMax = eastSplitX - 2;
        int eastRightMin = eastSplitX + 2;

        // HUB - Central hub area (lowest priority, catches everything inside)
        zones.add(ZoneDefinition.builder("hub")
            .displayName("Central Hub")
            .bounds(new ZoneBounds(
                layout.minX, layout.maxX,
                layout.originY - 4, layout.originY + WALL_HEIGHT + 16,
                layout.minZ, layout.maxZ))
            .spawnOffset(new BlockPos(0, 0, 0))
            .color(COLOR_HUB)
            .cueSound(Objects.requireNonNull(SoundEvents.NOTE_BLOCK_CHIME.value()))
            .hintMessage("Hub: workflow nodes, tools, /devmod nexus hub.")
            .allowBuilding(false)
            .isSpecial(false)
            .hasTeleport(true)
            .priority(0)
            .build());

        // COMBAT - Northwest top
        zones.add(ZoneDefinition.builder("combat")
            .displayName("Combat Test")
            .bounds(new ZoneBounds(
                layout.roomMinX1, layout.roomMaxX1,
                layout.originY - 4, layout.originY + WALL_HEIGHT + 16,
                layout.roomMinZ1, northTopMax))
            .spawnOffset(new BlockPos(-64, 0, -81))
            .color(COLOR_COMBAT)
            .cueSound(Objects.requireNonNull(SoundEvents.NOTE_BLOCK_PLING.value()))
            .hintMessage("Combat zone: range + melee lanes, dummies, target wall.")
            .allowBuilding(false)
            .isSpecial(false)
            .hasTeleport(true)
            .portalOffset(new BlockPos(-12, 1, -12))
            .portalColor(PortalColor.RED)
            .priority(10)
            .build());

        // ARENA - Northwest bottom
        zones.add(ZoneDefinition.builder("arena")
            .displayName("Arena Test")
            .bounds(new ZoneBounds(
                layout.roomMinX1, layout.roomMaxX1,
                layout.originY - 4, layout.originY + WALL_HEIGHT + 16,
                northBottomMin, layout.roomMaxZ1))
            .spawnOffset(new BlockPos(-64, 0, -47))
            .color(COLOR_ARENA)
            .cueSound(Objects.requireNonNull(SoundEvents.NOTE_BLOCK_BELL.value()))
            .hintMessage("Arena zone: staging, briefing wall, wave bowl.")
            .allowBuilding(false)
            .isSpecial(false)
            .hasTeleport(true)
            .portalOffset(new BlockPos(-12, 1, 0))
            .portalColor(PortalColor.ORANGE)
            .priority(10)
            .build());

        // UI - Northeast top
        zones.add(ZoneDefinition.builder("ui")
            .displayName("UI Lab")
            .bounds(new ZoneBounds(
                layout.roomMinX2, layout.roomMaxX2,
                layout.originY - 4, layout.originY + WALL_HEIGHT + 16,
                layout.roomMinZ1, northTopMax))
            .spawnOffset(new BlockPos(63, 0, -81))
            .color(COLOR_UI)
            .cueSound(Objects.requireNonNull(SoundEvents.NOTE_BLOCK_BIT.value()))
            .hintMessage("UI lab: wall screens, console pods, photo bay.")
            .allowBuilding(false)
            .isSpecial(false)
            .hasTeleport(true)
            .portalOffset(new BlockPos(12, 1, -12))
            .portalColor(PortalColor.LIGHT_BLUE)
            .priority(10)
            .build());

        // TELEMETRY - Northeast bottom (contains QUIET sub-zone)
        zones.add(ZoneDefinition.builder("telemetry")
            .displayName("Telemetry Lab")
            .bounds(new ZoneBounds(
                layout.roomMinX2, layout.roomMaxX2,
                layout.originY - 4, layout.originY + WALL_HEIGHT + 16,
                northBottomMin, layout.roomMaxZ1))
            .spawnOffset(new BlockPos(63, 0, -47))
            .color(COLOR_TELEMETRY)
            .cueSound(Objects.requireNonNull(SoundEvents.NOTE_BLOCK_XYLOPHONE.value()))
            .hintMessage("Telemetry lab: heatmap, live dashboard, log desk.")
            .allowBuilding(false)
            .isSpecial(false)
            .hasTeleport(true)
            .portalOffset(new BlockPos(12, 1, 0))
            .portalColor(PortalColor.LIME)
            .priority(10)
            .build());

        // QUIET - Special zone inside Telemetry
        int quietMinX = layout.roomMaxX2 - 8;
        int quietMinZ = layout.roomMaxZ1 - 8;
        zones.add(ZoneDefinition.builder("quiet")
            .displayName("Quiet Bay")
            .bounds(new ZoneBounds(
                quietMinX, quietMinX + 4,
                layout.originY - 4, layout.originY + WALL_HEIGHT + 16,
                quietMinZ, quietMinZ + 4))
            .spawnOffset(null)
            .color(COLOR_SPECIAL)
            .cueSound(Objects.requireNonNull(SoundEvents.NOTE_BLOCK_HAT.value()))
            .hintMessage("Quiet bay: isolated testing booth.")
            .allowBuilding(false)
            .isSpecial(true)
            .hasTeleport(false)
            .priority(20) // Higher priority than telemetry
            .build());

        // SHOWCASE - Southwest left
        zones.add(ZoneDefinition.builder("showcase")
            .displayName("Showcase")
            .bounds(new ZoneBounds(
                layout.roomMinX1, westLeftMax,
                layout.originY - 4, layout.originY + WALL_HEIGHT + 16,
                layout.roomMinZ2, layout.roomMaxZ2))
            .spawnOffset(new BlockPos(-81, 0, 63))
            .color(COLOR_SHOWCASE)
            .cueSound(Objects.requireNonNull(SoundEvents.NOTE_BLOCK_HARP.value()))
            .hintMessage("Showcase: test pads, XL cases, item plinths.")
            .allowBuilding(false)
            .isSpecial(false)
            .hasTeleport(true)
            .portalOffset(new BlockPos(-12, 1, 12))
            .portalColor(PortalColor.MAGENTA)
            .priority(10)
            .build());

        // INTEGRATION - Southwest right
        zones.add(ZoneDefinition.builder("integration")
            .displayName("Integration Bay")
            .bounds(new ZoneBounds(
                westRightMin, layout.roomMaxX1,
                layout.originY - 4, layout.originY + WALL_HEIGHT + 16,
                layout.roomMinZ2, layout.roomMaxZ2))
            .spawnOffset(new BlockPos(-47, 0, 63))
            .color(COLOR_INTEGRATION)
            .cueSound(Objects.requireNonNull(SoundEvents.NOTE_BLOCK_BASS.value()))
            .hintMessage("Integration: pods show active mods (gray = missing).")
            .allowBuilding(false)
            .isSpecial(false)
            .hasTeleport(true)
            .portalOffset(new BlockPos(0, 1, 12))
            .portalColor(PortalColor.PURPLE)
            .priority(10)
            .build());

        // SANDBOX - Southeast left (contains PERFORMANCE sub-zone)
        zones.add(ZoneDefinition.builder("sandbox")
            .displayName("Sandbox")
            .bounds(new ZoneBounds(
                layout.roomMinX2, eastLeftMax,
                layout.originY - 4, layout.originY + WALL_HEIGHT + 16,
                layout.roomMinZ2, layout.roomMaxZ2))
            .spawnOffset(new BlockPos(46, 0, 63))
            .color(COLOR_SANDBOX)
            .cueSound(Objects.requireNonNull(SoundEvents.NOTE_BLOCK_COW_BELL.value()))
            .hintMessage("Sandbox: build area + performance bay.")
            .allowBuilding(true) // Only zone that allows building
            .isSpecial(false)
            .hasTeleport(true)
            .portalOffset(new BlockPos(6, 1, 12))
            .portalColor(PortalColor.CYAN)
            .priority(10)
            .build());

        // PERFORMANCE - Special zone inside Sandbox
        int perfMinX = eastLeftMax - 10;
        int perfMinZ = layout.roomMinZ2 + 4;
        zones.add(ZoneDefinition.builder("performance")
            .displayName("Performance Bay")
            .bounds(new ZoneBounds(
                perfMinX, perfMinX + 6,
                layout.originY - 4, layout.originY + WALL_HEIGHT + 16,
                perfMinZ, perfMinZ + 6))
            .spawnOffset(null)
            .color(COLOR_SPECIAL)
            .cueSound(Objects.requireNonNull(SoundEvents.NOTE_BLOCK_SNARE.value()))
            .hintMessage("Performance bay: stress/lag tests.")
            .allowBuilding(true)
            .isSpecial(true)
            .hasTeleport(false)
            .priority(20) // Higher priority than sandbox
            .build());

        // MECHANICS - Southeast right
        zones.add(ZoneDefinition.builder("mechanics")
            .displayName("Mechanics")
            .bounds(new ZoneBounds(
                eastRightMin, layout.roomMaxX2,
                layout.originY - 4, layout.originY + WALL_HEIGHT + 16,
                layout.roomMinZ2, layout.roomMaxZ2))
            .spawnOffset(new BlockPos(80, 0, 63))
            .color(COLOR_MECHANICS)
            .cueSound(Objects.requireNonNull(SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value()))
            .hintMessage("Mechanics: redstone rigs and extended benches.")
            .allowBuilding(false)
            .isSpecial(false)
            .hasTeleport(true)
            .portalOffset(new BlockPos(12, 1, 12))
            .portalColor(PortalColor.YELLOW)
            .priority(10)
            .build());

        // OVERVIEW - Elevated deck
        int deckCenterX = layout.originX + 18;
        int deckCenterZ = layout.originZ;
        int deckY = layout.originY + 20;
        int deckHalf = 7;
        zones.add(ZoneDefinition.builder("overview")
            .displayName("Overview Deck")
            .bounds(new ZoneBounds(
                deckCenterX - deckHalf, deckCenterX + deckHalf,
                deckY, deckY + 2,
                deckCenterZ - deckHalf, deckCenterZ + deckHalf))
            .spawnOffset(new BlockPos(18, 20, 0))
            .color(COLOR_OVERVIEW)
            .cueSound(Objects.requireNonNull(SoundEvents.NOTE_BLOCK_CHIME.value()))
            .hintMessage("Overview deck: full layout scan.")
            .allowBuilding(false)
            .isSpecial(false)
            .hasTeleport(true)
            .priority(30) // High priority (elevated area)
            .build());

        // AFK - Special alcove in center
        int afkMinX = layout.centerMinX + 2;
        int afkMinZ = layout.centerMinZ + 10;
        zones.add(ZoneDefinition.builder("afk")
            .displayName("AFK Alcove")
            .bounds(new ZoneBounds(
                afkMinX, afkMinX + 4,
                layout.originY - 4, layout.originY + WALL_HEIGHT + 16,
                afkMinZ, afkMinZ + 4))
            .spawnOffset(null)
            .color(COLOR_SPECIAL)
            .cueSound(Objects.requireNonNull(SoundEvents.NOTE_BLOCK_CHIME.value()))
            .hintMessage("AFK alcove: safe idle spot.")
            .allowBuilding(false)
            .isSpecial(true)
            .hasTeleport(false)
            .priority(25)
            .build());

        return zones;
    }

    /**
     * Gets the default alias mappings.
     *
     * @return Map of alias to zone ID
     */
    @Nonnull
    public static Map<String, String> getDefaultAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();

        // Hub aliases
        aliases.put("home", "hub");
        aliases.put("spawn", "hub");

        // Overview aliases
        aliases.put("view", "overview");
        aliases.put("deck", "overview");

        // UI aliases
        aliases.put("interface", "ui");

        // Telemetry aliases
        aliases.put("data", "telemetry");

        // Showcase aliases
        aliases.put("items", "showcase");

        // Integration aliases
        aliases.put("mods", "integration");

        // Mechanics aliases
        aliases.put("redstone", "mechanics");

        return aliases;
    }

    // ========================================================================
    // Layout Helper
    // ========================================================================

    private static int splitCoordinate(int min, int max) {
        return min + (max - min) / 2;
    }

    /**
     * Layout calculation helper (mirrors NexusZoneLayout.Layout).
     */
    private static final class Layout {
        final int originX;
        final int originY;
        final int originZ;
        final int minX;
        final int maxX;
        final int minZ;
        final int maxZ;
        final int centerMinX;
        final int centerMaxX;
        final int centerMinZ;
        final int centerMaxZ;
        final int roomMinX1;
        final int roomMaxX1;
        final int roomMinX2;
        final int roomMaxX2;
        final int roomMinZ1;
        final int roomMaxZ1;
        final int roomMinZ2;
        final int roomMaxZ2;

        Layout(BlockPos origin) {
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
    }
}
