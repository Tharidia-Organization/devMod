package com.devmod.transport;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/**
 * Enhancements that can be applied to transport nodes.
 * These correspond to physical Warp Core modules, legacy rune effects, and system flags.
 *
 * <p>Enhancements are divided into categories:
 * <ul>
 *   <li><b>Rune Effects:</b> Legacy portal runes (HASTE, GATE, ENHANCER, etc.)</li>
 *   <li><b>Warp Core Modules:</b> Physical blocks placed near core</li>
 *   <li><b>Temporal:</b> Time-limited behavior (TEMPORAL, SNAPSHOT)</li>
 *   <li><b>Charging:</b> Charging and cooldown modifiers</li>
 *   <li><b>Party:</b> Multi-player coordination</li>
 *   <li><b>Recovery:</b> State save/restore</li>
 *   <li><b>Special:</b> Visual and protection flags</li>
 * </ul>
 */
public enum TransportEnhancement implements StringRepresentable {
    // ============================================================================
    // RUNE EFFECTS (Legacy Portal System)
    // ============================================================================

    /** Instant teleportation with no charging delay. */
    HASTE("haste", Category.RUNE, false, false),

    /** Cross-dimension teleportation capability. */
    GATE("gate", Category.RUNE, false, false),

    /** +100 blocks linking range. */
    ENHANCER("enhancer", Category.RUNE, false, false),

    /** +500 blocks linking range. */
    STRONG_ENHANCER("strong_enhancer", Category.RUNE, false, false),

    /** Unlimited linking range. */
    INFINITY("infinity", Category.RUNE, false, false),

    // ============================================================================
    // WARP CORE MODULES (New Physical Blocks)
    // ============================================================================

    /** Chromatic Lens - Color customization for portal. */
    CHROMATIC("chromatic", Category.MODULE, true, false),

    /** Range Amplifier - +100 blocks range (stackable up to 3). */
    RANGE_AMPLIFIER("range_amplifier", Category.MODULE, false, false),

    /** Flux Capacitor - Instant charging (bypass charge time). */
    FLUX_CAPACITOR("flux_capacitor", Category.MODULE, false, false),

    /** Network Relay - Enables network mode. */
    NETWORK_RELAY("network_relay", Category.MODULE, false, false),

    /** Memory Core - Automatic return point save. */
    MEMORY_CORE("memory_core", Category.MODULE, false, false),

    /** Party Beacon - Party synchronization teleport. */
    PARTY_BEACON("party_beacon", Category.MODULE, false, true),

    /** Frame Segment - Contributes to visual portal frame. */
    FRAME_SEGMENT("frame_segment", Category.MODULE, true, false),

    // ============================================================================
    // TEMPORAL ENHANCEMENTS
    // ============================================================================

    /** Node is temporary with expiration time. */
    TEMPORAL("temporal", Category.TEMPORAL, true, false),

    /** Saves and restores terrain on node creation/expiration. */
    SNAPSHOT("snapshot", Category.TEMPORAL, true, false),

    // ============================================================================
    // CHARGING/COOLDOWN
    // ============================================================================

    /** Teleport requires charging (default for TELEPAD/PORTAL). */
    CHARGED("charged", Category.CHARGING, false, false),

    /** Countdown before teleport (InstanceManager style). */
    COUNTDOWN("countdown", Category.CHARGING, true, false),

    // ============================================================================
    // PARTY/MULTI-PLAYER
    // ============================================================================

    /** Party synchronization (all members teleport together). */
    PARTY_SYNC("party_sync", Category.PARTY, false, true),

    /** Requires arrival confirmation before proceeding. */
    ARRIVAL_CONFIRM("arrival_confirm", Category.PARTY, false, true),

    // ============================================================================
    // RECOVERY
    // ============================================================================

    /** Recovery mode: saves/restores full player state. */
    RECOVERY("recovery", Category.RECOVERY, true, false),

    /** Automatically saves return point when teleporting away. */
    SAVE_RETURN("save_return", Category.RECOVERY, false, false),

    // ============================================================================
    // SPECIAL FLAGS
    // ============================================================================

    /** Silent operation (no particles/sounds). */
    SILENT("silent", Category.SPECIAL, false, false),

    /** Protected from block breaking. */
    PROTECTED("protected", Category.SPECIAL, false, false),

    /** Show BossBar countdown (for temporal nodes). */
    BOSSBAR("bossbar", Category.SPECIAL, true, false),

    /** Legacy mode flag for backward compatibility. */
    LEGACY_MODE("legacy_mode", Category.SPECIAL, false, false),

    /** Entity transport capability (non-players). */
    ENTITY_TRANSPORT("entity_transport", Category.SPECIAL, false, false),

    /** Item transport capability. */
    ITEM_TRANSPORT("item_transport", Category.SPECIAL, false, false);

    /**
     * Categories for grouping enhancements.
     */
    public enum Category {
        RUNE,       // Legacy rune effects
        MODULE,     // Warp Core physical modules
        TEMPORAL,   // Time-based behavior
        CHARGING,   // Charge/cooldown modifiers
        PARTY,      // Multi-player coordination
        RECOVERY,   // State save/restore
        SPECIAL     // Visual/protection flags
    }

    public static final Codec<TransportEnhancement> CODEC = StringRepresentable.fromEnum(TransportEnhancement::values);

    /** Enhancements that correspond to physical module blocks. */
    public static final Set<TransportEnhancement> MODULE_TYPES = EnumSet.of(
        CHROMATIC, RANGE_AMPLIFIER, FLUX_CAPACITOR, NETWORK_RELAY,
        MEMORY_CORE, PARTY_BEACON, FRAME_SEGMENT
    );

    /** Enhancements inherited from legacy rune system. */
    public static final Set<TransportEnhancement> RUNE_TYPES = EnumSet.of(
        HASTE, GATE, ENHANCER, STRONG_ENHANCER, INFINITY
    );

    private final String name;
    private final Category category;
    private final boolean requiresData;
    private final boolean requiresParty;

    TransportEnhancement(String name, Category category, boolean requiresData, boolean requiresParty) {
        this.name = name;
        this.category = category;
        this.requiresData = requiresData;
        this.requiresParty = requiresParty;
    }

    @Override
    @Nonnull
    public String getSerializedName() {
        return Objects.requireNonNull(name);
    }

    /**
     * Returns the category of this enhancement.
     */
    @Nonnull
    public Category getCategory() {
        return Objects.requireNonNull(category);
    }

    /**
     * Returns true if this enhancement requires additional configuration data.
     */
    public boolean requiresData() {
        return requiresData;
    }

    /**
     * Returns true if this enhancement only works with party teleports.
     */
    public boolean requiresParty() {
        return requiresParty;
    }

    /**
     * Returns true if this enhancement is a physical Warp Core module.
     */
    public boolean isModule() {
        return category == Category.MODULE;
    }

    /**
     * Returns true if this enhancement is a legacy rune effect.
     */
    public boolean isRune() {
        return category == Category.RUNE;
    }

    /**
     * Returns true if this enhancement affects charging behavior.
     */
    public boolean affectsCharging() {
        return this == HASTE || this == FLUX_CAPACITOR || this == CHARGED || this == COUNTDOWN;
    }

    /**
     * Returns true if this enhancement affects range.
     */
    public boolean affectsRange() {
        return this == ENHANCER || this == STRONG_ENHANCER || this == INFINITY || this == RANGE_AMPLIFIER;
    }

    /**
     * Returns true if this enhancement enables cross-dimension teleport.
     */
    public boolean enablesCrossDimension() {
        return this == GATE;
    }

    /**
     * Calculates the range bonus provided by this enhancement.
     *
     * @return range bonus in blocks, or 0 if not a range enhancement
     */
    public int getRangeBonus() {
        return switch (this) {
            case ENHANCER, RANGE_AMPLIFIER -> 100;
            case STRONG_ENHANCER -> 500;
            case INFINITY -> Integer.MAX_VALUE;
            default -> 0;
        };
    }

    /**
     * Gets an enhancement by its serialized name.
     *
     * @param name the serialized name
     * @return the enhancement, or null if not found
     */
    @Nullable
    public static TransportEnhancement byName(String name) {
        for (TransportEnhancement enhancement : values()) {
            if (enhancement.name.equals(name)) {
                return enhancement;
            }
        }
        return null;
    }

    /**
     * Gets an enhancement by index.
     */
    @Nonnull
    public static TransportEnhancement byIndex(int index) {
        TransportEnhancement[] values = values();
        if (index < 0 || index >= values.length) {
            return Objects.requireNonNull(CHARGED);
        }
        return Objects.requireNonNull(values[index]);
    }

    /**
     * Returns the index of this enhancement.
     */
    public int getIndex() {
        return ordinal();
    }

    /**
     * Returns all enhancements in the given category.
     */
    @Nonnull
    public static Set<TransportEnhancement> byCategory(Category category) {
        EnumSet<TransportEnhancement> result = EnumSet.noneOf(TransportEnhancement.class);
        for (TransportEnhancement enhancement : values()) {
            if (enhancement.category == category) {
                result.add(enhancement);
            }
        }
        return Objects.requireNonNull(result);
    }
}
