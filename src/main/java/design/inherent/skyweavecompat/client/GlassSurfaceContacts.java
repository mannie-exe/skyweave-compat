package design.inherent.skyweavecompat.client;

import design.inherent.skyweavecompat.rendering.SurfaceRegions.Rect;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import design.inherent.skyweavecompat.rendering.GeometryScope;
import design.inherent.skyweavecompat.rendering.PlanarSurface;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/** Immutable final local-space original faces; never retains a mesh view, entity or level. */
public record GlassSurfaceContacts(List<List<Rect>> sides, List<Contact> faces, long generation) {
    public static final ModelProperty<GlassSurfaceContacts> PROPERTY = new ModelProperty<>();
    static final float POSITION_TOLERANCE = 1f / 32768;
    public record Contact(PlanarSurface surface, GlassMesh.Owner owner) {}
    public GlassSurfaceContacts(List<List<Rect>> sides) { this(sides, List.of(), 0); }
    public GlassSurfaceContacts(List<List<Rect>> sides, List<Contact> faces) { this(sides, faces, 0); }
    public GlassSurfaceContacts {
        if (sides.size() != 6) throw new IllegalArgumentException("Expected six contact groups");
        sides = sides.stream().map(List::copyOf).toList(); faces = List.copyOf(faces);
        if (faces.size() > 2048 || sides.stream().mapToLong(List::size).sum() > 2048 || generation < 0)
            throw new IllegalArgumentException("Contact budget/generation invalid");
    }
    public List<Rect> side(Direction direction) { return sides.get(direction.ordinal()); }
    public static boolean sampling() { return GeometryScope.sampling(); }
    public static GlassSurfaceContacts from(ModelData data, TransparencyProfiles.Snapshot profiles) {
        var value = data.get(PROPERTY);
        return value != null && value.generation == profiles.generation()
                && value.generation == TransparencyProfiles.publishedGeneration() ? value : null;
    }

    public static GlassSurfaceContacts capture(BlockAndTintGetter view, BlockPos pos) {
        return capture(view, pos, TransparencyProfiles.current());
    }
    public static GlassSurfaceContacts capture(BlockAndTintGetter view, BlockPos pos, TransparencyProfiles.Snapshot profiles) {
        if (sampling()) return null;
        if (profiles.generation() != TransparencyProfiles.publishedGeneration()) return null;
        try (var scope = GeometryScope.source()) {
            var sides = new ArrayList<List<Rect>>(6);
            var contacts = new ArrayList<Contact>();
            for (Direction side : Direction.values()) {
                var regions = new ArrayList<Rect>();
                BlockPos neighborPos = pos.relative(side);
                BlockState neighbor = view.getBlockState(neighborPos);
                if (!neighbor.isAir() && neighbor.getRenderShape() == net.minecraft.world.level.block.RenderShape.MODEL) {
                    var model = Minecraft.getInstance().getBlockRenderer().getBlockModel(neighbor);
                    if (model instanceof GlassMesh.Generation generation && generation.skyweave$resourceGeneration() != profiles.generation()) return null;
                    if (neighbor.hasBlockEntity() && !(model instanceof PlainGlassModels) && !(model instanceof GlassMesh.Provider)) {
                        sides.add(List.of()); continue;
                    }
                    if (!(model instanceof GlassMesh.Generation)
                            && !(model instanceof net.minecraft.client.resources.model.SimpleBakedModel)
                            && !(model instanceof net.minecraft.client.resources.model.WeightedBakedModel)
                            && !(model instanceof net.minecraft.client.resources.model.MultiPartBakedModel)) {
                        TransparencyDiagnostics.fallback(TransparencyDiagnostics.Reason.UNSUPPORTED_PROVIDER);
                        sides.add(List.of()); continue;
                    }
                    var incoming = view.getModelData(neighborPos);
                    if (neighbor.hasBlockEntity() && model instanceof GlassMesh.Provider && incoming == ModelData.EMPTY) {
                        TransparencyDiagnostics.fallback(TransparencyDiagnostics.Reason.MODEL_DATA_UNAVAILABLE);
                        sides.add(List.of()); continue;
                    }
                    var data = model.getModelData(view, neighborPos, neighbor, incoming);
                    long seed = neighbor.getSeed(neighborPos);
                    var random = RandomSource.create(seed);
                    for (var layer : model.getRenderTypes(neighbor, random, data)) {
                        List<GlassMesh.Face> output;
                        if (model instanceof GlassMesh.Provider provider) {
                            random.setSeed(seed); output = provider.skyweave$originalFaces(neighbor, seed, data, layer);
                        } else {
                            var staticFaces = new ArrayList<GlassMesh.Face>();
                            for (int group = 0; group < 2; group++) {
                                Direction cull = group == 0 ? null : side.getOpposite();
                                if (cull != null && !Block.shouldRenderFace(neighbor, view, neighborPos, cull, neighborPos.relative(cull))) continue;
                                random.setSeed(seed);
                                for (var quad : model.getQuads(neighbor, cull, random, data, layer))
                                    staticFaces.add(new GlassMesh.Face(quad, cull,
                                            new GlassMesh.Owner("static", "", neighbor, layer), false));
                                if (staticFaces.size() > 1024) break;
                            }
                            output = staticFaces;
                        }
                        if (output.size() > 1024 || contacts.size() + output.size() > 2048) {
                            TransparencyDiagnostics.fallback(TransparencyDiagnostics.Reason.FACE_BUDGET); return null;
                        }
                        for (var face : output) {
                            if (face.cullFace() != null && !Block.shouldRenderFace(neighbor, view, neighborPos,
                                    face.cullFace(), neighborPos.relative(face.cullFace()))) continue;
                            var polygon = polygon(face.quad());
                            if (polygon == null) continue;
                            contacts.add(new Contact(polygon.translated(new PlanarSurface.Point(side.getStepX(), side.getStepY(), side.getStepZ())), face.owner()));
                            if (face.quad().getDirection() != side.getOpposite()) continue;
                            var surface = surface(face.quad());
                            float boundary = side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0 : 1;
                            if (surface != null && Math.abs(surface.plane() - boundary) <= POSITION_TOLERANCE
                                    && !regions.contains(surface.bounds())) regions.add(surface.bounds());
                        }
                    }
                }
                sides.add(List.copyOf(regions));
            }
            return contacts.isEmpty() || profiles.generation() != TransparencyProfiles.publishedGeneration()
                    ? null : new GlassSurfaceContacts(sides, contacts, profiles.generation());
        }
    }

    public static PlanarSurface polygon(BakedQuad quad) {
        int[] packed = quad.getVertices();
        if (packed.length != 32) return null;
        var points = new ArrayList<PlanarSurface.Point>(4);
        for (int i = 0; i < 4; i++) {
            double x = Float.intBitsToFloat(packed[i * 8]), y = Float.intBitsToFloat(packed[i * 8 + 1]), z = Float.intBitsToFloat(packed[i * 8 + 2]);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return null;
            points.add(new PlanarSurface.Point(x, y, z));
        }
        return PlanarSurface.of(points, quad.getDirection().getAxis().ordinal(), quad.getDirection().getAxisDirection().getStep());
    }

    public record Surface(int axis, int u, int v, float plane, Rect bounds) {}

    static Surface surface(BakedQuad quad) {
        int[] vertices = quad.getVertices();
        if (vertices.length != 32) return null;
        int axis = quad.getDirection().getAxis().ordinal();
        int u = (axis + 1) % 3, v = (axis + 2) % 3;
        float plane = Float.intBitsToFloat(vertices[axis]);
        if (!Float.isFinite(plane)) return null;
        float u0 = Float.POSITIVE_INFINITY, v0 = u0, u1 = Float.NEGATIVE_INFINITY, v1 = u1;
        for (int i = 0; i < 4; i++) {
            float coordinate = Float.intBitsToFloat(vertices[i * 8 + axis]);
            if (!Float.isFinite(coordinate) || Math.abs(coordinate - plane) > POSITION_TOLERANCE) return null;
            float a = Float.intBitsToFloat(vertices[i * 8 + u]), b = Float.intBitsToFloat(vertices[i * 8 + v]);
            if (!Float.isFinite(a) || !Float.isFinite(b)) return null;
            u0 = Math.min(u0, a); u1 = Math.max(u1, a); v0 = Math.min(v0, b); v1 = Math.max(v1, b);
        }
        if (u0 >= u1 || v0 >= v1) return null;
        int corners = 0;
        for (int i = 0; i < 4; i++) {
            float a = Float.intBitsToFloat(vertices[i * 8 + u]), b = Float.intBitsToFloat(vertices[i * 8 + v]);
            if (Math.min(Math.abs(a - u0), Math.abs(a - u1)) > POSITION_TOLERANCE
                    || Math.min(Math.abs(b - v0), Math.abs(b - v1)) > POSITION_TOLERANCE) return null;
            int corner = (Math.abs(a - u0) <= POSITION_TOLERANCE ? 0 : 1)
                    | (Math.abs(b - v0) <= POSITION_TOLERANCE ? 0 : 2);
            if ((corners & 1 << corner) != 0) return null;
            corners |= 1 << corner;
        }
        return new Surface(axis, u, v, plane, new Rect(u0, v0, u1, v1));
    }
}
