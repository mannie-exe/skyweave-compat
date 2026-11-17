package design.inherent.skyweavecompat.encounters;

import design.inherent.skyweavecompat.terrain.LocalWater;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import java.util.OptionalInt;

public final class MurkWater {
    /** Native sea level is the datum above the last water cell, including beneath a solid cover. */
    public static OptionalInt datum(LevelAccessor level,BlockPos position){
        var cell=LocalWater.waterCell(level,position);
        if(cell==null)return OptionalInt.empty();
        var surface=LocalWater.surface(level,cell);
        return surface==null?OptionalInt.empty():OptionalInt.of(surface+1);
    }
    private MurkWater(){}
}
