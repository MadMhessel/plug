package ru.letopis.dungeon;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import ru.letopis.dungeon.command.LetoDungeonCommand;
import ru.letopis.dungeon.core.DungeonManager;
import ru.letopis.dungeon.core.Messages;
import ru.letopis.dungeon.listener.ProtectionListener;
import ru.letopis.dungeon.listener.SessionListener;

public final class LetopisDungeonPlugin extends JavaPlugin {

    private DungeonManager dungeonManager;
    private Messages messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.messages = new Messages(this);
        this.dungeonManager = new DungeonManager(this);

        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new SessionListener(this), this);

        PluginCommand cmd = getCommand("letodungeon");
        if (cmd != null) {
            var handler = new LetoDungeonCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        } else {
            getLogger().severe("Command letodungeon is not defined in plugin.yml");
        }

        dungeonManager.ensureDungeonWorld();
        getLogger().info("LetopisDungeon enabled.");
    }

    @Override
    public void onDisable() {
        if (dungeonManager != null) dungeonManager.shutdown();
        getLogger().info("LetopisDungeon disabled.");
    }

    public DungeonManager dungeon() { return dungeonManager; }
    public Messages msg() { return messages; }

    public void reloadAll() {
        reloadConfig();
        this.messages.reload();
        dungeonManager.reload();
    }
}
