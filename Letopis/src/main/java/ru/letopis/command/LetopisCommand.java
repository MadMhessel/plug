package ru.letopis.command;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import ru.letopis.LetopisManager;
import ru.letopis.model.JournalEntry;
import ru.letopis.model.PlayerMeta;
import ru.letopis.model.Scale;
import ru.letopis.model.WorldState;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Locale;

public final class LetopisCommand implements CommandExecutor, TabCompleter {
    private final LetopisManager manager;

    public LetopisCommand(LetopisManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("letopis.use")) {
            sender.sendMessage(msg("commands.no_permission"));
            return true;
        }
        if (args.length == 0) {
            return handleRoot(sender);
        }
        if (args[0].equalsIgnoreCase("journal")) {
            return handleJournal(sender, args);
        }
        if (args[0].equalsIgnoreCase("guide") || args[0].equalsIgnoreCase("help")) {
            return handleGuide(sender);
        }
        if (args[0].equalsIgnoreCase("contribute")) {
            return handleContribute(sender);
        }
        if (args[0].equalsIgnoreCase("effects")) {
            return handleEffects(sender);
        }
        if (args[0].equalsIgnoreCase("title")) {
            return handleTitle(sender, args);
        }
        if (args[0].equalsIgnoreCase("admin")) {
            return handleAdmin(sender, args);
        }
        return false;
    }

    private boolean handleRoot(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("commands.player_only"));
            return true;
        }
        WorldState state = manager.getWorldState(player.getWorld().getName());
        if (state == null) return true;
        sender.sendMessage(prefix() + "§eСостояние мира:");
        for (Scale scale : Scale.values()) {
            sender.sendMessage("§7" + scale.displayName() + ": §f" + String.format(Locale.US, "%.2f", state.get(scale)) + "/" + manager.config().thresholdMax);
        }
        TextComponent guide = new TextComponent("§e📖 Справка");
        guide.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/letopis guide"));
        player.spigot().sendMessage(guide);
        return true;
    }

    private boolean handleGuide(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("commands.player_only"));
            return true;
        }
        manager.openGuide(player);
        return true;
    }

    private boolean handleJournal(CommandSender sender, String[] args) {
        if (!sender.hasPermission("letopis.journal")) {
            sender.sendMessage(msg("commands.no_permission"));
            return true;
        }
        int page = 1;
        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        int limit = 10;
        int offset = (page - 1) * limit;
        List<JournalEntry> entries = manager.storage().loadJournal(limit, offset);
        sender.sendMessage(prefix() + "§eЖурнал (стр. " + page + "):");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault());
        for (JournalEntry entry : entries) {
            String line = "§7" + formatter.format(Instant.ofEpochSecond(entry.ts())) + " §f" + entry.world() + " §8» §e" + entry.type();
            if (entry.scale() != null) {
                line += " §7(" + entry.scale().displayName() + ")";
            }
            sender.sendMessage(line);
        }
        return true;
    }

    private boolean handleContribute(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("commands.player_only"));
            return true;
        }
        Map<Scale, Double> map = manager.storage().loadContribution(player.getUniqueId(), player.getWorld().getName());
        sender.sendMessage(prefix() + "§eВаш вклад за сутки:");
        for (Scale scale : Scale.values()) {
            sender.sendMessage("§7" + scale.displayName() + ": §f" + String.format("%.1f", map.getOrDefault(scale, 0.0)));
        }
        return true;
    }

    private boolean handleEffects(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("commands.player_only"));
            return true;
        }
        PlayerMeta meta = manager.storage().loadPlayerMeta(player.getUniqueId());
        boolean enabled = !meta.cosmeticsEnabled();
        manager.storage().savePlayerMeta(player.getUniqueId(), new PlayerMeta(meta.title(), enabled));
        sender.sendMessage(prefix() + "§eКосметика: " + (enabled ? "§aвключено" : "§cвыключено"));
        return true;
    }

    private boolean handleTitle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("commands.player_only"));
            return true;
        }
        List<String> titles = manager.storage().loadPlayerTitles(player.getUniqueId());
        PlayerMeta meta = manager.storage().loadPlayerMeta(player.getUniqueId());
        if (args.length == 1) {
            sender.sendMessage(prefix() + "§eВаши титулы:");
            for (String title : titles) {
                sender.sendMessage("§7- " + title);
            }
            sender.sendMessage("§7Текущий: " + (meta.title() == null ? "нет" : meta.title()));
            return true;
        }
        String choice = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        if (choice.equalsIgnoreCase("off")) {
            manager.storage().savePlayerMeta(player.getUniqueId(), new PlayerMeta(null, meta.cosmeticsEnabled()));
            sender.sendMessage(prefix() + "§eТитул отключён.");
            return true;
        }
        if (!titles.contains(choice)) {
            sender.sendMessage(prefix() + "§cТитул недоступен.");
            return true;
        }
        manager.storage().savePlayerMeta(player.getUniqueId(), new PlayerMeta(choice, meta.cosmeticsEnabled()));
        sender.sendMessage(prefix() + "§eТитул установлен: " + choice);
        return true;
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("letopis.admin")) {
            sender.sendMessage(msg("commands.no_permission"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(prefix() + "§e/admin status|set|add|start|stop|reload|export|givecodex");
            return true;
        }
        String sub = args[1];
        if (sub.equalsIgnoreCase("status")) {
            for (World world : Bukkit.getWorlds()) {
                WorldState state = manager.getWorldState(world.getName());
                if (state == null) continue;
                sender.sendMessage("§e" + world.getName() + " §7» Шум " + String.format(Locale.US, "%.2f", state.get(Scale.NOISE)) + " Пепел "
                    + String.format(Locale.US, "%.2f", state.get(Scale.ASH)) + " Чаща " + String.format(Locale.US, "%.2f", state.get(Scale.GROVE))
                    + " Разлом " + String.format(Locale.US, "%.2f", state.get(Scale.RIFT)));
            }
            return true;
        }
        if (sub.equalsIgnoreCase("set") && args.length >= 5) {
            World world = Bukkit.getWorld(args[2]);
            Scale scale = Scale.fromKey(args[3]);
            double value = Double.parseDouble(args[4]);
            if (world != null && scale != null) {
                manager.setScaleValue(world, scale, value);
                sender.sendMessage(prefix() + "§aГотово.");
            }
            return true;
        }
        if (sub.equalsIgnoreCase("add") && args.length >= 5) {
            World world = Bukkit.getWorld(args[2]);
            Scale scale = Scale.fromKey(args[3]);
            double delta = Double.parseDouble(args[4]);
            if (world != null && scale != null) {
                manager.addScaleValue(world, scale, delta);
                sender.sendMessage(prefix() + "§aГотово.");
            }
            return true;
        }
        if (sub.equalsIgnoreCase("start") && args.length >= 4) {
            World world = Bukkit.getWorld(args[2]);
            Scale scale = Scale.fromKey(args[3]);
            manager.forceStartEvent(world, scale);
            sender.sendMessage(prefix() + "§aСобытие запущено.");
            return true;
        }
        if (sub.equalsIgnoreCase("stop") && args.length >= 3) {
            World world = Bukkit.getWorld(args[2]);
            manager.stopEvent(world);
            sender.sendMessage(prefix() + "§aСобытие остановлено.");
            return true;
        }
        if (sub.equalsIgnoreCase("reload")) {
            manager.reloadPlugin();
            sender.sendMessage(prefix() + "§aПерезагружено.");
            return true;
        }
        if (sub.equalsIgnoreCase("export")) {
            String stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm").withZone(ZoneId.systemDefault())
                .format(Instant.now());
            File out = new File(manager.dataFolder(), "export/letopis_export_" + stamp + ".json");
            if (out.getParentFile() != null) {
                out.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(out)) {
                writer.write(manager.storage().exportJson());
            } catch (IOException e) {
                sender.sendMessage(prefix() + "§cОшибка экспорта: " + e.getMessage());
                return true;
            }
            sender.sendMessage(prefix() + "§aЭкспорт сохранён: " + out.getName());
            return true;
        }
        if (sub.equalsIgnoreCase("givecodex") && args.length >= 3) {
            Player target = Bukkit.getPlayer(args[2]);
            if (target != null) {
                target.getInventory().addItem(manager.itemFactory().createCodex());
                sender.sendMessage(prefix() + "§aКодекс выдан.");
            }
            return true;
        }
        return true;
    }

    private String prefix() {
        return manager.messages().getString("prefix", "");
    }

    private String msg(String key) {
        FileConfiguration messages = manager.messages();
        return messages.getString(key, "");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("journal", "guide", "help", "contribute", "effects", "title", "admin");
        }
        return Collections.emptyList();
    }
}
