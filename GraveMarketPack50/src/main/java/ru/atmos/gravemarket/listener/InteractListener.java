package ru.atmos.gravemarket.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import ru.atmos.gravemarket.GraveMarketPlugin;
import ru.atmos.gravemarket.grave.GraveRecord;
import ru.atmos.gravemarket.util.LocationCodec;

public final class InteractListener implements Listener {

    private final GraveMarketPlugin plugin;

    public InteractListener(GraveMarketPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getClickedBlock() == null) return;
        Block b = e.getClickedBlock();

        Location bl = b.getLocation();
        GraveRecord g = plugin.graves().findByBlock(bl);
        if (g == null) return;

        var actor = e.getPlayer();
        if (!plugin.graves().isAuthorized(g, actor.getUniqueId())) {
            e.setCancelled(true);
            actor.sendMessage(Component.text("§6[Могила] §cЭто не ваша могила.").append(Component.text(" ")).append(
                    Component.text("§e(Можно доверить через /grave trust)").clickEvent(ClickEvent.suggestCommand("/grave trust "))));
            return;
        }

        if (!g.paid) {
            e.setCancelled(true);
            long cost = g.extractCost;
            actor.sendMessage(Component.text("§6[Могила] §eДоступ к вещам закрыт до оплаты извлечения: §6" + cost + " " + plugin.economy().currencyName()));
            actor.sendMessage(Component.text("§6[Могила] ").append(
                    Component.text("§a[Оплатить]").clickEvent(ClickEvent.runCommand("/grave pay"))).append(
                    Component.text(" §7Баланс: §f" + plugin.economy().balance(actor.getUniqueId()))));
            actor.sendMessage(Component.text("§6[Могила] §7Координаты: §f" + LocationCodec.pretty(g.graveLoc())));
            return;
        }

        if (plugin.audit() != null) {
            plugin.audit().log("OPEN", actor.getUniqueId(), g.owner, g.id, g.graveLoc(), "");
        }
    }
}
