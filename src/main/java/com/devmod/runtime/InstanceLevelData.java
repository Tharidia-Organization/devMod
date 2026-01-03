package com.devmod.runtime;

import net.minecraft.world.Difficulty;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;

/**
 * Custom LevelData wrapper for instance dimensions that forces NORMAL difficulty.
 *
 * This is necessary because DerivedLevelData inherits difficulty from the overworld,
 * and if the server is set to PEACEFUL, mobs cannot attack players in instance dimensions.
 *
 * By overriding getDifficulty() to always return NORMAL, we ensure that:
 * - Hostile mobs can spawn and attack players
 * - Mobs don't despawn due to peaceful difficulty checks
 * - Combat mechanics work correctly in endurance quests
 * - Damage is balanced (no 1.5x Hard mode multiplier)
 */
public class InstanceLevelData extends DerivedLevelData {

    private static final Difficulty INSTANCE_DIFFICULTY = Difficulty.NORMAL;

    public InstanceLevelData(WorldData worldData, ServerLevelData overworldData) {
        super(worldData, overworldData);
    }

    /**
     * Always returns NORMAL difficulty for instance dimensions.
     * This overrides the server's global difficulty setting.
     */
    @Override
    public Difficulty getDifficulty() {
        return INSTANCE_DIFFICULTY;
    }

    /**
     * Returns false to indicate difficulty is not locked - but we still force NORMAL.
     * This allows the getDifficulty() override to take effect.
     */
    @Override
    public boolean isDifficultyLocked() {
        return false;
    }
}
