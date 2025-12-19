package com.frenkvs.devmod.endurance;

import com.frenkvs.devmod.telemetry.endurance.EnduranceTelemetryService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Roguelike Perk System for Endurance Quests.
 *
 * After each wave, players choose from 3 random perks that modify their abilities.
 * Perks stack and synergize with each other, creating unique builds each run.
 *
 * Features:
 * - Tiered perks (Common, Uncommon, Rare, Epic, Legendary)
 * - Perk synergies and combos
 * - Curse perks (negative effects for bonus rewards)
 * - Meta-progression unlocks
 */
public class PerkSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(PerkSystem.class);

    public static final PerkSystem INSTANCE = new PerkSystem();

    // All available perks
    private final Map<String, Perk> allPerks = new LinkedHashMap<>();

    // Player perk sessions
    private final Map<UUID, PerkSession> activeSessions = new ConcurrentHashMap<>();

    // Meta-progression: unlocked perks per player (persistent)
    private final Map<UUID, Set<String>> unlockedPerks = new ConcurrentHashMap<>();

    private final Random random = new Random();

    /**
     * Perk rarity tiers.
     */
    public enum PerkTier {
        COMMON(0xAAAAAA, "Common", 60),      // 60% chance
        UNCOMMON(0x4ade80, "Uncommon", 25),  // 25% chance
        RARE(0x60a5fa, "Rare", 10),          // 10% chance
        EPIC(0xa855f7, "Epic", 4),           // 4% chance
        LEGENDARY(0xfbbf24, "Legendary", 1); // 1% chance

        public final int color;
        public final String displayName;
        public final int weight;

        PerkTier(int color, String displayName, int weight) {
            this.color = color;
            this.displayName = displayName;
            this.weight = weight;
        }
    }

    /**
     * Perk categories for organization and synergies.
     */
    public enum PerkCategory {
        OFFENSE("Offense", 0xFF6B6B),      // Damage, crits, attack speed
        DEFENSE("Defense", 0x4ECDC4),       // Health, armor, resistance
        UTILITY("Utility", 0xFFE66D),       // Movement, cooldowns, resource
        VAMPIRIC("Vampiric", 0x9B59B6),     // Life steal, drain effects
        ELEMENTAL("Elemental", 0xE74C3C),   // Fire, ice, lightning
        COMBO("Combo", 0xF39C12),           // Style and combo bonuses
        CURSE("Curse", 0x2C3E50);           // Negative effects for rewards

        public final String displayName;
        public final int color;

        PerkCategory(String displayName, int color) {
            this.displayName = displayName;
            this.color = color;
        }
    }

    /**
     * A single perk definition.
     */
    public static class Perk {
        public final String id;
        public final String name;
        public final String description;
        public final PerkTier tier;
        public final PerkCategory category;
        public final String iconPath;

        // Can this perk stack?
        public final boolean stackable;
        public final int maxStacks;

        // Required perks to appear (synergy)
        public final Set<String> requiredPerks;

        // Perks that make this one not appear
        public final Set<String> incompatiblePerks;

        // Effect when acquired
        private final Consumer<PerkContext> onAcquire;

        // Effect every tick (if applicable)
        private final Consumer<PerkContext> onTick;

        // Effect on specific events
        private final Consumer<PerkContext> onHit;
        private final Consumer<PerkContext> onKill;
        private final Consumer<PerkContext> onDamageTaken;

        public Perk(String id, String name, String description, PerkTier tier, PerkCategory category,
                    boolean stackable, int maxStacks, Consumer<PerkContext> onAcquire) {
            this(id, name, description, tier, category, null, stackable, maxStacks,
                Set.of(), Set.of(), onAcquire, null, null, null, null);
        }

        public Perk(String id, String name, String description, PerkTier tier, PerkCategory category,
                    String iconPath, boolean stackable, int maxStacks,
                    Set<String> requiredPerks, Set<String> incompatiblePerks,
                    Consumer<PerkContext> onAcquire, Consumer<PerkContext> onTick,
                    Consumer<PerkContext> onHit, Consumer<PerkContext> onKill,
                    Consumer<PerkContext> onDamageTaken) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.tier = tier;
            this.category = category;
            this.iconPath = iconPath;
            this.stackable = stackable;
            this.maxStacks = maxStacks;
            this.requiredPerks = requiredPerks;
            this.incompatiblePerks = incompatiblePerks;
            this.onAcquire = onAcquire;
            this.onTick = onTick;
            this.onHit = onHit;
            this.onKill = onKill;
            this.onDamageTaken = onDamageTaken;
        }

        public void apply(PerkContext context) {
            if (onAcquire != null) onAcquire.accept(context);
        }

        public void tick(PerkContext context) {
            if (onTick != null) onTick.accept(context);
        }

        public void onHitEnemy(PerkContext context) {
            if (onHit != null) onHit.accept(context);
        }

        public void onKillEnemy(PerkContext context) {
            if (onKill != null) onKill.accept(context);
        }

        public void onTakeDamage(PerkContext context) {
            if (onDamageTaken != null) onDamageTaken.accept(context);
        }
    }

    /**
     * Context passed to perk effects.
     */
    public static class PerkContext {
        public final ServerPlayer player;
        public final PerkSession session;
        public final int stackCount;

        // Event-specific data
        public float damage;
        public float damageMultiplier = 1.0f;
        public boolean isCritical;

        public PerkContext(ServerPlayer player, PerkSession session, int stackCount) {
            this.player = player;
            this.session = session;
            this.stackCount = stackCount;
        }
    }

    /**
     * Player's perk state during a quest.
     */
    public static class PerkSession {
        private final UUID playerId;
        private final UUID questId;

        // Acquired perks and their stack counts
        private final Map<String, Integer> acquiredPerks = new LinkedHashMap<>();

        // Stat modifiers from perks
        private float damageMultiplier = 1.0f;
        private float attackSpeedMultiplier = 1.0f;
        private float movementSpeedMultiplier = 1.0f;
        private float damageReductionMultiplier = 1.0f;
        private float lifestealPercent = 0f;
        private float critChanceBonus = 0f;
        private float critDamageMultiplier = 1.5f;

        // Elemental effects
        private float fireChance = 0f;
        private float freezeChance = 0f;
        private float lightningChance = 0f;

        // Combo/style bonuses
        private float styleMultiplier = 1.0f;
        private float comboDecayReduction = 0f;

        // Curse effects (for bonus rewards)
        private int curseLevel = 0;
        private float curseRewardMultiplier = 1.0f;

        // Pending perk choices
        private List<Perk> pendingChoices = new ArrayList<>();

        // Applied attribute modifiers (for cleanup)
        private final List<AttributeModifier> appliedModifiers = new ArrayList<>();

        public PerkSession(UUID playerId, UUID questId) {
            this.playerId = playerId;
            this.questId = questId;
        }

        public boolean hasPerk(String perkId) {
            return acquiredPerks.containsKey(perkId);
        }

        public int getPerkStacks(String perkId) {
            return acquiredPerks.getOrDefault(perkId, 0);
        }

        public void addPerk(String perkId) {
            acquiredPerks.merge(perkId, 1, (a, b) -> a + b);
        }

        public Set<String> getAcquiredPerkIds() {
            return acquiredPerks.keySet();
        }

        public int getTotalPerksAcquired() {
            return acquiredPerks.values().stream().mapToInt(Integer::intValue).sum();
        }

        // Stat getters and modifiers
        public float getDamageMultiplier() { return damageMultiplier; }
        public void addDamageMultiplier(float amount) { damageMultiplier += amount; }

        public float getAttackSpeedMultiplier() { return attackSpeedMultiplier; }
        public void addAttackSpeedMultiplier(float amount) { attackSpeedMultiplier += amount; }

        public float getMovementSpeedMultiplier() { return movementSpeedMultiplier; }
        public void addMovementSpeedMultiplier(float amount) { movementSpeedMultiplier += amount; }

        public float getDamageReductionMultiplier() { return damageReductionMultiplier; }
        public void addDamageReduction(float amount) { damageReductionMultiplier -= amount; }

        public float getLifestealPercent() { return lifestealPercent; }
        public void addLifesteal(float amount) { lifestealPercent += amount; }

        public float getCritChanceBonus() { return critChanceBonus; }
        public void addCritChance(float amount) { critChanceBonus += amount; }

        public float getCritDamageMultiplier() { return critDamageMultiplier; }
        public void addCritDamage(float amount) { critDamageMultiplier += amount; }

        public float getFireChance() { return fireChance; }
        public void addFireChance(float amount) { fireChance += amount; }

        public float getFreezeChance() { return freezeChance; }
        public void addFreezeChance(float amount) { freezeChance += amount; }

        public float getLightningChance() { return lightningChance; }
        public void addLightningChance(float amount) { lightningChance += amount; }

        public float getStyleMultiplier() { return styleMultiplier; }
        public void addStyleMultiplier(float amount) { styleMultiplier += amount; }

        public float getComboDecayReduction() { return comboDecayReduction; }
        public void addComboDecayReduction(float amount) { comboDecayReduction = Math.min(0.9f, comboDecayReduction + amount); }

        public int getCurseLevel() { return curseLevel; }
        public void addCurse(int level) {
            curseLevel += level;
            curseRewardMultiplier += level * 0.25f; // +25% rewards per curse level
        }

        public float getCurseRewardMultiplier() { return curseRewardMultiplier; }

        public List<Perk> getPendingChoices() { return pendingChoices; }
        public void setPendingChoices(List<Perk> choices) { this.pendingChoices = choices; }
        public void clearPendingChoices() { pendingChoices.clear(); }

        public UUID getPlayerId() { return playerId; }
        public UUID getQuestId() { return questId; }

        public void addAppliedModifier(AttributeModifier modifier) {
            appliedModifiers.add(modifier);
        }

        public List<AttributeModifier> getAppliedModifiers() {
            return appliedModifiers;
        }
    }

    // ========== Initialization ==========

    private PerkSystem() {
        initializePerks();
    }

    private void initializePerks() {
        // === OFFENSE PERKS ===

        // NOTE: Stackable perks use fixed per-stack bonuses (not multiplied by stackCount)
        // because the callback is called each time the perk is acquired, not once for all stacks

        registerPerk(new Perk("sharp_blades", "Sharp Blades", "+15% damage per stack", PerkTier.COMMON, PerkCategory.OFFENSE,
            true, 5, ctx -> ctx.session.addDamageMultiplier(0.15f)));

        registerPerk(new Perk("fury", "Fury", "+10% attack speed per stack", PerkTier.COMMON, PerkCategory.OFFENSE,
            true, 5, ctx -> ctx.session.addAttackSpeedMultiplier(0.10f)));

        registerPerk(new Perk("critical_eye", "Critical Eye", "+10% crit chance per stack", PerkTier.UNCOMMON, PerkCategory.OFFENSE,
            true, 3, ctx -> ctx.session.addCritChance(0.10f)));

        registerPerk(new Perk("executioner", "Executioner", "+50% crit damage per stack", PerkTier.RARE, PerkCategory.OFFENSE,
            true, 2, ctx -> ctx.session.addCritDamage(0.50f)));

        registerPerk(new Perk("berserker", "Berserker", "+5% damage per 10% missing HP", PerkTier.EPIC, PerkCategory.OFFENSE,
            false, 1, ctx -> {
                // Calculated dynamically during combat
            }));

        registerPerk(new Perk("glass_cannon", "Glass Cannon", "+100% damage, +50% damage taken", PerkTier.LEGENDARY, PerkCategory.OFFENSE,
            false, 1, ctx -> {
                ctx.session.addDamageMultiplier(1.0f);
                ctx.session.addDamageReduction(-0.5f); // More damage taken
            }));

        // === DEFENSE PERKS ===

        registerPerk(new Perk("tough_skin", "Tough Skin", "-10% damage taken per stack", PerkTier.COMMON, PerkCategory.DEFENSE,
            true, 5, ctx -> ctx.session.addDamageReduction(0.10f)));

        registerPerk(new Perk("vitality", "Vitality", "+2 max hearts", PerkTier.UNCOMMON, PerkCategory.DEFENSE,
            true, 5, ctx -> {
                var attr = ctx.player.getAttribute(Objects.requireNonNull(Attributes.MAX_HEALTH));
                if (attr != null) {
                    // Each stack uses a unique modifier ID so they don't conflict
                    // Only add the INCREMENTAL bonus (4.0 HP per new stack, not cumulative)
                    ResourceLocation modId = Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "vitality_stack_" + ctx.stackCount));
                    AttributeModifier mod = new AttributeModifier(
                        modId,
                        4.0, // Fixed per-stack bonus, not multiplied by stackCount
                        AttributeModifier.Operation.ADD_VALUE
                    );
                    attr.addTransientModifier(mod);
                    ctx.session.addAppliedModifier(mod);
                    ctx.player.setHealth(ctx.player.getHealth() + 4.0f);
                }
            }));

        registerPerk(new Perk("regeneration", "Regeneration", "Slowly regenerate health", PerkTier.RARE, PerkCategory.DEFENSE,
            false, 1, null)); // Applied via tick

        registerPerk(new Perk("second_wind", "Second Wind", "Survive fatal blow once per wave", PerkTier.EPIC, PerkCategory.DEFENSE,
            false, 1, ctx -> {
                // Tracked separately
            }));

        registerPerk(new Perk("immortal", "Immortal", "Cannot die for 3 seconds after taking fatal damage", PerkTier.LEGENDARY, PerkCategory.DEFENSE,
            false, 1, ctx -> {
                // Tracked separately
            }));

        // === UTILITY PERKS ===

        registerPerk(new Perk("swift_feet", "Swift Feet", "+15% movement speed per stack", PerkTier.COMMON, PerkCategory.UTILITY,
            true, 3, ctx -> ctx.session.addMovementSpeedMultiplier(0.15f)));

        registerPerk(new Perk("treasure_hunter", "Treasure Hunter", "+25% reward points", PerkTier.UNCOMMON, PerkCategory.UTILITY,
            true, 3, ctx -> {
                // Applied to rewards
            }));

        registerPerk(new Perk("combo_master", "Combo Master", "-25% combo decay rate per stack", PerkTier.RARE, PerkCategory.COMBO,
            true, 3, ctx -> ctx.session.addComboDecayReduction(0.25f)));

        // === VAMPIRIC PERKS ===

        registerPerk(new Perk("lifesteal", "Lifesteal", "Heal for 5% of damage dealt per stack", PerkTier.UNCOMMON, PerkCategory.VAMPIRIC,
            true, 4, ctx -> ctx.session.addLifesteal(0.05f)));

        registerPerk(new Perk("blood_frenzy", "Blood Frenzy", "Killing enemies heals 2 HP", PerkTier.RARE, PerkCategory.VAMPIRIC,
            true, 3, null)); // Applied on kill

        registerPerk(new Perk("soul_drain", "Soul Drain", "Kills restore hunger and give brief regen", PerkTier.EPIC, PerkCategory.VAMPIRIC,
            false, 1, null)); // Applied on kill

        // === ELEMENTAL PERKS ===

        registerPerk(new Perk("fire_aspect", "Blazing Strikes", "20% chance to ignite enemies per stack", PerkTier.UNCOMMON, PerkCategory.ELEMENTAL,
            true, 3, ctx -> ctx.session.addFireChance(0.20f)));

        registerPerk(new Perk("frost_touch", "Frost Touch", "20% chance to slow enemies per stack", PerkTier.UNCOMMON, PerkCategory.ELEMENTAL,
            true, 3, ctx -> ctx.session.addFreezeChance(0.20f)));

        registerPerk(new Perk("lightning_strike", "Lightning Strike", "10% chance to chain lightning per stack", PerkTier.RARE, PerkCategory.ELEMENTAL,
            true, 2, ctx -> ctx.session.addLightningChance(0.10f)));

        registerPerk(new Perk("elemental_mastery", "Elemental Mastery", "All elemental effects +50%", PerkTier.EPIC, PerkCategory.ELEMENTAL,
            false, 1, ctx -> {
                ctx.session.addFireChance(ctx.session.getFireChance() * 0.5f);
                ctx.session.addFreezeChance(ctx.session.getFreezeChance() * 0.5f);
                ctx.session.addLightningChance(ctx.session.getLightningChance() * 0.5f);
            }));

        // === COMBO/STYLE PERKS ===

        registerPerk(new Perk("showoff", "Showoff", "+25% style points per stack", PerkTier.COMMON, PerkCategory.COMBO,
            true, 4, ctx -> ctx.session.addStyleMultiplier(0.25f)));

        registerPerk(new Perk("momentum", "Momentum", "Combo hits increase damage (max +50%)", PerkTier.RARE, PerkCategory.COMBO,
            false, 1, ctx -> {
                // Calculated dynamically
            }));

        registerPerk(new Perk("style_is_substance", "Style is Substance", "Style rank affects damage", PerkTier.EPIC, PerkCategory.COMBO,
            false, 1, ctx -> {
                // S rank = +50% damage, SSS = +100%
            }));

        // === CURSE PERKS (risk/reward) ===

        registerPerk(new Perk("curse_fragility", "Curse: Fragility", "+50% damage taken per stack, +50% rewards", PerkTier.UNCOMMON, PerkCategory.CURSE,
            true, 2, ctx -> {
                ctx.session.addDamageReduction(-0.5f);
                ctx.session.addCurse(1);
            }));

        registerPerk(new Perk("curse_weakness", "Curse: Weakness", "-25% damage per stack, +50% rewards", PerkTier.UNCOMMON, PerkCategory.CURSE,
            true, 2, ctx -> {
                ctx.session.addDamageMultiplier(-0.25f);
                ctx.session.addCurse(1);
            }));

        registerPerk(new Perk("curse_doom", "Curse: Doom", "Take 1 damage/second, +100% rewards", PerkTier.RARE, PerkCategory.CURSE,
            false, 1, ctx -> {
                ctx.session.addCurse(2);
            })); // Damage applied via tick

        registerPerk(new Perk("ultimate_curse", "Pact with Death", "Die in one hit, +300% rewards", PerkTier.LEGENDARY, PerkCategory.CURSE,
            false, 1, ctx -> {
                var attr = ctx.player.getAttribute(Objects.requireNonNull(Attributes.MAX_HEALTH));
                if (attr != null) {
                    ctx.player.setHealth(1.0f);
                }
                ctx.session.addCurse(6);
            }));

        // === LEGENDARY SYNERGY PERKS ===

        registerPerk(new Perk("avatar_of_war", "Avatar of War",
            "Requires 5+ offense perks. +100% damage, attacks cause explosions",
            PerkTier.LEGENDARY, PerkCategory.OFFENSE, null, false, 1,
            Set.of(), Set.of(), // Requires offense perks (checked separately)
            ctx -> ctx.session.addDamageMultiplier(1.0f),
            null, null, null, null));

        registerPerk(new Perk("unkillable", "Unkillable",
            "Requires 5+ defense perks. Immune to one-shots, +50% health",
            PerkTier.LEGENDARY, PerkCategory.DEFENSE, null, false, 1,
            Set.of(), Set.of(),
            ctx -> {
                var attr = ctx.player.getAttribute(Objects.requireNonNull(Attributes.MAX_HEALTH));
                if (attr != null) {
                    float bonus = ctx.player.getMaxHealth() * 0.5f;
                    ctx.player.setHealth(ctx.player.getHealth() + bonus);
                }
            },
            null, null, null, null));

        LOGGER.info("[PerkSystem] Registered {} perks", allPerks.size());
    }

    private void registerPerk(Perk perk) {
        allPerks.put(perk.id, perk);
    }

    // ========== Session Management ==========

    /**
     * Start a new perk session for a player.
     */
    public PerkSession startSession(UUID playerId, UUID questId) {
        PerkSession session = new PerkSession(playerId, questId);
        activeSessions.put(playerId, session);
        LOGGER.debug("[PerkSystem] Started session for player {}", playerId);
        return session;
    }

    /**
     * Get active session for a player.
     */
    public Optional<PerkSession> getSession(UUID playerId) {
        return Optional.ofNullable(activeSessions.get(playerId));
    }

    /**
     * End session and clean up all perk effects.
     */
    public PerkSession endSession(ServerPlayer player) {
        PerkSession session = activeSessions.remove(player.getUUID());
        if (session != null) {
            // Remove applied attribute modifiers from all relevant attributes
            for (AttributeModifier mod : session.getAppliedModifiers()) {
                // Try removing from MAX_HEALTH (vitality perk)
                var healthAttr = player.getAttribute(Objects.requireNonNull(Attributes.MAX_HEALTH));
                if (healthAttr != null) {
                    healthAttr.removeModifier(Objects.requireNonNull(mod));
                }
                // Try removing from other attributes that perks might modify
                var speedAttr = player.getAttribute(Objects.requireNonNull(Attributes.MOVEMENT_SPEED));
                if (speedAttr != null) {
                    speedAttr.removeModifier(Objects.requireNonNull(mod));
                }
                var attackSpeedAttr = player.getAttribute(Objects.requireNonNull(Attributes.ATTACK_SPEED));
                if (attackSpeedAttr != null) {
                    attackSpeedAttr.removeModifier(Objects.requireNonNull(mod));
                }
            }

            // Remove any perk-related potion effects that might still be active
            player.removeEffect(Objects.requireNonNull(MobEffects.REGENERATION));
            player.removeEffect(Objects.requireNonNull(MobEffects.MOVEMENT_SPEED));
            player.removeEffect(Objects.requireNonNull(MobEffects.DAMAGE_BOOST));
            player.removeEffect(Objects.requireNonNull(MobEffects.DAMAGE_RESISTANCE));

            // Reset player health to max (after attribute cleanup) to avoid issues
            // with health being above new max health
            float maxHealth = player.getMaxHealth();
            if (player.getHealth() > maxHealth) {
                player.setHealth(maxHealth);
            }

            LOGGER.info("[PerkSystem] Session ended for player {} (quest {}) - {} perks acquired, all effects removed",
                session.getPlayerId(), session.getQuestId(), session.getTotalPerksAcquired());
        }
        return session;
    }

    /**
     * End session without a player reference (used during forced shutdown).
     */
    public PerkSession endSession(UUID playerId) {
        return activeSessions.remove(playerId);
    }

    /**
     * Check if a player has unlocked a specific perk.
     */
    public boolean hasUnlockedPerk(UUID playerId, String perkId) {
        Set<String> perks = unlockedPerks.get(playerId);
        return perks != null && perks.contains(perkId);
    }

    /**
     * Get all unlocked perks for a player.
     */
    public Set<String> getUnlockedPerks(UUID playerId) {
        return unlockedPerks.getOrDefault(playerId, Set.of());
    }

    // ========== Perk Selection ==========

    /**
     * Generate perk choices for a player after completing a wave.
     */
    public List<Perk> generatePerkChoices(ServerPlayer player, int waveNumber) {
        PerkSession session = activeSessions.get(player.getUUID());
        if (session == null) return List.of();

        List<Perk> choices = new ArrayList<>();
        List<Perk> availablePerks = getAvailablePerks(session, waveNumber);

        // Select 3 random perks weighted by tier
        int attempts = 0;
        while (choices.size() < 3 && attempts < 100 && !availablePerks.isEmpty()) {
            Perk selected = selectWeightedPerk(availablePerks, waveNumber);
            if (selected != null && !choices.contains(selected)) {
                choices.add(selected);
                availablePerks.remove(selected);
            }
            attempts++;
        }

        session.setPendingChoices(choices);

        // Telemetry: record perk choices offered
        if (!choices.isEmpty()) {
            EnduranceTelemetryService.INSTANCE.recordPerkChoicesOffered(
                player.getUUID(),
                session.getQuestId(),
                waveNumber,
                choices
            );
        }

        return choices;
    }

    /**
     * Get perks available for selection.
     */
    private List<Perk> getAvailablePerks(PerkSession session, int waveNumber) {
        List<Perk> available = new ArrayList<>();

        for (Perk perk : allPerks.values()) {
            // Check if already at max stacks
            if (!perk.stackable && session.hasPerk(perk.id)) continue;
            if (perk.stackable && session.getPerkStacks(perk.id) >= perk.maxStacks) continue;

            // Check required perks
            if (!perk.requiredPerks.isEmpty()) {
                boolean hasAll = perk.requiredPerks.stream().allMatch(session::hasPerk);
                if (!hasAll) continue;
            }

            // Check incompatible perks
            if (perk.incompatiblePerks.stream().anyMatch(session::hasPerk)) continue;

            // Check synergy requirements for legendary perks
            if (perk.tier == PerkTier.LEGENDARY) {
                if (perk.id.equals("avatar_of_war")) {
                    long offenseCount = session.getAcquiredPerkIds().stream()
                        .map(allPerks::get)
                        .filter(p -> p != null && p.category == PerkCategory.OFFENSE)
                        .count();
                    if (offenseCount < 5) continue;
                } else if (perk.id.equals("unkillable")) {
                    long defenseCount = session.getAcquiredPerkIds().stream()
                        .map(allPerks::get)
                        .filter(p -> p != null && p.category == PerkCategory.DEFENSE)
                        .count();
                    if (defenseCount < 5) continue;
                }
            }

            // Higher tier perks appear more in later waves
            if (perk.tier.ordinal() > waveNumber / 2) {
                // Skip very rare perks in early waves
                if (waveNumber < 3 && perk.tier.ordinal() >= PerkTier.EPIC.ordinal()) continue;
            }

            available.add(perk);
        }

        return available;
    }

    /**
     * Select a perk weighted by tier rarity.
     */
    private Perk selectWeightedPerk(List<Perk> perks, int waveNumber) {
        if (perks.isEmpty()) return null;

        // Calculate total weight
        int totalWeight = 0;
        for (Perk perk : perks) {
            // Higher waves increase rare perk chances
            int weight = perk.tier.weight;
            if (waveNumber >= 5) weight *= (perk.tier.ordinal() + 1);
            totalWeight += weight;
        }

        // Select random
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (Perk perk : perks) {
            int weight = perk.tier.weight;
            if (waveNumber >= 5) weight *= (perk.tier.ordinal() + 1);
            cumulative += weight;
            if (roll < cumulative) {
                return perk;
            }
        }

        return perks.get(random.nextInt(perks.size()));
    }

    /**
     * Player selects a perk from choices.
     */
    public boolean selectPerk(ServerPlayer player, int choiceIndex) {
        PerkSession session = activeSessions.get(player.getUUID());
        if (session == null) return false;

        List<Perk> choices = session.getPendingChoices();
        if (choiceIndex < 0 || choiceIndex >= choices.size()) return false;

        Perk selected = choices.get(choiceIndex);
        applyPerk(player, session, selected);
        session.clearPendingChoices();

        LOGGER.info("[PerkSystem] Player {} selected perk: {} ({})",
            player.getName().getString(), selected.name, selected.tier.displayName);

        return true;
    }

    /**
     * Apply a perk to player.
     */
    private void applyPerk(ServerPlayer player, PerkSession session, Perk perk) {
        session.addPerk(perk.id);

        // Telemetry: record perk selection
        EnduranceTelemetryService.INSTANCE.recordPerkSelected(
            player.getUUID(),
            session.getQuestId(),
            perk.id,
            perk.name,
            perk.tier,
            perk.category,
            session.getPerkStacks(perk.id),
            session.getTotalPerksAcquired()
        );

        // Trigger player attribute snapshot on perk acquisition
        com.frenkvs.devmod.telemetry.player.PlayerAttributeTelemetryService.INSTANCE
            .recordSnapshot(player, "perk_acquired_" + perk.id);

        PerkContext context = new PerkContext(player, session, session.getPerkStacks(perk.id));
        perk.apply(context);
    }

    // ========== Perk Effects ==========

    /**
     * Process tick effects for all active sessions.
     */
    public void tick(ServerPlayer player) {
        PerkSession session = activeSessions.get(player.getUUID());
        if (session == null) return;

        // Regeneration perk
        if (session.hasPerk("regeneration")) {
            if (player.tickCount % 40 == 0) { // Every 2 seconds
                player.heal(1.0f);
            }
        }

        // Doom curse
        if (session.hasPerk("curse_doom")) {
            if (player.tickCount % 20 == 0) { // Every second
                player.hurt(Objects.requireNonNull(player.damageSources().magic()), 1.0f);
            }
        }

        // Process perk tick callbacks
        for (String perkId : session.getAcquiredPerkIds()) {
            Perk perk = allPerks.get(perkId);
            if (perk != null) {
                PerkContext context = new PerkContext(player, session, session.getPerkStacks(perkId));
                perk.tick(context);
            }
        }
    }

    /**
     * Process on-hit effects.
     */
    public float processHit(ServerPlayer player, float damage, boolean isCritical) {
        PerkSession session = activeSessions.get(player.getUUID());
        if (session == null) return damage;

        float finalDamage = damage * session.getDamageMultiplier();

        // Berserker perk
        if (session.hasPerk("berserker")) {
            float missingHpPercent = 1.0f - (player.getHealth() / player.getMaxHealth());
            finalDamage *= 1.0f + (missingHpPercent * 0.5f);
        }

        // Momentum perk - bonus damage based on combo count
        if (session.hasPerk("momentum")) {
            var comboOpt = ComboSystem.INSTANCE.getSession(player.getUUID());
            if (comboOpt.isPresent()) {
                float comboBonus = Math.min(0.5f, comboOpt.get().getCurrentCombo() * 0.01f);
                finalDamage *= 1.0f + comboBonus;
            }
        }

        // Critical damage
        if (isCritical) {
            finalDamage *= session.getCritDamageMultiplier();
        }

        // Lifesteal
        if (session.getLifestealPercent() > 0) {
            float heal = finalDamage * session.getLifestealPercent();
            player.heal(heal);
        }

        // Elemental effects are processed separately

        return finalDamage;
    }

    /**
     * Process on-kill effects.
     */
    public void processKill(ServerPlayer player) {
        PerkSession session = activeSessions.get(player.getUUID());
        if (session == null) return;

        // Blood Frenzy
        if (session.hasPerk("blood_frenzy")) {
            player.heal(2.0f * session.getPerkStacks("blood_frenzy"));
        }

        // Soul Drain
        if (session.hasPerk("soul_drain")) {
            player.getFoodData().eat(2, 0.5f);
            player.addEffect(new MobEffectInstance(Objects.requireNonNull(MobEffects.REGENERATION), 40, 0));
        }
    }

    /**
     * Process damage taken.
     */
    public float processDamageTaken(ServerPlayer player, float damage) {
        PerkSession session = activeSessions.get(player.getUUID());
        if (session == null) return damage;

        float finalDamage = damage * session.getDamageReductionMultiplier();

        // Second Wind (one-time save)
        // Immortal (temporary invulnerability)
        // These need special handling in damage events

        return Math.max(0, finalDamage);
    }

    // ========== Getters ==========

    public Collection<Perk> getAllPerks() {
        return Collections.unmodifiableCollection(allPerks.values());
    }

    public Optional<Perk> getPerk(String id) {
        return Optional.ofNullable(allPerks.get(id));
    }
}
