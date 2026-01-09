package ru.letopis.dungeon.room;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import ru.letopis.dungeon.core.DungeonBuilder;

import java.util.Random;
import java.util.UUID;

public final class BossRoom implements Room {

    private final DungeonBuilder builder;
    private final String name;
    private final EntityType bossType;
    private final double bossHealth;
    private final double damageMultiplier;
    private final int minionCount;
    private final EntityType minionType;
    private UUID bossId;
    private boolean phaseTwo = false;
    private boolean complete = false;
    private double progress = 0.0;

    public BossRoom(DungeonBuilder builder, String name, ConfigurationSection config) {
        this.builder = builder;
        this.name = name;
        this.bossType = parseEntity(config.getString("type", "PIGLIN_BRUTE"));
        this.bossHealth = config.getDouble("health", 220.0);
        this.damageMultiplier = config.getDouble("damageMultiplier", 1.4);
        this.minionCount = config.getInt("phaseTwoMinions", 4);
        this.minionType = parseEntity(config.getString("minionType", "ZOMBIE"));
    }

    @Override
    public String name() { return name; }

    @Override
    public String objective() { return "Победите босса"; }

    @Override
    public void build(RoomContext context) {
        builder.buildRoomBase(context.world(), context.origin(), context.sizeX(), context.sizeY(), context.sizeZ(),
                Material.BLACKSTONE, Material.POLISHED_BLACKSTONE_BRICKS, Material.BLACKSTONE, Material.SOUL_LANTERN);
        builder.buildPillars(context.world(), context.origin(), context.sizeX(), context.sizeY(), context.sizeZ(),
                Material.GILDED_BLACKSTONE);
    }

    @Override
    public void start(RoomContext context) {
        phaseTwo = false;
        complete = false;
        progress = 0.0;
        spawnBoss(context);
    }

    @Override
    public void tick(RoomContext context) {
        if (complete) return;
        Entity entity = bossId != null ? context.world().getEntity(bossId) : null;
        if (entity == null || entity.isDead()) {
            complete = true;
            progress = 1.0;
            return;
        }
        if (entity instanceof LivingEntity living) {
            double health = living.getHealth();
            double maxHealth = living.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
            progress = maxHealth > 0 ? Math.max(0.0, health / maxHealth) : 0.0;
            if (!phaseTwo && health <= maxHealth * 0.5) {
                phaseTwo = true;
                living.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, living.getLocation(), 60, 1, 1, 1, 0.1);
                living.getWorld().playSound(living.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.8f);
                spawnMinions(context);
                context.manager().announceBossPhase(context.session());
            }
        }
    }

    @Override
    public boolean isComplete() { return complete; }

    @Override
    public double progress() {
        return complete ? 1.0 : progress;
    }

    @Override
    public void onPlayerInteract(RoomContext context, PlayerInteractEvent event) {
    }

    @Override
    public void cleanup(RoomContext context) {
    }

    private void spawnBoss(RoomContext context) {
        Location spawn = context.origin().clone().add(context.sizeX() / 2.0, 1, context.sizeZ() / 2.0);
        Entity entity = context.world().spawnEntity(spawn, bossType);
        context.manager().tagEntity(entity, context.session(), "boss");
        bossId = entity.getUniqueId();
        if (entity instanceof LivingEntity living) {
            living.customName(context.manager().bossName());
            living.setCustomNameVisible(true);
            if (living.getAttribute(Attribute.MAX_HEALTH) != null) {
                living.getAttribute(Attribute.MAX_HEALTH).setBaseValue(bossHealth);
                living.setHealth(bossHealth);
            }
            if (living.getAttribute(Attribute.ATTACK_DAMAGE) != null) {
                double base = living.getAttribute(Attribute.ATTACK_DAMAGE).getBaseValue();
                living.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(base * damageMultiplier);
            }
        }
    }

    private void spawnMinions(RoomContext context) {
        Random random = new Random();
        for (int i = 0; i < minionCount; i++) {
            Location loc = context.origin().clone().add(3 + random.nextInt(context.sizeX() - 6), 1,
                    3 + random.nextInt(context.sizeZ() - 6));
            Entity entity = context.world().spawnEntity(loc, minionType);
            context.manager().tagEntity(entity, context.session(), "minion");
        }
        for (Player player : context.world().getPlayers()) {
            if (context.session().region().contains(player.getLocation())) {
                player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 0.6f, 0.8f);
            }
        }
    }

    private EntityType parseEntity(String name) {
        try {
            return EntityType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return EntityType.PIGLIN_BRUTE;
        }
    }
}
