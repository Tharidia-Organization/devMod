package com.frenkvs.devmod.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Gestisce il rendering del pannello Impact Analysis in 3D nel mondo.
 *
 * Invece di usare un framebuffer off-screen (complesso e problematico),
 * renderizza direttamente il contenuto dell'HUD come geometria 3D nel mondo.
 *
 * Questo approccio è più semplice, più performante e si integra meglio
 * con il sistema di rendering di Minecraft 1.21.1.
 */
@SuppressWarnings("null") // Minecraft API methods are not annotated but never return null in practice
public class Impact3DRenderer {

    // Singleton instance
    public static final Impact3DRenderer INSTANCE = new Impact3DRenderer();

    // === Dimensioni pannello (in unità mondo) ===
    private static final float PANEL_SCALE = 0.02f;  // Scala base del pannello (più grande = più leggibile)
    private static final float PANEL_OFFSET_SIDE = 4.5f;  // Offset laterale dal punto di impatto (a destra)
    private static final float PANEL_OFFSET_UP = 1.0f;    // Offset verticale (sopra il punto)

    // === Colori UI (stile immagine di riferimento) ===
    private static final int PANEL_BG = 0xDD1A1A2E;           // Blu scuro 87% opacity
    private static final int PANEL_BORDER = 0xFF3D5AFE;       // Blu elettrico

    private static final int TEXT_TITLE = 0x00FFFF;           // Cyan (senza alpha per font)
    private static final int TEXT_NORMAL = 0xFFFFFF;          // Bianco
    private static final int TEXT_VALUE = 0x00FF00;           // Verde
    private static final int TEXT_FORMULA = 0xFFD700;         // Oro
    private static final int TEXT_MUTED = 0xAAAAAA;           // Grigio

    // === Dimensioni interne (in pixel font, scala 1:1 con font Minecraft) ===
    private static final float PANEL_WIDTH_PX = 320f;   // Larghezza pannello in pixel font
    private static final float PANEL_HEIGHT_PX = 220f;  // Altezza pannello in pixel font
    private static final float PADDING = 6f;
    private static final float LINE_HEIGHT = 11f;
    private static final float SECTION_SPACING = 5f;

    private Impact3DRenderer() {}

    /**
     * Renderizza un pannello 3D nel mondo con i dati dell'impatto.
     * Il pannello fa billboard verso la camera.
     *
     * @param poseStack Stack di trasformazioni
     * @param bufferSource Buffer per il rendering
     * @param cameraPos Posizione della camera
     * @param panelWorldPos Posizione del pannello nel mondo
     * @param hitPoint Punto di impatto originale (per la linea di collegamento)
     * @param data Dati dell'impatto da visualizzare
     * @param alpha Alpha globale per fade in/out
     */
    public void renderPanel(PoseStack poseStack, MultiBufferSource bufferSource,
                            Vec3 cameraPos, Vec3 panelWorldPos, Vec3 hitPoint,
                            ImpactData data, float alpha) {
        if (alpha <= 0.01f) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 1. Renderizza la linea di collegamento dal hit point al pannello
        renderConnectionLine(poseStack, bufferSource, cameraPos, hitPoint, panelWorldPos, alpha);

        // 2. Trasla alla posizione del pannello (relativa alla camera)
        poseStack.pushPose();
        Vec3 relativePos = panelWorldPos.subtract(cameraPos);
        poseStack.translate(relativePos.x, relativePos.y, relativePos.z);

        // 3. Calcola rotazione billboard (guarda verso la camera)
        Vec3 toCamera = cameraPos.subtract(panelWorldPos).normalize();
        float yaw = (float) Math.atan2(toCamera.x, toCamera.z);
        poseStack.mulPose(new Quaternionf().rotationY(yaw));

        // 4. Scala uniforme per il pannello - Y negativo per flip verticale
        poseStack.scale(PANEL_SCALE, -PANEL_SCALE, PANEL_SCALE);

        // 5. Centra il pannello rispetto alla sua larghezza/altezza
        poseStack.translate(-PANEL_WIDTH_PX / 2, -PANEL_HEIGHT_PX / 2, 0);

        // 6. Renderizza il contenuto del pannello
        renderPanelContent(poseStack, bufferSource, data, alpha, mc.font);

        poseStack.popPose();
    }

    /**
     * Renderizza la linea di collegamento cyan dal punto di impatto al pannello.
     * Usa RenderType.lines() per evitare flickering.
     */
    private void renderConnectionLine(PoseStack poseStack, MultiBufferSource bufferSource,
                                       Vec3 cameraPos, Vec3 hitPoint, Vec3 panelPos, float alpha) {
        poseStack.pushPose();

        // Posizione relativa alla camera
        Vec3 hitRel = hitPoint.subtract(cameraPos);
        Vec3 panelRel = panelPos.subtract(cameraPos);

        Matrix4f matrix = poseStack.last().pose();
        // Usa RenderType.lines() standard invece di debugLineStrip per stabilità
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());

        // Colore cyan con alpha
        float r = 0.0f;
        float g = 0.9f;
        float b = 1.0f;
        float a = alpha * 0.9f;

        // Linea dritta e stabile dal hit point al pannello
        // RenderType.lines() richiede coppie di vertici
        consumer.addVertex(matrix, (float)hitRel.x, (float)hitRel.y, (float)hitRel.z)
            .setColor(r, g, b, a)
            .setNormal(poseStack.last(), 0, 1, 0);
        consumer.addVertex(matrix, (float)panelRel.x, (float)panelRel.y, (float)panelRel.z)
            .setColor(r, g, b, a)
            .setNormal(poseStack.last(), 0, 1, 0);

        poseStack.popPose();
    }

    /**
     * Renderizza il contenuto interno del pannello (sfondo, testo, dati).
     */
    private void renderPanelContent(PoseStack poseStack, MultiBufferSource bufferSource,
                                     ImpactData data, float alpha, Font font) {

        // === SFONDO ===
        renderPanelBackground(poseStack, bufferSource, alpha);

        // === CONTENUTO TESTO ===
        // Offset Z per evitare z-fighting con lo sfondo
        poseStack.pushPose();
        poseStack.translate(0, 0, -0.01f);

        float textX = PADDING;
        float textY = PADDING;

        // === TITOLO ===
        renderText3D(poseStack, bufferSource, font,
            "Impact Analysis (Multi-Part & Mod Integrated)",
            textX, textY, applyAlpha(TEXT_TITLE, alpha), alpha);
        textY += LINE_HEIGHT + 2;

        // Linea separatore (renderizzata come quad sottile)
        renderSeparatorLine(poseStack, bufferSource, 4, textY, PANEL_WIDTH_PX - 12, alpha);
        textY += SECTION_SPACING;

        // === PART HIT ===
        String partText = "Part Hit: " + data.bodyPart.name() + " (Modifier: x" +
                          String.format("%.2f", data.bodyPartMultiplier) + ")";
        renderText3D(poseStack, bufferSource, font, partText,
            textX, textY, applyAlpha(TEXT_NORMAL, alpha), alpha);
        textY += LINE_HEIGHT + 2;

        // === SOURCE ===
        renderText3D(poseStack, bufferSource, font,
            "Source: " + data.getFormattedAttackSource(),
            textX, textY, applyAlpha(TEXT_MUTED, alpha), alpha);
        textY += LINE_HEIGHT + SECTION_SPACING;

        // === BREAKDOWN ===
        DamageBreakdown bd = data.breakdown;

        // Base Weapon Dmg
        renderText3D(poseStack, bufferSource, font,
            String.format("Base Weapon Dmg: %.1f", bd.baseWeaponDamage),
            textX, textY, applyAlpha(TEXT_NORMAL, alpha), alpha);
        textY += LINE_HEIGHT;

        // Enchants
        for (DamageBreakdown.EnchantBonus eb : bd.enchantBonuses) {
            if (eb.bonus() > 0) {
                renderText3D(poseStack, bufferSource, font,
                    String.format("Enchant (%s): +%.1f", eb.name(), eb.bonus()),
                    textX, textY, applyAlpha(TEXT_VALUE, alpha), alpha);
                textY += LINE_HEIGHT;
            }
        }

        // Pehkui Size Bonus
        if (bd.pehkuiSizeBonus > 0) {
            renderText3D(poseStack, bufferSource, font,
                String.format("Pehkui Size Bonus (+25%% of Base): +%.1f", bd.pehkuiSizeBonus),
                textX, textY, applyAlpha(TEXT_VALUE, alpha), alpha);
            textY += LINE_HEIGHT;
        }

        textY += SECTION_SPACING;

        // === FORMULA ===
        renderText3D(poseStack, bufferSource, font,
            "Local Part Calc: " + bd.getFormulaString(),
            textX, textY, applyAlpha(TEXT_FORMULA, alpha), alpha);
        textY += LINE_HEIGHT + 2;

        // === DANNO REALE O CALCOLATO ===
        if (data.hasActualDamage()) {
            float actualDmg = data.getActualDamageDealt();
            renderText3D(poseStack, bufferSource, font,
                String.format("ACTUAL DAMAGE: %.1f", actualDmg),
                textX, textY, applyAlpha(0xFF4444, alpha), alpha);
            textY += LINE_HEIGHT + 2;

            String healthText = String.format("HP: %.1f -> %.1f",
                data.getHealthBefore(), data.getHealthAfter());
            renderText3D(poseStack, bufferSource, font, healthText,
                textX, textY, applyAlpha(TEXT_MUTED, alpha), alpha);
            textY += LINE_HEIGHT;

            float reduction = data.getDamageReduction();
            if (Math.abs(reduction) > 0.1f) {
                String reductionText;
                int reductionColor;
                if (reduction > 0) {
                    reductionText = String.format("Armor/Effects reduced: -%.1f", reduction);
                    reductionColor = 0xFF8888;
                } else {
                    reductionText = String.format("Damage amplified: +%.1f", -reduction);
                    reductionColor = 0x88FF88;
                }
                renderText3D(poseStack, bufferSource, font, reductionText,
                    textX, textY, applyAlpha(reductionColor, alpha), alpha);
            }
        } else {
            String finalText = String.format("*Calculated Dmg: %.1f*", bd.finalDamage);
            renderText3D(poseStack, bufferSource, font, finalText,
                textX, textY, applyAlpha(TEXT_VALUE, alpha), alpha);
        }

        // === MOD SPECIFICS (se presenti) ===
        if (data.hasPehkuiModification() || data.isBetterCombatAttack()) {
            textY += LINE_HEIGHT + SECTION_SPACING;

            renderText3D(poseStack, bufferSource, font, "Mod Specifics",
                textX, textY, applyAlpha(TEXT_TITLE, alpha), alpha);
            textY += LINE_HEIGHT + 4;

            if (data.isBetterCombatAttack()) {
                renderText3D(poseStack, bufferSource, font,
                    "Better Combat: Arc Collision Detected",
                    textX, textY, applyAlpha(TEXT_MUTED, alpha), alpha);
                textY += LINE_HEIGHT;
            }

            if (data.hasPehkuiModification()) {
                String scaleText = String.format("Pehkui: Entity Size Modified (Scale %.1f)",
                    data.pehkuiVisualScale != null ? data.pehkuiVisualScale : 1.0f);
                renderText3D(poseStack, bufferSource, font, scaleText,
                    textX, textY, applyAlpha(TEXT_MUTED, alpha), alpha);
            }
        }

        poseStack.popPose();
    }

    /**
     * Renderizza lo sfondo del pannello con bordo.
     * Usa RenderType.gui() per rendering stabile senza flickering.
     */
    private void renderPanelBackground(PoseStack poseStack, MultiBufferSource bufferSource, float alpha) {
        Matrix4f matrix = poseStack.last().pose();

        float w = PANEL_WIDTH_PX;
        float h = PANEL_HEIGHT_PX;

        // Sfondo principale - usa debug quads che è più stabile per aree filled
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugQuads());

        // Sfondo scuro semi-trasparente
        int bgColor = applyAlphaARGB(PANEL_BG, alpha);
        float br = ((bgColor >> 16) & 0xFF) / 255f;
        float bgc = ((bgColor >> 8) & 0xFF) / 255f;
        float bb = (bgColor & 0xFF) / 255f;
        float ba = ((bgColor >> 24) & 0xFF) / 255f;

        // Quad per lo sfondo (ordine vertici per corretta visualizzazione)
        consumer.addVertex(matrix, 0, 0, 0.001f).setColor(br, bgc, bb, ba).setNormal(0, 0, 1);
        consumer.addVertex(matrix, 0, h, 0.001f).setColor(br, bgc, bb, ba).setNormal(0, 0, 1);
        consumer.addVertex(matrix, w, h, 0.001f).setColor(br, bgc, bb, ba).setNormal(0, 0, 1);
        consumer.addVertex(matrix, w, 0, 0.001f).setColor(br, bgc, bb, ba).setNormal(0, 0, 1);

        // Bordo con RenderType.lines() - più stabile
        VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());
        int borderColor = applyAlphaARGB(PANEL_BORDER, alpha * 0.9f);
        float bor = ((borderColor >> 16) & 0xFF) / 255f;
        float bog = ((borderColor >> 8) & 0xFF) / 255f;
        float bob = (borderColor & 0xFF) / 255f;
        float boa = ((borderColor >> 24) & 0xFF) / 255f;

        // Top line
        lineConsumer.addVertex(matrix, 0, 0, 0).setColor(bor, bog, bob, boa).setNormal(poseStack.last(), 1, 0, 0);
        lineConsumer.addVertex(matrix, w, 0, 0).setColor(bor, bog, bob, boa).setNormal(poseStack.last(), 1, 0, 0);
        // Right line
        lineConsumer.addVertex(matrix, w, 0, 0).setColor(bor, bog, bob, boa).setNormal(poseStack.last(), 0, 1, 0);
        lineConsumer.addVertex(matrix, w, h, 0).setColor(bor, bog, bob, boa).setNormal(poseStack.last(), 0, 1, 0);
        // Bottom line
        lineConsumer.addVertex(matrix, w, h, 0).setColor(bor, bog, bob, boa).setNormal(poseStack.last(), 1, 0, 0);
        lineConsumer.addVertex(matrix, 0, h, 0).setColor(bor, bog, bob, boa).setNormal(poseStack.last(), 1, 0, 0);
        // Left line
        lineConsumer.addVertex(matrix, 0, h, 0).setColor(bor, bog, bob, boa).setNormal(poseStack.last(), 0, 1, 0);
        lineConsumer.addVertex(matrix, 0, 0, 0).setColor(bor, bog, bob, boa).setNormal(poseStack.last(), 0, 1, 0);
    }

    /**
     * Renderizza una linea separatrice orizzontale.
     */
    private void renderSeparatorLine(PoseStack poseStack, MultiBufferSource bufferSource,
                                      float x, float y, float width, float alpha) {
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());

        int color = applyAlphaARGB(PANEL_BORDER, alpha * 0.5f);
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;

        consumer.addVertex(matrix, x, y, 0).setColor(r, g, b, a).setNormal(poseStack.last(), 1, 0, 0);
        consumer.addVertex(matrix, x + width, y, 0).setColor(r, g, b, a).setNormal(poseStack.last(), 1, 0, 0);
    }

    /**
     * Renderizza testo nel mondo 3D usando il font di Minecraft.
     * Usa SEE_THROUGH per visibilità corretta in 3D.
     */
    private void renderText3D(PoseStack poseStack, MultiBufferSource bufferSource, Font font,
                               String text, float x, float y, int color, float globalAlpha) {
        poseStack.pushPose();
        poseStack.translate(x, y, -0.5f); // Offset Z per essere davanti allo sfondo

        Matrix4f matrix = poseStack.last().pose();

        // Applica alpha al colore
        int alpha = (int) (((color >> 24) & 0xFF) * globalAlpha);
        if (alpha == 0) alpha = (int) (255 * globalAlpha);
        int finalColor = (alpha << 24) | (color & 0x00FFFFFF);

        // Usa SEE_THROUGH per rendering 3D corretto (non viene bloccato da depth)
        font.drawInBatch(
            text,
            0, 0,
            finalColor,
            false, // shadow
            matrix,
            bufferSource,
            Font.DisplayMode.SEE_THROUGH,
            0, // backgroundColor
            15728880 // packedLight (full bright)
        );

        poseStack.popPose();
    }

    /**
     * Applica alpha a un colore RGB (senza alpha originale).
     */
    private int applyAlpha(int rgb, float alpha) {
        int a = (int) (255 * alpha);
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    /**
     * Applica alpha a un colore ARGB esistente.
     */
    private int applyAlphaARGB(int argb, float alphaMultiplier) {
        int originalAlpha = (argb >> 24) & 0xFF;
        int newAlpha = (int) (originalAlpha * alphaMultiplier);
        return (newAlpha << 24) | (argb & 0x00FFFFFF);
    }

    /**
     * Calcola la posizione del pannello dato un punto di impatto.
     * Il pannello viene posizionato a lato del punto di impatto.
     *
     * @param hitPoint Punto di impatto
     * @param cameraPos Posizione della camera
     * @return Posizione del pannello nel mondo
     */
    public Vec3 calculatePanelPosition(Vec3 hitPoint, Vec3 cameraPos) {
        // Direzione dalla camera al punto di impatto
        Vec3 toHit = hitPoint.subtract(cameraPos).normalize();

        // Vettore perpendicolare (a destra della visuale)
        Vec3 right = toHit.cross(new Vec3(0, 1, 0)).normalize();

        // Se il vettore right è zero (guardiamo dritto su/giù), usa un default
        if (right.lengthSqr() < 0.001) {
            right = new Vec3(1, 0, 0);
        }

        // Posiziona il pannello a destra e sopra il punto di impatto
        // L'offset laterale sposta il pannello verso destra della visuale
        // L'offset verticale lo solleva leggermente
        return hitPoint
            .add(right.scale(PANEL_OFFSET_SIDE))
            .add(0, PANEL_OFFSET_UP, 0);
    }
}
