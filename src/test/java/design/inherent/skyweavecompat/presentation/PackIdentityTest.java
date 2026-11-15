package design.inherent.skyweavecompat.presentation;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PackIdentityTest {
    private static Map<String, Object> properties(String version) {
        return Map.of("skyweave:identity-schema", 1, "skyweave:pack-id", "create_skyweave",
                "skyweave:pack-version", version);
    }

    @Test void acceptsInstalledBaselineDevelopmentAndPrereleaseVersions() {
        for (String version : new String[]{"DEV", "0.18.0", "0.18.0-rc.1+build.2"})
            assertEquals(version, PackIdentity.read(properties(version)).orElseThrow().version());
        var longSchema = new HashMap<>(properties("0.18.0"));
        longSchema.put("skyweave:identity-schema", 1L);
        assertTrue(PackIdentity.read(longSchema).isPresent());
    }

    @Test void missingForeignUnknownAndMalformedMetadataAreNotPackIdentity() {
        assertTrue(PackIdentity.read(null).isEmpty());
        assertTrue(PackIdentity.read(Map.of()).isEmpty());
        var valid = properties("0.18.0");
        for (String key : valid.keySet()) {
            var missing = new HashMap<>(valid); missing.remove(key);
            assertTrue(PackIdentity.read(missing).isEmpty(), key);
        }
        for (Object invalid : new Object[]{"1", 1.0, true, 0, 2, -1}) {
            var changed = new HashMap<>(valid); changed.put("skyweave:identity-schema", invalid);
            assertTrue(PackIdentity.read(changed).isEmpty(), invalid.toString());
        }
        var foreign = new HashMap<>(valid); foreign.put("skyweave:pack-id", "another_pack");
        assertTrue(PackIdentity.read(foreign).isEmpty());
        for (Object invalid : new Object[]{1, true, "", " 0.18.0", "0.18.0\nforged line", "x".repeat(129)}) {
            var changed = new HashMap<>(valid); changed.put("skyweave:pack-version", invalid);
            assertTrue(PackIdentity.read(changed).isEmpty(), invalid.toString());
        }
    }
}
