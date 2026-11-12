package design.inherent.skyweavecompat.mixin;

import design.inherent.skyweavecompat.terrain.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.stream.Stream;

@Mixin(HeightRangePlacement.class)
public abstract class OreHeightMixin {
    @Shadow @Final private HeightProvider height;
    @Unique private static final TagKey<PlacedFeature> SKYWEAVE_RELATIVE = TagKey.create(
            Registries.PLACED_FEATURE, ResourceLocation.fromNamespaceAndPath("skyweave_compat", "relative_geology"));
    @Unique private static final TagKey<PlacedFeature> SKYWEAVE_SURFACE = TagKey.create(
            Registries.PLACED_FEATURE, ResourceLocation.fromNamespaceAndPath("skyweave_compat", "relative_surface"));

    @Inject(method = "getPositions", at = @At("HEAD"), cancellable = true)
    private void relativeHeight(PlacementContext context, RandomSource random, BlockPos position,
            CallbackInfoReturnable<Stream<BlockPos>> result) {
        if (!(context.generator() instanceof SkyIslandChunkGenerator generator)
                || !((GeologicalGenerator)(Object)generator).skyweave$isEnabled() || context.topFeature().isEmpty()) return;
        var registry = context.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        var feature = registry.wrapAsHolder(context.topFeature().orElseThrow());
        boolean surface = feature.is(SKYWEAVE_SURFACE);
        if (!surface && !feature.is(SKYWEAVE_RELATIVE)) return;
        var reference = new ReferenceHeightContext(generator, context.getLevel());
        // Consume exactly the native height sample, including rejected attempts, before geometry lookup.
        int sample = height.sample(random, reference);
        var field = ((GeologicalGenerator)(Object)generator).skyweave$field(context.getLevel().getLevel().getChunkSource().randomState());
        var frame = ((GeologicalField)(Object)field).skyweave$originFrameAt(position.getX(), position.getZ(), generator.resolvedSkyIslandSettings());
        if (frame == null || !frame.containsReference(sample)) {
            result.setReturnValue(Stream.empty());
            return;
        }
        int y = frame.toWorld(sample);
        if (surface) {
            var column = field.sampleColumn(position.getX(), position.getZ(), generator.resolvedSkyIslandSettings());
            if (!column.exists()) {
                result.setReturnValue(Stream.empty());
                return;
            }
            y = column.topY() + sample - GeologicalFrame.REFERENCE_SURFACE;
        }
        result.setReturnValue(context.getLevel().isOutsideBuildHeight(y) ? Stream.empty() : Stream.of(position.atY(y)));
    }
}
