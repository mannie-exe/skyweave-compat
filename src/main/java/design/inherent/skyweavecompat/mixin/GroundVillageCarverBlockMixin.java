package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.structure.GroundVillageCarving;
import java.util.function.Function;
import net.minecraft.core.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.carver.*;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep original cap cells; native tunnel geometry, masks, fluids and distant caves remain native. */
@Mixin(WorldCarver.class)
public abstract class GroundVillageCarverBlockMixin {
    @Inject(method="carveBlock",at=@At("HEAD"),cancellable=true)
    private void skyweave$preserveAnchor(CarvingContext context,CarverConfiguration configuration,ChunkAccess chunk,
            Function<BlockPos,Holder<Biome>> biomes,CarvingMask mask,BlockPos.MutableBlockPos position,
            BlockPos.MutableBlockPos below,Aquifer aquifer,MutableBoolean surface,CallbackInfoReturnable<Boolean> callback) {
        if(GroundVillageCarving.protects(chunk,position))callback.setReturnValue(false);
    }
}
