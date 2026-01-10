package ru.atmstr.nightpact;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Statistic;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SleepManager {

    private final NightPactPlugin plugin;
    private final EffectManager effectManager;

    private final Map<UUID, Participant> participants = new ConcurrentHashMap<>();
    private final Map<UUID, WorldState> worldStates = new ConcurrentHashMap<>();

    private final Deque<String> debugLog = new ArrayDeque<>();
    private boolean debugEnabled;

    public SleepManager(NightPactPlugin plugin, EffectManager effectManager) {
        this.plugin = plugin;
        this.effectManager = effectManager;
        this.debugEnabled = plugin.getConfig().getBoolean("settings.debug", false);
        initializeWorldStates();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isWorldEnabled(player.getWorld())) {
                trackOnline(player);
            }
        }
    }

    public void trackOnline(Player p) {
        if (!isWorldEnabled(p.getWorld())) return;
        WorldState state = getWorldState(p.getWorld());
        state.getOnline().add(p.getUniqueId());
        participants.computeIfAbsent(p.getUniqueId(), id -> new Participant());
        refreshBossBar(state);
        tryStartOrUpdatePact(state.getWorld());
    }

    public void untrack(Player p) {
        World world = p.getWorld();
        if (isWorldEnabled(world)) {
            WorldState state = getWorldState(world);
            state.getOnline().remove(p.getUniqueId());
            state.getSleepers().remove(p.getUniqueId());
            refreshBossBar(state);
            tryStartOrUpdatePact(world);
        }
        Participant part = participants.get(p.getUniqueId());
        if (part != null) {
            part.setSleeping(false);
            part.setAnxious(false);
            part.setBedLocation(null);
        }
    }

    public void markSleeping(Player p, Location bedLocation) {
        if (!isWorldEnabled(p.getWorld())) return;
        trackOnline(p);

        Participant part = participants.computeIfAbsent(p.getUniqueId(), id -> new Participant());
        part.setSleeping(true);
        part.setBedLocation(bedLocation);

        // тревожный сон
        if (plugin.getConfig().getBoolean("settings.anxious_sleep.enabled", true)) {
            part.setAnxious(isAnxiousNow(p, bedLocation));
        } else {
            part.setAnxious(false);
        }

        getWorldState(p.getWorld()).getSleepers().add(p.getUniqueId());
        tryStartOrUpdatePact(p.getWorld());
    }

    public void markAwake(Player p) {
        Participant part = participants.get(p.getUniqueId());
        if (part != null) {
            part.setSleeping(false);
            part.setAnxious(false);
            part.setBedLocation(null);
        }
        if (isWorldEnabled(p.getWorld())) {
            getWorldState(p.getWorld()).getSleepers().remove(p.getUniqueId());
        }
        tryStartOrUpdatePact(p.getWorld());
    }

    public void tagCombat(Player p) {
        Participant part = participants.computeIfAbsent(p.getUniqueId(), id -> new Participant());
        part.setLastCombatMillis(System.currentTimeMillis());
    }

    public void handleWorldChange(Player player, World from, World to) {
        if (from != null && isWorldEnabled(from)) {
            WorldState state = getWorldState(from);
            state.getOnline().remove(player.getUniqueId());
            state.getSleepers().remove(player.getUniqueId());
            refreshBossBar(state);
            tryStartOrUpdatePact(from);
        }

        Participant part = participants.get(player.getUniqueId());
        if (part != null) {
            part.setSleeping(false);
            part.setAnxious(false);
            part.setBedLocation(null);
        }

        if (to != null && isWorldEnabled(to)) {
            WorldState state = getWorldState(to);
            state.getOnline().add(player.getUniqueId());
            refreshBossBar(state);
            tryStartOrUpdatePact(to);
        }
    }

    public String buildStatusLine(World world) {
        if (!isWorldEnabled(world)) {
            return NightPactPlugin.colorize(plugin.getConfig().getString("messages.prefix", "") +
                    "&cМир не включён в NightPact: " + world.getName());
        }

        WorldState state = getWorldState(world);
        PactNumbers n = computeNumbers(state);
        boolean preparing = state.getCountdownTask() != null;
        int seconds = state.getCountdownRemaining();

        boolean combo = buildContext(world).hasComboCluster();
        int anxious = countAnxious(state);

        String yes = plugin.getConfig().getString("messages.yes", "да");
        String no = plugin.getConfig().getString("messages.no", "нет");

        String raw = plugin.getConfig().getString("messages.status_line",
                "&7Мир: &f%WORLD%&7 | Спят: &f%SLEEPERS%&7/&f%ONLINE%&7 (нужно: &f%REQUIRED%&7) " +
                        "| Подготовка: &f%PREPARING%&7 (%SECONDS%с) | Тревожный сон: &f%ANXIOUS%&7 | Комбо: &f%COMBO%");
        raw = raw.replace("%WORLD%", world.getName())
                .replace("%SLEEPERS%", String.valueOf(n.sleepers))
                .replace("%ONLINE%", String.valueOf(n.online))
                .replace("%REQUIRED%", String.valueOf(n.required))
                .replace("%PREPARING%", preparing ? yes : no)
                .replace("%SECONDS%", String.valueOf(preparing ? seconds : 0))
                .replace("%ANXIOUS%", String.valueOf(anxious))
                .replace("%COMBO%", combo ? yes : no);
        return NightPactPlugin.colorize(plugin.getConfig().getString("messages.prefix", "") + raw);
    }

    public void forceSkip(CommandSender sender, World world) {
        // В принудительном режиме запускаем "пропуск" без требований, но с теми же эффектами (спящие = те, кто реально в кровати).
        sender.sendMessage(NightPactPlugin.colorize(plugin.getConfig().getString("messages.prefix", "")) +
                NightPactPlugin.colorize(plugin.getConfig().getString("messages.forced", "&eПропуск ночи запущен принудительно.")));
        doSkip(world);
    }

    private void tryStartOrUpdatePact(World world) {
        if (!isWorldEnabled(world)) return;
        WorldState state = getWorldState(world);

        if (plugin.getConfig().getBoolean("settings.night_only", true) && !isNight(world)) {
            // если уже идёт подготовка — отменяем; иначе молчим (чтобы не спамить).
            if (state.getCountdownTask() != null) stopCountdown(state, false);
            return;
        }

        PactNumbers n = computeNumbers(state);

        if (n.online == 0) {
            stopCountdown(state, false);
            return;
        }

        if (n.sleepers >= n.required && n.sleepers >= n.minSleepers) {
            if (state.getCountdownTask() == null) {
                startCountdown(state);
            } else {
                // уже идет — просто обновим полосу
                refreshBossBar(state);
            }
        } else {
            // не хватает — отменяем, если было
            if (state.getCountdownTask() != null) {
                stopCountdown(state, true);
            } else {
                refreshBossBar(state);
            }
        }
    }

    private void startCountdown(WorldState state) {
        state.setCountdownRemaining(plugin.getConfig().getInt("settings.preparation_seconds", 15));
        if (state.getCountdownRemaining() < 3) state.setCountdownRemaining(3);

        broadcast(state.getWorld(), plugin.getConfig().getString("messages.prep_started", "&eПакт сна начат. Подготовка: &f%SECONDS%&e сек.")
                .replace("%SECONDS%", String.valueOf(state.getCountdownRemaining())));

        BossBar bar = state.getBossBar();
        if (bar != null) {
            bar.setVisible(true);
        }

        BukkitRunnable task = new BukkitRunnable() {
            final int total = state.getCountdownRemaining();

            @Override
            public void run() {
                // пересчёт требований на каждом тике подготовки
                PactNumbers n = computeNumbers(state);

                if (plugin.getConfig().getBoolean("settings.night_only", true) && !isNight(state.getWorld())) {
                    stopCountdown(state, false);
                    return;
                }

                if (n.sleepers < n.required || n.sleepers < n.minSleepers) {
                    stopCountdown(state, true);
                    return;
                }

                state.setCountdownRemaining(state.getCountdownRemaining() - 1);
                updateBossBar(state, total);

                if (state.getCountdownRemaining() <= 0) {
                    stopCountdown(state, false);
                    doSkip(state.getWorld());
                }
            }
        };
        state.setCountdownTask(task);
        task.runTaskTimer(plugin, 20L, 20L);
        refreshBossBar(state);
    }

    private void stopCountdown(WorldState state, boolean cancelled) {
        if (state.getCountdownTask() != null) {
            state.getCountdownTask().cancel();
            state.setCountdownTask(null);
        }

        BossBar bar = state.getBossBar();
        if (bar != null) {
            bar.setVisible(false);
            bar.removeAll();
        }

        if (cancelled) {
            broadcast(state.getWorld(), plugin.getConfig().getString("messages.prep_cancelled", "&cПакт сна сорван: недостаточно спящих."));
        }
    }

    private void doSkip(World world) {
        // Собираем контекст
        WorldState state = getWorldState(world);
        List<Player> onlinePlayers = getOnlinePlayersInWorld(state);
        List<Player> sleepers = onlinePlayers.stream()
                .filter(p -> {
                    Participant part = participants.get(p.getUniqueId());
                    return part != null && part.isSleeping();
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
                if (part.isAnxious()) anxiousCount++;
                if (part.getBedLocation() != null) bedLocations.put(p.getUniqueId(), part.getBedLocation());
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
        PactContext ctx = new PactContext(world, onlinePlayers, sleepers, sentinels, anxiousCount, bedLocations, participants,
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

        resetPhantomTimer(world, sleepers, onlinePlayers);

        // Сброс состояния сна (чтобы не зависало)
        for (Player p : onlinePlayers) {
            Participant part = participants.get(p.getUniqueId());
            if (part != null) {
                part.setSleeping(false);
                part.setAnxious(false);
                part.setBedLocation(null);
            }
        }
        state.getSleepers().clear();
    }

    private void updateBossBar(WorldState state, int total) {
        BossBar bar = state.getBossBar();
        if (bar == null) return;

        PactNumbers n = computeNumbers(state);

        double progress = (double) (total - state.getCountdownRemaining()) / (double) total;
        if (progress < 0) progress = 0;
        if (progress > 1) progress = 1;

        String title = plugin.getConfig().getString("messages.prefix", "&6[&eNightPact&6]&r ") +
                "&eПакт сна: &f" + n.sleepers + "/" + n.online + "&e | &7" + state.getCountdownRemaining() + "с";
        bar.setTitle(NightPactPlugin.colorize(title));
        bar.setProgress(progress);

        refreshBossBar(state);

        if (plugin.getConfig().getBoolean("settings.show_actionbar", false)) {
            String actionRaw = plugin.getConfig().getString("messages.actionbar", "&eПакт сна: &f%SLEEPERS%&7/&f%ONLINE%&e | &7%SECONDS%с");
            String action = actionRaw.replace("%SLEEPERS%", String.valueOf(n.sleepers))
                    .replace("%ONLINE%", String.valueOf(n.online))
                    .replace("%SECONDS%", String.valueOf(state.getCountdownRemaining()));
            for (Player p : getOnlinePlayersInWorld(state)) {
                p.sendActionBar(NightPactPlugin.colorize(action));
            }
        }
    }

    private void refreshBossBar(WorldState state) {
        BossBar bar = state.getBossBar();
        if (bar == null) return;

        bar.removeAll();
        for (Player p : getOnlinePlayersInWorld(state)) {
            bar.addPlayer(p);
        }
    }

    private void broadcast(World world, String msgRaw) {
        String msg = NightPactPlugin.colorize(plugin.getConfig().getString("messages.prefix", "") + msgRaw);
        WorldState state = getWorldState(world);
        for (Player p : getOnlinePlayersInWorld(state)) {
            p.sendMessage(msg);
        }
    }

    private boolean isNight(World world) {
        long t = world.getTime();
        return t >= 12541 && t <= 23458;
    }

    private PactNumbers computeNumbers(WorldState state) {
        int onlineCount = state.getOnline().size();
        int sleepersCount = state.getSleepers().size();

        double ratio = plugin.getConfig().getDouble("settings.required_ratio", 0.40);
        if (ratio < 0) ratio = 0;
        if (ratio > 1) ratio = 1;

        int required = (int) Math.ceil(onlineCount * ratio);
        int minSleepers = Math.max(1, plugin.getConfig().getInt("settings.min_sleepers", 1));
        if (required < minSleepers) required = minSleepers;

        return new PactNumbers(onlineCount, sleepersCount, required, minSleepers);
    }

    private List<Player> getOnlinePlayersInWorld(WorldState state) {
        List<Player> players = new ArrayList<>();
        for (UUID uuid : state.getOnline()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline() && p.getWorld().equals(state.getWorld())) {
                players.add(p);
            }
        }
        return players;
    }

    private boolean isAnxiousNow(Player p, Location bedLocation) {
        // Недавний бой
        int combatSeconds = plugin.getConfig().getInt("settings.anxious_sleep.combat_tag_seconds", 12);
        long combatWindowMs = Math.max(1, combatSeconds) * 1000L;

        Participant part = participants.get(p.getUniqueId());
        if (part != null) {
            long delta = System.currentTimeMillis() - part.getLastCombatMillis();
            if (delta >= 0 && delta <= combatWindowMs) {
                return true;
            }
        }

        // Мобы рядом
        int radius = plugin.getConfig().getInt("settings.anxious_sleep.hostile_scan_radius", 12);
        if (radius <= 0) return false;

        Location center = (bedLocation != null) ? bedLocation : p.getLocation();
        Collection<Entity> near = center.getWorld().getNearbyEntities(center, radius, radius, radius);
        Set<EntityType> list = parseHostileList();
        String modeRaw = plugin.getConfig().getString("settings.anxious_sleep.mode", "MONSTER_ONLY");
        AnxiousMode mode = AnxiousMode.from(modeRaw);

        for (Entity e : near) {
            if (e instanceof Player) continue;
            if (mode.matches(e, list)) return true;
        }
        return false;
    }

    private PactContext buildContext(World world) {
        WorldState state = getWorldState(world);
        List<Player> onlinePlayers = getOnlinePlayersInWorld(state);
        List<Player> sleepers = onlinePlayers.stream()
                .filter(p -> state.getSleepers().contains(p.getUniqueId()))
                .collect(Collectors.toList());
        List<Player> sentinels = onlinePlayers.stream()
                .filter(p -> !state.getSleepers().contains(p.getUniqueId()))
                .collect(Collectors.toList());
        Map<UUID, Location> bedLocations = new HashMap<>();
        for (Player p : sleepers) {
            Participant part = participants.get(p.getUniqueId());
            if (part != null && part.getBedLocation() != null) {
                bedLocations.put(p.getUniqueId(), part.getBedLocation());
            }
        }

        return new PactContext(world, onlinePlayers, sleepers, sentinels, countAnxious(state), bedLocations, participants,
                plugin.getConfig().getBoolean("settings.combo_sleep.enabled", true),
                plugin.getConfig().getInt("settings.combo_sleep.radius", 32),
                plugin.getConfig().getInt("settings.combo_sleep.min_cluster_size", 2));
    }

    private int countAnxious(WorldState state) {
        int anxious = 0;
        for (UUID uuid : state.getSleepers()) {
            Participant part = participants.get(uuid);
            if (part != null && part.isAnxious()) anxious++;
        }
        return anxious;
    }

    private void resetPhantomTimer(World world, List<Player> sleepers, List<Player> onlinePlayers) {
        String modeRaw = plugin.getConfig().getString("settings.reset_phantom_timer.mode", "SLEEPERS");
        ResetMode mode = ResetMode.from(modeRaw);
        if (mode == ResetMode.NONE) return;

        List<Player> targets = mode == ResetMode.ALL ? onlinePlayers : sleepers;
        for (Player player : targets) {
            player.setStatistic(Statistic.TIME_SINCE_REST, 0);
        }
    }

    private Set<EntityType> parseHostileList() {
        List<String> list = plugin.getConfig().getStringList("settings.anxious_sleep.hostile_entity_types");
        if (list == null || list.isEmpty()) return Collections.emptySet();
        Set<EntityType> types = EnumSet.noneOf(EntityType.class);
        for (String raw : list) {
            if (raw == null || raw.isBlank()) continue;
            try {
                types.add(EntityType.valueOf(raw.trim().toUpperCase()));
            } catch (Exception e) {
                plugin.getLogger().warning("Некорректный тип сущности в settings.anxious_sleep.hostile_entity_types: " + raw);
            }
        }
        return types;
    }

    private void initializeWorldStates() {
        for (World world : plugin.getEnabledWorlds()) {
            getWorldState(world);
        }
    }

    private boolean isWorldEnabled(World world) {
        return plugin.getEnabledWorlds().contains(world);
    }

    private WorldState getWorldState(World world) {
        return worldStates.computeIfAbsent(world.getUID(), id -> {
            WorldState state = new WorldState(world);
            if (plugin.getConfig().getBoolean("settings.show_progress_bar", true)) {
                BossBar bar = Bukkit.createBossBar(
                        NightPactPlugin.colorize(plugin.getConfig().getString("messages.prefix", "&6[&eNightPact&6]&r ") + "&e..."),
                        BarColor.YELLOW, BarStyle.SOLID);
                bar.setVisible(false);
                state.setBossBar(bar);
            }
            return state;
        });
    }

    public void shutdown() {
        for (WorldState state : worldStates.values()) {
            if (state.getCountdownTask() != null) {
                state.getCountdownTask().cancel();
                state.setCountdownTask(null);
            }
            if (state.getBossBar() != null) {
                state.getBossBar().removeAll();
            }
        }
        worldStates.clear();
    }

    public void setDebugEnabled(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
    }

    private void debug(String message) {
        if (!debugEnabled) return;
        if (debugLog.size() >= 100) {
            debugLog.pollFirst();
        }
        debugLog.addLast(message);
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

    private enum ResetMode {
        SLEEPERS,
        ALL,
        NONE;

        static ResetMode from(String raw) {
            if (raw == null) return SLEEPERS;
            try {
                return ResetMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (Exception e) {
                return SLEEPERS;
            }
        }
    }

    private enum AnxiousMode {
        MONSTER_ONLY,
        LIST_ONLY,
        MONSTER_PLUS_LIST;

        static AnxiousMode from(String raw) {
            if (raw == null) return MONSTER_ONLY;
            try {
                return AnxiousMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (Exception e) {
                return MONSTER_ONLY;
            }
        }

        boolean matches(Entity entity, Set<EntityType> list) {
            return switch (this) {
                case MONSTER_ONLY -> entity instanceof Monster;
                case LIST_ONLY -> list.contains(entity.getType());
                case MONSTER_PLUS_LIST -> entity instanceof Monster || list.contains(entity.getType());
            };
        }
    }
}
