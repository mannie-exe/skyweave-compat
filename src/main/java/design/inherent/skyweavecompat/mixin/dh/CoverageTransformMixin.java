package design.inherent.skyweavecompat.mixin.dh;

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import design.inherent.skyweavecompat.rendering.CoverageCarrier;
import design.inherent.skyweavecompat.rendering.CoverageColumns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observe the native conversion while its pooled full/source columns are still owned by this worker. */
@Mixin(targets = "com.seibel.distanthorizons.core.dataObjects.transformers.FullDataToRenderDataTransformer", remap = false)
abstract class CoverageTransformMixin {
    @Inject(method = "transformFullDataToRenderSource", at = @At("RETURN"), require = 1)
    private static void skyweave$observe(FullDataSourceV2 full, IClientLevelWrapper level,
            CallbackInfoReturnable<ColumnRenderSource> ci) {
        var render = ci.getReturnValue();
        if (render == null) return;
        byte[] states = new byte[CoverageColumns.WIDTH * CoverageColumns.WIDTH];
        for (int x = 0; x < CoverageColumns.WIDTH; x++) for (int z = 0; z < CoverageColumns.WIDTH; z++) {
            int index = FullDataSourceV2.relativePosToIndex(x, z);
            // Native parent downsampling retains the minimum child generation step. Upsampled/missing
            // columns do not acquire complete coverage merely because a proxy datapoint exists.
            if (full.columnGenerationSteps.getByte(index) < EDhApiWorldGenerationStep.TERRAIN.value) continue;
            long point = render.maxVerticalSliceCount > 0 ? render.getDataPoint(x, z, 0) : 0;
            int renderIndex = x * CoverageColumns.WIDTH + z;
            if (RenderDataPointUtil.doesDataPointExist(point) && !RenderDataPointUtil.hasZeroHeight(point))
                states[renderIndex] = (byte) CoverageColumns.State.READY.ordinal();
            else if (RenderDataPointUtil.doesDataPointExist(render.voidRenderDataContainer.getLong(renderIndex)))
                states[renderIndex] = (byte) CoverageColumns.State.EMPTY.ordinal();
        }
        ((CoverageCarrier) render).skyweave$coverage(new CoverageColumns(render.pos,
                DhSectionPos.getMinCornerBlockX(render.pos), DhSectionPos.getMinCornerBlockZ(render.pos),
                DhSectionPos.getBlockWidth(render.pos) / CoverageColumns.WIDTH, states));
    }
}
