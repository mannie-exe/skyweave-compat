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
    private static String variantKey(String value) {
        try {
            return "sha256:"+java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch(java.security.NoSuchAlgorithmException impossible) {throw new AssertionError(impossible);}
    }
    @SuppressWarnings("unchecked")
    private static List<net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureEntityInfo> rawEntities(
            net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate template) {
        try {
            var field=template.getClass().getDeclaredField("entityInfoList");field.setAccessible(true);
            return (List<net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureEntityInfo>)field.get(template);
        } catch(ReflectiveOperationException failure) {throw new IllegalStateException("Pinned native entity metadata API changed",failure);}
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static JsonElement directParameters(Object processor,net.minecraft.resources.RegistryOps<JsonElement> ops) {
        try {
            var codec=(com.mojang.serialization.MapCodec)processor.getClass().getField("CODEC").get(null);
            return (JsonElement)codec.codec().encodeStart(ops,processor).getOrThrow();
        } catch(ReflectiveOperationException failure) { throw new IllegalStateException("Pinned processor API changed: "+processor.getClass().getName(),failure); }
    }
    static JsonElement definition(net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor processor,
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
        var geometry=new TreeMap<String,JsonObject>();var poolGraph=new JsonArray();
        var ops=net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,server.registryAccess());
        int pools=0,elements=0,blocks=0,dynamicShapes=0,unsupported=0,connectorBytes=0,entityVisits=0;
        for(var entry:server.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL).entrySet()) {
            var id=entry.getKey().location();
            if(!id.getPath().contains("village") && !id.getNamespace().equals("ctov")) continue;
            if(++pools>8192) throw new IllegalStateException("Support survey pool budget exhausted");
            var poolRow=new JsonObject();poolGraph.add(poolRow);poolRow.addProperty("pool",id.toString());
            poolRow.addProperty("fallback",entry.getValue().getFallback().unwrapKey().orElseThrow().location().toString());
            var choices=new JsonArray();poolRow.add("choices",choices);
            var shuffled=entry.getValue().getShuffledTemplates(RandomSource.create(42));
            var weights=new IdentityHashMap<net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement,Integer>();
            for(var original:shuffled)weights.merge(original,1,Integer::sum);
            var seen=Collections.newSetFromMap(new IdentityHashMap<net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement,Boolean>());
            for(var original:shuffled) {
                if(!seen.add(original)) continue;
                if(++elements>32768) throw new IllegalStateException("Support survey element budget exhausted");
                var choice=new JsonObject();choices.add(choice);choice.addProperty("weight",weights.get(original));
                choice.addProperty("nativeElementType",original.getClass().getName());
                var originalEncoding=net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement.CODEC.encodeStart(ops,original);
                if(originalEncoding.result().isPresent())choice.add("nativeElement",originalEncoding.result().orElseThrow());
                else choice.addProperty("unserializableNativeElement",original.getClass().getName());
                var element=original;
                for(int depth=0;element instanceof VillageDelegate delegate && depth<8;depth++) element=delegate.skyweave$delegate();
                types.merge(element.getClass().getName(),1,Integer::sum);
                if(!(element instanceof SinglePoolElement single)) {
                    unsupported++;
                    var encoded=net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement.CODEC.encodeStart(ops,original).result();
                    if(encoded.isPresent())choice.add("nonSingleElement",encoded.orElseThrow());
                    else choice.addProperty("unqualifiedElement",element.getClass().getName());
                    continue;
                }
                var access=(VillageTemplateAccess)single;
                String name=access.skyweave$template().left().map(Object::toString).orElse("runtime:"+id+":"+seen.size());
                var settings=access.skyweave$settings(Rotation.NONE,new BoundingBox(-2048,-2048,-2048,2048,2048,2048),LiquidSettings.APPLY_WATERLOGGING,false);
                var sequence=new JsonArray();
                for(var processor:settings.getProcessors()) {
                    String type=processor.getClass().getName();processors.merge(type,1,Integer::sum);
                    JsonElement encoded=definition(processor,ops,0,new int[1]);
                    String key=encoded.toString();processorDefinitions.put(key,encoded);sequence.add(encoded);
                }
                String variant=name+"|"+single.getProjection()+"|"+sequence;
                String variantKey=variantKey(variant);
                choice.addProperty("variant",variantKey);
                if(templates.containsKey(variant)) continue;
                var shape=geometry.get(name);
                if(shape==null) {
                    var template=access.skyweave$template(server.getStructureManager());
                    var palettes=((VillagePaletteAccess)template).skyweave$palettes();
                    shape=new JsonObject();shape.addProperty("template",name);shape.addProperty("palettes",palettes.size());
                    shape.addProperty("sizeX",template.getSize().getX());shape.addProperty("sizeY",template.getSize().getY());shape.addProperty("sizeZ",template.getSize().getZ());
                    var sizes=new JsonArray();
                    var connectors=new JsonArray();var pois=new TreeMap<String,Integer>();
                    var blockEntities=new TreeMap<String,Integer>();var loot=new TreeMap<String,Integer>();
                    int paletteIndex=0;
                    for(var palette:palettes) {
                        blocks+=palette.blocks().size();sizes.add(palette.blocks().size());
                        if(blocks>10000000) throw new IllegalStateException("Support survey raw block budget exhausted at "+name+" after "+geometry.size()+" unique templates");
                        for(var block:palette.blocks()) {
                            if(block.state().getBlock().hasDynamicShape()) dynamicShapes++;
                            net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(block.state()).ifPresent(type->
                                    pois.merge(type.unwrapKey().orElseThrow().location().toString(),1,Integer::sum));
                            if(block.nbt()!=null) {
                                if(block.nbt().contains("id",8))blockEntities.merge(block.nbt().getString("id"),1,Integer::sum);
                                if(block.nbt().contains("LootTable",8))loot.merge(block.nbt().getString("LootTable"),1,Integer::sum);
                            }
                            if(block.state().is(net.minecraft.world.level.block.Blocks.JIGSAW)) {
                                var connector=new JsonObject();connector.addProperty("palette",paletteIndex);
                                connector.addProperty("x",block.pos().getX());connector.addProperty("y",block.pos().getY());connector.addProperty("z",block.pos().getZ());
                                connector.addProperty("orientation",block.state().getValue(net.minecraft.world.level.block.JigsawBlock.ORIENTATION).getSerializedName());
                                if(block.nbt()!=null)connector.add("nativeData",net.minecraft.nbt.NbtOps.INSTANCE.convertTo(com.mojang.serialization.JsonOps.INSTANCE,block.nbt()));
                                else connector.addProperty("missingNativeData",true);
                                connectorBytes+=connector.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                                if(connectorBytes>8*1024*1024)throw new IllegalStateException("Native connector catalog byte budget exhausted");
                                connectors.add(connector);
                            }
                        }
                        paletteIndex++;
                    }
                    var entities=new TreeMap<String,Integer>();
                    for(var entity:rawEntities(template)) {
                        if(++entityVisits>65536)throw new IllegalStateException("Native entity catalog budget exhausted");
                        entities.merge(entity.nbt.getString("id"),1,Integer::sum);
                    }
                    shape.add("rawConnectors",connectors);shape.add("rawPoiStatesAcrossPalettes",new Gson().toJsonTree(pois));
                    shape.add("rawEntityTypes",new Gson().toJsonTree(entities));shape.add("rawBlockEntityTypesAcrossPalettes",new Gson().toJsonTree(blockEntities));
                    shape.add("rawLootTablesAcrossPalettes",new Gson().toJsonTree(loot));
                    shape.add("paletteBlocks",sizes);geometry.put(name,shape);
                }
                var row=new JsonObject();row.addProperty("key",variantKey);row.addProperty("template",name);row.addProperty("projection",single.getProjection().name());
                row.addProperty("groundLevelDelta",single.getGroundLevelDelta());
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
        var shapes=new JsonArray();geometry.values().forEach(shapes::add);report.add("templates",shapes);report.add("poolGraph",poolGraph);
        report.addProperty("scope","Resolved native registry content, including pools not reachable from a chosen profile. Raw connectors, POIs, loot and entity metadata do not prove processed placement, access or final contents. Non-single choices remain explicit and unflattened.");
        if(report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>16*1024*1024)
            throw new IllegalStateException("Native village catalog publication byte budget exhausted");
        return report;
    }
}
