package ru.letopis.ritual;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Lectern;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Tag;
import ru.letopis.LetopisManager;
import ru.letopis.config.LetopisConfig;
import ru.letopis.model.JournalEntry;
import ru.letopis.model.Scale;
import ru.letopis.model.WorldState;

import java.time.Instant;
import java.util.*;

public final class RitualManager implements Listener {
    private final LetopisManager manager;
    private final LetopisConfig config;
    private FileConfiguration messages;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();

    public RitualManager(LetopisManager manager, LetopisConfig config, FileConfiguration messages) {
        this.manager = manager;
        this.config = config;
        this.messages = messages;
    }

    public void reloadMessages(FileConfiguration messages) {
        this.messages = messages;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!config.ritualEnabled) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        Block block = event.getClickedBlock();
        if (block.getType() != config.altarLectern) return;
        if (!(block.getState() instanceof Lectern lectern)) return;
        if (!isValidAltar(block)) return;
        ItemStack book = lectern.getInventory().getItem(0);
        if (book == null || !book.hasItemMeta()) return;
        if (!ChatColor.stripColor(book.getItemMeta().getDisplayName()).equalsIgnoreCase("Кодекс Летописи")) return;
        openMenu(event.getPlayer(), block.getLocation());
    }

    private boolean isValidAltar(Block lectern) {
        Block base = lectern.getRelative(0, -1, 0);
        if (base.getType() != config.altarBase) return false;
        Block north = lectern.getRelative(0, 0, -1);
        Block south = lectern.getRelative(0, 0, 1);
        Block east = lectern.getRelative(1, 0, 0);
        Block west = lectern.getRelative(-1, 0, 0);
        Tag<Material> candleTag = Tag.CANDLES;
        return candleTag.isTagged(north.getType()) && candleTag.isTagged(south.getType())
            && candleTag.isTagged(east.getType()) && candleTag.isTagged(west.getType());
    }

    private void openMenu(Player player, Location altarLoc) {
        String worldName = altarLoc.getWorld().getName();
        long now = Instant.now().getEpochSecond();
        long cooldownUntil = cooldowns.getOrDefault(worldName, 0L);
        if (now < cooldownUntil) {
            player.sendMessage(prefix() + "§cАлтарь отдыхает ещё " + ((cooldownUntil - now) / 60) + " мин.");
            return;
        }
        Inventory inventory = Bukkit.createInventory(player, 27, "Успокоить мир");
        inventory.setItem(10, scaleItem(Scale.NOISE));
        inventory.setItem(12, scaleItem(Scale.ASH));
        inventory.setItem(14, scaleItem(Scale.GROVE));
        inventory.setItem(16, scaleItem(Scale.RIFT));
        inventory.setItem(22, confirmItem());
        sessions.put(player.getUniqueId(), new Session(altarLoc, inventory));
        player.openInventory(inventory);
    }

    private ItemStack scaleItem(Scale scale) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(scale.chatColor() + scale.displayName());
        List<String> lore = new ArrayList<>();
        for (LetopisConfig.ItemStackConfig item : config.offerings.getOrDefault(scale, List.of())) {
            lore.add("§7" + item.material().name() + " x" + item.amount());
        }
        lore.add("§eСнижение: -" + config.offeringReduce.getOrDefault(scale, 100));
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack confirmItem() {
        ItemStack stack = new ItemStack(Material.EMERALD);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName("§aПодтвердить ритуал");
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (!event.getInventory().equals(session.inventory)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 10) session.selected = Scale.NOISE;
        if (slot == 12) session.selected = Scale.ASH;
        if (slot == 14) session.selected = Scale.GROVE;
        if (slot == 16) session.selected = Scale.RIFT;
        if (slot >= 19 && slot <= 25) {
            event.setCancelled(false);
            return;
        }
        if (slot == 22) {
            tryConfirm(player, session);
        }
    }

    private void tryConfirm(Player player, Session session) {
        if (session.selected == null) {
            player.sendMessage(prefix() + "§cСначала выберите шкалу.");
            return;
        }
        WorldState state = manager.getWorldState(session.altar.getWorld().getName());
        if (state == null) return;
        if (!hasOfferings(session.inventory, session.selected)) {
            player.sendMessage(prefix() + "§cНедостаточно подношений.");
            return;
        }
        consumeOfferings(session.inventory, session.selected);
        int reduce = config.offeringReduce.getOrDefault(session.selected, 100);
        state.add(session.selected, -reduce, config.thresholdMax);
        manager.storage().saveWorldState(state);
        cooldowns.put(session.altar.getWorld().getName(), Instant.now().getEpochSecond() + config.ritualCooldownMinutes * 60L);
        manager.storage().insertJournal(new JournalEntry(Instant.now().getEpochSecond(), session.altar.getWorld().getName(),
            "RITUAL_SUCCESS", session.selected, "{\"player\":\"" + player.getName() + "\",\"reduce\":" + reduce + "}"));
        player.sendMessage(prefix() + "§aЛетопись приняла подношение.");
        player.closeInventory();
    }

    private boolean hasOfferings(Inventory inventory, Scale scale) {
        Map<Material, Integer> counts = countOfferings(inventory);
        for (LetopisConfig.ItemStackConfig item : config.offerings.getOrDefault(scale, List.of())) {
            int have = counts.getOrDefault(item.material(), 0);
            if (have < item.amount()) return false;
        }
        return true;
    }

    private void consumeOfferings(Inventory inventory, Scale scale) {
        Map<Material, Integer> needed = new HashMap<>();
        for (LetopisConfig.ItemStackConfig item : config.offerings.getOrDefault(scale, List.of())) {
            needed.put(item.material(), item.amount());
        }
        for (int slot = 19; slot <= 25; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null) continue;
            Material type = stack.getType();
            if (!needed.containsKey(type)) continue;
            int toRemove = Math.min(stack.getAmount(), needed.get(type));
            stack.setAmount(stack.getAmount() - toRemove);
            needed.put(type, needed.get(type) - toRemove);
            if (stack.getAmount() <= 0) {
                inventory.setItem(slot, null);
            }
        }
    }

    private Map<Material, Integer> countOfferings(Inventory inventory) {
        Map<Material, Integer> counts = new HashMap<>();
        for (int slot = 19; slot <= 25; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null) continue;
            counts.merge(stack.getType(), stack.getAmount(), Integer::sum);
        }
        return counts;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        for (int slot = 19; slot <= 25; slot++) {
            ItemStack stack = session.inventory.getItem(slot);
            if (stack != null) {
                player.getInventory().addItem(stack);
            }
        }
    }

    private String prefix() {
        return messages.getString("prefix", "");
    }

    private static final class Session {
        private final Location altar;
        private final Inventory inventory;
        private Scale selected;

        private Session(Location altar, Inventory inventory) {
            this.altar = altar;
            this.inventory = inventory;
        }
    }
}
