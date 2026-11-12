package design.inherent.skyweavecompat.terrain.structure;

import design.inherent.skyweavecompat.configuration.SettingSpec;
import design.inherent.skyweavecompat.configuration.RecordSettings;
import design.inherent.skyweavecompat.configuration.ConfigFile;
import com.google.gson.GsonBuilder;
import design.inherent.skyweavecompat.configuration.ProfileJson;
import java.nio.file.*;

/** Operational search limits, deliberately excluded from terrain identity. */
public record SearchSettings(
        @SettingSpec(minimum="16", maximum="65536", unit="candidates", group="coverage") int candidateLimit,
        @SettingSpec(minimum="5", maximum="600", unit="seconds", group="coverage") int timeLimitSeconds,
        @SettingSpec(minimum="1", maximum="2", unit="chunks", group="scheduling") int concurrentChunks,
        @SettingSpec(minimum="1", maximum="10", unit="milliseconds", group="scheduling") int tickBudgetMillis) {
    public static final String FILE = "skyweave_compat-search.json";
    public static final SearchSettings DEFAULT = new SearchSettings(65536, 600, 2, 5);
    private static volatile SearchSettings active = DEFAULT;
    public SearchSettings {
        RecordSettings.validate(SearchSettings.class, candidateLimit, timeLimitSeconds, concurrentChunks, tickBudgetMillis);
    }
    public static SearchSettings active() { return active; }
    public static SearchSettings parse(String text) { return ProfileJson.record(text, SearchSettings.class); }
    public static void load(Path directory) {
        var path = directory.resolve(FILE);
        try {
            if (!Files.exists(path)) Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(DEFAULT) + "\n", StandardOpenOption.CREATE_NEW);
            active = parse(ConfigFile.read(path));
        } catch (java.io.IOException | RuntimeException failure) { throw new IllegalStateException("Cannot load " + path, failure); }
    }
}
