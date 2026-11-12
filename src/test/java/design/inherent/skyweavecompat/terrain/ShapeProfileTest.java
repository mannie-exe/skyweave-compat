package design.inherent.skyweavecompat.terrain;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ShapeProfileTest {
    private final Gson json = new Gson();
    @Test void retiredPerimeterSchemaIsRejectedWithoutDefaultInsertion() {
        var old = json.toJsonTree(ShapeProfile.INITIAL).getAsJsonObject();
        old.remove("fringe"); old.add("edges", JsonParser.parseString("{\"chance\":0,\"affectedFraction\":0.625,\"bandWidth\":57,\"amplitude\":24,\"wavelength\":26}"));
        assertThrows(IllegalArgumentException.class, () -> ShapeConfiguration.parse(old.toString()));
    }
    @Test void strictNestedProfilesRejectCoercionMissingFieldsAndInvalidLinkedBounds() {
        var original = json.toJsonTree(ShapeProfile.INITIAL).getAsJsonObject();
        assertEquals(ShapeProfile.INITIAL, ShapeConfiguration.parse(original.toString()));
        var string = original.deepCopy(); string.getAsJsonObject("outline").addProperty("warpAmplitude", "18");
        assertThrows(RuntimeException.class, () -> ShapeConfiguration.parse(string.toString()));
        var missing = original.deepCopy(); missing.getAsJsonObject("fringe").remove("falloffDistance");
        assertThrows(RuntimeException.class, () -> ShapeConfiguration.parse(missing.toString()));
        var fractional = original.deepCopy(); fractional.getAsJsonObject("fractures").addProperty("cellSize", 72.5);
        assertThrows(RuntimeException.class, () -> ShapeConfiguration.parse(fractional.toString()));
        var linked = original.deepCopy(); linked.getAsJsonObject("fringe").addProperty("maximumSmoothFraction", .2);
        assertThrows(RuntimeException.class, () -> ShapeConfiguration.parse(linked.toString()));
        var duplicate = original.deepCopy(); duplicate.getAsJsonObject("fractures").add("biomes", json.toJsonTree(List.of("minecraft:badlands", "minecraft:badlands")));
        assertThrows(RuntimeException.class, () -> ShapeConfiguration.parse(duplicate.toString()));
        var invalid = original.deepCopy(); invalid.getAsJsonObject("fractures").add("biomes", json.toJsonTree(List.of("Badlands")));
        assertThrows(RuntimeException.class, () -> ShapeConfiguration.parse(invalid.toString()));
        var excessive = original.deepCopy(); excessive.addProperty("strengthVariation", .6);
        assertThrows(RuntimeException.class, () -> ShapeConfiguration.parse(excessive.toString()));
    }

    @Test void perIslandStrengthsStayBoundedCenteredAndIndependentlySeeded() {
        var p = ShapeProfile.INITIAL;
        var fixed = new ShapeProfile(true, 0, p.outline(), p.fringe(), p.fractures());
        double total = 0; int low = 0, high = 0, different = 0;
        for (long seed = -10000; seed < 10000; seed++) {
            double outline = IslandStyles.strengthScale(seed, 0x4fc139L, p);
            double edge = IslandStyles.strengthScale(seed, 0x3ab97fL, p);
            assertTrue(outline >= 1 - p.strengthVariation() && outline <= 1 + p.strengthVariation());
            assertTrue(edge >= 1 - p.strengthVariation() && edge <= 1 + p.strengthVariation());
            assertEquals(1, IslandStyles.strengthScale(seed, 0x4fc139L, fixed));
            if (outline < .9) low++;
            if (outline > 1.1) high++;
            if (Math.abs(outline - edge) > .01) different++;
            total += outline;
        }
        assertEquals(1, total / 20000, .004);
        assertTrue(low > 2000 && high > 2000 && different > 15000);
    }

    @Test void perimeterMaskHasNoAngularSeamAndSupportsSmoothAndDisturbedSections() {
        for (long seed = -20; seed < 20; seed++) {
            assertEquals(IslandStyles.perimeterMask(seed, -Math.PI, .45), IslandStyles.perimeterMask(seed, Math.PI, .45), 1e-12);
            int smooth = 0, rough = 0;
            for (int i = 0; i < 360; i++) {
                double mask = IslandStyles.perimeterMask(seed, i * Math.PI / 180, .45);
                assertTrue(mask >= 0 && mask <= 1);
                if (mask < .05) smooth++;
                if (mask > .95) rough++;
            }
            assertTrue(smooth > 60 && rough > 60, "Perimeter should retain both kinds of stretch");
            assertEquals(0, IslandStyles.perimeterMask(seed, 1, 0));
            assertEquals(1, IslandStyles.perimeterMask(seed, 1, 1));
        }
    }

    @Test void noiseIsBoundedContinuousAcrossNegativeLatticeEdgesAndRepeatable() {
        for (long seed = 0; seed < 100; seed++) {
            for (int x = -8; x <= 8; x++) {
                double a = IslandStyles.noise(seed, x - 1e-6, -3.2), b = IslandStyles.noise(seed, x + 1e-6, -3.2);
                assertEquals(a, b, 1e-8);
                assertTrue(a >= -1 && a <= 1);
                assertEquals(b, IslandStyles.noise(seed, x + 1e-6, -3.2));
            }
        }
    }

    @Test void disabledAndZeroAspectChoicesKeepTheNativeDimensions() {
        var p = ShapeProfile.INITIAL;
        var off = new ShapeProfile(false, p.strengthVariation(), p.outline(), p.fringe(), p.fractures());
        var zero = new ShapeProfile(true, p.strengthVariation(), new ShapeProfile.Outline(0, 2, 0, 128), p.fringe(), p.fractures());
        int elongated = 0;
        for (long seed = 0; seed < 20000; seed++) {
            assertEquals(1, IslandStyles.aspect(seed, off));
            assertEquals(1, IslandStyles.aspect(seed, zero));
            double aspect = IslandStyles.aspect(seed, p);
            assertTrue(aspect >= 1 && aspect <= p.outline().maximumAspectRatio());
            if (aspect > 1) elongated++;
        }
        assertEquals(.65, elongated / 20000.0, .015);
        assertEquals(1, off.maximumAxisScale());
        assertEquals(1, zero.maximumAxisScale());
    }

    @Test void styleCacheEvictionAndConcurrentReadsDoNotChangeEligibility() throws Exception {
        var p = ShapeProfile.INITIAL; var f = p.fractures();
        var profile = new ShapeProfile(true, p.strengthVariation(), p.outline(), p.fringe(), new ShapeProfile.Fractures(1,
                List.of("minecraft:mushroom_fields"), f.cellSize(), f.gapWidth(), f.verticalRange(), f.removalChance(), 0));
        var styles = new IslandStyles(profile, (x, y, z) -> new org.sathrek.sky_archipelago.worldgen.generator.field.model.BiomeSample(
                null, false, false, net.minecraft.resources.ResourceLocation.parse(x < 0 ? "minecraft:mushroom_fields" : "minecraft:plains")));
        var samples = java.util.stream.LongStream.range(-200, 200).mapToObj(seed ->
                new org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandDescriptor(
                        org.sathrek.sky_archipelago.worldgen.generator.field.model.IslandFamily.ANCHOR_PLATEAU,
                        (int)seed, 300, 0, 300, 300, 300, 0,
                        org.sathrek.sky_archipelago.worldgen.generator.field.IslandShapeArchetype.CLASSIC,
                        28, 250, 48, 0, 0, 50, 50, .4, seed)).toList();
        var expected = samples.stream().map(styles::style).toList();
        var field = IslandStyles.class.getDeclaredField("styles"); field.setAccessible(true);
        ((com.google.common.cache.Cache<?, ?>)field.get(styles)).invalidateAll();
        samples.parallelStream().forEach(island -> assertEquals(expected.get(samples.indexOf(island)), styles.style(island)));
        for (int i = 0; i < samples.size(); i++) assertEquals(samples.get(i).centerX() < 0 ? IslandStyles.Kind.FRACTURED
                : IslandStyles.Kind.NATIVE, expected.get(i).kind());
        var off = new IslandStyles(new ShapeProfile(false, p.strengthVariation(), p.outline(), p.fringe(), profile.fractures()),
                (x, y, z) -> new org.sathrek.sky_archipelago.worldgen.generator.field.model.BiomeSample(null, false));
        assertEquals(IslandStyles.Kind.NATIVE, off.style(samples.getFirst()).kind());
    }
}
