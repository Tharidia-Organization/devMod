package com.devmod.debug.client;

/**
 * Client-side boolean flags to control which debug renderers are active.
 * These are set when the server sends toggle packets to the client.
 */
public class DebugRenderBools {
    private static boolean entityPathing = false;
    private static boolean entityGoals = false;
    private static boolean entityBrains = false;
    private static boolean poi = false;
    private static boolean blockUpdates = false;
    private static boolean structures = false;
    private static boolean raids = false;
    private static boolean gameEvents = false;
    private static boolean bees = false;
    private static boolean beeHives = false;
    private static boolean water = false;
    private static boolean heightmap = false;
    private static boolean collision = false;
    private static boolean light = false;
    private static boolean solidFaces = false;
    private static boolean chunk = false;
    private static boolean breeze = false;
    private static boolean spawnChunks = false;

    public static boolean isEntityPathing() {
        return entityPathing;
    }

    public static void setEntityPathing(boolean value) {
        entityPathing = value;
    }

    public static boolean isEntityGoals() {
        return entityGoals;
    }

    public static void setEntityGoals(boolean value) {
        entityGoals = value;
    }

    public static boolean isEntityBrains() {
        return entityBrains;
    }

    public static void setEntityBrains(boolean value) {
        entityBrains = value;
    }

    public static boolean isPoi() {
        return poi;
    }

    public static void setPoi(boolean value) {
        poi = value;
    }

    public static boolean isBlockUpdates() {
        return blockUpdates;
    }

    public static void setBlockUpdates(boolean value) {
        blockUpdates = value;
    }

    public static boolean isStructures() {
        return structures;
    }

    public static void setStructures(boolean value) {
        structures = value;
    }

    public static boolean isRaids() {
        return raids;
    }

    public static void setRaids(boolean value) {
        raids = value;
    }

    public static boolean isGameEvents() {
        return gameEvents;
    }

    public static void setGameEvents(boolean value) {
        gameEvents = value;
    }

    public static boolean isBees() {
        return bees;
    }

    public static void setBees(boolean value) {
        bees = value;
    }

    public static boolean isBeeHives() {
        return beeHives;
    }

    public static void setBeeHives(boolean value) {
        beeHives = value;
    }

    public static boolean isWater() {
        return water;
    }

    public static void setWater(boolean value) {
        water = value;
    }

    public static boolean isHeightmap() {
        return heightmap;
    }

    public static void setHeightmap(boolean value) {
        heightmap = value;
    }

    public static boolean isCollision() {
        return collision;
    }

    public static void setCollision(boolean value) {
        collision = value;
    }

    public static boolean isLight() {
        return light;
    }

    public static void setLight(boolean value) {
        light = value;
    }

    public static boolean isSolidFaces() {
        return solidFaces;
    }

    public static void setSolidFaces(boolean value) {
        solidFaces = value;
    }

    public static boolean isChunk() {
        return chunk;
    }

    public static void setChunk(boolean value) {
        chunk = value;
    }

    public static boolean isBreeze() {
        return breeze;
    }

    public static void setBreeze(boolean value) {
        breeze = value;
    }

    public static boolean isSpawnChunks() {
        return spawnChunks;
    }

    public static void setSpawnChunks(boolean value) {
        spawnChunks = value;
    }

    /**
     * Clear all debug render flags.
     */
    public static void clearAll() {
        entityPathing = false;
        entityGoals = false;
        entityBrains = false;
        poi = false;
        blockUpdates = false;
        structures = false;
        raids = false;
        gameEvents = false;
        bees = false;
        beeHives = false;
        water = false;
        heightmap = false;
        collision = false;
        light = false;
        solidFaces = false;
        chunk = false;
        breeze = false;
        spawnChunks = false;
    }
}
