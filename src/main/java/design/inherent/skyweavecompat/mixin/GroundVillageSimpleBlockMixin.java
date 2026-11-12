package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.structure.GroundVillageTerrain;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.SimpleBlockFeature;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.sathrek.sky_archipelago.worldgen.benchmark.StructureBenchmarkRecorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native simple blocks can write a double plant; qualify both cells before provider RNG. */
@Mixin(SimpleBlockFeature.class)
public abstract class GroundVillageSimpleBlockMixin {
    @Inject(method="place",at=@At("HEAD"),cancellable=true)
    private void skyweave$protectVillage(FeaturePlaceContext<SimpleBlockConfiguration> context,CallbackInfoReturnable<Boolean> callback) {
        var origin=context.origin();
        var writes=new BoundingBox(origin.getX(),origin.getY(),origin.getZ(),origin.getX(),origin.getY()+1,origin.getZ());
        if(GroundVillageTerrain.featureConflict(context.level(),writes)) {
            StructureBenchmarkRecorder.increment("skyweaveVillageSimpleBlockRefused");
            callback.setReturnValue(false);
        }
    }
}
