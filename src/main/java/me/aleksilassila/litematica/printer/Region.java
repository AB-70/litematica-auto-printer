package me.aleksilassila.litematica.printer;

import fi.dy.masa.litematica.selection.Box;
import net.minecraft.util.math.BlockPos;

public class Region {
    private final String name;
    private final Box box;

    public final int minX, minY, minZ, maxX, maxY, maxZ;

    public Region(String name, Box box) {
        this.name = name;
        this.box = box;
        BlockPos min = box.getPos1();
        BlockPos max = box.getPos2();

        this.minX = Math.min(min.getX(), max.getX()) ;
        minY = Math.min(min.getY(), max.getY()) ;
        minZ = Math.min(min.getZ(), max.getZ()) ;
        maxX = Math.max(min.getX(), max.getX()) ;
        maxY = Math.max(min.getY(), max.getY()) ;
        maxZ = Math.max(min.getZ(), max.getZ()) ;
    }

    public String getName() {
        return name;
    }

    public Box getBox() {
        return box;
    }

    public boolean containsPosition(BlockPos pos) {
        return pos.getX() >= minX &&
               pos.getX() <= maxX &&
               pos.getY() >= minY &&
               pos.getY() <= maxY &&
               pos.getZ() >= minZ &&
               pos.getZ() <= maxZ;
    }
}
