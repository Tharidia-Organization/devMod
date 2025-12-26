package com.devmod.telemetry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import com.devmod.DevMod;
import com.devmod.telemetry.boss.UnifiedBossDetector;

/**
 * Auto-detects boss phases based on HP thresholds.
 *
 * Bosses are identified as entities with >=100 max HP (configurable).
 * Phases are detected at 75%, 50%, 25% HP thresholds.
 *
 * Example phases:
 * - Phase 1: 100% - 75% HP
 * - Phase 2: 75% - 50% HP
 * - Phase 3: 50% - 25% HP
 * - Phase 4 (Enrage): <25% HP
 */
@EventBusSubscriber(modid = DevMod.MODID)
public class BossPhaseDetector {
    private static final Map<UUID, BossState> bossStates = new HashMap<>();

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOW, receiveCanceled = true)
    public static void onDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        // Check if boss phase detection is enabled
        TelemetrySettings settings = TelemetryService.INSTANCE.getSettings();
        if (!settings.bossPhaseDetectionEnabled()) return;

        // Use unified boss detection for consistency with client-side overlay
        if (!UnifiedBossDetector.INSTANCE.isBoss(entity)) {
            return; // Not a boss
        }

        double maxHp = entity.getMaxHealth();

        UUID bossId = entity.getUUID();
        double hpBefore = entity.getHealth();
        double hpAfter = Math.max(0, hpBefore - event.getAmount());

        BossState state = bossStates.computeIfAbsent(bossId, k -> new BossState());

        // Calculate HP percentages
        double percentBefore = (hpBefore / maxHp) * 100;
        double percentAfter = (hpAfter / maxHp) * 100;

        // Phase transitions (from high to low HP)
        checkPhaseTransition(serverLevel, entity, state, percentBefore, percentAfter, 75.0, "phase_2_aggressive");
        checkPhaseTransition(serverLevel, entity, state, percentBefore, percentAfter, 50.0, "phase_3_dangerous");
        checkPhaseTransition(serverLevel, entity, state, percentBefore, percentAfter, 25.0, "phase_4_enrage");

        // Cleanup on death
        if (hpAfter <= 0) {
            if (state.currentPhase != null) {
                TelemetryService.INSTANCE.logBossPhaseEnd(entity);
            }
            bossStates.remove(bossId);
        }
    }

    private static void checkPhaseTransition(ServerLevel level, LivingEntity boss, BossState state,
                                             double percentBefore, double percentAfter,
                                             double threshold, String newPhase) {
        // Crossed threshold from above to below (e.g., 76% -> 74%)
        if (percentBefore >= threshold && percentAfter < threshold) {
            // End previous phase if any
            if (state.currentPhase != null) {
                TelemetryService.INSTANCE.logBossPhaseEnd(boss);
            }

            // Start new phase
            state.currentPhase = newPhase;
            TelemetryService.INSTANCE.logBossPhaseStart(level, boss, newPhase);
        }
    }

    private static class BossState {
        String currentPhase = null;
    }
}
