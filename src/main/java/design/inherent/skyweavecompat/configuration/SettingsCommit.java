package design.inherent.skyweavecompat.configuration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Frozen submissions and partial writes; application receipts belong to the runtime owner. */
public final class SettingsCommit {
    @FunctionalInterface public interface Operation { void run() throws Exception; }
    public record Text(String baseline, String candidate) {
        public Text { Objects.requireNonNull(baseline); Objects.requireNonNull(candidate); }
    }
    public static final class Write {
        private final String filename;
        private final Text text;
        private final Operation check, save;
        private boolean consumed;

        public Write(String filename, Operation check, Operation save) { this(filename, null, check, save); }
        public Write(String filename, Text text, Operation check, Operation save) {
            this.filename = Objects.requireNonNull(filename); this.text = text;
            this.check = Objects.requireNonNull(check); this.save = Objects.requireNonNull(save);
        }
        public String filename() { return filename; }
        public Text text() { return text; }
        private void requireUnused() {
            if (consumed) throw new IllegalStateException("Settings write was already attempted");
        }
        public synchronized void check() throws Exception { requireUnused(); check.run(); }
        public synchronized void save() throws Exception {
            requireUnused(); consumed = true;
            check.run(); save.run();
        }
    }
    public static final class Prepared {
        private final List<Write> writes;
        private boolean consumed;
        private Prepared(List<Write> writes) {
            this.writes = List.copyOf(writes);
            if (this.writes.stream().map(Write::filename).distinct().count() != writes.size())
                throw new IllegalArgumentException("Duplicate configuration in save plan");
        }
        public synchronized Result save() {
            if (consumed) throw new IllegalStateException("Settings plan was already attempted");
            consumed = true;
            for (var write : writes) try { write.check(); }
            catch (Exception failure) { return new Result(List.of(), write.filename(), failure); }
            var written = new ArrayList<String>();
            for (var write : writes) try { write.save(); written.add(write.filename()); }
            catch (Exception failure) { return new Result(written, write.filename(), failure); }
            return new Result(written, null, null);
        }
    }
    public record Result(List<String> written, String failed, Exception failure) {
        public Result { written = List.copyOf(written); }
        public boolean success() { return failure == null; }
        public void requireSuccess() throws IOException {
            if (failure instanceof IOException io) throw io;
            if (failure instanceof RuntimeException runtime) throw runtime;
            if (failure != null) throw new IOException("Cannot save " + failed, failure);
        }
    }
    public static Prepared prepare(List<Write> pending) { return new Prepared(pending); }
    public static Result save(List<Write> pending) { return prepare(pending).save(); }
    private SettingsCommit() {}
}
