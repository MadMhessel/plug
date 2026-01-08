package ru.letopis.dungeon.command;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import ru.letopis.dungeon.LetopisDungeonPlugin;
import ru.letopis.dungeon.util.BookUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class LetoDungeonCommand implements CommandExecutor, TabCompleter {

    private final LetopisDungeonPlugin plugin;

    public LetoDungeonCommand(LetopisDungeonPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            for (String line : plugin.msg().helpLines()) sender.sendMessage(plugin.msg().prefix() + line);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "join" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("Only players."); return true; }
                plugin.dungeon().join(p);
                return true;
            }
            case "leave" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("Only players."); return true; }
                plugin.dungeon().leave(p);
                return true;
            }
            case "status" -> {
                var s = plugin.dungeon().session();
                sender.sendMessage(plugin.msg().prefix() + "Состояние: " + s.state()
                        + " | Напряжение: " + s.director().tension() + "/1000"
                        + " | Участники: " + s.participantCount());
                return true;
            }
            case "guide" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("Only players."); return true; }
                p.getInventory().addItem(BookUtil.buildGuideBook(plugin));
                p.sendMessage(plugin.msg().prefix() + "Путеводитель выдан.");
                return true;
            }
            case "admin" -> {
                if (!sender.hasPermission("letodungeon.admin")) { sender.sendMessage(plugin.msg().prefix() + "Нет прав."); return true; }
                if (args.length < 2) { sender.sendMessage(plugin.msg().prefix() + "Использование: /letodungeon admin <start|stop|reload|set>"); return true; }
                String a = args[1].toLowerCase();
                switch (a) {
                    case "start" -> plugin.dungeon().startRun(sender);
                    case "stop" -> plugin.dungeon().stopRun(sender);
                    case "reload" -> { plugin.reloadAll(); sender.sendMessage(plugin.msg().prefix() + "Перезагружено."); }
                    case "set" -> {
                        if (args.length < 3) { sender.sendMessage(plugin.msg().prefix() + "Использование: /letodungeon admin set <tension>"); return true; }
                        try {
                            int v = Integer.parseInt(args[2]);
                            plugin.dungeon().setTension(v);
                            sender.sendMessage(plugin.msg().prefix() + "Напряжение: " + v);
                        } catch (NumberFormatException e) {
                            sender.sendMessage(plugin.msg().prefix() + "Нужно число.");
                        }
                    }
                    default -> sender.sendMessage(plugin.msg().prefix() + "Неизвестно: " + a);
                }
                return true;
            }
            default -> {
                sender.sendMessage(plugin.msg().prefix() + "Неизвестная команда. /letodungeon help");
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(Arrays.asList("help","join","leave","status","guide","admin"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) return filter(Arrays.asList("start","stop","reload","set"), args[1]);
        return List.of();
    }

    private List<String> filter(List<String> items, String prefix) {
        String p = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String it : items) if (it.startsWith(p)) out.add(it);
        return out;
    }
}
