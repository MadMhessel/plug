package ru.letopis.dungeon.room;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import ru.letopis.dungeon.core.DungeonBuilder;

import java.util.*;

public final class WaveRoom implements Room {

    private final DungeonBuilder builder;
    private final String name;
    private final String objective;
    private final int wavesTotal;
    private final Set<UUID> activeEntities = new HashSet<>();
    private int currentWave = 0;
    private boolean complete = false;
    private final List<EntityType> mobTypes;
    private final int baseMobs;
    private final int perPlayerMobs;
    private final double healthMultiplier;

    public WaveRoom(DungeonBuilder builder, String name, String objective, ConfigurationSection config, int wavesTotal) {
        this.builder = builder;
        this.name = name;
        this.objective = objective;
        this.wavesTotal = wavesTotal;
        this.mobTypes = loadMobTypes(config.getStringList("mobTypes"));
        this.baseMobs = config.getInt("base", 3);
        this.perPlayerMobs = config.getInt("perPlayer", 2);
        this.healthMultiplier = config.getDouble("healthMultiplier", 1.0);
    }

    @Override
    public String name() { return name; }

    @Override
    public String objective() { return objective + " (волна " + currentWave + "/" + wavesTotal + ")"; }

    @Override
    public void build(RoomContext context) {
        builder.buildRoom(context);
        builder.carveDoor(context.world(), context.origin(), context.sizeX(), context.sizeY(), context.sizeZ(), true);
    }

    @Override
    public void start(RoomContext context) {
        activeEntities.clear();
        currentWave = 0;
        complete = false;
        spawnNextWave(context);
    }

    @Override
    public void tick(RoomContext context) {
        activeEntities.removeIf(uuid -> {
            Entity e = context.world().getEntity(uuid);
            return e == null || e.isDead();
        });
        if (activeEntities.isEmpty() && !complete) {
            if (currentWave >= wavesTotal) {
                complete = true;
            } else {
                spawnNextWave(context);
            }
        }
    }

    private void spawnNextWave(RoomContext context) {
        currentWave++;
        int players = Math.max(1, context.session().participantCount());
        int total = baseMobs + perPlayerMobs * players;
        List<Location> spawnPoints = spawnPoints(context.origin(), context.sizeX(), context.sizeZ());
        Random random = new Random();
        for (int i = 0; i < total; i++) {
            Location spawn = spawnPoints.get(random.nextInt(spawnPoints.size())).clone();
            spawn.add(0.5, 1, 0.5);
            EntityType type = mobTypes.get(random.nextInt(mobTypes.size()));
            Entity entity = context.world().spawnEntity(spawn, type);
            context.manager().tagEntity(entity, context.session(), "wave");
            if (entity instanceof LivingEntity living) {
                if (living.getAttribute(Attribute.MAX_HEALTH) != null) {
                    double base = living.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
                    living.getAttribute(Attribute.MAX_HEALTH).setBaseValue(base * healthMultiplier);
                    living.setHealth(living.getAttribute(Attribute.MAX_HEALTH).getBaseValue());
                }
            }
            activeEntities.add(entity.getUniqueId());
        }
        context.manager().announceWave(context.session(), currentWave, wavesTotal, total);
    }

    @Override
    public boolean isComplete() { return complete; }

    @Override
    public double progress() {
        if (wavesTotal == 0) return 1.0;
        return Math.min(1.0, (double) currentWave / wavesTotal);
    }

    @Override
    public void onPlayerInteract(RoomContext context, org.bukkit.event.player.PlayerInteractEvent event) {
    }

    @Override
    public void cleanup(RoomContext context) {
        activeEntities.clear();
    }

    public int currentWave() { return currentWave; }
    public int wavesTotal() { return wavesTotal; }
    public int aliveMobs() { return activeEntities.size(); }

    private List<Location> spawnPoints(Location origin, int sizeX, int sizeZ) {
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();
        return List.of(
                new Location(origin.getWorld(), ox + 4, oy + 1, oz + 4),
                new Location(origin.getWorld(), ox + sizeX - 5, oy + 1, oz + 4),
                new Location(origin.getWorld(), ox + 4, oy + 1, oz + sizeZ - 5),
                new Location(origin.getWorld(), ox + sizeX - 5, oy + 1, oz + sizeZ - 5),
                new Location(origin.getWorld(), ox + sizeX / 2, oy + 1, oz + sizeZ / 2)
        );
    }

    private List<EntityType> loadMobTypes(List<String> names) {
        List<EntityType> types = new ArrayList<>();
        for (String name : names) {
            try {
                EntityType type = EntityType.valueOf(name.toUpperCase());
                if (type.isAlive()) types.add(type);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (types.isEmpty()) {
            types.add(EntityType.ZOMBIE);
            types.add(EntityType.SKELETON);
        }
        return types;
    }
}
