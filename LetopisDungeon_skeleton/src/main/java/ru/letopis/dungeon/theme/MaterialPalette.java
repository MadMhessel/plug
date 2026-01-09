package ru.letopis.dungeon.theme;

import java.util.List;

public record MaterialPalette(
        List<WeightedMaterial> floor,
        List<WeightedMaterial> wall,
        List<WeightedMaterial> ceiling,
        List<WeightedMaterial> pillar,
        List<WeightedMaterial> decor,
        List<WeightedMaterial> light,
        List<WeightedMaterial> accent,
        List<WeightedMaterial> gate
) {
}
