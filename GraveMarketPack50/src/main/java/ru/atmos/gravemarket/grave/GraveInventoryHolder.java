package ru.atmos.gravemarket.grave;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class GraveInventoryHolder implements InventoryHolder {
    private final String graveId;
    private final int page;

    public GraveInventoryHolder(String graveId, int page) {
        this.graveId = graveId;
        this.page = page;
    }

    public String graveId() {
        return graveId;
    }

    public int page() {
        return page;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
