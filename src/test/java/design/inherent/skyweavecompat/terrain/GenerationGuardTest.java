package design.inherent.skyweavecompat.terrain;

import java.nio.file.Path;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class GenerationGuardTest {
    @TempDir Path world;
    private static ResourceKey<Level> dimension(String id) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(id));
    }
    @Test void dimensionIdsCannotEscapeOrAliasStorageThroughDotSegments() {
        for (String id : new String[]{"example:../../peer", "example:a/../b", "example:./b", "..:outside", ".:outside"}) {
            assertThrows(IllegalStateException.class,()->GenerationGuard.dimensionPath(world,dimension(id)),id);
        }
    }
    @Test void emptyPathSegmentsCannotAliasAnotherDimension() {
        for (String id : new String[]{"example:/islands", "example:nested//islands", "example:nested/"}) {
            assertThrows(IllegalStateException.class,()->GenerationGuard.dimensionPath(world,dimension(id)),id);
        }
    }
}
