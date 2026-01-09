package ru.atmos.gravemarket.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class SafeSpotFinder {
    private SafeSpotFinder() {}

    public static boolean isDangerous(Block b) {
        Material m = b.getType();
        return m == Material.LAVA || m == Material.FIRE || m == Material.CAMPFIRE || m == Material.SOUL_CAMPFIRE
                || m == Material.MAGMA_BLOCK;
    }

    public static boolean isFluid(Block b) {
        Material m = b.getType();
        return m == Material.WATER || m == Material.LAVA;
    }

    public static Location findSafeBlockLocation(Location origin, int radius) {
        if (origin == null || origin.getWorld() == null) return null;
        World w = origin.getWorld();

        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();

        // Search upward a little, then around.
        int minY = Math.max(w.getMinHeight(), oy - 2);
        int maxY = Math.min(w.getMaxHeight() - 2, oy + 2);

        Location best = null;
        double bestDist2 = Double.MAX_VALUE;

        for (int y = minY; y <= maxY; y++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int x = ox + dx;
                    int z = oz + dz;

                    Block feet = w.getBlockAt(x, y, z);
                    Block head = w.getBlockAt(x, y + 1, z);
                    Block below = w.getBlockAt(x, y - 1, z);

                    if (!feet.getType().isAir()) continue; // must place in air
                    if (!head.getType().isAir()) continue; // opening space

                    if (below.getType().isAir()) continue;
                    if (isFluid(below)) continue;
                    if (isDangerous(below)) continue;
                    if (isDangerous(feet) || isDangerous(head)) continue;

                    // avoid water/lava adjacent (simple)
                    if (isFluid(w.getBlockAt(x, y, z)) || isFluid(w.getBlockAt(x, y + 1, z))) continue;

                    double d2 = origin.distanceSquared(new Location(w, x + 0.5, y, z + 0.5));
                    if (d2 < bestDist2) {
                        bestDist2 = d2;
                        best = new Location(w, x, y, z);
                    }
                }
            }
        }
        return best;
    }
}
