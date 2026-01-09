package ru.atmos.gravemarket.econ;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public final class InternalEconomy implements Economy {
    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration yaml;

    public InternalEconomy(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "gravemarket-economy.yml");
        this.yaml = new YamlConfiguration();
        reload();
    }

    private void reload() {
        if (!file.exists()) return;
        try {
            yaml.load(file);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load gravemarket-economy.yml: " + e.getMessage());
        }
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save gravemarket-economy.yml: " + e.getMessage());
        }
    }

    @Override
    public long balance(UUID playerId) {
        return yaml.getLong("balances." + playerId, 0L);
    }

    @Override
    public boolean withdraw(UUID playerId, long amount) {
        if (amount <= 0) return true;
        long b = balance(playerId);
        if (b < amount) return false;
        yaml.set("balances." + playerId, b - amount);
        save();
        return true;
    }

    @Override
    public void deposit(UUID playerId, long amount) {
        if (amount <= 0) return;
        long b = balance(playerId);
        yaml.set("balances." + playerId, b + amount);
        save();
    }

    @Override
    public String currencyName() {
        return "кредиты";
    }
}
