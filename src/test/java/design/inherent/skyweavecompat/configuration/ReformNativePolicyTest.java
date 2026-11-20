package design.inherent.skyweavecompat.configuration;

import design.inherent.skyweavecompat.NeighborConfig;
import design.inherent.skyweavecompat.placement.NeighborRule;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReformNativePolicyTest {
    @Test void wholeNativeDocumentRetainsTheExistingNeighborDistanceRule() throws Exception {
        var values = Map.<String, Object>of("minimumGap", 200, "maximumGap", 128, "minimumIslandRadius", 96);
        var source = com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        NeighborConfig.SPEC.correct(source);
        values.forEach(source::set);
        assertTrue(NeighborConfig.SPEC.isCorrect(source));
        assertThrows(IllegalArgumentException.class, () -> new NeighborRule.Settings(true, 200, 128, 96));
        var entry = SettingsCatalog.file("skyweave_compat-common.toml");
        var report = NativeSettingsPolicy.validate(entry, values);
        assertFalse(report.valid());
        assertEquals("neighbor_gap_order", report.issues().getFirst().code());
        assertEquals(design.inherent.reform.definition.FieldPath.ROOT.field("minimumGap"), report.issues().getFirst().path());
        assertTrue(report.issues().getFirst().related().contains(design.inherent.reform.definition.FieldPath.ROOT.field("maximumGap")));
        assertTrue(NativeSettingsPolicy.validate(entry, Map.of("minimumGap", 64, "maximumGap", 128, "minimumIslandRadius", 96)).valid());
    }
}
