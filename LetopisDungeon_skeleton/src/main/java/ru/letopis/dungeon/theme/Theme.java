package ru.letopis.dungeon.theme;

import org.bukkit.Material;

import java.util.List;

public enum Theme {
    CATACOMBS(new MaterialPalette(
            List.of(w(Material.DEEPSLATE_TILES, 3), w(Material.DEEPSLATE_BRICKS, 2), w(Material.POLISHED_DEEPSLATE, 1)),
            List.of(w(Material.DEEPSLATE_BRICKS, 3), w(Material.DEEPSLATE_TILES, 2), w(Material.CHISELED_DEEPSLATE, 1)),
            List.of(w(Material.DEEPSLATE_TILES, 3), w(Material.POLISHED_DEEPSLATE, 2)),
            List.of(w(Material.CHISELED_DEEPSLATE, 2), w(Material.POLISHED_BASALT, 1)),
            List.of(w(Material.BONE_BLOCK, 2), w(Material.IRON_BARS, 2), w(Material.DARK_OAK_FENCE, 1)),
            List.of(w(Material.SOUL_LANTERN, 2), w(Material.CANDLE, 1)),
            List.of(w(Material.CUT_COPPER, 1), w(Material.BLACKSTONE, 1)),
            List.of(w(Material.DEEPSLATE_BRICKS, 3), w(Material.CHISELED_DEEPSLATE, 1))
    )),
    BASTION(new MaterialPalette(
            List.of(w(Material.BLACKSTONE, 2), w(Material.POLISHED_BLACKSTONE, 2), w(Material.BASALT, 1)),
            List.of(w(Material.POLISHED_BLACKSTONE_BRICKS, 3), w(Material.BLACKSTONE, 2), w(Material.CRACKED_POLISHED_BLACKSTONE_BRICKS, 1)),
            List.of(w(Material.BLACKSTONE, 2), w(Material.POLISHED_BLACKSTONE, 2)),
            List.of(w(Material.GILDED_BLACKSTONE, 1), w(Material.POLISHED_BASALT, 2)),
            List.of(w(Material.IRON_BARS, 2), w(Material.NETHER_BRICK_FENCE, 1), w(Material.MAGMA_BLOCK, 1)),
            List.of(w(Material.LANTERN, 2), w(Material.SOUL_LANTERN, 1)),
            List.of(w(Material.CUT_COPPER, 1), w(Material.NETHER_BRICKS, 1)),
            List.of(w(Material.POLISHED_BLACKSTONE_BRICKS, 3), w(Material.GILDED_BLACKSTONE, 1))
    )),
    FROST_TOMB(new MaterialPalette(
            List.of(w(Material.PACKED_ICE, 2), w(Material.BLUE_ICE, 1), w(Material.SNOW_BLOCK, 1)),
            List.of(w(Material.PACKED_ICE, 2), w(Material.ICE, 1), w(Material.BLUE_ICE, 1)),
            List.of(w(Material.PACKED_ICE, 2), w(Material.SNOW_BLOCK, 1)),
            List.of(w(Material.ICE, 2), w(Material.BLUE_ICE, 1)),
            List.of(w(Material.SNOW, 2), w(Material.ICE, 1), w(Material.WHITE_CANDLE, 1)),
            List.of(w(Material.SOUL_LANTERN, 1), w(Material.CANDLE, 2)),
            List.of(w(Material.QUARTZ_BLOCK, 1), w(Material.WHITE_CONCRETE, 1)),
            List.of(w(Material.PACKED_ICE, 2), w(Material.QUARTZ_BLOCK, 1))
    )),
    OVERGROWN_TEMPLE(new MaterialPalette(
            List.of(w(Material.MOSSY_STONE_BRICKS, 2), w(Material.STONE_BRICKS, 2), w(Material.MOSS_BLOCK, 1)),
            List.of(w(Material.MOSSY_STONE_BRICKS, 3), w(Material.CRACKED_STONE_BRICKS, 1), w(Material.STONE_BRICKS, 2)),
            List.of(w(Material.STONE_BRICKS, 2), w(Material.MOSSY_STONE_BRICKS, 1)),
            List.of(w(Material.MOSSY_STONE_BRICKS, 2), w(Material.OAK_LOG, 1)),
            List.of(w(Material.VINE, 2), w(Material.MOSS_CARPET, 2), w(Material.FLOWERING_AZALEA, 1)),
            List.of(w(Material.LANTERN, 1), w(Material.CANDLE, 2)),
            List.of(w(Material.COPPER_BLOCK, 1), w(Material.PRISMARINE, 1)),
            List.of(w(Material.MOSSY_STONE_BRICKS, 3), w(Material.STONE_BRICKS, 1))
    )),
    ARCANE_LIBRARY(new MaterialPalette(
            List.of(w(Material.POLISHED_ANDESITE, 2), w(Material.SMOOTH_STONE, 2), w(Material.POLISHED_DEEPSLATE, 1)),
            List.of(w(Material.DEEPSLATE_BRICKS, 2), w(Material.POLISHED_DEEPSLATE, 2), w(Material.DARK_PRISMARINE, 1)),
            List.of(w(Material.SMOOTH_STONE, 2), w(Material.POLISHED_ANDESITE, 1)),
            List.of(w(Material.CHISELED_DEEPSLATE, 1), w(Material.POLISHED_BASALT, 2)),
            List.of(w(Material.BOOKSHELF, 2), w(Material.AMETHYST_BLOCK, 1), w(Material.PURPUR_PILLAR, 1)),
            List.of(w(Material.LANTERN, 1), w(Material.CANDLE, 2), w(Material.SOUL_LANTERN, 1)),
            List.of(w(Material.PURPUR_BLOCK, 1), w(Material.AMETHYST_BLOCK, 1)),
            List.of(w(Material.DEEPSLATE_BRICKS, 2), w(Material.PURPUR_BLOCK, 1))
    ));

    private final MaterialPalette palette;

    Theme(MaterialPalette palette) {
        this.palette = palette;
    }

    public MaterialPalette palette() {
        return palette;
    }

    private static WeightedMaterial w(Material material, int weight) {
        return new WeightedMaterial(material, weight);
    }
}
