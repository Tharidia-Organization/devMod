package com.devmod.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.errorprone.annotations.Immutable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

import com.devmod.config.Config;

/**
 * Selects spawn pads in the Nexus hub.
 *
 * @deprecated Use {@link com.devmod.zone.data.ZoneRegistry} and
 * {@link com.devmod.zone.runtime.ZoneResolver#getSpawnPosition} instead.
 * Zone spawn offsets are now defined in {@link com.devmod.zone.data.ZoneDefinition}.
 */
@Deprecated(since = "1.21.1", forRemoval = true)
public final class NexusSpawnManager {
    private static final AtomicInteger ROUND_ROBIN = new AtomicInteger();
    private static final Offset DEFAULT_OFFSET = new Offset(0, 0, 34);
    private static final Offset COMBAT_OFFSET = new Offset(-64, 0, -81);
    private static final Offset ARENA_OFFSET = new Offset(-64, 0, -47);
    private static final Offset UI_OFFSET = new Offset(63, 0, -81);
    private static final Offset TELEMETRY_OFFSET = new Offset(63, 0, -47);
    private static final Offset SHOWCASE_OFFSET = new Offset(-81, 0, 63);
    private static final Offset INTEGRATION_OFFSET = new Offset(-47, 0, 63);
    private static final Offset SANDBOX_OFFSET = new Offset(46, 0, 63);
    private static final Offset MECHANICS_OFFSET = new Offset(80, 0, 63);
    private static final Offset OVERVIEW_OFFSET = new Offset(18, 20, 0);

    @Immutable
    private record Offset(int x, int y, int z) {
        BlockPos toBlockPos() {
            return new BlockPos(x, y, z);
        }
    }

    /**
     * @deprecated Use zone IDs from {@link com.devmod.zone.data.ZoneRegistry} instead.
     */
    @Deprecated(since = "1.21.1", forRemoval = true)
    public enum Zone {
        HUB("hub", "Central Hub", DEFAULT_OFFSET),
        OVERVIEW("overview", "Overview Deck", OVERVIEW_OFFSET),
        COMBAT("combat", "Combat Test", COMBAT_OFFSET),
        ARENA("arena", "Arena Test", ARENA_OFFSET),
        UI("ui", "UI Lab", UI_OFFSET),
        TELEMETRY("telemetry", "Telemetry Lab", TELEMETRY_OFFSET),
        SHOWCASE("showcase", "Showcase", SHOWCASE_OFFSET),
        INTEGRATION("integration", "Integration Bay", INTEGRATION_OFFSET),
        SANDBOX("sandbox", "Sandbox", SANDBOX_OFFSET),
        MECHANICS("mechanics", "Mechanics", MECHANICS_OFFSET);

        private final String id;
        private final String label;
        private final Offset offset;

        Zone(String id, String label, Offset offset) {
            this.id = id;
            this.label = label;
            this.offset = offset;
        }

        public String id() {
            return id;
        }

        public String label() {
            return label;
        }

        public BlockPos offset() {
            return offset.toBlockPos();
        }
    }

    // Holder class to avoid static initialization order issues when Zone enum is loaded
    // before the outer class's static fields are fully initialized.
    private static final class ZoneHolder {
        static final Zone[] ROUTABLE_ZONES = {
            Zone.COMBAT,
            Zone.ARENA,
            Zone.UI,
            Zone.TELEMETRY,
            Zone.SHOWCASE,
            Zone.INTEGRATION,
            Zone.SANDBOX,
            Zone.MECHANICS
        };

        static final Map<String, Zone> ZONE_BY_ID = Map.ofEntries(
            Map.entry("hub", Zone.HUB),
            Map.entry("home", Zone.HUB),
            Map.entry("spawn", Zone.HUB),
            Map.entry("overview", Zone.OVERVIEW),
            Map.entry("view", Zone.OVERVIEW),
            Map.entry("deck", Zone.OVERVIEW),
            Map.entry("combat", Zone.COMBAT),
            Map.entry("arena", Zone.ARENA),
            Map.entry("ui", Zone.UI),
            Map.entry("interface", Zone.UI),
            Map.entry("telemetry", Zone.TELEMETRY),
            Map.entry("data", Zone.TELEMETRY),
            Map.entry("showcase", Zone.SHOWCASE),
            Map.entry("items", Zone.SHOWCASE),
            Map.entry("integration", Zone.INTEGRATION),
            Map.entry("mods", Zone.INTEGRATION),
            Map.entry("sandbox", Zone.SANDBOX),
            Map.entry("mechanics", Zone.MECHANICS),
            Map.entry("redstone", Zone.MECHANICS)
        );
    }

    private NexusSpawnManager() {}

    public static BlockPos getDefaultSpawnOffset() {
        return DEFAULT_OFFSET.toBlockPos();
    }

    public static BlockPos getDefaultSpawn(BlockPos hubOrigin) {
        return hubOrigin.offset(DEFAULT_OFFSET.x(), DEFAULT_OFFSET.y(), DEFAULT_OFFSET.z());
    }

    public static BlockPos getSpawnForZone(BlockPos hubOrigin, Zone zone) {
        if (hubOrigin == null || zone == null) {
            return hubOrigin == null ? BlockPos.ZERO
                : hubOrigin.offset(DEFAULT_OFFSET.x(), DEFAULT_OFFSET.y(), DEFAULT_OFFSET.z());
        }
        return hubOrigin.offset(Objects.requireNonNull(zone.offset()));
    }

    public static BlockPos getSpawnForPlayer(ServerPlayer player, BlockPos hubOrigin) {
        Config.NexusSpawnMode mode = Config.NEXUS_SPAWN_MODE.get();
        BlockPos offset = switch (mode) {
            case BY_TEAM -> resolveTeamOffset(player);
            case ROUND_ROBIN -> nextRoundRobinOffset();
            default -> DEFAULT_OFFSET.toBlockPos();
        };
        return hubOrigin.offset(Objects.requireNonNull(offset));
    }

    private static BlockPos nextRoundRobinOffset() {
        int index = Math.floorMod(ROUND_ROBIN.getAndIncrement(), ZoneHolder.ROUTABLE_ZONES.length);
        return ZoneHolder.ROUTABLE_ZONES[index].offset();
    }

    private static BlockPos resolveTeamOffset(ServerPlayer player) {
        if (player == null) {
            return DEFAULT_OFFSET.toBlockPos();
        }

        Zone tagZone = resolveZoneFromTags(player.getTags());
        if (tagZone != null) {
            return tagZone.offset();
        }

        Zone teamZone = resolveZoneFromTeam(player.getTeam());
        if (teamZone != null) {
            return teamZone.offset();
        }

        return nextRoundRobinOffset();
    }

    private static Zone resolveZoneFromTags(Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        if (tags.contains("nexus_combat")) {
            return Zone.COMBAT;
        }
        if (tags.contains("nexus_arena")) {
            return Zone.ARENA;
        }
        if (tags.contains("nexus_ui")) {
            return Zone.UI;
        }
        if (tags.contains("nexus_telemetry")) {
            return Zone.TELEMETRY;
        }
        if (tags.contains("nexus_showcase")) {
            return Zone.SHOWCASE;
        }
        if (tags.contains("nexus_integration")) {
            return Zone.INTEGRATION;
        }
        if (tags.contains("nexus_sandbox")) {
            return Zone.SANDBOX;
        }
        if (tags.contains("nexus_mechanics")) {
            return Zone.MECHANICS;
        }

        return null;
    }

    private static Zone resolveZoneFromTeam(PlayerTeam team) {
        if (team != null && team.getName() != null) {
            String name = team.getName().toLowerCase(Locale.ROOT);
            if (name.contains("combat")) {
                return Zone.COMBAT;
            }
            if (name.contains("arena")) {
                return Zone.ARENA;
            }
            if (name.contains("ui") || name.contains("interface")) {
                return Zone.UI;
            }
            if (name.contains("telemetry") || name.contains("data")) {
                return Zone.TELEMETRY;
            }
            if (name.contains("showcase") || name.contains("item")) {
                return Zone.SHOWCASE;
            }
            if (name.contains("integration") || name.contains("mod")) {
                return Zone.INTEGRATION;
            }
            if (name.contains("sandbox")) {
                return Zone.SANDBOX;
            }
            if (name.contains("mechanic") || name.contains("system")) {
                return Zone.MECHANICS;
            }
        }

        return null;
    }

    public static Zone resolveZone(String input) {
        if (input == null) {
            return null;
        }
        return ZoneHolder.ZONE_BY_ID.get(input.trim().toLowerCase(Locale.ROOT));
    }

    public static List<String> zoneIds() {
        List<String> ids = new ArrayList<>();
        for (Zone zone : Zone.values()) {
            ids.add(zone.id());
        }
        return ids;
    }

    public static String zoneListLabel() {
        StringBuilder builder = new StringBuilder();
        for (Zone zone : Zone.values()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(zone.id());
        }
        return builder.toString();
    }

}
