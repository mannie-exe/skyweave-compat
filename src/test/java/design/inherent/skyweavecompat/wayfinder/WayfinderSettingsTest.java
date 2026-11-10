package design.inherent.skyweavecompat.wayfinder;

import com.google.gson.Gson;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WayfinderSettingsTest {
    @Test void retiredPermissionToggleDoesNotBecomeGlobalDebugOrEraseTuning() {
        var old = new Gson().toJsonTree(WayfinderSettings.DEFAULT).getAsJsonObject();
        old.remove("debugMessages"); old.remove("placementLookupHeight");
        old.addProperty("hideHotkeyPermissionMessage",false); old.addProperty("cooldownTicks",73);
        var policy = WayfinderSettings.parse(old.toString());
        assertFalse(policy.debugMessages()); assertEquals(16,policy.placementLookupHeight());
        assertEquals(73,policy.cooldownTicks());
        assertFalse(WayfinderSettings.normalize(old.toString()).contains("hideHotkeyPermissionMessage"));
        old.addProperty("hideHotkeyPermissionMessage","false");
        assertThrows(IllegalArgumentException.class,()->WayfinderSettings.parse(old.toString()));
    }
    @Test void upwardLookupRejectsUnboundedOrNegativeHeights() {
        var value = new Gson().toJsonTree(WayfinderSettings.DEFAULT).getAsJsonObject();
        for (int height : List.of(-1,65)) {
            value.addProperty("placementLookupHeight",height);
            assertThrows(IllegalArgumentException.class,()->WayfinderSettings.parse(value.toString()));
        }
    }
    @Test void policyAllowsUnknownModIdsAndTagsWithoutAnAllowlist() {
        var policy = new WayfinderSettings(false,false,0,8,128,2,200,List.of("optional_mod:hazard","#minecraft:leaves"));
        assertEquals(policy,WayfinderSettings.parse(new Gson().toJson(policy)));
    }
    @Test void malformedPolicyCannotSilentlyEnableAccess() {
        var json = new Gson().toJson(WayfinderSettings.DEFAULT);
        assertThrows(IllegalArgumentException.class,()->WayfinderSettings.parse(json.replace("\"operatorsOnly\":true","\"operatorsOnly\":\"false\"")));
        assertThrows(IllegalArgumentException.class,()->WayfinderSettings.parse(json.replace("{","{\"operatorsOnly\":false,")));
        assertThrows(IllegalArgumentException.class,()->new WayfinderSettings(true,true,40,8,128,1,200,List.of("../bad")));
        assertThrows(IllegalArgumentException.class,()->new WayfinderSettings(true,true,40,8,128,1,200,List.of("minecraft:fire","minecraft:fire")));
    }
}
