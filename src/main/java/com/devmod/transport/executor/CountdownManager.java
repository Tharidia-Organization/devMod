package com.devmod.transport.executor;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import com.devmod.transport.TransportColor;
import com.devmod.transport.network.TransportCountdownPayload;
import com.devmod.transport.network.TransportNetworkHandler;

/**
 * Manages countdown timers with BossBar display for transport operations.
 *
 * <p>Features:
 * <ul>
 *   <li>Phase-based countdown with color progression</li>
 *   <li>Distance-based player visibility (auto add/remove from BossBar)</li>
 *   <li>Callback execution on countdown completion</li>
 *   <li>Cancellation support</li>
 * </ul>
 *
 * <p>Pattern based on RiftStampManager's BossBar implementation.
 */
public final class CountdownManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CountdownManager.class);

    public static final CountdownManager INSTANCE = new CountdownManager();

    /** Default range for BossBar visibility (blocks squared). */
    private static final double BOSSBAR_RANGE_SQ = 48.0 * 48.0;

    /** Interval for updating BossBar name (ticks). */
    private static final int BOSSBAR_UPDATE_INTERVAL = 10;

    /** Active countdown sessions by unique ID. */
    private final Map<UUID, CountdownSession> activeSessions = new ConcurrentHashMap<>();

    private CountdownManager() {}

    /**
     * Starts a countdown timer with BossBar display.
     *
     * @param sessionId Unique identifier for this countdown
     * @param level The server level
     * @param centerPos Center position for distance calculations
     * @param durationTicks Total duration in ticks
     * @param translationKey Translation key for BossBar title (receives seconds as arg)
     * @param color Transport color for theming
     * @param onComplete Callback when countdown completes (null = no callback)
     * @return true if countdown started, false if session already exists
     */
    public boolean startCountdown(
            @Nonnull UUID sessionId,
            @Nonnull ServerLevel level,
            @Nonnull BlockPos centerPos,
            int durationTicks,
            @Nonnull String translationKey,
            @Nonnull TransportColor color,
            @Nullable Runnable onComplete) {

        if (activeSessions.containsKey(sessionId)) {
            LOGGER.warn("[CountdownManager] Session {} already exists", sessionId);
            return false;
        }

        long startTick = level.getGameTime();
        long endTick = startTick + durationTicks;

        // Create BossBar with initial title
        int initialSeconds = durationTicks / 20;
        ServerBossEvent bossBar = new ServerBossEvent(
            Component.translatable(translationKey, initialSeconds),
            toBossBarColor(color),
            BossEvent.BossBarOverlay.PROGRESS
        );
        bossBar.setDarkenScreen(false);
        bossBar.setCreateWorldFog(false);
        bossBar.setPlayBossMusic(false);
        bossBar.setProgress(1.0f);

        CountdownSession session = new CountdownSession(
            sessionId,
            level,
            centerPos,
            translationKey,
            color,
            bossBar,
            startTick,
            endTick,
            durationTicks,
            onComplete
        );

        activeSessions.put(sessionId, session);

        LOGGER.debug("[CountdownManager] Started countdown {} for {}s at {}",
            sessionId, initialSeconds, centerPos);

        return true;
    }

    /**
     * Starts a countdown for a specific player (tracked by player UUID).
     *
     * @param player The player this countdown is for
     * @param durationTicks Total duration in ticks
     * @param translationKey Translation key for BossBar title
     * @param color Transport color for theming
     * @param onComplete Callback when countdown completes
     * @return true if countdown started
     */
    public boolean startPlayerCountdown(
            @Nonnull ServerPlayer player,
            int durationTicks,
            @Nonnull String translationKey,
            @Nonnull TransportColor color,
            @Nullable Runnable onComplete) {

        UUID sessionId = player.getUUID();
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }

        return startCountdown(
            sessionId,
            level,
            player.blockPosition(),
            durationTicks,
            translationKey,
            color,
            onComplete
        );
    }

    /**
     * Cancels an active countdown.
     *
     * @param sessionId The session to cancel
     * @return true if cancelled, false if not found
     */
    public boolean cancelCountdown(@Nonnull UUID sessionId) {
        CountdownSession session = activeSessions.remove(sessionId);
        if (session == null) {
            return false;
        }

        cleanup(session);
        LOGGER.debug("[CountdownManager] Cancelled countdown {}", sessionId);
        return true;
    }

    /**
     * Checks if a countdown is active for the given session ID.
     */
    public boolean hasActiveCountdown(@Nonnull UUID sessionId) {
        return activeSessions.containsKey(sessionId);
    }

    /**
     * Gets the remaining ticks for a countdown.
     *
     * @return Remaining ticks, or -1 if not found
     */
    public int getRemainingTicks(@Nonnull UUID sessionId) {
        CountdownSession session = activeSessions.get(sessionId);
        if (session == null) {
            return -1;
        }
        long now = session.level.getGameTime();
        return Math.max(0, (int) (session.endTick - now));
    }

    /**
     * Gets the progress (0.0-1.0) for a countdown.
     *
     * @return Progress (1.0 = full, 0.0 = done), or -1 if not found
     */
    public float getProgress(@Nonnull UUID sessionId) {
        CountdownSession session = activeSessions.get(sessionId);
        if (session == null) {
            return -1.0f;
        }
        long now = session.level.getGameTime();
        long remaining = Math.max(0L, session.endTick - now);
        return (float) remaining / (float) session.totalDuration;
    }

    /**
     * Called every server tick to update all active countdowns.
     * Should be called from server tick event.
     */
    public void tick(@Nonnull MinecraftServer server) {
        if (activeSessions.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, CountdownSession>> iterator = activeSessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, CountdownSession> entry = iterator.next();
            CountdownSession session = entry.getValue();

            // Verify level is still valid
            ServerLevel level = session.level;
            if (level == null || level.getServer() == null) {
                iterator.remove();
                cleanup(session);
                continue;
            }

            long now = level.getGameTime();

            // Check if countdown completed
            if (now >= session.endTick) {
                iterator.remove();

                // Execute callback before cleanup
                if (session.onComplete != null) {
                    try {
                        session.onComplete.run();
                    } catch (Exception e) {
                        LOGGER.error("[CountdownManager] Error in completion callback for {}", session.sessionId, e);
                    }
                }

                cleanup(session);
                LOGGER.debug("[CountdownManager] Countdown {} completed", session.sessionId);
                continue;
            }

            // Update BossBar
            updateBossBar(session, now);

            if (now == session.startTick || now % BOSSBAR_UPDATE_INTERVAL == 0) {
                sendCountdownUpdate(session, now);
            }
        }
    }

    /**
     * Updates BossBar progress, color, name, and player visibility.
     */
    private void updateBossBar(CountdownSession session, long now) {
        ServerBossEvent bossBar = session.bossBar;
        ServerLevel level = session.level;

        // Calculate progress
        long remaining = Math.max(0L, session.endTick - now);
        float progress = (float) remaining / (float) session.totalDuration;
        bossBar.setProgress(Math.max(0.0f, Math.min(1.0f, progress)));

        // Update color based on progress phase
        BossEvent.BossBarColor color = getPhaseColor(session.color, progress);
        bossBar.setColor(color);

        // Update name periodically
        if (now % BOSSBAR_UPDATE_INTERVAL == 0) {
            int seconds = (int) Math.ceil(remaining / 20.0);
            bossBar.setName(Component.translatable(session.translationKey, seconds));
        }

        // Update player visibility based on distance
        double cx = session.centerPos.getX() + 0.5;
        double cy = session.centerPos.getY() + 0.5;
        double cz = session.centerPos.getZ() + 0.5;

        for (ServerPlayer player : level.players()) {
            double distSq = player.distanceToSqr(cx, cy, cz);
            if (distSq <= BOSSBAR_RANGE_SQ) {
                bossBar.addPlayer(player);
            } else {
                bossBar.removePlayer(player);
            }
        }
    }

    private void sendCountdownUpdate(CountdownSession session, long now) {
        ServerLevel level = session.level;
        if (level == null || level.getServer() == null) {
            return;
        }

        long remaining = Math.max(0L, session.endTick - now);
        int secondsRemaining = (int) Math.ceil(remaining / 20.0);
        int totalSeconds = (int) Math.ceil(session.totalDuration / 20.0);
        float progress = session.totalDuration > 0 ? (float) remaining / (float) session.totalDuration : 0.0f;

        int phase = progress > 0.66f
            ? 0
            : progress > 0.33f ? 1 : 2;

        TransportCountdownPayload payload = new TransportCountdownPayload(
            secondsRemaining,
            totalSeconds,
            phase,
            session.color.getIndex()
        );

        double cx = session.centerPos.getX() + 0.5;
        double cy = session.centerPos.getY() + 0.5;
        double cz = session.centerPos.getZ() + 0.5;

        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(cx, cy, cz) <= BOSSBAR_RANGE_SQ) {
                TransportNetworkHandler.sendCountdown(player, payload);
            }
        }
    }

    /**
     * Gets the BossBar color based on countdown phase and transport color.
     *
     * <p>Phase progression:
     * <ul>
     *   <li>100%-66%: Base color (or BLUE for neutral)</li>
     *   <li>66%-33%: PURPLE (warning)</li>
     *   <li>33%-0%: RED (urgent)</li>
     * </ul>
     */
    private BossEvent.BossBarColor getPhaseColor(TransportColor transportColor, float progress) {
        if (progress > 0.66f) {
            return toBossBarColor(transportColor);
        } else if (progress > 0.33f) {
            return BossEvent.BossBarColor.PURPLE;
        } else {
            return BossEvent.BossBarColor.RED;
        }
    }

    /**
     * Converts TransportColor to BossBarColor.
     */
    private BossEvent.BossBarColor toBossBarColor(TransportColor color) {
        return color.toBossBarColor();
    }

    /**
     * Cleans up a countdown session.
     */
    private void cleanup(CountdownSession session) {
        if (session.bossBar != null) {
            session.bossBar.removeAllPlayers();
        }
    }

    /**
     * Clears all active countdowns (for server shutdown/reload).
     */
    public void clearAll() {
        for (CountdownSession session : activeSessions.values()) {
            cleanup(session);
        }
        activeSessions.clear();
        LOGGER.info("[CountdownManager] Cleared all countdowns");
    }

    /**
     * Gets the number of active countdowns.
     */
    public int getActiveCount() {
        return activeSessions.size();
    }

    // =========================================================================
    // Inner classes
    // =========================================================================

    /**
     * Represents an active countdown session.
     */
    private record CountdownSession(
        UUID sessionId,
        ServerLevel level,
        BlockPos centerPos,
        String translationKey,
        TransportColor color,
        ServerBossEvent bossBar,
        long startTick,
        long endTick,
        int totalDuration,
        @Nullable Runnable onComplete
    ) {}
}
