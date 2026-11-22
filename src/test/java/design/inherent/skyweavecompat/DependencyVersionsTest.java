package design.inherent.skyweavecompat;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DependencyVersionsTest {
    @Test void baseApiFamiliesAdmitStablePatchesAndRejectOtherGamesFamiliesAndPrereleases() {
        var installed = new HashMap<String, String>();
        DependencyVersions.base().forEach((id, contract) -> installed.put(id, contract.developedAgainst()));
        assertDoesNotThrow(() -> DependencyVersions.requireBase(installed));
        installed.put("reform", "0.2.0");
        assertDoesNotThrow(() -> DependencyVersions.requireBase(installed));
        installed.put("reform", "0.2.7");
        installed.put("neoforge", "21.1.999");
        installed.put("javafml", "4.0.99");
        assertDoesNotThrow(() -> DependencyVersions.requireBase(installed));
        for (var entry : Map.of(
                "reform", List.of("0.1.99", "0.3.0", "0.3.0-rc.1", "0.2.7-SNAPSHOT", "0.2.7+dev", "0.2.0-dev.unselected",
                        DependencyVersions.base().get("reform").developedAgainst() + ".unselected"),
                "neoforge", List.of("21.1.264", "21.2.0", "21.2.0-beta.1", "22.1.1"),
                "javafml", List.of("3.0.99", "5.0.0", "5.0.0-rc.1"),
                "minecraft", List.of("1.21", "1.21.2", "26.3")).entrySet()) {
            for (var unsupported : entry.getValue()) {
                var invalid = new HashMap<>(installed);
                invalid.put(entry.getKey(), unsupported);
                var failure = assertThrows(IllegalStateException.class, () -> DependencyVersions.requireBase(invalid));
                assertTrue(failure.getMessage().contains(entry.getKey()));
                assertTrue(failure.getMessage().contains("installed " + unsupported));
                assertTrue(failure.getMessage().contains("developed against " + DependencyVersions.base().get(entry.getKey()).developedAgainst()));
            }
            var missing = new HashMap<>(installed);
            missing.remove(entry.getKey());
            assertThrows(IllegalStateException.class, () -> DependencyVersions.requireBase(missing));
        }
    }

    @Test void architecturyApiFamilyIsBoundedAndMalformedPrivateMetadataIsAnExactValue() {
        var contract = DependencyVersions.contract("architectury", "13.0.12");
        assertTrue(contract.accepts("13.0.12"));
        assertTrue(contract.accepts("13.1.0"));
        for (var unsupported : List.of("13.", "13.0.11", "14.0.0", "14.0.0-beta.1", "13.0.99-SNAPSHOT"))
            assertFalse(contract.accepts(unsupported), unsupported);
        var confectionery = DependencyVersions.contract("create_confectionery", "1.1.3.");
        assertTrue(confectionery.accepts("1.1.3."));
        assertFalse(confectionery.accepts("1.1.3.future"));
    }

    @Test void reviewedAliasesDoNotPretendToBeTheDevelopmentBaseline() {
        var questlog = DependencyVersions.contract("questlog", FeatureSelection.dependencies(Feature.QUESTLOG_INTERFACE).get("questlog"));
        assertEquals("3.5.4", questlog.developedAgainst());
        for (var version : List.of("3.5.0", "3.5.3", "3.5.4")) assertTrue(questlog.accepts(version));
        for (var version : List.of("3.5.1", "3.5.5", "3.6.0")) assertFalse(questlog.accepts(version));
        assertTrue(DependencyVersions.contract("surveyor", "1.2.4_1.21").accepts("1.2.4+1.21"));
        assertFalse(DependencyVersions.contract("surveyor", "1.2.4_1.21").accepts("1.2.5+1.21"));
    }

    @Test void everyDhAndIrisPrivateAdapterRefusesUnreviewedUpdatesBeforeSelectingHooks() {
        for (var feature : Feature.values()) {
            var baseline = FeatureSelection.dependencies(feature);
            for (var owner : List.of("distanthorizons", "iris")) {
                if (!baseline.containsKey(owner)) continue;
                var enabled = settings(feature);
                var versions = new HashMap<String, String>();
                for (var active : enabled.values().entrySet())
                    if (active.getValue()) versions.putAll(FeatureSelection.dependencies(active.getKey()));
                assertTrue(FeatureSelection.select(enabled, versions, true).enabled(feature), feature.key);
                var unsupported = owner.equals("iris") ? "1.8.14+mc1.21.1" : "3.3.4";
                versions.put(owner, unsupported);
                var failure = assertThrows(IllegalStateException.class, () -> FeatureSelection.select(enabled, versions, true), feature.key);
                assertTrue(failure.getMessage().contains("developed against " + baseline.get(owner)));
                assertTrue(failure.getMessage().contains("installed " + unsupported));
                assertTrue(failure.getMessage().contains(feature.key));
                assertTrue(FeatureSelection.select(settings(), versions, true).mixins(true).isEmpty());
                if (FeatureSelection.clientOnly(feature))
                    assertFalse(FeatureSelection.select(enabled, versions, false).enabled(feature));
            }
        }
    }

    @Test void diagnosticSnapshotIsImmutableAndKeepsActualInstalledSpellings() {
        var mods = new HashMap<>(Map.of("questlog", "3.5.3"));
        var selected = FeatureSelection.select(settings(Feature.QUESTLOG_INTERFACE), mods, true);
        mods.put("questlog", "future");
        assertEquals("3.5.3", selected.installed().get("questlog"));
        assertThrows(UnsupportedOperationException.class, () -> selected.installed().clear());
        var diagnostic = selected.describe(Feature.QUESTLOG_INTERFACE);
        assertTrue(diagnostic.contains("developed against 3.5.4"));
        assertTrue(diagnostic.contains("installed 3.5.3"));
        assertTrue(diagnostic.contains("accepts 3.5.0, 3.5.3, 3.5.4"));
    }

    private static FeatureSettings settings(Feature... features) {
        var values = new EnumMap<Feature, Boolean>(Feature.class);
        for (var feature : Feature.values()) values.put(feature, false);
        for (var feature : features)
            for (var active = feature; active != null; active = FeatureSelection.prerequisite(active)) values.put(active, true);
        return new FeatureSettings(values);
    }
}
