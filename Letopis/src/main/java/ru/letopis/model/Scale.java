package ru.letopis.model;

import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;

public enum Scale {
    NOISE("noise", "Шум", BarColor.RED, ChatColor.RED),
    ASH("ash", "Пепел", BarColor.WHITE, ChatColor.GRAY),
    GROVE("grove", "Чаща", BarColor.GREEN, ChatColor.GREEN),
    RIFT("rift", "Разлом", BarColor.PURPLE, ChatColor.DARK_PURPLE);

    private final String key;
    private final String displayName;
    private final BarColor barColor;
    private final ChatColor chatColor;

    Scale(String key, String displayName, BarColor barColor, ChatColor chatColor) {
        this.key = key;
        this.displayName = displayName;
        this.barColor = barColor;
        this.chatColor = chatColor;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public BarColor barColor() {
        return barColor;
    }

    public ChatColor chatColor() {
        return chatColor;
    }

    public static Scale fromKey(String key) {
        for (Scale scale : values()) {
            if (scale.key.equalsIgnoreCase(key)) {
                return scale;
            }
        }
        return null;
    }
}
