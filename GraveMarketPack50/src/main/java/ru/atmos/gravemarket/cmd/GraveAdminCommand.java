package ru.atmos.gravemarket.cmd;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import ru.atmos.gravemarket.GraveMarketPlugin;
import ru.atmos.gravemarket.grave.GraveRecord;
import ru.atmos.gravemarket.util.LocationCodec;

import java.util.*;
import java.util.stream.Collectors;

public final class GraveAdminCommand implements CommandExecutor, TabCompleter {

    private final GraveMarketPlugin plugin;

    public GraveAdminCommand(GraveMarketPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean check(CommandSender sender) {
        if (!sender.hasPermission("gravemarket.admin")) {
            sender.sendMessage("Нет прав (gravemarket.admin).");
            return false;
        }
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!check(sender)) return true;

        String sub = (args.length == 0) ? "help" : args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "help" -> help(sender);
            case "list" -> list(sender, args);
            case "purge" -> purge(sender);
            case "remove" -> remove(sender, args);
            case "altar" -> altar(sender, args);
            default -> sender.sendMessage("Неизвестно. /graveadmin help");
        }
        return true;
    }

    private void help(CommandSender s) {
        s.sendMessage("GraveMarket admin:");
        s.sendMessage(" /graveadmin list [ник]");
        s.sendMessage(" /graveadmin purge");
        s.sendMessage(" /graveadmin remove <ник>");
        s.sendMessage(" /graveadmin altar set");
    }

    private void list(CommandSender s, String[] args) {
        if (args.length >= 2) {
            var op = Bukkit.getOfflinePlayer(args[1]);
            GraveRecord g = plugin.graves().activeGrave(op.getUniqueId());
            if (g == null) { s.sendMessage("Могил нет."); return; }
            s.sendMessage(format(g));
            return;
        }

        List<GraveRecord> all = new ArrayList<>(plugin.graves().all());
        all.sort(Comparator.comparingLong(a -> -a.createdAtEpochMs));
        if (all.isEmpty()) {
            s.sendMessage("Могил нет.");
            return;
        }
        s.sendMessage("Всего могил: " + all.size());
        for (int i = 0; i < Math.min(20, all.size()); i++) {
            s.sendMessage(format(all.get(i)));
        }
        if (all.size() > 20) s.sendMessage("... ещё " + (all.size() - 20));
    }

    private String format(GraveRecord g) {
        String loc = g.virtual ? LocationCodec.pretty(g.deathLoc()) : LocationCodec.pretty(g.graveLoc());
        return "[" + g.shortId() + "] " + g.ownerName + " " + (g.virtual ? "(вирт)" : "(мир)") + " paid=" + g.paid + " loc=" + loc;
    }

    private void purge(CommandSender s) {
        plugin.graves().tick();
        s.sendMessage("Ок: tick/purge выполнен.");
    }

    private void remove(CommandSender s, String[] args) {
        if (args.length < 2) {
            s.sendMessage("Использование: /graveadmin remove <ник>");
            return;
        }
        var op = Bukkit.getOfflinePlayer(args[1]);
        GraveRecord g = plugin.graves().activeGrave(op.getUniqueId());
        if (g == null) { s.sendMessage("Могила не найдена."); return; }
        plugin.graves().removeGrave(g, "admin_remove");
        s.sendMessage("Удалено: " + g.ownerName + " " + g.shortId());
    }

    private void altar(CommandSender s, String[] args) {
        if (args.length < 2) {
            s.sendMessage("Использование: /graveadmin altar set");
            return;
        }
        if (!(s instanceof Player p)) {
            s.sendMessage("Только из игры.");
            return;
        }
        if (!args[1].equalsIgnoreCase("set")) {
            s.sendMessage("Использование: /graveadmin altar set");
            return;
        }
        plugin.getConfig().set("altar.location", LocationCodec.encode(p.getLocation()));
        plugin.saveConfig();
        s.sendMessage("Алтарь установлен: " + LocationCodec.pretty(p.getLocation()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("help","list","purge","remove","altar").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            String pref = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(pref))
                    .sorted()
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("altar")) {
            return List.of("set").stream().filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
