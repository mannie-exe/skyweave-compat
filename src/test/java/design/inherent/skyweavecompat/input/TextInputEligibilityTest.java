package design.inherent.skyweavecompat.input;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TextInputEligibilityTest {
    @Test void textOriginStaysSuppressedThroughCloseAndReleaseThenFreshInputWorks() {
        var policy = new TextInputEligibility();
        policy.observe(Set.of(82, 89), true, true);
        assertFalse(policy.allows(List.of(82, 89), true, false, true));
        assertFalse(policy.allows(List.of(82, 89), false, false, true));
        policy.completed(Set.of(82));
        assertFalse(policy.allows(List.of(82), false, false, true));
        policy.completed(Set.of());
        assertTrue(policy.allows(List.of(82, 89), false, false, true));
    }

    @Test void deliberateSlotMouseAndModifiersWorkWhileTextMouseAndLetterChordsStayProtected() {
        var policy = new TextInputEligibility();
        policy.observe(Set.of(341, 82), true, true);
        assertTrue(policy.allows(List.of(-98), true, true, true));
        assertTrue(policy.allows(List.of(341, -98), true, true, true));
        assertFalse(policy.allows(List.of(82, -98), true, true, true));
        assertFalse(policy.allows(List.of(-98), true, false, true));
        assertFalse(policy.allows(List.of(341, 86), true, false, true));
    }

    @Test void windowLossDoesNotActivateHeldOrReleaseShortcuts() {
        var policy = new TextInputEligibility();
        policy.observe(Set.of(82), false, false);
        assertFalse(policy.allows(List.of(82), false, false, false));
        assertFalse(policy.allows(List.of(82), false, false, true));
        policy.completed(Set.of());
        assertTrue(policy.allows(List.of(82), false, false, true));
    }

    @Test void excessSyntheticKeysRefuseWithoutUnboundedRetentionAndResetIsLocal() {
        var policy = new TextInputEligibility();
        var upstream = new java.util.HashSet<Integer>();
        for (int i = 0; i < 600; i++) upstream.add(i);
        policy.observe(upstream, true, true);
        assertFalse(policy.allows(List.of(1), false, false, true));
        assertEquals(600, upstream.size());
        policy.completed(Set.of());
        assertTrue(policy.allows(List.of(1), false, false, true));
        policy.observe(Set.of(1), true, true);
        policy.reset();
        assertTrue(policy.allows(List.of(1), false, false, true));
    }
}
