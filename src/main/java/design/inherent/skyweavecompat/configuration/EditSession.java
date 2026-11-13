package design.inherent.skyweavecompat.configuration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Draft state on the owning thread; submissions retain immutable values across callbacks. */
public final class EditSession<K, V> {
    public static final class Submission<K, V> {
        private final EditSession<K, V> owner;
        private final long revision;
        private final Map<K, V> values, changes;
        private Submission(EditSession<K, V> owner, long revision, Map<K, V> values, Map<K, V> changes) {
            this.owner = owner; this.revision = revision;
            this.values = immutable(values); this.changes = immutable(changes);
        }
        public Map<K, V> values() { return values; }
        public Map<K, V> changes() { return changes; }
    }

    private Map<K, V> saved;
    private final Map<K, V> changes = new LinkedHashMap<>();
    private volatile long revision;

    public EditSession(Map<K, V> saved) { this.saved = immutable(saved); }
    private static <K, V> Map<K, V> immutable(Map<K, V> values) {
        values.forEach((key, value) -> { Objects.requireNonNull(key); Objects.requireNonNull(value); });
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
    public V saved(K key) { requireKey(key); return saved.get(key); }
    public V value(K key) { requireKey(key); return changes.getOrDefault(key, saved.get(key)); }
    public void set(K key, V value) {
        requireKey(key); Objects.requireNonNull(value);
        if (Objects.equals(saved.get(key), value)) changes.remove(key); else changes.put(key, value);
    }
    private void requireKey(K key) {
        if (!saved.containsKey(key)) throw new IllegalArgumentException("Unknown setting: " + key);
    }
    public boolean dirty() { return !changes.isEmpty(); }
    public boolean edited(K key) { requireKey(key); return changes.containsKey(key); }
    public Map<K, V> changes() { return immutable(changes); }
    public Map<K, V> values() {
        var values = new LinkedHashMap<>(saved); values.putAll(changes); return immutable(values);
    }
    public void discard() { changes.clear(); }
    public void reload(Map<K, V> values) {
        var next = immutable(values);
        if (!next.keySet().equals(saved.keySet())) throw new IllegalArgumentException("Settings schema changed");
        saved = next; changes.clear(); revision++;
    }
    public Submission<K, V> submit() { return new Submission<>(this, revision, values(), changes); }

    /** Typing preserves this revision; reload/acknowledgement invalidates prepared work. */
    public void checkCurrent(Submission<K, V> submission) {
        if (submission.owner != this || submission.revision != revision)
            throw new IllegalStateException("Stale settings submission");
    }

    /** A later edit or Revert is preserved against the newly acknowledged saved baseline. */
    public void acknowledge(Submission<K, V> submission, Map<K, V> actual) {
        checkCurrent(submission);
        var next = immutable(actual);
        if (!next.keySet().equals(saved.keySet())) throw new IllegalArgumentException("Settings schema changed");
        var current = values();
        saved = next; changes.clear(); revision++;
        current.forEach((key, value) -> {
            if (!Objects.equals(value, submission.values.get(key)) && !Objects.equals(value, saved.get(key)))
                changes.put(key, value);
        });
    }
}
