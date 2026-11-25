/* SPDX-License-Identifier: Apache-2.0 */
package design.inherent.skyweavecompat.placement;

import com.mojang.logging.LogUtils;
import design.inherent.skyweavecompat.mixin.DeliverySearchAccess;
import design.inherent.skyweavecompat.mixin.DeliverySearchConfigAccess;
import design.inherent.skyweavecompat.mixin.StructureSearchCacheAccess;
import design.inherent.skyweavecompat.mixin.StructureSearchChunksAccess;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Retains the native locator continuation on its worker; only bounded status admission runs on a tick. */
public final class DeliverySearch {
    private static final TicketType<UUID> TICKET = TicketType.create("skyweave_delivery_search", UUID::compareTo);
    private static final Map<MinecraftServer, State> STATES = new ConcurrentHashMap<>();
    private static final ThreadLocal<Binding> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Dispatch> DISPATCH = new ThreadLocal<>();
    private static final AtomicReference<Dispatch> FLAG_OWNER = new AtomicReference<>();
    // Admission policies, not native producer maxima. No search radius or route-distance substitution.
    private static final long JOB_NANOS = TimeUnit.MINUTES.toNanos(10), REQUEST_NANOS = TimeUnit.SECONDS.toNanos(60);
    private static final int MAX_REQUESTS = 32768;
    private static final int MAX_CACHED_READS_PER_TICK = 64;
    private static final int MAX_STATUS_ADMISSIONS_PER_TICK = 4;
    private record Binding(Scope scope, boolean locating, Request request) {}
    private static final class State {
        volatile boolean closed;volatile Job active;final AtomicInteger nativeRequests=new AtomicInteger();
        int cachedTick=-1,cachedChecks;
        int admissionTick=-1,admissions;
        final Set<Dispatch> dispatches=ConcurrentHashMap.newKeySet();
        final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),task->{
            var thread=new Thread(task,"Skyweave delivery search");thread.setDaemon(true);return thread;
        });
        State(){worker.allowCoreThreadTimeOut(true);}
    }
    private static final class Dispatch {
        final State state;final ServerLevel level;final Object generator;final UUID recipient;
        final CompletableFuture<Void> result=new CompletableFuture<>();
        Dispatch(State state,ServerLevel level,UUID recipient){this.state=state;this.level=level;this.recipient=recipient;generator=level.getChunkSource().getGenerator();}
        boolean live(){return !state.closed && level.getServer().isRunning() && level.getServer().getLevel(level.dimension())==level && level.getChunkSource().getGenerator()==generator;}
        void retire(){result.completeExceptionally(new CancellationException("Delivery dispatch retired"));releaseFlag(this);}
    }
    private DeliverySearch() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event)->STATES.put(event.getServer(),new State()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Pre event)->{
            var state=STATES.get(event.getServer());if(state==null || state.active==null)return;
            var job=state.active;
            if(!job.valid() || System.nanoTime()>=job.deadline){job.cancel("expired or unavailable world");return;}
            var request=job.pending;
            if(request!=null && !request.admitted.isDone()) {
                admit(request);
            }
        });
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload event)->{
            if(event.getLevel() instanceof ServerLevel level) {
                var state=STATES.get(level.getServer());if(state!=null && state.active!=null && state.active.level==level)state.active.cancel("world unloaded");
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event)->{
            if(!(event.getEntity() instanceof ServerPlayer player))return;
            var state=STATES.get(player.server);
            if(state!=null && state.active!=null && player.getUUID().equals(state.active.recipient))state.active.cancel("requesting player disconnected");
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event)->{
            var state=STATES.get(event.getServer());if(state==null)return;state.closed=true;
            if(state.active!=null)state.active.cancel("server stopping");
            state.active=null;
            for(var dispatch:List.copyOf(state.dispatches))dispatch.retire();state.dispatches.clear();
            state.worker.shutdownNow();
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event)->STATES.remove(event.getServer()));
    }

    public static Scope enter(ServerLevel level,UUID recipient) {
        return new Scope(level,recipient,DeliveryChunks.applies(level));
    }
    /** Other native location modes retain their executor and implementation. */
    public static boolean dispatches(ServerLevel level) {
        if(!DeliveryChunks.applies(level))return false;
        String mode=DeliverySearchConfigAccess.skyweave$locationMode().get().name();
        return mode.equals("VILLAGE") || mode.equals("ANY_STRUCTURE");
    }
    public static CompletableFuture<Void> dispatch(ServerLevel level,UUID recipient,Runnable action) {
        var server=level.getServer();
        if(!server.isSameThread())throw new IllegalStateException("Delivery generation must be submitted on the server thread");
        var state=STATES.get(server);
        if(state==null || state.closed)throw new CancellationException("Delivery server is unavailable");
        var dispatch=new Dispatch(state,level,recipient);FLAG_OWNER.set(dispatch);
        if(state.active!=null || state.nativeRequests.get()!=0 || state.dispatches.size()>=2) {
            LogUtils.getLogger().info("Delivery generation deferred: a prior route or retired chunk request is still active");
            finishDispatch(dispatch);dispatch.result.completeExceptionally(new RejectedExecutionException("Prior delivery work is still settling; defer this attempt"));
            return dispatch.result;
        }
        state.dispatches.add(dispatch);
        try {
            state.worker.execute(()->{
                var previous=DISPATCH.get();DISPATCH.set(dispatch);
                try {
                    if(!dispatch.live())throw new CancellationException("Delivery world retired before dispatch");
                    action.run();dispatch.result.complete(null);
                }catch(Throwable error){
                    dispatch.result.completeExceptionally(error);
                    var active=state.active;
                    if(active!=null && active.scope.dispatch==dispatch)active.requestCancellation("delivery dispatch failed: "+error);
                    else if(dispatch.live())server.execute(()->{if(FLAG_OWNER.get()==dispatch)finishDispatch(dispatch);});
                    else releaseFlag(dispatch);
                }
                finally{state.dispatches.remove(dispatch);if(previous==null)DISPATCH.remove();else DISPATCH.set(previous);}
            });
        }catch(RejectedExecutionException error){state.dispatches.remove(dispatch);finishDispatch(dispatch);dispatch.result.completeExceptionally(error);}
        return dispatch.result;
    }
    public static void admittedNativeEntry(){FLAG_OWNER.set(null);}
    public static boolean nativeReset(AtomicBoolean flag,boolean value,boolean producer) {
        var binding=CURRENT.get();
        if(value || binding==null || !binding.scope.owned || binding.scope.dispatch==null || flag!=DeliverySearchAccess.skyweave$generating())return true;
        var job=binding.scope.job;
        if(producer && job!=null && job.endpointBound && job.valid())return false;
        return FLAG_OWNER.compareAndSet(binding.scope.dispatch,null);
    }
    private static void releaseFlag(Dispatch dispatch){if(FLAG_OWNER.compareAndSet(dispatch,null))DeliverySearchAccess.skyweave$generating().set(false);}
    private static void finishDispatch(Dispatch dispatch) {
        var previous=DISPATCH.get();DISPATCH.set(dispatch);
        try(var scope=enter(dispatch.level,dispatch.recipient)){DeliverySearchAccess.skyweave$finish(dispatch.level,dispatch.recipient);}
        finally{if(previous==null)DISPATCH.remove();else DISPATCH.set(previous);}
    }
    public static final class Scope implements AutoCloseable {
        private final ServerLevel level;
        private final UUID recipient;
        private final Binding previous;
        private final boolean owned;
        private final Dispatch dispatch;
        private volatile boolean closed;
        private volatile Job job;
        private Scope(ServerLevel level,UUID recipient,boolean owned) {
            this.level=level;this.recipient=recipient;this.owned=owned;previous=CURRENT.get();dispatch=DISPATCH.get();
            CURRENT.set(new Binding(this,false,null));
        }
        @Override public void close() {
            closed=true;
            if(previous==null)CURRENT.remove();else CURRENT.set(previous);
            if(!owned)return;
            var selected=job;if(selected==null)return;selected.producerDone=true;
            if(!selected.endpointBound || selected.closed)execute(selected,()->selected.finish("native search returned"));
        }
        private Job acquire() {
            if(job!=null){job.requireOpen();return job;}
            if(level.getServer().isSameThread())throw new IllegalStateException("Delivery structure discovery must remain on its worker");
            job=await(level.getServer().submit(()->{
                var state=STATES.get(level.getServer());
                if(closed || state==null || state.closed)throw new CancellationException("Delivery server is unavailable");
                if(state.active!=null)throw new RejectedExecutionException("Another delivery route is still active; defer this attempt");
                if(state.nativeRequests.get()!=0)throw new RejectedExecutionException("Retired delivery chunk work is still settling; defer this attempt");
                var created=new Job(this,state);job=created;state.active=created;return created;
            }),System.nanoTime()+TimeUnit.SECONDS.toNanos(5));
            return job;
        }
    }
    private static final class Job {
        final UUID id=UUID.randomUUID();
        final Scope scope;
        final State state;
        final ServerLevel level;
        final MinecraftServer server;
        final Object generator;
        final UUID recipient;
        final long began=System.nanoTime(),deadline=began+JOB_NANOS;
        final Set<CompletableFuture<?>> endpoints=Collections.newSetFromMap(new IdentityHashMap<>());
        volatile boolean closed,cancellationRequested,producerDone,endpointBound,nativeFinished;
        volatile Request pending;
        int requests;
        String outcome;
        Job(Scope scope,State state) {
            this.scope=scope;this.state=state;level=scope.level;server=level.getServer();recipient=scope.recipient;generator=level.getChunkSource().getGenerator();
        }
        boolean valid(){return !closed && !cancellationRequested && !state.closed && state.active==this && server.isRunning()
                && server.getLevel(level.dimension())==level && level.getChunkSource().getGenerator()==generator;}
        void requireOpen(){if(!valid() || System.nanoTime()>=deadline)throw new CancellationException("Delivery search expired, cancelled, or belongs to another world session");}
        void cancel(String why) {
            if(closed)return;cancellationRequested=true;closed=true;outcome=why;
            if(state.closed && scope.dispatch!=null)releaseFlag(scope.dispatch);
            var request=pending;if(request!=null){request.fail(new CancellationException(why));release(request);}
            synchronized(endpoints){for(var endpoint:List.copyOf(endpoints))endpoint.completeExceptionally(new CancellationException(why));endpoints.clear();}
            DeliveryChunks.finishSearch(level,this);
            if(producerDone)finish(why);
        }
        void requestCancellation(String why){cancellationRequested=true;execute(this,()->cancel(why));}
        void finish(String why) {
            if(state.active!=this)return;
            if(!closed){closed=true;outcome=why;var request=pending;if(request!=null){request.fail(new CancellationException(why));release(request);}}
            if(!producerDone)return;
            // Retire only this generation; late callbacks cannot release a successor's route lease.
            DeliveryChunks.finishSearch(level,this);
            if(state.active==this) {
                if(!nativeFinished && !state.closed)bound(scope,false,null,()->{DeliverySearchAccess.skyweave$finish(level,recipient);return null;});
                state.active=null;
            }
            synchronized(endpoints){endpoints.clear();}
            LogUtils.getLogger().info("Delivery search {} ended: {}; {} status requests, {} ms",id,outcome,requests,(System.nanoTime()-began)/1_000_000);
        }
    }
    private static final class Request {
        final Job job;final ChunkPos position;final ChunkStatus status;final boolean create;
        final CompletableFuture<Void> admitted=new CompletableFuture<>();
        final CompletableFuture<ChunkResult<ChunkAccess>> delivered=new CompletableFuture<>();
        final long deadline;
        boolean ticket;
        volatile boolean completed;
        volatile ChunkAccess cached;
        Request(Job job,int x,int z,ChunkStatus status,boolean create){this.job=job;position=new ChunkPos(x,z);this.status=status;this.create=create;deadline=Math.min(job.deadline,System.nanoTime()+REQUEST_NANOS);}
        void fail(Throwable error){admitted.completeExceptionally(error);delivered.completeExceptionally(error);}
    }

    public static <T> T locate(ServerLevel level,Supplier<T> original) {
        var binding=CURRENT.get();
        if(binding==null || binding.scope.level!=level || !binding.scope.owned)return original.get();
        binding.scope.acquire();return bound(binding.scope,true,null,original);
    }
    public static boolean intercept(ServerChunkCache cache) {
        var binding=CURRENT.get();return binding!=null && binding.locating && binding.scope.job!=null && cache.getLevel()==binding.scope.level;
    }
    public static ChunkAccess chunk(ServerChunkCache cache,int x,int z,ChunkStatus status,boolean create) {
        var scope=CURRENT.get().scope;var job=scope.job;job.requireOpen();
        if(job.server.isSameThread())throw new IllegalStateException("Delivery locate cannot wait for chunks on the server thread");
        if(status!=ChunkStatus.STRUCTURE_STARTS)throw new IllegalStateException("Unexpected native delivery locate status: "+status);
        var request=await(job.server.submit(()->{
            job.requireOpen();
            if(scope.closed)throw new CancellationException("Delivery producer already returned");
            if(job.pending!=null && job.pending.completed)release(job.pending);
            if(job.pending!=null)throw new IllegalStateException("Delivery already has an outstanding status request");
            if(job.requests>=MAX_REQUESTS)throw new RejectedExecutionException("Delivery status-request budget exhausted; defer this attempt");
            job.requests++;var value=new Request(job,x,z,status,create);job.pending=value;
            admit(value);
            return value;
        }),Math.min(job.deadline,System.nanoTime()+REQUEST_NANOS));
        long deadline=request.deadline;
        boolean completed=false;
        try {
            await(request.admitted,deadline);job.requireOpen();
            if(request.cached!=null){completed=true;return request.cached;}
            // This method's worker branch schedules registration without managedBlocking the server.
            var nativeFuture=bound(scope,false,request,()->nativeRequest(job,()->cache.getChunkFuture(x,z,status,create)));
            var weak=new WeakReference<>(request);
            nativeFuture.whenComplete((result,error)->{
                var target=weak.get();if(target==null)return;
                if(error!=null)target.delivered.completeExceptionally(error);
                else if(target.job.valid())target.delivered.complete(result);
                else target.delivered.completeExceptionally(new CancellationException("Retired delivery chunk result"));
            });
            var result=await(request.delivered,deadline);job.requireOpen();
            var chunk=result.orElse(null);
            if(chunk==null && create)throw new IllegalStateException("Delivery chunk request failed: "+result.getError());
            completed=true;
            return chunk;
        } catch(RuntimeException error) {job.requestCancellation(error.toString());throw error;}
        finally {
            // Keep at most the last successful status ticket until the next read or job retirement.
            // This preserves native holder readiness across consecutive cached locator reads.
            if(completed && job.valid())request.completed=true;
            else execute(job,()->release(request));
        }
    }
    private static boolean ready(Request request) {
        var job=request.job;
        if(!job.server.isSameThread())throw new IllegalStateException("Delivery cache inspection must run on the server thread");
        int tick=job.server.getTickCount();
        if(job.state.cachedTick!=tick){job.state.cachedTick=tick;job.state.cachedChecks=0;}
        if(job.state.cachedChecks>=MAX_CACHED_READS_PER_TICK)return false;
        job.state.cachedChecks++;
        var holder=((StructureSearchChunksAccess)job.level.getChunkSource().chunkMap).skyweave$searchUpdatingChunks().get(request.position.toLong());
        if(holder==null)return false;
        var chunk=holder.getChunkIfPresent(request.status);
        if(chunk==null)return false;
        request.cached=chunk;return true;
    }
    private static void admit(Request request) {
        var job=request.job;
        if(!job.server.isSameThread())throw new IllegalStateException("Delivery admission must run on the server thread");
        try {
            job.requireOpen();
            if(ready(request)){request.admitted.complete(null);return;}
            int tick=job.server.getTickCount();
            if(job.state.admissionTick!=tick){job.state.admissionTick=tick;job.state.admissions=0;}
            if(job.state.admissions>=MAX_STATUS_ADMISSIONS_PER_TICK)return;
            job.state.admissions++;
            if(request.create) {
                request.ticket=true;
                ((StructureSearchCacheAccess)job.level.getChunkSource()).skyweave$searchTickets()
                        .addTicket(TICKET,request.position,ChunkLevel.byStatus(request.status),job.id);
            }
            request.admitted.complete(null);
        }catch(RuntimeException error){job.cancel("status admission failed: "+error);}
    }
    public static <T> Supplier<T> registration(Supplier<T> original) {
        var binding=CURRENT.get();if(binding==null || binding.scope.job==null)return original;
        var weakJob=new WeakReference<>(binding.scope.job);var weakRequest=new WeakReference<>(binding.request);
        return ()->{var job=weakJob.get();if(job==null)throw new CancellationException("Retired delivery registration");
            job.requireOpen();var request=weakRequest.get();
            if(request!=null && System.nanoTime()>=request.deadline)throw new CancellationException("Delivery registration deadline exceeded");
            return bound(job.scope,false,request,original);};
    }
    public static Object token(){var binding=CURRENT.get();return binding==null?null:binding.scope.job;}
    public static void requireToken(Object token) {if(token!=null)((Job)token).requireOpen();}
    public static <T> CompletableFuture<T> nativeRequest(Object token,Supplier<CompletableFuture<T>> request) {
        if(token==null)return request.get();var job=(Job)token;job.requireOpen();var outstanding=job.state.nativeRequests;
        int count;
        do {count=outstanding.get();if(count>=19)throw new RejectedExecutionException("Delivery native request admission is full");}
        while(!outstanding.compareAndSet(count,count+1));
        try {
            var future=request.get();future.whenComplete((result,error)->outstanding.decrementAndGet());return future;
        }catch(RuntimeException | Error error){outstanding.decrementAndGet();throw error;}
    }
    public static <T> T withToken(Object token,Supplier<T> action) {
        if(token==null)return action.get();var job=(Job)token;job.requireOpen();return bound(job.scope,false,null,action);
    }
    public static <T> CompletableFuture<T> endpoint(CompletableFuture<T> original) {
        var token=token();if(token==null)return original;var job=(Job)token;job.requireOpen();
        var proxy=new CompletableFuture<T>();
        synchronized(job.endpoints){job.endpoints.add(proxy);}
        var weak=new WeakReference<>(proxy);var weakJob=new WeakReference<>(job);
        original.whenComplete((value,error)->{
            var target=weak.get();if(target==null)return;
            var owner=weakJob.get();
            if(error!=null){if(owner!=null)owner.requestCancellation(error.toString());target.completeExceptionally(error);}
            else if(owner!=null && owner.valid())target.complete(value);
            else target.completeExceptionally(new CancellationException("Retired delivery endpoint"));
            if(owner!=null)synchronized(owner.endpoints){owner.endpoints.remove(target);}
        });
        return proxy;
    }
    public static <A,B,R> BiFunction<A,B,R> continuation(BiFunction<A,B,R> original) {
        var binding=CURRENT.get();if(binding==null || binding.scope.job==null)return original;
        var scope=binding.scope;scope.job.endpointBound=true;
        return (a,b)->{scope.job.requireOpen();return bound(scope,false,null,()->original.apply(a,b));};
    }
    public static <A,R> Function<A,R> failure(Function<A,R> original) {
        var binding=CURRENT.get();if(binding==null || binding.scope.job==null)return original;
        var scope=binding.scope;
        return value->scope.job.valid()?bound(scope,false,null,()->original.apply(value)):null;
    }
    public static Runnable publication(Runnable original) {
        var binding=CURRENT.get();if(binding==null || binding.scope.job==null)return original;
        var scope=binding.scope;var job=scope.job;
        return ()->{if(!job.valid())return;boolean normal=false;try{bound(scope,false,null,()->{original.run();return null;});normal=true;}
            finally{job.nativeFinished=normal;job.finish("native finalization returned");}};
    }
    private static <T> T bound(Scope scope,boolean locating,Request request,Supplier<T> action) {
        var previous=CURRENT.get();CURRENT.set(new Binding(scope,locating,request));
        try{return action.get();}finally{if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
    }
    private static <T> T await(CompletableFuture<T> future,long deadline) {
        try{return future.get(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);}
        catch(InterruptedException error){Thread.currentThread().interrupt();throw new CancellationException("Delivery search interrupted");}
        catch(TimeoutException error){throw new CompletionException("Delivery request deadline exceeded",error);}
        catch(ExecutionException error){throw new CompletionException(error.getCause());}
    }
    private static void execute(Job job,Runnable action){if(job.server.isSameThread())action.run();else if(!job.state.closed)job.server.execute(action);}
    private static void release(Request request) {
        var job=request.job;
        if(request.ticket) {
            ((StructureSearchCacheAccess)job.level.getChunkSource()).skyweave$searchTickets()
                    .removeTicket(TICKET,request.position,ChunkLevel.byStatus(request.status),job.id);request.ticket=false;
        }
        if(job.pending==request)job.pending=null;
    }
}
