package ru.letopis.dungeon.listener;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import ru.letopis.dungeon.LetopisDungeonPlugin;

public final class ProtectionListener implements Listener {
    private final LetopisDungeonPlugin plugin;

    public ProtectionListener(LetopisDungeonPlugin plugin) { this.plugin = plugin; }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!plugin.getConfig().getBoolean("dungeon.rules.denyBlockBreak", true)) return;
        World w = e.getBlock().getWorld();
        if (plugin.dungeon().isDungeonWorld(w)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!plugin.getConfig().getBoolean("dungeon.rules.denyBlockPlace", true)) return;
        World w = e.getBlock().getWorld();
        if (plugin.dungeon().isDungeonWorld(w)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (!plugin.getConfig().getBoolean("dungeon.rules.denyExplosions", true)) return;
        World w = e.getLocation().getWorld();
        if (w != null && plugin.dungeon().isDungeonWorld(w)) {
            e.blockList().clear();
            e.setYield(0f);
        }
    }
}
