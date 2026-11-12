package design.inherent.skyweavecompat.terrain;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GenerationProfileTest {
    @Test void permitsDisplacedCandidatesButBoundsTheirSearchCost() {
        var json = new Gson().toJsonTree(GenerationProfile.INITIAL).getAsJsonObject();
        json.addProperty("spacing", 2000);
        var profile = GenerationConfiguration.parse(json.toString());
        assertTrue(profile.spacing() - 2 * profile.jitter() - 2 * profile.clusterReach() < profile.minimumClusterGap());
        assertTrue(profile.conflictCells() <= 8);
        json.addProperty("minimumClusterGap", 1000000);
        assertThrows(RuntimeException.class, () -> GenerationConfiguration.parse(json.toString()));
        json.addProperty("minimumClusterGap", Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> GenerationConfiguration.parse(json.toString()));
    }

    @Test void intrinsicParsingDoesNotUseTheRunningShapeProfile() {
        var json = new Gson().toJsonTree(GenerationProfile.INITIAL).getAsJsonObject();
        json.addProperty("spacing", 400);
        json.addProperty("jitter", 0);
        // This fits disabled outline/fringe shaping but not the running default shapes.
        // Parsing must permit a proposed pair without installing either draft globally.
        var profile = assertDoesNotThrow(() -> design.inherent.skyweavecompat.configuration.ProfileJson.generation(json.toString()));
        assertEquals(400, profile.spacing());
        var initial = ShapeProfile.INITIAL;
        var disabled = new ShapeProfile(false, initial.strengthVariation(), initial.outline(), initial.fringe(), initial.fractures(), initial.deepDark());
        assertSame(profile, profile.validateAgainst(disabled));
        assertThrows(IllegalArgumentException.class, () -> profile.validateAgainst(initial));
        assertThrows(IllegalArgumentException.class, () -> GenerationConfiguration.parse(json.toString(), initial));
        assertEquals(profile, GenerationConfiguration.parse(json.toString(), disabled));
    }

    @Test void rejectsUnknownMissingFractionalAndImpossibleControls() {
        var json = new Gson().toJsonTree(GenerationProfile.INITIAL).getAsJsonObject();
        var unknown = json.deepCopy(); unknown.addProperty("typo", 42);
        assertThrows(RuntimeException.class, () -> GenerationConfiguration.parse(unknown.toString()));
        var missing = json.deepCopy(); missing.remove("activation");
        assertThrows(RuntimeException.class, () -> GenerationConfiguration.parse(missing.toString()));
        var fractional = json.deepCopy(); fractional.addProperty("centerMinY", 200.5);
        assertThrows(RuntimeException.class, () -> GenerationConfiguration.parse(fractional.toString()));
        var impossible = json.deepCopy(); impossible.addProperty("buildingHeadroom", 800);
        assertThrows(RuntimeException.class, () -> GenerationConfiguration.parse(impossible.toString()));
    }

    @Test void safetyMarginsNarrowPlacementWithoutShrinkingBodies() {
        var json = new Gson().toJsonTree(GenerationProfile.INITIAL).getAsJsonObject();
        json.addProperty("centerMinY", 100);
        json.addProperty("centerMaxY", 700);
        var profile = GenerationConfiguration.parse(json.toString());
        assertTrue(profile.safeMinimumY() - GenerationProfile.depthBelowCenter(
                profile.cliffDepth(profile.anchorMaxHeight()), profile.anchorPlateau(), 48)
                >= GenerationProfile.MIN_Y + profile.floorClearance());
        assertTrue(profile.safeMaximumY() + profile.anchorPlateau() + 48 <= GenerationProfile.MAX_Y - 1 - profile.buildingHeadroom());
        assertTrue(profile.safeMaximumY() > profile.safeMinimumY());
        assertTrue(profile.safeMinimumY(profile.anchorMinHeight()) < profile.safeMinimumY(profile.anchorMaxHeight()));
        for (int height = profile.anchorMinHeight(); height <= profile.anchorMaxHeight(); height++) {
            assertTrue(profile.safeMinimumY(height) - GenerationProfile.depthBelowCenter(profile.cliffDepth(height), profile.anchorPlateau(), 48)
                    >= GenerationProfile.MIN_Y + profile.floorClearance());
        }
    }

    @Test void nativeShapeParameterTargetsTheNominalBodyInsteadOfOldTaperArithmetic() {
        var profile = GenerationProfile.INITIAL;
        for (int height = 250; height <= 400; height++) {
            double nominal = 1.18 * (profile.anchorPlateau() + .97 * profile.cliffDepth(height)) + 3.2;
            assertEquals(height, nominal, .58);
        }
        assertTrue(profile.safeMinimumY(250) <= 200);
        assertTrue(profile.safeMinimumY(400) < profile.safeMaximumY());
    }
}
