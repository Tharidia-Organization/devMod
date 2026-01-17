package com.devmod.area.aesthetic;

/**
 * BIBBIA ESTETICA - REGOLA 8: INTERAZIONE FISICA
 *
 * Pattern di interazione con l'Area Editor Block.
 * Definisce distanze, permessi e feedback per ogni tipo di interazione.
 */
public final class AreaBuilderInteraction {

    private AreaBuilderInteraction() {}

    // ============================================================================
    // INTERACTION PATTERNS
    // ============================================================================

    /*
     * Click destro: apre GUI
     * Shift + Click destro: toggle preview on/off
     * Click sinistro: nessun effetto (blocco indistruttibile senza permessi)
     * Redstone signal: nessun effetto (no automazione)
     */

    // ============================================================================
    // DISTANCES
    // ============================================================================

    /** Maximum distance for block interaction (in blocks) */
    public static final double MAX_INTERACT_DISTANCE = 6.0;

    /** Maximum distance for preview visibility (in blocks) */
    public static final double PREVIEW_VISIBLE_DISTANCE = 64.0;

    /** Maximum distance for particle visibility (in blocks) */
    public static final double PARTICLE_VISIBLE_DISTANCE = 48.0;

    /** Distance for auto-close GUI when player moves away */
    public static final double GUI_AUTO_CLOSE_DISTANCE = 12.0;

    // ============================================================================
    // PERMISSION LEVELS
    // ============================================================================

    /** Permission level to view areas (everyone) */
    public static final int PERMISSION_VIEW = 0;

    /** Permission level to edit areas (OP 2+) */
    public static final int PERMISSION_EDIT = 2;

    /** Permission level to delete areas (OP 4) */
    public static final int PERMISSION_DELETE = 4;

    /** Permission level to manage system areas (OP 4) */
    public static final int PERMISSION_SYSTEM = 4;

    // ============================================================================
    // LIGHT LEVELS
    // ============================================================================

    /** Light level for idle editor block */
    public static final int LIGHT_LEVEL_IDLE = 7;

    /** Light level for active editor block */
    public static final int LIGHT_LEVEL_ACTIVE = 12;

    /** Light level for building editor block */
    public static final int LIGHT_LEVEL_BUILDING = 15;

    /** Light level for error state */
    public static final int LIGHT_LEVEL_ERROR = 4;

    // ============================================================================
    // INTERACTION RESULTS
    // ============================================================================

    /**
     * Result of an interaction attempt.
     */
    public enum InteractionResult {
        /** Interaction successful */
        SUCCESS,

        /** Player too far from block */
        TOO_FAR,

        /** Player lacks permission */
        NO_PERMISSION,

        /** Block is locked/busy */
        LOCKED,

        /** Interaction on cooldown */
        COOLDOWN,

        /** Area overlaps with another */
        OVERLAP,

        /** Area is out of bounds */
        OUT_OF_BOUNDS,

        /** Unknown/generic error */
        ERROR
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    /**
     * Checks if a player is within interaction distance of a position.
     */
    public static boolean isWithinInteractDistance(double playerX, double playerY, double playerZ,
                                                   double blockX, double blockY, double blockZ) {
        double dx = playerX - (blockX + 0.5);
        double dy = playerY - (blockY + 0.5);
        double dz = playerZ - (blockZ + 0.5);
        return (dx * dx + dy * dy + dz * dz) <= MAX_INTERACT_DISTANCE * MAX_INTERACT_DISTANCE;
    }

    /**
     * Checks if a position is within preview visible distance.
     */
    public static boolean isWithinPreviewDistance(double playerX, double playerY, double playerZ,
                                                  double centerX, double centerY, double centerZ) {
        double dx = playerX - centerX;
        double dy = playerY - centerY;
        double dz = playerZ - centerZ;
        return (dx * dx + dy * dy + dz * dz) <= PREVIEW_VISIBLE_DISTANCE * PREVIEW_VISIBLE_DISTANCE;
    }

    /**
     * Checks if a player has the required permission level.
     */
    public static boolean hasPermission(int playerPermissionLevel, int requiredLevel) {
        return playerPermissionLevel >= requiredLevel;
    }

    /**
     * Gets the appropriate light level for an editor state.
     */
    public static int getLightLevelForState(EditorState state) {
        return switch (state) {
            case IDLE, LOCKED -> LIGHT_LEVEL_IDLE;
            case PREVIEW -> LIGHT_LEVEL_ACTIVE;
            case BUILDING, COMPLETE -> LIGHT_LEVEL_BUILDING;
            case ERROR -> LIGHT_LEVEL_ERROR;
        };
    }

    /**
     * Checks if shift-click was used (for preview toggle).
     */
    public static boolean isPreviewToggle(boolean isSneaking) {
        return isSneaking;
    }

    /**
     * Gets the interaction result for a given situation.
     */
    public static InteractionResult checkInteraction(
            boolean withinDistance,
            boolean hasPermission,
            boolean isLocked,
            boolean onCooldown) {

        if (!withinDistance) {
            return InteractionResult.TOO_FAR;
        }
        if (!hasPermission) {
            return InteractionResult.NO_PERMISSION;
        }
        if (isLocked) {
            return InteractionResult.LOCKED;
        }
        if (onCooldown) {
            return InteractionResult.COOLDOWN;
        }
        return InteractionResult.SUCCESS;
    }
}
