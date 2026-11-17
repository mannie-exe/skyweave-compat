package design.inherent.skyweavecompat.mixin.dh;

import design.inherent.skyweavecompat.rendering.CoverageCarrier;
import design.inherent.skyweavecompat.rendering.CoverageColumns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(targets = {"com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource",
        "com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodQuadBuilder"}, remap = false)
abstract class CoverageSourceMixin implements CoverageCarrier {
    @Unique private volatile CoverageColumns skyweave$columns;
    @Override public CoverageColumns skyweave$coverage() { return skyweave$columns; }
    @Override public void skyweave$coverage(CoverageColumns columns) { skyweave$columns = columns; }
}
