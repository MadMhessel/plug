package ru.letopis.dungeon.model;

public final class Director {
    private int tension = 0;

    public int tension() { return tension; }

    public void setTension(int value) {
        this.tension = Math.max(0, Math.min(1000, value));
    }

    public void addTension(int delta) { setTension(this.tension + delta); }
}
