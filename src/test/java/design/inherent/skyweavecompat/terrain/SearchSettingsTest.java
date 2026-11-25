package design.inherent.skyweavecompat.terrain;

import design.inherent.skyweavecompat.terrain.structure.SearchSettings;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SearchSettingsTest {
    @Test void schedulingRejectsUnboundedOrAmbiguousInputs() {
        assertThrows(IllegalArgumentException.class, () -> new SearchSettings(0, 5));
        assertThrows(IllegalArgumentException.class, () -> new SearchSettings(3, 5));
        assertThrows(IllegalArgumentException.class, () -> new SearchSettings(2, 11));
        assertThrows(IllegalArgumentException.class, () -> SearchSettings.parse("{\"candidateLimit\":100}"));
        assertThrows(IllegalArgumentException.class, () -> SearchSettings.parse("{\"candidateLimit\":100,\"timeLimitSeconds\":10,\"concurrentChunks\":\"2\",\"tickBudgetMillis\":5}"));
        assertEquals(new SearchSettings(100, 1, 2), SearchSettings.parse("{\"candidateLimit\":100,\"timeLimitSeconds\":10,\"concurrentChunks\":1,\"tickBudgetMillis\":2}"));
    }
    @Test void legacyCutoffsAreRetiredWithoutChangingSchedulingOrAcceptingUnknownFields() {
        String legacy = "{\"candidateLimit\":16,\"timeLimitSeconds\":5,\"concurrentChunks\":1,\"tickBudgetMillis\":2}";
        assertEquals(new SearchSettings(16, 1, 2), SearchSettings.parse(legacy));
        var normalized = design.inherent.skyweavecompat.configuration.ProfileJson.object(SearchSettings.normalize(legacy));
        assertEquals(java.util.Set.of("membershipBatchSize", "concurrentChunks", "tickBudgetMillis"), normalized.keySet());
        assertEquals(new SearchSettings(16, 1, 2), SearchSettings.parse(normalized.toString()));
        assertThrows(IllegalArgumentException.class, () -> SearchSettings.parse(legacy.replace("\"timeLimitSeconds\":5", "\"timeLimitSeconds\":\"5\"")));
        assertThrows(IllegalArgumentException.class, () -> SearchSettings.parse(legacy.replace("\"timeLimitSeconds\":5", "\"unknown\":5")));
    }
}
