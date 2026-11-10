package design.inherent.skyweavecompat.probe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import design.inherent.skyweavecompat.terrain.structure.*;
import java.util.function.Supplier;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import org.sathrek.sky_archipelago.worldgen.generator.structure.StructurePlacementPassContext;

/** Exercises declared road fallbacks without placing blocks or changing registered pools. */
public final class VillageTerminatorProbe {
    private VillageTerminatorProbe() {}

    public static JsonObject inspect(StructureDistribution distribution, StructureStart start) throws Exception {
        var field = StructureDistribution.class.getDeclaredField("environment");
        field.setAccessible(true);
        var environment = (StructurePlacementPassContext)field.get(distribution);
        var planning = Class.forName("design.inherent.skyweavecompat.terrain.structure.LayoutPlanning");
        var at = planning.getDeclaredMethod("at", StructurePlacementPassContext.class, StructureSelection.Site.class);
        at.setAccessible(true);
        var pass = (StructurePlacementPassContext)at.invoke(null, environment,
                new StructureSelection.Site(start.getChunkPos().x, start.getChunkPos().z));
        var scope = new StructurePlacementScope(distribution, StructureFamily.VILLAGES, pass, true);
        var result = new JsonObject();
        result.add("originalAssemblyRetention", retention(scope, start));
        var attempts = new JsonArray();
        result.add("attempts", attempts);
        var emptyProjection = EmptyPoolElement.INSTANCE.getProjection();
        var serialization = new net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext(
                net.minecraft.server.packs.resources.ResourceManager.Empty.INSTANCE, pass.registryAccess(), pass.templateManager());
        int checked = 0;
        for (var piece : start.getPieces()) {
            var node = node(piece, pass);
            if (value(node, "role") != VillageLayoutRepair.Role.ROAD || !(piece instanceof PoolElementStructurePiece road)
                    || road.getJunctions().isEmpty()) continue;
            if (checked++ == 12) break;
            var projection = road.getElement().getProjection();
            var fallbacks = fallbackProjections(node, pass);
            var originalTag = road.createTag(serialization);
            List<StructurePiece> cut = List.of(road);
            var repaired = within(scope,
                    () -> VillageRoadEnds.close(cut, pass.islandField(), pass.settings()));
            var row = new JsonObject();
            row.addProperty("road", road.getElement().toString());
            row.addProperty("junctions", road.getJunctions().size());
            row.addProperty("caps", repaired.size() - 1);
            attempts.add(row);
            if (road.getElement().getProjection() != projection || EmptyPoolElement.INSTANCE.getProjection() != emptyProjection)
                throw new AssertionError("Road closure mutated a registered pool element");
            for (var entry : fallbacks.entrySet()) if (entry.getKey().getProjection() != entry.getValue())
                throw new AssertionError("Road closure mutated a registered fallback element");
            if (!originalTag.equals(road.createTag(serialization))) throw new AssertionError("Road closure mutated its input piece");
            if (repaired.size() == 1) continue;
            junctions(repaired, road, pass);
            for (var cap : repaired.subList(1, repaired.size())) {
                var capNode = node(cap, pass);
                if (!FoundationSupport.evaluate(feet(capNode), foot -> ground(pass, foot), distribution.villageFitting()).accepted())
                    throw new AssertionError("Native terminator has unsupported footing");
            }
            // Translate private decoded pieces into proven void, including their junctions.
            var voidX = 29_999_000;
            var voidZ = 29_999_000;
            var shifted = new PoolElementStructurePiece(pass.templateManager(), road.getElement(),
                    new BlockPos(voidX, road.getPosition().getY(), voidZ), road.getGroundLevelDelta(), road.getRotation(),
                    road.getBoundingBox().moved(voidX - road.getPosition().getX(), 0, voidZ - road.getPosition().getZ()),
                    net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings.IGNORE_WATERLOGGING);
            int dx = voidX - road.getPosition().getX(), dz = voidZ - road.getPosition().getZ();
            for (var junction : road.getJunctions()) shifted.addJunction(new net.minecraft.world.level.levelgen.structure.pools.JigsawJunction(
                    junction.getSourceX() + dx, junction.getSourceGroundY(), junction.getSourceZ() + dz,
                    junction.getDeltaY(), junction.getDestProjection()));
            if (feet(node(shifted, pass)).stream().anyMatch(foot -> ground(pass, foot) != FoundationSupport.MISSING)) {
                result.addProperty("voidControl", "chosen control intersects terrain");
                continue;
            }
            var rejected = within(scope,
                    () -> VillageRoadEnds.close(List.of(shifted), pass.islandField(), pass.settings()));
            if (rejected.size() != 1) throw new AssertionError("Road closure fabricated a cap over void");
            result.addProperty("positiveCaps", repaired.size() - 1);
            result.addProperty("voidCaps", rejected.size() - 1);
            result.addProperty("registeredProjectionsUnchanged", true);
            result.addProperty("reciprocalJunctionsVerified", true);
            result.addProperty("inputPiecesUnchanged", true);
            return result;
        }
        result.addProperty("positiveCaps", 0);
        return result;
    }

    private static Object node(StructurePiece piece, StructurePlacementPassContext pass) throws Exception {
        var method = VillageLayoutRepair.class.getDeclaredMethod("node", StructurePiece.class,
                net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager.class);
        method.setAccessible(true);
        return method.invoke(null, piece, pass.templateManager());
    }

    @SuppressWarnings("unchecked")
    private static void junctions(List<StructurePiece> repaired, PoolElementStructurePiece original,
            StructurePlacementPassContext pass) throws Exception {
        var parent = (PoolElementStructurePiece)repaired.getFirst();
        if (parent == original) throw new AssertionError("Road closure did not isolate parent junction edits");
        var ports = (List<net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo>)
                value(node(parent, pass), "ports");
        for (var added : repaired.subList(1, repaired.size())) {
            var cap = (PoolElementStructurePiece)added;
            if (cap.getJunctions().size() != 1) throw new AssertionError("A native cap requires one reciprocal junction");
            var back = cap.getJunctions().getFirst();
            boolean matched = false;
            for (var port : ports) for (var other : cap.getElement().getShuffledJigsawBlocks(pass.templateManager(),
                    cap.getPosition(), cap.getRotation(), net.minecraft.util.RandomSource.create(0))) {
                var target = port.pos().relative(net.minecraft.world.level.block.JigsawBlock.getFrontFacing(port.state()));
                if (!target.equals(other.pos()) || !net.minecraft.world.level.block.JigsawBlock.canAttach(port, other)) continue;
                int delta = port.pos().getY() - parent.getBoundingBox().minY()
                        - (other.pos().getY() - cap.getPosition().getY());
                boolean forward = parent.getJunctions().stream().anyMatch(j -> j.getSourceX() == target.getX()
                        && j.getSourceZ() == target.getZ() && j.getSourceGroundY() == parent.getBoundingBox().minY() + parent.getGroundLevelDelta()
                        && j.getDeltaY() == delta && j.getDestProjection() == cap.getElement().getProjection());
                if (forward && back.getSourceX() == port.pos().getX() && back.getSourceZ() == port.pos().getZ()
                        && back.getSourceGroundY() == cap.getBoundingBox().minY() + cap.getGroundLevelDelta()
                        && back.getDeltaY() == -delta && back.getDestProjection() == parent.getElement().getProjection()) matched = true;
            }
            if (!matched) throw new AssertionError("Native road-cap junction pair disagrees with its connectors");
        }
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement,
            net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool.Projection> fallbackProjections(
                    Object node, StructurePlacementPassContext pass) throws Exception {
        var result = new java.util.IdentityHashMap<net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement,
                net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool.Projection>();
        var registry = pass.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.TEMPLATE_POOL);
        for (var port : (List<net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo>)value(node, "ports")) {
            if (port.nbt() == null) continue;
            var id = net.minecraft.resources.ResourceLocation.tryParse(port.nbt().getString("pool"));
            var pool = id == null ? null : registry.get(id);
            if (pool == null) continue;
            for (var choice : pool.getFallback().value().getShuffledTemplates(net.minecraft.util.RandomSource.create(0))
                    .stream().distinct().limit(256).toList()) result.put(choice, choice.getProjection());
        }
        return result;
    }

    /** The same surviving pieces must not acquire a smaller denominator at a later pass. */
    private static JsonObject retention(StructurePlacementScope scope, StructureStart original) throws Exception {
        var templates = scope.pass().templateManager();
        int buildings = 0;
        for (var piece : original.getPieces())
            if (value(node(piece, scope.pass()), "role") == VillageLayoutRepair.Role.BUILDING) buildings++;
        var subset = new java.util.ArrayList<StructurePiece>();
        int kept = 0;
        for (var piece : original.getPieces()) {
            if (value(node(piece, scope.pass()), "role") != VillageLayoutRepair.Role.BUILDING || kept++ < buildings / 2)
                subset.add(piece);
        }
        var trimmed = new StructureStart(original.getStructure(), original.getChunkPos(), 0,
                new net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer(subset));
        var local = within(scope, () -> VillageLayoutRepair.retain(subset, trimmed,
                scope.pass().islandField(), scope.pass().settings()));
        var shared = within(scope, () -> VillageLayoutRepair.assembly(original, templates,
                () -> VillageLayoutRepair.retain(subset, trimmed, scope.pass().islandField(), scope.pass().settings())));
        if (buildings < 2 || local.isEmpty() || !shared.isEmpty())
            throw new AssertionError("Original-assembly retention control did not discriminate repeated trimming");
        // The assembly context must have been released for the next independent evaluation.
        var restored = within(scope, () -> VillageLayoutRepair.retain(subset, trimmed,
                scope.pass().islandField(), scope.pass().settings()));
        if (restored.isEmpty()) throw new AssertionError("Original-assembly context leaked into the next evaluation");
        var noisy = new java.util.ArrayList<>(subset);
        for (int i = 0; i < 3; i++) for (var piece : original.getPieces())
            if (value(node(piece, scope.pass()), "role") != VillageLayoutRepair.Role.BUILDING) noisy.add(piece);
        var inflated = new StructureStart(original.getStructure(), original.getChunkPos(), 0,
                new net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer(noisy));
        double quality = VillageLayoutRepair.quality(original, trimmed, templates);
        if (quality > .5 || quality != VillageLayoutRepair.quality(original, inflated, templates))
            throw new AssertionError("Decoration/road pieces inflated inhabited-building retention quality");
        var result = new JsonObject();
        result.addProperty("originalBuildings", buildings);
        result.addProperty("independentlyAcceptedPieces", local.size());
        result.addProperty("wholeAssemblyRejectedPieces", shared.size());
        result.addProperty("contextReleased", true);
        result.addProperty("inhabitedQuality", quality);
        result.addProperty("nonBuildingPiecesDoNotInflateQuality", true);
        return result;
    }

    private static Object value(Object record, String name) throws Exception {
        var getter = record.getClass().getDeclaredMethod(name);
        getter.setAccessible(true);
        return getter.invoke(record);
    }

    @SuppressWarnings("unchecked")
    private static List<FoundationSupport.Foot> feet(Object node) throws Exception {
        return (List<FoundationSupport.Foot>)value(node, "feet");
    }

    private static int ground(StructurePlacementPassContext pass, FoundationSupport.Foot foot) {
        var cp = new net.minecraft.world.level.ChunkPos(Math.floorDiv(foot.x(), 16), Math.floorDiv(foot.z(), 16));
        return pass.islandField().sampleChunkTerrainSnapshot(cp, pass.settings()).segmentsAt(foot.x(), foot.z()).stream()
                .filter(span -> span.bottomY() < foot.y()).mapToInt(span -> Math.min(span.topY(), foot.y() - 1))
                .max().orElse(FoundationSupport.MISSING);
    }

    @SuppressWarnings("unchecked")
    private static List<StructurePiece> within(StructurePlacementScope scope, Supplier<List<StructurePiece>> action) throws Exception {
        var method = StructurePlacementScope.class.getDeclaredMethod("with", StructurePlacementScope.class, Supplier.class);
        method.setAccessible(true);
        return (List<StructurePiece>)method.invoke(null, scope, action);
    }
}
