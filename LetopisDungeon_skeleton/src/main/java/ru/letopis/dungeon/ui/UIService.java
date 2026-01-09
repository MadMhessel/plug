package ru.letopis.dungeon.ui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
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
        BossBar b = bars.computeIfAbsent(p.getUniqueId(), id -> Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SEGMENTED_10));
        b.addPlayer(p);
        update(p, s);
    }

    public void update(Player p, Session s) {
        if (!plugin.getConfig().getBoolean("ui.bossbar.enabled", true)) return;
        BossBar b = bars.computeIfAbsent(p.getUniqueId(), id -> Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SEGMENTED_10));
        if (!b.getPlayers().contains(p)) b.addPlayer(p);

        String title = plugin.msg().get("ui.bossbar.title", Map.of(
                "room", s.currentRoomName(),
                "participants", Integer.toString(s.participantCount()),
                "wave", Integer.toString(s.currentWave()),
                "waveTotal", Integer.toString(s.currentWaveTotal())
        ));
        b.setTitle(title);
        b.setProgress(Math.max(0.0, Math.min(1.0, s.currentProgress())));

        if (s.currentProgress() >= 0.75) b.setColor(BarColor.RED);
        else if (s.currentProgress() >= 0.5) b.setColor(BarColor.YELLOW);
        else b.setColor(BarColor.GREEN);

        if (plugin.getConfig().getBoolean("ui.actionbar.enabled", true)) {
            String action = plugin.msg().get("ui.actionbar", Map.of("objective", s.currentObjective()));
            p.sendActionBar(Component.text(action));
        }
    }

    public void clear(Player p) {
        BossBar b = bars.remove(p.getUniqueId());
        if (b != null) b.removeAll();
        if (plugin.getConfig().getBoolean("ui.actionbar.enabled", true)) {
            p.sendActionBar(Component.empty());
        }
    }

    public void clearAll() {
        for (BossBar b : bars.values()) b.removeAll();
        bars.clear();
    }
}
