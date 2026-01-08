package ru.letopis.util;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ChunkPenalty {
    private final Map<UUID, Map<String, Long>> lastUse = new HashMap<>();

    public double apply(UUID uuid, String world, int x, int z, long windowSeconds, double multiplier) {
        if (uuid == null) return 1.0;
        long now = Instant.now().getEpochSecond();
        Map<String, Long> byChunk = lastUse.computeIfAbsent(uuid, k -> new HashMap<>());
        String key = world + ":" + x + ":" + z;
        Long last = byChunk.get(key);
        byChunk.put(key, now);
        if (last != null && (now - last) <= windowSeconds) {
            return multiplier;
        }
        return 1.0;
    }
}
