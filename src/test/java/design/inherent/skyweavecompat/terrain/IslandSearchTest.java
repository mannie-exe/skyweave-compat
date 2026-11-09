package design.inherent.skyweavecompat.terrain;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IslandSearchTest {
    @Test void nearbyCellsComeFirstWithoutDiscardingTheHalo() {
        var search = new IslandSearch<Integer>(-1200, 1200, 4800, 2400, 1, Comparator.naturalOrder());
        assertEquals(new ClusterLayout.Cell(-1, 0), search.next());
        var visited = new HashSet<ClusterLayout.Cell>();
        visited.add(new ClusterLayout.Cell(-1, 0));
        long last = 0;
        while (!search.complete()) {
            var cell = search.next();
            long x = cell.x() + 1L, z = cell.z();
            long distance = x * x + z * z;
            assertTrue(distance >= last); last = distance;
            assertTrue(visited.add(cell));
        }
        assertEquals(49, visited.size());
        assertTrue(visited.contains(new ClusterLayout.Cell(-4, -3)));
        assertTrue(visited.contains(new ClusterLayout.Cell(2, 3)));
    }
    @Test void traversalResumesWithoutOmittingOrRepeatingNegativeCells() {
        var search = new IslandSearch<Integer>(-2400, -1, 100, 2400, 1, Comparator.naturalOrder());
        var visited = new LinkedHashSet<ClusterLayout.Cell>();
        while (!search.complete()) assertTrue(visited.add(search.next()));
        assertEquals(search.total(), visited.size());
        assertTrue(visited.contains(new ClusterLayout.Cell(-3, -2)));
        assertTrue(visited.contains(new ClusterLayout.Cell(0, 1)));
        assertThrows(NoSuchElementException.class, search::next);
        assertEquals(new ArrayList<>(visited), traverse(-2400, -1));
    }
    private List<ClusterLayout.Cell> traverse(int x, int z) {
        var cursor = new IslandSearch<Integer>(x, z, 100, 2400, 1, Comparator.naturalOrder());
        var result = new ArrayList<ClusterLayout.Cell>();
        while (!cursor.complete()) result.add(cursor.next());
        return result;
    }
    @Test void nearestRetentionIsBoundedAndIndependentOfArrivalOrder() {
        var forward = new IslandSearch<Integer>(0, 0, 10, 2400, 0, Comparator.naturalOrder());
        var reverse = new IslandSearch<Integer>(0, 0, 10, 2400, 0, Comparator.naturalOrder());
        var values = java.util.stream.IntStream.range(0, 200).boxed().toList();
        forward.accept(values, value -> value % 2 == 0);
        reverse.accept(values.reversed(), value -> value % 2 == 0);
        assertEquals(100, forward.matches()); assertEquals(64, forward.results().size());
        assertEquals(126, forward.results().getLast()); assertEquals(forward.results(), reverse.results());
        assertThrows(UnsupportedOperationException.class, () -> forward.results().clear());
    }
    @Test void invalidAndExcessiveSearchesFailBeforeAllocatingAnArea() {
        assertThrows(IllegalArgumentException.class, () -> new IslandSearch<Integer>(0, 0, 131073, 2400, 1, Comparator.naturalOrder()));
        assertThrows(IllegalArgumentException.class, () -> new IslandSearch<Integer>(0, 0, 131072, 1, 0, Comparator.naturalOrder()));
        var edge = new IslandSearch<Integer>(Integer.MIN_VALUE, Integer.MAX_VALUE, 1, 2400, 0, Comparator.naturalOrder());
        assertTrue(edge.total() <= 4);
    }
    @Test void filtersRejectContradictionsAndRetainCombinedCriteria() {
        assertEquals(Map.of("family", "anchor_plateau", "archetype", "lagoon", "shoreline", "perimeter"),
                IslandLocator.filters("archetype=lagoon shoreline=perimeter"));
        assertEquals(Map.of("exterior", "fractured"), IslandLocator.filters("family=any exterior=fractured"));
        assertEquals(Map.of("family", "anchor_plateau", "archetype", "crescent", "fringe", "disintegrating"),
                IslandLocator.filters("archetype=crescent fringe=disintegrating"));
        assertEquals("mixed", IslandLocator.filters("archetype=lagoon fringe=mixed").get("fringe"));
        assertThrows(IllegalArgumentException.class, () -> IslandLocator.filters("edge=coastal"));
        assertThrows(IllegalArgumentException.class, () -> IslandLocator.filters("apron=mixed"));
        assertEquals(Map.of("family", "anchor_plateau", "archetype", "lagoon", "shoreline", "coastal", "fringe", "disintegrating"),
                IslandLocator.filters("archetype=lagoon shoreline=coastal fringe=disintegrating"));
        assertThrows(IllegalArgumentException.class, () -> IslandLocator.filters("fringe=coastal"));
        assertThrows(IllegalArgumentException.class, () -> IslandLocator.filters("archetype=classic shoreline=coastal"));
        assertThrows(IllegalArgumentException.class, () -> IslandLocator.filters("interior=hollow interior=noise"));
        assertThrows(IllegalArgumentException.class, () -> IslandLocator.filters("unknown=value"));
    }

    @Test void descriptorPrecheckPreservesBoundaryAndDefersStyleDependentEvidence() {
        var deferred = Map.of("biome", "minecraft:deep_dark", "exterior", "deep_dark", "fringe", "mixed");
        assertTrue(IslandLocator.quickMatch(deferred, 100, 100, "satellite", "classic"));
        assertFalse(IslandLocator.quickMatch(deferred, 101, 100, "satellite", "classic"));
        var anchors = IslandLocator.filters("archetype=lagoon");
        assertTrue(IslandLocator.quickMatch(anchors, 100, 100, "anchor_plateau", "lagoon"));
        assertFalse(IslandLocator.quickMatch(anchors, 0, 100, "satellite", "lagoon"));
        assertFalse(IslandLocator.quickMatch(anchors, 0, 100, "anchor_plateau", "classic"));
        assertTrue(IslandLocator.quickMatch(IslandLocator.filters("family=any"), 0, 100, "spire", "classic"));
    }
}
