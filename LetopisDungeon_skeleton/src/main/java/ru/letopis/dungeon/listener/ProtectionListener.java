package ru.letopis.dungeon.listener;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import ru.letopis.dungeon.LetopisDungeonPlugin;
import ru.letopis.dungeon.model.Session;
import ru.letopis.dungeon.model.SessionState;

public final class ProtectionListener implements Listener {
    private final LetopisDungeonPlugin plugin;

    public ProtectionListener(LetopisDungeonPlugin plugin) { this.plugin = plugin; }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!plugin.getConfig().getBoolean("dungeon.rules.denyBlockBreak", true)) return;
        World w = e.getBlock().getWorld();
        if (!plugin.dungeon().isDungeonWorld(w)) return;
        Session session = plugin.dungeon().session();
        if (session.state() != SessionState.RUNNING || !session.region().contains(e.getBlock().getLocation())) return;
        Player player = e.getPlayer();
        if (player.hasPermission("letodungeon.debug")) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!plugin.getConfig().getBoolean("dungeon.rules.denyBlockPlace", true)) return;
        World w = e.getBlock().getWorld();
        if (!plugin.dungeon().isDungeonWorld(w)) return;
        Session session = plugin.dungeon().session();
        if (session.state() != SessionState.RUNNING || !session.region().contains(e.getBlock().getLocation())) return;
        Player player = e.getPlayer();
        if (player.hasPermission("letodungeon.debug")) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (!plugin.getConfig().getBoolean("dungeon.rules.denyExplosions", true)) return;
        World w = e.getLocation().getWorld();
        if (w != null && plugin.dungeon().isDungeonWorld(w)) {
            Session session = plugin.dungeon().session();
            if (session.state() != SessionState.RUNNING || !session.region().contains(e.getLocation())) return;
            e.blockList().clear();
            e.setYield(0f);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Session session = plugin.dungeon().session();
        if (session.state() != SessionState.RUNNING) return;
        if (event.getTo() == null) return;
        if (!session.region().contains(event.getFrom())) return;
        if (session.region().contains(event.getTo())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(plugin.msg().prefix() + plugin.msg().get("session.teleportBlocked"));
    }
}
