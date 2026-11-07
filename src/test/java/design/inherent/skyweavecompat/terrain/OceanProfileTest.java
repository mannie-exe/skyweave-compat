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
        var radius = original.deepCopy(); radius.addProperty("perimeterMaxRadius", 10000);
        assertThrows(RuntimeException.class, () -> OceanConfiguration.parse(radius.toString()));
        var fraction = original.deepCopy(); fraction.addProperty("perimeterMaxRadius", 48.5);
        assertThrows(ArithmeticException.class, () -> OceanConfiguration.parse(fraction.toString()));
    }
}
