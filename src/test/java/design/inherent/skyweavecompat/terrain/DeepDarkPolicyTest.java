package design.inherent.skyweavecompat.terrain;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeepDarkPolicyTest {
    @Test void surfaceMaskIsCoherentRepeatableAndHandlesCoverageEndpoints() {
        var settings = ShapeProfile.DeepDark.DEFAULT;
        var none = new ShapeProfile.DeepDark(0,48,0,0,0,0);
        var all = new ShapeProfile.DeepDark(1,48,0,0,0,0);
        int covered=0, borders=0, changedSeed=0;
        for(int x=-256;x<256;x++)for(int z=-256;z<256;z++) {
            boolean selected=DeepDarkMaterials.sculk(42,x,z,settings);
            assertEquals(selected,DeepDarkMaterials.sculk(42,x,z,settings));
            assertFalse(DeepDarkMaterials.sculk(42,x,z,none));
            assertTrue(DeepDarkMaterials.sculk(42,x,z,all));
            if(selected)covered++;
            if(selected!=DeepDarkMaterials.sculk(42,x+1,z,settings))borders++;
            if(selected!=DeepDarkMaterials.sculk(43,x,z,settings))changedSeed++;
        }
        assertTrue(covered>230000 && covered<262144,"Near-continuous cap must retain exposed-rock interruptions: "+covered);
        assertTrue(borders>0 && borders<5000,"A coherent mask must not become per-block speckling: "+borders);
        assertTrue(changedSeed>0);
    }

    @Test void authoredScopeIsNestedRestoredOnFailureAndThreadLocal() throws Exception {
        assertFalse(DeepDarkHabitat.authored());
        DeepDarkHabitat.authored(()->{
            assertTrue(DeepDarkHabitat.authored());
            assertThrows(IllegalStateException.class,()->DeepDarkHabitat.authored(()->{throw new IllegalStateException();}));
            assertTrue(DeepDarkHabitat.authored());
            var unrelated = java.util.concurrent.CompletableFuture.supplyAsync(DeepDarkHabitat::authored);
            assertFalse(unrelated.join());
        });
        assertFalse(DeepDarkHabitat.authored());
        assertThrows(IllegalStateException.class,()->DeepDarkHabitat.authored(()->{throw new IllegalStateException();}));
        assertFalse(DeepDarkHabitat.authored());
    }

    @Test void retiredPatchKnobAndMissingGroupAreRejectedWithoutMigration() {
        var json = new Gson().toJsonTree(ShapeProfile.INITIAL).getAsJsonObject();
        var missing = json.deepCopy();missing.remove("deepDark");
        assertThrows(RuntimeException.class,()->ShapeConfiguration.parse(missing.toString()));
        var retired = json.deepCopy();retired.getAsJsonObject("fractures").addProperty("sculkPatchChance",.25);
        assertThrows(RuntimeException.class,()->ShapeConfiguration.parse(retired.toString()));
        var excessive = json.deepCopy();excessive.getAsJsonObject("deepDark").addProperty("surfaceScale",129);
        assertThrows(RuntimeException.class,()->ShapeConfiguration.parse(excessive.toString()));
        var negative = json.deepCopy();negative.getAsJsonObject("deepDark").addProperty("shriekerChance",-.01);
        assertThrows(RuntimeException.class,()->ShapeConfiguration.parse(negative.toString()));
    }
}
