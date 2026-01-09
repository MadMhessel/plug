package ru.atmos.gravemarket.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import ru.atmos.gravemarket.GraveMarketPlugin;

public final class ReturnListener implements Listener {

    private final GraveMarketPlugin plugin;

    public ReturnListener(GraveMarketPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        int count = plugin.returns().deliver(e.getPlayer());
        if (count > 0) {
            e.getPlayer().sendMessage(Component.text("§6[Могила] §aВам возвращены предметы: §e" + count));
        }
    }
}
