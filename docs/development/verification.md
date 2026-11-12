# Verification

Evidence below qualifies named artifacts only. Build and native fixture commands live in [testing](testing.md); adapter contracts and update checks live in their domain guides. A later checkout needs its own relevant checks.

## Packaged client and release review

Candidate `b8c18caf5f010ed3affb219164e3fe029471f2b668f3a0be36c55d87d0c2527b` loads and reopens the owned Skyweave world through Prism 11.1.1, Minecraft 1.21.1, NeoForge 21.1.265 and Java 21 on macOS arm64. The fixture uses the complete selected mod input and all 18 authored file resource packs, without ModDev source roots. It renders both generated BSL/Colorwheel/Skyweave and Complementary/Euphoria/Skyweave outputs. In an authored Taiga biome control at physical Y338/reference Y71 and raised Y345/reference Y78, the raw altitude query reports snow while the actual native rain query and Iris precipitation uniform report rain. Same-process integrated stop/disconnect/reopen restores authoritative context with shaders enabled. Climate forms show the four authored values at GUI scales 3 and 4; a confirmed invalid commit is refused before any file write. This is controlled native behavior, not a natural climate-frequency or WAN test.

The versioned 0.33.0 JAR has SHA-256 `fec6e54e9df850f3158d9f2901ad92307d241f102e90130d418ed1c2abc0705d`. Its only changed archive entry from that candidate is `META-INF/neoforge.mods.toml`; all classes and other resources match. The actual packaged 0.33.0 Prism client also reopens the saved generation-14 fixture with its shader and synchronized climate. The final required Gradle run passes 329 tests with no failures, errors or skips, plus package, upstream, Iris, Ponder and tool checks.

The initial named ModDev full-client attempt exposes the same pinned Surveyor artifact's original `+1.21` version spelling; selection now accepts only that exact pair and Hoofprint's equivalent normalized spelling. Adjacent-version and suffix negatives remain rejected. Connector subsequently cannot locate a clean Minecraft artifact in that named bootstrap, so it is not counted as a full-client pass. Actual packaged qualification uses Prism. Copied shader outputs without receipts are correctly refused; rebuilding from staged inputs creates owned outputs. Euphoria's ordinary watcher then incorrectly treats the newly added BSL output as Complementary and logs a download/clipboard warning. Both tested shader pipelines still render; no watcher repair is claimed. Exact failures, launch boundaries, client states, inputs and cleanup are retained in `.git/captures/client-terrain-final-qualification.json`.

The same 103-piece fortified village yields 47 two-way paths among 64 inspected compatible horizontal road-related connector pairs with support enabled or disabled. Thirteen pairs lack the chosen exact endpoint stance and four fail a direction within the six-block search; reachability/status is unchanged, while some path/stance details differ. Compatible ports are not proof of an actual native parent-child route or the absence of a longer alternative. This is bounded road evidence, not whole-network certification; `.git/captures/village-connector-paths.json` retains both controls.

An independent read-only review of the same candidate reports no new blocking/P1/P2 defect in its inspected support/material/stage, contributor/climate, optional-consumer and authority seams. It runs no native jobs and does not substitute for the above checks. Attributed findings and scope are in `.git/captures/terrain-climate-review-result.json`.

## Island climate and tracked context

Minecraft 1.21.1 / NeoForge 21.1.265 / Java 21 / Sky Archipelago 1.4 native checks use Compat candidate SHA-256 `917244328aed38557194f71da80672a9e43473610530f6942cfd232b9e5f7ae3`. This is an unpublished candidate; these checks do not qualify the unfinished structure-support or full special-island qualification. Compact receipts are in `.git/captures/terrain-climate-delivery.json`.

A separate loopback dedicated server and minimum ModDev client negotiate the actual climate channel over `InetSocketAddress`. Server altitude scale 2 overrides local scale 1: the same physical position maps to reference altitude 78 rather than 71. End travel clears policy and diagnostics; overworld return, reconnect with a new connection and travel out of the original tracked area restore the expected mapping. The server uses the candidate JAR; the client uses ModDev main classes and client probe SHA-256 `0004c860d36fa9886abb786c5e05b2d50bf7f0c2eef0e7a3614a7cc01d55223b`. This is minimum network/lifecycle coverage, not full-pack shader or WAN qualification. Weak diagnostic entries may survive until collection; lookup authority remains the actual loaded chunk.

With Dynamic Waters 13.1.0 and probe SHA-256 `b66ee5309becd46e5b5403d42cd7e140f0e9d2bc1d45b4627ea8102a880edbce`, a physical pool at Y508 rejects warm-biome group insertion and admits three cold-biome floes. An actual floe tick ages once in cold water, then by ten after the water biome becomes warm. Explicitly disabling the climate adaptation restores three warm-biome insertions and ordinary aging, reproducing native altitude cooling. Both controls preserve loaded-chunk count and complete in about 38 seconds including startup. They exercise upstream insertion and entity aging in an authored pool, not natural river-node selection or long-run drift.

Probe SHA-256 `03f0987e7ef4c203cfd36107bd3ced44e54bbf029a09a68850479bc0c3cf25fd` verifies the same candidate's actual field cache. Of 4,096 distinct columns, 2,048 are demonstrably evicted; reverse-order recomputation preserves contributor identities, thermal columns and intrinsic occupancy samples. Four barrier-released cold callers agree, and 10,000 warm requests retain cache identity. Loaded-chunk count remains unchanged. Observed times are 33.7 ms for the first column sweep, 25.1 ms for reverse recomputation and 7.6 ms for warm requests; these do not isolate whole-generation overhead or prove simultaneous execution inside the provider. The accompanying native cave-biome and feature checks still pass.

## Occupancy-aware thermal ownership

The old exterior-only capture disagrees with actual physical ownership when one contributor's cavity overlaps another's solid body. A regression reproduces reference Y14 instead of -186 at the same physical point. A second control reproduces an air-tie error caused by sorting thermal intervals by height rather than canonical owner identity. Both are corrected; unknown overlapping occupancy refuses capture instead of borrowing a known contributor's authority. The final full Gradle suite for this thermal cut passes 324 tests.

Candidate `d109399b8e94597255b438e7ae2f04036c7554a61579c7a7ee5b844f7197e0f7` passes the stacked-companion native thermal/physical-owner and packed-client comparisons across the recorded three seeds. The old exterior comparator differs at 759 sampled points. The checks load no chunks, retain bounded intervals and distinguish cold capture time from total generation cost. Exact samples, configuration and artifact hashes are in `.git/captures/thermal-ownership-delivery.json`.

The preceding client-lifecycle candidate `aa1baf89a7c9be465b007bde98be0d0790879beca0d354d049cbb8acb0d0e3a4` passes a real minimum TCP connection using optional protocol `climate-2`. The server's scale 1 overrides the client's local scale 0: physical Y339 maps to reference 71, and physical Y353 at the controlled column maps to 85. Actual End travel clears policy and data; return and same-process reconnect restore them. A matched long teleport reproduces 6311 retained old layers before the weak-identity forget repair and zero afterward; returning repopulates the expected mapping. Both owned processes stop cleanly and their server runtimes are removed. The server uses the packaged product; the client uses ModDev class roots and the separately hashed probe, rather than Prism or a JAR-only bootstrap. The desktop was locked, so this proves transport/lifecycle behavior, not visual shader or GUI behavior. Full-pack client, optional-protocol mismatch, C2ME and overall cost qualification remain open.

## Authored template clears

Candidate `77baed120371d7427c709c88a66af2a4e1c8fe39d7f4a1df238238ab9ce4fb8e`, probe `6b1559ac5bde126641b9e5f81725f7b5eb2f12f6119f194fec9ea342ceb2a337`, retains the minimum native 150-foot/450-fill fixture across reopening with 40 foundation controls. New controls distinguish explicit later-piece/same-start authored AIR from foreign, unobserved and same-piece changes, verify no fill or physical credit, and invalidate changed AIR.

The actual CTOV Savanna witness in seed -6565383426043466991 retains the same selected source, 131 pieces and physical support counts: 3652 contacts, 10 native bases and 176 shallow completions. Two requirements explicitly cleared by later authored templates persist as `RETIRED:AUTHORED_CLEAR` after LIGHT and reopening. Five other changed feet and seven real void feet remain refused. The two-boot run takes 154.9 seconds including startup and preserves its exact saved structure/proof digest. This is a disposition repair, not complete village admission or access qualification; `.git/captures/village-final-native-intent.json` retains baseline, raw-intent witness and current results.

## Raw host-biome history

Candidate `0b0c0d940205a1bc044c84aae49681a62a179b793942a322673b6e16d6ca470a`, probe `8a26aefc4274648a030e6d60cdd1bfe7a318afd25bc5e36bf1bbe6f0d92023ae`, passes the full native cave survey and all three stored/decorated cave targets in 136.2 seconds including startup. An actual generator-owned native host-cache fixture reproduces query-history-dependent selection before the repair. Afterward, owned generators sharing the same source agree, while foreign generators retain their original discriminating plains/lush-caves results. Physical raw depth remains effective. A native cache constructed with its actual two-entry capacity positively evicts the witness key and recomputes the same result; this is not a measurement of maximum production cache cost. Separate Java controls cover nested foreign/owned cache scopes, exceptions and thread isolation. The earlier minimum native receipt precedes the explicit eviction control. Exact red and green artifacts, configurations and reports live in `.git/captures/terrain-climate-delivery.json`. Actual river/shore fallback combinations, concurrent native misses and broad performance remain unqualified.

## Native climate-index history

Candidate `3c6bbc3ffadd9efc0d7b00c4f94a56ff46dec57baa6b5582e7df82b811a6aacc`, probe `acd62ea00f60def845499e4224caa42924258e8674f9255b0556d41e7c7d6f72`, passes the full-pack native cave survey with all three target biomes stored and decorated. A discriminating native two-row table reproduces plains/lush-caves selection at the same quantized depth 2750 after different query histories. Owned selection returns the same biome after both histories, leaves the original native tree's last leaf unchanged, and cleans up after a thrown native query. Five Y-sensitive synthetic sampler controls verify that surface fallback changes depth alone. These are coordinate-contract controls, not naturally generated profiles at the synthetic sample height. The run takes 137.1 seconds including startup and native feature checks; it is not an isolated index benchmark. The preceding minimum run independently verifies stable ties and stored cave biomes at its own artifact. Exact receipts live in `.git/captures/terrain-climate-delivery.json`; the raw-host consumer is qualified separately above; cold concurrency, cache-row exhaustion and broader cost still need their own qualification.

Candidate `1c4b1ea75656a6dea5596ba864f2c3fff07f8850e5cf599789531e0d22c2c8cf`, probe `d25f01ef9695ec18aca180ac03e9e7621da059cee1dc26b38088c8496815165e`, retains those native index controls after intrinsic material ownership takes priority over overlapping cavity datums. The full-pack survey stores and decorates lush caves, dripstone caves and Deep Dark, preserving the inspected surface biome samples. It completes in 141.6 seconds including startup with a 4 GiB heap. This is not a forced overlap-biome fixture or a broad ecology/performance measurement; the separate overlap context check qualifies its own points.

The explicit stacked-companion geometry fixture checks 134,400 intrinsic point/interval and biome-owner associations across three seeds, including 1,638 simultaneous cavity/solid points. Material ownership wins every such competition. The metadata workload takes 13.7 seconds, excluding startup. The default geometry's selected centers supply no overlapping point controls, so its successful exterior comparison is not counted as this coverage. Neither metadata probe stores overlapping biome chunks or qualifies an encounter rate.

## Special Deep Dark natural terrain

The `--variation deep_dark` fixture uses a fixed native Deep Dark source and an explicit shape profile; it qualifies selected recipe behavior, not natural world frequency. Candidate `359c44171e879606a65d4b751a1e76836215513a0b72cd81b5de79da0153793d`, probe `7398bf8ac72874540dfdd99406b56c451ab1f8d146117b972ffc5c3f5f20ff30`, passes minimum seed-77 and full-pack seed-42 generation plus save/reopen. The minimum maximum-device control compares 917,504 actual NOISE blocks with base-column queries, observes 146,940 deepslate body blocks and 23,105 owned cavern-air blocks, and finds a 94-block sampled cavity. The full-pack default control compares 688,128 blocks, observes 59,636 deepslate body blocks and 20,584 cavern-air blocks, and retains identical saved block digests after reopening. Fresh SURFACE observations are retained separately from FULL chunks, where later native ores and features can legitimately change material.

The native vein-growth feature initially bypassed the owned device budget: whole sampled chunks contained 6–92 veins. The qualified natural-growth scope guards actual generation-region writes entering the special habitat, including writes from neighboring origins. The final maximum-device fixture has at most one of each device per sampled special habitat/chunk, with three eligible exposed-rock veins and four each of sensor, catalyst and summon-capable shrieker across four chunks. The zero-chance control retains no selected-habitat devices. Ordinary Deep Dark retains actual stone and native vein growth; disabling the cave-biome adaptation selects no special-habitat mixins or promoted islands in the bounded control. Exact artifacts and inputs for each control remain distinct in `.git/captures/deep-dark-terrain-delivery.json`.

An independent source review found that the initial feature guards also intercepted runtime `ServerLevel` calls used by farming. The earlier direct-server plant-denial test was not a natural-generation witness and does not qualify player farming. Corrected product `ca31c42a0db4c38e8f22431149360136d5c1cd7ad9326a51945f279dea841e52`, probe `d6e4f8e48a8cc19fd86411f6e4f494d4680bbcf5a5bf15c551eefac0d0fb5620`, passes minimum and full-pack seed-42 fixtures across two boots each. Actual `WorldGenRegion` decoration rejects natural plants/trees while retaining the template-authored plant; the rejected tree consumes no placement draw. Runtime native sapling, mushroom and grass bonemeal callbacks produce tree, mushroom and vegetation blocks, with the bounded fixture restored afterward. Native animal predicates reject natural/chunk-generation spawning while breeding and command predicates remain allowed. These controls do not establish every modded spawn hook, animal AI, Warden encounter or arbitrary cross-boundary tree canopy. No city is forced or required.

Two fresh runs of the same artifact/probe reproduce all four maximum-device block digests. A separately compiled direct-per-voxel style-lookup control with the same probe also matches the captured owner-classification implementation exactly. An earlier cross-probe comparison differed; it is retained without attributing a cause or claiming historical artifact equality. The paired controls qualify the bounded material lookup change, not general concurrent-feature determinism or performance.

## Native Ancient City assembly boundary

The `--city-recipe --minimal --seed 42 --heap 4G --reopen` probe assembles four real native Ancient Cities with a fixed Deep Dark source and a plain native generator whose height/column methods throw. The 89, 77, 97 and 90-piece layouts are entirely rigid, make zero terrain queries, preserve native NBT exactly on serialization/reload and do not change loaded-chunk count. Both JVMs return the same assembly observations. Exact artifacts/configuration are `nativeCityAssemblyBoundary` in `.git/captures/terrain-village-support-inputs.json`. These checks establish a terrain-independent native assembly boundary. They do not fit a city into an island, reserve it, carve a cavern, realize its blocks or guarantee a city on a special island. Native adjusted bounds include terrain-adaptation padding and must not substitute for occupied feet.

## Processed shallow-support core

The unpublished compiler/planner/writer cut at main SHA-256 `2ef575be129f90e8c531b942febfef5184cc0de450e06ea748614d2ec303d440` and probe `ca17e8711de898b77011520a9d964f838a182efc09d1bf6c0315450f7868b030` passed native `--village-support --reopen` checks on Minecraft 1.21.1 / NeoForge 21.1.265 / Java 21. Minimum installation: eight placements, 150 actual processed feet, 450 support writes and 42 damaged raw feet left unfilled. Full pack adds the actual composed CTOV small house and Lithostitched processors: 12 placements, 1,240 feet, 3,720 writes and 100 damaged raw feet left unfilled. Both runs reopen saved final block states and pass nine changed-input, co-owner, conflict and replay/heightmap controls.

A failing native control reproduced that using a derived piece ground delta incorrectly changed template support intent. The corrected compiler uses the element's own floor delta; 16 minimum and 24 full-pack altered-piece-delta controls preserve the footprint. Native template placement does not consume the derived piece delta in the same way as terrain adaptation.

These are diagnostic authored platforms and natural-owner inputs, not whole-layout or natural-distribution witnesses. They qualify processed-foot equality, shallow completion, preserved damage and saved block output. Production CARVERS/FEATURES binding, roads, native Beardifier, whole-layout acceptance and late-feature coordination require separate evidence. Compact receipts remain in `.git/captures/terrain-climate-delivery.json` under `shallowFoundationMinimalFinal`, `shallowFoundationFullFinal` and `derivedGroundDeltaRed`.

## Natural structure inputs at CARVERS

Main artifact `7976a60e061fa0b791481e1491f0c5d1c09d90b2220df9b1a57f8bbf11c68415` and probe `83ed743995d223d1bb83220d1dabcec782e13853125ce4507203054da34fd533` passed `--natural-inputs --reopen` in minimum and full-pack installations on Minecraft 1.21.1 / NeoForge 21.1.265 / Java 21. Each run used the actual native CARVERS task on a distant generated island, saved that partial chunk after replacing physical grass with bricks, and reopened in a second JVM. The retained source and its read-only processor view still returned original grass while the saved physical block remained bricks.

Fourteen controls refuse missing/late, malformed or mismatched source metadata without substituting live terrain. They include schema, seed, policy fingerprint, dimension, chunk/bounds, absent sections, invalid/unknown palette state, oversized packed storage and wrong header types. The selected chunk's compressed attachment was 1,434 bytes minimum and 1,432 bytes full pack; startup-inclusive elapsed times were 34.6 and 69.9 seconds. These are fixture observations, not overall storage or generation-overhead bounds. The final-artifact disabled structure-policy control registers no source attachment, exposes no source authority and refuses the processor view. Required Gradle checks, Python syntax and Markdown/diff checks pass.

This qualifies production capture and partial-chunk persistence, not whole-layout support, a real neighbor-feature writer, native village Beardifier or attachment retirement. Exact receipts and source hashes are retained under `naturalInputNativeMinimalFinal`, `naturalInputNativeFullFinal` and `naturalInputNativeScope` in `.git/captures/terrain-climate-delivery.json`.

## Native ground-village preparation and feature reservations

Candidate `fbf59b412ce7aac4564d95a1319b3a16f572934c33ca4897e2663adbfc1ac109`, probe `2db0ca34961df5e0cabca3bb095eb9748a521e8cc0d7d012243cc44ec2896ce3`, passes a controlled registered native house through CARVERS, FEATURES, FULL and a second JVM with the full pack. Minecraft 1.21.1 / NeoForge 21.1.265 / Sky Archipelago 1.4 loads Better Villages' actual 13×48×17 replacement of the vanilla house resource. All 147 processed footing blocks have immediate contact; the inspected source chunk retains 687 physical grading cells. Saved block states and its exact grading journal survive reopening. Nine malformed, changed-source and changed-intent controls pass. The run takes 42 seconds including startup and reopening, with a 4 GiB maximum heap; this is not a general generation benchmark.

Exact-foot write tracing first reproduced native `BlockBlobFeature.place`, selected through `RandomSelectorFeature`, replacing authored grass feet with stone. Whole-candidate protection removes that conflict. Explicit native boulder and lake calls at the protected site consume zero random draws and leave 2,888 inspected cells unchanged. An unrelated native boulder consumes 18 draws and places actual stone. Probe tracing and controlled feature calls are absent from the shipped artifact. Attributable red/green receipts are retained in `.git/captures/terrain-village-support-inputs.json`.

This is one controlled native start, not natural candidacy, full-village admission, roads, all late-feature writers, pending-reference delivery, DH qualification or source-attachment retirement. Earlier minimum and disabled contact receipts retain their own artifact identities.

The subsequent source-lifetime/final-evidence cut uses candidate `55ccaf9d2fff43fb89af5a8305140d7e3fbfbcd54c50703819917b716f5701d5` and probe `e3a110a643f14a9ca475542eeb56073b412edad8e9255d374c07037281c34817`. Minimum and full fixtures retain 24/24 and 147/147 verified footing positions at native LIGHT, then save/reopen both their blocks and exact per-foot evidence. Eighteen controls reject malformed or oversized data, changed feet, early verification and missing source intent; stored invalidation remains invalid after restoration. Copied source palettes and grading intents are absent after LIGHT and reopening. These controls include detached native chunks; they do not reproduce every possible neighbor writer or certify a full village.

A separate two-JVM CARVERS witness at candidate `b1564beb0fdc0ad4537149e0b360c3b6104ce5fc0f0bafee7b139e104b89746c`, probe `407e110d4fbf3d22ed5213b088b4e04072f2299e325dc218249b20423c48d68f`, preserves both original natural blocks and a non-empty grading journal despite a later physical brick write. Fifteen refusal controls include early-retirement rejection. Advancing the reopened native chunk to FULL removes its copied inputs. This is retained-input serialization/lifetime evidence, not a complete interrupted-village replay.

## Actual fortified-village contact

The CTOV fortified witness near (1904, -1488), seed -1626161469983550673, retains 103 pieces and 37 buildings. Product `ca0a30b8` verifies all 2,922 logical footing keys after native generation and reopening: 2,283 existing contacts, 13 native-base cases and 626 shallow completions. Native villager-sized paths cross all 41 inspected doors in both directions. One required neighborhood chunk is explicitly generated by fixture setup before those read-only path queries; their loaded-chunk count remains unchanged. The two boots take 141.42 seconds, including startup, with a 6 GiB maximum heap. This is a named contact/local-access witness, not a production benchmark or whole-village navigation certificate.

The same product's west witness retains 103 pieces, 2,691 verified feet and 14 intentional authored-clear retirements, with all 26 local doorway paths passing. Reverse FULL request order retains its exact footing/current-block digest. The north CTOV jungle witness retains 68 pieces, 1,917 verified feet and all 16 local doorway paths. Actual write traces distinguish native tree soil writes, cave carving, native template cuts and unsupported decoration roots. Shallow carver exclusions preserve 15 observed cap cells while an unprotected deeper cell still carves. The source chunk is requested first in both west orders; these runs do not prove every pending-reference or concurrent schedule.

The isolated fixed-desert house retains all 24 contacts through native falling-block ticks and reopening: 191 actual falling support cells remain unchanged. That gravity-only profile omits the separate plains-only auxiliary feature controls. It does not qualify natural desert village candidacy. No production gravity change was inferred from it.

Exact artifacts, commands, initial incomplete door-neighborhood observations, source attribution and saved evidence are retained in `.git/captures/village-access-and-west-stage.json` and `village-native-gravity.json`. These checks do not certify whole-road connectivity, arbitrary late writers, all projected or addon processors, late relocated-reference preservation or DH generation.

## Shared raw roots and natural gap eligibility

Candidate `2f29f94bff8472fb9061f8404c1c274afde7991d958cdaadce83d49e4adf953b` shares the native-selected palette and declared-floor root mask between fitting and material compilation. Sixty-four actual native placements select both alternative palettes, agree with the compiler and exclude above-floor decoration. The full support fixture retains 1,240 feet and 3,720 writes, while the fortified village retains 2,922 logical feet and 1,570 immediate raw building contacts; both reopen exactly. These are raw-intent/contact controls, not complete admission or access qualification.

The subsequent shared natural gap envelope passes 34 native refusal/positive controls, including protected water, cavities, overlapping contributors, unknown context and captured open caps. The minimum processed fixture retains 150 feet, 450 writes and damaged-foot exclusions across reopening. The broader historical suite remains red: the lagoon cluster keeps a positive allowance but no longer admits the former unsupported water layout. Native diagnostics distinguish the rarity allowance, candidate coverage, biome rejection and unsupported centers. Projected assembly-source binding is implemented development work. The finer-grid trial is removed: a separate read-only one-chunk survey finds 30 eligible dry roots in the sampled cells, but all eight best-ranked native layout fits fail. It queries 805 exterior metadata columns without changing loaded-chunk count. Production retains the original eight-chunk grid and encounter accounting. Coastal opportunities, whole layouts and broad regressions remain unqualified. No failing witness has been promoted to a passing requirement.

## Complete inhabited footing and opportunity controls

Default-on complete inhabited support now rejects unrepairable center/building/farm roots while preserving the configured shallow reach and independent road/decor coverage. In the matched Savanna cluster, eligible area 1,216 and allowance 1 remains: a supported 77-piece T&T alternative verifies all 1,267 feet (1,209 contact, 4 native-base and 54 shallow). Explicit false selects the former 131-piece CTOV layout and retains its seven genuine VOID refusals. A 16-cluster seed 42 comparison retains identical village/mansion allowances and four native starts in the first four positive clusters; warm and reverse-evicted plans match. These are bounded opportunity controls, not worldwide encounter rates. An intrinsic no-owner preflight trial produced no measured planning-time improvement and was removed.

The original fortified witness retains 103 pieces and 37 buildings at quality 1. All 2,922 canonical footing keys verify after reopening (2,239 contact, 13 native-base and 670 shallow); 547 actual fill writes and 1,928 graded cells complete the original gaps. This run takes 145.34 seconds including startup/reopen with a 6 GiB heap and approximately 7.05 GB peak process RSS. Door/road routes and whole-layout acceptance are separate qualification. Exact commands, configuration and hashes are retained in `.git/captures/village-complete-footing.json`; historical results retain their own artifacts.

## Assembled-craft temperature

The independently selected Sable climate adaptation passes an actual occupied 18-block craft on the dedicated server. At parent Y=422 with natural datum 403, storage Y=320 maps to reference 83 and the parent X/Z. Translating the real pose produces reference 131; tilting it moves the observed block and produces reference 129. Native Plains/Taiga temperature helpers agree with the projected reference, markers remain idempotent and the queries do not create chunks. The same three-pose test passes with the DW artifact absent. Explicit false preserves the old storage-coordinate input in all three poses. A full-DW fourth pose moves beyond loaded parent terrain: the existing reference 64 fallback applies at the new parent X/Z with 188 chunks unchanged.

These tests qualify server temperature conversion and absence/disablement boundaries, not client render publication, a replacement biome selector, water forces or habitat AI. The original red occupied-plot observation, corrected pinned API assumptions, exact artifacts and per-pose observations are retained in `.git/captures/sable-climate-frame.json`.

## Native elevated-water movement and hatching

The enabled minimum seed 42 and full-stack seed 43 fixtures exercise actual native goals at a physical water surface of Y508. Across 64 turtle travel seeds, 22 targets rise within the local-water ceiling. Native drowned ascent produces a seven-node path ending at Y507 and becomes ineligible near the surface. Controlled native breeding completion, home-arrival checks and egg-laying produce three eggs at Y508; actual random ticks hatch three babies retaining that physical home. The stone-substrate egg control does not hatch. These calls leave loaded-chunk counts unchanged after explicit fixture preparation.

The explicit `terrain.water_animal_spawns=false` minimum preserves vanilla global-height behavior: none of the 64 travel targets rises and the high drowned does not start the native sea-level ascent. Breeding, nesting and hatching remain functional. This is consumer-level optionality, not merely class loading. The enabled full stack also retains Dynamic Waters' three cold-water floes, no warm-water insertion and warm aging of 11 after the cold-to-warm transition. That pool alone does not qualify cached-node selection or drift; the layered native river-ice checks below exercise those consumers separately.

The enabled minimum, disabled minimum and enabled full runs take 23.29, 17.89 and 35.37 seconds including startup, with 4/4/6 GiB maximum heaps. These are complete fixture costs, not isolated per-tick overhead or world-generation benchmarks. The initial movement attempt omitted the native sky-brightness refresh after changing time; its failed day/night fixture is retained separately. Exact artifacts, configuration and observations are in `.git/captures/habitat-native-movement.json`. These checks do not establish natural populations, autonomous mating, full-body locomotion or long-range route completion.

## Layered native river ice

Dynamic Waters' actual cached node at (-1312, 406, -1284), seed 42, reproduces the height loss: native group insertion and `spawnNear` both select unrelated water at Y439 and insert zero floes. The native route admits Y406 and rejects Y439. Retaining each cached node's Y and choosing loaded water near that height yields three direct-group floes and seven through the actual native spawner. Native X/Z candidate order and weights remain equal. The pool and two physical water layers are authored fixture data around a real native cached route, not a natural-frequency sample.

The full and minimum-pair controls retain 40 native cold entity ticks with actual downstream drift. A subsequent Taiga transition is cold at the real global Y under unmodified vanilla temperature but warm at the island-relative reference; the floe reaches its native retained lifetime. Explicit climate disablement preserves cold aging from 40 to 80 while the water-layer repair remains active. Explicit water-height disablement preserves zero lower-layer insertions. Both spawner paths refuse an immediate solid roof and falling water; unloaded columns and invalid query bounds are refused. A foreign End pool retains the native highest-column policy. Floe queries do not increase loaded-chunk counts. Work/result caps skip an entire cached-node query on exhaustion; producer exhaustion is not exercised by these ordinary-route fixtures.

The combined full-stack witness also exercises actual native Sable/DW discovery and a real assembled craft: zero dry-current calls, one wet-current call and identical world-space wet impulses after yaw. The explicit ship fixture increases loaded chunks by one and is measured separately from non-creating floe queries. These are synchronous force-call observations, not a free-running flight simulation or biome refresh. Exact artifacts, failed measurement scope, commands and outcomes are retained in `.git/captures/floe-layer-admission.json`.

## Dedicated DW/Sable discovery

Actual RuntimeDistCleaner caller capture traces the client-class resolution to DW13.1.0 `SableCompat.initPhysics` reflecting over Sable2.0.6's container overloads. The selected server-only container bridge initializes all nine native physics method handles; an actual foreign-End container/list invocation leaves loaded chunks at 0 and emits no captured client-class error. Explicit adapter false restores the error and nine null methods. These controls qualify dedicated discovery, not forces on a moving craft. The probe's active shutdown invocation requires `--inspect-sable-discovery`; ordinary fixtures retain passive bounded caller capture without actively invoking foreign physics. Exact observations are in `village-complete-footing.json`.

## Native tree protection

The minimum native house verifies 24 footing contacts across reopening. Actual native oak, bush, acacia and mega-jungle candidates refuse the complete protected envelope without changing 2,888 watched cells; each still places on the unrelated layer 32 blocks higher. Native disk, plant, ore, boulder, lake, proof and journal controls remain separate in the same fixture. Actual west-village traces attribute the former grass-to-dirt changes to mega-jungle trunk soil preparation; the qualified guard removes those changes. Custom root/trunk/foliage/decorator bounds remain unqualified. Exact controls and dependency artifacts are retained in `.git/captures/village-access-and-west-stage.json` and `terrain-village-support-inputs.json`.

The recorded lagoon remains an accepted natural LAGOON host with allowance one and area 448; its old full village is rejected by the new support policy. Scoped diagnostics account for water, absent solid ground and excessive gaps, with no unknown-terrain or budget refusal in the observed lagoon fits. Six `INVALID_AFTER_BIOME_ACCEPTED` failures belong to unrelated spawn-area candidates, not this lagoon. Candidate ranking, descriptor, bounded alternative survey and zero-fit result match before and after tracing. The historical positive fixture remains explicitly failed. A separate native house on the lagoon's dry coast verifies all 24 footing positions and original sandy support across reopening without requiring a grading patch. This qualifies the bounded coastal writer, not whole-village availability or frequency. Exact diagnostics, failed assumptions and positive control are retained in `.git/captures/lagoon-village-host-review.json`.

## Neighboring soil and plant writes

Actual disk and double-plant calls from local and neighboring decoration regions refuse protected destinations before provider randomness or block changes. Unrelated native disks, sunflowers and ore veins still place; the unrelated boulder retains its original 18 draws. Ore protection runs at the native section-write boundary, which bypasses ordinary chunk setters. These are bounded known-writer controls, not a global feature reservation guarantee.

The earlier natural Savanna traces separate soil, ore and template AIR changes. The current complete-support choice preserves that cluster's area and allowance while selecting a supported native T&T alternative, described above. Do not interpret earlier partial improvements in the rejected CTOV layout as current complete support. Historical attributed red/green writes, exact native artifacts and the explicit disabled control remain in `.git/captures/terrain-village-support-inputs.json`; the accepted replacement and matched opportunity controls are in `village-complete-footing.json`.

## Village fitting and discovery 0.32.0

The combined release candidate has SHA-256 `95c2a06c688280b1118a166caa2c6706d4c59a529a5222dcfe77a3666fd858a0`. It includes the Wayfinder tuning below. Required Gradle checks pass 297 JUnit tests plus package, upstream, Ponder and tool checks. Retained receipts are in `.git/captures/village-fitting/` in the development checkout.

The final combined JAR differs from the native village/command/client candidate only in NeoForge version metadata. Its Java classes and English resources are byte-identical. Earlier receipts identify their own artifacts; the separate material fixture also used a task-only candidate whose terrain classes match.

- The four recorded villages retain 105, 131, 68 and 107 pieces. Physical audits cover 105 inhabited buildings and 4,983 footing columns: every building meets the 90% support-within-three-blocks policy and no tested authored floor is missing. Native road-fallback controls preserve registered elements and input pieces, add reciprocal junctions, and reject the proven-void control. These checks do not establish universal walkability for every third-party template.
- The actual shipwreck foundation writer changes 889 generic grass/dirt/stone writes to 872 sand and 17 sandstone writes. Frozen thin-skin, overlapping-write, context-release, cold-surface and fluid/block-entity palette controls pass. Material selection does not change native fill extent or permission.
- Across two matched 24-cluster allowance samples, the first four positive village clusters per seed commit eight candidate villages versus five baseline villages. Allowances are identical. The second-seed candidate preserves native piece digests across save/reopen and agrees after warm/reverse-evicted planning. These eight positive clusters do not establish a population encounter rate.
- The first seed takes 137.0 seconds including startup versus 119.0 for baseline. Its initial per-owner timings can include spawn-warmed caches. The second-seed candidate explicitly evicts fit plans; its 253.1-second total includes reopening. These are bounded fixture costs, not cold JVM or general server benchmarks.
- Source/reference aliases and reversed discovery order yield one physical match; distinct equal-bounds layouts yield two. Native jigsaw reload retains identity and reconstructed reference bounds. A one-block-radius control includes a layout whose source lies outside the query. All 88 registered village definitions and the remaining managed structure types have qualified metadata envelopes in the selected stack; this does not certify arbitrary addon assemblers.
- Real family-search jobs retain positive results after saved-start reopening, preserve clickable navigation and cancellation, and explain both complete and partial empty results. Operator checks survive an unrestricted sibling command registration. Unknown layout/placement contracts retain explicit partial coverage.
- The full native client loads a copied stopped fixture. Local chunks and visible meshes are checked before the jungle/savanna captures. Localized settings search opens the fitting group, reads the seven defaults, disables saving an invalid support fraction, and restores clean draft state at GUI scales four and three.

Combined-artifact minimum and unsupported-disabled dedicated startup pass. The combined native client also opens the copied integrated fixture successfully. The qualified terrain-only startup and full material fixture are recorded separately. New-generation behavior uses the changed saved identity; no existing save was silently converted during verification.

Wayfinder's combined-artifact minimum fixture verifies silent default feedback, two immediate successful warps without cooldown bookkeeping, and actual upward placement with the 16-block default. Explicit range controls cover hazards, headroom, shape support, cancellation, permission/session changes and lease release. The retired hotkey-warning field is read without rewriting the file; explicit cooldown and other retained values remain authoritative. These controls extend the broader 0.31.0 transport/client evidence below; they are not another WAN multiplayer or arbitrary terrain survey.

## Wayfinder 0.31.0

Minecraft 1.21.1 / NeoForge 21.1.265 / Java 21 passes the required `check build probeJar clientProbeJar` command with 282 JUnit tests and the package, upstream, Ponder and tooling checks. Native minimum/full-height receipts identify candidate SHA-256 `9339c656f98f50c4ec901ddf8174cf4f3594121bc6302011e87cd2a8ab3d547d`. Retained receipts and the task checkpoint live under `.git/captures/wayfinder/`.

- The minimum dedicated fixture contains only Compat and its probe. Native commands land on full blocks and slabs, preserve direction/cooldown, reject hazards/headroom/ceiling ascent, respect a native teleport-hook veto, reject LOD candidates without real support, and exercise real leaves, stairs and scaled player bounds. Full-pack checks additionally use Create casing and actual assembled Sable 2.0.6 craft before/behind the target, plus storage-frame guards.
- Native authority checks cover non-operators and legacy tags, malformed/off-ray candidates, stale/duplicate replies, permission loss, movement and disconnect. Reconnecting the same UUID rejects packets from its retired player object. Dimension travel cancels work, concurrent admission remains at four jobs, cancelled cold destinations release four leases and drain their requests, and pending server shutdown clears jobs/tickets and stops its owned worker. Denied hotkey starts produce no message or admitted work by default; commands still explain denied access. A separate server-policy fixture permits a non-operator only after an explicit policy change.
- The real custom-height fixture uses native bounds -128 through 767, with an actual arrival at Y602. DH 3.3.3 imports/readbacks cover X8192, 65536, -8192 and 29000000 with a real top at Y651. These are database/height controls, not four client teleports or a survey of arbitrary terrain.
- Actual full-client G queries arrive at 8192 and 32768 blocks with the native target initially absent client-side. The final 32768 replay interrupts an earlier progressing query with same-JVM reconnect, then completes a fresh query and leaves zero jobs, destination requests and leases. Its maximum observed active server-tick gap is about 72 ms; this is a fixture observation, not a general performance guarantee. Prepared native corridor/LOD data belong to the fixture; product queries do not generate that corridor.
- TCP tests connect a minimum client to an independently running loopback dedicated server: native operator G arrival, non-operator denial and reconnect pass. A server without Compat negotiates no channel and receives no Wayfinder request. These complement embedded native authority tests; they do not claim a two-person WAN or remote DH-server transfer test.
- Native client checks cover held input, rebinding/persistence and suppression in chat, inventory and settings. G has no conflict in the selected pack registry. Core-disabled client loading removes the binding/channel. DH-disabled loading preserves native controls and refuses unavailable distant data without movement. Dedicated minimum, all-unsupported-disabled and active-unsupported startup controls qualify class-loading and exact Sable-version rejection.

The full-client replay uses candidate SHA-256 `bc736fdfd04e2ad0c05c0bfdf69c0eb811428d259d37c23f5e7304f7ad3c2d51`. Archive comparison with the 0.31.0 candidate identifies only version metadata, English wording and two diagnostic-string class changes; targeting, transport, input and lifecycle classes are byte-identical. The final server fixtures use 0.31.0 itself. No target indicator is implemented, so shader-indicator rendering is outside this verification scope.

One full integrated startup failed before querying inside Hoofprint's `HoofprintMapStorage.get` / Surveyor terrain-update callback with a `ConcurrentModificationException`; the retry loaded and completed. The stack is retained separately. This is an upstream observation, not a Wayfinder repair or proof of general Hoofprint stability.

## Discovery and capability integration 0.30.0

The release uses Minecraft 1.21.1, NeoForge 21.1.265 and Java 21. The required `check build probeJar clientProbeJar` run passes 268 Java tests with no failures, plus package, upstream, Ponder and release-tool checks. Native receipts under `.git/captures/misc-changes/` identify the tested unpublished artifacts; a later version label alone is not a fresh runtime check.

- Managed family/tag/exact-ID searches return positive native villages, including saved starts after a forced negative new-generation precheck. Lazy/eager plans, reordered/concurrent use and cache eviction controls agree. A 40,000-block full search returns useful bounded partial results; it does not prove full coverage.
- Sky Villages selected-site enumeration agrees with native placement in positive and negative-coordinate controls. One positive comparison reduces 444 grid checks to one candidate. A mixed query finds managed and ordinary random-spread starts, qualifies an unsupported ring entry and preserves results after reopen. A separate 16-candidate control stops with explicit incomplete coverage.
- A follow-up lifecycle fixture against the published 0.30.0 JAR (SHA-256 `fbc64f79844e86824d87c2b77af608ff3cb73edf872afd97a272236b93e9c79c`) verifies both locators cancel after native caller removal, travel to the End and actual Overworld unload. An independently leased FULL chunk completes alongside the searches; unload leaves no search or fixture tickets. Island cancellation removes its job, while structure cancellation retains a terminal report. These embedded-player checks qualify lifecycle cleanup, not remote networking, positive discovery or a throughput improvement.
- Dedicated enabled/disabled controls cover both portal axes, ordinary fire, saved-Nether login recovery, valid personal beds and no denied-entry Nether chunk growth. Shared companion selection survives restart. A full integrated client also arrives dry on a supported SATELLITE and retains spawn on reopen.
- Native client checks reproduce and repair FTB creative-menu restoration at multiple scales; survival, chest and newly active server-menu controls pass. Hoofprint checks cover endpoints, pointer anchoring, navigation, waypoints, dimension ratios, resize, resource reload and same-JVM reconnect. A minimum client without optional targets loads a world. These are interaction controls, not broad FPS or remote-player network qualification.
- Native FTB server fixtures verify strict item alternatives, detection versus consumption, early advancement polling, party/offline progress, independent personal/team claims, duplicates, overflow conservation and actual restart. Connections use the native embedded-test pattern rather than two remote clients. The pack's compiler/exporter and Atlas have separate definition/browser checks.

## Branding release 0.29.1

`./gradlew --no-daemon check build` passes 249 Java tests plus package, Ponder/upstream, framebuffer and release-tool checks. The JAR includes the native metadata-referenced white indexed logo. This update changes branding/docs, not terrain behavior or generation identity; existing world compatibility remains subject to the same saved-profile guard. Logo presentation was verified at the archive/metadata boundary, not through a fresh native mod-list capture.

## Terrain, settlements and construct picking

The November 8 follow-up uses Minecraft 1.21.1, NeoForge 21.1.265, Java 21, Sky Archipelago 1.4, Dynamic Waters 13.1.0, Macaw’s Bridges 3.1.2 and Sable 2.0.6. `./gradlew --no-daemon check build probeJar clientProbeJar` passes 249 Java tests, package/Ponder/upstream checks and release-tool tests. The native 0.29.0 candidate SHA-256 is `275510a308c6c221aba5c8f4976dca0e926e4d59e612468810927a8038b6709c`. Native fixtures use disposable worlds and exact artifact/configuration digests; the personal world is not their input.

- Two live Render-thread dumps reproduced the platform-assembly freeze inside our global lantern DDA. Sable’s combined result could contain plot coordinates. The same-frame hook passes 1,024 rays across eight lantern states, translated and yawed, in both minimum and full dedicated combinations. The full client assembles a pinned planks platform, tracks it, hits its plot block through normal player picking and an explicit ray, and advances 130 ticks across six seconds. This establishes responsive picking, not flight balance or an FPS benchmark.
- Two native Sky Village starts satisfy final-footprint clearance, with actual generated host columns about 126.59 and 119.67 blocks away. Other sampled candidates are rejected. A follow-up using the published 0.29.0 binary generates representative building chunks to FULL and verifies actual placed structure blocks for both starts. A selected opportunity is not a guaranteed village, and a start-only search is not proof that its whole footprint is loaded.
- Full-pack seed 42 surface/features checks cover dry land, Broken Reef, native Lagoon and coastal Lagoon fragments, including Mixed and Disintegrating treatment. Reef materials use the exposed native palette rather than its dirt-producing required-soil fallback. Material checks survive save/reopen; fragments retain dry metadata and no source fluid at/above their surface. These bounded controls do not establish every overlapping lower overlay or every foreign biome’s later decoration. Native lower overlays retain ownership.
- The full-pack island locator passes automatic continuation, result navigation, cancellation, permission and empty-result checks. After 40 unchanged startup ticks, its loaded-chunk assertion remains 49→49 across 625 cells (4.514 seconds). The earlier 130→134 startup run failed and is not counted as a pass. Its timing did not establish a search-induced chunk request.
- The 0.29.0 full-pack structure fixture finds the same real fortified-plains CTOV start at (-3936,426,816) cold/warm across 1,731 candidates. Empty results, permission, cancellation and lease cleanup pass; maximum observed tick gap is 79.99 ms. This fixed-plains control is separate from natural scarcity sampling.
- Both commands accept `page` without a number and explain empty/partial results without blank pages. Island results remain plans; structure results require actual valid native starts. Broad vanilla `/locate` remains synchronous.
- Native configuration search selects the localized Fragment fringe fields on the Sky Archipelago page, retains its section anchor and leaves the draft clean. Canonical profile rejection, optimistic save/conflict handling, independent optional selection and invalid ray boundaries have targeted regressions.

### Settlement evidence

In seed -6318123714405175225, the baseline 32-cluster survey has 27,328 eligible village chunks, nine allowance slots and four actual fitted starts. One is the intentional Towns & Towers forest ruin. In a bounded budget comparison, one rejected cluster fails all eight alternatives and a control succeeds on its second; production retains three alternatives per allowance and its safety rules.

The revised village rate 0.889 produces twelve allowances and nine actual starts in sixteen clusters. The same sixteen baseline clusters had two allowances and one start. Five revised starts use intentional forest-ruin content. Counts are not population verification or worldwide encounter rates. The mansion rate 0.32 remains biome-limited: a controlled dark-forest eight-cluster fixture produces one allowance, one fitted layout and one real mansion start. It is an enabled placement control, not a natural rarity measurement.

The marked personal Sky Villages explain the distance complaint: one host was only planned/unsaved at the tested positions; another had actual saved terrain about 287 blocks away, outside a 12-chunk client view. Neither observation proves a missing-island generator defect. Paired native start records sharing a physical layout are not counted as separate settlements or declared a duplication bug.

### Other log findings

DH 3.3.3’s missing-payload errors occur before decoding when a split-buffer entry is absent. Its receiver expires entries after 30 seconds and the request queue retries. These traces do not prove LOD database corruption or identify why the buffer was unavailable. No cache clearing, fabricated data or speculative network hook was added. Recheck after removing the independent Render-thread stall. The Accessories null-entity respawn error was not reproduced in the current log. Copycats 3.0.9 supplies the stale `copycats:copycat_catwalk` physics selector itself; the missing block’s ignored mass/volume entry is unrelated to picking. These remain upstream investigation limits, not claimed repairs.

## Historical evidence

The following records are pinned to the published 0.29.1 tree. They preserve artifact hashes, controls, failures and coverage limits without making old test results the operating instructions for today's checkout. Use the named record when tracing a patch; rerun affected checks after changing its dependencies.

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
