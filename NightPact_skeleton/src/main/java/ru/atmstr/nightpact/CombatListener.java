package ru.atmstr.nightpact;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public class CombatListener implements Listener {

    private final NightPactPlugin plugin;
    private final SleepManager sleepManager;

    public CombatListener(NightPactPlugin plugin, SleepManager sleepManager) {
        this.plugin = plugin;
        this.sleepManager = sleepManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (!plugin.getEnabledWorlds().contains(p.getWorld())) return;

        sleepManager.tagCombat(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p) {
            if (!plugin.getEnabledWorlds().contains(p.getWorld())) return;
            sleepManager.tagCombat(p);
        }
    }
}
