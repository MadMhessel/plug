package ru.atmstr.nightpact;

public interface PactEffect {

    enum Category { POSITIVE, NEUTRAL, NEGATIVE }

    String getId();

    Category getCategory();

    void apply(NightPactPlugin plugin, PactContext ctx);
}
