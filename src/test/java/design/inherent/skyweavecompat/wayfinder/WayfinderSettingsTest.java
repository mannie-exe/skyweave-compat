package design.inherent.skyweavecompat.wayfinder;

import com.google.gson.Gson;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WayfinderSettingsTest {
    @Test void retiredServerDisplayFieldsRequireAnExplicitReconciliation() {
        var value = new Gson().toJsonTree(WayfinderSettings.DEFAULT).getAsJsonObject();
        value.addProperty("cooldownTicks",73);
        assertEquals(73,WayfinderSettings.parse(value.toString()).cooldownTicks());
        for(var field:List.of("debugMessages","hideHotkeyPermissionMessage")) {
            var old=value.deepCopy();old.addProperty(field,false);
            var failure=assertThrows(IllegalArgumentException.class,()->WayfinderSettings.parse(old.toString()));
            assertTrue(failure.getMessage().contains("client-owned"));
        }
    }
    @Test void upwardLookupRejectsUnboundedOrNegativeHeights() {
        var value = new Gson().toJsonTree(WayfinderSettings.DEFAULT).getAsJsonObject();
        for (int height : List.of(-1,65)) {
            value.addProperty("placementLookupHeight",height);
            assertThrows(IllegalArgumentException.class,()->WayfinderSettings.parse(value.toString()));
        }
    }
    @Test void policyAllowsUnknownModIdsAndTagsWithoutAnAllowlist() {
        var policy = new WayfinderSettings(false,0,8,128,2,200,List.of("optional_mod:hazard","#minecraft:leaves"));
        assertEquals(policy,WayfinderSettings.parse(new Gson().toJson(policy)));
    }
    @Test void malformedPolicyCannotSilentlyEnableAccess() {
        var json = new Gson().toJson(WayfinderSettings.DEFAULT);
        assertThrows(IllegalArgumentException.class,()->WayfinderSettings.parse(json.replace("\"operatorsOnly\":true","\"operatorsOnly\":\"false\"")));
        assertThrows(IllegalArgumentException.class,()->WayfinderSettings.parse(json.replace("{","{\"operatorsOnly\":false,")));
        assertThrows(IllegalArgumentException.class,()->new WayfinderSettings(true,40,8,128,1,200,List.of("../bad")));
        assertThrows(IllegalArgumentException.class,()->new WayfinderSettings(true,40,8,128,1,200,List.of("minecraft:fire","minecraft:fire")));
    }
}
