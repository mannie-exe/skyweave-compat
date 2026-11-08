package design.inherent.skyweavecompat.terrain;

import design.inherent.skyweavecompat.terrain.structure.SearchSettings;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SearchSettingsTest {
    @Test void budgetsRejectUnboundedOrAmbiguousInputs() {
        assertThrows(IllegalArgumentException.class, () -> new SearchSettings(65537, 120, 2, 5));
        assertThrows(IllegalArgumentException.class, () -> new SearchSettings(65536, 0, 2, 5));
        assertThrows(IllegalArgumentException.class, () -> new SearchSettings(65536, 120, 3, 5));
        assertThrows(IllegalArgumentException.class, () -> new SearchSettings(65536, 120, 2, 11));
        assertThrows(IllegalArgumentException.class, () -> SearchSettings.parse("{\"candidateLimit\":100}"));
        assertThrows(IllegalArgumentException.class, () -> SearchSettings.parse("{\"candidateLimit\":100,\"timeLimitSeconds\":10,\"concurrentChunks\":\"2\",\"tickBudgetMillis\":5}"));
        assertEquals(new SearchSettings(100, 10, 1, 2), SearchSettings.parse("{\"candidateLimit\":100,\"timeLimitSeconds\":10,\"concurrentChunks\":1,\"tickBudgetMillis\":2}"));
    }
}
