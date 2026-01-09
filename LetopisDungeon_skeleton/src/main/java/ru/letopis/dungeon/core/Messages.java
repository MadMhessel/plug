package ru.letopis.dungeon.core;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class Messages {

    private final JavaPlugin plugin;
    private YamlConfiguration yml;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "messages_ru.yml");
        if (!f.exists()) plugin.saveResource("messages_ru.yml", false);
        this.yml = YamlConfiguration.loadConfiguration(f);

        try (var in = plugin.getResource("messages_ru.yml")) {
            if (in != null) {
                var def = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                this.yml.setDefaults(def);
            }
        } catch (Exception ignored) {
        }
    }

    public String prefix() {
        return color(yml.getString("prefix", "§8[§aДанж§8]§r "));
    }

    public String get(String path) {
        return color(yml.getString(path, path));
    }

    public String get(String path, Map<String, String> replacements) {
        String value = get(path);
        for (var entry : replacements.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return value;
    }

    public List<String> list(String path) {
        List<String> raw = yml.getStringList(path);
        if (raw == null) return Collections.emptyList();
        return raw.stream().map(Messages::color).toList();
    }

    public String bookTitle() { return get("guideBook.title"); }
    public String bookAuthor() { return get("guideBook.author"); }
    public List<String> bookPages() { return list("guideBook.pages"); }

    private static String color(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
