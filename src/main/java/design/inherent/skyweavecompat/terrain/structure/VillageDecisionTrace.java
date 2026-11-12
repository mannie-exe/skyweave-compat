package design.inherent.skyweavecompat.terrain.structure;

import com.google.gson.Gson;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.sathrek.sky_archipelago.worldgen.benchmark.StructureBenchmarkRecorder;

/** Opt-in native benchmark diagnostics; no retained decisions or placement authority. */
final class VillageDecisionTrace {
    private static final Gson JSON=new Gson();
    private static final org.slf4j.Logger LOGGER=com.mojang.logging.LogUtils.getLogger();
    static boolean enabled() {return StructureBenchmarkRecorder.enabled();}
    static void emit(StructureStart start,String stage,String outcome,Object details) {
        if(!enabled())return;
        var scope=StructurePlacementScope.current();if(scope==null)return;
        emit(scope.pass().registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(start.getStructure()),stage,outcome,details);
    }
    static void emit(ResourceLocation id,String stage,String outcome,Object details) {
        if(!enabled())return;
        var scope=StructurePlacementScope.current();if(scope==null)return;
        var row=new LinkedHashMap<String,Object>();
        row.put("seed",scope.pass().levelSeed());row.put("siteChunk",List.of(scope.destination().x,scope.destination().z));
        row.put("structure",id==null?"unregistered":id.toString());row.put("planning",scope.planning());
        row.put("thread",Thread.currentThread().getName());row.put("stage",stage);row.put("outcome",outcome);row.put("details",details);
        LOGGER.info("Skyweave village decision {}",JSON.toJson(row));
    }
}
