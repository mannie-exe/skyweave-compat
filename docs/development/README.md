# Development

Use JDK 21 and the checked-in Gradle wrapper:

```sh
./gradlew --no-daemon check build
```

The target is Minecraft 1.21.1 / NeoForge 21.1.265. Sky Archipelago 1.4 and
Sky Villages 1.0.6 are pinned by Modrinth version ID and SHA-256. They are downloaded
for compilation/testing, not redistributed. Architectury 13.0.11 is included in
the development runtime for Sky Villages. Output is in `build/libs`.

`src/main/resources/logo.png` owns the NeoForge mod-list image, referenced by
`logoFile` in the mod metadata. Replace this image independently of the modpack
and its resource packs. The current placeholder is an opaque white, indexed PNG
banner; `logoBlur=false` preserves crisp pixels. Gradle packages it at the JAR
root. It does not set a hosted project's publishing icon.

See [DH integration](lod.md) for optional lifecycle and cache fixes, pinned
implementation boundaries and dependency-update checks. The server and client
probes are separate artifacts and are never bundled in the mod.

For an isolated full-pack shutdown check with DH generation active:

```sh
./gradlew probeJar
python3 tools/probe.py --dh-pregen --dh-stop-after 2 --seed 42 \
  --server /path/to/server/game --java /path/to/java
```

The input server is read-only. The fixture uses a temporary save and a local random
port, requests a bounded DH job, and asks the fixture server to stop while the job
is active. A normal process exit is required within 90 seconds. Add `--dh-world-key`
to exercise opt-in UUID cache naming; without it the fixture requires ordinary keys
and no new identity marker. These commands never submit work to the input server.

The [client probes](#client-probes) cover legacy-cache construction and known
fluid/material inputs. Scalar measurements do not replace native-versus-LOD
visual comparison.

See the [configuration interface](configuration.md) for settings ownership, safe
startup editing, native UI fixtures, and platform update checks.

See [radio synchronization](audio.md) and [construct grave recovery](recovery.md)
for their independent optional boundaries, native fixtures and update checks.

See [Questlog integration](questlog.md) for client presentation, notification state,
server validation and the isolated journal fixture.

See [moving lights](lighting.md) for the optional SDL/Sodium hook, native fixture,
upstream boundaries and pack-integration requirements.

See the [Hoofprint zoom investigation](hoofprint.md) for the pinned config and
scroll limits, a proposed client adapter, and remaining Connector/runtime checks.

See [Linear Bearing](linear-bearing.md) for machinery contracts, standalone-fix
ownership, finite native fixtures and dependency-update boundaries.
Its optional client tutorials and Jade inspection use the same machinery contract.
Run `python3 tools/generate_ponder.py` after editing scene prose or the bounded
workbench; Gradle checks those resources. The disposable client probe accepts
`-Dskyweave_compat.probe.guidance=true` alongside `probe.linear=true`. Use
`compatprobe_guidance_check` to compile and seek the selected scenes, and
`compatprobe_ponder <item-id>` to inspect real playback. A successful compile/seek
does not establish visible geometry, reading time or correct interaction cues.

## Implementation

See [bridge lanterns](macaw.md) for the optional Macaw shape correction and its required resource model.

See [Transmission & Linkage](transmission-linkage.md) for optional interaction
authorization, Jade inspection, upstream limits and native machinery fixtures.

`FeatureSettings` reads immutable switches before Mixin target resolution.
`FeatureSelection` uses loader metadata, the physical side, and explicit feature
prerequisites. `OptionalMixinPlugin` selects only the active mixins. Disabled
adapters do not validate versions. `SkyweaveCompat` registers callbacks and
loads integration classes only for active selections. `IntegrationPacks` exposes
terrain, relative-placement, and pressure data as separately gated built-in packs.
No integration data is unconditionally exposed at the JAR's `data/` root.

See [adapter maintenance](adapters.md) for ownership, dependency updates, and
retirement conditions, and [rendering](rendering.md) for client-specific paths.
The minimum runtime is NeoForge plus Skyweave: Compatibility Patches; development dependencies are not
runtime requirements. `tools/startup_probe.py` covers disposable dedicated-server
startup combinations. It never launches a client or touches the installed save.

The Sable API is compile-only with a separate development runtime dependency.
Development runs retain the complete pinned integration set. For the minimal
native check, run:

```sh
python3 tools/probe.py --generation adapted --minimal --seed 42 --reopen --heap 6G \
  --server /path/to/server/game --java /path/to/java
```

It stages only Sky Archipelago, Skyweave: Compatibility Patches and the separate probe, without copying
other mods' configs.
The full check omits `--minimal`. These cover the general gameplay integration set. The optional BlueMap boundary
has a separate minimal-plus-BlueMap check described in [BlueMap](bluemap.md).

Loading references: [NeoForge metadata](https://docs.neoforged.net/docs/1.21.1/gettingstarted/modfiles/)
and [Mixin configuration plugins](https://javadoc.io/static/net.fabricmc/sponge-mixin/0.16.4%2Bmixin.0.8.7/org/spongepowered/asm/mixin/extensibility/IMixinConfigPlugin.html).

[Island generation](terrain.md) owns the expanded preset, geometry, geological
frame, saved-world compatibility and native terrain survey. The following describes
the retained village-neighbor predicate.

[Terrain variation](terrain-variation.md) describes horizontal sampling, fractured ecology, ocean admission, bounded metadata discovery, and the structure-lookup boundary. Configuration defaults and verification remain in their owning guides. Gargantuan islands remain deferred.

`placement/NeighborRule` owns the loader-independent geometry. `IslandTerrain`
adapts Sky Archipelago's resolved column field; it never requests world chunks.
`StructureSupportMixin` filters accepted SKY reports for exactly
`skyvillages:skyvillage`. Native height fitting happens before this hook; overlap
reservation, registration and structure references happen afterward.

The rule first rejects obvious overlap on a coarse grid, then finds a real nearby
terrain sample with a qualifying dominant island descriptor. Accepted candidates
must additionally pass an exhaustive column check in the minimum-clearance band.
All distances use the complete layout's X/Z bounding rectangle. Bounding-box gaps
between buildings are deliberately reserved too. This predicate introduces no mutable placement counters or visitation-order
choices. The dedicated preset separately applies the deterministic per-archipelago
allowance described in the island-generation document.

This predicate is deterministic for the supplied layout, terrain field and
settings. It does not remove upstream's existing structure overlap reservations
or prove that the entire upstream generation pipeline is independent of order.
Debug logging distinguishes `TOO_CLOSE` and `NO_HOST`; the upstream engine may also
report its generic air-clearance rejection stage.

`NeighborConfig` registers a NeoForge COMMON spec and captures immutable settings
at server startup. A client-only entry point registers NeoForge's config screen.
The server artifact has no dependency on loading client GUI classes.

## Analog Audio

`AnalogAudioCreateMixin` corrects one reflective lookup in Analog Audio
0.1.5-hotfix.1 with Create 6.0.10. Upstream asks the package-private registry
implementation for `register(Block, MovementBehaviour)`. Java erases those generic
parameters to `Object`; obtaining the method from Create's public `SimpleRegistry`
interface also avoids illegal access to its implementation. The adapter preserves
upstream setup timing, handler construction, registration and error handling.

Loader metadata selects the hook only for that exact pair. Neither mod becomes a
required or compile-time dependency, and no client classes enter the dedicated
server path. Disable this feature to retain upstream behavior with other versions; an enabled
complete but unsupported pair is rejected with a configuration remedy. This adapter changes
no recipes, resources, weights, radio controls or Sable coordinate handling.

Before changing either dependency, inspect `CreateCompat.lambda$onCommonSetup$1`,
`SimpleRegistry.register` and the upstream `RadioMovementBehaviour`. Remove the
hook if upstream fixes registration; otherwise update its exact gate only after
rechecking the lookup and required injection count. Do not replace the upstream
handler or broaden GUI behavior as part of this fix.

Build with `./gradlew check build probeJar`, then run
`python3 tools/probe.py --audio SELECTION --server /path/to/server/game --java /path/to/java`
for `minimal`, `create`, `analog`, `pair` and `full`. These modes check optional
class loading, the actual radio registration, a control Create drill handler,
and dedicated-server callbacks. Use `--mod /path/to/baseline.jar --audio pair`
for the expected failing control. Reports include exact artifact hashes.
Client playback, moving sound positions, stopping/disassembly and GUI controls
require a separate interactive check; successful registration does not prove them.

## Verification

Native generation checks must reach the relevant chunk stages. C2ME can leave a failed generation future pending after a critical Mixin error; inspect worker errors promptly rather than treating a timeout as slow terrain. Stop only the owned disposable JVM, preserve the exact error, correct the hook and rerun. A successful Java build does not validate native injection coverage.

For bounded settlement diagnostics, use `tools/probe.py --surface-structures`
with `--settlement-review allowances` or `--settlement-review fits` and
`--structure-clusters`. Fitting observes at most two positive clusters and eight
ranked alternatives per cluster; it never commits a structure. `--sky-neighborhood`
checks actual Sky Village starts and generated host blocks. These modes use the
existing `--server`, `--java`, `--shape-config` and `--structure-config` inputs.
Use `--perimeter --ocean-fringes --reopen` for native shore/reef material checks.
Keep metadata allowances, fit diagnostics, generated starts and population checks
separate when reporting results.


`tools/probe.py --coverage` exports resolved Overworld biome feature lists, placed
and configured features, structures, structure sets, resource priority and bounded
ancient-city eligibility observations. `--structures --heap 6G` requests natural
underground structure candidates and realizes the first bounded layout per type.
`--surface-structures` does the same for vanilla surface/water families. These modes
accept `--minimal`; generated JSON records scope and artifact hashes. Full-set
structure captures wait for a loaded neighborhood and ticks to inspect one Lootr
conversion. Generation captures disable only their disposable BlueMap webserver
and asset-download configuration. They do not test map rendering. Counts
of accepted starts are candidate observations, not natural occurrence rates.
`tools/feature_coverage.py CAPTURE --output CLOSURE` traces registered and inline
nested placements from that capture. It distinguishes placed/configured IDs and
reports height modifiers under selectors; update its typed traversal if new feature
codecs add nested placements.


`--terrain` checks native descriptors and reordered/concurrent queries across
five seeds. Use `--terrain --terrain-seed SEED` to reproduce a particular world's
layout with its installed configuration instead. This estimates terrain from the generator,
rather than reading stored chunks; compare selected points with the saved world before
making claims about its contents. Compare its reports with `tools/layout_report.py` (matplotlib), which
draws descriptor radii at equal scale rather than pretending they are shorelines.
`--overlap --minimal` compares exterior intervals under VOID/OVERLAP at identical
geometry and three seeds. `--interiors --minimal --seed N` records representative
baseline/noise/hollow cross-sections, solid-volume differences and independent
snapshot/direct query-order checks. Bounded skylight witnesses also check native
NOISE/SURFACE writes. Sampled volumes remain pre-carver metadata, not a survey of
decorated chunks or Dynamic Waters. Use `--generation adapted`
for actual fill/base-column agreement and completed chunk content.
`--veins --minimal --seed N` searches a bounded set of solid columns for raw
iron/copper witnesses, then checks native fill, base columns and completed chunks.
This deliberately targeted search does not estimate vein abundance. A missing
witness fails its completion check without establishing global absence.
`--surface-climate --seed 42` inspects contiguous warm, cold and transition areas
before and after an explicit native precipitation sweep. `--benchmark --seed 42`
measures a fixed column/chunk workload; use `--mod /exact/baseline.jar` for a
comparison without modifying installed instances. Different layouts generate
different terrain, so timings do not isolate the cost of a single algorithm.

`--lookup --lookup-structure ctov:medium/village_plains --lookup-radius 100`
checks native cold/warm discovery for that exact registered variant. Include the
pinned `--dynamic-waters` artifact when qualifying its planning cost. The default
target remains `minecraft:ancient_city`; `--city-witness` retains its separate
fixed city control. Fresh-world startup and the timed lookup are distinct costs.
Use the disposable fixture, never repeated watchdog failures on a playing server.

With `--hydrology --water-witnesses tools/fixtures/terrain/river-climate-regression.json`, each point may override the
fixture's `waterY`. An `expectedWet` value enables water assertions; omitting it
records observation only; `expectedBlock` asserts the final block. Optional fixture
`weatherSweeps` invokes native precipitation, and `freezeControl` temporarily tests
cold water in the disposable world before restoring the witness. The report separates native generation stages, stored
and runtime biomes, reference-temperature checks, and bounded snow/ice writer
stacks. A completed observation is attribution evidence, not a repaired-behavior
regression. Use zero fluid ticks when isolating initial generation.
`--climate-only` restricts that exact-witness check to generation and precipitation;
it requires zero fluid ticks and no reopening. Use it for climate-disabled controls,
where legitimate frozen channels would invalidate the separate flow assertions.
Reports include water/air biome receivers and their native freezing decisions.

`--interiors --skylights-only` checks bounded roof openings without repeating the
volume survey. Positive fixtures require native HOLLOW hosts with an actual center
roof; fractured protected cells and empty crescent centers are not positive shafts.
The fixture must find real openings, preserve NOISE roofs and retain its protected
fracture control where available. Keep volume/query-order evidence separate.

`--structure-search --search-radius 3000 --search-target 'structure=#minecraft:village'`
executes the real command over ordinary ticks, checking cold/warm completion,
result navigation, cancellation and lease release. Use `--without-c2me` to qualify
vanilla scheduling. `--reopen` requires the same saved results after restarting,
with a probe-only negative new-start precheck to exercise saved-data discovery.
For broader/rare searches, partial results prove bounded work, not absence or nearest
coverage. Keep generation/fixture settings in the report and separately qualify a
positive target. No search test belongs on a playing server.

For isolated terrain screenshots, `skyweave_compat.probe.terrain_readiness=true`
enables the existing `terrain_ready` observation without changing fog. Require
received FULL chunks and completed visible Sodium sections before capturing.
The separate `terrain_capture` photography option also overrides fog; do not use
those images to qualify weather, visibility distance or environmental rendering.

`check` runs geometry tests and verifies upstream artifact hashes. Tests cover
void, fragments, exact distance boundaries, narrow unaligned overlaps, full
footprints, invalid ranges, negative/world-border coordinates and concurrent
calls. CI runs checks/builds on Linux, Windows and macOS. Native generation is a
separate opt-in check; CI unit tests are not a substitute for it.

To test with an installed server's exact mod selection and config:

```sh
./gradlew --no-daemon check build probeJar
python3 tools/probe.py --server /path/to/server/game --java /path/to/java
python3 tools/probe.py --natural --seed 1 --server /path/to/server/game --java /path/to/java
```

Use `--generation-config /path/to/skyweave_compat-generation.json` to stage an
authored candidate profile instead of the installed one. Strict profile parsing
rejects obsolete fields; do not accidentally test against an older installed JSON.
`--interior-config /path/to/skyweave_compat-interiors.json` similarly stages
an explicit cavity/carver comparison without changing the installed instance.

The runner reads installed libraries, mods, config and existing EULA acceptance.
It creates a disposable world, binds loopback on an ephemeral port, and removes
the temporary server on exit. It never mounts a live save. The probe waits for the first completed server tick, after all startup spawn
handlers. Its JAR is separate from release artifacts. Results go to `build/verification`.

`--dh-pregen --seed N --heap 6G` runs the installed DH dedicated pregeneration
manager in a disposable full-installation world. It uses the command's minimum
32-chunk radius, waits on that isolated job's future, records region-header entries
before/after, and removes the test world. This mode cannot run with `--minimal`.
It is a development probe, not a monitor for an operator's server or pregen task.

`--islands --seed N --heap 6G` samples three anchors using 49 spatial strata each. It records
observed blocks, weighted estimates, relative ore depths, spawn collision checks,
surface biomes, descriptors and generation/scanning time. Reports are JSON and
can be consumed by external analysis tools. See
[terrain](terrain.md) for sampling limits.

The default heap is 4 GiB; `--heap` accepts 4G, 6G or 8G. The report records the
selected limit and observed end-of-scan heap usage. The runner rejects fatal
runtime and shutdown errors even when Minecraft exits zero after writing a report.
End-of-scan usage is not peak memory or a recommended production allocation.

`--mechanics` calls the installed balloon search and assembly/disassembly helpers
at boundary heights. It allows normal ticks for deferred physics cleanup before
stopping. Use a separate interactive aircraft fixture for flight and persistence.

`--chunk-futures --seed 42 --heap 6G` compares background FULL requests, explicit
retained-ticket controls and the production delivery adapter. The last case
invokes ADQ's applied rejection handler and checks that the lease is released.
This diagnoses endpoint loading; it does not transport or complete cargo.

### Client probes

`./gradlew clientProbeJar` builds `build/probe/skyweave-client-probe.jar`.
Install it only in a disposable client, with desktop authorization before launch.
It is excluded from production artifacts and the server probe.

`tools/probe.py --world-output /new/fixture/path` retains a successful, stopped
probe world for client inspection. The destination must not exist. Use the same
dependencies and generation configuration in the isolated client; this does not
export a distributable world or update a personal instance.

For resource comparisons in a full rendering client with Iris, use
`-Dskyweave_compat.probe.resources=true`. This mode requires
`resource-review-fixture.json` in the disposable game directory and returns before
the flight telemetry modes. The owning fixture supplies its world and resources;
the probe neither creates a survival world nor installs candidate packs.

`resource-review-control.json` accepts sequenced resource selection, integrated-world
commands, native block interaction, container closing, shader switching and shutdown.
`resource-review-state.json` records the selected packs, camera, screen, container
slots and advancing ticks. Native inspection also records face visibility and
world-aware CT quad UVs; raw three-argument model quads are insufficient for this.
Iris is optional for resource checks; shader switching requires it. Select one `review-*.zip` treatment above the captured
baseline; returning to baseline removes review treatments. Remove stale control
files before another launch. Shutdown explicitly halts the integrated server.
These controls are test instrumentation, not a production authoring interface.

For SSRD/Sable/Iris/DH distance investigations, also set
`-Dskyweave_compat.probe.distance=true`. The marked integrated resource fixture
accepts the `distance` operation: `capture: true` records the next opaque construct
and LOD draw; a later request reads the observations. `chunks` changes the isolated
SSRD client request and sends its native server packet. `dh` toggles the disposable
DH renderer. These controls are not production repairs.
`nestedFailure` checks the production draw scope's cleanup using one caught,
probe-only nested exception. Server ship snapshots run on the server thread and
include physics validity and velocities. Resource `command` acknowledgement
means queued execution; confirm coordinates, bounds or a subsequent server-thread
snapshot before accepting a capture. The marked resource fixture can `rejoin`
in-process without the optional world-text fixture.
Keep the native terrain distance, shader presets, paused physics, camera and
static/assembled controls recorded with captures. Inspect both unobstructed and
occluded views; seeing a distant ship alone does not establish depth correctness.

With DH 3.3.3 installed, JVM flags select these checks:

| Flag | Behavior and output |
| --- | --- |
| `-Dskyweave_compat.probe.dhCache=true` | Exercises the real DH client constructor against temporary legacy/current folders; writes `lod-cache-client-probe.json`. Does not open a world. |
| `-Dskyweave_compat.probe.dhFluid=true` | Creates or reopens `dh-fluid-review`, places lava/water and stone/lichen fixtures, and writes `dh-fluid-colors.json` with exact material/color measurements and lichen-transfer controls. |

Both flags may be used together. For the fluid scene, disable background DH
generation in the disposable configuration, visit the columns at X=0..81, Z=0,
Y=500..700, then view them beyond native render distance. Compare day/night,
shaders off/on and a normal save/reopen. The fixture retains native fluid ticks.
Changing a block exclusion should be tested against the same persisted LODs,
not a cleared database. See [DH behavior](lod.md#luminous-columns-and-fluid-colors).

ModDev's `runClient` cannot locate Connector's clean Minecraft artifact in the
tested setup. The renderer check excluded Connector, Surveyor and Hoofprint;
this is not complete-pack launcher coverage. When connecting a development
client to an isolated production server containing owo-lib, match its handshake
mode with `-Dowo.handshake.enabled=true` on that test server. Do not alter a
playing server for a harness-only mismatch.

Without an opt-in fixture mode or either DH flag, the probe logs the dimension envelope, Sable pressure,
altitude and tracked sub-level bounds every five seconds. That mode writes no
world state. Telemetry does not replace checking aircraft construction and controls.
Close the selected client process normally after recording results.

For the complete Skyweave development stack, the separate
`-Dskyweave_compat.probe.compatibility=true` mode opens `compatibility-review`,
a disposable flat-world entity fixture. It exports live Sable block properties,
checks seven assembled building-material fixtures, and samples natural terrain
models through DH's real conversion path. It requires DH, Sable, Simulated and
the fixture's building mods; it is not a minimum-install probe. The JSON outputs
are `block-mass.json`, `mass-assemblies.json` and `dh-terrain-materials.json`.
Visual animation checks still require desktop observation and short recordings.

The layout probe exercises the loaded Mixin against native village layouts across
four seeds and compares maximum-distance variants. The natural probe checks saved
starts against the predicate and fully generates two retained villages. It counts
realized blocks, rolls their actual chest tables without a player, and checks ADQ's
native surface resolver through the applied compatibility handler. Loot samples
are not Lootr player entitlements or a completed delivery. Global
Mixin debug expectation counting is not enabled: it changes optional third-party
injection validation in the tested pack. This mod's injection remains required,
and the fixture asserts its actual rejection behavior.

Before release, rerun native checks after changes to upstream dependencies or the
predicate. Update the [verification evidence](verification.md), inspect package
contents and test the release staging process. Keep downloaded classes, worlds,
logs and inspection files out of Git.

## Redstone checks

See [redstone adapters](redstone.md) for signal contracts, authorization and update
boundaries. `tools/linear_probe.py --scenario redstone` reuses the finite machinery
runner with `--redstone-gui /path/to/gui.jar` and
`--redstone-additions /path/to/additions.jar`. Use `--minimal` for Create/Ponder
without Sable; `--redstone-target gui` or `additions` checks target absence. Both
JAR arguments remain available to the fixture compiler. `--redstone-baseline`
disables only these adapters for the upstream control. The full run also checks
projected Sable coordinates and the installing pack's block drops.

The disposable client probe accepts `-Dskyweave_compat.probe.redstone=true`,
requires `config-review-fixture.json`, and creates only its `redstone-review`
fixture save. It exposes the existing finite configuration controls and Ponder
commands. Never install probe JARs or fixture markers in personal instances.
