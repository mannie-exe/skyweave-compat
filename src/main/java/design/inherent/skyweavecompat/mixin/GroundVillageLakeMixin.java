package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.structure.GroundVillageTerrain;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.LakeFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.sathrek.sky_archipelago.worldgen.benchmark.StructureBenchmarkRecorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native lake fluid, air and barrier writes share this conservative complete envelope. */
@Mixin(LakeFeature.class)
public abstract class GroundVillageLakeMixin {
    @Inject(method="place",at=@At("HEAD"),cancellable=true)
    private void skyweave$protectVillage(FeaturePlaceContext<LakeFeature.Configuration> context,CallbackInfoReturnable<Boolean> callback) {
        var origin=context.origin();
        var writes=new BoundingBox(origin.getX(),origin.getY()-4,origin.getZ(),origin.getX()+15,origin.getY()+3,origin.getZ()+15);
        if(GroundVillageTerrain.featureConflict(context.level(),writes)) {
            StructureBenchmarkRecorder.increment("skyweaveVillageLakeRefused");
            callback.setReturnValue(false);
        }
    }
}
