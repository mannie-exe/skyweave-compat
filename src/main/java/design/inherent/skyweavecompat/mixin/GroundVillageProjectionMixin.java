package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import design.inherent.skyweavecompat.terrain.structure.GroundVillageTerrain;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.GravityProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GravityProcessor.class)
public abstract class GroundVillageProjectionMixin {
    @WrapOperation(method="processBlock",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/LevelReader;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I"))
    private int skyweave$preparedHeight(LevelReader level,Heightmap.Types type,int x,int z,Operation<Integer> original) {
        return GroundVillageTerrain.height(level,type,x,z,()->original.call(level,type,x,z));
    }
}
