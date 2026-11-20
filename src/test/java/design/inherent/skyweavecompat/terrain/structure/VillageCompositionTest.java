package design.inherent.skyweavecompat.terrain.structure;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.util.*;
import org.junit.jupiter.api.Test;

class VillageCompositionTest {
    private static final String SOURCE = """
        {"schema":1,"roles":{
          "homes":{"minimum":2,"alternatives":[{"template":"test:house","type":"minecraft:legacy_single_pool_element","processors":"test:weathering","projection":"rigid"}]},
          "workshop":{"minimum":1,"alternatives":[{"template":"test:house","type":"minecraft:legacy_single_pool_element","processors":"test:weathering","projection":"rigid"}]}
        }}
        """;
    private static final VillageComposition.Identity HOUSE = new VillageComposition.Identity(
            "test:house", "minecraft:legacy_single_pool_element", "test:weathering", "rigid");
    private static VillageComposition.Occurrence house(int x) {
        return new VillageComposition.Occurrence(HOUSE, x, 72, 0, "NONE");
    }
    @Test void onePhysicalHouseCannotSatisfyTwoHomesThroughDuplicatePoolPaths() {
        var contract = VillageComposition.parse(SOURCE);
        var one = VillageComposition.evaluate(contract, List.of(house(0), house(0)), false);
        assertEquals(VillageComposition.Status.MISSING_REQUIRED, one.status());
        assertEquals(Map.of("homes", 1), one.missing());
        assertEquals(1, one.counts().get("workshop"));
        var two = VillageComposition.evaluate(contract, List.of(house(0), house(16)), false);
        assertTrue(two.qualified());
        assertEquals(2, two.counts().get("homes"));
        assertThrows(UnsupportedOperationException.class, () -> contract.roles().clear());
        assertThrows(UnsupportedOperationException.class, () -> contract.roles().get("homes").alternatives().clear());
    }
    @Test void processingAndProjectionVariantsMustMatchAndUnknownIsNotMissing() {
        var contract = VillageComposition.parse(SOURCE);
        for (var identity : List.of(
                new VillageComposition.Identity(HOUSE.template(), HOUSE.type(), "test:other", HOUSE.projection()),
                new VillageComposition.Identity(HOUSE.template(), "minecraft:single_pool_element", HOUSE.processors(), HOUSE.projection()),
                new VillageComposition.Identity(HOUSE.template(), HOUSE.type(), HOUSE.processors(), "terrain_matching"))) {
            var wrong = new VillageComposition.Occurrence(identity, 16, 72, 0, "NONE");
            var result = VillageComposition.evaluate(contract, List.of(house(0), wrong), true);
            assertEquals(VillageComposition.Status.UNSUPPORTED_ELEMENT, result.status());
            assertEquals(1, result.counts().get("homes"));
        }
        // An unknown unrelated element cannot erase positively proven required content.
        assertTrue(VillageComposition.evaluate(contract, List.of(house(0), house(16)), true).qualified());
    }
    @Test void exhaustedAssessmentHasNoPartialRoleCredit() {
        var result = VillageComposition.evaluate(VillageComposition.parse(SOURCE), Collections.nCopies(2049, house(0)), false);
        assertEquals(VillageComposition.Status.PIECE_BUDGET, result.status());
        assertEquals(Map.of(), result.counts());
        assertEquals(0, result.inspected());
    }
    @Test void savedPolicyIgnoresSetOrderButRetainsActualRequirements() {
        var id=net.minecraft.resources.ResourceLocation.parse("test:village");
        var contract=VillageComposition.parse(SOURCE);
        var other=new VillageComposition.Identity("test:other",HOUSE.type(),HOUSE.processors(),HOUSE.projection());
        var forward=new VillageComposition.Contract(Map.of("homes",new VillageComposition.Requirement(2,new LinkedHashSet<>(List.of(HOUSE,other)))));
        var reverse=new VillageComposition.Contract(Map.of("homes",new VillageComposition.Requirement(2,new LinkedHashSet<>(List.of(other,HOUSE)))));
        assertEquals(VillageComposition.generationInputs(Map.of(id,forward)),VillageComposition.generationInputs(Map.of(id,reverse)));
        assertNotEquals(VillageComposition.generationInputs(Map.of(id,forward)),VillageComposition.generationInputs(Map.of(id,contract)));
        assertNotEquals(VillageComposition.generationInputs(Map.of(id,forward)),VillageComposition.generationInputs(Map.of(id,
                new VillageComposition.Contract(Map.of("homes",new VillageComposition.Requirement(1,Set.of(HOUSE,other)))))));
    }
    @Test void invalidOrAmbiguousAuthoredContractsCannotReplaceCurrentPolicy() {
        for (String input : List.of(SOURCE.replace("\"schema\":1", "\"schema\":1,\"schema\":1"),
                SOURCE.replace("\"minimum\":2", "\"minimum\":\"2\""),
                SOURCE.replace("\"minimum\":2", "\"minimum\":0"),
                SOURCE.replace("\"minimum\":2", "\"minimum\":17"),
                SOURCE.replace("\"minimum\":2", "\"minimum\":1.5"),
                SOURCE.replace("\"rigid\"", "\"floating\""),
                SOURCE.replace("test:house", "test:../house"),
                SOURCE.replace("\"schema\":1", "\"schema\":2"),
                SOURCE.replace("\"roles\":", "\"unknown\":"))) {
            assertThrows(IllegalArgumentException.class, () -> VillageComposition.parse(input), input);
        }
        var duplicate = JsonParser.parseString(SOURCE).getAsJsonObject();
        var alternatives = duplicate.getAsJsonObject("roles").getAsJsonObject("homes").getAsJsonArray("alternatives");
        alternatives.add(alternatives.get(0).deepCopy());
        assertThrows(IllegalArgumentException.class, () -> VillageComposition.parse(duplicate.toString()));
    }
}
