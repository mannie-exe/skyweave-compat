package design.inherent.skyweavecompat.probe.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import design.inherent.skyweavecompat.probe.AccessProbe;
import net.minecraft.core.*;
import net.minecraft.server.commands.LocateCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Observe the precise native call boundary; disabled controls avoid an expensive real lookup. */
@Mixin(value = LocateCommand.class, priority = 900)
public abstract class StructureCommandWitnessMixin {
    @WrapOperation(method = "locateBiome", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/level/ServerLevel;findClosestBiome3d(Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;III)Lcom/mojang/datafixers/util/Pair;"))
    private static com.mojang.datafixers.util.Pair<BlockPos, Holder<net.minecraft.world.level.biome.Biome>> biome(ServerLevel level,
            java.util.function.Predicate<Holder<net.minecraft.world.level.biome.Biome>> targets, BlockPos origin, int radius, int horizontal, int vertical,
            Operation<com.mojang.datafixers.util.Pair<BlockPos, Holder<net.minecraft.world.level.biome.Biome>>> original) {
        if (Boolean.getBoolean("neighbors.probe.access")) { AccessProbe.synchronousBiomeCalls++; return null; }
        return original.call(level, targets, origin, radius, horizontal, vertical);
    }
    @WrapOperation(method = "locatePoi", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;findClosestWithType(Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;ILnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;)Ljava/util/Optional;"))
    private static java.util.Optional<com.mojang.datafixers.util.Pair<Holder<net.minecraft.world.entity.ai.village.poi.PoiType>, BlockPos>> poi(
            net.minecraft.world.entity.ai.village.poi.PoiManager manager,
            java.util.function.Predicate<Holder<net.minecraft.world.entity.ai.village.poi.PoiType>> targets, BlockPos origin, int radius,
            net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy occupancy,
            Operation<java.util.Optional<com.mojang.datafixers.util.Pair<Holder<net.minecraft.world.entity.ai.village.poi.PoiType>, BlockPos>>> original) {
        if (Boolean.getBoolean("neighbors.probe.access")) { AccessProbe.synchronousPoiCalls++; return java.util.Optional.empty(); }
        return original.call(manager, targets, origin, radius, occupancy);
    }
    @WrapOperation(method = "locateStructure", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/chunk/ChunkGenerator;findNearestMapStructure(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/HolderSet;Lnet/minecraft/core/BlockPos;IZ)Lcom/mojang/datafixers/util/Pair;"))
    private static com.mojang.datafixers.util.Pair<BlockPos, Holder<Structure>> observe(ChunkGenerator generator,
            ServerLevel level, HolderSet<Structure> targets, BlockPos origin, int radius, boolean skipKnown,
            Operation<com.mojang.datafixers.util.Pair<BlockPos, Holder<Structure>>> original) {
        if (Boolean.getBoolean("neighbors.probe.access")) { AccessProbe.synchronousStructureCalls++; return null; }
        return original.call(generator,level,targets,origin,radius,skipKnown);
    }
}
