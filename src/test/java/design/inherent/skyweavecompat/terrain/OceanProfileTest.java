package design.inherent.skyweavecompat.terrain;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OceanProfileTest {
    @Test void enabledDefaultRetainsExplicitOptOutAndRejectsStringBooleans() {
        var json = JsonParser.parseString(new Gson().toJson(OceanProfile.INITIAL)).getAsJsonObject();
        assertTrue(OceanConfiguration.parse(json.toString()).enabled());
        json.addProperty("enabled", false);
        assertFalse(OceanConfiguration.parse(json.toString()).enabled());
        json.addProperty("enabled", "false");
        assertThrows(IllegalArgumentException.class, () -> OceanConfiguration.parse(json.toString()));
    }

    @Test void rejectOverlappingWeightsAndUnboundedGeometry() {
        var original = JsonParser.parseString(new Gson().toJson(OceanProfile.INITIAL)).getAsJsonObject();
        var weights = original.deepCopy(); weights.addProperty("perimeterChance", .8); weights.addProperty("coastalChance", .5);
        assertThrows(RuntimeException.class, () -> OceanConfiguration.parse(weights.toString()));
        var radius = original.deepCopy(); radius.addProperty("coveOpeningDegrees", 10000);
        assertThrows(RuntimeException.class, () -> OceanConfiguration.parse(radius.toString()));
        var fraction = original.deepCopy(); fraction.addProperty("coveOpeningDegrees", 100.5);
        assertThrows(ArithmeticException.class, () -> OceanConfiguration.parse(fraction.toString()));
    }

    @Test void releasedFullProfileKeepsAdmissionAndOptOutButRetiresTinyPerimeterSizing() {
        var old = JsonParser.parseString(new Gson().toJson(OceanProfile.INITIAL)).getAsJsonObject();
        old.remove("coveOpeningDegrees"); old.remove("coveShoulderDegrees");
        old.addProperty("perimeterMinRadius", 40); old.addProperty("perimeterMaxRadius", 64); old.addProperty("outletWidth", 2);
        old.addProperty("enabled", false); old.addProperty("admissionChance", .35);
        var normalized = OceanConfiguration.parse(old.toString());
        assertFalse(normalized.enabled()); assertEquals(.35, normalized.admissionChance());
        assertEquals(100, normalized.coveOpeningDegrees());
        old.addProperty("outletWidth", "2");
        assertThrows(RuntimeException.class, () -> OceanConfiguration.parse(old.toString()));
        old.remove("outletWidth");
        assertThrows(RuntimeException.class, () -> OceanConfiguration.parse(old.toString()));
    }
}
