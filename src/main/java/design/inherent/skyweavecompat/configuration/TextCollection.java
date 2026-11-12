package design.inherent.skyweavecompat.configuration;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Session-local row identity over an existing draft binding; never a persistence owner. */
public final class TextCollection {
    public record Row(long id, String value) {}
    private static final Gson JSON = new Gson();
    private final SettingId id;
    private SettingsBinding binding;
    private List<Row> rows = List.of();
    private final Map<Long, Integer> positions = new HashMap<>();
    private final Map<String, Integer> counts = new HashMap<>();
    private long nextId;
    private String lastRaw, error;

    public TextCollection(SettingsBinding binding) { id = binding.id(); bind(binding); }
    public void bind(SettingsBinding binding) {
        if (!id.equals(binding.id()) || binding.definition().kind() != SettingDefinition.Kind.STRINGS)
            throw new IllegalArgumentException("Collection binding identity changed");
        this.binding = binding; synchronize();
    }
    private void synchronize() {
        String raw = binding.read().get();
        if (Objects.equals(lastRaw, raw)) return;
        var next = new ArrayList<Row>();
        try {
            for (String text : binding.readElements().get()) next.add(new Row(++nextId, text));
            error = null;
        } catch (RuntimeException invalid) { error = Objects.toString(invalid.getMessage(), "Invalid collection"); }
        rows = List.copyOf(next); lastRaw = raw; reindex();
    }
    private void reindex() {
        positions.clear(); counts.clear();
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i); positions.put(row.id(), i); counts.merge(row.value(), 1, Integer::sum);
        }
    }
    public List<Row> rows() { synchronize(); return rows; }
    public String error() { synchronize(); return error; }
    public String anchor(long row) { return id.anchor() + "/row/" + row; }
    public boolean ordered() { return binding.definition().valueType() != Set.class; }
    private int position(long row) {
        synchronize(); var index = positions.get(row);
        if (error != null || index == null) throw new IllegalStateException("Collection row is no longer attached");
        return index;
    }
    public String value(long row) { return rows.get(position(row)).value(); }
    public SettingIssue issue(long row) {
        String value = value(row); var issue = binding.elementIssue(value);
        if (issue != null) return issue;
        if ((!ordered() || binding.definition().constraints().unique()) && counts.get(value) > 1)
            return new SettingIssue("unique", binding.definition().key(), List.of(), "Entries must be unique");
        return null;
    }
    public boolean canAdd() {
        synchronize(); int maximum = binding.definition().maximumEntries();
        return error == null && (maximum < 0 || rows.size() < maximum);
    }
    private void publish(List<Row> next) {
        String raw = JSON.toJson(next.stream().map(Row::value).toList());
        binding.edit().accept(raw);
        rows = List.copyOf(next); lastRaw = binding.read().get(); error = null; reindex();
    }
    public void set(long row, String value) {
        int index = position(row); Objects.requireNonNull(value);
        var next = new ArrayList<>(rows); next.set(index, new Row(row, value)); publish(next);
    }
    public long add() {
        if (!canAdd()) throw new IllegalStateException("Collection cannot accept another row");
        long row = ++nextId; var next = new ArrayList<>(rows); next.add(new Row(row, "")); publish(next); return row;
    }
    /** Return the nearest surviving row for focus, or null for the collection actions. */
    public Long remove(long row) {
        int index = position(row); var next = new ArrayList<>(rows); next.remove(index); publish(next);
        return rows.isEmpty() ? null : rows.get(Math.min(index, rows.size() - 1)).id();
    }
    public boolean canMove(long row, int direction) {
        if (direction != -1 && direction != 1) throw new IllegalArgumentException("Expected one row step");
        int destination = position(row) + direction;
        return ordered() && destination >= 0 && destination < rows.size();
    }
    public void move(long row, int direction) {
        if (!canMove(row, direction)) throw new IllegalArgumentException("Row cannot move there");
        int index = position(row); var next = new ArrayList<>(rows);
        java.util.Collections.swap(next, index, index + direction); publish(next);
    }
}
