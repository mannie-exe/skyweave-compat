package design.inherent.skyweavecompat.terrain;

import org.junit.jupiter.api.Test;
import org.sathrek.sky_archipelago.worldgen.generator.field.*;
import org.sathrek.sky_archipelago.worldgen.generator.field.model.*;
import static org.junit.jupiter.api.Assertions.*;

class OceanIslandsTest {
    private static final long SEED = 17;
    private IslandDescriptor island(IslandShapeArchetype archetype) {
        return island(archetype, SEED);
    }
    private IslandDescriptor island(IslandShapeArchetype archetype, long seed) {
        return new IslandDescriptor(IslandFamily.ANCHOR_PLATEAU, 0, 0, 0, 64, 64, 64, 0,
                archetype, 28, 100, 48, 0, 0, 24, 24, .4, seed);
    }
    private WaterArchetypeColumnProfile shore() {
        return new WaterArchetypeColumnProfile(WaterArchetypeColumnProfile.Region.SHORE,
                WaterArchetypeColumnProfile.SurfaceRole.SANDY,
                WaterArchetypeColumnProfile.BiomeProjection.BEACH_SHORE, 40, 34, 36, 0, true);
    }
    private HorizontalSample point(double x, double z) { return new HorizontalSample(x, z, .5, .5, 1, true); }

    @Test void coastalOpeningIsDirectionalAndPreservesNativeFluidAndRockProvenance() {
        var profile = new OceanProfile(true, 1, 1, 0, 0, 1, 1, 0, 100, 20, 2, 4, true, 16);
        double angle = OceanProfile.unit(SEED ^ 0x179d5b396a1cb874L) * Math.PI * 2;
        var source = shore();
        var changed = OceanIslands.adapt(island(IslandShapeArchetype.LAGOON),
                point(50 * Math.cos(angle), 50 * Math.sin(angle)), source, profile);
        assertTrue(changed.water());
        assertEquals(34, changed.fluidSurfaceY());
        assertTrue(changed.configuredOceanFluid());
        assertEquals(0, changed.underbellyBottomY());
        assertTrue(changed.containsProtectedSealAt(changed.solidSurfaceY()));
        assertSame(source, OceanIslands.adapt(island(IslandShapeArchetype.LAGOON),
                point(-50 * Math.cos(angle), -50 * Math.sin(angle)), source, profile));
    }

    @Test void perimeterReusesLagoonFootprintAndOtherArchetypesStayUnchanged() {
        var profile = new OceanProfile(true, 1, 1, 0, 0, 1, 0, 1, 100, 20, 2, 4, true, 16);
        var disabled = new OceanProfile(false, 1, 1, 0, 0, 1, 0, 1, 100, 20, 2, 4, true, 16);
        var source = shore();
        assertTrue(OceanIslands.adapt(island(IslandShapeArchetype.LAGOON), point(-60, 20), source, profile).water());
        assertSame(source, OceanIslands.adapt(island(IslandShapeArchetype.LAGOON), point(0, 0), source, disabled));
        assertSame(WaterArchetypeColumnProfile.OUTSIDE, OceanIslands.adapt(island(IslandShapeArchetype.LAGOON), point(1000, 0),
                WaterArchetypeColumnProfile.OUTSIDE, profile));
        for (var type : new IslandShapeArchetype[]{IslandShapeArchetype.REEF_RING, IslandShapeArchetype.DEAD_OCEAN_SHELF,
                IslandShapeArchetype.BROKEN_OCEAN_REEF, IslandShapeArchetype.CLASSIC}) {
            assertSame(source, OceanIslands.adapt(island(type), point(-60, 20), source, profile));
        }
    }

    @Test void coveHasAWideMouthAndGradedDryShouldersWithoutRemovingBasinIslets() {
        var profile = new OceanProfile(true, 1, 1, 0, 0, 1, 1, 0, 100, 20, 2, 4, true, 16);
        var island = island(IslandShapeArchetype.LAGOON);
        double direction = OceanProfile.unit(SEED ^ 0x179d5b396a1cb874L) * Math.PI * 2;
        for (int degrees : new int[]{-30, 0, 30}) {
            double angle = direction + Math.toRadians(degrees);
            assertTrue(OceanIslands.adapt(island, point(60 * Math.cos(angle), 60 * Math.sin(angle)), shore(), profile).water());
        }
        double angle = direction + Math.toRadians(45);
        var shoulder = OceanIslands.adapt(island, point(60 * Math.cos(angle), 60 * Math.sin(angle)), shore(), profile);
        assertTrue(shoulder.shore());
        assertTrue(shoulder.solidSurfaceY() > shoulder.fluidSurfaceY());
        assertEquals(WaterArchetypeColumnProfile.BiomeProjection.BEACH_SHORE, shoulder.biomeProjection());
        var islet = new WaterArchetypeColumnProfile(WaterArchetypeColumnProfile.Region.INTERIOR_LAND,
                WaterArchetypeColumnProfile.SurfaceRole.VEGETATED_LAND,
                WaterArchetypeColumnProfile.BiomeProjection.VEGETATED_LAND, 36, 34, 32, 0, true);
        assertSame(islet, OceanIslands.adapt(island, point(30 * Math.cos(direction), 30 * Math.sin(direction)), islet, profile));
    }

    @Test void wetAnchorsUseTheSameSizeAndAspectAuthorityAsLandAnchors() {
        var settings = GenerationProfile.INITIAL;
        var cluster = new ClusterDescriptor(0, 0, 0, 450, 0, null, null, null, 0,
                IslandShapeArchetype.LAGOON, 0, 0, 17);
        var lagoon = IslandGeometry.island(island(IslandShapeArchetype.LAGOON, SEED), cluster, settings);
        var classic = IslandGeometry.island(island(IslandShapeArchetype.CLASSIC, SEED), cluster, settings);
        assertEquals(classic.radiusX(), lagoon.radiusX());
        assertEquals(classic.radiusZ(), lagoon.radiusZ());
        assertTrue(lagoon.maxRadius() >= settings.anchorMinRadius());
    }

    @Test void admissionAndLagoonModesUseIndependentStableDraws() {
        var profile = OceanProfile.INITIAL;
        int admitted = 0, coastal = 0, perimeter = 0;
        for (long seed = 0; seed < 40000; seed++) if (profile.admits(seed)) {
            admitted++;
            if (profile.mode(seed) == OceanProfile.Mode.COASTAL) coastal++;
            if (profile.mode(seed) == OceanProfile.Mode.PERIMETER) perimeter++;
        }
        assertEquals(.20, admitted / 40000.0, .01);
        assertEquals(.25, coastal / (double)admitted, .02);
        assertEquals(0, perimeter);
        var json = new com.google.gson.Gson().toJson(profile);
        assertEquals(profile, OceanConfiguration.parse(json));
        assertThrows(RuntimeException.class, () -> OceanConfiguration.parse(json.replace("\"admissionChance\":0.2", "\"admissionChance\":2")));
        assertThrows(RuntimeException.class, () -> OceanConfiguration.parse(json.replace("\"lagoonWeight\":1.0", "\"lagoonWeight\":-1")));
    }
}
