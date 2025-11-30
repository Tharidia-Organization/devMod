package com.frenkvs.devmod;

public class ModConfig {
    // --- VISIBILITÀ GENERALE ---
    public static boolean showOverlay = true;       // Mostra scritte a schermo
    public static boolean showRender = true;        // Mostra sfere semitrasparenti

    // --- NUOVE OPZIONI PER STUCK & PATH ---
    public static boolean enableStuckDebug = false; // Attiva il controllo "Stuck"
    public static boolean showMobPath = false;      // Mostra il percorso del mob con particelle
    public static int stuckThresholdSeconds = 3;    // Secondi prima di considerare il mob bloccato
    public static boolean showStuckChat = false;    // Scrive in chat quando un mob si blocca

    // --- CONTROLLO RENDER PER TIPO DI MOB ---
    public static boolean renderFriendlyAggro = true;    // Mostra raggio aggio mob amichevoli
    public static boolean renderFriendlyAttack = true;   // Mostra raggio attacco mob amichevoli
    public static boolean renderHostileAggro = true;     // Mostra raggio aggio mob ostili
    public static boolean renderHostileAttack = true;    // Mostra raggio attacco mob ostili

    // --- DISTANZA DI RENDER ---
    public static int renderDistanceChunks = 8;  // Distanza di render in chunks (1-10)

    // --- COLORI (In formato ARGB Hex) ---
    // 0xFFFF0000 = Rosso, 0xFFFFFF00 = Giallo, 0xFF00FF00 = Verde, 0xFF00FFFF = Ciano
    public static int followRangeColor = 0xFFFF0000; // Default Rosso

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