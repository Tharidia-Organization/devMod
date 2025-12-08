package com.frenkvs.devmod.config;

import net.minecraft.core.BlockPos;

public class ModConfig {
    // --- VISIBILITÀ ---
    public static boolean showOverlay = true;
    public static boolean showRender = true;
    public static boolean showArrowHits = true;

    // --- DEBUG & PATH ---
    public static boolean enableStuckDebug = false;
    public static boolean showMobPath = true;
    public static int stuckThresholdSeconds = 3;
    public static boolean showStuckChat = false;

    // --- ANCHOR & MARKERS ---
    public static boolean showAnchors = false;
    public static boolean showMarkers = false;

    // --- RENDER MOB ---
    public static boolean renderFriendlyAggro = true;
    public static boolean renderFriendlyAttack = true;
    public static boolean renderHostileAggro = true;
    public static boolean renderHostileAttack = true;
    public static int renderDistanceChunks = 8;

    // --- COLORI ---
    public static int followRangeColor = 0xFFFF0000;

    // --- FREE CAMERA ---
    public static boolean freeCamEnabled = false;
    public static double fcX, fcY, fcZ;
    public static float fcYaw, fcPitch;
    public static float fcSpeed = 1.0f;

    // --- MISURATORE ---
    public static boolean enableMeasureTool = true;
    public static BlockPos measurePos1 = null;
    public static BlockPos measurePos2 = null;
    public static boolean showMeasureLines = true;

    // --- GRIGLIA VERTICALE ---
    public static boolean showVerticalLevels = false;
    public static int gridRadius = 10;
    public static int gridSpacingY = 4;
    public static int gridFloorsUp = 2;
    public static int gridFloorsDown = 1;
    public static boolean gridLockY = false;
    public static double lockedYValue = 0.0;

    // --- GUIDA CERCHIO (MODIFICATO) ---
    public static boolean showCircleGuide = false;
    public static int circleRadius = 5;
    // Coordinate fisse del centro del cerchio
    public static int circleCenterX = 0;
    public static int circleCenterY = 0;
    public static int circleCenterZ = 0;

    // --- LINE OF SIGHT ---
    public static boolean showLoS = false;
    public static boolean showAllMobsLoS = false;
    public static int losDistance = 16;
    public static int allMobsLoSRadius = 24;

    // --- METODI ---
    public static void cycleColor() {
        if (followRangeColor == 0xFFFF0000) followRangeColor = 0xFFFFFF00;
        else if (followRangeColor == 0xFFFFFF00) followRangeColor = 0xFF00FF00;
        else if (followRangeColor == 0xFF00FF00) followRangeColor = 0xFF00FFFF;
        else if (followRangeColor == 0xFF00FFFF) followRangeColor = 0xFF0000FF;
        else followRangeColor = 0xFFFF0000;
    }

    public static String getColorTranslationKey() {
        if (followRangeColor == 0xFFFF0000) return "devmod.color.red";
        if (followRangeColor == 0xFFFFFF00) return "devmod.color.yellow";
        if (followRangeColor == 0xFF00FF00) return "devmod.color.green";
        if (followRangeColor == 0xFF00FFFF) return "devmod.color.cyan";
        if (followRangeColor == 0xFF0000FF) return "devmod.color.blue";
        return "devmod.color.unknown";
    }
}