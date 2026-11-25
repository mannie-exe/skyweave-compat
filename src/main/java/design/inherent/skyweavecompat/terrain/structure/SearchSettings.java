package design.inherent.skyweavecompat.terrain.structure;

import design.inherent.skyweavecompat.configuration.SettingSpec;
import design.inherent.skyweavecompat.configuration.RecordSettings;
import design.inherent.skyweavecompat.configuration.ConfigFile;
import com.google.gson.GsonBuilder;
import design.inherent.skyweavecompat.configuration.ProfileJson;
import java.nio.file.*;

/** Operational search limits, deliberately excluded from terrain identity. */
public record SearchSettings(
        @SettingSpec(minimum="16", maximum="65536", unit="candidates", group="scheduling") int membershipBatchSize,
        @SettingSpec(minimum="1", maximum="" + SearchSettings.MAX_CONCURRENT_CHUNKS, unit="chunks", group="scheduling") int concurrentChunks,
        @SettingSpec(minimum="1", maximum="10", unit="milliseconds", group="scheduling") int tickBudgetMillis) {
    public static final int MAX_CONCURRENT_CHUNKS = 2;
    public static final String FILE = "skyweave_compat-search.json";
    public static final SearchSettings DEFAULT = new SearchSettings(65536, 2, 5);
    public SearchSettings(int concurrentChunks, int tickBudgetMillis) { this(65536, concurrentChunks, tickBudgetMillis); }
    public SearchSettings {
        RecordSettings.validate(SearchSettings.class, membershipBatchSize, concurrentChunks, tickBudgetMillis);
    }
    public static SearchSettings parse(String text) { return ProfileJson.record(current(text).toString(), SearchSettings.class); }
    /** Retired operational cutoffs are accepted as legacy integers, never applied or rewritten on load. */
    private static com.google.gson.JsonObject current(String text) {
        var object = ProfileJson.object(text);
        boolean legacy = object.has("candidateLimit") || object.has("timeLimitSeconds");
        if (!object.has("membershipBatchSize") && legacy) object.add("membershipBatchSize",
                object.has("candidateLimit") ? object.get("candidateLimit") : new com.google.gson.JsonPrimitive(65536));
        for (String key : java.util.List.of("candidateLimit", "timeLimitSeconds")) if (object.has(key)) {
            var value = object.get(key);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
                throw new IllegalArgumentException("Expected legacy integer for " + key);
            try { value.getAsBigDecimal().intValueExact(); }
            catch (ArithmeticException error) { throw new IllegalArgumentException("Expected legacy integer for " + key, error); }
            object.remove(key);
        }
        return object;
    }
    public static String normalize(String text) {
        return new GsonBuilder().setPrettyPrinting().create().toJson(parse(text)) + "\n";
    }
    public static SearchSettings load(Path directory) {
        var path = directory.resolve(FILE);
        try {
            if (!Files.exists(path)) Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(DEFAULT) + "\n", StandardOpenOption.CREATE_NEW);
            return parse(ConfigFile.read(path));
        } catch (java.io.IOException | RuntimeException failure) { throw new IllegalStateException("Cannot load " + path, failure); }
    }
}
