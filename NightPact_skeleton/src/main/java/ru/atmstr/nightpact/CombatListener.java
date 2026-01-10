package ru.atmstr.nightpact;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.entity.Projectile;
import org.bukkit.projectiles.ProjectileSource;

public class CombatListener implements Listener {

    private final NightPactPlugin plugin;

    public CombatListener(NightPactPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim) {
            if (plugin.getEnabledWorlds().contains(victim.getWorld())) {
                plugin.getSleepManager().tagCombat(victim);
            }
        }

        Player damagerPlayer = null;
        if (event.getDamager() instanceof Player direct) {
            damagerPlayer = direct;
        } else if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player shooter) {
                damagerPlayer = shooter;
            }
        }

        if (damagerPlayer != null && plugin.getEnabledWorlds().contains(damagerPlayer.getWorld())) {
            plugin.getSleepManager().tagCombat(damagerPlayer);
        }
    }
}
