package com.frenkvs.devmod.config;

public class BuilderConfig {
    // --- FREECAM ---
    public static float freeCamSpeed = 1.0f; // Velocità base volo
    public static boolean freezePlayer = true; // Blocca input del player durante la freecam

    // --- RENDER ---
    public static float lineWidth = 3.0f; // Spessore linee guida
    public static boolean xrayMode = false; // Se true, le guide si vedono attraverso i muri

    // --- GUIDE ---
    public static int defaultCircleRadius = 5;
    public static int gridSpacing = 4;
}