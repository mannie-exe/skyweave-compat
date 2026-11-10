package design.inherent.skyweavecompat.terrain.structure;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import org.sathrek.sky_archipelago.config.SkyIslandSettings;
import org.sathrek.sky_archipelago.worldgen.benchmark.StructureBenchmarkRecorder;
import org.sathrek.sky_archipelago.worldgen.generator.field.IslandField;

/** Close cut road connectors using their declared native fallback, never an invented bridge. */
public final class VillageRoadEnds {
    private VillageRoadEnds() {}
    public static List<StructurePiece> close(List<StructurePiece> input,IslandField field,SkyIslandSettings settings) {
        var scope=StructurePlacementScope.current();
        if(scope==null || scope.family()!=StructureFamily.VILLAGES)return input;
        var result=new ArrayList<>(input);
        var templates=scope.pass().templateManager();
        var registry=scope.pass().registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        var policy=scope.distribution().villageFitting();
        var terrain=new VillageLayoutRepair.Terrain(field,settings,policy.maximumColumnProbes());
        // The qualified vanilla pool piece codec uses registries and templates, not resources.
        var serialization = new StructurePieceSerializationContext(net.minecraft.server.packs.resources.ResourceManager.Empty.INSTANCE,
                scope.pass().registryAccess(), templates);
        var parents = new java.util.IdentityHashMap<PoolElementStructurePiece, PoolElementStructurePiece>();
        int caps=0,attempts=0;
        var nodes=input.stream().map(piece->VillageLayoutRepair.node(piece,templates)).toList();
        roads: for(var node:nodes) {
            if(!(node.piece() instanceof PoolElementStructurePiece source))continue;
            // Do not discard unknown subclass state by copying it into a vanilla piece.
            if (source.getClass() != PoolElementStructurePiece.class) continue;
            if(node.role()!=VillageLayoutRepair.Role.ROAD && node.role()!=VillageLayoutRepair.Role.CENTER)continue;
            for(var port:node.ports()) {
                if(caps==8 || attempts>=64 || terrain.exhausted)break roads;
                var direction=JigsawBlock.getFrontFacing(port.state());
                if(direction.getAxis().isVertical() || port.nbt()==null)continue;
                var target=port.pos().relative(direction);
                boolean connected=nodes.stream().filter(other->other!=node).flatMap(other->other.ports().stream())
                        .anyMatch(other->other.pos().getX()==target.getX() && other.pos().getZ()==target.getZ() && JigsawBlock.canAttach(port,other));
                var cut = source.getJunctions().stream().filter(j -> j.getSourceX() == target.getX()
                        && j.getSourceZ() == target.getZ()).toList();
                if (connected || cut.size() != 1) continue;
                var poolId=ResourceLocation.tryParse(port.nbt().getString("pool"));
                var pool=poolId==null ? null : registry.get(poolId);
                if(pool==null)continue;
                boolean placed=false;
                for(var choice:pool.getFallback().value().getShuffledTemplates(RandomSource.create(port.pos().asLong()^scope.pass().levelSeed())).stream().limit(4).toList()) {
                    if(placed)break;
                    if(choice==net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement.INSTANCE)continue;
                    // A private codec copy keeps global pool projections/processors immutable.
                    var ops=RegistryOps.create(JsonOps.INSTANCE,scope.pass().registryAccess());
                    var encoded=StructurePoolElement.CODEC.encodeStart(ops,choice).getOrThrow();
                    rigidProjection(encoded);
                    var copy=StructurePoolElement.CODEC.parse(ops,encoded).getOrThrow();
                    for(var rotation:Rotation.values()) {
                        if(placed || attempts++>=64)break;
                        for(var connector:copy.getShuffledJigsawBlocks(templates,BlockPos.ZERO,rotation,RandomSource.create(0))) {
                            if(!JigsawBlock.canAttach(port,connector))continue;
                            var pos=target.subtract(connector.pos());
                            var bounds=copy.getBoundingBox(templates,pos,rotation);
                            if(result.stream().anyMatch(piece->piece.getBoundingBox().intersects(bounds)))continue;
                            int delta = port.pos().getY() - source.getBoundingBox().minY() - connector.pos().getY();
                            int groundDelta = source.getElement().getProjection() == StructureTemplatePool.Projection.RIGID
                                    ? source.getGroundLevelDelta() - delta : copy.getGroundLevelDelta();
                            var sourceTag = source.createTag(serialization);
                            var liquids = sourceTag.contains("liquid_settings")
                                    ? LiquidSettings.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE,
                                            sourceTag.get("liquid_settings")).getOrThrow()
                                    : net.minecraft.world.level.levelgen.structure.structures.JigsawStructure.DEFAULT_LIQUID_SETTINGS;
                            var cap=new PoolElementStructurePiece(templates,copy,pos,groundDelta,rotation,bounds,liquids);
                            var description=VillageLayoutRepair.node(cap,templates);
                            if(description.role()!=VillageLayoutRepair.Role.ROAD || !FoundationSupport.evaluate(description.feet(),terrain::ground,policy).accepted())continue;
                            var parent = parents.get(source);
                            if (parent == null) {
                                parent = new PoolElementStructurePiece(serialization, sourceTag);
                                parents.put(source, parent);
                                result.set(result.indexOf(source), parent);
                            }
                            parent.getJunctions().removeIf(j -> j.getSourceX() == target.getX() && j.getSourceZ() == target.getZ());
                            parent.addJunction(new JigsawJunction(target.getX(),
                                    source.getBoundingBox().minY() + source.getGroundLevelDelta(), target.getZ(),
                                    delta, StructureTemplatePool.Projection.RIGID));
                            cap.addJunction(new JigsawJunction(port.pos().getX(), bounds.minY() + groundDelta,
                                    port.pos().getZ(), -delta, source.getElement().getProjection()));
                            result.add(cap);caps++;placed=true;break;
                        }
                    }
                }
            }
        }
        StructureBenchmarkRecorder.add("skyweaveVillageTerminators",caps);
        StructureBenchmarkRecorder.add("skyweaveVillageTerminatorAttempts",attempts);
        return result;
    }

    /** Rewrite only encoded pool nodes; decoded empty elements may be shared singletons. */
    private static void rigidProjection(JsonElement encoded) {
        if(!encoded.isJsonObject())return;
        var node=encoded.getAsJsonObject();
        if(node.has("projection"))node.addProperty("projection","rigid");
        if(node.has("delegate"))rigidProjection(node.get("delegate"));
        if(node.has("elements") && node.get("elements").isJsonArray())
            node.getAsJsonArray("elements").forEach(VillageRoadEnds::rigidProjection);
    }
}
