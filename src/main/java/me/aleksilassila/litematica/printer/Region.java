package me.aleksilassila.litematica.printer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.malilib.util.JsonUtils;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.List;

public class Region {
    private final String name;
    private final Box box;

    public final int minX, minY, minZ, maxX, maxY, maxZ;

    public List<BlockPos> positions = new java.util.ArrayList<>();

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

    public void addPosition(BlockPos pos) {
        this.positions.add(pos);
    }

    public BlockPos getBlock(int index){
        return positions.get(index);
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

    @Nullable
    public static Region fromJson(JsonObject obj)
    {
        if (JsonUtils.hasString(obj, "name") && JsonUtils.hasObject(obj, "box"))
        {
            var _box = Box.fromJson(obj.get("box").getAsJsonObject());
            var _name = obj.get("name").getAsString();
            var _positions = obj.get("positions").getAsJsonArray();
            var _region = new Region(_name, _box);
            for(int i = 0; i < _positions.size(); i++){
                _region.addPosition(JsonUtils.blockPosFromJson(_positions.get(i).getAsJsonObject(), "pos"));
            }
            return _region;
        }

        return null;
    }

    @Nullable
    public JsonObject toJson()
    {
        JsonObject obj = new JsonObject();
        obj.add("box", this.box.toJson());
        obj.addProperty("name", this.getName());
        JsonArray positionsArray = new JsonArray();
        for(BlockPos _pos : positions){
            var _posObj = new JsonObject();
            _posObj.add("pos", JsonUtils.blockPosToJson(_pos));
            positionsArray.add(_posObj);
        }
        obj.add("positions", positionsArray);

        return obj;
    }
}
