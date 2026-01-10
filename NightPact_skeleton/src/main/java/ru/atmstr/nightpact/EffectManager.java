package ru.atmstr.nightpact;

import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

public class EffectManager {

    private final NightPactPlugin plugin;
    private final Map<String, Integer> baseWeights = new HashMap<>();
    private final List<PactEffect> effects = new ArrayList<>();
    private final Random rng = new Random();

    public EffectManager(NightPactPlugin plugin) {
        this.plugin = plugin;
        loadWeights();
        registerDefaults();
    }

    private void loadWeights() {
        baseWeights.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("effect_weights");
        if (sec == null) return;

        for (String key : sec.getKeys(false)) {
            baseWeights.put(key, Math.max(0, sec.getInt(key)));
        }
    }

    private void registerDefaults() {
        effects.clear();
        effects.add(new effects.WellRested());
        effects.add(new effects.ClearMind());
        effects.add(new effects.NoDreams());
        effects.add(new effects.NightCold());
        effects.add(new effects.MessedSchedule());
        effects.add(new effects.BadDream());
        effects.add(new effects.PropheticDream());
        effects.add(new effects.NightDeal());
    }

    public String getEffectDisplayName(PactEffect effect) {
        String id = effect.getId();
        String key = "messages.effect." + id;
        return NightPactPlugin.colorize(plugin.getConfig().getString(key, id));
    }

    public PactEffect select(PactContext ctx) {
        // финальные веса с модификаторами
        double posMult = plugin.getConfig().getDouble("selection_modifiers.positive_multiplier", 1.0);
        double neuMult = plugin.getConfig().getDouble("selection_modifiers.neutral_multiplier", 1.0);
        double negMult = plugin.getConfig().getDouble("selection_modifiers.negative_multiplier", 1.0);

        boolean combo = ctx.hasComboCluster();
        double comboPos = plugin.getConfig().getDouble("selection_modifiers.combo_positive_multiplier", 1.25);

        double anxiousNeg = plugin.getConfig().getDouble("selection_modifiers.anxious_negative_multiplier", 1.35);
        double sentNeg = plugin.getConfig().getDouble("selection_modifiers.sentinels_negative_multiplier", 1.10);

        List<Entry> pool = new ArrayList<>();
        double total = 0.0;

        for (PactEffect e : effects) {
            int base = baseWeights.getOrDefault(e.getId(), 0);
            if (base <= 0) continue;

            double w = base;
            switch (e.getCategory()) {
                case POSITIVE -> {
                    w *= posMult;
                    if (combo) w *= comboPos;
                }
                case NEUTRAL -> w *= neuMult;
                case NEGATIVE -> {
                    w *= negMult;
                    if (ctx.anxiousSleepers > 0) w *= anxiousNeg;
                    if (!ctx.sentinels.isEmpty()) w *= sentNeg;
                }
            }

            if (w <= 0.0001) continue;
            total += w;
            pool.add(new Entry(e, w));
        }

        // fallback: если всё выключено — no_dreams
        if (pool.isEmpty()) {
            for (PactEffect e : effects) {
                if ("no_dreams".equals(e.getId())) return e;
            }
            return effects.get(0);
        }

        double roll = rng.nextDouble() * total;
        double cur = 0.0;
        for (Entry ent : pool) {
            cur += ent.weight;
            if (roll <= cur) return ent.effect;
        }
        return pool.get(pool.size() - 1).effect;
    }

    private record Entry(PactEffect effect, double weight) {}
}
