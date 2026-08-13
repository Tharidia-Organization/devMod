package com.devmod.endurance;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;

/**
 * Centralized structured logging for Endurance quest sequences.
 * Provides consistent formatting and phase tracking across all Endurance components.
 */
public final class EnduranceLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger("EnduranceFlow");
    private static final AtomicLong SEQUENCE_COUNTER = new AtomicLong(0);

    private EnduranceLogger() {}

    // ═══════════════════════════════════════════════════════════════
    // PHASE MARKERS
    // ═══════════════════════════════════════════════════════════════

    public enum Phase {
        QUEST_START("🚀 QUEST_START"),
        INSTANCE_CREATE("🏗️ INSTANCE_CREATE"),
        INSTANCE_READY("✅ INSTANCE_READY"),
        PLAYER_TELEPORT("🚶 PLAYER_TELEPORT"),
        KIT_APPLY("🎒 KIT_APPLY"),
        WAVE_COUNTDOWN("⏳ WAVE_COUNTDOWN"),
        WAVE_START("⚔️ WAVE_START"),
        MOB_SPAWN("👹 MOB_SPAWN"),
        MOB_DEATH("💀 MOB_DEATH"),
        WAVE_COMPLETE("🏆 WAVE_COMPLETE"),
        CHECKPOINT("💾 CHECKPOINT"),
        PERK_SELECTION("🎯 PERK_SELECTION"),
        SHOP_OPEN("🛒 SHOP_OPEN"),
        PLAYER_DEATH("☠️ PLAYER_DEATH"),
        PLAYER_RESPAWN("🔄 PLAYER_RESPAWN"),
        DEATH_SCREEN("📺 DEATH_SCREEN"),
        RESULTS_SCREEN("📊 RESULTS_SCREEN"),
        BOSS_SPAWN("👑 BOSS_SPAWN"),
        BOSS_DEFEAT("🎉 BOSS_DEFEAT"),
        QUEST_COMPLETE("🏅 QUEST_COMPLETE"),
        QUEST_FAIL("❌ QUEST_FAIL"),
        QUEST_ABANDON("🚪 QUEST_ABANDON"),
        INSTANCE_DESTROY("💥 INSTANCE_DESTROY"),
        CLEANUP("🧹 CLEANUP"),
        ERROR("🔥 ERROR");

        private final String marker;
        Phase(String marker) { this.marker = marker; }
        public String marker() { return marker; }
    }

    // ═══════════════════════════════════════════════════════════════
    // LOGGING METHODS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Log a phase transition with full context.
     */
    @FormatMethod
    public static void phase(Phase phase, @Nullable ServerPlayer player, @Nullable UUID questId, @FormatString String format, Object... args) {
        long seq = SEQUENCE_COUNTER.incrementAndGet();
        String playerName = player != null ? player.getName().getString() : "?";
        String questStr = questId != null ? questId.toString().substring(0, 8) : "no-quest";
        String message = String.format(format, args);

        LOGGER.info("[{}][seq={}][player={}][quest={}] {}",
            phase.marker(), seq, playerName, questStr, message);
    }

    /**
     * Log a phase transition without a player.
     */
    @FormatMethod
    public static void phase(Phase phase, @Nullable UUID questId, @FormatString String format, Object... args) {
        long seq = SEQUENCE_COUNTER.incrementAndGet();
        String questStr = questId != null ? questId.toString().substring(0, 8) : "no-quest";
        String message = String.format(format, args);

        LOGGER.info("[{}][seq={}][quest={}] {}",
            phase.marker(), seq, questStr, message);
    }

    /**
     * Log wave-specific events with wave context.
     */
    @FormatMethod
    public static void wave(Phase phase, @Nullable ServerPlayer player, UUID questId, int wave, int totalWaves, @FormatString String format, Object... args) {
        long seq = SEQUENCE_COUNTER.incrementAndGet();
        String playerName = player != null ? player.getName().getString() : "?";
        String questStr = questId.toString().substring(0, 8);
        String message = String.format(format, args);

        LOGGER.info("[{}][seq={}][player={}][quest={}][wave={}/{}] {}",
            phase.marker(), seq, playerName, questStr, wave, totalWaves, message);
    }

    /**
     * Log mob-related events with mob context.
     */
    @FormatMethod
    public static void mob(Phase phase, UUID questId, int wave, UUID mobId, String mobType, @FormatString String format, Object... args) {
        long seq = SEQUENCE_COUNTER.incrementAndGet();
        String questStr = questId.toString().substring(0, 8);
        String mobStr = mobId.toString().substring(0, 8);
        String message = String.format(format, args);

        LOGGER.info("[{}][seq={}][quest={}][wave={}][mob={}:{}] {}",
            phase.marker(), seq, questStr, wave, mobType, mobStr, message);
    }

    /**
     * Log state transitions.
     */
    public static void stateChange(ServerPlayer player, UUID questId, EnduranceQuestState from, EnduranceQuestState to, String reason) {
        long seq = SEQUENCE_COUNTER.incrementAndGet();
        String playerName = player != null ? player.getName().getString() : "?";
        String questStr = questId.toString().substring(0, 8);

        LOGGER.info("[📊 STATE][seq={}][player={}][quest={}] {} -> {} (reason: {})",
            seq, playerName, questStr, from, to, reason);
    }

    /**
     * Log errors with full context.
     */
    @FormatMethod
    public static void error(ServerPlayer player, @Nullable UUID questId, @FormatString String format, Object... args) {
        long seq = SEQUENCE_COUNTER.incrementAndGet();
        String playerName = player != null ? player.getName().getString() : "?";
        String questStr = questId != null ? questId.toString().substring(0, 8) : "no-quest";
        String message = String.format(format, args);

        LOGGER.error("[{}][seq={}][player={}][quest={}] {}",
            Phase.ERROR.marker(), seq, playerName, questStr, message);
    }

    /**
     * Log errors with exception.
     */
    public static void error(ServerPlayer player, @Nullable UUID questId, String message, Throwable t) {
        long seq = SEQUENCE_COUNTER.incrementAndGet();
        String playerName = player != null ? player.getName().getString() : "?";
        String questStr = questId != null ? questId.toString().substring(0, 8) : "no-quest";

        LOGGER.error("[{}][seq={}][player={}][quest={}] {}",
            Phase.ERROR.marker(), seq, playerName, questStr, message, t);
    }

    /**
     * Log timing information for performance tracking.
     */
    public static void timing(Phase phase, UUID questId, String operation, long durationMs) {
        long seq = SEQUENCE_COUNTER.incrementAndGet();
        String questStr = questId.toString().substring(0, 8);

        LOGGER.info("[⏱️ TIMING][seq={}][quest={}][phase={}] {} took {}ms",
            seq, questStr, phase.name(), operation, durationMs);
    }

    /**
     * Create a summary log entry for quest completion/failure.
     */
    public static void summary(ServerPlayer player, UUID questId, boolean success,
                                int wavesCompleted, int totalWaves, int kills, int deaths, long durationMs) {
        long seq = SEQUENCE_COUNTER.incrementAndGet();
        String playerName = player != null ? player.getName().getString() : "?";
        String questStr = questId.toString().substring(0, 8);
        String result = success ? "✅ SUCCESS" : "❌ FAILED";

        LOGGER.info("[📋 SUMMARY][seq={}][player={}][quest={}] {} | waves={}/{} | kills={} | deaths={} | duration={}s",
            seq, playerName, questStr, result, wavesCompleted, totalWaves, kills, deaths, durationMs / 1000);
    }

    /**
     * Reset sequence counter (for testing).
     */
    public static void resetSequence() {
        SEQUENCE_COUNTER.set(0);
    }
}
