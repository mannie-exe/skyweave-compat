package design.inherent.skyweavecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import design.inherent.skyweavecompat.terrain.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.carver.CaveWorldCarver;
import net.minecraft.world.level.levelgen.carver.CanyonWorldCarver;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({CaveWorldCarver.class, CanyonWorldCarver.class})
public abstract class CarverHeightMixin {
    @WrapOperation(method = "carve", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/heightproviders/HeightProvider;sample(Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/WorldGenerationContext;)I"))
    private int originHeight(HeightProvider provider, RandomSource random, WorldGenerationContext context,
            Operation<Integer> original, @Local(argsOnly = true) ChunkPos source) {
        var geology = context instanceof GeologicalCarving scoped ? scoped.skyweave$geology() : null;
        if (geology == null) return original.call(provider, random, context);
        int sample = original.call(provider, random, geology.reference());
        if ((Object)this instanceof CanyonWorldCarver) {
            return geology.canyonHeight(sample, source.getMiddleBlockX(), source.getMiddleBlockZ(), GenerationProfile.MIN_Y - 1024);
        }
        // A void origin still consumes the native random sample and tunnel decisions, away from playable terrain.
        return geology.originHeight(sample, source.getMiddleBlockX(), source.getMiddleBlockZ(), GenerationProfile.MIN_Y - 1024);
    }
}
