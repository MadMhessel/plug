package ru.atmos.gravemarket.grave;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class InsuranceStore {

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration yaml = new YamlConfiguration();
    private final Map<UUID, Long> insuredUntil = new HashMap<>();

    public InsuranceStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "insurance.yml");
    }

    public void load() {
        insuredUntil.clear();
        if (file.exists()) {
            try {
                yaml.load(file);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load insurance.yml: " + e.getMessage());
            }
        }
        ConfigurationSection sec = yaml.getConfigurationSection("insurance");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                UUID owner = UUID.fromString(key);
                long until = sec.getLong(key, 0L);
                if (until > System.currentTimeMillis()) {
                    insuredUntil.put(owner, until);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        yaml = new YamlConfiguration();
        ConfigurationSection sec = yaml.createSection("insurance");
        for (Map.Entry<UUID, Long> entry : insuredUntil.entrySet()) {
            sec.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save insurance.yml: " + e.getMessage());
        }
    }

    public boolean isInsured(UUID owner) {
        if (owner == null) return false;
        Long until = insuredUntil.get(owner);
        if (until == null) return false;
        if (until <= System.currentTimeMillis()) {
            insuredUntil.remove(owner);
            return false;
        }
        return true;
    }

    public long insuredUntil(UUID owner) {
        if (owner == null) return 0L;
        return insuredUntil.getOrDefault(owner, 0L);
    }

    public void insure(UUID owner, long durationSeconds) {
        if (owner == null || durationSeconds <= 0) return;
        long now = System.currentTimeMillis();
        long until = now + durationSeconds * 1000L;
        insuredUntil.put(owner, until);
        save();
    }
}
