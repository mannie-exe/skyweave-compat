package design.inherent.skyweavecompat.rendering;

import design.inherent.skyweavecompat.client.GlassBackfaces;
import design.inherent.skyweavecompat.client.GlassMesh;
import design.inherent.skyweavecompat.client.GlassSurfaceContacts;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GlassMeshTest {
    private static BakedQuad face(Direction direction, float plane, float u0, float v0, float u1, float v1) {
        int axis = direction.getAxis().ordinal(), u = (axis + 1) % 3, v = (axis + 2) % 3;
        int[] packed = new int[32]; float[][] coords = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
        for (int i = 0; i < 4; i++) {
            int at = i * 8; packed[at + axis] = Float.floatToIntBits(plane);
            packed[at + u] = Float.floatToIntBits(coords[i][0]); packed[at + v] = Float.floatToIntBits(coords[i][1]);
            packed[at + 3] = 0xffb08040; packed[at + 4] = Float.floatToIntBits(coords[i][0]); packed[at + 5] = Float.floatToIntBits(coords[i][1]);
            packed[at + 6] = 0x00f00070; packed[at + 7] = (direction.getAxisDirection().getStep() * 127 & 255) << axis * 8;
        }
        return new BakedQuad(packed, 3, direction, null, false, false);
    }
    private static GlassMesh.Face owned(BakedQuad quad, String part) {
        return new GlassMesh.Face(quad, null, new GlassMesh.Owner("fixture", part, null, null), true);
    }
    @Test void internalDifferentPartsKeepOriginalFacesAndSeparateBothAddedReverseSheetsOnEveryAxis() {
        for (var direction : Direction.values()) {
            var a = face(direction, .5f, 0, 0, 1, 1);
            var b = face(direction.getOpposite(), .5f, 0, 0, 1, 1);
            var input = List.of(owned(a, "a"), owned(b, "b"));
            var out = GlassBackfaces.appendMesh(input, null);
            assertEquals(4, out.size()); assertSame(a, out.get(0).quad()); assertSame(b, out.get(1).quad());
            int axis = direction.getAxis().ordinal();
            float x = Float.intBitsToFloat(out.get(2).quad().getVertices()[axis]);
            float y = Float.intBitsToFloat(out.get(3).quad().getVertices()[axis]);
            assertEquals(.5f - direction.getAxisDirection().getStep() / 256f, x);
            assertEquals(.5f + direction.getAxisDirection().getStep() / 256f, y);
            assertEquals("a", out.get(2).owner().part()); assertEquals("b", out.get(3).owner().part());
            assertSame(out, GlassBackfaces.appendMesh(out, null));
        }
    }
    @Test void equalGeometryWithDifferentPackedAttributesIsNotPositionOnlyDeduplicated() {
        var a = face(Direction.SOUTH, .5f, 0, 0, 1, 1);
        var vertices = ReverseVertices.copy(a.getVertices()); vertices[3] ^= 0xff;
        var b = new BakedQuad(vertices, 3, Direction.NORTH, null, false, false);
        var out = GlassBackfaces.appendMesh(List.of(owned(a, "same"), owned(b, "same")), null);
        assertEquals(4, out.size()); assertSame(a, out.get(0).quad()); assertSame(b, out.get(1).quad());
    }
    @Test void diagonalContactKeepsTheUncoveredTriangleAtItsOriginalPlaneAndInterpolatesUvs() {
        var a = face(Direction.SOUTH, .5f, 0, 0, 1, 1);
        var contact = PlanarSurface.of(List.of(new PlanarSurface.Point(0, 0, .5), new PlanarSurface.Point(1, 0, .5),
                new PlanarSurface.Point(0, 1, .5), new PlanarSurface.Point(0, 1, .5)), 2, -1);
        var sides = java.util.stream.IntStream.range(0, 6).mapToObj(i -> List.<SurfaceRegions.Rect>of()).toList();
        var contacts = new GlassSurfaceContacts(sides, List.of(new GlassSurfaceContacts.Contact(contact, owned(a, "neighbor").owner())));
        var out = GlassBackfaces.appendMesh(List.of(owned(a, "a")), contacts);
        double shared = 0, exposed = 0;
        for (var entry : out.subList(1, out.size())) {
            var q = entry.quad(); var poly = GlassSurfaceContacts.polygon(q); assertNotNull(poly);
            double area = Math.abs(PlanarSurface.area(poly.points(), 2));
            float z = Float.intBitsToFloat(q.getVertices()[2]);
            if (z < .5f) shared += area; else exposed += area;
            for (int i = 0; i < 4; i++) {
                assertEquals(Float.intBitsToFloat(q.getVertices()[i * 8]), Float.intBitsToFloat(q.getVertices()[i * 8 + 4]), 1e-6);
                assertEquals(Float.intBitsToFloat(q.getVertices()[i * 8 + 1]), Float.intBitsToFloat(q.getVertices()[i * 8 + 5]), 1e-6);
                assertEquals(0xffb08040, q.getVertices()[i * 8 + 3]); assertEquals(0x00f00070, q.getVertices()[i * 8 + 6]);
            }
        }
        assertEquals(.5, shared, 1e-6); assertEquals(.5, exposed, 1e-6);
    }
    @Test void explicitlyUnsupportedAndSourcePhaseMeshesRemainOriginal() {
        var face = owned(face(Direction.EAST, .5f, 0, 0, 1, 1), "part");
        var unsupported = List.of(new GlassMesh.Face(face.quad(), null, face.owner(), false));
        assertSame(unsupported, GlassBackfaces.appendMesh(unsupported, null));
        var supported = List.of(face);
        try (var scope = GeometryScope.source()) { assertSame(supported, GlassBackfaces.appendMesh(supported, null)); }
        assertEquals(2, GlassBackfaces.appendMesh(supported, null).size());
        var excessive = java.util.Collections.nCopies(1025, face.quad());
        assertSame(excessive, GlassBackfaces.append(excessive));
    }

    @Test void modelDataCannotReuseContactsFromAnotherOrUnpublishedResourceGeneration() {
        var snapshot = new design.inherent.skyweavecompat.client.TransparencyProfiles.Snapshot(3, java.util.Map.of(), java.util.Map.of());
        var sides = java.util.stream.IntStream.range(0, 6).mapToObj(i -> List.<SurfaceRegions.Rect>of()).toList();
        var contacts = new GlassSurfaceContacts(sides, List.of(), 3);
        var data = net.neoforged.neoforge.client.model.data.ModelData.builder().with(GlassSurfaceContacts.PROPERTY, contacts).build();
        long previous = design.inherent.skyweavecompat.client.TransparencyProfiles.publishedGeneration();
        try {
            design.inherent.skyweavecompat.client.TransparencyProfiles.publish(snapshot);
            assertSame(contacts, GlassSurfaceContacts.from(data, snapshot));
            assertNull(GlassSurfaceContacts.from(data, new design.inherent.skyweavecompat.client.TransparencyProfiles.Snapshot(2, java.util.Map.of(), java.util.Map.of())));
            design.inherent.skyweavecompat.client.TransparencyProfiles.beginPublication();
            assertNull(GlassSurfaceContacts.from(data, snapshot));
        } finally {
            design.inherent.skyweavecompat.client.TransparencyProfiles.publish(
                    new design.inherent.skyweavecompat.client.TransparencyProfiles.Snapshot(previous, java.util.Map.of(), java.util.Map.of()));
        }
        assertThrows(UnsupportedOperationException.class, () -> snapshot.rules().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.diagnostics().clear());
    }
}
