package design.inherent.skyweavecompat.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import design.inherent.skyweavecompat.terrain.structure.SurveyorLayout;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Rejected starts have no registered structure and are not map records. */
@Pseudo
@Mixin(targets = "folk.sisby.surveyor.structure.WorldStructures", remap = false)
public abstract class SurveyorStructureMixin {
    @Inject(method = "put(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/levelgen/structure/StructureStart;)V",
            at = @At("HEAD"), cancellable = true)
    private void rejectedStart(ServerLevel level, StructureStart start, CallbackInfo callback) {
        if (!start.isValid()) callback.cancel();
    }

    /** Keep recorded aliases/exploration intact, but present each physical layout once. */
    @Inject(method = "asMap", at = @At("RETURN"), cancellable = true)
    private void physicalLayouts(CallbackInfoReturnable<Map<ResourceKey<Structure>, Map<ChunkPos, Object>>> callback) {
        var result = new HashMap<ResourceKey<Structure>, Map<ChunkPos, Object>>();
        for (var entry : callback.getReturnValue().entrySet()) {
            var seen = new HashSet<String>();
            var layouts = new HashMap<ChunkPos, Object>();
            entry.getValue().entrySet().stream().sorted(Map.Entry.comparingByKey(
                    java.util.Comparator.comparingInt((ChunkPos pos) -> pos.x).thenComparingInt(pos -> pos.z)))
                    .forEach(layout -> {
                        if (seen.add(((SurveyorLayout)layout.getValue()).skyweave$identity(entry.getKey().location())))
                            layouts.put(layout.getKey(), layout.getValue());
                    });
            result.put(entry.getKey(), layouts);
        }
        callback.setReturnValue(result);
    }
}
