package ru.letopis.dungeon.core;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Candle;
import org.bukkit.block.data.type.Slab;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import ru.letopis.dungeon.model.BlockPos;
import ru.letopis.dungeon.model.SessionRegion;
import ru.letopis.dungeon.room.RoomContext;
import ru.letopis.dungeon.room.RoomLayout;
import ru.letopis.dungeon.theme.DarknessStyle;
import ru.letopis.dungeon.theme.MaterialPalette;
import ru.letopis.dungeon.theme.Theme;
import ru.letopis.dungeon.theme.WeightedMaterial;
import ru.letopis.dungeon.theme.WeightedPicker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class DungeonBuilder {

    private final JavaPlugin plugin;

    public DungeonBuilder(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void buildRoom(RoomContext context) {
        Theme theme = context.theme();
        MaterialPalette palette = theme.palette();
        Random random = context.random();

        Material floor = pick(palette.floor(), random);
        Material wall = pick(palette.wall(), random);
        Material ceiling = pick(palette.ceiling(), random);
        Material pillar = pick(palette.pillar(), random);

        buildRoomShell(context.world(), context.origin(), context.sizeX(), context.sizeY(), context.sizeZ(), floor, wall, ceiling);
        carveLayout(context, wall);
        if (pillar != null) buildPillars(context, pillar);
        if (context.layout() == RoomLayout.SPLIT_LEVEL) addSplitLevel(context, pick(palette.accent(), random));
        addRoomDecor(context, decorDensity());
        addThemeMarkers(context);
        addLights(context, maxLightsPerRoom(), lightSpacing(), darknessStyle());
    }

    public void buildCorridor(World world, Location origin, int length, int height, int width,
                              Theme theme, boolean alongX) {
        MaterialPalette palette = theme.palette();
        Material floor = pick(palette.floor(), new Random());
        Material wall = pick(palette.wall(), new Random());
        Material ceiling = pick(palette.ceiling(), new Random());

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
                }
                for (int y = oy; y <= maxY; y++) {
                    setBlock(world, ox - half, y, z, wall);
                    setBlock(world, ox + half, y, z, wall);
                }
            }
        }

        RoomContext fakeContext = new RoomContext(plugin, null, null, null, world, origin, length, height, width,
                theme, RoomLayout.RECT, new Random());
        addLights(fakeContext, Math.max(1, maxLightsPerRoom() / 2), lightSpacing(), darknessStyle());
    }

    public List<BlockPos> carveDoor(World world, Location origin, int sizeX, int sizeY, int sizeZ, boolean alongX) {
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();
        int doorWidth = 3;
        int doorHeight = 4;
        List<BlockPos> positions = new ArrayList<>();
        if (alongX) {
            int x = ox + sizeX - 1;
            int zStart = oz + sizeZ / 2 - 1;
            for (int y = oy + 1; y <= oy + doorHeight; y++) {
                for (int z = zStart; z < zStart + doorWidth; z++) {
                    setBlock(world, x, y, z, Material.AIR);
                    positions.add(new BlockPos(world, x, y, z));
                }
            }
        } else {
            int z = oz + sizeZ - 1;
            int xStart = ox + sizeX / 2 - 1;
            for (int y = oy + 1; y <= oy + doorHeight; y++) {
                for (int x = xStart; x < xStart + doorWidth; x++) {
                    setBlock(world, x, y, z, Material.AIR);
                    positions.add(new BlockPos(world, x, y, z));
                }
            }
        }
        return positions;
    }

    public void buildRegionShell(SessionRegion region, Material material, int batchSize) {
        World world = region.world();
        List<BlockPos> positions = new ArrayList<>();
        int minX = region.minX();
        int maxX = region.maxX();
        int minY = region.minY();
        int maxY = region.maxY();
        int minZ = region.minZ();
        int maxZ = region.maxZ();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                positions.add(new BlockPos(world, x, minY, z));
                positions.add(new BlockPos(world, x, maxY, z));
            }
        }
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                positions.add(new BlockPos(world, x, y, minZ));
                positions.add(new BlockPos(world, x, y, maxZ));
            }
            for (int z = minZ; z <= maxZ; z++) {
                positions.add(new BlockPos(world, minX, y, z));
                positions.add(new BlockPos(world, maxX, y, z));
            }
        }
        applyBatch(positions, material, batchSize);
    }

    public void clearAreaBatch(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int batchSize) {
        List<BlockPos> positions = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    positions.add(new BlockPos(world, x, y, z));
                }
            }
        }
        applyBatch(positions, Material.AIR, batchSize);
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

    public void applyBatch(List<BlockPos> positions, Material material, int batchSize) {
        if (positions.isEmpty()) return;
        List<BlockPos> queue = new ArrayList<>(positions);
        new BukkitRunnable() {
            @Override
            public void run() {
                int count = 0;
                while (!queue.isEmpty() && count < batchSize) {
                    BlockPos pos = queue.remove(queue.size() - 1);
                    setBlock(pos.world(), pos.x(), pos.y(), pos.z(), material);
                    count++;
                }
                if (queue.isEmpty()) cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void buildRoomShell(World world, Location origin, int sizeX, int sizeY, int sizeZ,
                                Material floor, Material wall, Material ceiling) {
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

    private void carveLayout(RoomContext context, Material wall) {
        int ox = context.origin().getBlockX();
        int oy = context.origin().getBlockY();
        int oz = context.origin().getBlockZ();
        int maxX = ox + context.sizeX() - 1;
        int maxY = oy + context.sizeY() - 1;
        int maxZ = oz + context.sizeZ() - 1;
        for (int x = ox + 1; x < maxX; x++) {
            for (int z = oz + 1; z < maxZ; z++) {
                boolean inside = isInsideLayout(context.layout(), x - ox, z - oz, context.sizeX(), context.sizeZ());
                if (!inside) {
                    for (int y = oy + 1; y < maxY; y++) {
                        setBlock(context.world(), x, y, z, wall);
                    }
                } else {
                    for (int y = oy + 1; y < maxY; y++) {
                        setBlock(context.world(), x, y, z, Material.AIR);
                    }
                }
            }
        }
    }

    private void buildPillars(RoomContext context, Material material) {
        int ox = context.origin().getBlockX();
        int oy = context.origin().getBlockY();
        int oz = context.origin().getBlockZ();
        List<int[]> points = List.of(
                new int[]{ox + 2, oz + 2},
                new int[]{ox + 2, oz + context.sizeZ() - 3},
                new int[]{ox + context.sizeX() - 3, oz + 2},
                new int[]{ox + context.sizeX() - 3, oz + context.sizeZ() - 3}
        );
        for (int[] pt : points) {
            if (!isInsideLayout(context.layout(), pt[0] - ox, pt[1] - oz, context.sizeX(), context.sizeZ())) continue;
            for (int y = oy + 1; y <= oy + context.sizeY() - 2; y++) {
                setBlock(context.world(), pt[0], y, pt[1], material);
            }
        }
    }

    private void addSplitLevel(RoomContext context, Material accent) {
        if (accent == null) return;
        int ox = context.origin().getBlockX();
        int oy = context.origin().getBlockY();
        int oz = context.origin().getBlockZ();
        int startX = ox + context.sizeX() / 3;
        int endX = ox + context.sizeX() - 3;
        int startZ = oz + context.sizeZ() / 3;
        int endZ = oz + context.sizeZ() - 3;
        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                if (!isInsideLayout(context.layout(), x - ox, z - oz, context.sizeX(), context.sizeZ())) continue;
                setBlock(context.world(), x, oy + 1, z, accent);
                setBlock(context.world(), x, oy + 2, z, Material.AIR);
            }
        }
    }

    private void addRoomDecor(RoomContext context, double density) {
        if (density <= 0) return;
        Random random = context.random();
        MaterialPalette palette = context.theme().palette();
        List<int[]> candidates = collectInsidePositions(context);
        Collections.shuffle(candidates, random);
        int count = Math.min(candidates.size(), Math.max(2, (int) (candidates.size() * density * 0.08)));
        for (int i = 0; i < count; i++) {
            int[] pos = candidates.get(i);
            if (random.nextDouble() > density) continue;
            Material decor = pick(palette.decor(), random);
            if (decor == null) continue;
            setBlock(context.world(), pos[0], pos[1], pos[2], decor);
        }
    }

    private void addThemeMarkers(RoomContext context) {
        Random random = context.random();
        MaterialPalette palette = context.theme().palette();
        List<int[]> candidates = collectInsidePositions(context);
        if (candidates.isEmpty()) return;
        Collections.shuffle(candidates, random);
        int markers = Math.min(2, 1 + random.nextInt(2));
        for (int i = 0; i < markers; i++) {
            int[] pos = candidates.get(i);
            switch (context.theme()) {
                case CATACOMBS -> {
                    setBlock(context.world(), pos[0], pos[1], pos[2], Material.BONE_BLOCK);
                    setBlock(context.world(), pos[0], pos[1] + 1, pos[2], Material.SKELETON_SKULL);
                }
                case OVERGROWN_TEMPLE -> {
                    setBlock(context.world(), pos[0], pos[1], pos[2], Material.MOSS_BLOCK);
                    setBlock(context.world(), pos[0], pos[1] + 1, pos[2], Material.VINE);
                }
                case ARCANE_LIBRARY -> {
                    setBlock(context.world(), pos[0], pos[1], pos[2], Material.AMETHYST_BLOCK);
                    setBlock(context.world(), pos[0], pos[1] + 1, pos[2], Material.PURPUR_PILLAR);
                }
                case BASTION -> {
                    setBlock(context.world(), pos[0], pos[1], pos[2], Material.MAGMA_BLOCK);
                    setBlock(context.world(), pos[0], pos[1] + 1, pos[2], Material.IRON_BARS);
                }
                case FROST_TOMB -> {
                    setBlock(context.world(), pos[0], pos[1], pos[2], Material.BLUE_ICE);
                    setBlock(context.world(), pos[0], pos[1] + 1, pos[2], Material.WHITE_CANDLE);
                }
            }
        }
    }

    private void addLights(RoomContext context, int maxLights, int lightSpacing, DarknessStyle darknessStyle) {
        if (maxLights <= 0) return;
        Random random = context.random();
        List<int[]> candidates = collectInsidePositions(context);
        if (candidates.isEmpty()) return;
        int area = candidates.size();
        int budget = Math.max(1, area / Math.max(1, lightSpacing));
        int count = Math.min(maxLights, budget);
        double factor = switch (darknessStyle) {
            case BRIGHT -> 1.2;
            case MOODY -> 0.6;
            case NORMAL -> 1.0;
        };
        count = Math.max(1, (int) Math.round(count * factor));
        Collections.shuffle(candidates, random);
        MaterialPalette palette = context.theme().palette();
        for (int i = 0; i < Math.min(count, candidates.size()); i++) {
            int[] pos = candidates.get(i);
            Material light = pick(palette.light(), random);
            if (light == null) continue;
            placeLight(context.world(), pos[0], pos[1], pos[2], light, random);
        }
    }

    private void placeLight(World world, int x, int y, int z, Material light, Random random) {
        if (light == Material.LANTERN || light == Material.SOUL_LANTERN) {
            setBlock(world, x, y + 2, z, Material.IRON_BARS);
            setBlock(world, x, y + 1, z, light);
            return;
        }
        if (light.name().endsWith("_CANDLE") || light == Material.CANDLE) {
            setBlock(world, x, y, z, Material.SMOOTH_STONE_SLAB);
            Block candleBlock = world.getBlockAt(x, y + 1, z);
            candleBlock.setType(light, false);
            if (candleBlock.getBlockData() instanceof Candle candle) {
                candle.setLit(true);
                candleBlock.setBlockData(candle, false);
            }
            return;
        }
        if (light.name().endsWith("_SLAB")) {
            Block slab = world.getBlockAt(x, y, z);
            slab.setType(light, false);
            if (slab.getBlockData() instanceof Slab slabData) {
                slabData.setType(Slab.Type.BOTTOM);
                slab.setBlockData(slabData, false);
            }
            setBlock(world, x, y + 1, z, Material.CANDLE);
            return;
        }
        setBlock(world, x, y + 1, z, light);
    }

    private List<int[]> collectInsidePositions(RoomContext context) {
        int ox = context.origin().getBlockX();
        int oy = context.origin().getBlockY();
        int oz = context.origin().getBlockZ();
        int maxX = ox + context.sizeX() - 2;
        int maxZ = oz + context.sizeZ() - 2;
        List<int[]> positions = new ArrayList<>();
        for (int x = ox + 2; x <= maxX; x++) {
            for (int z = oz + 2; z <= maxZ; z++) {
                if (!isInsideLayout(context.layout(), x - ox, z - oz, context.sizeX(), context.sizeZ())) continue;
                positions.add(new int[]{x, oy + 1, z});
            }
        }
        return positions;
    }

    private boolean isInsideLayout(RoomLayout layout, int relX, int relZ, int sizeX, int sizeZ) {
        int maxX = sizeX - 1;
        int maxZ = sizeZ - 1;
        int midX = sizeX / 2;
        int midZ = sizeZ / 2;
        if (relX == 0 || relZ == 0 || relX == maxX || relZ == maxZ) return true;
        return switch (layout) {
            case RECT -> true;
            case L_SHAPE -> relX <= midX || relZ <= midZ;
            case CROSS -> Math.abs(relX - midX) <= 3 || Math.abs(relZ - midZ) <= 3;
            case OCTAGON -> {
                int dx = Math.abs(relX - midX);
                int dz = Math.abs(relZ - midZ);
                int maxDist = (sizeX + sizeZ) / 4;
                yield dx + dz <= maxDist;
            }
            case SPLIT_LEVEL -> true;
            case ARENA_RING -> relX <= 3 || relZ <= 3 || relX >= maxX - 3 || relZ >= maxZ - 3;
        };
    }

    private Material pick(List<WeightedMaterial> list, Random random) {
        WeightedMaterial result = WeightedPicker.pick(list, random);
        return result == null ? null : result.material();
    }

    private int maxLightsPerRoom() {
        return plugin.getConfig().getInt("dungeon.lights.maxPerRoom", 4);
    }

    private int lightSpacing() {
        return plugin.getConfig().getInt("dungeon.lights.spacing", 24);
    }

    private double decorDensity() {
        return plugin.getConfig().getDouble("dungeon.decor.density", 0.35);
    }

    private DarknessStyle darknessStyle() {
        String value = plugin.getConfig().getString("dungeon.lights.darknessStyle", "NORMAL");
        try {
            return DarknessStyle.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return DarknessStyle.NORMAL;
        }
    }

    private void setBlock(World world, int x, int y, int z, Material type) {
        Block b = world.getBlockAt(x, y, z);
        if (b.getType() != type) b.setType(type, false);
    }
}
