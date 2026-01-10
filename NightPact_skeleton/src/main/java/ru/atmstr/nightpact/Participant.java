package ru.atmstr.nightpact;

import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;

public class Participant {

    private boolean sleeping;
    private boolean anxious;
    private Location bedLocation;
    private long lastCombatMillis;
    private final Map<String, Long> effectCooldowns = new HashMap<>();

    public boolean isSleeping() {
        return sleeping;
    }

    public void setSleeping(boolean sleeping) {
        this.sleeping = sleeping;
    }

    public boolean isAnxious() {
        return anxious;
    }

    public void setAnxious(boolean anxious) {
        this.anxious = anxious;
    }

    public Location getBedLocation() {
        return bedLocation;
    }

    public void setBedLocation(Location bedLocation) {
        this.bedLocation = bedLocation;
    }

    public long getLastCombatMillis() {
        return lastCombatMillis;
    }

    public void setLastCombatMillis(long lastCombatMillis) {
        this.lastCombatMillis = lastCombatMillis;
    }

    public boolean isOnCooldown(String key, long cooldownMillis, long now) {
        if (cooldownMillis <= 0) return false;
        long last = effectCooldowns.getOrDefault(key, 0L);
        return now - last < cooldownMillis;
    }

    public long getCooldownRemaining(String key, long cooldownMillis, long now) {
        long last = effectCooldowns.getOrDefault(key, 0L);
        long remaining = cooldownMillis - (now - last);
        return Math.max(0L, remaining);
    }

    public void setCooldown(String key, long now) {
        effectCooldowns.put(key, now);
    }
}
