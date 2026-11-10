# Adapter maintenance

[Configuration](../configuration.md#adaptation-switches) owns feature keys,
defaults, supported versions, and prerequisites. `FeatureSelection` is the code
owner of those boundaries. Loader metadata deliberately accepts optional versions;
only active adapters enforce supported versions. Build-time SHA-256 checks bind
implementation tests to the exact released artifacts.

| Adapter family | Implementation and interactions | Update and retirement checks |
| --- | --- | --- |
| Island preset | `TerrainIntegration`, terrain mixins, terrain built-in pack; dependent generation switches require `terrain.preset` | Review Sky Archipelago generator/field contracts, codecs, dimensions, and saved identity. Run native generation/reopen checks. Never substitute a saved generator. |
| Cluster discoveries | `terrain/structure/`, scoped structure-policy hooks; active preset, city branch additionally needs the city adaptation | Review [placement ownership](terrain.md#cluster-structure-policy), family membership, native fit/claims/references and saved-level initialization. Compare order, reopening, actual structures and cost. |
| Relative placements | `OreHeightMixin`, ores built-in pack | Review vanilla placements and optional Create/Propulsion IDs; disabling removes both the hook and tags. |
| Village clearance/placement | Separate hook selections; neighbor tuning only when relevant | Review Sky Villages + Architectury and Sky Archipelago validator order. Check ordinary/expanded terrain, footprint bounds, allowance, and disabled combinations. |
| Sable pressure | `DimensionPhysicsMixin`, pressure built-in pack | Review Sable physics profile codec and island-only selection; preserve upstream machine/block weights. |
| ADQ | Separate distance, chunk-loading, and landing mixins | Review private route lambdas, injection counts, ticket lifetime and cleanup. Landing requires Sky Villages but does not enable its placement adaptations. |
| Analog Audio | `AnalogAudioCreateMixin` | Review public Create registry erasure and upstream reflective lookup. Retire when released Analog Audio registers the handler correctly. See [procedure](audio.md#create-registration). |
| Radio timeline | Client clocks, native queue correction and guarded async callbacks | Review the exact bundled media engine, queue positions and session ownership; see [audio](audio.md). |
| Construct recovery | Server support sampling, YiGD placement metadata and lifecycle/tracking hooks | Review both mods' save, plot, death, claim, disassembly and compass paths; see [recovery](recovery.md). |
| Linear Bearing | Separate constructor/loot, carriage lifecycle, kinetic links and gyro hooks | Review [contracts and native fixtures](linear-bearing.md), exact constructor shape, repair ownership, Sable frames/handles and native Create accounting. |
| Transmission & Linkage | Optional server-authorized mutations, deliberate rod removal and Jade state | Review [packet, binding, protection and client input boundaries](transmission-linkage.md) against the exact release; preserve native physics/save ownership. |
| DH | Separate client handoff, shutdown, cache selections | Review ownership and dimension guards, ordinary/default keys, reconnect and shutdown. See [DH](lod.md). |
| Wayfinder | Native server authority and optional client DH reader; conditional Sable frame guard | Review [targeting, bounds, pooled data and update checks](wayfinder.md). Requalify real distant arrival, current-session authority, cancellation, missing capabilities and native chunk-ticket cleanup. |
| BlueMap | Dedicated-server registration only | Review extension schema, map opt-in and renderer boundaries. See [BlueMap](bluemap.md). |
| Moving lights | `SodiumLightingMixin` and independent Create-owner cleanup, physical client; no terrain prerequisite | See [lighting](lighting.md). Retire each hook when SDL supports the selected Sodium cache API or clears its Create holders on world unload. |
| Colorwheel sampler allocation | Independent client repair when upstream disables OIT | Review slot ownership, OIT guards and Iris external-sampler contract; supported OIT remains unchanged. |
| Colorwheel materials | Dependent client writer for construct material scopes | Review direct Sable tessellation and Colorwheel terrain/emission encoding together; see [rendering](rendering.md#colorwheel). |
| Macaw bridge lantern | Optional shared outline and loaded-neighbor selection, default on | Review [model/shape coupling and selection checks](macaw.md); keep flat placement and empty collision native. |
| Glass/decorative backfaces | Post-CT Create, post-processor Fusion blocks, post-modifier Fusion panes and explicit static-model wrappers with neighbor contact ModelData | Review model-data ownership/reentry, contact partitioning, split attributes, depth precision, native cull groups, Sodium batching and Fusion modifiers. Static block-entity exceptions require an admitted wrapper; selections default on. See [rendering](rendering.md#glass-backfaces). |
| Create windows | Client-only material-boundary correction in both native glass/window overrides | Review face suppression and CT-data collection together; preserve matching interfaces and shape occlusion. See [window faces](rendering.md#window-faces). |
| Rechiseled glass | Independent client-only axis-boundary correction in `RechiseledGlassPillarBlock` | Review both vertical and horizontal paths, the vanilla AXIS property, and native slab/stair shape handling. See [window faces](rendering.md#window-faces). |
| Distant constructs | Independent physical-client SSRD/Sable/Iris distance scope and additional DH opaque-depth guard | Review actual draw boundaries, both Iris GLSL transformers, effective SSRD caps, same-frame depth/copy timing and sampler limits. See [rendering](rendering.md#distant-constructs-with-shaders). |
| Framebuffer, balloon, material IDs, model data, render passes | Five independent client selections | See [rendering](rendering.md). These do not depend on island generation or each other. |

For a dependency update, inspect the exact release and any nested JARs before
changing pins. Compare the affected methods with the patch and its upstream issue.
Update compile/test artifact hashes, feature version gates, operator documentation,
and pack pins together. A merged PR does not establish that a released artifact
contains the complete fix. Remove only the superseded adapter and its switch,
resources, tests, and documentation; leave unrelated adapters intact. Document a
removed key before upgrading a pack because unknown configuration keys fail.

Run `./gradlew check build` and relevant disposable startup combinations. Include
an installed-but-disabled target and an unsupported-but-disabled target whenever
activation changes. Background startup and transformed-bytecode checks do not
replace native client checks when rendering behavior changes.

The pack owns static resource/model patches and its explicit feature selections.
Skyweave: Compatibility Patches owns runtime hooks. The Atlas consumes runtime exports, not this adapter
reference; shader IDs are not recipe or weight data. Update the owning pack/app
pipeline only when its actual inputs or interpretation change.

Shader assembly keeps its file-only engine in `shader/`, the Iris coordinator in
`client/`, and optional version-qualified scanner hooks in `mixin/client/`. Keep
recipe-specific Euphoria selection outside the global Iris dependency gate.
[Rendering](rendering.md#shader-assembly) owns the schema and update boundaries.

## Terrain variation maintenance

[Horizontal variation](terrain-variation.md) owns the geometry/metadata boundary. Recheck native descriptor and plan bounds, `sampleHorizontal`, `prepareDensity`, completed-snapshot cleanup/interpolation, and column ownership when updating Sky Archipelago. All terrain stages and BlueMap/DH consumers must agree on surviving spans. Keep native river beds and ocean retaining seals distinct from exterior fragments and interior cavities.

Structure discovery additionally depends on Minecraft's random-spread lookup order and `StructureCheck`: loaded and saved starts must remain authoritative before the owned selected-candidate rejection. Recheck canonical relocated starts, references, unknown structures, and disabled/native routes after Minecraft or NeoForge updates. Ranked lookup rejection and fitting share `StructureSelection.attemptLimit`; any change to retry order or admission must preserve that conservative relationship.

The island locator captures its field, immutable settings, and origin on the server thread, then uses the same metadata-only planning path as native concurrent worldgen. Native caches use concurrent maps or thread-local entries; Compat descriptor/style caches are bounded. Revalidate the host-biome cache, sampler thread-safety, cache keys, and admission determinism before keeping this worker boundary across dependency updates. It must never acquire chunks or mutate reservations. Replies and lifecycle cancellation return to the server thread.

The separate structure command verifies native `STRUCTURE_STARTS` and can save early chunks. For Minecraft, NeoForge or C2ME updates, recheck `ServerChunkCache.getChunkFuture` on both calling threads: the inspected main-thread branch waits, while its worker branch marshals scheduling without that caller-side wait. Qualify `ChunkLevel.byStatus`, ticket add/remove ownership, saved-chunk scanning, result inspection and late callbacks after cancellation. Never replace the qualified path with a synchronous locate loop. Re-run positive/saved-start cases and broad partial searches with and without C2ME; the [discovery contract](terrain-variation.md#structure-discovery) owns coverage, ordering and limits.

Owned profile schema changes need startup/editor parity, a deliberate prior-schema policy, conflict-preserving saves, and a separate saved-world identity decision. Never infer world compatibility from a configuration file parsing successfully.

## Dimension access and initial spawn

`minecraft.nether_access` shares its server-owned decision in `DimensionAccess`.
Recheck frame ignition before item consumption, direct `PortalShape` creation,
old-portal destination lookup, entity travel/direct teleports and player login/
respawn hooks on Minecraft/NeoForge updates. Disabled/unmarked controls retain
native behavior. The policy keeps the Nether registered and preserves ordinary
fire, inventory and progress; implementations bypassing those native boundaries
need separate qualification. The denied-activation response is deliberately a
no-op extension point, not a prison implementation.

`SpawnFallbackMixin` runs only within upstream's initial-world window and records
successful shared selection in `InitialSpawnState`. Recheck startup listener order,
the saved-selection HEAD guard, accepted SATELLITE identity and actual landing
support/headroom/fluids when Sky Archipelago changes. Older worlds are not
reallocated. Preserve native personal beds and keep future team/home policy out
of this shared recovery owner.

## FTB quest item alternatives

`ftbquests.item_alternatives` registers an `ItemFilterAdapter` with the exact
FTB Quests 2101.1.38 common API after mod construction. It adds no item, packet,
completion store or new inventory handler. FTB still owns detection, consumption,
team progress, rewards and matching-stack previews.

Authored selectors use a paper stack with a versioned `minecraft:custom_data`
entry named `skyweave_compat:quest_items`: `schema: 1` plus either `items` or
`tag`. Explicit alternatives accept 1–64 unique namespaced IDs; tags use native
item membership. Unknown fields, future schemas, invalid IDs and oversized lists
match nothing. Mixed stacks contribute to the native task count, so describe a
combined quantity rather than promising a full stack of one alternative.

Emit strict component matching as the fallback. With the adapter disabled,
ordinary paper must not satisfy its definition. Install the adapter on the server
for matching and on the client for previews; disabling it requires replacing
these selectors with supported native objectives. Other item stacks and foreign
filter adapters retain their native behavior.

On an FTB or Minecraft update, recheck adapter registration order, native matcher
precedence, custom-data stack decoding, tag reloads and display-stack caching.
Verify mixed quantities, a nonmatching ordinary paper stack, malformed selectors,
absence/disabled loading and dedicated-server execution. The upstream artifact
is compile-only and hash checked; it must never be bundled.

## FTB inventory return

`ftblibrary.creative_inventory_return` requires FTB Library 2101.1.39 on the physical client. `BaseScreen.closeGui(boolean)` closes the player's container before restoring its previous screen. An existing creative screen does not rerun its constructor, which normally assigns the creative menu; IPN then observes the survival menu and places its controls against creative geometry. The adapter restores that screen's client-owned creative menu only when creative mode remains active and native closing left the personal inventory menu selected.

A server-owned container has a different contract: its menu was genuinely closed. Returning to its old screen would display a stale menu. The adapter returns to the world instead; it never reactivates the closed server menu or replaces a newly active menu. Ordinary personal inventory returns keep their native behavior. IPN, Quests and Teams are not dependencies of this FTB boundary.

Recheck the exact `closeGui(Z)V → Minecraft.setScreen(Screen)` invocation, creative constructor/init ownership, mode changes and menu closing when updating FTB or Minecraft. Native qualification must include disabled creative failure, enabled Accept/Escape, resize, creative tabs, ordinary survival and a real chest whose contents persist after closing and reopening. Probe observations and visuals are separate evidence. This repair does not alter inventory gestures, toolbar positions or artwork.

## Island water and environment

Dynamic Waters 13.1.0 is an optional pinned owner of routing, lake filling, current packets and persistence. Recheck its `CoarseHeightField` fast path, `BasinTile.Builder` sinks/settlement/mouth indices, `HydrologyManager` carver sentinels and global caches, snapshot resolver construction, biome projection, ocean/blacklist checks, server scheduling and store stamp on an update. The adapter consumes real accepted island columns and never promotes void sinks into terrain. Exterior watershed proposals protect per-contributor beds before cavity subtraction; snapshot fill and post-carver repair then share protected INTERIOR admission. Recheck that the early proposal scope cannot recurse into INTERIOR/completed snapshots, contributor ownership remains conservative, and minimum-bed endpoints remain inclusive. Qualify sink settlement locals, emitted mouth heights, mouth walking and sea-contact shortcuts against physical local water versus void, including seam imports and persistence. A source block alone does not establish a working outlet: verify normal fluid ticks and persisted reopening. The watershed stamp includes the bridge algorithm and saved generator identity. Native lowland mode must remain `VANILLA`; `NONE` rewrites ridges. Both native Sky Archipelago planners stay off while this bridge is active. Configuration generation inputs enter the saved identity separately from visual/audio/force settings. Registered COMMON writes update their owner immediately: block generation saves beneath a running integrated world, preserve the draft, and retain the fresh-world confirmation from the main menu. Non-generation values keep their native application timing. Recheck upstream custom palette and resource reload ownership when extending the supported material workflow.

The compound Sable adapter uses exact typed APIs. Upstream 13.1.0 reflection on `SubLevelContainer.getContainer` resolves a client-only overload and silently abandons its dedicated-server bridge. Recheck that overload boundary, plot/world projections, `RigidBodyHandle`, world-space impulse semantics and contact eligibility before retiring the replacement. It cancels the upstream ship tick only in marked worlds, submits one current impulse per qualifying ship and uses loaded water touching a bounded world envelope; it does not calculate full voxel immersion. Native boundary tests distinguish dry, wet and yawed craft and restore their test-only pipeline recorder before normal ticks. They do not establish every vessel's sailing behavior.

Native climate consumers retain Y64 temperature queries while real block/light/collision positions remain unchanged. Recheck both Sky Archipelago projection contexts and locator shortcuts, material/river selectors, native freeze/snow wrappers, weather/rain checks, and Iris precipitation. Initial `SnowAndFreezeFeature` and recurring `ServerLevel.tickPrecipitation` freeze the biome of the water cell, rather than reusing a possibly cold biome from the air above. Recheck both `shouldFreeze` overload descriptors, exact receiver selection and Dynamic Waters running-water exceptions. Snow skin can extend below a cold surface into another fuzzy biome cell; that alone is not a temperature defect. Direct biome calls without a world context still have vanilla altitude semantics. They are not proof that a mapped rendering or weather consumer uses that altitude.

The cloud provider belongs to the marked dimension effects registration. Recheck the provider and Iris cloud-height reader after Minecraft/Iris changes. Native void hooks target the altitude factor before mob-effect composition and the separate black underside-sky buffer draw; cancelling all fog or sky would violate the boundary. Shader-lab recipes consume explicit world/void/open-sky uniforms; verify registration, per-frame refresh, disabled defaults, dimension switches and shader rebuilds. Bounded exposure queries use loaded blocks and weak level ownership, not terrain generation. Dependency updates must preserve real cave, underwater and status-effect behavior as well as open-sky corrections.
