package ru.atmstr.nightpact.effects;

import org.bukkit.Location;
import org.bukkit.StructureType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import ru.atmstr.nightpact.NightPactPlugin;
import ru.atmstr.nightpact.Participant;
import ru.atmstr.nightpact.PactContext;
import ru.atmstr.nightpact.PactEffect;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class PropheticDream implements PactEffect {

    private static final Map<UUID, CacheEntry> WORLD_CACHE = new HashMap<>();
    private static final Map<UUID, Long> WORLD_COOLDOWNS = new HashMap<>();

    @Override
    public String getId() {
        return "prophetic_dream";
    }

    @Override
    public Category getCategory() {
        return Category.NEUTRAL;
    }

    @Override
    public void apply(NightPactPlugin plugin, PactContext ctx) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("effects.prophetic_dream");
        if (sec == null) return;
        if (!sec.getBoolean("enabled", true)) return;

        String st = sec.getString("structure_type", "VILLAGE");
        int radius = Math.max(200, sec.getInt("search_radius_blocks", 2000));
        boolean unexplored = sec.getBoolean("find_unexplored", false);
        int playerCooldownMin = Math.max(0, sec.getInt("cooldown_minutes.player", 20));
        int worldCooldownMin = Math.max(0, sec.getInt("cooldown_minutes.world", 10));
        int cacheMinutes = Math.max(1, sec.getInt("cache_minutes", 20));

        StructureType type = resolveStructureType(st);

        long now = System.currentTimeMillis();
        long playerCooldownMs = playerCooldownMin * 60_000L;
        long worldCooldownMs = worldCooldownMin * 60_000L;
        long cacheMs = cacheMinutes * 60_000L;
        UUID worldId = ctx.world.getUID();

        for (Player p : ctx.sleepers) {
            Participant participant = ctx.getParticipant(p.getUniqueId());
            if (participant != null && participant.isOnCooldown(getId(), playerCooldownMs, now)) {
                sendCooldownMessage(plugin, p, participant.getCooldownRemaining(getId(), playerCooldownMs, now));
                continue;
            }

            Location origin = p.getLocation();
            Location found = null;
            int chunkX = origin.getBlockX() >> 4;
            int chunkZ = origin.getBlockZ() >> 4;
            CacheEntry cache = WORLD_CACHE.get(worldId);

            if (cache != null && cache.matches(type, chunkX, chunkZ, now, cacheMs)) {
                found = cache.found;
            } else {
                long lastWorldUse = WORLD_COOLDOWNS.getOrDefault(worldId, 0L);
                if (worldCooldownMs > 0 && now - lastWorldUse < worldCooldownMs) {
                    sendCooldownMessage(plugin, p, worldCooldownMs - (now - lastWorldUse));
                    continue;
                }
                try {
                    found = ctx.world.locateNearestStructure(origin, type, radius, unexplored);
                } catch (Throwable ignored) {
                    // если API отличается — не ломаем сервер, просто даём атмосферное сообщение
                }
                WORLD_COOLDOWNS.put(worldId, now);
                if (found != null) {
                    WORLD_CACHE.put(worldId, new CacheEntry(type, chunkX, chunkZ, found, now));
                }
            }

            if (found != null) {
                p.sendMessage(NightPactPlugin.colorize(plugin.getConfig().getString("messages.prefix", "")) +
                        "§bВещий сон: §f" + type.getName() + " §7≈ §f" +
                        found.getBlockX() + " " + found.getBlockY() + " " + found.getBlockZ());
            } else {
                p.sendMessage(NightPactPlugin.colorize(plugin.getConfig().getString("messages.prefix", "")) +
                        "§bВещий сон: §7образ расплывается…");
            }

            if (participant != null) {
                participant.setCooldown(getId(), now);
            }
        }
    }

    private void sendCooldownMessage(NightPactPlugin plugin, Player player, long remainingMs) {
        long minutes = Math.max(1, (remainingMs + 59_999) / 60_000L);
        String raw = plugin.getConfig().getString("messages.prophetic_cooldown",
                "§bВещий сон: §7слишком туманно (ещё %MINUTES% мин.)");
        String msg = raw.replace("%MINUTES%", String.valueOf(minutes));
        player.sendMessage(NightPactPlugin.colorize(plugin.getConfig().getString("messages.prefix", "")) + msg);
    }

    private StructureType resolveStructureType(String raw) {
        if (raw == null) return StructureType.VILLAGE;
        String target = raw.trim().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, StructureType> entry : StructureType.getStructureTypes().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(target)) {
                return entry.getValue();
            }
        }
        return StructureType.VILLAGE;
    }

    private record CacheEntry(StructureType type, int chunkX, int chunkZ, Location found, long timestamp) {
        boolean matches(StructureType other, int otherChunkX, int otherChunkZ, long now, long cacheMs) {
            if (type != other) return false;
            if (chunkX != otherChunkX || chunkZ != otherChunkZ) return false;
            return now - timestamp <= cacheMs;
        }
    }
}
