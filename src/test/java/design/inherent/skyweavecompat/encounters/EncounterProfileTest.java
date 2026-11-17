package design.inherent.skyweavecompat.encounters;

import com.google.gson.Gson;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EncounterProfileTest {
    private static final Gson JSON=new Gson();
    @Test void defaultsPreserveNativeModeAndStrictRoundTrip(){
        assertEquals(20,EncounterProfile.DEFAULT.patrolCheckPercent());
        assertEquals(EncounterProfile.Mode.NATIVE_TYPE,EncounterProfile.DEFAULT.spawners().mode());
        assertEquals(EncounterProfile.DEFAULT,EncounterConfiguration.parse(JSON.toJson(EncounterProfile.DEFAULT)));
    }
    @Test void invalidOwnershipCandidatesDoNotCoerceOrDropFields(){
        String source=JSON.toJson(EncounterProfile.DEFAULT);
        for(String candidate:new String[]{source.replace("NATIVE_TYPE","unknown"),source.replace("20.0","\"20\""),
                source.replace("\"patrolCheckPercent\":20.0","\"patrolCheckPercent\":101"),
                source.replace("\"patrolCheckPercent\":20.0","\"patrolCheckPercent\":1e999"),
                source.replace("\"husk\":{","\"husk\":{\"unknown\":false,"),
                source.replace("has_husk","Bad Identifier"),
                source.replace("\"priority\":20","\"priority\":10"),
                source.replaceFirst("\\{","{\"patrolCheckPercent\":20,")})
            assertThrows(RuntimeException.class,()->EncounterConfiguration.parse(candidate),candidate);
    }
    @Test void overlappingTagsChooseOneDeclaredPriority(){
        var rules=EncounterProfile.DEFAULT.spawners().rules();
        assertEquals(SpawnerSelection.Variant.HUSK,SpawnerSelection.select(rules,"minecraft:zombie",v->true,id->true));
        assertEquals(SpawnerSelection.Variant.GELID,SpawnerSelection.select(rules,"minecraft:zombie",v->v!=SpawnerSelection.Variant.HUSK,id->true));
        assertEquals(SpawnerSelection.Variant.BOGGED,SpawnerSelection.select(rules,"minecraft:skeleton",v->true,
                Set.of("variantsandventures:has_bogged","variantsandventures:has_verdant")::contains));
        assertNull(SpawnerSelection.select(rules,"minecraft:spider",v->true,id->true));
        assertNull(SpawnerSelection.select(rules,"minecraft:zombie",v->true,id->false));
        assertTrue(SpawnerSelection.sameFamily("minecraft:zombie","variantsandventures:thicket"));
        assertFalse(SpawnerSelection.sameFamily("minecraft:zombie","variantsandventures:verdant"));
        assertFalse(SpawnerSelection.sameFamily("minecraft:zombie","minecraft:zombie_villager"));
    }
}
