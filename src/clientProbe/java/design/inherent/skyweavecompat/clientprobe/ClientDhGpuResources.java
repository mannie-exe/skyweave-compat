/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.clientprobe;

import com.google.gson.JsonObject;
import com.seibel.distanthorizons.common.render.openGl.glObject.buffer.GLBuffer;
import com.seibel.distanthorizons.core.wrapperInterfaces.render.objects.IVertexBufferWrapper;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL15;

/** Observe native deletion of actual uploaded VBO generations, without owning their cleanup. */
final class ClientDhGpuResources {
    private static final class Generation {
        final long sequence;
        final int epoch, id;
        final WeakReference<GLBuffer> wrapper;
        boolean deleted, deletionVerified;
        Generation(long sequence, int epoch, GLBuffer wrapper) {
            this.sequence = sequence; this.epoch = epoch; this.id = wrapper.getId();
            this.wrapper = new WeakReference<>(wrapper);
        }
    }
    private record WorldRefs(WeakReference<Object> client, WeakReference<Object> dh) {}
    private final Map<Integer, Generation> liveById = new HashMap<>();
    private final List<Generation> generations = new ArrayList<>();
    private final Map<Integer, WorldRefs> worlds = new HashMap<>();
    private long nextSequence;
    private int reusedNames, alreadyClosedWrappers;

    synchronized void world(int epoch, Object client, Object dh) {
        if (client != null && dh != null)
            worlds.putIfAbsent(epoch, new WorldRefs(new WeakReference<>(client), new WeakReference<>(dh)));
    }

    synchronized void uploaded(int epoch, IVertexBufferWrapper[] buffers) {
        if (buffers == null) return;
        for (var buffer : buffers) {
            if (buffer == null || buffer.getVertexCount() == 0) continue;
            if (!(buffer instanceof GLBuffer gl)) throw new AssertionError("Unqualified native VBO backend: " + buffer.getClass());
            int id = gl.getId();
            if (id == 0) { alreadyClosedWrappers++; continue; }
            var previous = liveById.get(id);
            if (previous != null) {
                if (previous.wrapper.get() != gl) throw new AssertionError("Native GL name reused before observed deletion: " + id);
                if (previous.epoch != epoch) throw new AssertionError("Native uploaded VBO crossed world epochs");
                continue;
            }
            for (var old : generations) if (old.id == id) { reusedNames++; break; }
            var generation = new Generation(++nextSequence, epoch, gl);
            generations.add(generation); liveById.put(id, generation);
        }
    }

    synchronized void nativeDeleted(int id) {
        var generation = liveById.remove(id);
        if (generation == null) return; // Shared IBOs and unrelated native resources are not this witness's custody.
        generation.deleted = true;
        var mc = Minecraft.getInstance();
        boolean owner = mc.isSameThread() && mc.getWindow() != null
                && GLFW.glfwGetCurrentContext() == mc.getWindow().getWindow();
        if (!owner) throw new AssertionError("Cannot verify native VBO deletion outside its actual main context");
        // Check synchronously after native deletion, before any later allocation can reuse this name.
        if (GL15.glIsBuffer(id)) throw new AssertionError("Native deletion left an observed GL buffer name live: " + id);
        generation.deletionVerified = true;
    }

    synchronized boolean retired(int epoch) {
        boolean observed = false;
        for (var generation : generations) if (generation.epoch == epoch) {
            observed = true;
            if (!generation.deleted || !generation.deletionVerified) return false;
        }
        return observed;
    }

    synchronized JsonObject snapshot() {
        var result = new JsonObject(); var epochs = new JsonObject();
        for (var entry : worlds.entrySet()) {
            int observed = 0, deleted = 0, verified = 0;
            for (var generation : generations) if (generation.epoch == entry.getKey()) {
                observed++; if (generation.deleted) deleted++; if (generation.deletionVerified) verified++;
            }
            var row = new JsonObject(); row.addProperty("observedUploadedVboGenerations", observed);
            row.addProperty("nativeDeleted", deleted); row.addProperty("deletionVerifiedInOwnerContext", verified);
            row.addProperty("clientLevelCollected", entry.getValue().client().get() == null);
            row.addProperty("dhWorldCollected", entry.getValue().dh().get() == null);
            epochs.add(entry.getKey().toString(), row);
        }
        result.add("epochs", epochs); result.addProperty("reusedNamesAfterNativeDeletion", reusedNames);
        result.addProperty("alreadyClosedWrappersAtUploadObservation", alreadyClosedWrappers);
        result.addProperty("scope", "Observed uploaded native VBOs only; generation-aware synchronous native deletion checks, weak world observations; no forced cleanup, shared IBO retirement, pool or total GPU-memory claim");
        return result;
    }
}
