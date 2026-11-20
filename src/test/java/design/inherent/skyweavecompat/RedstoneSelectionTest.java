package design.inherent.skyweavecompat;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RedstoneSelectionTest {
    private FeatureSettings only(Feature... enabled) {
        var values = new EnumMap<Feature, Boolean>(Feature.class);
        for (var feature : Feature.values()) values.put(feature, false);
        for (var feature : enabled) values.put(feature, true);
        return new FeatureSettings(values);
    }
    @Test void guiNeedsBothTargetsAndKeepsShipIntegrationOptional() {
        var settings = only(Feature.LINK_GUI_INTERACTIONS, Feature.LINK_GUI_RELOCATION, Feature.LINK_GUI_SABLE);
        var versions = new java.util.HashMap<>(Map.of("create", "6.0.10", "createredstonelinkgui", "1.21.1-1.9.2"));
        for (boolean client : new boolean[]{false, true}) {
            var result = FeatureSelection.select(settings, versions, client);
            assertTrue(result.enabled(Feature.LINK_GUI_INTERACTIONS));
            assertTrue(result.enabled(Feature.LINK_GUI_RELOCATION));
            assertFalse(result.enabled(Feature.LINK_GUI_SABLE));
            assertEquals(client, result.mixins(client).contains("client.GuiRelocationScreenMixin"));
        }
        versions.put("sable", "2.0.6"); assertTrue(FeatureSelection.select(settings, versions, false).enabled(Feature.LINK_GUI_SABLE));
        versions.remove("create"); assertTrue(FeatureSelection.select(settings, versions, false).active().isEmpty());
        versions.put("create", "6.0.10"); versions.remove("createredstonelinkgui");
        assertTrue(FeatureSelection.select(settings, versions, true).active().isEmpty());
    }
    @Test void disabledGuiDoesNotConstrainUnreviewedTargets() {
        var future = Map.of("create", "future", "createredstonelinkgui", "future", "sable", "future");
        assertTrue(FeatureSelection.select(only(), future, true).active().isEmpty());
        assertThrows(IllegalStateException.class, () -> FeatureSelection.select(only(Feature.LINK_GUI_INTERACTIONS), future, true));
        var withoutAccess = FeatureSelection.select(only(Feature.LINK_GUI_SABLE), future, true);
        assertTrue(withoutAccess.active().isEmpty());
    }
    @Test void crossroadReadsRequireTheQualifiedAdditionsAndCreateOnEitherSide() {
        var settings = only(Feature.REDSTONE_LOGIC);
        var versions = Map.of("create", "6.0.10", "create_redstone_additions", "1.0.6");
        for (boolean client : new boolean[]{false, true}) {
            var selection = FeatureSelection.select(settings, versions, client);
            assertEquals(java.util.Set.of(Feature.REDSTONE_LOGIC), selection.active());
            assertEquals(java.util.List.of("redstone.CrossroadMixin"), selection.mixins(client));
        }
        for (String missing : versions.keySet()) {
            var absent = new java.util.HashMap<>(versions);
            absent.remove(missing);
            assertTrue(FeatureSelection.select(settings, absent, true).active().isEmpty());
        }
        var future = Map.of("create", "6.0.10", "create_redstone_additions", "999.0.0");
        assertTrue(FeatureSelection.select(only(), future, true).active().isEmpty());
        assertThrows(IllegalStateException.class, () -> FeatureSelection.select(settings, future, false));
    }
}
