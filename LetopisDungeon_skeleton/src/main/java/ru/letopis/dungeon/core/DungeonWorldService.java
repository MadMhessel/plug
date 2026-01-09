package ru.letopis.dungeon.core;

import org.bukkit.*;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.HeightMap;
import org.bukkit.block.Biome;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;

public final class DungeonWorldService {

    private final JavaPlugin plugin;
    private World dungeonWorld;

    public DungeonWorldService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public World ensureWorld() {
        String worldName = plugin.getConfig().getString("dungeon.worldName", "letopis_dungeon");
        World w = Bukkit.getWorld(worldName);
        if (w == null) {
            WorldCreator wc = new WorldCreator(worldName);
            wc.environment(World.Environment.NORMAL);
            wc.generator(new VoidGenerator());
            w = wc.createWorld();
        }
        this.dungeonWorld = w;
        applyRules();
        return w;
    }

    public World world() { return dungeonWorld; }

    public boolean isDungeonWorld(World w) {
        return dungeonWorld != null && w != null && dungeonWorld.getUID().equals(w.getUID());
    }

    private void applyRules() {
        if (dungeonWorld == null) return;
        dungeonWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        dungeonWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        dungeonWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        dungeonWorld.setGameRule(GameRule.MOB_GRIEFING, false);
        dungeonWorld.setGameRule(GameRule.RANDOM_TICK_SPEED, plugin.getConfig().getInt("dungeon.rules.randomTickSpeed", 0));
        dungeonWorld.setStorm(false);
        dungeonWorld.setThundering(false);
        dungeonWorld.setWeatherDuration(0);
    }

    private static final class VoidGenerator extends ChunkGenerator {
        @Override
        public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
            ChunkData data = createChunkData(world);
            for (int i = 0; i < 256; i++) biome.setBiome(i & 15, i >> 4, Biome.THE_VOID);
            return data;
        }

        @Override
        public boolean shouldGenerateCaves() { return false; }

        @Override
        public boolean shouldGenerateMobs() { return false; }

        @Override
        public boolean shouldGenerateStructures() { return false; }

        @Override
        public boolean shouldGenerateDecorations() { return false; }

        @Override
        public boolean shouldGenerateSurface() { return false; }

        @Override
        public boolean shouldGenerateNoise() { return false; }

        @Override
        public int getBaseHeight(WorldInfo worldInfo, Random random, int x, int z, HeightMap heightMap) {
            return 0;
        }
    }
}
