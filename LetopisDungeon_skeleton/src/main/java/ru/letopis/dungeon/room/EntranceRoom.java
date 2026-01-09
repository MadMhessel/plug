package ru.letopis.dungeon.room;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import ru.letopis.dungeon.core.DungeonBuilder;

public final class EntranceRoom implements Room {

    private final DungeonBuilder builder;
    private final String name;
    private boolean complete = false;
    private int ticks = 0;

    public EntranceRoom(DungeonBuilder builder, String name) {
        this.builder = builder;
        this.name = name;
    }

    @Override
    public String name() { return name; }

    @Override
    public String objective() { return "Соберите группу и приготовьтесь"; }

    @Override
    public void build(RoomContext context) {
        builder.buildRoomBase(context.world(), context.origin(), context.sizeX(), context.sizeY(), context.sizeZ(),
                Material.DEEPSLATE_TILES, Material.DEEPSLATE_BRICKS, Material.DEEPSLATE_TILES, Material.SOUL_LANTERN);
        builder.buildPillars(context.world(), context.origin(), context.sizeX(), context.sizeY(), context.sizeZ(),
                Material.CHISELED_DEEPSLATE);
        builder.carveDoor(context.world(), context.origin(), context.sizeX(), context.sizeY(), context.sizeZ(), true);

        Location signLoc = context.origin().clone().add(2, 2, 2);
        context.world().getBlockAt(signLoc).setType(Material.OAK_SIGN, false);
        if (context.world().getBlockAt(signLoc).getState() instanceof Sign sign) {
            SignSide side = sign.getSide(Side.FRONT);
            side.setLine(0, "Готовы?");
            side.setLine(1, "Продвигайтесь");
            side.setLine(2, "вглубь");
            sign.update(false, false);
        }
    }

    @Override
    public void start(RoomContext context) {
        ticks = 0;
        complete = false;
    }

    @Override
    public void tick(RoomContext context) {
        ticks++;
        if (ticks > 200) complete = true;
    }

    @Override
    public boolean isComplete() { return complete; }

    @Override
    public double progress() { return Math.min(1.0, ticks / 200.0); }

    @Override
    public void onPlayerInteract(RoomContext context, org.bukkit.event.player.PlayerInteractEvent event) {
    }

    @Override
    public void cleanup(RoomContext context) {
    }
}
