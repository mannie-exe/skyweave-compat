# Horizontal terrain variation

The marked Skyweave preset composes horizontal outlines, selective edge disturbance, and fractured exteriors over Sky Archipelago 1.4's native land body. [Terrain](terrain.md) owns geological mapping, interiors, hydrology, structures, and saved-world boundaries. [Configuration](../configuration.md#island-outlines-and-fractured-terrain) owns field defaults and application timing; [verification](verification.md) separates actual native observations from visual tuning and balance.

## Geometry and ownership

`ShapeProfile` is an immutable startup description. `IslandGeometry` selects seeded aspect and retains native orientation. For equivalent radius r and aspect a, axes r√a and r/√a approximately preserve area; integer rounding and native shape masks still affect realized land. Ordinary cluster spacing, center jitter, activation, elevation, and the native vertical taper retain their owners.

`IslandStyles` resolves ecology and outline variation using independent salts and a bounded field-local cache. `OceanShapeMixin` applies broad horizontal warp before the native shape mask. Crescent openings and crater intent remain part of the retained body.

`GranularEdges` adds density lobes outside that body rather than subtracting notches from its useful land. Mixed profiles choose coherent smooth and granular perimeter sectors; Disintegrating profiles increase coverage and maximum reach while retaining quiet sectors. Broad periodic noise varies local reach below the conservative maximum, avoiding a continuous uniform fringe. The lobe kernel provides seeded positions, varied elliptical cell radii, local warp, independent fragment height/depth and outward decay. Cell-center distance is estimated from the native local boundary normal. Deterministic thinning and shrinking radii make outer remnants smaller and more widely separated on average; no concentric tiers or per-column grid rescaling are introduced. A small separation admits detached shoulders. `PerimeterColumn` carries fragment metadata separately from native horizontal coverage; `ShapeDensityMixin` builds its own vertical interval, within the envelope, and retains the native core's broad taper. A bounded underside accent adds small points rather than replacing the body sampler.

`FragmentedExterior` remains the separate biome-aware broken-land profile: bounded jittered cells, joins, missing remnants and shared remnant heights. Fragments retain their parent descriptor, geological frame and structure allowance. They do not create new resource or landmark budgets. Added apron columns retain BASELINE interiors; existing fractured bodies retain their configured NOISE/HOLLOW behavior and shell checks.

Placement and native plan bounds include rotated aspect changes, broad displacement, outer-apron reach and companions before clearance. The field's same accepted membership reaches fill, height/column queries, structures, metadata previews and consumers. A rejected ocean candidate cannot reserve phantom clearance. Query order, eviction and worker scheduling must not change the result. Conservative larger bounds can reject a neighboring candidate; unchanged spacing settings alone do not prove unchanged realized cluster counts.

Potentially fragmented columns require exact snapshot sampling: four empty corners cannot prove that the intervening cell contains no remnant. Proven-empty candidate-index regions retain the empty fast path. Compare exterior, pre-water interior, completed columns and saved blocks separately. Complete legacy edge objects are strictly translated for authoring; unknown, partial or incorrectly typed objects fail rather than silently changing terrain. Opening an editor can normalize its draft without rewriting a startup file.

## Biomes and water

Fracture geometry is independent of NOISE/HOLLOW interiors and surface ecology. Mushroom Fields retain native mycelium and mushrooms; Badlands retain terracotta and dry vegetation. Configured biome IDs admit a fractured alternative without changing every island of that biome. Foreign biome namespaces keep their own surface and decoration ownership.

The rare Deep Dark profile requires native underground Deep Dark climate eligibility and the cave-biome adaptation. It uses the same fractured geometry, projects the native biome into stored and physical surface queries, and places registered vanilla sculk patches. It introduces no dimension, external content, custom loot, guaranteed Ancient City, or surface-city fallback. Ordinary burial, cavernization, and support checks still decide whether a city fits.

Ocean admission retains the native eligibility tag. A failed probability roll leaves void, matching the native all-ocean-archetypes-disabled outcome. Marked-preset ocean weights and Lagoon edge treatments come from the owned ocean profile; ordinary upstream presets keep their own settings. Broken Reef is not reused as a water enum for fractured land.

Separate solid spires may accompany ocean anchors. They remain outside native wet/rim support, enter cluster bounds and clearance, and use parent geology. They do not enable ordinary land satellites or alter the anchor's underside. Native shore projection, water material, seals, fluid ticks, and local spawning checks retain their separate responsibilities. A planned edge mode does not prove that a waterfall has flowed in saved terrain.

## Island metadata locator

Operators can inspect accepted plans without loading or generating chunks:

```text
/skyweavecompat islands search 24000 archetype=lagoon edge=perimeter
/skyweavecompat islands status
/skyweavecompat islands page 2
/skyweavecompat islands cancel
```

The search captures its origin and dimension. Filters compose as unique `key=value` arguments; suggestions expose native archetypes, `exterior=native|fractured|deep_dark`, `interior=baseline|noise|hollow`, `edge=none|native|coastal|perimeter|mixed|disintegrating`, biome IDs, and families. The default family is `anchor_plateau`; `family=any` includes companions. Non-native Lagoon edge filters cannot be combined with another archetype.

Searches continue automatically in worker slices of at most 256 cells or two seconds, yielding through the server tick scheduler between slices. Search bounds allow at most 131072 blocks and 65536 cells; overly dense configurations require a smaller radius. The cursor retains at most 64 nearest planned centers, ten per page. The full finite search must finish to establish those nearest results. Status and pages remain available while running; these pages are provisional. No match so far differs from completed absence. The old `more` command is an observational alias for `status`; it does not enqueue extra work.

Only one search runs per server. Another requester cannot replace active work. Starting a new search after completion replaces the retained result set. Cancellation, player disconnect or dimension change, dimension unload, and server shutdown cancel pending work. Native field planning runs on a bounded metadata worker; immutable snapshots published by server-thread callbacks own status and paging. Progress uses the player action bar at most every two seconds or console messages at most every ten seconds; completion prints one result page in chat. The worker receives immutable settings and the already-configured field, never chunk tickets or a mutable world-generation request. Review the exact native cache and biome-sampler concurrency contracts on dependency updates.

Result rows show representative coordinates, horizontal distance/direction, and the main island profile. Hover reveals biome, family, modifiers and identity. Clickable coordinates only suggest a teleport command for review; previous/next/cancel actions execute the corresponding locator command. A center may be void or unsafe for landing. Planned terrain is not a claim that chunks are saved, unmodified, or that fluid has flowed. Foreign or disabled generators are unsupported. The command neither teleports nor starts pregen.

## Structure discovery

Minecraft's random-spread lookup enumerates potential chunks before presence resolution; it does not first call the owned `isPlacementChunk` override. `ArchipelagoStructurePlacement.applyAdditionalChunkRestrictions` shares the distribution's selected-site rejection with native lookup after loaded/saved-start checks. Unselected void candidates can therefore be rejected without requesting STRUCTURE_STARTS. Before fitting a full cluster plan, the same sampled area, biome eligibility, allowance, and ranked attempt budget used by fitting reject impossible family/site pairs. This avoids fitting unrelated surface families for a city search with no eligible city site. Full fitting, family priority, conflicts, and successful starts retain their existing behavior.

An unbound distribution must not publish a cached negative based on provisional fitting. Runtime context binding remains authoritative. Preserve canonical relocated positions, saved starts, references, skip-known behavior, native bypasses, disabled adapters, and unknown structures. Finding a positive city and rejecting void requests do not establish all rare-search latency or structure frequency; retain bounded cold/warm and missing-result checks.

This is a discovery repair, not a rarity or watchdog-policy change. Two original watchdog reports showed synchronous STRUCTURE_STARTS waits; only one also had active DH workers. Those observations did not establish a rarity regression, deadlock, or DH as the sole cause. Use isolated fixtures rather than repeating watchdog crashes on a playing server.

## Deferred Gargantuan islands

Gargantuan is a distinct special type with independent 4000–6000-block diameter and provisional 1/150 selection per activated cluster opportunity. It is not a larger LARGE band and must not inherit ordinary size settings. This idea is documented, not scheduled for implementation here.

It needs its own placement/reservation layer, conservative bounds, finite spatial lookup, cache budgets, biome policy and structure-density review. A 1024-chunk-radius disk contains about 146 current grid cells, or 124 activated candidates before clearance, so 1/150 implies roughly 0.8 candidates in that area before overlap rejection. Neither that rate nor a nominal 3×3/4×4 footprint guarantees one island or a valid reservation. Reserve actual bounded influence, including distortion and companions; keep the existing ordinary grid untouched.

The 4–6 km diameter exceeds current Compat radius validators and native configured radius limits. Enlarging all lookup ranges would impose a cost on ordinary chunks. Broken Reef planning also contains all-cell searches that require measurement before scaling. A 5 km disk has about 69 times a 600 m disk's footprint; resource abundance and useful travel need review independently of its rarity.
