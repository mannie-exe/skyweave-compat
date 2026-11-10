# Testing and native probes

Use JDK 21 and the checked-in wrapper:

```sh
./gradlew --no-daemon check build probeJar clientProbeJar
```

Probe JARs are separate from the released mod. Use disposable instances and exact
artifact/configuration digests; a successful build is not native verification.
[Verification](verification.md) owns retained results and their limits.

## Wayfinder

`tools/probe.py --wayfinder --minimal --seed 42 --server /path/to/server/game --java /path/to/java`
uses only Compat and its probe with Minecraft/NeoForge. It exercises native command
teleports, shapes, hazards, permission and token checks, nearer obstruction and
cancellation of an actual cold destination request. It inspects owned tickets and
waits for abandoned native work to drain. The endpoints are embedded native
players, not remote network clients.

Add `--wayfinder-dh` and omit `--minimal` for the selected full integration set.
The fixture imports real native chunks through DH's public lighting/import path,
reads their actual columns at several distances and checks cancellation recovery.
Column readback does not establish a client ray or teleport. Add `--wayfinder-high`
for the real Skyweave build-height contract, or use `--wayfinder-public --minimal`
for explicit non-operator access.

The existing owned `ClientMiscProbe` supports `skyweave_compat.probe.wayfinder=true`.
Its `wayfinder` controls prepare explicit native terrain, aim using Minecraft's
actual view vector, inspect the marker, import existing native marker data, and
observe the real protocol and destination leases. Prepared test chunks and imports
belong to the fixture, never product preview. `reuse` preserves prepared terrain;
readiness checks the start and actual target marker, while the real Wayfinder
query must observe the intervening data. Preserve artifact provenance with each
run and use physical input for activation, rebinding and GUI/held-key checks.
For actual TCP qualification, `tools/startup_probe.py --scenario standalone --serve`
keeps an owned loopback server alive and forwards console input until `stop`.
Set `skyweave_compat.probe.misc.server=127.0.0.1:PORT` on the owned client to connect
and reconnect to it. `--without-compat` qualifies the absent-server channel.
These modes never bind a public address or reuse the installed server save.

## Shutdown under DH load

```sh
python3 tools/probe.py --dh-pregen --dh-stop-after 2 --seed 42 \
  --server /path/to/server/game --java /path/to/java
```

The input server is read-only. The fixture uses a temporary save and random local
port, requests a bounded DH job and asks its owned server to stop while the job is
active. A normal process exit is required within 90 seconds. Add `--dh-world-key`
to exercise UUID cache naming; without it the fixture requires ordinary keys and
no new identity marker. These commands never submit work to the input server.

## Server and generation probes


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
`--search-planning` compares selected-site enumeration with native eligibility and
checks ordered lazy/eager planning where applicable. Sky Villages uses its own
selected-site contract; it is not the foreign-grid control. `--search-fixture mixed`
adds an exact vanilla random-spread marker alongside managed villages and an
unsupported ring entry. The marker template is prepared before spawn generation
can cache it. Require positives from both supported paths and explicit partial
coverage for the unsupported entry. `--search-fixture cap` uses a disposable
16-candidate limit and verifies that exhaustion stays partial. Neither fixture
changes the input server or production generation policy.

`--search-fixture lifecycle --search-radius 64 --search-target family=villages`
adds native caller removal, travel to the End and actual Overworld unload while
both locators have active searches. It also completes a separately leased FULL
chunk request alongside the searches. Embedded native test players exercise
server lifecycle boundaries; they are not remote network clients. On unload,
island status must report no owned job, structure status must be terminal, and
neither search nor fixture chunk tickets may remain. This fixture qualifies
cleanup, not positive discovery or search throughput; retain the separate
positive family/tag/ID and saved-start controls.

For broader/rare searches, partial results prove bounded work, not absence or nearest
coverage. Keep generation/fixture settings in the report and separately qualify a
positive target. No search test belongs on a playing server.

`--quests --seed 42 --reopen` installs a disposable five-quest book and exercises
native FTB consumption/detection, early advancement polling, personal-to-party
merge, offline membership, manual personal/team claims, duplicate prevention,
overflow conservation and restart persistence. It uses embedded native test
connections, not two remote clients. Quests/Teams/Library must be present; the
fixture rejects `--minimal` and never reads personal quest progress.

The opt-in `ClientMiscProbe` requires its owned-profile marker and system property.
It observes native inventory return, Hoofprint controls, resource reload and
same-JVM disconnect/reconnect. Disconnect must halt an owned integrated server
before calling the client wait loop, matching the native pause screen. The
`skyweave_compat.probe.misc.skyweave` property selects a separate marked spawn
world for integrated arrival/access observations; the default remains flat.
Minimum-client loading must be checked without optional mods, not inferred from
a successful full-pack screen test. Probe artifacts are never release contents.

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
