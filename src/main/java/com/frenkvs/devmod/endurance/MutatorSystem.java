package com.frenkvs.devmod.endurance;

import com.devmod.arena.policy.ArenaPolicy;
import com.frenkvs.devmod.telemetry.endurance.EnduranceTelemetryService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamic Mutator System - Modifies gameplay rules each run.
 *
 * Mutators are randomly selected at the start of each quest and can:
 * - Modify mob behavior and stats
 * - Change player abilities
 * - Add environmental hazards
 * - Create special scoring conditions
 *
 * Categories:
 * - POSITIVE: Benefits the player
 * - NEGATIVE: Makes the quest harder (but gives bonus rewards)
 * - NEUTRAL: Changes gameplay without clear advantage
 * - CHAOTIC: Unpredictable effects
 */
public class MutatorSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(MutatorSystem.class);

    public static final MutatorSystem INSTANCE = new MutatorSystem();

    // All available mutators
    private final Map<String, Mutator> allMutators = new LinkedHashMap<>();

    // Active mutators per session
    private final Map<UUID, MutatorSession> activeSessions = new ConcurrentHashMap<>();

    private final Random random = new Random();

    /**
     * Mutator category determines reward scaling.
     */
    public enum MutatorCategory {
        POSITIVE("Blessing", 0x4ade80, 0.8f),   // Easier, less rewards
        NEGATIVE("Curse", 0xFF6B6B, 1.5f),       // Harder, more rewards
        NEUTRAL("Twist", 0x60a5fa, 1.0f),        // Different, same rewards
        CHAOTIC("Chaos", 0xFFAA00, 1.25f);       // Unpredictable

        public final String displayName;
        public final int color;
        public final float rewardMultiplier;

        MutatorCategory(String displayName, int color, float rewardMultiplier) {
            this.displayName = displayName;
            this.color = color;
            this.rewardMultiplier = rewardMultiplier;
        }
    }

    /**
     * A single mutator definition.
     */
    public static class Mutator {
        public final String id;
        public final String name;
        public final String description;
        public final MutatorCategory category;
        public final String iconSymbol;

        // Can be combined with other mutators?
        public final Set<String> incompatibleMutators;

        // Weight for random selection (higher = more common)
        public final int weight;

        public Mutator(String id, String name, String description, MutatorCategory category,
                       String iconSymbol, int weight, Set<String> incompatible) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.category = category;
            this.iconSymbol = iconSymbol;
            this.weight = weight;
            this.incompatibleMutators = incompatible != null ? incompatible : Set.of();
        }

        public Mutator(String id, String name, String description, MutatorCategory category,
                       String iconSymbol, int weight) {
            this(id, name, description, category, iconSymbol, weight, Set.of());
        }
    }

    /**
     * Active mutators for a quest session.
     */
    public static class MutatorSession {
        private final UUID questId;
        private final List<Mutator> activeMutators = new ArrayList<>();
        private float totalRewardMultiplier = 1.0f;

        // Cached effects for performance
        private float mobHealthMultiplier = 1.0f;
        private float mobDamageMultiplier = 1.0f;
        private float mobSpeedMultiplier = 1.0f;
        private float playerDamageMultiplier = 1.0f;
        private float playerSpeedMultiplier = 1.0f;
        private float pointsMultiplier = 1.0f;

        // Special flags
        private boolean explodingMobs = false;
        private boolean vampiricMobs = false;
        private boolean invisibleMobs = false;
        private boolean giantMobs = false;
        private boolean miniMobs = false;
        private boolean splitOnDeath = false;
        private boolean healingDisabled = false;
        private boolean oneHitKills = false;
        private boolean mirrorDamage = false;
        private boolean lowGravity = false;
        private boolean highGravity = false;
        private boolean fastForward = false;
        private boolean bulletHell = false;

        private Set<String> suggestedMutators = Set.of();
        private Set<String> excludedMutators = Set.of();
        private Set<String> requiredMutators = Set.of();

        public MutatorSession(UUID questId) {
            this.questId = questId;
        }

        public void applyBindings(@javax.annotation.Nullable ArenaPolicy.MutatorBindings bindings) {
            if (bindings == null) {
                suggestedMutators = Set.of();
                excludedMutators = Set.of();
                requiredMutators = Set.of();
                return;
            }
            suggestedMutators = bindings.suggested() != null ? Set.copyOf(bindings.suggested()) : Set.of();
            excludedMutators = bindings.excluded() != null ? Set.copyOf(bindings.excluded()) : Set.of();
            requiredMutators = bindings.required() != null ? Set.copyOf(bindings.required()) : Set.of();
        }

        public boolean isSuggested(String mutatorId) {
            return mutatorId != null && suggestedMutators.contains(mutatorId);
        }

        public boolean isExcluded(String mutatorId) {
            return mutatorId != null && excludedMutators.contains(mutatorId);
        }

        public Set<String> getRequiredMutators() {
            return requiredMutators;
        }

        public void addMutator(Mutator mutator) {
            activeMutators.add(mutator);
            totalRewardMultiplier *= mutator.category.rewardMultiplier;
            applyMutatorEffects(mutator);
        }

        private void applyMutatorEffects(Mutator mutator) {
            switch (mutator.id) {
                // Mob stat mutators
                case "beefy_mobs" -> mobHealthMultiplier *= 2.0f;
                case "glass_mobs" -> mobHealthMultiplier *= 0.5f;
                case "deadly_mobs" -> mobDamageMultiplier *= 1.5f;
                case "weak_mobs" -> mobDamageMultiplier *= 0.5f;
                case "speedy_mobs" -> mobSpeedMultiplier *= 1.5f;
                case "slow_mobs" -> mobSpeedMultiplier *= 0.5f;

                // Player stat mutators
                case "glass_cannon" -> {
                    playerDamageMultiplier *= 2.0f;
                    // Player takes more damage (handled elsewhere)
                }
                case "tank_mode" -> {
                    playerDamageMultiplier *= 0.5f;
                    // Player takes less damage
                }
                case "speed_demon" -> playerSpeedMultiplier *= 1.5f;

                // Special mob behavior
                case "exploding" -> explodingMobs = true;
                case "vampiric" -> vampiricMobs = true;
                case "invisible" -> invisibleMobs = true;
                case "giant" -> giantMobs = true;
                case "mini" -> miniMobs = true;
                case "splitting" -> splitOnDeath = true;

                // Environmental
                case "no_healing" -> healingDisabled = true;
                case "one_hit" -> oneHitKills = true;
                case "mirror" -> mirrorDamage = true;
                case "low_gravity" -> lowGravity = true;
                case "high_gravity" -> highGravity = true;
                case "fast_forward" -> fastForward = true;
                case "bullet_hell" -> bulletHell = true;

                // Points
                case "double_points" -> pointsMultiplier *= 2.0f;
                case "half_points" -> pointsMultiplier *= 0.5f;
            }
        }

        // Getters
        public UUID getQuestId() { return questId; }
        public List<Mutator> getActiveMutators() { return Collections.unmodifiableList(activeMutators); }
        public float getTotalRewardMultiplier() { return totalRewardMultiplier; }

        public float getMobHealthMultiplier() { return mobHealthMultiplier; }
        public float getMobDamageMultiplier() { return mobDamageMultiplier; }
        public float getMobSpeedMultiplier() { return mobSpeedMultiplier; }
        public float getPlayerDamageMultiplier() { return playerDamageMultiplier; }
        public float getPlayerSpeedMultiplier() { return playerSpeedMultiplier; }
        public float getPointsMultiplier() { return pointsMultiplier; }

        public boolean hasExplodingMobs() { return explodingMobs; }
        public boolean hasVampiricMobs() { return vampiricMobs; }
        public boolean hasInvisibleMobs() { return invisibleMobs; }
        public boolean hasGiantMobs() { return giantMobs; }
        public boolean hasMiniMobs() { return miniMobs; }
        public boolean hasSplitOnDeath() { return splitOnDeath; }
        public boolean isHealingDisabled() { return healingDisabled; }
        public boolean hasOneHitKills() { return oneHitKills; }
        public boolean hasMirrorDamage() { return mirrorDamage; }
        public boolean hasLowGravity() { return lowGravity; }
        public boolean hasHighGravity() { return highGravity; }
        public boolean isLowGravity() { return lowGravity; }
        public boolean isHighGravity() { return highGravity; }
        public boolean isFastForward() { return fastForward; }
        public boolean isBulletHell() { return bulletHell; }

        public boolean hasMutator(String id) {
            return activeMutators.stream().anyMatch(m -> m.id.equals(id));
        }

        public float getRewardMultiplier() { return totalRewardMultiplier; }
        public int getActiveMutatorCount() { return activeMutators.size(); }

        public String getSummary() {
            if (activeMutators.isEmpty()) return "No mutators";
            return activeMutators.stream()
                .map(m -> m.iconSymbol + " " + m.name)
                .reduce((a, b) -> a + " | " + b)
                .orElse("");
        }
    }

    // ========== Initialization ==========

    private MutatorSystem() {
        initializeMutators();
    }

    private void initializeMutators() {
        // === POSITIVE MUTATORS (make game easier) ===

        register(new Mutator("glass_mobs", "Glass Cannon Enemies",
            "Enemies have 50% less health", MutatorCategory.POSITIVE, "💔", 30));

        register(new Mutator("weak_mobs", "Weakened Enemies",
            "Enemies deal 50% less damage", MutatorCategory.POSITIVE, "🛡️", 30));

        register(new Mutator("slow_mobs", "Sluggish Enemies",
            "Enemies move 50% slower", MutatorCategory.POSITIVE, "🐢", 25));

        register(new Mutator("double_points", "Double Points",
            "All points earned are doubled", MutatorCategory.POSITIVE, "⭐", 20));

        register(new Mutator("regen", "Regeneration",
            "Slowly regenerate health over time", MutatorCategory.POSITIVE, "💚", 25));

        register(new Mutator("speed_demon", "Speed Demon",
            "Move 50% faster", MutatorCategory.POSITIVE, "⚡", 20));

        // === NEGATIVE MUTATORS (make game harder, more rewards) ===

        register(new Mutator("beefy_mobs", "Beefy Enemies",
            "Enemies have 100% more health", MutatorCategory.NEGATIVE, "💪", 30));

        register(new Mutator("deadly_mobs", "Deadly Enemies",
            "Enemies deal 50% more damage", MutatorCategory.NEGATIVE, "☠️", 30));

        register(new Mutator("speedy_mobs", "Speedy Enemies",
            "Enemies move 50% faster", MutatorCategory.NEGATIVE, "🏃", 25));

        register(new Mutator("no_healing", "No Healing",
            "Cannot regenerate or heal", MutatorCategory.NEGATIVE, "🚫", 20,
            Set.of("regen")));

        register(new Mutator("half_points", "Half Points",
            "Points earned are halved", MutatorCategory.NEGATIVE, "📉", 15));

        register(new Mutator("high_gravity", "Heavy Atmosphere",
            "Increased gravity, harder to jump", MutatorCategory.NEGATIVE, "⬇️", 20,
            Set.of("low_gravity")));

        register(new Mutator("vampiric", "Vampiric Enemies",
            "Enemies heal when they hit you", MutatorCategory.NEGATIVE, "🧛", 15));

        // === NEUTRAL MUTATORS (change gameplay) ===

        register(new Mutator("giant", "Giant Enemies",
            "Enemies are twice as large", MutatorCategory.NEUTRAL, "🦣", 20,
            Set.of("mini")));

        register(new Mutator("mini", "Mini Enemies",
            "Enemies are half size but more numerous", MutatorCategory.NEUTRAL, "🐜", 20,
            Set.of("giant")));

        register(new Mutator("low_gravity", "Low Gravity",
            "Jump higher and fall slower", MutatorCategory.NEUTRAL, "🌙", 25,
            Set.of("high_gravity")));

        register(new Mutator("fast_forward", "Fast Forward",
            "Everything moves 25% faster", MutatorCategory.NEUTRAL, "⏩", 15));

        register(new Mutator("invisible", "Phantom Enemies",
            "Enemies are partially invisible", MutatorCategory.NEUTRAL, "👻", 15));

        // === CHAOTIC MUTATORS (unpredictable) ===

        register(new Mutator("exploding", "Explosive Death",
            "Enemies explode on death", MutatorCategory.CHAOTIC, "💥", 20));

        register(new Mutator("splitting", "Mitosis",
            "Enemies split into 2 smaller copies on death", MutatorCategory.CHAOTIC, "🔀", 15));

        register(new Mutator("one_hit", "Glass World",
            "Everything dies in one hit (including you!)", MutatorCategory.CHAOTIC, "💎", 10,
            Set.of("beefy_mobs", "glass_mobs", "tank_mode")));

        register(new Mutator("mirror", "Mirror Match",
            "Damage you deal is also dealt to you", MutatorCategory.CHAOTIC, "🪞", 10));

        register(new Mutator("bullet_hell", "Bullet Hell",
            "Enemies constantly fire projectiles", MutatorCategory.CHAOTIC, "🎯", 15));

        register(new Mutator("random_size", "Size Roulette",
            "Enemies spawn with random sizes", MutatorCategory.CHAOTIC, "🎲", 20));

        register(new Mutator("elemental_chaos", "Elemental Chaos",
            "Random elemental effects on all attacks", MutatorCategory.CHAOTIC, "🌈", 15));

        LOGGER.info("[MutatorSystem] Registered {} mutators", allMutators.size());
    }

    private void register(Mutator mutator) {
        allMutators.put(mutator.id, mutator);
    }

    // ========== Session Management ==========

    /**
     * Create a new mutator session with randomly selected mutators.
     */
    public MutatorSession createSession(UUID questId, int mutatorCount, int waveNumber) {
        return createSession(questId, mutatorCount, waveNumber, null);
    }

    public MutatorSession createSession(UUID questId, int mutatorCount, int waveNumber,
                                        @javax.annotation.Nullable ArenaPolicy policy) {
        MutatorSession session = new MutatorSession(questId);
        if (policy != null && policy.mutatorBindings() != null) {
            session.applyBindings(policy.mutatorBindings());
        }

        List<Mutator> available = new ArrayList<>(allMutators.values());
        available.removeIf(m -> session.isExcluded(m.id));
        Set<String> selectedIds = new HashSet<>();

        // Higher waves allow more/harder mutators
        int actualCount = Math.min(mutatorCount, 1 + waveNumber / 3);

        if (!session.getRequiredMutators().isEmpty()) {
            for (String requiredId : session.getRequiredMutators()) {
                Mutator required = allMutators.get(requiredId);
                if (required == null) {
                    LOGGER.warn("[MutatorSystem] Required mutator '{}' not found", requiredId);
                    continue;
                }
                if (session.isExcluded(required.id)) {
                    LOGGER.warn("[MutatorSystem] Required mutator '{}' excluded by policy", requiredId);
                    continue;
                }
                session.addMutator(required);
                selectedIds.add(required.id);
                available.removeIf(m ->
                    m.id.equals(required.id) ||
                    m.incompatibleMutators.contains(required.id) ||
                    required.incompatibleMutators.contains(m.id)
                );
            }
            actualCount = Math.max(actualCount, selectedIds.size());
        }

        for (int i = 0; i < actualCount && !available.isEmpty(); i++) {
            Mutator selected = selectWeightedMutator(available, session);
            if (selected != null) {
                session.addMutator(selected);
                selectedIds.add(selected.id);

                // Remove incompatible mutators from pool
                available.removeIf(m ->
                    m.id.equals(selected.id) ||
                    m.incompatibleMutators.contains(selected.id) ||
                    selected.incompatibleMutators.contains(m.id)
                );
            }
        }

        activeSessions.put(questId, session);
        LOGGER.info("[MutatorSystem] Created session with {} mutators: {}",
            session.getActiveMutators().size(), session.getSummary());

        // Telemetry: record mutators assigned
        if (!session.getActiveMutators().isEmpty()) {
            EnduranceTelemetryService.INSTANCE.recordMutatorsAssigned(
                questId,
                session.getActiveMutators(),
                session.getTotalRewardMultiplier()
            );
        }

        return session;
    }

    /**
     * Create session with specific mutators (for custom games).
     */
    public MutatorSession createSession(UUID questId, List<String> mutatorIds) {
        MutatorSession session = new MutatorSession(questId);

        for (String id : mutatorIds) {
            Mutator mutator = allMutators.get(id);
            if (mutator != null) {
                session.addMutator(mutator);
            }
        }

        activeSessions.put(questId, session);
        return session;
    }

    /**
     * Get active session.
     */
    public Optional<MutatorSession> getSession(UUID questId) {
        return Optional.ofNullable(activeSessions.get(questId));
    }

    /**
     * End and remove session.
     */
    public MutatorSession endSession(UUID questId) {
        return activeSessions.remove(questId);
    }

    /**
     * Roll and add a new random mutator to an existing session.
     * Used between waves to add progressive difficulty.
     */
    public void rollNewMutator(MutatorSession session) {
        rollNewMutator(session, -1);
    }

    /**
     * Roll and add a new random mutator to an existing session with wave tracking.
     */
    public void rollNewMutator(MutatorSession session, int waveNumber) {
        if (session == null) return;

        // Get available mutators (not already active)
        List<Mutator> available = new ArrayList<>(allMutators.values());
        available.removeIf(m -> session.hasMutator(m.id));
        available.removeIf(m -> session.isExcluded(m.id));

        // Prefer negative mutators (challenging) as waves progress
        List<Mutator> negativeMutators = available.stream()
            .filter(m -> m.category == MutatorCategory.NEGATIVE || m.category == MutatorCategory.CHAOTIC)
            .collect(java.util.stream.Collectors.toList());

        List<Mutator> pool = negativeMutators.isEmpty() ? available : negativeMutators;

        if (!pool.isEmpty()) {
            Mutator newMutator = selectWeightedMutator(pool, session);
            if (newMutator != null) {
                session.addMutator(newMutator);
                LOGGER.info("[MutatorSystem] Added new mutator: {} ({})", newMutator.name, newMutator.id);

                // Telemetry: record mutator added mid-quest
                EnduranceTelemetryService.INSTANCE.recordMutatorAdded(
                    session.getQuestId(),
                    newMutator,
                    waveNumber
                );
            }
        }
    }

    private Mutator selectWeightedMutator(List<Mutator> mutators, @javax.annotation.Nullable MutatorSession session) {
        if (mutators.isEmpty()) return null;

        int totalWeight = 0;
        for (Mutator mutator : mutators) {
            int weight = mutator.weight;
            if (session != null && session.isSuggested(mutator.id)) {
                weight = (int) Math.ceil(weight * 1.5);
            }
            totalWeight += weight;
        }
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;

        for (Mutator mutator : mutators) {
            int weight = mutator.weight;
            if (session != null && session.isSuggested(mutator.id)) {
                weight = (int) Math.ceil(weight * 1.5);
            }
            cumulative += weight;
            if (roll < cumulative) {
                return mutator;
            }
        }

        return mutators.get(random.nextInt(mutators.size()));
    }

    // ========== Effect Processing ==========

    /**
     * Apply mutator effects to a newly spawned mob.
     */
    public void applyToMob(UUID questId, Mob mob) {
        MutatorSession session = activeSessions.get(questId);
        if (session == null) return;

        // Size modifications
        if (session.hasGiantMobs()) {
            mob.setNoGravity(false);
            // Scale would need a mixin or capability
        }
        if (session.hasMiniMobs()) {
            // Scale down
        }

        // Invisibility
        if (session.hasInvisibleMobs()) {
            mob.setInvisible(true);
        }

        // Stat modifiers are applied via WaveManager
    }

    /**
     * Process damage with mutator effects.
     */
    public float processDamage(UUID questId, float damage, boolean isPlayerDealing) {
        MutatorSession session = activeSessions.get(questId);
        if (session == null) return damage;

        float result = damage;

        if (isPlayerDealing) {
            result *= session.getPlayerDamageMultiplier();
            if (session.hasOneHitKills()) {
                result = 9999f;
            }
        } else {
            result *= session.getMobDamageMultiplier();
        }

        return result;
    }

    /**
     * Calculate points with mutator effects.
     */
    public int processPoints(UUID questId, int basePoints) {
        MutatorSession session = activeSessions.get(questId);
        if (session == null) return basePoints;

        return (int) (basePoints * session.getPointsMultiplier() * session.getTotalRewardMultiplier());
    }

    /**
     * Check if player can heal.
     */
    public boolean canPlayerHeal(UUID questId) {
        MutatorSession session = activeSessions.get(questId);
        return session == null || !session.isHealingDisabled();
    }

    /**
     * Handle mob death effects (explosions, splitting).
     */
    public void onMobDeath(UUID questId, Mob mob, ServerPlayer killer) {
        MutatorSession session = activeSessions.get(questId);
        if (session == null) return;

        if (session.hasExplodingMobs()) {
            // Create explosion
            mob.level().explode(mob, mob.getX(), mob.getY(), mob.getZ(),
                2.0f, Level.ExplosionInteraction.NONE);
        }

        if (session.hasSplitOnDeath() && !mob.getPersistentData().getBoolean("is_split")) {
            // Spawn 2 smaller copies (handled by WaveManager)
        }
    }

    /**
     * Process mirror damage.
     */
    public float getMirrorDamage(UUID questId, float damage) {
        MutatorSession session = activeSessions.get(questId);
        if (session == null || !session.hasMirrorDamage()) return 0;
        return damage * 0.5f; // Reflect 50% back
    }

    // ========== Getters ==========

    public Collection<Mutator> getAllMutators() {
        return Collections.unmodifiableCollection(allMutators.values());
    }

    public Optional<Mutator> getMutator(String id) {
        return Optional.ofNullable(allMutators.get(id));
    }

    public List<Mutator> getMutatorsByCategory(MutatorCategory category) {
        return allMutators.values().stream()
            .filter(m -> m.category == category)
            .toList();
    }
}
