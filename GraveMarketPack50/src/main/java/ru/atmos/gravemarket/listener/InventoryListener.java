package ru.atmos.gravemarket.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.atmos.gravemarket.GraveMarketPlugin;
import ru.atmos.gravemarket.grave.GraveRecord;

public final class InventoryListener implements Listener {

    private final GraveMarketPlugin plugin;

    public InventoryListener(GraveMarketPlugin plugin) {
        this.plugin = plugin;
    }

    private GraveRecord graveByTop(Inventory top) {
        if (top == null) return null;
        Location loc = top.getLocation();
        if (loc == null) return null;
        return plugin.graves().findByBlock(loc);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof org.bukkit.entity.Player p)) return;

        Inventory top = e.getView().getTopInventory();
        GraveRecord g = graveByTop(top);
        if (g == null) return;

        boolean auth = plugin.graves().isAuthorized(g, p.getUniqueId());
        if (!auth) {
            e.setCancelled(true);
            p.closeInventory();
            p.sendMessage(Component.text("§6[Могила] §cДоступ запрещён."));
            return;
        }

        boolean isOwner = p.getUniqueId().equals(g.owner);
        boolean isTrusted = !isOwner;

        if (!g.paid) {
            e.setCancelled(true);
            p.closeInventory();
            p.sendMessage(Component.text("§6[Могила] §eСначала оплатите извлечение: /grave pay"));
            return;
        }

        // Prevent putting items INTO grave
        Inventory clicked = e.getClickedInventory();
        InventoryAction action = e.getAction();

        boolean clickedTop = clicked != null && clicked.equals(top);
        boolean clickedBottom = clicked != null && clicked.equals(e.getView().getBottomInventory());

        // Any shift-click from bottom -> top is blocked
        if (clickedBottom && action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            e.setCancelled(true);
            return;
        }

        if (clickedTop) {
            if (isTrusted && shouldTagBorrowed(action)) {
                ItemStack current = e.getCurrentItem();
                if (current != null) {
                    tagBorrowed(current, g);
                    if (plugin.audit() != null) {
                        plugin.audit().log("take_item", p.getUniqueId(), g.owner, g.id, g.graveLoc(),
                                "item=" + current.getType() + " amount=" + current.getAmount());
                    }
                }
            }
            // allow taking out, block placing in / swapping in
            switch (action) {
                case PLACE_ALL, PLACE_ONE, PLACE_SOME,
                        SWAP_WITH_CURSOR, HOTBAR_SWAP, HOTBAR_MOVE_AND_READD,
                        MOVE_TO_OTHER_INVENTORY, COLLECT_TO_CURSOR,
                        DROP_ALL_CURSOR, DROP_ONE_CURSOR,
                        DROP_ALL_SLOT, DROP_ONE_SLOT,
                        CLONE_STACK, UNKNOWN -> {
                    // MOVE_TO_OTHER_INVENTORY from top is taking out -> allow
                    if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) return;
                    // Drops from grave are allowed (it is still extraction) -> allow
                    if (action == InventoryAction.DROP_ALL_SLOT || action == InventoryAction.DROP_ONE_SLOT) return;

                    // placing/swap into top -> cancel
                    if (action == InventoryAction.PLACE_ALL || action == InventoryAction.PLACE_ONE || action == InventoryAction.PLACE_SOME
                            || action == InventoryAction.SWAP_WITH_CURSOR || action == InventoryAction.HOTBAR_SWAP || action == InventoryAction.HOTBAR_MOVE_AND_READD
                            || action == InventoryAction.COLLECT_TO_CURSOR || action == InventoryAction.CLONE_STACK) {
                        e.setCancelled(true);
                    }
                }
                default -> { /* ok */ }
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        GraveRecord g = graveByTop(top);
        if (g == null) return;

        int topSize = top.getSize();
        for (int raw : e.getRawSlots()) {
            if (raw < topSize) {
                // drag affects top inventory -> cancel (no storage abuse)
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        Inventory top = e.getView().getTopInventory();
        GraveRecord g = graveByTop(top);
        if (g == null) return;

        // sync and cleanup
        plugin.graves().syncFromContainer(g);

        if (g.paid && plugin.graves().isContainerEmpty(g)) {
            plugin.graves().removeGrave(g, "emptied");
            if (e.getPlayer() instanceof org.bukkit.entity.Player p) {
                p.sendMessage(Component.text("§6[Могила] §aМогила очищена. Спасибо, что не сделали из неё склад :)"));
            }
        }
    }

    private boolean shouldTagBorrowed(InventoryAction action) {
        return switch (action) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME,
                    MOVE_TO_OTHER_INVENTORY, HOTBAR_SWAP, HOTBAR_MOVE_AND_READD,
                    DROP_ALL_SLOT, DROP_ONE_SLOT -> true;
            default -> false;
        };
    }

    private void tagBorrowed(ItemStack item, GraveRecord g) {
        if (g == null || g.owner == null || item == null || item.getType().isAir()) return;
        var meta = item.getItemMeta();
        if (meta == null) return;
        var pdc = meta.getPersistentDataContainer();
        pdc.set(plugin.borrowOwnerKey(), org.bukkit.persistence.PersistentDataType.STRING, g.owner.toString());
        pdc.set(plugin.borrowGraveKey(), org.bukkit.persistence.PersistentDataType.STRING, g.id);
        item.setItemMeta(meta);
    }
}
