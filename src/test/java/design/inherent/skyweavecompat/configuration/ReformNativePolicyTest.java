package design.inherent.skyweavecompat.configuration;

import design.inherent.reform.definition.DocumentDefinition;
import design.inherent.reform.definition.DocumentId;
import design.inherent.skyweavecompat.NeighborConfig;
import design.inherent.skyweavecompat.placement.NeighborRule;
import java.util.Map;
import java.util.Set;
import net.neoforged.neoforge.common.ModConfigSpec;
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
        var schema = Class.forName("design.inherent.reform.neoforge.configuration.NativeSchema");
        var constructor = schema.getDeclaredConstructor(DocumentId.class, ModConfigSpec.class, Map.class, int.class, Set.class,
                java.util.function.Function.class);
        constructor.setAccessible(true);
        var nativeSchema = constructor.newInstance(DocumentId.parse("skyweave_compat:sky_villages"), NeighborConfig.SPEC,
                Map.of(), 1024, Set.of(), (java.util.function.Function<Map<String, Object>, design.inherent.reform.validation.ValidationReport>)
                        candidate -> NativeSettingsPolicy.validate(SettingsCatalog.file("skyweave_compat-common.toml"), candidate));
        var field = schema.getDeclaredField("definition");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var definition = (DocumentDefinition<Map<String, Object>>) field.get(nativeSchema);
        assertFalse(definition.validate(values).report().valid(), "Native shared editing bypassed the existing whole-document distance rule");
    }
}
