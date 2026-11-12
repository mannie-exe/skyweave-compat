package design.inherent.skyweavecompat.probe.mixin;

import design.inherent.skyweavecompat.probe.GroundVillageProbe;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public abstract class GroundFeatureControlsMixin {
    private static final boolean GROUND=Boolean.getBoolean("neighbors.probe.groundVillage");
    @Inject(method="applyBiomeDecoration",at=@At(value="INVOKE",target="Lnet/minecraft/SharedConstants;debugVoidTerrain(Lnet/minecraft/world/level/ChunkPos;)Z",shift=At.Shift.AFTER))
    private void skyweave$featureControls(WorldGenLevel level,ChunkAccess chunk,StructureManager structures,CallbackInfo callback) {
        if(GROUND)GroundVillageProbe.featureControls(level,chunk,(ChunkGenerator)(Object)this);
    }
}
