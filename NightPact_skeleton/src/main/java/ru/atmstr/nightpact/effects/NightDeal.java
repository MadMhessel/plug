package ru.atmstr.nightpact.effects;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.atmstr.nightpact.NightPactPlugin;
import ru.atmstr.nightpact.Participant;
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

        int cooldownMinutes = Math.max(0, sec.getInt("cooldown_minutes", 30));
        long cooldownMs = cooldownMinutes * 60_000L;
        long now = System.currentTimeMillis();

        List<ItemStack> loot = parseLoot(plugin, sec.getStringList("loot"));
        if (loot.isEmpty()) return;

        double rareChance = Math.max(0.0, sec.getDouble("rare_chance", 0.08));
        List<ItemStack> rareLoot = parseLoot(plugin, sec.getStringList("rare_loot"));

        for (Player p : ctx.sleepers) {
            Participant participant = ctx.getParticipant(p.getUniqueId());
            if (participant != null && participant.isOnCooldown(getId(), cooldownMs, now)) {
                long minutesLeft = Math.max(1, (participant.getCooldownRemaining(getId(), cooldownMs, now) + 59_999) / 60_000L);
                String raw = plugin.getConfig().getString("messages.night_deal_cooldown",
                        "§6Ночная сделка: §7рынок молчит (ещё %MINUTES% мин.)");
                String msg = raw.replace("%MINUTES%", String.valueOf(minutesLeft));
                p.sendMessage(NightPactPlugin.colorize(plugin.getConfig().getString("messages.prefix", "")) + msg);
                continue;
            }

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

            if (participant != null) {
                participant.setCooldown(getId(), now);
            }
        }
    }

    private List<ItemStack> parseLoot(NightPactPlugin plugin, List<String> lines) {
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
                plugin.getLogger().warning("Некорректный материал в effects.night_deal: " + matStr);
                continue;
            }
            amount = Math.max(1, Math.min(64, amount));
            out.add(new ItemStack(m, amount));
        }
        return out;
    }
}
