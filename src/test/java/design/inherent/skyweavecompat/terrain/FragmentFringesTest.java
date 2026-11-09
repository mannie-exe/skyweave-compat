package design.inherent.skyweavecompat.terrain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FragmentFringesTest {
    @Test void mixedPerimetersRetainSmoothSectorsAndDisintegrationExtendsTheFringe() {
        var p = ShapeProfile.INITIAL.fringe();
        int mixed = 0, disintegrating = 0;
        for (long seed = 0; seed < 10000; seed++) {
            double fraction = FragmentFringes.granularFraction(seed, p);
            if (FragmentFringes.disintegrating(seed, p)) {
                disintegrating++;
                assertTrue(fraction > .6 && fraction < .84);
                assertEquals(p.falloffDistance() * p.disintegratingScale(), FragmentFringes.width(seed, 300, p));
            } else {
                mixed++;
                assertTrue(fraction >= .3 && fraction <= .7);
                assertEquals(p.falloffDistance(), FragmentFringes.width(seed, 300, p));
            }
            assertTrue(FragmentFringes.width(seed, 30, p) <= 24);
        }
        assertTrue(mixed > 8000 && disintegrating > 1200 && disintegrating < 1800);
    }

    @Test void fragmentsAreBoundedRepeatableAndNeverReplaceTheMainBody() {
        var p = ShapeProfile.INITIAL.fringe();
        int fragments = 0, deepest = 0;
        for (long seed = -12; seed <= 12; seed++) for (int x = -64; x <= 64; x += 4) for (int z = -64; z <= 64; z += 4) {
            double angle = Math.atan2(z, x);
            assertNull(FragmentFringes.sample(seed, x, z, boundary(angle, -1), 300, 350, p));
            assertNull(FragmentFringes.sample(seed, x, z, boundary(angle, 1000), 300, 350, p));
            var fragment = FragmentFringes.sample(seed, x, z, boundary(angle, 12), 300, 350, p);
            assertEquals(fragment, FragmentFringes.sample(seed, x, z, boundary(angle, 12), 300, 350, p));
            if (fragment == null) continue;
            fragments++;
            assertTrue(fragment.depth() >= 8 && fragment.depth() <= Math.ceil(350 * p.fragmentDepthRatio() * 1.2));
            deepest = Math.max(deepest, fragment.depth());
        }
        assertTrue(fragments > 300);
        assertTrue(deepest > 120, "Outer remnants need their own span, not the exhausted eight-block rim");
    }
    @Test void localReachIsPeriodicUnevenAndInsidePlacementBounds() {
        var settings = ShapeProfile.INITIAL.fringe();
        for (long seed = 0; seed < 32; seed++) {
            double min = Double.POSITIVE_INFINITY, max = 0;
            for (int i = 0; i < 360; i++) {
                double angle = i * Math.PI / 180;
                double width = FragmentFringes.localWidth(seed, 300, angle, settings);
                assertTrue(width >= 0 && width <= FragmentFringes.width(seed, 300, settings));
                min = Math.min(min, width); max = Math.max(max, width);
            }
            assertTrue(max > min * 1.5, "A nominal falloff must not produce a uniform fringe");
            assertEquals(FragmentFringes.localWidth(seed, 300, 0, settings),
                    FragmentFringes.localWidth(seed, 300, Math.PI * 2, settings), 1e-9);
        }
    }

    @Test void outwardFragmentsShrinkAndTheirGapsIncrease() {
        var initial = ShapeProfile.INITIAL.fringe();
        // Full coverage isolates fragment spacing from the angular sector mask.
        var settings = new ShapeProfile.Fringe(true, 0, 0, initial.falloffDistance(), 1,
                initial.disintegratingScale(), initial.fragmentScale(), initial.fragmentDepthRatio(),
                initial.density(), initial.falloffExponent(), initial.undersideAccent());
        int[] covered = new int[2], runs = new int[2];
        for (long seed = 0; seed < 24; seed++) for (int band = 0; band < 2; band++) {
            double distance = FragmentFringes.localWidth(seed, 300, 0, settings) * (band == 0 ? .25 : .7);
            boolean previous = false;
            for (double tangent = -1000; tangent <= 1000; tangent += .5) {
                boolean present = FragmentFringes.sample(seed, 300 + distance, tangent,
                        boundary(0, distance), 300, 350, settings) != null;
                if (present) { covered[band]++; if (!previous) runs[band]++; }
                previous = present;
            }
        }
        assertTrue(covered[0] > covered[1] && runs[0] > runs[1] && runs[1] > 0,
                "Outward fragments must thin, not repeat at the inner-band density");
        assertTrue(covered[0] / (double)runs[0] > covered[1] / (double)runs[1],
                "Outer cross-sections must be smaller on average");
    }

    private static FragmentFringes.Boundary boundary(double angle, double distance) {
        return new FragmentFringes.Boundary(angle, distance, Math.cos(angle), Math.sin(angle));
    }

}
