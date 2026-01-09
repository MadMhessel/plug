package ru.letopis.dungeon.theme;

import java.util.List;
import java.util.Random;

public final class WeightedPicker {
    private WeightedPicker() {
    }

    public static <T extends WeightedMaterial> WeightedMaterial pick(List<WeightedMaterial> list, Random random) {
        if (list == null || list.isEmpty()) return null;
        int total = 0;
        for (WeightedMaterial w : list) total += Math.max(0, w.weight());
        if (total <= 0) return list.get(0);
        int roll = random.nextInt(total);
        int current = 0;
        for (WeightedMaterial w : list) {
            current += Math.max(0, w.weight());
            if (roll < current) return w;
        }
        return list.get(list.size() - 1);
    }
}
