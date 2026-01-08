package ru.letopis.util;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.letopis.model.Scale;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class ItemFactory {
    private final NamespacedKey sealKey;
    private final NamespacedKey trophyKey;

    public ItemFactory(NamespacedKey sealKey, NamespacedKey trophyKey) {
        this.sealKey = sealKey;
        this.trophyKey = trophyKey;
    }

    public ItemStack createSeal(Scale scale) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        String date = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        meta.setDisplayName(scale.chatColor() + "Печать " + scale.displayName());
        meta.setLore(List.of(ChatColor.GRAY + "Печать Летописи", ChatColor.DARK_GRAY + "Сезон: " + date));
        meta.getPersistentDataContainer().set(sealKey, PersistentDataType.STRING, scale.key());
        stack.setItemMeta(meta);
        return stack;
    }

    public ItemStack createTrophy(Scale scale) {
        ItemStack stack = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(scale.chatColor() + "Осколок Летописи");
        meta.setLore(List.of(ChatColor.GRAY + "Сувенир события: " + scale.displayName()));
        meta.getPersistentDataContainer().set(trophyKey, PersistentDataType.STRING, scale.key());
        stack.setItemMeta(meta);
        return stack;
    }

    public ItemStack createCodex() {
        ItemStack stack = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Кодекс Летописи");
        meta.setLore(List.of(ChatColor.GRAY + "Книга для алтаря Летописи"));
        stack.setItemMeta(meta);
        return stack;
    }
}
