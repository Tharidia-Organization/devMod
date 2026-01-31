package com.devmod.client.overlay;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import com.devmod.client.overlay.vfx.GlyphRenderer;
import com.devmod.client.overlay.vfx.LinesRenderer;
import com.devmod.client.overlay.vfx.SlashRenderer;
import com.devmod.client.overlay.vfx.VortexRenderer;
import com.devmod.client.rendering.shader.VFXShaderRegistry;
import com.devmod.client.ui.overlay.OverlayTheme;
import com.devmod.combat.HitHelper;
import com.devmod.config.Config;
import com.devmod.damage.DamageBreakdown;

import static com.devmod.client.overlay.vfx.ImpactVFXConstants.CORE_DURATION_MS;
import static com.devmod.client.overlay.vfx.ImpactVFXConstants.CORE_FADE_IN_MS;
import static com.devmod.client.overlay.vfx.ImpactVFXConstants.CORE_FADE_OUT_MS;
import static com.devmod.client.overlay.vfx.ImpactVFXConstants.GLYPH_DURATION_MS;
import static com.devmod.client.overlay.vfx.ImpactVFXConstants.GLYPH_FADE_IN_MS;
import static com.devmod.client.overlay.vfx.ImpactVFXConstants.GLYPH_FADE_OUT_MS;
import static com.devmod.client.overlay.vfx.ImpactVFXConstants.LINE_DURATION_MS;
import static com.devmod.client.overlay.vfx.ImpactVFXConstants.LINE_FADE_IN_MS;
import static com.devmod.client.overlay.vfx.ImpactVFXConstants.LINE_FADE_OUT_MS;
import static com.devmod.client.overlay.vfx.ImpactVFXConstants.SLASH_DURATION_MS;
import static com.devmod.client.overlay.vfx.ImpactVFXConstants.TWO_PI;
import static com.devmod.client.overlay.vfx.ImpactVFXConstants.applyIntensity;

/**
 * Facade for Impact VFX rendering.
 * Delegates actual rendering to specialized renderer classes in the vfx package.
 */
public class ImpactVFX {

    private static final List<ImpactEffect> activeEffects = new ArrayList<>();

    /**
     * Adds a new impact effect.
     * Only spawns VFX effects (vortex, slash, lines, glyphs).
     * Panel spawning is handled by ImpactHudController.
     */
    public static void addImpact(Vec3 hitPoint, Vec3 slashDirection, ImpactData data) {
        if (!isVfxEnabled()) {
            return;
        }

        boolean anyEffectEnabled = isVortexEnabled() || isSlashEnabled() || isLinesEnabled() || isGlyphsEnabled();
        if (!anyEffectEnabled) {
            return;
        }

        synchronized (activeEffects) {
            while (activeEffects.size() > 5) {
                activeEffects.remove(0);
            }
            activeEffects.add(new ImpactEffect(Objects.requireNonNull(hitPoint, "hitPoint"),
                slashDirection, data));
        }
    }

    /**
     * Renders all active effects.
     * Called from RenderEvents during AFTER_ENTITIES.
     */
    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos) {
        if (!isVfxEnabled()) {
            synchronized (activeEffects) {
                activeEffects.clear();
            }
            return;
        }

        synchronized (activeEffects) {
            if (activeEffects.isEmpty()) return;

            long now = System.currentTimeMillis();
            for (int i = activeEffects.size() - 1; i >= 0; i--) {
                if (activeEffects.get(i).isExpired(now)) {
                    activeEffects.remove(i);
                }
            }

            Vec3 cam = nnVec(cameraPos, "cameraPos");
            for (ImpactEffect effect : activeEffects) {
                renderEffect(poseStack, bufferSource, cam, effect, now);
            }
        }
    }

    private static void renderEffect(PoseStack poseStack, MultiBufferSource bufferSource,
                                     Vec3 cameraPos, ImpactEffect effect, long now) {
        float intensity = getIntensity();
        float coreAlpha = applyIntensity(effect.getCoreAlpha(now), intensity);
        Vec3 hitPoint = nnVec(effect.hitPoint, "hitPoint");
        Vec3 cam = nnVec(cameraPos, "cameraPos");

        boolean useGPU = VFXShaderRegistry.isImpactShaderReady();

        // 1. Energy Vortex Core
        if (isVortexEnabled() && coreAlpha > 0.01f) {
            poseStack.pushPose();
            Vec3 rel = Objects.requireNonNull(hitPoint.subtract(cam), "relative position");
            poseStack.translate(rel.x, rel.y, rel.z);

            if (useGPU) {
                VortexRenderer.renderGPU(poseStack, bufferSource, coreAlpha,
                    effect.getRotation(now), effect.getPulseScale(now), intensity);
            } else {
                VortexRenderer.render(poseStack, bufferSource, coreAlpha,
                    effect.getRotation(now), effect.getPulseScale(now));
            }

            poseStack.popPose();
        }

        // 2. Slash Animation
        if (isSlashEnabled()) {
            float slashProgress = effect.getSlashProgress(now);
            if (slashProgress >= 0 && slashProgress <= 1.0f) {
                poseStack.pushPose();
                Vec3 rel = Objects.requireNonNull(hitPoint.subtract(cam), "relative position");
                poseStack.translate(rel.x, rel.y, rel.z);

                if (useGPU) {
                    SlashRenderer.renderGPU(poseStack, bufferSource,
                        effect.slashDirection, slashProgress, intensity);
                } else {
                    SlashRenderer.render(poseStack, bufferSource,
                        effect.slashDirection, slashProgress, intensity);
                }

                poseStack.popPose();
            }
        }

        // 3. Connection Lines
        if (isLinesEnabled()) {
            float lineAlpha = applyIntensity(effect.getLineAlpha(now), intensity);
            if (lineAlpha > 0.05f) {
                if (useGPU) {
                    LinesRenderer.renderGPU(poseStack, bufferSource, cameraPos, hitPoint,
                        lineAlpha, effect.getRotation(now), intensity);
                } else {
                    LinesRenderer.render(poseStack, bufferSource, cameraPos, hitPoint,
                        lineAlpha, effect.getRotation(now));
                }
            }
        }

        // 4. Combat Glyphs
        if (isGlyphsEnabled()) {
            float glyphAlpha = applyIntensity(effect.getGlyphAlpha(now), intensity);
            if (glyphAlpha > 0.01f) {
                GlyphRenderer.render(poseStack, bufferSource, cam, hitPoint, glyphAlpha,
                    effect.getDamageScale(), effect.bodyPart, effect.bodyPartColor,
                    effect.isCritical, effect.reduction, effect.getReductionScale());
            }
        }
    }

    /**
     * Clears all effects.
     */
    public static void clear() {
        synchronized (activeEffects) {
            activeEffects.clear();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // IMPACT EFFECT
    // ═══════════════════════════════════════════════════════════════════════════

    private static class ImpactEffect {
        @Nonnull final Vec3 hitPoint;
        @Nonnull final Vec3 slashDirection;
        @Nonnull final HitHelper.BodyPart bodyPart;
        final int bodyPartColor;
        final boolean isCritical;
        final float finalDamage;
        final float reduction;
        final long startTime;

        ImpactEffect(@Nonnull Vec3 hitPoint, Vec3 slashDirection, ImpactData data) {
            this.hitPoint = Objects.requireNonNull(hitPoint, "hitPoint");
            this.slashDirection = slashDirection != null ? slashDirection : new Vec3(1, 0, 0);
            this.startTime = System.currentTimeMillis();
            this.bodyPart = data != null ? data.getBodyPart() : HitHelper.BodyPart.BODY;
            this.bodyPartColor = data != null ? data.getBodyPartColor() : OverlayTheme.Impact.CORE_PRIMARY;
            float damage = 0f;
            float reductionAmount = 0f;
            if (data != null) {
                DamageBreakdown breakdown = data.getBreakdown();
                damage = data.hasActualDamage() ? data.getActualDamageDealt() : breakdown.getFinalDamage();
                reductionAmount = data.getDamageReduction();
            }
            this.finalDamage = damage;
            this.reduction = reductionAmount;
            this.isCritical = data != null && data.getBodyPartMultiplier() > 1.5f;
        }

        boolean isExpired(long now) {
            return now - startTime > CORE_DURATION_MS;
        }

        float getCoreAlpha(long now) {
            long elapsed = now - startTime;
            if (elapsed > CORE_DURATION_MS) return 0;
            if (elapsed < CORE_FADE_IN_MS) {
                return elapsed / (float) CORE_FADE_IN_MS;
            }
            long fadeStart = CORE_DURATION_MS - CORE_FADE_OUT_MS;
            if (elapsed > fadeStart) {
                return 1.0f - (elapsed - fadeStart) / (float) CORE_FADE_OUT_MS;
            }
            return 1.0f;
        }

        float getLineAlpha(long now) {
            long elapsed = now - startTime;
            if (elapsed > LINE_DURATION_MS) return 0;
            if (elapsed < LINE_FADE_IN_MS) {
                return elapsed / (float) LINE_FADE_IN_MS * 0.8f;
            }
            long fadeStart = LINE_DURATION_MS - LINE_FADE_OUT_MS;
            if (elapsed > fadeStart) {
                return 0.8f * (1.0f - (elapsed - fadeStart) / (float) LINE_FADE_OUT_MS);
            }
            return 0.8f;
        }

        float getSlashProgress(long now) {
            long elapsed = now - startTime;
            if (elapsed > SLASH_DURATION_MS) return -1;
            return (float) elapsed / SLASH_DURATION_MS;
        }

        float getRotation(long now) {
            long elapsed = now - startTime;
            return (elapsed * 0.003f) % TWO_PI;
        }

        float getPulseScale(long now) {
            long elapsed = now - startTime;
            double pulse = Math.sin(elapsed * 0.008) * 0.15 + 1.0;
            return (float) pulse;
        }

        float getGlyphAlpha(long now) {
            long elapsed = now - startTime;
            if (elapsed > GLYPH_DURATION_MS) return 0;
            if (elapsed < GLYPH_FADE_IN_MS) {
                return elapsed / (float) GLYPH_FADE_IN_MS;
            }
            long fadeStart = GLYPH_DURATION_MS - GLYPH_FADE_OUT_MS;
            if (elapsed > fadeStart) {
                return 1.0f - (elapsed - fadeStart) / (float) GLYPH_FADE_OUT_MS;
            }
            return 1.0f;
        }

        float getDamageScale() {
            float normalized = Math.min(finalDamage / 20.0f, 1.0f);
            return 0.06f * normalized;
        }

        float getReductionScale() {
            return Math.min(Math.abs(reduction) / Math.max(finalDamage, 1.0f), 1.0f);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONFIG HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private static boolean isVfxEnabled() {
        try {
            return Config.IMPACT_VFX_ENABLED.get();
        } catch (Exception ignored) {
            return true;
        }
    }

    private static boolean isVortexEnabled() {
        try {
            if (isGlyphsExclusive()) return false;
            return Config.IMPACT_VFX_VORTEX_ENABLED.get();
        } catch (Exception ignored) {
            return true;
        }
    }

    private static boolean isSlashEnabled() {
        try {
            if (isGlyphsExclusive()) return false;
            return Config.IMPACT_VFX_SLASH_ENABLED.get();
        } catch (Exception ignored) {
            return true;
        }
    }

    private static boolean isLinesEnabled() {
        try {
            if (isGlyphsExclusive()) return false;
            return Config.IMPACT_VFX_LINES_ENABLED.get();
        } catch (Exception ignored) {
            return true;
        }
    }

    private static boolean isGlyphsEnabled() {
        try {
            return Config.IMPACT_VFX_GLYPHS_ENABLED.get();
        } catch (Exception ignored) {
            return true;
        }
    }

    private static boolean isGlyphsExclusive() {
        try {
            return isGlyphsEnabled() && Config.IMPACT_VFX_GLYPHS_EXCLUSIVE.get();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static float getIntensity() {
        try {
            return (float) (double) Config.IMPACT_VFX_INTENSITY.get();
        } catch (Exception ignored) {
            return 1.0f;
        }
    }

    @Nonnull
    private static Vec3 nnVec(Vec3 vec, String context) {
        return Objects.requireNonNull(vec, context);
    }
}
