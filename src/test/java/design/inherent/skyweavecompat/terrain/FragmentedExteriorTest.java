package design.inherent.skyweavecompat.terrain;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FragmentedExteriorTest {
    @Test void remnantsHaveCoherentHeightsOpenGapsAndFiniteShellBounds() {
        var profile = ShapeProfile.INITIAL.fractures();
        int open = 0, solid = 0, deep = 0;
        var observed = new LinkedHashMap<String, FragmentedExterior.Sample>();
        for (int x = -300; x <= 300; x += 3) for (int z = -300; z <= 300; z += 3) {
            var value = FragmentedExterior.sample(42, x, z, profile);
            observed.put(x + "," + z, value);
            assertTrue(Double.isFinite(value.conservativeClearance()));
            assertTrue(value.conservativeClearance() >= 0);
            assertTrue(value.heightChoice() >= 0 && value.heightChoice() < 1);
            if (!value.exists()) { open++; continue; }
            solid++;
            if (value.conservativeClearance() > 12) {
                deep++;
                for (int dx : new int[]{-8, 8}) for (int dz : new int[]{-8, 8}) {
                    var neighbor = FragmentedExterior.sample(42, x + dx, z + dz, profile);
                    assertTrue(neighbor.exists(), "Conservative shell crossed a missing remnant");
                    assertEquals(value.heightChoice(), neighbor.heightChoice());
                }
            }
        }
        assertTrue(open > 1000 && solid > 1000 && deep > 100);
        for (var entry : new ArrayList<>(observed.entrySet()).reversed()) {
            var coordinates = entry.getKey().split(",");
            assertEquals(entry.getValue(), FragmentedExterior.sample(42,
                    Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1]), profile));
        }
    }
}
