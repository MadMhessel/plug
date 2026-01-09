package ru.atmos.gravemarket.econ;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Best-effort bridge to TeleportMarket shared credits.
 * Tries multiple method names to be resilient.
 */
public final class TeleportMarketEconomy implements Economy {

    private final JavaPlugin plugin;
    private final Object store;
    private final Method mGet;
    private final Method mTake;
    private final Method mAdd;

    private TeleportMarketEconomy(JavaPlugin plugin, Object store, Method mGet, Method mTake, Method mAdd) {
        this.plugin = plugin;
        this.store = store;
        this.mGet = mGet;
        this.mTake = mTake;
        this.mAdd = mAdd;
    }

    public static Optional<Economy> tryCreate(JavaPlugin plugin) {
        Plugin tp = Bukkit.getPluginManager().getPlugin("TeleportMarket");
        if (tp == null || !tp.isEnabled()) return Optional.empty();

        try {
            Object store = null;

            for (String methodName : new String[]{ "store", "getStore", "dataStore", "getDataStore" }) {
                try {
                    Method m = tp.getClass().getMethod(methodName);
                    store = m.invoke(tp);
                    if (store != null) break;
                } catch (NoSuchMethodException ignored) {}
            }

            if (store == null) {
                for (String fieldName : new String[]{ "store", "dataStore", "datastore" }) {
                    try {
                        var f = tp.getClass().getDeclaredField(fieldName);
                        f.setAccessible(true);
                        store = f.get(tp);
                        if (store != null) break;
                    } catch (NoSuchFieldException ignored) {}
                }
            }

            if (store == null) return Optional.empty();

            Method mGet = findMethod(store.getClass(), new String[]{ "getBalance", "balance" }, UUID.class);
            Method mTake = findMethod(store.getClass(), new String[]{ "takeBalance", "withdraw", "take" }, UUID.class, long.class);
            Method mAdd = findMethod(store.getClass(), new String[]{ "addBalance", "deposit", "add" }, UUID.class, long.class);

            if (mGet == null || mTake == null || mAdd == null) return Optional.empty();

            mGet.setAccessible(true);
            mTake.setAccessible(true);
            mAdd.setAccessible(true);

            return Optional.of(new TeleportMarketEconomy(plugin, store, mGet, mTake, mAdd));
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "TeleportMarket bridge init failed: " + t.getMessage());
            return Optional.empty();
        }
    }

    private static Method findMethod(Class<?> cls, String[] names, Class<?>... params) {
        for (String n : names) {
            try { return cls.getMethod(n, params); } catch (NoSuchMethodException ignored) {}
        }
        for (String n : names) {
            try { return cls.getDeclaredMethod(n, params); } catch (NoSuchMethodException ignored) {}
        }
        return null;
    }

    @Override
    public long balance(UUID playerId) {
        try {
            Object r = mGet.invoke(store, playerId);
            if (r instanceof Number num) return num.longValue();
        } catch (Throwable t) {
            plugin.getLogger().warning("TeleportMarket balance() failed: " + t.getMessage());
        }
        return 0L;
    }

    @Override
    public boolean withdraw(UUID playerId, long amount) {
        if (amount <= 0) return true;
        try {
            Object r = mTake.invoke(store, playerId, amount);
            if (r instanceof Boolean b) return b;
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("TeleportMarket withdraw() failed: " + t.getMessage());
            return false;
        }
    }

    @Override
    public void deposit(UUID playerId, long amount) {
        if (amount <= 0) return;
        try {
            mAdd.invoke(store, playerId, amount);
        } catch (Throwable t) {
            plugin.getLogger().warning("TeleportMarket deposit() failed: " + t.getMessage());
        }
    }

    @Override
    public String currencyName() {
        return "кредиты";
    }
}
