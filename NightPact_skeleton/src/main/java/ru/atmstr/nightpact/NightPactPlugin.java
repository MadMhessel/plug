package ru.atmstr.nightpact;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class NightPactPlugin extends JavaPlugin {

    private SleepManager sleepManager;
    private EffectManager effectManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadAll();

        // listeners
        Bukkit.getPluginManager().registerEvents(new SleepListener(this), this);
        Bukkit.getPluginManager().registerEvents(new CombatListener(this), this);

        // command
        PluginCommand cmd = getCommand("nightpact");
        if (cmd != null) {
            NightPactCommand executor = new NightPactCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        } else {
            getLogger().warning("Команда /nightpact не зарегистрирована (plugin.yml).");
        }

        getLogger().info("NightPact включён.");
    }

    @Override
    public void onDisable() {
        if (sleepManager != null) {
            sleepManager.shutdown();
        }
        getLogger().info("NightPact выключен.");
    }

    public void reloadAll() {
        if (sleepManager != null) {
            sleepManager.shutdown();
        }
        reloadConfig();
        validateConfig();

        this.effectManager = new EffectManager(this);
        this.sleepManager = new SleepManager(this, effectManager);
    }

    public SleepManager getSleepManager() {
        return sleepManager;
    }

    public Set<World> getEnabledWorlds() {
        List<String> names = getConfig().getStringList("settings.enabled_worlds");
        if (names == null || names.isEmpty()) return new HashSet<>();

        return names.stream()
                .map(Bukkit::getWorld)
                .filter(w -> w != null)
                .collect(Collectors.toSet());
    }

    private void validateConfig() {
        FileConfiguration cfg = getConfig();

        double ratio = cfg.getDouble("settings.required_ratio", 0.40);
        if (ratio < 0 || ratio > 1) {
            double clamped = Math.max(0.0, Math.min(1.0, ratio));
            getLogger().warning("settings.required_ratio вне диапазона 0..1, исправлено на " + clamped);
            cfg.set("settings.required_ratio", clamped);
        }

        int prep = cfg.getInt("settings.preparation_seconds", 15);
        if (prep < 3) {
            getLogger().warning("settings.preparation_seconds < 3, исправлено на 3.");
            cfg.set("settings.preparation_seconds", 3);
        }

        int minSleepers = cfg.getInt("settings.min_sleepers", 1);
        if (minSleepers < 1) {
            getLogger().warning("settings.min_sleepers < 1, исправлено на 1.");
            cfg.set("settings.min_sleepers", 1);
        }

        ConfigurationSection weights = cfg.getConfigurationSection("effect_weights");
        if (weights != null) {
            for (String key : weights.getKeys(false)) {
                int val = weights.getInt(key, 0);
                if (val < 0) {
                    getLogger().warning("Отрицательный вес эффекта " + key + ", исправлено на 0.");
                    weights.set(key, 0);
                }
            }
        }

        validateLootList("effects.night_deal.loot");
        validateLootList("effects.night_deal.rare_loot");
        validateEntityTypeList("effects.bad_dream.mob_types");
        validateEntityTypeList("settings.anxious_sleep.hostile_entity_types");

        List<String> worlds = cfg.getStringList("settings.enabled_worlds");
        if (worlds != null) {
            for (String name : worlds) {
                if (Bukkit.getWorld(name) == null) {
                    getLogger().warning("Мир из enabled_worlds не найден: " + name);
                }
            }
        }
        saveConfig();
    }

    private void validateLootList(String path) {
        List<String> lines = getConfig().getStringList(path);
        if (lines == null) return;
        for (String line : lines) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split(":");
            String matStr = parts[0].trim().toUpperCase();
            try {
                org.bukkit.Material.valueOf(matStr);
            } catch (Exception e) {
                getLogger().warning("Некорректный материал в " + path + ": " + matStr);
            }
        }
    }

    private void validateEntityTypeList(String path) {
        List<String> list = getConfig().getStringList(path);
        if (list == null) return;
        for (String s : list) {
            if (s == null || s.isBlank()) continue;
            try {
                org.bukkit.entity.EntityType.valueOf(s.trim().toUpperCase());
            } catch (Exception e) {
                getLogger().warning("Некорректный тип сущности в " + path + ": " + s);
            }
        }
    }

    public static String colorize(String s) {
        if (s == null) return "";
        return s.replace("&", "§");
    }
}
