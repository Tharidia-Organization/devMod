package com.devmod.telemetry;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

import com.devmod.DevMod;

/**
 * Tracks potion/effect usage as "skills" for telemetry analytics.
 *
 * Skills tracked:
 * - Potion applications (healing, regeneration, strength, etc.)
 * - Harmful effect applications (poison, wither, etc.)
 *
 * A skill "hits" when it successfully affects an entity (damage/heal).
 * A skill "whiffs" when it expires without doing anything meaningful.
 */
@EventBusSubscriber(modid = DevMod.MODID)
public class EffectSkillTracker {

    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!TelemetryService.INSTANCE.getSettings().skillTrackingEnabled()) return;

        MobEffectInstance effect = event.getEffectInstance();
        if (effect == null) return;

        // Track effect application as a skill cast
        String skillId = "effect_" + effect.getEffect().value().getDescriptionId();
        TelemetryService.INSTANCE.logSkillCast(entity, skillId);
    }

    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        // Effect expired naturally - already tracked by skill whiff detection (2s window)
        // No action needed here, TelemetryService.tickSkills() handles whiff detection
    }

    @SubscribeEvent
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!TelemetryService.INSTANCE.getSettings().skillTrackingEnabled()) return;

        MobEffectInstance effect = event.getEffectInstance();
        if (effect == null) return;

        // Effect removed (e.g., milk bucket, death)
        // Mark as "hit" to prevent whiff (it did SOMETHING even if removed early)
        String skillId = "effect_" + effect.getEffect().value().getDescriptionId();
        TelemetryService.INSTANCE.logSkillHit(entity, skillId);
    }
}
