package ru.letopis.dungeon.model;

import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Gate {
    private final World world;
    private final List<BlockPos> blocks;
    private final Material material;
    private boolean closed;

    public Gate(World world, List<BlockPos> blocks, Material material) {
        this.world = world;
        this.blocks = new ArrayList<>(blocks);
        this.material = material;
    }

    public List<BlockPos> blocks() {
        return Collections.unmodifiableList(blocks);
    }

    public Material material() {
        return material;
    }

    public World world() {
        return world;
    }

    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }
}
