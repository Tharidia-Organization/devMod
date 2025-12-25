package com.devmod.client.rendering;

import com.devmod.DevMod;
import com.devmod.client.ui.unified.persistence.SettingsManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Objects;

/**
 * VOXEL-LAB Debug Overlay: Entity Info Floating Labels
 *
 * Displays floating info labels above entities showing:
 * - Entity type and ID
 * - Health/max health
 * - Armor value
 * - Attack damage (for mobs)
 * - Current AI goal (for mobs)
 * - Distance to player
 *
 * Toggle: Part of Debug Overlay system (G key)
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT)

public class EntityInfoOverlay {

    public static final EntityInfoOverlay INSTANCE = new EntityInfoOverlay();

    private boolean enabled = false;
    private static final float LABEL_SCALE = 0.025f;

    // Get configurable render distance
    private static double getMaxRenderDistance() {
        return SettingsManager.INSTANCE.getSettings().visualizers.getRenderDistance();
    }

    // Colors
    private static final int COLOR_HEALTH_GOOD = 0x55FF55;
    private static final int COLOR_HEALTH_MED = 0xFFFF55;
    private static final int COLOR_HEALTH_LOW = 0xFF5555;
    private static final int COLOR_STAT = 0xAAAAAA;
    private static final int COLOR_HOSTILE = 0xFF5555;
    private static final int COLOR_PASSIVE = 0x55FF55;
    private static final int COLOR_NEUTRAL = 0xFFFF55;

    private EntityInfoOverlay() {}

    public void toggle() {
        enabled = !enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!INSTANCE.enabled || !DebugRenderer.INSTANCE.isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Vec3 cameraPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        // Get nearby entities
        var player = Objects.requireNonNull(mc.player);
        var level = Objects.requireNonNull(mc.level);
        AABB searchBox = Objects.requireNonNull(player.getBoundingBox().inflate(getMaxRenderDistance()));
        List<Entity> entities = level.getEntities(player, searchBox,
            e -> e instanceof LivingEntity && !(e instanceof Player) && e.isAlive());

        for (Entity entity : entities) {
            if (!(entity instanceof LivingEntity living)) continue;

            double dist = entity.distanceTo(player);
            if (dist > getMaxRenderDistance()) continue;

            // Calculate alpha based on distance (fade out)
            float alpha = 1.0f - (float)(dist / getMaxRenderDistance()) * 0.5f;

            renderEntityInfo(poseStack, bufferSource, living, cameraPos, alpha, mc.font);
        }

        bufferSource.endBatch();
    }

    private static void renderEntityInfo(PoseStack poseStack, MultiBufferSource buffer,
                                          LivingEntity entity, Vec3 cameraPos, float alpha, Font font) {
        Vec3 entityPos = entity.position();
        double x = entityPos.x - cameraPos.x;
        double y = entityPos.y + entity.getBbHeight() + 0.5 - cameraPos.y;
        double z = entityPos.z - cameraPos.z;

        poseStack.pushPose();
        poseStack.translate(x, y, z);

        // Billboard rotation (face camera)
        Minecraft mc = Minecraft.getInstance();
        poseStack.mulPose(Objects.requireNonNull(mc.getEntityRenderDispatcher().cameraOrientation()));
        poseStack.scale(-LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);

        // Build info lines
        String[] lines = buildInfoLines(entity, mc.player);

        // Calculate panel height for centering
        int panelHeight = lines.length * 10 + 6;
        Matrix4f matrix = Objects.requireNonNull(poseStack.last().pose());

        // Render text lines (centered)
        int textY = -panelHeight / 2 + 3;
        for (int i = 0; i < lines.length; i++) {
            String line = Objects.requireNonNull(lines[i]);
            int color = getLineColor(i, entity, alpha);

            int textX = -font.width(line) / 2;
            font.drawInBatch(line, textX, textY, color, false, matrix, Objects.requireNonNull(buffer),
                Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);

            textY += 10;
        }

        poseStack.popPose();
    }

    private static String[] buildInfoLines(LivingEntity entity, Player player) {
        // Line 1: Entity name/type
        EntityType<?> entityType = Objects.requireNonNull(entity.getType());
        ResourceLocation id = Objects.requireNonNull(BuiltInRegistries.ENTITY_TYPE.getKey(entityType));
        var customName = entity.getCustomName();
        String name = entity.hasCustomName() && customName != null ?
            customName.getString() :
            id.getPath().replace("_", " ");

        // Line 2: Health
        float health = entity.getHealth();
        float maxHealth = entity.getMaxHealth();
        String healthStr = String.format("HP: %.0f/%.0f", health, maxHealth);

        // Line 3: Armor
        double armor = entity.getAttributeValue(Objects.requireNonNull(Attributes.ARMOR));
        String armorStr = String.format("Armor: %.0f", armor);

        // Line 4: Attack (for mobs)
        String attackStr = "";
        if (entity instanceof Mob) {
            double attack = entity.getAttributeValue(Objects.requireNonNull(Attributes.ATTACK_DAMAGE));
            attackStr = String.format("ATK: %.1f", attack);
        }

        // Line 5: Distance
        double dist = player != null ? entity.distanceTo(player) : 0.0;
        String distStr = String.format("Dist: %.1fm", dist);

        // Line 6: AI State (simplified)
        String aiStr = "";
        if (entity instanceof Mob mob) {
            var target = mob.getTarget();
            if (target != null) {
                aiStr = "Targeting: " + target.getName().getString();
            } else if (mob.isAggressive()) {
                aiStr = "Aggressive";
            } else {
                aiStr = "Idle";
            }
        }

        // Build array (filter empty lines)
        if (!attackStr.isEmpty() && !aiStr.isEmpty()) {
            return new String[] { name, healthStr, armorStr, attackStr, distStr, aiStr };
        } else if (!attackStr.isEmpty()) {
            return new String[] { name, healthStr, armorStr, attackStr, distStr };
        } else if (!aiStr.isEmpty()) {
            return new String[] { name, healthStr, armorStr, distStr, aiStr };
        } else {
            return new String[] { name, healthStr, armorStr, distStr };
        }
    }

    private static int getLineColor(int lineIndex, LivingEntity entity, float alpha) {
        int alphaInt = (int)(alpha * 255) << 24;

        if (lineIndex == 0) {
            // Name color based on entity type
            if (entity instanceof Monster) {
                return alphaInt | (COLOR_HOSTILE & 0x00FFFFFF);
            } else if (entity instanceof Mob mob && mob.getTarget() != null) {
                return alphaInt | (COLOR_NEUTRAL & 0x00FFFFFF);
            } else {
                return alphaInt | (COLOR_PASSIVE & 0x00FFFFFF);
            }
        } else if (lineIndex == 1) {
            // Health color based on percentage
            float healthPercent = entity.getHealth() / entity.getMaxHealth();
            int healthColor;
            if (healthPercent > 0.6f) {
                healthColor = COLOR_HEALTH_GOOD;
            } else if (healthPercent > 0.3f) {
                healthColor = COLOR_HEALTH_MED;
            } else {
                healthColor = COLOR_HEALTH_LOW;
            }
            return alphaInt | (healthColor & 0x00FFFFFF);
        } else {
            return alphaInt | (COLOR_STAT & 0x00FFFFFF);
        }
    }
}
