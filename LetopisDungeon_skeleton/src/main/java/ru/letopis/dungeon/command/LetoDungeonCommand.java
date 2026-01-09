package ru.letopis.dungeon.command;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import ru.letopis.dungeon.LetopisDungeonPlugin;
import ru.letopis.dungeon.model.StartMode;
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
            for (String line : plugin.msg().list("help")) sender.sendMessage(plugin.msg().prefix() + line);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "guide" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(plugin.msg().prefix() + plugin.msg().get("commands.onlyPlayers"));
                    return true;
                }
                p.getInventory().addItem(BookUtil.buildGuideBook(plugin));
                p.sendMessage(plugin.msg().prefix() + plugin.msg().get("commands.guideGiven"));
                return true;
            }
            case "start" -> {
                if (!sender.hasPermission("letodungeon.use")) {
                    sender.sendMessage(plugin.msg().prefix() + plugin.msg().get("commands.noPermission"));
                    return true;
                }
                StartMode mode = null;
                if (args.length > 1) {
                    if (args[1].equalsIgnoreCase("overworld")) mode = StartMode.OVERWORLD_ENTRY;
                    if (args[1].equalsIgnoreCase("dimension")) mode = StartMode.DIRECT_DIMENSION;
                }
                plugin.dungeon().startRun(sender, mode);
                return true;
            }
            case "join" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(plugin.msg().prefix() + plugin.msg().get("commands.onlyPlayers"));
                    return true;
                }
                plugin.dungeon().join(p);
                return true;
            }
            case "leave" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(plugin.msg().prefix() + plugin.msg().get("commands.onlyPlayers"));
                    return true;
                }
                plugin.dungeon().leave(p);
                p.sendMessage(plugin.msg().prefix() + plugin.msg().get("commands.left"));
                return true;
            }
            case "status" -> {
                for (String line : plugin.dungeon().statusLines()) sender.sendMessage(line);
                return true;
            }
            case "stop" -> {
                if (!sender.hasPermission("letodungeon.admin")) {
                    sender.sendMessage(plugin.msg().prefix() + plugin.msg().get("commands.noPermission"));
                    return true;
                }
                plugin.dungeon().stopRun(sender);
                return true;
            }
            case "debug" -> {
                if (!sender.hasPermission("letodungeon.debug")) {
                    sender.sendMessage(plugin.msg().prefix() + plugin.msg().get("commands.noPermission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(plugin.msg().prefix() + plugin.msg().get("debug.usage"));
                    return true;
                }
                String arg = args[1].toLowerCase();
                if (arg.equals("room") && args.length > 2) {
                    plugin.dungeon().debugSkipToRoom(sender, args[2]);
                    return true;
                }
                if (arg.equals("wave")) {
                    plugin.dungeon().debugSpawnWave(sender);
                    return true;
                }
                if (arg.equals("boss")) {
                    plugin.dungeon().debugSpawnBoss(sender);
                    return true;
                }
                sender.sendMessage(plugin.msg().prefix() + plugin.msg().get("debug.usage"));
                return true;
            }
            default -> {
                sender.sendMessage(plugin.msg().prefix() + plugin.msg().get("commands.unknown"));
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(Arrays.asList("help", "guide", "start", "join", "leave", "status", "stop", "debug"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) return filter(Arrays.asList("overworld", "dimension"), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) return filter(Arrays.asList("room", "wave", "boss"), args[1]);
        if (args.length == 3 && args[0].equalsIgnoreCase("debug") && args[1].equalsIgnoreCase("room")) {
            return filter(Arrays.asList("prep", "waves1", "puzzle", "waves2", "boss"), args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> items, String prefix) {
        String p = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String it : items) if (it.startsWith(p)) out.add(it);
        return out;
    }
}
