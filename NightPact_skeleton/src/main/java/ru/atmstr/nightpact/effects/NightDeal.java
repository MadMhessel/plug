package ru.atmstr.nightpact.effects;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.atmstr.nightpact.NightPactPlugin;
import ru.atmstr.nightpact.PactContext;
import ru.atmstr.nightpact.PactEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class NightDeal implements PactEffect {

    private final Random rng = new Random();

    @Override
    public String getId() {
        return "night_deal";
    }

    @Override
    public Category getCategory() {
        return Category.POSITIVE;
    }

    @Override
    public void apply(NightPactPlugin plugin, PactContext ctx) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("effects.night_deal");
        if (sec == null) return;
        if (!sec.getBoolean("enabled", true)) return;

        List<ItemStack> loot = parseLoot(sec.getStringList("loot"));
        if (loot.isEmpty()) return;

        double rareChance = Math.max(0.0, sec.getDouble("rare_chance", 0.08));
        List<ItemStack> rareLoot = parseLoot(sec.getStringList("rare_loot"));

        for (Player p : ctx.sleepers) {
            Location base = ctx.bedLocations.getOrDefault(p.getUniqueId(), p.getLocation());
            Location drop = base.clone().add(0.5, 1.2, 0.5);

            // обычный лут
            for (ItemStack it : loot) {
                ctx.world.dropItemNaturally(drop, it.clone());
            }

            // редкий лут
            if (!rareLoot.isEmpty() && rng.nextDouble() < rareChance) {
                for (ItemStack it : rareLoot) {
                    ctx.world.dropItemNaturally(drop, it.clone());
                }
            }
        }
    }

    private List<ItemStack> parseLoot(List<String> lines) {
        List<ItemStack> out = new ArrayList<>();
        if (lines == null) return out;

        for (String line : lines) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split(":");
            String matStr = parts[0].trim().toUpperCase();
            int amount = 1;
            if (parts.length >= 2) {
                try { amount = Integer.parseInt(parts[1].trim()); } catch (Exception ignored) {}
            }
            Material m;
            try {
                m = Material.valueOf(matStr);
            } catch (Exception e) {
                continue;
            }
            amount = Math.max(1, Math.min(64, amount));
            out.add(new ItemStack(m, amount));
        }
        return out;
    }
}
