package ru.letopis.dungeon.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class SafeTeleport {
    private SafeTeleport() {}

    public static void teleport(Player p, Location target) {
        Location safe = findNearbySafe(target, 8);
        p.teleport(safe != null ? safe : target);
    }

    private static Location findNearbySafe(Location origin, int radius) {
        World w = origin.getWorld();
        if (w == null) return null;

        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();

        for (int dy = 0; dy <= 6; dy++) {
            for (int r = 0; r <= radius; r++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        int x = ox + dx;
                        int y = oy + dy;
                        int z = oz + dz;
                        if (isSafe(w, x, y, z)) {
                            return new Location(w, x + 0.5, y, z + 0.5, origin.getYaw(), origin.getPitch());
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean isSafe(World w, int x, int y, int z) {
        Material feet = w.getBlockAt(x, y, z).getType();
        Material head = w.getBlockAt(x, y + 1, z).getType();
        Material below = w.getBlockAt(x, y - 1, z).getType();
        if (!below.isSolid()) return false;
        if (below == Material.LAVA) return false;
        if (feet.isSolid()) return false;
        if (head.isSolid()) return false;
        return true;
    }
}
