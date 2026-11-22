package design.inherent.skyweavecompat.terrain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import static org.junit.jupiter.api.Assertions.*;

class GenerationIdentityTest {
    @TempDir Path world;

    @Test void unchangedWorldReopensButNewSettingsCannotOverwriteIdentity() throws Exception {
        GenerationIdentity.prepare(world, "first", null).write();
        Files.createDirectory(world.resolve("region"));
        Files.write(world.resolve("region/r.0.0.mca"), new byte[]{1});
        GenerationIdentity.prepare(world, "first", null).write();
        assertThrows(IllegalStateException.class, () -> GenerationIdentity.prepare(world, "changed", null));
        assertEquals("first", Files.readString(world.resolve(GenerationIdentity.FILE)));
    }

    @Test void populatedUnmarkedWorldIsNeverAdopted() throws Exception {
        Files.createDirectory(world.resolve("region"));
        Files.write(world.resolve("region/r.0.0.mca"), new byte[]{1});
        assertThrows(IllegalStateException.class, () -> GenerationIdentity.prepare(world, "candidate", saved -> true));
        assertFalse(Files.exists(world.resolve(GenerationIdentity.FILE)));
    }

    @Test void devCanAdvanceCompatibleRevisionAndPolicyWithoutRewritingTerrain() throws Exception {
        String old = identity(17, Long.MIN_VALUE, 896, "old");
        String current = identity(18, Long.MIN_VALUE, 896, "current");
        Path marker = world.resolve(GenerationIdentity.FILE);
        Files.writeString(marker, old);
        var region = Files.createDirectory(world.resolve("region"));
        var savedChunk = Files.writeString(region.resolve("r.0.0.mca"), "saved data");
        var admission = GenerationIdentity.prepare(world, current, saved -> saved.get("policy").getAsString().equals("old"));
        assertTrue(admission.advances());
        assertEquals(old, Files.readString(marker));

        admission.write();
        assertEquals(current, Files.readString(marker));
        assertEquals("saved data", Files.readString(savedChunk));
        var witnessTime = FileTime.fromMillis(1000);
        Files.setLastModifiedTime(marker, witnessTime);
        var reopened = GenerationIdentity.prepare(world, current, saved -> fail("Exact identity must not need DEV admission"));
        assertFalse(reopened.advances());
        reopened.write();
        assertEquals(witnessTime, Files.getLastModifiedTime(marker));
    }

    @Test void devRefusesUnknownCorruptOrIncompatibleMarkers() throws Exception {
        String current = identity(18, 42, 896, "current");
        String previous = identity(18, 42, 896, "previous");
        Path marker = world.resolve(GenerationIdentity.FILE);
        for (String refused : new String[]{
                identity(19, 42, 896, "future revision"),
                identity(18, 43, 896, "different seed"),
                identity(18, 42, 880, "different bounds"),
                previous.replace("islands_v1", "foreign"),
                previous.replace("\"seed\":42", "\"seed\":\"42\""),
                previous.replace("\"seed\":42", "\"seed\":42.5"),
                previous.replace("\"seed\":42", "\"seed\":1,\"seed\":42"),
                previous.replace("{\"present\":true}", "{}"),
                previous + "{}",
                "{\"algorithm\":"
        }) {
            Files.writeString(marker, refused);
            assertThrows(IllegalStateException.class, () -> GenerationIdentity.prepare(world, current, saved -> true), refused);
            assertEquals(refused, Files.readString(marker));
        }
        Files.writeString(marker, previous);
        assertThrows(IllegalStateException.class, () -> GenerationIdentity.prepare(world, current, saved -> false));
        assertEquals(previous, Files.readString(marker));
    }

    @Test void devAdvancePreservesAChangedPreimage() throws Exception {
        var marker = world.resolve(GenerationIdentity.FILE);
        Files.writeString(marker, identity(18, 42, 896, "previous"));
        var admission = GenerationIdentity.prepare(world, identity(18, 42, 896, "current"), saved -> true);
        Files.writeString(marker, "edited");
        assertThrows(IOException.class, admission::write);
        assertEquals("edited", Files.readString(marker));
        try (var files = Files.list(world)) {
            assertEquals(1, files.count());
        }
    }

    private static String identity(int revision, long seed, int height, String policy) {
        return """
                {
                  "algorithm":"islands_v1-skyarch-1.4-%d",
                  "seed":%d,
                  "dimension":{"min_y":-128,"height":%d},
                  "biomes":{"present":true},
                  "islands":{"present":true},
                  "noise":{"present":true},
                  "profile":{"present":true},
                  "policy":"%s"
                }
                """.formatted(revision, seed, height, policy);
    }
}
