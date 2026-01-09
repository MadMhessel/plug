package ru.atmos.gravemarket.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import ru.atmos.gravemarket.GraveMarketPlugin;
import ru.atmos.gravemarket.grave.GraveRecord;
import ru.atmos.gravemarket.util.LocationCodec;
import ru.atmos.gravemarket.util.SafeSpotFinder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class DeathListener implements Listener {

    private final GraveMarketPlugin plugin;

    public DeathListener(GraveMarketPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Location death = p.getLocation();

        boolean pvp = p.getKiller() != null && plugin.getConfig().getBoolean("pvp.enabled", true);
        boolean keepInventory = e.getKeepInventory();
        List<ItemStack> dropsCopy = new ArrayList<>(e.getDrops());
        List<ItemStack> keepDrops = new ArrayList<>();
        List<ItemStack> toGrave = new ArrayList<>();
        if (keepInventory || dropsCopy.isEmpty()) {
            toGrave.addAll(collectInventoryItems(p));
            clearInventory(p.getInventory());
        } else {
            double dropChance = Math.max(0.0, Math.min(1.0, plugin.getConfig().getDouble("pvp.partialDropChance", 0.30)));
            for (ItemStack it : dropsCopy) {
                if (it == null || it.getType() == Material.AIR) continue;
                if (pvp && ThreadLocalRandom.current().nextDouble() < dropChance) {
                    keepDrops.add(it);
                } else {
                    toGrave.add(it);
                }
            }
        }

        e.getDrops().clear();
        e.getDrops().addAll(keepDrops);

        int exp = e.getDroppedExp();
        int expToGrave = e.getKeepLevel() ? 0 : exp;
        e.setDroppedExp(0);

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

        boolean insured = plugin.insurance().isInsured(p.getUniqueId());
        if (insured) {
            double extractMult = plugin.getConfig().getDouble("insurance.extractDiscountMultiplier", 0.7);
            r.extractCost = Math.max(0L, Math.round(r.extractCost * extractMult));
            long bonus = plugin.getConfig().getLong("insurance.lifetimeBonusSeconds", 600);
            if (bonus > 0) r.expiresAtEpochMs += bonus * 1000L;
            plugin.graves().save();
        }

        logDebug(() -> "death dropsCopy=" + dropsCopy.size()
                + " keepInventory=" + keepInventory
                + " keepLevel=" + e.getKeepLevel()
                + " keepDrops=" + keepDrops.size()
                + " toGrave=" + toGrave.size()
                + " containerItems=" + r.storedItems.size()
                + " overflowItems=" + (r.overflowItems == null ? 0 : r.overflowItems.size())
                + " graveLoc=" + (r.virtual ? LocationCodec.pretty(r.deathLoc()) : LocationCodec.pretty(r.graveLoc()))
                + " virtual=" + r.virtual
                + " insured=" + insured);
        if (dropsCopy.isEmpty()) {
            logDebug(() -> "death dropsCopy empty. reason keepInventory=" + keepInventory
                    + " keepRule=" + (p.getWorld() == null ? "unknown" : p.getWorld().getGameRuleValue(org.bukkit.GameRule.KEEP_INVENTORY)));
        }

        // Feedback
        long now = System.currentTimeMillis();
        long left = Math.max(0, (r.expiresAtEpochMs - now) / 1000L);
        long mm = left / 60;
        long ss = left % 60;
        p.sendMessage(Component.text("§6[Могила] §fВаши вещи сохранены. " +
                (r.virtual ? "§eВиртуальная могила§f (место небезопасно)." : "§aМогила установлена§f.") +
                " §7Осталось: §e" + mm + ":" + String.format(java.util.Locale.ROOT, "%02d", ss)));

        Location graveLoc = r.virtual ? r.deathLoc() : r.graveLoc();
        if (graveLoc != null) {
            p.sendMessage(Component.text("§6[Могила] §fКоординаты: §e" + LocationCodec.pretty(graveLoc)));
        }
        p.sendMessage(Component.text("§6[Могила] §fМесто смерти: §7" + LocationCodec.pretty(death)));

        // Quick actions (без внешних зависимостей)
        Component actions = Component.text("§6[Могила] §fДействия: ")
                .append(Component.text("§b[Метка]").clickEvent(ClickEvent.runCommand("/grave mark")))
                .append(Component.text(" §a[Инфо]").clickEvent(ClickEvent.runCommand("/grave info")))
                .append(Component.text(" §e[Компас]").clickEvent(ClickEvent.runCommand("/grave compass")))
                .append(Component.text(" §c[Телепорт]").clickEvent(ClickEvent.runCommand("/grave tp")));
        p.sendMessage(actions);

        // Small particles beacon at the place (optional)
        if (!r.virtual && plugin.getConfig().getBoolean("graves.spawnParticlesOnCreate", true) && safeBlock != null) {
            var w = safeBlock.getWorld();
            if (w != null) {
                w.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, safeBlock.clone().add(0.5, 1.0, 0.5), 14, 0.3, 0.6, 0.3, 0.02);
            }
        }
    }

    private List<ItemStack> collectInventoryItems(Player player) {
        PlayerInventory inv = player.getInventory();
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack it : inv.getStorageContents()) {
            if (it != null && it.getType() != Material.AIR) items.add(it);
        }
        for (ItemStack it : inv.getArmorContents()) {
            if (it != null && it.getType() != Material.AIR) items.add(it);
        }
        for (ItemStack it : inv.getExtraContents()) {
            if (it != null && it.getType() != Material.AIR) items.add(it);
        }
        ItemStack offhand = inv.getItemInOffHand();
        if (offhand != null && offhand.getType() != Material.AIR) items.add(offhand);
        return items;
    }

    private void clearInventory(PlayerInventory inv) {
        inv.clear();
        inv.setArmorContents(new ItemStack[4]);
        inv.setExtraContents(new ItemStack[1]);
        inv.setItemInOffHand(null);
    }

    private void logDebug(java.util.function.Supplier<String> message) {
        if (!plugin.getConfig().getBoolean("graves.debug", false)) return;
        plugin.getLogger().info("[GraveMarket] " + message.get());
    }
}
