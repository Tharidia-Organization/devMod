package com.devmod.client.overlay.effekseer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.DevMod;
import com.devmod.client.overlay.effekseer.EffectContext.EffectType;
import com.devmod.client.vfx.effekseer.EffekseerClient;
import com.devmod.client.vfx.effekseer.api.ParticleEmitter;
import com.devmod.config.Config;
/**
 * Orchestrates complex VFX sequences for impacts.
 *
 * <p>The orchestrator handles:
 * <ul>
 *   <li>Effect selection based on context (damage, combo, kill, etc.)</li>
 *   <li>Effect layering (primary + secondary effects)</li>
 *   <li>Directional orientation of effects</li>
 *   <li>Dynamic scaling based on damage and combo</li>
 *   <li>Effect pooling and lifecycle management</li>
 *   <li>Performance-based quality adjustment</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * EffectContext ctx = EffectContext.from(impactData, hitPoint, isKill, comboInfo);
 * EffectOrchestrator.play(ctx);
 * </pre>
 */
@OnlyIn(Dist.CLIENT)
public final class EffectOrchestrator {

    /** Active emitters for lifecycle management. */
    private static final Deque<ActiveEffect> ACTIVE_EFFECTS = new ArrayDeque<>();

    /** Maximum active effects (prevents performance issues). */
    private static final int MAX_ACTIVE_EFFECTS = 20;

    private EffectOrchestrator() {}

    /**
     * Plays appropriate effects for the given context.
     *
     * @param context The impact context
     * @return List of spawned emitters
     */
    public static List<ParticleEmitter> play(@Nonnull EffectContext context) {
        Objects.requireNonNull(context, "context");
        List<ParticleEmitter> emitters = new ArrayList<>();

        EffectPreset preset = getActivePreset();

        // Cleanup expired effects
        cleanupExpiredEffects();

        // Check if we can spawn more effects (respecting both preset and hard cap)
        int maxAllowed = Math.min(preset.getMaxSimultaneousEffects(), MAX_ACTIVE_EFFECTS);
        int available = maxAllowed - ACTIVE_EFFECTS.size();
        if (available <= 0) {
            // Remove oldest effect to make room for new one
            removeOldestEffect();
            available = 1;
        }

        // Play primary effect
        ParticleEmitter primary = playEffect(
            context.getPrimaryEffectType(),
            context,
            preset,
            true
        );
        if (primary != null && primary.exists()) {
            emitters.add(primary);
            available--;
        }

        // Play secondary effects if enabled and room available
        if (preset.enableSecondaryEffects() && available > 0) {
            for (EffectType secondary : context.getSecondaryEffects()) {
                if (available <= 0) break;

                ParticleEmitter secondaryEmitter = playEffect(
                    secondary,
                    context,
                    preset,
                    false
                );
                if (secondaryEmitter != null && secondaryEmitter.exists()) {
                    emitters.add(secondaryEmitter);
                    available--;
                }
            }
        }

        // Special effects for milestones
        if (shouldPlayMilestoneEffect(context)) {
            ParticleEmitter milestone = playMilestoneEffect(context, preset);
            if (milestone != null && milestone.exists()) {
                emitters.add(milestone);
            }
        }

        return emitters;
    }

    /**
     * Plays a single effect with full configuration.
     */
    private static ParticleEmitter playEffect(
        EffectType type,
        EffectContext context,
        EffectPreset preset,
        boolean isPrimary
    ) {
        ResourceLocation effectId = ResourceLocation.fromNamespaceAndPath(
            DevMod.MODID, type.getPath()
        );

        ParticleEmitter emitter = EffekseerClient.play(effectId);
        if (!emitter.exists()) {
            return emitter; // Dummy emitter, effect file doesn't exist
        }

        // Position
        Vec3 pos = context.hitPoint();
        emitter.setPosition((float) pos.x, (float) pos.y, (float) pos.z);

        // Calculate scale
        float baseScale = isPrimary ? 1.0f : 0.7f;
        float intensityMult = context.getIntensityMultiplier();
        float finalScale = preset.calculateFinalScale(baseScale, intensityMult);
        emitter.setScale(finalScale, finalScale, finalScale);

        // Orientation based on hit direction
        Vec3 dir = context.hitDirection();
        if (dir != null && dir.lengthSqr() > 0.01) {
            float[] rotation = calculateRotationFromDirection(dir);
            emitter.setRotation(rotation[0], rotation[1], rotation[2]);
        }

        // Dynamic inputs for shader control
        emitter.setDynamicInput(0, HitWeight.getNormalizedWeight(context.damage())); // Damage intensity
        emitter.setDynamicInput(1, intensityMult);                   // Overall intensity
        emitter.setDynamicInput(2, context.comboInfo().count());     // Combo count
        emitter.setDynamicInput(3, context.isKill() ? 1.0f : 0.0f);  // Is kill

        // Track active effect
        long duration = (long) (getBaseDuration(type) * preset.getDurationMultiplier());
        ACTIVE_EFFECTS.addLast(new ActiveEffect(emitter, System.currentTimeMillis(), duration));

        return emitter;
    }

    /**
     * Calculates rotation angles from a direction vector.
     */
    private static float[] calculateRotationFromDirection(Vec3 direction) {
        Vec3 dir = direction.normalize();

        // Calculate yaw (rotation around Y axis)
        float yaw = (float) Math.atan2(dir.x, dir.z);

        // Calculate pitch (rotation around X axis)
        float horizontalLength = (float) Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        float pitch = (float) Math.atan2(-dir.y, horizontalLength);

        return new float[] { pitch, yaw, 0 };
    }

    /**
     * Gets base duration for an effect type (milliseconds).
     */
    private static long getBaseDuration(EffectType type) {
        return switch (type) {
            case GLANCING -> 120;
            case LIGHT -> 160;
            case MEDIUM -> 200;
            case HEAVY -> 260;
            case CRUSHING -> 320;
            case OBLITERATE -> 420;
            case CRITICAL -> 380;
            case HEADSHOT -> 450;
            case COMBO -> 350;
            case KILL -> 800;
            case FINISHER -> 1200;
            case BACKSTAB -> 450;
            case COMBO_INDICATOR -> 220;
            case WEIGHT_FLASH -> 180;
            case BLOCK -> 250;
        };
    }

    /**
     * Checks if a milestone effect should play.
     */
    private static boolean shouldPlayMilestoneEffect(EffectContext context) {
        int combo = context.comboInfo().count();
        // Milestones at 5, 10, 20, 50, etc.
        return combo == 5 || combo == 10 || combo == 20 ||
               combo == 50 || combo == 100;
    }

    /**
     * Plays a milestone celebration effect.
     */
    private static ParticleEmitter playMilestoneEffect(EffectContext context, EffectPreset preset) {
        ResourceLocation effectId = ResourceLocation.fromNamespaceAndPath(
            DevMod.MODID, "impact/milestone"
        );

        ParticleEmitter emitter = EffekseerClient.play(effectId);
        if (!emitter.exists()) {
            return emitter;
        }

        Vec3 pos = context.hitPoint();
        emitter.setPosition((float) pos.x, (float) pos.y + 0.5f, (float) pos.z);

        // Milestone effects are bigger
        float scale = preset.getScaleMultiplier() * 1.5f;
        emitter.setScale(scale, scale, scale);

        // Combo tier for color/intensity
        int tier = ComboTracker.getComboTier(context.comboInfo().count()).ordinal();
        emitter.setDynamicInput(0, tier);
        emitter.setDynamicInput(1, context.comboInfo().count());

        ACTIVE_EFFECTS.addLast(new ActiveEffect(
            emitter,
            System.currentTimeMillis(),
            (long) (1000 * preset.getDurationMultiplier())
        ));

        return emitter;
    }

    /**
     * Gets the active effect preset from config.
     */
    private static EffectPreset getActivePreset() {
        try {
            int ordinal = Config.IMPACT_VFX_EFFEKSEER_PRESET.get();
            return EffectPreset.fromOrdinal(ordinal);
        } catch (Exception e) {
            return EffectPreset.STANDARD;
        }
    }

    /**
     * Removes expired effects from tracking.
     */
    private static void cleanupExpiredEffects() {
        long now = System.currentTimeMillis();
        ACTIVE_EFFECTS.removeIf(effect -> {
            if (!effect.emitter.exists() || now - effect.startTime > effect.duration) {
                effect.emitter.stop();
                return true;
            }
            return false;
        });
    }

    /**
     * Removes the oldest effect to make room for new ones.
     */
    private static void removeOldestEffect() {
        ActiveEffect oldest = ACTIVE_EFFECTS.pollFirst();
        if (oldest != null) {
            oldest.emitter.stop();
        }
    }

    /**
     * Stops all active effects (e.g., on world change).
     */
    public static void stopAll() {
        for (ActiveEffect effect : ACTIVE_EFFECTS) {
            effect.emitter.stop();
        }
        ACTIVE_EFFECTS.clear();
    }

    /**
     * Gets count of currently active effects.
     */
    public static int getActiveCount() {
        return ACTIVE_EFFECTS.size();
    }

    /**
     * Active effect tracking record.
     */
    private record ActiveEffect(
        ParticleEmitter emitter,
        long startTime,
        long duration
    ) {}
}
