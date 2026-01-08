package ru.letopis.dungeon.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;

public final class PlayerReturnPoint {
    private final UUID worldId;
    private final double x, y, z;
    private final float yaw, pitch;

    private PlayerReturnPoint(UUID worldId, double x, double y, double z, float yaw, float pitch) {
        this.worldId = worldId;
        this.x = x; this.y = y; this.z = z;
        this.yaw = yaw; this.pitch = pitch;
    }

    public static PlayerReturnPoint fromPlayer(Player p) {
        Location l = p.getLocation();
        World w = l.getWorld();
        Objects.requireNonNull(w, "world");
        return new PlayerReturnPoint(w.getUID(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    public Location toLocation() {
        World w = Bukkit.getWorld(worldId);
        if (w == null) w = Bukkit.getWorlds().get(0);
        return new Location(w, x, y, z, yaw, pitch);
    }
}
