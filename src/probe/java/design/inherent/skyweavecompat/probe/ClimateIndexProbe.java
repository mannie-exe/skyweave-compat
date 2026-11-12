package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import com.mojang.datafixers.util.Pair;
import design.inherent.skyweavecompat.terrain.CaveProjection;
import java.nio.file.*;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.sathrek.sky_archipelago.worldgen.generator.core.SkyIslandChunkGenerator;

/** Discriminating native equal-distance index control, separate from geometry/storage coverage. */
final class ClimateIndexProbe {
    static JsonObject run(ServerLevel level,SkyIslandChunkGenerator generator,Path output) throws Exception {
        var biomes=level.registryAccess().registryOrThrow(Registries.BIOME);
        var low=biomes.getHolderOrThrow(Biomes.PLAINS);var high=biomes.getHolderOrThrow(Biomes.LUSH_CAVES);
        var source=MultiNoiseBiomeSource.createFromList(new Climate.ParameterList<>(List.of(
                Pair.of(Climate.parameters(0,0,0,0,0,0,0),low),Pair.of(Climate.parameters(0,0,0,0,.55f,0,0),high))));
        var near=new Climate.TargetPoint(0,0,0,0,0,0);var deep=new Climate.TargetPoint(0,0,0,0,5500,0);var tie=new Climate.TargetPoint(0,0,0,0,2750,0);
        source.getNoiseBiome(near);var nativeA=source.getNoiseBiome(tie);source.getNoiseBiome(deep);var nativeB=source.getNoiseBiome(tie);
        if(nativeA.equals(nativeB))throw new AssertionError("Native history control stopped discriminating");
        CaveProjection.select(generator,source,near);var ownedA=CaveProjection.select(generator,source,tie);
        CaveProjection.select(generator,source,deep);var ownedB=CaveProjection.select(generator,source,tie);
        var result=new JsonObject();result.addProperty("nativeAfterSurface",nativeA.getRegisteredName());result.addProperty("nativeAfterCave",nativeB.getRegisteredName());
        result.addProperty("ownedAfterSurface",ownedA.getRegisteredName());result.addProperty("ownedAfterCave",ownedB.getRegisteredName());
        result.addProperty("ownedHistoryIndependent",ownedA.equals(ownedB));
        if(!ownedA.equals(ownedB)) {
            var report=new JsonObject();report.addProperty("complete",false);report.add("climateIndex",result);Files.writeString(output,report.toString());
            throw new AssertionError("Owned cave selection depends on previous native queries");
        }
        var history=history(source);var leaf=history.get();
        CaveProjection.select(generator,source,near);CaveProjection.select(generator,source,tie);
        if(history.get()!=leaf)throw new AssertionError("Owned index mutated the original native tree history");
        boolean failed=false;try { CaveProjection.select(generator,source,null); }catch(NullPointerException expected) { failed=true; }
        if(!failed || design.inherent.skyweavecompat.climate.NativeClimateIndex.creating() || history.get()!=leaf)
            throw new AssertionError("Native failure leaked construction scope or touched original history");
        int axes=0;
        for(int axis:new int[]{0,1,2,3,5}) {
            float[] values=new float[6];values[axis]=1;
            var highSurface=Climate.parameters(values[0],values[1],values[2],values[3],0,values[5],0);
            var highCave=Climate.parameters(values[0],values[1],values[2],values[3],1,values[5],0);
            var axisSource=MultiNoiseBiomeSource.createFromList(new Climate.ParameterList<>(List.of(
                    Pair.of(Climate.parameters(0,0,0,0,0,0,0),low),Pair.of(highSurface,biomes.getHolderOrThrow(Biomes.DESERT)),Pair.of(highCave,high))));
            var functions=new net.minecraft.world.level.levelgen.DensityFunction[6];java.util.Arrays.fill(functions,DensityFunctions.constant(0));
            functions[4]=DensityFunctions.constant(1);functions[axis]=DensityFunctions.yClampedGradient(64,2048,0,1);
            var sampler=new Climate.Sampler(functions[0],functions[1],functions[2],functions[3],functions[4],functions[5],List.of());
            var raw=axisSource.getNoiseBiome(0,512,0,sampler);
            if(!raw.is(Biomes.LUSH_CAVES))throw new AssertionError("Y-sensitive axis control lost its raw cave witness");
            var selected=CaveProjection.physical(generator,level.getChunkSource().randomState(),axisSource,raw,0,512,0,sampler);
            if(!selected.is(Biomes.DESERT))throw new AssertionError("Owned surface fallback changed physical climate axis "+axis);
            axes++;
        }
        result.addProperty("physicalAxisControls",axes);result.addProperty("originalHistoryUnchanged",true);result.addProperty("exceptionControl",true);
        result.add("rawHost",RawHostClimateProbe.run(level,generator,output));
        return result;
    }
    private static ThreadLocal<?> history(MultiNoiseBiomeSource source) throws ReflectiveOperationException {
        var method=MultiNoiseBiomeSource.class.getDeclaredMethod("parameters");method.setAccessible(true);var parameters=method.invoke(source);
        var field=Climate.ParameterList.class.getDeclaredField("index");field.setAccessible(true);var tree=field.get(parameters);
        var cache=tree.getClass().getDeclaredField("lastResult");cache.setAccessible(true);return (ThreadLocal<?>)cache.get(tree);
    }
}
