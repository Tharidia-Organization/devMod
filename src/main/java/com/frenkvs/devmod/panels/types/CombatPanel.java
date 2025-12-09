package com.frenkvs.devmod.panels.types;

import com.frenkvs.devmod.hud.DamageBreakdown;
import com.frenkvs.devmod.hud.ImpactData;
import com.frenkvs.devmod.panels.core.FloatingPanel;
import com.frenkvs.devmod.panels.core.PanelType;
import com.frenkvs.devmod.ui.UIConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

/**
 * Pannello che mostra informazioni di combattimento per un impatto.
 *
 * Informazioni visualizzate:
 * - Danno inflitto (base e finale)
 * - Parte del corpo colpita
 * - Modificatori attivi
 * - Breakdown del danno
 */
public class CombatPanel extends FloatingPanel {

    private final ImpactData impactData;
    private final Vec3 hitPoint;

    // Dati estratti per rendering veloce
    private final String partHit;
    private final float baseDamage;
    private final float finalDamage;
    private final float partMultiplier;
    private final boolean hasActualDamage;
    private final float actualDamage;

    /**
     * Crea un pannello combat per i dati di impatto.
     *
     * @param data Dati dell'impatto
     * @param hitPoint Punto di impatto nel mondo
     */
    public CombatPanel(ImpactData data, Vec3 hitPoint) {
        super(PanelType.COMBAT, hitPoint.add(0, 0.5, 0)); // Offset sopra il punto
        this.impactData = data;
        this.hitPoint = hitPoint;

        // Estrai dati per rendering veloce
        this.partHit = data.bodyPart != null ? data.bodyPart.name() : "BODY";
        this.partMultiplier = data.bodyPartMultiplier;

        DamageBreakdown bd = data.breakdown;
        this.baseDamage = bd != null ? bd.baseWeaponDamage : 0;
        this.finalDamage = bd != null ? bd.finalDamage : 0;

        this.hasActualDamage = data.hasActualDamage();
        this.actualDamage = hasActualDamage ? data.getActualDamageDealt() : finalDamage;
    }

    @Override
    public void renderContent(GuiGraphics graphics, int contentWidth, int contentHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) return;

        int y = 0;
        int lineHeight = 10;

        // Danno principale (grande e colorato)
        String damageText = String.format("%.1f", actualDamage);
        int damageColor = getDamageColor(actualDamage);
        graphics.drawString(mc.font, damageText, 0, y, damageColor, false);
        y += lineHeight + 4;

        // Parte colpita
        String partText = partHit + " (x" + String.format("%.2f", partMultiplier) + ")";
        graphics.drawString(mc.font, partText, 0, y, UIConstants.Text.SECONDARY, false);
        y += lineHeight + 2;

        // Breakdown se c'e' spazio
        if (contentHeight > 60) {
            // Linea separatrice
            graphics.fill(0, y, contentWidth - 4, y + 1, UIConstants.Border.SEPARATOR);
            y += 4;

            // Base damage
            String baseText = String.format("Base: %.1f", baseDamage);
            graphics.drawString(mc.font, baseText, 0, y, UIConstants.Text.MUTED, false);
            y += lineHeight;

            // Calculated vs Actual
            if (hasActualDamage && Math.abs(actualDamage - finalDamage) > 0.1f) {
                float diff = actualDamage - finalDamage;
                String diffText = String.format("Armor: %s%.1f", diff < 0 ? "" : "+", diff);
                int diffColor = diff < 0 ? UIConstants.Status.ERROR : UIConstants.Status.SUCCESS;
                graphics.drawString(mc.font, diffText, 0, y, diffColor, false);
            }
        }
    }

    /**
     * Ottiene il colore basato sul danno inflitto.
     */
    private int getDamageColor(float damage) {
        if (damage >= 15) return 0xFFFF4444; // Rosso critico
        if (damage >= 8) return 0xFFFFAA00;  // Arancione alto
        if (damage >= 4) return 0xFFFFFF00;  // Giallo medio
        return 0xFFFFFFFF;                    // Bianco basso
    }

    @Override
    public void renderContent3D(PoseStack poseStack, MultiBufferSource bufferSource, Font font,
                                 int contentWidth, int contentHeight, float alpha) {
        int y = 0;
        int lineHeight = 10;

        // Danno principale
        String damageText = String.format("%.1f DMG", actualDamage);
        int damageColor = getDamageColor(actualDamage);
        renderText3D(poseStack, bufferSource, font, damageText, 0, y, applyAlpha(damageColor, alpha));
        y += lineHeight + 4;

        // Parte colpita
        String partText = partHit + " (x" + String.format("%.2f", partMultiplier) + ")";
        renderText3D(poseStack, bufferSource, font, partText, 0, y, applyAlpha(UIConstants.Text.SECONDARY, alpha));
        y += lineHeight + 2;

        // Base damage
        String baseText = String.format("Base: %.1f", baseDamage);
        renderText3D(poseStack, bufferSource, font, baseText, 0, y, applyAlpha(UIConstants.Text.MUTED, alpha));
        y += lineHeight;

        // Differenza armor se significativa
        if (hasActualDamage && Math.abs(actualDamage - finalDamage) > 0.1f) {
            float diff = actualDamage - finalDamage;
            String diffText = String.format("Armor: %s%.1f", diff < 0 ? "" : "+", diff);
            int diffColor = diff < 0 ? UIConstants.Status.ERROR : UIConstants.Status.SUCCESS;
            renderText3D(poseStack, bufferSource, font, diffText, 0, y, applyAlpha(diffColor, alpha));
        }
    }

    @Override
    public String getTitle() {
        return "DMG: " + String.format("%.1f", actualDamage);
    }

    // === Getters ===

    public ImpactData getImpactData() {
        return impactData;
    }

    public Vec3 getHitPoint() {
        return hitPoint;
    }

    public String getPartHit() {
        return partHit;
    }

    public float getBaseDamage() {
        return baseDamage;
    }

    public float getFinalDamage() {
        return finalDamage;
    }

    public float getActualDamage() {
        return actualDamage;
    }

    public float getPartMultiplier() {
        return partMultiplier;
    }

    @Override
    public String toString() {
        return String.format("CombatPanel[dmg=%.1f, part=%s, pos=%s]",
            actualDamage, partHit, hitPoint);
    }
}
