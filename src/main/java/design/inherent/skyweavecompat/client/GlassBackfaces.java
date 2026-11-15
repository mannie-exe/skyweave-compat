package design.inherent.skyweavecompat.client;

import design.inherent.skyweavecompat.rendering.ReverseVertices;
import design.inherent.skyweavecompat.rendering.PlanarSurface;
import design.inherent.skyweavecompat.rendering.SurfaceRegions;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/** Copies final model output, retaining the caller's native neighbor-cull group. */
public final class GlassBackfaces {
    private GlassBackfaces() {}

    public static boolean vanillaPane(ResourceLocation id) {
        return design.inherent.skyweavecompat.rendering.TransparencyProfile.Family.VANILLA_PANES
                .matches(id.getNamespace(), id.getPath(), ignored -> false);
    }
    public static boolean fusionTarget(BlockState state) {
        return TransparencyProfiles.current().supports(design.inherent.skyweavecompat.rendering.TransparencyProfile.Provider.FUSION, state);
    }

    public static List<BakedQuad> append(List<BakedQuad> quads) {
        return append(quads, null);
    }

    public static List<BakedQuad> append(List<BakedQuad> quads, GlassSurfaceContacts contacts) {
        if (GlassSurfaceContacts.sampling() || quads.isEmpty()) return quads;
        if (quads.size() > 1024) { TransparencyDiagnostics.fallback(TransparencyDiagnostics.Reason.FACE_BUDGET); return quads; }
        if (contacts == null) {
            ArrayList<BakedQuad> result = null;
            for (var quad : quads) {
                if (quad instanceof ReverseQuad || quad.getVertices().length != 32) continue;
                boolean paired = false;
                for (var other : quads) if (reversePair(quad, other)) { paired = true; break; }
                if (paired) continue;
                if (result == null) result = new ArrayList<>(quads);
                result.add(new ReverseQuad(quad, ReverseVertices.copy(quad.getVertices())));
            }
            return result == null ? quads : List.copyOf(result);
        }
        var owner = new GlassMesh.Owner("static", "", null, null);
        var faces = quads.stream().map(q -> new GlassMesh.Face(q, null, owner, true)).toList();
        var result = appendMesh(faces, contacts);
        return result == faces ? quads : result.stream().map(GlassMesh.Face::quad).toList();
    }

    public static List<GlassMesh.Face> appendMesh(List<GlassMesh.Face> faces, GlassSurfaceContacts contacts) {
        if (GlassSurfaceContacts.sampling() || faces.isEmpty()) return faces;
        if (faces.size() > 1024) { TransparencyDiagnostics.fallback(TransparencyDiagnostics.Reason.FACE_BUDGET); return faces; }
        var polygons = faces.stream().map(f -> GlassSurfaceContacts.polygon(f.quad())).toList();
        var adjacent = contacts == null ? List.<PlanarSurface>of()
                : contacts.faces().stream().map(GlassSurfaceContacts.Contact::surface).toList();
        ArrayList<GlassMesh.Face> result = null;
        for (int index = 0; index < faces.size(); index++) {
            var face = faces.get(index); var quad = face.quad();
            if (!face.supported() || quad instanceof ReverseQuad || hasReverse(faces, face)) continue;
            if (quad.getVertices().length != 32) continue;
            if (result == null) result = new ArrayList<>(faces);
            var polygon = polygons.get(index);
            if (polygon == null) {
                TransparencyDiagnostics.fallback(TransparencyDiagnostics.Reason.NONPLANAR_FACE);
                add(result, face, ReverseVertices.copy(quad.getVertices())); continue;
            }
            var overlaps = new ArrayList<PlanarSurface>();
            for (var other : adjacent) if (polygon.oppositePlane(other)) overlaps.add(other);
            for (int other = 0; other < polygons.size(); other++)
                if (other != index && polygons.get(other) != null && polygon.oppositePlane(polygons.get(other))) overlaps.add(polygons.get(other));
            var surface = GlassSurfaceContacts.surface(quad);
            var rectangles = new ArrayList<SurfaceRegions.Rect>();
            boolean rectangular = surface != null;
            if (rectangular) for (var other : overlaps) {
                var rect = rectangle(other, surface.u(), surface.v());
                if (rect == null) { rectangular = false; break; }
                rectangles.add(rect);
            }
            if (surface != null && contacts != null && contacts.faces().isEmpty()) {
                float boundary = quad.getDirection().getAxisDirection().getStep() > 0 ? 1 : 0;
                if (Math.abs(surface.plane() - boundary) <= GlassSurfaceContacts.POSITION_TOLERANCE)
                    rectangles.addAll(contacts.side(quad.getDirection()));
            }
            float depth = CONTACT_DEPTH;
            for (int other = 0; other < polygons.size(); other++) {
                var opposite = polygons.get(other);
                if (other == index || opposite == null || !face.owner().equals(faces.get(other).owner())
                        || polygon.normal().dot(opposite.normal()) > -1 + 1e-7) continue;
                double thickness = polygon.distance() + opposite.distance();
                if (thickness > PlanarSurface.TOLERANCE) depth = Math.min(depth, (float) thickness / 4);
            }
            if (depth < 4 * PlanarSurface.TOLERANCE) {
                TransparencyDiagnostics.fallback(TransparencyDiagnostics.Reason.THIN_SURFACE); depth = 0;
            }
            if (rectangular) {
                var parts = SurfaceRegions.partitionChecked(surface.bounds(), rectangles);
                if (parts == null) {
                    TransparencyDiagnostics.fallback(TransparencyDiagnostics.Reason.PARTITION_BUDGET);
                    add(result, face, ReverseVertices.copy(quad.getVertices()));
                } else for (var part : parts) add(result, face, reversePart(quad, surface, part, depth));
            } else {
                var parts = polygon.partition(overlaps);
                if (parts == null) {
                    TransparencyDiagnostics.fallback(TransparencyDiagnostics.Reason.PARTITION_BUDGET);
                    add(result, face, ReverseVertices.copy(quad.getVertices()));
                } else for (var part : parts) reversePolygon(result, face, polygon, part, depth);
            }
            if (result.size() > 4096) {
                TransparencyDiagnostics.fallback(TransparencyDiagnostics.Reason.FACE_BUDGET); return faces;
            }
        }
        return result == null ? faces : List.copyOf(result);
    }

    private static void add(List<GlassMesh.Face> out, GlassMesh.Face original, int[] vertices) {
        out.add(new GlassMesh.Face(new ReverseQuad(original.quad(), vertices), original.cullFace(), original.owner(), true));
    }

    private static SurfaceRegions.Rect rectangle(PlanarSurface polygon, int u, int v) {
        if (polygon.points().size() != 4) return null;
        double a = Double.POSITIVE_INFINITY, b = a, c = Double.NEGATIVE_INFINITY, d = c;
        for (var p : polygon.points()) { a = Math.min(a, p.coordinate(u)); b = Math.min(b, p.coordinate(v)); c = Math.max(c, p.coordinate(u)); d = Math.max(d, p.coordinate(v)); }
        if (c <= a || d <= b) return null;
        int corners = 0;
        for (var p : polygon.points()) {
            if (Math.min(Math.abs(p.coordinate(u) - a), Math.abs(p.coordinate(u) - c)) > PlanarSurface.TOLERANCE
                    || Math.min(Math.abs(p.coordinate(v) - b), Math.abs(p.coordinate(v) - d)) > PlanarSurface.TOLERANCE) return null;
            int corner = (Math.abs(p.coordinate(u) - a) < PlanarSurface.TOLERANCE ? 0 : 1)
                    | (Math.abs(p.coordinate(v) - b) < PlanarSurface.TOLERANCE ? 0 : 2);
            if ((corners & 1 << corner) != 0) return null;
            corners |= 1 << corner;
        }
        return new SurfaceRegions.Rect((float) a, (float) b, (float) c, (float) d);
    }

    private static boolean hasReverse(List<GlassMesh.Face> faces, GlassMesh.Face source) {
        for (var face : faces)
            if (face.owner().equals(source.owner()) && reversePair(source.quad(), face.quad())) return true;
        return false;
    }
    private static boolean reversePair(BakedQuad quad, BakedQuad other) {
        if (other instanceof ReverseQuad reverse && reverse.source == quad) return true;
        if (other.getDirection() != quad.getDirection().getOpposite() || other.getSprite() != quad.getSprite()
                || other.getTintIndex() != quad.getTintIndex() || other.isShade() != quad.isShade()
                || other.hasAmbientOcclusion() != quad.hasAmbientOcclusion()) return false;
        return ReverseVertices.matches(quad.getVertices(), other.getVertices());
    }

    private static void reversePolygon(List<GlassMesh.Face> out, GlassMesh.Face face, PlanarSurface source,
            PlanarSurface.Part part, float depth) {
        var points = part.points();
        if (!part.shared() && points.equals(source.points())) {
            add(out, face, ReverseVertices.copy(face.quad().getVertices())); return;
        }
        if (points.size() == 4) add(out, face, polygonVertices(face.quad(), source, points, part.shared() ? depth : 0));
        else for (int i = 1; i + 1 < points.size(); i++)
            add(out, face, polygonVertices(face.quad(), source, List.of(points.getFirst(), points.get(i), points.get(i + 1), points.get(i + 1)), part.shared() ? depth : 0));
    }

    private static int[] polygonVertices(BakedQuad quad, PlanarSurface source, List<PlanarSurface.Point> points, float depth) {
        int[] original = quad.getVertices(), result = new int[32];
        for (int i = 0; i < 4; i++) {
            var position = points.get(i); double[] weights = source.weights(position), packedWeights = new double[4];
            // A triangle can repeat any source vertex, not only the last BLOCK vertex.
            for (int unique = 0; unique < source.points().size(); unique++) for (int raw = 0; raw < 4; raw++) {
                var p = source.points().get(unique);
                if (Math.abs(Float.intBitsToFloat(original[raw * 8]) - p.x()) < 1e-7
                        && Math.abs(Float.intBitsToFloat(original[raw * 8 + 1]) - p.y()) < 1e-7
                        && Math.abs(Float.intBitsToFloat(original[raw * 8 + 2]) - p.z()) < 1e-7) {
                    packedWeights[raw] += weights[unique]; break;
                }
            }
            position = position.minus(source.normal().times(depth));
            for (int axis = 0; axis < 3; axis++) result[i * 8 + axis] = Float.floatToIntBits((float) position.coordinate(axis));
            interpolate(original, result, i * 8, packedWeights);
        }
        return ReverseVertices.copy(result);
    }

    private static void interpolate(int[] original, int[] result, int offset, double[] weights) {
        for (int attribute = 3; attribute < 8; attribute++) {
            if (attribute == 4 || attribute == 5) {
                double value = 0;
                for (int corner = 0; corner < 4; corner++) value += weights[corner] * Float.intBitsToFloat(original[corner * 8 + attribute]);
                result[offset + attribute] = Float.floatToIntBits((float) value);
            } else {
                int value = 0, bits = attribute == 6 ? 16 : 8, mask = (1 << bits) - 1;
                for (int channel = 0; channel < 32; channel += bits) {
                    double component = 0;
                    for (int corner = 0; corner < 4; corner++) {
                        int raw = original[corner * 8 + attribute] >>> channel;
                        component += weights[corner] * (attribute == 7 && channel < 24 ? (byte) raw : raw & mask);
                    }
                    value |= ((int) Math.round(component) & mask) << channel;
                }
                result[offset + attribute] = value;
            }
        }
    }

    // Contact-only render skin: one sixteenth of a 16px texel, 128 Sodium position steps.
    // Only the shared reverse area moves; original and exposed faces retain their positions.
    private static final float CONTACT_DEPTH = 1f / 256;

    private static int[] reversePart(BakedQuad quad, GlassSurfaceContacts.Surface surface, SurfaceRegions.Part part, float depth) {
        int[] original = quad.getVertices();
        int[] result = original.clone();
        var bounds = surface.bounds(); var clipped = part.bounds();
        float shift = part.shared() ? -quad.getDirection().getAxisDirection().getStep() * depth : 0;
        if (clipped.equals(bounds)) {
            if (shift != 0) for (int vertex = 0; vertex < 4; vertex++) {
                int offset = vertex * 8 + surface.axis();
                result[offset] = Float.floatToIntBits(Float.intBitsToFloat(original[offset]) + shift);
            }
            return ReverseVertices.copy(result);
        }
        double[] weights = new double[4];
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * 8;
            float oldU = Float.intBitsToFloat(original[offset + surface.u()]);
            float oldV = Float.intBitsToFloat(original[offset + surface.v()]);
            float u = Math.abs(oldU - bounds.u0()) <= GlassSurfaceContacts.POSITION_TOLERANCE ? clipped.u0() : clipped.u1();
            float v = Math.abs(oldV - bounds.v0()) <= GlassSurfaceContacts.POSITION_TOLERANCE ? clipped.v0() : clipped.v1();
            result[offset + surface.u()] = Float.floatToIntBits(u);
            result[offset + surface.v()] = Float.floatToIntBits(v);
            result[offset + surface.axis()] = Float.floatToIntBits(surface.plane() + shift);
            if (!clipped.equals(bounds)) {
                float tu = (u - bounds.u0()) / (bounds.u1() - bounds.u0());
                float tv = (v - bounds.v0()) / (bounds.v1() - bounds.v0());
                for (int corner = 0; corner < 4; corner++) weights[corner] = weight(original, corner, surface, tu, tv);
                interpolate(original, result, offset, weights);
            }
        }
        return ReverseVertices.copy(result);
    }

    private static double weight(int[] vertices, int corner, GlassSurfaceContacts.Surface surface, float u, float v) {
        float a = Float.intBitsToFloat(vertices[corner * 8 + surface.u()]);
        float b = Float.intBitsToFloat(vertices[corner * 8 + surface.v()]);
        return (Math.abs(a - surface.bounds().u0()) <= GlassSurfaceContacts.POSITION_TOLERANCE ? 1 - u : u)
                * (Math.abs(b - surface.bounds().v0()) <= GlassSurfaceContacts.POSITION_TOLERANCE ? 1 - v : v);
    }

    private static final class ReverseQuad extends BakedQuad {
        private final BakedQuad source;
        ReverseQuad(BakedQuad source, int[] vertices) {
            super(vertices, source.getTintIndex(),
                    source.getDirection().getOpposite(), source.getSprite(), source.isShade(),
                    source.hasAmbientOcclusion());
            this.source = source;
        }
    }
}
