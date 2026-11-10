# Verification

Evidence below qualifies named artifacts only. Build and native fixture commands
live in [testing](testing.md); adapter contracts and update checks live in their
domain guides. A later checkout needs its own relevant checks.

## Village fitting and discovery 0.32.0

The combined release candidate has SHA-256
`95c2a06c688280b1118a166caa2c6706d4c59a529a5222dcfe77a3666fd858a0`.
It includes the Wayfinder tuning below. Required Gradle checks pass 297 JUnit
tests plus package, upstream, Ponder and tool checks. Retained receipts are in
`.git/captures/village-fitting/` in the development checkout.

The final combined JAR differs from the native village/command/client candidate
only in NeoForge version metadata. Its Java classes and English resources are
byte-identical. Earlier receipts identify their own artifacts; the separate
material fixture also used a task-only candidate whose terrain classes match.

- The four recorded villages retain 105, 131, 68 and 107 pieces. Physical audits
  cover 105 inhabited buildings and 4,983 footing columns: every building meets
  the 90% support-within-three-blocks policy and no tested authored floor is
  missing. Native road-fallback controls preserve registered elements and input
  pieces, add reciprocal junctions, and reject the proven-void control. These
  checks do not establish universal walkability for every third-party template.
- The actual shipwreck foundation writer changes 889 generic grass/dirt/stone
  writes to 872 sand and 17 sandstone writes. Frozen thin-skin, overlapping-write,
  context-release, cold-surface and fluid/block-entity palette controls pass.
  Material selection does not change native fill extent or permission.
- Across two matched 24-cluster allowance samples, the first four positive
  village clusters per seed commit eight candidate villages versus five baseline
  villages. Allowances are identical. The second-seed candidate preserves native
  piece digests across save/reopen and agrees after warm/reverse-evicted planning.
  These eight positive clusters do not establish a population encounter rate.
- The first seed takes 137.0 seconds including startup versus 119.0 for baseline.
  Its initial per-owner timings can include spawn-warmed caches. The second-seed
  candidate explicitly evicts fit plans; its 253.1-second total includes reopening.
  These are bounded fixture costs, not cold JVM or general server benchmarks.
- Source/reference aliases and reversed discovery order yield one physical match;
  distinct equal-bounds layouts yield two. Native jigsaw reload retains identity
  and reconstructed reference bounds. A one-block-radius control includes a layout
  whose source lies outside the query. All 88 registered village definitions and
  the remaining managed structure types have qualified metadata envelopes in the
  selected stack; this does not certify arbitrary addon assemblers.
- Real family-search jobs retain positive results after saved-start reopening,
  preserve clickable navigation and cancellation, and explain both complete and
  partial empty results. Operator checks survive an unrestricted sibling command
  registration. Unknown layout/placement contracts retain explicit partial coverage.
- The full native client loads a copied stopped fixture. Local chunks and visible
  meshes are checked before the jungle/savanna captures. Localized settings search
  opens the fitting group, reads the seven defaults, disables saving an invalid
  support fraction, and restores clean draft state at GUI scales four and three.

Combined-artifact minimum and unsupported-disabled dedicated startup pass. The
combined native client also opens the copied integrated fixture successfully.
The qualified terrain-only startup and full material fixture are recorded separately.
New-generation behavior uses the changed saved identity; no existing save was
silently converted during verification.

Wayfinder's combined-artifact minimum fixture verifies silent default feedback,
two immediate successful warps without cooldown bookkeeping, and actual upward
placement with the 16-block default. Explicit range controls cover hazards,
headroom, shape support, cancellation, permission/session changes and lease
release. The retired hotkey-warning field is read without rewriting the file;
explicit cooldown and other retained values remain authoritative. These controls
extend the broader 0.31.0 transport/client evidence below; they are not another
WAN multiplayer or arbitrary terrain survey.

## Wayfinder 0.31.0

Minecraft 1.21.1 / NeoForge 21.1.265 / Java 21 passes the required
`check build probeJar clientProbeJar` command with 282 JUnit tests and the package,
upstream, Ponder and tooling checks. Native minimum/full-height receipts identify
candidate SHA-256 `9339c656f98f50c4ec901ddf8174cf4f3594121bc6302011e87cd2a8ab3d547d`.
Retained receipts and the task checkpoint live under `.git/captures/wayfinder/`.

- The minimum dedicated fixture contains only Compat and its probe. Native
  commands land on full blocks and slabs, preserve direction/cooldown, reject
  hazards/headroom/ceiling ascent, respect a native teleport-hook veto, reject
  LOD candidates without real support, and exercise real leaves, stairs and scaled
  player bounds. Full-pack checks additionally use Create casing and actual
  assembled Sable 2.0.6 craft before/behind the target, plus storage-frame guards.
- Native authority checks cover non-operators and legacy tags, malformed/off-ray
  candidates, stale/duplicate replies, permission loss, movement and disconnect.
  Reconnecting the same UUID rejects packets from its retired player object.
  Dimension travel cancels work, concurrent admission remains at four jobs,
  cancelled cold destinations release four leases and drain their requests,
  and pending server shutdown clears jobs/tickets and stops its owned worker.
  Denied hotkey starts produce no message or admitted work by default; commands
  still explain denied access. A separate server-policy fixture permits a
  non-operator only after an explicit policy change.
- The real custom-height fixture uses native bounds -128 through 767, with an
  actual arrival at Y602. DH 3.3.3 imports/readbacks cover X8192, 65536, -8192 and
  29000000 with a real top at Y651. These are database/height controls, not
  four client teleports or a survey of arbitrary terrain.
- Actual full-client G queries arrive at 8192 and 32768 blocks with the native
  target initially absent client-side. The final 32768 replay interrupts an
  earlier progressing query with same-JVM reconnect, then completes a fresh
  query and leaves zero jobs, destination requests and leases. Its maximum
  observed active server-tick gap is about 72 ms; this is a fixture observation,
  not a general performance guarantee. Prepared native corridor/LOD data belong
  to the fixture; product queries do not generate that corridor.
- TCP tests connect a minimum client to an independently running loopback
  dedicated server: native operator G arrival, non-operator denial and reconnect
  pass. A server without Compat negotiates no channel and receives no Wayfinder
  request. These complement embedded native authority tests; they do not claim
  a two-person WAN or remote DH-server transfer test.
- Native client checks cover held input, rebinding/persistence and suppression in
  chat, inventory and settings. G has no conflict in the selected pack registry.
  Core-disabled client loading removes the binding/channel. DH-disabled loading
  preserves native controls and refuses unavailable distant data without movement.
  Dedicated minimum, all-unsupported-disabled and active-unsupported startup
  controls qualify class-loading and exact Sable-version rejection.

The full-client replay uses candidate SHA-256
`bc736fdfd04e2ad0c05c0bfdf69c0eb811428d259d37c23f5e7304f7ad3c2d51`.
Archive comparison with the 0.31.0 candidate identifies only version metadata,
English wording and two diagnostic-string class changes; targeting, transport,
input and lifecycle classes are byte-identical. The final server fixtures use
0.31.0 itself. No target indicator is implemented, so shader-indicator rendering
is outside this verification scope.

One full integrated startup failed before querying inside Hoofprint's
`HoofprintMapStorage.get` / Surveyor terrain-update callback with a
`ConcurrentModificationException`; the retry loaded and completed. The stack is
retained separately. This is an upstream observation, not a Wayfinder repair or
proof of general Hoofprint stability.

## Discovery and capability integration 0.30.0

The release uses Minecraft 1.21.1, NeoForge 21.1.265 and Java 21. The required
`check build probeJar clientProbeJar` run passes 268 Java tests with no failures,
plus package, upstream, Ponder and release-tool checks. Native receipts under
`.git/captures/misc-changes/` identify the tested unpublished artifacts; a later
version label alone is not a fresh runtime check.

- Managed family/tag/exact-ID searches return positive native villages, including
  saved starts after a forced negative new-generation precheck. Lazy/eager plans,
  reordered/concurrent use and cache eviction controls agree. A 40,000-block full
  search returns useful bounded partial results; it does not prove full coverage.
- Sky Villages selected-site enumeration agrees with native placement in positive
  and negative-coordinate controls. One positive comparison reduces 444 grid
  checks to one candidate. A mixed query finds managed and ordinary random-spread
  starts, qualifies an unsupported ring entry and preserves results after reopen.
  A separate 16-candidate control stops with explicit incomplete coverage.
- A follow-up lifecycle fixture against the published 0.30.0 JAR (SHA-256
  `fbc64f79844e86824d87c2b77af608ff3cb73edf872afd97a272236b93e9c79c`)
  verifies both locators cancel after native caller removal, travel to the End
  and actual Overworld unload. An independently leased FULL chunk completes
  alongside the searches; unload leaves no search or fixture tickets. Island
  cancellation removes its job, while structure cancellation retains a terminal
  report. These embedded-player checks qualify lifecycle cleanup, not remote
  networking, positive discovery or a throughput improvement.
- Dedicated enabled/disabled controls cover both portal axes, ordinary fire,
  saved-Nether login recovery, valid personal beds and no denied-entry Nether
  chunk growth. Shared companion selection survives restart. A full integrated
  client also arrives dry on a supported SATELLITE and retains spawn on reopen.
- Native client checks reproduce and repair FTB creative-menu restoration at
  multiple scales; survival, chest and newly active server-menu controls pass.
  Hoofprint checks cover endpoints, pointer anchoring, navigation, waypoints,
  dimension ratios, resize, resource reload and same-JVM reconnect. A minimum
  client without optional targets loads a world. These are interaction controls,
  not broad FPS or remote-player network qualification.
- Native FTB server fixtures verify strict item alternatives, detection versus
  consumption, early advancement polling, party/offline progress, independent
  personal/team claims, duplicates, overflow conservation and actual restart.
  Connections use the native embedded-test pattern rather than two remote clients.
  The pack's compiler/exporter and Atlas have separate definition/browser checks.

## Branding release 0.29.1

`./gradlew --no-daemon check build` passes 249 Java tests plus package,
Ponder/upstream, framebuffer and release-tool checks. The JAR includes the native
metadata-referenced white indexed logo. This update changes branding/docs, not
terrain behavior or generation identity; existing world compatibility remains
subject to the same saved-profile guard. Logo presentation was verified at the
archive/metadata boundary, not through a fresh native mod-list capture.

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
  candidates are rejected. A follow-up using the published 0.29.0 binary
  generates representative building chunks to FULL and verifies actual placed
  structure blocks for both starts. A selected opportunity is not a guaranteed
  village, and a start-only search is not proof that its whole footprint is loaded.
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

## Historical evidence

The following records are pinned to the published 0.29.1 tree. They preserve
artifact hashes, controls, failures and coverage limits without making old test
results the operating instructions for today's checkout. Use the named record
when tracing a patch; rerun affected checks after changing its dependencies.

<a id="terrain-repair-and-structure-discovery-0280"></a>
- [Terrain repair and structure discovery (0.28.0)](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#terrain-repair-and-structure-discovery-0280)

<a id="terrain-and-structure-compatibility"></a>
- [Terrain and structure compatibility](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#terrain-and-structure-compatibility)

<a id="granular-edges-and-island-watersheds"></a>
- [Granular edges and island watersheds](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#granular-edges-and-island-watersheds)

<a id="horizontal-variation-and-discovery"></a>
- [Horizontal variation and discovery](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#horizontal-variation-and-discovery)

<a id="cluster-structure-distribution"></a>
- [Cluster structure distribution](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#cluster-structure-distribution)

<a id="sky-archipelago-14-terrain-and-city-boundaries"></a>
- [Sky Archipelago 1.4 terrain and city boundaries](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#sky-archipelago-14-terrain-and-city-boundaries)

<a id="release-0221"></a>
- [Release 0.22.1](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#release-0221)

<a id="configuration-composition"></a>
- [Configuration composition](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#configuration-composition)

<a id="configuration-input-and-selection-polish"></a>
- [Configuration input and selection polish](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#configuration-input-and-selection-polish)

<a id="configuration-pages-and-safe-editors"></a>
- [Configuration pages and safe editors](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#configuration-pages-and-safe-editors)

<a id="decorative-block-backfaces"></a>
- [Decorative block backfaces](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#decorative-block-backfaces)

<a id="static-glass-and-bridge-lantern-coverage"></a>
- [Static glass and bridge lantern coverage](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#static-glass-and-bridge-lantern-coverage)

<a id="ssrd-shader-adaptations"></a>
- [SSRD shader adaptations](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#ssrd-shader-adaptations)

<a id="0211-dependency-qualification"></a>
- [0.21.1 dependency qualification](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#0211-dependency-qualification)

<a id="0210-shader-assembly-and-glass-integration"></a>
- [0.21.0 shader assembly and glass integration](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#0210-shader-assembly-and-glass-integration)

<a id="display-text-0201"></a>
- [Display text, 0.20.1](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#display-text-0201)

<a id="identity-migration-0200"></a>
- [Identity migration, 0.20.0](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#identity-migration-0200)

<a id="world-text-lighting-0190"></a>
- [World-text lighting, 0.19.0](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#world-text-lighting-0190)

<a id="world-text-shadows-2026-11-04"></a>
- [World-text shadows, 2026-11-04](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#world-text-shadows-2026-11-04)

<a id="hanging-bridge-lanterns-2026-11-03"></a>
- [Hanging bridge lanterns, 2026-11-03](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#hanging-bridge-lanterns-2026-11-03)

<a id="tiled-glass-and-default-activation"></a>
- [Tiled glass and default activation](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#tiled-glass-and-default-activation)

<a id="glass-backfaces-and-shared-surfaces"></a>
- [Glass backfaces and shared surfaces](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#glass-backfaces-and-shared-surfaces)

<a id="connected-pane-edges"></a>
- [Connected pane edges](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#connected-pane-edges)

<a id="shared-surface-qualification"></a>
- [Shared-surface qualification](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#shared-surface-qualification)

<a id="glass-culling-0150"></a>
- [Glass culling, 0.15.0](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#glass-culling-0150)

<a id="configuration-sidebar-0130"></a>
- [Configuration sidebar, 0.13.0](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#configuration-sidebar-0130)

<a id="transmission--linkage-0111"></a>
- [Transmission & Linkage 0.11.1](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#transmission--linkage-0111)

<a id="questlog-353-support"></a>
- [Questlog 3.5.3 support](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#questlog-353-support)

<a id="release-0101"></a>
- [Release 0.10.1](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#release-0101)

<a id="release-090"></a>
- [Release 0.9.0](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#release-090)

<a id="release-080"></a>
- [Release 0.8.0](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#release-080)

<a id="lighting-adaptation"></a>
- [Lighting adaptation](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#lighting-adaptation)

<a id="release-070"></a>
- [Release 0.7.0](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#release-070)

<a id="release-061"></a>
- [Release 0.6.1](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#release-061)

<a id="release-060"></a>
- [Release 0.6.0](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#release-060)

<a id="release-052"></a>
- [Release 0.5.2](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#release-052)

<a id="release-051"></a>
- [Release 0.5.1](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#release-051)

<a id="dh-integration"></a>
- [DH integration](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#dh-integration)

<a id="release-050"></a>
- [Release 0.5.0](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#release-050)

<a id="bluemap-adapter"></a>
- [BlueMap adapter](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#bluemap-adapter)

<a id="earlier-terrain-regression-evidence"></a>
- [Earlier terrain regression evidence](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#earlier-terrain-regression-evidence)

<a id="audio-and-construct-recovery-0120"></a>
- [Audio and construct recovery, 0.12.0](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#audio-and-construct-recovery-0120)

<a id="playback"></a>
- [Playback](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#playback)

<a id="recovery"></a>
- [Recovery](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#recovery)

<a id="redstone-adapters-0140"></a>
- [Redstone adapters, 0.14.0](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.1/docs/development/verification.md#redstone-adapters-0140)
