package ru.letopis;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import ru.letopis.command.LetopisCommand;
import ru.letopis.config.LetopisConfig;
import ru.letopis.guide.GuideService;
import ru.letopis.model.PlayerMeta;
import ru.letopis.ritual.RitualManager;
import ru.letopis.storage.SqliteStorage;
import ru.letopis.storage.StorageService;

import java.io.File;

public final class LetopisPlugin extends JavaPlugin {

    private SqliteStorage storage;
    private StorageService storageService;
    private LetopisManager manager;
    private RitualManager ritualManager;
    private LetopisConfig configValues;
    private GuideService guideService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);
        saveResource("guide.yml", false);

        reloadConfig();
        this.configValues = new LetopisConfig();
        configValues.load(getConfig());

        this.storage = new SqliteStorage(this);
        storage.init();
        this.storageService = new StorageService(storage);

        this.guideService = new GuideService();
        guideService.load(getDataFolder());
        this.manager = new LetopisManager(this, storageService, configValues, getConfigMessages(), guideService);
        manager.start();
        this.ritualManager = new RitualManager(manager, configValues, getConfigMessages());

        LetopisCommand command = new LetopisCommand(manager);
        getCommand("letopis").setExecutor(command);
        getCommand("letopis").setTabCompleter(command);

        Bukkit.getPluginManager().registerEvents(manager, this);
        Bukkit.getPluginManager().registerEvents(ritualManager, this);

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    PlayerMeta meta = storageService.loadPlayerMeta(player.getUniqueId());
                    if (!meta.cosmeticsEnabled()) continue;
                    player.spawnParticle(Particle.ENCHANTMENT_TABLE, player.getLocation().add(0, 1.2, 0), 2, 0.3, 0.4, 0.3, 0);
                }
            }
        }.runTaskTimer(this, 100L, 100L);

        getLogger().info("Letopis включён. Версия: " + getDescription().getVersion());
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.stop();
        if (storageService != null) storageService.shutdown();
        if (storage != null) storage.close();
        getLogger().info("Letopis выключен.");
    }

    public void reloadLetopis() {
        reloadConfig();
        configValues.load(getConfig());
        manager.reloadMessages(getConfigMessages());
        ritualManager.reloadMessages(getConfigMessages());
        manager.reloadGuide();
    }

    private org.bukkit.configuration.file.FileConfiguration getConfigMessages() {
        return org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
    }
}
