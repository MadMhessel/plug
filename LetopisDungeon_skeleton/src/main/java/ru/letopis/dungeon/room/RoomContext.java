package ru.letopis.dungeon.room;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import ru.letopis.dungeon.core.DungeonManager;
import ru.letopis.dungeon.core.DungeonWorldService;
import ru.letopis.dungeon.model.Session;
import ru.letopis.dungeon.theme.Theme;

import java.util.Random;

public final class RoomContext {
    private final JavaPlugin plugin;
    private final DungeonManager manager;
    private final DungeonWorldService worldService;
    private final Session session;
    private final World world;
    private final Location origin;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final Theme theme;
    private final RoomLayout layout;
    private final Random random;

    public RoomContext(JavaPlugin plugin, DungeonManager manager, DungeonWorldService worldService,
                       Session session, World world, Location origin, int sizeX, int sizeY, int sizeZ,
                       Theme theme, RoomLayout layout, Random random) {
        this.plugin = plugin;
        this.manager = manager;
        this.worldService = worldService;
        this.session = session;
        this.world = world;
        this.origin = origin;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.theme = theme;
        this.layout = layout;
        this.random = random;
    }

    public JavaPlugin plugin() { return plugin; }
    public DungeonManager manager() { return manager; }
    public DungeonWorldService worldService() { return worldService; }
    public Session session() { return session; }
    public World world() { return world; }
    public Location origin() { return origin; }
    public int sizeX() { return sizeX; }
    public int sizeY() { return sizeY; }
    public int sizeZ() { return sizeZ; }
    public Theme theme() { return theme; }
    public RoomLayout layout() { return layout; }
    public Random random() { return random; }

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null) return false;
        if (!location.getWorld().getUID().equals(world.getUID())) return false;
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        int minX = origin.getBlockX();
        int minY = origin.getBlockY();
        int minZ = origin.getBlockZ();
        int maxX = minX + sizeX - 1;
        int maxY = minY + sizeY - 1;
        int maxZ = minZ + sizeZ - 1;
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }
}
