package com.devmod.npc;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.Codec;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.StringRepresentable;

/**
 * NPC emotional states that affect visual appearance and can be used in conditions.
 * Each emotion maps to a visual state and optionally spawns particles.
 *
 * <p>Usage:
 * <pre>
 * // Set emotion via action
 * new DialogAction.SetEmotion(NpcEmotion.HAPPY)
 *
 * // Check emotion via condition
 * new DialogCondition.NpcHasEmotion(NpcEmotion.ANGRY)
 * </pre>
 */
/**
 * Uses runtime lookup to avoid storing mutable references in enum constants.
 */
public enum NpcEmotion implements StringRepresentable {

    /**
     * Default neutral state.
     */
    NEUTRAL("neutral", NpcState.IDLE),

    /**
     * Happy/pleased emotion - spawns heart particles.
     */
    HAPPY("happy", NpcState.ACTION),

    /**
     * Sad/disappointed emotion - spawns dripping water particles.
     */
    SAD("sad", NpcState.THINKING),

    /**
     * Angry/upset emotion - spawns angry villager particles.
     */
    ANGRY("angry", NpcState.ERROR),

    /**
     * Surprised/shocked emotion - spawns enchant particles.
     */
    SURPRISED("surprised", NpcState.TALKING),

    /**
     * Scared/fearful emotion - spawns smoke particles.
     */
    SCARED("scared", NpcState.UNAVAILABLE),

    /**
     * Curious/interested emotion - spawns witch particles.
     */
    CURIOUS("curious", NpcState.THINKING),

    /**
     * Excited/energetic emotion - spawns firework particles.
     */
    EXCITED("excited", NpcState.ACTION);

    public static final Codec<NpcEmotion> CODEC = StringRepresentable.fromEnum(NpcEmotion::values);

    private final String name;
    private final NpcState visualState;

    NpcEmotion(String name, NpcState visualState) {
        this.name = Objects.requireNonNull(name);
        this.visualState = Objects.requireNonNull(visualState);
    }

    @Override
    @Nonnull
    public String getSerializedName() {
        return Objects.requireNonNull(name);
    }

    /**
     * Gets the visual state associated with this emotion.
     * Used for glow colors and other visual effects.
     */
    @Nonnull
    public NpcState getVisualState() {
        return Objects.requireNonNull(visualState);
    }

    /**
     * Gets the particle type to spawn for this emotion, if any.
     * Uses runtime lookup to avoid storing mutable references.
     *
     * @return The particle options, or null if no particles
     */
    @Nullable
    public ParticleOptions getParticle() {
        return switch (this) {
            case NEUTRAL -> null;
            case HAPPY -> ParticleTypes.HEART;
            case SAD -> ParticleTypes.FALLING_WATER;
            case ANGRY -> ParticleTypes.ANGRY_VILLAGER;
            case SURPRISED -> ParticleTypes.ENCHANT;
            case SCARED -> ParticleTypes.SMOKE;
            case CURIOUS -> ParticleTypes.WITCH;
            case EXCITED -> ParticleTypes.FIREWORK;
        };
    }

    /**
     * Checks if this emotion has associated particles.
     */
    public boolean hasParticle() {
        return this != NEUTRAL;
    }

    /**
     * Gets the primary color for this emotion (from visual state).
     */
    public int getPrimaryColor() {
        return visualState.getPrimary();
    }

    /**
     * Gets the secondary color for this emotion (from visual state).
     */
    public int getSecondaryColor() {
        return visualState.getSecondary();
    }

    /**
     * Parses an emotion from its name, returning NEUTRAL if not found.
     */
    @Nonnull
    public static NpcEmotion fromName(@Nonnull String name) {
        for (NpcEmotion emotion : values()) {
            if (emotion.name.equalsIgnoreCase(name)) {
                return emotion;
            }
        }
        return NEUTRAL;
    }
}
