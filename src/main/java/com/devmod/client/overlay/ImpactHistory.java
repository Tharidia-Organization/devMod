package com.devmod.client.overlay;

import com.devmod.config.Config;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores recent impacts per player for HUD history.
 */
public final class ImpactHistory {
    private static final int DEFAULT_MAX_HISTORY = 10;

    private static final Map<UUID, Deque<ImpactData>> HISTORY_BY_PLAYER = new ConcurrentHashMap<>();

    private ImpactHistory() {}

    public static void record(ImpactData data) {
        if (data == null || data.attackerUUID == null) {
            return;
        }

        Deque<ImpactData> history = HISTORY_BY_PLAYER.computeIfAbsent(data.attackerUUID, id -> new ArrayDeque<>());
        synchronized (history) {
            history.addFirst(data);
            int limit = getMaxEntries();
            while (history.size() > limit) {
                history.removeLast();
            }
        }
    }

    public static List<ImpactData> getRecent(UUID playerId, int count) {
        if (playerId == null || count <= 0) {
            return List.of();
        }

        Deque<ImpactData> history = HISTORY_BY_PLAYER.get(playerId);
        if (history == null) {
            return List.of();
        }

        List<ImpactData> result = new ArrayList<>();
        synchronized (history) {
            int added = 0;
            for (ImpactData data : history) {
                result.add(data);
                if (++added >= count) {
                    break;
                }
            }
        }
        return List.copyOf(result);
    }

    public static int getMaxEntries() {
        try {
            return Config.IMPACT_HUD_HISTORY_COUNT.get();
        } catch (Exception ignored) {
            return DEFAULT_MAX_HISTORY;
        }
    }

    public static void clearForPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        HISTORY_BY_PLAYER.remove(playerId);
    }

    public static void clearAll() {
        HISTORY_BY_PLAYER.clear();
    }
}
