package ru.letopis.model;

import java.util.EnumMap;
import java.util.Map;

public final class WorldState {
    private final String world;
    private final Map<Scale, Double> values = new EnumMap<>(Scale.class);
    private long lastDecayTs;
    private String activeEventId;
    private Long eventEndTs;
    private Long cooldownUntilTs;
    private long lastDangerousEventTs;

    public WorldState(String world) {
        this.world = world;
        for (Scale scale : Scale.values()) {
            values.put(scale, 0.0);
        }
    }

    public String world() {
        return world;
    }

    public double get(Scale scale) {
        return values.getOrDefault(scale, 0.0);
    }

    public void set(Scale scale, double value) {
        values.put(scale, Math.max(0.0, value));
    }

    public void add(Scale scale, double delta, double max) {
        double value = Math.min(max, Math.max(0.0, get(scale) + delta));
        values.put(scale, value);
    }

    public Map<Scale, Double> values() {
        return values;
    }

    public long lastDecayTs() {
        return lastDecayTs;
    }

    public void setLastDecayTs(long lastDecayTs) {
        this.lastDecayTs = lastDecayTs;
    }

    public String activeEventId() {
        return activeEventId;
    }

    public void setActiveEventId(String activeEventId) {
        this.activeEventId = activeEventId;
    }

    public Long eventEndTs() {
        return eventEndTs;
    }

    public void setEventEndTs(Long eventEndTs) {
        this.eventEndTs = eventEndTs;
    }

    public Long cooldownUntilTs() {
        return cooldownUntilTs;
    }

    public void setCooldownUntilTs(Long cooldownUntilTs) {
        this.cooldownUntilTs = cooldownUntilTs;
    }

    public long lastDangerousEventTs() {
        return lastDangerousEventTs;
    }

    public void setLastDangerousEventTs(long lastDangerousEventTs) {
        this.lastDangerousEventTs = lastDangerousEventTs;
    }
}
