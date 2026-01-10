package ru.atmstr.nightpact;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class NightPactCommand implements CommandExecutor, TabCompleter {

    private final NightPactPlugin plugin;

    public NightPactCommand(NightPactPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(NightPactPlugin.colorize(plugin.getConfig().getString("messages.prefix", "")) +
                    "§7Использование: /nightpact <status|reload|force|debug>");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> {
                World world = resolveWorld(sender, args, 1);
                if (world == null) {
                    sender.sendMessage(NightPactPlugin.colorize(plugin.getConfig().getString("messages.prefix", "")) +
                            "§cУкажите корректный мир: /nightpact status <world>");
                    return true;
                }
                sender.sendMessage(plugin.getSleepManager().buildStatusLine(world));
                return true;
            }
            case "reload" -> {
                plugin.reloadAll();
                sender.sendMessage(NightPactPlugin.colorize(plugin.getConfig().getString("messages.prefix", "")) +
                        NightPactPlugin.colorize(plugin.getConfig().getString("messages.reloaded", "&aКонфигурация перезагружена.")));
                return true;
            }
            case "force" -> {
                World world = resolveWorld(sender, args, 1);
                if (world == null) {
                    sender.sendMessage(NightPactPlugin.colorize(plugin.getConfig().getString("messages.prefix", "")) +
                            "§cУкажите корректный мир: /nightpact force <world>");
                    return true;
                }
                plugin.getSleepManager().forceSkip(sender, world);
                return true;
            }
            case "debug" -> {
                if (args.length < 2) {
                    sender.sendMessage(NightPactPlugin.colorize(plugin.getConfig().getString("messages.prefix", "")) +
                            "§7Использование: /nightpact debug <on|off>");
                    return true;
                }
                String mode = args[1].toLowerCase(Locale.ROOT);
                boolean enabled = mode.equals("on");
                if (!enabled && !mode.equals("off")) {
                    sender.sendMessage(NightPactPlugin.colorize(plugin.getConfig().getString("messages.prefix", "")) +
                            "§cИспользуйте on/off.");
                    return true;
                }
                plugin.getSleepManager().setDebugEnabled(enabled);
                sender.sendMessage(NightPactPlugin.colorize(plugin.getConfig().getString("messages.prefix", "")) +
                        (enabled ? "§aДебаг включён." : "§cДебаг выключен."));
                return true;
            }
            default -> {
                sender.sendMessage(NightPactPlugin.colorize(plugin.getConfig().getString("messages.prefix", "")) +
                        "§cНеизвестная команда.");
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("status", "reload", "force", "debug");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("status") || args[0].equalsIgnoreCase("force"))) {
            List<String> worlds = new ArrayList<>();
            for (World world : Bukkit.getWorlds()) {
                worlds.add(world.getName());
            }
            return worlds;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            return Arrays.asList("on", "off");
        }
        return Collections.emptyList();
    }

    private World resolveWorld(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            return Bukkit.getWorld(args[index]);
        }
        if (sender instanceof Player player) {
            return player.getWorld();
        }
        return null;
    }
}
