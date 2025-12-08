package com.frenkvs.devmod.event.common;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.ArmorStand;

public class BuilderState {

    // FREECAM
    public static boolean isFreeCamActive = false;
    public static ArmorStand freeCamEntity = null; // L'entità finta che guidiamo

    // STATO METRO (TAPE MEASURE)
    public static BlockPos measureA = null;
    public static BlockPos measureB = null;

    // STATO GUIDA FORME
    public static boolean isShapeActive = false;
    public static BlockPos shapeCenter = null; // Il centro fisso del cerchio
    public static int shapeRadius = 5;
    public static ShapeType currentShape = ShapeType.CIRCLE;

    public enum ShapeType {
        CIRCLE, SQUARE, SPHERE
    }

    // Metodi Reset
    public static void resetMeasure() {
        measureA = null; measureB = null;
    }
}