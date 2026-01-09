package ru.letopis.dungeon.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class Director {
    private int tension = 0;
    private long seed = 0L;
    private final List<String> roomPlan = new ArrayList<>();

    public int tension() { return tension; }
    public long seed() { return seed; }
    public List<String> roomPlan() { return List.copyOf(roomPlan); }

    public void setTension(int value) {
        this.tension = Math.max(0, Math.min(1000, value));
    }

    public void addTension(int delta) { setTension(this.tension + delta); }

    public void newPlan() {
        this.seed = System.currentTimeMillis();
        Random random = new Random(seed);
        roomPlan.clear();
        roomPlan.add("prep");
        roomPlan.add(random.nextBoolean() ? "waves" : "waves");
        roomPlan.add("puzzle");
        roomPlan.add(random.nextBoolean() ? "waves" : "waves");
        roomPlan.add("boss");
    }
}
