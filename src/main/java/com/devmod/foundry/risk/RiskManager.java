package com.devmod.foundry.risk;

import java.util.List;
import java.util.Random;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import com.devmod.foundry.thermal.ThermalManager;

/**
 * Manages risk assessment and incident handling for foundry operations.
 * Uses normalized risk factors (0.0-1.0) with configurable weights for balanced evaluation.
 */
public class RiskManager {
    private static final String TAG_ACCUMULATED_RISK = "AccumulatedRisk";
    private static final String TAG_LAST_INCIDENT = "LastIncidentTick";
    private static final String TAG_INCIDENTS_COUNT = "IncidentsCount";

    // Risk factor weights (must sum to 1.0 for proper normalization)
    private static final float WEIGHT_TEMPERATURE = 0.30f;
    private static final float WEIGHT_TANK_FILL = 0.20f;
    private static final float WEIGHT_THERMAL_STRESS = 0.30f;
    private static final float WEIGHT_STRUCTURE_DAMAGE = 0.15f;
    private static final float WEIGHT_ACCUMULATED = 0.05f;

    // Thresholds for risk level determination (normalized 0.0-1.0)
    private static final float THRESHOLD_ELEVATED = 0.25f;
    private static final float THRESHOLD_HIGH = 0.50f;
    private static final float THRESHOLD_CRITICAL = 0.75f;

    private final Random random = new Random();

    private int accumulatedRisk = 0;
    private long lastIncidentTick = 0;
    private int totalIncidents = 0;
    private RiskLevel currentLevel = RiskLevel.SAFE;

    /**
     * Evaluates current risk level based on foundry state using normalized factors.
     * Each factor contributes a value between 0.0-1.0, weighted and combined.
     *
     * @param currentTemp      Current operating temperature
     * @param requiredTemp     Minimum required temperature
     * @param tankFillPercent  How full the tank is (0.0 - 1.0)
     * @param thermalManager   The thermal manager for stress info
     * @param structureDamage  Current structure damage (0 = none)
     * @return The calculated risk level
     */
    public RiskLevel evaluate(
            float currentTemp,
            float requiredTemp,
            float tankFillPercent,
            ThermalManager thermalManager,
            int structureDamage
    ) {
        // Factor 1: Temperature risk (0.0 - 1.0)
        // Starts at 0 when at/below required temp, scales up as temp exceeds requirement
        float tempRiskFactor = 0.0f;
        if (requiredTemp > 0 && currentTemp > requiredTemp) {
            float tempExcess = (currentTemp - requiredTemp) / requiredTemp;
            tempRiskFactor = Math.min(1.0f, tempExcess / 0.6f); // Full risk at 160% of required
        }

        // Factor 2: Tank fill risk (0.0 - 1.0)
        // Gradual increase starting at 80% fill, full risk at 100%
        float tankRiskFactor = 0.0f;
        if (tankFillPercent > 0.8f) {
            tankRiskFactor = (tankFillPercent - 0.8f) / 0.2f;
        }

        // Factor 3: Thermal stress risk (0.0 - 1.0)
        // Direct mapping from stress percentage
        float stressRiskFactor = thermalManager.getStressPercent();

        // Factor 4: Structure damage risk (0.0 - 1.0)
        // Binary with some scaling for multiple damages
        float damageRiskFactor = Math.min(1.0f, structureDamage / 3.0f);

        // Factor 5: Accumulated risk from near-misses (0.0 - 1.0)
        float accumulatedFactor = Math.min(1.0f, accumulatedRisk / 200.0f);

        // Calculate weighted normalized risk score
        float normalizedRiskScore =
            (tempRiskFactor * WEIGHT_TEMPERATURE) +
            (tankRiskFactor * WEIGHT_TANK_FILL) +
            (stressRiskFactor * WEIGHT_THERMAL_STRESS) +
            (damageRiskFactor * WEIGHT_STRUCTURE_DAMAGE) +
            (accumulatedFactor * WEIGHT_ACCUMULATED);

        // Determine risk level from normalized score
        if (normalizedRiskScore >= THRESHOLD_CRITICAL) {
            currentLevel = RiskLevel.CRITICAL;
        } else if (normalizedRiskScore >= THRESHOLD_HIGH) {
            currentLevel = RiskLevel.HIGH;
        } else if (normalizedRiskScore >= THRESHOLD_ELEVATED) {
            currentLevel = RiskLevel.ELEVATED;
        } else {
            currentLevel = RiskLevel.SAFE;
        }

        return currentLevel;
    }

    /**
     * Rolls for an incident based on current risk level.
     *
     * @param level    The level to access for world tick
     * @param blockPos Position for effects
     * @return The incident that occurred, or null if none
     */
    @Nullable
    public IncidentType rollForIncident(Level level, BlockPos blockPos) {
        if (currentLevel == RiskLevel.SAFE) {
            return null;
        }

        // Cooldown between incidents
        long currentTick = level.getGameTime();
        if (currentTick - lastIncidentTick < 200) { // 10 second cooldown
            return null;
        }

        float roll = random.nextFloat();
        if (roll < currentLevel.getIncidentChance()) {
            IncidentType incident = IncidentType.getRandomForRisk(currentLevel, random);
            if (incident != null) {
                lastIncidentTick = currentTick;
                totalIncidents++;
                accumulatedRisk = Math.max(0, accumulatedRisk - 50); // Risk decreases after incident

                // Play warning sound
                level.playSound(null, blockPos, SoundEvents.FIRE_EXTINGUISH,
                        SoundSource.BLOCKS, 1.0f, 0.5f + random.nextFloat() * 0.5f);

                return incident;
            }
        } else {
            // Near miss - accumulate risk
            if (currentLevel.getLevel() >= RiskLevel.HIGH.getLevel()) {
                accumulatedRisk += 10;
            }
        }

        return null;
    }

    /**
     * Applies the effects of an incident.
     *
     * @param incident         The incident to apply
     * @param level            The world
     * @param controllerPos    Position of controller
     * @param thermalManager   Thermal manager to affect
     * @param onStructureDamage Callback when structure should be damaged
     * @param onFluidLoss      Callback when fluid should be lost (amount in mb)
     * @param onPurityLoss     Callback when purity should be reduced (0.0 - 0.3)
     */
    public void applyIncident(
            IncidentType incident,
            ServerLevel level,
            BlockPos controllerPos,
            ThermalManager thermalManager,
            Runnable onStructureDamage,
            java.util.function.IntConsumer onFluidLoss,
            java.util.function.DoubleConsumer onPurityLoss
    ) {
        switch (incident) {
            case SPLATTER -> {
                // Damage nearby players
                AABB area = new AABB(controllerPos).inflate(3.0);
                List<Player> players = level.getEntitiesOfClass(Player.class, area);
                for (Player player : players) {
                    player.hurt(level.damageSources().hotFloor(), 2.0f + random.nextFloat() * 2.0f);
                }
                // Lose some fluid
                onFluidLoss.accept(100 + random.nextInt(200));
            }

            case THERMAL_CRACK -> {
                // Damage structure
                onStructureDamage.run();
                thermalManager.onDamageApplied();
            }

            case OXIDATION_BURST -> {
                // Reduce purity significantly
                onPurityLoss.accept(0.1 + random.nextDouble() * 0.1);
                // Visual effect would go here
            }

            case FUEL_FLARE -> {
                // Area damage
                AABB area = new AABB(controllerPos).inflate(5.0);
                List<Player> players = level.getEntitiesOfClass(Player.class, area);
                for (Player player : players) {
                    player.hurt(level.damageSources().inFire(), 4.0f + random.nextFloat() * 4.0f);
                    player.setRemainingFireTicks(40); // 2 seconds of fire
                }
            }

            case STRUCTURE_FAIL -> {
                // Major structure damage
                for (int i = 0; i < 3; i++) {
                    onStructureDamage.run();
                }
                thermalManager.onDamageApplied();
                // Lose significant fluid
                onFluidLoss.accept(500 + random.nextInt(500));
            }

            case CONTAINMENT_BREACH -> {
                // Everything bad
                onStructureDamage.run();
                onFluidLoss.accept(1000 + random.nextInt(1000));
                onPurityLoss.accept(0.2 + random.nextDouble() * 0.1);

                // Major damage to nearby players
                AABB area = new AABB(controllerPos).inflate(4.0);
                List<Player> players = level.getEntitiesOfClass(Player.class, area);
                for (Player player : players) {
                    player.hurt(level.damageSources().hotFloor(), 6.0f + random.nextFloat() * 6.0f);
                    player.setRemainingFireTicks(80);
                }
            }
        }
    }

    /**
     * Gets the efficiency multiplier for current risk level.
     */
    public float getEfficiencyMultiplier() {
        return currentLevel.getEfficiencyMultiplier();
    }

    /**
     * Resets accumulated risk (e.g., after maintenance).
     */
    public void resetRisk() {
        accumulatedRisk = 0;
    }

    // Getters

    public RiskLevel getCurrentLevel() {
        return currentLevel;
    }

    public int getAccumulatedRisk() {
        return accumulatedRisk;
    }

    public int getTotalIncidents() {
        return totalIncidents;
    }

    // Serialization

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_ACCUMULATED_RISK, accumulatedRisk);
        tag.putLong(TAG_LAST_INCIDENT, lastIncidentTick);
        tag.putInt(TAG_INCIDENTS_COUNT, totalIncidents);
        return tag;
    }

    public void load(CompoundTag tag) {
        accumulatedRisk = tag.getInt(TAG_ACCUMULATED_RISK);
        lastIncidentTick = tag.getLong(TAG_LAST_INCIDENT);
        totalIncidents = tag.getInt(TAG_INCIDENTS_COUNT);
    }
}
