package ru.atmstr.nightpact;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class PactContext {

    public final World world;
    public final List<Player> onlinePlayers;
    public final List<Player> sleepers;
    public final List<Player> sentinels;

    public final int anxiousSleepers;
    public final Map<UUID, Location> bedLocations;
    public final Map<UUID, Participant> participants;

    public final boolean comboEnabled;
    public final int comboRadius;
    public final int comboMinCluster;

    public PactContext(World world,
                       List<Player> onlinePlayers,
                       List<Player> sleepers,
                       List<Player> sentinels,
                       int anxiousSleepers,
                       Map<UUID, Location> bedLocations,
                       Map<UUID, Participant> participants,
                       boolean comboEnabled,
                       int comboRadius,
                       int comboMinCluster) {
        this.world = world;
        this.onlinePlayers = onlinePlayers;
        this.sleepers = sleepers;
        this.sentinels = sentinels;
        this.anxiousSleepers = anxiousSleepers;
        this.bedLocations = bedLocations;
        this.participants = participants;
        this.comboEnabled = comboEnabled;
        this.comboRadius = comboRadius;
        this.comboMinCluster = comboMinCluster;
    }

    public Participant getParticipant(UUID uuid) {
        return participants.get(uuid);
    }

    public boolean hasComboCluster() {
        if (!comboEnabled) return false;
        if (sleepers.size() < comboMinCluster) return false;

        // Простая проверка: есть ли у кого-то >= (comboMinCluster-1) спящих в радиусе.
        int r = Math.max(1, comboRadius);
        double r2 = (double) r * r;

        List<Location> locs = sleepers.stream()
                .map(p -> bedLocations.getOrDefault(p.getUniqueId(), p.getLocation()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        for (int i = 0; i < locs.size(); i++) {
            int nearby = 1;
            for (int j = 0; j < locs.size(); j++) {
                if (i == j) continue;
                if (!locs.get(i).getWorld().equals(locs.get(j).getWorld())) continue;
                if (locs.get(i).distanceSquared(locs.get(j)) <= r2) nearby++;
                if (nearby >= comboMinCluster) return true;
            }
        }
        return false;
    }
}
