package ru.letopis.guide;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class GuideService {
    private String title = "";
    private String author = "";
    private List<String> pages = List.of();

    public void load(File dataFolder) {
        File file = new File(dataFolder, "guide.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        title = config.getString("title", "");
        author = config.getString("author", "");
        List<String> loadedPages = config.getStringList("pages");
        if (loadedPages != null && !loadedPages.isEmpty()) {
            List<String> prepared = new ArrayList<>();
            for (String page : loadedPages) {
                prepared.add(page.replace("\\n", "\n"));
            }
            pages = prepared;
        } else {
            pages = List.of();
        }
    }

    public void openGuide(Player player) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle(title);
        meta.setAuthor(author);
        meta.setPages(pages);
        book.setItemMeta(meta);
        player.openBook(book);
    }
}
