package design.inherent.skyweavecompat.mixin.water;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import design.inherent.skyweavecompat.terrain.LocalWater;
import design.inherent.skyweavecompat.water.IslandWaterFloes;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets="com.AdamNew.dynamicwaters.events.IceFloeSpawner",remap=false)
public abstract class FloeWaterLevelMixin {
    @WrapOperation(method="spawnNear",at=@At(value="INVOKE",target=
            "Lcom/AdamNew/dynamicwaters/HydrologyManager;mountainRiverNodesNear(DDDD)Ljava/util/List;"))
    private static List<double[]> skyweave$nodeHeights(double x,double z,double minimum,double maximum,
            Operation<List<double[]>> original,@Local(argsOnly=true) ServerLevel level) {
        return LocalWater.applies(level)?IslandWaterFloes.nodesNear(x,z,minimum,maximum):original.call(x,z,minimum,maximum);
    }
    @WrapOperation(method="spawnNear",at=@At(value="INVOKE",target=
            "Lnet/minecraft/server/level/ServerLevel;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I"))
    private static int skyweave$riverLayer(ServerLevel level,Heightmap.Types type,int x,int z,Operation<Integer> original,
            @Local(ordinal=0) double[] node) {
        if(!LocalWater.applies(level))return original.call(level,type,x,z);
        if(node.length!=3 || !Double.isFinite(node[2]) || node[2]<level.getMinBuildHeight() || node[2]>=level.getMaxBuildHeight())
            return level.getMinBuildHeight();
        // Pinned native river admission uses a +/-20 height band, independently of temperature.
        var surface=LocalWater.surfaceNear(level,x,z,(int)node[2],20);
        return surface==null?level.getMinBuildHeight():surface+1;
    }
    @WrapOperation(method="setGroupAfloat",at=@At(value="INVOKE",target=
            "Lnet/minecraft/server/level/ServerLevel;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I"))
    private static int skyweave$groupLayer(ServerLevel level,Heightmap.Types type,int x,int z,Operation<Integer> original,
            @Local(argsOnly=true,ordinal=1) int centerY) {
        if(!LocalWater.applies(level))return original.call(level,type,x,z);
        var surface=LocalWater.surfaceNear(level,x,z,centerY,1);
        return surface==null?level.getMinBuildHeight():surface+1;
    }
}
