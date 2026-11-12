package design.inherent.skyweavecompat.bluemap;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlueMapSettingsTest {
    private static final String VALID = """
        {"maps":["world"],"detailDistance":4000,"tileRadius":31,"concurrentRequests":4,"geometryMiB":256}
        """;

    @Test void startupAndEditorCannotDisagreeAboutDuplicateObjectKeys() {
        var duplicate=VALID.replace("\"maps\":[\"world\"]","\"maps\":[],\"maps\":[\"world\"]");
        assertThrows(RuntimeException.class,()->BlueMapSettings.parse(duplicate));
    }

    @Test void boundedSelectionRejectsMalformedPolicies() {
        assertTrue(BlueMapSettings.DEFAULT.maps().isEmpty());
        assertTrue(BlueMapSettings.parse(VALID).maps().contains("world"));
        for (var malformed : new String[]{VALID.replace("4000", "4000.1"), VALID.replace("31", "49"),
                VALID.replace("256", "2048"), VALID.replace("[\"world\"]", "[true]"),
                VALID.replace("4000", "\"4000\""), VALID.replace("\"world\"", "\"\"")}) {
            assertThrows(RuntimeException.class, () -> BlueMapSettings.parse(malformed));
        }
        assertThrows(UnsupportedOperationException.class, () -> BlueMapSettings.parse(VALID).maps().add("other"));
    }
}
