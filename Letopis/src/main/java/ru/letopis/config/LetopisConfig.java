package ru.letopis.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import ru.letopis.model.Scale;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class LetopisConfig {
    public List<String> worlds = new ArrayList<>();
    public int globalCooldownMinutesAfterEvent = 15;
    public int safeDistanceFromSpawn = 150;
    public int eventRadius = 80;
    public int maxLocationAttempts = 20;

    public int decayPerHour = 20;
    public int decayExtraPerHour = 5;

    public int thresholdOmen = 250;
    public int thresholdEvent = 500;
    public int thresholdBoss = 750;
    public int thresholdMax = 1000;

    public final Map<Scale, Integer> maxPerMinute = new EnumMap<>(Scale.class);
    public int noiseSameChunkCooldown = 20;
    public double noiseSameChunkMultiplier = 0.35;
    public int groveSameChunkWindow = 120;
    public double groveSameChunkMultiplier = 0.6;
    public int riftPortalCooldownSeconds = 120;

    public double noiseCreeper = 12;
    public double noiseTnt = 10;
    public double noiseBed = 14;
    public double noiseAnchor = 14;
    public double noiseCrystal = 20;
    public double noiseWither = 60;
    public double noiseDeathBonus = 6;
    public int noiseDeathBonusCooldown = 30;

    public double ashFurnace = 0.20;
    public double ashBlast = 0.25;
    public double ashSmoker = 0.15;
    public double ashCampfire = 0.10;
    public double ashLavaBonus = 0.05;
    public boolean detectLavaFuel = false;

    public double groveLog = 0.60;
    public double groveLeaves = 0.20;
    public double groveSapling = 0.30;
    public double groveIgnite = 2.0;
    public boolean groveTrackIgnite = true;
    public boolean groveTrackAnimal = false;
    public double groveAnimal = 1.0;
    public double groveSaplingReduction = 0.40;
    public double groveBonemealReduction = 0.20;
    public boolean groveTrackBonemeal = false;

    public double riftPortal = 3.0;
    public double riftDeathNether = 15;
    public double riftDeathEnd = 20;
    public double riftPortalCreate = 10;
    public boolean riftTrackPortalCreate = true;
    public double riftRespawnUse = 3;
    public boolean riftTrackRespawnUse = false;

    public int omenCount = 3;
    public int omenDelaySeconds = 420;

    public final Map<Scale, Integer> eventDuration = new EnumMap<>(Scale.class);

    public double scalingHp = 0.35;
    public double scalingDmg = 0.20;
    public int scalingWaveAdd = 2;

    public boolean ritualEnabled = true;
    public int ritualCooldownMinutes = 30;
    public Material altarLectern = Material.LECTERN;
    public Material altarBase = Material.CHISELED_STONE_BRICKS;
    public String altarCandleTag = "CANDLES";
    public final Map<Scale, List<ItemStackConfig>> offerings = new EnumMap<>(Scale.class);
    public final Map<Scale, Integer> offeringReduce = new EnumMap<>(Scale.class);

    public String rewardSealDropMode = "PER_EVENT_RANDOM";
    public double rewardTitleChanceNormal = 0.15;
    public double rewardTitleChanceBoss = 0.25;
    public boolean rewardBuffEnabled = true;
    public String rewardBuffType = "XP_BOOST";
    public int rewardBuffDurationMinutes = 30;
    public double rewardBuffValue = 0.05;

    public void load(FileConfiguration config) {
        worlds = config.getStringList("general.worlds");
        globalCooldownMinutesAfterEvent = config.getInt("general.globalCooldownMinutesAfterEvent", 15);
        safeDistanceFromSpawn = config.getInt("general.safeDistanceFromSpawn", 150);
        eventRadius = config.getInt("general.eventRadius", 80);
        maxLocationAttempts = config.getInt("general.maxLocationAttempts", 20);

        decayPerHour = config.getInt("decay.perHour", 20);
        decayExtraPerHour = config.getInt("decay.extraPerHourWhenCalm", 5);

        thresholdOmen = config.getInt("thresholds.omen", 250);
        thresholdEvent = config.getInt("thresholds.event", 500);
        thresholdBoss = config.getInt("thresholds.boss", 750);
        thresholdMax = config.getInt("thresholds.max", 1000);

        maxPerMinute.put(Scale.NOISE, config.getInt("limits.noise.maxPerMinutePerPlayer", 60));
        maxPerMinute.put(Scale.ASH, config.getInt("limits.ash.maxPerMinutePerPlayer", 40));
        maxPerMinute.put(Scale.GROVE, config.getInt("limits.grove.maxPerMinutePerPlayer", 50));
        maxPerMinute.put(Scale.RIFT, config.getInt("limits.rift.maxPerMinutePerPlayer", 25));
        noiseSameChunkCooldown = config.getInt("limits.noise.cooldownSameChunkSeconds", 20);
        noiseSameChunkMultiplier = config.getDouble("limits.noise.sameChunkMultiplier", 0.35);
        groveSameChunkWindow = config.getInt("limits.grove.sameChunkWindowSeconds", 120);
        groveSameChunkMultiplier = config.getDouble("limits.grove.sameChunkMultiplier", 0.6);
        riftPortalCooldownSeconds = config.getInt("limits.rift.portalCooldownSeconds", 120);

        noiseCreeper = config.getDouble("points.noise.creeperExplosion", 12);
        noiseTnt = config.getDouble("points.noise.tntExplosion", 10);
        noiseBed = config.getDouble("points.noise.bedExplosion", 14);
        noiseAnchor = config.getDouble("points.noise.anchorExplosion", 14);
        noiseCrystal = config.getDouble("points.noise.endCrystalExplosion", 20);
        noiseWither = config.getDouble("points.noise.witherSpawn", 60);
        noiseDeathBonus = config.getDouble("points.noise.playerDeathByExplosionBonus", 6);
        noiseDeathBonusCooldown = config.getInt("points.noise.deathByExplosionBonusCooldownSeconds", 30);

        ashFurnace = config.getDouble("points.ash.furnace", 0.20);
        ashBlast = config.getDouble("points.ash.blastFurnace", 0.25);
        ashSmoker = config.getDouble("points.ash.smoker", 0.15);
        ashCampfire = config.getDouble("points.ash.campfire", 0.10);
        ashLavaBonus = config.getDouble("points.ash.lavaFuelBonus", 0.05);
        detectLavaFuel = config.getBoolean("points.ash.detectLavaFuel", false);

        groveLog = config.getDouble("points.grove.logBreak", 0.60);
        groveLeaves = config.getDouble("points.grove.leavesBreak", 0.20);
        groveSapling = config.getDouble("points.grove.saplingBreak", 0.30);
        groveIgnite = config.getDouble("points.grove.igniteTree", 2.0);
        groveTrackIgnite = config.getBoolean("points.grove.trackIgnite", true);
        groveTrackAnimal = config.getBoolean("points.grove.trackAnimalKill", false);
        groveAnimal = config.getDouble("points.grove.animalKill", 1.0);
        groveSaplingReduction = config.getDouble("points.grove.saplingPlaceReduction", 0.40);
        groveBonemealReduction = config.getDouble("points.grove.bonemealReduction", 0.20);
        groveTrackBonemeal = config.getBoolean("points.grove.trackBonemeal", false);

        riftPortal = config.getDouble("points.rift.portalTravel", 3.0);
        riftDeathNether = config.getDouble("points.rift.deathNether", 15);
        riftDeathEnd = config.getDouble("points.rift.deathEnd", 20);
        riftPortalCreate = config.getDouble("points.rift.portalCreate", 10);
        riftTrackPortalCreate = config.getBoolean("points.rift.trackPortalCreate", true);
        riftRespawnUse = config.getDouble("points.rift.respawnAnchorUse", 3.0);
        riftTrackRespawnUse = config.getBoolean("points.rift.trackRespawnAnchorUse", false);

        omenCount = config.getInt("omens.count", 3);
        omenDelaySeconds = config.getInt("omens.delaySeconds", 420);

        eventDuration.put(Scale.NOISE, config.getInt("events.noise.durationSeconds", 480));
        eventDuration.put(Scale.ASH, config.getInt("events.ash.durationSeconds", 600));
        eventDuration.put(Scale.GROVE, config.getInt("events.grove.durationSeconds", 480));
        eventDuration.put(Scale.RIFT, config.getInt("events.rift.durationSeconds", 420));

        scalingHp = config.getDouble("scaling.hpPerExtraPlayer", 0.35);
        scalingDmg = config.getDouble("scaling.dmgPerExtraPlayer", 0.20);
        scalingWaveAdd = config.getInt("scaling.waveAddPerExtraPlayer", 2);

        ritualEnabled = config.getBoolean("ritual.enabled", true);
        ritualCooldownMinutes = config.getInt("ritual.cooldownMinutes", 30);
        Material lectern = Material.matchMaterial(config.getString("ritual.altar.lecternBlock", "LECTERN"));
        Material base = Material.matchMaterial(config.getString("ritual.altar.baseBlock", "CHISELED_STONE_BRICKS"));
        altarLectern = lectern == null ? Material.LECTERN : lectern;
        altarBase = base == null ? Material.CHISELED_STONE_BRICKS : base;
        altarCandleTag = config.getString("ritual.altar.candleBlockTag", "CANDLES");

        offerings.clear();
        offeringReduce.clear();
        for (Scale scale : Scale.values()) {
            ConfigurationSection section = config.getConfigurationSection("ritual.offerings." + scale.key());
            if (section == null) continue;
            List<ItemStackConfig> items = new ArrayList<>();
            for (Object obj : section.getList("items", new ArrayList<>())) {
                if (obj instanceof java.util.Map<?, ?> map) {
                    Object matName = map.get("material");
                    Object amount = map.get("amount");
                    Material material = matName == null ? null : Material.matchMaterial(String.valueOf(matName));
                    int amt = amount == null ? 1 : Integer.parseInt(String.valueOf(amount));
                    if (material != null) {
                        items.add(new ItemStackConfig(material, amt));
                    }
                }
            }
            offerings.put(scale, items);
            offeringReduce.put(scale, section.getInt("reduce", 100));
        }

        rewardSealDropMode = config.getString("rewards.sealDropMode", "PER_EVENT_RANDOM");
        rewardTitleChanceNormal = config.getDouble("rewards.titleChanceNormal", 0.15);
        rewardTitleChanceBoss = config.getDouble("rewards.titleChanceBoss", 0.25);
        rewardBuffEnabled = config.getBoolean("rewards.utilityBuffEnabled", true);
        rewardBuffType = config.getString("rewards.utilityBuffType", "XP_BOOST");
        rewardBuffDurationMinutes = config.getInt("rewards.utilityBuffDurationMinutes", 30);
        rewardBuffValue = config.getDouble("rewards.utilityBuffValue", 0.05);
    }

    public record ItemStackConfig(Material material, int amount) {}
}
