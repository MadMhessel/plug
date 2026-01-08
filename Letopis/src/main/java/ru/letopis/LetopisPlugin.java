package ru.letopis;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import ru.letopis.storage.SqliteStorage;

public final class LetopisPlugin extends JavaPlugin {

    private SqliteStorage storage;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);

        this.storage = new SqliteStorage(this);
        storage.init();

        getLogger().info("Letopis включён. Версия: " + getDescription().getVersion());
        getLogger().info("Это стартовая заготовка репозитория. Логика шкал/событий добавляется поверх.");
    }

    @Override
    public void onDisable() {
        if (storage != null) storage.close();
        getLogger().info("Letopis выключен.");
    }

    public SqliteStorage storage() {
        return storage;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("letopis")) return false;

        if (!sender.hasPermission("letopis.use")) {
            sender.sendMessage("§cНет прав.");
            return true;
        }

        sender.sendMessage("§6[Летопись]§r Заготовка проекта установлена.");
        sender.sendMessage("§7Дальше сюда подключается ядро: шкалы, предвестники, события, ритуалы, награды.");
        return true;
    }
}
