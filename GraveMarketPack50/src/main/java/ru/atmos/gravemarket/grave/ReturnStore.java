package ru.atmos.gravemarket.grave;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import ru.atmos.gravemarket.util.AuditLog;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public final class ReturnStore {

    private final JavaPlugin plugin;
    private final AuditLog audit;
    private final File file;
    private YamlConfiguration yaml = new YamlConfiguration();
    private final Map<UUID, List<ItemStack>> pending = new HashMap<>();

    public ReturnStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.audit = (plugin instanceof ru.atmos.gravemarket.GraveMarketPlugin g) ? g.audit() : null;
        this.file = new File(plugin.getDataFolder(), "returns.yml");
    }

    public void load() {
        pending.clear();
        if (file.exists()) {
            try {
                yaml.load(file);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load returns.yml: " + e.getMessage());
            }
        }
        ConfigurationSection sec = yaml.getConfigurationSection("returns");
        if (sec == null) return;

        for (String key : sec.getKeys(false)) {
            try {
                UUID owner = UUID.fromString(key);
                List<ItemStack> items = new ArrayList<>();
                List<?> raw = sec.getList(key);
                if (raw != null) {
                    for (Object o : raw) {
                        if (o instanceof ItemStack it && it.getType() != Material.AIR) {
                            items.add(it);
                        }
                    }
                }
                if (!items.isEmpty()) pending.put(owner, items);
            } catch (IllegalArgumentException ignored) {
                // skip invalid
            }
        }
    }

    public void save() {
        yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("returns");
        for (Map.Entry<UUID, List<ItemStack>> entry : pending.entrySet()) {
            root.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save returns.yml: " + e.getMessage());
        }
    }

    public void add(UUID owner, List<ItemStack> items) {
        if (owner == null || items == null || items.isEmpty()) return;
        List<ItemStack> list = pending.computeIfAbsent(owner, k -> new ArrayList<>());
        list.addAll(items);
        save();
    }

    public List<ItemStack> take(UUID owner) {
        if (owner == null) return Collections.emptyList();
        List<ItemStack> items = pending.remove(owner);
        if (items == null) return Collections.emptyList();
        save();
        return items;
    }

    public int deliver(Player player) {
        List<ItemStack> items = take(player.getUniqueId());
        if (items.isEmpty()) return 0;
        int count = 0;
        for (ItemStack it : items) {
            if (it == null || it.getType() == Material.AIR) continue;
            var leftover = player.getInventory().addItem(it);
            if (!leftover.isEmpty()) {
                for (ItemStack lf : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), lf);
                }
            }
            count += it.getAmount();
        }
        if (audit != null) audit.log("RETURN", null, player.getUniqueId(), "-", player.getLocation(), "items=" + count);
        return count;
    }
}
