package ru.atmstr.nightpact;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class NightPactPlugin extends JavaPlugin {

    private SleepManager sleepManager;
    private EffectManager effectManager;

    private BossBar bossBar;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadAll();

        // listeners
        Bukkit.getPluginManager().registerEvents(new SleepListener(this, sleepManager), this);
        Bukkit.getPluginManager().registerEvents(new CombatListener(this, sleepManager), this);

        // command
        PluginCommand cmd = getCommand("nightpact");
        if (cmd != null) {
            NightPactCommand executor = new NightPactCommand(this, sleepManager);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        } else {
            getLogger().warning("Команда /nightpact не зарегистрирована (plugin.yml).");
        }

        getLogger().info("NightPact включён.");
    }

    @Override
    public void onDisable() {
        if (bossBar != null) {
            bossBar.removeAll();
        }
        getLogger().info("NightPact выключен.");
    }

    public void reloadAll() {
        reloadConfig();
        FileConfiguration cfg = getConfig();

        this.effectManager = new EffectManager(this);
        this.sleepManager = new SleepManager(this, effectManager);

        // bossbar
        if (bossBar != null) bossBar.removeAll();
        if (cfg.getBoolean("settings.show_progress_bar", true)) {
            bossBar = Bukkit.createBossBar(colorize(cfg.getString("messages.prefix", "&6[&eNightPact&6]&r ") + "&e..."),
                    BarColor.YELLOW, BarStyle.SOLID);
            bossBar.setVisible(false);
        } else {
            bossBar = null;
        }
    }

    public BossBar getBossBar() {
        return bossBar;
    }

    public Set<World> getEnabledWorlds() {
        List<String> names = getConfig().getStringList("settings.enabled_worlds");
        if (names == null || names.isEmpty()) return new HashSet<>();

        return names.stream()
                .map(Bukkit::getWorld)
                .filter(w -> w != null)
                .collect(Collectors.toSet());
    }

    public static String colorize(String s) {
        if (s == null) return "";
        return s.replace("&", "§");
    }
}
