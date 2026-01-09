package ru.atmos.gravemarket.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import ru.atmos.gravemarket.GraveMarketPlugin;
import ru.atmos.gravemarket.grave.GraveRecord;
import ru.atmos.gravemarket.util.LocationCodec;
import ru.atmos.gravemarket.util.SafeSpotFinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class DeathListener implements Listener {

    private final GraveMarketPlugin plugin;
    private final Random rnd = new Random();

    public DeathListener(GraveMarketPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Location death = p.getLocation();

        boolean pvp = p.getKiller() != null && plugin.getConfig().getBoolean("pvp.enabled", true);
        double dropChance = Math.max(0.0, Math.min(1.0, plugin.getConfig().getDouble("pvp.partialDropChance", 0.30)));

        List<ItemStack> original = new ArrayList<>(e.getDrops());
        List<ItemStack> keepDrops = new ArrayList<>();
        List<ItemStack> toGrave = new ArrayList<>();

        for (ItemStack it : original) {
            if (it == null || it.getType() == Material.AIR) continue;
            if (pvp && rnd.nextDouble() < dropChance) keepDrops.add(it);
            else toGrave.add(it);
        }

        e.getDrops().clear();
        e.getDrops().addAll(keepDrops);

        int exp = e.getDroppedExp();
        int expToGrave = exp;
        if (pvp) {
            // keep some exp in world
            int keep = (int) Math.round(exp * dropChance);
            expToGrave = Math.max(0, exp - keep);
            e.setDroppedExp(keep);
        } else {
            e.setDroppedExp(0);
        }

        // Determine if we should force virtual
        boolean forceVirtual = false;
        EntityDamageEvent last = p.getLastDamageCause();
        if (last != null && last.getCause() == EntityDamageEvent.DamageCause.VOID) forceVirtual = true;

        // If death block is lava -> virtual
        if (!forceVirtual) {
            var b = death.getBlock();
            if (b.getType() == Material.LAVA || b.getType() == Material.WATER) forceVirtual = true;
        }

        int radius = plugin.getConfig().getInt("graves.safeSearchRadius", 8);
        Location safeBlock = forceVirtual ? null : SafeSpotFinder.findSafeBlockLocation(death, radius);

        GraveRecord r = plugin.graves().createGrave(
                p.getUniqueId(),
                p.getName(),
                death,
                safeBlock,
                toGrave,
                expToGrave,
                pvp
        );

        // Feedback
        p.sendMessage(Component.text("§6[Могила] §fВаши вещи сохранены. " +
                (r.virtual ? "§eВиртуальная могила§f (место небезопасно)." : "§aМогила установлена§f.")));

        Location graveLoc = r.virtual ? r.deathLoc() : r.graveLoc();
        if (graveLoc != null) {
            p.sendMessage(Component.text("§6[Могила] §fКоординаты: §e" + LocationCodec.pretty(graveLoc)));
        }

        // Quick actions (без внешних зависимостей)
        Component actions = Component.text("§6[Могила] §fДействия: ")
                .append(Component.text("§a[Инфо]").clickEvent(ClickEvent.runCommand("/grave info")))
                .append(Component.text(" §e[Компас]").clickEvent(ClickEvent.runCommand("/grave compass")))
                .append(Component.text(" §d[Луч]").clickEvent(ClickEvent.runCommand("/grave beacon")))
                .append(Component.text(" §c[Возврат на спавн]").clickEvent(ClickEvent.runCommand("/grave recall")));
        p.sendMessage(actions);

        // Small particles beacon at the place (optional)
        if (!r.virtual && plugin.getConfig().getBoolean("graves.spawnParticlesOnCreate", true) && safeBlock != null) {
            var w = safeBlock.getWorld();
            if (w != null) {
                w.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, safeBlock.clone().add(0.5, 1.0, 0.5), 14, 0.3, 0.6, 0.3, 0.02);
            }
        }
    }
}
