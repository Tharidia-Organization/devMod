package com.frenkvs.devmod.panels.types;

import com.frenkvs.devmod.panels.core.FloatingPanel;
import com.frenkvs.devmod.panels.core.PanelType;
import com.frenkvs.devmod.panels.tracking.EntityTracker;
import com.frenkvs.devmod.ui.UIConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Pannello che mostra informazioni dettagliate su un'entita'.
 *
 * Informazioni visualizzate:
 * - Nome e tipo entita'
 * - Health bar e valori
 * - Attributi (attack damage, armor)
 * - Effetti attivi
 */
public class EntityInfoPanel extends FloatingPanel {

    // Cache dei dati per evitare lookup ogni frame
    private String entityName = "";
    private String entityType = "";
    private float currentHealth = 0;
    private float maxHealth = 20;
    private float attackDamage = 0;
    private float armor = 0;
    private int effectCount = 0;
    private long lastUpdateTick = 0;

    private static final int UPDATE_INTERVAL_TICKS = 5; // Aggiorna ogni 5 tick

    /**
     * Crea un pannello info per l'entita' specificata.
     */
    public EntityInfoPanel(Entity target) {
        super(PanelType.ENTITY_INFO, new EntityTracker(target));
        updateEntityData();
    }

    /**
     * Crea un pannello con offset custom.
     */
    public EntityInfoPanel(Entity target, Vec3 offset) {
        super(PanelType.ENTITY_INFO, new EntityTracker(target));
        if (tracker != null) {
            tracker.setOffset(offset);
        }
        updateEntityData();
    }

    @Override
    public void tick() {
        super.tick();

        // Aggiorna i dati periodicamente
        long currentTick = System.currentTimeMillis() / 50; // ~20 ticks/sec
        if (currentTick - lastUpdateTick >= UPDATE_INTERVAL_TICKS) {
            updateEntityData();
            lastUpdateTick = currentTick;
        }
    }

    /**
     * Aggiorna i dati cached dell'entita'.
     */
    private void updateEntityData() {
        if (tracker == null) return;

        Entity target = tracker.getEntity();
        if (target == null) return;

        entityName = target.getName().getString();
        entityType = target.getType().getDescription().getString();

        if (target instanceof LivingEntity living) {
            currentHealth = living.getHealth();
            maxHealth = living.getMaxHealth();

            // Attributi
            if (living.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
                attackDamage = (float) living.getAttributeValue(Attributes.ATTACK_DAMAGE);
            }
            if (living.getAttribute(Attributes.ARMOR) != null) {
                armor = (float) living.getAttributeValue(Attributes.ARMOR);
            }

            effectCount = living.getActiveEffects().size();
        }
    }

    @Override
    public void renderContent(GuiGraphics graphics, int contentWidth, int contentHeight) {
        // Nota: questo metodo e' per rendering 2D
        // Il rendering 3D effettivo e' gestito da PanelRenderer

        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) return;

        int y = 0;
        int lineHeight = 10;

        // Nome entita'
        graphics.drawString(mc.font, entityName, 0, y, UIConstants.Text.PRIMARY, false);
        y += lineHeight + 2;

        // Tipo
        graphics.drawString(mc.font, entityType, 0, y, UIConstants.Text.MUTED, false);
        y += lineHeight + 4;

        // Health bar
        int barWidth = contentWidth - 4;
        int barHeight = 6;
        float healthPercent = maxHealth > 0 ? currentHealth / maxHealth : 0;

        // Background bar
        graphics.fill(0, y, barWidth, y + barHeight, UIConstants.Background.INPUT);
        // Health fill
        int healthColor = getHealthColor(healthPercent);
        graphics.fill(0, y, (int)(barWidth * healthPercent), y + barHeight, healthColor);
        y += barHeight + 2;

        // Health text
        String healthText = String.format("%.1f / %.1f", currentHealth, maxHealth);
        graphics.drawString(mc.font, healthText, 0, y, UIConstants.Text.SECONDARY, false);
        y += lineHeight + 4;

        // Stats
        if (attackDamage > 0) {
            graphics.drawString(mc.font, String.format("ATK: %.1f", attackDamage), 0, y, UIConstants.Status.ERROR, false);
            y += lineHeight;
        }

        if (armor > 0) {
            graphics.drawString(mc.font, String.format("DEF: %.1f", armor), 0, y, UIConstants.Status.INFO, false);
            y += lineHeight;
        }

        // Effects count
        if (effectCount > 0) {
            graphics.drawString(mc.font, String.format("Effects: %d", effectCount), 0, y, UIConstants.Status.WARNING, false);
        }
    }

    /**
     * Ottiene il colore della health bar basato sulla percentuale.
     */
    private int getHealthColor(float percent) {
        if (percent > 0.6f) return UIConstants.Status.SUCCESS;
        if (percent > 0.3f) return UIConstants.Status.WARNING;
        return UIConstants.Status.ERROR;
    }

    @Override
    public void renderContent3D(PoseStack poseStack, MultiBufferSource bufferSource, Font font,
                                 int contentWidth, int contentHeight, float alpha) {
        int y = 0;
        int lineHeight = 10;

        // Nome entita'
        renderText3D(poseStack, bufferSource, font, entityName, 0, y, applyAlpha(UIConstants.Text.PRIMARY, alpha));
        y += lineHeight + 2;

        // Tipo
        renderText3D(poseStack, bufferSource, font, entityType, 0, y, applyAlpha(UIConstants.Text.MUTED, alpha));
        y += lineHeight + 4;

        // Health (testo)
        float healthPercent = maxHealth > 0 ? currentHealth / maxHealth : 0;
        String healthText = String.format("HP: %.0f/%.0f (%.0f%%)", currentHealth, maxHealth, healthPercent * 100);
        int healthColor = getHealthColor(healthPercent);
        renderText3D(poseStack, bufferSource, font, healthText, 0, y, applyAlpha(healthColor, alpha));
        y += lineHeight + 2;

        // Stats
        if (attackDamage > 0) {
            String atkText = String.format("ATK: %.1f", attackDamage);
            renderText3D(poseStack, bufferSource, font, atkText, 0, y, applyAlpha(UIConstants.Status.ERROR, alpha));
            y += lineHeight;
        }

        if (armor > 0) {
            String defText = String.format("DEF: %.1f", armor);
            renderText3D(poseStack, bufferSource, font, defText, 0, y, applyAlpha(UIConstants.Status.INFO, alpha));
            y += lineHeight;
        }

        // Effects count
        if (effectCount > 0) {
            String effectText = String.format("Effects: %d", effectCount);
            renderText3D(poseStack, bufferSource, font, effectText, 0, y, applyAlpha(UIConstants.Status.WARNING, alpha));
        }
    }

    @Override
    public String getTitle() {
        return entityName.isEmpty() ? "Entity Info" : entityName;
    }

    // === Getters per dati entity ===

    public String getEntityName() {
        return entityName;
    }

    public String getEntityType() {
        return entityType;
    }

    public float getCurrentHealth() {
        return currentHealth;
    }

    public float getMaxHealth() {
        return maxHealth;
    }

    public float getHealthPercent() {
        return maxHealth > 0 ? currentHealth / maxHealth : 0;
    }

    public float getAttackDamage() {
        return attackDamage;
    }

    public float getArmor() {
        return armor;
    }

    public int getEffectCount() {
        return effectCount;
    }

    @Nullable
    public Entity getTargetEntity() {
        return tracker != null ? tracker.getEntity() : null;
    }

    /**
     * Verifica se il target e' un player.
     */
    public boolean isTargetPlayer() {
        Entity target = getTargetEntity();
        return target instanceof Player;
    }

    /**
     * Verifica se il target e' un mob ostile.
     */
    public boolean isTargetHostile() {
        Entity target = getTargetEntity();
        if (target instanceof Mob mob) {
            return mob.getTarget() != null;
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("EntityInfoPanel[entity=%s, health=%.1f/%.1f]",
            entityName, currentHealth, maxHealth);
    }
}
