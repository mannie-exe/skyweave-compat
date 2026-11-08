package design.inherent.skyweavecompat.terrain.structure;

import com.google.gson.GsonBuilder;
import design.inherent.skyweavecompat.configuration.ProfileJson;
import java.nio.file.*;

/** Operational search limits, deliberately excluded from terrain identity. */
public record SearchSettings(int candidateLimit, int timeLimitSeconds, int concurrentChunks, int tickBudgetMillis) {
    public static final String FILE = "skyweave_compat-search.json";
    public static final SearchSettings DEFAULT = new SearchSettings(65536, 120, 2, 5);
    private static volatile SearchSettings active = DEFAULT;
    public SearchSettings {
        if (candidateLimit < 16 || candidateLimit > 65536 || timeLimitSeconds < 5 || timeLimitSeconds > 600
                || concurrentChunks < 1 || concurrentChunks > 2 || tickBudgetMillis < 1 || tickBudgetMillis > 10)
            throw new IllegalArgumentException("Search limits: candidates16–65536, seconds5–600, requests1–2, tick milliseconds1–10");
    }
    public static SearchSettings active() { return active; }
    public static SearchSettings parse(String text) { return ProfileJson.record(text, SearchSettings.class); }
    public static void load(Path directory) {
        var path = directory.resolve(FILE);
        try {
            if (!Files.exists(path)) Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(DEFAULT) + "\n", StandardOpenOption.CREATE_NEW);
            active = parse(Files.readString(path));
        } catch (java.io.IOException | RuntimeException failure) { throw new IllegalStateException("Cannot load " + path, failure); }
    }
}
