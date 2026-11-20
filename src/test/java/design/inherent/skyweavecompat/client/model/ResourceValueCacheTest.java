package design.inherent.skyweavecompat.client.model;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResourceValueCacheTest {
    @Test void identityKindsEvictionAndResetPreserveActualProjectionOwnership() {
        var cache = new ResourceValueCache(2); var calls = new AtomicInteger();
        var first = new String("same"); var equal = new String("same"); var third = new Object();
        var compute = (java.util.function.Function<Object, Object>) value -> { calls.incrementAndGet(); return new Object(); };
        Object original = cache.project(ResourceValueCache.Kind.PAINTING, first, compute);
        assertSame(original, cache.project(ResourceValueCache.Kind.PAINTING, first, compute));
        assertNotSame(original, cache.project(ResourceValueCache.Kind.AXOLOTL, first, compute));
        assertNotSame(original, cache.project(ResourceValueCache.Kind.PAINTING, equal, compute));
        assertEquals(3, calls.get());
        cache.project(ResourceValueCache.Kind.POTION, third, compute);
        assertEquals(2, cache.size());
        assertNotSame(original, cache.project(ResourceValueCache.Kind.PAINTING, first, compute));
        assertEquals(5, calls.get());
        cache.clear(); assertEquals(0, cache.size());
        assertNotSame(original, cache.project(ResourceValueCache.Kind.PAINTING, first, compute));
        assertSame(ResourceValueCache.ABSENT, cache.project(ResourceValueCache.Kind.PAINTING, null, compute));
        assertEquals(6, calls.get());
    }
}
