# Moving lights

The optional `lighting.sodium` client adaptation connects Sable Dynamic Lights (SDL) 2.0.1 to Sodium 0.8.13 on Minecraft 1.21.1. Create 6.0.10 and LambDynamicLights 4.8.11+1.21.1 are required by the selected integration. Sable, Iris, Sky Archipelago and the Skyweave modpack are not prerequisites for this hook. [Configuration](../configuration.md#adaptation-switches) owns activation.

## Ownership and hook

SDL owns scanning, source registration, Create/Sable transforms and virtual client light propagation. Lamb owns its dynamic-light lookup and rebuild scheduler. Skyweave: Compatibility Patches keeps dynamic illumination client-local and preserves ordinary server lighting. It changes no spawning rules, block states, mass or terrain generation.

SDL's released Sodium mixin targets two obsolete class namespaces and an instance `getLightmap(BlockPos)` signature. The selected Sodium uses `net.caffeinemc.mods.sodium.client.model.light.data.LightDataAccess`, whose `getLightmap(int)` is a static decoder without a position. Both old SDL targets are absent, so they cannot compete with this hook in the supported combination. No mixin-cancellation dependency is needed.

`SodiumLightingMixin` injects at `compute(int,int,int)` return, before Sodium caches the result. It uses the instance's protected level field and SDL's `WorldPosResolver.getCombinedWorldLight` API. It masks only `packBL(15)` and packs the replacement through Sodium's helper. Sky light, luminance, AO, emissive, opacity and face flags remain unchanged. It performs no chunk requests or reflective field search. Lookup runs per cache computation, not per vertex. SDL's own resolver still owns any cached reflective Create lookup.

Sodium render views need not be the client level. Passing the actual view preserves SDL's existing context contract; the hook does not pretend a chunk snapshot is a Sable plot or reapply a ship transform. SDL routes contained Create sources through its separate virtual Sable bridge. Plot-space source coordinates alone do not establish a broken world projection.

## World and source ownership

`lighting.lifecycle` is independent of Sodium and Sable. It needs SDL, Lamb and Create. SDL's unload and logout callbacks clear virtual positions without removing its tracked Create contraptions. `onTick` can then tick those owners in another client level and register their sources again. The unadapted native scene retained the Overworld bearing source throughout its Nether visit.

`CreateLightSourcesAccessor` and `LightingLifecycleMixin` copy SDL's active contraption list and call its existing `onContraptionEntityLeave` for each owner before client-level unload/logout clearing. That removes active and pending entries and unregisters/marks their sources through SDL's holder. The operation is idempotent; server-level unloads do not trigger it. It also calls SDL's own Sable holder `clear` and resets that handler's cached level and scan timestamp through `SableLightStateAccessor`. Those classes belong to SDL and require no Sable API to clear, so this does not add a Sable dependency. It releases SDL's level reference even when the player remains at the title screen. There is no new source registry or per-tick sweep.

Physical-client mixins also transform the integrated server's engine classes. SDL's virtual-emission and dynamic-light callbacks lack an owning-level check. In the native control, a client-only virtual source of 15 caused a server light sample of 14 after an explicit server `checkBlock`, despite air at the source. This is an authoritative lighting defect, not a renderer approximation.

`VirtualEmissionSideMixin` and `DynamicLightSideMixin` wrap the complete native `getEmission` and `getLightValue` methods, including SDL's injections. For an owning `LevelAccessor` whose `isClientSide()` is false, they enter a thread-local server-query scope. `ScopedLightQueryMixin` then returns zero for SDL's virtual emission and Lamb-delegate contribution only within that scope. The original engine still computes real block emission and stored light. `finally` restores nested scopes; no lock, source-state mutation or thread-name heuristic is used. Client and other render views retain the upstream calls. `LightEngineOwnerAccessor` reads the actual engine's `chunkSource`; it does not infer ownership from the physical side or current screen.

`ServerLightViewMixin` separately preserves the incoming value when SDL's combined lookup is called with a server `LevelAccessor`, including world-generation views. This prevents its interface brightness hook from adding client illumination back after an engine query has completed. It leaves client render snapshots eligible.

Retire the lifecycle hook when released SDL clears its Create owners. Retire the scope, engine wrappers and query guard together when SDL's engine callbacks enforce logical-side ownership; retire the view guard when its resolver rejects server views. Recheck active/pending lists, Lamb world reset, entity leave callbacks, virtual propagation and the native/server controls before updating. These fixes are not evidence that the upstream reported hang has the same cause.

## Artifacts and updates

| Artifact               | Modrinth version | SHA-256                                                            |
| ---------------------- | ---------------- | ------------------------------------------------------------------ |
| SDL 2.0.1              | `vjb0hAWB`       | `65893d1623b36670de0438752a7c1e7c023ce1226ead08beea8ee4d4e8938f7b` |
| Lamb 4.8.11+1.21.1     | `ksaGCvSu`       | `913b0358b1031828f1e060ea0ee361232dc2a1c82464f488a0a920739ce7c763` |
| Sodium 0.8.13 NeoForge | `uMOpc5uV`       | `575d187b15328d7bddd8a9b1bee64333c22c14735f5780ec49eac0cd5503c08d` |

Sodium's tested nested mod hash is `d71acbffafd3d177add7624710114d8570cdd70d5553a2b5c8ab371852c59ce3`. Gradle verifies the SDL compile artifact and both Sodium archive layers. Upstream JARs and inspection sources are not bundled. The adapter calls SDL APIs; it does not incorporate SDL's Apache-licensed implementation.

Before updating, inspect the released SDL mixin, Sodium cache computation and packing helpers, and Lamb lookup/rebuild behavior. Recheck method descriptors, staticness, view lifetime and source cleanup. Retire this hook when released SDL supports the selected API; do not stack a second lookup blindly. Update exact version guards, build/test pins, configuration documentation and pack dependencies together. Review Sable Companion and Lamb's nested native metadata too.

Lamb's universal JAR contains a native NeoForge runtime and supporting libraries; do not add Fabric API merely from catalog metadata. The selected 1.21 Lamb branch is end-of-life. SDL bundles Sable Companion 1.6.0; its Sable fallback is intended to remain inactive when Sable is absent.

## Configuration and limits

Keep Lamb's normal lighting and rebuild settings under Lamb's configuration. When shaders already light a held item, disable Lamb's first-person source if it produces duplicate illumination. SDL also adds a held BlockItem fallback independently; Lamb's self switch alone does not establish that every held-light contribution is disabled. This hook does not automatically switch preferences with shaders.

Lamb's radial dynamic lighting is not a general occlusion solution. SDL additionally projects virtual sources between main-world and plot coordinates; walls represented in one space are not necessarily present in another. A Sodium API repair does not establish sealed-cabin lighting. Do not advertise this adapter as fixing every [wall-leak report](https://github.com/fr0nick/Sable-Dynamic-Lights/issues/4).

The ground-level native control registered a virtual source of 15, observed 14 beside it and 0 after removal. Upstream diff-before-map-commit ordering therefore was not changed on speculation. Neither that control nor a successful launch proves that all [residual-light](https://github.com/fr0nick/Sable-Dynamic-Lights/issues/6) or [reported-hang](https://github.com/fr0nick/Sable-Dynamic-Lights/issues/7) cases are solved. Obtain a source-owner trace or thread dump for those failures.

DH 3.3.3's NeoForge chunk wrapper stores DH lighting separately in `ChunkLightStorage`, initialized independently of the client light engine. Its block-state wrapper reads state emission; SDL patches client light-engine queries, not state emission. This inspected path does not justify a DH hook or cache reset. It is not a visual persistence test of every DH source provider.

## Isolated verification

Build the test-only probe with `./gradlew --no-daemon clientProbeJar`. In an owned, disposable client, set `-Dskyweave_compat.probe.lighting=true`. It opens only `saves/lighting-review`, creates a night flat scene, and writes `lighting-control.json` and `lighting-scene.json` to that fixture's game directory. Use `-Dskyweave_compat.probe.lighting.controlOnly=true` for a client without Sable or Aeronautics. Do not put the probe or its flags in a player's instance.

The full scene uses a static control, a supported rotating Create lantern, a glued Sable cabin and a separate bearing on its extended deck. Physics is paused only in the fixture; scripted rigid-body teleports vary position and rotation. The probe removes the sublevel, changes dimension, travels away and returns. Frame intervals are measured after warmup in the same scripted motion window. They include the rest of the client and the configured frame cap; they are not GPU timings or an isolated lighting benchmark.

Use `/compatprobe_stop` for this isolated integrated server, wait for saving, then close the test window. [Verification](verification.md) records the actual tested artifact, combinations, observed results and limitations. No personal instance or saved LOD database is part of these checks.

## Pack integration

Install SDL and Lamb on clients only; the selected Create and Sodium dependencies are already listed above. Keep `lighting.sodium` and `lighting.lifecycle` enabled. Do not add Contraption Lights or a second Sodium Dynamic Lights engine. Existing Sable, Iris/Veil, DH and resource adapters keep their current owners and settings. Use the verified Skyweave: Compatibility Patches artifact identified in the verification record when preparing a release; these instructions do not install it into an existing profile.

This adaptation is suitable for further playtesting with the stated approximation limits. It does not turn Lamb's radial lights into an occlusion-correct renderer. There is no automatic shader/held-light preference switch. A release promising sealed-cabin lighting or a fix for issue #7 needs further work and evidence.

## Upstream report candidates

Against SDL 2.0.1, source revision `4af5fc44b83d7a0e79b9c8e6d719b216d3649a36`:

- Its Sodium mixin has obsolete target namespaces and an incompatible lightmap signature. Report the current cache API and the packed-field preservation check.
- A virtual client source can enter an integrated server's light calculation. The isolated air-source control emits 15 on the client; explicitly checking that position on the server produces adjacent light 14 without the ownership repair and 0 with it. This does not require Sable or shaders. Ordinary block-light updates can reach the same native method; actual mob spawning or saved-world damage was not tested.
- Create source owners survive a dimension change and re-register in the new level. The unadapted Overworld bearing remains in the source set during the Nether visit; the repaired scene has no such source and correctly registers it on return. Include SDL's missing Sable-holder/cache disposal in the cleanup review, separately from claims about ghost illumination or total world-memory reclamation.

Use the retained dependency manifest, native reports and focused fixture for a report or PR. No report was submitted automatically. The wall/ghost/hang issue links above describe different symptoms; do not close them from these checks alone.
