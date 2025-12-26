package com.devmod.combat.signature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import static java.util.Objects.requireNonNull;

/**
 * Manages Soul Imprints for weapons during Endurance Quests.
 *
 * Tracks combat statistics and applies them to the player's held weapon,
 * allowing weapons to evolve and gain permanent traits over time.
 */
public class SoulImprintManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(SoulImprintManager.class);

    public static final SoulImprintManager INSTANCE = new SoulImprintManager();

    // Cache of active imprints being tracked (player UUID -> weapon hash -> imprint)
    private final Map<UUID, Map<Integer, SoulImprint>> activeImprints = new ConcurrentHashMap<>();

    // Pending trait unlocks to notify players about
    private final Map<UUID, List<TraitUnlockEvent>> pendingUnlocks = new ConcurrentHashMap<>();

    private SoulImprintManager() {}

    // ========== Stat Recording ==========

    /**
     * Record a kill with the player's current weapon.
     */
    public void recordKill(ServerPlayer player, LivingEntity target, boolean isBoss) {
        SoulImprint imprint = getOrCreateImprint(player);
        if (imprint == null) return;

        boolean unlockedNew = imprint.recordStat(SoulImprint.ImprintStat.TOTAL_KILLS, 1);
        if (unlockedNew) {
            queueUnlockNotification(player, imprint.getPrimaryTrait());
        }

        if (isBoss) {
            unlockedNew = imprint.recordStat(SoulImprint.ImprintStat.BOSS_KILLS, 1);
            if (unlockedNew) {
                queueUnlockNotification(player, WeaponTraitRegistry.TYRANT_SLAYER);
            }
        }

        // Save after recording
        saveImprint(player, imprint);
    }

    /**
     * Record a headshot kill.
     */
    public void recordHeadshot(ServerPlayer player) {
        SoulImprint imprint = getOrCreateImprint(player);
        if (imprint == null) return;

        boolean unlockedNew = imprint.recordStat(SoulImprint.ImprintStat.HEADSHOTS, 1);
        if (unlockedNew) {
            queueUnlockNotification(player, WeaponTraitRegistry.EXECUTIONER);
        }

        saveImprint(player, imprint);
    }

    /**
     * Record a critical hit.
     */
    public void recordCriticalHit(ServerPlayer player) {
        SoulImprint imprint = getOrCreateImprint(player);
        if (imprint == null) return;

        boolean unlockedNew = imprint.recordStat(SoulImprint.ImprintStat.CRITICAL_HITS, 1);
        if (unlockedNew) {
            queueUnlockNotification(player, WeaponTraitRegistry.PRECISION);
        }

        saveImprint(player, imprint);
    }

    /**
     * Record damage dealt.
     */
    public void recordDamage(ServerPlayer player, float damage) {
        SoulImprint imprint = getOrCreateImprint(player);
        if (imprint == null) return;

        boolean unlockedNew = imprint.recordStat(SoulImprint.ImprintStat.TOTAL_DAMAGE, (int) damage);
        if (unlockedNew) {
            queueUnlockNotification(player, WeaponTraitRegistry.DEVASTATING);
        }

        saveImprint(player, imprint);
    }

    /**
     * Record completing a wave with SSS rank.
     */
    public void recordSSSWave(ServerPlayer player) {
        SoulImprint imprint = getOrCreateImprint(player);
        if (imprint == null) return;

        boolean unlockedNew = imprint.recordStat(SoulImprint.ImprintStat.SSS_WAVES, 1);
        if (unlockedNew) {
            queueUnlockNotification(player, WeaponTraitRegistry.STYLISH);
        }

        saveImprint(player, imprint);
    }

    /**
     * Record a perfect resonance chain.
     */
    public void recordPerfectResonance(ServerPlayer player) {
        SoulImprint imprint = getOrCreateImprint(player);
        if (imprint == null) return;

        boolean unlockedNew = imprint.recordStat(SoulImprint.ImprintStat.PERFECT_RESONANCES, 1);
        if (unlockedNew) {
            queueUnlockNotification(player, WeaponTraitRegistry.HARMONIC);
        }

        saveImprint(player, imprint);
    }

    /**
     * Record reaching a 50+ combo.
     */
    public void recordHighCombo(ServerPlayer player) {
        SoulImprint imprint = getOrCreateImprint(player);
        if (imprint == null) return;

        boolean unlockedNew = imprint.recordStat(SoulImprint.ImprintStat.HIGH_COMBOS, 1);
        if (unlockedNew) {
            queueUnlockNotification(player, WeaponTraitRegistry.RELENTLESS);
        }

        saveImprint(player, imprint);
    }

    /**
     * Record completing a wave without taking damage.
     */
    public void recordNoHitWave(ServerPlayer player) {
        SoulImprint imprint = getOrCreateImprint(player);
        if (imprint == null) return;

        boolean unlockedNew = imprint.recordStat(SoulImprint.ImprintStat.NO_HIT_WAVES, 1);
        if (unlockedNew) {
            queueUnlockNotification(player, WeaponTraitRegistry.GUARDIAN);
        }

        saveImprint(player, imprint);
    }

    /**
     * Record an execute kill (below threshold).
     */
    public void recordExecuteKill(ServerPlayer player) {
        SoulImprint imprint = getOrCreateImprint(player);
        if (imprint == null) return;

        boolean unlockedNew = imprint.recordStat(SoulImprint.ImprintStat.EXECUTE_KILLS, 1);
        if (unlockedNew) {
            queueUnlockNotification(player, WeaponTraitRegistry.FINISHER);
        }

        saveImprint(player, imprint);
    }

    /**
     * Record a multi-kill (2+ kills in quick succession).
     */
    public void recordMultiKill(ServerPlayer player) {
        SoulImprint imprint = getOrCreateImprint(player);
        if (imprint == null) return;

        boolean unlockedNew = imprint.recordStat(SoulImprint.ImprintStat.MULTI_KILLS, 1);
        if (unlockedNew) {
            queueUnlockNotification(player, WeaponTraitRegistry.CLEAVING);
        }

        saveImprint(player, imprint);
    }

    /**
     * Record a parry counter kill.
     */
    public void recordParryKill(ServerPlayer player) {
        SoulImprint imprint = getOrCreateImprint(player);
        if (imprint == null) return;

        boolean unlockedNew = imprint.recordStat(SoulImprint.ImprintStat.PARRY_KILLS, 1);
        if (unlockedNew) {
            queueUnlockNotification(player, WeaponTraitRegistry.RETALIATING);
        }

        saveImprint(player, imprint);
    }

    // ========== Imprint Management ==========

    /**
     * Get or create a soul imprint for the player's current weapon.
     */
    @Nullable
    public SoulImprint getOrCreateImprint(ServerPlayer player) {
        ItemStack weapon = player.getMainHandItem();
        if (weapon.isEmpty() || !isValidWeapon(weapon)) {
            return null;
        }

        UUID playerId = player.getUUID();
        int weaponHash = getWeaponHash(weapon);

        // Check cache first
        Map<Integer, SoulImprint> playerImprints = activeImprints.computeIfAbsent(
            playerId, k -> new ConcurrentHashMap<>()
        );

        SoulImprint cached = playerImprints.get(weaponHash);
        if (cached != null) {
            return cached;
        }

        // Load from item or create new
        SoulImprint imprint = SoulImprint.loadFromItem(weapon);
        if (imprint == null) {
            imprint = new SoulImprint(playerId, player.getName().getString());
            LOGGER.debug("[SoulImprint] Created new imprint for {} with weapon {}",
                player.getName().getString(), weapon.getDisplayName().getString());
        }

        playerImprints.put(weaponHash, imprint);
        return imprint;
    }

    /**
     * Get imprint from a specific item stack.
     */
    @Nullable
    public SoulImprint getImprint(ItemStack stack) {
        return SoulImprint.loadFromItem(stack);
    }

    /**
     * Save imprint to the player's current weapon.
     */
    private void saveImprint(ServerPlayer player, SoulImprint imprint) {
        ItemStack weapon = player.getMainHandItem();
        if (!weapon.isEmpty() && isValidWeapon(weapon)) {
            imprint.saveToItem(weapon);
        }
    }

    /**
     * Force save all cached imprints for a player.
     */
    public void flushPlayerImprints(ServerPlayer player) {
        UUID playerId = player.getUUID();
        Map<Integer, SoulImprint> playerImprints = activeImprints.remove(playerId);

        if (playerImprints != null && !playerImprints.isEmpty()) {
            ItemStack weapon = player.getMainHandItem();
            if (!weapon.isEmpty()) {
                int hash = getWeaponHash(weapon);
                SoulImprint imprint = playerImprints.get(hash);
                if (imprint != null) {
                    imprint.saveToItem(weapon);
                    LOGGER.debug("[SoulImprint] Flushed imprint for {} (kills: {}, traits: {})",
                        player.getName().getString(),
                        imprint.getTotalKills(),
                        imprint.getTraitCount());
                }
            }
        }
    }

    /**
     * Clear all cached imprints (server shutdown).
     */
    public void clearAll() {
        activeImprints.clear();
        pendingUnlocks.clear();
    }

    // ========== Trait Effects ==========

    /**
     * Get combined trait effects for damage calculation.
     */
    @SuppressWarnings("deprecation") // WeaponTraitRegistry.getCombinedEffect - migration pending
    public TraitEffects getTraitEffects(ItemStack weapon) {
        SoulImprint imprint = SoulImprint.loadFromItem(weapon);
        if (imprint == null) {
            return TraitEffects.EMPTY;
        }

        Set<WeaponTrait> traits = imprint.getUnlockedTraits();
        if (traits.isEmpty()) {
            return TraitEffects.EMPTY;
        }

        float damagePercent = WeaponTraitRegistry.getCombinedEffect(traits,
            WeaponTrait.TraitEffectType.DAMAGE_PERCENT);
        float headshotBonus = WeaponTraitRegistry.getCombinedEffect(traits,
            WeaponTrait.TraitEffectType.HEADSHOT_BONUS);
        float bossDamage = WeaponTraitRegistry.getCombinedEffect(traits,
            WeaponTrait.TraitEffectType.BOSS_DAMAGE);
        float styleGain = WeaponTraitRegistry.getCombinedEffect(traits,
            WeaponTrait.TraitEffectType.STYLE_GAIN);
        float lifesteal = WeaponTraitRegistry.getCombinedEffect(traits,
            WeaponTrait.TraitEffectType.LIFESTEAL);
        float critChance = WeaponTraitRegistry.getCombinedEffect(traits,
            WeaponTrait.TraitEffectType.CRIT_CHANCE);
        float comboDecay = WeaponTraitRegistry.getCombinedEffect(traits,
            WeaponTrait.TraitEffectType.COMBO_DECAY);
        float damageReduction = WeaponTraitRegistry.getCombinedEffect(traits,
            WeaponTrait.TraitEffectType.DAMAGE_REDUCTION);
        float resonanceBonus = WeaponTraitRegistry.getCombinedEffect(traits,
            WeaponTrait.TraitEffectType.RESONANCE_BONUS);
        float executeThreshold = WeaponTraitRegistry.getCombinedEffect(traits,
            WeaponTrait.TraitEffectType.EXECUTE_THRESHOLD);

        return new TraitEffects(
            damagePercent, headshotBonus, bossDamage, styleGain,
            lifesteal, critChance, comboDecay, damageReduction,
            resonanceBonus, executeThreshold
        );
    }

    /**
     * Combined trait effects for easy access.
     */
    public record TraitEffects(
        float damagePercent,
        float headshotBonus,
        float bossDamage,
        float styleGain,
        float lifesteal,
        float critChance,
        float comboDecay,
        float damageReduction,
        float resonanceBonus,
        float executeThreshold
    ) {
        public static final TraitEffects EMPTY = new TraitEffects(
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f
        );

        public boolean hasEffects() {
            return damagePercent > 0 || headshotBonus > 0 || bossDamage > 0 ||
                   styleGain > 0 || lifesteal > 0 || critChance > 0 ||
                   comboDecay > 0 || damageReduction > 0 || resonanceBonus > 0 ||
                   executeThreshold > 0;
        }
    }

    // ========== Notifications ==========

    /**
     * Queue a trait unlock notification for the player.
     */
    private void queueUnlockNotification(ServerPlayer player, @Nullable WeaponTrait trait) {
        if (trait == null) return;

        UUID playerId = player.getUUID();
        pendingUnlocks.computeIfAbsent(playerId, k -> new ArrayList<>())
            .add(new TraitUnlockEvent(trait, System.currentTimeMillis()));

        LOGGER.info("[SoulImprint] {} unlocked trait: {}",
            player.getName().getString(), trait.getId());
    }

    /**
     * Get and clear pending unlock notifications for a player.
     */
    public List<TraitUnlockEvent> consumePendingUnlocks(UUID playerId) {
        List<TraitUnlockEvent> unlocks = pendingUnlocks.remove(playerId);
        return unlocks != null ? unlocks : Collections.emptyList();
    }

    /**
     * Check if player has pending unlock notifications.
     */
    public boolean hasPendingUnlocks(UUID playerId) {
        List<TraitUnlockEvent> unlocks = pendingUnlocks.get(playerId);
        return unlocks != null && !unlocks.isEmpty();
    }

    /**
     * Trait unlock event data.
     */
    public record TraitUnlockEvent(WeaponTrait trait, long timestamp) {}

    // ========== Utility ==========

    /**
     * Check if an item is a valid weapon for soul imprinting.
     */
    private boolean isValidWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;

        // Check for weapon-like attributes
        var attributes = stack.getAttributeModifiers();

        // Check if it has attack damage attribute
        return attributes.modifiers().stream()
            .anyMatch(entry -> entry.attribute().value().getDescriptionId()
                .contains("attack_damage"));
    }

    /**
     * Get a hash for the weapon to track it across sessions.
     * Uses item type + any custom name for differentiation.
     */
    private int getWeaponHash(ItemStack stack) {
        int hash = stack.getItem().hashCode();
        // Check for custom name using DataComponents (1.21.1 API)
        if (stack.has(requireNonNull(DataComponents.CUSTOM_NAME))) {
            hash = hash * 31 + stack.getHoverName().getString().hashCode();
        }
        return hash;
    }
}
