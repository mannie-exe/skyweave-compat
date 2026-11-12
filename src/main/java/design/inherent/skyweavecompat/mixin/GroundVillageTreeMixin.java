package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import design.inherent.skyweavecompat.terrain.structure.GroundVillageTerrain;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.BlobFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FancyFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.BushFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.AcaciaFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.MegaJungleFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.FancyTrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.StraightTrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.ForkingTrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.MegaJungleTrunkPlacer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.sathrek.sky_archipelago.worldgen.benchmark.StructureBenchmarkRecorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Refuse a whole qualified native tree before roots/trunk/leaves/decorators write. */
@Mixin(TreeFeature.class)
public abstract class GroundVillageTreeMixin {
    @Unique private static final Set<String> SKYWEAVE_DECORATORS=Set.of(
            "net.minecraft.world.level.levelgen.feature.treedecorators.AlterGroundDecorator",
            "net.minecraft.world.level.levelgen.feature.treedecorators.BeehiveDecorator",
            "net.minecraft.world.level.levelgen.feature.treedecorators.CocoaDecorator",
            "net.minecraft.world.level.levelgen.feature.treedecorators.LeaveVineDecorator",
            "net.minecraft.world.level.levelgen.feature.treedecorators.TrunkVineDecorator");

    @WrapOperation(method="doPlace",at=@At(value="INVOKE",target=
            "Lnet/minecraft/world/level/levelgen/feature/TreeFeature;getMaxFreeTreeHeight(Lnet/minecraft/world/level/LevelSimulatedReader;ILnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/feature/configurations/TreeConfiguration;)I"))
    private int skyweave$protectVillage(TreeFeature feature,LevelSimulatedReader reader,int height,BlockPos origin,
            TreeConfiguration configuration,Operation<Integer> original,@Local(ordinal=1) int foliageHeight,@Local(ordinal=3) int radius) {
        var trunk=configuration.trunkPlacer.getClass();var foliage=configuration.foliagePlacer.getClass();
        if(reader instanceof WorldGenLevel level && configuration.rootPlacer.isEmpty()
                && (trunk==StraightTrunkPlacer.class || trunk==FancyTrunkPlacer.class || trunk==ForkingTrunkPlacer.class || trunk==MegaJungleTrunkPlacer.class)
                && (foliage==BlobFoliagePlacer.class || foliage==FancyFoliagePlacer.class
                    || foliage==BushFoliagePlacer.class || foliage==AcaciaFoliagePlacer.class || foliage==MegaJungleFoliagePlacer.class)
                && configuration.decorators.stream().allMatch(decorator->SKYWEAVE_DECORATORS.contains(decorator.getClass().getName()))) {
            // Forking branches move at most three cells. Bush rows expand by their
            // full downward height; Blob/Fancy expand by half. Acacia adds one radius.
            int foliageReach=foliage==BushFoliagePlacer.class || foliage==MegaJungleFoliagePlacer.class?foliageHeight:(foliageHeight+1)/2;
            // Mega-jungle branches reach five cells, with a double-trunk leaf edge
            // and native decoration outside it; keep the full downward foliage spread.
            int branchPadding=trunk==MegaJungleTrunkPlacer.class?8:3;
            int reach=Math.max(6,height+radius+foliageReach+branchPadding);
            // Native hanging vines descend at most four cells; allow the full native
            // foliage offset/height and dirt-decorator range without claiming lower layers.
            var bounds=new BoundingBox(origin.getX()-reach,Math.max(level.getMinBuildHeight(),origin.getY()-foliageHeight-5),origin.getZ()-reach,
                    origin.getX()+reach,Math.min(level.getMaxBuildHeight()-1,origin.getY()+height+18),origin.getZ()+reach);
            if(GroundVillageTerrain.featureConflict(level,bounds)) {
                StructureBenchmarkRecorder.increment("skyweaveVillageTreeRefused");
                return -1; // Below every admitted native minimum clipped height.
            }
        }
        return original.call(feature,reader,height,origin,configuration);
    }
}
