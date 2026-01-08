package ru.letopis;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import ru.letopis.config.LetopisConfig;
import ru.letopis.model.JournalEntry;
import ru.letopis.model.PlayerMeta;
import ru.letopis.model.Scale;
import ru.letopis.model.WorldState;
import ru.letopis.storage.StorageService;
import ru.letopis.util.ChunkPenalty;
import ru.letopis.util.CooldownTracker;
import ru.letopis.util.ItemFactory;
import ru.letopis.util.RateLimiter;

import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

public final class LetopisManager implements Listener {
    private final JavaPlugin plugin;
    private final StorageService storage;
    private final LetopisConfig config;
    private FileConfiguration messages;

    private final Map<String, WorldState> worldStates = new HashMap<>();
    private final Map<String, ActiveEvent> activeEvents = new HashMap<>();
    private final Map<String, OmenState> omenStates = new HashMap<>();
    private final Map<UUID, BuffState> buffs = new HashMap<>();

    private final RateLimiter rateLimiter = new RateLimiter();
    private final ChunkPenalty noiseChunkPenalty = new ChunkPenalty();
    private final ChunkPenalty groveChunkPenalty = new ChunkPenalty();
    private final CooldownTracker portalCooldown = new CooldownTracker();
    private final CooldownTracker explosionDeathCooldown = new CooldownTracker();

    private final NamespacedKey eventKey;
    private final NamespacedKey scaleKey;
    private final NamespacedKey typeKey;
    private final NamespacedKey objectiveKey;
    private final ItemFactory itemFactory;

    private BukkitTask tickTask;
    private BukkitTask decayTask;
    private LocalDate lastDailyReset = LocalDate.now();

    public LetopisManager(JavaPlugin plugin, StorageService storage, LetopisConfig config, FileConfiguration messages) {
        this.plugin = plugin;
        this.storage = storage;
        this.config = config;
        this.messages = messages;
        this.eventKey = new NamespacedKey(plugin, "eventId");
        this.scaleKey = new NamespacedKey(plugin, "scale");
        this.typeKey = new NamespacedKey(plugin, "type");
        this.objectiveKey = new NamespacedKey(plugin, "objective");
        this.itemFactory = new ItemFactory(new NamespacedKey(plugin, "seal"), new NamespacedKey(plugin, "trophy"));
    }

    public void start() {
        loadWorldStates();
        startTasks();
    }

    public void stop() {
        if (tickTask != null) tickTask.cancel();
        if (decayTask != null) decayTask.cancel();
        for (ActiveEvent event : activeEvents.values()) {
            event.stop(false);
        }
        activeEvents.clear();
        omenStates.clear();
        saveAll();
    }

    public ItemFactory itemFactory() {
        return itemFactory;
    }

    public void reloadMessages(FileConfiguration messages) {
        this.messages = messages;
    }

    private void loadWorldStates() {
        Map<String, WorldState> loaded = new HashMap<>();
        for (WorldState state : storage.loadWorldStates()) {
            loaded.put(state.world(), state);
        }
        List<World> worlds = resolveWorlds();
        for (World world : worlds) {
            loaded.computeIfAbsent(world.getName(), name -> new WorldState(name));
        }
        worldStates.clear();
        worldStates.putAll(loaded);
        worldStates.values().forEach(storage::saveWorldState);
    }

    private List<World> resolveWorlds() {
        if (config.worlds.size() == 1 && config.worlds.get(0).equalsIgnoreCase("auto")) {
            return Bukkit.getWorlds();
        }
        List<World> worlds = new ArrayList<>();
        for (String name : config.worlds) {
            World world = Bukkit.getWorld(name);
            if (world != null) {
                worlds.add(world);
            }
        }
        return worlds.isEmpty() ? Bukkit.getWorlds() : worlds;
    }

    private void startTasks() {
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 20L, 20L);

        decayTask = new BukkitRunnable() {
            @Override
            public void run() {
                decayTick();
            }
        }.runTaskTimer(plugin, 1200L, 1200L);
    }

    private void tick() {
        long now = Instant.now().getEpochSecond();
        if (!LocalDate.now().equals(lastDailyReset)) {
            storage.resetDailyContrib();
            lastDailyReset = LocalDate.now();
        }
        for (ActiveEvent event : new ArrayList<>(activeEvents.values())) {
            event.tick(now);
        }
        cleanExpiredBuffs();
        updateBossBars();
        checkOmensAndEvents();
    }

    private void decayTick() {
        long now = Instant.now().getEpochSecond();
        for (WorldState state : worldStates.values()) {
            long last = state.lastDecayTs();
            if (last == 0) {
                state.setLastDecayTs(now);
                storage.saveWorldState(state);
                continue;
            }
            long deltaSeconds = now - last;
            if (deltaSeconds < 3600) {
                continue;
            }
            int hours = (int) (deltaSeconds / 3600);
            double decay = config.decayPerHour * hours;
            if (now - state.lastDangerousEventTs() > 1800) {
                decay += config.decayExtraPerHour * hours;
            }
            for (Scale scale : Scale.values()) {
                state.add(scale, -decay, config.thresholdMax);
            }
            state.setLastDecayTs(now);
            storage.saveWorldState(state);
        }
    }

    private void checkOmensAndEvents() {
        for (WorldState state : worldStates.values()) {
            World world = Bukkit.getWorld(state.world());
            if (world == null) continue;
            if (activeEvents.containsKey(state.world())) continue;
            if (state.cooldownUntilTs() != null && Instant.now().getEpochSecond() < state.cooldownUntilTs()) {
                continue;
            }
            Scale hottest = hottestScale(state);
            double value = state.get(hottest);
            if (value >= config.thresholdEvent) {
                startEvent(world, hottest);
                continue;
            }
            if (value >= config.thresholdOmen && !omenStates.containsKey(state.world())) {
                startOmen(world, hottest);
            }
        }
    }

    private Scale hottestScale(WorldState state) {
        Scale hottest = Scale.NOISE;
        double max = -1;
        for (Scale scale : Scale.values()) {
            double value = state.get(scale);
            if (value > max) {
                max = value;
                hottest = scale;
            }
        }
        return hottest;
    }

    private void startOmen(World world, Scale scale) {
        OmenState omen = new OmenState(scale, Instant.now().getEpochSecond());
        omen.bar = Bukkit.createBossBar("", scale.barColor(), BarStyle.SOLID);
        for (Player player : world.getPlayers()) {
            omen.bar.addPlayer(player);
        }
        omenStates.put(world.getName(), omen);
        broadcastOmen(world, scale);
        new BukkitRunnable() {
            int sent = 1;

            @Override
            public void run() {
                if (!omenStates.containsKey(world.getName())) {
                    cancel();
                    return;
                }
                if (sent >= config.omenCount) {
                    cancel();
                    return;
                }
                sent++;
                broadcastOmen(world, scale);
            }
        }.runTaskTimer(plugin, 200L, 200L);

        new BukkitRunnable() {
            @Override
            public void run() {
                OmenState current = omenStates.get(world.getName());
                if (current == null || current.scale != scale) return;
                WorldState state = worldStates.get(world.getName());
                if (state != null && state.get(scale) >= config.thresholdEvent) {
                    startEvent(world, scale);
                } else {
                    omenStates.remove(world.getName());
                    if (omen.bar != null) omen.bar.removeAll();
                    storage.insertJournal(new JournalEntry(Instant.now().getEpochSecond(), world.getName(), "OMEN_END", scale, "{}"));
                }
            }
        }.runTaskLater(plugin, config.omenDelaySeconds * 20L);

        storage.insertJournal(new JournalEntry(Instant.now().getEpochSecond(), world.getName(), "OMEN_START", scale, "{}"));
    }

    private void broadcastOmen(World world, Scale scale) {
        String message = messages.getString("omens." + scale.key(), scale.displayName());
        for (Player player : world.getPlayers()) {
            player.sendMessage(prefix() + message);
            playOmenEffects(player, scale);
        }
    }

    private void playOmenEffects(Player player, Scale scale) {
        Location loc = player.getLocation();
        switch (scale) {
            case NOISE -> {
                player.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 0.8f);
                player.spawnParticle(Particle.CLOUD, loc, 10, 2, 1, 2, 0.01);
            }
            case ASH -> {
                player.spawnParticle(Particle.SMOKE_NORMAL, loc, 15, 2, 1, 2, 0.02);
                player.playSound(loc, Sound.BLOCK_SMOKER_SMOKE, 0.6f, 0.8f);
            }
            case GROVE -> {
                player.spawnParticle(Particle.SPORE_BLOSSOM_AIR, loc, 12, 2, 1, 2, 0.01);
                player.playSound(loc, Sound.BLOCK_GRASS_PLACE, 0.6f, 0.9f);
            }
            case RIFT -> {
                player.spawnParticle(Particle.PORTAL, loc, 20, 1.5, 1, 1.5, 0.1);
                player.playSound(loc, Sound.ENTITY_ENDERMAN_STARE, 0.6f, 1.2f);
            }
        }
    }

    private void startEvent(World world, Scale scale) {
        OmenState omen = omenStates.remove(world.getName());
        if (omen != null && omen.bar != null) {
            omen.bar.removeAll();
        }
        WorldState state = worldStates.get(world.getName());
        if (state == null) return;
        Location center = pickEventLocation(world, scale);
        if (center == null) return;
        ActiveEvent event = new ActiveEvent(scale, world, center, state.get(scale) >= config.thresholdBoss);
        activeEvents.put(world.getName(), event);
        state.setActiveEventId(event.id.toString());
        long endTs = Instant.now().getEpochSecond() + config.eventDuration.getOrDefault(scale, 480);
        state.setEventEndTs(endTs);
        storage.saveWorldState(state);
        storage.insertJournal(new JournalEntry(Instant.now().getEpochSecond(), world.getName(), "EVENT_START", scale,
            "{\"x\":" + center.getBlockX() + ",\"y\":" + center.getBlockY() + ",\"z\":" + center.getBlockZ() + "}"));

        for (Player player : world.getPlayers()) {
            player.sendMessage(prefix() + messages.getString("event.start", "")
                .replace("%scale%", scale.displayName()));
        }
    }

    private Location pickEventLocation(World world, Scale scale) {
        List<Player> players = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            if (player.getGameMode() != GameMode.SPECTATOR) {
                players.add(player);
            }
        }
        if (players.isEmpty()) return null;
        Player anchor = players.get(new Random().nextInt(players.size()));
        Location origin = anchor.getLocation();
        for (int i = 0; i < config.maxLocationAttempts; i++) {
            double distance = 80 + new Random().nextInt(80);
            double angle = Math.random() * Math.PI * 2;
            int x = (int) (origin.getX() + Math.cos(angle) * distance);
            int z = (int) (origin.getZ() + Math.sin(angle) * distance);
            if (origin.getWorld().getSpawnLocation().distanceSquared(new Location(world, x, origin.getY(), z))
                < config.safeDistanceFromSpawn * config.safeDistanceFromSpawn) {
                continue;
            }
            int y = world.getHighestBlockYAt(x, z);
            Block block = world.getBlockAt(x, y - 1, z);
            if (block.getType() == Material.WATER || block.getType() == Material.LAVA) {
                continue;
            }
            Location candidate = new Location(world, x + 0.5, y, z + 0.5);
            return candidate;
        }
        Location spawn = world.getSpawnLocation();
        return spawn.add(new Vector(200, 0, 0));
    }

    private void endEvent(ActiveEvent event, boolean success) {
        WorldState state = worldStates.get(event.world.getName());
        if (state != null) {
            if (success) {
                state.add(event.scale, -120, config.thresholdMax);
                if (event.bossMode) {
                    state.add(event.scale, -250, config.thresholdMax);
                }
            } else {
                state.add(event.scale, 50, config.thresholdMax);
            }
            state.setActiveEventId(null);
            state.setEventEndTs(null);
            state.setCooldownUntilTs(Instant.now().getEpochSecond() + config.globalCooldownMinutesAfterEvent * 60L);
            if (event.scale == Scale.NOISE || event.scale == Scale.ASH || event.scale == Scale.GROVE || event.scale == Scale.RIFT) {
                if (state.get(event.scale) >= config.thresholdEvent) {
                    state.setLastDangerousEventTs(Instant.now().getEpochSecond());
                }
            }
            storage.saveWorldState(state);
        }
        activeEvents.remove(event.world.getName());
        event.stop(success);
        String message = success ? messages.getString("event.success", "") : messages.getString("event.fail", "");
        for (Player player : event.world.getPlayers()) {
            player.sendMessage(prefix() + message);
        }
        if (success) {
            distributeRewards(event);
        } else {
            applyFailureEffects(event);
        }
        storage.insertJournal(new JournalEntry(Instant.now().getEpochSecond(), event.world.getName(),
            success ? "EVENT_SUCCESS" : "EVENT_FAIL", event.scale, "{\"participants\":" + event.participants.size() + "}"));
    }

    private void applyFailureEffects(ActiveEvent event) {
        for (UUID uuid : event.participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            switch (event.scale) {
                case NOISE -> {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 20 * 60, 1));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20 * 60, 0));
                }
                case ASH -> {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 20 * 90, 1));
                }
                case GROVE -> {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 20 * 120, 0));
                }
                case RIFT -> {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 20 * 20, 0));
                }
            }
        }
    }

    private void distributeRewards(ActiveEvent event) {
        List<UUID> participants = new ArrayList<>(event.participants);
        if (participants.isEmpty()) return;
        Collections.shuffle(participants);
        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.getInventory().addItem(itemFactory.createTrophy(event.scale));
            }
        }
        Random random = new Random();
        if (event.bossMode || random.nextDouble() < (event.bossMode ? config.rewardTitleChanceBoss : config.rewardTitleChanceNormal)) {
            UUID winner = participants.get(random.nextInt(participants.size()));
            Player player = Bukkit.getPlayer(winner);
            if (player != null) {
                player.getInventory().addItem(itemFactory.createSeal(event.scale));
                String title = titleForScale(event.scale);
                storage.addPlayerTitle(player.getUniqueId(), title);
                player.sendMessage(prefix() + "§eВы получили титул: §f" + title);
            }
        }
        if (config.rewardBuffEnabled) {
            for (UUID uuid : participants) {
                buffs.put(uuid, new BuffState(config.rewardBuffType, Instant.now().getEpochSecond() + config.rewardBuffDurationMinutes * 60L));
            }
        }
    }

    private String titleForScale(Scale scale) {
        return switch (scale) {
            case NOISE -> "Осколочный свидетель";
            case ASH -> "Пепельный очиститель";
            case GROVE -> "Хранитель чащи";
            case RIFT -> "Стабилизатор разлома";
        };
    }

    private void updateBossBars() {
        for (WorldState state : worldStates.values()) {
            World world = Bukkit.getWorld(state.world());
            if (world == null) continue;
            BossBar bar = null;
            Scale scale = null;
            if (activeEvents.containsKey(state.world())) {
                ActiveEvent event = activeEvents.get(state.world());
                bar = event.bossBar;
                scale = event.scale;
            } else if (omenStates.containsKey(state.world())) {
                OmenState omen = omenStates.get(state.world());
                bar = omen.bar;
                scale = omen.scale;
            }
            if (bar != null && scale != null) {
                double value = state.get(scale);
                bar.setProgress(Math.min(1.0, value / config.thresholdMax));
                String level = value >= config.thresholdBoss ? "критично" : value >= config.thresholdEvent ? "опасно" : "тревожно";
                bar.setTitle(messages.getString("ui.bossbar", "")
                    .replace("%scale_name%", scale.displayName())
                    .replace("%value%", String.format(Locale.US, "%.2f", value))
                    .replace("%max%", String.valueOf(config.thresholdMax))
                    .replace("%level%", level));
            }
        }
    }

    private void cleanExpiredBuffs() {
        long now = Instant.now().getEpochSecond();
        buffs.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
    }

    private void saveAll() {
        for (WorldState state : worldStates.values()) {
            storage.saveWorldState(state);
        }
    }

    private String prefix() {
        return messages.getString("prefix", "");
    }

    public WorldState getWorldState(String world) {
        return worldStates.get(world);
    }

    public Map<UUID, BuffState> buffs() {
        return buffs;
    }

    public LetopisConfig config() {
        return config;
    }

    public FileConfiguration messages() {
        return messages;
    }

    public void reloadAll() {
        if (plugin instanceof LetopisPlugin letopis) {\n            letopis.reloadLetopis();\n        }\n    }

    public StorageService storage() {
        return storage;
    }

    public File dataFolder() {\n        return plugin.getDataFolder();\n    }

    public NamespacedKey eventKey() {
        return eventKey;
    }

    public NamespacedKey scaleKey() {
        return scaleKey;
    }

    public NamespacedKey objectiveKey() {
        return objectiveKey;
    }

    public void forceStartEvent(World world, Scale scale) {
        if (world == null || scale == null) return;
        if (activeEvents.containsKey(world.getName())) return;
        startEvent(world, scale);
    }

    public void stopEvent(World world) {
        if (world == null) return;
        ActiveEvent event = activeEvents.get(world.getName());
        if (event != null) {
            endEvent(event, false);
        }
    }

    public ActiveEvent getActiveEvent(World world) {
        return world == null ? null : activeEvents.get(world.getName());
    }

    public void setScaleValue(World world, Scale scale, double value) {
        WorldState state = worldStates.get(world.getName());
        if (state == null) return;
        state.set(scale, Math.min(config.thresholdMax, Math.max(0, value)));
        storage.saveWorldState(state);
    }

    public void addScaleValue(World world, Scale scale, double delta) {
        WorldState state = worldStates.get(world.getName());
        if (state == null) return;
        state.add(scale, delta, config.thresholdMax);
        storage.saveWorldState(state);
    }

    public void addPoints(Player player, World world, Scale scale, double points) {
        if (world == null) return;
        WorldState state = worldStates.get(world.getName());
        if (state == null) return;
        double allowed = points;
        if (points > 0) {
            allowed = rateLimiter.applyLimit(player == null ? null : player.getUniqueId(), scale, points,
                config.maxPerMinute.getOrDefault(scale, 0.0));
        }
        if (allowed == 0) return;
        state.add(scale, allowed, config.thresholdMax);
        storage.saveWorldState(state);
        if (player != null) {
            storage.addContribution(player.getUniqueId(), world.getName(), scale, allowed);
        }
    }

    private void addPointsWithChunkPenalty(Player player, World world, Scale scale, double points, Chunk chunk, long window, double multiplier) {
        double factor = chunk == null ? 1.0 : noiseChunkPenalty.apply(player.getUniqueId(), world.getName(), chunk.getX(), chunk.getZ(), window, multiplier);
        addPoints(player, world, scale, points * factor);
    }

    private void addPointsWithGrovePenalty(Player player, World world, Scale scale, double points, Chunk chunk, long window, double multiplier) {
        double factor = chunk == null ? 1.0 : groveChunkPenalty.apply(player.getUniqueId(), world.getName(), chunk.getX(), chunk.getZ(), window, multiplier);
        addPoints(player, world, scale, points * factor);
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        World world = entity.getWorld();
        Scale scale = Scale.NOISE;
        double points = 0;
        if (entity instanceof Creeper) {
            points = config.noiseCreeper;
        } else if (entity instanceof TNTPrimed) {
            points = config.noiseTnt;
        } else if (entity instanceof EnderCrystal) {
            points = config.noiseCrystal;
        }
        if (points > 0) {
            Player nearest = nearestPlayer(entity.getLocation());
            addPointsWithChunkPenalty(nearest, world, scale, points, entity.getLocation().getChunk(), config.noiseSameChunkCooldown, config.noiseSameChunkMultiplier);
        }
        if (isEventEntity(entity)) {
            event.blockList().clear();
            event.setYield(0f);
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        World world = block.getWorld();
        double points = 0;
        if (Tag.BEDS.isTagged(type) && (world.getEnvironment() == World.Environment.NETHER || world.getEnvironment() == World.Environment.THE_END)) {
            points = config.noiseBed;
        } else if (type == Material.RESPAWN_ANCHOR) {
            points = config.noiseAnchor;
        }
        if (points > 0) {
            Player nearest = nearestPlayer(block.getLocation());
            addPointsWithChunkPenalty(nearest, world, Scale.NOISE, points, block.getChunk(), config.noiseSameChunkCooldown, config.noiseSameChunkMultiplier);
        }
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() == EntityType.WITHER && event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.BUILD_WITHER) {
            Player nearest = nearestPlayer(event.getLocation());
            addPoints(nearest, event.getLocation().getWorld(), Scale.NOISE, config.noiseWither);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        World world = player.getWorld();
        if (player.getLastDamageCause() != null && player.getLastDamageCause().getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            if (explosionDeathCooldown.isReady(player.getUniqueId(), config.noiseDeathBonusCooldown)) {
                addPoints(player, world, Scale.NOISE, config.noiseDeathBonus);
            }
        }
        if (world.getEnvironment() == World.Environment.NETHER) {
            addPoints(player, world, Scale.RIFT, config.riftDeathNether);
        }
        if (world.getEnvironment() == World.Environment.THE_END) {
            addPoints(player, world, Scale.RIFT, config.riftDeathEnd);
        }
    }

    @EventHandler
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        double points = switch (type) {
            case FURNACE -> config.ashFurnace;
            case BLAST_FURNACE -> config.ashBlast;
            case SMOKER -> config.ashSmoker;
            default -> 0;
        };
        if (points > 0) {
            addPoints(event.getPlayer(), block.getWorld(), Scale.ASH, points * event.getItemAmount());
        }
    }

    @EventHandler
    public void onBlockCook(BlockCookEvent event) {
        Block block = event.getBlock();
        if (!(block.getBlockData() instanceof Campfire)) return;
        Player nearest = nearestPlayer(block.getLocation());
        addPoints(nearest, block.getWorld(), Scale.ASH, config.ashCampfire);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        if (Tag.LOGS.isTagged(type)) {
            addPointsWithGrovePenalty(event.getPlayer(), block.getWorld(), Scale.GROVE, config.groveLog, block.getChunk(), config.groveSameChunkWindow, config.groveSameChunkMultiplier);
        } else if (Tag.LEAVES.isTagged(type)) {
            addPointsWithGrovePenalty(event.getPlayer(), block.getWorld(), Scale.GROVE, config.groveLeaves, block.getChunk(), config.groveSameChunkWindow, config.groveSameChunkMultiplier);
        } else if (Tag.SAPLINGS.isTagged(type)) {
            addPointsWithGrovePenalty(event.getPlayer(), block.getWorld(), Scale.GROVE, config.groveSapling, block.getChunk(), config.groveSameChunkWindow, config.groveSameChunkMultiplier);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (Tag.SAPLINGS.isTagged(event.getBlockPlaced().getType())) {
            addPoints(event.getPlayer(), event.getBlockPlaced().getWorld(), Scale.GROVE, -config.groveSaplingReduction);
        }
    }

    @EventHandler
    public void onIgnite(BlockIgniteEvent event) {
        if (!config.groveTrackIgnite) return;
        Material type = event.getBlock().getType();
        if (Tag.LOGS.isTagged(type) || Tag.LEAVES.isTagged(type)) {
            Player player = event.getPlayer();
            addPoints(player, event.getBlock().getWorld(), Scale.GROVE, config.groveIgnite);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            event.setDroppedExp(applyBuffXp(killer, event.getDroppedExp()));
        }
        if (!config.groveTrackAnimal) return;
        if (event.getEntity() instanceof Animals animal && animal.getKiller() != null) {
            addPoints(animal.getKiller(), animal.getWorld(), Scale.GROVE, config.groveAnimal);
        }
    }

    @EventHandler
    public void onItemDamage(PlayerItemDamageEvent event) {
        applyBuffDamageReduction(event.getPlayer(), event);
    }

    @EventHandler
    public void onPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        if (portalCooldown.isReady(player.getUniqueId(), config.riftPortalCooldownSeconds)) {
            addPoints(player, player.getWorld(), Scale.RIFT, config.riftPortal);
        }
    }

    @EventHandler
    public void onPortalCreate(PortalCreateEvent event) {
        if (!config.riftTrackPortalCreate) return;
        Player player = event.getEntity() instanceof Player p ? p : null;
        addPoints(player, event.getWorld(), Scale.RIFT, config.riftPortalCreate);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        if (!isEventEntity(living)) return;
        Player damager = event.getDamager() instanceof Player p ? p : null;
        if (damager != null) {
            ActiveEvent activeEvent = activeEvents.get(living.getWorld().getName());
            if (activeEvent != null) {
                activeEvent.addParticipant(damager);
            }
        }
    }

    @EventHandler
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand stand)) return;
        ActiveEvent activeEvent = activeEvents.get(event.getPlayer().getWorld().getName());
        if (activeEvent == null) return;
        if (!stand.getPersistentDataContainer().has(eventKey, PersistentDataType.STRING)) return;
        String objective = stand.getPersistentDataContainer().get(objectiveKey, PersistentDataType.STRING);
        if (objective == null) return;
        event.setCancelled(true);
        activeEvent.handleObjectiveInteract(event.getPlayer(), stand, objective, event.getPlayer().getInventory().getItemInMainHand());
    }

    private boolean isEventEntity(Entity entity) {
        PersistentDataContainer container = entity.getPersistentDataContainer();
        return container.has(eventKey, PersistentDataType.STRING);
    }

    private Player nearestPlayer(Location location) {
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Player player : location.getWorld().getPlayers()) {
            double dist = player.getLocation().distanceSquared(location);
            if (dist < best) {
                best = dist;
                nearest = player;
            }
        }
        return nearest;
    }

    private final class OmenState {
        private final Scale scale;
        private final long startTs;
        private BossBar bar;

        private OmenState(Scale scale, long startTs) {
            this.scale = scale;
            this.startTs = startTs;
        }
    }

    public final class ActiveEvent {
        private final UUID id = UUID.randomUUID();
        private final Scale scale;
        private final World world;
        private final Location center;
        private final boolean bossMode;
        private final BossBar bossBar;
        private final Set<UUID> participants = new HashSet<>();
        private final Map<UUID, Integer> stayTicks = new HashMap<>();
        private final Map<UUID, Long> lastFog = new HashMap<>();

        private int waveTimer = 0;
        private int echoTimer = 0;
        private int fogTimer = 0;
        private int blinkTimer = 0;
        private int actionbarTimer = 0;

        private int ashClosed = 0;
        private final List<ArmorStand> ashHearths = new ArrayList<>();
        private final Map<UUID, Integer> ashProgress = new HashMap<>();

        private int groveSeals = 3;
        private int groveProgress = 0;
        private ArmorStand groveHeart;

        private int riftClosed = 0;
        private final List<ArmorStand> riftPoints = new ArrayList<>();
        private final Map<UUID, Integer> riftProgress = new HashMap<>();

        private LivingEntity boss;
        private final long endTs;

        private ActiveEvent(Scale scale, World world, Location center, boolean bossMode) {
            this.scale = scale;
            this.world = world;
            this.center = center;
            this.bossMode = bossMode;
            this.endTs = Instant.now().getEpochSecond() + config.eventDuration.getOrDefault(scale, 480);
            this.bossBar = Bukkit.createBossBar("", scale.barColor(), BarStyle.SOLID);
            for (Player player : world.getPlayers()) {
                bossBar.addPlayer(player);
            }
            if (scale == Scale.ASH) {
                spawnAshHearths();
            }
            if (scale == Scale.GROVE) {
                spawnGroveHeart();
            }
            if (scale == Scale.RIFT) {
                spawnRiftPoints();
            }
        }

        private void spawnAshHearths() {
            for (int i = 0; i < 3; i++) {
                Location loc = center.clone().add(randomOffset(60), 0, randomOffset(60));
                ArmorStand stand = spawnMarker(loc, "Сажевый очаг");
                stand.getPersistentDataContainer().set(objectiveKey, PersistentDataType.STRING, "ash");
                ashHearths.add(stand);
            }
        }

        private void spawnGroveHeart() {
            groveHeart = spawnMarker(center.clone(), "Сердце чащи");
            groveHeart.getPersistentDataContainer().set(objectiveKey, PersistentDataType.STRING, "grove");
        }

        private void spawnRiftPoints() {
            for (int i = 0; i < 4; i++) {
                Location loc = center.clone().add(randomOffset(60), 0, randomOffset(60));
                ArmorStand stand = spawnMarker(loc, "Трещина");
                stand.getPersistentDataContainer().set(objectiveKey, PersistentDataType.STRING, "rift");
                riftPoints.add(stand);
            }
        }

        private double randomOffset(int radius) {
            return (Math.random() * radius) - (radius / 2.0);
        }

        private ArmorStand spawnMarker(Location loc, String name) {
            Location spawn = new Location(world, loc.getX(), world.getHighestBlockYAt(loc), loc.getZ());
            ArmorStand stand = world.spawn(spawn, ArmorStand.class, entity -> {
                entity.setInvisible(true);
                entity.setMarker(true);
                entity.setCustomNameVisible(true);
                entity.setCustomName(ChatColor.YELLOW + name);
                entity.getPersistentDataContainer().set(eventKey, PersistentDataType.STRING, id.toString());
                entity.getPersistentDataContainer().set(scaleKey, PersistentDataType.STRING, scale.key());
            });
            return stand;
        }

        private void tick(long now) {
            if (now >= endTs) {
                if (isSuccess()) {
                    endEvent(this, true);
                } else {
                    endEvent(this, false);
                }
                return;
            }
            updateParticipants();
            if (scale == Scale.NOISE) {
                handleNoise();
            } else if (scale == Scale.ASH) {
                handleAsh(now);
            } else if (scale == Scale.GROVE) {
                handleGrove();
            } else if (scale == Scale.RIFT) {
                handleRift();
            }
            if (isSuccess()) {
                endEvent(this, true);
            }
        }

        private void updateParticipants() {
            for (Player player : world.getPlayers()) {
                if (!bossBar.getPlayers().contains(player)) {
                    bossBar.addPlayer(player);
                }
                if (player.getLocation().distanceSquared(center) <= config.eventRadius * config.eventRadius) {
                    stayTicks.merge(player.getUniqueId(), 1, Integer::sum);
                    if (stayTicks.get(player.getUniqueId()) >= 60) {
                        participants.add(player.getUniqueId());
                    }
                }
            }
            actionbarTimer++;
            if (actionbarTimer >= 10) {
                actionbarTimer = 0;
                sendActionBar();
            }
        }

        private void sendActionBar() {
            int secondsLeft = (int) (endTs - Instant.now().getEpochSecond());
            String time = String.format("%02d:%02d", secondsLeft / 60, secondsLeft % 60);
            String objective = switch (scale) {
                case NOISE -> "Волны";
                case ASH -> "Очаги: " + ashClosed + "/3";
                case GROVE -> "Печати: " + (3 - groveSeals) + "/3";
                case RIFT -> "Трещины: " + riftClosed + "/4";
            };
            String message = messages.getString("ui.actionbar", "")
                .replace("%time%", time)
                .replace("%objective%", objective)
                .replace("%participants%", String.valueOf(participants.size()));
            for (Player player : world.getPlayers()) {
                player.sendActionBar(message);
            }
        }

        private void handleNoise() {
            waveTimer++;
            echoTimer++;
            if (waveTimer >= 90) {
                waveTimer = 0;
                spawnWave(EntityType.ZOMBIE, 6);
            }
            if (echoTimer >= 45) {
                echoTimer = 0;
                doEchoDamage();
            }
            if (bossMode && boss == null && endTs - Instant.now().getEpochSecond() <= 60) {
                boss = spawnBoss(EntityType.WITHER_SKELETON, "Осколочный узел");
            }
        }

        private void handleAsh(long now) {
            fogTimer++;
            if (fogTimer >= 60) {
                fogTimer = 0;
                applyFog();
            }
            if (bossMode && boss == null && ashClosed >= 2) {
                boss = spawnBoss(EntityType.IRON_GOLEM, "Пепельный голем");
            }
        }

        private void handleGrove() {
            waveTimer++;
            if (waveTimer >= 90) {
                waveTimer = 0;
                spawnWave(EntityType.WOLF, 4);
            }
            if (groveHeart != null) {
                int nearby = 0;
                for (Player player : world.getPlayers()) {
                    if (player.getLocation().distanceSquared(groveHeart.getLocation()) <= 9) {
                        nearby++;
                    }
                }
                if (nearby >= 2) {
                    groveProgress++;
                    if (groveProgress >= 10) {
                        groveProgress = 0;
                        groveSeals = Math.max(0, groveSeals - 1);
                        world.spawnParticle(Particle.HAPPY_VILLAGER, groveHeart.getLocation(), 20, 0.5, 0.5, 0.5, 0.05);
                        if (bossMode && boss == null && groveSeals <= 1) {
                            boss = spawnBoss(EntityType.IRON_GOLEM, "Страж чащи");
                        }
                        if (groveSeals == 0) {
                            world.playSound(groveHeart.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1f);
                        }
                    }
                } else {
                    groveProgress = 0;
                }
            }
        }

        private void handleRift() {
            blinkTimer++;
            if (blinkTimer >= 90) {
                blinkTimer = 0;
                blinkPlayers();
            }
            if (bossMode && boss == null && riftClosed >= 2) {
                boss = spawnBoss(EntityType.ENDERMAN, "Искатель разлома");
            }
        }

        private void spawnWave(EntityType type, int baseCount) {
            int count = baseCount + config.scalingWaveAdd * Math.max(0, participants.size() - 1);
            for (int i = 0; i < count; i++) {
                Location loc = center.clone().add(randomOffset(10), 0, randomOffset(10));
                world.spawnEntity(loc, type, SpawnReason.CUSTOM, entity -> {
                    if (entity instanceof LivingEntity living) {
                        applyScaling(living);
                        tagEntity(living, "wave");
                    }
                });
            }
        }

        private void doEchoDamage() {
            List<Player> inZone = new ArrayList<>();
            for (Player player : world.getPlayers()) {
                if (player.getLocation().distanceSquared(center) <= config.eventRadius * config.eventRadius) {
                    inZone.add(player);
                }
            }
            if (inZone.isEmpty()) return;
            Player target = inZone.get(new Random().nextInt(inZone.size()));
            Location loc = target.getLocation();
            world.spawnParticle(Particle.EXPLOSION_LARGE, loc, 2, 0.5, 0.2, 0.5, 0);
            world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.4f);
            for (Player player : inZone) {
                if (player.getLocation().distanceSquared(loc) <= 36) {
                    double damage = 4.0;
                    if (isStacked(player, inZone)) {
                        damage *= 1.15;
                    }
                    player.damage(damage);
                }
            }
        }

        private boolean isStacked(Player player, List<Player> players) {
            for (Player other : players) {
                if (!other.equals(player) && other.getLocation().distanceSquared(player.getLocation()) <= 4) {
                    return true;
                }
            }
            return false;
        }

        private void applyFog() {
            for (Player player : world.getPlayers()) {
                long now = Instant.now().getEpochSecond();
                long last = lastFog.getOrDefault(player.getUniqueId(), 0L);
                if (now - last < 45) continue;
                if (player.getLocation().distanceSquared(center) > config.eventRadius * config.eventRadius) continue;
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 40, 0));
                lastFog.put(player.getUniqueId(), now);
            }
        }

        private void blinkPlayers() {
            for (Player player : world.getPlayers()) {
                if (player.getLocation().distanceSquared(center) > config.eventRadius * config.eventRadius) continue;
                Vector offset = new Vector((Math.random() * 8) - 4, 0, (Math.random() * 8) - 4);
                Location target = player.getLocation().clone().add(offset);
                if (isSafeBlink(target)) {
                    player.teleport(target);
                    player.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.2f);
                }
            }
        }

        private boolean isSafeBlink(Location target) {
            Block feet = target.getBlock();
            Block head = feet.getRelative(0, 1, 0);
            Block below = feet.getRelative(0, -1, 0);
            if (feet.getType().isSolid() || head.getType().isSolid()) return false;
            if (!below.getType().isSolid()) return false;
            return below.getType() != Material.LAVA;
        }

        private LivingEntity spawnBoss(EntityType type, String name) {
            Location loc = center.clone();
            return (LivingEntity) world.spawnEntity(loc, type, SpawnReason.CUSTOM, entity -> {
                if (entity instanceof LivingEntity living) {
                    living.setCustomName(ChatColor.GOLD + name);
                    living.setCustomNameVisible(true);
                    applyScaling(living);
                    tagEntity(living, "boss");
                }
            });
        }

        private void applyScaling(LivingEntity entity) {
            int participantsCount = Math.max(1, participants.size());
            double hpMultiplier = 1 + config.scalingHp * (participantsCount - 1);
            double dmgMultiplier = 1 + config.scalingDmg * (participantsCount - 1);
            if (entity.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
                entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue() * hpMultiplier);
                entity.setHealth(entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue());
            }
            if (entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
                entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).getBaseValue() * dmgMultiplier);
            }
        }

        private void tagEntity(LivingEntity entity, String type) {
            PersistentDataContainer container = entity.getPersistentDataContainer();
            container.set(eventKey, PersistentDataType.STRING, id.toString());
            container.set(scaleKey, PersistentDataType.STRING, scale.key());
            container.set(typeKey, PersistentDataType.STRING, type);
        }

        private void stop(boolean success) {
            bossBar.removeAll();
            for (ArmorStand stand : ashHearths) stand.remove();
            for (ArmorStand stand : riftPoints) stand.remove();
            if (groveHeart != null) groveHeart.remove();
            if (boss != null) boss.remove();
        }

        private boolean isSuccess() {
            return switch (scale) {
                case NOISE -> !bossMode || (boss != null && boss.isDead());
                case ASH -> ashClosed >= 3 && (!bossMode || (boss != null && boss.isDead()));
                case GROVE -> groveSeals <= 0 && (!bossMode || (boss != null && boss.isDead()));
                case RIFT -> riftClosed >= 4 && (!bossMode || (boss != null && boss.isDead()));
            };
        }

        private void handleObjectiveInteract(Player player, ArmorStand stand, String objective, ItemStack hand) {
            if (objective.equals("ash")) {
                if (hand.getType() == Material.WATER_BUCKET || hand.getType() == Material.SAND) {
                    ashProgress.merge(player.getUniqueId(), 1, Integer::sum);
                    participants.add(player.getUniqueId());
                    if (ashProgress.get(player.getUniqueId()) >= 5) {
                        ashClosed = Math.min(3, ashClosed + 1);
                        stand.remove();
                        ashProgress.remove(player.getUniqueId());
                        world.playSound(stand.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1f, 1f);
                    }
                }
            }
            if (objective.equals("rift")) {
                if (hand.getType() == Material.ENDER_PEARL || hand.getType() == Material.OBSIDIAN) {
                    riftProgress.merge(player.getUniqueId(), 1, Integer::sum);
                    participants.add(player.getUniqueId());
                    if (riftProgress.get(player.getUniqueId()) >= 3) {
                        riftClosed = Math.min(4, riftClosed + 1);
                        stand.remove();
                        riftProgress.remove(player.getUniqueId());
                        world.playSound(stand.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 1f);
                    }
                }
            }
        }

        private void addParticipant(Player player) {
            participants.add(player.getUniqueId());
        }
    }

    public record BuffState(String type, long expiresAt) {}

    public void applyBuffDamageReduction(Player player, PlayerItemDamageEvent event) {
        BuffState buff = buffs.get(player.getUniqueId());
        if (buff == null || !buff.type.equalsIgnoreCase("DURABILITY_REDUCTION")) return;
        if (Math.random() < config.rewardBuffValue) {
            event.setCancelled(true);
        }
    }

    public int applyBuffXp(Player player, int exp) {
        BuffState buff = buffs.get(player.getUniqueId());
        if (buff == null || !buff.type.equalsIgnoreCase("XP_BOOST")) return exp;
        return exp + (int) Math.round(exp * config.rewardBuffValue);
    }
}
