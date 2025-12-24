package com.devmod.telemetry.util;

/**
 * PERFORMANCE: Bit-packed flags for telemetry data compression.
 *
 * Instead of using full booleans (8 bits each) or integers (32 bits),
 * this packs multiple flags into a single integer, reducing memory
 * and I/O overhead by up to 90%.
 *
 * Usage:
 * - Pack: int flags = BitPackedFlags.pack(isSprinting, isSneaking, isSwimming, isFalling);
 * - JSON: "movementFlags": 13  (instead of 4 separate boolean fields)
 * - Unpack: boolean[] values = BitPackedFlags.unpack(flags, 4);
 *
 * Bit layout examples:
 * - Movement: [sprint|sneak|swim|fall|jump|climb|fly|glide] = 8 flags in 1 byte
 * - Combat: [inCombat|blocking|attacking|charging|stunned|invulnerable] = 6 flags
 * - State: [isDead|isCreative|isSpectator|isFlying|isInvisible] = 5 flags
 */
public final class BitPackedFlags {

    private BitPackedFlags() {} // Utility class

    // ============================================
    // MOVEMENT FLAGS (bits 0-7)
    // ============================================
    public static final int FLAG_SPRINTING = 0;
    public static final int FLAG_SNEAKING = 1;
    public static final int FLAG_SWIMMING = 2;
    public static final int FLAG_FALLING = 3;
    public static final int FLAG_JUMPING = 4;
    public static final int FLAG_CLIMBING = 5;
    public static final int FLAG_FLYING = 6;
    public static final int FLAG_GLIDING = 7;

    // ============================================
    // COMBAT FLAGS (bits 0-7)
    // ============================================
    public static final int FLAG_IN_COMBAT = 0;
    public static final int FLAG_BLOCKING = 1;
    public static final int FLAG_ATTACKING = 2;
    public static final int FLAG_CHARGING = 3;
    public static final int FLAG_STUNNED = 4;
    public static final int FLAG_INVULNERABLE = 5;
    public static final int FLAG_ON_FIRE = 6;
    public static final int FLAG_POISONED = 7;

    // ============================================
    // ABILITY FLAGS (bits 0-7)
    // ============================================
    public static final int FLAG_DASH_AVAILABLE = 0;
    public static final int FLAG_DODGE_AVAILABLE = 1;
    public static final int FLAG_STAMINA_FULL = 2;
    public static final int FLAG_STAMINA_EMPTY = 3;
    public static final int FLAG_ABILITY_COOLDOWN = 4;
    public static final int FLAG_IFRAME_ACTIVE = 5;

    // ============================================
    // PACKING METHODS
    // ============================================

    /**
     * Pack up to 8 booleans into a single byte.
     * @param flags Variable number of boolean flags (max 8)
     * @return Packed byte value
     */
    public static int pack(boolean... flags) {
        int result = 0;
        for (int i = 0; i < flags.length && i < 8; i++) {
            if (flags[i]) {
                result |= (1 << i);
            }
        }
        return result;
    }

    /**
     * Pack movement-related flags into a single byte.
     */
    public static int packMovement(boolean sprinting, boolean sneaking, boolean swimming,
                                    boolean falling, boolean jumping, boolean climbing,
                                    boolean flying, boolean gliding) {
        return pack(sprinting, sneaking, swimming, falling, jumping, climbing, flying, gliding);
    }

    /**
     * Pack movement flags (simplified 4-flag version).
     */
    public static int packMovementSimple(boolean sprinting, boolean sneaking,
                                          boolean swimming, boolean falling) {
        return pack(sprinting, sneaking, swimming, falling);
    }

    /**
     * Pack combat-related flags into a single byte.
     */
    public static int packCombat(boolean inCombat, boolean blocking, boolean attacking,
                                  boolean charging, boolean stunned, boolean invulnerable,
                                  boolean onFire, boolean poisoned) {
        return pack(inCombat, blocking, attacking, charging, stunned, invulnerable, onFire, poisoned);
    }

    /**
     * Pack ability-related flags into a single byte.
     */
    public static int packAbility(boolean dashAvailable, boolean dodgeAvailable,
                                   boolean staminaFull, boolean staminaEmpty,
                                   boolean abilityCooldown, boolean iframeActive) {
        return pack(dashAvailable, dodgeAvailable, staminaFull, staminaEmpty,
                   abilityCooldown, iframeActive);
    }

    // ============================================
    // UNPACKING METHODS
    // ============================================

    /**
     * Unpack a byte into individual boolean flags.
     * @param packed The packed byte value
     * @param count Number of flags to extract
     * @return Array of boolean flags
     */
    public static boolean[] unpack(int packed, int count) {
        boolean[] result = new boolean[count];
        for (int i = 0; i < count && i < 8; i++) {
            result[i] = (packed & (1 << i)) != 0;
        }
        return result;
    }

    /**
     * Check if a specific flag is set.
     * @param packed The packed value
     * @param flagIndex The flag index (0-7)
     * @return true if the flag is set
     */
    public static boolean isSet(int packed, int flagIndex) {
        return (packed & (1 << flagIndex)) != 0;
    }

    /**
     * Set a specific flag.
     * @param packed The current packed value
     * @param flagIndex The flag index to set (0-7)
     * @return Updated packed value
     */
    public static int setFlag(int packed, int flagIndex) {
        return packed | (1 << flagIndex);
    }

    /**
     * Clear a specific flag.
     * @param packed The current packed value
     * @param flagIndex The flag index to clear (0-7)
     * @return Updated packed value
     */
    public static int clearFlag(int packed, int flagIndex) {
        return packed & ~(1 << flagIndex);
    }

    // ============================================
    // COMPACT INTEGER ENCODING
    // ============================================

    /**
     * Pack a small integer (0-15) into 4 bits.
     * Useful for directions (4 values), small counts, etc.
     * @param value Value 0-15
     * @return Packed nibble (4 bits)
     */
    public static int packNibble(int value) {
        return value & 0x0F;
    }

    /**
     * Pack two nibbles (4-bit values) into a single byte.
     * @param low Lower nibble (0-15)
     * @param high Upper nibble (0-15)
     * @return Packed byte
     */
    public static int packTwoNibbles(int low, int high) {
        return (low & 0x0F) | ((high & 0x0F) << 4);
    }

    /**
     * Extract lower nibble from packed byte.
     */
    public static int getLowNibble(int packed) {
        return packed & 0x0F;
    }

    /**
     * Extract upper nibble from packed byte.
     */
    public static int getHighNibble(int packed) {
        return (packed >> 4) & 0x0F;
    }

    /**
     * Pack a direction (0-3: NORTH, SOUTH, EAST, WEST) into 2 bits.
     */
    public static int packDirection(int direction) {
        return direction & 0x03;
    }

    /**
     * Pack dodge direction with success flag.
     * Bits: [0-1] direction, [2] success
     * @param direction 0-3 for LEFT, RIGHT, BACK, FORWARD
     * @param success Whether the dodge was successful
     * @return Packed value (0-7)
     */
    public static int packDodgeResult(int direction, boolean success) {
        return (direction & 0x03) | (success ? 0x04 : 0x00);
    }

    // ============================================
    // JSON HELPERS
    // ============================================

    /**
     * Append packed flags as a compact JSON field.
     * Output: "fieldName":value
     */
    public static void appendToJson(StringBuilder sb, String fieldName, int packedFlags) {
        sb.append('"').append(fieldName).append("\":").append(packedFlags);
    }

    /**
     * Create a human-readable debug string for packed movement flags.
     */
    public static String debugMovement(int packed) {
        StringBuilder sb = new StringBuilder("[");
        if (isSet(packed, FLAG_SPRINTING)) sb.append("SPRINT ");
        if (isSet(packed, FLAG_SNEAKING)) sb.append("SNEAK ");
        if (isSet(packed, FLAG_SWIMMING)) sb.append("SWIM ");
        if (isSet(packed, FLAG_FALLING)) sb.append("FALL ");
        if (isSet(packed, FLAG_JUMPING)) sb.append("JUMP ");
        if (isSet(packed, FLAG_CLIMBING)) sb.append("CLIMB ");
        if (isSet(packed, FLAG_FLYING)) sb.append("FLY ");
        if (isSet(packed, FLAG_GLIDING)) sb.append("GLIDE ");
        return sb.toString().trim() + "]";
    }
}
