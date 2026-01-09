package ru.letopis.dungeon.model;

import org.bukkit.Location;
import org.bukkit.World;

public final class SessionRegion {
    private final World world;
    private final int minX;
    private final int maxX;
    private final int minY;
    private final int maxY;
    private final int minZ;
    private final int maxZ;

    public SessionRegion(World world, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        this.world = world;
        this.minX = Math.min(minX, maxX);
        this.maxX = Math.max(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.maxY = Math.max(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxZ = Math.max(minZ, maxZ);
    }

    public World world() { return world; }

    public boolean contains(Location l) {
        if (l == null || l.getWorld() == null) return false;
        if (!l.getWorld().getUID().equals(world.getUID())) return false;
        int x = l.getBlockX();
        int y = l.getBlockY();
        int z = l.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public int minX() { return minX; }
    public int maxX() { return maxX; }
    public int minY() { return minY; }
    public int maxY() { return maxY; }
    public int minZ() { return minZ; }
    public int maxZ() { return maxZ; }
}
