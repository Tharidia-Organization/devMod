package com.devmod.client.rendering;

import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.devmod.client.ui.overlay.OverlayTheme;
import com.devmod.config.MobConfigManager;
import com.devmod.config.WeaponConfigManager;
import com.devmod.stats.WeaponStats;

public class MobDebugOverlay {

    // Colors (delegating to OverlayTheme.Debug)
    private static final int COLOR_HITBOX = OverlayTheme.Debug.HITBOX;
    private static final int COLOR_LABEL = OverlayTheme.Debug.LABEL;
    private static final int COLOR_TITLE = OverlayTheme.Debug.TITLE;
    private static final long SHAPE_TTL_MS = 150;
    private static final long LABEL_TTL_MS = 250;
    private static final double STAT_EPSILON = 0.01;
    private static final int MAX_NAME_LENGTH = 24;

    // Track current mob for continuous rendering
    @Nullable
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

        // Capture to local variable for null-safety (field could change between checks)
        Mob currentMob = trackedMob;

        // If we're not looking at any mob but have a recent tracked mob, continue showing the overlay
        if (currentMob != null) {
            // Check timeout
            if (System.currentTimeMillis() - lastUpdateTime > TRACKING_TIMEOUT) {
                trackedMob = null;
                return;
            }

            // Check if the mob is still valid and alive
            if (!currentMob.isAlive() || currentMob.isRemoved()) {
                trackedMob = null;
                return;
            }

            // Render main hitbox
            renderMainHitbox(currentMob);

            // Render body parts
            renderBodyParts(currentMob);

            // Render aggro range sphere
            renderAggroRange(currentMob);

            // Render statistiche
            renderStats(currentMob);
        }
    }

    /**
     * Finds the mob the player is looking at using custom raycast
     */
    @Nullable
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
                double distance = eye.distanceTo(clip.get());
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
        DebugRenderer.INSTANCE.addBox(box, COLOR_HITBOX, true, SHAPE_TTL_MS);
    }

    private static void renderBodyParts(LivingEntity entity) {
        WeaponStats stats = WeaponConfigManager.getGlobalStats();

        // Use BodyPartCalculator as SINGLE SOURCE OF TRUTH for body part AABBs
        BodyPartCalculator.BodyPartAABB[] bodyParts = BodyPartCalculator.calculateAllBodyParts(Objects.requireNonNull(entity));

        // Render each body part with corresponding label and damage multiplier
        for (BodyPartCalculator.BodyPartAABB bodyPart : bodyParts) {
            AABB box = bodyPart.box();
            int color = bodyPart.color();

            // Render the box
            DebugRenderer.INSTANCE.addBox(box, color, true, SHAPE_TTL_MS);

            // Calculate label position (center of box)
            Vec3 labelPos = getLabelPositionForBodyPart(bodyPart);
            String labelText = getLabelTextForBodyPart(bodyPart, stats);

            // Render the label
            DebugRenderer.INSTANCE.addLabel(labelPos, labelText, COLOR_LABEL, LABEL_TTL_MS);
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
    private static String getLabelTextForBodyPart(BodyPartCalculator.BodyPartAABB bodyPart, WeaponStats stats) {
        return switch (bodyPart.part()) {
            case HEAD -> stats.getHeadMult() >= 2.0f
                ? String.format("Head [Crit x%.1f]", stats.getHeadMult())
                : String.format("Head [x%.2f]", stats.getHeadMult());
            case ARMS -> String.format("Arms [x%.2f]", stats.getArmsMult());
            case BODY -> String.format("Torso [x%.2f]", stats.getBodyMult());
            case LEGS -> String.format("Legs [x%.2f]", stats.getLegsMult());
        };
    }

    private static void renderStats(Mob mob) {
        // Get current stats
        double health = mob.getHealth();
        double maxHealth = mob.getMaxHealth();
        double armor = mob.getArmorValue();

        // Safe attribute access - check if attribute exists before getting value
        var damageAttr = mob.getAttribute(Objects.requireNonNull(Attributes.ATTACK_DAMAGE));
        double damage = damageAttr != null ? damageAttr.getValue() : 0.0;

        var rangeAttr = mob.getAttribute(Objects.requireNonNull(Attributes.FOLLOW_RANGE));
        double range = rangeAttr != null ? rangeAttr.getValue() : 0.0;

        var reachAttr = mob.getAttribute(Objects.requireNonNull(Attributes.ENTITY_INTERACTION_RANGE));
        double reach = reachAttr != null ? reachAttr.getValue() : 0.0;

        // Check if there's a saved config
        MobConfigManager.SavedStats saved = MobConfigManager.getGlobalStats(mob.getType());

        // Build stats text
        StringBuilder stats = new StringBuilder();
        String mobName = truncateName(mob.getName().getString(), MAX_NAME_LENGTH);
        stats.append("=== ").append(mobName);
        if (saved != null) {
            stats.append(" [CFG]");
        }
        stats.append(" ===\n");
        stats.append(String.format("HP: %.1f / %.1f", health, maxHealth));
        if (saved != null && differs(saved.maxHealth(), maxHealth)) {
            stats.append(String.format(" (cfg %.1f)", saved.maxHealth()));
        }
        stats.append("\n");

        stats.append(String.format("Armor: %.1f", armor));
        if (saved != null && differs(saved.armor(), armor)) {
            stats.append(String.format(" (cfg %.1f)", saved.armor()));
        }
        stats.append("\n");

        // Only show damage if mob has attack damage attribute
        if (damageAttr != null) {
            stats.append(String.format("Damage: %.1f", damage));
            if (saved != null && differs(saved.damage(), damage)) {
                stats.append(String.format(" (cfg %.1f)", saved.damage()));
            }
            stats.append("\n");
        }

        if (rangeAttr != null || saved != null) {
            if (rangeAttr != null) {
                stats.append(String.format("Aggro: %.1f", range));
            } else {
                stats.append("Aggro: n/a");
            }
            if (saved != null && (rangeAttr == null || differs(saved.range(), range))) {
                stats.append(String.format(" (cfg %.1f)", saved.range()));
            }
            stats.append("\n");
        }

        if (reachAttr != null || saved != null) {
            if (reachAttr != null) {
                stats.append(String.format("Reach: %.1f", reach));
            } else {
                stats.append("Reach: n/a");
            }
            if (saved != null && (reachAttr == null || differs(saved.attackReach(), reach))) {
                stats.append(String.format(" (cfg %.1f)", saved.attackReach()));
            }
        }

        // Split into multiple labels for better readability
        String[] lines = stats.toString().lines().toArray(String[]::new);
        Vec3 pos = mob.position().add(0, mob.getBbHeight() + 0.7 + (lines.length * 0.12), 0);
        for (int i = 0; i < lines.length; i++) {
            DebugRenderer.INSTANCE.addLabel(
                pos.add(0, -i * 0.3, 0),
                lines[i],
                i == 0 ? COLOR_TITLE : COLOR_LABEL,
                LABEL_TTL_MS
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
        int color = OverlayTheme.Debug.AGGRO_SPHERE;
        int segments = 16; // Number of segments for sphere rendering

        // Use DebugRenderer to add sphere visualization
        DebugRenderer.INSTANCE.addSphere(mobPos, aggroRange, color, segments, SHAPE_TTL_MS);
    }

    private static boolean differs(double a, double b) {
        return Math.abs(a - b) > STAT_EPSILON;
    }

    private static String truncateName(String name, int maxLen) {
        if (name == null) return "";
        if (name.length() <= maxLen) return name;
        return name.substring(0, maxLen - 2) + "..";
    }
}
