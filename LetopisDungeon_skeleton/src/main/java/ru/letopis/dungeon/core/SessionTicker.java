package ru.letopis.dungeon.core;

import org.bukkit.scheduler.BukkitRunnable;
import ru.letopis.dungeon.LetopisDungeonPlugin;
import ru.letopis.dungeon.model.SessionState;

public final class SessionTicker extends BukkitRunnable {

    private final LetopisDungeonPlugin plugin;
    private final DungeonManager manager;

    public SessionTicker(LetopisDungeonPlugin plugin, DungeonManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public void run() {
        if (manager.session().state() == SessionState.IDLE) return;
        manager.tickSession();
    }

    public void start() {
        runTaskTimer(plugin, 20L, 20L);
    }
}
