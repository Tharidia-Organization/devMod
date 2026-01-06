package com.devmod.runtime.network;

public enum NexusLogType {
    HITS("hits.ndjson", "Hits"),
    HEALS("heals.ndjson", "Heals"),
    DEATHS("deaths.ndjson", "Deaths"),
    SPAWNS("spawns.ndjson", "Spawns"),
    MINIONS("minions.ndjson", "Minions"),
    PHASES("phases.ndjson", "Boss Phases"),
    FIGHTS("fights.ndjson", "Fights"),
    PROJECTILES("projectiles.ndjson", "Projectiles"),
    ROOM_TIME("room_time.ndjson", "Room Time"),
    BACKTRACKS("backtracks.ndjson", "Backtracks"),
    DUNGEON_RUNS("dungeon_runs.ndjson", "Dungeon Runs"),
    DUNGEON_SESSIONS("dungeon_sessions.ndjson", "Dungeon Sessions"),
    PARKOUR_FALLS("parkour_falls.ndjson", "Parkour Falls"),
    PLAYER_QUITS("player_quits.ndjson", "Player Quits"),
    DAMAGE_STATS("damage_stats.ndjson", "Damage Stats"),
    ALERTS("alerts.ndjson", "Alerts"),
    PERFORMANCE("performance.ndjson", "Performance"),
    ACTIONS("actions.ndjson", "Actions"),
    ECONOMY("economy.ndjson", "Economy"),
    PROGRESSION("progression.ndjson", "Progression"),
    ENDURANCE("endurance.ndjson", "Endurance"),
    NETWORK("network.ndjson", "Network"),
    ATTRIBUTES("player_attributes.ndjson", "Attributes"),
    ABILITIES("ability_usage.ndjson", "Abilities"),
    HEATMAP_DEATH("death_heatmap.ndjson", "Heatmap: Death"),
    HEATMAP_MOVEMENT("movement_heatmap.ndjson", "Heatmap: Movement"),
    HEATMAP_CAMPING("camping_heatmap.ndjson", "Heatmap: Camping"),
    HEATMAP_STUCK("stuck_heatmap.ndjson", "Heatmap: Stuck"),
    HEATMAP_AGGRO_DROP("aggro_drop_heatmap.ndjson", "Heatmap: Aggro Drop"),
    HEATMAP_KITING("kiting_heatmap.ndjson", "Heatmap: Kiting"),
    HEATMAP_CHOKE_POINTS("choke_point_heatmap.ndjson", "Heatmap: Choke Points"),
    HEATMAP_PARKOUR_FALL("parkour_fall_heatmap.ndjson", "Heatmap: Parkour Falls"),
    HEATMAP_INVISIBLE_COLLISION("invisible_collision_heatmap.ndjson", "Heatmap: Invisible Collision");

    private final String fileName;
    private final String label;

    NexusLogType(String fileName, String label) {
        this.fileName = fileName;
        this.label = label;
    }

    public String fileName() {
        return fileName;
    }

    public String label() {
        return label;
    }

    public static NexusLogType fromOrdinal(int ordinal) {
        NexusLogType[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return HITS;
        }
        return values[ordinal];
    }
}
