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

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Panel that shows combat information for an impact.
 *
 * Displayed information:
 * - Damage dealt (base and final)
 * - Body part hit
 * - Active modifiers
 * - Damage breakdown
 */
public class CombatPanel extends FloatingPanel {

    private final ImpactData impactData;
    private final Vec3 hitPoint;

    // Extracted data for fast rendering
    private final String partHit;
    private final float baseDamage;
    private final float finalDamage;
    private final float partMultiplier;
    private final boolean hasActualDamage;
    private final float actualDamage;

    /**
     * Creates a combat panel for impact data.
     *
     * @param data Impact data
     * @param hitPoint Impact point in the world
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

        Font font = Objects.requireNonNull(mc.font, "font");
        int y = 0;
        int lineHeight = 10;

        // Danno principale (grande e colorato)
        String damageText = Objects.requireNonNull(String.format("%.1f", actualDamage), "damageText");
        int damageColor = getDamageColor(actualDamage);
        graphics.drawString(font, damageText, 0, y, damageColor, false);
        y += lineHeight + 4;

        // Parte colpita
        String partText = Objects.requireNonNull(partHit + " (x" + String.format("%.2f", partMultiplier) + ")", "partText");
        graphics.drawString(font, partText, 0, y, UIConstants.Text.SECONDARY, false);
        y += lineHeight + 2;

        // Breakdown se c'e' spazio
        if (contentHeight > 60) {
            // Linea separatrice
            graphics.fill(0, y, contentWidth - 4, y + 1, UIConstants.Border.SEPARATOR);
            y += 4;

            // Base damage
            String baseText = Objects.requireNonNull(String.format("Base: %.1f", baseDamage), "baseText");
            graphics.drawString(font, baseText, 0, y, UIConstants.Text.MUTED, false);
            y += lineHeight;

            // Calculated vs Actual
            if (hasActualDamage && Math.abs(actualDamage - finalDamage) > 0.1f) {
                float diff = actualDamage - finalDamage;
                String diffText = Objects.requireNonNull(String.format("Armor: %s%.1f", diff < 0 ? "" : "+", diff), "diffText");
                int diffColor = diff < 0 ? UIConstants.Status.ERROR : UIConstants.Status.SUCCESS;
                graphics.drawString(font, diffText, 0, y, diffColor, false);
            }
        }
    }

    /**
     * Gets color based on damage dealt.
     */
    private int getDamageColor(float damage) {
        if (damage >= 15) return 0xFFFF4444; // Critical red
        if (damage >= 8) return 0xFFFFAA00;  // High orange
        if (damage >= 4) return 0xFFFFFF00;  // Medium yellow
        return 0xFFFFFFFF;                    // Low white
    }

    @Override
    public void renderContent3D(@Nonnull PoseStack poseStack, @Nonnull MultiBufferSource bufferSource, @Nonnull Font font,
                                 int contentWidth, int contentHeight, float alpha) {
        int y = 0;
        int lineHeight = 10;

        // Danno principale
        String damageText = Objects.requireNonNull(String.format("%.1f DMG", actualDamage), "damageText");
        int damageColor = getDamageColor(actualDamage);
        renderText3D(poseStack, bufferSource, font, damageText, 0, y, applyAlpha(damageColor, alpha));
        y += lineHeight + 4;

        // Parte colpita
        String partText = Objects.requireNonNull(partHit + " (x" + String.format("%.2f", partMultiplier) + ")", "partText");
        renderText3D(poseStack, bufferSource, font, partText, 0, y, applyAlpha(UIConstants.Text.SECONDARY, alpha));
        y += lineHeight + 2;

        // Base damage
        String baseText = Objects.requireNonNull(String.format("Base: %.1f", baseDamage), "baseText");
        renderText3D(poseStack, bufferSource, font, baseText, 0, y, applyAlpha(UIConstants.Text.MUTED, alpha));
        y += lineHeight;

        // Differenza armor se significativa
        if (hasActualDamage && Math.abs(actualDamage - finalDamage) > 0.1f) {
            float diff = actualDamage - finalDamage;
            String diffText = Objects.requireNonNull(String.format("Armor: %s%.1f", diff < 0 ? "" : "+", diff), "diffText");
            int diffColor = diff < 0 ? UIConstants.Status.ERROR : UIConstants.Status.SUCCESS;
            renderText3D(poseStack, bufferSource, font, diffText, 0, y, applyAlpha(diffColor, alpha));
        }
    }

    @Override
    @Nonnull
    public String getTitle() {
        return Objects.requireNonNull("DMG: " + String.format("%.1f", actualDamage), "title");
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
