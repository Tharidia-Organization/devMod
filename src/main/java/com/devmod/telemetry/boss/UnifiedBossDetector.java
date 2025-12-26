package com.devmod.telemetry.boss;

import java.util.Objects;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;

import com.devmod.telemetry.TelemetryService;
import com.devmod.telemetry.TelemetrySettings;
public class UnifiedBossDetector {

    public static final UnifiedBossDetector INSTANCE = new UnifiedBossDetector();

    private UnifiedBossDetector() {}

    /**
     * Check if an entity should be considered a boss.
     *
     * @param entity The entity to check
     * @return true if the entity is a boss
     */
    public boolean isBoss(LivingEntity entity) {
        if (entity == null) return false;

        // 1. Vanilla bosses - always true
        if (entity instanceof WitherBoss || entity instanceof EnderDragon) {
            return true;
        }

        // 2. Explicit boss tags
        if (hasBossTag(entity)) {
            return true;
        }

        // 3. NBT marker (modded boss compatibility)
        if (hasNbtBossMarker(entity)) {
            return true;
        }

        // 4. Entity type name patterns
        if (hasBossTypePattern(entity)) {
            return true;
        }

        // 5. HP threshold check
        double maxHp = entity.getMaxHealth();
        double threshold = getHpThreshold();

        if (maxHp >= threshold) {
            // 6. Exclude buffed regular mobs
            if (!isBuffedRegularMob(entity, maxHp)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check for explicit boss tags.
     */
    private boolean hasBossTag(LivingEntity entity) {
        return entity.getTags().contains("devmod:boss")
            || entity.getTags().contains("boss")
            || entity.getTags().contains("minecraft:boss");
    }

    /**
     * Check for NBT boss marker (used by L_Ender's Cataclysm, Mowzie's Mobs, etc).
     */
    private boolean hasNbtBossMarker(LivingEntity entity) {
        return entity.getPersistentData().contains("IsBoss")
            && entity.getPersistentData().getBoolean("IsBoss");
    }

    /**
     * Check entity type name for boss patterns.
     */
    private boolean hasBossTypePattern(LivingEntity entity) {
        String entityId = Objects.requireNonNull(BuiltInRegistries.ENTITY_TYPE.getKey(Objects.requireNonNull(entity.getType()))).toString().toLowerCase();
        return entityId.contains("boss")
            || entityId.contains("ender_guardian")
            || entityId.contains("void_worm")
            || entityId.contains("harbinger")
            || entityId.contains("warden")
            || entityId.contains("elder");
    }

    /**
     * Check if entity is a buffed regular mob (Apotheosis, Scaling Health, etc).
     */
    private boolean isBuffedRegularMob(LivingEntity entity, double maxHp) {
        // If HP is very high (>=200), probably a real boss
        if (maxHp >= 200) {
            return false;
        }

        // Check for buff mod tags
        return entity.getTags().stream().anyMatch(tag ->
            tag.contains("elite")
            || tag.contains("champion")
            || tag.contains("rare")
            || tag.contains("infernal")
            || tag.contains("scaling_health")
        );
    }

    /**
     * Get the configurable HP threshold for boss detection.
     */
    private double getHpThreshold() {
        try {
            TelemetrySettings settings = TelemetryService.INSTANCE.getSettings();
            return settings.bossHpThreshold();
        } catch (Exception e) {
            // Fallback to default if TelemetryService not initialized
            return 100.0;
        }
    }

    /**
     * Get boss classification for display purposes.
     */
    public BossClassification classify(LivingEntity entity) {
        if (entity == null) return BossClassification.NOT_A_BOSS;

        if (entity instanceof WitherBoss) {
            return BossClassification.VANILLA_WITHER;
        }
        if (entity instanceof EnderDragon) {
            return BossClassification.VANILLA_DRAGON;
        }
        if (hasBossTag(entity)) {
            return BossClassification.TAGGED_BOSS;
        }
        if (hasNbtBossMarker(entity)) {
            return BossClassification.MODDED_BOSS;
        }
        if (hasBossTypePattern(entity)) {
            return BossClassification.TYPE_PATTERN_BOSS;
        }
        if (entity.getMaxHealth() >= getHpThreshold() && !isBuffedRegularMob(entity, entity.getMaxHealth())) {
            return BossClassification.HP_THRESHOLD_BOSS;
        }

        return BossClassification.NOT_A_BOSS;
    }

    /**
     * Boss classification enum for debugging and display.
     */
    public enum BossClassification {
        NOT_A_BOSS("Not a boss"),
        VANILLA_WITHER("Wither Boss"),
        VANILLA_DRAGON("Ender Dragon"),
        TAGGED_BOSS("Tagged Boss"),
        MODDED_BOSS("Modded Boss"),
        TYPE_PATTERN_BOSS("Type Pattern Boss"),
        HP_THRESHOLD_BOSS("HP Threshold Boss");

        private final String displayName;

        BossClassification(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean isBoss() {
            return this != NOT_A_BOSS;
        }
    }
}
