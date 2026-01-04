package com.devmod.endurance.nutrition;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.resources.ResourceLocation;

import com.devmod.compat.mods.easydiet.EasyDietCompat;

/**
 * Bridge system that connects Easy-Diet nutrition data with DevMod Endurance quests.
 *
 * <p>This system:
 * <ul>
 *   <li>Creates nutrition sessions when quests start</li>
 *   <li>Updates nutrition values from Easy-Diet periodically</li>
 *   <li>Applies attribute modifiers based on nutrition state</li>
 *   <li>Provides combat modifier queries for damage calculations</li>
 *   <li>Cleans up sessions when quests end</li>
 * </ul>
 *
 * <p>The system only activates when Easy-Diet is loaded. When absent,
 * all methods return neutral values (1.0 multipliers, 0 bonuses).
 *
 * @author DevMod Team
 */
public class NutritionBridgeSystem {

    private static final Logger LOGGER = LoggerFactory.getLogger(NutritionBridgeSystem.class);

    /** Singleton instance. */
    public static final NutritionBridgeSystem INSTANCE = new NutritionBridgeSystem();

    /** Attribute modifier IDs - cached with null check at class load. */
    @Nonnull
    private static final ResourceLocation SPEED_MODIFIER_ID =
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "nutrition_speed"));
    @Nonnull
    private static final ResourceLocation DAMAGE_MODIFIER_ID =
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "nutrition_damage"));

    /**
     * Cached attribute holders - validated once at class load to avoid
     * repeated Objects.requireNonNull calls every tick.
     */
    @Nonnull
    private static final Holder<Attribute> ATTR_MOVEMENT_SPEED =
        Objects.requireNonNull(Attributes.MOVEMENT_SPEED);
    @Nonnull
    private static final Holder<Attribute> ATTR_ATTACK_DAMAGE =
        Objects.requireNonNull(Attributes.ATTACK_DAMAGE);

    /** Update interval in ticks (1 second). */
    private static final int UPDATE_INTERVAL_TICKS = 20;

    /** Health change accumulator tick interval. */
    private static final int HEALTH_TICK_INTERVAL = 20;

    /** Active sessions by player UUID. */
    private final Map<UUID, NutritionSession> sessions = new ConcurrentHashMap<>();

    /** Health change accumulators by player UUID. */
    private final Map<UUID, Float> healthAccumulators = new ConcurrentHashMap<>();

    private NutritionBridgeSystem() {
        // Singleton
    }

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    /**
     * Called when a quest starts for a player.
     *
     * @param playerId the player UUID
     * @param questId the quest UUID
     */
    public void onQuestStart(UUID playerId, UUID questId) {
        if (!EasyDietCompat.isAvailable()) {
            LOGGER.debug("[NutritionBridge] Easy-Diet not available, skipping session creation");
            return;
        }

        // Remove any existing session
        NutritionSession existing = sessions.remove(playerId);
        if (existing != null) {
            LOGGER.debug("[NutritionBridge] Removed stale session for player {}", playerId);
        }

        // Initial values will be set on first tick when player is available
        @Nonnull UUID safePlayerId = Objects.requireNonNull(playerId);
        @Nonnull UUID safeQuestId = Objects.requireNonNull(questId);
        NutritionSession session = new NutritionSession(safePlayerId, safeQuestId, null);
        sessions.put(playerId, session);
        healthAccumulators.put(playerId, 0f);

        LOGGER.info("[NutritionBridge] Created nutrition session for player {} in quest {}",
            playerId.toString().substring(0, 8), questId.toString().substring(0, 8));
    }

    /**
     * Called when a quest ends for a player.
     *
     * @param playerId the player UUID
     */
    public void onQuestEnd(UUID playerId) {
        NutritionSession session = sessions.remove(playerId);
        healthAccumulators.remove(playerId);

        if (session != null) {
            LOGGER.info("[NutritionBridge] Ended nutrition session for player {} (balance: {:.1f}%)",
                playerId.toString().substring(0, 8), session.getBalance() * 100);
        }
    }

    /**
     * Called when a player disconnects - cleans up any orphaned session.
     *
     * <p>This prevents memory leaks when players disconnect during a quest
     * without the quest system calling {@link #onQuestEnd(UUID)}.
     *
     * @param playerId the disconnecting player's UUID
     */
    public void onPlayerDisconnect(UUID playerId) {
        NutritionSession session = sessions.remove(playerId);
        healthAccumulators.remove(playerId);

        if (session != null) {
            LOGGER.debug("[NutritionBridge] Cleaned up orphaned session for disconnected player {}",
                playerId.toString().substring(0, 8));
        }
    }

    /**
     * Check if a player has an active nutrition session.
     *
     * @param playerId the player UUID
     * @return true if player has an active session
     */
    public boolean hasSession(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    /**
     * Called every tick for active quest players.
     *
     * @param player the player
     */
    public void tick(ServerPlayer player) {
        if (!EasyDietCompat.isAvailable()) {
            return;
        }

        UUID playerId = player.getUUID();
        NutritionSession session = sessions.get(playerId);
        if (session == null) {
            return;
        }

        long currentTick = player.level().getGameTime();

        // Update values periodically
        if (currentTick % UPDATE_INTERVAL_TICKS == 0) {
            try {
                float[] newValues = com.devmod.compat.mods.easydiet.EasyDietBridge.getDietValues(player);
                session.updateValues(newValues, currentTick);
                updateAttributeModifiers(player, session);
                sendSyncToClient(player, session);
            } catch (Exception e) {
                LOGGER.debug("[NutritionBridge] Error updating nutrition: {}", e.getMessage());
            }
        }

        // Apply health changes
        if (currentTick % HEALTH_TICK_INTERVAL == 0) {
            applyHealthChange(player, session);
        }
    }

    // =========================================================================
    // ATTRIBUTE MODIFIERS
    // =========================================================================

    /**
     * Update player attribute modifiers based on nutrition state.
     */
    private void updateAttributeModifiers(ServerPlayer player, NutritionSession session) {
        // Speed modifier
        AttributeInstance speedAttr = player.getAttribute(ATTR_MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(SPEED_MODIFIER_ID);

            float speedMult = session.getSpeedMultiplier();
            if (Math.abs(speedMult - 1.0f) > 0.001f) {
                AttributeModifier speedMod = new AttributeModifier(
                    SPEED_MODIFIER_ID,
                    speedMult - 1.0f, // Convert multiplier to additive percentage
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                );
                speedAttr.addTransientModifier(speedMod);
            }
        }

        // Attack damage modifier
        AttributeInstance damageAttr = player.getAttribute(ATTR_ATTACK_DAMAGE);
        if (damageAttr != null) {
            damageAttr.removeModifier(DAMAGE_MODIFIER_ID);

            float damageMult = session.getDamageMultiplier();
            if (Math.abs(damageMult - 1.0f) > 0.001f) {
                AttributeModifier damageMod = new AttributeModifier(
                    DAMAGE_MODIFIER_ID,
                    damageMult - 1.0f,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                );
                damageAttr.addTransientModifier(damageMod);
            }
        }
    }

    /**
     * Remove all nutrition attribute modifiers from a player.
     */
    public void removeAttributeModifiers(ServerPlayer player) {
        AttributeInstance speedAttr = player.getAttribute(ATTR_MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(SPEED_MODIFIER_ID);
        }

        AttributeInstance damageAttr = player.getAttribute(ATTR_ATTACK_DAMAGE);
        if (damageAttr != null) {
            damageAttr.removeModifier(DAMAGE_MODIFIER_ID);
        }
    }

    // =========================================================================
    // HEALTH CHANGES
    // =========================================================================

    /**
     * Apply health regen or drain based on nutrition state.
     */
    private void applyHealthChange(ServerPlayer player, NutritionSession session) {
        float changePerSec = session.getHealthChangePerSecond();
        if (Math.abs(changePerSec) < 0.001f) {
            return;
        }

        UUID playerId = player.getUUID();
        float accumulated = healthAccumulators.getOrDefault(playerId, 0f);
        accumulated += changePerSec;

        // Apply when we have at least 1 HP of change
        if (Math.abs(accumulated) >= 1.0f) {
            int hpChange = (int) accumulated;
            float remainder = accumulated - hpChange;
            healthAccumulators.put(playerId, remainder);

            if (hpChange > 0) {
                // Heal
                player.heal(hpChange);
            } else {
                // Damage (but don't kill)
                float newHealth = player.getHealth() + hpChange;
                if (newHealth < 1.0f) {
                    newHealth = 1.0f;
                }
                player.setHealth(newHealth);
            }
        } else {
            healthAccumulators.put(playerId, accumulated);
        }
    }

    // =========================================================================
    // CLIENT SYNC
    // =========================================================================

    /**
     * Send nutrition sync payload to client.
     */
    private void sendSyncToClient(ServerPlayer player, NutritionSession session) {
        try {
            NutritionSyncPayload payload = NutritionSyncPayload.fromSession(session);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                Objects.requireNonNull(player),
                Objects.requireNonNull(payload)
            );
        } catch (Exception e) {
            LOGGER.debug("[NutritionBridge] Error sending sync: {}", e.getMessage());
        }
    }

    // =========================================================================
    // QUERIES
    // =========================================================================

    /**
     * Get the active session for a player.
     *
     * @param playerId the player UUID
     * @return the session, or empty if not in a quest
     */
    public Optional<NutritionSession> getSession(UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    /**
     * Get nutrition score for a player.
     *
     * @param player the player
     * @return balance 0.0-1.0, or 0.5 (neutral) if not available
     */
    public float getNutritionScore(ServerPlayer player) {
        if (!EasyDietCompat.isAvailable()) {
            return 0.5f; // Neutral - no buff/debuff
        }

        NutritionSession session = sessions.get(player.getUUID());
        if (session != null) {
            return session.getBalance();
        }

        // Fall back to direct query if no session
        try {
            return com.devmod.compat.mods.easydiet.EasyDietBridge.getNutritionBalance(player);
        } catch (Exception e) {
            return 0.5f; // Neutral on error
        }
    }

    /**
     * Get damage multiplier for combat calculations.
     *
     * @param player the player
     * @return multiplier (1.0 = no change)
     */
    public float getDamageMultiplier(ServerPlayer player) {
        NutritionSession session = sessions.get(player.getUUID());
        return session != null ? session.getDamageMultiplier() : 1.0f;
    }

    /**
     * Get damage reduction for combat calculations.
     *
     * @param player the player
     * @return reduction factor (can be negative for increased damage taken)
     */
    public float getDamageReduction(ServerPlayer player) {
        NutritionSession session = sessions.get(player.getUUID());
        return session != null ? session.getDamageReduction() : 0f;
    }

    /**
     * Get movement speed multiplier.
     *
     * @param player the player
     * @return multiplier (1.0 = no change)
     */
    public float getMovementSpeedMultiplier(ServerPlayer player) {
        NutritionSession session = sessions.get(player.getUUID());
        return session != null ? session.getSpeedMultiplier() : 1.0f;
    }

    /**
     * Check if healing is allowed for a player.
     *
     * @param player the player
     * @return true if healing is allowed
     */
    public boolean canHeal(ServerPlayer player) {
        NutritionSession session = sessions.get(player.getUUID());
        return session == null || session.isHealingAllowed();
    }

    /**
     * Check if player can perform executions.
     *
     * @param player the player
     * @return true if executions are allowed
     */
    public boolean canPerformExecutions(ServerPlayer player) {
        NutritionSession session = sessions.get(player.getUUID());
        return session == null || session.canPerformExecutions();
    }

    /**
     * Get combo decay multiplier.
     *
     * @param player the player
     * @return multiplier (> 1.0 = faster decay)
     */
    public float getComboDecayMultiplier(ServerPlayer player) {
        NutritionSession session = sessions.get(player.getUUID());
        return session != null ? session.getComboDecayMultiplier() : 1.0f;
    }

    /**
     * Check if player is well-fed.
     *
     * @param player the player
     * @return true if well-fed
     */
    public boolean isWellFed(ServerPlayer player) {
        NutritionSession session = sessions.get(player.getUUID());
        return session != null && session.isWellFed();
    }

    /**
     * Check if player is malnourished.
     *
     * @param player the player
     * @return true if malnourished
     */
    public boolean isMalnourished(ServerPlayer player) {
        NutritionSession session = sessions.get(player.getUUID());
        return session != null && session.isMalnourished();
    }

    /**
     * Check if player is in critical state.
     *
     * @param player the player
     * @return true if critical
     */
    public boolean isCritical(ServerPlayer player) {
        NutritionSession session = sessions.get(player.getUUID());
        return session != null && session.isCritical();
    }

    // =========================================================================
    // DEBUG
    // =========================================================================

    /**
     * Get count of active sessions.
     *
     * @return number of active nutrition sessions
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }

    /**
     * Get status summary for debugging.
     *
     * @return status string
     */
    public String getStatusSummary() {
        if (!EasyDietCompat.isAvailable()) {
            return "NutritionBridge: Easy-Diet not available";
        }
        return String.format("NutritionBridge: %d active sessions", sessions.size());
    }

    /**
     * Clear all sessions (for testing or shutdown).
     */
    public void clearAll() {
        sessions.clear();
        healthAccumulators.clear();
        LOGGER.debug("[NutritionBridge] Cleared all sessions");
    }
}
