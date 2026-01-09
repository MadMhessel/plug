package ru.letopis.dungeon.room;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.letopis.dungeon.core.DungeonBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class PuzzleRoom implements Room {

    private final DungeonBuilder builder;
    private final String name;
    private final int leverCount;
    private final int penaltySeconds;
    private final List<Location> leverLocations = new ArrayList<>();
    private List<Integer> order = new ArrayList<>();
    private int progressIndex = 0;
    private boolean complete = false;

    public PuzzleRoom(DungeonBuilder builder, String name, int leverCount, int penaltySeconds) {
        this.builder = builder;
        this.name = name;
        this.leverCount = leverCount;
        this.penaltySeconds = penaltySeconds;
    }

    @Override
    public String name() { return name; }

    @Override
    public String objective() { return "Активируйте рычаги по порядку"; }

    @Override
    public void build(RoomContext context) {
        builder.buildRoom(context);
        builder.carveDoor(context.world(), context.origin(), context.sizeX(), context.sizeY(), context.sizeZ(), true);
        placeLevers(context.world(), context.origin(), context.sizeX(), context.sizeY(), context.sizeZ());
    }

    @Override
    public void start(RoomContext context) {
        complete = false;
        progressIndex = 0;
        order = randomOrder(leverCount);
        context.manager().announcePuzzle(context.session(), order);
    }

    @Override
    public void tick(RoomContext context) {
    }

    @Override
    public boolean isComplete() { return complete; }

    @Override
    public double progress() {
        if (leverCount == 0) return 1.0;
        return Math.min(1.0, (double) progressIndex / leverCount);
    }

    @Override
    public void onPlayerInteract(RoomContext context, PlayerInteractEvent event) {
        if (complete) return;
        if (event.getClickedBlock() == null) return;
        Block block = event.getClickedBlock();
        if (block.getType() != Material.LEVER) return;
        int index = leverLocations.indexOf(block.getLocation());
        if (index < 0) return;
        Player player = event.getPlayer();

        int expected = order.get(progressIndex);
        if (index == expected) {
            progressIndex++;
            if (progressIndex >= leverCount) {
                complete = true;
                context.manager().announcePuzzleSolved(context.session());
                return;
            }
            context.manager().announcePuzzleStep(context.session(), progressIndex, leverCount);
        } else {
            progressIndex = 0;
            resetLevers();
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, penaltySeconds * 20, 1));
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, penaltySeconds * 20, 0));
            context.manager().announcePuzzleFailed(context.session());
        }
    }

    @Override
    public void cleanup(RoomContext context) {
    }

    private void placeLevers(World world, Location origin, int sizeX, int sizeY, int sizeZ) {
        leverLocations.clear();
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();
        int wallX = ox + sizeX - 1;
        int startZ = oz + 4;
        for (int i = 0; i < leverCount; i++) {
            int z = startZ + i * 3;
            Location loc = new Location(world, wallX, oy + 2, z);
            world.getBlockAt(loc).setType(Material.LEVER, false);
            Block block = world.getBlockAt(loc);
            if (block.getBlockData() instanceof Switch lever) {
                lever.setFacing(BlockFace.WEST);
                block.setBlockData(lever, false);
            }
            leverLocations.add(loc);
        }
    }

    private void resetLevers() {
        for (Location loc : leverLocations) {
            Block block = loc.getWorld().getBlockAt(loc);
            if (block.getBlockData() instanceof Switch lever) {
                lever.setPowered(false);
                block.setBlockData(lever, false);
            }
        }
    }

    private List<Integer> randomOrder(int count) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < count; i++) result.add(i);
        Random random = new Random();
        for (int i = result.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int tmp = result.get(i);
            result.set(i, result.get(j));
            result.set(j, tmp);
        }
        return result;
    }
}
