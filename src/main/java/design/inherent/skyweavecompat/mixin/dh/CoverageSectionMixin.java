package design.inherent.skyweavecompat.mixin.dh;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodQuadBuilder;
import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import design.inherent.skyweavecompat.rendering.CoverageCarrier;
import design.inherent.skyweavecompat.rendering.CoverageColumns;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Carry the immutable build observation into the exact upload future, before native publication. */
@Mixin(targets = "com.seibel.distanthorizons.core.render.QuadTree.LodRenderSection", remap = false)
abstract class CoverageSectionMixin {
    @Shadow @Final private IDhClientLevel clientLevel;
    @Unique private CoverageColumns skyweave$uploadColumns;

    @WrapOperation(method = "getAndBuildRenderData", at = @At(value = "INVOKE",
            target = "Lcom/seibel/distanthorizons/core/dataObjects/render/bufferBuilding/ColumnRenderBufferBuilder;makeLodRenderData(Lcom/seibel/distanthorizons/core/dataObjects/render/bufferBuilding/LodQuadBuilder;Lcom/seibel/distanthorizons/core/dataObjects/render/ColumnRenderSource;Lcom/seibel/distanthorizons/core/wrapperInterfaces/world/IClientLevelWrapper;[Lcom/seibel/distanthorizons/core/dataObjects/render/ColumnRenderSource;[Z)V"), require = 1)
    private void skyweave$bindBuilder(LodQuadBuilder builder, ColumnRenderSource source, IClientLevelWrapper level,
            ColumnRenderSource[] neighbors, boolean[] detail, Operation<Void> original) {
        original.call(builder, source, level, neighbors, detail);
        var columns = ((CoverageCarrier) source).skyweave$coverage();
        ((CoverageCarrier) builder).skyweave$coverage(columns == null ? null : columns.ownedBy(clientLevel));
    }

    @WrapMethod(method = "getAndBuildRenderData")
    private LodQuadBuilder skyweave$retain(Operation<LodQuadBuilder> original) {
        skyweave$uploadColumns = null;
        var builder = original.call();
        if (builder != null) skyweave$uploadColumns = ((CoverageCarrier) builder).skyweave$coverage();
        return builder;
    }

    @WrapOperation(method = "uploadToGpuAsync", at = @At(value = "INVOKE",
            target = "Lcom/seibel/distanthorizons/core/dataObjects/render/bufferBuilding/LodBufferContainer;tryMakeAndUploadBuffersAsync(JLcom/seibel/distanthorizons/core/wrapperInterfaces/world/IClientLevelWrapper;Ljava/util/ArrayList;Ljava/util/ArrayList;)Ljava/util/concurrent/CompletableFuture;"), require = 1)
    private CompletableFuture<LodBufferContainer> skyweave$bindUpload(long position, IClientLevelWrapper level,
            ArrayList<ByteBuffer> opaque, ArrayList<ByteBuffer> transparent, Operation<CompletableFuture<LodBufferContainer>> original) {
        var columns = skyweave$uploadColumns;
        return original.call(position, level, opaque, transparent).thenApply(buffer -> {
            if (buffer != null) ((CoverageCarrier) buffer).skyweave$coverage(columns);
            return buffer;
        });
    }
}
