package ru.letopis.dungeon.model;

import org.bukkit.Location;

import java.time.Instant;
import java.util.*;

public final class Session {
    private SessionState state = SessionState.IDLE;
    private final Director director = new Director();
    private final Map<UUID, SessionParticipant> participants = new HashMap<>();

    private UUID sessionId;
    private UUID leaderId;
    private StartMode startMode = StartMode.DIRECT_DIMENSION;
    private SessionRegion region;
    private Location roomStartLocation;
    private Location entryLocation;

    private long startedAtEpoch = 0L;
    private int elapsedSeconds = 0;
    private int currentRoomIndex = 0;
    private String currentRoomName = "";
    private String currentObjective = "";
    private double currentProgress = 0.0;
    private int currentWave = 0;
    private int currentWaveTotal = 0;

    public void startNew(UUID sessionId, UUID leaderId, StartMode mode, SessionRegion region, Location roomStart) {
        this.state = SessionState.RUNNING;
        this.sessionId = sessionId;
        this.leaderId = leaderId;
        this.startMode = mode;
        this.region = region;
        this.roomStartLocation = roomStart;
        this.director.setTension(0);
        this.participants.clear();
        this.startedAtEpoch = Instant.now().getEpochSecond();
        this.elapsedSeconds = 0;
        this.currentRoomIndex = 0;
        this.currentRoomName = "";
        this.currentObjective = "";
        this.currentProgress = 0.0;
        this.currentWave = 0;
        this.currentWaveTotal = 0;
    }

    public void finish() {
        this.state = SessionState.FINISHED;
    }

    public void tick(int seconds) { this.elapsedSeconds += seconds; }

    public boolean isTimeOver(int maxMinutes) {
        return elapsedSeconds >= maxMinutes * 60;
    }

    public SessionState state() { return state; }
    public Director director() { return director; }
    public int elapsedSeconds() { return elapsedSeconds; }
    public UUID sessionId() { return sessionId; }
    public UUID leaderId() { return leaderId; }
    public StartMode startMode() { return startMode; }
    public SessionRegion region() { return region; }
    public Location roomStartLocation() { return roomStartLocation; }
    public Location entryLocation() { return entryLocation; }

    public void setEntryLocation(Location entryLocation) { this.entryLocation = entryLocation; }

    public void markParticipant(UUID uuid) {
        participants.computeIfAbsent(uuid, key -> new SessionParticipant());
    }

    public void markAlive(UUID uuid, boolean alive) {
        participants.computeIfAbsent(uuid, key -> new SessionParticipant()).setAlive(alive);
    }

    public void markActive(UUID uuid, boolean active) {
        participants.computeIfAbsent(uuid, key -> new SessionParticipant()).setActive(active);
    }

    public void updateLocation(UUID uuid, Location loc) {
        participants.computeIfAbsent(uuid, key -> new SessionParticipant()).setLastKnownLocation(loc);
    }

    public Map<UUID, SessionParticipant> participants() { return Collections.unmodifiableMap(participants); }

    public int participantCount() {
        int count = 0;
        for (SessionParticipant p : participants.values()) if (p.isActive()) count++;
        return count;
    }

    public int aliveCount() {
        int count = 0;
        for (SessionParticipant p : participants.values()) if (p.isAlive()) count++;
        return count;
    }

    public void setRoomInfo(String name, String objective, double progress, int wave, int waveTotal) {
        this.currentRoomName = name;
        this.currentObjective = objective;
        this.currentProgress = progress;
        this.currentWave = wave;
        this.currentWaveTotal = waveTotal;
    }

    public String currentRoomName() { return currentRoomName; }
    public String currentObjective() { return currentObjective; }
    public double currentProgress() { return currentProgress; }
    public int currentWave() { return currentWave; }
    public int currentWaveTotal() { return currentWaveTotal; }

    public int currentRoomIndex() { return currentRoomIndex; }
    public void setCurrentRoomIndex(int currentRoomIndex) { this.currentRoomIndex = currentRoomIndex; }

    public void resetParticipants() { participants.clear(); }

    public boolean isLeader(UUID uuid) {
        return leaderId != null && leaderId.equals(uuid);
    }
}
