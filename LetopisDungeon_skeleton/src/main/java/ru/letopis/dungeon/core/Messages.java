package ru.letopis.dungeon.core;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

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
        } catch (Exception ignored) {}
    }

    public String prefix() { return yml.getString("prefix", "§8[§aДанж§8]§r "); }
    public List<String> helpLines() { return yml.getStringList("help"); }
    public String bookTitle() { return yml.getString("guideBook.title", "Путеводитель данжа"); }
    public String bookAuthor() { return yml.getString("guideBook.author", "Летопись"); }
    public List<String> bookPages() { return yml.getStringList("guideBook.pages"); }
}
