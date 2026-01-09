package ru.letopis.dungeon.room;

import org.bukkit.event.player.PlayerInteractEvent;

public interface Room {
    String name();
    String objective();
    void build(RoomContext context);
    void start(RoomContext context);
    void tick(RoomContext context);
    boolean isComplete();
    double progress();
    void onPlayerInteract(RoomContext context, PlayerInteractEvent event);
    void cleanup(RoomContext context);
}
