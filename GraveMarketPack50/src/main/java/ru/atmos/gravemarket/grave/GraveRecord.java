package ru.atmos.gravemarket.grave;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import ru.atmos.gravemarket.util.LocationCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class GraveRecord {
    public String id;
    public UUID owner;
    public String ownerName;

    public boolean virtual;
    public boolean paid;
    public boolean pvpDeath;

    public long createdAtEpochMs;
    public long expiresAtEpochMs;
    public long retentionUntilEpochMs;

    public long extractCost;

    public String deathLocation; // encoded
    public String graveLocation; // encoded (block position)
    public String hologramEntity; // UUID string

    public int storedExp;
    public List<ItemStack> storedItems = new ArrayList<>();

    public Location deathLoc() { return LocationCodec.decode(deathLocation); }
    public Location graveLoc() { return LocationCodec.decode(graveLocation); }

    public String shortId() {
        if (id == null) return "?";
        return id.length() <= 8 ? id : id.substring(0, 8);
    }
}
