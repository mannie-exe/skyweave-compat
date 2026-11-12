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

    @Test void oversizedValidStartupJsonIsRefusedWithoutRewritingTheFile() throws Exception {
        var path=directory.resolve(SearchSettings.FILE);
        var policy=new SearchSettings(123,600,1,2);
        Files.writeString(path,new Gson().toJson(policy));
        assertEquals(policy,SearchSettings.load(directory));
        var oversized=" ".repeat(ConfigFile.LIMIT)+new Gson().toJson(SearchSettings.DEFAULT);
        Files.writeString(path,oversized);
        assertThrows(IOException.class,()->new ConfigFile(directory,SearchSettings.FILE).read());
        var failure=assertThrows(IllegalStateException.class,()->SearchSettings.load(directory));
        assertInstanceOf(IOException.class,failure.getCause());
        assertEquals(oversized,Files.readString(path));
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
