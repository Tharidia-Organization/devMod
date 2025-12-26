package com.devmod.endurance.resonance;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

import com.devmod.config.gamedesign.GameDesignConfig;
import com.devmod.config.gamedesign.GameDesignConfigManager;
import com.devmod.endurance.ComboSystem;
import com.devmod.telemetry.endurance.EnduranceTelemetryService;

public final class ResonanceChainSystem {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResonanceChainSystem.class);

    public static final ResonanceChainSystem INSTANCE = new ResonanceChainSystem();

    // Recent hits per entity: entityId -> list of hit records
    private final Map<Integer, List<HitRecord>> recentHits = new ConcurrentHashMap<>();

    // Cleanup tracking
    private long lastCleanupTick = 0;
    private static final long CLEANUP_INTERVAL_TICKS = 20; // Every second
    private static final long HIT_EXPIRY_MS = 1000; // Hits expire after 1 second

    // Resonance windows per tier (milliseconds)
    private static final long DUO_WINDOW_MS = 500;
    private static final long TRINITY_WINDOW_MS = 300;
    private static final long APOCALYPSE_WINDOW_MS = 200;

    // Cooldown to prevent spam (per entity)
    private final Map<Integer, Long> resonanceCooldowns = new ConcurrentHashMap<>();
    private static final long RESONANCE_COOLDOWN_MS = 1500;

    // Empty participants list constant for null-safety
    private static final List<UUID> EMPTY_PARTICIPANTS = List.of();

    private ResonanceChainSystem() {}

    /**
     * Get config for the given quest instance.
     */
    private GameDesignConfig.ResonanceConfig getConfig(@Nullable UUID questId) {
        return GameDesignConfigManager.INSTANCE.getResonanceConfig(questId);
    }

    /**
     * Resonance tier definitions.
     */
    public enum ResonanceTier {
        NONE(0, 1.0f, 0, ""),
        DUO(2, 1.5f, 200, "RESONANCE!"),
        TRINITY(3, 2.5f, 500, "TRINITY!"),
        APOCALYPSE(4, 5.0f, 1000, "APOCALYPSE!");

        public final int requiredPlayers;
        public final float damageMultiplier;
        public final int styleBonus;
        public final String announcement;

        ResonanceTier(int requiredPlayers, float damageMultiplier, int styleBonus, String announcement) {
            this.requiredPlayers = requiredPlayers;
            this.damageMultiplier = damageMultiplier;
            this.styleBonus = styleBonus;
            this.announcement = announcement;
        }

        public int getColor() {
            return switch (this) {
                case DUO -> 0xFFD700;      // Gold
                case TRINITY -> 0x9400D3;  // Purple
                case APOCALYPSE -> 0xFF0000; // Red
                default -> 0xFFFFFF;
            };
        }
    }

    /**
     * Record of a single hit.
     */
    public record HitRecord(
        UUID attackerId,
        String attackerName,
        long timestamp,
        float damage,
        @Nullable UUID questId
    ) {}

    /**
     * Result of a resonance check.
     */
    public record ResonanceResult(
        ResonanceTier tier,
        float damageMultiplier,
        int styleBonus,
        List<UUID> participants,
        int entityId
    ) {
        public boolean triggered() {
            return tier != ResonanceTier.NONE;
        }
    }

    /**
     * Record a hit on an entity and check for resonance.
     *
     * @param attacker The player who attacked
     * @param target The entity that was hit
     * @param damage The damage dealt
     * @param questId The quest ID (for filtering same-quest players)
     * @return ResonanceResult indicating if resonance was triggered
     */
    public ResonanceResult recordHit(
        ServerPlayer attacker,
        LivingEntity target,
        float damage,
        @Nullable UUID questId
    ) {
        // Check if resonance is enabled
        GameDesignConfig.ResonanceConfig config = getConfig(questId);
        if (!config.enabled) {
            return new ResonanceResult(ResonanceTier.NONE, 1.0f, 0, EMPTY_PARTICIPANTS, target.getId());
        }

        int entityId = target.getId();
        long now = System.currentTimeMillis();

        // Check cooldown (use config value)
        long cooldownMs = config.cooldownMs > 0 ? config.cooldownMs : RESONANCE_COOLDOWN_MS;
        Long lastResonance = resonanceCooldowns.get(entityId);
        if (lastResonance != null && now - lastResonance < cooldownMs) {
            return new ResonanceResult(ResonanceTier.NONE, 1.0f, 0, EMPTY_PARTICIPANTS, entityId);
        }

        // Create hit record
        UUID attackerId = attacker.getUUID();
        if (attackerId == null) {
            return new ResonanceResult(ResonanceTier.NONE, 1.0f, 0, EMPTY_PARTICIPANTS, target.getId());
        }
        String attackerName = resolveAttackerName(attacker);
        HitRecord record = new HitRecord(attackerId, attackerName, now, damage, questId);

        // Add to recent hits
        List<HitRecord> hits = recentHits.computeIfAbsent(entityId, k -> new ArrayList<>());
        synchronized (hits) {
            hits.add(record);

            // Remove expired hits
            hits.removeIf(h -> now - h.timestamp > HIT_EXPIRY_MS);
        }

        // Check for resonance
        return checkResonance(entityId, questId, now, target);
    }

    /**
     * Check if recent hits form a resonance chain.
     */
    private ResonanceResult checkResonance(
        int entityId,
        @Nullable UUID questId,
        long now,
        LivingEntity target
    ) {
        List<HitRecord> hits = recentHits.get(entityId);
        if (hits == null || hits.size() < 2) {
            return new ResonanceResult(ResonanceTier.NONE, 1.0f, 0, EMPTY_PARTICIPANTS, entityId);
        }

        // Get config values
        GameDesignConfig.ResonanceConfig config = getConfig(questId);
        long duoWindow = config.duoWindowMs > 0 ? config.duoWindowMs : DUO_WINDOW_MS;
        long trinityWindow = config.trinityWindowMs > 0 ? config.trinityWindowMs : TRINITY_WINDOW_MS;
        long apocalypseWindow = config.apocalypseWindowMs > 0 ? config.apocalypseWindowMs : APOCALYPSE_WINDOW_MS;

        // Filter to same quest and recent time window
        Set<UUID> uniqueAttackers = new HashSet<>();
        long earliestTime = Long.MAX_VALUE;
        long latestTime = 0;

        synchronized (hits) {
            for (HitRecord hit : hits) {
                if (!Objects.equals(hit.questId, questId)) continue;
                if (now - hit.timestamp > duoWindow) continue; // Outside widest window

                if (hit.attackerId != null) {
                    uniqueAttackers.add(hit.attackerId);
                }
                if (hit.timestamp < earliestTime) earliestTime = hit.timestamp;
                if (hit.timestamp > latestTime) latestTime = hit.timestamp;
            }
        }

        int playerCount = uniqueAttackers.size();
        if (playerCount < 2) {
            return new ResonanceResult(ResonanceTier.NONE, 1.0f, 0, EMPTY_PARTICIPANTS, entityId);
        }

        long timeSpan = latestTime - earliestTime;

        // Determine tier based on player count AND time window (using config values)
        ResonanceTier tier;
        float damageMultiplier;
        int styleBonus;

        if (playerCount >= 4 && timeSpan <= apocalypseWindow) {
            tier = ResonanceTier.APOCALYPSE;
            damageMultiplier = config.apocalypseDamageMultiplier;
            styleBonus = config.apocalypseStyleBonus;
        } else if (playerCount >= 3 && timeSpan <= trinityWindow) {
            tier = ResonanceTier.TRINITY;
            damageMultiplier = config.trinityDamageMultiplier;
            styleBonus = config.trinityStyleBonus;
        } else if (playerCount >= 2 && timeSpan <= duoWindow) {
            tier = ResonanceTier.DUO;
            damageMultiplier = config.duoDamageMultiplier;
            styleBonus = config.duoStyleBonus;
        } else {
            return new ResonanceResult(ResonanceTier.NONE, 1.0f, 0, EMPTY_PARTICIPANTS, entityId);
        }

        // Set cooldown
        resonanceCooldowns.put(entityId, now);

        // Clear hits for this entity to prevent double-triggering
        synchronized (hits) {
            hits.clear();
        }

        List<UUID> participants = List.copyOf(uniqueAttackers);

        String tierName = tier.name();
        LOGGER.info("[Resonance] {} triggered on entity {} by {} players! Time span: {}ms, damage: {}x, style: +{}",
            tierName, entityId, playerCount, timeSpan, damageMultiplier, styleBonus);

        // Apply effects (pass styleBonus from config, not tier defaults)
        applyResonanceEffects(tier, styleBonus, participants, target, questId);

        // Telemetry
        if (questId != null) {
            EnduranceTelemetryService.INSTANCE.recordResonanceChain(
                questId, tierName, playerCount, timeSpan, entityId
            );
        }

        return new ResonanceResult(tier, damageMultiplier, styleBonus, participants, entityId);
    }

    /**
     * Apply resonance effects to all participants.
     *
     * @param tier The resonance tier achieved
     * @param styleBonus The style bonus from config (may differ from tier default)
     * @param participants List of player UUIDs who participated
     * @param target The target entity
     * @param questId The quest ID for config lookup (nullable)
     */
    private void applyResonanceEffects(ResonanceTier tier, int styleBonus, List<UUID> participants,
                                        LivingEntity target, @Nullable UUID questId) {
        for (UUID playerId : participants) {
            // Add style bonus to combo session (using config value, not tier default)
            ComboSystem.INSTANCE.getSession(playerId).ifPresent(session -> {
                session.addBonusPoints(styleBonus);

                // Instant SSS for APOCALYPSE
                if (tier == ResonanceTier.APOCALYPSE) {
                    // Add enough points to reach SSS threshold
                    int sssThreshold = ComboSystem.StyleRank.SSS.threshold;
                    if (session.getStyleScore() < sssThreshold) {
                        session.addBonusPoints(sssThreshold - session.getStyleScore() + 1);
                    }
                }
            });

            // Get player and apply visual/audio feedback
            if (target.level() instanceof ServerLevel serverLevel && serverLevel.getServer() != null) {
                if (playerId == null) {
                    continue;
                }
                ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerId);
                if (player != null) {
                    // Sound effect
                    float pitch = switch (tier) {
                        case DUO -> 1.2f;
                        case TRINITY -> 1.5f;
                        case APOCALYPSE -> 2.0f;
                        default -> 1.0f;
                    };
                    playSound(player, SoundEvents.PLAYER_LEVELUP, 1.0f, pitch);

                    // Additional sound for higher tiers
                    if (tier == ResonanceTier.TRINITY || tier == ResonanceTier.APOCALYPSE) {
                        playSound(player, SoundEvents.TOTEM_USE, 0.5f, pitch);
                    }

                    // Send network packet for HUD notification
                    sendResonanceNotification(player, tier, styleBonus);
                }
            }
        }

        // Spawn particles at target location
        if (target.level() instanceof ServerLevel serverLevel) {
            double x = target.getX();
            double y = target.getY() + target.getBbHeight() / 2;
            double z = target.getZ();

            int particleCount = switch (tier) {
                case DUO -> 20;
                case TRINITY -> 40;
                case APOCALYPSE -> 80;
                default -> 0;
            };

            sendParticles(serverLevel, ParticleTypes.END_ROD, x, y, z,
                particleCount, 0.5, 0.5, 0.5, 0.1);

            // Extra particles for APOCALYPSE
            if (tier == ResonanceTier.APOCALYPSE) {
                sendParticles(serverLevel, ParticleTypes.EXPLOSION, x, y, z,
                    5, 0.3, 0.3, 0.3, 0);
            }

            // AoE shockwave for TRINITY and APOCALYPSE
            if (tier == ResonanceTier.TRINITY || tier == ResonanceTier.APOCALYPSE) {
                applyShockwave(serverLevel, target, tier, questId);
            }
        }
    }

    /**
     * Apply AoE shockwave damage for TRINITY/APOCALYPSE tiers.
     */
    private void applyShockwave(ServerLevel level, LivingEntity center, ResonanceTier tier,
                                 @Nullable UUID questId) {
        // Get config values for shockwave parameters
        GameDesignConfig.ResonanceConfig config = getConfig(questId);
        double radius = tier == ResonanceTier.APOCALYPSE
            ? config.apocalypseShockwaveRadius : config.trinityShockwaveRadius;
        float damage = tier == ResonanceTier.APOCALYPSE
            ? config.apocalypseShockwaveDamage : config.trinityShockwaveDamage;

        var bounds = center.getBoundingBox();
        if (bounds == null) {
            return;
        }
        bounds = bounds.inflate(radius);
        if (bounds == null) {
            return;
        }
        List<LivingEntity> nearby = level.getEntitiesOfClass(
            LivingEntity.class,
            bounds,
            e -> e != center && !(e instanceof ServerPlayer) && e.isAlive()
        );

        for (LivingEntity entity : nearby) {
            var magicDamage = level.damageSources().magic();
            if (magicDamage != null) {
                entity.hurt(magicDamage, damage);
            }

            // Knockback away from center
            double dx = entity.getX() - center.getX();
            double dz = entity.getZ() - center.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > 0) {
                double knockback = tier == ResonanceTier.APOCALYPSE ? 1.5 : 0.8;
                entity.setDeltaMovement(
                    dx / dist * knockback,
                    0.3,
                    dz / dist * knockback
                );
            }
        }

        LOGGER.debug("[Resonance] Shockwave hit {} entities for {} damage", nearby.size(), damage);
    }

    /**
     * Send network notification to player for HUD display.
     *
     * @param player The player to notify
     * @param tier The resonance tier achieved
     * @param styleBonus The style bonus from config (may differ from tier default)
     */
    private void sendResonanceNotification(ServerPlayer player, ResonanceTier tier, int styleBonus) {
        if (player == null) {
            return;
        }
        String tierName = tier.name();
        String announcement = tier.announcement;
        // Create and send payload (use config styleBonus, not tier default)
        ResonanceNotificationPayload payload = new ResonanceNotificationPayload(
            tierName,
            announcement,
            tier.getColor(),
            styleBonus
        );
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
    }

    /**
     * Periodic cleanup of expired hit records.
     */
    public void tick(long currentTick) {
        if (currentTick - lastCleanupTick < CLEANUP_INTERVAL_TICKS) {
            return;
        }
        lastCleanupTick = currentTick;

        long now = System.currentTimeMillis();

        // Clean up hit records
        recentHits.entrySet().removeIf(entry -> {
            List<HitRecord> hits = entry.getValue();
            synchronized (hits) {
                hits.removeIf(h -> now - h.timestamp > HIT_EXPIRY_MS);
                return hits.isEmpty();
            }
        });

        // Clean up cooldowns
        resonanceCooldowns.entrySet().removeIf(entry ->
            now - entry.getValue() > RESONANCE_COOLDOWN_MS * 2);
    }

    /**
     * Clear all data (call on quest end).
     */
    public void clear() {
        recentHits.clear();
        resonanceCooldowns.clear();
    }

    /**
     * Get recent hit count for an entity (for debugging).
     */
    public int getHitCount(int entityId) {
        List<HitRecord> hits = recentHits.get(entityId);
        return hits != null ? hits.size() : 0;
    }

    private static String resolveAttackerName(ServerPlayer attacker) {
        var nameComponent = attacker.getName();
        String name = nameComponent != null ? nameComponent.getString() : null;
        if (name == null || name.trim().isEmpty()) {
            return "Unknown";
        }
        return name;
    }

    private static void playSound(ServerPlayer player, net.minecraft.sounds.SoundEvent sound,
            float volume, float pitch) {
        if (player == null || sound == null) {
            return;
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void sendParticles(ServerLevel level, SimpleParticleType particle,
            double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        if (level == null || particle == null) {
            return;
        }
        level.sendParticles(particle, x, y, z, count, dx, dy, dz, speed);
    }
}
