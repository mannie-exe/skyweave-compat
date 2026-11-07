package design.inherent.skyweavecompat.configuration;

import com.google.gson.JsonParser;
import design.inherent.skyweavecompat.Feature;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettingsCatalogTest {
    @Test void everySwitchHasOneHomeAndEveryOwnedFileIsReachable() {
        var found=EnumSet.noneOf(Feature.class);
        for(var group:SettingsCatalog.Group.values())for(var feature:SettingsCatalog.features(group))assertTrue(found.add(feature));
        assertEquals(EnumSet.allOf(Feature.class),found);
        var files=new HashSet<String>();
        for(var entry:SettingsCatalog.FILES){assertTrue(files.add(entry.filename()));assertFalse(entry.filename().contains("/"));if(entry.group()!=null)assertNotNull(entry.group().category);}
    }
    @Test void catalogueTranslationsExistWithoutLoadingOptionalMods() throws Exception {
        try(var reader=new InputStreamReader(getClass().getResourceAsStream("/assets/skyweave_compat/lang/en_us.json"),StandardCharsets.UTF_8)){
            var text=JsonParser.parseReader(reader).getAsJsonObject();
            for(var group:SettingsCatalog.Group.values())assertTrue(text.has("skyweave_compat.settings.mod."+group.label),group.name());
            for(var file:SettingsCatalog.FILES){assertTrue(text.has(file.labelKey()),file.filename());assertTrue(text.has(file.summaryKey()),file.filename());}
            for(var feature:Feature.values())assertTrue(text.has("skyweave_compat.settings.summary."+feature.key),feature.key);
            var shapes = JsonSettings.find("skyweave_compat-shapes.json");
            translatedFields(text, JsonParser.parseString(shapes.defaults()).getAsJsonObject(), "skyweave_compat.settings.field.shapes");
        }
    }
    private static void translatedFields(com.google.gson.JsonObject text, com.google.gson.JsonObject fields, String prefix) {
        for (var entry : fields.entrySet()) {
            String key = prefix + "." + entry.getKey();
            assertTrue(text.has(key), key);
            if (entry.getValue().isJsonObject()) translatedFields(text, entry.getValue().getAsJsonObject(), key);
            else assertTrue(text.has(key + ".help"), key + ".help");
        }
    }
}
