package ru.letopis.dungeon.model;

import org.bukkit.Location;

public final class SessionParticipant {
    private boolean alive = true;
    private boolean inSession = false;
    private Location lastKnownLocation;
    private long lastSeenTs;

    public boolean isAlive() { return alive; }
    public boolean isInSession() { return inSession; }
    public Location lastKnownLocation() { return lastKnownLocation; }
    public long lastSeenTs() { return lastSeenTs; }

    public void setAlive(boolean alive) { this.alive = alive; }
    public void setInSession(boolean inSession) { this.inSession = inSession; }
    public void setLastKnownLocation(Location loc) { this.lastKnownLocation = loc; }
    public void setLastSeenTs(long lastSeenTs) { this.lastSeenTs = lastSeenTs; }
}
