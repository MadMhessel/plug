package ru.letopis.dungeon.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import ru.letopis.dungeon.LetopisDungeonPlugin;

public final class BookUtil {
    private BookUtil() {}

    public static ItemStack buildGuideBook(LetopisDungeonPlugin plugin) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) return book;
        meta.setTitle(plugin.msg().bookTitle());
        meta.setAuthor(plugin.msg().bookAuthor());
        meta.setPages(plugin.msg().bookPages());
        book.setItemMeta(meta);
        return book;
    }
}
