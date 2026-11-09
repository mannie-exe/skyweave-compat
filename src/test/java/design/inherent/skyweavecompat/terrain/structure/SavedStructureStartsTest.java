package design.inherent.skyweavecompat.terrain.structure;

import net.minecraft.nbt.CompoundTag;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SavedStructureStartsTest {
    private static final Set<String> TARGETS = Set.of("ctov:small/village_plains");

    @Test void modernEmptyInvalidAndOtherStructuresDoNotRequireGeneration() {
        var root = new CompoundTag(); root.putInt("DataVersion", 3955);
        assertFalse(SavedStructureStarts.possible(null, TARGETS));
        assertFalse(SavedStructureStarts.possible(root, TARGETS));
        var starts = new CompoundTag(); var structures = new CompoundTag();
        structures.put("starts", starts); root.put("structures", structures);
        var invalid = new CompoundTag(); invalid.putString("id", "INVALID");
        starts.put(TARGETS.iterator().next(), invalid);
        assertFalse(SavedStructureStarts.possible(root, TARGETS));
        var other = new CompoundTag(); other.putString("id", "minecraft:swamp_hut");
        starts.put("minecraft:swamp_hut", other);
        assertFalse(SavedStructureStarts.possible(root, TARGETS));
        var possible = new CompoundTag(); possible.putString("id", TARGETS.iterator().next());
        starts.put(TARGETS.iterator().next(), possible);
        assertTrue(SavedStructureStarts.possible(root, TARGETS));
    }

    @Test void unknownAndLegacyFormatsRequireNativeUpgradeInsteadOfNegativeCaching() {
        var root = new CompoundTag();
        assertTrue(SavedStructureStarts.possible(root, TARGETS));
        root.putInt("DataVersion", 2730);
        assertTrue(SavedStructureStarts.possible(root, TARGETS));
    }
}
