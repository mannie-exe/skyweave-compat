package design.inherent.skyweavecompat.terrain;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import design.inherent.skyweavecompat.configuration.ConfigJson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Admission of the recorded generation contract before native level startup. */
public final class GenerationIdentity {
    public static final String FILE = "skyweave-generation.json";
    private static final Pattern OWNED_REVISION = Pattern.compile("islands_v1-skyarch-1\\.4-([1-9][0-9]{0,8})");
    private static final int NATIVE_CODEC_DEPTH = 64;
    private GenerationIdentity() {}

    /** The save holds its native session lock during preparation and write. */
    static Admission prepare(Path world, String identity, Predicate<JsonObject> devNativeLayout) throws IOException {
        Path marker = world.resolve(FILE);
        if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Generation identity must be a regular file: " + marker);
            }
            String recorded = Files.readString(marker);
            if (recorded.equals(identity)) return new Admission(marker, recorded, identity);
            if (devNativeLayout == null) throw mismatch(null);

            JsonObject saved;
            JsonObject current;
            try {
                saved = ConfigJson.object(recorded, NATIVE_CODEC_DEPTH);
                current = ConfigJson.object(identity, NATIVE_CODEC_DEPTH);
            } catch (IOException | NumberFormatException invalid) {
                throw mismatch("The DEV marker could not be read as a valid generation identity.");
            }
            if (!compatible(saved, current) || !devNativeLayout.test(saved)) {
                throw mismatch("DEV reopening requires a recognized identity with the same seed and a compatible native generator and dimension layout.");
            }
            return new Admission(marker, recorded, identity);
        }

        Path region = world.resolve("region");
        if (Files.isDirectory(region)) try (var files = Files.list(region)) {
            if (files.anyMatch(p -> p.getFileName().toString().endsWith(".mca"))) {
                throw new IllegalStateException("Existing terrain has no Skyweave generation identity; refusing automatic conversion.");
            }
        }
        return new Admission(marker, null, identity);
    }

    private static IllegalStateException mismatch(String reason) {
        return new IllegalStateException("Skyweave generation settings differ from this world's recorded identity. "
                + (reason == null ? "" : reason + " ")
                + "Restore its original mod/settings or create a separate world; automatic conversion is unsupported.");
    }

    private static boolean compatible(JsonObject saved, JsonObject current) {
        int revision = ownedRevision(saved.get("algorithm"));
        if (revision == 0 || revision > ownedRevision(current.get("algorithm"))) return false;
        for (String field : new String[]{"dimension", "biomes", "islands", "noise", "profile"}) {
            if (!(saved.get(field) instanceof JsonObject object) || object.size() == 0) return false;
        }
        if (!saved.get("dimension").equals(current.get("dimension"))) return false;
        var seed = saved.get("seed");
        if (seed == null || !seed.isJsonPrimitive() || !seed.getAsJsonPrimitive().isNumber()) return false;
        try {
            return seed.getAsBigDecimal().longValueExact() == current.get("seed").getAsBigDecimal().longValueExact();
        } catch (ArithmeticException invalid) {
            return false;
        }
    }

    private static int ownedRevision(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return 0;
        var matcher = OWNED_REVISION.matcher(value.getAsString());
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    record Admission(Path marker, String recorded, String identity) {
        boolean advances() { return recorded != null && !recorded.equals(identity); }

        void write() throws IOException {
            if (recorded == null) {
                Files.createDirectories(marker.getParent());
                Files.writeString(marker, identity, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } else if (advances()) {
                Path temporary = Files.createTempFile(marker.getParent(), ".skyweave-generation-", ".tmp");
                try {
                    Files.writeString(temporary, identity);
                    if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS) || !recorded.equals(Files.readString(marker))) {
                        throw new IOException("Generation identity changed during startup: " + marker);
                    }
                    Files.move(temporary, marker, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
        }
    }
}
