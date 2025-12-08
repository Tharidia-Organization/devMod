package com.frenkvs.devmod.config;

import net.minecraft.core.BlockPos; // Importante per il metro!

public class ModConfig {
    public enum SphereRenderMode {
        WIREFRAME,
        FILLED
    }

    // --- VISIBILITÀ GENERALE ---
    public static boolean showOverlay = false;       // Mostra scritte a schermo
    public static boolean showRender = false;        // Mostra sfere semitrasparenti
    public static boolean showArrowHits = true;      // Controlla solo i colpi delle frecce

    // --- NUOVE OPZIONI PER STUCK & PATH ---
    public static boolean enableStuckDebug = false; // Attiva il controllo "Stuck"
    public static boolean showMobPath = false;      // Mostra il percorso del mob con particelle
    public static int stuckThresholdSeconds = 3;    // Secondi prima di considerare il mob bloccato
    public static boolean showStuckChat = false;    // Scrive in chat quando un mob si blocca

    // --- ANCHOR & MARKERS (NUOVO SISTEMA SEPARATO) ---
    public static boolean showAnchors = false;    // Mostra Entità con NAME TAG (ArmorStand, Mob, ecc)
    public static boolean showMarkers = false;    // Mostra entità MARKER (Blocco Verde)

    // --- LINE OF SIGHT (LoS) ---
    public static boolean showLoS = false;
    public static boolean showAllMobsLoS = false;
    public static int losDistance = 16;           // Lunghezza dei raggi laser
    public static int allMobsLoSRadius = 24;

    // --- CONTROLLO RENDER PER TIPO DI MOB ---
    public static boolean renderFriendlyAggro = true;    // Mostra raggio aggio mob amichevoli
    public static boolean renderFriendlyAttack = true;   // Mostra raggio attacco mob amichevoli
    public static boolean renderHostileAggro = true;     // Mostra raggio aggio mob ostili
    public static boolean renderHostileAttack = true;    // Mostra raggio attacco mob ostili

    // --- MODO DI RENDER SPHERE ---
    public static SphereRenderMode sphereRenderMode = SphereRenderMode.WIREFRAME;

    // --- DISTANZA DI RENDER ---
    public static int renderDistanceChunks = 8;  // Distanza di render in chunks (1-10)

    // --- COLORI (In formato ARGB Hex) ---
    public static int followRangeColor = 0xFFFF0000; // Default Rosso

    // ========================================================================
    //    AGGIUNTE PER I TOOL DA BUILDER E FREECAM (Necessarie per compilare)
    // ========================================================================

    // --- FREECAM ---
    public static boolean freeCamEnabled = false;
    public static float fcSpeed = 1.0f;
    // Coordinate (usate se vogliamo salvare la posizione o per compatibilità)
    public static double fcX, fcY, fcZ;
    public static float fcYaw, fcPitch;

    // --- MISURATORE (METRO) ---
    public static boolean enableMeasureTool = true;
    public static BlockPos measurePos1 = null; // Punto A
    public static BlockPos measurePos2 = null; // Punto B
    public static boolean showMeasureLines = true;

    // --- GRIGLIA VERTICALE ---
    public static boolean showVerticalLevels = false;
    public static int gridRadius = 10;
    public static int gridSpacingY = 4;
    public static int gridFloorsUp = 2;
    public static int gridFloorsDown = 1;
    public static boolean gridLockY = false;
    public static double lockedYValue = 0.0;

    // --- GUIDA CERCHIO ---
    public static boolean showCircleGuide = false;
    public static int circleRadius = 5;
    public static boolean circleLockY = false;
    public static int circleY = 64;
    // Coordinate fisse del centro del cerchio
    public static int circleCenterX = 0;
    public static int circleCenterY = 0;
    public static int circleCenterZ = 0;

    // ========================================================================

    // Metodo per ciclare i colori nel menu
    public static void cycleColor() {
        if (followRangeColor == 0xFFFF0000) followRangeColor = 0xFFFFFF00; // Rosso -> Giallo
        else if (followRangeColor == 0xFFFFFF00) followRangeColor = 0xFF00FF00; // Giallo -> Verde
        else if (followRangeColor == 0xFF00FF00) followRangeColor = 0xFF00FFFF; // Verde -> Ciano
        else if (followRangeColor == 0xFF00FFFF) followRangeColor = 0xFF0000FF; // Ciano -> Blu
        else followRangeColor = 0xFFFF0000; // Blu -> Rosso
    }

    public static String getColorName() {
        if (followRangeColor == 0xFFFF0000) return "Rosso";
        if (followRangeColor == 0xFFFFFF00) return "Giallo";
        if (followRangeColor == 0xFF00FF00) return "Verde";
        if (followRangeColor == 0xFF00FFFF) return "Ciano";
        if (followRangeColor == 0xFF0000FF) return "Blu";
        return "Sconosciuto";
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