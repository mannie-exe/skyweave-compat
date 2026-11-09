package design.inherent.skyweavecompat.quests;

import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class QuestItemFiltersTest {
    private static CompoundTag items(String... ids) {
        var value = new CompoundTag(); value.putInt("schema", 1);
        var list = new ListTag(); for (String id : ids) list.add(StringTag.valueOf(id));
        value.put("items", list); return value;
    }
    @Test void alternativesAndTagAreDistinctImmutableSelectors() {
        var value = items("minecraft:potato", "minecraft:bread");
        var result = QuestItemFilters.parse(value);
        assertEquals(Set.of("minecraft:potato", "minecraft:bread"), result.items().stream().map(Object::toString).collect(java.util.stream.Collectors.toSet()));
        value.remove("items");
        assertEquals(2, result.items().size());
        value.putString("tag", "minecraft:logs");
        assertEquals("minecraft:logs", QuestItemFilters.parse(value).tag().location().toString());
    }
    @Test void malformedAmbiguousOversizedAndFuturePayloadsFailClosed() {
        assertNull(QuestItemFilters.parse(null));
        assertNull(QuestItemFilters.parse(StringTag.valueOf("minecraft:bread")));
        assertNull(QuestItemFilters.parse(items()));
        assertNull(QuestItemFilters.parse(items("bread")));
        assertNull(QuestItemFilters.parse(items("minecraft:bread", "minecraft:bread")));
        var value = items("minecraft:bread"); value.putInt("schema", 2);
        assertNull(QuestItemFilters.parse(value));
        value.putInt("schema", 1); value.putString("tag", "minecraft:logs");
        assertNull(QuestItemFilters.parse(value));
        value.remove("tag"); value.putBoolean("invented", true);
        assertNull(QuestItemFilters.parse(value));
        String[] ids = new String[65]; java.util.Arrays.setAll(ids, i -> "minecraft:item_" + i);
        assertNull(QuestItemFilters.parse(items(ids)));
        assertNull(QuestItemFilters.parse(items("minecraft:" + "a".repeat(256))));
        value = items("minecraft:bread"); value.putString("items", "minecraft:bread");
        assertNull(QuestItemFilters.parse(value));
    }
}
