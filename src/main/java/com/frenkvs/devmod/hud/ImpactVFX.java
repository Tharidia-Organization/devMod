package com.frenkvs.devmod.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Gestisce gli effetti visivi 3D dell'impatto:
 * - Energy Vortex Core (spirale di energia al centro)
 * - Slash Animation (arco che segue il colpo)
 * - Connection Lines (linee dal core al pannello HUD)
 */
public class ImpactVFX {

    // Lista di effetti attivi (thread-safe per evitare ConcurrentModificationException)
    private static final List<ImpactEffect> activeEffects = new CopyOnWriteArrayList<>();

    // Durata effetti
    private static final long CORE_DURATION_MS = 2500;
    private static final long SLASH_DURATION_MS = 600;  // Più lungo per vedere l'animazione
    private static final long LINE_DURATION_MS = 2000;

    // Colori - Electric Blue theme
    private static final int COLOR_CORE_PRIMARY = 0xFF3D5AFE;     // Electric blue
    private static final int COLOR_CORE_SECONDARY = 0xFF00E5FF;   // Cyan
    private static final int COLOR_CORE_GLOW = 0xFF82B1FF;        // Light blue
    private static final int COLOR_SLASH = 0xFF3D5AFE;            // Electric blue
    private static final int COLOR_LINE = 0xFF00E5FF;             // Cyan

    /**
     * Aggiunge un nuovo effetto impatto.
     * Spawna sia gli effetti VFX (vortex, slash, linee) che il pannello 3D.
     */
    public static void addImpact(Vec3 hitPoint, Vec3 slashDirection, ImpactData data) {
        // Rimuovi vecchi effetti se ce ne sono troppi
        while (activeEffects.size() > 5) {
            activeEffects.remove(0);
        }

        activeEffects.add(new ImpactEffect(hitPoint, slashDirection, data));

        // === NUOVO: Spawna anche il pannello 3D ===
        Impact3DPanelManager.INSTANCE.spawnPanelFromImpact(data);
    }

    /**
     * Renderizza tutti gli effetti attivi.
     * Chiamato da RenderEvents durante AFTER_ENTITIES.
     */
    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 cameraPos) {
        if (activeEffects.isEmpty()) return;

        // Rimuovi effetti scaduti
        long now = System.currentTimeMillis();
        activeEffects.removeIf(e -> e.isExpired(now));

        for (ImpactEffect effect : activeEffects) {
            renderEffect(poseStack, bufferSource, cameraPos, effect, now);
        }

        // Flush buffer per assicurare rendering
        if (bufferSource instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch();
        }
    }

    private static void renderEffect(PoseStack poseStack, MultiBufferSource bufferSource,
                                     Vec3 cameraPos, ImpactEffect effect, long now) {
        float coreAlpha = effect.getCoreAlpha(now);

        // 1. Render Energy Vortex Core (spirale di energia)
        if (coreAlpha > 0.01f) {
            poseStack.pushPose();
            Vec3 rel = effect.hitPoint.subtract(cameraPos);
            poseStack.translate(rel.x, rel.y, rel.z);

            renderEnergyVortex(poseStack, bufferSource, coreAlpha, effect.getRotation(now), effect.getPulseScale(now));

            poseStack.popPose();
        }

        // 2. Render Slash Animation (arco che segue il colpo)
        float slashProgress = effect.getSlashProgress(now);
        if (slashProgress >= 0 && slashProgress <= 1.0f) {
            poseStack.pushPose();
            Vec3 rel = effect.hitPoint.subtract(cameraPos);
            poseStack.translate(rel.x, rel.y, rel.z);

            renderSlashTrail(poseStack, bufferSource, effect.slashDirection, slashProgress);

            poseStack.popPose();
        }

        // 3. Render Connection Lines (dal core verso lo schermo/HUD)
        float lineAlpha = effect.getLineAlpha(now);
        if (lineAlpha > 0.05f) {
            renderConnectionLines(poseStack, bufferSource, cameraPos, effect.hitPoint, lineAlpha, effect.getRotation(now));
        }
    }

    /**
     * Renderizza il vortice di energia centrale (spirale rotante).
     */
    private static void renderEnergyVortex(PoseStack poseStack, MultiBufferSource bufferSource,
                                            float alpha, float rotation, float pulseScale) {
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugLineStrip(2.5));

        // Colori
        float r1 = ((COLOR_CORE_PRIMARY >> 16) & 0xFF) / 255.0f;
        float g1 = ((COLOR_CORE_PRIMARY >> 8) & 0xFF) / 255.0f;
        float b1 = (COLOR_CORE_PRIMARY & 0xFF) / 255.0f;

        float r2 = ((COLOR_CORE_SECONDARY >> 16) & 0xFF) / 255.0f;
        float g2 = ((COLOR_CORE_SECONDARY >> 8) & 0xFF) / 255.0f;
        float b2 = (COLOR_CORE_SECONDARY & 0xFF) / 255.0f;

        float r3 = ((COLOR_CORE_GLOW >> 16) & 0xFF) / 255.0f;
        float g3 = ((COLOR_CORE_GLOW >> 8) & 0xFF) / 255.0f;
        float b3 = (COLOR_CORE_GLOW & 0xFF) / 255.0f;

        // === SPIRALE ESTERNA (rotazione oraria) ===
        float baseRadius = 0.25f * pulseScale;
        int spiralSegments = 32;
        float spiralRotations = 2.0f; // Giri della spirale

        for (int i = 0; i <= spiralSegments; i++) {
            float t = (float) i / spiralSegments;
            float angle = rotation + t * spiralRotations * (float) Math.PI * 2;
            float radius = baseRadius * (1.0f - t * 0.6f); // Si restringe verso il centro

            float x = (float) Math.cos(angle) * radius;
            float y = (float) Math.sin(angle) * radius * 0.5f; // Schiacciata verticalmente
            float z = (float) Math.sin(angle) * radius;

            // Colore che sfuma dal primario al secondario
            float colorMix = t;
            float r = r1 * (1 - colorMix) + r2 * colorMix;
            float g = g1 * (1 - colorMix) + g2 * colorMix;
            float b = b1 * (1 - colorMix) + b2 * colorMix;

            consumer.addVertex(matrix, x, y, z)
                .setColor(r, g, b, alpha * (1.0f - t * 0.5f))
                .setNormal(0, 1, 0);
        }

        // === SPIRALE INTERNA (rotazione antioraria) ===
        for (int i = 0; i <= spiralSegments; i++) {
            float t = (float) i / spiralSegments;
            float angle = -rotation * 1.5f + t * spiralRotations * (float) Math.PI * 2;
            float radius = baseRadius * 0.6f * (1.0f - t * 0.5f);

            float x = (float) Math.cos(angle) * radius;
            float y = (float) Math.sin(angle) * radius * 0.3f;
            float z = (float) Math.sin(angle) * radius;

            consumer.addVertex(matrix, x, y, z)
                .setColor(r2, g2, b2, alpha * 0.7f * (1.0f - t * 0.3f))
                .setNormal(0, 1, 0);
        }

        // === ANELLI CONCENTRICI (effetto "onde") ===
        int ringCount = 3;
        for (int ring = 0; ring < ringCount; ring++) {
            float ringRadius = baseRadius * (0.4f + ring * 0.3f);
            float ringAlpha = alpha * (1.0f - ring * 0.25f);
            int ringSegments = 24;

            // Offset rotazione per ogni anello
            float ringRotation = rotation * (1.0f + ring * 0.3f);

            for (int i = 0; i <= ringSegments; i++) {
                float angle = ringRotation + (float) (2 * Math.PI * i / ringSegments);
                float x = (float) Math.cos(angle) * ringRadius;
                float z = (float) Math.sin(angle) * ringRadius;

                // Ondulazione verticale
                float waveY = (float) Math.sin(angle * 3 + rotation * 2) * 0.03f;

                consumer.addVertex(matrix, x, waveY, z)
                    .setColor(r3, g3, b3, ringAlpha * 0.5f)
                    .setNormal(0, 1, 0);
            }
        }

        // === RAGGI DAL CENTRO ===
        int rayCount = 6;
        float rayLength = baseRadius * 1.2f;

        for (int i = 0; i < rayCount; i++) {
            float angle = rotation * 0.5f + (float) (2 * Math.PI * i / rayCount);

            // Punto centrale
            consumer.addVertex(matrix, 0, 0, 0)
                .setColor(r2, g2, b2, alpha)
                .setNormal(0, 1, 0);

            // Punto esterno
            float x = (float) Math.cos(angle) * rayLength;
            float z = (float) Math.sin(angle) * rayLength;
            consumer.addVertex(matrix, x, 0, z)
                .setColor(r2, g2, b2, alpha * 0.2f)
                .setNormal(0, 1, 0);
        }

        // === PUNTO CENTRALE BRILLANTE ===
        float dotSize = 0.02f * pulseScale;
        consumer.addVertex(matrix, -dotSize, 0, 0).setColor(1f, 1f, 1f, alpha).setNormal(1, 0, 0);
        consumer.addVertex(matrix, dotSize, 0, 0).setColor(1f, 1f, 1f, alpha).setNormal(1, 0, 0);
        consumer.addVertex(matrix, 0, -dotSize, 0).setColor(1f, 1f, 1f, alpha).setNormal(0, 1, 0);
        consumer.addVertex(matrix, 0, dotSize, 0).setColor(1f, 1f, 1f, alpha).setNormal(0, 1, 0);
        consumer.addVertex(matrix, 0, 0, -dotSize).setColor(1f, 1f, 1f, alpha).setNormal(0, 0, 1);
        consumer.addVertex(matrix, 0, 0, dotSize).setColor(1f, 1f, 1f, alpha).setNormal(0, 0, 1);
    }

    /**
     * Renderizza lo slash che simula il movimento dell'arma che taglia.
     * L'animazione mostra una "lama" che si muove attraverso il punto di impatto.
     */
    private static void renderSlashTrail(PoseStack poseStack, MultiBufferSource bufferSource,
                                          Vec3 direction, float progress) {
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugLineStrip(5.0));

        float r = ((COLOR_SLASH >> 16) & 0xFF) / 255.0f;
        float g = ((COLOR_SLASH >> 8) & 0xFF) / 255.0f;
        float b = (COLOR_SLASH & 0xFF) / 255.0f;

        // Alpha base che decresce con il progresso
        float baseAlpha = Math.max(0, 1.0f - progress * 0.8f);

        // === SLASH LINE (linea che "taglia" attraverso il punto) ===
        // Direzione perpendicolare allo sguardo (simula il movimento laterale della spada)
        float dirX = (float) direction.x;
        float dirZ = (float) direction.z;

        // Vettore perpendicolare per il taglio orizzontale
        float perpX = -dirZ;
        float perpZ = dirX;
        float perpLen = (float) Math.sqrt(perpX * perpX + perpZ * perpZ);
        if (perpLen > 0.001f) {
            perpX /= perpLen;
            perpZ /= perpLen;
        } else {
            perpX = 1;
            perpZ = 0;
        }

        // Dimensione della linea di taglio
        float slashLength = 0.8f;  // Lunghezza totale del taglio
        float slashHeight = 0.4f;  // Altezza dell'arco del taglio

        // === ANIMAZIONE: La lama si muove da sinistra a destra ===
        // progress 0.0 -> lama a sinistra
        // progress 0.5 -> lama al centro (punto di impatto)
        // progress 1.0 -> lama a destra

        // Posizione corrente della "testa" del taglio
        float bladePos = (progress * 2.0f - 1.0f) * slashLength; // da -slashLength a +slashLength

        // === SCIA DEL TAGLIO (trail che rimane) ===
        int trailSegments = 24;
        float trailStart = -slashLength;
        float trailEnd = Math.min(bladePos, slashLength);

        if (trailEnd > trailStart) {
            for (int i = 0; i <= trailSegments; i++) {
                float t = (float) i / trailSegments;
                float pos = trailStart + (trailEnd - trailStart) * t;

                // Posizione lungo la linea di taglio
                float x = perpX * pos;
                float z = perpZ * pos;

                // Arco verticale (il taglio è curvo, più alto al centro)
                float arcT = (pos + slashLength) / (2 * slashLength); // 0 a 1
                float y = (float) Math.sin(arcT * Math.PI) * slashHeight;

                // Alpha: più forte vicino alla lama, sfuma verso la coda
                float distFromBlade = Math.abs(pos - bladePos);
                float trailAlpha = baseAlpha * Math.max(0, 1.0f - distFromBlade / slashLength);

                // Colore che sfuma dal bianco (lama) al blu (scia)
                float colorFade = Math.min(1, distFromBlade / (slashLength * 0.3f));
                float cr = r + (1 - r) * (1 - colorFade);
                float cg = g + (1 - g) * (1 - colorFade);
                float cb = b + (1 - b) * (1 - colorFade);

                consumer.addVertex(matrix, x, y, z)
                    .setColor(cr, cg, cb, trailAlpha)
                    .setNormal(0, 1, 0);
            }
        }

        // === LAMA (punto più luminoso che si muove) ===
        if (progress < 0.95f && Math.abs(bladePos) <= slashLength) {
            float bladeX = perpX * bladePos;
            float bladeZ = perpZ * bladePos;
            float arcT = (bladePos + slashLength) / (2 * slashLength);
            float bladeY = (float) Math.sin(arcT * Math.PI) * slashHeight;

            // Punto centrale brillante
            float bladeAlpha = baseAlpha * 1.2f;
            float bladeSize = 0.08f;

            // Croce luminosa sulla lama
            consumer.addVertex(matrix, bladeX - bladeSize, bladeY, bladeZ)
                .setColor(1f, 1f, 1f, bladeAlpha).setNormal(1, 0, 0);
            consumer.addVertex(matrix, bladeX + bladeSize, bladeY, bladeZ)
                .setColor(1f, 1f, 1f, bladeAlpha).setNormal(1, 0, 0);
            consumer.addVertex(matrix, bladeX, bladeY - bladeSize, bladeZ)
                .setColor(1f, 1f, 1f, bladeAlpha).setNormal(0, 1, 0);
            consumer.addVertex(matrix, bladeX, bladeY + bladeSize, bladeZ)
                .setColor(1f, 1f, 1f, bladeAlpha).setNormal(0, 1, 0);

            // Linea verticale della lama (effetto "taglio")
            float bladeLengthVert = 0.25f;
            consumer.addVertex(matrix, bladeX, bladeY - bladeLengthVert, bladeZ)
                .setColor(1f, 1f, 1f, bladeAlpha * 0.8f).setNormal(0, 1, 0);
            consumer.addVertex(matrix, bladeX, bladeY + bladeLengthVert, bladeZ)
                .setColor(1f, 1f, 1f, bladeAlpha * 0.8f).setNormal(0, 1, 0);
        }

        // === PARTICELLE DI SCINTILLE ===
        if (progress < 0.7f) {
            int sparkCount = 6;
            for (int i = 0; i < sparkCount; i++) {
                // Posizione casuale lungo la scia (basata su indice)
                float sparkT = (float) i / sparkCount;
                float sparkPos = trailStart + (trailEnd - trailStart) * sparkT;

                // Offset "casuale" deterministico
                float offsetX = (float) Math.sin(i * 7.3f + progress * 10) * 0.1f;
                float offsetY = (float) Math.cos(i * 5.1f + progress * 8) * 0.15f;
                float offsetZ = (float) Math.sin(i * 3.7f + progress * 12) * 0.1f;

                float arcT = (sparkPos + slashLength) / (2 * slashLength);
                float baseY = (float) Math.sin(arcT * Math.PI) * slashHeight;

                float x = perpX * sparkPos + offsetX;
                float y = baseY + offsetY;
                float z = perpZ * sparkPos + offsetZ;

                float sparkAlpha = baseAlpha * (1.0f - progress) * 0.9f;

                // Piccolo punto luminoso
                float size = 0.015f;
                consumer.addVertex(matrix, x - size, y, z).setColor(1f, 1f, 0.8f, sparkAlpha).setNormal(1, 0, 0);
                consumer.addVertex(matrix, x + size, y, z).setColor(1f, 1f, 0.8f, sparkAlpha).setNormal(1, 0, 0);
            }
        }

        // === ARCO ESTERNO (bordo del taglio) ===
        // Linea superiore dell'arco
        int arcSegments = 16;
        float arcOffset = 0.05f; // Offset dal centro

        for (int i = 0; i <= arcSegments; i++) {
            float t = (float) i / arcSegments;
            float pos = -slashLength + 2 * slashLength * t;

            // Solo la parte già "tagliata"
            if (pos > trailEnd) break;

            float x = perpX * pos;
            float z = perpZ * pos;
            float arcT = (pos + slashLength) / (2 * slashLength);
            float y = (float) Math.sin(arcT * Math.PI) * (slashHeight + arcOffset);

            float arcAlpha = baseAlpha * 0.4f;

            consumer.addVertex(matrix, x, y, z)
                .setColor(r, g, b, arcAlpha)
                .setNormal(0, 1, 0);
        }
    }

    /**
     * Renderizza le linee di connessione dal core verso l'HUD.
     */
    private static void renderConnectionLines(PoseStack poseStack, MultiBufferSource bufferSource,
                                               Vec3 cameraPos, Vec3 hitPoint, float alpha, float rotation) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        poseStack.pushPose();
        Vec3 rel = hitPoint.subtract(cameraPos);
        poseStack.translate(rel.x, rel.y, rel.z);

        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugLineStrip(2.0));

        float r = ((COLOR_LINE >> 16) & 0xFF) / 255.0f;
        float g = ((COLOR_LINE >> 8) & 0xFF) / 255.0f;
        float b = (COLOR_LINE & 0xFF) / 255.0f;

        // Direzione verso la camera
        Vec3 toCamera = cameraPos.subtract(hitPoint).normalize();

        // === LINEE PRINCIPALI VERSO LA CAMERA ===
        float lineLength = 1.5f;
        int lineCount = 3;

        for (int i = 0; i < lineCount; i++) {
            // Offset angolare per ogni linea
            float angleOffset = (float) (2 * Math.PI * i / lineCount) + rotation * 0.2f;

            // Punto di partenza (leggermente offset dal centro)
            float startOffset = 0.1f;
            float startX = (float) Math.cos(angleOffset) * startOffset;
            float startZ = (float) Math.sin(angleOffset) * startOffset;

            // La linea si estende verso la camera con leggera curva
            int segments = 8;
            for (int j = 0; j <= segments; j++) {
                float t = (float) j / segments;

                // Interpolazione verso la camera
                float x = startX * (1 - t) + (float) toCamera.x * lineLength * t;
                float y = (float) toCamera.y * lineLength * t;
                float z = startZ * (1 - t) + (float) toCamera.z * lineLength * t;

                // Alpha che diminuisce verso la fine
                float segmentAlpha = alpha * (1.0f - t * 0.7f);

                // Effetto "pulsazione" lungo la linea
                float pulse = (float) Math.sin(t * Math.PI * 2 + rotation * 3) * 0.3f + 0.7f;
                segmentAlpha *= pulse;

                consumer.addVertex(matrix, x, y, z)
                    .setColor(r, g, b, segmentAlpha)
                    .setNormal(0, 1, 0);
            }
        }

        // === LINEE LATERALI (effetto "scanner") ===
        float sideLength = 0.8f;
        float sideOffset = 0.15f;

        // Linea destra
        consumer.addVertex(matrix, sideOffset, 0, 0)
            .setColor(r, g, b, alpha * 0.6f)
            .setNormal(1, 0, 0);
        consumer.addVertex(matrix, sideOffset + sideLength, 0.3f, 0)
            .setColor(r, g, b, alpha * 0.1f)
            .setNormal(1, 0, 0);

        // Linea sinistra
        consumer.addVertex(matrix, -sideOffset, 0, 0)
            .setColor(r, g, b, alpha * 0.6f)
            .setNormal(1, 0, 0);
        consumer.addVertex(matrix, -sideOffset - sideLength, 0.3f, 0)
            .setColor(r, g, b, alpha * 0.1f)
            .setNormal(1, 0, 0);

        // Linea alto
        consumer.addVertex(matrix, 0, sideOffset, 0)
            .setColor(r, g, b, alpha * 0.6f)
            .setNormal(0, 1, 0);
        consumer.addVertex(matrix, 0, sideOffset + sideLength, 0)
            .setColor(r, g, b, alpha * 0.1f)
            .setNormal(0, 1, 0);

        poseStack.popPose();
    }

    /**
     * Pulisce tutti gli effetti.
     */
    public static void clear() {
        activeEffects.clear();
    }

    /**
     * Classe interna per un singolo effetto impatto.
     */
    private static class ImpactEffect {
        final Vec3 hitPoint;
        final Vec3 slashDirection;
        final ImpactData data;
        final long startTime;

        ImpactEffect(Vec3 hitPoint, Vec3 slashDirection, ImpactData data) {
            this.hitPoint = hitPoint;
            this.slashDirection = slashDirection != null ? slashDirection : new Vec3(1, 0, 0);
            this.data = data;
            this.startTime = System.currentTimeMillis();
        }

        boolean isExpired(long now) {
            return now - startTime > CORE_DURATION_MS;
        }

        float getCoreAlpha(long now) {
            long elapsed = now - startTime;
            if (elapsed > CORE_DURATION_MS) return 0;

            // Fade in rapido nei primi 100ms
            if (elapsed < 100) {
                return elapsed / 100.0f;
            }

            // Fade out negli ultimi 600ms
            long fadeStart = CORE_DURATION_MS - 600;
            if (elapsed > fadeStart) {
                return 1.0f - (elapsed - fadeStart) / 600.0f;
            }
            return 1.0f;
        }

        float getLineAlpha(long now) {
            long elapsed = now - startTime;
            if (elapsed > LINE_DURATION_MS) return 0;

            // Fade in
            if (elapsed < 150) {
                return elapsed / 150.0f * 0.8f;
            }

            // Fade out
            long fadeStart = LINE_DURATION_MS - 500;
            if (elapsed > fadeStart) {
                return 0.8f * (1.0f - (elapsed - fadeStart) / 500.0f);
            }
            return 0.8f;
        }

        float getSlashProgress(long now) {
            long elapsed = now - startTime;
            if (elapsed > SLASH_DURATION_MS) return -1; // Slash finito
            return (float) elapsed / SLASH_DURATION_MS;
        }

        float getRotation(long now) {
            long elapsed = now - startTime;
            // Rotazione continua (radianti)
            return (elapsed * 0.003f) % ((float) Math.PI * 2);
        }

        float getPulseScale(long now) {
            long elapsed = now - startTime;
            // Pulsazione sinusoidale più pronunciata
            double pulse = Math.sin(elapsed * 0.008) * 0.15 + 1.0;
            return (float) pulse;
        }
    }
}
