package ru.atmstr.nightpact.effects;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.atmstr.nightpact.NightPactPlugin;
import ru.atmstr.nightpact.PactContext;
import ru.atmstr.nightpact.PactEffect;

import java.util.List;
import java.util.Objects;
import java.util.Random;

public class BadDream implements PactEffect {

    private final Random rng = new Random();

    @Override
    public String getId() {
        return "bad_dream";
    }

    @Override
    public Category getCategory() {
        return Category.NEGATIVE;
    }

    @Override
    public void apply(NightPactPlugin plugin, PactContext ctx) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("effects.bad_dream");
        if (sec == null) return;

        List<String> types = sec.getStringList("mob_types");
        int min = Math.max(0, sec.getInt("count_min", 1));
        int max = Math.max(min, sec.getInt("count_max", 2));
        int spawnRadius = Math.max(1, sec.getInt("spawn_radius", 10));
        int maxTotal = Math.max(0, sec.getInt("max_total_spawn", 4));

        int strSec = Math.max(1, sec.getInt("buff_strength_seconds", 25));
        int spdSec = Math.max(1, sec.getInt("buff_speed_seconds", 25));

        if (ctx.sentinels.isEmpty()) return;

        int totalSpawn = 0;

        for (Player sentinel : ctx.sentinels) {
            if (totalSpawn >= maxTotal) break;

            int count = min + rng.nextInt((max - min) + 1);
            for (int i = 0; i < count; i++) {
                if (totalSpawn >= maxTotal) break;

                EntityType et = pickEntityType(types);
                if (et == null || !et.isSpawnable() || !et.isAlive()) et = EntityType.ZOMBIE;

                Location base = sentinel.getLocation();
                Location spawn = base.clone().add(rng.nextInt(spawnRadius * 2 + 1) - spawnRadius, 0,
                        rng.nextInt(spawnRadius * 2 + 1) - spawnRadius);
                spawn.setY(base.getWorld().getHighestBlockYAt(spawn) + 1);

                Entity e = base.getWorld().spawnEntity(spawn, et);
                if (e instanceof LivingEntity le) {
                    le.setCustomName("§cЭхо ночи");
                    le.setCustomNameVisible(false);

                    le.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, strSec * 20, 0, true, true, true));
                    le.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, spdSec * 20, 0, true, true, true));

                    // слегка поджать ХП, чтобы это было мини-событие, а не катастрофа
                    try {
                        if (le.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
                            Objects.requireNonNull(le.getAttribute(Attribute.GENERIC_MAX_HEALTH)).setBaseValue(14.0);
                            le.setHealth(Math.min(le.getHealth(), 14.0));
                        }
                    } catch (Throwable ignored) {}

                    // таргет на дежурного, если возможно
                    if (le instanceof Mob mob) {
                        mob.setTarget(sentinel);
                    }
                }
                totalSpawn++;
            }
        }
    }

    private EntityType pickEntityType(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        String s = list.get(rng.nextInt(list.size()));
        try {
            return EntityType.valueOf(s.trim().toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }
}
