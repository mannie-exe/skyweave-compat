package design.inherent.skyweavecompat.placement;

import java.util.Set;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static design.inherent.skyweavecompat.placement.NeighborRule.*;

final class NeighborRuleTest {
    @Test void closePolicyRejectsDistantHostsAndKeepsExactOverlapChecks() {
        var close = new Settings(true, 64, 128, 96);
        assertEquals(Decision.ACCEPTED, evaluate(POINT, close, columns(96, new Point(128, 0))));
        assertEquals(Decision.NO_HOST, evaluate(POINT, close, columns(96, new Point(144, 0))));
        assertEquals(Decision.TOO_CLOSE, evaluate(POINT, close, columns(96, new Point(128, 0), new Point(7, 9))));
    }

    @Test void invalidHostSentinelCannotQualifyWhenMinimumRadiusIsZero() {
        assertEquals(Decision.NO_HOST, evaluate(POINT, new Settings(true, 64, 128, 0),
                columns(-1, new Point(128, 0))));
    }
    private static final Settings DEFAULTS = new Settings(true, 64, 512, 96);
    private static final Footprint POINT = new Footprint(0, 0, 0, 0);

    private record Point(int x, int z) {}
    private static Terrain columns(int radius, Point... points) {
        Set<Point> set = Set.of(points);
        return new Terrain() {
            public boolean exists(int x, int z) { return set.contains(new Point(x, z)); }
            public int islandRadius(int x, int z) { return radius; }
        };
    }

    @Test void emptyVoidIsRejected() {
        assertEquals(Decision.NO_HOST, evaluate(POINT, DEFAULTS, columns(500)));
    }
    @Test void disabledDoesNotQueryTerrain() {
        assertEquals(Decision.DISABLED, evaluate(POINT, new Settings(false, 64, 512, 96), null));
    }
    @Test void gapBoundaries() {
        assertEquals(Decision.TOO_CLOSE, evaluate(POINT, DEFAULTS, columns(96, new Point(63, 0), new Point(64, 0))));
        assertEquals(Decision.ACCEPTED, evaluate(POINT, DEFAULTS, columns(96, new Point(64, 0))));
        assertEquals(Decision.ACCEPTED, evaluate(POINT, DEFAULTS, columns(96, new Point(512, 0))));
        assertEquals(Decision.NO_HOST, evaluate(POINT, DEFAULTS, columns(96, new Point(513, 0))));
    }
    @Test void narrowUnalignedOverlapCannotSlipThroughCoarsePass() {
        assertEquals(Decision.TOO_CLOSE, evaluate(new Footprint(0, 0, 100, 100), DEFAULTS,
                columns(100, new Point(2, 3), new Point(-128, 0))));
    }
    @Test void footprintEdgeRatherThanCenterDeterminesClearance() {
        assertEquals(Decision.TOO_CLOSE, evaluate(new Footprint(-100, -100, 100, 100), DEFAULTS,
                columns(100, new Point(101, 0), new Point(-228, -100))));
    }
    @Test void evenZeroGapRejectsOverlappingColumns() {
        assertEquals(Decision.TOO_CLOSE, evaluate(POINT, new Settings(true, 0, 512, 96), columns(96, new Point(0, 0))));
    }
    @Test void fragmentsCannotQualifyAsHosts() {
        assertEquals(Decision.NO_HOST, evaluate(POINT, DEFAULTS, columns(95, new Point(128, 0))));
        assertEquals(Decision.ACCEPTED, evaluate(POINT, DEFAULTS, columns(96, new Point(128, 0))));
    }
    @Test void fragmentsStillBlockClearance() {
        Terrain terrain = new Terrain() {
            public boolean exists(int x, int z) { return (x == 7 && z == 9) || (x == 128 && z == 0); }
            public int islandRadius(int x, int z) { return x == 128 ? 100 : 10; }
        };
        assertEquals(Decision.TOO_CLOSE, evaluate(POINT, DEFAULTS, terrain));
    }
    @Test void roundDistanceDoesNotAcceptSquareCorners() {
        assertEquals(Decision.NO_HOST, evaluate(POINT, DEFAULTS, columns(100, new Point(512, 512))));
        assertEquals(2L * 512 * 512, POINT.distanceSquared(512, 512));
    }
    @Test void negativeAndWorldBorderCoordinates() {
        for (int origin : new int[]{-29_999_000, -1024, 29_999_000}) {
            assertEquals(Decision.ACCEPTED, evaluate(new Footprint(origin, origin, origin, origin),
                    DEFAULTS, columns(100, new Point(origin + 128, origin))));
        }
    }
    @Test void configurationRejectsInvertedRange() {
        assertThrows(IllegalArgumentException.class, () -> new Settings(true, 513, 512, 96));
        assertThrows(IllegalArgumentException.class, () -> new Settings(true, -1, 512, 96));
        assertThrows(IllegalArgumentException.class, () -> new Settings(true, 64, 2049, 96));
        assertThrows(IllegalArgumentException.class, () -> new Footprint(1, 0, 0, 0));
    }
    @Test void concurrentEvaluationsHaveNoSharedOrderState() throws Exception {
        try (var executor = Executors.newFixedThreadPool(4)) {
            var calls = java.util.stream.IntStream.range(0, 24).mapToObj(i ->
                    (java.util.concurrent.Callable<Decision>)() -> evaluate(POINT, DEFAULTS,
                            columns(100, new Point(128, 0)))).toList();
            for (var result : executor.invokeAll(calls)) assertEquals(Decision.ACCEPTED, result.get());
        }
    }
}
