# Delivery generation

`delivery.structure_search` is an experimental, default-off adaptation for Aeronautics Delivery Quests 1.1.1. It requires the active island preset and `delivery.chunk_loading`. It separates structure-status admission from the native village locator's worker continuation. Random locations and foreign generators retain their native dispatch. Existing distance and village landing adaptations remain separate.

## Native work and ownership

The native structure radius, retry order, RNG, templates, weights, rewards, route validation, and landing predicates remain authoritative. The adapter does not turn the structure search radius into a block distance or substitute sampled terrain for actual native starts. One owned worker and one queued root task bound dispatch; one structure-status request can be pending per active job. Loaded native holders are inspected only on the server thread, with at most 64 ready-holder checks per tick. Cache misses enter native asynchronous status registration after tick admission; they do not wait synchronously for generation on the server thread.

The job policies are 32,768 status requests, ten minutes overall, and sixty seconds per request. These are explicit admission limits, not native producer maxima or a bound on all downstream generation work. Refusal defers an attempt without shortening the selected route. The 19 outstanding native requests cover the existing eighteen endpoint requests and one settling structure request. Cancelling a private waiter does not cancel the native future or remove its reservation before that future actually settles.

Search tickets and endpoint tickets have separate owners. Record ownership before calling a native ticket mutation, so an exception after insertion can still release the ticket. Never remove unrelated tickets. Endpoint neighborhoods remain held until native registration returns or the attempt fails. World unload, requesting-player logout, server shutdown, failure, and expiry retire the corresponding job; late continuations must not publish into a replacement job or reset its generating flag. The adapter closes its worker on server shutdown.

## Qualification boundary

The current engineering candidate passes native public village-route generation, actual quest-file persistence and saved-world reopening, ticket insertion fault controls, and a delayed-result cancellation/successor control. The latter holds a fixture-only proxy, leaves the original native future untouched, and checks admission while retired work is still settling. A minimum current C2ME/NoTick cohort also creates and saves a real quest. These checks do not establish complete pack delivery gameplay, ordinary full-pack performance, or default-on readiness.

Qualify actual production client and dedicated-server bytecode independently. Include useful village and structure routes, sparse/no-result attempts, existing native random mode, foreign generators, explicit off, optional absence, and exact unsupported-version refusal. Verify saved offers, native acceptance/cargo/payment, disconnect/reconnect, expiry, late callbacks, and shutdown without weakening their authority or resource ownership. A successful offer is not a completed delivery. Record host pressure and profiling overhead; use comparable paired/interleaved runs before timing claims.

When updating ADQ or the loader, inspect both calling-thread branches of `ServerChunkCache.getChunkFuture`, the native root and finalization lambdas, `isGenerating` ownership, ticket levels, saved-file ownership, and all required injection counts. Retire the adapter only when the replacement native implementation preserves these behaviors. Do not broaden version guards or injection allowances merely to load a new binary.
