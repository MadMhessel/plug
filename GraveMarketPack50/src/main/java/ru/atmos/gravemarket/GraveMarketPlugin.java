package ru.atmos.gravemarket;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import ru.atmos.gravemarket.cmd.GraveAdminCommand;
import ru.atmos.gravemarket.cmd.GraveCommand;
import ru.atmos.gravemarket.econ.EconomyService;
import ru.atmos.gravemarket.econ.InternalEconomy;
import ru.atmos.gravemarket.econ.TeleportMarketEconomy;
import ru.atmos.gravemarket.grave.GraveManager;
import ru.atmos.gravemarket.grave.InsuranceStore;
import ru.atmos.gravemarket.grave.ReturnStore;
import ru.atmos.gravemarket.listener.BlockListener;
import ru.atmos.gravemarket.listener.CombatListener;
import ru.atmos.gravemarket.listener.DeathListener;
import ru.atmos.gravemarket.listener.InventoryListener;
import ru.atmos.gravemarket.listener.InteractListener;
import ru.atmos.gravemarket.listener.ReturnListener;
import ru.atmos.gravemarket.util.AuditLog;
import ru.atmos.gravemarket.util.CombatTracker;
import ru.atmos.gravemarket.util.LocationCodec;

import java.util.Optional;
import java.util.logging.Level;

public final class GraveMarketPlugin extends JavaPlugin {

    private EconomyService economy;
    private GraveManager graveManager;
    private AuditLog audit;
    private ReturnStore returnStore;
    private InsuranceStore insuranceStore;
    private CombatTracker combatTracker;
    private NamespacedKey borrowOwnerKey;
    private NamespacedKey borrowGraveKey;

    public EconomyService economy() { return economy; }
    public GraveManager graves() { return graveManager; }
    public AuditLog audit() { return audit; }
    public ReturnStore returns() { return returnStore; }
    public InsuranceStore insurance() { return insuranceStore; }
    public CombatTracker combat() { return combatTracker; }
    public NamespacedKey borrowOwnerKey() { return borrowOwnerKey; }
    public NamespacedKey borrowGraveKey() { return borrowGraveKey; }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.audit = new AuditLog(this);

        // Economy bridge
        boolean require = getConfig().getBoolean("integration.requireTeleportMarket", false);
        Optional<EconomyService> service = Optional.ofNullable(Bukkit.getServicesManager()
                .getRegistration(EconomyService.class)).map(r -> r.getProvider());
        if (service.isPresent()) {
            this.economy = service.get();
            getLogger().info("Economy: using EconomyService from Bukkit ServicesManager.");
        } else {
            Optional<EconomyService> bridge = TeleportMarketEconomy.tryCreate(this).map(e -> (EconomyService) e);
            if (bridge.isPresent()) {
                this.economy = bridge.get();
                getLogger().info("Economy: using TeleportMarket bridge (shared credits).");
            } else {
                if (require) {
                    getLogger().severe("TeleportMarket not found or incompatible, and integration.requireTeleportMarket=true. Disabling.");
                    Bukkit.getPluginManager().disablePlugin(this);
                    return;
                }
                this.economy = new InternalEconomy(this);
                getLogger().warning("Economy: TeleportMarket not found/incompatible. Using internal economy fallback (gravemarket-economy.yml).");
            }
        }

        this.graveManager = new GraveManager(this);
        try {
            graveManager.load();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to load graves.yml", e);
        }

        this.returnStore = new ReturnStore(this);
        try {
            returnStore.load();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to load returns.yml", e);
        }
        this.insuranceStore = new InsuranceStore(this);
        try {
            insuranceStore.load();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to load insurance.yml", e);
        }
        this.combatTracker = new CombatTracker();

        this.borrowOwnerKey = new NamespacedKey(this, "borrow_owner");
        this.borrowGraveKey = new NamespacedKey(this, "borrow_grave");

        // Commands
        GraveCommand graveCmd = new GraveCommand(this);
        getCommand("grave").setExecutor(graveCmd);
        getCommand("grave").setTabCompleter(graveCmd);

        GraveAdminCommand adminCmd = new GraveAdminCommand(this);
        getCommand("graveadmin").setExecutor(adminCmd);
        getCommand("graveadmin").setTabCompleter(adminCmd);

        // Listeners
        Bukkit.getPluginManager().registerEvents(new DeathListener(this), this);
        Bukkit.getPluginManager().registerEvents(new InteractListener(this), this);
        Bukkit.getPluginManager().registerEvents(new InventoryListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BlockListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ReturnListener(this), this);
        Bukkit.getPluginManager().registerEvents(new CombatListener(this), this);

        // Periodic expiry / holo refresh
        long periodTicks = 20L * 30; // every 30s
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            try { graveManager.tick(); } catch (Exception e) { getLogger().log(Level.WARNING, "tick() failed", e); }
        }, periodTicks, periodTicks);

        // Altar default in config (optional)
        String altar = getConfig().getString("altar.location", "");
        if (altar != null && !altar.isBlank()) {
            Location loc = LocationCodec.decode(altar);
            if (loc != null) {
                getLogger().info("Altar location loaded: " + LocationCodec.pretty(loc));
            }
        }

        getLogger().info("GraveMarket enabled.");
    }

    @Override
    public void onDisable() {
        if (graveManager != null) {
            try { graveManager.save(); } catch (Exception e) { getLogger().log(Level.SEVERE, "Failed to save graves.yml", e); }
        }
        if (returnStore != null) {
            try { returnStore.save(); } catch (Exception e) { getLogger().log(Level.SEVERE, "Failed to save returns.yml", e); }
        }
        if (insuranceStore != null) {
            try { insuranceStore.save(); } catch (Exception e) { getLogger().log(Level.SEVERE, "Failed to save insurance.yml", e); }
        }
        if (audit != null) audit.close();
    }

    public Plugin teleportMarketPlugin() {
        return Bukkit.getPluginManager().getPlugin("TeleportMarket");
    }
}
