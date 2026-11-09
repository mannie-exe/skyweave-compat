# Verification

## Terrain, settlements and construct picking

The November 8 follow-up uses Minecraft 1.21.1, NeoForge 21.1.265, Java 21,
Sky Archipelago 1.4, Dynamic Waters 13.1.0, Macaw’s Bridges 3.1.2 and Sable 2.0.6.
`./gradlew --no-daemon check build probeJar clientProbeJar` passes 249 Java tests,
package/Ponder/upstream checks and release-tool tests. The native 0.29.0 candidate
SHA-256 is `275510a308c6c221aba5c8f4976dca0e926e4d59e612468810927a8038b6709c`.
Native fixtures use disposable worlds and exact artifact/configuration digests;
the personal world is not their input.

- Two live Render-thread dumps reproduced the platform-assembly freeze inside our
  global lantern DDA. Sable’s combined result could contain plot coordinates.
  The same-frame hook passes 1,024 rays across eight lantern states, translated
  and yawed, in both minimum and full dedicated combinations. The full client
  assembles a pinned planks platform, tracks it, hits its plot block through normal
  player picking and an explicit ray, and advances 130 ticks across six seconds.
  This establishes responsive picking, not flight balance or an FPS benchmark.
- Two native Sky Village starts satisfy final-footprint clearance, with actual
  generated host columns about 126.59 and 119.67 blocks away. Other sampled
  candidates are rejected. A selected opportunity is not a guaranteed village.
- Full-pack seed 42 surface/features checks cover dry land, Broken Reef, native
  Lagoon and coastal Lagoon fragments, including Mixed and Disintegrating
  treatment. Reef materials use the exposed native palette rather than its
  dirt-producing required-soil fallback. Material checks survive save/reopen;
  fragments retain dry metadata and no source fluid at/above their surface.
  These bounded controls do not establish every overlapping lower overlay or
  every foreign biome’s later decoration. Native lower overlays retain ownership.
- The full-pack island locator passes automatic continuation, result navigation,
  cancellation, permission and empty-result checks. After 40 unchanged startup
  ticks, its loaded-chunk assertion remains 49→49 across 625 cells (4.514 seconds).
  The earlier 130→134 startup run failed and is not counted as a pass. Its timing
  did not establish a search-induced chunk request.
- The 0.29.0 full-pack structure fixture finds the same real fortified-plains
  CTOV start at (-3936,426,816) cold/warm across 1,731 candidates. Empty results,
  permission, cancellation and lease cleanup pass; maximum observed tick gap is
  79.99 ms. This fixed-plains control is separate from natural scarcity sampling.
- Both commands accept `page` without a number and explain empty/partial results
  without blank pages. Island results remain plans; structure results require
  actual valid native starts. Broad vanilla `/locate` remains synchronous.
- Native configuration search selects the localized Fragment fringe fields on the
  Sky Archipelago page, retains its section anchor and leaves the draft clean.
  Canonical profile rejection, optimistic save/conflict handling, independent
  optional selection and invalid ray boundaries have targeted regressions.

### Settlement evidence

In seed -6318123714405175225, the baseline 32-cluster survey has 27,328 eligible
village chunks, nine allowance slots and four actual fitted starts. One is the
intentional Towns & Towers forest ruin. In a bounded budget comparison, one
rejected cluster fails all eight alternatives and a control succeeds on its
second; production retains three alternatives per allowance and its safety rules.

The revised village rate 0.889 produces twelve allowances and nine actual starts
in sixteen clusters. The same sixteen baseline clusters had two allowances and
one start. Five revised starts use intentional forest-ruin content. Counts are
not population verification or worldwide encounter rates. The mansion rate 0.32
remains biome-limited: a controlled dark-forest eight-cluster fixture produces
one allowance, one fitted layout and one real mansion start. It is an enabled
placement control, not a natural rarity measurement.

The marked personal Sky Villages explain the distance complaint: one host was
only planned/unsaved at the tested positions; another had actual saved terrain
about 287 blocks away, outside a 12-chunk client view. Neither observation proves
a missing-island generator defect. Paired native start records sharing a physical
layout are not counted as separate settlements or declared a duplication bug.

### Other log findings

DH 3.3.3’s missing-payload errors occur before decoding when a split-buffer entry
is absent. Its receiver expires entries after 30 seconds and the request queue
retries. These traces do not prove LOD database corruption or identify why the
buffer was unavailable. No cache clearing, fabricated data or speculative network
hook was added. Recheck after removing the independent Render-thread stall.
The Accessories null-entity respawn error was not reproduced in the current log.
Copycats 3.0.9 supplies the stale `copycats:copycat_catwalk` physics selector itself;
the missing block’s ignored mass/volume entry is unrelated to picking. These
remain upstream investigation limits, not claimed repairs.

## Terrain repair and structure discovery (0.28.0)

Tests use Minecraft 1.21.1, NeoForge 21.1.265, Sky Archipelago 1.4,
Dynamic Waters 13.1.0 and Java 21.0.13 on Apple Silicon. Native reports record
exact artifact digests and fixture configurations; older reports remain evidence
for their tested snapshots, rather than every subsequent binary.

The release candidate SHA-256 is `abb6e33539513c04e4b5ac0bfeb557a0f6b207fb1968a0442221d5e965ab26f0`.
Climate, DH and roof captures used `ed1a6b07d4286e10d8f9739078da0ee68208d6c00a75b04bbe24bfad0919a6c7`;
archive comparison confirms identical terrain/rendering bytecode and resources.
The final difference is limited to structure-command metadata backpressure and
its state counter. Final positive, repeated-cancellation and saved-start checks
run against the release candidate with C2ME and the separate non-C2ME control.

- Seed `-134939836980226417`: both reported dry ridges and adjacent wet controls
  contain water at the observed NOISE/SURFACE/CARVERS/FEATURES boundaries. The
  supported twelve-block exterior retains its bed shell before cavity subtraction.
  A 200-tick/reopened fixture preserves water, produces a natural falling outlet
  and retains a generated lake. These are finite local controls, not all river paths.
- At `(3040,498,-653)`, the actual water biome is forest and the air biome is
  jagged peaks. With the repair active, the native air receiver would freeze that
  water while the water receiver rejects freezing; generation and two deliberate
  precipitation sweeps retain water. The same-input climate-disabled control
  produces ice. Genuine snowy-slopes snow remains, and a separate cold-water
  precipitation control freezes. Snow material extending beneath a genuinely
  cold surface cap is retained.
- Seed1's chunk-free fine drainage survey passes across ordinary and fractured
  hosts without wet route continuation after crossing void. This checks planned
  channel continuity, not every subsequent falling-water path.
- Independent interior volume/query-order controls pass at seeds 42 and 1. The final
  seed 1 full-pack roof fixture finds a real HOLLOW opening, preserves the two NOISE
  roof controls and a narrow protected Deep Dark fragment. The same admitted host
  has zero openings when skylights are disabled. Empty crescent centers are not
  roofs and are excluded from positive shaft coverage. Native roof witnesses
  survive NOISE/SURFACE; exterior bounds and island floors remain intact.
  Fully loaded client captures check the roof and connection, with fog unchanged;
  the interior geometry view uses explicit night vision, not natural-light evidence.
- Structure search with C2ME, the natural biome source and the exact rare target
  `ctov:medium/village_plains` visits 15,101 cold and 22,269 warm candidates before
  the configured 120-second limits. Both report partial with no matches; neither
  establishes absence or nearest coverage. Maximum observed tick gap is 598 ms.
  Cancellation, permission checks, stale callbacks and lease cleanup pass.
- Separate positive fixtures find real CTOV starts through family and village-tag
  selectors. Without C2ME, the bounded tag domain completes cold/warm with the
  same canonical start. JVM reopening plus a probe-forced negative new-start
  precheck still finds that saved start and releases its leases. The largest
  reopened tick gap is 621 ms; these observations are not a throughput guarantee.
  The final C2ME candidate also preserves the positive canonical start across JVM
  reopening under a forced negative new-start precheck. Repeated rapid cancels
  leave no leases or stale completion. The non-C2ME candidate completes 1,731
  candidates with that same start cold/warm; its maximum observed tick gap is 72 ms.
- Standalone, terrain-disabled and all-disabled dedicated starts pass. The native
  full-pack client opens the existing configuration screen and its new skylight
  and search-limit anchors, with readable labels and authored defaults. A native
  player search cancels on dimension change and reports its cancelled status
  after returning; no stale completion replaces it.
- The final full-pack DH 32-chunk-radius job completes in 91 seconds of generation.
  Its closed LOD database passes SQLite quick-check with 410 FullData records.
  BlueMap passes adapted, ordinary and foreign bounds/selection, reload and
  shutdown checks. These do not establish distant fluid colors or every renderer.

Required source checks are `./gradlew --no-daemon check build probeJar clientProbeJar`
and `git diff --check`. Reproduce the focused controls using the options in the
[development guide](README.md); use disposable worlds and read-only installed
server inputs. Vanilla `/locate` remains synchronous and broad cold searches are
not certified safe by these command checks. Historical starts whose IDs no longer
have active placement grids are explicitly outside the new command's coverage.

## Terrain and structure compatibility

Native checks use Minecraft 1.21.1, NeoForge 21.1.265, Sky Archipelago 1.4,
Dynamic Waters 13.1.0, Java 21.0.13 and the exact optional versions in the
[configuration matrix](../configuration.md). These qualify the changed seams,
not every generated arrangement or survival balance.

- Original-spawn seed 1477658567442615827: all four reported lake columns are wet,
  both void columns and sampled detached fragments remain dry, and no low source
  shelf returns after 120 ticks/reopening. The sampled submerged lake bands have
  no logs/leaves. Probe-only native task-completion taps also observe all nine
  sites after NOISE, SURFACE, CARVERS and FEATURES: lake points are wet from NOISE,
  void/remnant points stay dry and no low source shelf appears. The four submerged
  lake bands contain no logs/leaves at those stages. Persisted status still names
  the preceding stage until Minecraft commits completion; later requested-status
  reads correctly report FULL. Initial traces remain in the reopened report.
- Native watershed checks with the thirteen added dependencies, C2ME and Dynamic
  Waters pass at seeds 42 and 2026 through fluid ticks/reopening. The bounded
  fractured-host path survey finds no wet continuation after crossing void and
  acquires no chunks. Ordinary falling water is not a generated channel.
- Composed plains villages retain native pieces, processors and containers through
  full generation/reopening with water enabled. Local checks find 76 supported
  doors and 53 supported beds across two profiles. Desert profiles add regional
  samples. This is local placement evidence, not villager pathfinding.
- Native replacement flags suppress six vanilla structure IDs in attempts and
  prospective lookup; false flags restore eligibility. Independent generator
  snapshots and the API-only/disabled controls pass without distant chunk loads.
- Desert and jungle temples each retain five accepted starts in their final
  fixed-biome samples; full first-layout realization and reopening pass with
  Dynamic Waters/C2ME. Water and village exclusion controls retain rejection.
- Native mineshaft and hut support controls place no supports in the sampled void
  column and seven on valid ground; unadapted controls placed 826/827 into void.
  Entrance absolute coordinates, native solid/fluid checks and foreign controls
  remain covered separately.
- Geophilic's resolved features and supported/unsupported decoration controls pass
  across forest, desert, snowy-taiga and explicitly permitted shore fixtures.
  No separate ecology patch is justified by those finite observations.
- A full expanded-stack DH 3.3.3 32-chunk-radius job completes and grows saved
  terrain from 156 to 8,258 chunks. Its closed SQLite database passes quick-check
  and retains 412 FullData records. This proves generation/storage, not fluid
  palette appearance or every client distance policy. BlueMap 5.7 passes adapted,
  ordinary and foreign selection/bounds controls, reload and shutdown cleanup.
- Native witch-circle sampling retains two starts, complete first-layout
  realization and reopening under its independent source schedule. Shared-budget
  selection is pack-owned. The approved shared-budget fixture admits both huts
  and circles, realizes one layout of each and preserves the results on reopening.
- Normal-sized Lagoon treatments pass 120 ticks/reopening with the expanded mod
  stack. The bounded YUNG monument fixture rejects rough or insufficiently
  submerged hosts; it does not prove a viable island monument.
- Full-pack configuration checks read the nested owned files and native foreign
  TOML, save/read back/restored owned and foreign values, preserve generation
  confirmation and work at 720×480. No legacy root files are regenerated.

`tools/probe.py --water-witnesses tools/fixtures/terrain/water-spawn-regression.json`
adds the original water sites to a native hydrology run. Its probe-only
`ChunkStatusTasks` future taps observe completed stage work before status commit;
recheck those method descriptors and completion ordering on Minecraft/C2ME updates.
The runner requires all four initial traces and preserves them through reopening.
Keep the recorded persisted status distinct from the completed task stage.
`--drainage-only`
provides a bounded metadata route check without chunks; `--ecology` and the
`--yung` controls exercise qualified upstream implementations. Probe JARs and
fixture overrides are excluded from release artifacts.

## Granular edges and island watersheds

Candidate checks use Minecraft 1.21.1, NeoForge 21.1.265, Sky Archipelago 1.4,
Dynamic Waters 13.1.0 and Java 21.0.13. These observations qualify the changed
boundaries; they do not replace the earlier release-specific evidence below.

| Boundary | Observed result |
| --- | --- |
| Perimeter geometry | Seed 42 covers Mixed/Disintegrating plans and all four land archetypes (Classic, Crescent, Terrace and Bowl Crater), including an ellipse. Seed 8675309 adds independent Classic witnesses. Checks cover conservative bounds, exact columns, density and native NOISE filling. Pure geometry regressions verify periodic uneven reach and smaller, more widely spaced outer fragments. Metadata sampling requests no chunks. The small plan sample does not establish population frequencies. |
| Water ownership | A failing native fixture isolated sandstone appearing at CARVERS across an outlet that was air at NOISE and SURFACE. Shared pre-water support checks remove that foundation while preserving upstream bed decoration and lake containment. |
| Rivers and falls | Full pack with C2ME, seed 42: generated river sources at Y406 and a lake at Y509 agree with native base-column/height queries. After 200 ordinary fluid ticks, water falls below the supported outlet at (-1216, -1073). No source was hand-placed or manually scheduled. Reopening preserves the hydrology witnesses. Twelve route nodes enter void sinks; they do not create terrain there. |
| DH persistence | The actual DH 3.3.3 generator completes a 32-chunk-radius full-pack job in 88.32 seconds, increasing saved chunks from 144 to 8202. The closed SQLite database passes quick-check, contains 412 FullData records and 28 palettes with Minecraft water. This checks persistence, not distant fluid color or visual coverage. |
| Remote client | A full client connects to an isolated dedicated server, receives generated river blocks and nonzero watershed flow, and reproduces that flow through a render-style BlockGetter. Disconnecting and rejoining the same server restores the same vector. An integrated-server-only command is refused, confirming this exercises a separate server process. |
| Sable boundary | A real assembled body records zero dry and one wet current impulse; a 90-degree yaw preserves the world-space vector. Plot-to-world fluid coordinates agree. The synchronous test recorder is restored before physics ticks; this is not a vessel sailing playtest or full voxel-immersion proof. |
| Climate and environment | High temperate shore and hot Badlands controls do not freeze; a genuinely cold biome retains freezing. Native cloud height is 469.3333. Low open-sky fog remains clear across the floor band while blindness remains black. Generated Complementary/Euphoria and BSL/Colorwheel corrections compile/reload and preserve an enclosed dark room. Native rain and Iris agree at high river altitude; underwater fog survives, Nether effects remain native, and returning restores the marked provider. Explicitly disabling cloud/void corrections restores Y192 clouds and black below-floor fog. Shader captures are separate from photography fixtures with fog overridden. |
| Ocean sizing and cove | Normal anchor sizing, aspect and outer dry fragments pass native base-column/FULL checks for Lagoons (seeds42 and2026) and dry Broken Reef (2026). Lagoon native/open-cove/perimeter modes retain supported water through120 ticks on both seeds; seed 42 reopening preserves source/bed ownership. The full pack with C2ME and Dynamic Waters repeats the seed 42 controls. The cove/perimeter witnesses have17 fluid blocks in the inspected fall band. Warm metadata sweeps and bounded fixture times are not a cold-cache or world-wide performance claim. New ocean visuals and aquatic balance remain unqualified. |
| Locator | Automatic completion visits 625 cells and retains nearest results, provisional paging, cancellation, stale-connection rejection and permission checks. Coordinate clicks suggest rather than execute teleportation. Maximum loaded chunks do not exceed the initial 1930; normal startup unloading reduces the final count. |
| Configuration | Dynamic Waters COMMON entries use the existing typed editor, including validated string lists. The editor shows readable generation timing. A native generation edit is blocked while the integrated server runs; its draft survives and discarding leaves the file unchanged. Main-menu generation saves retain the fresh-world confirmation. |
| Optional loading | Standalone dedicated startup and a Minecraft/NeoForge/Compat-only native client/config screen have no active foreign adapters. A terrain-only native client without Iris or Dynamic Waters activates the cloud/void hooks and opens configuration successfully. Default full-pack startup succeeds. An all-disabled full-pack control passes with the pack's existing `zfastnoise.mixin.properties` biome optimization opt-out. The first failed fixture omitted that prerequisite and exposed the upstream Sky Archipelago/ZFastNoise redirect conflict; it was a fixture configuration error. |

Reproduce the watershed check with `tools/probe.py --hydrology --dynamic-waters
/path/to/dynamicwaters.jar --seed 42 --fluid-ticks 200 --reopen`, the authored
shape/river inputs, and read-only server/Java arguments. The report records exact
artifact digests, settings and native results. The full-pack reopened run takes
142.72 seconds including startup with a 6 GiB heap and peak resident memory
3,601,350,656 bytes; this single fixture is not a pregen throughput estimate.

Current limitations include untested arbitrary boats/ships, unknown shaderpacks
and Photon on Apple hardware. The source-level use of ordinary Minecraft water
does not establish every Create machine, DH material or BlueMap view. Native
hydrology must remain disabled and Dynamic Waters must use VANILLA lowland mode
with mountain tunnels off for this bridge. The owner accepted the revised shorter, uneven aprons, with varied fragments
that shrink and thin outward. Reach, coverage, density, fragment scale/depth,
falloff and Disintegrating frequency/reach remain configurable.


## Horizontal variation and discovery

Verified on 2026-11-07 with Minecraft 1.21.1, NeoForge 21.1.265, Temurin 21.0.13,
Sky Archipelago 1.4 and DH 3.3.3. The configuration/lookup candidate JAR is
`3e75581153e90cf2e1a12fee598bf6cf1dfcf835c429c79cd96a05e5e29a90f3` (0.25.0).
`./gradlew --no-daemon check build probeJar clientProbeJar` passes 208 Java tests
with no failures or skips, plus the tooling, resource and package checks.

| Boundary | Native observations |
| --- | --- |
| Geometry and order | Seed 42 covers 12 adapted and 12 native layouts, 225 reverse/concurrent cells and 19 cross-cell companions/geological frames. Sampled bounds and gaps pass. Zero/disabled modifier and envelope checks also have targeted unit coverage. |
| Actual generation | Full-pack seed 1, including C2ME, compares 6,193,152 noise/base-column states and completes 27 FULL chunks with ores, veins and features. Minimal seed 8675309 generates and reopens. Explicit baseline and strong profiles have separate successful full-pack reports. These are finite correctness workloads, not pregen throughput estimates. |
| Fractured ecology | Forced Mushroom Fields, Badlands and Deep Dark fixtures compare direct/snapshot/query order and stored/physical biomes against generated blocks. Observed features include mycelium/mushroom stems, terracotta/dead bushes, and deepslate/sculk/catalysts/sensors. Forced selection does not establish natural frequency or survival balance. |
| Water and spires | Explicit Lagoon native/coastal/perimeter fixtures survive 120 fluid ticks and reopening. A mixed Lagoon/Broken Reef fixture observes solid, dry spires of both parent types with shared geological frames; no ordinary satellites are admitted. These checks do not establish complete aquatic progression. |
| Spawn | A fully wet perimeter fixture reproduced startup failure in the previous 16-block landing search. The bounded metadata fallback selects actual dry, sturdy, unobstructed ground and passes startup/reopening; native/coastal Lagoon controls also pass. It creates no platform. |
| Native lookup | Eight provably unselected void candidates change from CHUNK_LOAD_NEEDED to START_NOT_PRESENT without loading their chunks. A retained 90-piece seed-42 city preserves canonical lookup/reopening. A separate new-shape seed-42 search finds a city at 6448, 0, -4176 in 1.05 seconds cold and 0.132 warm. |
| Lookup cost | The seed-1, 100-ring missing-city search initially takes 121.3 seconds cold while fitting unrelated surface families. Shared ranked-prefix rejection reduces that fixture to 11.9 seconds cold and 0.81 warm with the same absent result. This is one bounded observation, not a guarantee for every seed, origin or concurrent workload. The watchdog and rarity policy are unchanged. |
| Metadata locator | Actual registered commands cover permission denial, filters, partial continuation, paging, cancellation and completed no-match. Loaded-chunk counts remain unchanged. The command observes plans rather than saved blocks and never promises a safe landing. |
| Configuration | Native wide/narrow and GUI-scale checks show localized nested groups in authored order. An external 20-block warp conflicts with the editor's 19-block draft without overwriting either; restoring the original baseline permits save/readback, then native editing restores 18. Legacy Ocean schema loading is read-only until an explicit validated edit. |
| Consumers and optionality | BlueMap's adapted, ordinary and foreign-world selection/bounds/reload/shutdown checks pass. An isolated DH job is active when interrupted after two seconds, exits normally and retains its per-save identity. A later tuned 0.25.0 job completes its 32-chunk radius in 59.5 seconds, increasing saved chunks from 170 to 8406. This finite result is not an ETA for the personal 1024-chunk job. Standalone, terrain-disabled and unsupported-disabled dedicated startup controls pass. |

Explicit profiles receive separate native reports and world identities. Shape-review
captures use native terrain with DH rendering disabled in the disposable fixture.
The capture gate checks receipt of all expected FULL client chunks, Sodium's visible
top-section meshes and its build queue, across four consecutive observations.
Elapsed time alone is not a readiness check. Camera, profile, artifact, readiness
counts and the fixture-only distance-fog override are recorded beside each image.
The owner reviewed the complete baseline/default/strong native captures and selected an intermediate base: 70% toward strong edge settings and 80% toward strong shaping, with seeded strength variation. Natural selection rates remain conditional settings rather than playtested balance. The [variation guide](terrain-variation.md)
owns behavior, limits, and the deferred Gargantuan idea.

The tuned candidate repeats full-pack seed-1 generation/reopen, minimum seed-8675309 generation/reopen, full-pack Mushroom/Deep Dark native positive fixtures, and the full configuration screen. The minimum client (Minecraft, NeoForge, Compat only) opens configuration with no active foreign adaptations. Strength variation is localized, typed and visible at 0.15. The final-version archive differs from the tuned full-generation candidate only in version metadata; terrain classes and resources are byte-identical.

Reproduce the retained seed-42 city control with `tools/probe.py --lookup --seed 42 --city-witness --shape-config tools/fixtures/terrain/lookup-shapes.json --ocean-config tools/fixtures/terrain/lookup-oceans.json --minimal --reopen` and the required read-only server/Java arguments. These frozen fixture inputs are not shipping defaults.

## Cluster structure distribution

Verified on 2026-11-07 with Minecraft 1.21.1, NeoForge 21.1.265, Temurin
21.0.13 and the pinned Sky Archipelago 1.4 artifact listed below.
`./gradlew --no-daemon check build probeJar clientProbeJar` passes 194 Java tests
and the tooling, generated-resource, upstream-hash and package checks.

| Boundary | Observations |
| --- | --- |
| Family policy | Three fixed seeds each cover 32 distinct accepted clusters. Surface-quiet counts are 18, 15 and 18; portal plans are 8, 9 and 5. Villages, wrecks and hidden underground content remain available. These are fitted plans, with only the explicitly sampled native starts committed; the sample does not establish rare-feature absence or playtested scarcity. |
| Native ground routes | Ordinary wreck, mansion, jungle temple and swamp hut fixtures materialize native content and retain starts through reopening. The wreck retains supply, treasure and map loot. Igloo fixtures retain top, ladder and optional basement. Ordinary/beached wrecks share policy, but the latest positive wreck is the ordinary variant. |
| Desert pyramids | A controlled failure dropped a fitted temple to Y=-129 after a footprint-height scan encountered void. Persisting the accepted anchor preserves four loot chests, nine TNT and the pressure plate. Native client inspection then exposed an unsupported foundation column; complete footprint/trap-depth admission selects a supported alternative, with the same loot/trap and saved outcome. Full-pack checks include C2ME. |
| Native underground routes | A default-policy BASELINE city has 90 pieces, sampled exterior burial 0.9979, nine core floor/roof witnesses and 1,090 two-high air samples opened from 1,729 solid samples. Loot, locate and reopening pass. A small forced-city fixture rejects all four hosts through existing burial/core/roof checks. Existing NOISE/HOLLOW terrain and cavern fixtures below retain their own scope. |
| Archaeology and parallel plans | Trail ruins retain native pieces and 87 suspicious-gravel blocks through reopening. Concurrent detached owners reproduced a vanilla lazy rotation-table race; startup initialization removes the observed null-direction failure. Independent concurrent plans and recomputation after cache eviction agree. |
| Delivery and lifecycle | Native duplicate guards and reservation stores remain independent between same-seed generator owners. Deferred releases preserve both owned and unowned contexts. Three fitted villages survive both forward and reverse source visitation with identical geometry and piece NBT. The probe rechecks all starts after later attempts, including pending revocation. |
| Configuration and sides | Standalone and installed-but-disabled dedicated startup pass. Minimum and full clients expose localized typed settings, search and selected section anchors. Native save requires the existing new-world confirmation and reads the edited value back. A full client loads the native temple world, saves and rejoins it in the same JVM. |

The matched structures-enabled cost observation requests 10,201 columns and 18
chunks at seed 42. Released 0.23.1 takes 19.84 seconds for columns and 15.67 for
chunk requests; the policy candidate takes 19.16 and 12.76. Both report 1,080
occupied column samples. This is one bounded observation under host contention,
not a statistical speedup or pregen ETA. Later scoped support/matching repairs
have their own native checks; point queries and rare failed fits still cost work.

Use `tools/probe.py` for fixtures: `--surface-structures` or `--structures`,
`--structure-config`, `--structure-id`, `--structure-clusters`, `--far-structure-fixture`,
`--layouts-only`, `--reverse-structure-order` and `--reopen` distinguish plans,
starts and full content. Fixed-biome tests must supply compatible river settings.
A shore-only source spends excessive time in upstream host-biome search; the
positive wreck fixture therefore uses the normal multi-biome generator.

Native clients used shaders off. The pass does not establish survival balance,
every modded structure, ocean progression, a beached-wreck positive, or every
possible layout. `/locate` checks resolve the committed destination; finite native
search budgets are not a guarantee that any rare structure can be discovered from
any origin. Historical Atlas village surveys remain observations of their recorded
preset and are not measurements of this new cluster policy.

## Sky Archipelago 1.4 terrain and city boundaries

Terrain and city regression evidence uses Minecraft 1.21.1, NeoForge 21.1.265,
Temurin 21.0.13 and exact Sky Archipelago 1.4 (`XPc17YJD`, SHA-256
`4aa55186f8c49c4143cc0d9fd8e11acad103edaf0cc8dcee7845ac10c80ccc20`).
The 0.23 series established the terrain/city boundaries below. Structure-family
admission is covered separately; historical terrain witnesses do not establish its
current frequency or every integration's gameplay behavior.

The final configuration check reproduced duplicate-key acceptance in all three generation/interior/ocean startup readers while the editor rejected the same files. Those readers now delegate to the existing strict reader. The focused test fails before the fix and passes afterward. The configuration-validation candidate `a1287c4ba1b9751f7a3dd349b4f0619751e4423bda464cbb3b4087624e9eb563` differs from `5884e1811f66ca186798e2783e3284bbbbb1be1a800eec5ef283cca6a206b2ed` only in `ProfileJson`, `InteriorConfiguration` and `OceanConfiguration`; terrain, rendering and other resources are byte-identical. That validation candidate repeats minimum Sky Archipelago startup and full-pack seed-1 generation. The city-clearance candidate below adds its own scoped implementation and checks. Earlier seed-42 and visual observations retain their recorded artifact scope.

| Boundary | Native evidence and limits |
| --- | --- |
| Geometry and clearance | Seed-42 survey covers 12 bodies across all four land archetypes and verifies the dimension envelope. Eighteen surveyed clusters have conservative support gaps of 414–2,505 blocks, above the 350-block policy. An ownership-filtered 16-block shoreline grid records nearest-center-pair gaps of about 1,463 and 2,707 blocks; these are sampled distances, not exhaustive lower bounds or navigated routes. |
| Fresh spawn | Full-pack seed 1 lands at `(840,428,1539)` and minimum seed 42 at `(1105,307,-595)`. Both have a sturdy grass floor, collision-free feet/head space and no fluid at those positions. Each run also repeats 6,193,152 noise/base-column comparisons. These two fresh-world witnesses do not establish spawn safety for every seed. |
| Independent interiors | Full-pack seed 42 retains BASELINE, NOISE and HOLLOW, with 86 independent-field query-order checks and no cavity refill. Native client inspection shows the hollow floor, roof, pillars and selected openings. |
| Materials and features | Full-pack seed 42 finds actual raw copper and iron vein blocks with matching base columns, across 1,453,160 sampled solid blocks. These targeted witnesses are not an abundance estimate. Cave decoration, geode, portal and iceberg controls pass. |
| Cave biomes | Six known Deep Dark cells agree between stored BIOMES, physical queries and queries outside generation scope. The exterior body supplies the depth band even when cleanup leaves a roof profile. All three cave biomes retain actual decoration and sampled surface ownership. This does not prove native biome-locate discovery finds every projected cave biome. |
| Rivers and lakes | Minimum seed 1 retains sources for 600 ticks; full-pack seed 42 retains sources and supported beds for 200 ticks and through reopening. Native client inspection shows the river and habitat with shaders off and merged Complementary/Euphoria/Skyweave enabled. |
| Floating water | Opt-in fixtures keep the global ocean off. Native water/bed and a cold-biome ice control survive 200 ticks. Coastal and perimeter treatments produce 17 flowing blocks beneath an open edge; the stronger check excludes terrain for 16 blocks below that edge. Perimeter source/bed ownership also survives reopening. A full-pack mixed fixture retains all three treatments for 200 ticks; shader-enabled native inspection shows both the coastal outlet and perimeter curtain. These do not certify aquatic progression or every native ocean archetype. |
| Ancient Cities | Two natural NOISE-host starts are accepted; one 93-piece city is fully realized with native loot/center, nine core floor/roof witnesses, canonical locate after source FULL and unchanged starts after reopening. Fixed-Deep-Dark/river-disabled BASELINE, NOISE and HOLLOW fixtures realize native blocks; NOISE and HOLLOW retain canonical pieces/center/locate through reopening. |
| City exposure | A smaller-radius BASELINE fixture accepts burial `0.9920350657678523`; a 140–160-radius control rejects `0.8914370928063798` below the 0.90 policy without surface fallback. Pure tests compare weighted overlapping-envelope samples with denser references and preserve layout order/duplicate invariance. |
| Other structures | A full-pack natural stronghold retains 105 pieces, all 12 portal-frame blocks, canonical locate/source presence and save/reopen identity. A mineshaft retains 543 rails and its starts through reopening. A trial chamber retains 21 trial spawners, 30 vaults and correctly converted Lootr entrance loot, with matching starts after reopening. |
| Settlement coexistence | One fully generated regular savanna village and two valid native Sky Village starts share the `(-1115,-1361)` archipelago in the same world. The Sky Village layouts have 47 and 60 pieces and separate bounds. The broader 21-anchor allowance survey has histogram `[17,2,1,1]`, unchanged by query order; realized counts stay within allowances. |
| Delivery | Both fully realized Sky Village samples resolve elevated ADQ landings and pass scope/horizontal-distance controls. This does not transport cargo or complete a delivery. |
| DH lifecycle | A full-pack disposable 32-chunk-radius job is active at the two-second interruption; shutdown exits normally and the per-save cache key is correct. This is not a completed pregen. The isolated client also renders distant island silhouettes and companions. |
| BlueMap | Minimum-plus-BlueMap checks retain expanded/ordinary bounds, foreign-world exclusion, serialized reload selection and shutdown cleanup. These verify adapter lifecycle, not a newly rendered public map. |
| Surveyor | Four failing chunk records contain ordinary `INVALID` starts. The applied optional guard accepts a direct rejected-start call without throwing, retains a real stronghold record and leaves native start maps unchanged. Disabled future-version targets also load without selecting foreign hooks. |
| Configuration | Native client inspection covers localized river fields, stable enabled-first ordering and the existing shared sidebar/content form. No personal configuration is changed. |

The native clearance check first reproduces the defect on the preceding `a1287c4b…` implementation: all 1,729 sampled BASELINE core positions remain solid. The final shipping candidate opens 945 of 1,729 sampled solid positions in a seed-1 BASELINE fixture and retains 1,011 two-high air samples in a seed-42 HOLLOW fixture. Both preserve nine core floor and roof witnesses and canonical starts through reopening. Earlier NOISE/minimum checks open 885 previously solid samples and retain 962 two-high air samples. The directly verified 0.23.0 full-pack natural NOISE city has 926 two-high air samples, including 880 opened from 1,517 pre-structure solid samples; its floor/roof, loot, locate and reopening checks pass. These are scoped fixtures, not matched-layout frequency estimates. A 100-block-height HOLLOW fixture accepts no cities; its native outcomes are insufficient hosts/site-budget exhaustion, not a direct test of every support rejection. Pure core-policy tests separately reject missing footing and insufficient shell.

Vanilla source review establishes why clearance belongs here: Ancient Cities request `beard_box`, and the vanilla noise path applies a negative density contribution around rigid pieces. Sky Archipelago's surface-only adaptation does not supply that interior room. The new pass reuses the native kernel with bounded piece influence, full-neighborhood shell retention and protected foundations. The exposure fixtures still accept burial `0.9920350657678523` and reject `0.8914370928063798` through the native city-burial policy. Native client inspection with shaders off and merged Complementary/Euphoria/Skyweave enabled shows the exposed arch, usable room and surrounding rock/sculk. Room sampling and client inspection demonstrate usable sections; they do not prove a traversable route through every room or every possible layout.

The city/terrain fixture artifacts retain their own scope: `04b9f8e15931cf64839b81c54b52ee6bbd96ce726340e199a29a78a2cd848eba` for the full natural city, `a35302500d798006ce008d940a9e3af272a38313b90578356a1cab5562cc8ead` for hydrology/villages, and `dd60866a5cbf527074ac65d10bffe1f56e91661bce67eb12dba1999f780b44ed` for the completed physical cave-biome boundary and later shape/interior/structure checks. The scoped-only physical-source attempt returned surface/dripstone biomes outside generation scope; the final helper uses native sampler association without synthetic structure scopes. Ordinary-preset and known-empty locate controls pass.

A single cold-process cost observation on the `dd60866a…` terrain candidate and seed 42 uses the same 4 GiB heap and fixed workloads against released 1.3.6-compatible code and the 1.4 candidate. Both query 122,412 columns and request 18 chunks. The old run takes 5.25 seconds for columns and 3.76 for chunks; the candidate takes 286.07 and 6.23 seconds. Occupied column counts differ (15,177 versus 14,785) because terrain changes. Other isolated checks were running: this is not a controlled statistical benchmark. The sparse completed-column path performs native completed-neighborhood and interior-noise work, rather than resolving only the requested column. Bulk chunk generation retains native snapshots and empty paths; do not extrapolate point-survey time to chunk throughput or claim optimized sparse queries.

Probe corrections preserve the intended checks: piece-NBT digests are recorded after realization because vanilla chest/spawner flags change during placement; reopening replays the original candidate set; dispensers and trial vaults are not expected to become Lootr containers. BlueMap waits for native reload completion and reuses accepted Minecraft resources rather than inheriting the unrelated download-disabled fixture policy.

Commands are owned by `tools/probe.py`, `tools/startup_probe.py` and `tools/bluemap_probe.py`. Use exact dependencies in disposable staging; `--minimal` selects only Sky Archipelago and Compat. Fixed-biome, forced-interior and opt-in ocean settings are explicit fixtures, not installation defaults. Native worlds, reports and captures stay outside version control. The current personal-world survey remains an observation of its original generator.

## Release 0.22.1

The release candidate SHA-256 is `da95628b57af390f5736a6b7838a73299a33847689c359f84a5c1d758f67384f`. Its only archive-entry
difference from the reviewed configuration-composition candidate below is the
version in `META-INF/neoforge.mods.toml`; all production classes and other resources
are identical. `./gradlew --no-daemon check build clientProbeJar` passes again
with 161 Java tests and the tooling, generated-resource, upstream and packaging
checks. Native evidence below applies to the same implementation. The POSIX directory-mode
failure fixture is explicitly conditional on filesystem support; Windows skips it.

## Configuration composition

Verified 2026-11-06 on Minecraft 1.21.1, NeoForge 21.1.265 and Temurin 21.0.13,
from the working tree based on `f6c091e`. Candidate SHA-256:
`be7ad1ea73355e53b07342fc4eb2d27ba86669187d037eea0d92bd4951c133c6`.
`./gradlew --no-daemon check build clientProbeJar` passes 161 Java tests and the
tooling, generated-resource, upstream and packaging checks. `git diff --check` passes.

Full and minimum native clients opened the configuration screen. Probe observations
matched sidebar destinations to measured content anchors in order, with no duplicate
groups. Unavailable preferences disappear in the minimum installation; journal file
links still return to the owning page. Native labels, compact single-field forms,
Status & files diagnostics, outlined groups and the text-only information action
were inspected. Only the information glyph uses Minecraft's bundled `uniform` font.
Narrow/wide reflow retains the end anchor.

Staged native fields left files unchanged until Save. Invalid village bounds blocked
the whole save; valid fields wrote through NeoForge. Read-only files produced partial
save feedback with the remaining draft retained; retry succeeded. External edits
were preserved until explicit reload. A native atomic-write failure left the draft
intact and restored the previous live value; restoring write access allowed retry.
That failure check used the preceding candidate with byte-identical writer code;
the final candidate repeated a native save and live readback.

Native reload-callback failure handling was inspected, not artificially injected.
Unsupported non-scalar registered values remain unavailable rather than receiving
a guessed editor. Dedicated startup, audible narration and Windows/Linux shortcuts
were not repeated for this client-UI extraction. Earlier evidence retains its own
version scope. See [configuration boundaries](configuration.md) for ownership and
optimistic conflict guarantees.

## Configuration input and selection polish

Verified 2026-11-06 on Minecraft 1.21.1, NeoForge 21.1.265 and Temurin 21.0.13.
Candidate SHA-256:
`d2b9c6810e44c10cf5c2efe9560e7392d56566b5c25f20b566c496f4df77c448`.
`./gradlew --no-daemon check build clientProbeJar` passes 157 Java tests, tooling,
generated resources, upstream hashes and packaging checks. `git diff --check` passes.

The preceding candidate reproduced a failed Command+A selection in the full
native client. Minecraft EditBox notifies its responder on cursor movement, even
when text is unchanged; the old filter rebuilt the input and collapsed selection.
The retained native widget now passes actual macOS Command+A/C/X/V, Unicode
copy/paste, word movement, Shift selection and selection surviving resize. Probe
observations confirm the same input instance across filtering and replacement.
The final build repeated native selection/replacement and Tab ownership checks.

Native full/minimum clients render the information sprite and explicit selected
title. Selecting another destination replaces the old highlight; measured section
jumps and the information-action return work without optional dependencies.
The final full-client capture records the vanilla yellow/white/gray hierarchy and
unfilled information action. Windows/Linux shortcut execution and audible narration
were not tested; native EditBox/Button platform and narration contracts remain
in use. Dedicated startup was not repeated for these client-only changes; no
selection, persistence, optional-dependency or server implementation changed.

## Configuration pages and safe editors

Verified 2026-11-06 on Minecraft 1.21.1, NeoForge 21.1.265 and Temurin 21.0.13,
from the working tree based on `f6c091e`. Candidate SHA-256:
`639a0e0bfc71d035b76670814875cd33a87eda87cac4885f74bcc80a8003cb04`.

`./gradlew --no-daemon check build clientProbeJar` passes 157 Java tests, six
Python tooling tests, generated Ponder/upstream/package checks. Focused additions
exercise canonical pages/search, end bookmarks after column reflow, typed JSON
and cross-field validation, stale drafts, temporary-file failure, registered-owner
readback failure and partial save reporting. Existing properties-preservation
and sidedness tests remain passing.

Native checks during this pass exercised system/mod pages, localized search into JSON
fields and registered TOML editors, first/last anchors, wheel/scrollbar input,
keyboard focus, narrow/wide layouts and one/two-column end retention. Native GUI
sizes included 427×240, 640×360 and 1680×1000. An invalid JSON draft survived
navigation and a French resource reload. All five owned JSON families saved with
validated disk readback; feature writes left the running selection unchanged.
Village bounds rejected an inverted range. External JSON and TOML changes were
preserved, with drafts retained or explicitly reloaded. Link GUI's native enum and
common range controls wrote their owning TOMLs. Global preflight/partial failure
outcomes and filesystem failure cases have focused automated coverage.

The final artifact repeated navigation, reflow and invalid-draft checks after
the last UI-only adjustments; save/conflict checks exercised the same writer
implementation before those adjustments. It also opened configuration and Iris's unavailable page in a
minimum client with no optional adapters. Disposable standalone/full dedicated
servers started and shut down normally, with zero client-only hooks selected.
Personal server/world/LOD data was not used as a test fixture. Native narration
keeps the existing widget contracts; audible output was not assessed. Filesystem
race guarantees remain optimistic, and native Windows/Linux GUI checks were not
performed. See [configuration boundaries](configuration.md).

## Decorative block backfaces

The approved decorative families were checked on 2026-11-05 with Java 21.0.13,
Minecraft 1.21.1, NeoForge 21.1.265, Create Confectionery 1.1.3b (mod metadata
`1.1.3.`), Linear Bearing 1.3.5, Design n’ Decor 2.2b and Bits ’n’ Bobs 2.3.5.
Candidate SHA-256:
`c1fcfe24eef7eabce64ce4c937b7756d480f3a7bd483f9d07f616e7b35c0fc85`.
`./gradlew --no-daemon check build clientProbeJar` passes 146 Java tests and the
resource, artifact and upstream checks. Selection tests cover independent
defaults, missing/disabled/unsupported targets and physical-server exclusion.
A fresh client with only Compat and its test probe loads a vanilla world with no
active adaptations; native glass remains an unwrapped six-quad model. It saves
and exits normally, and the disposable minimum runtime is removed.

In the full isolated client, caramel bricks, bubble gum and industrial grating
change from six to twelve quads; ornate grate changes from twelve to twenty-four.
Every original surface sample retains its positions, UVs, sprite, tint and
shading attributes, including the grates' processed CT output. Matching interior
captures show the restored surfaces. Representative bottom-slab and straight
stair states retain their original output and receive reverse faces; every
possible stair arrangement was not individually captured.

Shader-off captures cover all four families; BSL/Colorwheel captures additionally
cover bubble gum and industrial grating. Native glass beside the bubble-gum block
entity receives the shared-face 1/256 inset, and removing that neighbor restores
the original exposed plane. These are rendering checks, not new physics or
adhesion tests. Scaffolding remains excluded; its existing inward geometry was
reviewed against vanilla source models rather than altered.

Local evidence is in `.git/glass-approved-native/`. Dedicated-server execution
was not repeated; these new adapters have no server hooks or foreign Java-class
references. The preceding implementation's dedicated checks retain their own
recorded artifact and version scope.

## Static glass and bridge lantern coverage

The 2026-11-05 follow-up uses Minecraft 1.21.1, NeoForge 21.1.265,
Macaw’s Windows 2.4.2 and Bridges 3.1.2. Candidate JAR SHA-256:
`fd3016507f02ccfff6b82319f6f2d3f1d4a4382c3d9f053b2d9e093d4e3fc670`.
`./gradlew --no-daemon check build clientProbeJar` passes 145 Java tests and
the required resource, package and upstream checks.

Native full-pack observations compare the preceding unpublished build with this
candidate. Mosaic block/pane output increases from 6/10 to 12/20 quads; tinted
glass increases from 18 to 36. Original vertices, UVs, sprites, tint and shading
attributes remain unchanged. Interior captures with BSL show the restored
backfaces. Shader-off inspection also selects the wrapper. Ordinary, barred and
Gothic windows keep their existing two-sided geometry and original quad counts.
The sixteen mosaic colors and four vertical states are admitted by the static
target policy; native captures use representative red blocks and panes rather
than every color/state combination.

A fresh minimum client with only Compat, its test probe and Macaw’s Windows
loads, selects only `MACAW_GLASS`, renders twelve mosaic quads and saves/exits.
This verifies that the glass adapter does not require Create, Fusion or Iris.

Flat-lantern overhang rays change from misses to hits for all four facings;
the four stair directions remain selectable. Additional rope and stone flat/stair
supports return the correct lantern owner. All retain empty physical collision.
Material and connection variants share the two native lantern model states;
this is source coverage, not a claim that every bridge was placed interactively.

Evidence is retained locally in `.git/glass-macaw-native/`. Dedicated probes were
not rerun for this follow-up: the personal server has NeoForge 21.1.264 and the
runner requires 21.1.265. Its libraries and installation were left untouched.
The previous dedicated checks below apply to their recorded artifact.

## SSRD shader adaptations

The unpublished implementation was checked on 2026-11-05 with Java 21.0.13,
Apple Silicon, Minecraft 1.21.1, NeoForge 21.1.265, Sable 2.0.6, SSRD 1.8.8,
Iris 1.8.14-beta.1, Sodium 0.8.13 and DH 3.3.3. Main JAR SHA-256:
`44536fd4f154ac2095f936f5f82ff9908ae579c46758efed210dc003a401f1c3`.
Earlier chunked-path controls used
`1fb5447e09382b7c23897f06b2f785ba7fccd9ff399370e888c8325d52042957`;
the final build adds the verified immediate-uploader correction. Final native
checks repeat both shaderpacks' opaque controls, scoped restoration, single-block
visibility/occlusion, the DH-absent client and minimum client. The final build and
standalone/full/unsupported-disabled server probes also pass.
It retains the 0.21.1 version identifier for local verification; it is not the
published 0.21.1 binary.

`./gradlew --no-daemon check build clientProbeJar` passes 143 Java tests, six
release-tool tests, upstream hash checks, transformed targets and artifact-boundary
checks.

The owned fixture uses native distance eight chunks and SSRD 128, with the server
request confirmed. A red/gold assembled hull is displaced from its original
terrain location; a separate blue static cube controls terrain visibility. DH
uses pre-existing chunks only. Personal instances and caches were not changed.

| Check | Observed result |
| --- | --- |
| Published baseline | Ship visible nearby and shader-off at 256/512 blocks; missing at distance with BSL or Complementary. SSRD 1.8.7 reproduces it. |
| Both adaptations | BSL 10.1.8/Colorwheel 1.0.5 and Complementary 5.9.3/Euphoria 1.10.5/Skyweave 0.1.0 hide the hull behind an opaque DH wall and reveal its clear portion from an offset camera. |
| Distance alone | BSL restores the distant hull but it shows through the DH wall, confirming the independent occlusion switch. |
| Single block | A gold single-block construct renders beyond native distance after the uploader correction, disappears behind the DH wall and remains visible in front of it. Its renderer type is confirmed in telemetry. |
| Draw state | GPU `far` rises from 128 to effective SSRD 2048 during the draw; the occlusion flag is scoped. Both restore afterwards, including a deliberately nested exception. |
| Moving body | Native impulses produce nonzero linear/angular velocity on a valid physics handle; a short recording shows the hull emerging beside the occluder. |
| Materials | Leaf cutouts, lime glass and opaque panels remain visible near and far. Near-view chest, shaft and cogwheel controls render; this does not establish distant block-entity/Flywheel coverage. |
| Water | Contained native water retains shader absorption; distant water is not promoted to an opaque blocker. DH's water approximation differs from the native tank, so this is not proof of identical translucent composition. |
| Lifecycle | Shader switching/off/on, viewport resize and Nether/Overworld transitions complete. A distance-only client also saves and reopens the fixture in-process. |
| DH rendering off | BSL keeps the hull visible and the occlusion guard off. Complementary's final deferred fog hides its opaque hull; omitting only that fog in a disposable shader restores it. The adapter preserves that shader policy. |
| Disabled switches | A restarted full client with both switches false retains native shader range and omits both adaptations. |
| DH absent | Full client with DH and its polygon patch removed loads the world and settings; default distance correction renders the BSL hull, while occlusion is not selected. |
| Minimum client | Compat and probe load a fresh vanilla world and configuration with no optional adaptations active. |
| Dedicated server | Standalone, full-pack and unsupported-but-disabled startup probes pass on NeoForge 21.1.265; client features are excluded. |

Source inspection separates Sable's buffered block-entity rendering and
Flywheel/Colorwheel embedded programs from the terrain draw scope. Arbitrary
entity renderers, other shaderpacks, dedicated multiplayer visuals, reduced
shader render scales, custom fragment depth, alternate depth conventions and
performance are not qualified.
Do not infer them from an unobstructed hull capture.

Retained baseline media is in the local harness's
`images/2026-11-05-ssrd-shaders/`; candidate media and JSON observations are in
Compat's `.git/ssrd-native/`. Keep exact shader presets with reproduction inputs.
A shader-disabled fallback after a compile failure is invalid visual evidence.
[Rendering](rendering.md#distant-constructs-with-shaders) owns implementation and
update contracts.

## 0.21.1 dependency qualification

Verified on 2026-11-05 with Java 21.0.13, Apple Silicon, Minecraft 1.21.1,
NeoForge 21.1.265 and Colorwheel 1.3.2+mc1.21.1. Candidate JAR SHA-256:
`99fe10b41446785c1b83f41b26fb019fd4a8bcdb95b5fcdaf80fda01c574fc9e`.
`./gradlew --no-daemon check build clientProbeJar probeJar` passes 137 Java tests,
release-tool checks, exact dependency verification, transformed targets and
artifact boundaries. Standalone and full-pack dedicated-server startup passed;
the full check used this exact candidate.

Minimal and full adapted-generation fixtures generated and reopened seed 42 with
matching ore counts on NeoForge 21.1.265 using this exact candidate. They do not
constitute a new terrain survey or Sky Archipelago 1.4 qualification.

The isolated full client loaded the updated resource stack and selected
`flywheel:instancing` without shaders and `colorwheel:instancing` with shaders.
On the 16-texture-unit Apple driver, the native direct-buffer probe observed
terrain metadata and packed emission 15 while emitting four vertices. Static and
assembled material samples rendered under generated BSL 10.1.8 / Colorwheel
Patcher 1.0.5 and Complementary Unbound 5.9.3 / Euphoria 1.10.5 / Skyweave 0.1.0.
Shader switching and the small glass/Copycat fixture also rendered. This is not a
repeat of moving-flight, every glass boundary, FA entity animation, inventory
interaction or GPU performance testing. Photon remains untested on Apple hardware.

The shader-lab check passes with this runner. Author and recipient archives have
identical paths and decompressed payloads; their ZIP bytes differ, so this pass
does not establish byte-identical archives across the two Java environments.
Veil logs unsupported boolean-uniform warnings during shader linkage; no causal
rendering failure was established and those warnings are not claimed repaired.
Native images and state records are retained in the local harness under
`images/2026-11-05-dependency-upgrade/`.

## 0.21.0 shader assembly and glass integration

Java 21.0.13 on Apple Silicon, Minecraft 1.21.1 and NeoForge 21.1.264:
`./gradlew --no-daemon check build clientProbeJar` passes, including seven focused
shader workspace tests for composition/preconditions, bounded input and expansion,
canonical identity, deterministic rebuilding, cache repair, locking, interrupted
publication, ownership and selection/preset preservation. Existing geometry,
configuration and generation-identity checks remain enabled.

The isolated client generated BSL 10.1.8 + Colorwheel Patcher 1.0.5 from a genuinely
missing output before Iris first opened it, with no Euphoria mod or launch hook.
A subsequent full-pack cold run generated Complementary Unbound 5.9.3 + Euphoria
1.10.5 + Skyweave 0.1.0 before the native patcher scanner. Receipt-based recognition
removed the initial misleading missing-input report. Both shader choices compiled
and rendered the glass fixture. Repeated shader loads preserved ZIP hashes and
modification times; explicit rebuilds produced identical bytes. A deliberately
incorrect source precondition retained the old file but blocked its selection;
restoring the recipe made the qualified shader usable again. Native output-name
migration loaded the new archive within the same Iris reload. An exact isolated
empack installation generated both outputs, then passed `instance prepare` with
zero managed-file changes or downloads. Resource reload and
static/assembled Sable material fixtures rendered with the generated Euphoria
output. This is not a general claim about moving-airship shadows or every GPU.

The shared Java author build matched the previous builder's exact file payloads
for both recipes; its controlled ZIP metadata now also permits byte-for-byte
author/recipient comparison. The minimum native client reached its world and
configuration screen without Iris/Euphoria and selected no adaptations. Disposable
standalone and full-pack dedicated-server startup checks passed, without shader
hooks. Native media and state records are retained in the local Resource Review
harness under `images/2026-11-05-shader-runner/`.

The exact Iris target is 1.8.14-beta.1+mc1.21.1. Euphoria scanner hooks qualify
1.10.5-r5.9.3-neoforge; Colorwheel runtime is 1.3.0+mc1.21.1. Source inspection and
native testing cover these versions. Windows archive-handle behavior is enforced
through Iris's close-before-reload boundary and atomic publication, with headless
CI coverage; no native Windows GPU session was available. Photon was not forced
onto unsupported Apple hardware.

## Display text, 0.20.1

Verified on 2026-11-04 with Java 21 and NeoForge 21.1.264. Candidate JAR SHA-256: `f8392b30a6eb7d73a541b6f20a6b6ef2abad6e4c282e99fb991ddbeaea551120`.

`./gradlew --no-daemon check build clientProbeJar` passes 123 Java tests, six release-tool tests, generated-resource checks, upstream verification, and package boundaries. Production Java changes are confined to string literals. The built JAR retains `skyweave_compat` and exposes Skyweave: Compatibility Patches in mod metadata and the configuration translation.

`tools/startup_probe.py --scenario standalone` and `--scenario full` pass against disposable dedicated servers using installed libraries as read-only inputs. Startup logs contain the corrected display name. This text-only update changes no registry, saved-world, terrain, or feature-selection behavior. Interactive client layout/rendering was not rechecked.

## Identity migration, 0.20.0

Tested on 2026-11-04 from `~/production/skyweave-compat` with Java 21 and NeoForge 21.1.264. Candidate JAR SHA-256: `4194514849f43b57c05a6a4e85423cf20d5d3d2a8bb6b20cbe79e5891e6a17e2`.

`./gradlew --no-daemon check build clientProbeJar probeJar` passes 123 Java tests, six release-tool tests, generated Ponder checks, exact upstream hashes, and artifact boundaries. The new marker tests reject old worlds both without chunks and with a competing new marker; rejection preserves the old marker. Existing tests cover optional loading and client/server exclusion. A comparison of all 246 production Java files against the previous revision found only identity substitutions, explicit legacy-marker rejection, and the documented persistence/noise exceptions.

`tools/startup_probe.py --scenario standalone` and `--scenario full` pass in disposable dedicated servers. `tools/probe.py --generation adapted --seed 42 --reopen --heap 6G`, with and without `--minimal`, generates and reopens fresh worlds successfully. These commands use the installed server only as read-only library/mod input. The first full generation attempt exposed an old unversioned Compat JAR in the fixture; metadata-based filtering fixed duplicate installation before the successful retry. No live instance or world was modified.

The renamed physical-client artifact still needs its native check. Historical GPU and gameplay evidence below remains attributable to its recorded release, not this JAR.

## World-text lighting, 0.19.0

Tested on 2026-11-04 with Minecraft 1.21.1, NeoForge 21.1.264, Iris
1.8.14-beta.1+mc1.21.1 and macOS arm64/OpenGL 4.1. Candidate JAR SHA-256:
`5096cb3428108410cd695577f73d1fd2703274cf17cda11c035c5ed0cb9fb47e`.

`./gradlew --no-daemon check build clientProbeJar` passes 121 Java tests and six
release-tool tests, upstream/package checks and generated Ponder verification.
The transformed pinned Minecraft/Iris classes cover concrete emitter injections,
shared backend selection and pipeline destruction. Scope tests cover nesting and
exceptions. Feature tests cover independent selection, side exclusion, missing
Iris, disabled future versions and active unsupported versions.

Native isolated checks use all four lighting-switch combinations with
Complementary Unbound r5.9.3 + Euphoria 1.10.5. Both adaptations also render with
the complete BSL 10.1.8 + Colorwheel 1.0.5 archive generated by the shader lab.
Fixed-time front/back captures show reduced directional color changes in selected
displays and the red cow name. Disabling display lighting restores its darker
entity-program appearance while the independently enabled name remains neutral.
White/black/translucent backgrounds and ordinary/full-bright displays remain
visible. Nearby block, cow, sign and item/block-display controls retain rendering.
These stills do not establish every compositing or driver combination.

An opaque wall hides the ordinary display; the explicit see-through display
remains visible through a wall. Shader reload and off/on transitions close the old
fallback programs. Resource reload and same-process disconnect/rejoin also close
old programs and create a new pipeline. Native telemetry reports no outside-scope
selection, ordinary `TEXT` lookups still return Iris `ExtendedShader`, and caches
remain small in this fixture. A temporary local TTF provider exercises the third
`TEXT_INTENSITY` key alongside `TEXT` and `TEXT_BG`; the Unicode setting alone used
`TEXT` and was not counted as intensity coverage. The temporary font is not shipped.

A minimum native client loads with Compat and its probe only, with both lighting
switches default-on and no active optional adaptations. A second client loads
unsupported Iris metadata with all four text lighting/shadow switches disabled,
again with no selected hooks. Isolated `startup_probe.py` standalone and
unsupported-disabled dedicated-server runs pass with the two lighting switches
configured on and no client adaptations selected. The personal server is not used
for these runs. Config-screen captures include the centered footer hint.

The retained native images and sidecars are under
`build/verification/world-text-adapter/`; the marked fixture is maintained in
`tools/fixtures/rendering`. The extra ordinary-font control emitted a background
without readable glyphs in the chosen render-stage probe, so it is not visual
proof of unaffected arbitrary fonts. Unmodified shader-map lookups, the independent
switch cases and sign rendering provide narrower evidence. No blanket font,
shader, performance or exact-RGB guarantee is claimed. Later exposure, grading and
compositing remain shader-owned; custom nametag renderers bypassing the base method
and Photon on this Apple GPU are untested.

## World-text shadows, 2026-11-04

Source based on `09fedc1`, Minecraft 1.21.1, NeoForge 21.1.264 and Iris
1.8.14-beta.1+mc1.21.1. The final candidate SHA-256 is
`c4c763a05aa49072aa1e8884430a7408fb71e664bf448622b681d37017b45ce1`.
The same implementation was published as 0.18.0 after verification; its version
metadata is the only binary payload change from this candidate.

`./gradlew --no-daemon check build clientProbeJar` passes 119 Java tests, six
Python tooling tests, upstream pins, generated Ponder resources and package
checks. Targeted tests cover clamped section selection, bookmarks across reflow,
missing anchors and independent Iris/side/disable selection. The real Mixin
transformer applies both shadow guards to the pinned Minecraft targets. Extracted
handlers cancel only the shadow pass. The nametag test includes Iris's actual
material mixin and proves cancellation precedes its setup; item/block display
renderers and entity-body rendering retain their boundaries.

The final minimum client loads with only Compat and the test probe. Both new
switches are configured on, yet no optional hooks activate without Iris. Final
dedicated standalone and future-target metadata fixtures also start and exit
normally with both client switches on, zero active adaptations and no transformed
target references to Compat. These checks use isolated runtimes and loopback
servers, not the personal server.

A disposable flat scene compares a full-bright text display, named cow, static
pillar, item/block displays and a sign. All four startup-switch combinations load
and render with Complementary Unbound r5.9.3 plus Euphoria 1.10.5. The shader
candidate also renders with the complete BSL 10.1.8 + Clrwl 1.0.5 archive generated
by the shader lab, and with shaders off. Text and the cow name remain visible. The wide stills do not establish a clear
before/after change. A subsequent fixed-camera BSL comparison places a full-bright
horizontal text display above a white floor, clear of other fixture platforms.
Its rectangular floor shadow disappears with the exclusions on; the display and
block-shadow controls remain visible. Nametag shadow removal remains visually
inconclusive; independent selection and transformed-handler tests establish its
cancellation boundary. Keep the HUD visible during nametag checks: hiding it also
hides the names.

The footer hint now centers its measured wrapped text height against the native
button row. The native configuration capture confirms its alignment. Both Iris
exclusions default on in source and the pack configuration. Gradle checks/build
and the configuration audit pass. Configuration remains installation-local; changing a startup switch requires
a full client restart.

These checks concern cast shadows. Sun-dependent changes to the text's own color
are separate from cast shadows. A later diagnostic reproduced them with a colored
full-bright billboard and red nametag, and substantially reduced them by selecting
Iris fallback text programs. This is evidence for the rendering route, not a
finished scoped adapter; see the [lighting investigation](rendering.md#world-text-lighting-investigation).
The authored scene remains in `tools/fixtures/rendering` for repeat checks.

Commands, native state, before/after focus evidence and unedited captures are in
ignored `build/verification/configuration-iris/`, indexed by `checks.json`.
Shader captures record their tested candidate hashes. Archive comparison proves
that the final JAR differs from the BSL-tested candidate only in the two
configuration UI classes; rendering classes and other payloads are unchanged.
Final minimum/focus and dedicated checks use the final JAR.
Saved next-start switches and active runtime selection are separate in the
records. Spoken narration, arbitrary modded nametag renderers and unsupported
Apple/Photon rendering were not evaluated. No performance claim is made.

## Hanging bridge lanterns, 2026-11-03

The optional Macaw’s Bridges 3.1.2 adaptation preserves empty collision and moves only stair-lantern outlines. A shape-only native control missed a direct outline intersection outside the owning cell. The corrected implementation passes 28 native ray cases across all four facings: outside-cell and ordinary selection, above/below approaches, collision queries, segment endpoints, solid obstruction, nearest lantern and removal. The native bridge remains the nearer obstruction for the low control ray.

Native item placement, breaking and replacement preserve the stair state; resource reload retains selection. The configuration entry renders under Server / shared → Macaw’s Bridges. A cold reopen of the 0.17.0 build retains outside-cell selection. Shader-off captures show the approved upright hanging appearance; flat resource geometry is unchanged. The isolated harness retains the protocol and captures under `images/2026-11-03-macaw-shapes` and `macaw_lantern_review.py`. These checks do not establish arbitrary moving-construct interactions or other Macaw versions.

`./gradlew --no-daemon check build` passes. Dedicated startup passes with no optional mods, Macaw alone enabled/disabled, future targets disabled and the complete pack. The 0.17.0 build's shipping classes match the ray-tested candidate byte for byte. Feature-selection tests cover missing, unsupported and disabled targets on both physical sides. The package check excludes Macaw classes and probes.


## Tiled glass and default activation

The 0.20.1 development build with SHA-256
`8c92065c2a5d86208fdf4d30d86a750bb163f9374e90148e5c9afa560030818e`
adds the non-CT model path for Create 6.0.10 Tiled Glass and its pane.
With the old backface switch enabled, native model queries show no generated
reverse faces on either tiled model, while the CT controls do have them. The new
wrapper produces six reverse faces on the sampled cube and eighteen on the
connected pane. Partial cube/pane contact exercises the same shared partitioner.

An isolated Create/Ponder client with no Sodium or Iris, and a full-pack client
with the merged Complementary Unbound 5.9.3 + Euphoria 1.10.5 + Skyweave shader,
render both viewing directions. These checks preserve pane connections and the
existing CT neighbor; they do not extend the earlier matrix to arbitrary models.
Source input, native records and raw captures are in the Resource Review harness
under `images/2026-11-05-tiled-glass`.

The required `check build clientProbeJar` passes 130 Java tests and tooling,
package and upstream checks. The new model-registration test covers block and
multipart states, inventory exclusion, namespace isolation and repeated wrapping.
A fresh feature file contains no false adaptation switches; explicit false values
still survive loading and editor save/discard. Standalone dedicated startup with
these defaults selects no optional adapters and exits normally. Pack-specific
settings remain authoritative, including its explicit DH cache opt-out.

## Glass backfaces and shared surfaces

### Connected pane edges

The pane-edge correction was checked with Minecraft 1.21.1 / NeoForge 21.1.265,
Fusion 1.3.16, Create 6.0.10, Macaw's Windows 2.4.2, Sodium 0.8.13 and
Iris 1.8.14-beta.1 on macOS ARM64 / Java 21.0.13. Candidate SHA-256:
`416c3dc5c1d9f470f52f2f2f5da7f9a310a5439ba225b4fbb8da30dd181a3d59`.
The loaded client contained exactly one Compat artifact; runtime model inspection
confirmed the completed-model wrapper.

Five-wide, three-high red and magenta pane walls each leaked six internal
horizontal cap quads before the correction and zero afterward. Above-angle
captures confirm the strip disappears with shaders off and with Complementary
Unbound r5.9.3 + Euphoria 1.10.5 + Skyweave 0.1.0. Create oak and Macaw red
mosaic pane controls retain zero internal caps. Native queries preserve exposed
outer caps, different-color boundaries and the uncovered arm of a partial
vertical connection. Resource reload retains the wrapper and corrected culling.

A separate resource-only A/B confirms inconsistent center-strip AO in
Midnighttigger's pane models; Visual Patches corrects the property spelling.
The reported dark vertical bands at **joins between blocks** were not clearly
reproduced in this fixture, so the center-strip finding does not establish their
cause. The owner subsequently reviewed this candidate with the corrected Visual
Patches pack in the personal client and confirmed that the reported pane appearance
was correct. That acceptance does not extend the controlled fixture's coverage.

The candidate passes `./gradlew --no-daemon check build clientProbeJar`
(179 Java tests plus tooling, upstream and packaging checks). This pass used the
isolated full-pack client; the earlier minimum/server matrix below was not rerun
for this candidate. Evidence and staging corrections are retained privately in
`.git/pane-seams/`; personal instances were not changed.

### Shared-surface qualification

Native checks target Minecraft 1.21.1 / NeoForge 21.1.264, Create 6.0.10,
Fusion 1.3.16, Rechiseled 1.2.6 / Rechiseled Create 1.1.1, Sodium 0.8.13 and
Iris 1.8.14-beta.1+mc1.21.1. The host is macOS ARM64 with Java 21.0.13.
The unreleased candidate SHA-256 is
`91bc3d7aec948c724929641aeb54a2d395847894ef2690f111c4d762ef2b2959`.

The shader-off fixture covers 13 paired arrangements in both directions:
Create framed, wooden and iron windows; panes and cube/pane partial contact;
Fusion/Rechiseled materials and rotated axes; cross-family contact; tinted
vanilla glass; matching, opaque, separated and chunk-edge controls. Original
quad CT UVs and native face-visibility decisions match the baseline in the
compared center samples. Native matching pane interfaces remain culled.

A striped diagnostic pack makes material priority visible independently of
shader reflections. Before the repair, original/reverse geometry overlaps over
one square block on mixed full faces and 1/8 block on the cube/pane contact, in
both directions. The candidate removes these coincident same-facing regions.
The nearer interior strips cover farther strips, holes reveal the farther
material, and partial-alpha strips still blend. Actual block/face ownership,
positions, source groups and UVs accompany captures.

The final candidate repeats the diagnostic contacts, six representative CT
arrangements with Skyweave Glass r5.9.3-1.10.5, neighbor removal/restoration and
resource reload. The latter restore identical model output. Static views cover
8, 32, 64 and 128 blocks, 75-degree grazing views and a partial-contact corner,
with shaders off/on. Eight-second mouse-motion recordings of cutout and
translucent contacts show no observed flicker with either shader state. These
samples do not establish arbitrary GPU/depth-format behavior or unlimited distance.
Shader material classification remains a separate shader-lab responsibility.

Separate Create-only and Fusion-only clients load and render the paired blocks
and panes without Sodium, Iris or the other CT mod. The minimum client reaches
its world without any optional targets. Disabling each installed CT adapter
restores native one-sided output with no generated reverse quads. Dedicated
standalone, full integration
with both glass switches explicitly enabled, and unsupported-but-disabled
metadata fixtures start and exit normally; no glass hook is selected on a server.

Three warmed full-pack samples of 2,000 contact-model-data queries took
20.67, 13.67 and 10.50 ms. This measures render-thread model queries, not chunk-mesh
throughput, allocations or GPU frame time. No performance improvement is claimed.

`./gradlew --no-daemon check build clientProbeJar` passes 129 Java tests,
Python tooling checks, exact upstream pins and package/resource checks. Coverage
includes rectangular overlap/union, clipped UVs and attributes, source immutability,
repeated wrapping, bounded fallback, exceptional sampling cleanup and existing
optional feature selection. Dedicated checks use `tools/startup_probe.py` with
`standalone`, `full` (both glass keys enabled) and `unsupported-disabled`.

The Minecraft Resource Review harness retains raw images, motion clips, native
records, artifact hashes and the executed protocol in
`projects/create-sky-resources/images/2026-11-04-shared-glass`.
The shader-off matrix and first motion recordings precede a bounded fallback
and sampling-signature cleanup; the final artifact repeats the controls noted
above. Unsupported nonrectangular, highly subdivided, moving, block-entity,
Copycat and DH paths are not claimed fixed. See the maintained
[rendering contract](rendering.md#glass-backfaces) for hooks and update checks.

## Glass culling, 0.15.0

Minecraft 1.21.1 / NeoForge 21.1.264; Java 21; Create 6.0.10, Rechiseled 1.2.6,
Rechiseled Create 1.1.1, Fusion 1.3.16 and Create Deco 2.1.3. The isolated native
fixture uses restored upstream window models; the rejected all-face override and
Fusion appended-face prototype are absent from its selected stack.

The final candidate SHA-256 is
`057777a29e61d58a8500c52cf19d29b51e6bbcaa99cc6a753eb50d5deb51107d`.
Both disabled and enabled runs use those same JAR bytes. Native inspection reads
world-aware model data, directional quads/UVs and `Block.shouldRenderFace` rather
than treating raw item-model quads as connected-texture evidence.

| Boundary | Disabled control | Adapted behavior |
| --- | --- | --- |
| Different Create framed/wood/iron windows | Covered face hidden, missing CT context | Visible, CT equals the uncovered reference |
| Same Create window | Internal face hidden | Unchanged |
| Create Deco window against framed glass | Shares Create's affected block implementation | Visible, CT equals the uncovered reference |
| Rechiseled glass, Y against X axis | Face hidden; reverse direction visible | Both directions visible with native Fusion CT |
| Matching Rechiseled axis | Internal face hidden | Unchanged |
| Different Rechiseled materials and cross-mod glass | Visible with native CT | Unchanged |
| Nonconnecting variants, vanilla glass, panes, opaque neighbors | Native behavior | Unchanged |

The first full-pack pass confirmed byte-identical inspected controls across ten
unaffected cases. The final 16-case native pass verifies visibility and compares
visible full-window CT with the same fixture after removing only its cover.
Pane geometry is a separate control, not subjected to a full-cube UV-equivalence
assertion. The Create-only client independently passes without Fusion, Rechiseled,
Iris or optimization mods. Minimum and full dedicated startup checks select no
client glass hooks.

Focused reproduction: build a two-by-two framed-glass wall, then cover half its
front with vertical framed glass. View the wall from inside the covering block.
With `create.window_culling=false`, the covered wall face is missing; enable the
switch and fully restart to compare. For the separate Rechiseled issue, use
`rechiseledcreate:birch_window_panes_connecting[axis=y]` behind the same block
with `axis=x`, then reverse their axes. Native suppression is asymmetric; the
Rechiseled switch corrects that boundary without changing Fusion predicates.
These are upstream-report candidates; no issue was submitted.

`./gradlew --no-daemon check build clientProbeJar` passes 108 Java cases, six
release-tool cases, artifact pin checks and package/resource validation. The final
minimum integrated client has only NeoForge, Compat and the separate probe; its
active adaptation set is empty. The exact final binary also passes minimum and
full dedicated-server startup with no glass target transforms.

The selected Complementary Unbound 5.9.3 / Euphoria 1.10.5 configuration passes
five focused native cases, including both Rechiseled axis directions. Eight
representative cases pass after resource reload and again after a cold fixture
reopen. A moving-view
recording accompanies the saved fixture evidence; no frame-time benchmark or
exhaustive flicker/shader matrix is claimed.

The reusable `glass_culling_review.py` fixture in the Resource Review harness
owns the finite cases, matched cameras and `--verify` native reference checks.
Captures and sidecars remain there. This is not an exhaustive shader, third-party
window, Copycat or moving-construct compatibility claim. No terrain, save identity,
personal instances, worlds or LODs were changed.


## Configuration sidebar, 0.13.0

Verified on 2026-11-01 with Java 21.0.13, NeoForge 21.1.264 and macOS ARM64.
The native client candidate is
`a9d575964ae2eacc695119537aa26410d84632308171681875ea4bb8e100d92e`.
`./gradlew --no-daemon check build clientProbeJar` passed 102 Java tests,
six release-tool tests, exact dependency pins and generated-resource checks.
The shipping JAR contains no client probe or bundled upstream classes.

Separate minimum and full-pack Prism fixtures exercised the real configuration
screen. The minimum client loaded without any optional mods and reported missing
targets without creating their tuning files. Native keyboard checks covered Tab,
up/down navigation and moving into content controls. Separate pane scrolling,
window resizing and GUI scale 3 at a 427×280 logical viewport remained usable.
These checks do not establish every keyboard path or operating-system UI.

Startup checks covered disk save, unchanged running selection, cold restart,
discard/continue dialogs, stale external edits and explicit reload. A read-only
file refused saving without losing the draft or changing bytes. Inverted village
minimum/maximum values refused closing; a corrected range saved. Cancelling a
generation-change confirmation left the file unchanged.

Both journal entrances used the same live preferences. Native pointer input and
before/after captures confirmed return to the selected Conditions view with a
nonzero scroll position retained. The journal skin was preserved. The final
candidate passed minimum and full-client smoke checks after metadata, narration
and wording refinements. Dedicated standalone, all-disabled and full-pack startup
fixtures passed earlier candidates; those server adapter classes were unchanged
by the final UI refinements. Personal worlds and installations were not used.

The opt-in configuration probe and concise observations/captures are retained
under ignored `build/verification/config-ux/` during development. Production
verification uses the public artifact separately before the pack pin is updated.
The downloaded GitHub 0.13.0 JAR matched both published checksum manifests and
the native candidate byte for byte; all three CI platforms passed.

## Transmission & Linkage 0.11.1

2026-11-01, Java 21.0.13 / NeoForge 21.1.264, Create 6.0.10, Sable 2.0.6,
Simulated 1.3.2, Transmission & Linkage 0.2.9 and Jade 15.10.6.
Native candidate SHA-256:
`e1377ebbaab92fa07a85c6f1148b9297f31a2ef7936f5f0e9b586c4223eea822`.

`./gradlew --no-daemon check build clientProbeJar` passed 87 Java cases,
release-tool checks, artifact pins and generated-resource checks. The minimum
NeoForge-plus-Compat startup passed. The native machinery runner's `linkage`
scene passed in the full pack; its reduced runtime omits Jade. The original
adapter-disabled control reproduced instant bare removal. Enabled checks reject
adventure/spectator removal, protected peers, stale menu edits and protected
binding without rod consumption; authorized settings, binding and repeated
removal controls pass.

The `linkage-lifecycle` scene covers three native normal/giant pairs, reversed
link order, rotated/off-center bodies, assembly of an already linked endpoint,
one live rod constraint per pair, cold save/reopen and isolated endpoint removal.
All eight registered AUO blocks report mass 1.0, matching a Create mechanical
bearing. Three actual head-plus-stone bodies report mass 3.0 (stone is 2.0 in
this runtime). No weight override is justified.

The `linkage-physics` observation moves normal and giant endpoints about 3.61
and 2.80 blocks without supply blocks. A fixture imposing 2 rad/s at the native
pre-physics event produces 19 RPM and about 3,710.94 SU from the separate output
object. Capacity per RPM is 195.3125; it is not the total SU. This controlled drive
checks conversion, not a practical prime mover, balanced production or a closed
self-powered loop. Native actuation and operating costs are deliberately retained.

An isolated Prism client exercised ordinary attack, settings opening and
sneak-wrench rod removal. Jade displayed server link length/target and the
conversion bearing's separate output. Normal/giant rod models and outlines rendered with shaders off and with
Complementary Unbound 5.9.3 plus Euphoria 1.10.5 on the installed
Iris/Sodium/Colorwheel stack; the assembled converter was also inspected with
that default shader. All six native
AUO tutorials and twelve existing Linear/AK tutorials compiled and sought to the
end. Representative native rod playback showed geometry, the settings overlay
and the pack-corrected interaction wording; compilation alone is not playback
verification of every scene.

These checks do not establish every claim-mod API, mid-session chunk-unload order,
hinge layout, long configuration or GPU/shader combination. Explicit wake requests
after resting are fixture coverage, not proof of a particular engine sleep state.
The stock length range avoids the documented enlarged-range visual cap; no new
renderer or range-extension patch is installed.

The Ponder resource checker reads UTF-8 explicitly; a subprocess check rejects
accidental dependence on the host default encoding, including Windows locales.

## Questlog 3.5.3 support

Compared exact 3.5.0 and 3.5.3 JARs. Quest details, display records, objective
interfaces and reward-collection packet classes are unchanged. Changes cover the
upstream editor, scrollable description, loot-table reward implementation and
structure/biome tag objectives. The adaptation compiles against checked 3.5.3;
version selection accepts only the two reviewed versions.

`./gradlew --no-daemon check build` passed 86 Java cases and five tool checks.
`tools/startup_probe.py --scenario questlog` passed a dedicated server with only
Compat, Questlog 3.5.3 and its required animation library; only server reward
validation activated. The updated full-pack native exporter exercised all 72 authored entries and 25 fixed reward grants, including repeated-claim rejection. The isolated client displayed the wrapped OR conditions, reward preview and settings bookmark with Questlog 3.5.3, and Jade retained its server-observed carriage state. These checks do not establish every upstream editor or loot-table reward path.

## Release 0.10.1

Java 21 / NeoForge 21.1.264, Linear Bearing 1.3.5, standalone Fix 1.0.3,
Create 6.0.10, Sable 2.0.6, Simulated 1.3.2, Ponder 1.0.87+mc1.21.1 and
Jade 15.10.6+neoforge form the guidance fixture. `./gradlew --no-daemon check build`
passes 86 Java cases and five release-tool checks, exact upstream/package checks and
Ponder resource generation check. Metadata-selection coverage includes absent,
disabled, wrong-side and unsupported guidance dependencies.

`tools/startup_probe.py` passed `standalone` and `jade`: Compat needs neither
optional API, and Jade without Linear loads without resolving Linear providers.
The full-pack carriage fixture selected the new server adapter, exercised real
native assembly/braking/release, and required the actual Jade server-data provider
to agree with the brake constraint in both states. Free travel remained about
0.69 blocks under the fixture impulse; braked displacement was below 1e-12.
Native docking and rail-removal controls also passed. The runner now excludes
pack-selected Linear/Fix before adding the explicitly requested test artifacts.

An isolated Prism client compiled and sought all six Compat scenes and the six
Absolute Kinematics scenes. Visual inspection caught the empty-template bounds
problem that compilation alone missed; the maintained workbench now establishes
the complete editable envelope before instructions run. Actual playback shows
rails, payload, staged text and controls. Jade displays the English carriage name
and server-backed brake status. F8 reloads the selected Complementary/Euphoria
shader and produces Iris's native reload confirmation; ordinary R is untouched.
These observations concern the selected renderer
stack, not every shader, driver, locale or aircraft arrangement.


## Release 0.9.0

2026-10-31, macOS arm64, Temurin 21.0.13, Minecraft 1.21.1, NeoForge
21.1.264. Native-checked artifact SHA-256:
`eadbdb99e9e32b96352e56e7953818caf0682b866ff24b82a7cd96e52c570b5d`.
The published binary has SHA-256
`6806eba864105219ced3c7dcf376e67ebd4c5a02eb4f363b869e3eebd78e7eee`.
Linux, Windows and macOS CI produced identical binary and source JARs. Its only
entry difference from the native candidate is a compiler-generated `getFocused()`
bridge in the configuration list, delegating to the inherited implementation.
All machinery, lighting and rendering entries match byte for byte. The published
binary also loaded in the complete Create Sky Atlas capture and decoded 70 entries.

`./gradlew --no-daemon check build clientProbeJar` passes 85 Java cases,
five release-tool cases, exact upstream hashes and package boundaries. The
constructor test uses the released class, preserves safe registrations and rejects
a changed call site before editing. Metadata tests cover absent, disabled,
unsupported-but-disabled, dedicated/client and standalone-fix ownership selections.

The final candidate passes a full dedicated fixture with Linear Bearings Fix 1.0.3:
all nine obtainable blocks and recipes load; one gum block drops; the standalone
fix owns startup/loot. Thirteen signed converter thresholds, all 32 lamp emission
states, crouching gum collision and copied-plank/fixed-friction behavior pass.
An empty-front assembly creates one body; a bedrock refusal preserves the bearing,
target and body count. Both transmissions drive a real fan at 32 RPM magnitude
with one real source, share its stress budget, stall on insufficient capacity and
stop when that source is removed. Driven wrong-facing, obstructed, out-of-range
and other-dimension controls reject coupling. Bootstrap without the standalone
fix also loads in the minimum target fixture. A Compat-only server selects no
adaptations and shuts down normally.

A cold-reopen fixture on the final candidate assembles a guide on a parent rotated
60 degrees, then applies real angular and linear impulses. Relative locked travel
is about 0.0000022 blocks with no measured relative angle error. Both body roots
see the Sable connection. After saving and reopening the same disposable world,
the brake and reciprocal links rebuild; relative position error is about 0.003
blocks and manual release works.

Earlier fixtures of the same carriage implementation exercise floor, ceiling and
both wall profiles, free travel, repeated brake/release, rail removal and a native
docking control. Quarter-turn recovery preserves four named copper ingots in a
chest, four rails and one bearing with no loose item drops; the empty body disappears
and the bearing reassembles. Blocked and above-ceiling destinations refuse without
changing cargo or the brake. These checks use actual ticks, rigid bodies and
impulses rather than only checking a lock flag.

The transmission fixture exercises a 90-degree magnetic ship endpoint, a spring
link to a ship, incompatible driven inputs, reattachment, saved static links and
source removal. A physically rotating gyro feeds a zero-load converter, reaching
118 RPM in this fixture. Stopping the body returns both readings to zero. Before
the rounding correction, both a repeated-orientation sample and the physical stop
remained at +0.5 RPM. Zero stress capacity is independently checked.

`tools/linear_probe.py` owns repeatable commands and input freezing; manifests and
results are in `build/verification/linear`. No performance or arbitrary-aircraft
claims follow from these small scenes. Ordinary recipe costs and weight policy
are unchanged. Utility collision/material checks do not establish gum as physical
glue or copied-material mass. Old networks saved by the upstream injected-source
implementation and every arbitrary third-party assembly callback are not covered
by the new-world fixtures.

The final artifact also loaded the intended client selection with Linear added
and the standalone fix absent. The opt-in integrated scene accepted a real
empty-hand use packet, assembled one unbraked carriage, engaged its brake and
recovered one bearing with zero remaining bodies. Native rail/carriage/payload
models rendered in the selected resource stack with shaders disabled. Saving,
quitting, reopening the same save and reassembling worked; the powered payload
rebuilt the brake. Screenshots, state snapshots and sanitized logs are retained
under `build/verification/linear/client`. Existing optional-resource/model warnings
are not claimed fixed. This is not a shader/performance or every utility-block
animation comparison. The disposable profile was closed normally; personal
instances, launcher settings and saves are untouched.

## Release 0.8.0

2026-10-31, macOS arm64, Temurin 21.0.13, Minecraft 1.21.1, NeoForge
21.1.264. Published binary SHA-256:
`6a69429fa30e63672f8778226179f6b908286b6398563e0a1add9a108e047681`.
`./gradlew --no-daemon check build clientProbeJar` passes 81 Java cases, five
release-tool cases, released-target transformations, package boundaries and exact
artifact checks. Colorwheel's real `ClrwlProgram.postLink` contains both sampler
hooks; Compat's own material scope contains the selected Colorwheel writer hook.
Writer cases cover recursive failure/restoration and plain Iris emitter fallback.
Metadata cases cover missing, disabled, unsupported-but-disabled and server sides.

Colorwheel 1.3.0+mc1.21.1 (`8rq5KP4U`) was compared with its material bridge
disabled and enabled. Native transformed `BufferBuilder` observations with the
shader active showed terrain=false/emission=-1/packed emission=-1 in the control,
and terrain=true/emission=15/packed emission=15 in the adapted four-vertex draw.
Sable's released platform directly calls non-AO tessellation, bypassing Colorwheel's
outer wrapper; [rendering](rendering.md#colorwheel) owns the call-path explanation.

The Apple renderer selected `colorwheel:instancing`. Its native embedded
translucent shader failed before the sampler repair with
`No more available texture units while activating sampler shadowtex0`. The same
scene compiled and rendered after unused OIT slots were released. Colorwheel's own
OIT support flag remained false. This is an upstream report candidate, not a
submitted report. Supported-OIT hardware is not available in this check; its
original allocation and registrations are retained by the guarded code path.

The combined SDL/Lamb run retained virtual client light 14, authoritative server
light 0 and removal back to 0. It exercised an ordinary Create bearing, moving
Sable cabin, a bearing aboard it, source deletion, dimension travel and reload.
Shader-on imagery showed the lantern-bearing translucent slime base and opaque
cabin. No Colorwheel shader compilation failure recurred in that adapted scene.
This native run used candidate
`96a950dfa442922f4641ec85dccd865461c61ea6b3e328546eb1a1c6043a9d51`;
the final binary adds status labels and a uniqueness annotation, with rendering
logic unchanged. See [lighting](lighting.md) for the earlier independent controls.

The final binary also ran without SDL/Lamb in the intended 134-JAR client fixture.
With shaders off it selected `flywheel:instancing`; enabling Complementary/Euphoria
selected `colorwheel:instancing` with Iris's 16-unit sampler limit. Static and
single-block assembled glass/Copycat controls remained visible in both modes.
Mechanical-press and wooden-propeller Ponder scenes rendered, as did the Macaw
covered-lantern assembly after a shader reload. The final native shader vertex
sample still carried emission 15; no Colorwheel compilation error occurred.
Normal fixture shutdown saved and disconnected. An independent minimum client
loaded a fresh flat world with only Compat and the opt-in probe on NeoForge, with
no optional APIs installed and no selected adaptations. It also saved and closed.

Final dedicated `unsupported-disabled` and `full` startup/shutdown probes pass.
The former selects no hooks even with future-version metadata; the latter selects
19 existing server adaptations and no client renderer hooks. Personal server data
is a read-only source for the disposable startup harness. The standalone probe
also passed on the preceding candidate. The release workflow passed Linux, Windows and macOS with matching artifacts.
The local native candidate's SHA-256 was
`8b60320ad5d0e98f18b084f52494f5a5aee78986966adbcbce137dd52c9a23f1`.
Its renderer/lighting classes match the published binary byte for byte; the only
entry difference is a compiler-generated `getFocused()` bridge in the configuration
information list, delegating to its superclass. The downloaded published JAR also
loaded a fresh isolated world, selected Colorwheel instancing, produced packed
emission 15, rendered propeller Ponder and saved/disconnected normally. Published
checksums were verified against both manifests. The retained published check is
in `build/verification/colorwheel`; earlier disposable run output was cleaned.

No performance improvement is claimed from these bounded observations. OIT,
indirect rendering, every shader/driver combination, arbitrary modded models and
the older airship-shadow report remain outside the verified scope. Existing Veil
uniform/resource diagnostics and brief assembly packet warnings are not claimed
fixed. Personal saves, caches and playing instances remain untouched.

## Lighting adaptation

2026-10-31, macOS arm64, Temurin 21.0.13, Minecraft 1.21.1 and NeoForge
21.1.264. Checked artifact, compiled with version 0.7.0, SHA-256:
`69dbc917619c0db18cb18a4784f8d60f771100a245f22994f19b6a761ade28f8`.
This identifies the lighting implementation separately from the published 0.7.0
binary below. `./gradlew --no-daemon check build clientProbeJar` passes 76 Java
cases, five release-tool cases, exact dependency hashes and package boundaries.
Actual transformations include the original SDL emission/value mixins, the scoped
engine wrappers, resolver guard, source accessors, unload callbacks and released
Sodium cache. Controlled execution of the transformed handler covers packed-field preservation and nested/exceptional scope
restoration without cross-thread leakage.

`tools/startup_probe.py --server <installed-server>/game --java <java21>
--scenario <standalone|unsupported-disabled|full>` passes on that artifact.
The first two select no adaptations; the complete dedicated fixture selects 19
existing adaptations and no client lighting hooks. The tool selects the supported
21.1.264 launch configuration even when an older installed loader remains nearby.
Its source installation is read-only; saves and ports are disposable.

Native checks cover a Compat-only client with three loader entries, a Create +
SDL + Lamb + Sodium client without Sable/Iris/Sky Archipelago, and the installed
full pack with SDL/Lamb added. With hooks disabled, the client selects no
adaptations. In the fresh enabled control, virtual emission is 15, adjacent client
light is 14 and returns to 0 after removal; the server stays 0. The same forced
server `checkBlock` produces 14 without ownership protection. An ordinary static
lantern still supplies server light 14 in the full fixture.

A glued cabin, supported off-axis rotating lantern, ordinary Create bearing and
bearing aboard the ship exercise movement and projection. The unadapted scene
retains an Overworld Create source during the Nether visit. The repair removes it,
re-registers it on return, removes it at travel unload and registers it on reload.
Deleting the fresh cabin removes its light without a residual ground sample.
The final disconnect check starts with one Create owner, 26 Sable owners and a
cached client level; all owners become 0 and the reference becomes null. The
no-Sable client also closes with empty holders and no cached level.

Complementary Unbound r5.9.3/Euphoria 1.10.5 renders the cabin interior/exterior;
the same native views remain visible after toggling shaders off. A separate Macaw
covered-lantern assembly illuminates terrain with shaders off. A connected
24-glowstone assembly also renders during the bounded busier check. These visual
captures used artifact `e7513854ceed507837698bc010ac1881b063a1154d594820126ef6fb3874e7b4`;
the final artifact adds Sable-holder/cache disposal, verified by the subsequent
full and no-Sable disconnect checks. Rendering/engine implementations are identical.

The earlier shader-off API/lifecycle comparison measured median/p95 frame intervals
of 17.416/23.952 ms unadapted and 17.453/23.772 ms adapted over roughly 1,008
frames/17.5 seconds at a 60 FPS cap. Render-thread CPU totals were 4.744/4.663
seconds. That comparison predates the logical-side guards and does not establish
their cost. The later shader-on run measured 17.255/25.360 ms and 7.689 seconds
of render-thread CPU. A short shader-off process-CPU sample before/after the
24-block bank measured 25.37/25.13 CPU-seconds over five wall seconds. Process CPU
sums multiple threads/cores; these are bounded observations, not an isolated
rebuild cost, GPU benchmark or statistically demonstrated improvement.

The full client saved and disconnected normally using the fixture stop command.
Short assembly packet warnings and existing resource/recipe diagnostics are not
claimed fixed. Complete cabin occlusion, every modded lamp, shader hand-light
preferences and the upstream reported hang are not established by these checks.
DH's inspected light-storage path did not justify a hook or cache change. See
[lighting](lighting.md) for contracts, limits, retirement and upstream-report
candidates. The observations above retain the lighting findings; raw run output, disposable
instances and inspection sources are cleaned after review. No personal profile, server, world or LOD database was used.

## Release 0.7.0

2026-10-31, macOS arm64, Temurin Java 21.0.13, Minecraft 1.21.1,
NeoForge 21.1.264 and Questlog 3.5.0. Final local binary SHA-256:
`2252e8a5c0da41c983e78895d6ceb806963691338297da9124cfb79419faa64a`.
`./gradlew --no-daemon check build clientProbeJar` passes 73 unit cases,
package/dependency checks and five release-tool cases. Dedicated startup/shutdown
probes pass `standalone`, `questlog`, `questlog-disabled`, `unsupported-disabled`
and `full` using `tools/startup_probe.py --server <installed-server>/game
--java <java21> --scenario <case>`. Questlog-only dedicated loading selects reward
validation without client hooks. The full server selects 19 adaptations.

Isolated minimal and 133-JAR clients exercised native parchment, replacement quest
artwork, wrapped conditions/rewards, centered headings, bounded custom icons,
GUI scales 2/3, German headings and a 480×500 window. Grouped settings and the
native registered editor share saved preferences. Settings retain entry scroll
and a collected repeatable cycle; an adapted dedicated server confirmed ordinary
entry closure still resets that cycle. Main-navigation bookmarks survive search
and chapter refresh without overlapping expanded search controls. Configuration
status shows omitted-key defaults and local/server scope instructions. A physical
client with only Compat and NeoForge opens that overview/status without optional
API failures; its registered editor is correctly disabled. Native resource-pack
selection triggers a real reload, after which entry layout renders normally.

Integrated and dedicated claim checks pass all seven cases, including malformed
claims, choices, duplicate requests and survival overflow. Actual collection of
item, choice and XP rewards also succeeds against a Questlog server without
Compat; validation is not claimed for that server. Full-pack checks retain
Complementary Unbound r5.9.3/Euphoria 1.10.5. The final full-client run used the
same UI implementation before removal of an unused helper and a diagnostic-label
refinement; final dedicated checks use the binary above.

The 300-frame full-client CPU render sample after 60 warmup frames measures
1.314 ms median and 1.570 ms at the 95th percentile. The previous adapter measured
1.388/1.664 ms in the same fixture. These are screen-render intervals, not FPS,
GPU timings or a statistically established improvement. Spoken narration was not
verified. Native UI/probe evidence is retained locally in
`build/verification/questlog-redesign`; test worlds and probes are not shipped.
The [integration guide](questlog.md) owns hooks, notification limits and update
procedures. No personal world was used for these checks.

## Release 0.6.1

2026-10-29, macOS arm64, Temurin Java 21, Minecraft 1.21.1 and NeoForge
21.1.264. Candidate binary SHA-256:
`fc8aa5c9f4e82dd7daa0e26c0c54e4e9c844881f1ec623348b1bd71a0636370c`.
`JAVA_HOME=<java21> ./gradlew check build clientProbeJar` passed 64 JUnit cases,
five release-tool cases, dependency hashes, packaging, and dedicated-side exclusion.
The new transform check applies the pass hook to the released Sable dispatcher;
selection checks cover its independent switch, missing Iris, disabled version
constraints, and dedicated-side exclusion. No terrain or gameplay code changed.
`python3 tools/startup_probe.py --server <installed-server>/game --java <java21>
--scenario <standalone|full> --mod <candidate-jar>` also passed both dedicated
startup/shutdown cases: zero active features standalone, 18 with the full server
selection. This confirms client-only exclusion, not every upstream log diagnostic.

A separate physical client with only Compat and NeoForge reached the title screen
with zero active adaptations and no missing optional-class failure. Its three
loader entries were Minecraft, NeoForge, and Compat; no content mods or probe were
installed. This checks minimum client loading, not survival gameplay.

Native runs used a marked disposable Prism instance containing the full installed
pack, including Connector, with the Compat candidate replacing 0.6.0 and no
standalone Veil Iris Fix. The cloned Resource Review save and maintained
`tools/fixtures/rendering` datapack supplied static/assembled controls. The shader
selection was Complementary Unbound r5.9.3 with Euphoria Patches 1.10.5.

- In 0.6.0, freshly assembled ordinary glass and glass Copycat single-block
  constructs disappeared with shaders; stationary controls remained visible.
  Glass Copycat geometry was visible with shaders off. Ordinary glass reproduced
  the discrepancy while the material-ID adapter was disabled, isolating it from
  the Copycat model-data and material annotations.
- In 0.6.1, the same freshly assembled controls rendered alongside their static
  counterparts with shaders on and off. Sable's original deferred flush runs
  before entities, outside the later translucent pass. The added flush draws the
  current layer while Iris's matching pass is active. Restarting 0.6.1 with only
  `rendering.single_block_passes=false` reproduced both missing constructs in a
  fresh paused fixture; shader-off controls remained visible.
- Propeller and mechanical-press Ponder scenes rendered with shaders, including
  after shader reload. Earlier 0.6.0 checks also covered shader-off Ponder and
  animated propeller scenes. The UI and scene remained visible.
- Two hot-air envelopes were valid with capacity 150, predicted filled volume
  100, and non-null client render regions. No misplaced screen overlay was seen
  in the inspected exterior/interior views. Sampled metal, glowstone, glass, and
  gold Copycat geometry rendered; construct motion changed its cast shadow.

These checks are bounded to this hardware, shader selection and fixture. They do
not prove all special-model materials, normals, shadow filtering or shader packs.
The original gapped/flickering airship shadow report was not reproduced and is
not claimed fixed. The balloon source guard and controlled shadow-pass test
establish cancellation; the native scene did not reproduce the old misplaced
overlay with the guard disabled, so it is not a native before/after reproduction.

Physics pause does not survive a client restart; fresh floating controls and
telemetry avoid mistaking a fallen construct for missing geometry. Offline
quickplay worlds opened to LAN stalled on owner-disconnect shutdown while the
server kept ticking. Singleplayer Save and Quit succeeded. The test-only
`compatprobe_stop` command explicitly halted the integrated server and completed
chunk/database shutdown. This is a fixture exit procedure, not a production
shutdown fix or evidence of error-free upstream logs. Play worlds were untouched.

Native logs, original clips and screenshots are retained locally under
`build/verification/native-rendering`; they are not distribution assets. The
15.29-second Ponder recording was decoded and sampled (2560×1656 H.264). Audio
was not assessed. Failed command/setup captures are excluded from evidence.

## Release 0.6.0

2026-10-29, macOS arm64 / Temurin Java 21. Initial background checks preceded
the subsequently authorized native comparisons recorded above.
The tested binary SHA-256 is
`f0e9f715ee9b4e0991d7e3d885dce01c985655fbb9511eff743826dae31708ce`.
This is the 0.6.0 implementation before a metadata-only trailing-newline cleanup;
final binary SHA-256 is `6b6b5abc76d13fb9f65aa3945b2e110b2b9ad49db9f51d4582368670ad86a836`. ZIP comparison found only
that TOML whitespace difference, with identical parsed metadata and identical
classes/resources. The background evidence therefore applies to its implementation.
The downloaded release binary has SHA-256
`df67c9a85117719c0f60c684e6695c2cac2810a8ec9db3e9f5e6b2f1361b4a02`.
Its regular ZIP entries are byte-identical to the final local binary; only stale
empty directory entries differ. Native 0.6.0 checks used that published binary.

`JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ./gradlew check build probeJar`
passed: 63 JUnit cases, five release-tool cases, exact upstream hashes, packaging,
and the framebuffer server-side exclusion probe. Released-target transformations
cover the original Veil Iris injection plus the framebuffer wrapper, Aeronautics
balloon callback, both NeoForge tessellation overloads, and Sable model-data query.
Controlled framebuffer execution checks GUI/world/shadow behavior and exceptional
restoration. Balloon execution preserves the callback outside a shadow pass and
cancels it inside one. Material-scope checks cover recursion and exceptions.
These checks do not use native GL or establish visible shader results.

`python3 tools/startup_probe.py --server <installed-server>/game --java <java21> --scenario <case>`
passed all seven cases: `standalone`, `terrain-disabled`, `terrain`, `all-disabled`,
`unsupported-disabled`, `unsupported-enabled`, and `full`. Each uses a disposable
loopback dedicated server and removes its save afterward. Standalone and disabled
cases reached Done with no active adaptations and no exported transformed target
referencing Compat. Unsupported-target fixtures advertise version 999.0.0 without
target classes; disabled passes, while enabled rejects the exact terrain pin with
a feature-file remedy. The full installed server selection reached Done and exited
normally with 18 common/server adaptations. These fixtures do not exercise a
physical client or every possible feature combination. The full fixture still
logs upstream Struts/C2ME client-target warnings, Power Grid’s native-backend
fallback, resource warnings, and two nonfatal IslandField Mixin analyzer errors.
A matching installed-0.5.2 control also reached Done and produced both analyzer
errors; they are not introduced by this release. Successful startup is not a
claim of an error-free upstream log.

`python3 tools/probe.py --generation adapted --minimal --seed 42 --reopen --heap 6G --server <installed-server>/game --java <java21>`
passed native generation and reopen with matching ore counts. It uses only Compat,
Sky Archipelago, and the separate probe; no play save is opened. The scan took
30.17 seconds including startup/reopen. This supports preset resources and default
generation continuity, not a new multi-seed qualification of terrain algorithms.

Subsequent full-stack Prism checks rendered Ponder scenes with shaders on/off and
after reload, and inspected paired static/assembled material and balloon fixtures.
Single-block glass disappeared with shaders; the 0.6.1 record above owns the
reproduction and correction. Other flickering/gapped cast shadows were not reproduced.



Checks use Java 21, Minecraft 1.21.1 and NeoForge 21.1.264 with the exact upstream
pins in `build.gradle`. Results below distinguish integration checks from historical
terrain checks; a source inspection does not establish runtime behavior.

## Release 0.5.2

On 2026-10-29, `./gradlew --offline --no-daemon check build probeJar` passed
47 Java tests, five release-tool checks, pinned dependencies and package checks.
The release binary has SHA-256
`0228b2cd34f402114ba6bf57a02e29b6d03c2b294dd8f970b37bd523a828320e`.
Compared with 0.5.1, only optional integration selection and version metadata
changed among existing entries; `AnalogAudioCreateMixin` is the only added entry.
Terrain, physics, resources and other integration classes remain byte-identical.

The maintained `tools/probe.py --audio SELECTION` runner used the Create Sky
0.1.18 installed server as a read-only dependency source, Java 21 and Apple silicon
macOS. All five selections passed: `minimal`, `create`, `analog`, `pair`, `full`.
The first three left the adapter disabled. The pair and full pack registered
upstream `RadioMovementBehaviour`; a control drill handler remained present.
Radio `tick` and `stopMoving` completed on the dedicated server without client
class loading. Full startup no longer logged the movement-registration exception.

The same pair probe with `--mod` pointing to published 0.5.1 reproduced the
`NoSuchMethodException` and failed because the radio handler was missing. The matrix reports retain exact loaded JAR hashes; disposable worlds and raw
logs were removed after review. No live instance was modified.

This checks the applied common-setup Mixin and dedicated-server callbacks. It
does not establish audible playback, client GUIs, moving audio positions or all
contraption lifecycle cases. Those remain interactive checks of the upstream
handler; the adapter does not rewrite it. See [maintenance](README.md#analog-audio).

## Release 0.5.1

The 2026-10-28 release candidate has SHA-256
`1a340ef70560c78edb2e6d3416769fa068a784db54818418a9783a1381a91a9d`.
`./gradlew check build probeJar clientProbeJar` passed: 47 Java tests, five Python
release-tool checks, upstream pins and package boundaries. Compared with the
installed 0.5.0 JAR, existing terrain/adaptation entries are byte-identical; only
the entrypoint, optional integration selection and metadata differ, with new DH
classes added.

A fresh minimum-install `--generation adapted --minimal --seed 42 --samples 1`
completed its native block survey. The full-pack `--dh-pregen --dh-stop-after 2`
fixture reported generation active at stop, preserved the ordinary DH key without
creating a saved identity, and exited normally. It used C2ME 0.4.0-alpha.0.124,
Ponder 1.0.87, Radars 5.0.2, Fzzy Config 0.7.7+fix1, JEI 19.70.0.464 and
Core Library 1.1.26 alongside the pinned integration versions. The same full set
passed `--chunk-futures --seed 42`, including retained-ticket completion and ADQ
rejection cleanup.

The isolated physical-client material check converted ten sampled modded
terrain types plus stone, deepslate and grass through DH's real transformer.
All retained their 32-block column and opaque, texture-derived color; none used
the missing-texture sprite. This checks conversion, not every distance/shader
combination. The expanded preset and unrelated worldgen were not modified.

## DH integration

On 2026-10-28, `./gradlew check build probeJar clientProbeJar` passed, including
47 Java tests, five Python release-tool checks, exact dependency pins and
package-boundary checks. The tested artifact has SHA-256
`7d6f5fa4da9f159761f7e00d44e7f89c2b3caeb1cb58ac17014e694fcae0055b`.
Its existing terrain classes, adapters and resources are byte-identical to the
installed 0.5.0 release. Only the entrypoint, optional integration selection and
metadata changed among existing JAR entries; the DH classes are additional.
The unchanged version label does not identify this unreleased build's content.

| Check | Observed result | Artifact SHA-256 prefix |
| --- | --- | --- |
| Minimal `--generation adapted --minimal --seed 42 --samples 1` | Native block survey completed; optional DH and other integrations absent | `7d6f5fa4da9f` |
| Full-pack `--chunk-futures --seed 42` | ADQ retained chunk loading and rejection cleanup passed; process exited normally | `a234216e2436` |
| Full-pack `--dh-pregen --dh-stop-after 2 --seed 42` | Generation active at shutdown request; normal exit; default DH key retained without creating a UUID marker | `5cc05d968779` |
| Full-pack shutdown with `--dh-world-key` | Generation active at stop; normal exit; explicit UUID key observed in actual server wrapper | `7d6f5fa4da9f` |
| Default/opt-in cache key unit checks | Default leaves no marker; opt-in persists across reopen and config reset; malformed markers fail | `7d6f5fa4da9f` |
| Physical client constructor | Compat UUID keys preserved legacy folders; ordinary and similar-looking keys retained DH's native migration | `7d6f5fa4da9f` |
| Client/server lifecycle | Connected, changed Overworld → Nether → Overworld, disconnected and reconnected; normal shutdown | `7d6f5fa4da9f` |

A control using the installed pre-cleanup Compat artifact (`8fb487b85333`) also
exited normally in the dedicated-server shutdown fixture. This fixture verifies
normal shutdown and key preservation; it does not reproduce the earlier
integrated-client unload livelock or establish a timing improvement. The client
checks verify real hook application and ordinary lifecycle behavior; they do not
deterministically reproduce the concurrent race. Ownership interleavings are
covered separately by the concurrent tests.

Automated fixtures used disposable directories, copied configurations and loopback
ports. The network fixture enabled saved-world identity: the island Overworld used
its UUID key across reconnect; the foreign Nether kept DH's ordinary key. No
prediction provider or SeedGen was installed. The renderer client excluded
Connector, Surveyor and Hoofprint because Connector cannot launch in the tested
ModDev environment. owo-lib's development handshake mode was matched explicitly
on the isolated server. These are harness constraints, not production pack changes.

`ClientDhFluidProbe` measured lava/water states 0, 1, 7, 8 and 15 on a physical
client. Known columns were viewed natively and as LODs with shaders off/on, then
saved and reopened without clearing the DH database. A side-supported lichen
fixture reproduced the luminous column. Actual DH conversion changed stone, lava
and water to lichen's material/color; excluding lichen preserved each substrate.
The bare control and native lichen remained unchanged. The shader was Complementary
Unbound r5.9.3 + Euphoria 1.10.5, through Iris 1.8.14-beta.1 with DH 3.3.3.
See [evidence and workaround](lod.md#luminous-columns-and-fluid-colors).

An authorized live-client observation confirmed pale distant cliff patches and
native glow lichen, with orange native lava nearby. This does not establish that
all reported historical flowing-liquid anomalies share the same cause. Modded
fluids, other shader packs and the complete Connector client stack were not covered
by the isolated rendering check. No personal installation received the candidate.

## Release 0.5.0

The versioned release candidate passes `./gradlew check build` on Java 21.
All 40 JUnit results remain valid for unchanged classes; the five release-tool
checks, package boundaries and exact upstream artifact checks pass. Comparing
its production JAR with the qualified `2fdc7f4f…` artifact changes only
`META-INF/neoforge.mods.toml` (the mod version). Terrain, saved generation identity,
adapters and resources remain byte-identical. Native evidence below therefore
applies to the release code; this comparison does not replace release CI or the
post-publication server reopen check.

The published 0.5.0 JAR passed the Linux/Windows/macOS CI comparison and matched
local SHA-256 `8fb487b8533389f224d7133312e854f308a44c6c69ca5af8dc0d3a80f09405f0`.
Create Sky 0.1.16 installed that artifact on both local sides. The dedicated
server reached Done with its previous generation marker, server properties and
DH settings preserved. Startup error-message comparison found no new messages
relative to its preceding launch. BlueMap served its active schema-2 adapter;
the same DH pregen command was accepted once without completion polling. The
client's native empack prepare check passed without file changes; Minecraft was
not relaunched for a fresh visual check.

## BlueMap adapter

The restored adapter artifact is
`2fdc7f4fff387c42330a2f2e7e8cb6c75f409e2334ceea68448268e37b3cd3e3`.
`./gradlew check build` passed 40 JUnit and five release-tool tests. Byte comparison
against the preceding terrain candidate `58377f…` found changes only in the
entrypoint, optional integration predicate and BlueMap Java/browser implementation;
terrain classes and resources are identical.

The seed-42 minimal native generation/reopen check passed without BlueMap.
`tools/bluemap_probe.py` passed dedicated-server startup, selection, bounds,
reload and shutdown with only Sky Archipelago, Compat and BlueMap. Expanded bounds
were −128..768 exclusive; ordinary bounds were −64..320. A flat foreign world
was excluded and shutdown cleared the manifest in all three cases.

Headless Chrome inspected existing saved map tiles through the read-only preview.
At camera distance 1600, enabling the adapter reduced stretched empty-edge
triangles compared with stock; disabling and re-enabling restored the expected
renderer without page errors. The inspected dense island reached the 256 MiB
retained-geometry budget and reduced its detailed radius to 128 blocks. A view
from Y≈9 showed detailed underside geometry but also unresolved coarse sheets.
A 20,000-block pan and return exercised the floating-origin transition without
page errors. These checks do not establish complete stacked-terrain rendering,
whole-world visual correctness or compatibility with other BlueMap versions.

The full local server then reopened the existing marked world without changing
its generation identity. Its live webapp served schema 2 for `world` and headless
Chrome confirmed the adapter active. The same DH pregen command was accepted once;
no completion poll followed. Both stopped local instances received the artifact,
but no Minecraft client was launched for this pass.

## Earlier terrain regression evidence

The [pre-1.4 verification record](https://github.com/mannie-exe/skyweave-compat/blob/v0.22.1/docs/development/verification.md)
preserves prototype geometry, water, materials and cost observations with their
original artifact hashes. Those measurements do not qualify the current native
1.4 bodies or Dynamic Waters bridge. Retired enclosed-basin and underside-outlet
implementations are not supported modes of the current generator.

The maintained regression boundaries are:

- Fill, density, height and base-column queries must agree, including cross-cell
  companions, sparse-cluster clearance and saved-world identity.
- Interiors and classic carving must preserve supported rock shells. Large-vein
  and ore consumers must use the owning island's geological frame; an added
  remnant does not receive an independent resource or structure budget.
- Water support, lining, fluid ticks, mapped climate and aquatic spawn-height
  eligibility need separate checks. A low water block is not proof of a leak;
  an aquatic spawn predicate is not proof of a viable ecosystem.
- Metadata, optional map adapters and read-only climate queries must not load
  chunks or activate gameplay. A bounded disposable DH job is not an ETA or a
  completion report for a personal pregen task.

Current observations are recorded in the sections above and the owning
[terrain contract](terrain.md). Earlier [interior](hollow-interior.png) and
[waterfall](ocean-perimeter.png) images remain historical visual references.

## Audio and construct recovery, 0.12.0

Verified on 2026-11-01 with Java 21.0.13, NeoForge 21.1.264 and macOS ARM64.
Local native-check binary SHA-256:
`b46118d651e66dac8ed8a48667b8b7466c33b35fd80fa6a8186dfa8afb28af12`.
`./gradlew --no-daemon check build clientProbeJar` passed. Probes are separate
artifacts and are not distributed with the mod.

### Playback

Analog Audio `BshMjyFt` (0.1.5-hotfix.1) bundles the inspected AnalogLib 0.1.0.
A direct-WAV fixture delayed HTTP loading by five seconds. The unadapted engine's
reported native playback cursor remained about 5.14 seconds behind the level
clock; the initial adaptation reduced the median to about 0.13 seconds over 114
playing samples. This measures a native queue cursor, with chunk-sized precision,
not sample-perfect audible synchronization.

Two isolated clients then used native block interaction to insert cassettes on a
loopback dedicated server, swapping inserter/observer roles. A second HTTP fixture
introduced unequal request delays. Final playback medians were approximately
0.09/0.08 seconds for the first roles and 0.12/0.06 seconds after swapping.
Pause/resume, range departure and return, a late reconnect, replacement, repeated
short loops and an online YouTube cassette were exercised. Online loops retained
the native server cadence rather than producing duplicate track advances. Both
clients received native radio and speaker ranges of 128; a client at 100 blocks
still had an active emitter. Gains, attenuation and spatiality settings were not
changed. An eight-second window recording contained non-silent application audio.

The final binary's eight audio classes are byte-identical to the two-client tested
artifact (`11430d462500f815f60b7702bbac54ca8cf44b7aa235c4bb2ed0e05b7ad71ba6`).
Later binary changes concern recovery and release metadata. This does not verify
every media site, arbitrary network failure, all GPUs/operating systems or voice-chat
integrations. Initial internet silence remains expected.

The reusable client probe accepts `-Dcreate_sky_compat.probe.audio=true` and an
optional `-Dcreate_sky_compat.probe.audio.server=127.0.0.1:PORT`. The server fixture is
`tools/fixtures/audio/AudioProbe.java`. Compile it against the selected server and
mod JARs in a disposable harness, serve a finite WAV through loopback port 18849,
at `/stagger.wav` (including query variants), and use
`audiofixture cassette PLAYER VARIANT` before native insertion. The
fixture's `audio-control.json` accepts numbered insert/observe/disconnect/connect/
close actions. Install neither probe in a personal instance. The test initially
used a client-only disconnect without closing the connection; that fixture mistake
was corrected, rather than adding an engine workaround for a server timeout.

### Recovery

Exact native targets were Sable 2.0.6 and YiGD 2.0.13 (`Axk4bfXT`), with Cloth
Config 15.0.140. The minimum fixture has no Create or Aeronautics dependency.
Both minimum and full-pack runs passed save and cold-reopen phases:

```sh
python3 tools/linear_probe.py --scenario recovery --minimal \
  --yigd /path/to/yigd.jar --server /path/to/server/game \
  --pack /path/to/pack --java /path/to/java
```

Omit `--minimal` for the full pack. The same finite fixture verified:

- Actual plot placement, a dry grave and projected world coordinates.
- Ordinary deck death and an ordinary world death retaining native YiGD placement.
- Loss before death, a supported world fallback and loss after grave placement.
- Translated/rotated claiming, remote/non-owner refusal and stale-address refusal.
- A placement veto retaining the same recovery, followed by a permitted retry.
- Unloading remaining distinct from destruction without a second recovery record.
- Native Simulated disassembly, cold reopen and a single owner claim.
- Accessories-equipped backpack, 13 named emeralds, goggles and boots restored
  once, with no loose duplicate backpack. Competing death placement was disabled.

The headless fixture uses FakePlayer; its blocked retry calls the same deferred
placement operation directly because the fake is not an online player. It does
not independently prove every real-player collision or online retry schedule.

A separate full-client fixture (`probe.recovery=true`) verified natural grounded
Sable tracking, native death and respawn, a client ray hitting the real plot grave,
and native right-click claiming. It restored seven diamonds, the equipped backpack
and nested contents, goggles, boots and the expected vanilla death drop of 98 XP.
Screenshots confirmed a dry model and matching world-coordinate notices.

That visual fixture caught two adapter defects before publication: an unspecified
waterlogged state inherited the block's true default, and bottom-face projection
could round down at a transformed block boundary. Air placements now set the state
explicitly; location display and distance checks project block centers. Its ship
setup waits for client-world readiness after an early-entry Create registry error.

Protection evidence covers vanilla checks and the cancellable placement event,
not every external claims system. Arbitrary construct splitting, other inventory
mods, incompatible saved metadata versions and foreign relocation tools remain
unverified. See the [recovery contract](recovery.md) before changing those boundaries.

The published 0.12.0 binary is `ad94fd9756f4aa78a1d75f42fb64a1a772491f03b0472a26410795325faece9b`.
Its only entry difference is a generated `getFocused()` bridge in the configuration
overview list, forwarding to the existing superclass method. All adapter classes
match the native-check binary; the published JAR also passed the full recovery
fixture. Version 0.12.1 adds missing English audio, recovery and Linkage labels and a check
that every feature has one; it does not alter adapter behavior.

The published 0.12.1 JAR is
`ad42b6566763d35fbec46f577645e4f64bda85dd7c53cee60d4d0da76509e9ed`.
All 246 production classes match published 0.12.0. The full native recovery
fixture passed again with this exact public artifact; the final check/build has
93 passing unit tests, including startup feature-label coverage.

## Redstone adapters, 0.14.0

Java 21 / NeoForge 21.1.264 checks used unmodified Link GUI 1.21.1-1.9.2 and
Additions 1.0.5 with Create 6.0.10. `check build clientProbeJar` passed.
The final local binary SHA-256 is
`d38184414450a4408a33746fd8d05404fa212eddb544b07994a81d8a70fc6338`.

The finite redstone fixture reproduced adventure frequency editing and
placement-query world mutation with the adapters disabled. Enabled runs covered
GUI-only, Additions-only, both targets without Sable, a full pack and standalone
Compat startup. Native handlers verified authorized edits, four preset rows,
invalid-slot refusal, ghost-item counts, a protection veto, stale/distant menus,
plain-link relocation after menu closure and duplicate-move refusal. The full
fixture additionally verified projected Sable menus, same-frame relocation
eligibility and cross-frame rejection. Its ship check exercises the permission
boundary, not a long-running moving-vessel simulation.

Native block checks covered AND partial/full inputs and support loss, the OR
default, Crossroad strong-output attenuation and feedback clearing, native wrench
rotation, successful axe scraping and canceled scraping. The full pack resolved
all nine authored block drops. These checks do not establish every circuit timing
combination, external claim system, copper-door interaction or Tiny/Void Link path.

The isolated client opened the real link menu, selected an emerald ghost frequency
without consuming its inventory stack, and opened a vector-thruster channel. The
corrected move button was inactive on that machine. All six Additions Ponders
compiled and sought their final frame alongside existing machinery tutorials.
The lit inverter was visible without an opaque texture background. Configuration
catalogue navigation showed the native click-mode file under Client / Link GUI;
its shared permission and relocation switches remain under Server / shared.

Early fixture failures came from incorrect synthetic method targets and checking
Create behavior before initialization. Both were corrected before publication;
foreign resource warnings already present in the full pack are not claimed fixed.
