package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import design.inherent.skyweavecompat.terrain.structure.GroundVillageTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.BlockBlobFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.sathrek.sky_archipelago.worldgen.benchmark.StructureBenchmarkRecorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Refuse the complete native boulder before its first random draw or block write. */
@Mixin(BlockBlobFeature.class)
public abstract class GroundVillageBoulderMixin {
    @Inject(method="place",at=@At(value="INVOKE",target="Lnet/minecraft/util/RandomSource;nextInt(I)I",ordinal=0),cancellable=true)
    private void skyweave$protectVillage(FeaturePlaceContext<BlockStateConfiguration> context,CallbackInfoReturnable<Boolean> callback,
            @Local(ordinal=0) BlockPos anchor,@Local(ordinal=0) int iteration) {
        if(iteration!=0)return;
        // Three radius<=1 blobs; the first two center shifts are each in [-1,0].
        var writes=new BoundingBox(anchor.getX()-3,anchor.getY()-3,anchor.getZ()-3,anchor.getX()+1,anchor.getY()+1,anchor.getZ()+1);
        if(GroundVillageTerrain.featureConflict(context.level(),writes)) {
            StructureBenchmarkRecorder.increment("skyweaveVillageBoulderRefused");
            callback.setReturnValue(false);
        }
    }
}
