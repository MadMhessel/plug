package ru.letopis.dungeon.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EntranceStructure {
    private final List<BlockPos> placedBlocks = new ArrayList<>();

    public void add(BlockPos pos) {
        placedBlocks.add(pos);
    }

    public List<BlockPos> placedBlocks() {
        return Collections.unmodifiableList(placedBlocks);
    }

    public boolean isEmpty() {
        return placedBlocks.isEmpty();
    }
}
