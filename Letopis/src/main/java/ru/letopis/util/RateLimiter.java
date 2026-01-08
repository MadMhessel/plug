package ru.letopis.util;

import ru.letopis.model.Scale;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RateLimiter {
    private static final class Bucket {
        long minuteKey;
        double points;
    }

    private final Map<UUID, Map<Scale, Bucket>> buckets = new HashMap<>();

    public double applyLimit(UUID uuid, Scale scale, double delta, double maxPerMinute) {
        if (uuid == null) return delta;
        long minuteKey = Instant.now().getEpochSecond() / 60;
        Map<Scale, Bucket> byScale = buckets.computeIfAbsent(uuid, k -> new EnumMap<>(Scale.class));
        Bucket bucket = byScale.computeIfAbsent(scale, k -> new Bucket());
        if (bucket.minuteKey != minuteKey) {
            bucket.minuteKey = minuteKey;
            bucket.points = 0;
        }
        double allowed = Math.max(0.0, maxPerMinute - bucket.points);
        double applied = Math.min(delta, allowed);
        bucket.points += applied;
        return applied;
    }
}
