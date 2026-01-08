package ru.letopis.util;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CooldownTracker {
    private final Map<UUID, Long> last = new HashMap<>();

    public boolean isReady(UUID uuid, long cooldownSeconds) {
        long now = Instant.now().getEpochSecond();
        Long lastTs = last.get(uuid);
        if (lastTs == null || (now - lastTs) >= cooldownSeconds) {
            last.put(uuid, now);
            return true;
        }
        return false;
    }
}
