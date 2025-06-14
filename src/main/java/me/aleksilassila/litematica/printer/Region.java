package me.aleksilassila.litematica.printer;

import fi.dy.masa.litematica.selection.Box;
import net.minecraft.util.math.BlockPos;

public class Region {
    private final String name;
    private final Box box;

    public Region(String name, Box box) {
        this.name = name;
        this.box = box;
    }

    public String getName() {
        return name;
    }

    public Box getBox() {
        return box;
    }

    public boolean containsPosition(BlockPos pos) {
        BlockPos min = box.getPos1();
        BlockPos max = box.getPos2();
        return pos.getX() >= Math.min(min.getX(), max.getX()) &&
               pos.getX() <= Math.max(min.getX(), max.getX()) &&
               pos.getY() >= Math.min(min.getY(), max.getY()) &&
               pos.getY() <= Math.max(min.getY(), max.getY()) &&
               pos.getZ() >= Math.min(min.getZ(), max.getZ()) &&
               pos.getZ() <= Math.max(min.getZ(), max.getZ());
    }
}
