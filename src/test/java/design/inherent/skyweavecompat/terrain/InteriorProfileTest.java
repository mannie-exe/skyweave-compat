package design.inherent.skyweavecompat.terrain;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InteriorProfileTest {
    @Test void missingSkylightPolicyIsRejectedInsteadOfInserted() {
        var json = JsonParser.parseString(new Gson().toJson(InteriorProfile.INITIAL)).getAsJsonObject();
        json.remove("skylights");
        assertThrows(IllegalArgumentException.class, () -> InteriorConfiguration.parse(json.toString()));
        json.add("skylights", new JsonObject());
        assertThrows(IllegalArgumentException.class, () -> InteriorConfiguration.parse(json.toString()));
    }
    @Test void independentBaselineAndCarverPolicy() {
        var json = JsonParser.parseString(new Gson().toJson(InteriorProfile.INITIAL)).getAsJsonObject();
        json.addProperty("noiseChance", 0); json.addProperty("hollowChance", 0);
        var baseline = InteriorConfiguration.parse(json.toString());
        assertTrue(baseline.nativeCarvers());
        json.addProperty("nativeCarvers", false);
        assertFalse(InteriorConfiguration.parse(json.toString()).nativeCarvers());
    }

    @Test void rejectAmbiguousAndUnboundedConfiguration() {
        var initial = JsonParser.parseString(new Gson().toJson(InteriorProfile.INITIAL)).getAsJsonObject();
        var json = initial.deepCopy(); json.remove("shellThickness");
        assertThrows(IllegalArgumentException.class, () -> InteriorConfiguration.parse(json.toString()));
        var unknown = initial.deepCopy(); unknown.addProperty("hollowFrequency", 1);
        assertThrows(IllegalArgumentException.class, () -> InteriorConfiguration.parse(unknown.toString()));
        var fraction = initial.deepCopy(); fraction.addProperty("shellThickness", 12.5);
        assertThrows(ArithmeticException.class, () -> InteriorConfiguration.parse(fraction.toString()));
        var chance = initial.deepCopy(); chance.addProperty("hollowChance", 0.8);
        assertThrows(RuntimeException.class, () -> InteriorConfiguration.parse(chance.toString()));
        var wrongType = initial.deepCopy(); wrongType.addProperty("nativeCarvers", "false");
        assertThrows(IllegalArgumentException.class, () -> InteriorConfiguration.parse(wrongType.toString()));
    }
}
