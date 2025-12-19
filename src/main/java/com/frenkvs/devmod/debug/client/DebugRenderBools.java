package com.frenkvs.devmod.debug.client;

/**
 * Client-side boolean flags to control which debug renderers are active.
 * These are set when the server sends toggle packets to the client.
 */
public class DebugRenderBools {
    public static boolean ENTITY_PATHING = false;
    public static boolean ENTITY_GOALS = false;
    public static boolean ENTITY_BRAINS = false;
    public static boolean POI = false;
    public static boolean BLOCK_UPDATES = false;
    public static boolean STRUCTURES = false;
    public static boolean RAIDS = false;
    public static boolean GAME_EVENTS = false;
    public static boolean BEES = false;
    public static boolean BEE_HIVES = false;
    public static boolean WATER = false;
    public static boolean HEIGHTMAP = false;
    public static boolean COLLISION = false;
    public static boolean LIGHT = false;
    public static boolean SOLID_FACES = false;
    public static boolean CHUNK = false;
    public static boolean BREEZE = false;

    /**
     * Clear all debug render flags.
     */
    public static void clearAll() {
        ENTITY_PATHING = false;
        ENTITY_GOALS = false;
        ENTITY_BRAINS = false;
        POI = false;
        BLOCK_UPDATES = false;
        STRUCTURES = false;
        RAIDS = false;
        GAME_EVENTS = false;
        BEES = false;
        BEE_HIVES = false;
        WATER = false;
        HEIGHTMAP = false;
        COLLISION = false;
        LIGHT = false;
        SOLID_FACES = false;
        CHUNK = false;
        BREEZE = false;
    }
}
