package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import design.inherent.skyweavecompat.mixin.VillagePaletteAccess;
import design.inherent.skyweavecompat.mixin.VillageTemplateAccess;
import design.inherent.skyweavecompat.terrain.structure.VillageDelegate;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;

/** Resolved runtime content and native settings; raw palette geometry is not processed footing. */
final class VillageSupportSurvey {
    private VillageSupportSurvey() {}
    @SuppressWarnings({"rawtypes","unchecked"})
    private static JsonElement directParameters(Object processor,net.minecraft.resources.RegistryOps<JsonElement> ops) {
        try {
            var codec=(com.mojang.serialization.MapCodec)processor.getClass().getField("CODEC").get(null);
            return (JsonElement)codec.codec().encodeStart(ops,processor).getOrThrow();
        } catch(ReflectiveOperationException failure) { throw new IllegalStateException("Pinned processor API changed: "+processor.getClass().getName(),failure); }
    }
    private static JsonElement definition(net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor processor,
            net.minecraft.resources.RegistryOps<JsonElement> ops,int depth,int[] visits) {
        if(depth>16 || ++visits[0]>4096)throw new IllegalStateException("Processor input graph budget exhausted");
        var result=StructureProcessorType.SINGLE_CODEC.encodeStart(ops,processor).result();
        if(result.isPresent())return result.orElseThrow();
        String type=processor.getClass().getName();
        var unavailable=new JsonObject();unavailable.addProperty("unserializableProcessor",type);
        if(type.equals("net.choicetheorem.ctov.worldgen.processor.ModularCompatProcessor"))
            unavailable.add("directCodecParameters",directParameters(processor,ops));
        else if(type.equals("dev.worldgen.lithostitched.impl.worldgen.processor.ApplyRandomStructureProcessor")) {
            // Its direct codec also encounters CTOV's unregistered TYPE inside inline lists.
            // Inspect the actual immutable weighted choices; never replace a failed dispatch with an empty list.
            try {
                var random=processor.getClass().getMethod("randomSettings").invoke(processor);
                unavailable.addProperty("randomMode",((Enum<?>)random.getClass().getMethod("mode").invoke(random)).name());
                unavailable.addProperty("randomName",random.getClass().getMethod("name").invoke(random).toString());
                var holders=processor.getClass().getMethod("processorLists").invoke(processor);
                var field=holders.getClass().getDeclaredField("weightedList");field.setAccessible(true);
                var weighted=((java.util.function.Supplier<?>)field.get(holders)).get();
                var entries=(List<?>)weighted.getClass().getMethod("unwrap").invoke(weighted);
                if(entries.size()>256)throw new IllegalStateException("Weighted processor list budget exhausted");
                var choices=new JsonArray();
                for(var entry:entries) {
                    var holder=(net.minecraft.core.Holder<?>)entry.getClass().getMethod("value").invoke(entry);
                    var list=(net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList)holder.value();
                    var choice=new JsonObject();choice.addProperty("weight",(Integer)entry.getClass().getMethod("weight").invoke(entry));
                    holder.unwrapKey().ifPresent(key->choice.addProperty("reference",key.location().toString()));
                    var nested=new JsonArray();
                    for(var child:list.list())nested.add(definition(child,ops,depth+1,visits));
                    choice.add("processors",nested);choices.add(choice);
                }
                unavailable.add("resolvedWeightedChoices",choices);
            } catch(ReflectiveOperationException failure) { throw new IllegalStateException("Pinned Lithostitched processor API changed",failure); }
        }
        return unavailable;
    }
    static JsonObject run(MinecraftServer server) {
        var report=new JsonObject();var processors=new TreeMap<String,Integer>();var types=new TreeMap<String,Integer>();
        var templates=new TreeMap<String,JsonObject>();var processorDefinitions=new TreeMap<String,JsonElement>();
        var geometry=new HashMap<String,JsonObject>();
        var ops=net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,server.registryAccess());
        int pools=0,elements=0,blocks=0,dynamicShapes=0,unsupported=0;
        for(var entry:server.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL).entrySet()) {
            var id=entry.getKey().location();
            if(!id.getPath().contains("village") && !id.getNamespace().equals("ctov")) continue;
            if(++pools>8192) throw new IllegalStateException("Support survey pool budget exhausted");
            var seen=Collections.newSetFromMap(new IdentityHashMap<net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement,Boolean>());
            for(var original:entry.getValue().getShuffledTemplates(RandomSource.create(42))) {
                if(!seen.add(original)) continue;
                if(++elements>32768) throw new IllegalStateException("Support survey element budget exhausted");
                var element=original;
                for(int depth=0;element instanceof VillageDelegate delegate && depth<8;depth++) element=delegate.skyweave$delegate();
                types.merge(element.getClass().getName(),1,Integer::sum);
                if(!(element instanceof SinglePoolElement single)) { unsupported++;continue; }
                var access=(VillageTemplateAccess)single;
                String name=access.skyweave$template().left().map(Object::toString).orElse("runtime:"+id);
                var settings=access.skyweave$settings(Rotation.NONE,new BoundingBox(-2048,-2048,-2048,2048,2048,2048),LiquidSettings.APPLY_WATERLOGGING,false);
                var sequence=new JsonArray();
                for(var processor:settings.getProcessors()) {
                    String type=processor.getClass().getName();processors.merge(type,1,Integer::sum);
                    JsonElement encoded=definition(processor,ops,0,new int[1]);
                    String key=encoded.toString();processorDefinitions.put(key,encoded);sequence.add(encoded);
                }
                String variant=name+"|"+single.getProjection()+"|"+sequence;
                if(templates.containsKey(variant)) continue;
                var shape=geometry.get(name);
                if(shape==null) {
                    var template=access.skyweave$template(server.getStructureManager());
                    var palettes=((VillagePaletteAccess)template).skyweave$palettes();
                    shape=new JsonObject();shape.addProperty("palettes",palettes.size());
                    var sizes=new JsonArray();
                    for(var palette:palettes) {
                        blocks+=palette.blocks().size();sizes.add(palette.blocks().size());
                        if(blocks>10000000) throw new IllegalStateException("Support survey raw block budget exhausted at "+name+" after "+geometry.size()+" unique templates");
                        for(var block:palette.blocks()) if(block.state().getBlock().hasDynamicShape()) dynamicShapes++;
                    }
                    shape.add("paletteBlocks",sizes);geometry.put(name,shape);
                }
                var row=new JsonObject();row.addProperty("template",name);row.addProperty("projection",single.getProjection().name());
                row.addProperty("palettes",shape.get("palettes").getAsInt());row.add("processors",sequence);
                row.add("paletteBlocks",shape.get("paletteBlocks"));templates.put(variant,row);
            }
        }
        report.addProperty("resolvedPools",pools);report.addProperty("uniqueElementsPerPool",elements);
        report.addProperty("uniqueTemplates",geometry.size());report.addProperty("templateProcessorVariants",templates.size());report.addProperty("rawBlockVisits",blocks);
        report.addProperty("dynamicShapeVisits",dynamicShapes);report.addProperty("nonSingleElements",unsupported);
        report.add("elementTypes",new Gson().toJsonTree(types));report.add("processorTypes",new Gson().toJsonTree(processors));
        var definitions=new JsonArray();processorDefinitions.values().forEach(definitions::add);report.add("processorDefinitions",definitions);
        var variants=new JsonArray();templates.values().forEach(variants::add);report.add("variants",variants);
        return report;
    }
}
