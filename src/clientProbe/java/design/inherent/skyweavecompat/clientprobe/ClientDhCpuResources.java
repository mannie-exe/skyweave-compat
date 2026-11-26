/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.JsonObject;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.util.objects.pooling.PhantomArrayList.PhantomArrayListCheckout;
import java.lang.ref.WeakReference;
import java.util.*;

/** Trace only native byte-buffer loans made inside an admitted render-section worker. */
final class ClientDhCpuResources {
    private static final class Loan {
        final long sequence;
        final int epoch;
        final String pool;
        final WeakReference<PhantomArrayListCheckout> checkout;
        boolean returned;
        Loan(long sequence, int epoch, String pool, PhantomArrayListCheckout checkout) {
            this.sequence = sequence; this.epoch = epoch; this.pool = pool;
            this.checkout = new WeakReference<>(checkout);
        }
    }
    private static final class Frame {
        final long id;
        final int epoch;
        boolean ended;
        Frame(long id, int epoch) { this.id = id; this.epoch = epoch; }
    }
    private final ThreadLocal<ArrayDeque<Frame>> current = ThreadLocal.withInitial(ArrayDeque::new);
    private final Map<Long, Frame> frames = new HashMap<>();
    private final Map<Integer, List<Loan>> outstandingByIdentity = new HashMap<>();
    private final List<Loan> loans = new ArrayList<>();
    private long sequence;
    private int faults;

    synchronized long enter(int epoch) {
        var frame = new Frame(++sequence, epoch); frames.put(frame.id, frame); current.get().push(frame); return frame.id;
    }
    synchronized void leave(long id) {
        var frame = current.get().pop();
        if (frame.id != id) throw new AssertionError("Native CPU observation frame mismatch");
        frame.ended = true;
        if (current.get().isEmpty()) current.remove();
    }
    synchronized void borrowed(String pool, PhantomArrayListCheckout checkout) {
        var frame = current.get().peek(); if (frame == null || frame.epoch <= 0) return;
        var loan = new Loan(++sequence, frame.epoch, pool, checkout); loans.add(loan);
        outstandingByIdentity.computeIfAbsent(System.identityHashCode(checkout), ignored -> new ArrayList<>()).add(loan);
    }
    synchronized Object returning(PhantomArrayListCheckout checkout) {
        // Snapshot before native publication into its pool. A later checkout can reuse the
        // same object before returnCheckout itself exits; that new loan is not this return.
        var matching = new ArrayList<Loan>();
        for (var loan : outstandingByIdentity.getOrDefault(System.identityHashCode(checkout), List.of()))
            if (!loan.returned && loan.checkout.get() == checkout) matching.add(loan);
        return matching;
    }
    @SuppressWarnings("unchecked") synchronized void returned(Object observation) {
        for (var loan : (List<Loan>) observation) loan.returned = true;
        outstandingByIdentity.values().forEach(list -> list.removeIf(loan -> loan.returned));
        outstandingByIdentity.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
    synchronized void afterActualJoin(Object value) {
        if (!Boolean.getBoolean("skyweave_compat.probe.dhCpuFault") || faults != 0 || current.get().isEmpty()
                || current.get().peek().epoch <= 0
                || !(value instanceof LodBufferContainer buffer) || !buffer.buffersUploaded) return;
        long vertices = 0;
        for (var vbo : buffer.vboOpaqueWrappers) if (vbo != null) vertices += vbo.getVertexCount();
        for (var vbo : buffer.vboTransparentWrappers) if (vbo != null) vertices += vbo.getVertexCount();
        if (vertices == 0) return;
        faults++;
        // The real native future has already completed its upload. This controlled failure
        // exercises the caller's exception edge without abandoning work still using bytes.
        throw new IllegalStateException("Skyweave disposable CPU-checkout fault after actual native upload completion");
    }
    synchronized boolean retired(int epoch) {
        boolean observed = false;
        for (var frame : frames.values()) if (frame.epoch == epoch && !frame.ended) return false;
        for (var loan : loans) if (loan.epoch == epoch) { observed = true; if (!loan.returned) return false; }
        return observed;
    }
    synchronized JsonObject snapshot() {
        var result = new JsonObject(); var pools = new JsonObject();
        for (var loan : loans) {
            String key = loan.epoch + ":" + loan.pool;
            var row = pools.has(key) ? pools.getAsJsonObject(key) : new JsonObject();
            int borrowed = row.has("borrowed") ? row.get("borrowed").getAsInt() : 0;
            int returned = row.has("returned") ? row.get("returned").getAsInt() : 0;
            row.addProperty("borrowed", borrowed + 1); row.addProperty("returned", returned + (loan.returned ? 1 : 0));
            row.addProperty("outstanding", borrowed + 1 - returned - (loan.returned ? 1 : 0)); pools.add(key, row);
        }
        long active = frames.values().stream().filter(frame -> !frame.ended).count();
        result.add("poolsByEpoch", pools); result.addProperty("activeNativeFrames", active);
        result.addProperty("faultsAfterActualUploadCompletion", faults);
        result.addProperty("scope", "Actual byte-buffer checkouts inside admitted native render workers and successful native returnCheckout calls; weak identity/generation observation only, no returns/frees or total native/heap claim");
        return result;
    }
}
