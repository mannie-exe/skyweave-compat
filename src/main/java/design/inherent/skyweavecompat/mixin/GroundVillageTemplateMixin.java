package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import design.inherent.skyweavecompat.terrain.structure.GroundVillageTerrain;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Actual processed template intent supplements successful native output, never preview output. */
@Mixin(SinglePoolElement.class)
public abstract class GroundVillageTemplateMixin {
    @WrapOperation(method="place",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/levelgen/structure/pools/SinglePoolElement;getSettings(Lnet/minecraft/world/level/block/Rotation;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;Lnet/minecraft/world/level/levelgen/structure/templatesystem/LiquidSettings;Z)Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;"))
    private StructurePlaceSettings skyweave$actualIntent(SinglePoolElement element,Rotation rotation,BoundingBox bounds,
            LiquidSettings liquids,boolean keep,Operation<StructurePlaceSettings> original) {
        return GroundVillageTerrain.observeSettings(original.call(element,rotation,bounds,liquids,keep));
    }
}
