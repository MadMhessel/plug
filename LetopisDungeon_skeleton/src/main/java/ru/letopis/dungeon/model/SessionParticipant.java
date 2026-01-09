package ru.letopis.dungeon.model;

import org.bukkit.Location;

public final class SessionParticipant {
    private boolean alive = true;
    private boolean active = false;
    private Location lastKnownLocation;

    public boolean isAlive() { return alive; }
    public boolean isActive() { return active; }
    public Location lastKnownLocation() { return lastKnownLocation; }

    public void setAlive(boolean alive) { this.alive = alive; }
    public void setActive(boolean active) { this.active = active; }
    public void setLastKnownLocation(Location loc) { this.lastKnownLocation = loc; }
}
