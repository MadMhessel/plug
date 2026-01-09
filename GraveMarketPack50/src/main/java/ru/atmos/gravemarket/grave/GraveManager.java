package ru.atmos.gravemarket.grave;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import ru.atmos.gravemarket.util.AuditLog;
import ru.atmos.gravemarket.util.LocationCodec;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;

public final class GraveManager {

    private final JavaPlugin plugin;
    private final AuditLog audit;
    private final File file;
    private YamlConfiguration yaml = new YamlConfiguration();

    private final NamespacedKey keyGraveId;

    private final Map<String, GraveRecord> byId = new HashMap<>();
    private final Map<String, String> byBlockKey = new HashMap<>(); // world:x:y:z -> graveId

    private final TrustManager trust;

    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    public GraveManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.audit = (plugin instanceof ru.atmos.gravemarket.GraveMarketPlugin g) ? g.audit() : null;
        this.file = new File(plugin.getDataFolder(), "graves.yml");
        this.keyGraveId = new NamespacedKey(plugin, "grave_id");
        this.trust = new TrustManager(plugin);
        this.trust.load();
    }

    public TrustManager trust() { return trust; }

    public Collection<GraveRecord> all() { return Collections.unmodifiableCollection(byId.values()); }

    public GraveRecord get(String id) { return byId.get(id); }

    public GraveRecord findByBlock(Location blockLoc) {
        if (blockLoc == null) return null;
        String id = byBlockKey.get(LocationCodec.blockKey(blockLoc));
        return id == null ? null : byId.get(id);
    }

    public GraveRecord activeGrave(UUID owner) {
        GraveRecord best = null;
        for (GraveRecord g : byId.values()) {
            if (g.owner == null || !g.owner.equals(owner)) continue;
            // Treat any not fully cleaned-up as "active"
            if (best == null || g.createdAtEpochMs > best.createdAtEpochMs) best = g;
        }
        return best;
    }

    public boolean isAuthorized(GraveRecord g, UUID actor) {
        if (g == null || actor == null) return false;
        if (actor.equals(g.owner)) return true;
        return trust.isTrusted(g.owner, actor);
    }

    public long baseExtractCost(GraveRecord g) {
        long base = plugin.getConfig().getLong("economy.prices.extract", 480);
        if (g != null && g.pvpDeath) {
            double mult = plugin.getConfig().getDouble("pvp.extractCostMultiplier", 2.0);
            return Math.max(0L, Math.round(base * mult));
        }
        return base;
    }

    public void load() {
        byId.clear();
        byBlockKey.clear();

        if (file.exists()) {
            try {
                yaml.load(file);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load graves.yml: " + e.getMessage());
            }
        }

        ConfigurationSection sec = yaml.getConfigurationSection("graves");
        if (sec == null) return;

        for (String id : sec.getKeys(false)) {
            ConfigurationSection g = sec.getConfigurationSection(id);
            if (g == null) continue;

            GraveRecord r = new GraveRecord();
            r.id = id;
            try { r.owner = UUID.fromString(g.getString("owner", "")); } catch (IllegalArgumentException ignored) {}
            r.ownerName = g.getString("ownerName", "Player");
            r.virtual = g.getBoolean("virtual", false);
            r.paid = g.getBoolean("paid", false);
            r.pvpDeath = g.getBoolean("pvpDeath", false);
            r.createdAtEpochMs = g.getLong("createdAtEpochMs", System.currentTimeMillis());
            r.expiresAtEpochMs = g.getLong("expiresAtEpochMs", r.createdAtEpochMs);
            r.retentionUntilEpochMs = g.getLong("retentionUntilEpochMs", r.expiresAtEpochMs);
            r.extractCost = g.getLong("extractCost", baseExtractCost(r));
            r.deathLocation = g.getString("deathLocation", "");
            r.graveLocation = g.getString("graveLocation", "");
            r.hologramEntity = g.getString("hologramEntity", "");
            r.storedExp = g.getInt("storedExp", 0);

            List<?> raw = g.getList("items");
            if (raw != null) {
                for (Object o : raw) {
                    if (o instanceof ItemStack it && it.getType() != Material.AIR) {
                        r.storedItems.add(it);
                    }
                }
            }

            byId.put(id, r);
            if (!r.virtual) {
                Location bl = r.graveLoc();
                if (bl != null) byBlockKey.put(LocationCodec.blockKey(bl), id);
            }
        }

        // restore physical inventories + holograms
        for (GraveRecord r : byId.values()) {
            if (!r.virtual) {
                ensurePhysicalState(r);
            }
        }
    }

    public void save() {
        yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("graves");

        for (GraveRecord r : byId.values()) {
            ConfigurationSection g = root.createSection(r.id);
            g.set("owner", r.owner == null ? "" : r.owner.toString());
            g.set("ownerName", r.ownerName);
            g.set("virtual", r.virtual);
            g.set("paid", r.paid);
            g.set("pvpDeath", r.pvpDeath);
            g.set("createdAtEpochMs", r.createdAtEpochMs);
            g.set("expiresAtEpochMs", r.expiresAtEpochMs);
            g.set("retentionUntilEpochMs", r.retentionUntilEpochMs);
            g.set("extractCost", r.extractCost);
            g.set("deathLocation", r.deathLocation);
            g.set("graveLocation", r.graveLocation);
            g.set("hologramEntity", r.hologramEntity);
            g.set("storedExp", r.storedExp);
            g.set("items", r.storedItems);
        }

        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save graves.yml: " + e.getMessage());
        }
    }

    public void tick() {
        long now = System.currentTimeMillis();

        // update blockKey mapping
        byBlockKey.clear();
        for (GraveRecord r : byId.values()) {
            if (!r.virtual) {
                Location bl = r.graveLoc();
                if (bl != null) byBlockKey.put(LocationCodec.blockKey(bl), r.id);
            }
        }

        List<String> toRemove = new ArrayList<>();

        for (GraveRecord r : byId.values()) {
            // expire -> virtualize
            if (!r.virtual && now > r.expiresAtEpochMs) {
                virtualize(r, "expire");
                r.retentionUntilEpochMs = now + plugin.getConfig().getLong("graves.expiredRetentionSeconds", 86400) * 1000L;
            }

            // delete after retention
            if (r.virtual && now > r.retentionUntilEpochMs) {
                toRemove.add(r.id);
                continue;
            }

            // keep physical state sane
            if (!r.virtual) {
                ensurePhysicalState(r);
                updateHologramText(r);
            }
        }

        for (String id : toRemove) {
            GraveRecord r = byId.remove(id);
            if (r != null && audit != null) audit.log("delete", null, r.owner, r.id, r.deathLoc(), "reason=retention_end");
        }

        save();
    }

    private Material graveContainerMaterial() {
        String s = plugin.getConfig().getString("graves.containerMaterial", "BARREL");
        try {
            Material m = Material.valueOf(s.toUpperCase(Locale.ROOT));
            if (m.isBlock() && (m == Material.BARREL || m.name().endsWith("CHEST") || m.name().endsWith("SHULKER_BOX"))) {
                return m;
            }
        } catch (IllegalArgumentException ignored) {}
        return Material.BARREL;
    }

    public void ensurePhysicalState(GraveRecord r) {
        Location bl = r.graveLoc();
        if (bl == null || bl.getWorld() == null) {
            r.virtual = true;
            return;
        }

        Block b = bl.getBlock();
        Material container = graveContainerMaterial();

        if (b.getType() == Material.AIR) {
            b.setType(container, false);
        }

        if (b.getType() != container) {
            // someone broke/changed it -> virtualize
            virtualize(r, "block_changed");
            return;
        }

        if (b.getState() instanceof Container c) {
            Inventory inv = c.getInventory();
            // keep inventory in sync with storedItems (authoritative) on server start only
            if (!inv.isEmpty() && inv.getSize() > 0) {
                // do nothing: players may have already interacted
            } else if (!r.storedItems.isEmpty()) {
                inv.clear();
                for (ItemStack it : r.storedItems) {
                    HashMap<Integer, ItemStack> leftover = inv.addItem(it);
                    if (!leftover.isEmpty()) {
                        // drop leftovers at grave
                        for (ItemStack lf : leftover.values()) {
                            bl.getWorld().dropItemNaturally(bl.clone().add(0.5, 1.0, 0.5), lf);
                        }
                    }
                }
                c.update(true, false);
            }
        }

        // hologram
        if (plugin.getConfig().getBoolean("graves.hologram.enabled", true)) {
            ensureHologram(r);
        }
    }

    private void ensureHologram(GraveRecord r) {
        Location bl = r.graveLoc();
        if (bl == null || bl.getWorld() == null) return;

        UUID holoId = null;
        try { if (r.hologramEntity != null && !r.hologramEntity.isBlank()) holoId = UUID.fromString(r.hologramEntity); } catch (IllegalArgumentException ignored) {}
        Entity existing = (holoId == null) ? null : bl.getWorld().getEntity(holoId);

        if (existing instanceof ArmorStand stand && stand.isValid()) {
            // ok
            updateHologramText(r);
            return;
        }

        Location spawn = bl.clone().add(0.5, 1.2, 0.5);
        ArmorStand stand = bl.getWorld().spawn(spawn, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setMarker(true);
            as.setGravity(false);
            as.setSmall(true);
            as.customName(makeHoloName(r));
            as.setCustomNameVisible(true);
            as.setInvulnerable(true);
            as.setSilent(true);
            as.getPersistentDataContainer().set(keyGraveId, PersistentDataType.STRING, r.id);
        });
        r.hologramEntity = stand.getUniqueId().toString();

        if (audit != null) audit.log("holo_spawn", null, r.owner, r.id, bl, "");
    }

    private Component makeHoloName(GraveRecord r) {
        long now = System.currentTimeMillis();
        long leftSec = Math.max(0, (r.expiresAtEpochMs - now) / 1000L);
        long mm = leftSec / 60;
        long ss = leftSec % 60;
        String time = timeFmt.format(Instant.ofEpochMilli(r.createdAtEpochMs));
        String txt = "Могила " + r.ownerName + " · " + time + " · осталось " + mm + ":" + String.format(Locale.ROOT, "%02d", ss);
        return Component.text(txt);
    }

    public void updateHologramText(GraveRecord r) {
        if (!plugin.getConfig().getBoolean("graves.hologram.enabled", true)) return;
        Location bl = r.graveLoc();
        if (bl == null || bl.getWorld() == null) return;

        UUID holoId = null;
        try { if (r.hologramEntity != null && !r.hologramEntity.isBlank()) holoId = UUID.fromString(r.hologramEntity); } catch (IllegalArgumentException ignored) {}
        if (holoId == null) return;
        Entity e = bl.getWorld().getEntity(holoId);
        if (e instanceof ArmorStand stand && stand.isValid()) {
            stand.customName(makeHoloName(r));
        }
    }

    public GraveRecord createGrave(UUID owner, String ownerName, Location deathLoc, Location graveBlockLoc,
                                  List<ItemStack> items, int exp, boolean pvpDeath) {
        long now = System.currentTimeMillis();

        // enforce max active
        int max = plugin.getConfig().getInt("graves.maxActivePerPlayer", 1);
        if (max <= 0) max = 1;
        List<GraveRecord> existing = new ArrayList<>();
        for (GraveRecord g : byId.values()) {
            if (g.owner != null && g.owner.equals(owner)) existing.add(g);
        }
        existing.sort(Comparator.comparingLong(a -> a.createdAtEpochMs));
        while (existing.size() >= max) {
            GraveRecord oldest = existing.remove(0);
            if (!oldest.virtual) virtualize(oldest, "max_active");
            else {
                byId.remove(oldest.id);
                if (audit != null) audit.log("delete", null, oldest.owner, oldest.id, oldest.deathLoc(), "reason=max_active");
            }
        }

        GraveRecord r = new GraveRecord();
        r.id = UUID.randomUUID().toString();
        r.owner = owner;
        r.ownerName = ownerName;
        r.virtual = (graveBlockLoc == null);
        r.paid = false;
        r.pvpDeath = pvpDeath;
        r.createdAtEpochMs = now;
        long lifetime = plugin.getConfig().getLong("graves.lifetimeSeconds", 1800);
        r.expiresAtEpochMs = now + Math.max(60, lifetime) * 1000L;
        long retention = plugin.getConfig().getLong("graves.expiredRetentionSeconds", 86400);
        r.retentionUntilEpochMs = r.expiresAtEpochMs + Math.max(600, retention) * 1000L;
        r.extractCost = baseExtractCost(r);
        r.deathLocation = LocationCodec.encode(deathLoc);
        r.graveLocation = (graveBlockLoc == null) ? "" : LocationCodec.encode(graveBlockLoc);
        r.storedExp = exp;
        r.storedItems = new ArrayList<>();
        for (ItemStack it : items) {
            if (it != null && it.getType() != Material.AIR) r.storedItems.add(it);
        }

        byId.put(r.id, r);
        if (!r.virtual) {
            byBlockKey.put(LocationCodec.blockKey(graveBlockLoc), r.id);
            placeContainerAndFill(r);
            ensureHologram(r);
        }

        if (audit != null) audit.log("create", null, owner, r.id, graveBlockLoc != null ? graveBlockLoc : deathLoc,
                "virtual=" + r.virtual + " items=" + r.storedItems.size() + " exp=" + r.storedExp + " pvp=" + pvpDeath);

        save();
        return r;
    }

    private void placeContainerAndFill(GraveRecord r) {
        Location bl = r.graveLoc();
        if (bl == null || bl.getWorld() == null) return;
        Block b = bl.getBlock();
        Material container = graveContainerMaterial();
        b.setType(container, false);
        if (b.getState() instanceof Container c) {
            Inventory inv = c.getInventory();
            inv.clear();
            for (ItemStack it : r.storedItems) {
                if (it == null || it.getType() == Material.AIR) continue;
                HashMap<Integer, ItemStack> leftover = inv.addItem(it);
                if (!leftover.isEmpty()) {
                    for (ItemStack lf : leftover.values()) {
                        bl.getWorld().dropItemNaturally(bl.clone().add(0.5, 1.0, 0.5), lf);
                    }
                }
            }
            c.update(true, false);
        }
    }

    public void syncFromContainer(GraveRecord r) {
        if (r == null || r.virtual) return;
        Location bl = r.graveLoc();
        if (bl == null || bl.getWorld() == null) return;
        Block b = bl.getBlock();
        if (!(b.getState() instanceof Container c)) return;

        Inventory inv = c.getInventory();
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack it : inv.getContents()) {
            if (it != null && it.getType() != Material.AIR) items.add(it.clone());
        }
        r.storedItems = items;
        save();
    }

    public boolean isContainerEmpty(GraveRecord r) {
        if (r == null || r.virtual) return true;
        Location bl = r.graveLoc();
        if (bl == null || bl.getWorld() == null) return true;
        Block b = bl.getBlock();
        if (!(b.getState() instanceof Container c)) return true;
        Inventory inv = c.getInventory();
        for (ItemStack it : inv.getContents()) {
            if (it != null && it.getType() != Material.AIR) return false;
        }
        return true;
    }

    public void removeGrave(GraveRecord r, String reason) {
        if (r == null) return;
        if (!r.virtual) {
            // remove block
            Location bl = r.graveLoc();
            if (bl != null && bl.getWorld() != null) {
                Block b = bl.getBlock();
                if (b.getType() != Material.AIR) b.setType(Material.AIR, false);
            }
            removeHologram(r);
        }
        byId.remove(r.id);
        if (audit != null) audit.log("remove", null, r.owner, r.id, r.deathLoc(), "reason=" + reason);
        save();
    }

    public void removeHologram(GraveRecord r) {
        Location bl = r.graveLoc();
        if (bl == null || bl.getWorld() == null) return;
        UUID holoId = null;
        try { if (r.hologramEntity != null && !r.hologramEntity.isBlank()) holoId = UUID.fromString(r.hologramEntity); } catch (IllegalArgumentException ignored) {}
        if (holoId == null) return;
        Entity e = bl.getWorld().getEntity(holoId);
        if (e != null) e.remove();
        r.hologramEntity = "";
    }

    public void virtualize(GraveRecord r, String reason) {
        if (r == null || r.virtual) return;

        // pull items from container
        try {
            syncFromContainer(r);
        } catch (Exception ignored) {}

        // remove physical block and holo
        Location bl = r.graveLoc();
        if (bl != null && bl.getWorld() != null) {
            Block b = bl.getBlock();
            if (b.getType() != Material.AIR) b.setType(Material.AIR, false);
        }
        removeHologram(r);

        r.virtual = true;
        r.graveLocation = "";

        if (audit != null) audit.log("virtualize", null, r.owner, r.id, r.deathLoc(), "reason=" + reason);
        save();
    }
}
