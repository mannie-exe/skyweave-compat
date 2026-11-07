# Horizontal terrain variation

The marked Skyweave preset composes horizontal outlines, selective edge disturbance, and fractured exteriors over Sky Archipelago 1.4's native land body. [Terrain](terrain.md) owns geological mapping, interiors, hydrology, structures, and saved-world boundaries. [Configuration](../configuration.md#island-outlines-and-fractured-terrain) owns field defaults and application timing; [verification](verification.md) separates actual native observations from visual tuning and balance.

## Geometry and ownership

`ShapeProfile` is an immutable startup description. `IslandGeometry` selects seeded aspect and retains native orientation. For equivalent radius r and aspect a, axes r√a and r/√a approximately preserve area; integer rounding and native shape masks still affect realized land. Ordinary cluster spacing, center jitter, activation, elevation, and the native vertical taper retain their owners.

`IslandStyles` resolves per-descriptor ecology and disturbance using separate salts and a bounded field-local cache. `OceanShapeMixin` applies continuous broad warp and a periodic perimeter mask at the native horizontal sampling boundary. Smooth and disturbed stretches coexist. The finite edge band admits coherent notches and separated remnants without applying erosion throughout the body. Native Crescent openings and crater intent remain part of the input mask.

`FragmentedExterior` uses bounded, jittered cells, selective joins, missing remnants, and shared remnant height choices. `ShapeDensityMixin` translates admitted native body intervals within dimension and plan bounds. It does not install a second taper sampler. Fragments retain their parent descriptor, geological frame, and structure allowance; they are not independent resource or landmark budgets. Thin remnants and positions near fragment boundaries retain rock when the configured interior shell cannot fit.

Placement and native plan envelopes enclose rotated aspect changes, displacement, and companions. The field's same accepted membership reaches fill, height/column queries, structures, and metadata previews. A rejected ocean candidate cannot reserve phantom clearance. Query order, cache eviction, or worker scheduling must not change membership or geometry.

Potentially fractured columns require exact snapshot sampling: four empty corners cannot prove that the intervening cell contains no remnant. Proven-empty candidate-index regions retain the empty fast path. Native cleanup and hydrology remain separate stages; compare raw exterior, pre-water interior, completed columns, and saved blocks instead of treating one as proof of another.

Per-island outline and edge strengths use independent seeded draws around their configured bases. The shared strength-variation control does not change selection probabilities, spacing, size budgets, or native rotation. Area-preserving aspect choices retain their own seeded range. Edge coverage remains at most the whole perimeter, displacement remains within the configured edge band and small-body support envelope, and zero edge amplitude disables that disturbance. A zero variation value retains fixed configured strengths.

## Biomes and water

Fracture geometry is independent of NOISE/HOLLOW interiors and surface ecology. Mushroom Fields retain native mycelium and mushrooms; Badlands retain terracotta and dry vegetation. Configured biome IDs admit a fractured alternative without changing every island of that biome. Foreign biome namespaces keep their own surface and decoration ownership.

The rare Deep Dark profile requires native underground Deep Dark climate eligibility and the cave-biome adaptation. It uses the same fractured geometry, projects the native biome into stored and physical surface queries, and places registered vanilla sculk patches. It introduces no dimension, external content, custom loot, guaranteed Ancient City, or surface-city fallback. Ordinary burial, cavernization, and support checks still decide whether a city fits.

Ocean admission retains the native eligibility tag. A failed probability roll leaves void, matching the native all-ocean-archetypes-disabled outcome. Marked-preset ocean weights and Lagoon edge treatments come from the owned ocean profile; ordinary upstream presets keep their own settings. Broken Reef is not reused as a water enum for fractured land.

Separate solid spires may accompany ocean anchors. They remain outside native wet/rim support, enter cluster bounds and clearance, and use parent geology. They do not enable ordinary land satellites or alter the anchor's underside. Native shore projection, water material, seals, fluid ticks, and local spawning checks retain their separate responsibilities. A planned edge mode does not prove that a waterfall has flowed in saved terrain.

## Island metadata locator

Operators can inspect accepted plans without loading or generating chunks:

```text
/skyweavecompat islands search 24000 archetype=lagoon edge=perimeter
/skyweavecompat islands more
/skyweavecompat islands page 2
/skyweavecompat islands cancel
```

The search captures its origin and dimension. Filters compose as unique `key=value` arguments; suggestions expose native archetypes, `exterior=native|fractured|deep_dark`, `interior=baseline|noise|hollow`, `edge=none|native|coastal|perimeter`, biome IDs, and families. The default family is `anchor_plateau`; `family=any` includes companions. Non-native Lagoon edge filters cannot be combined with another archetype.

Each batch visits at most 256 cells, yielding after its two-second time budget between cells. Search bounds allow at most 131072 blocks and 65536 cells; overly dense configurations require a smaller radius. The cursor retains at most 64 nearest planned centers, ten per page. Paused results are provisional; use `more` until completion to establish the nearest retained centers within the requested radius. No match so far differs from a completed search with no match.

Only one batch runs per server. The requester owns its cursor; cancellation, dimension unload, and server shutdown cancel pending work. Native field planning runs on a bounded metadata worker; server-thread callbacks own replies and lifecycle state. The worker receives immutable settings and the already-configured field, never chunk tickets or a mutable world-generation request. Review the exact native cache and biome-sampler concurrency contracts on dependency updates.

Results identify the cluster/member, representative X/Y/Z, horizontal center distance and direction, archetype, exterior/interior, biome, and edge mode. A center may be void or unsafe for landing. Planned terrain is not a claim that chunks are saved, unmodified, or that fluid has flowed. Foreign or disabled generators are unsupported. The command neither teleports nor starts pregen.

## Structure discovery

Minecraft's random-spread lookup enumerates potential chunks before presence resolution; it does not first call the owned `isPlacementChunk` override. `ArchipelagoStructurePlacement.applyAdditionalChunkRestrictions` shares the distribution's selected-site rejection with native lookup after loaded/saved-start checks. Unselected void candidates can therefore be rejected without requesting STRUCTURE_STARTS. Before fitting a full cluster plan, the same sampled area, biome eligibility, allowance, and ranked attempt budget used by fitting reject impossible family/site pairs. This avoids fitting unrelated surface families for a city search with no eligible city site. Full fitting, family priority, conflicts, and successful starts retain their existing behavior.

An unbound distribution must not publish a cached negative based on provisional fitting. Runtime context binding remains authoritative. Preserve canonical relocated positions, saved starts, references, skip-known behavior, native bypasses, disabled adapters, and unknown structures. Finding a positive city and rejecting void requests do not establish all rare-search latency or structure frequency; retain bounded cold/warm and missing-result checks.

This is a discovery repair, not a rarity or watchdog-policy change. Two original watchdog reports showed synchronous STRUCTURE_STARTS waits; only one also had active DH workers. Those observations did not establish a rarity regression, deadlock, or DH as the sole cause. Use isolated fixtures rather than repeating watchdog crashes on a playing server.

## Deferred Gargantuan islands

Gargantuan is a distinct special type with independent 4000–6000-block diameter and provisional 1/150 selection per activated cluster opportunity. It is not a larger LARGE band and must not inherit ordinary size settings. This idea is documented, not scheduled for implementation here.

It needs its own placement/reservation layer, conservative bounds, finite spatial lookup, cache budgets, biome policy and structure-density review. A 1024-chunk-radius disk contains about 146 current grid cells, or 124 activated candidates before clearance, so 1/150 implies roughly 0.8 candidates in that area before overlap rejection. Neither that rate nor a nominal 3×3/4×4 footprint guarantees one island or a valid reservation. Reserve actual bounded influence, including distortion and companions; keep the existing ordinary grid untouched.

The 4–6 km diameter exceeds current Compat radius validators and native configured radius limits. Enlarging all lookup ranges would impose a cost on ordinary chunks. Broken Reef planning also contains all-cell searches that require measurement before scaling. A 5 km disk has about 69 times a 600 m disk's footprint; resource abundance and useful travel need review independently of its rarity.
