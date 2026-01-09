package ru.atmos.gravemarket.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Locale;

public final class LocationCodec {
    private LocationCodec() {}

    public static String encode(Location loc) {
        if (loc == null || loc.getWorld() == null) return "";
        return String.format(Locale.ROOT, "%s:%.2f:%.2f:%.2f:%.2f:%.2f",
                loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch());
    }

    public static Location decode(String s) {
        if (s == null || s.isBlank()) return null;
        String[] p = s.split(":");
        if (p.length < 4) return null;
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        try {
            double x = Double.parseDouble(p[1]);
            double y = Double.parseDouble(p[2]);
            double z = Double.parseDouble(p[3]);
            float yaw = (p.length >= 5) ? Float.parseFloat(p[4]) : 0f;
            float pitch = (p.length >= 6) ? Float.parseFloat(p[5]) : 0f;
            return new Location(w, x, y, z, yaw, pitch);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String blockKey(Location loc) {
        if (loc == null || loc.getWorld() == null) return "";
        return loc.getWorld().getUID() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    public static String pretty(Location loc) {
        if (loc == null || loc.getWorld() == null) return "(?)";
        return loc.getWorld().getName() + " " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ();
    }
}
