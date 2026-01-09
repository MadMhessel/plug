package ru.atmos.gravemarket.util;

import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class AuditLog implements Closeable {

    private final JavaPlugin plugin;
    private final File file;
    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private BufferedWriter out;

    public AuditLog(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "grave-audit.log");
        try {
            plugin.getDataFolder().mkdirs();
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().warning("Cannot open grave-audit.log: " + e.getMessage());
        }
    }

    public synchronized void log(String action, UUID actor, UUID owner, String graveId, Location loc, String details) {
        if (out == null) return;
        try {
            String line = fmt.format(Instant.now()) +
                    " action=" + action +
                    " actor=" + (actor == null ? "-" : actor) +
                    " owner=" + (owner == null ? "-" : owner) +
                    " grave=" + graveId +
                    " loc=" + (loc == null ? "-" : LocationCodec.pretty(loc)) +
                    " " + (details == null ? "" : details);
            out.write(line);
            out.newLine();
            out.flush();
        } catch (IOException e) {
            plugin.getLogger().warning("Audit write failed: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        if (out != null) {
            try { out.close(); } catch (IOException ignored) {}
        }
    }
}
