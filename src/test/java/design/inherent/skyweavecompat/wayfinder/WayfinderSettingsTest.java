package design.inherent.skyweavecompat.wayfinder;

import com.google.gson.Gson;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WayfinderSettingsTest {
    @Test void policyAllowsUnknownModIdsAndTagsWithoutAnAllowlist() {
        var policy = new WayfinderSettings(false,false,0,128,2,200,List.of("optional_mod:hazard","#minecraft:leaves"));
        assertEquals(policy,WayfinderSettings.parse(new Gson().toJson(policy)));
    }
    @Test void malformedPolicyCannotSilentlyEnableAccess() {
        var json = new Gson().toJson(WayfinderSettings.DEFAULT);
        assertThrows(IllegalArgumentException.class,()->WayfinderSettings.parse(json.replace("\"operatorsOnly\":true","\"operatorsOnly\":\"false\"")));
        assertThrows(IllegalArgumentException.class,()->WayfinderSettings.parse(json.replace("{","{\"operatorsOnly\":false,")));
        assertThrows(IllegalArgumentException.class,()->new WayfinderSettings(true,true,40,128,1,200,List.of("../bad")));
        assertThrows(IllegalArgumentException.class,()->new WayfinderSettings(true,true,40,128,1,200,List.of("minecraft:fire","minecraft:fire")));
    }
}
