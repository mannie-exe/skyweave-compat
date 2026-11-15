package design.inherent.skyweavecompat.rendering;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransparencyProfileTest {
    private static final String VALID = """
            {"schema":1,"feature":"fusion.glass_backfaces","providers":["plain","material"],
             "targets":[{"family":"vanilla_panes"}],"exclusions":[{"block":"minecraft:glass_pane","state":{"waterlogged":"true"}}]}
            """;
    private static TransparencyProfile parse(String source) { return TransparencyProfile.parse(JsonParser.parseString(source).getAsJsonObject()); }
    @Test void explicitFalseAndStateExclusionsStayTypedAndImmutable() {
        var p = parse(VALID.replace("\"schema\":1", "\"schema\":1,\"enabled\":false"));
        assertFalse(p.enabled()); assertEquals("true", p.exclusions().getFirst().state().get("waterlogged"));
        assertThrows(UnsupportedOperationException.class, () -> p.providers().clear());
        assertThrows(UnsupportedOperationException.class, () -> p.targets().clear());
    }
    @Test void resourcesCannotSelectUnknownCodeProvidersOrUnrelatedFeatures() {
        for (String text : java.util.List.of(VALID.replace("\"plain\"", "\"java.lang.Runtime\""),
                VALID.replace("fusion.glass_backfaces", "worldgen.terrain"), VALID.replace("\"plain\"", "\"create_ct\""),
                VALID.replace("\"schema\":1", "\"schema\":1.0"), VALID.replace("\"schema\":1", "\"schema\":\"1\""),
                VALID.replace("\"schema\":1", "\"schema\":1,\"enabled\":\"false\""),
                VALID.replace("\"schema\":1", "\"schema\":1,\"handler\":\"anything\""),
                VALID.replace("minecraft:glass_pane", "../bad")))
            assertThrows(IllegalArgumentException.class, () -> parse(text), text);
        String excessive = VALID.replace("{\"family\":\"vanilla_panes\"}",
                String.join(",", java.util.Collections.nCopies(129, "{\"family\":\"vanilla_panes\"}")));
        assertThrows(IllegalArgumentException.class, () -> parse(excessive));
    }
}
