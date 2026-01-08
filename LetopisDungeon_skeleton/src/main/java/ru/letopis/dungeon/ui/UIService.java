package ru.letopis.dungeon.ui;

import org.bukkit.Bukkit;
import org.bukkit.boss.*;
import org.bukkit.entity.Player;
import ru.letopis.dungeon.LetopisDungeonPlugin;
import ru.letopis.dungeon.model.Session;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class UIService {
    private final LetopisDungeonPlugin plugin;
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public UIService(LetopisDungeonPlugin plugin) {
        this.plugin = plugin;
    }

    public void show(Player p, Session s) {
        if (!plugin.getConfig().getBoolean("ui.bossbar.enabled", true)) return;
        BossBar b = bars.computeIfAbsent(p.getUniqueId(), id -> Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SOLID));
        b.addPlayer(p);
        update(p, s);
    }

    public void update(Player p, Session s) {
        if (!plugin.getConfig().getBoolean("ui.bossbar.enabled", true)) return;
        BossBar b = bars.computeIfAbsent(p.getUniqueId(), id -> Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SOLID));
        if (!b.getPlayers().contains(p)) b.addPlayer(p);

        int t = s.director().tension();
        b.setTitle("§aДанж§r | Напряжение: §e" + t + "§7/1000 §8|§r Участники: §d" + s.participantCount());
        b.setProgress(Math.max(0.0, Math.min(1.0, t / 1000.0)));

        if (t >= 750) b.setColor(BarColor.PURPLE);
        else if (t >= 500) b.setColor(BarColor.RED);
        else if (t >= 250) b.setColor(BarColor.YELLOW);
        else b.setColor(BarColor.GREEN);
    }

    public void clear(Player p) {
        BossBar b = bars.remove(p.getUniqueId());
        if (b != null) b.removeAll();
    }

    public void clearAll() {
        for (BossBar b : bars.values()) b.removeAll();
        bars.clear();
    }
}
