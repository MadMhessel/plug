package ru.letopis.dungeon.model;

import org.bukkit.World;

public record BlockPos(World world, int x, int y, int z) {
}
