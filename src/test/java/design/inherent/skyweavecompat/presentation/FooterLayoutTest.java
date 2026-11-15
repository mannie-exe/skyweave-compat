package design.inherent.skyweavecompat.presentation;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FooterLayoutTest {
    private static final List<FooterLayout.Row> NATIVE_MINIMUM = List.of(
            new FooterLayout.Row(92, 20), new FooterLayout.Row(116, 20), new FooterLayout.Row(140, 20),
            new FooterLayout.Row(164, 20), new FooterLayout.Row(198, 20));

    @Test void onlyBottomRowMovesForStandaloneMinimumWindow() {
        assertEquals(List.of(92, 116, 140, 164, 188), FooterLayout.reserve(NATIVE_MINIMUM, 83, 208).orElseThrow());
        assertEquals(198, NATIVE_MINIMUM.getLast().top());
    }

    @Test void modernFixAndIdentityCloseGapsWithoutCrossingLogoOrFooter() {
        var positions = FooterLayout.reserve(NATIVE_MINIMUM, 83, 198).orElseThrow();
        assertEquals(List.of(90, 112, 134, 156, 178), positions);
        for (int i = 1; i < positions.size(); i++) assertTrue(positions.get(i) >= positions.get(i - 1) + 22);
        var arranged = positions.stream().map(y -> new FooterLayout.Row(y, 20)).toList();
        assertEquals(positions, FooterLayout.reserve(arranged, 83, 198).orElseThrow());
    }

    @Test void spaciousNativeLayoutRemainsExact() {
        assertEquals(NATIVE_MINIMUM.stream().map(FooterLayout.Row::top).toList(),
                FooterLayout.reserve(NATIVE_MINIMUM, 83, 300).orElseThrow());
    }

    @Test void impossibleSpacePreservesNativeFallbackAndMalformedRowsAreRefused() {
        assertTrue(FooterLayout.reserve(NATIVE_MINIMUM, 83, 170).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> FooterLayout.reserve(
                List.of(new FooterLayout.Row(10, 20), new FooterLayout.Row(10, 20)), 0, 100));
        assertThrows(IllegalArgumentException.class, () -> FooterLayout.reserve(
                List.of(new FooterLayout.Row(10, 0)), 0, 100));
    }
}
