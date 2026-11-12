package design.inherent.skyweavecompat.configuration;

import com.google.gson.Gson;
import design.inherent.skyweavecompat.FeatureSettings;
import design.inherent.skyweavecompat.terrain.structure.SearchSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StartupFileBoundsTest {
    @TempDir Path directory;

    @Test void oversizedValidStartupJsonIsRefusedWithoutChangingTheActivePolicyOrFile() throws Exception {
        var original=SearchSettings.active();var path=directory.resolve(SearchSettings.FILE);
        try {
            var policy=new SearchSettings(123,600,1,2);
            Files.writeString(path,new Gson().toJson(policy));SearchSettings.load(directory);
            assertEquals(policy,SearchSettings.active());
            var oversized=" ".repeat(ConfigFile.LIMIT)+new Gson().toJson(SearchSettings.DEFAULT);
            Files.writeString(path,oversized);
            assertThrows(IOException.class,()->new ConfigFile(directory,SearchSettings.FILE).read());
            var failure=assertThrows(IllegalStateException.class,()->SearchSettings.load(directory));
            assertInstanceOf(IOException.class,failure.getCause());
            assertEquals(policy,SearchSettings.active());
            assertEquals(oversized,Files.readString(path));
        } finally {
            Files.writeString(path,new Gson().toJson(original));SearchSettings.load(directory);
        }
    }

    @Test void startupSwitchesUseTheSameFileBoundAsTheirEditor() throws Exception {
        var path=directory.resolve(FeatureSettings.FILE);
        var oversized="#"+"a".repeat(ConfigFile.LIMIT)+"\nnavigation.wayfinder=false\n";
        Files.writeString(path,oversized);
        assertThrows(IOException.class,()->new ConfigFile(directory,FeatureSettings.FILE).read());
        assertThrows(IOException.class,()->FeatureSettings.load(directory));
        assertEquals(oversized,Files.readString(path));
    }
}
