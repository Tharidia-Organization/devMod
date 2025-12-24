package com.devmod.panels.types;

import com.devmod.panels.core.FloatingPanel;
import com.devmod.panels.core.PanelType;
import com.devmod.panels.tracking.EntityTracker;
import com.devmod.ui.editor.core.UIConstants;
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Panel that shows detailed information about an entity.
 *
 * Information displayed:
 * - Name and entity type
 * - Health bar and values
 * - Attributes (attack damage, armor)
 * - Active effects
 */
public class EntityInfoPanel extends FloatingPanel {

    // Data cache to avoid lookups every frame
    private String entityName = "";
    private String entityType = "";
    private float currentHealth = 0;
    private float maxHealth = 20;
    private float attackDamage = 0;
    private float armor = 0;
    private int effectCount = 0;
    private long lastUpdateTick = 0;

    private static final int UPDATE_INTERVAL_TICKS = 5; // Update every 5 ticks

    /**
     * Creates an info panel for the specified entity.
     */
    public EntityInfoPanel(Entity target) {
        super(PanelType.ENTITY_INFO, new EntityTracker(target));
        updateEntityData();
    }

    /**
     * Creates a panel with custom offset.
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

        // Update data periodically
        long currentTick = System.currentTimeMillis() / 50; // ~20 ticks/sec
        if (currentTick - lastUpdateTick >= UPDATE_INTERVAL_TICKS) {
            updateEntityData();
            lastUpdateTick = currentTick;
        }
    }

    /**
     * Updates the entity's cached data.
     */
    private void updateEntityData() {
        EntityTracker localTracker = this.tracker;
        if (localTracker == null) return;

        Entity target = localTracker.getEntity();
        if (target == null) return;

        entityName = target.getName().getString();
        entityType = target.getType().getDescription().getString();

        if (target instanceof LivingEntity living) {
            currentHealth = living.getHealth();
            maxHealth = living.getMaxHealth();

            // Attributes
            if (living.getAttribute(Objects.requireNonNull(Attributes.ATTACK_DAMAGE)) != null) {
                attackDamage = (float) living.getAttributeValue(Objects.requireNonNull(Attributes.ATTACK_DAMAGE));
            }
            if (living.getAttribute(Objects.requireNonNull(Attributes.ARMOR)) != null) {
                armor = (float) living.getAttributeValue(Objects.requireNonNull(Attributes.ARMOR));
            }

            effectCount = living.getActiveEffects().size();
        }
    }

    @Override
    public void renderContent(GuiGraphics graphics, int contentWidth, int contentHeight) {
        // Note: this method is for 2D rendering
        // The actual 3D rendering is handled by PanelRenderer

        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) return;

        int y = 0;
        int lineHeight = 10;

        // Entity name
        Font font = Objects.requireNonNull(mc.font);
        graphics.drawString(font, entityName, 0, y, UIConstants.Text.PRIMARY(), false);
        y += lineHeight + 2;

        // Type
        graphics.drawString(font, entityType, 0, y, UIConstants.Text.MUTED(), false);
        y += lineHeight + 4;

        // Health bar
        int barWidth = contentWidth - 4;
        int barHeight = 6;
        float healthPercent = maxHealth > 0 ? currentHealth / maxHealth : 0;

        // Background bar
        graphics.fill(0, y, barWidth, y + barHeight, UIConstants.Background.INPUT());
        // Health fill
        int healthColor = getHealthColor(healthPercent);
        graphics.fill(0, y, (int)(barWidth * healthPercent), y + barHeight, healthColor);
        y += barHeight + 2;

        // Health text
        String healthText = String.format("%.1f / %.1f", currentHealth, maxHealth);
        graphics.drawString(font, healthText, 0, y, UIConstants.Text.SECONDARY(), false);
        y += lineHeight + 4;

        // Stats
        if (attackDamage > 0) {
            graphics.drawString(font, String.format("ATK: %.1f", attackDamage), 0, y, UIConstants.Status.ERROR(), false);
            y += lineHeight;
        }

        if (armor > 0) {
            graphics.drawString(font, String.format("DEF: %.1f", armor), 0, y, UIConstants.Status.INFO(), false);
            y += lineHeight;
        }

        // Effects count
        if (effectCount > 0) {
            graphics.drawString(font, String.format("Effects: %d", effectCount), 0, y, UIConstants.Status.WARNING(), false);
        }
    }

    /**
     * Gets the health bar color based on percentage.
     */
    private int getHealthColor(float percent) {
        if (percent > 0.6f) return UIConstants.Status.SUCCESS();
        if (percent > 0.3f) return UIConstants.Status.WARNING();
        return UIConstants.Status.ERROR();
    }

    @Override
    public void renderContent3D(@Nonnull PoseStack poseStack, @Nonnull MultiBufferSource bufferSource, @Nonnull Font font,
                                 int contentWidth, int contentHeight, float alpha) {
        int y = 0;
        int lineHeight = 10;

        // Entity name
        renderText3D(poseStack, bufferSource, font, Objects.requireNonNull(entityName, "entityName"), 0, y, applyAlpha(UIConstants.Text.PRIMARY(), alpha));
        y += lineHeight + 2;

        // Type
        renderText3D(poseStack, bufferSource, font, Objects.requireNonNull(entityType, "entityType"), 0, y, applyAlpha(UIConstants.Text.MUTED(), alpha));
        y += lineHeight + 4;

        // Health (testo)
        float healthPercent = maxHealth > 0 ? currentHealth / maxHealth : 0;
        String healthText = Objects.requireNonNull(String.format("HP: %.0f/%.0f (%.0f%%)", currentHealth, maxHealth, healthPercent * 100), "healthText");
        int healthColor = getHealthColor(healthPercent);
        renderText3D(poseStack, bufferSource, font, healthText, 0, y, applyAlpha(healthColor, alpha));
        y += lineHeight + 2;

        // Stats
        if (attackDamage > 0) {
            String atkText = Objects.requireNonNull(String.format("ATK: %.1f", attackDamage), "atkText");
            renderText3D(poseStack, bufferSource, font, atkText, 0, y, applyAlpha(UIConstants.Status.ERROR(), alpha));
            y += lineHeight;
        }

        if (armor > 0) {
            String defText = Objects.requireNonNull(String.format("DEF: %.1f", armor), "defText");
            renderText3D(poseStack, bufferSource, font, defText, 0, y, applyAlpha(UIConstants.Status.INFO(), alpha));
            y += lineHeight;
        }

        // Effects count
        if (effectCount > 0) {
            String effectText = Objects.requireNonNull(String.format("Effects: %d", effectCount), "effectText");
            renderText3D(poseStack, bufferSource, font, effectText, 0, y, applyAlpha(UIConstants.Status.WARNING(), alpha));
        }
    }

    @Override
    @Nonnull
    public String getTitle() {
        return entityName.isEmpty() ? "Entity Info" : Objects.requireNonNull(entityName, "entityName");
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
     * Checks if the target is a player.
     */
    public boolean isTargetPlayer() {
        Entity target = getTargetEntity();
        return target instanceof Player;
    }

    /**
     * Checks if the target is a hostile mob.
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
