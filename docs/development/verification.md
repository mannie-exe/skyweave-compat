# Verification

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

## Ocean prototypes and biome materials

The ocean/material candidate has SHA-256
`58377f93469acc17d5fa4831bdd8ea479fdc7f43d8e3df5550f8dd75f94271ab`.
`./gradlew check build probeJar` passed with 40 JUnit and five release-tool tests.
Ocean variants remain disabled by default. The native fixtures enabled them with
basin/coastal/perimeter selection chances of 0.2 each to find examples efficiently;
those probabilities are not proposed pack defaults.

`tools/probe.py --oceans --ocean-config PATH --fluid-ticks 600 --heap 6G`
passed on minimal seed 42 and full-pack seed 8675309. The full run also supplied
the candidate generation configuration explicitly. Each compared 1,835,008
native fill/query blocks and inspected three distinct water forms after fluid
ticks. The closed and coastal examples had no sampled floor/rim holes or gaps
in planned sources. Perimeter islands deliberately lack a retaining rim; their
unsupported edge columns became falling water, rather than failed containment.

The full run stored cold-ocean, ocean and warm-ocean habitats, with native aquatic
vegetation and applicable aquatic spawn entries. Its coastal sample contained
1,893 beach columns, including 1,806 exposed sand columns. That outlet flowed
onto a nearby ledge; a coastal spill need not immediately become a deep waterfall.
The perimeter sample reached Y=159 after 600 ticks. Vegetation persisted inside
the basins while some edge plants were lost as perimeter water began flowing.
No coral blocks were observed; this does not establish their absence elsewhere
or certify coral acquisition. Natural spawning populations remain unmeasured.

Native work took 61.6 seconds / 3.07 GB peak RSS for the minimal run and
55.4 seconds / 4.40 GB for the full run. These are different generated examples,
with conservatively forced regions, not a comparative performance benchmark.
The full search stopped after finding all three forms; its counts are not an
occurrence survey.

A second minimum-installation seed-42 run used 3,200 fluid ticks on the same
production artifact. Perimeter water reached Y=-128; planned sources remained
intact, and the enclosed/coastal samples retained their floor and rim. Native
work took 201.6 seconds with 3.24 GB peak RSS. This bounded observation does not
establish long-term loaded-world cost or ecological balance.

A full-pack seed-42 material probe on this candidate found 654 mud blocks within
the top three layers of 256 mangrove columns. The earlier minimal check found 655
on `61ddfbc6…`, before the surface-override precedence guard. These establish native
mud placement in both installations, not all override combinations. The adapter
changes actual surface writes because upstream's fixed surface-material enum
cannot retain mud. Reports retain exact hashes and generation settings.

The same artifact completed a full-pack seed-42 native generation/reopen run:
27 chunks, including a cross-cell companion, retained matching generated/query
blocks and reopened ore counts. This used default-off ocean variants and explicit
candidate geometry; it does not certify every optional ocean layout.

The full seed-42 resource survey sampled three anchors with 49 strata each.
It records ore in 16-level reference-depth bands and counts sampled ore adjacent
to air or fluid, excluding chunk-edge columns to avoid neighbor loading. All
three samples contained exposed zinc and iron. Two contained raw copper blocks.
These are witnesses of generated resources, not guaranteed access or per-island
budgets. Native work took 41.9 seconds with 7.42 GB peak RSS under a 6G Java heap;
process RSS includes memory outside that heap. Reports and Atlas retain the counts
and exact probe hash separately from area-weighted estimates.

### Dedicated background generation

`tools/probe.py --dh-pregen --seed 42 --heap 6G` with explicit candidate geometry
completed on the full installation and the same production artifact. DH's native
pregeneration future completed for a 32-chunk-radius job centered at (1282, -673).
Saved region-header entries increased from 133 to 8,122; padding and unfinished
generation stages can contribute to that count. The native job took 37.3 seconds
(60.9 seconds including startup), with 3.77 GB peak RSS. This disposable test
exercises the dedicated DH path; it does not predict 1024-radius completion time.

### Client review

An independently installed empack reference snapshot loaded the full client set
and created seed 8675309 with the recorded OVERLAP policy and boosted ocean fixture
settings. Shader-off spectator review used night vision for interior inspection;
it does not represent ordinary cave lighting or a survival traversal test.

The hollow at (-725, 262, 933) contains a substantial chamber with rock pillars,
exposed ores and native sculk. Its surrounding terrain has openings and narrow
outer branches. The noise-cave sample at (-997, 139, -1632) contains native lush
decoration and exposed deepslate. Classic carvers remained enabled in both.
These views support retaining the current composition for playtesting; they do
not prove every chamber has a safe or connected entrance.
The client save reopened successfully with its recorded generation identity.

![Hollow interior with native decoration](hollow-interior.png)

The coastal example near (-22443, 453, 792) has a sand shore and aquatic plants.
The perimeter example near (-20214, 294, 4050) retains a submerged rock body and
develops a broad falling-water curtain. Its visual footprint is substantial;
keep this experimental variant disabled by default rather than treating it as
an inexpensive substitute for an ordinary companion island.

![Experimental water island after fluid flow](ocean-perimeter.png)

## Local water

The source-water/lining candidate has SHA-256
`fe7912fb9b656c34e20e21fd9c53475499732cfceb57b9c88ad4d0413bfd7e21`.
`./gradlew check build probeJar` passed with 38 JUnit and five release-tool tests.
The boundary tests include deep, narrow bowls whose stepped floors need vertical
lining as well as a horizontal bed. Native water probes use the installed server
as read-only input and delete their isolated worlds afterward.

| Check | Observed scope |
| --- | --- |
| `--water --seed 42 --fluid-ticks 600 --heap 6G` with explicit candidate geometry | Full mod set: 1,835,008 fill/base-column block comparisons, water-surface/solid-floor height agreement, no sampled floor/rim holes or air gaps inside planned basin sources, flowing overflow and underside outlets |
| `--water --minimal --seed 8675309 --fluid-ticks 3200 --heap 6G` | Same checks over a second seed; underside water reached Y=-128 while its basin remained intact; cold surface ice persisted |
| Native carvers disabled, full seed 42 | Control removed the unexpected air beneath basin sources, identifying classic carving as the cause; source-water protection fixed it with carving enabled |

The full 600-tick run used `6f55d160…`. The candidate above adds the saved
`waterPolicy` identity field without changing terrain composition and was used
for the 3,200-tick run. Reports retain complete production/probe hashes and installed
mod lists. The full run took 47.2 seconds for admission, generation and 600 ticks,
with 3.57 GB peak RSS. The longer minimum-installation run took 178.6 seconds with
2.38 GB peak RSS. These bounded observations are not a throughput benchmark.

The 21-by-21-cell admission surveys found 16 basins among 232 anchors in seed 42
and nine among 207 in seed 8675309. Terrain support rejects some candidates after
the configured probability draw. Three selected regions per seed provide the
material/fluid witnesses; this does not establish global occurrence rates.

The water-animal height predicate passed at positions with real water clearance;
out-of-region and foreign-generator controls retained their original sea level.
This verifies height eligibility, not natural spawn populations, ocean vegetation
or complete habitat. Native springs can flow below a sealed basin independently;
low water blocks alone are not a containment test. Visual appearance, long-term
ecology and optional ocean variants remain separate checks.

## Interiors and large veins

The `islands_v1-prototype-10` material/interior candidate has SHA-256
`948586b2a28f9030d959fe022b81dd24cf0f3cf5ea7cb0da59e22e8a54b8dee9`.
`./gradlew check build probeJar` passed, including 36 JUnit and five release-tool
tests. Native commands below used `tools/probe.py`, an installed server as read-only
input, Java 21 and `--heap 6G`. Full-pack runs used the explicit candidate geometry
JSON rather than inheriting the installed server's older tuning.

| Native check | Observed scope |
| --- | --- |
| `--overlap --minimal` | Six clusters across seeds 1, 42 and 8675309; upper exterior intervals unchanged, sampled retained rock increased 0.6–1.2% |
| `--interiors --minimal --seed 42` | Baseline/noise/hollow sections; 86 independent direct/snapshot comparisons |
| `--interiors --minimal --seed 8675309` | 108 independent comparisons, including uncached exterior/interior planning stages |
| `--veins --minimal --seed 42` | Raw copper and iron agree between native noise fill and base-column queries; both survive in completed chunks |
| `--coverage --minimal --seed 42` | City eligibility, query-scope restoration, ordinary-generator and locate-preview controls pass with interiors enabled |
| `--generation adapted --seed 1 --reopen` | Full mod set including C2ME; 6,193,152 fill/query block comparisons and unchanged reopened ore counts |
| `--islands --seed 1` | Full-set generated samples across three anchors; supported spawn and spatial resource samples |

The overlap comparison used the pre-interior exterior baseline. The seed-42
interior sections precede the planning-stage additions. Full-pack generation and
the island survey used material/interior artifact `d556837e…`; the later candidate
adds scoped planning queries without changing complete terrain composition. Reports
retain exact hashes; these checks do not certify later untested changes.

The two noise-interior examples removed 6.5% and 7.3% of sampled exterior rock.
Hollow examples removed 21.5% and 23.1%, with largest sampled vertical gaps of
127 and 125 blocks. These are selected sections before native classic carving,
decoration or fluid updates, not global frequency estimates or proof of connected,
walkable caves. A baseline anchor can overlap a differently selected companion;
its footprint is not an assertion that every contributor has baseline interiors.

Raw copper was observed at (−959,292,−1237), reference Y=34; raw iron at
(3682,182,−1788), reference Y=−32, in seed 42. The sparse witness search missed
raw iron, despite finding iron ore and tuff. Inspecting one complete noise-filled
chunk around a witnessed iron vein found it. This targeted search establishes
material generation, not scarcity or mining accessibility. The broader island
survey also remains a sample, not a complete ore inventory.

The full generation/reopen run took 43.6 seconds including startup and shutdown,
with 3.20 GB peak process RSS. The three-island resource survey took 64.9 seconds
with 7.10 GB peak RSS at a 6 GB maximum Java heap; RSS includes native allocations.
These single observations are not a throughput benchmark. Cavern appearance,
entrance usability, support after native carving, and fluid behavior require
separate visual and runtime checks.

## Generation coverage

The coverage candidate has SHA-256
`d0416871fbb09c9d8d04d8efe6c2d1028bb78aeac559efc2d81b4fb670015977`.
It adds ancient-city eligibility and locate-preview corrections, plus a saved
structure-policy identity field. Existing terrain classes/resources are unchanged
from the terrain baseline except that guard and the new registered hooks.

`./gradlew check build probeJar` passed. Native commands used Java 21, `--heap 6G`
and the installed server directory as read-only dependency input:

| Command arguments to `tools/probe.py` | Evidence |
| --- | --- |
| `--coverage --minimal --seed 42` | 19 biome controls, ordinary-preset exclusion and locate-preview controls |
| `--coverage --seed 1` | Full mod set; 16 biome controls and the same exclusions |
| `--structures --seed 42` | Full mod set; realized underground layouts, locate and Lootr conversion |
| `--surface-structures --seed 42` | Full mod set; surface/water-family survey and Lootr conversion |
| `--surface-structures --minimal --seed 1` | Second seed and minimum-install surface/water survey |
| `--generation adapted --minimal --seed 42 --reopen` | Saved-world identity, terrain/query agreement and reopened ore counts |

Reports under ignored `build/verification` identify production/probe hashes,
installed JAR names and runtime selection. The archive inventory additionally
hashed 389 archives including nested JARs; its definition filter did not include
every tag, preset or noise-settings resource. Runtime resolution, not filenames,
established 53 possible Overworld biomes, 157 top-level features, 240 registered
placed features, 203 configured features, 35 structures and 21 structure sets.
The latter registries include other dimensions. Resource stacks captured 1,118
worldgen, biome-modifier and worldgen-tag paths with pack priority.

Typed traversal from Overworld biome features reached 182 registered placed and
149 configured features. There were 61 height-range paths and no nested
height-range path; blue ice was the only untagged root using one. Six modded roots
were Create zinc/striated Overworld and four Propulsion platinum entries. The only
non-vanilla registered structure was `skyvillages:skyvillage`. Effective resources
confirmed that the pack's Sky Villages processors/pools override their upstream
counterparts. This excludes neither arbitrary runtime callbacks nor future mods;
rerun the inventory after dependency/config updates.

### Corrected eligibility and lookup

Eight sampled Deep Dark footprints failed the baseline city midpoint precheck.
The correction made all eight eligible; the 19 seed-42 controls included 14
non-Deep-Dark columns. All 16 seed-1 controls also matched the expected projected
biome. Trial-chamber queries and ordinary Sky Archipelago eligibility were unchanged;
query scopes were restored. Native support checks still rejected some layouts.
Ancient cities were not universally absent before this correction.

A matched seed-42 control JAR omitted only `StructureLocateCheckMixin`. The city
and mineshaft had valid starts and realized blocks, but locate reported absence
even after their source chunks reached FULL. With the hook, both resolved; the
stronghold and trial chamber remained discoverable. Separate controls retained
known-empty chunks, rejected ocean-disabled WATER structures, and compared five
ordinary-preset preview results with direct native calls.

| Structure | Accepted / queried, seed 42 | First realized layout |
| --- | ---: | --- |
| Stronghold | 2 / 8 | Eight pieces, 12 portal frames, one spawner, one chest |
| Trial chambers | 2 / 4 | 254 pieces, 13 trial spawners, 13 vaults, 18 chests |
| Ancient city | 1 / 16 | 56 reinforced deepslate, 20 chests and sculk/shriekers |
| Mineshaft | 2 / 4 | 174 rails, three spawners, one chest minecart |
| Badlands mineshaft | 0 / 16 | No accepted candidate; realization unverified |

All four realized types were locatable in the full set. The matched minimal
control/correction runs took about 46 seconds including startup, generation and
shutdown; they do not isolate lookup overhead. In the final full run, the first
stronghold lookup took 2.33 seconds; the other three were below one millisecond
with candidates already generated. This is not a bound on a cold, wide-radius
search. Upstream locator Y=0 is not a safe landing position.

### Surface structures and containers

Seed 42's full-set survey realized plains, desert, savanna and snowy villages,
an outpost, both temples, a witch hut, trail ruins, ordinary/mountain ruined
portals and both shipwreck variants. Seed 1's minimum run also realized plains
villages, an outpost, jungle temples, trail ruins, portals and both wreck variants.
Every realized sample's locate check succeeded. Counts are from at most 16 selected
candidates per type, stopping after two acceptances; these are not occurrence rates.

Neither survey accepted a taiga village, mansion, igloo or buried treasure in its
selected candidates. Their upstream routes exist, but usable realization remains
unverified here. Ocean monuments and both ocean-ruin families had zero starts,
consistent with the explicit ocean-disabled WATER policy. These failures should
not all be described as missing generation.

Full-set runs loaded a 5×5 neighborhood around one generated chest, then allowed
100 server ticks. Both the stronghold and plains-village samples converted to
`lootr:lootr_chest` with their original loot-table IDs preserved. Earlier immediate
captures showing `minecraft:chest` were before this lifecycle. Native table rolls
also contained Camping's injected enderbag. Per-player Lootr entitlements, all
container types and completed survival encounters were not exercised.

### Remaining limits

The [terrain document](terrain.md) owns the source-backed differences: custom
surface palettes, separately composed interiors and vein materials, ocean-dependent
acquisition, native carver aquifer coordinates and column-overlap handling.
These require separate design or focused realization checks, not blanket parity
patches. No additional installed ore/structure registration was missed by the
resolved inventory. That conclusion does not cover every arbitrary code callback.

Surface access to caves, rare-biome structures, natural fluid behavior and
long-distance progression remain playtest concerns. No new visual
flight or survival session was performed for these two structure fixes; native
block/loot/query checks are the evidence above. Prior silhouette, weather and
terrain surveys below remain attributable to their original artifact.

The first full coverage capture encountered a DH shutdown exception and was not
counted as passing. Later captures allow settling ticks and exited cleanly; this
is harness lifecycle evidence, not a DH production fix. Disposable worlds were
removed. The playing server, world and DH task were not changed or polled.

## Horizontal arrangement

Compare the original 90-block jitter with the 750-block candidate at the same
2400-block indexing grid. Both reports contain five seeds, native biome admission
and descriptor previews to 16,000 blocks. For anchors within 10,000 blocks, find
the nearest captured anchor and fold its direction modulo 90 degrees. Directions
within 10 degrees of a cardinal axis fell from **149/155 (96.1%) to 52/136 (38.2%)**.
The accepted anchor count fell by 12.3%. Changes include clearance and displaced
biome admission as well as jitter; this is not an isolated jitter benchmark.

| Seed | Anchors | Mean / largest nearest-center distance | Minimum support gap | Sampled shore gaps |
| --- | ---: | --- | ---: | --- |
| 1 | 30 | 2507 / 3490 | 588 | 1058–1405 |
| 42 | 26 | 2497 / 3737 | 461 | 1005–1404 |
| 8675309 | 25 | 2510 / 3103 | 421 | 1378–2015 |
| 424242 | 25 | 2348 / 3366 | 425 | 873–1268 |
| -3095298318114116683 | 30 | 2395 / 3427 | 385 | 1027–2637 |

Distances are blocks, rounded. The lowest sampled support gap remains above the
350-block policy. Three nearest-center pairs per seed supply the shore estimates;
these are not global nearest-shore proofs or navigated journeys. The retained
2400 grid indexes candidates, not equally spaced accepted clusters.

![Equal-scale baseline and candidate descriptor layouts](cluster-layout.svg)

The plot shows descriptor radii, not actual coastlines. Seed 42's bounded query
check exercised 225 cells: 137 eligible candidates, 114 accepted and 23 rejected
by clearance. Reverse and parallel query order matched. All 29 companions crossing
cell boundaries retained the correct geological owner. Unit tests additionally
exercise exact clearance boundaries, priority ties, negative coordinates and the
derived neighbor search against exhaustive candidates.

## Terrain and resources

Full and minimum seed-42 runs each compared **6,193,152 generated/base-column
blocks** across 27 chunks, including a companion across a cell boundary. Both
reopened with matching ore counts. The minimum installation contains only Sky
Archipelago, Compat and the disposable probe alongside Minecraft/NeoForge.
An isolated physical client also reached the title screen with Sky Archipelago
and Compat and no BlueMap; that check establishes loading, not survival gameplay.

Six anchor surveys used 49 spatial strata each. Counts combine ordinary and
deepslate ores in sampled anchor-owned columns, not complete island inventories.

| Seed / center Y | Observed block Y | Headroom | Zinc | Diamond |
| --- | --- | ---: | ---: | ---: |
| 42 / 269 | 63–315 | 452 | 368 | 54 |
| 42 / 337 | 51–399 | 368 | 415 | 79 |
| 42 / 321 | 118–370 | 397 | 515 | 137 |
| 8675309 / 354 | 105–405 | 362 | 372 | 65 |
| 8675309 / 281 | −32–343 | 424 | 295 | 33 |
| 8675309 / 292 | 85–331 | 436 | 199 | 30 |

All six contained zinc and diamonds. This does not guarantee mining access,
quantities per trip or scarcity for every spawn. No sampled column had the
probe's eight-block-deep surface opening; absence in these strata does not prove
there are no cave entrances. Surface access remains a playtest concern.
Sampled straight center lines had longest void intervals of 1494–2300 blocks.
They are neither altitude-safe flight routes nor movement-time measurements.

Shader-off client review inspected the current seed-42 body and underside,
plus a contiguous cold/temperate boundary. Initially incomplete distant terrain
was excluded from the visual evidence. The retained body view shows continuous
taper rather than a broad bottom slice; it is not a proof for every archetype.

![Generated island body](island-profile.png)

## Snow, biomes and integrations

Contiguous 48×48 areas on seeds 1 and 42 covered temperate, cold and transition
regions. The probe compared stored quart biomes, smoothed/player-facing queries,
reference-height temperatures, tags, surface materials and exposed snow. It then
removed snow layers and applied an explicit native precipitation sweep. No warm
stored-biome snow blocks or warm-weather snow columns were found. This is a
controlled weather invocation, not hours of observed natural weather.

Boundary disagreement between stored and smoothed biomes was observed: 83 of
2301 sampled columns in seed 42's transition area. Cold-biome surface rules can
select snow independently of altitude cooling. These are plausible explanations
for a grainy edge, not a reproduction of the friend's unidentified location.
The cause of that particular observation remains unresolved. No global biome
smoothing or additional temperature fix was justified.

![Cold/temperate transition](snow-transition-detail.png)

Seed 42's cave/feature check found lush caves, dripstone caves and deep dark with
matching stored biomes and unchanged surface projections. Deep dark retains
upstream's fixed −48 proxy and lower-column restriction; it does not use exactly
the same frame as the other two targets. A direct geode placement and warm/cold
portal material fixtures passed. A seed-1 high-portal search found no suitable
fixture; that run does not establish a production defect.

Seed 42's village plan retained the zero-to-three allowance, with target counts
`[26,7,3,1]`. Eleven starts were accepted and two fully generated; reopening and
order checks matched. Native delivery endpoint checks passed. These establish
placement and endpoint handling, not completed deliveries or route budgets.
Pressure, client climate scope, classic carvers and geological feature mappings
remain in their owning terrain adapters. No new noise caves or aquifers were added.

## Cost and limits

A shared benchmark queried 122,412 columns and requested 18 chunks against the
original installed 0.4.0 and the candidate. Column time was 2.97 → 2.82 seconds;
chunk time was 1.42 → 2.28 seconds; peak process RSS was 3.03 → 3.11 GiB. The
terrain differs and this is one workload, so the chunk difference is not a causal
estimate of clearance overhead. The larger island surveys peaked near 6.6 GiB RSS
with a 6 GiB Java heap; process RSS includes non-heap allocation.

Local-priority rejection is bounded and order-independent, but not maximal: a
candidate may lose to an eligible neighbor that itself loses elsewhere. The
measured density reduction was accepted without changing island size or weakening
clearance. Longer empty crossings remain possible outside the sampled seeds.

Existing markers reject incompatible generation. Do not delete a marker to adopt
this layout in an old world. Nether/End and ordinary-generator scope were retained;
this pass did not survey every foreign structure, complete cave connectivity or
repeat the earlier release's cargo flight. The previous bounded DH pregen check
is historical evidence, not proof that a new overnight pregen has finished.

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
