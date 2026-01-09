package ru.letopis.dungeon.core;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.List;

public final class DungeonBuilder {

    public void buildRoomBase(World world, Location origin, int sizeX, int sizeY, int sizeZ,
                              Material floor, Material wall, Material ceiling, Material light) {
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();
        int maxX = ox + sizeX - 1;
        int maxY = oy + sizeY - 1;
        int maxZ = oz + sizeZ - 1;

        for (int x = ox; x <= maxX; x++) {
            for (int z = oz; z <= maxZ; z++) {
                setBlock(world, x, oy, z, floor);
                setBlock(world, x, maxY, z, ceiling);
                if ((x + z) % 7 == 0) setBlock(world, x, oy + 1, z, light);
            }
        }

        for (int y = oy; y <= maxY; y++) {
            for (int x = ox; x <= maxX; x++) {
                setBlock(world, x, y, oz, wall);
                setBlock(world, x, y, maxZ, wall);
            }
            for (int z = oz; z <= maxZ; z++) {
                setBlock(world, ox, y, z, wall);
                setBlock(world, maxX, y, z, wall);
            }
        }
    }

    public void carveDoor(World world, Location origin, int sizeX, int sizeY, int sizeZ, boolean alongX) {
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();
        int doorWidth = 3;
        int doorHeight = 4;
        if (alongX) {
            int x = ox + sizeX - 1;
            int zStart = oz + sizeZ / 2 - 1;
            for (int y = oy + 1; y <= oy + doorHeight; y++) {
                for (int z = zStart; z < zStart + doorWidth; z++) {
                    setBlock(world, x, y, z, Material.AIR);
                }
            }
        } else {
            int z = oz + sizeZ - 1;
            int xStart = ox + sizeX / 2 - 1;
            for (int y = oy + 1; y <= oy + doorHeight; y++) {
                for (int x = xStart; x < xStart + doorWidth; x++) {
                    setBlock(world, x, y, z, Material.AIR);
                }
            }
        }
    }

    public void buildCorridor(World world, Location origin, int length, int height, int width,
                              Material floor, Material wall, Material ceiling, Material light, boolean alongX) {
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();
        int maxY = oy + height - 1;

        if (alongX) {
            int maxX = ox + length - 1;
            int half = width / 2;
            for (int x = ox; x <= maxX; x++) {
                for (int z = oz - half; z <= oz + half; z++) {
                    setBlock(world, x, oy, z, floor);
                    setBlock(world, x, maxY, z, ceiling);
                    if ((x + z) % 5 == 0) setBlock(world, x, oy + 1, z, light);
                }
                for (int y = oy; y <= maxY; y++) {
                    setBlock(world, x, y, oz - half, wall);
                    setBlock(world, x, y, oz + half, wall);
                }
            }
        } else {
            int maxZ = oz + length - 1;
            int half = width / 2;
            for (int z = oz; z <= maxZ; z++) {
                for (int x = ox - half; x <= ox + half; x++) {
                    setBlock(world, x, oy, z, floor);
                    setBlock(world, x, maxY, z, ceiling);
                    if ((x + z) % 5 == 0) setBlock(world, x, oy + 1, z, light);
                }
                for (int y = oy; y <= maxY; y++) {
                    setBlock(world, ox - half, y, z, wall);
                    setBlock(world, ox + half, y, z, wall);
                }
            }
        }
    }

    public void buildPillars(World world, Location origin, int sizeX, int sizeY, int sizeZ, Material material) {
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();
        List<int[]> points = List.of(
                new int[]{ox + 2, oz + 2},
                new int[]{ox + 2, oz + sizeZ - 3},
                new int[]{ox + sizeX - 3, oz + 2},
                new int[]{ox + sizeX - 3, oz + sizeZ - 3}
        );
        for (int[] pt : points) {
            for (int y = oy + 1; y <= oy + sizeY - 2; y++) {
                setBlock(world, pt[0], y, pt[1], material);
            }
        }
    }

    public void clearArea(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    setBlock(world, x, y, z, Material.AIR);
                }
            }
        }
    }

    private void setBlock(World world, int x, int y, int z, Material type) {
        Block b = world.getBlockAt(x, y, z);
        if (b.getType() != type) b.setType(type, false);
    }
}
