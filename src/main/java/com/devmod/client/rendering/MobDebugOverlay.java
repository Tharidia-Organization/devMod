package com.devmod.client.rendering;

import com.devmod.config.MobConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * Gestisce il rendering delle informazioni debug per i mob guardati dal player
 */
// Minecraft API methods are not annotated but never return null in practice

public class MobDebugOverlay {

    private static final int COLOR_HITBOX = 0x80FFFF00; // Transparent yellow
    private static final int COLOR_LABEL = 0xFFFFFF; // White

    // Track current mob for continuous rendering
    private static Mob trackedMob = null;
    private static long lastUpdateTime = 0;
    private static final long TRACKING_TIMEOUT = 3000; // 3 seconds without looking = stop tracking

    public static void renderMobInfo() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (!DebugRenderer.INSTANCE.isEnabled()) return;

        // MAP MAKING MODE: Render ALL spheres EVERY FRAME for smooth movement
        // We DON'T use DebugRenderer for global spheres - we render them directly
        // This avoids lag and maintains smooth rendering

        // Custom raycast to find the looked-at mob (for the detailed overlay)
        Mob lookedAtMob = findLookedAtMob(mc);

        // If we're looking at a mob, update tracking for the detailed overlay
        if (lookedAtMob != null) {
            trackedMob = lookedAtMob;
            lastUpdateTime = System.currentTimeMillis();
        }

        // If we're not looking at any mob but have a recent tracked mob, continue showing the overlay
        if (trackedMob != null) {
            // Check timeout
            if (System.currentTimeMillis() - lastUpdateTime > TRACKING_TIMEOUT) {
                trackedMob = null;
                return;
            }

            // Check if the mob is still valid and alive
            if (!trackedMob.isAlive() || trackedMob.isRemoved()) {
                trackedMob = null;
                DebugRenderer.INSTANCE.clear();
                return;
            }

            // Clear previous frame and render the tracked mob
            DebugRenderer.INSTANCE.clear();

            // Render main hitbox
            renderMainHitbox(trackedMob);

            // Render body parts
            renderBodyParts(trackedMob);

            // Render aggro range sphere
            renderAggroRange(trackedMob);

            // Render statistiche
            renderStats(trackedMob);
        }
    }

    /**
     * Finds the mob the player is looking at using custom raycast
     */
    private static Mob findLookedAtMob(Minecraft mc) {
        // Null safety: verify player and level exist
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null) {
            return null;
        }

        Vec3 eye = Objects.requireNonNull(player.getEyePosition(1.0F));
        Vec3 look = player.getViewVector(1.0F);

        // PERFORMANCE FIX: Limit render distance to 16 blocks max
        // Prevents lag when looking at distant mobs with extended reach
        double reach = Math.min(player.blockInteractionRange(), 16.0);
        Vec3 end = Objects.requireNonNull(eye.add(Objects.requireNonNull(look.scale(reach))));

        // Search for entities in range
        var entities = level.getEntities(player,
            Objects.requireNonNull(player.getBoundingBox().inflate(reach)));

        Mob closestMob = null;
        double closestDistance = reach;

        for (var entity : entities) {
            if (!(entity instanceof Mob mob)) continue;

            AABB box = Objects.requireNonNull(mob.getBoundingBox().inflate(0.3));
            var clip = box.clip(eye, end);

            if (clip.isPresent()) {
                double distance = eye.distanceTo(Objects.requireNonNull(clip.get()));
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestMob = mob;
                }
            }
        }

        return closestMob;
    }

    private static void renderMainHitbox(Mob mob) {
        AABB box = mob.getBoundingBox();
        DebugRenderer.INSTANCE.addBox(box, COLOR_HITBOX, true);
    }

    private static void renderBodyParts(LivingEntity entity) {
        // Use BodyPartCalculator as SINGLE SOURCE OF TRUTH for body part AABBs
        BodyPartCalculator.BodyPartAABB[] bodyParts = BodyPartCalculator.calculateAllBodyParts(Objects.requireNonNull(entity));

        // Render each body part with corresponding label and damage multiplier
        for (BodyPartCalculator.BodyPartAABB bodyPart : bodyParts) {
            AABB box = bodyPart.box();
            int color = bodyPart.color();

            // Render the box
            DebugRenderer.INSTANCE.addBox(box, color, true);

            // Calculate label position (center of box)
            Vec3 labelPos = getLabelPositionForBodyPart(bodyPart);
            String labelText = getLabelTextForBodyPart(bodyPart);

            // Render the label
            DebugRenderer.INSTANCE.addLabel(labelPos, labelText, COLOR_LABEL);
        }
    }

    /**
     * Get appropriate label position for each body part type
     */
    private static Vec3 getLabelPositionForBodyPart(BodyPartCalculator.BodyPartAABB bodyPart) {
        AABB box = bodyPart.box();
        Vec3 center = box.getCenter();

        return switch (bodyPart.part()) {
            case HEAD -> new Vec3(center.x, box.maxY + 0.3, center.z);
            case LEGS -> new Vec3(center.x, box.minY - 0.3, center.z);
            case BODY, ARMS -> center;
        };
    }

    /**
     * Get label text with damage multiplier for each body part
     */
    private static String getLabelTextForBodyPart(BodyPartCalculator.BodyPartAABB bodyPart) {
        return switch (bodyPart.part()) {
            case HEAD -> "Head [x2.0]";
            case ARMS -> "Arms [x0.9]";
            case BODY -> "Body [x1.0]";
            case LEGS -> "Legs [x0.75]";
        };
    }

    private static void renderStats(Mob mob) {
        // Label position above the mob
        Vec3 pos = mob.position().add(0, mob.getBbHeight() + 1.0, 0);

        // Get current stats
        double health = mob.getHealth();
        double maxHealth = mob.getMaxHealth();
        double armor = mob.getArmorValue();

        // Safe attribute access - check if attribute exists before getting value
        var damageAttr = mob.getAttribute(Objects.requireNonNull(Attributes.ATTACK_DAMAGE));
        double damage = damageAttr != null ? damageAttr.getValue() : 0.0;

        var rangeAttr = mob.getAttribute(Objects.requireNonNull(Attributes.FOLLOW_RANGE));
        double range = rangeAttr != null ? rangeAttr.getValue() : 0.0;

        // Check if there's a saved config
        MobConfigManager.SavedStats saved = MobConfigManager.getGlobalStats(mob.getType());

        // Build stats text
        StringBuilder stats = new StringBuilder();
        stats.append("=== ").append(mob.getName().getString()).append(" ===\n");
        stats.append(String.format("HP: %.1f / %.1f", health, maxHealth));
        if (saved != null && saved.maxHealth() != maxHealth) {
            stats.append(String.format(" (Config: %.1f)", saved.maxHealth()));
        }
        stats.append("\n");

        stats.append(String.format("Armor: %.1f", armor));
        if (saved != null && saved.armor() != armor) {
            stats.append(String.format(" (Config: %.1f)", saved.armor()));
        }
        stats.append("\n");

        // Only show damage if mob has attack damage attribute
        if (damageAttr != null) {
            stats.append(String.format("Damage: %.1f", damage));
            if (saved != null && saved.damage() != damage) {
                stats.append(String.format(" (Config: %.1f)", saved.damage()));
            }
            stats.append("\n");
        }

        stats.append(String.format("Range: %.1f", range));
        if (saved != null && saved.range() != range) {
            stats.append(String.format(" (Config: %.1f)", saved.range()));
        }

        // Split into multiple labels for better readability
        String[] lines = stats.toString().split("\n");
        for (int i = 0; i < lines.length; i++) {
            DebugRenderer.INSTANCE.addLabel(
                pos.add(0, -i * 0.3, 0),
                lines[i],
                i == 0 ? 0xFFFFAA00 : COLOR_LABEL // Title in yellow
            );
        }
    }

    /**
     * Renderizza la sfera di aggro range del mob.
     * Mostra visivamente l'area in cui il mob rileva il player.
     */
    private static void renderAggroRange(Mob mob) {
        // Ottieni il FOLLOW_RANGE attribute (detection range)
        var rangeAttr = mob.getAttribute(Objects.requireNonNull(Attributes.FOLLOW_RANGE));
        if (rangeAttr == null) return; // Skip if mob has no detection range

        double aggroRange = rangeAttr.getValue();
        if (aggroRange <= 0) return; // Skip invalid ranges

        Vec3 mobPos = mob.position().add(0, mob.getBbHeight() / 2.0, 0); // Center of mob

        // Add sphere wireframe to debug renderer
        // Transparent cyan color (ARGB format: 0x80_00_FF_FF)
        int color = 0x8000FFFF; // Alpha=0x80 (50%), RGB=0x00FFFF (cyan)
        int segments = 16; // Number of segments for sphere rendering

        // Use DebugRenderer to add sphere visualization
        DebugRenderer.INSTANCE.addSphere(mobPos, aggroRange, color, segments);
    }

}
