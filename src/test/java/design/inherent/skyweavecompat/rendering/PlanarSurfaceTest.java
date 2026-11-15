package design.inherent.skyweavecompat.rendering;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import design.inherent.skyweavecompat.rendering.PlanarSurface.Point;

class PlanarSurfaceTest {
    private static Point p(double x, double y, double z) { return new Point(x, y, z); }
    private static PlanarSurface rectangle() {
        return PlanarSurface.of(List.of(p(0, 0, .5), p(1, 0, .5), p(1, 1, .5), p(0, 1, .5)), 2, 1);
    }
    @Test void triangleContactLeavesItsActualComplementInsteadOfItsBoundingBox() {
        var source = rectangle();
        var triangle = PlanarSurface.of(List.of(p(0, 0, .5), p(1, 0, .5), p(0, 1, .5), p(0, 1, .5)), 2, -1);
        var parts = source.partition(List.of(triangle));
        assertEquals(1, parts.stream().filter(PlanarSurface.Part::shared).count());
        assertEquals(.5, parts.stream().filter(PlanarSurface.Part::shared)
                .mapToDouble(part -> Math.abs(PlanarSurface.area(part.points(), 2))).sum(), 1e-9);
        assertEquals(1, parts.stream().mapToDouble(part -> Math.abs(PlanarSurface.area(part.points(), 2))).sum(), 1e-9);
        assertEquals(1, source.partition(List.of(triangle.translated(p(0, 0, .1)))) .size());
        assertFalse(source.partition(List.of(triangle.translated(p(0, 0, .1)))).getFirst().shared());
    }
    @Test void slopedPlaneRetainsWorldCoordinatesAndAffineAttributeWeights() {
        var points = List.of(p(0, 0, 0), p(1, 0, 0), p(1, 1, 1), p(0, 1, 1));
        var slope = PlanarSurface.of(points, 1, 1);
        assertNotNull(slope);
        assertEquals(1, Math.sqrt(slope.normal().dot(slope.normal())), 1e-12);
        var weights = slope.weights(p(.3, .7, .7));
        Point reconstructed = p(0, 0, 0);
        for (int i = 0; i < 4; i++) reconstructed = reconstructed.plus(points.get(i).times(weights[i]));
        assertEquals(.3, reconstructed.x(), 1e-9); assertEquals(.7, reconstructed.y(), 1e-9);
        var other = PlanarSurface.of(points, 1, -1);
        assertTrue(slope.oppositePlane(other)); assertTrue(slope.partition(List.of(other)).getFirst().shared());
    }
    @Test void overlappingContactsDoNotDuplicateAreaAndBudgetsDoNotReturnPartialCoverage() {
        var source = rectangle();
        var contact = PlanarSurface.of(List.of(p(.2, .2, .5), p(.8, .2, .5), p(.8, .8, .5), p(.2, .8, .5)), 2, -1);
        var parts = source.partition(List.of(contact, contact));
        assertEquals(.36, parts.stream().filter(PlanarSurface.Part::shared)
                .mapToDouble(part -> Math.abs(PlanarSurface.area(part.points(), 2))).sum(), 1e-9);
        assertEquals(1, parts.stream().mapToDouble(part -> Math.abs(PlanarSurface.area(part.points(), 2))).sum(), 1e-9);
        assertNull(source.partition(java.util.Collections.nCopies(513, contact)));
        assertNull(PlanarSurface.of(List.of(p(0, 0, 0), p(1, 0, 0), p(1, 1, .1), p(0, 1, 0)), 2, 1));
        assertNull(PlanarSurface.of(List.of(p(0, 0, 0), p(1, 1, 0), p(1, 0, 0), p(0, 1, 0)), 2, 1));
    }
    @Test void nestedExceptionalScopesRestoreTheirExactParentAndOtherThreadsStayUnchanged() throws Exception {
        assertFalse(GeometryScope.sampling());
        try (var first = GeometryScope.source()) {
            assertThrows(IllegalStateException.class, () -> { try (var second = GeometryScope.source()) { throw new IllegalStateException("fixture"); } });
            assertTrue(GeometryScope.sampling());
            try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
                assertFalse(executor.submit(GeometryScope::sampling).get());
            }
        }
        assertFalse(GeometryScope.sampling());
    }
}
