package ru.letopis.dungeon.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import ru.letopis.dungeon.LetopisDungeonPlugin;

public final class SessionListener implements Listener {

    private final LetopisDungeonPlugin plugin;

    public SessionListener(LetopisDungeonPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        plugin.dungeon().handleInteract(event);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        plugin.dungeon().onPlayerDeath(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.dungeon().onPlayerQuit(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        plugin.dungeon().onPlayerChangedWorld(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.dungeon().onPlayerRespawn(event.getPlayer());
    }
}
