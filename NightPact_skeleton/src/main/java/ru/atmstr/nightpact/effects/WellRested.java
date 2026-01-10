package ru.atmstr.nightpact.effects;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.atmstr.nightpact.NightPactPlugin;
import ru.atmstr.nightpact.PactContext;
import ru.atmstr.nightpact.PactEffect;

public class WellRested implements PactEffect {

    @Override
    public String getId() {
        return "well_rested";
    }

    @Override
    public Category getCategory() {
        return Category.POSITIVE;
    }

    @Override
    public void apply(NightPactPlugin plugin, PactContext ctx) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("effects.well_rested");
        int dur = sec != null ? sec.getInt("duration_seconds", 35) : 35;
        int amp = sec != null ? sec.getInt("amplifier", 0) : 0;

        int ticks = Math.max(1, dur) * 20;

        for (Player p : ctx.sleepers) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, ticks, Math.max(0, amp), true, true, true));
        }
    }
}
