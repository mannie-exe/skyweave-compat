package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.structure.GroundVillageTerrain;
import net.minecraft.world.level.levelgen.feature.DiskFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.DiskConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.sathrek.sky_archipelago.worldgen.benchmark.StructureBenchmarkRecorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Protect the complete possible native disk before sampling radius or changing soil. */
@Mixin(DiskFeature.class)
public abstract class GroundVillageDiskMixin {
    @Inject(method="place",at=@At("HEAD"),cancellable=true)
    private void skyweave$protectVillage(FeaturePlaceContext<DiskConfiguration> context,CallbackInfoReturnable<Boolean> callback) {
        var origin=context.origin();int radius=context.config().radius().getMaxValue(),height=context.config().halfHeight();
        var writes=new BoundingBox(origin.getX()-radius,origin.getY()-height,origin.getZ()-radius,
                origin.getX()+radius,origin.getY()+height,origin.getZ()+radius);
        if(GroundVillageTerrain.featureConflict(context.level(),writes)) {
            StructureBenchmarkRecorder.increment("skyweaveVillageDiskRefused");
            callback.setReturnValue(false);
        }
    }
}
