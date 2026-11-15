package design.inherent.skyweavecompat.rendering;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReverseVerticesTest {
    @Test void reversesWindingAndNormalsWithoutSeparatingAttributes() {
        int[] source = new int[32];
        for (int vertex = 0; vertex < 4; vertex++) {
            for (int attribute = 0; attribute < 7; attribute++) source[vertex * 8 + attribute] = vertex * 100 + attribute;
            source[vertex * 8 + 7] = 0x42007f81;
        }
        int[] before = source.clone();
        int[] reversed = ReverseVertices.copy(source);
        assertArrayEquals(before, source);
        assertNotSame(source, reversed);
        for (int vertex = 0; vertex < 4; vertex++) {
            int original = (4 - vertex) % 4;
            for (int attribute = 0; attribute < 7; attribute++)
                assertEquals(source[original * 8 + attribute], reversed[vertex * 8 + attribute]);
            assertEquals(0x4200817f, reversed[vertex * 8 + 7]);
        }
        assertArrayEquals(source, ReverseVertices.copy(reversed));
    }

    @Test void rejectsUnknownVertexFormats() {
        assertThrows(IllegalArgumentException.class, () -> ReverseVertices.copy(new int[28]));
    }

    @Test void comparesEveryAttributeAndCyclicOrderWithoutChangingEitherInput() {
        int[] source = new int[32];
        for (int i = 0; i < 32; i++) source[i] = i * 127 + 3;
        for (int shift = 0; shift < 4; shift++) {
            int[] reverse = ReverseVertices.copy(source), candidate = new int[32];
            for (int vertex = 0; vertex < 4; vertex++)
                System.arraycopy(reverse, vertex * 8, candidate, ((vertex + shift) % 4) * 8, 8);
            assertTrue(ReverseVertices.matches(source, candidate));
            assertArrayEquals(reverse, ReverseVertices.copy(source));
            for (int attr = 0; attr < 8; attr++) {
                candidate[attr] ^= 1;
                assertFalse(ReverseVertices.matches(source, candidate));
                candidate[attr] ^= 1;
            }
        }
        assertFalse(ReverseVertices.matches(source, new int[28]));
    }
}
