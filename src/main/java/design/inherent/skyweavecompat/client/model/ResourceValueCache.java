package design.inherent.skyweavecompat.client.model;

import java.util.*;
import java.util.function.Function;

/** Identity cache for immutable component projections; owned by one resource epoch/render thread. */
final class ResourceValueCache {
    enum Kind { ENCHANTMENTS, STEW, PAINTING, AXOLOTL, POTION }
    static final Object ABSENT = new Object();
    private final int capacity;
    private final IdentityHashMap<Object, EnumMap<Kind, Object>> values = new IdentityHashMap<>();
    private final ArrayDeque<Object> order = new ArrayDeque<>();
    private final List<Runnable> invalidations = new ArrayList<>();

    ResourceValueCache(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Component cache capacity must be positive");
        this.capacity = capacity;
    }

    Object project(Kind kind, Object component, Function<Object, Object> projection) {
        if (component == null) return ABSENT;
        var entry = values.get(component);
        if (entry == null) {
            if (values.size() == capacity) values.remove(order.removeFirst());
            entry = new EnumMap<>(Kind.class);
            values.put(component, entry); order.addLast(component);
        }
        var value = entry.get(kind);
        if (value == null) {
            value = Objects.requireNonNullElse(projection.apply(component), ABSENT);
            entry.put(kind, value);
        }
        return value;
    }

    int size() { return values.size(); }
    void onClear(Runnable action) { invalidations.add(action); }
    void clear() { values.clear(); order.clear(); invalidations.forEach(Runnable::run); }
}
