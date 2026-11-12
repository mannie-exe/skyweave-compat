package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.GeologicalFrame;
import design.inherent.skyweavecompat.terrain.IslandContext;
import design.inherent.skyweavecompat.terrain.GeologicalMaterial;
import design.inherent.skyweavecompat.terrain.IslandSurfaceScope;
import com.llamalad7.mixinextras.sugar.Local;
import org.sathrek.sky_archipelago.worldgen.generator.terrain.SkyIslandColumnMaterialPlan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import design.inherent.skyweavecompat.terrain.IslandVeins;
import design.inherent.skyweavecompat.terrain.IslandStyles;
import design.inherent.skyweavecompat.terrain.DeepDarkMaterials;

@Mixin(value = SkyIslandColumnMaterialPlan.class, remap = false)
public abstract class GeologicalMaterialMixin implements GeologicalMaterial {
    @Unique private IslandContext skyweave$context;
    @Unique private IslandVeins skyweave$veins;
    @Unique private java.util.Set<IslandContext.Body> skyweave$deepDarkOwners = java.util.Set.of();
    @Unique private org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandDescriptor skyweave$bedOwner;
    @Unique private int skyweave$bedFloor = Integer.MAX_VALUE;
    @Override public void skyweave$protectBed(org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandDescriptor owner, int floor) {
        skyweave$bedOwner=owner; skyweave$bedFloor=floor;
    }
    @Override public void skyweave$setContext(IslandContext context, IslandStyles styles) {
        skyweave$context = context;
        var selected = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<IslandContext.Body,Boolean>());
        if(context!=null)for(var body:context.bodies())if(DeepDarkMaterials.special(styles,body))selected.add(body);
        // These are the exact immutable bodies returned by this context's solidOwner. Do not
        // repeat descriptor hashing and style-cache lookups for every voxel in the column.
        skyweave$deepDarkOwners = java.util.Collections.unmodifiableSet(selected);
    }
    @Unique private GeologicalFrame skyweave$frame(int y) {
        var owner = skyweave$context == null ? null : skyweave$context.solidOwner(y,skyweave$bedOwner,skyweave$bedFloor);
        return owner == null ? null : owner.geology();
    }
    @Override public void skyweave$setVeins(IslandVeins veins) { skyweave$veins = veins; }

    @Inject(method = "plannedStateAt(ILnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("RETURN"), cancellable = true)
    private void veins(int y, BlockState stone, BlockState deepslate, BlockState oceanFluid, BlockState riverFluid, BlockState bedrock,
            CallbackInfoReturnable<BlockState> result) {
        var state = result.getReturnValue();
        if (state == null || !state.is(Blocks.STONE) && !state.is(Blocks.DEEPSLATE)) return;
        var plan = (SkyIslandColumnMaterialPlan)(Object)this;
        var owner = skyweave$context == null ? null : skyweave$context.solidOwner(y,skyweave$bedOwner,skyweave$bedFloor);
        if (owner == null) return;
        var vein = skyweave$veins == null ? null : skyweave$veins.sample(plan.worldX(), y, plan.worldZ(), owner.geology());
        result.setReturnValue(DeepDarkMaterials.body(vein == null ? state : vein, skyweave$deepDarkOwners.contains(owner)));
    }

    @ModifyVariable(method = "usesDeepslateAtY(I)Z", at = @At("HEAD"), argsOnly = true)
    private int geologicalY(int y) {
        var frame = skyweave$frame(y);
        return frame == null ? y : frame.toReference(y);
    }

    @ModifyVariable(method = "resolveSurfaceStone", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private static int surfaceGeologicalY(int y, @Local(argsOnly = true, ordinal = 0) int x,
            @Local(argsOnly = true, ordinal = 2) int z) {
        return IslandSurfaceScope.referenceY(x, y, z);
    }
}
