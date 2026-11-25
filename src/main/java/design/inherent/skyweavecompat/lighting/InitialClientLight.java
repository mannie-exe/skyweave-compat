/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.lighting;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LevelLightEngine;

/** Keep changed-position checks after their own chunk's initial light-data commit. */
public final class InitialClientLight {
    private static final ThreadLocal<Frame> INCOMING=new ThreadLocal<>();
    private InitialClientLight() {}

    public interface EngineOwner {
        Gate skyweave$initialLight(ClientLevel level,ClientPacketListener listener);
        Gate skyweave$existingInitialLight();
    }

    public static void incoming(ClientPacketListener listener,ClientLevel level,int x,int z,Runnable original) {
        var mc=Minecraft.getInstance();
        if(!mc.isSameThread() || level==null || listener.getLevel()!=level || !listener.getConnection().isConnected()){original.run();return;}
        var previous=INCOMING.get();var frame=new Frame(listener,level,x,z);INCOMING.set(frame);
        boolean complete=false;
        try {original.run();complete=true;}
        finally {
            if(previous==null)INCOMING.remove();else INCOMING.set(previous);
            if(frame.state!=null && !frame.callbackQueued) {
                if(complete && frame.current())frame.state.gate.releaseNative(frame.state);
                else frame.state.gate.cancel(frame.state);
            }
        }
    }

    public static void decoded(LevelChunk chunk) {
        var frame=INCOMING.get();
        if(frame==null || chunk.getLevel()!=frame.level || chunk.getPos().x!=frame.x || chunk.getPos().z!=frame.z)return;
        if(frame.state!=null)return;
        var gate=((EngineOwner)frame.level.getLightEngine()).skyweave$initialLight(frame.level,frame.listener);
        frame.gate=gate;if(gate!=null)frame.state=gate.begin(chunk);
    }

    public static Runnable queued(ClientLevel level,Runnable original) {
        var frame=INCOMING.get();
        if(frame==null || frame.callbackQueued)return original;
        frame.callbackQueued=true;var state=frame.state;
        if(frame.level!=level || !frame.current()) {
            if(state!=null)state.gate.cancel(state);return ()->{};
        }
        boolean admitted=state!=null && state.gate.current(state);
        if(state!=null && !admitted)state.gate.releaseNative(state);
        var once=new AtomicBoolean();
        return ()->{
            if(!once.compareAndSet(false,true))return;
            var mc=Minecraft.getInstance();
            if(!mc.isSameThread()) {
                try {original.run();} finally {if(admitted)mc.execute(()->state.gate.cancel(state));}
                return;
            }
            if(!frame.current()){if(admitted)state.gate.cancel(state);return;}
            if(!admitted){original.run();return;}
            if(!state.gate.loaded(state)){state.gate.cancel(state);return;}
            if(!state.gate.current(state)){original.run();return;}
            boolean complete=false;
            try {original.run();complete=true;}
            finally {if(complete)state.gate.applied(state);else state.gate.cancel(state);}
        };
    }

    public static void forget(ClientLevel level,LevelChunk chunk) {
        var mc=Minecraft.getInstance();if(!mc.isSameThread()){mc.execute(()->forget(level,chunk));return;}
        var gate=((EngineOwner)level.getLightEngine()).skyweave$existingInitialLight();
        if(gate!=null){var state=gate.states.get(chunk);if(state!=null)gate.cancel(state);}
    }

    public static void retire(ClientLevel level) {
        var mc=Minecraft.getInstance();if(!mc.isSameThread()){mc.execute(()->retire(level));return;}
        var gate=((EngineOwner)level.getLightEngine()).skyweave$existingInitialLight();if(gate!=null)gate.retire();
    }

    private static final class Frame {
        final ClientPacketListener listener;
        final ClientLevel level;
        final int x,z;
        State state;
        Gate gate;
        boolean callbackQueued;
        Frame(ClientPacketListener listener,ClientLevel level,int x,int z){this.listener=listener;this.level=level;this.x=x;this.z=z;}
        boolean current(){return listener.getLevel()==level && listener.getConnection().isConnected() && (gate==null || !gate.retired);}
    }

    private static final class State {
        final Gate gate;
        final LevelChunk chunk;
        int pending;
        boolean readyQueued,retired;
        LongOpenHashSet checks;
        State(Gate gate,LevelChunk chunk){this.gate=gate;this.chunk=chunk;}
        void discard(){retired=true;checks=null;}
    }

    /** One native client engine owns this finite set of pending packet/chunk identities. */
    public static final class Gate {
        private final ClientLevel level;
        private final LevelLightEngine engine;
        private final ClientPacketListener listener;
        private final IdentityHashMap<LevelChunk,State> states=new IdentityHashMap<>();
        private final ArrayDeque<State> ready=new ArrayDeque<>();
        private boolean retired;

        public Gate(ClientLevel level,LevelLightEngine engine,ClientPacketListener listener){this.level=level;this.engine=engine;this.listener=listener;}
        public boolean belongs(ClientPacketListener listener){return this.listener==listener;}
        private State begin(LevelChunk chunk) {
            if(retired)return null;
            var state=states.computeIfAbsent(chunk,key->new State(this,key));state.pending++;return state;
        }
        private boolean owner(){return Minecraft.getInstance().isSameThread() && listener.getLevel()==level && listener.getConnection().isConnected() && !retired;}
        private boolean current(State state) {
            return !state.retired && states.get(state.chunk)==state && loaded(state);
        }
        private boolean loaded(State state) {
            return owner() && level.getChunkSource().getChunk(state.chunk.getPos().x,state.chunk.getPos().z,ChunkStatus.FULL,false)==state.chunk;
        }
        public boolean defer(BlockPos position) {
            if(states.isEmpty() || !owner())return false;
            State state=null;var frame=INCOMING.get();
            if(frame!=null && frame.level==level && frame.state!=null && !frame.state.retired && frame.state.gate==this
                    && frame.x==(position.getX()>>4) && frame.z==(position.getZ()>>4))state=frame.state;
            if(state==null){var chunk=level.getChunkSource().getChunk(position.getX()>>4,position.getZ()>>4,ChunkStatus.FULL,false);state=states.get(chunk);}
            if(state==null || state.retired)return false;
            if(state.checks==null)state.checks=new LongOpenHashSet();state.checks.add(position.asLong());return true;
        }
        private void applied(State state) {
            if(state.retired)return;
            if(state.pending<=0)throw new IllegalStateException("Unbalanced initial client light callback");
            if(--state.pending==0 && !state.readyQueued){state.readyQueued=true;ready.addLast(state);}
        }
        private void cancel(State state) {
            if(states.get(state.chunk)==state)states.remove(state.chunk);state.discard();
        }
        private void releaseNative(State state) {
            var checks=state.checks;cancel(state);
            if(checks!=null){var positions=checks.iterator();while(positions.hasNext())engine.checkBlock(BlockPos.of(positions.nextLong()));}
        }
        public boolean hasReady(){return owner() && !ready.isEmpty();}
        public void committed() {
            if(!Minecraft.getInstance().isSameThread())return;
            if(!owner()){retire();return;}
            int count=ready.size();
            while(count-->0) {
                var state=ready.removeFirst();state.readyQueued=false;
                if(state.retired)continue;
                if(state.pending!=0)continue;
                if(!current(state)){cancel(state);continue;}
                states.remove(state.chunk);state.retired=true;var checks=state.checks;state.checks=null;
                if(checks!=null){var positions=checks.iterator();while(positions.hasNext())engine.checkBlock(BlockPos.of(positions.nextLong()));}
            }
        }
        public void retire() {
            retired=true;for(var state:states.values())state.discard();states.clear();ready.clear();
        }
    }
}
