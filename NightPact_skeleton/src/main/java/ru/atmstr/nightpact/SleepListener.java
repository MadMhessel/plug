package ru.atmstr.nightpact;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class SleepListener implements Listener {

    private final NightPactPlugin plugin;
    private final SleepManager sleepManager;

    public SleepListener(NightPactPlugin plugin, SleepManager sleepManager) {
        this.plugin = plugin;
        this.sleepManager = sleepManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        Player p = event.getPlayer();
        World w = p.getWorld();
        if (!plugin.getEnabledWorlds().contains(w)) return;

        // Paper/Spigot: if event.getBed() is null on some versions, use player's location fallback.
        sleepManager.markSleeping(p, event.getBed() != null ? event.getBed().getLocation() : p.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedLeave(PlayerBedLeaveEvent event) {
        Player p = event.getPlayer();
        if (!plugin.getEnabledWorlds().contains(p.getWorld())) return;

        sleepManager.markAwake(p);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (!plugin.getEnabledWorlds().contains(p.getWorld())) return;

        sleepManager.trackOnline(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        sleepManager.untrack(p);
    }
}
