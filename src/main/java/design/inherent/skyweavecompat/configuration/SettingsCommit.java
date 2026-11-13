package design.inherent.skyweavecompat.configuration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Frozen persistence submissions and observed partial results; runtime application remains owner-specific. */
public final class SettingsCommit {
    @FunctionalInterface public interface Operation { void run() throws Exception; }
    @FunctionalInterface public interface Evidence { Observation read() throws Exception; }
    @FunctionalInterface public interface Intended { boolean matches(String text) throws Exception; }
    public enum Persistence { UNATTEMPTED, REFUSED, FAILED, UNKNOWN, SAVED, WRITTEN }
    public record Text(String baseline, String candidate) {
        public Text { Objects.requireNonNull(baseline); Objects.requireNonNull(candidate); }
    }
    /** savedText is supplied only by a backend that observed those bytes. */
    public record Observation(Persistence state, String savedText) {
        public Observation {
            if (state != Persistence.WRITTEN && savedText != null) throw new IllegalArgumentException("Only observed writes have saved text");
        }
    }
    public record Receipt(String filename, Persistence state, String savedText, Exception failure) {}
    public static final class Write {
        private final String filename;
        private final Text text;
        private final Operation check, save;
        private final Evidence saved, failed;
        private Receipt receipt;
        private boolean consumed;

        public Write(String filename, Operation check, Operation save) { this(filename, null, check, save); }
        public Write(String filename, Text text, Operation check, Operation save) {
            this(filename,text,check,save,() -> new Observation(Persistence.WRITTEN,null),
                    () -> new Observation(Persistence.UNKNOWN,null));
        }
        public Write(String filename, Text text, Operation check, Operation save, Evidence saved, Evidence failed) {
            this.filename = Objects.requireNonNull(filename); this.text = text;
            this.check = Objects.requireNonNull(check); this.save = Objects.requireNonNull(save);
            this.saved = Objects.requireNonNull(saved); this.failed = Objects.requireNonNull(failed);
        }
        public String filename() { return filename; }
        public Text text() { return text; }
        private void requireUnused() {
            if (consumed) throw new IllegalStateException("Settings write was already attempted");
        }
        public synchronized void check() throws Exception { requireUnused(); check.run(); }
        /** Execute once; throwing does not imply that persistence never happened. */
        public synchronized void save() throws Exception {
            var result = execute(); if (result.failure() != null) throw result.failure();
        }
        private synchronized Receipt execute() {
            requireUnused(); consumed = true;
            boolean attempted = false;
            try {
                check.run(); attempted = true; save.run();
                var observation = saved.read();
                if (observation.state() != Persistence.WRITTEN) throw new IOException("Cannot establish saved configuration values");
                return receipt = new Receipt(filename,Persistence.WRITTEN,observation.savedText(),null);
            } catch (Exception failure) {
                var observation = new Observation(attempted ? Persistence.UNKNOWN : Persistence.REFUSED,null);
                if (attempted) try { observation = failed.read(); }
                catch (Exception unreadable) { failure.addSuppressed(unreadable); }
                return receipt = new Receipt(filename,observation.state(),observation.savedText(),failure);
            }
        }
        Observation savedObservation() {
            if (receipt == null || receipt.state() != Persistence.WRITTEN) throw new IllegalStateException("No observed saved receipt");
            return new Observation(receipt.state(),receipt.savedText());
        }
        Observation failureObservation() throws Exception { return failed.read(); }
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
            catch (Exception failure) {
                return new Result(writes.stream().map(owner -> new Receipt(owner.filename(),owner == write ? Persistence.REFUSED
                        : Persistence.UNATTEMPTED,null,owner == write ? failure : null)).toList(),write.filename(),failure);
            }
            var receipts = new ArrayList<Receipt>(); String failed = null; Exception failure = null;
            for (var write : writes) {
                if (failure != null) { receipts.add(new Receipt(write.filename(),Persistence.UNATTEMPTED,null,null)); continue; }
                Receipt receipt;
                try { receipt = write.execute(); }
                catch (Exception refused) { receipt = new Receipt(write.filename(),Persistence.REFUSED,null,refused); }
                receipts.add(receipt);
                if (receipt.failure() != null) { failed = write.filename(); failure = receipt.failure(); }
            }
            return new Result(receipts,failed,failure);
        }
    }
    public record Result(List<Receipt> receipts, String failed, Exception failure) {
        public Result { receipts = List.copyOf(receipts); }
        public List<String> written() { return receipts.stream().filter(receipt -> receipt.state() == Persistence.WRITTEN).map(Receipt::filename).toList(); }
        public boolean success() { return failure == null; }
        public void requireSuccess() throws IOException {
            if (failure instanceof IOException io) throw io;
            if (failure instanceof RuntimeException runtime) throw runtime;
            if (failure != null) throw new IOException("Cannot save " + failed, failure);
        }
    }
    /** Failure reconciliation is bounded and read-only; never roll back over an external edit. */
    public static Observation observe(ConfigFile file, String baseline, Intended intended) throws IOException {
        String actual = file.read();
        if (actual.equals(baseline)) return new Observation(Persistence.FAILED,null);
        try { if (intended.matches(actual)) return new Observation(Persistence.WRITTEN,actual); }
        catch (Exception invalid) { return new Observation(Persistence.UNKNOWN,null); }
        return new Observation(Persistence.UNKNOWN,null);
    }
    public static Prepared prepare(List<Write> pending) { return new Prepared(pending); }
    public static Result save(List<Write> pending) { return prepare(pending).save(); }
    private SettingsCommit() {}
}
