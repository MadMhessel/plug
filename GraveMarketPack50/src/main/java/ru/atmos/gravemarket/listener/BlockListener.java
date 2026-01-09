package ru.atmos.gravemarket.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import ru.atmos.gravemarket.GraveMarketPlugin;
import ru.atmos.gravemarket.grave.GraveRecord;

import java.util.Iterator;

public final class BlockListener implements Listener {

    private final GraveMarketPlugin plugin;

    public BlockListener(GraveMarketPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Location loc = e.getBlock().getLocation();
        GraveRecord g = plugin.graves().findByBlock(loc);
        if (g == null) return;

        // only admin can remove by breaking, to avoid dupes/abuse
        if (!e.getPlayer().hasPermission("gravemarket.admin")) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(Component.text("§6[Могила] §cНельзя ломать могилу. Заберите вещи и она исчезнет сама."));
        }
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        Iterator<org.bukkit.block.Block> it = e.blockList().iterator();
        while (it.hasNext()) {
            var b = it.next();
            if (plugin.graves().findByBlock(b.getLocation()) != null) {
                it.remove();
            }
        }
    }
}
