package com.devmod.endurance.combat;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

import com.devmod.endurance.ComboSystem.ActionType;
import com.devmod.endurance.combat.api.ComboEvent;
import com.devmod.endurance.combat.api.IComboEventListener;
import com.devmod.endurance.combat.api.IComboSession;
import com.devmod.endurance.combat.core.ComboSessionImpl;
import com.devmod.endurance.combat.events.ChallengeComboListener;
import com.devmod.endurance.combat.events.ComboEventDispatcher;
import com.devmod.endurance.combat.events.TelemetryComboListener;

/**
 * Facade for the combo system.
 *
 * <p>This is the single entry point for all combo-related operations.
 * It manages session lifecycle, delegates to the appropriate handlers,
 * and dispatches events to registered listeners.</p>
 *
 * <h2>Design Goals</h2>
 * <ul>
 *   <li>Single source of truth for combo sessions (no dual storage)</li>
 *   <li>Decoupled event handling via dispatcher</li>
 *   <li>Clean API for external consumers</li>
 *   <li>Testable via interface abstraction</li>
 * </ul>
 *
 * <h2>Migration Note</h2>
 * <p>This facade replaces the scattered session management in:
 * <ul>
 *   <li>{@code ComboSystem.INSTANCE.activeSessions}</li>
 *   <li>{@code EnduranceEventCombat.comboSessions}</li>
 * </ul>
 * During migration, both systems can coexist with this facade being
 * the authoritative source.</p>
 *
 * @see IComboSession for session interface
 * @see IComboEventListener for event handling
 */
public final class ComboSystemFacade {

    private static final Logger LOGGER = LoggerFactory.getLogger(ComboSystemFacade.class);

    // Singleton instance - consider DI in future
    private static ComboSystemFacade instance;

    // === Core State ===

    /** Single source of truth for all active combo sessions. */
    private final Map<UUID, IComboSession> sessions = new ConcurrentHashMap<>();

    /** Event dispatcher for decoupled notifications. */
    private final ComboEventDispatcher eventDispatcher = new ComboEventDispatcher();

    // === Initialization ===

    private ComboSystemFacade() {
        // Register default listeners
        eventDispatcher.addListener(new TelemetryComboListener());
        eventDispatcher.addListener(new ChallengeComboListener());
        LOGGER.debug("[ComboSystem] Registered default listeners: Telemetry, Challenge");
    }

    /**
     * Initialize the facade with default configuration.
     * Call this during mod initialization.
     */
    public static void initialize() {
        if (instance != null) {
            LOGGER.warn("[ComboSystem] Facade already initialized, reinitializing");
        }
        instance = new ComboSystemFacade();
        LOGGER.info("[ComboSystem] Facade initialized with {} listeners",
            instance.eventDispatcher.getListenerCount());
    }

    /**
     * Get the singleton instance.
     *
     * @throws IllegalStateException if not initialized
     */
    public static ComboSystemFacade get() {
        if (instance == null) {
            throw new IllegalStateException("ComboSystemFacade not initialized. Call initialize() first.");
        }
        return instance;
    }

    /**
     * Check if facade is initialized.
     */
    public static boolean isInitialized() {
        return instance != null;
    }

    // === Listener Management ===

    /**
     * Register an event listener.
     *
     * @param listener The listener to register
     */
    public void addListener(IComboEventListener listener) {
        eventDispatcher.addListener(listener);
    }

    /**
     * Remove an event listener.
     *
     * @param listener The listener to remove
     */
    public void removeListener(IComboEventListener listener) {
        eventDispatcher.removeListener(listener);
    }

    // === Session Management ===

    /**
     * Start a new combo session for a player.
     *
     * @param playerId The player's UUID
     * @param questId The quest's UUID
     * @return The created session
     */
    public IComboSession startSession(UUID playerId, UUID questId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(questId, "questId");

        // Check for existing session
        IComboSession existing = sessions.get(playerId);
        if (existing != null) {
            LOGGER.warn("[ComboSystem] Player {} already has active session for quest {}, ending it",
                playerId, existing.getQuestId());
            endSession(playerId);
        }

        // Create new session
        // TODO: Replace with ComboSessionImpl when implemented
        IComboSession session = createSession(playerId, questId);
        sessions.put(playerId, session);

        // Dispatch event
        eventDispatcher.dispatch(new ComboEvent.SessionStarted(
            playerId, questId, System.currentTimeMillis()
        ));

        LOGGER.info("[ComboSystem] Started session for player {} in quest {}", playerId, questId);
        return session;
    }

    /**
     * Get an active session for a player.
     *
     * @param playerId The player's UUID
     * @return Optional containing the session if active
     */
    public Optional<IComboSession> getSession(UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    /**
     * Check if a player has an active session.
     *
     * @param playerId The player's UUID
     * @return true if session exists
     */
    public boolean hasSession(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    /**
     * End a session and return final state.
     *
     * @param playerId The player's UUID
     * @return The ended session, or null if none existed
     */
    @Nullable
    public IComboSession endSession(UUID playerId) {
        IComboSession session = sessions.remove(playerId);
        if (session != null) {
            // Dispatch end event
            eventDispatcher.dispatch(new ComboEvent.SessionEnded(
                playerId,
                session.getQuestId(),
                System.currentTimeMillis(),
                session.getFinalScore(),
                session.getMaxCombo(),
                session.getHighestRank()
            ));

            LOGGER.info("[ComboSystem] Ended session for player {}: score={}, maxCombo={}, rank={}",
                playerId, session.getFinalScore(), session.getMaxCombo(), session.getHighestRank());
        }
        return session;
    }

    /**
     * End all active sessions.
     * Call this during server shutdown.
     */
    public void endAllSessions() {
        LOGGER.info("[ComboSystem] Ending all {} active sessions", sessions.size());
        for (UUID playerId : sessions.keySet()) {
            endSession(playerId);
        }
    }

    // === Event Handlers ===

    /**
     * Handle player attack.
     *
     * @param player The attacking player
     * @param target The attack target
     * @param damage Damage dealt
     * @param isCritical Whether it was a critical hit
     * @param isAerial Whether it was an aerial attack
     * @param isBackstab Whether it was a backstab
     * @return Action result, or null if no active session
     */
    @Nullable
    public IComboSession.ActionResult onPlayerAttack(
            ServerPlayer player,
            LivingEntity target,
            float damage,
            boolean isCritical,
            boolean isAerial,
            boolean isBackstab) {

        IComboSession session = sessions.get(player.getUUID());
        if (session == null) return null;

        // Determine action type
        ActionType action;
        if (isCritical) {
            action = ActionType.CRITICAL_HIT;
        } else if (isAerial) {
            action = ActionType.AERIAL_ATTACK;
        } else if (isBackstab) {
            action = ActionType.BACKSTAB;
        } else if (damage >= 10) {
            action = ActionType.HEAVY_ATTACK;
        } else {
            action = ActionType.LIGHT_ATTACK;
        }

        IComboSession.ActionResult result = session.registerAction(action, damage);

        // Play sound on rank up
        if (result.announcement() != null && result.announcement().isRankUp()) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                Objects.requireNonNull(SoundEvents.PLAYER_LEVELUP), SoundSource.PLAYERS, 1.0f, 1.5f);
        }

        return result;
    }

    /**
     * Handle mob killed by player.
     *
     * @param player The player who killed the mob
     * @param mob The killed mob
     * @param overkillDamage Damage beyond killing blow
     * @param killTime Time in ms from first hit to kill
     * @return Action result, or null if no active session
     */
    @Nullable
    public IComboSession.ActionResult onMobKilled(
            ServerPlayer player,
            LivingEntity mob,
            float overkillDamage,
            long killTime) {

        IComboSession session = sessions.get(player.getUUID());
        if (session == null) return null;

        boolean wasQuick = killTime < 5000; // Under 5 seconds
        return session.registerKill(wasQuick, overkillDamage);
    }

    /**
     * Handle player taking damage.
     *
     * @param player The damaged player
     * @param damage Damage taken
     */
    public void onPlayerDamaged(ServerPlayer player, float damage) {
        IComboSession session = sessions.get(player.getUUID());
        if (session != null) {
            session.onDamageTaken(damage);
        }
    }

    /**
     * Handle wave completion.
     *
     * @param player The player
     * @return Action result for no-damage wave bonus, or null
     */
    @Nullable
    public IComboSession.ActionResult onWaveCompleted(ServerPlayer player) {
        IComboSession session = sessions.get(player.getUUID());
        if (session != null) {
            // Check for no-damage wave bonus
            // This will be handled by the session implementation
            session.startNewWave();
        }
        return null;
    }

    // === Tick Processing ===

    /**
     * Process tick for all active sessions.
     * Call this every server tick.
     */
    public void tick() {
        for (IComboSession session : sessions.values()) {
            session.tick();
        }
    }

    // === Stats ===

    /**
     * Get number of active sessions.
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }

    // === Internal ===

    /**
     * Create a session implementation using the new decomposed architecture.
     */
    private IComboSession createSession(UUID playerId, UUID questId) {
        return new ComboSessionImpl(playerId, questId, eventDispatcher);
    }

    /**
     * Get the event dispatcher (for testing or advanced use cases).
     */
    public ComboEventDispatcher getEventDispatcher() {
        return eventDispatcher;
    }
}
