package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import design.inherent.skyweavecompat.mixin.*;
import design.inherent.skyweavecompat.terrain.structure.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.security.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pools.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;

/** Post-modifier potential connector closure, not proof of spatial fit, placement or population rates. */
final class StructurePlacementCoverage {
    private record Element(String id, List<StructureTemplate.StructureBlockInfo> ports) {}
    private record Request(ResourceLocation pool, StructureTemplate.StructureBlockInfo parent, int depth) {}
    private final MinecraftServer server;
    private final net.minecraft.core.Registry<StructureTemplatePool> pools;
    private final RegistryOps<JsonElement> ops;
    private final IdentityHashMap<StructurePoolElement, Element> elements = new IdentityHashMap<>();
    private final SortedMap<String, JsonObject> variants = new TreeMap<>();
    private final SortedMap<String, JsonObject> poolContents = new TreeMap<>();
    private final Map<net.minecraft.world.level.block.state.BlockState,Boolean> rawRoots=new HashMap<>();
    private final Method rootMethod,nodeMethod,roleMethod;
    private int visits;
    private StructurePlacementCoverage(MinecraftServer server) {
        this.server=server; pools=server.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        ops=RegistryOps.create(JsonOps.INSTANCE,server.registryAccess());
        try {
            rootMethod=Class.forName("design.inherent.skyweavecompat.terrain.structure.VillageBearing")
                    .getDeclaredMethod("root",net.minecraft.world.level.block.state.BlockState.class);rootMethod.setAccessible(true);
            nodeMethod=VillageLayoutRepair.class.getDeclaredMethod("node",StructurePiece.class,StructureTemplateManager.class);nodeMethod.setAccessible(true);
            roleMethod=nodeMethod.getReturnType().getDeclaredMethod("role");roleMethod.setAccessible(true);
        } catch(ReflectiveOperationException changed) { throw new IllegalStateException("Shipping placement policy API changed",changed); }
    }
    private boolean root(net.minecraft.world.level.block.state.BlockState state) {
        return rawRoots.computeIfAbsent(state,key->{
            try { return (boolean)rootMethod.invoke(null,key); }
            catch(ReflectiveOperationException changed) { throw new IllegalStateException("Cannot inspect raw root policy",changed); }
        });
    }
    private String role(StructurePiece piece) {
        try { return roleMethod.invoke(nodeMethod.invoke(null,piece,server.getStructureManager())).toString(); }
        catch(ReflectiveOperationException changed) { throw new IllegalStateException("Cannot inspect template role policy",changed); }
    }
    static JsonObject run(MinecraftServer server) { return new StructurePlacementCoverage(server).scan(); }
    private static String digest(byte[] data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
        catch(NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static String portKey(StructureTemplate.StructureBlockInfo port) {
        return port==null?"root":port.state()+"|"+port.nbt();
    }
    private JsonObject scan() {
        var result=new JsonObject(); var profiles=new TreeMap<String,JsonObject>(); var schedules=new TreeMap<String,Set<String>>();
        var structures=server.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for(var set:server.overworld().getChunkSource().getGeneratorState().possibleStructureSets())
            for(var entry:set.value().structures()) schedules.computeIfAbsent(entry.structure().getRegisteredName(),key->new TreeSet<>())
                    .add(set.getRegisteredName());
        for(var entry:schedules.entrySet()) {
            var id=ResourceLocation.parse(entry.getKey());var holder=structures.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE,id));
            var value=holder.value();var family=StructureMembership.family(holder);
            var row=new JsonObject();row.add("sets",new Gson().toJsonTree(entry.getValue()));
            row.addProperty("class",value.getClass().getName());row.addProperty("family",family==null?"native/foreign":family.key);
            row.addProperty("terrainAdjustment",value.terrainAdaptation().getSerializedName());
            row.add("tags",new Gson().toJsonTree(holder.tags().map(tag->tag.location().toString()).sorted().toList()));
            row.addProperty("supportRoute",family==StructureFamily.VILLAGES?"ground-village":family==null?"native/foreign":"specialized-"+family.key);
            if(value instanceof net.minecraft.world.level.levelgen.structure.structures.JigsawStructure || value instanceof IslandJigsaw) {
                var definition=Structure.DIRECT_CODEC.encodeStart(ops,value).getOrThrow().getAsJsonObject();
                row.add("effectiveDefinition",definition);
                if(definition.has("start_pool") && definition.get("start_pool").isJsonPrimitive() && definition.has("size"))
                    closure(row,ResourceLocation.parse(definition.get("start_pool").getAsString()),definition.get("size").getAsInt(),definition);
                else row.addProperty("closureRefusal","unqualified_root_or_depth");
            } else row.addProperty("closureDisposition","non-jigsaw_native_or_specialized_assembler");
            profiles.put(id.toString(),row);
        }
        result.addProperty("scope","Actual enabled Overworld schedules and post-modifier registries; all positive-weight native palettes/rotations and connector-compatible alternatives. Ignores spatial collisions, terrain/biome eligibility, sampled empty-entry ordering and alias correlations; this is potential closure, not an assembled layout.");
        result.add("profiles",new Gson().toJsonTree(profiles));result.add("variants",new Gson().toJsonTree(variants));
        result.add("effectivePools",new Gson().toJsonTree(poolContents));
        var modifiers=new TreeMap<String,JsonObject>();
        server.getResourceManager().listResources("lithostitched/worldgen_modifier",id->id.getPath().endsWith(".json"))
                .forEach((id,resource)->{
                    var row=new JsonObject();row.addProperty("provider",resource.sourcePackId());
                    try(var input=resource.open()){row.addProperty("sha256",digest(input.readAllBytes()));}
                    catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
                    modifiers.put(id.toString(),row);
                });
        result.add("winningModifierInputs",new Gson().toJsonTree(modifiers));
        result.addProperty("scheduledProfiles",profiles.size());result.addProperty("variantCount",variants.size());result.addProperty("boundedEntryVisits",visits);
        return result;
    }
    private void closure(JsonObject row, ResourceLocation root, int maximumDepth, JsonObject definition) {
        if(maximumDepth<0 || maximumDepth>64)throw new IllegalArgumentException("Invalid native assembly depth");
        var aliases=new HashMap<ResourceLocation,Set<ResourceLocation>>();
        if(definition.has("pool_aliases")) for(var entry:definition.getAsJsonArray("pool_aliases")) {
            var binding=entry.getAsJsonObject();
            String type=binding.get("type").getAsString();
            if(type.equals("lithostitched:internal/random_entries")) {
                var names=binding.getAsJsonArray("aliases");var sets=binding.getAsJsonArray("pools");
                if(names.size()!=sets.size() || names.isEmpty())throw new IllegalStateException("Changed Lithostitched alias cardinality");
                int size=-1;
                for(int i=0;i<names.size();i++) {
                    var targets=net.minecraft.core.RegistryCodecs.homogeneousList(Registries.TEMPLATE_POOL).parse(ops,sets.get(i)).getOrThrow();
                    if(targets.size()==0 || size!=-1 && size!=targets.size())throw new IllegalStateException("Changed correlated alias target sizes");
                    size=targets.size();var choices=aliases.computeIfAbsent(ResourceLocation.parse(names.get(i).getAsString()),key->new TreeSet<>());
                    targets.forEach(holder->choices.add(holder.unwrapKey().orElseThrow().location()));
                }
                continue;
            }
            if(!type.equals("minecraft:direct")){row.addProperty("closureRefusal","unqualified_alias:"+type);return;}
            aliases.computeIfAbsent(ResourceLocation.parse(binding.get("alias").getAsString()),key->new TreeSet<>())
                    .add(ResourceLocation.parse(binding.get("target").getAsString()));
        }
        var queue=new ArrayDeque<Request>();queue.add(new Request(root,null,0));
        var reachedPools=new TreeSet<String>();var reachedVariants=new TreeSet<String>();var missing=new TreeSet<String>();
        var visited=new HashMap<String,Integer>();int requests=0,matched=0,rejected=0;
        while(!queue.isEmpty()) {
            var request=queue.removeFirst();
            for(var target:aliases.getOrDefault(request.pool(),Set.of(request.pool()))) {
                String key=target+"|"+portKey(request.parent());var before=visited.get(key);
                if(before!=null && before<=request.depth())continue;
                if(++requests>65536)throw new IllegalStateException("Connector coverage request budget exhausted");
                visited.put(key,request.depth());
                var pool=pools.get(target);if(pool==null){missing.add(target.toString());continue;}
                reachedPools.add(target.toString());
                pool(target,pool);
                var candidates=new ArrayList<StructureTemplatePool>();
                if(request.depth()<=maximumDepth)candidates.add(pool);
                if(request.parent()!=null) {
                    // Native children try one fallback, even at max depth; fallbacks are not recursively chased.
                    candidates.add(pool.getFallback().value());
                    reachedPools.add(pool.getFallback().getRegisteredName());
                    pool(pool.getFallback().unwrapKey().orElseThrow().location(),pool.getFallback().value());
                }
                for(var candidate:candidates)for(var choice:((TemplatePoolSearchAccess)candidate).skyweave$entries()) {
                    if(choice.getSecond()<=0)continue;
                    if(++visits>4_000_000)throw new IllegalStateException("Connector coverage entry budget exhausted");
                    var element=element(choice.getFirst());
                    boolean connects=request.parent()==null || element.ports().stream().anyMatch(port->JigsawBlock.canAttach(request.parent(),port));
                    if(!connects){rejected++;continue;}
                    matched++;reachedVariants.add(element.id());
                    if(maximumDepth>0 && request.depth()<=maximumDepth)for(var port:element.ports()) {
                        if(port.nbt()==null)continue;
                        var next=ResourceLocation.tryParse(port.nbt().getString("pool"));
                        if(next!=null && !next.equals(ResourceLocation.withDefaultNamespace("empty")))
                            queue.addLast(new Request(next,port,request.depth()+1));
                    }
                }
            }
        }
        row.add("reachablePools",new Gson().toJsonTree(reachedPools));row.add("reachableVariants",new Gson().toJsonTree(reachedVariants));
        row.add("missingPools",new Gson().toJsonTree(missing));row.addProperty("connectorCandidateMatches",matched);
        row.addProperty("connectorCandidateRefusals",rejected);row.addProperty("closureRequests",requests);
    }
    private void pool(ResourceLocation id,StructureTemplatePool value) {
        if(poolContents.containsKey(id.toString()))return;
        var row=new JsonObject();row.addProperty("fallback",value.getFallback().getRegisteredName());var entries=new JsonArray();
        for(var pair:((TemplatePoolSearchAccess)value).skyweave$entries()) {
            var entry=new JsonObject();entry.addProperty("variant",element(pair.getFirst()).id());entry.addProperty("weight",pair.getSecond());entries.add(entry);
        }
        row.add("runtimeEntries",entries);
        server.getResourceManager().getResource(id.withPath("worldgen/template_pool/"+id.getPath()+".json")).ifPresent(resource->{
            row.addProperty("winningResourceProvider",resource.sourcePackId());
            try(var input=resource.open()){row.addProperty("winningResourceSha256",digest(input.readAllBytes()));}
            catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
        });
        poolContents.put(id.toString(),row);
    }
    private Element element(StructurePoolElement original) {
        var known=elements.get(original);if(known!=null)return known;
        if(elements.size()>=32768)throw new IllegalStateException("Coverage element budget exhausted");
        var row=new JsonObject();row.addProperty("outerClass",original.getClass().getName());
        row.addProperty("projection",original.getProjection().getSerializedName());
        var element=original;var delegates=new JsonArray();
        for(int depth=0;element instanceof VillageDelegate delegate && depth<8;depth++) {
            var entry=new JsonObject();entry.addProperty("class",element.getClass().getName());
            entry.addProperty("terrainAdjustment",delegate.skyweave$terrainAdjustment().map(Enum::name).orElse("inherited"));delegates.add(entry);
            element=delegate.skyweave$delegate();
        }
        row.add("delegates",delegates);row.addProperty("class",element.getClass().getName());
        var ports=new ArrayList<StructureTemplate.StructureBlockInfo>();
        if(element instanceof SinglePoolElement single) {
            var access=(VillageTemplateAccess)single;var id=access.skyweave$template().left().orElse(null);
            row.addProperty("template",id==null?"inline":id.toString());row.addProperty("groundDelta",single.getGroundLevelDelta());
            var piece=new PoolElementStructurePiece(server.getStructureManager(),original,BlockPos.ZERO,original.getGroundLevelDelta(),Rotation.NONE,
                    original.getBoundingBox(server.getStructureManager(),BlockPos.ZERO,Rotation.NONE),LiquidSettings.APPLY_WATERLOGGING);
            row.addProperty("role",role(piece));
            if(id!=null) {
                var location=id.withPath("structure/"+id.getPath()+".nbt");
                server.getResourceManager().getResource(location).ifPresent(resource->{
                    row.addProperty("provider",resource.sourcePackId());
                    try(var input=resource.open()){row.addProperty("sourceSha256",digest(input.readAllBytes()));}
                    catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
                });
            }
            var settings=access.skyweave$settings(Rotation.NONE,new BoundingBox(-2048,-2048,-2048,2048,2048,2048),LiquidSettings.APPLY_WATERLOGGING,false);
            var sequence=new JsonArray();for(var processor:settings.getProcessors())sequence.add(VillageSupportSurvey.definition(processor,ops,0,new int[1]));
            row.add("processors",sequence);String qualification=VillageProcessors.qualify(settings.getProcessors(),server.registryAccess());
            row.addProperty("processorQualification",qualification==null?"declared_graph_qualified; input_and_actual_footing_require_native_preview":qualification);
            row.addProperty("materialRule",sequence.toString().contains("skyweave_compat:natural_soil")?"explicit_natural_base_soil":"native_authored_processors");
            var palettes=((VillagePaletteAccess)access.skyweave$template(server.getStructureManager())).skyweave$palettes();
            if(palettes.isEmpty())row.addProperty("disposition","empty_native_template; no_compatible_child_ports; not_bearing_qualified");
            int water=0,lava=0,campfires=0,scaffolds=0,rawFloor=0,beds=0;long blocks=0;
            for(var palette:palettes)for(var block:palette.blocks()) {
                if(++blocks>1_048_576)throw new IllegalStateException("Coverage template block budget exhausted");
                var state=block.state();if(state.is(Blocks.WATER))water++;if(state.is(Blocks.LAVA))lava++;
                if(state.is(Blocks.CAMPFIRE)||state.is(Blocks.SOUL_CAMPFIRE))campfires++;if(state.is(Blocks.SCAFFOLDING))scaffolds++;
                if(state.is(net.minecraft.tags.BlockTags.BEDS))beds++;
                if(block.pos().getY()<=single.getGroundLevelDelta()-1 && root(state))rawFloor++;
                if(state.is(Blocks.JIGSAW))for(var rotation:Rotation.values())ports.add(new StructureTemplate.StructureBlockInfo(
                        StructureTemplate.calculateRelativePosition(new StructurePlaceSettings().setRotation(rotation),block.pos()),state.rotate(rotation),block.nbt()));
            }
            row.addProperty("palettes",palettes.size());row.addProperty("paletteBlockVisits",blocks);row.addProperty("rawFloorCandidates",rawFloor);
            row.addProperty("rawWater",water);row.addProperty("rawLava",lava);row.addProperty("rawCampfires",campfires);row.addProperty("rawScaffolds",scaffolds);
            row.addProperty("rawBeds",beds);
            row.addProperty("rawFlagsAreNotDefects",true);
            boolean qualifiedSingle=element.getClass()==SinglePoolElement.class || element.getClass()==LegacySinglePoolElement.class;
            row.addProperty("bearingApplicability",rawFloor==0?"no_raw_floor_candidates":qualification!=null?"unqualified_processor_graph":
                    "candidate_only; selected_palette_position_and_processed_feet_require_native_preview");
            row.addProperty("localCutApplicability",!qualifiedSingle?"unqualified_single_subclass":
                    original.getProjection()!=StructureTemplatePool.Projection.RIGID?"projected_native_route":
                    !row.get("role").getAsString().equals("DECORATION")?"inhabited_or_road_not_decoration":
                    settings.getProcessors().stream().anyMatch(processor->processor.getClass()!=BlockIgnoreProcessor.class
                            && processor.getClass()!=JigsawReplacementProcessor.class && processor.getClass()!=NopProcessor.class)
                            ?"unqualified_cut_processor_sequence":"authored_air_fluid_candidate; final_conflict_is_layout_relative");
            row.addProperty("nativeEvidence","not_inferred_from_coverage; join exact template/processor identity to recorded native fixtures");
        } else if(element==EmptyPoolElement.INSTANCE)row.addProperty("disposition","empty_native_choice");
        else {
            row.addProperty("disposition","non-single_element_requires_own_native_contract");
            for(var rotation:Rotation.values())ports.addAll(original.getShuffledJigsawBlocks(server.getStructureManager(),BlockPos.ZERO,rotation,net.minecraft.util.RandomSource.create(0)));
        }
        var unique=new LinkedHashMap<String,StructureTemplate.StructureBlockInfo>();for(var port:ports)unique.putIfAbsent(portKey(port),port);
        row.addProperty("connectorSignatures",unique.size());
        String id=digest(row.toString().getBytes(StandardCharsets.UTF_8));variants.put(id,row);
        var value=new Element(id,List.copyOf(unique.values()));elements.put(original,value);return value;
    }
}
