package ru.atmos.gravemarket.grave;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public final class TrustManager {
    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration yaml = new YamlConfiguration();

    public TrustManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "trust.yml");
    }

    public void load() {
        if (!file.exists()) return;
        try {
            yaml.load(file);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load trust.yml: " + e.getMessage());
        }
    }

    public void save() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save trust.yml: " + e.getMessage());
        }
    }

    public Set<UUID> trusted(UUID owner) {
        List<String> raw = yaml.getStringList("trust." + owner);
        Set<UUID> out = new HashSet<>();
        for (String s : raw) {
            try { out.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
        }
        return out;
    }

    public boolean isTrusted(UUID owner, UUID other) {
        return trusted(owner).contains(other);
    }

    public void add(UUID owner, UUID other) {
        Set<UUID> set = trusted(owner);
        set.add(other);
        yaml.set("trust." + owner, set.stream().map(UUID::toString).sorted().collect(Collectors.toList()));
        save();
    }

    public void remove(UUID owner, UUID other) {
        Set<UUID> set = trusted(owner);
        set.remove(other);
        yaml.set("trust." + owner, set.stream().map(UUID::toString).sorted().collect(Collectors.toList()));
        save();
    }
}
