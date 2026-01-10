package ru.atmstr.nightpact.effects;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.atmstr.nightpact.NightPactPlugin;
import ru.atmstr.nightpact.PactContext;
import ru.atmstr.nightpact.PactEffect;

public class NightCold implements PactEffect {

    @Override
    public String getId() {
        return "night_cold";
    }

    @Override
    public Category getCategory() {
        return Category.NEUTRAL;
    }

    @Override
    public void apply(NightPactPlugin plugin, PactContext ctx) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("effects.night_cold");
        int dur = sec != null ? sec.getInt("duration_seconds", 30) : 30;
        int amp = sec != null ? sec.getInt("amplifier", 0) : 0;

        int ticks = Math.max(1, dur) * 20;

        for (Player p : ctx.sentinels) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, Math.max(0, amp), true, true, true));
        }
    }
}
