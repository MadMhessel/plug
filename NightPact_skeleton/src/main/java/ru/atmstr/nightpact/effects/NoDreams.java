package ru.atmstr.nightpact.effects;

import ru.atmstr.nightpact.NightPactPlugin;
import ru.atmstr.nightpact.PactContext;
import ru.atmstr.nightpact.PactEffect;

public class NoDreams implements PactEffect {

    @Override
    public String getId() {
        return "no_dreams";
    }

    @Override
    public Category getCategory() {
        return Category.NEUTRAL;
    }

    @Override
    public void apply(NightPactPlugin plugin, PactContext ctx) {
        // намеренно пусто
    }
}
