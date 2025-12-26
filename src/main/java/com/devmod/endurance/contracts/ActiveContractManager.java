package com.devmod.endurance.contracts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import com.devmod.endurance.ComboSystem;
import com.devmod.endurance.EnduranceEventCombat;
import com.devmod.telemetry.endurance.EnduranceTelemetryService;

public final class ActiveContractManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActiveContractManager.class);

    public static final ActiveContractManager INSTANCE = new ActiveContractManager();

    // Active contracts per player (questId -> playerId -> contracts)
    private final Map<UUID, Map<UUID, ContractSession>> sessions = new ConcurrentHashMap<>();

    private ActiveContractManager() {}

    /**
     * Session tracking contract state for a player in a quest.
     */
    public static class ContractSession {
        private final UUID playerId;
        private final UUID questId;
        private final List<BloodContract> activeContracts = new ArrayList<>();
        private final Set<BloodContract> violatedContracts = new HashSet<>();

        // Tracking for violation checks
        private float damageTaken = 0;
        private int hitsTaken = 0;
        private long waveStartTime = System.currentTimeMillis();
        private boolean playerDied = false;

        public ContractSession(UUID playerId, UUID questId) {
            this.playerId = playerId;
            this.questId = questId;
        }

        // ========== Contract Management ==========

        public boolean signContract(BloodContract contract) {
            // Check compatibility with existing contracts
            for (BloodContract existing : activeContracts) {
                if (!contract.isCompatibleWith(existing)) {
                    LOGGER.debug("[Contracts] {} incompatible with {}", contract.getId(), existing.getId());
                    return false;
                }
            }

            activeContracts.add(contract);
            LOGGER.info("[Contracts] Player {} signed {} ({}x multiplier)",
                playerId, contract.getId(), contract.getTotalMultiplier());
            return true;
        }

        public void unsignContract(BloodContract contract) {
            activeContracts.remove(contract);
        }

        public List<BloodContract> getActiveContracts() {
            return Collections.unmodifiableList(activeContracts);
        }

        public Set<BloodContract> getViolatedContracts() {
            return Collections.unmodifiableSet(violatedContracts);
        }

        public boolean hasContract(BloodContract.ContractEffect effect) {
            return activeContracts.stream().anyMatch(c -> c.getEffect() == effect);
        }

        // ========== Damage Tracking ==========

        public void recordDamageTaken(float damage) {
            this.damageTaken += damage;
            this.hitsTaken++;
        }

        public void recordDeath() {
            this.playerDied = true;
        }

        public void startWave() {
            this.waveStartTime = System.currentTimeMillis();
            this.damageTaken = 0;
            this.hitsTaken = 0;
            this.playerDied = false;
        }

        // ========== Violation Checking ==========

        /**
         * Check all contracts for violations.
         * Returns list of newly violated contracts.
         */
        @Nonnull
        public List<BloodContract> checkViolations(ServerPlayer player) {
            List<BloodContract> newViolations = new ArrayList<>();

            // Get current style rank
            int styleRank = 0;
            ComboSystem.ComboSession comboSession = EnduranceEventCombat.getComboSession(playerId);
            if (comboSession != null) {
                styleRank = comboSession.getCurrentRank().ordinal();
            }

            BloodContract.ContractContext context = new BloodContract.ContractContext(
                0, // damageDealt not tracked per-wave currently
                damageTaken,
                hitsTaken,
                waveStartTime,
                System.currentTimeMillis(),
                styleRank,
                playerDied
            );

            for (BloodContract contract : activeContracts) {
                if (violatedContracts.contains(contract)) continue;

                if (contract.isViolated(player, context)) {
                    violatedContracts.add(contract);
                    newViolations.add(contract);
                    LOGGER.info("[Contracts] Player {} violated {}!", playerId, contract.getId());
                }
            }

            return newViolations;
        }

        // ========== Reward Calculation ==========

        /**
         * Calculate total reward multiplier from non-violated contracts.
         */
        public float getRewardMultiplier() {
            float multiplier = 1.0f;

            for (BloodContract contract : activeContracts) {
                if (!violatedContracts.contains(contract)) {
                    multiplier *= contract.getTotalMultiplier();
                }
            }

            return multiplier;
        }

        /**
         * Get bonus style points from active contracts.
         */
        public int getStyleBonus() {
            int bonus = 0;

            for (BloodContract contract : activeContracts) {
                if (!violatedContracts.contains(contract)) {
                    // Style bonus based on contract tier
                    bonus += switch (contract.getTier()) {
                        case MINOR -> 50;
                        case STANDARD -> 100;
                        case MAJOR -> 200;
                        case BLOOD -> 500;
                    };
                }
            }

            return bonus;
        }

        /**
         * Clear all contracts and tracking (end of wave/quest).
         */
        public void clear() {
            activeContracts.clear();
            violatedContracts.clear();
            damageTaken = 0;
            hitsTaken = 0;
            playerDied = false;
        }

        // ========== Getters ==========

        public UUID getPlayerId() { return playerId; }
        public UUID getQuestId() { return questId; }
        public float getDamageTaken() { return damageTaken; }
        public int getHitsTaken() { return hitsTaken; }
        public boolean hasViolations() { return !violatedContracts.isEmpty(); }
    }

    // ========== Session Management ==========

    /**
     * Get or create a contract session for a player in a quest.
     */
    @Nonnull
    public ContractSession getOrCreateSession(UUID questId, UUID playerId) {
        return sessions
            .computeIfAbsent(questId, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(playerId, k -> new ContractSession(playerId, questId));
    }

    /**
     * Get session if it exists.
     */
    public Optional<ContractSession> getSession(UUID questId, UUID playerId) {
        Map<UUID, ContractSession> questSessions = sessions.get(questId);
        if (questSessions == null) return Optional.empty();
        return Optional.ofNullable(questSessions.get(playerId));
    }

    /**
     * Get all sessions for a quest.
     */
    @Nonnull
    public Collection<ContractSession> getQuestSessions(UUID questId) {
        Map<UUID, ContractSession> questSessions = sessions.get(questId);
        if (questSessions == null) return Collections.emptyList();
        return questSessions.values();
    }

    // ========== Contract Operations ==========

    /**
     * Sign a contract for a player.
     */
    public boolean signContract(UUID questId, ServerPlayer player, BloodContract contract) {
        ContractSession session = getOrCreateSession(questId, player.getUUID());
        boolean success = session.signContract(contract);

        if (success) {
            // Play contract signing sound
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 0.7f);

            // Dramatic sound for blood tier
            if (contract.getTier() == BloodContract.ContractTier.BLOOD) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.5f, 1.5f);
            }

            // Telemetry
            EnduranceTelemetryService.INSTANCE.recordContractSigned(
                questId, player.getUUID(), contract.getId().toString(), contract.getTotalMultiplier()
            );
        }

        return success;
    }

    /**
     * Record damage taken for violation tracking.
     */
    public void recordDamageTaken(UUID questId, UUID playerId, float damage) {
        getSession(questId, playerId).ifPresent(session -> session.recordDamageTaken(damage));
    }

    /**
     * Record player death for violation tracking.
     */
    public void recordDeath(UUID questId, UUID playerId) {
        getSession(questId, playerId).ifPresent(ContractSession::recordDeath);
    }

    /**
     * Check for contract violations and apply consequences.
     */
    public void checkViolations(UUID questId, ServerPlayer player) {
        getSession(questId, player.getUUID()).ifPresent(session -> {
            List<BloodContract> violations = session.checkViolations(player);

            for (BloodContract violated : violations) {
                // Violation sound
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0f, 0.5f);

                // Apply violation consequences
                applyViolationConsequence(player, violated);

                // Telemetry
                EnduranceTelemetryService.INSTANCE.recordContractViolated(
                    questId, player.getUUID(), violated.getId().toString()
                );
            }
        });
    }

    /**
     * Apply consequences when a contract is violated.
     */
    private void applyViolationConsequence(ServerPlayer player, BloodContract contract) {
        switch (contract.getTier()) {
            case BLOOD -> {
                // Blood contracts = instant death on violation
                player.hurt(player.damageSources().magic(), Float.MAX_VALUE);
                LOGGER.info("[Contracts] {} killed for violating BLOOD contract {}",
                    player.getName().getString(), contract.getId());
            }
            case MAJOR -> {
                // Major contracts = heavy damage
                float damage = player.getMaxHealth() * 0.5f;
                player.hurt(player.damageSources().magic(), damage);
            }
            case STANDARD, MINOR -> {
                // No additional punishment beyond losing reward multiplier
            }
        }
    }

    /**
     * Signal wave start for all players in a quest.
     */
    public void onWaveStart(UUID questId) {
        for (ContractSession session : getQuestSessions(questId)) {
            session.startWave();
        }
    }

    /**
     * Get total reward multiplier for a player.
     */
    public float getRewardMultiplier(UUID questId, UUID playerId) {
        return getSession(questId, playerId)
            .map(ContractSession::getRewardMultiplier)
            .orElse(1.0f);
    }

    /**
     * Clear all sessions for a quest (quest end).
     */
    public void clearQuest(UUID questId) {
        Map<UUID, ContractSession> questSessions = sessions.remove(questId);
        if (questSessions != null) {
            LOGGER.debug("[Contracts] Cleared {} sessions for quest {}", questSessions.size(), questId);
        }
    }

    /**
     * Clear a player's session (player leaves quest).
     */
    public void clearPlayer(UUID questId, UUID playerId) {
        Map<UUID, ContractSession> questSessions = sessions.get(questId);
        if (questSessions != null) {
            questSessions.remove(playerId);
        }
    }

    // ========== Effect Queries ==========

    /**
     * Check if player has a specific contract effect active.
     */
    public boolean hasEffect(UUID questId, UUID playerId, BloodContract.ContractEffect effect) {
        return getSession(questId, playerId)
            .map(s -> s.hasContract(effect))
            .orElse(false);
    }

    /**
     * Get damage multiplier from contracts (GLASS_CANNON, BERSERKER).
     */
    public float getDamageDealtMultiplier(UUID questId, UUID playerId, float currentHealth, float maxHealth) {
        return getSession(questId, playerId).map(session -> {
            float multiplier = 1.0f;

            if (session.hasContract(BloodContract.ContractEffect.GLASS_CANNON)) {
                multiplier *= 1.5f; // +50% damage
            }

            if (session.hasContract(BloodContract.ContractEffect.BERSERKER)) {
                if (currentHealth / maxHealth < 0.5f) {
                    multiplier *= 1.75f; // +75% damage when low health
                }
            }

            if (session.hasContract(BloodContract.ContractEffect.BLITZ)) {
                multiplier *= 3.0f; // +200% damage for 30s time limit
            }

            return multiplier;
        }).orElse(1.0f);
    }

    /**
     * Get damage taken multiplier from contracts (GLASS_CANNON, SACRIFICE).
     */
    public float getDamageTakenMultiplier(UUID questId, UUID playerId) {
        return getSession(questId, playerId).map(session -> {
            float multiplier = 1.0f;

            if (session.hasContract(BloodContract.ContractEffect.GLASS_CANNON)) {
                multiplier *= 2.0f; // +100% damage taken
            }

            // Note: SACRIFICE effect handled separately for random party member

            return multiplier;
        }).orElse(1.0f);
    }

    /**
     * Check if healing is disabled (BERSERKER, VAMPIRE).
     */
    public boolean isHealingDisabled(UUID questId, UUID playerId) {
        return hasEffect(questId, playerId, BloodContract.ContractEffect.BERSERKER) ||
               hasEffect(questId, playerId, BloodContract.ContractEffect.VAMPIRE);
    }

    /**
     * Check if lifesteal is enabled (VAMPIRE).
     */
    public boolean hasLifesteal(UUID questId, UUID playerId) {
        return hasEffect(questId, playerId, BloodContract.ContractEffect.VAMPIRE);
    }
}
