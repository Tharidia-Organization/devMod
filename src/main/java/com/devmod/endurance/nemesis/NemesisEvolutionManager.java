package com.devmod.endurance.nemesis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import com.devmod.config.gamedesign.GameDesignConfig;
import com.devmod.config.gamedesign.GameDesignConfigManager;

/**
 * Nemesis Evolution Manager - Tracks boss memories and applies adaptations.
 *
 * Each player has separate nemesis profiles for each boss type they encounter.
 * Profiles persist across sessions and accumulate over time.
 */
public class NemesisEvolutionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(NemesisEvolutionManager.class);

    public static final NemesisEvolutionManager INSTANCE = new NemesisEvolutionManager();

    // Player UUID -> Boss Type -> Profile
    private final Map<UUID, Map<ResourceLocation, NemesisProfile>> playerProfiles = new ConcurrentHashMap<>();

    // Active tracking sessions (boss UUID -> tracking session)
    private final Map<UUID, TrackingSession> activeSessions = new ConcurrentHashMap<>();

    // Attribute modifier ID prefix
    private static final String MODIFIER_PREFIX = "nemesis_";

    private NemesisEvolutionManager() {}

    /**
     * Get config for the given quest instance.
     */
    private GameDesignConfig.NemesisConfig getConfig(@Nullable UUID questId) {
        return GameDesignConfigManager.INSTANCE.getNemesisConfig(questId);
    }

    /**
     * Get configured stat modifier for an adaptation.
     * Uses config values instead of hardcoded enum values.
     */
    public float getConfiguredStatModifier(NemesisAdaptation adaptation, NemesisAdaptation.StatType stat,
                                            @Nullable UUID questId) {
        GameDesignConfig.NemesisConfig config = getConfig(questId);

        return switch (adaptation) {
            case PROJECTILE_DEFLECTION -> stat == NemesisAdaptation.StatType.PROJECTILE_RESIST
                ? config.projectileDeflectionChance : 0f;
            case DAMAGE_RESISTANCE -> stat == NemesisAdaptation.StatType.DAMAGE_RESIST
                ? config.weaponTypeResistance : 0f;
            case PROTECTIVE_HELMET -> stat == NemesisAdaptation.StatType.HEAD_DAMAGE_RESIST
                ? config.headboxReduction : 0f;
            case IMPROVED_REFLEXES -> switch (stat) {
                case ATTACK_SPEED, MOVEMENT_SPEED -> config.improvedReflexesSpeedBonus;
                default -> 0f;
            };
            case ENRAGED -> stat == NemesisAdaptation.StatType.DAMAGE
                ? config.enragedDamageBonus : 0f;
            case VETERAN -> switch (stat) {
                case DAMAGE, HEALTH, ATTACK_SPEED -> config.veteranStatBonus;
                default -> 0f;
            };
            case REGENERATION -> stat == NemesisAdaptation.StatType.REGEN
                ? config.regenRatePerSecond : 0f;
            default -> adaptation.getStatModifier(stat); // Fallback to enum default
        };
    }

    // ========== Profile Management ==========

    /**
     * Get or create a nemesis profile for a player/boss combination.
     */
    public NemesisProfile getOrCreateProfile(UUID playerId, ResourceLocation bossType) {
        Map<ResourceLocation, NemesisProfile> bossProfiles = playerProfiles.computeIfAbsent(
            playerId, k -> new ConcurrentHashMap<>()
        );

        return bossProfiles.computeIfAbsent(bossType, NemesisProfile::new);
    }

    /**
     * Get existing profile if present.
     */
    @Nullable
    public NemesisProfile getProfile(UUID playerId, ResourceLocation bossType) {
        Map<ResourceLocation, NemesisProfile> bossProfiles = playerProfiles.get(playerId);
        if (bossProfiles == null) return null;
        return bossProfiles.get(bossType);
    }

    /**
     * Get all profiles for a player.
     */
    public Collection<NemesisProfile> getPlayerProfiles(UUID playerId) {
        Map<ResourceLocation, NemesisProfile> bossProfiles = playerProfiles.get(playerId);
        return bossProfiles != null ? bossProfiles.values() : Collections.emptyList();
    }

    // ========== Combat Tracking ==========

    /**
     * Start tracking a boss encounter.
     */
    public void startTracking(UUID bossId, UUID playerId, ResourceLocation bossType) {
        TrackingSession session = new TrackingSession(playerId, bossType, System.currentTimeMillis());
        activeSessions.put(bossId, session);

        LOGGER.debug("[Nemesis] Started tracking boss {} for player {}", bossType, playerId);
    }

    /**
     * Record a hit on the boss.
     */
    public void recordHit(UUID bossId, boolean isRanged, boolean isHeadshot, String weaponType) {
        TrackingSession session = activeSessions.get(bossId);
        if (session == null) return;

        session.rangedHits += isRanged ? 1 : 0;
        session.meleeHits += isRanged ? 0 : 1;
        session.headshots += isHeadshot ? 1 : 0;
        session.totalHits++;
        session.weaponTypes.merge(weaponType, 1, Integer::sum);
    }

    /**
     * Record player dodge near the boss.
     */
    public void recordDodge(UUID bossId, NemesisProfile.DodgeDirection direction) {
        TrackingSession session = activeSessions.get(bossId);
        if (session == null) return;

        session.dodges.add(direction);
    }

    /**
     * Record boss phase completion.
     */
    public void recordPhaseComplete(UUID bossId, int phase) {
        TrackingSession session = activeSessions.get(bossId);
        if (session == null) return;

        long now = System.currentTimeMillis();
        long phaseTime = now - (session.lastPhaseTime > 0 ? session.lastPhaseTime : session.startTime);
        session.phaseTimes.put(phase, phaseTime);
        session.lastPhaseTime = now;
    }

    /**
     * Record boss defeat and finalize tracking.
     */
    public void recordBossDefeat(UUID bossId) {
        TrackingSession session = activeSessions.remove(bossId);
        if (session == null) return;

        // Apply tracked data to profile
        NemesisProfile profile = getOrCreateProfile(session.playerId, session.bossType);

        // Record all hits
        for (int i = 0; i < session.totalHits; i++) {
            boolean isRanged = i < session.rangedHits;
            boolean isHeadshot = i < session.headshots;
            String weaponType = session.weaponTypes.keySet().stream().findFirst().orElse("unknown");
            profile.recordHit(isRanged, isHeadshot, weaponType);
        }

        // Record dodges
        for (NemesisProfile.DodgeDirection dodge : session.dodges) {
            profile.recordDodge(dodge);
        }

        // Record phase times
        for (var entry : session.phaseTimes.entrySet()) {
            profile.recordPhaseTime(entry.getKey(), entry.getValue());
        }

        // Record defeat
        profile.recordDefeat();

        LOGGER.info("[Nemesis] Boss {} defeated. Profile: {} defeats, {} adaptations",
            session.bossType, profile.getDefeatCount(), profile.getAdaptations().size());
    }

    /**
     * Record boss victory (player died).
     */
    public void recordBossVictory(UUID bossId) {
        TrackingSession session = activeSessions.remove(bossId);
        if (session == null) return;

        NemesisProfile profile = getOrCreateProfile(session.playerId, session.bossType);
        profile.recordVictory();

        LOGGER.debug("[Nemesis] Boss {} won against player", session.bossType);
    }

    /**
     * Stop tracking without result (quest abandoned, etc.)
     */
    public void cancelTracking(UUID bossId) {
        activeSessions.remove(bossId);
    }

    // ========== Adaptation Application ==========

    /**
     * Apply nemesis adaptations to a boss entity (backward compatible).
     * @deprecated Use {@link #applyAdaptations(Mob, UUID, ResourceLocation, UUID)} instead.
     */
    @Deprecated
    public void applyAdaptations(Mob boss, UUID playerId, ResourceLocation bossType) {
        applyAdaptations(boss, playerId, bossType, null);
    }

    /**
     * Apply nemesis adaptations to a boss entity.
     *
     * @param boss The boss mob to apply adaptations to
     * @param playerId The player UUID whose profile to use
     * @param bossType The boss type resource location
     * @param questId The quest ID for config lookup (nullable)
     */
    public void applyAdaptations(Mob boss, UUID playerId, ResourceLocation bossType, @Nullable UUID questId) {
        // Check if nemesis system is enabled
        GameDesignConfig.NemesisConfig config = getConfig(questId);
        if (!config.enabled) {
            return;
        }

        NemesisProfile profile = getProfile(playerId, bossType);
        if (profile == null || profile.getAdaptations().isEmpty()) {
            return;
        }

        Set<NemesisAdaptation> adaptations = profile.getAdaptations();
        LOGGER.info("[Nemesis] Applying {} adaptations to boss {}", adaptations.size(), bossType);

        // Apply stat modifiers (using config values)
        for (NemesisAdaptation adaptation : adaptations) {
            applyAdaptationStats(boss, adaptation, questId);
        }

        // Store adaptations in boss data for effect checking
        CompoundTag bossData = boss.getPersistentData();
        ListTag adaptationList = new ListTag();
        for (NemesisAdaptation adaptation : adaptations) {
            CompoundTag adaptTag = new CompoundTag();
            adaptTag.putString("id", adaptation.name());
            adaptationList.add(adaptTag);
        }
        bossData.put("nemesis_adaptations", adaptationList);
        bossData.putInt("nemesis_scar_level", profile.getScarLevel().level);
        bossData.putInt("nemesis_defeat_count", profile.getDefeatCount());

        // Store dominant weapon type for damage resistance
        String dominantWeapon = profile.getDominantWeaponType();
        if (dominantWeapon != null && adaptations.contains(NemesisAdaptation.DAMAGE_RESISTANCE)) {
            bossData.putString("nemesis_resist_type", dominantWeapon);
        }
    }

    /**
     * Apply stat modifiers from an adaptation.
     *
     * @param boss The boss mob to modify
     * @param adaptation The adaptation to apply
     * @param questId The quest ID for config lookup (nullable)
     */
    private void applyAdaptationStats(Mob boss, NemesisAdaptation adaptation, @Nullable UUID questId) {
        // Movement speed (using config values)
        float speedBonus = getConfiguredStatModifier(adaptation, NemesisAdaptation.StatType.MOVEMENT_SPEED, questId);
        if (speedBonus > 0 && boss.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            var speedAttr = boss.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) {
                speedAttr.addPermanentModifier(new AttributeModifier(
                    ResourceLocation.fromNamespaceAndPath("devmod", MODIFIER_PREFIX + adaptation.name().toLowerCase() + "_speed"),
                    speedBonus,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                ));
            }
        }

        // Attack speed (if applicable, using config values)
        float attackSpeedBonus = getConfiguredStatModifier(adaptation, NemesisAdaptation.StatType.ATTACK_SPEED, questId);
        if (attackSpeedBonus > 0 && boss.getAttribute(Attributes.ATTACK_SPEED) != null) {
            var attackAttr = boss.getAttribute(Attributes.ATTACK_SPEED);
            if (attackAttr != null) {
                attackAttr.addPermanentModifier(new AttributeModifier(
                    ResourceLocation.fromNamespaceAndPath("devmod", MODIFIER_PREFIX + adaptation.name().toLowerCase() + "_attack"),
                    attackSpeedBonus,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                ));
            }
        }

        // Max health (using config values)
        float healthBonus = getConfiguredStatModifier(adaptation, NemesisAdaptation.StatType.HEALTH, questId);
        if (healthBonus > 0 && boss.getAttribute(Attributes.MAX_HEALTH) != null) {
            var healthAttr = boss.getAttribute(Attributes.MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.addPermanentModifier(new AttributeModifier(
                    ResourceLocation.fromNamespaceAndPath("devmod", MODIFIER_PREFIX + adaptation.name().toLowerCase() + "_health"),
                    healthBonus,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                ));
                // Heal to new max
                boss.setHealth(boss.getMaxHealth());
            }
        }

        // Attack damage (using config values)
        float damageBonus = getConfiguredStatModifier(adaptation, NemesisAdaptation.StatType.DAMAGE, questId);
        if (damageBonus > 0 && boss.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            var damageAttr = boss.getAttribute(Attributes.ATTACK_DAMAGE);
            if (damageAttr != null) {
                damageAttr.addPermanentModifier(new AttributeModifier(
                    ResourceLocation.fromNamespaceAndPath("devmod", MODIFIER_PREFIX + adaptation.name().toLowerCase() + "_damage"),
                    damageBonus,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                ));
            }
        }
    }

    /**
     * Check if a boss has a specific adaptation.
     */
    public boolean hasAdaptation(Mob boss, NemesisAdaptation adaptation) {
        CompoundTag data = boss.getPersistentData();
        if (!data.contains("nemesis_adaptations")) return false;

        ListTag adaptations = data.getList("nemesis_adaptations", Tag.TAG_COMPOUND);
        for (int i = 0; i < adaptations.size(); i++) {
            if (adaptations.getCompound(i).getString("id").equals(adaptation.name())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the scar level for a boss.
     */
    public int getScarLevel(Mob boss) {
        return boss.getPersistentData().getInt("nemesis_scar_level");
    }

    /**
     * Get the weapon type this boss resists.
     */
    @Nullable
    public String getResistType(Mob boss) {
        CompoundTag data = boss.getPersistentData();
        return data.contains("nemesis_resist_type") ? data.getString("nemesis_resist_type") : null;
    }

    // ========== NBT Serialization ==========

    /**
     * Save all nemesis data to NBT.
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        ListTag playersList = new ListTag();
        for (var playerEntry : playerProfiles.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("playerId", playerEntry.getKey());

            ListTag bossList = new ListTag();
            for (var bossEntry : playerEntry.getValue().entrySet()) {
                CompoundTag bossTag = new CompoundTag();
                bossTag.putString("bossType", bossEntry.getKey().toString());
                bossTag.put("profile", bossEntry.getValue().save());
                bossList.add(bossTag);
            }
            playerTag.put("bosses", bossList);
            playersList.add(playerTag);
        }
        tag.put("players", playersList);

        return tag;
    }

    /**
     * Load all nemesis data from NBT.
     */
    public void load(CompoundTag tag) {
        playerProfiles.clear();

        if (tag.contains("players")) {
            ListTag playersList = tag.getList("players", Tag.TAG_COMPOUND);
            for (int i = 0; i < playersList.size(); i++) {
                CompoundTag playerTag = playersList.getCompound(i);
                UUID playerId = playerTag.getUUID("playerId");

                Map<ResourceLocation, NemesisProfile> bossProfiles = new ConcurrentHashMap<>();
                ListTag bossList = playerTag.getList("bosses", Tag.TAG_COMPOUND);
                for (int j = 0; j < bossList.size(); j++) {
                    CompoundTag bossTag = bossList.getCompound(j);
                    ResourceLocation bossType = ResourceLocation.tryParse(bossTag.getString("bossType"));
                    if (bossType != null) {
                        NemesisProfile profile = NemesisProfile.load(bossTag.getCompound("profile"));
                        bossProfiles.put(bossType, profile);
                    }
                }
                playerProfiles.put(playerId, bossProfiles);
            }
        }

        LOGGER.info("[Nemesis] Loaded {} player nemesis profiles", playerProfiles.size());
    }

    /**
     * Clear all data.
     */
    public void clear() {
        playerProfiles.clear();
        activeSessions.clear();
    }

    // ========== Tracking Session ==========

    /**
     * Internal class to track an active boss encounter.
     */
    private static class TrackingSession {
        final UUID playerId;
        final ResourceLocation bossType;
        final long startTime;

        int rangedHits = 0;
        int meleeHits = 0;
        int headshots = 0;
        int totalHits = 0;
        final Map<String, Integer> weaponTypes = new HashMap<>();
        final List<NemesisProfile.DodgeDirection> dodges = new ArrayList<>();
        final Map<Integer, Long> phaseTimes = new HashMap<>();
        long lastPhaseTime = 0;

        TrackingSession(UUID playerId, ResourceLocation bossType, long startTime) {
            this.playerId = playerId;
            this.bossType = bossType;
            this.startTime = startTime;
        }
    }
}
