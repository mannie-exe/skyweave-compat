# Island generation

The `skyweave_compat:islands_v1` preset owns the expanded terrain described here.
The [verification record](verification.md) distinguishes native observations from
unverified survival balance and vehicle capabilities.

## Ownership and scope

The preset owns its dimension type, noise settings and embedded Sky Archipelago
settings. Both height envelopes are `[-128, 768)`. The noise settings retain
Minecraft 1.21.1's Overworld router and surface definitions with the expanded
envelope. Sky Archipelago still supplies terrain filling and surface treatment;
this bypasses the complete vanilla terrain pipeline. Skyweave: Compatibility Patches supplies its own interior
composition and connects the native large-vein material rule. Classic carving still
has a separate native aquifer query, described below.

`GeneratorProfileMixin` opts in only when the generator references the versioned
noise-settings key. It configures each field before returning it to a caller.
The field, sampler and descriptor factory share an immutable `GenerationProfile`;
there is no global current-world flag. Other presets retain upstream terrain.

The marked generator resolves its embedded settings on both dedicated servers and
singleplayer. Upstream otherwise gives a dedicated server's common TOML priority,
which would make the same preset produce different settings across runtimes.
The adapter preserves upstream resolution for every other generator. Missing
embedded settings fail explicitly rather than falling back to a host's defaults.

`IslandGeometry` retains upstream archetypes, horizontal shape noise and shape
parameters while separating anchor dimensions from companion radius/orbit ranges.
The spatial grid indexes candidates; accepted centers are displaced independently
on X and Z. Companions retain explicit source-cell ownership even when they cross
a cell boundary. Terrain fills, height queries, structure support and previews
consume the same accepted descriptors.

Base main-island radii are 270 to 370 blocks before area-preserving horizontal elongation; ordinary companions use radii 40 to 110 and orbits 470 to 680. Separate solid spires retain their smaller dimensions and can accompany land or ocean anchors. Ocean clusters do not receive ordinary land satellites. [Horizontal variation](terrain-variation.md) defines outlines, disturbed edges, and fractured ecology without replacing the native vertical body. Each cluster selects an integer nominal height from 250 to 400. `GenerationProfile.cliffDepth` inverts native 1.4's full-coverage body-depth approximation; it does not replace the native sampler. Parameter rounding and native relief mean the target is not the height of every column or a guarantee of exact island extrema.

Requested centers remain Y=200 to 490. Placement fits the selected native body rather than clipping it: a 400-block target currently needs Y>=338 at the default clearances, while a 250-block target can begin at Y=200. The lower bound accounts for native shoulder expansion, bounded relief and wet-profile underbelly budgets. Companion elevations are fitted independently. The shape reserves 32 blocks above the dimension floor and 200 above terrain. Terrain and geology are not compressed to fit.

Sky Archipelago 1.4 owns land-body intervals, rims, erosion and exterior archetypes. Compat retains the native taper; horizontal masks and bounded remnant translations compose with its body intervals. Pure interval and proven-empty paths remain available. Native four-corner interpolation remains available for baseline columns; columns involving authored interiors require exact resolution because four exterior heights cannot prove the absence of a passage between them.

Conservative lookup bounds enclose native rotated/warped plans and water archetypes. They do not modify the shape and are not realized shoreline measurements. Whole-cluster clearance compares native plan support rectangles, including companions. Trees, structures and player construction are outside this terrain-support guarantee. Missing clusters can create longer journeys.

Completed point queries use the same native chunk snapshot as filling, including shoreline sealing, cleanup and fractures. A sparse scan that requests one point per chunk therefore pays for a neighborhood rather than only that column. Reuse native snapshots for bulk work; raw/exterior planning queries are cheaper but do not represent completed terrain. The [verification record](verification.md#sky-archipelago-14-terrain-and-city-boundaries) gives the bounded before/after workload and its limits.

Updates must recheck native plan bounds, prepared land/water intervals, direct and snapshot resolution, shoreline sealing, surface cleanup and the geological datum. Query/fill agreement is a runtime requirement, not a consequence of matching method names. Ordinary presets retain upstream behavior. The native codec still serializes the ground-support groups as `surface_sky` and `small_sky`; only their Java accessors were renamed to `mediumGround` and `smallGround` in 1.4. Do not rename serialized keys based on accessor names.

The native fresh-world fallback predicts a landing before carving/features have
necessarily left the same surface. `SpawnFallbackMixin` validates only its final
spawn assignment in this preset. `SpawnLanding` checks the generated chunk's
motion-blocking height, solid support, clear feet/head and fluids, then searches
bounded four-block offsets up to 16 blocks if necessary. It fails explicitly when
no landing exists. Chunk loads occur only during startup, never in the terrain
query path. Review the upstream fallback's event and saved-world guard when
updating; this hook must not relocate existing worlds. When the local 16-block check fails on water or an opening, Compat considers up to 24 nearby accepted descriptors within 8192 blocks, inspecting at most 216 planned positions and explicitly requesting at most 32 chunks across the whole validation. It skips planned underwater caps, then checks generated support, collision-free feet/head space, fluid, and the world border. This startup-only fallback can use a solid companion; metadata lookup itself never requests chunks. Failure remains explicit when the finite search finds no safe landing.


Embedded settings retain biome eligibility, archetypes and structure policy.
Their serialized spacing is a placeholder: upstream caps that field at 2000,
while the field and sampler adapters own the real grid. Upstream common TOML,
DYNAMIC spacing and size/band controls do not override this preset's geometry.
See [configuration](../configuration.md) for authoritative controls.

## Cluster placement

`ClusterPlacementMixin` separates candidate construction from acceptance. A cell
first draws its activation and native archetype/companion descriptors. Biome
admission samples the displaced anchor at its surface datum. Ocean exclusion and
other upstream policy still apply; admission does not assign one biome to an island.
`IslandGeometry` retains the native shape noise and the configured vertical profile.

`ClusterLayout` assigns a stable priority to each eligible candidate. It rejects a
candidate if a higher-priority eligible neighbor has any island support rectangle closer
than `minimumClusterGap` to one of its rectangles. Exact-gap contact is permitted. Ties
are resolved by source-cell coordinates. Accepted clusters therefore cannot violate
the gap, including companion-to-companion approaches. The rule compares candidates,
never recursively accepted neighbors. It can discard a candidate whose winning
neighbor is itself rejected elsewhere; this trades some density for a bounded,
order-independent decision. It is not a maximal packing or a blue-noise guarantee.

The default uses ±750 independent X/Z offsets with a 2400-block candidate grid.
Activation precedes biome and clearance rejection, so it is not the realized
fraction of occupied cells. Larger jitter does not shrink islands or move rejected
candidates back toward cell centers.

The conflict neighborhood is bounded by
`ceil((2*jitter + 2*clusterReach + minimumClusterGap) / spacing)` cells. Point and
area lookups use `ceil((jitter + reach + ceil(spacing/2)) / spacing)`. With the default maximum elongation, conflict search covers three cells and point/area lookup two. Profile validation limits conflict searches to eight cells.
Village lookup includes its maximum supported placement reach and retry allowance.
Native density, column resolution, chunk snapshots, previews and geological lookup
use the derived bounds; none loads neighboring chunks to decide terrain. Candidate
and decision caches are separately capped at 8192 entries per field. Eviction may
repeat deterministic work but cannot change acceptance.

`ClusterField` provides accepted membership by source cell. A companion's position
is not its cluster ID. Geological mapping and the native survey use that membership;
spawn and optional village/delivery consumers query the accepted field.

![Native descriptor layouts at equal scale](cluster-layout.svg)

The comparison uses the same seed and vertical profile, but changes both horizontal
placement and the biome admission point. It is not a jitter-only experiment. The
[verification record](verification.md) reports alignment, density, clearance and
travel observations. Regenerate the figure with `tools/layout_report.py` from
native terrain reports; circles represent descriptor radii, not measured shores.

When updating Sky Archipelago, inspect `sampleCluster`, descriptor construction,
biome admission, cache keys, preview filtering and all three native search loops.
Verify that the density support bound still contains the generated terrain. A
matching method signature alone does not preserve these contracts. Changed layout
requires a new recorded algorithm identity and a fresh world.

## Columns and internal cavities

`IslandDensityEvaluator` prepares native land-body or water-archetype intervals. `IslandColumnResolver` uses those intervals and its density classification to produce ordered solid segments before `IslandOverlapResolver` applies the selected overlap policy. These are upstream shape terms, not Minecraft's complete noise-cave router.

The preset explicitly selects `OVERLAP`, retaining all exterior solid intervals.
`VOID` retains only the highest, while `CRATER` selectively modifies lower islands.
Cavity subtraction runs after this policy. It cannot make the overlap resolver
mistake a cavern floor for a separate island and discard it.

`IslandInteriors` evaluates each contributor separately within the admitted exterior.
It subtracts only that contributor's selected cavities, then unions the surviving
rock. A hole in one island cannot erase a different island. `LayeredColumn` retains
the original exterior intervals alongside the native post-cavity solid segments.
Native completed `ColumnProfile` values may describe only the upper surviving segment after sidewall cleanup. Biome projection explicitly reads the uncarved exterior; solid-segment,
base-column, density-sign, top-solid-interval and support queries use actual baseline
rock. Saved blocks remain authoritative after classic carving, decoration or play.
`TerrainStage` scopes uncached planning reads to exterior or pre-water interior
columns. These reads bypass the final-column cache and restore their stage after
errors. Later planners can inspect earlier terrain without recursively invoking
themselves or consulting generated neighbor chunks.

Noise interiors combine intersecting noise bands and larger chambers. Hollow interiors
use an irregular chamber with three rock pillars. Descriptor-seeded selection also
admits occasional side passages and, by default, HOLLOW-only skylights. Their bounded seeded oval/warped outlines connect through the cap to an actual retained cavity, preserve water protection and omit columns blocked by pillars. NOISE skylights and the whole roof-opening feature have separate controls; the existing side-passage roll remains independent of roof applicability. Vertical shell protection and a radial
shoulder limit constrain ordinary cavities; selected openings deliberately bypass that
shell. These are independently configured interiors, not new upstream exterior enums.
The selected native exterior remains independent of the interior mode.

Surface painting follows the exterior cap and skips cavity air/fluid instead of
refilling openings. Lush/dripstone projection tests the exterior envelope, including
interior air and retained lower bodies. Ordinary cave Deep Dark retains native 1.4's fixed Y=-48 biome proxy and lower 45% of the uncarved native exterior. The separately selected [fractured Deep Dark ecology](terrain-variation.md#biomes-and-water) also owns its surface biome without guaranteeing a city. Both stored biome creation and structure eligibility use that same body; a surviving roof cannot replace its depth band. Ancient Cities query their actual proposed/final interior height, without a separate eligibility-only height substitution. `CaveProjection` also serves the generator's physical-biome boundary, using its existing sampler-to-world association. Native land-host resolution otherwise drops cave ownership before final structure validation and outside generation scopes. The adapter reuses native Deep Dark eligibility/projection methods and shares the relative lush/dripstone resolver with biome filling; it does not fabricate a structure scope. Ordinary presets, suppressed projection and samplers not yet associated with a world retain native behavior. Dependency updates must recheck both projector overloads, the generator's physical-source return boundary, native projection methods, sampler association, host-biome suppression, and stored/physical-query agreement. Native biome discovery also has its own candidate search; matching queried cells does not prove that search finds every projected cave biome. Native classic carving is separately configurable and
can alter the baseline after these queries. Queries never load neighboring chunks.

## Local water

Sky Archipelago 1.4 owns the native solid/fluid column, shoreline seal and water-archetype contracts. `RiverConfiguration` explicitly defaults its river and lake planners off through `RiverSettings.CODEC`; its nested JSON uses the shared optimistic editor. A missing optional codec field must not silently enable a different policy. Optional Dynamic Waters supplies upstream mountain watersheds and basin lakes through these same columns, without a second Compat basin planner.

Completed point queries and chunk filling consume the same cached native snapshot, including interpolation, hydrology, shoreline sealing, peak cleanup and sidewall treatment. EXTERIOR and INTERIOR planning reads retain their separate earlier meanings and never load neighboring chunks or tick fluids. The earlier raw exterior is not necessarily byte-identical to a completed surface after native interpolation and cleanup.

The Dynamic Waters bridge substitutes accepted EXTERIOR main-body heights for the vanilla density-router shortcut. `DrainageBoundary` distinguishes supported land, physically present native water and terminal void, including detached fringe remnants. Ocean biome names alone do not prove that water exists. `WaterSinksMixin` uses local rim/water elevations in sink settlement and mouth emission, terminates mouth walking at the first physical boundary, and disables the upstream sideways sea-contact shortcut in marked worlds. Routing metadata may reach terminal sky; it never authorizes foundations or source-water shelves there.

Water proposals precede cavity subtraction. The resolver receives an optional per-contributor bed protector from `IslandWaterColumns`. It asks upstream carving against EXTERIOR under an explicit early-stage scope, retains the configured inclusive shell beneath an admitted bed, then subtracts the remaining interior. The bounded field-local floor cache contains immutable decisions; it does not store mutable carving objects. A contributor cannot retain rock outside its original envelope or protect an unrelated overlapping island. Raw proposal reads bypass the INTERIOR admission wrapper, preventing a planning cycle.

Snapshot filling and upstream post-carver repair then share the protected INTERIOR boundary through `WaterCutOwnershipMixin`. Unsupported original exteriors still reject cuts. Inert rejected cuts retain the caller's ground in both height fields, because native repair reads `waterLevel` even when `modifies` is false. Native ocean beds/seals remain outside watershed carving. Channels retain the native outlet flag, while lakes keep containment. Bed protection fixes the demonstrated twelve-block cavity-roof interruptions without reducing the shell or raising water to conceal dry strips. Base-column/height queries and stored river biomes consume the completed admitted water column.

Native lowland mode is `VANILLA`: `NONE` rewrites ridge inputs even though its name suggests otherwise. Experimental mountain tunnels are unsupported by this bridge and default off. Global border prewarming is suppressed for marked worlds; bounded on-demand upstream planning and packets remain available. Persisted watershed stamps include the saved owning generator identity and effective generation settings. Flow zones and configured biome vetoes retain ownership; only the automatic ocean-neighbor veto is bypassed for a real island stream's flow query. Supported cases and update seams are recorded in [adapter maintenance](adapters.md).


`WaterCarverMixin` protects authored native water, retaining seals and river floors from classic aquifer replacement. Native river postprocessing remains upstream-owned. `WaterPostprocessing` adds flow-frontier updates for selected ocean-profile extensions during filling; read-only queries never schedule updates. Initial sources, actual flow and saved/reloaded fluid behavior need separate verification.

`LocalWater` adapts the sea-level predicate only inside surviving native local-water intervals. Vanilla biome spawn lists, free space, population caps and other spawn predicates remain authoritative. This does not certify aquatic progression or add ocean species to an inland biome.

## Ocean habitats

The global ocean layer stays disabled. `OceanProfile.enabled` separately admits native floating water archetypes in eligible ocean-biome locations; its default is true. Existing explicit false values remain authoritative and settings remain part of saved generation identity. The owned ocean profile resolves marked-preset admission and relative weights; native material, biome, seal, and underbelly code retains its responsibilities. Wet anchors can receive separate solid spires, but no ordinary land satellites.

`OceanIslands` extends native Lagoon profiles only. Open-cove mode lowers a broad shore arc with graded shoulders, keeping the basin floor and interior islets; perimeter mode lowers the rim around the native footprint to provide falling edges. Ocean hosts share the ordinary anchor sizing, seeded aspect and broad warp. Native wet columns retain their own geometry and provenance. There is no independent enclosed basin, ocean-biome projector or underside-outlet generator. Selection and extension parameters are deterministic and configurable; configured chances are not measured realized frequencies.

`OceanPerimeter` caches a radial outer silhouette per native plan for additive dry aprons. Its fixed 96 rays perform at most 128 native occupancy samples each on a cold plan; column queries interpolate that metadata without sampling neighboring chunks. This outline is only a conservative placement guide for extra dry fragments, not an alternative water generator or an exact signed-distance field. Internal reef channels stay behind the sampled outer silhouette, and open-cove mouths exclude aprons. Native water profiles always win at a column; spire clearance includes both wet columns and added fragments. Cache eviction may repeat the bounded sampling without changing results.

When updating Sky Archipelago, review the native ocean coverage sentinel (outside below -0.5), finite footprint bounds, radial sampling limits, water-profile provenance through all column reconstruction and snapshot stages, native rim sealing, river/aquifer ownership, flow-frontier scheduling, projected biomes, and native structure support. Selected fixtures cannot establish world-wide frequency, aquatic spawn populations or complete ocean progression.

## Large veins

`IslandVeins` invokes Minecraft's `OreVeinifier` with the preset's toggle,
ridge and gap definitions bound to the existing `RandomState` noises. It does not
replace nodes in C2ME's compiled runtime router. Interpolated markers use the noise settings' cell dimensions
and Minecraft's Y/X/Z interpolation order. Samples and positional randomness use
island-relative reference depth; the material rule keeps native thresholds, granite/
copper and tuff/iron composition, and raw-ore blocks. A thread retains at most one
cell per interpolated noise. Cache state does not affect query order or chunk seams.

Only surviving stone/deepslate is eligible. Air and water cannot acquire vein blocks.
The same material plan serves fill and base-column queries. Ordinary placed ore
features run afterward and retain their counts, replacement predicates and mapped
heights. This adapter does not add large zinc or platinum veins. The preset's
`ore_veins_enabled` controls this material path; the flag alone was insufficient
while the custom fill bypassed Minecraft's native vein rule.

When updating Minecraft, review `OreVeinifier`, `RandomState`, `NoiseChunk` marker
wrapping/interpolation and the vein functions in the preset. Verify native generated
raw blocks, reference-depth distributions, fill/query agreement and reopening.

## Surface and generation stages

Sky Archipelago 1.4 fills chunks through `SkyIslandTerrainPass` material plans and updates `OCEAN_FLOOR_WG` and `WORLD_SURFACE_WG`. Its height queries distinguish heightmap predicates. Compat attaches the geological frame and native vein material rule to all fill/base-column/material-plan paths. Baseline queries remain distinct from completed saved heightmaps after trees, structures or fluid updates.

`SkyIslandSurfacePass` combines native biome palette overrides with `IslandSurfaceRules`, which consumes the selected noise-settings surface rules. Native structure-base blending and mineshaft support remain separate consumers. A surface-rule definition still needs verification at its actual consuming stage; it does not imply that every vanilla terrain feature runs.

`IslandSurfaceScope` supplies the marked field and settings to native surface helpers and restores prior context on exit. Skin writes preserve cavity air and fluids; structure foundation writes may intentionally fill air. `BiomeMaterials` replaces only upper grass/dirt soil with tagged mangrove mud, after native override selection. `resolveSurfaceStone` uses island-relative geological Y inside this scope. The resolved mud-biome tag is part of generation identity.

Classic carving, biome decoration, structure references, light/postprocessing and
initial mob population retain Minecraft's chunk-stage machinery. Initial population
uses `NoiseBasedChunkGenerator.spawnOriginalMobs` and native heightmap/support
checks; no separate island mob ecology is implemented. Structure spawn overrides
remain native. Fresh-preset generation is supported; vanilla terrain blending and
below-zero retrogen are not an old-world migration path.

## Geological frame

`GeologicalFrame` maps reference Y=-64 to the anchor's nominal native-body bottom and reference Y=64 to `centerY + plateauHeight`. `IslandGeometry.geologicalFrame` is the shared descriptor-to-frame owner. Land height follows the native full-coverage approximation; wet height uses the native descriptor budget. The datum is independent of cavities, conservative search bounds and a particular surface column. Underground heights scale with integer floor division; above reference 64, heights retain one-block offsets. Out-of-range reference samples remain outside rather than being clamped. Cave-biome and ore-reporting inverses use the same piecewise mapping.

The frame is shared across an anchor, its shorelines and companions; a shallow
companion does not acquire a complete compressed ore column. The datum is not the
actual surface of every column. Relief and erosion remain meaningful: upper iron,
coal and emerald attempts can land in air, while attempts below the datum remain
in the deep body where replacement rules permit them. Native counts are unchanged.

`OreHeightMixin` intercepts the existing height modifier only for features in
`#skyweave_compat:relative_geology`, inside the opted-in generator. It samples
the original height provider against an explicit reference context. Absolute,
bottom-relative and top-relative anchors retain their original meaning. It
consumes the sample even when the target column is void, then maps successful
samples. Counts, feature order, biome checks, configured veins, replacement rules
and exposure probabilities remain native. Nether/End and unlisted placements
are unchanged. Feature-internal checks remain native. Fossils still fit their selected template
below the local heightmap and reject excessive empty corners. Standalone jungle
vines use a separate `relative_surface` tag: reference 64 to 100 becomes actual
column top through top+36, preserving their neighbor-attachment test. Blue ice is
not moved: its native feature requires water, packed ice and a position below sea
level, which the disabled global-ocean layer does not provide. Moving its origin
would not supply that environment. Ocean-specific content is not guaranteed.

The tag includes vanilla Overworld ores, underground clay, both fossil placements,
geological patches, Create zinc and
Overworld striated deposits, and Propulsion platinum. Mod entries are optional
registry references; their absence does not invalidate the datapack.

Geodes, Overworld water/lava springs and underground lava lakes use the same
height-placement adapter. Native rarity/count modifiers, environmental scans,
surface-distance checks and feature geometry remain intact. Surface-heightmap
vegetation is not moved through the ore frame. Cave vegetation, dripstone, sculk,
underground monster rooms, glow lichen and underwater magma use their native
height providers through the same adapter. Environmental checks remain native.

Material plans map their deepslate test through the same frame. Terrain fill and
base-column queries use the same adapter, retaining the native transition noise.
The preset's deepslate transition is expressed at reference Y=0. Frame lookup uses
cached terrain descriptors and bounded cell searches; it never loads chunks.

## Carving

Vanilla cave origins sample the reference envelope and map through the geological
frame at their source chunk's center. Canyon origins instead preserve their native
10 to 67 height spread relative to the actual local surface at reference Y=64. That fixed origin
frame is shared by every target chunk carved by the same tunnel. Tunnel lengths,
branching and shapes remain native. A void origin consumes the native random
sample and subsequent tunnel decisions below the playable envelope; it cannot
manufacture a new geological frame at an island edge.

The lava-floor anchor resolves through the frame at each carved block. The real
carving context retains the full dimension bounds; canyon width arrays and native
clipping therefore still use valid world coordinates. No neighboring chunks are
loaded for these lookups. Other generators retain their original provider calls.
This restores classic carvers within island rock; it does not introduce vanilla's
noise-cave or aquifer terrain-filling systems.

There is a narrower unresolved fluid distinction: delegated `applyCarvers` creates
a native `NoiseChunk`, and `WorldCarver.getCarveState` consults its aquifer above
the mapped lava floor. Those noise/sea-level inputs still use world coordinates;
only the explicit lava anchor is mapped. This does not create a full aquifer during
island filling, but it prevents claiming that all carved fluids are island-relative.
A future fluid-policy change needs matched low/high island samples, containment and
exposed-side checks. It must not introduce global water to repair an origin height.

## Cave biomes

During native chunk biome filling, lush and dripstone cave footprints are sampled
at island-relative reference depth. Projection requires an existing solid column
and leaves its upper 16 blocks to the original surface biome. It shares the anchor
geological frame with ore placement; thin companions do not gain an independent
complete cave-biome column.

The adapter extends Sky Archipelago's existing projection scope and recursion
protection. Its Deep Dark projection retains priority and upstream behavior.
Lush/dripstone projection does not extend to direct structure eligibility queries;
adding structures that require those biomes needs a separate placement review.
Vanilla cave decoration retains its own ground, ceiling, water and space checks.

## Feature coverage and limits

Height-range tags adapt origins, not every algorithm inside a feature. The resolved
registry and typed selector traversal are checked with the native coverage probe.
Trees, flowers, crop patches and surface lakes primarily use actual heightmaps,
soil/fluid predicates and local offsets. Root systems, vegetation patches and
selectors retain their nested placements. Cave decoration searches real floors,
ceilings and available space; moving its origin does not guarantee a suitable cave.
Geodes, springs and underground lakes retain native shape and enclosure rejection.
Monster rooms retain floor/wall/opening checks. Fossils fit below the local heightmap
and reject excessive empty corners. No scan range or feature geometry is stretched
with the geological frame.

Create's `LayeredOreFeature` checks the actual ocean-floor heightmap, replaceability
and exposure; it has no extra fixed-Y cutoff. Almost Unified rewrites
`OreConfiguration` targets, not the generic layered feature. Its removal of an
empty unified feature would need rechecking after mod/config updates. Propulsion's
three bottom-relative platinum distributions partly sample below reference −64;
those attempts remain rejected, rather than being clamped into extra ore.

Oceanless generation excludes ocean hosts and reports sea level at the build floor.
Icebergs reset their origin to sea level; blue ice also needs water and packed ice.
Kelp, seagrass, coral and sea pickles need suitable water/substrate. Wandering traders
provide kelp, sea pickles, coral blocks, packed/blue ice and nautilus shells, but that
is not a claim of natural habitat or a complete substitute for ocean progression.
Monuments, guardian farms and sponge access need an explicit pack acquisition policy
if required. Adding an ocean or replacement recipes remains a separate design choice.

Sky Archipelago's registered `IceSpikeFeatureMixin` replaces vanilla spike placement
with `SkyIslandIceSpikeFeature.placeWithoutVanillaRoot`. It removes the long root
loop, so vanilla's fixed Y=50 root cutoff is not an active defect in this stack.
This also illustrates why a resolved feature class alone does not establish its
transformed behavior. Other native predicates and future foreign feature classes
need their own review; tagged origins are not universal height remapping.

## Structures

Sky Archipelago owns structure search, native layout generation, reservations, canonical commits, references and postprocessing. Strongholds, trial chambers and mineshafts retain their own native fit policies. Compat replaces only the registered Ancient City's surface route in the marked Skyweave preset.

`AncientCityRoutingMixin` admits that city to island-first planning before native generation at its vanilla height can reject the biome. `AncientCityPlacementMixin` scopes site selection, claims, biome preflight and fit evaluation through the existing native service. `AncientCityHeightMixin` gives the native jigsaw generator the selected interior height. Biome validation uses the real proposed position, with a final check after fitting; there is no lower-quarter query substitution. Other registered structures and unmarked generators retain native behavior.

Normal/NOISE sites begin near reference Y=-27 in the anchor's geological frame. Fitting tries a bounded sequence of nearby elevations and returns the first acceptable result, rather than always maximizing burial. HOLLOW sites begin at an actual cavity floor with a retained ceiling; unsuitable hosts are rejected without a surface fallback.

`StructureBurial` defines the burial metric: 4096 deterministic samples allocated by piece-envelope volume, with inverse overlap multiplicity to estimate the union. Identical envelopes are deduplicated. Small pieces do not receive equal votes with large rooms. The geometry-only implementation is checked against overlapping-volume controls and a denser reference. This remains a sampled acceptance metric, not an exact survey of every voxel. Rooms, caves and structure air count as buried when they remain within the host's native exterior.

`CityFit` checks the first native piece's core separately at nine XZ positions, requiring body cover above and below and nearby floor support. Non-HOLLOW layouts use the configurable buried fraction (default 0.90), at least 80% weighted floor support, and peripheral unsupported samples within 16 blocks of support at similar height. This allows limited exposed edges without permitting a suspended center. HOLLOW layouts require full sampled exterior containment, at least 90% weighted floor/roof support, a retained core roof, and predominantly existing cavity space. These are fit checks against generated terrain predictions; final blocks and later generation stages require native verification.

Vanilla Ancient Cities request `beard_box` rather than testing for an already-empty cavern. `AncientCityTerrainMixin` runs after Sky Archipelago's surface adaptation and before native templates are placed. `CityCavern` uses the canonical rigid pieces, ground-level deltas, junctions and vanilla `Beardifier`; the maximum squeezed vanilla density supplies a conservative clearance threshold for the custom solid-body generator. It removes only replaceable dry terrain, never adds an artificial foundation, and keeps the piece ground planes and four blocks beneath them. Natural air stays air, including HOLLOW room; rock between separate piece influences remains.

The pass intersects every column in a complete block-radius neighborhood to retain the configured interior shell, excludes foreign dominant hosts, and protects native river beds, wet seals, fluids and block entities. It mutates only the current generation chunk and refreshes its heightmaps; no neighboring chunks are loaded. Kernel/column caches live only for that chunk. The existing Ancient City switch owns routing, fitting and clearance together. Ordinary generators retain their own terrain adaptation. Native DH preview exclusions remain upstream-owned rather than activating structure generation for a preview.

Burial still measures the host's exterior before structure clearance. The saved identity records `ancientCityClearance: bounded_beard_box_v1`. Updates must recheck native pre-template timing, canonical references/revocations, piece ground levels, the Beardifier kernel, replaceable tags, fluid provenance, shell/foundation bounds and realized room after saving/reopening.

Accepted layouts continue through the native underground committer and three-dimensional overlap policy. Nearby ground and Sky Villages are not made island-exclusive. Optional native benchmark counters distinguish scope admission, city fit rejection and commit rejection without retaining world references in Compat. Update checks must cover the legacy-core pre-native routing boundary, jigsaw height sampling, native service signatures, envelope sampling, support budgets, biome scopes, canonical delivery, final blocks, locate and reopening.

`StructureLocateCheckMixin` corrects the native `StructureCheck` fallback preview.
That preview tests a layout at its original source chunk, while the upstream
planner can relocate it to a valid island. A failed preview could therefore hide
an existing valid start. For this preset, unknown permitted candidates request
`STRUCTURE_STARTS` resolution instead. Loaded/saved results and placement
restrictions stay authoritative; denylisted candidates remain excluded. Floating native water hosts may resolve WATER structures even when the global ocean layer is disabled. This is not unconditional acceptance or a FULL-chunk request.

This increases work for some unsuccessful lookups. Search radius and candidate
selection stay native; broad searches in sparse worlds can still be expensive.
Locate coordinates remain upstream's X/Z target and may retain Y=0. They are not
validated landings. Review `StructureCheck.checkStart/canCreateStructure`, upstream
category policy and reference registration together when updating dependencies.

Upstream routes vanilla villages through its ground-village planner; outposts and
portals use surface/small-island routes, while other surface structures can use
DEFAULT anchoring and specialized adapters. Shipwreck pieces explicitly suppress
late height re-anchoring after island placement, explaining valid land wrecks
without a new airship generator. Monuments and ocean ruins use native WATER placement and require a suitable actual water host; disabling the global ocean layer alone is not a rejection rule. A missing start in a bounded search is not proof
of a missing implementation. Lootr conversion occurs after chunk loading and server
ticks, not synchronously when a structure's chest block is first placed.

### Cluster structure policy

`terrain/structure/` owns qualified family admission in the marked preset. Compat does not rewrite registered
structure sets; `StructurePolicyStateMixin` supplies runtime
random-spread views. The shared `managedFamily` decision also owns attempt scoping,
so unwrapped rings, mixed sets and disabled city adaptations are not accidentally
filtered. Native custom-rule opt-outs, explicit whitelists and unsupported category
changes also keep the whole registered set native. Do not wrap its placement while
leaving its attempt unowned (or vice versa). Foreign generators retain native placements. [Configuration](../configuration.md#structure-distribution)
owns rates, caps, units and exceptions.

Explicit `skyweave_compat:managed/<family>` structure tags can admit qualified modded profiles to the same allowance. Native IDs retain their family; conflicting tags fail rather than silently reclassifying a structure. Unknown tagged types remain native unless they use vanilla jigsaw or a supported metadata bridge. Family and route maps belong to the generator's detached registry snapshot, not global mutable state. Pack-authored schedule and pool changes are separate resources and may deliberately apply to ordinary worlds as well.

The optional `lithostitched.island_structures` adaptation supports Lithostitched 1.8.1 with Sky Archipelago 1.4 and structure distribution enabled. `AlternateJigsawStructure` exposes its assembly-depth and horizontal-reach metadata through `IslandJigsaw`; its native generator still owns aliases, connectors, limited/delegating elements, processors and templates. Scoped routing preserves the village pre-anchor category at both native type checks. Outside the owned attempt, ordinary-world and unsupported-type routing remain upstream-owned. No foreign class is resolved when the bridge is absent or disabled.

Dependency updates must recheck the custom config accessors, maximum reach/depth semantics, both `StructureRoutingPolicy` descriptors, category demotion, native size metadata and immutable registry membership. Qualify the resolved modifiers and complete layouts, not just raw files. Vanilla jigsaw pieces discard temporary expansion padding when reloaded and reconstruct their bounds from templates; persistence checks compare native round-tripped piece records, including placement, rotation, processors and junctions, while fresh fitting still checks the assembly bounds.

`yungsapi.island_structures` exposes YUNG's native jigsaw reach and depth through the same metadata interface. The supported API is `1.21.1-NeoForge-5.1.9`; the adaptation requires structure distribution. For owned desert/jungle attempts without native terrain adaptation, a buried floor is supported only when the same accepted descriptor contains one solid span covering the floor and four blocks beneath it. Missing terrain, foreign hosts and cave gaps remain rejected; no foundation is fabricated. Native fixed-biome fixtures verify two desert starts and five jungle starts, complete realization of one layout in each, and save/reload. Puzzle gameplay and survival balance require separate review.

Sky Archipelago's generation service bypasses YUNG's `ChunkGenerator.tryGenerateStructure` suppression hooks. The adapter reads the five supported mods' registered COMMON Boolean bindings through NeoForge, without resolving their classes or parsing their files. `YungReplacementBindings` owns their exact versions and schema paths. A generator-owned immutable snapshot feeds the generation marker, weighted planning, native attempt filtering and prospective structure lookup. Enabled upstream replacement flags suppress the corresponding vanilla types, including both mineshaft variants; false flags retain eligibility. Saved structure starts remain discoverable, replacement structures remain eligible, and foreign generators retain their native path. Prospective checks do not load chunks.

No config or resource is rewritten. Restart/recreate the world deliberately when changing a recorded replacement policy; live edits cannot mutate cached plans. Native controls cover all five replacements enabled, all disabled, API-only loading and independent generator snapshots. Updates must requalify the native binding paths/restart semantics, structure type IDs, generation-service entry point and `StructureCheck` prospective boundary. Lookup hook selection is shared with the existing location adaptation; either feature can select it once without enabling the other's policy.

The shared lattice also replaces YUNG's custom spread-placement entry points. `YungTempleRules` reads the qualified original placement codecs once when binding a generator, resolving ordinary and enhanced exclusion sets/tags to managed families. It preserves their chunk-distance budgets around the translated first-piece anchors and checks the native 48-block/two-quart water buffer at actual island surfaces, including accepted river plans painted later in chunk generation. It does not query the old vanilla opportunity grid or recursively request other structure plans. Unknown exclusion targets fail for review instead of silently losing a rule. Foreign worlds retain upstream placements.

Native controls decode the registered codecs, accept a dry island host, reject a village at the desert temple's six-chunk boundary and reject both temples on a wet Lagoon host without loading chunks. These synthetic root bounds qualify the restriction boundary separately from full template realization. Dependency updates must recheck the custom placement IDs, codec fields/holder tags, horizontal search scale, final anchor convention and source exclusions. The existing YUNG adaptation switch owns these rules together with fitting and vanilla replacement policy.

The optional `bettermineshafts.island_placement` bridge requires the supported Better Mineshafts artifact and the general YUNG policy. Explicit family tags admit its thirteen native variants to the existing mineshaft allowance. Native tunnel assembly, ore deposits, loot and local connector coordinates remain upstream-owned. The entrance's absolute saved center follows owned placement translations; its vertical exit-search reservation retains the fixed dimension ceiling. Other presets retain native movement. The remaining cached Big Tunnel entrance coordinates are local and must not receive the same translation.

The native underground solver normally fits mineshafts by their anchor alone. For the qualified custom type, it instead applies its existing 90% sampled piece-coverage solver to actual rooms, excluding the entrance's dimension-height search reservation from that calculation. No piece is removed from the committed structure. Placement remains bounded by the native work budgets and the shared family policy. During postprocessing, the physical marked level supplies a per-call lower support bound: native rock still stops legs, but neither leg placement nor its downward lava scan continues below island terrain or through a column with no island. Native 20-block pillar and 50-block chain routines retain their separate limits.

`betterwitchhuts.island_support` bounds YUNG's log/fence marker processors through the same `IslandSupportBounds` helper. Native control columns reproduced 827 supports descending through void in each processor. The enabled hook produces none there and preserves seven supports above real ground; marker conversion, solid/fluid termination and ordinary-world behavior remain native. The bound is computed once per marked support, outside the loop. Native full hut generation and reopening pass. Recheck both processor signatures, marker types, physical WorldGenLevel ownership and native loop comparisons when updating Better Witch Huts.

Updates must recheck all twelve native piece classes for absolute versus local state, entrance serialization and movement, the native coverage call sites/budgets and the two unbounded leg methods. `tools/probe.py --yung policy`, `--yung frames` and `--yung supports` exercise registered replacement flags, owned/foreign movement, serialization and both native leg loops with void/solid controls. These are isolated contract fixtures; complete natural layout, persisted-world and survival checks remain separate evidence.

`StructureDistribution` samples accepted cluster members, estimates eligible area,
then ranks lattice destinations independently of visitation. It uses per-generator,
bounded caches as memoization, not commitments. Fitted choices retain the weighted
native variant, immutable piece geometry, adjusted native bounds, portal impact
bounds and category padding. Ground, village, igloo, water and underground helpers
reuse their native routing and fit stages. Planning commits no starts, references,
terrain reservations or blocks; native postprocessing remains part of actual
materialization.

The distribution binds detached registries, templates and height metadata on level
load, including saved levels with no new chunks. The structure-pass hook remains a
fallback. No live chunk, structure manager or level is retained. Queries never
load neighboring chunks. Fractional admission, bounded alternatives and fitted
piece conflicts are deterministic. The village committer supplies raw grounded
unions, while other routes can supply terrain-adjusted or portal-impact bounds;
matching recognizes all three representations of a fitted layout. Native final delivery and unknown-geometry
checks remain active. Early two-dimensional footprints do not overrule a fitted
three-dimensional plan. Native adjusted/reservation bounds remain the collision
volume except between two ordinary villages, where separate padded buildings
allow neighboring settlements without treating the empty space between them as
solid. Portal spread/drip influence remains part of its occupied volume.

Scoped exact-ID diversity, regional no-host misses, adaptive DEFAULT cost throttles
and shared anchor claims cannot own managed eligibility. Native anchor cache keys
do not describe the selected-host scope, so scoped results are recomputed rather
than reused from unscoped searches. The native physical duplicate-key set and reservation arbiter are
owned by each generator instead of the process. Native padding, duplicate checks,
bounded reservation storage and pending takeover rules remain active. Deferred
revocation callbacks retain a weak owner, including an explicit unowned context.
Postprocess descriptor/delivery claims reset at server start and after full stop;
this clears only ephemeral memory, never saved starts, blocks or player data.
Native reference delivery already follows registry-session identity. Startup also
initializes vanilla's small OctahedralGroup direction tables before parallel fits:
its lazy map publication can otherwise expose missing directions to concurrent
jigsaw rotation. No generation lock or new rotation implementation is added. Sky Villages retains
its independent placement owner and allowance.

Owned portal searches stop at the island body rather than the dimension floor;
the mountain datum follows island geology. Owned igloos must be drafted at their
surface anchor before native grounding checks. Their top, rather than the buried
laboratory, supplies the contact plane. `SkyweaveIslandAnchor` persists on pieces
and prevents a second vanilla Y=90 correction during placement. Unmarked pieces
retain vanilla behavior. Owned desert pyramids also persist their fitted anchor:
vanilla's later minimum-height scan can encounter a void column at an otherwise
supported footprint and drop the entire temple below the island. The accepted
native support/fit remains authoritative; the marker suppresses only that second
height adjustment, retaining native traps, archaeology and loot. Pyramid admission
also checks the complete footprint and trap depth against the island body, so an
unsupported edge cannot become a native foundation pillar extending to the void floor.

For Sky Archipelago updates, recheck category routing, weighted alternatives,
catalog footprint requirements, all native pre-anchor/grounding/trimming/fit APIs,
claims and cache keys, vanilla rotation-cache initialization, budget units, pending delivery/revocation and canonical
references. For Minecraft updates, also check placement codecs, locate offsets,
terrain-adjusted bounds, portal impact/height behavior and pyramid/igloo template/NBT and
deferred-placement contracts. Compare final starts and materialized content across
query order and reopening; a layout-only positive does not prove loot or placement.

## Climate and villages

Temperature comparisons use reference Y=64 throughout this preset. Climate follows
biome rather than absolute altitude: a high temperate island does not become snowy
solely because it is high. Client weather and server freezing share this altitude reference without reconstructing server terrain or exposing its seed.
Altitude cooling above a mountain is deliberately absent in this preset. The
adapter remaps temperature-query positions; freezing also selects the actual water-cell biome. Light, fluid, terrain and block coordinates retain their real altitude. It covers Sky Archipelago surface
selection, ruined-portal cold classification, vanilla snow/freezing predicates,
precipitation ticks, rain exposure
and the client's weather rendering/sounds. Native snow rendering was checked
with shaders enabled and disabled. Surface selectors receive a bounded thread-local context from
the owning surface pass because their API has no world argument; `finally`
restores it across nested calls and failures. No biome temperature is mutated.

Surface generation and player-facing biome queries do not use identical sampling.
Sky Archipelago chooses materials from stored quart biomes at the surface;
Minecraft's smoothed biome lookup can select a neighboring cell. Upstream explicit
snow tags and cold-biome rules can also choose snowy ground independently of the
temperature adaptation. Its river/beach terrain-profile blending does not smooth
general biome boundaries.

Native attribution distinguishes genuine snowy surfaces, their material layers extending into neighboring biome cells, and warm water frozen by a cold air-biome receiver. Initial `SnowAndFreezeFeature` placement and recurring `ServerLevel.tickPrecipitation` now use the water cell's biome for freezing. Snow still follows its own surface/air eligibility; the patch neither warms cold habitats nor removes existing snow. [Verification](verification.md#terrain-repair-and-structure-discovery-0280) records the marked controls and remaining coverage limits.

Sky Villages keep their authored start-height spread, shifted from the old
Y=192 datum to the new anchor surface datum. Only the named structure and the
opted-in generator receive this adjustment. Horizontal clearance still uses the
complete generated footprint.

Each archipelago has an allowance of zero to three villages, with zero common
and three rare. The logarithmic-series weights are
`96:24:8:3`, or approximately 73.3%, 18.3%, 6.1% and 2.3%. `VillageDistribution`
implements the deterministic count draw. `ArchipelagoVillagePlacement` replaces
only the named village's runtime placement inside the opted-in generator. It
selects stable random-spread candidates per anchor; it never uses mutable accepted
counters or chunk visitation order. Companion islands do not receive separate
quotas. Layout, biome and clearance rejection can reduce the realized count.

The marked preset uses four-chunk spacing and two-chunk separation for candidate coverage near shaped shores. A provisional 144-block footprint receives coarse exterior/host checks before seeded selection; the native complete layout still passes exact clearance. The host must be a substantial body in the assigned cluster, not a detached fringe. Disabling clearance also disables that provisional screen rather than eliminating every site. No fine-grid change is applied to ordinary upstream presets.

Candidates belong to their nearest anchor, with coordinate tie-breaking. Center-biome screening, seeded ranking and a minimum 384-block separation retain the per-anchor allowance. Native Jigsaw checks its actual start-piece biome and may reject or move a layout through bounded retries. A circular inner annulus cannot represent crescent/elliptical shores and is not used as proof of proximity. Original salt, spread type, frequency, exclusion-zone checks and locate behavior retain their owners.

Candidate admission also reserves a 192-block square inside the anchor's nearest-
center region: 32 blocks for upstream SKY biome retries and 160 for layout extent.
The final layout must retain the same nearest anchor as its native start. This
prevents a village assigned to one cluster from consuming space beside another
cluster with a different allowance. Review these bounds when changing Sky Villages'
start pool or Jigsaw radius, or Sky Archipelago's source-retry search.

## Delivery endpoints

When Aeronautics Delivery Quests 1.1.1 is installed, the adapter changes its
structure-route loading, distance checks and surface finalization only in the
opted-in preset. RANDOM routing and foreign generators retain native behavior.

The background FULL chunk requests need retained tickets in the installed stack.
Without them, native futures can complete with an `Unloaded chunk` result that
ADQ ignores. `DeliveryChunks` holds a 3×3 chunk neighborhood for each endpoint,
at most 18 unique chunks per generation job, and waits for successful FULL results.
Loading remains asynchronous, with a 60-second timeout. Tickets are released on
registration, rejection, load failure or server shutdown. No chunk ticket is
stored in the world or retained for the accepted quest's lifetime. ADQ's single
active generation job is part of this version-coupled contract.
Native generation may request additional neighboring chunks at intermediate
statuses; 18 is the owned ticket bound, not a total generated-chunk budget.

The origin and route minimum-distance checks use horizontal distance. Native
locators can report Y=0 before realization and the actual surface Y afterward;
counting that difference admitted a one-block route as more than 500 blocks long.
The configured maximum still bounds the sampled search offset, not the final
located route. Surface retries can move the selected positions by a few blocks.

Native valid surface endpoints are returned unchanged. If an endpoint is rejected in the opted-in
preset and its loaded chunk contains a Sky Village start, the adapter searches
at most eight blocks around it, in deterministic rings. Every retry uses ADQ's
original dry-surface test and must remain inside the village's bounding box.
Void, fluids, leaves and uneven 3×3 surfaces remain rejected by that native test.

The existing locator can return a start chunk corner beside void even when the
village has valid nearby ground. This adapter changes endpoint selection, not
structure placement, cargo, rewards or RANDOM-mode routing. Search is bounded
and occurs during quest finalization, not terrain generation. Native surface
queries stay within the retained endpoint neighborhoods; this is not a landing planner.
If no valid nearby surface exists, ADQ retains its failure behavior.

The dependency is optional and version-guarded. Review the private methods before
changing ADQ: each of the surface, chunk-request and distance wrappers requires
exactly two call sites. Registration and failure entry points own lease cleanup.
Do not remove surface checks or expand the search into unrelated terrain to make
a route succeed. A generated vanilla-village offer was accepted and its cargo spawned natively.
Sky Village cargo placement and delivery completion remain separate play checks.

## Atmosphere

The preset uses a Sable dimension-physics data record under
`data/skyweave_compat/dimension_physics/islands_v1.json`. A narrow lookup adapter
selects that profile only for this dimension type. Sable's native loader and
network packet carry the record; no custom packet or global Overworld override is
needed. A higher-priority explicit dimension record remains authoritative.

The pressure curve starts at 1.5 below the islands, reaches 1.0 at the Y=508 pressure
reference, 0.8 at Y=600, 0.5 at Y=680 and zero at the exclusive ceiling of Y=768.
Its cubic interpolation is checked across every integer altitude for bounded,
monotonic pressure. Gravity, drag and magnetic north retain Sable's defaults.
Native aircraft behavior still requires separate flight checks; successful data
loading or interpolation alone cannot validate a ship design.

## Saved worlds

Before levels load, `GenerationGuard` records the seed, algorithm revision,
geometry parameters, resolved embedded settings, village policy and structure set,
biome source, noise settings, dimension and the ancient-city eligibility policy
in `skyweave-generation.json` within a new world's directory. Object keys are
canonicalized so map iteration cannot change the identity. Reopening with a
different identity fails before generation. Existing region files without an
identity are not adopted. Replacing the generator in a marked world is rejected.

The identity records `islands_v1-skyarch-1.4-5` and the `island_watersheds_v4` water bridge when active, resolved river settings and `ancientCityPlacement: interior_union_v1`, including the city policy. Existing worlds are not silently upgraded to different exterior, hydrology or structure rules. Keep their original artifacts and settings or generate a separate world.

There is no conversion or automatic marker rewrite. Restore a world's original
settings/mod version or create a separate world. Deleting the marker is not a
migration procedure. Changes to the generation algorithm require reviewing its
revision and compatibility contract before release.

## Native checks

`tools/probe.py --terrain --samples 4` compares actual registered-preset density
samples over four layout seeds. These are predictions before carving/features, with sampled
volume estimates rather than full island block counts.

`--generation current|adapted|vanilla --seed N` generates two bounded interior
samples of nine chunks each using the installed server's mod selection. It records
actual blocks and ore counts by reference depth. For Sky Archipelago generators,
it also fills standalone native ProtoChunks and compares every block with base-
column queries. Those isolated chunks cannot race C2ME surface/feature workers.
Full generated chunks are inspected separately because surface and ore features
legitimately replace predicted stone and deepslate.

`--islands --seed N` samples one deterministic random chunk from each cell of a
7×7 grid inside twice each of three nearby anchor radii. Counts include only
columns dominated by that anchor. Weighting each chunk by its stratum's chunk
count yields a bounded-area estimate, not an exact island inventory. Reports keep
observed counts and estimates separate, plus reference-depth ore bins, surface
biomes, headroom, spawn support/clearance, descriptors, timing and end-of-run heap.
Native cell membership also exports exact companion groups and geological frames.
An eight-block-or-finer center-line density sample records its longest void gap.
Descriptor edge gaps and sampled straight routes are approximations, not navigated
or altitude-safe crossings.

`--biomes --seed N` locates native cave-biome footprints and checks stored chunk
biomes, surface preservation and actual cave decoration in generated chunks.

`--water --seed N --fluid-ticks 600` selects bounded native water witnesses and compares initial native query material with loaded fluid sources and observed edge flows. `--river-config` supplies an isolated native river/lake configuration; `--ocean-config` supplies the optional coastal/perimeter settings. A missing eligible fixture is a reported failure, not a pass. Zero ticks checks initial material only. These checks do not certify complete rim containment, all heightmap predicates, world-wide frequency or aquatic spawn populations.

`--interiors` checks raw subtraction against its exterior, forbids later native stages from refilling authored cavities, and records completed native additions/removals separately. Independent fields compare snapshot/direct request order. Section records distinguish raw exterior, interior, completed solid and native fluid intervals.

`--reopen` repeats the generated sample after saving and restarting the same
throwaway world, requiring unchanged ore counts or natural village observations.
The village fixture checks all selected sites in a 12,000-block descriptor survey,
native locate, ownership, complete footprints, fresh reverse-order planning and two
fully generated villages. Successful reports identify the
exact mod/probe JAR hashes. Reports and logs live under ignored `build/verification`.
The runner removes its temporary world. Never pass a live save as a test target.

Before changing upstream versions, inspect generator caching, descriptor records,
column/material planning and placement modifier call paths. Rerun native Mixin,
foreign-preset, bounds, query agreement and reopening checks, then the full resource
and gameplay surveys. Build success alone does not validate an injection target.

API references: [NeoForge 1.21.1 biome modifiers](https://docs.neoforged.net/docs/1.21.1/worldgen/biomemodifier/)
and [MixinExtras WrapOperation](https://github.com/LlamaLad7/MixinExtras/wiki/WrapOperation).
Biome modifiers alone cannot provide the required per-generator placement scope.

## Surveyor structure records

The optional `surveyor.structure_starts` adaptation skips invalid starts at Surveyor's `WorldStructures.put(ServerLevel, StructureStart)` boundary. Surveyor 1.2.4 looks up the start's registered structure before checking validity. Sky Archipelago retains ordinary `StructureStart.INVALID_START` entries for rejected candidates; those entries have no registered structure. Passing them to the recorder throws and interrupts its chunk-load listener.

The guard changes only the consumer. Native starts, rejected-candidate caches, references and saved chunks remain unchanged; valid starts continue through Surveyor's own recorder. The dependency pair is checked before selecting the string-targeted mixin on either physical side. The Fabric artifact is pinned and hash-checked; the supported NeoForge version string reflects Connector's mapped metadata.

On updates, recheck the mapped `put` descriptor, validity/registry ordering, chunk-load registration and valid-record persistence. Remove this hook when upstream rejects invalid starts before registry lookup. Do not drop native rejected starts to accommodate a map consumer. Native client probe `structure_records` exercises the installed recorder with one rejected and one known valid start; generated observations remain private.
