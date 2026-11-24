package design.inherent.skyweavecompat.terrain;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class TerrainStageTest {
    @Test void restoresNestedAndFailedScopesWithoutLeakingToOtherThreads() {
        assertEquals(TerrainStage.COMPLETE, TerrainStage.current());
        TerrainStage.INTERIOR.query(() -> {
            assertEquals(TerrainStage.INTERIOR, TerrainStage.current());
            assertThrows(IllegalStateException.class, () -> TerrainStage.EXTERIOR.query(() -> {
                assertEquals(TerrainStage.EXTERIOR, TerrainStage.current());
                throw new IllegalStateException("expected");
            }));
            assertEquals(TerrainStage.INTERIOR, TerrainStage.current());
            assertThrows(IllegalArgumentException.class, () -> TerrainStage.INTERIOR.query(() -> {
                assertEquals(TerrainStage.INTERIOR, TerrainStage.current());
                throw new IllegalArgumentException("same-stage failure");
            }));
            assertEquals(TerrainStage.INTERIOR, TerrainStage.current());
            assertEquals(TerrainStage.COMPLETE, CompletableFuture.supplyAsync(TerrainStage::current).join());
            return null;
        });
        assertEquals(TerrainStage.COMPLETE, TerrainStage.current());
    }
}
