package ru.atmstr.nightpact;

import org.bukkit.World;
import org.bukkit.boss.BossBar;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WorldState {

    private final World world;
    private final Set<UUID> online = ConcurrentHashMap.newKeySet();
    private final Set<UUID> sleepers = ConcurrentHashMap.newKeySet();
    private BukkitRunnable countdownTask;
    private int countdownRemaining;
    private BossBar bossBar;

    public WorldState(World world) {
        this.world = world;
    }

    public World getWorld() {
        return world;
    }

    public Set<UUID> getOnline() {
        return online;
    }

    public Set<UUID> getSleepers() {
        return sleepers;
    }

    public BukkitRunnable getCountdownTask() {
        return countdownTask;
    }

    public void setCountdownTask(BukkitRunnable countdownTask) {
        this.countdownTask = countdownTask;
    }

    public int getCountdownRemaining() {
        return countdownRemaining;
    }

    public void setCountdownRemaining(int countdownRemaining) {
        this.countdownRemaining = countdownRemaining;
    }

    public BossBar getBossBar() {
        return bossBar;
    }

    public void setBossBar(BossBar bossBar) {
        this.bossBar = bossBar;
    }
}
