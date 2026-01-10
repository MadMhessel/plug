package ru.atmstr.nightpact;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SleepManager {

    private final NightPactPlugin plugin;
    private final EffectManager effectManager;

    private final Map<UUID, Participant> participants = new ConcurrentHashMap<>();
    private final Set<UUID> online = ConcurrentHashMap.newKeySet();

    private BukkitRunnable countdownTask;
    private int countdownRemaining;

    public SleepManager(NightPactPlugin plugin, EffectManager effectManager) {
        this.plugin = plugin;
        this.effectManager = effectManager;
    }

    public void trackOnline(Player p) {
        online.add(p.getUniqueId());
        participants.computeIfAbsent(p.getUniqueId(), id -> new Participant());
        refreshBossBar(p.getWorld());
    }

    public void untrack(Player p) {
        online.remove(p.getUniqueId());
        participants.remove(p.getUniqueId());
        refreshBossBar(p.getWorld());
    }

    public void markSleeping(Player p, Location bedLocation) {
        trackOnline(p);

        Participant part = participants.computeIfAbsent(p.getUniqueId(), id -> new Participant());
        part.sleeping = true;
        part.bedLocation = bedLocation;

        // тревожный сон
        if (plugin.getConfig().getBoolean("settings.anxious_sleep.enabled", true)) {
            part.anxious = isAnxiousNow(p, bedLocation);
        } else {
            part.anxious = false;
        }

        tryStartOrUpdatePact(p.getWorld());
    }

    public void markAwake(Player p) {
        Participant part = participants.get(p.getUniqueId());
        if (part != null) {
            part.sleeping = false;
            part.anxious = false;
            part.bedLocation = null;
        }
        tryStartOrUpdatePact(p.getWorld());
    }

    public void tagCombat(Player p) {
        Participant part = participants.computeIfAbsent(p.getUniqueId(), id -> new Participant());
        part.lastCombatMillis = System.currentTimeMillis();
    }

    public String buildStatusLine(CommandSender sender) {
        World w = (sender instanceof Player p) ? p.getWorld() : Bukkit.getWorlds().get(0);
        PactNumbers n = computeNumbers(w);

        String raw = plugin.getConfig().getString("messages.status_line", "&7Спят: &f%SLEEPERS%&7/&f%ONLINE%&7 (нужно: &f%REQUIRED%&7)");
        raw = raw.replace("%SLEEPERS%", String.valueOf(n.sleepers))
                .replace("%ONLINE%", String.valueOf(n.online))
                .replace("%REQUIRED%", String.valueOf(n.required));
        return NightPactPlugin.colorize(plugin.getConfig().getString("messages.prefix", "") + raw);
    }

    public void forceSkip(CommandSender sender) {
        // выбираем мир: если команда от игрока — его мир, иначе первый в списке enabled_worlds/или overworld.
        World world = null;
        if (sender instanceof Player p) {
            world = p.getWorld();
        } else {
            Set<World> enabled = plugin.getEnabledWorlds();
            world = enabled.stream().findFirst().orElse(Bukkit.getWorlds().get(0));
        }

        // В принудительном режиме запускаем "пропуск" без требований, но с теми же эффектами (спящие = те, кто реально в кровати).
        sender.sendMessage(NightPactPlugin.colorize(plugin.getConfig().getString("messages.prefix", "")) +
                NightPactPlugin.colorize(plugin.getConfig().getString("messages.forced", "&eПропуск ночи запущен принудительно.")));
        doSkip(world);
    }

    private void tryStartOrUpdatePact(World world) {
        if (!plugin.getEnabledWorlds().contains(world)) return;

        if (plugin.getConfig().getBoolean("settings.night_only", true) && !isNight(world)) {
            // если уже идёт подготовка — отменяем; иначе молчим (чтобы не спамить).
            if (countdownTask != null) stopCountdown(world, false);
            return;
        }

        PactNumbers n = computeNumbers(world);

        if (n.online == 0) {
            stopCountdown(world, false);
            return;
        }

        if (n.sleepers >= n.required && n.sleepers >= n.minSleepers) {
            if (countdownTask == null) {
                startCountdown(world);
            } else {
                // уже идет — просто обновим полосу
                refreshBossBar(world);
            }
        } else {
            // не хватает — отменяем, если было
            if (countdownTask != null) {
                stopCountdown(world, true);
            } else {
                refreshBossBar(world);
            }
        }
    }

    private void startCountdown(World world) {
        countdownRemaining = plugin.getConfig().getInt("settings.preparation_seconds", 15);
        if (countdownRemaining < 3) countdownRemaining = 3;

        broadcast(world, plugin.getConfig().getString("messages.prep_started", "&eПакт сна начат. Подготовка: &f%SECONDS%&e сек.")
                .replace("%SECONDS%", String.valueOf(countdownRemaining)));

        BossBar bar = plugin.getBossBar();
        if (bar != null) {
            bar.setVisible(true);
        }

        countdownTask = new BukkitRunnable() {
            final int total = countdownRemaining;

            @Override
            public void run() {
                // пересчёт требований на каждом тике подготовки
                PactNumbers n = computeNumbers(world);

                if (plugin.getConfig().getBoolean("settings.night_only", true) && !isNight(world)) {
                    stopCountdown(world, false);
                    return;
                }

                if (n.sleepers < n.required || n.sleepers < n.minSleepers) {
                    stopCountdown(world, true);
                    return;
                }

                countdownRemaining--;
                updateBossBar(world, total);

                if (countdownRemaining <= 0) {
                    stopCountdown(world, false);
                    doSkip(world);
                }
            }
        };
        countdownTask.runTaskTimer(plugin, 20L, 20L);
        refreshBossBar(world);
    }

    private void stopCountdown(World world, boolean cancelled) {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }

        BossBar bar = plugin.getBossBar();
        if (bar != null) {
            bar.setVisible(false);
            bar.removeAll();
        }

        if (cancelled) {
            broadcast(world, plugin.getConfig().getString("messages.prep_cancelled", "&cПакт сна сорван: недостаточно спящих."));
        }
    }

    private void doSkip(World world) {
        // Собираем контекст
        List<Player> onlinePlayers = getOnlinePlayersInWorld(world);
        List<Player> sleepers = onlinePlayers.stream()
                .filter(p -> {
                    Participant part = participants.get(p.getUniqueId());
                    return part != null && part.sleeping;
                })
                .collect(Collectors.toList());

        List<Player> sentinels = onlinePlayers.stream()
                .filter(p -> sleepers.stream().noneMatch(s -> s.getUniqueId().equals(p.getUniqueId())))
                .collect(Collectors.toList());

        int anxiousCount = 0;
        Map<UUID, Location> bedLocations = new HashMap<>();
        for (Player p : sleepers) {
            Participant part = participants.get(p.getUniqueId());
            if (part != null) {
                if (part.anxious) anxiousCount++;
                if (part.bedLocation != null) bedLocations.put(p.getUniqueId(), part.bedLocation);
            }
        }

        // Пропуск ночи
        world.setTime(0L);
        if (plugin.getConfig().getBoolean("settings.clear_weather_on_skip", true)) {
            world.setStorm(false);
            world.setThundering(false);
            world.setWeatherDuration(0);
        }

        // Эффект
        PactContext ctx = new PactContext(world, onlinePlayers, sleepers, sentinels, anxiousCount, bedLocations,
                plugin.getConfig().getBoolean("settings.combo_sleep.enabled", true),
                plugin.getConfig().getInt("settings.combo_sleep.radius", 32),
                plugin.getConfig().getInt("settings.combo_sleep.min_cluster_size", 2)
        );

        PactEffect effect = effectManager.select(ctx);
        String effectName = effectManager.getEffectDisplayName(effect);

        broadcast(world, plugin.getConfig().getString("messages.night_skipped", "&aНочь пропущена. Послевкусие: &f%EFFECT%&a.")
                .replace("%EFFECT%", effectName));

        try {
            effect.apply(plugin, ctx);
        } catch (Throwable t) {
            plugin.getLogger().warning("Ошибка при применении эффекта " + effect.getId() + ": " + t.getMessage());
            t.printStackTrace();
        }

        // Сброс состояния сна (чтобы не зависало)
        for (Player p : onlinePlayers) {
            Participant part = participants.get(p.getUniqueId());
            if (part != null) {
                part.sleeping = false;
                part.anxious = false;
                part.bedLocation = null;
            }
        }
    }

    private void updateBossBar(World world, int total) {
        BossBar bar = plugin.getBossBar();
        if (bar == null) return;

        PactNumbers n = computeNumbers(world);

        double progress = (double) (total - countdownRemaining) / (double) total;
        if (progress < 0) progress = 0;
        if (progress > 1) progress = 1;

        String title = plugin.getConfig().getString("messages.prefix", "&6[&eNightPact&6]&r ") +
                "&eПакт сна: &f" + n.sleepers + "/" + n.online + "&e | &7" + countdownRemaining + "с";
        bar.setTitle(NightPactPlugin.colorize(title));
        bar.setProgress(progress);

        refreshBossBar(world);
    }

    private void refreshBossBar(World world) {
        BossBar bar = plugin.getBossBar();
        if (bar == null) return;

        bar.removeAll();
        for (Player p : getOnlinePlayersInWorld(world)) {
            bar.addPlayer(p);
        }
    }

    private void broadcast(World world, String msgRaw) {
        String msg = NightPactPlugin.colorize(plugin.getConfig().getString("messages.prefix", "") + msgRaw);
        for (Player p : getOnlinePlayersInWorld(world)) {
            p.sendMessage(msg);
        }
    }

    private boolean isNight(World world) {
        long t = world.getTime();
        return t >= 12541 && t <= 23458;
    }

    private PactNumbers computeNumbers(World world) {
        List<Player> onlinePlayers = getOnlinePlayersInWorld(world);
        int onlineCount = onlinePlayers.size();

        int sleepersCount = 0;
        for (Player p : onlinePlayers) {
            Participant part = participants.get(p.getUniqueId());
            if (part != null && part.sleeping) sleepersCount++;
        }

        double ratio = plugin.getConfig().getDouble("settings.required_ratio", 0.40);
        if (ratio < 0) ratio = 0;
        if (ratio > 1) ratio = 1;

        int required = (int) Math.ceil(onlineCount * ratio);
        int minSleepers = Math.max(1, plugin.getConfig().getInt("settings.min_sleepers", 1));
        if (required < minSleepers) required = minSleepers;

        return new PactNumbers(onlineCount, sleepersCount, required, minSleepers);
    }

    private List<Player> getOnlinePlayersInWorld(World world) {
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getWorld().equals(world))
                .collect(Collectors.toList());
    }

    private boolean isAnxiousNow(Player p, Location bedLocation) {
        // Недавний бой
        int combatSeconds = plugin.getConfig().getInt("settings.anxious_sleep.combat_tag_seconds", 12);
        long combatWindowMs = Math.max(1, combatSeconds) * 1000L;

        Participant part = participants.get(p.getUniqueId());
        if (part != null) {
            long delta = System.currentTimeMillis() - part.lastCombatMillis;
            if (delta >= 0 && delta <= combatWindowMs) {
                return true;
            }
        }

        // Мобы рядом
        int radius = plugin.getConfig().getInt("settings.anxious_sleep.hostile_scan_radius", 12);
        if (radius <= 0) return false;

        Location center = (bedLocation != null) ? bedLocation : p.getLocation();
        Collection<Entity> near = center.getWorld().getNearbyEntities(center, radius, radius, radius);
        for (Entity e : near) {
            if (!(e instanceof LivingEntity le)) continue;
            // Упрощённая эвристика: враждебные существа обычно таргетят игроков, но API "hostile" не везде единый.
            // Берём по "глазному" правилу: если это не игрок и не мирный, и имеет AI — считаем опасным.
            if (e instanceof Player) continue;

            // Пытаемся определить агрессию через target (не у всех сущностей есть).
            try {
                Object target = le.getClass().getMethod("getTarget").invoke(le);
                if (target instanceof Player) return true;
            } catch (Throwable ignored) {
                // fallthrough
            }

            // Если сущность способна наносить урон и близко — тоже тревожно.
            // (оставляем максимально безопасно: если есть каст к "Monster" — точно hostile)
            try {
                Class<?> monster = Class.forName("org.bukkit.entity.Monster");
                if (monster.isInstance(e)) return true;
            } catch (Throwable ignored) {
                // If API changed, ignore.
            }
        }
        return false;
    }

    private static final class PactNumbers {
        final int online;
        final int sleepers;
        final int required;
        final int minSleepers;

        PactNumbers(int online, int sleepers, int required, int minSleepers) {
            this.online = online;
            this.sleepers = sleepers;
            this.required = required;
            this.minSleepers = minSleepers;
        }
    }

    private static final class Participant {
        boolean sleeping = false;
        boolean anxious = false;
        Location bedLocation = null;
        long lastCombatMillis = 0L;
    }
}
