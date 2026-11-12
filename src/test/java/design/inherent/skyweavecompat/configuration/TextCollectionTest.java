package design.inherent.skyweavecompat.configuration;

import com.google.gson.Gson;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TextCollectionTest {
    private record Fixture(EditSession<String, String> session, SettingsBinding binding, TextCollection rows) {}
    private Fixture fixture(Class<?> type, int maximum, boolean unique, List<String> initial) {
        var constraints = new SettingConstraints(null, null, false, BigDecimal.ONE, "", "", maximum, 8, unique, "[a-z]+");
        var definition = new SettingDefinition("values", type, type == Set.class ? new java.util.LinkedHashSet<>(initial) : initial, constraints);
        var session = new EditSession<>(Map.of("values", new Gson().toJson(initial)));
        var binding = new SettingsBinding(new SettingId("skyweave_compat:fixture", "values"), definition,
                () -> session.value("values"), value -> session.set("values", value), List::of, session::dirty,
                () -> definition.inspect(session.value("values")).issue(),
                () -> JsonEdits.textArray(session.value("values")), definition::elementIssue);
        return new Fixture(session, binding, new TextCollection(binding));
    }
    @Test void typingMovingRemovingAndRebindingKeepTheSurvivingRowIdentities() {
        var fixture = fixture(List.class, 256, false, List.of("alpha", "beta", "gamma")); var data = fixture.rows();
        var before = data.rows(); long first = before.get(0).id(), second = before.get(1).id(), third = before.get(2).id();
        data.set(first, "changed"); assertEquals(before.stream().map(TextCollection.Row::id).toList(), data.rows().stream().map(TextCollection.Row::id).toList());
        data.move(third, -1); assertEquals(List.of(first, third, second), data.rows().stream().map(TextCollection.Row::id).toList());
        assertEquals(third, data.remove(first));
        assertThrows(IllegalStateException.class, () -> data.set(first, "stale"));
        assertEquals(List.of("gamma", "beta"), fixture.binding().readElements().get());
        data.bind(fixture.binding()); assertEquals(List.of(third, second), data.rows().stream().map(TextCollection.Row::id).toList());
        assertFalse(data.canMove(third, -1)); assertFalse(data.canMove(second, 1));
        assertThrows(IllegalArgumentException.class, () -> data.move(third, -1));
        assertThrows(IllegalArgumentException.class, () -> data.canMove(second, 0));
    }
    @Test void invalidRowTextAndDuplicatesRemainDraftsWithTargetedIssues() {
        var fixture = fixture(List.class, 8, true, List.of("alpha", "beta")); var data = fixture.rows();
        long first = data.rows().get(0).id(), second = data.rows().get(1).id();
        data.set(second, "alpha"); assertEquals("unique", data.issue(first).code()); assertEquals("unique", data.issue(second).code());
        data.set(first, "INVALID LONG TEXT"); assertEquals("length", data.issue(first).code()); assertNull(data.issue(second));
        assertEquals("INVALID LONG TEXT", data.value(first)); assertNotNull(fixture.binding().issue());
        assertEquals(List.of("INVALID LONG TEXT", "alpha"), fixture.binding().readElements().get());
        assertTrue(fixture.session().dirty());
    }
    @Test void schemaLimitsPermitTheFull256RowsAndNeverRecycleRemovedIds() {
        var fixture = fixture(List.class, 256, false, List.of()); var data = fixture.rows();
        for (int i = 0; i < 256; i++) data.add();
        assertEquals(256, data.rows().size()); assertFalse(data.canAdd());
        String raw = fixture.binding().read().get(); assertThrows(IllegalStateException.class, data::add);
        assertEquals(raw, fixture.binding().read().get());
        long removed = data.rows().getLast().id(); data.remove(removed); assertTrue(data.canAdd());
        long replacement = data.add(); assertTrue(replacement > removed);
        assertThrows(IllegalStateException.class, () -> data.value(removed));
    }
    @Test void setsExposeNoOrderingAndKeepTheirUniquenessRule() {
        var fixture = fixture(Set.class, 8, false, List.of("alpha", "beta")); var data = fixture.rows();
        long first = data.rows().getFirst().id(), second = data.rows().getLast().id();
        assertFalse(data.ordered()); assertFalse(data.canMove(first, 1));
        assertThrows(IllegalArgumentException.class, () -> data.move(first, 1));
        data.set(second, "alpha"); assertEquals("unique", data.issue(first).code());
    }
    @Test void externalReplacementOrMalformedRawValuesCannotRedirectStaleRows() {
        var fixture = fixture(List.class, 8, false, List.of("alpha", "beta")); var data = fixture.rows();
        long old = data.rows().getFirst().id(); data.set(old, "changed"); fixture.session().discard();
        assertEquals(List.of("alpha", "beta"), data.rows().stream().map(TextCollection.Row::value).toList());
        assertTrue(data.rows().getFirst().id() > old); assertThrows(IllegalStateException.class, () -> data.set(old, "wrong"));
        for (String invalid : List.of("[1]", "[\"x\"],\"injected\":1", "[\"unterminated]")) {
            fixture.binding().edit().accept(invalid);
            assertNotNull(data.error()); assertTrue(data.rows().isEmpty()); assertFalse(data.canAdd());
            assertThrows(IllegalStateException.class, data::add); assertEquals(invalid, fixture.binding().read().get());
        }
        fixture.binding().reset(); assertNull(data.error()); assertEquals(2, data.rows().size());
    }
}
