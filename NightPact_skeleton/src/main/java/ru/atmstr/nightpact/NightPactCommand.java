package ru.atmstr.nightpact;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class NightPactCommand implements CommandExecutor, TabCompleter {

    private final NightPactPlugin plugin;
    private final SleepManager sleepManager;

    public NightPactCommand(NightPactPlugin plugin, SleepManager sleepManager) {
        this.plugin = plugin;
        this.sleepManager = sleepManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(NightPactPlugin.colorize(plugin.getConfig().getString("messages.prefix", "")) +
                    "§7Использование: /nightpact <status|reload|force>");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> {
                sender.sendMessage(sleepManager.buildStatusLine(sender));
                return true;
            }
            case "reload" -> {
                plugin.reloadAll();
                sender.sendMessage(NightPactPlugin.colorize(plugin.getConfig().getString("messages.prefix", "")) +
                        NightPactPlugin.colorize(plugin.getConfig().getString("messages.reloaded", "&aКонфигурация перезагружена.")));
                return true;
            }
            case "force" -> {
                sleepManager.forceSkip(sender);
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
            return Arrays.asList("status", "reload", "force");
        }
        return Collections.emptyList();
    }
}
