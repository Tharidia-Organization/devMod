package com.devmod.endurance;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnduranceEventCombatTest {

    @Test
    void recentCriticalKillMatchesTarget() throws Exception {
        UUID playerId = UUID.randomUUID();
        int entityId = 42;
        long now = System.currentTimeMillis();

        Map<UUID, Object> markerMap = getMarkerMap();
        markerMap.put(playerId, newMarker(entityId, now));

        boolean result = invokeIsRecentCriticalKill(playerId, entityId);
        assertTrue(result);
        assertFalse(markerMap.containsKey(playerId));
    }

    @Test
    void expiredCriticalKillReturnsFalse() throws Exception {
        UUID playerId = UUID.randomUUID();
        int entityId = 7;
        long window = getCriticalWindowMs();
        long expiredAt = System.currentTimeMillis() - window - 1;

        Map<UUID, Object> markerMap = getMarkerMap();
        markerMap.put(playerId, newMarker(entityId, expiredAt));

        boolean result = invokeIsRecentCriticalKill(playerId, entityId);
        assertFalse(result);
        assertFalse(markerMap.containsKey(playerId));
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Object> getMarkerMap() throws Exception {
        Field field = EnduranceEventCombat.class.getDeclaredField("lastCriticalHits");
        field.setAccessible(true);
        return (Map<UUID, Object>) field.get(null);
    }

    private static Object newMarker(int entityId, long timestamp) throws Exception {
        Class<?> markerClass = Class.forName("com.devmod.endurance.EnduranceEventCombat$CriticalHitMarker");
        Constructor<?> ctor = markerClass.getDeclaredConstructor(int.class, long.class);
        ctor.setAccessible(true);
        return ctor.newInstance(entityId, timestamp);
    }

    private static long getCriticalWindowMs() throws Exception {
        Field field = EnduranceEventCombat.class.getDeclaredField("CRITICAL_KILL_WINDOW_MS");
        field.setAccessible(true);
        return field.getLong(null);
    }

    private static boolean invokeIsRecentCriticalKill(UUID playerId, int entityId) throws Exception {
        Method method = EnduranceEventCombat.class.getDeclaredMethod("isRecentCriticalKill", UUID.class, int.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, playerId, entityId);
    }
}
