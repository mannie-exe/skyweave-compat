package design.inherent.skyweavecompat.probe;

import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.Vec3;
import java.util.TreeMap;

/** Native no-player loot rolls from realized containers, not Lootr entitlements. */
final class VillageContentProbe {
    private VillageContentProbe() {}

    /** Original rigid courts use the empty processor list, so their functional block positions are exact. */
    static JsonArray authoredCourts(ServerLevel level, net.minecraft.world.level.levelgen.structure.StructureStart start) {
        var rows = new JsonArray();
        for (var piece : start.getPieces()) {
            if (!(piece instanceof net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece pool)) continue;
            var element = pool.getElement();
            for (int depth=0; element instanceof design.inherent.skyweavecompat.terrain.structure.VillageDelegate delegate && depth<8; depth++)
                element=delegate.skyweave$delegate();
            if (!(element instanceof net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement single)) continue;
            var access=(design.inherent.skyweavecompat.mixin.VillageTemplateAccess)single;
            var id=access.skyweave$template().left().orElse(null);
            if (id==null || !id.getNamespace().equals("skyweave_compat") || !id.getPath().startsWith("villages/")) continue;
            if (single.getProjection()!=net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool.Projection.RIGID
                    || !access.skyweave$processors().unwrapKey().orElseThrow().location().equals(net.minecraft.resources.ResourceLocation.parse("minecraft:empty")))
                throw new AssertionError("Unqualified authored-court processing: "+id);
            var template=access.skyweave$template(level.getServer().getStructureManager());
            var palettes=((design.inherent.skyweavecompat.mixin.VillagePaletteAccess)template).skyweave$palettes();
            if(palettes.size()!=1)throw new AssertionError("Unqualified authored-court palettes: "+id);
            var expected=new TreeMap<String,Integer>();var matched=new TreeMap<String,Integer>();
            var failures=new JsonArray();int nonAir=0,sameBlock=0,floor=0,contacts=0;
            for(var info:palettes.getFirst().blocks()) {
                var state=info.state();
                if(state.isAir() || state.is(net.minecraft.world.level.block.Blocks.JIGSAW)
                        || state.is(net.minecraft.world.level.block.Blocks.STRUCTURE_VOID))continue;
                nonAir++;
                var at=net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.transform(info.pos(),
                        net.minecraft.world.level.block.Mirror.NONE,pool.getRotation(),BlockPos.ZERO).offset(pool.getPosition());
                var actual=level.getBlockState(at);
                boolean same=actual.is(state.getBlock());if(same)sameBlock++;
                if(info.pos().getY()==single.getGroundLevelDelta()-1 && state.isFaceSturdy(
                        net.minecraft.world.level.EmptyBlockGetter.INSTANCE,BlockPos.ZERO,net.minecraft.core.Direction.DOWN)) {
                    floor++;var below=at.below();
                    if(level.getBlockState(below).isFaceSturdy(level,below,net.minecraft.core.Direction.UP))contacts++;
                    else if(failures.size()<32) {
                        var failure=new JsonObject();failure.addProperty("unsupportedFloor",at.toShortString());
                        failure.addProperty("local",info.pos().toShortString());
                        var column=new JsonArray();for(int dy=1;dy<=9;dy++)column.add(level.getBlockState(at.below(dy)).toString());
                        failure.add("below",column);
                        var chunk=level.getChunkSource().getChunkNow(at.getX()>>4,at.getZ()>>4);
                        var proof=chunk==null?null:design.inherent.skyweavecompat.terrain.structure.GroundSupportProofs.existing(level,chunk);
                        var outcomes=new JsonArray();if(proof!=null)for(var entry:proof.entries())if(entry.foot().position().equals(at)) {
                            var outcome=new JsonObject();outcome.addProperty("template",entry.foot().template().toString());
                            outcome.addProperty("outcome",entry.outcome().name());outcome.addProperty("stored",entry.state().name());
                            outcome.addProperty("current",design.inherent.skyweavecompat.terrain.structure.GroundSupportProofs.inspect(entry,chunk).name());outcomes.add(outcome);
                        }
                        failure.add("supportProof",outcomes);failures.add(failure);
                    }
                }
                // Workstations, storage, crops and the rigging chain are identity-bearing contents.
                boolean functional=state.hasBlockEntity() || state.getBlock() instanceof net.minecraft.world.level.block.CropBlock
                        || state.is(net.minecraft.world.level.block.Blocks.STONECUTTER)
                        || state.is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE)
                        || state.is(net.minecraft.world.level.block.Blocks.SMITHING_TABLE)
                        || state.is(net.minecraft.world.level.block.Blocks.FLETCHING_TABLE)
                        || state.is(net.minecraft.world.level.block.Blocks.CARTOGRAPHY_TABLE)
                        || state.is(net.minecraft.world.level.block.Blocks.LOOM)
                        || state.is(net.minecraft.world.level.block.Blocks.GRINDSTONE)
                        || state.is(net.minecraft.world.level.block.Blocks.COMPOSTER)
                        || state.is(net.minecraft.world.level.block.Blocks.CHAIN);
                if(!functional)continue;
                String block=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();expected.merge(block,1,Integer::sum);
                if(same)matched.merge(block,1,Integer::sum);
                else if(failures.size()<16) {
                    var failed=new JsonObject();failed.addProperty("at",at.toShortString());failed.addProperty("expected",state.toString());
                    failed.addProperty("actual",actual.toString());failures.add(failed);
                }
            }
            var row=new JsonObject();row.addProperty("template",id.toString());row.addProperty("nonAir",nonAir);row.addProperty("sameBlock",sameBlock);
            row.addProperty("floorSamples",floor);row.addProperty("sturdyBeneathFloor",contacts);
            row.add("expectedFunctionalBlocks",new Gson().toJsonTree(expected));row.add("matchedFunctionalBlocks",new Gson().toJsonTree(matched));
            row.add("failures",failures);rows.add(row);
            if(expected.isEmpty() || !expected.equals(matched) || floor==0 || floor!=contacts) {
                row.add("nativeDiagnostics",new Gson().toJsonTree(org.sathrek.sky_archipelago.worldgen.benchmark.StructureBenchmarkRecorder
                        .report("skyweave",level.getSeed(),java.util.List.of(),java.util.Map.of())));
                throw new AssertionError("Authored court lost functional contents or floor contact: "+row);
            }
        }
        return rows;
    }

    /** Native walking across the defining court/landmark seam; no persistent actor or distant chunks. */
    static JsonObject landmarkAccess(ServerLevel level, net.minecraft.resources.ResourceLocation profile,
            net.minecraft.world.level.levelgen.structure.StructureStart start) {
        var roots=new java.util.ArrayList<net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece>();
        var landmarks=new java.util.ArrayList<net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece>();
        for(var piece:start.getPieces()) {
            if(!(piece instanceof net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece pool))continue;
            var roles=design.inherent.skyweavecompat.terrain.structure.VillageComposition.assess(profile,java.util.List.of(piece)).counts();
            if(roles.getOrDefault("authored_court",0)>0)roots.add(pool);
            if(roles.getOrDefault("regional_landmark",0)>0)landmarks.add(pool);
        }
        if(roots.isEmpty() && landmarks.isEmpty())return new JsonObject();
        if(roots.size()!=1 || landmarks.size()!=1)throw new AssertionError("Ambiguous defining court/landmark occurrence");
        var ports=roots.getFirst().getElement().getShuffledJigsawBlocks(level.getStructureManager(),roots.getFirst().getPosition(),
                roots.getFirst().getRotation(),net.minecraft.util.RandomSource.create(0));
        var exits=landmarks.getFirst().getElement().getShuffledJigsawBlocks(level.getStructureManager(),landmarks.getFirst().getPosition(),
                landmarks.getFirst().getRotation(),net.minecraft.util.RandomSource.create(0));
        var rows=new JsonArray();
        for(var parent:ports) {
            var direction=net.minecraft.world.level.block.JigsawBlock.getFrontFacing(parent.state());
            if(direction.getAxis().isVertical())continue;
            var adjacent=parent.pos().relative(direction);
            for(var child:exits) {
                if(adjacent.getX()!=child.pos().getX() || adjacent.getZ()!=child.pos().getZ()
                        || !net.minecraft.world.level.block.JigsawBlock.canAttach(parent,child))continue;
                var from=parent.pos().relative(direction.getOpposite(),2);
                var to=child.pos().relative(direction,2);
                if (profile.toString().equals("skyweave_compat:villages/flower_gate_dye_court")) {
                    if (level.getChunkSource().getChunkNow(to.getX() >> 4, to.getZ() >> 4) == null
                            || !level.getBlockState(to).is(net.minecraft.world.level.block.Blocks.STONE_BRICK_STAIRS)) {
                        throw new AssertionError("Missing retained stone-brick landing stair at " + to);
                    }
                    to=to.above();
                }
                if (profile.toString().equals("skyweave_compat:villages/snowline_relay_lodge")) {
                    from=parent.pos().relative(direction.getOpposite(),6).below(3);
                }
                var row=new JsonObject();row.addProperty("courtPort",parent.pos().toShortString());row.addProperty("landmarkPort",child.pos().toShortString());
                row.add("outward",walk(level,from,to));row.add("return",walk(level,to,from));rows.add(row);
            }
        }
        if(rows.size()!=1)throw new AssertionError("Missing or ambiguous actual defining connector pair: "+rows);
        var result=new JsonObject();result.add("seams",rows);result.addProperty("scope","Bounded unspawned native villager paths with door capability; not all-house navigation or town admission");
        return result;
    }

    private static JsonObject walk(ServerLevel level,BlockPos from,BlockPos to) {
        var minimum=new BlockPos(Math.min(from.getX(),to.getX())-3,Math.min(from.getY(),to.getY())-4,Math.min(from.getZ(),to.getZ())-3);
        var maximum=new BlockPos(Math.max(from.getX(),to.getX())+3,Math.max(from.getY(),to.getY())+4,Math.max(from.getZ(),to.getZ())+3);
        for(int x=Math.floorDiv(minimum.getX(),16);x<=Math.floorDiv(maximum.getX(),16);x++)
            for(int z=Math.floorDiv(minimum.getZ(),16);z<=Math.floorDiv(maximum.getZ(),16);z++)
                if(level.getChunkSource().getChunkNow(x,z)==null)throw new AssertionError("Native access fixture would load a missing chunk");
        int before=level.getChunkSource().getLoadedChunksCount();
        var actor=net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        if(actor==null)throw new AssertionError("Missing native villager actor");
        try {
            actor.setPos(from.getX()+.5,from.getY(),from.getZ()+.5);
            var evaluator=new net.minecraft.world.level.pathfinder.WalkNodeEvaluator();
            evaluator.setCanOpenDoors(true);evaluator.setCanPassDoors(true);
            var path=new net.minecraft.world.level.pathfinder.PathFinder(evaluator,2048).findPath(
                    new net.minecraft.world.level.PathNavigationRegion(level,minimum,maximum),actor,java.util.Set.of(to),16,0,1);
            var nodes=new JsonArray();
            if(path!=null)for(int i=0;i<Math.min(32,path.getNodeCount());i++)nodes.add(path.getNode(i).asBlockPos().toShortString());
            var row=new JsonObject();row.addProperty("from",from.toShortString());row.addProperty("to",to.toShortString());
            row.addProperty("canReach",path!=null && path.canReach());row.add("nodes",nodes);
            if(level.getChunkSource().getLoadedChunksCount()!=before)throw new AssertionError("Native access fixture loaded chunks");
            if(path==null || !path.canReach() || path.getNodeCount()>32)throw new AssertionError("Defining landmark seam is not traversable: "+row);
            return row;
        } finally { actor.discard(); }
    }

    /** Local placement integrity, not villager pathfinding or a survival playtest. */
    static JsonObject access(ServerLevel level, BoundingBox bounds) {
        int doors = 0, beds = 0, supportedBeds = 0;
        var unsupportedDoors = new JsonArray();
        var pos = new BlockPos.MutableBlockPos();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) for (int z = bounds.minZ(); z <= bounds.maxZ(); z++)
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                var state = level.getBlockState(pos.set(x,y,z));
                if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                        && state.getValue(net.minecraft.world.level.block.DoorBlock.HALF)
                        == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
                    doors++;
                    if (!state.canSurvive(level,pos)) unsupportedDoors.add(pos.toShortString());
                }
                if (state.getBlock() instanceof net.minecraft.world.level.block.BedBlock
                        && state.getValue(net.minecraft.world.level.block.BedBlock.PART)
                        == net.minecraft.world.level.block.state.properties.BedPart.HEAD) {
                    beds++;
                    var below = pos.below();
                    if (!level.getBlockState(below).getCollisionShape(level,below).isEmpty()) supportedBeds++;
                }
            }
        var result = new JsonObject(); result.addProperty("doors",doors); result.addProperty("beds",beds);
        result.addProperty("bedsWithSupportBelowHead",supportedBeds);result.add("unsupportedDoorPositions",unsupportedDoors);
        result.addProperty("evidence","Native door survival and local bed footing only; access/routes/jobs require separate review");
        return result;
    }

    static JsonArray inspect(ServerLevel level, BoundingBox bounds) throws Exception {
        return inspect(level, bounds, true);
    }

    static JsonArray inspect(ServerLevel level, BoundingBox bounds, boolean requireContainers) throws Exception {
        var rows = new JsonArray();
        for (int cx = bounds.minX() >> 4; cx <= bounds.maxX() >> 4; cx++)
            for (int cz = bounds.minZ() >> 4; cz <= bounds.maxZ() >> 4; cz++) {
                var entities = level.getChunk(cx, cz).getBlockEntities();
                // Stable ordering makes the saved/reopened comparison meaningful.
                for (var pos : entities.keySet().stream().sorted().toList()) {
                    if (!bounds.isInside(pos)) continue;
                    var entity = entities.get(pos);
                    ResourceKey<LootTable> key;
                    try {
                        @SuppressWarnings("unchecked")
                        var lootrKey = (ResourceKey<LootTable>)entity.getClass().getMethod("getInfoLootTable").invoke(entity);
                        key = lootrKey;
                    } catch (NoSuchMethodException absent) {
                        key = entity instanceof RandomizableContainerBlockEntity container ? container.getLootTable() : null;
                    }
                    if (key == null) continue;
                    var table = level.getServer().reloadableRegistries().getLootTable(key);
                    if (table == LootTable.EMPTY) throw new AssertionError("Missing realized village loot table: " + key);
                    var params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                            .create(LootContextParamSets.CHEST);
                    var counts = new TreeMap<String, Integer>();
                    for (var stack : table.getRandomItems(params, pos.asLong() ^ level.getSeed()))
                        counts.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
                    var row = new JsonObject(); row.add("position", new Gson().toJsonTree(pos));
                    row.addProperty("container", BuiltInRegistries.BLOCK.getKey(entity.getBlockState().getBlock()).toString());
                    row.addProperty("lootTable", key.location().toString()); row.add("sampleItems", new Gson().toJsonTree(counts));
                    rows.add(row);
                }
            }
        if (requireContainers && rows.isEmpty()) throw new AssertionError("No realized village loot containers");
        return rows;
    }
}
