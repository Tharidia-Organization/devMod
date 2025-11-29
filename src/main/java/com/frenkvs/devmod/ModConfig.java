package com.frenkvs.devmod;

public class ModConfig {
    // VISIBILITÀ
    public static boolean showOverlay = true;       // Mostra scritte a schermo
    public static boolean showRender = true;        // Mostra cerchi/blocchi a terra

    // MODALITÀ DI RENDER
    // true = Illumina i blocchi (Griglia rossa)
    // false = Disegna il cerchio semplice (Linea)
    public static boolean renderAsBlocks = true;

    // COLORI (In formato ARGB Hex)
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
}