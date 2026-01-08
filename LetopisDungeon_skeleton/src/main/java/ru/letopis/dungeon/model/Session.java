package ru.letopis.dungeon.model;

import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class Session {
    private SessionState state = SessionState.IDLE;
    private final Director director = new Director();
    private final Set<UUID> participants = new HashSet<>();

    private long startedAtEpoch = 0L;
    private int elapsedSeconds = 0;

    public void startNew() {
        this.state = SessionState.RUNNING;
        this.director.setTension(0);
        this.participants.clear();
        this.startedAtEpoch = Instant.now().getEpochSecond();
        this.elapsedSeconds = 0;
    }

    public void finish() { this.state = SessionState.FINISHED; }

    public void tick(int seconds) { this.elapsedSeconds += seconds; }

    public boolean isTimeOver(JavaPlugin plugin) {
        int maxMinutes = plugin.getConfig().getInt("dungeon.run.maxMinutes", 35);
        return elapsedSeconds >= maxMinutes * 60;
    }

    public SessionState state() { return state; }
    public Director director() { return director; }
    public int elapsedSeconds() { return elapsedSeconds; }

    public void markParticipant(UUID uuid) { participants.add(uuid); }
    public int participantCount() { return participants.size(); }
}
