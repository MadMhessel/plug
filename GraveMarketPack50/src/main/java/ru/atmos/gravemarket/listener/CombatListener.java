package ru.atmos.gravemarket.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import ru.atmos.gravemarket.GraveMarketPlugin;

public final class CombatListener implements Listener {

    private final GraveMarketPlugin plugin;

    public CombatListener(GraveMarketPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player victim) {
            plugin.combat().markCombat(victim.getUniqueId());
        }
        if (e.getDamager() instanceof Player attacker) {
            plugin.combat().markCombat(attacker.getUniqueId());
        }
    }
}
