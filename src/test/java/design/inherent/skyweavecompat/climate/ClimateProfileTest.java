package design.inherent.skyweavecompat.climate;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClimateProfileTest {
    @Test void runtimeAndEditorRejectMalformedOrUnboundedInputs() {
        String valid = "{\"altitudeScale\":1,\"airReach\":256,\"minimumCaveCover\":16,\"bottomDepth\":1.1}";
        assertEquals(ClimateProfile.DEFAULT, ClimateConfiguration.parse(valid));
        assertEquals(ClimateProfile.DEFAULT, ClimateConfiguration.parse(ClimateConfiguration.normalize(valid)));
        for (String invalid : new String[]{valid.replace("256", "256.5"), valid.replace("256", "513"),
                valid.replace("1.1", "0"), valid.replace("\"bottomDepth\":1.1", "\"typo\":1.1"), "{}"})
            assertThrows(RuntimeException.class, () -> ClimateConfiguration.parse(invalid));
    }
}
