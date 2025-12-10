package com.frenkvs.devmod.endurance;

/**
 * States for an Endurance Quest lifecycle.
 */
@SuppressWarnings({"null", "unused"})
public enum EnduranceQuestState {
    /** Quest available but not started */
    AVAILABLE,
    /** Player is in the arena, quest active */
    IN_PROGRESS,
    /** Between waves, checkpoint available */
    WAVE_COMPLETE,
    /** All waves completed successfully */
    COMPLETED,
    /** Player died or abandoned */
    FAILED,
    /** Quest on cooldown after completion/failure */
    COOLDOWN
}
