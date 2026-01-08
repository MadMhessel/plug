package ru.letopis.dungeon.core;

import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import ru.letopis.dungeon.LetopisDungeonPlugin;
import ru.letopis.dungeon.model.PlayerReturnPoint;
import ru.letopis.dungeon.model.Session;
import ru.letopis.dungeon.model.SessionState;
import ru.letopis.dungeon.ui.UIService;
import ru.letopis.dungeon.util.SafeTeleport;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DungeonManager {

    private final LetopisDungeonPlugin plugin;

    private World dungeonWorld;
    private final Session session = new Session();
    private final UIService ui;

    private final Map<UUID, PlayerReturnPoint> returnPoints = new HashMap<>();
    private BukkitRunnable tickTask;

    public DungeonManager(LetopisDungeonPlugin plugin) {
        this.plugin = plugin;
        this.ui = new UIService(plugin);
    }

    public void reload() {
        // TODO: load rooms.yml and runtime settings
    }

    public void shutdown() {
        if (tickTask != null) tickTask.cancel();
        ui.clearAll();
    }

    public World ensureDungeonWorld() {
        String worldName = plugin.getConfig().getString("dungeon.worldName", "letopis_dungeon");
        World w = Bukkit.getWorld(worldName);
        if (w == null) {
            WorldCreator wc = new WorldCreator(worldName);
            wc.environment(World.Environment.NORMAL);
            wc.type(WorldType.FLAT);
            w = wc.createWorld();
        }
        this.dungeonWorld = w;

        if (tickTask == null) {
            tickTask = new BukkitRunnable() {
                @Override public void run() { tick(); }
            };
            tickTask.runTaskTimer(plugin, 20L, 20L);
        }
        return w;
    }

    public boolean isDungeonWorld(World w) {
        return dungeonWorld != null && w != null && dungeonWorld.getUID().equals(w.getUID());
    }

    public Session session() { return session; }

    public void join(Player p) {
        ensureDungeonWorld();
        returnPoints.put(p.getUniqueId(), PlayerReturnPoint.fromPlayer(p));
        SafeTeleport.teleport(p, lobbyLocation());
        p.sendMessage(plugin.msg().prefix() + "Вы в лобби данжа.");
        ui.show(p, session);
    }

    public void leave(Player p) {
        var rp = returnPoints.remove(p.getUniqueId());
        if (rp != null) SafeTeleport.teleport(p, rp.toLocation());
        ui.clear(p);
    }

    public void startRun(CommandSender initiator) {
        ensureDungeonWorld();
        if (session.state() == SessionState.RUNNING) {
            initiator.sendMessage(plugin.msg().prefix() + "Забег уже идёт.");
            return;
        }
        session.startNew();
        broadcastToDungeon(plugin.msg().prefix() + "Забег начался. Режиссёр не любит афк.");
        // TODO: build route, place entry room, mark participants properly
    }

    public void stopRun(CommandSender initiator) {
        if (session.state() == SessionState.IDLE) {
            initiator.sendMessage(plugin.msg().prefix() + "Забега нет.");
            return;
        }
        session.finish();
        broadcastToDungeon(plugin.msg().prefix() + "Забег остановлен.");
        ui.clearAll();
    }

    public void setTension(int v) { session.director().setTension(v); }

    public Location lobbyLocation() {
        ensureDungeonWorld();
        double x = plugin.getConfig().getDouble("dungeon.lobby.x", 0);
        double y = plugin.getConfig().getDouble("dungeon.lobby.y", 80);
        double z = plugin.getConfig().getDouble("dungeon.lobby.z", 0);
        float yaw = (float) plugin.getConfig().getDouble("dungeon.lobby.yaw", 0);
        float pitch = (float) plugin.getConfig().getDouble("dungeon.lobby.pitch", 0);
        return new Location(dungeonWorld, x, y, z, yaw, pitch);
    }

    private void tick() {
        if (dungeonWorld == null) return;

        // UI update for everyone in dungeon world
        for (Player p : dungeonWorld.getPlayers()) {
            ui.update(p, session);
        }

        if (session.state() == SessionState.RUNNING) {
            session.tick(1);
            if (session.isTimeOver(plugin)) {
                broadcastToDungeon(plugin.msg().prefix() + "Время вышло. Забег завершён.");
                session.finish();
            }
        }
    }

    public void broadcastToDungeon(String msg) {
        if (dungeonWorld == null) return;
        for (Player p : dungeonWorld.getPlayers()) p.sendMessage(msg);
    }

    public boolean placeStructure(String structureKey, Location origin) {
        String tpl = plugin.getConfig().getString("dungeon.structure.placeCommandTemplate",
                "place structure %key% %x% %y% %z%");
        String cmd = tpl.replace("%key%", structureKey)
                .replace("%x%", Integer.toString(origin.getBlockX()))
                .replace("%y%", Integer.toString(origin.getBlockY()))
                .replace("%z%", Integer.toString(origin.getBlockZ()));
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }
}
