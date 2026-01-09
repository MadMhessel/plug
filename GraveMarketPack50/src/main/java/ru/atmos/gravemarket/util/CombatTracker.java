package ru.atmos.gravemarket.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatTracker {

    private final Map<UUID, Long> lastCombat = new ConcurrentHashMap<>();

    public void markCombat(UUID playerId) {
        if (playerId == null) return;
        lastCombat.put(playerId, System.currentTimeMillis());
    }

    public boolean isLocked(UUID playerId, long lockSeconds) {
        if (playerId == null || lockSeconds <= 0) return false;
        long last = lastCombat.getOrDefault(playerId, 0L);
        return (System.currentTimeMillis() - last) < lockSeconds * 1000L;
    }
}
