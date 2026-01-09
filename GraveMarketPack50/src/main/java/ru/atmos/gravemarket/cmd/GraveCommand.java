package ru.atmos.gravemarket.cmd;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import ru.atmos.gravemarket.GraveMarketPlugin;
import ru.atmos.gravemarket.grave.GraveRecord;
import ru.atmos.gravemarket.util.LocationCodec;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class GraveCommand implements CommandExecutor, TabCompleter {

    private final GraveMarketPlugin plugin;
    private final Map<UUID, Long> recallCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> beaconTasks = new ConcurrentHashMap<>();
    private final NamespacedKey compassKey;

    public GraveCommand(GraveMarketPlugin plugin) {
        this.plugin = plugin;
        this.compassKey = new NamespacedKey(plugin, "grave_compass");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Только для игроков.");
            return true;
        }

        String sub = (args.length == 0) ? "help" : args[0].toLowerCase(Locale.ROOT);

        return switch (sub) {
            case "help" -> { help(p); yield true; }
            case "info" -> { info(p); yield true; }
            case "pay" -> { pay(p); yield true; }
            case "trust" -> { trust(p, args); yield true; }
            case "untrust" -> { untrust(p, args); yield true; }
            case "trustlist" -> { trustList(p); yield true; }
            case "compass" -> { compass(p); yield true; }
            case "beacon" -> { beacon(p); yield true; }
            case "recall" -> { recall(p); yield true; }
            case "tp" -> { tp(p); yield true; }
            default -> { p.sendMessage(Component.text("§6[Могила] §cНеизвестная команда. /grave help")); yield true; }
        };
    }

    private void help(Player p) {
        p.sendMessage(Component.text("§6[Могила] §fКоманды:"));
        p.sendMessage(Component.text(" §e/grave info §7— информация по могиле"));
        p.sendMessage(Component.text(" §e/grave pay §7— оплатить извлечение"));
        p.sendMessage(Component.text(" §e/grave compass §7— компас к могиле (платно)"));
        p.sendMessage(Component.text(" §e/grave beacon §7— включить луч/частицы над могилой (платно)"));
        p.sendMessage(Component.text(" §e/grave recall §7— вернуть вещи к себе (очень дорого)"));
        p.sendMessage(Component.text(" §e/grave tp §7— телепорт к могиле (очень дорого)"));
        p.sendMessage(Component.text(" §e/grave trust <ник> §7— доверить доступ"));
        p.sendMessage(Component.text(" §e/grave untrust <ник> §7— убрать доверие"));
        p.sendMessage(Component.text(" §e/grave trustlist §7— список доверенных"));
    }

    private GraveRecord active(Player p) {
        GraveRecord g = plugin.graves().activeGrave(p.getUniqueId());
        if (g == null) {
            p.sendMessage(Component.text("§6[Могила] §7Активной могилы нет."));
            return null;
        }
        return g;
    }

    private void info(Player p) {
        GraveRecord g = active(p);
        if (g == null) return;

        long now = System.currentTimeMillis();
        long left = Math.max(0, (g.expiresAtEpochMs - now) / 1000L);
        long mm = left / 60;
        long ss = left % 60;

        Location gl = g.virtual ? g.deathLoc() : g.graveLoc();
        p.sendMessage(Component.text("§6[Могила] §fСтатус: " + (g.virtual ? "§eвиртуальная" : "§aв мире")));
        if (gl != null) p.sendMessage(Component.text("§6[Могила] §fКоординаты: §e" + LocationCodec.pretty(gl)));
        p.sendMessage(Component.text("§6[Могила] §fОсталось: §e" + mm + ":" + String.format(Locale.ROOT, "%02d", ss)));
        p.sendMessage(Component.text("§6[Могила] §fОплачено: " + (g.paid ? "§aда" : "§cнет") + " §7(извлечение: §6" + g.extractCost + " " + plugin.economy().currencyName() + "§7)"));

        if (!g.paid) {
            p.sendMessage(Component.text("§6[Могила] ").append(Component.text("§a[Оплатить]").clickEvent(ClickEvent.runCommand("/grave pay"))));
        }
    }

    private void pay(Player p) {
        GraveRecord g = active(p);
        if (g == null) return;
        if (!p.getUniqueId().equals(g.owner)) {
            p.sendMessage(Component.text("§6[Могила] §cОплатить может только владелец могилы."));
            return;
        }
        if (g.paid) {
            p.sendMessage(Component.text("§6[Могила] §aУже оплачено."));
            return;
        }

        long cost = g.extractCost;
        if (!plugin.economy().withdraw(p.getUniqueId(), cost)) {
            p.sendMessage(Component.text("§6[Могила] §cНедостаточно средств. Баланс: §f" + plugin.economy().balance(p.getUniqueId())));
            return;
        }
        g.paid = true;

        if (g.storedExp > 0) {
            p.giveExp(g.storedExp);
            g.storedExp = 0;
        }

        plugin.graves().save();
        p.sendMessage(Component.text("§6[Могила] §aОплачено: §6" + cost + " " + plugin.economy().currencyName() + "§a. Можно забирать вещи."));
    }

    private void trust(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage(Component.text("§6[Могила] §7Использование: /grave trust <ник>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            p.sendMessage(Component.text("§6[Могила] §cИгрок не найден онлайн: " + args[1]));
            return;
        }
        if (target.getUniqueId().equals(p.getUniqueId())) {
            p.sendMessage(Component.text("§6[Могила] §7Себе доверять не надо :)"));
            return;
        }
        plugin.graves().trust().add(p.getUniqueId(), target.getUniqueId());
        p.sendMessage(Component.text("§6[Могила] §aТеперь доверенный: §f" + target.getName()));
    }

    private void untrust(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage(Component.text("§6[Могила] §7Использование: /grave untrust <ник>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            p.sendMessage(Component.text("§6[Могила] §cИгрок не найден онлайн: " + args[1]));
            return;
        }
        plugin.graves().trust().remove(p.getUniqueId(), target.getUniqueId());
        p.sendMessage(Component.text("§6[Могила] §aДоверие убрано: §f" + target.getName()));
    }

    private void trustList(Player p) {
        Set<UUID> set = plugin.graves().trust().trusted(p.getUniqueId());
        if (set.isEmpty()) {
            p.sendMessage(Component.text("§6[Могила] §7Доверенных игроков нет."));
            return;
        }
        List<String> names = set.stream()
                .map(Bukkit::getOfflinePlayer)
                .map(op -> op.getName() == null ? op.getUniqueId().toString().substring(0, 8) : op.getName())
                .sorted()
                .collect(Collectors.toList());
        p.sendMessage(Component.text("§6[Могила] §fДоверенные: §e" + String.join(", ", names)));
    }

    private void compass(Player p) {
        GraveRecord g = active(p);
        if (g == null) return;
        if (!p.getUniqueId().equals(g.owner)) {
            p.sendMessage(Component.text("§6[Могила] §cКомпас может купить только владелец."));
            return;
        }

        long price = plugin.getConfig().getLong("economy.prices.compass", 960);
        if (!plugin.economy().withdraw(p.getUniqueId(), price)) {
            p.sendMessage(Component.text("§6[Могила] §cНедостаточно средств. Нужно: " + price));
            return;
        }

        Location target = g.virtual ? g.deathLoc() : g.graveLoc();
        if (target == null) {
            p.sendMessage(Component.text("§6[Могила] §cНет координат цели."));
            return;
        }

        ItemStack compass = new ItemStack(Material.COMPASS, 1);
        CompassMeta meta = (CompassMeta) compass.getItemMeta();
        meta.displayName(Component.text("§eКомпас могилы"));
        meta.lore(List.of(
                Component.text("§7Указывает на вашу последнюю могилу."),
                Component.text("§7" + LocationCodec.pretty(target)),
                Component.text("§8ID: " + g.shortId())
        ));
        meta.setLodestone(target);
        meta.setLodestoneTracked(false);
        meta.getPersistentDataContainer().set(compassKey, PersistentDataType.STRING, g.id);
        compass.setItemMeta(meta);

        giveOrDrop(p, compass);
        p.sendMessage(Component.text("§6[Могила] §aКомпас выдан. Цена: §6" + price + " " + plugin.economy().currencyName()));
    }

    private void beacon(Player p) {
        GraveRecord g = active(p);
        if (g == null) return;
        if (g.virtual) {
            p.sendMessage(Component.text("§6[Могила] §cДля виртуальной могилы луч недоступен."));
            return;
        }
        Location loc = g.graveLoc();
        if (loc == null || loc.getWorld() == null) {
            p.sendMessage(Component.text("§6[Могила] §cЛокация могилы не найдена."));
            return;
        }

        long price = plugin.getConfig().getLong("economy.prices.beacon", 2500);
        if (!plugin.economy().withdraw(p.getUniqueId(), price)) {
            p.sendMessage(Component.text("§6[Могила] §cНедостаточно средств. Нужно: " + price));
            return;
        }

        // cancel previous
        Integer old = beaconTasks.remove(p.getUniqueId());
        if (old != null) Bukkit.getScheduler().cancelTask(old);

        int duration = plugin.getConfig().getInt("beacon.durationSeconds", 120);
        int task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int left = duration;
            @Override public void run() {
                if (left-- <= 0) {
                    Integer id = beaconTasks.remove(p.getUniqueId());
                    if (id != null) Bukkit.getScheduler().cancelTask(id);
                    return;
                }
                if (loc.getWorld() == null) return;
                // vertical subtle particles
                for (int y = 0; y < 10; y++) {
                    loc.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, loc.clone().add(0.5, 1.0 + y * 0.8, 0.5), 1, 0, 0, 0, 0);
                }
            }
        }, 0L, 20L).getTaskId();

        beaconTasks.put(p.getUniqueId(), task);
        p.sendMessage(Component.text("§6[Могила] §aЛуч включён на " + duration + "с. Цена: §6" + price + " " + plugin.economy().currencyName()));
    }

    private void recall(Player p) {
        GraveRecord g = active(p);
        if (g == null) return;
        if (!p.getUniqueId().equals(g.owner)) {
            p.sendMessage(Component.text("§6[Могила] §cВозврат доступен только владельцу."));
            return;
        }

        int cd = plugin.getConfig().getInt("economy.recallCooldownSeconds", 1800);
        long now = System.currentTimeMillis();
        long until = recallCooldown.getOrDefault(p.getUniqueId(), 0L);
        if (until > now) {
            long left = (until - now) / 1000L;
            p.sendMessage(Component.text("§6[Могила] §cКулдаун: " + left + "с."));
            return;
        }

        long price = plugin.getConfig().getLong("economy.prices.recall", 7500);
        double altarMult = altarMultiplier(p.getLocation());
        price = Math.max(0, Math.round(price * altarMult));

        if (!plugin.economy().withdraw(p.getUniqueId(), price)) {
            p.sendMessage(Component.text("§6[Могила] §cНедостаточно средств. Нужно: " + price));
            return;
        }

        // sync items
        plugin.graves().syncFromContainer(g);

        // give exp
        if (g.storedExp > 0) {
            p.giveExp(g.storedExp);
            g.storedExp = 0;
        }

        // give items
        int count = 0;
        for (ItemStack it : new ArrayList<>(g.storedItems)) {
            if (it == null || it.getType() == Material.AIR) continue;
            giveOrDrop(p, it);
            count += it.getAmount();
        }

        // cleanup
        plugin.graves().removeGrave(g, "recall");
        recallCooldown.put(p.getUniqueId(), now + cd * 1000L);

        p.sendMessage(Component.text("§6[Могила] §aВозврат выполнен. Предметов: " + count + ". Цена: §6" + price + " " + plugin.economy().currencyName()));
    }

    private void tp(Player p) {
        GraveRecord g = active(p);
        if (g == null) return;
        if (!p.getUniqueId().equals(g.owner)) {
            p.sendMessage(Component.text("§6[Могила] §cТелепорт доступен только владельцу."));
            return;
        }
        if (g.virtual) {
            p.sendMessage(Component.text("§6[Могила] §cТелепорт к виртуальной могиле невозможен."));
            return;
        }
        if (!g.paid) {
            p.sendMessage(Component.text("§6[Могила] §eСначала оплатите извлечение: /grave pay"));
            return;
        }

        long price = plugin.getConfig().getLong("economy.prices.tp", 9000);
        if (!plugin.economy().withdraw(p.getUniqueId(), price)) {
            p.sendMessage(Component.text("§6[Могила] §cНедостаточно средств. Нужно: " + price));
            return;
        }

        Location loc = g.graveLoc();
        if (loc == null || loc.getWorld() == null) {
            p.sendMessage(Component.text("§6[Могила] §cЛокация могилы не найдена."));
            return;
        }

        // teleport slightly above grave
        Location dest = loc.clone().add(0.5, 1.0, 0.5);
        p.teleport(dest);
        p.sendMessage(Component.text("§6[Могила] §aТелепорт выполнен. Цена: §6" + price + " " + plugin.economy().currencyName()));
    }

    private double altarMultiplier(Location playerLoc) {
        if (!plugin.getConfig().getBoolean("altar.enabled", false)) return 1.0;
        String raw = plugin.getConfig().getString("altar.location", "");
        Location altar = ru.atmos.gravemarket.util.LocationCodec.decode(raw);
        if (altar == null || altar.getWorld() == null) return 1.0;
        if (playerLoc.getWorld() == null || !playerLoc.getWorld().equals(altar.getWorld())) return 1.0;
        double dist = playerLoc.distance(altar);
        if (dist <= plugin.getConfig().getDouble("altar.discountRadius", 6.0)) {
            return plugin.getConfig().getDouble("altar.recallPriceMultiplier", 0.7);
        }
        return 1.0;
    }

    private void giveOrDrop(Player p, ItemStack item) {
        var leftover = p.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            for (ItemStack lf : leftover.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), lf);
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("help","info","pay","compass","beacon","recall","tp","trust","untrust","trustlist").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust"))) {
            String pref = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(pref))
                    .sorted()
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
