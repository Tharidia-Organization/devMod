package com.devmod.debug;

import java.util.Locale;

/**
 * Enum of all available debug features.
 * Mirrors Mojang's internal debug rendering system.
 */
public enum DebugFeature {
    // Entity-related
    ENTITY_PATHING("Entity Pathing", "Shows navigation paths of entities"),
    ENTITY_GOALS("Entity Goals", "Shows AI goals and their priorities"),
    ENTITY_BRAINS("Entity Brains", "Shows brain activities (villagers, etc.)"),

    // World-related
    POI("Points of Interest", "Shows POI blocks (beds, workstations, etc.)"),
    BLOCK_UPDATES("Block Updates", "Shows blocks affected by state changes"),
    STRUCTURE_GENERATIONS("Structures", "Shows bounding boxes of generated structures"),

    // Game mechanics
    RAIDS("Raids", "Highlights raid centers and participants"),
    GAME_EVENTS("Game Events", "Shows game events and listeners (sculk, warden)"),
    BEE_HIVES("Bee Hives", "Shows bee hive information"),
    BEES("Bees", "Shows individual bee information"),

    // Technical
    WATER("Water Levels", "Shows water level of nearby water blocks"),
    HEIGHTMAP("Heightmap", "Displays the heightmap"),
    COLLISION("Collision", "Shows nearby block collisions"),
    LIGHT("Light Levels", "Shows sky light value of blocks"),
    SOLID_FACES("Solid Faces", "Shows solid faces of nearby blocks"),
    CHUNK("Chunk Info", "Shows chunk data (entity ticking, etc.)"),
    SPAWN_CHUNKS("Spawn Chunks", "Shows spawn chunk boundaries");

    private final String displayName;
    private final String description;

    DebugFeature(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getId() {
        return name().toLowerCase(Locale.ROOT);
    }
}
