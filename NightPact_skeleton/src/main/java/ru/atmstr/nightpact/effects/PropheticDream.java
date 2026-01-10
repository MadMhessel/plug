package ru.atmstr.nightpact.effects;

import org.bukkit.Location;
import org.bukkit.StructureType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import ru.atmstr.nightpact.NightPactPlugin;
import ru.atmstr.nightpact.PactContext;
import ru.atmstr.nightpact.PactEffect;

public class PropheticDream implements PactEffect {

    @Override
    public String getId() {
        return "prophetic_dream";
    }

    @Override
    public Category getCategory() {
        return Category.NEUTRAL;
    }

    @Override
    public void apply(NightPactPlugin plugin, PactContext ctx) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("effects.prophetic_dream");
        if (sec == null) return;
        if (!sec.getBoolean("enabled", true)) return;

        String st = sec.getString("structure_type", "VILLAGE");
        int radius = Math.max(200, sec.getInt("search_radius_blocks", 2000));
        boolean unexplored = sec.getBoolean("find_unexplored", false);

        StructureType type;
        try {
            type = StructureType.valueOf(st.trim().toUpperCase());
        } catch (Exception e) {
            type = StructureType.VILLAGE;
        }

        for (Player p : ctx.sleepers) {
            Location origin = p.getLocation();
            Location found = null;
            try {
                found = ctx.world.locateNearestStructure(origin, type, radius, unexplored);
            } catch (Throwable ignored) {
                // если API отличается — не ломаем сервер, просто даём атмосферное сообщение
            }

            if (found != null) {
                p.sendMessage(NightPactPlugin.colorize(plugin.getConfig().getString("messages.prefix", "")) +
                        "§bВещий сон: §f" + type.name() + " §7≈ §f" +
                        found.getBlockX() + " " + found.getBlockY() + " " + found.getBlockZ());
            } else {
                p.sendMessage(NightPactPlugin.colorize(plugin.getConfig().getString("messages.prefix", "")) +
                        "§bВещий сон: §7образ расплывается…");
            }
        }
    }
}
