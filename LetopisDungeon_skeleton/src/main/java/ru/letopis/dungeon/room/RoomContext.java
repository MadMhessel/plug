package ru.letopis.dungeon.room;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import ru.letopis.dungeon.core.DungeonManager;
import ru.letopis.dungeon.core.DungeonWorldService;
import ru.letopis.dungeon.model.Session;

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

    public RoomContext(JavaPlugin plugin, DungeonManager manager, DungeonWorldService worldService,
                       Session session, World world, Location origin, int sizeX, int sizeY, int sizeZ) {
        this.plugin = plugin;
        this.manager = manager;
        this.worldService = worldService;
        this.session = session;
        this.world = world;
        this.origin = origin;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
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
}
