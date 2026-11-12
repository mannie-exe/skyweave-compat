package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import design.inherent.skyweavecompat.climate.IslandClimate;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Temperature input only; plot-local collision, fluid and light checks remain physical. */
@Mixin(value=IslandClimate.class,remap=false)
public abstract class SableClimateMixin {
    @Shadow static boolean referencePosition(BlockPos position) {throw new AssertionError();}

    @WrapMethod(method="temperaturePosition(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;")
    private static BlockPos skyweave$parentTemperature(LevelReader reader,BlockPos position,Operation<BlockPos> original) {
        if(referencePosition(position) || !IslandClimate.applies(reader))return original.call(reader,position);
        var level=reader instanceof Level value?value:reader instanceof WorldGenLevel world?world.getLevel():null;
        if(level==null || Sable.HELPER.getContaining(level,position)==null)return original.call(reader,position);
        var parent=Sable.HELPER.projectOutOfSubLevel(level,position.getCenter());
        if(!Double.isFinite(parent.x) || !Double.isFinite(parent.y) || !Double.isFinite(parent.z))
            return original.call(reader,position);
        return original.call(reader,BlockPos.containing(parent));
    }
}
