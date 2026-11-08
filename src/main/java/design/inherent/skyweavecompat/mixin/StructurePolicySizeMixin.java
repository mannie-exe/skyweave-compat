package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.ProfiledTerrain;
import design.inherent.skyweavecompat.terrain.structure.IslandJigsaw;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;
import org.sathrek.sky_archipelago.worldgen.structure.advisor.StructureSizeProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = IslandField.class, remap = false)
public abstract class StructurePolicySizeMixin {
    @Inject(method = "structureSize", at = @At("HEAD"), cancellable = true)
    private void customSize(Structure structure, RegistryAccess registries, CallbackInfoReturnable<StructureSizeProfile> result) {
        if (((ProfiledTerrain)this).skyweave$profile() != null && structure instanceof IslandJigsaw custom)
            result.setReturnValue(StructureSizeProfile.jigsaw(custom.skyweave$horizontalReach(), custom.skyweave$assemblyDepth()));
    }
}
