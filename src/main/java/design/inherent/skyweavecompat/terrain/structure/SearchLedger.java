/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.terrain.structure;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/** Exact job-local membership with bounded memory. All mutating calls belong on background workers. */
final class SearchLedger implements Closeable {
    private final int width, batch;
    private final Path parent;
    private final TreeSet<byte[]> buffer = new TreeSet<>(Arrays::compareUnsigned);
    private final ArrayList<Run> levels = new ArrayList<>();
    private final Set<Path> owned = new HashSet<>();
    private final Set<Run> opened = new HashSet<>();
    private Path directory;
    private long sequence;
    private volatile long size;
    private boolean closed, failed;

    SearchLedger(int width, int batch, Path parent) {
        if (width < 1 || width > 256 || batch < 1 || batch > 65536)
            throw new IllegalArgumentException("Invalid search membership batch");
        this.width = width; this.batch = batch; this.parent = parent;
    }

    /** False means this exact fixed-width key was already admitted, including retired memory batches. */
    synchronized boolean add(byte[] key) throws IOException {
        if (closed || failed) throw new IllegalStateException("Search membership is retired or failed");
        if (key.length != width) throw new IllegalArgumentException("Wrong search key width");
        if (buffer.contains(key)) return false;
        try {
            for (var run : levels) if (run != null && run.contains(key)) return false;
            buffer.add(key.clone());
            if (buffer.size() == batch) spill();
            size++;
            return true;
        } catch (IOException | RuntimeException failure) { failed = true; throw failure; }
    }

    /** Progress reads never wait for disk IO or a merge. */
    long size() { return size; }
    synchronized int buffered() { return buffer.size(); }
    synchronized int runs() { return (int)levels.stream().filter(run -> run != null).count(); }

    private Path output() throws IOException {
        if (directory == null) directory = Files.createTempDirectory(parent, "skyweave-search-");
        var path = directory.resolve("run-" + sequence++);
        owned.add(path);
        return path;
    }

    private void spill() throws IOException {
        var path = output();
        try (var out = new BufferedOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE_NEW))) {
            for (var key : buffer) out.write(key);
        }
        var merged = new Run(path, buffer.size());
        int level = 0;
        while (level < levels.size() && levels.get(level) != null) {
            var previous = levels.get(level);
            var next = merge(previous, merged);
            previous.close(); merged.close();
            Files.delete(previous.path); owned.remove(previous.path);
            Files.delete(merged.path); owned.remove(merged.path);
            levels.set(level++, null); merged = next;
        }
        if (level == levels.size()) levels.add(merged); else levels.set(level, merged);
        buffer.clear();
    }

    private Run merge(Run first, Run second) throws IOException {
        var path = output();
        long a = 0, b = 0, count = 0;
        byte[] left = first.key(a), right = second.key(b);
        try (var out = new BufferedOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE_NEW))) {
            while (left != null || right != null) {
                int order = left == null ? 1 : right == null ? -1 : Arrays.compareUnsigned(left, right);
                out.write(order <= 0 ? left : right); count++;
                if (order <= 0) left = first.key(++a);
                if (order >= 0) right = second.key(++b);
            }
        }
        return new Run(path, count);
    }

    private final class Run implements Closeable {
        final Path path;
        final long count;
        final FileChannel channel;
        final byte[] first, last;
        Run(Path path, long count) throws IOException {
            this.path = path; this.count = count;
            channel = FileChannel.open(path, StandardOpenOption.READ);
            try {
                if (channel.size() != Math.multiplyExact(count, width)) throw new IOException("Incomplete search membership run");
                first = key(0); last = key(count - 1);
            } catch (IOException | RuntimeException error) { channel.close(); throw error; }
            opened.add(this);
        }
        byte[] key(long index) throws IOException {
            if (index >= count) return null;
            var key = ByteBuffer.allocate(width);
            long offset = Math.multiplyExact(index, width);
            while (key.hasRemaining()) {
                int n = channel.read(key, offset + key.position());
                if (n < 0) throw new IOException("Truncated search membership run");
            }
            return key.array();
        }
        boolean contains(byte[] key) throws IOException {
            if (Arrays.compareUnsigned(key, first) < 0 || Arrays.compareUnsigned(key, last) > 0) return false;
            long low = 0, high = count - 1;
            while (low <= high) {
                long mid = (low + high) >>> 1;
                int compare = Arrays.compareUnsigned(key(mid), key);
                if (compare < 0) low = mid + 1;
                else if (compare > 0) high = mid - 1;
                else return true;
            }
            return false;
        }
        @Override public void close() throws IOException {
            try { channel.close(); } finally { if (!channel.isOpen()) opened.remove(this); }
        }
    }

    @Override public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        IOException failure = null;
        for (var run : java.util.List.copyOf(opened)) try { run.close(); }
        catch (IOException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        for (var path : owned) try { Files.deleteIfExists(path); }
        catch (IOException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        if (directory != null) try { Files.deleteIfExists(directory); }
        catch (IOException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        buffer.clear(); levels.clear(); owned.clear();
        if (failure != null) throw failure;
    }
}
