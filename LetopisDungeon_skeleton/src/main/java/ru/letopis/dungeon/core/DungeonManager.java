package ru.letopis.dungeon.core;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import ru.letopis.dungeon.LetopisDungeonPlugin;
import ru.letopis.dungeon.model.*;
import ru.letopis.dungeon.room.*;
import ru.letopis.dungeon.theme.Theme;
import ru.letopis.dungeon.theme.WeightedMaterial;
import ru.letopis.dungeon.ui.UIService;
import ru.letopis.dungeon.util.SafeTeleport;

import java.util.*;

public final class DungeonManager {

    private final LetopisDungeonPlugin plugin;
    private final DungeonWorldService worldService;
    private final DungeonBuilder builder;
    private final UIService ui;
    private final Map<UUID, PlayerReturnPoint> returnPoints = new HashMap<>();
    private final Random random = new Random();

    private final Session session = new Session();
    private final List<Room> rooms = new ArrayList<>();
    private final List<RoomContext> roomContexts = new ArrayList<>();
    private final List<Gate> gates = new ArrayList<>();

    private final NamespacedKey sessionKey;
    private final NamespacedKey tagKey;

    private SessionTicker ticker;
    private int sessionIndex = 0;

    public DungeonManager(LetopisDungeonPlugin plugin) {
        this.plugin = plugin;
        this.worldService = new DungeonWorldService(plugin);
        this.builder = new DungeonBuilder(plugin);
        this.ui = new UIService(plugin);
        this.sessionKey = new NamespacedKey(plugin, "session_id");
        this.tagKey = new NamespacedKey(plugin, "dungeon_tag");
    }

    public void reload() {
        worldService.ensureWorld();
    }

    public void shutdown() {
        if (ticker != null) ticker.cancel();
        ui.clearAll();
    }

    public World ensureDungeonWorld() {
        World w = worldService.ensureWorld();
        if (ticker == null) {
            ticker = new SessionTicker(plugin, this);
            ticker.start();
        }
        return w;
    }

    public boolean isDungeonWorld(World w) {
        return worldService.isDungeonWorld(w);
    }

    public Session session() { return session; }

    public void join(Player p) {
        if (session.state() != SessionState.RUNNING) {
            p.sendMessage(plugin.msg().prefix() + plugin.msg().get("commands.noActiveSession"));
            return;
        }
        session.markParticipant(p.getUniqueId());
        returnPoints.putIfAbsent(p.getUniqueId(), PlayerReturnPoint.fromPlayer(p));
        if (session.startMode() == StartMode.OVERWORLD_ENTRY && session.entryLocation() != null) {
            SafeTeleport.teleport(p, session.entryLocation());
            p.sendMessage(plugin.msg().prefix() + plugin.msg().get("session.entryHint"));
            return;
        }
        SafeTeleport.teleport(p, session.roomStartLocation());
        p.sendMessage(plugin.msg().prefix() + plugin.msg().get("session.joined"));
        ui.show(p, session);
    }

    public void leave(Player p) {
        var rp = returnPoints.remove(p.getUniqueId());
        if (rp != null) SafeTeleport.teleport(p, rp.toLocation());
        session.markInSession(p.getUniqueId(), false);
        session.markAlive(p.getUniqueId(), false);
        ui.clear(p);
    }

    public void startRun(CommandSender initiator, StartMode forcedMode) {
        ensureDungeonWorld();
        if (session.state() == SessionState.RUNNING) {
            initiator.sendMessage(plugin.msg().prefix() + plugin.msg().get("session.alreadyRunning"));
            return;
        }
        Player leader = initiator instanceof Player player ? player : null;
        StartMode mode = forcedMode != null ? forcedMode : (random.nextBoolean() ? StartMode.OVERWORLD_ENTRY : StartMode.DIRECT_DIMENSION);
        if (leader == null && mode == StartMode.OVERWORLD_ENTRY) mode = StartMode.DIRECT_DIMENSION;

        session.director().newPlan();
        long seed = random.nextLong();
        Theme theme = pickTheme();
        SessionRegion region = allocateRegion();
        Location roomStart = buildDungeon(region, theme, seed);
        session.startNew(UUID.randomUUID(), leader != null ? leader.getUniqueId() : null, mode, region, roomStart);
        session.setSeed(seed);
        session.setTheme(theme);

        plugin.getLogger().info("Dungeon session started: " + session.sessionId() + " mode=" + mode + " region=" + region.minX() + "," + region.minZ());

        if (leader != null) {
            session.markParticipant(leader.getUniqueId());
            returnPoints.put(leader.getUniqueId(), PlayerReturnPoint.fromPlayer(leader));
        }

        if (mode == StartMode.OVERWORLD_ENTRY && leader != null) {
            Location entry = buildOverworldEntry(leader.getLocation());
            session.setEntryLocation(entry);
            leader.sendMessage(plugin.msg().prefix() + plugin.msg().get("session.entryCreated"));
        } else {
            if (leader != null) {
                SafeTeleport.teleport(leader, roomStart);
                leader.sendMessage(plugin.msg().prefix() + plugin.msg().get("session.teleported"));
                ui.show(leader, session);
            }
        }
        startRoom(0);
    }

    public void stopRun(CommandSender initiator) {
        if (session.state() == SessionState.IDLE) {
            initiator.sendMessage(plugin.msg().prefix() + plugin.msg().get("session.none"));
            return;
        }
        endSession(false, plugin.msg().get("session.stopped"));
    }

    public void tickSession() {
        if (session.state() != SessionState.RUNNING) return;
        session.tick(1);
        updateParticipants();

        Room current = currentRoom();
        if (current != null) {
            current.tick(roomContexts.get(session.currentRoomIndex()));
            updateRoomInfo(current);
            if (current.isComplete()) {
                moveToNextRoom();
            }
        }

        checkGateClosure();

        if (session.isTimeOver(plugin.getConfig().getInt("dungeon.session.maxMinutes", 25))) {
            endSession(false, plugin.msg().get("session.timeOver"));
        }
        if (session.aliveCount() == 0) {
            endSession(false, plugin.msg().get("session.wipe"));
        }

        for (Player p : worldService.world().getPlayers()) {
            if (session.region().contains(p.getLocation())) ui.update(p, session);
        }
    }

    private void updateParticipants() {
        World world = worldService.world();
        if (world == null) return;
        Set<UUID> active = new HashSet<>();
        for (Player p : world.getPlayers()) {
            if (!session.region().contains(p.getLocation())) continue;
            boolean inSession = p.getGameMode() != GameMode.SPECTATOR && !p.isDead();
            session.markParticipant(p.getUniqueId());
            session.markInSession(p.getUniqueId(), inSession);
            session.markAlive(p.getUniqueId(), inSession);
            session.updateLocation(p.getUniqueId(), p.getLocation());
            session.updateLastSeen(p.getUniqueId(), System.currentTimeMillis());
            active.add(p.getUniqueId());
        }
        for (UUID id : session.participants().keySet()) {
            if (!active.contains(id)) session.markInSession(id, false);
        }
        if (plugin.getConfig().getBoolean("dungeon.debug.participants", false)) {
            plugin.getLogger().info("Participants active=" + session.participantCount() + " alive=" + session.aliveCount());
        }
    }

    private SessionRegion allocateRegion() {
        World world = worldService.world();
        int areaSize = plugin.getConfig().getInt("dungeon.session.areaSize", 160);
        int areaHeight = plugin.getConfig().getInt("dungeon.session.areaHeight", 40);
        int gridStep = plugin.getConfig().getInt("dungeon.session.gridStep", 256);
        int baseY = plugin.getConfig().getInt("dungeon.session.baseY", 80);
        int offsetX = sessionIndex * gridStep;
        sessionIndex++;
        return new SessionRegion(world, offsetX, offsetX + areaSize - 1, baseY, baseY + areaHeight - 1, 0, areaSize - 1);
    }

    private Location buildDungeon(SessionRegion region, Theme theme, long seed) {
        World world = region.world();
        builder.clearArea(world, region.minX(), region.minY(), region.minZ(), region.maxX(), region.maxY(), region.maxZ());
        builder.buildRegionShell(region, Material.BARRIER, plugin.getConfig().getInt("dungeon.build.batchSize", 300));

        rooms.clear();
        roomContexts.clear();
        gates.clear();

        int sizeX = plugin.getConfig().getInt("dungeon.rooms.sizeX", 30);
        int sizeY = plugin.getConfig().getInt("dungeon.rooms.sizeY", 10);
        int sizeZ = plugin.getConfig().getInt("dungeon.rooms.sizeZ", 30);
        int corridorLength = plugin.getConfig().getInt("dungeon.rooms.corridorLength", 6);

        int startX = region.minX() + 6;
        int startZ = region.minZ() + 6;
        int y = region.minY();

        ConfigurationSection waveConfig = plugin.getConfig().getConfigurationSection("dungeon.waves");
        ConfigurationSection bossConfig = plugin.getConfig().getConfigurationSection("dungeon.boss");

        rooms.add(new EntranceRoom(builder, plugin.msg().get("rooms.prep")));
        rooms.add(new WaveRoom(builder, plugin.msg().get("rooms.waves1"), plugin.msg().get("rooms.wavesObjective"), waveConfig, plugin.getConfig().getInt("dungeon.waves.room1Waves", 3)));
        rooms.add(new PuzzleRoom(builder, plugin.msg().get("rooms.puzzle"), plugin.getConfig().getInt("dungeon.puzzle.levers", 4), plugin.getConfig().getInt("dungeon.puzzle.penaltySeconds", 3)));
        rooms.add(new WaveRoom(builder, plugin.msg().get("rooms.waves2"), plugin.msg().get("rooms.wavesObjective"), waveConfig, plugin.getConfig().getInt("dungeon.waves.room2Waves", 4)));
        rooms.add(new BossRoom(builder, plugin.msg().get("rooms.boss"), bossConfig));

        int x = startX;
        for (int i = 0; i < rooms.size(); i++) {
            Location origin = new Location(world, x, y, startZ);
            Room room = rooms.get(i);
            RoomLayout layout = pickLayout(i, seed);
            RoomContext ctx = new RoomContext(plugin, this, worldService, session, world, origin, sizeX, sizeY, sizeZ,
                    theme, layout, new Random(seed + i * 31L));
            room.build(ctx);
            roomContexts.add(ctx);
            if (i < rooms.size() - 1) {
                Location corridorOrigin = new Location(world, x + sizeX, y, startZ + sizeZ / 2);
                builder.buildCorridor(world, corridorOrigin, corridorLength, sizeY, 5, theme, true);
                Gate gate = buildGate(world, corridorOrigin, sizeY, 5, theme);
                gates.add(gate);
            }
            x += sizeX + corridorLength;
        }

        Location start = new Location(world, startX + sizeX / 2.0, y + 1, startZ + sizeZ / 2.0);
        return start;
    }

    private void startRoom(int index) {
        if (index < 0 || index >= rooms.size()) return;
        session.setCurrentRoomIndex(index);
        Room room = rooms.get(index);
        room.start(roomContexts.get(index));
        updateRoomInfo(room);
        plugin.getLogger().info("Room started: " + room.name() + " index=" + index);
    }

    private void moveToNextRoom() {
        int nextIndex = session.currentRoomIndex() + 1;
        Room current = currentRoom();
        if (current != null) current.cleanup(roomContexts.get(session.currentRoomIndex()));
        if (nextIndex >= rooms.size()) {
            endSession(true, plugin.msg().get("session.completed"));
            return;
        }
        startRoom(nextIndex);
    }

    private void updateRoomInfo(Room room) {
        int wave = 0;
        int waveTotal = 0;
        if (room instanceof WaveRoom waveRoom) {
            wave = waveRoom.currentWave();
            waveTotal = waveRoom.wavesTotal();
        }
        session.setRoomInfo(room.name(), room.objective(), room.progress(), wave, waveTotal);
    }

    public void handleInteract(PlayerInteractEvent event) {
        if (session.state() != SessionState.RUNNING) return;
        if (event.getClickedBlock() == null) return;
        Block clicked = event.getClickedBlock();
        if (session.entryLocation() != null && clicked.getLocation().equals(session.entryLocation()) && event.getPlayer() != null) {
            Player player = event.getPlayer();
            if (!returnPoints.containsKey(player.getUniqueId())) {
                returnPoints.put(player.getUniqueId(), PlayerReturnPoint.fromPlayer(player));
            }
            session.markParticipant(player.getUniqueId());
            SafeTeleport.teleport(player, session.roomStartLocation());
            player.sendMessage(plugin.msg().prefix() + plugin.msg().get("session.teleported"));
            ui.show(player, session);
            removeOverworldEntry(true);
            return;
        }

        Room room = currentRoom();
        if (room != null) {
            room.onPlayerInteract(roomContexts.get(session.currentRoomIndex()), event);
        }
    }

    public void onPlayerDeath(Player player) {
        if (session.state() != SessionState.RUNNING) return;
        if (!isDungeonWorld(player.getWorld())) return;
        session.markAlive(player.getUniqueId(), false);
        session.markInSession(player.getUniqueId(), false);
        player.sendMessage(plugin.msg().prefix() + plugin.msg().get("session.playerDown"));
        scheduleWipeCheck();
    }

    public void onPlayerQuit(Player player) {
        if (session.state() != SessionState.RUNNING) return;
        session.markInSession(player.getUniqueId(), false);
        session.markAlive(player.getUniqueId(), false);
        scheduleWipeCheck();
    }

    public void onPlayerChangedWorld(Player player) {
        if (session.state() != SessionState.RUNNING) return;
        if (!isDungeonWorld(player.getWorld())) {
            session.markInSession(player.getUniqueId(), false);
            session.markAlive(player.getUniqueId(), false);
        }
    }

    public void onPlayerRespawn(Player player) {
        if (session.state() != SessionState.RUNNING) return;
        if (!isDungeonWorld(player.getWorld())) return;
        boolean returnToLobby = plugin.getConfig().getBoolean("dungeon.session.respawn.returnToLobby", false);
        var rp = returnPoints.get(player.getUniqueId());
        if (returnToLobby) {
            if (rp != null) SafeTeleport.teleport(player, rp.toLocation());
            player.sendMessage(plugin.msg().prefix() + plugin.msg().get("session.respawned"));
        } else if (rp != null) {
            SafeTeleport.teleport(player, rp.toLocation());
        }
        session.markInSession(player.getUniqueId(), false);
        session.markAlive(player.getUniqueId(), false);
        scheduleWipeCheck();
    }

    public void announceWave(Session session, int wave, int total, int mobs) {
        broadcastToDungeon(plugin.msg().prefix() + plugin.msg().get("session.waveStarted", Map.of(
                "wave", Integer.toString(wave),
                "total", Integer.toString(total),
                "mobs", Integer.toString(mobs)
        )));
    }

    public void announcePuzzle(Session session, List<Integer> order) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < order.size(); i++) {
            builder.append(order.get(i) + 1);
            if (i < order.size() - 1) builder.append("-");
        }
        broadcastToDungeon(plugin.msg().prefix() + plugin.msg().get("session.puzzleHint", Map.of("order", builder.toString())));
    }

    public void announcePuzzleStep(Session session, int step, int total) {
        broadcastToDungeon(plugin.msg().prefix() + plugin.msg().get("session.puzzleProgress", Map.of(
                "step", Integer.toString(step),
                "total", Integer.toString(total)
        )));
    }

    public void announcePuzzleFailed(Session session) {
        broadcastToDungeon(plugin.msg().prefix() + plugin.msg().get("session.puzzleFailed"));
    }

    public void announcePuzzleSolved(Session session) {
        broadcastToDungeon(plugin.msg().prefix() + plugin.msg().get("session.puzzleSolved"));
    }

    public void announceBossPhase(Session session) {
        broadcastToDungeon(plugin.msg().prefix() + plugin.msg().get("session.bossPhase"));
    }

    public Component bossName() {
        return Component.text(plugin.msg().get("boss.name"));
    }

    public void tagEntity(Entity entity, Session session, String tag) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        data.set(sessionKey, PersistentDataType.STRING, session.sessionId().toString());
        data.set(tagKey, PersistentDataType.STRING, tag);
    }

    public int countTaggedEntities(Session session, String tag) {
        int count = 0;
        if (worldService.world() == null) return 0;
        for (Entity e : worldService.world().getEntities()) {
            PersistentDataContainer data = e.getPersistentDataContainer();
            String sessionId = data.get(sessionKey, PersistentDataType.STRING);
            String tagValue = data.get(tagKey, PersistentDataType.STRING);
            if (session.sessionId() != null && session.sessionId().toString().equals(sessionId) && tag.equals(tagValue)) {
                count++;
            }
        }
        return count;
    }

    public void broadcastToDungeon(String msg) {
        World world = worldService.world();
        if (world == null) return;
        for (Player p : world.getPlayers()) p.sendMessage(msg);
    }

    public void endSession(boolean success, String reason) {
        if (session.state() != SessionState.RUNNING) return;
        session.finish();
        broadcastToDungeon(plugin.msg().prefix() + reason);
        if (success) grantRewards();
        cleanupSession();
        plugin.getLogger().info("Dungeon session ended: " + session.sessionId() + " success=" + success);
    }

    private void cleanupSession() {
        clearEntities();
        for (UUID id : session.participants().keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) leave(p);
        }
        ui.clearAll();
        session.resetParticipants();
        removeOverworldEntry(true);
        gates.clear();
        rooms.clear();
        roomContexts.clear();
        if (session.region() != null) {
            builder.clearAreaBatch(session.region().world(), session.region().minX(), session.region().minY(), session.region().minZ(),
                    session.region().maxX(), session.region().maxY(), session.region().maxZ(),
                    plugin.getConfig().getInt("dungeon.build.batchSize", 300));
        }
    }

    private void clearEntities() {
        if (worldService.world() == null) return;
        for (Entity e : worldService.world().getEntities()) {
            PersistentDataContainer data = e.getPersistentDataContainer();
            String sessionId = data.get(sessionKey, PersistentDataType.STRING);
            if (session.sessionId() != null && session.sessionId().toString().equals(sessionId)) {
                e.remove();
            }
        }
    }

    private void grantRewards() {
        ConfigurationSection rewards = plugin.getConfig().getConfigurationSection("rewards");
        if (rewards == null) return;
        int min = rewards.getInt("countMin", 1);
        int max = rewards.getInt("countMax", 3);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) (List<?>) rewards.getMapList("items");
        if (items.isEmpty()) return;

        for (UUID id : session.participants().keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            int rolls = min + random.nextInt(Math.max(1, max - min + 1));
            for (int i = 0; i < rolls; i++) {
                var item = RewardPicker.pickItem(items, random);
                if (item != null) player.getInventory().addItem(item);
            }
            player.sendMessage(plugin.msg().prefix() + plugin.msg().get("rewards.received"));
        }
    }

    public void debugSkipToRoom(CommandSender sender, String type) {
        if (session.state() != SessionState.RUNNING) {
            sender.sendMessage(plugin.msg().prefix() + plugin.msg().get("session.none"));
            return;
        }
        int target = switch (type.toLowerCase()) {
            case "prep" -> 0;
            case "waves1" -> 1;
            case "puzzle" -> 2;
            case "waves2" -> 3;
            case "boss" -> 4;
            default -> -1;
        };
        if (target < 0 || target >= rooms.size()) {
            sender.sendMessage(plugin.msg().prefix() + plugin.msg().get("debug.unknownRoom"));
            return;
        }
        startRoom(target);
        sender.sendMessage(plugin.msg().prefix() + plugin.msg().get("debug.roomSkipped"));
    }

    public void debugSpawnWave(CommandSender sender) {
        Room room = currentRoom();
        if (room instanceof WaveRoom waveRoom) {
            waveRoom.start(roomContexts.get(session.currentRoomIndex()));
            sender.sendMessage(plugin.msg().prefix() + plugin.msg().get("debug.wave"));
        } else {
            sender.sendMessage(plugin.msg().prefix() + plugin.msg().get("debug.notWave"));
        }
    }

    public void debugSpawnBoss(CommandSender sender) {
        Room room = currentRoom();
        if (room instanceof BossRoom bossRoom) {
            bossRoom.start(roomContexts.get(session.currentRoomIndex()));
            sender.sendMessage(plugin.msg().prefix() + plugin.msg().get("debug.boss"));
        } else {
            sender.sendMessage(plugin.msg().prefix() + plugin.msg().get("debug.notBoss"));
        }
    }

    public List<String> statusLines() {
        List<String> lines = new ArrayList<>();
        lines.add(plugin.msg().prefix() + plugin.msg().get("status.header", Map.of(
                "id", session.sessionId() == null ? "-" : session.sessionId().toString(),
                "mode", session.startMode().name(),
                "state", session.state().name(),
                "room", session.currentRoomName()
        )));
        lines.add(plugin.msg().prefix() + plugin.msg().get("status.progress", Map.of(
                "wave", Integer.toString(session.currentWave()),
                "waveTotal", Integer.toString(session.currentWaveTotal()),
                "alive", Integer.toString(session.aliveCount())
        )));

        for (var entry : session.participants().entrySet()) {
            String name = Optional.ofNullable(Bukkit.getPlayer(entry.getKey())).map(Player::getName).orElse(entry.getKey().toString());
            SessionParticipant p = entry.getValue();
            String loc = p.lastKnownLocation() == null ? "?" : p.lastKnownLocation().getWorld().getName() +
                    " " + p.lastKnownLocation().getBlockX() + " " + p.lastKnownLocation().getBlockY() + " " + p.lastKnownLocation().getBlockZ();
            lines.add(plugin.msg().prefix() + plugin.msg().get("status.player", Map.of(
                    "name", name,
                    "alive", p.isAlive() ? plugin.msg().get("status.alive") : plugin.msg().get("status.dead"),
                    "active", p.isInSession() ? plugin.msg().get("status.in") : plugin.msg().get("status.out"),
                    "loc", loc
            )));
        }
        lines.add(plugin.msg().prefix() + plugin.msg().get("status.mobs", Map.of(
                "mobs", Integer.toString(countTaggedEntities(session, "wave"))
        )));
        return lines;
    }

    private Room currentRoom() {
        if (rooms.isEmpty()) return null;
        int index = session.currentRoomIndex();
        if (index < 0 || index >= rooms.size()) return null;
        return rooms.get(index);
    }

    private Location buildOverworldEntry(Location near) {
        World world = near.getWorld();
        Location base = near.clone().add(3, 0, 3);
        int x = base.getBlockX();
        int y = base.getBlockY();
        int z = base.getBlockZ();
        EntranceStructure structure = new EntranceStructure();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                setAndTrack(world, x + dx, y, z + dz, Material.SMOOTH_STONE, structure);
                setAndTrack(world, x + dx, y + 1, z + dz, Material.AIR, structure);
            }
        }
        Block entryBlock = world.getBlockAt(x, y + 1, z);
        entryBlock.setType(Material.EMERALD_BLOCK, false);
        structure.add(new BlockPos(world, x, y + 1, z));
        session.setEntranceStructure(structure);
        return entryBlock.getLocation();
    }

    private void removeOverworldEntry(boolean playEffects) {
        EntranceStructure structure = session.entranceStructure();
        if (structure == null || structure.isEmpty()) return;
        builder.applyBatch(structure.placedBlocks(), Material.AIR, plugin.getConfig().getInt("dungeon.build.batchSize", 300));
        session.setEntranceStructure(null);
        if (playEffects && session.entryLocation() != null) {
            World world = session.entryLocation().getWorld();
            if (world != null) {
                world.playSound(session.entryLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.8f, 1.2f);
                world.spawnParticle(Particle.PORTAL, session.entryLocation().clone().add(0.5, 1, 0.5), 40, 0.4, 0.4, 0.4, 0.01);
            }
        }
        session.setEntryLocation(null);
    }

    private void setAndTrack(World world, int x, int y, int z, Material material, EntranceStructure structure) {
        world.getBlockAt(x, y, z).setType(material, false);
        structure.add(new BlockPos(world, x, y, z));
    }

    private Theme pickTheme() {
        List<String> enabled = plugin.getConfig().getStringList("dungeon.themes.enabled");
        List<Theme> candidates = new ArrayList<>();
        if (enabled.isEmpty()) {
            Collections.addAll(candidates, Theme.values());
        } else {
            for (String name : enabled) {
                try {
                    candidates.add(Theme.valueOf(name.toUpperCase()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        if (candidates.isEmpty()) Collections.addAll(candidates, Theme.values());
        ConfigurationSection weightSection = plugin.getConfig().getConfigurationSection("dungeon.themes.weights");
        Map<String, Object> weights = weightSection != null ? weightSection.getValues(false) : Collections.emptyMap();
        double total = 0.0;
        Map<Theme, Double> weightMap = new HashMap<>();
        for (Theme theme : candidates) {
            double weight = toDouble(weights.getOrDefault(theme.name().toLowerCase(), 1.0));
            weightMap.put(theme, weight);
            total += weight;
        }
        double roll = random.nextDouble() * Math.max(1.0, total);
        double current = 0.0;
        for (var entry : weightMap.entrySet()) {
            current += entry.getValue();
            if (roll <= current) return entry.getKey();
        }
        return candidates.get(0);
    }

    private RoomLayout pickLayout(int index, long seed) {
        RoomLayout[] layouts = RoomLayout.values();
        Random layoutRandom = new Random(seed + index * 97L);
        return layouts[layoutRandom.nextInt(layouts.length)];
    }

    private Gate buildGate(World world, Location corridorOrigin, int height, int width, Theme theme) {
        int ox = corridorOrigin.getBlockX();
        int oy = corridorOrigin.getBlockY();
        int oz = corridorOrigin.getBlockZ();
        int half = width / 2;
        int doorWidth = 3;
        int doorHeight = 4;
        int x = ox + 1;
        int zStart = oz - doorWidth / 2;
        List<BlockPos> blocks = new ArrayList<>();
        for (int y = oy + 1; y <= oy + doorHeight; y++) {
            for (int z = zStart; z < zStart + doorWidth; z++) {
                blocks.add(new BlockPos(world, x, y, z));
            }
        }
        WeightedMaterial gateMat = ru.letopis.dungeon.theme.WeightedPicker.pick(theme.palette().gate(), random);
        Material material = gateMat == null ? Material.BARRIER : gateMat.material();
        return new Gate(world, blocks, material);
    }

    private void checkGateClosure() {
        if (session.currentRoomIndex() <= 0) return;
        int gateIndex = session.currentRoomIndex() - 1;
        if (gateIndex < 0 || gateIndex >= gates.size()) return;
        Gate gate = gates.get(gateIndex);
        if (gate.isClosed()) return;
        RoomContext ctx = roomContexts.get(session.currentRoomIndex());
        for (Player player : gate.world().getPlayers()) {
            if (ctx.contains(player.getLocation())) {
                for (BlockPos pos : gate.blocks()) {
                    gate.world().getBlockAt(pos.x(), pos.y(), pos.z()).setType(gate.material(), false);
                }
                gate.setClosed(true);
                break;
            }
        }
    }

    private void scheduleWipeCheck() {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (session.state() == SessionState.RUNNING && session.aliveCount() == 0) {
                endSession(false, plugin.msg().get("session.wipe"));
            }
        }, 2L);
    }

    private double toDouble(Object value) {
        if (value instanceof Number num) return num.doubleValue();
        try {
            return Double.parseDouble(Objects.toString(value));
        } catch (NumberFormatException e) {
            return 1.0;
        }
    }
}

final class RewardPicker {
    private RewardPicker() {
    }

    public static org.bukkit.inventory.ItemStack pickItem(List<Map<String, Object>> items, Random random) {
        double totalWeight = 0.0;
        for (Map<String, Object> item : items) {
            totalWeight += toDouble(item.getOrDefault("weight", 1.0));
        }
        double roll = random.nextDouble() * totalWeight;
        Map<String, Object> chosen = null;
        double current = 0.0;
        for (Map<String, Object> item : items) {
            current += toDouble(item.getOrDefault("weight", 1.0));
            if (roll <= current) {
                chosen = item;
                break;
            }
        }
        if (chosen == null) return null;
        double chance = toDouble(chosen.getOrDefault("chance", 1.0));
        if (random.nextDouble() > chance) return null;
        String matName = Objects.toString(chosen.getOrDefault("material", "IRON_INGOT"));
        Material material;
        try {
            material = Material.valueOf(matName.toUpperCase());
        } catch (IllegalArgumentException e) {
            material = Material.IRON_INGOT;
        }
        int min = ((Number) chosen.getOrDefault("min", 1)).intValue();
        int max = ((Number) chosen.getOrDefault("max", min)).intValue();
        int amount = min + random.nextInt(Math.max(1, max - min + 1));
        var stack = new org.bukkit.inventory.ItemStack(material, amount);
        if (chosen.containsKey("name") || chosen.containsKey("lore")) {
            var meta = stack.getItemMeta();
            if (meta != null) {
                if (chosen.containsKey("name")) meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', Objects.toString(chosen.get("name"))));
                if (chosen.containsKey("lore")) {
                    List<String> lore = new ArrayList<>();
                    Object loreObj = chosen.get("lore");
                    if (loreObj instanceof List<?> list) {
                        for (Object line : list) {
                            lore.add(ChatColor.translateAlternateColorCodes('&', Objects.toString(line)));
                        }
                    }
                    meta.setLore(lore);
                }
                stack.setItemMeta(meta);
            }
        }
        return stack;
    }

    private static double toDouble(Object value) {
        if (value instanceof Number num) return num.doubleValue();
        try {
            return Double.parseDouble(Objects.toString(value));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
