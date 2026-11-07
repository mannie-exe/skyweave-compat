# Configuration

## Release identity and existing saves

Skyweave: Compatibility Patches uses `skyweave_compat` resources and configuration filenames. Saved registry references must match the installed resources. Existing saves are not automatically converted, and old names are not aliases. A deliberate namespace migration must update saved references as well as the generation marker filename; changing a marker alone does not migrate a save. The generation guard verifies the recorded settings and rejects incompatible terrain configuration.

The interior noise salt `create_sky_compat:interiors` remains an opaque deterministic input so the same seed retains its terrain algorithm. DH's `create-sky-lod-world-id` marker and `csky_` cache key format remain persistence contracts, preserving existing cache discovery. They are not public resource aliases. Other serialized owned IDs, including grave recovery metadata, use the new namespace only; this release does not convert existing saved data.

## Adaptation switches

`config/skyweave_compat-features.properties` is the authoritative startup feature
file. It is created when absent; supplied values are preserved. Use literal `true`
or `false`. Unknown keys, duplicates, and invalid values fail with an explanation.
Missing keys use their documented defaults. Restart the entire game/server after
changing switches; applied mixins cannot be unloaded during play.

All switches default to `true`; explicit `false` selections remain authoritative. A true setting is eligibility, not proof of activation.
Missing targets,
physical-side restrictions, or disabled prerequisites leave an adapter inactive.
An active combination validates the versions below. Disabling its switch avoids
that validation, its mixins, callbacks, and integration data. No switch enables a
disabled prerequisite. Startup logs list active adaptations; debug logs explain
inactive selections.

| Switch | Adaptation | Prerequisites |
| --- | --- | --- |
| `terrain.preset` | Expanded preset, geometry, interiors, geology, and local water | Sky Archipelago 1.4 |
| `terrain.ore_placement` | Relative ore and surface-feature placements | Active preset |
| `terrain.relative_carvers` | Relative classic carvers and `nativeCarvers` policy | Active preset |
| `terrain.cave_biomes` | Island-relative cave biome selection | Active preset |
| `terrain.ancient_cities` | Interior city placement, burial and support | Active preset |
| `terrain.spawn_landing` | Generated-surface spawn landing | Active preset |
| `terrain.structure_location` | Relocated structure lookup | Active preset |
| `terrain.structure_distribution` | Cluster structure allowances and fitted destinations | Active preset; city allowances additionally need `terrain.ancient_cities` |
| `terrain.water_animal_spawns` | Local-water spawn height | Active preset |
| `terrain.climate` | Surface temperature, snow, weather, and portal height | Active preset |
| `villages.clearance` | Village footprint clearance/proximity | Sky Archipelago 1.4, Sky Villages 1.0.6, Architectury 13.x |
| `villages.placement` | Village altitude and cluster allowance | Active preset, Sky Villages 1.0.6, Architectury 13.x |
| `sable.island_pressure` | Island-altitude pressure | Active preset, Sable 2.0.6 |
| `delivery.horizontal_distance` | Horizontal route distance | Active preset, ADQ 1.1.1 |
| `delivery.chunk_loading` | Retained chunk requests and cleanup | Active preset, ADQ 1.1.1 |
| `delivery.village_landing` | Bounded village surface retry | Active preset, ADQ 1.1.1, Sky Villages 1.0.6, Architectury 13.x |
| `analog_audio.movement_registration` | Radio handler registration | Analog Audio 0.1.5-hotfix.1, Create 6.0.10 |
| `analog_audio.playback_timeline` | Current media position after loading and standby | Client, Analog Audio 0.1.5-hotfix.1, AnalogLib 0.1.0 |
| `yigd.construct_recovery` | Construct graves, bounded fallback and world-space tracking | Sable 2.0.6, YiGD 2.0.13; item and XP storage enabled |
| `transmission_linkage.interactions` | Authorized endpoint/settings changes and deliberate rod removal | Transmission & Linkage 0.2.9, Create 6.0.10, Sable 2.0.6, Simulated 1.3.2 |
| `transmission_linkage.jade` | Server-backed link/actuator inspection | Active interaction adapter, Jade 15.10.6+neoforge |
| `redstone_link_gui.interactions` | Authorize packets and native menu actions | Link GUI 1.21.1-1.9.2, Create 6.0.10 |
| `redstone_link_gui.relocation` | Restrict moves to ordinary links in one coordinate frame | Link GUI 1.21.1-1.9.2, Create 6.0.10 |
| `redstone_link_gui.ship_access` | Project menu and relocation distances | Active interaction adapter, Link GUI 1.21.1-1.9.2, Create 6.0.10, Sable 2.0.6 |
| `redstone_additions.logic` | Pure placement, coherent signals and support loss | Additions 1.0.5, Create 6.0.10 |
| `redstone_additions.scraping` | Award verdigris after successful axe scraping | Additions 1.0.5, Create 6.0.10 |
| `redstone_additions.wrench` | Native rotation and recovery | Additions 1.0.5, Create 6.0.10 |
| `redstone_additions.transparency` | Lit inverter cutout layer | Client, Additions 1.0.5, Create 6.0.10 |
| `linear_bearing.bootstrap` | Dedicated startup and missing bubble-gum drop; defers to Linear Bearings Fix 1.0.3 when present | Linear Bearing 1.3.5; terrain is not required |
| `linear_bearing.carriage` | Checked assembly, relative carriage brake and guarded recovery | Linear Bearing 1.3.5, Create 6.0.10, Sable 2.0.6, Simulated 1.3.2 |
| `linear_bearing.transmission` | Native kinetic edges for paired springs and aligned magnetic ports | Linear Bearing 1.3.5, Create 6.0.10, Sable 2.0.6, Simulated 1.3.2 |
| `linear_bearing.gyro_sensor` | Angular-rate output with zero stress capacity | Linear Bearing 1.3.5, Create 6.0.10, Sable 2.0.6 |
| `linear_bearing.ponder` | Six machinery tutorials; transmission/gyro scenes follow their owning switches | Client, active carriage adapter, Create 6.0.10, Ponder 1.0.87+mc1.21.1 |
| `linear_bearing.jade` | Server-backed brake/link status and inspection hints | Active carriage adapter, Jade 15.10.6+neoforge |
| `distant_horizons.client_handoff` | Concurrent client wrapper ownership | Client, DH 3.3.3 |
| `distant_horizons.shutdown` | Bounded island unload passes | DH 3.3.3, Sky Archipelago 1.4 |
| `distant_horizons.world_cache` | Per-save key and guarded legacy import | DH 3.3.3, Sky Archipelago 1.4; default true |
| `lighting.lifecycle` | Keep moving lights client-local and remove old world owners | Client, Sable Dynamic Lights 2.0.1, LambDynamicLights 4.8.11+1.21.1, Create 6.0.10 |
| `lighting.sodium` | Position-aware moving-light lookup in Sodium | Client, Sable Dynamic Lights 2.0.1, LambDynamicLights 4.8.11+1.21.1, Create 6.0.10, Sodium 0.8.13+mc1.21.1; Sable and Iris are not required |
| `questlog.interface` | Wrapped conditions, reward preview and scrollbar/input adaptation | Client, Questlog 3.5.0 or 3.5.3 |
| `questlog.notifications` | First-progress and completion toasts | Client, Questlog 3.5.0 or 3.5.3 |
| `questlog.reward_validation` | Validate eligibility and choices before grants | Logical server, Questlog 3.5.0 or 3.5.3 |
| `bluemap.viewer` | Viewer adapter registration | Dedicated server, BlueMap 5.7, Sky Archipelago 1.4; map opt-in below |
| `surveyor.structure_starts` | Ignore rejected starts when recording map structures; native negative-cache entries remain intact | Shared, Sky Archipelago 1.4 and Surveyor 1.2.4 (NeoForge metadata `1.2.4_1.21`); default on |
| `rendering.shader_patches` | Assemble verified shader recipes before Iris opens them; default on | Client, Iris 1.8.14-beta.1+mc1.21.1; recipe-specific runtime requirements are separate |
| `rendering.veil_framebuffer` | Framebuffer preservation and active Iris target | Client, Veil 4.3.2, Iris 1.8.14-beta.1+mc1.21.1 |
| `rendering.balloon_shadow_pass` | Exclude screen-space balloon effect from shadow pass | Client, Aeronautics 1.3.2, Iris version above |
| `rendering.text_display_shadow_pass` | Exclude text displays and their backgrounds from shader shadows; default on | Client, Iris version above |
| `rendering.nametag_shadow_pass` | Exclude base-renderer entity names from shader shadows; default on | Client, Iris version above |
| `rendering.text_display_lighting` | Neutral text-display lighting; later shader color processing remains | Client, Iris version above; default on |
| `rendering.nametag_lighting` | Neutral base-renderer nametag lighting; later shader color processing remains | Client, Iris version above; default on |
| `rendering.construct_distance` | Use SSRD's effective range during construct shader draws; default on | Client, SSRD 1.8.8, Sable 2.0.6, Iris version above; DH not required |
| `rendering.construct_occlusion` | Hide construct fragments behind opaque DH terrain; default on | Client, dependencies above and DH 3.3.3; independent of the distance switch |
| `rendering.construct_material_ids` | Annotate construct shader materials | Client, Sable 2.0.6, Iris version above, Sodium 0.8.13+mc1.21.1 |
| `rendering.colorwheel_samplers` | Release unused OIT sampler slots when Colorwheel disables OIT | Client, Colorwheel 1.3.2+mc1.21.1, Iris version above, Flywheel 1.0.6; independent of Sable |
| `rendering.colorwheel_materials` | Colorwheel terrain/emission metadata on direct construct draws | Client, active `rendering.construct_material_ids`, Colorwheel 1.3.2+mc1.21.1 |
| `rendering.single_block_model_data` | Match model data used for layer selection and tessellation | Client, Sable 2.0.6 |
| `rendering.single_block_passes` | Draw single-block geometry in its current Iris terrain pass | Client, Sable 2.0.6, Iris 1.8.14-beta.1+mc1.21.1, Sodium 0.8.13+mc1.21.1 |
| `macaw.bridge_lantern_shape` | Flat/stair lantern outlines and selection; matching resource models required, default on | Shared, Macaw’s Bridges 3.1.2 |
| `create.glass_backfaces` | Show exposed Create glass from behind, retaining final connected textures | Client, Create 6.0.10; default on, independent of terrain and renderers |
| `macaw.glass_backfaces` | Two-sided mosaic blocks/panes, retaining native states and material boundaries | Client, Macaw’s Windows 2.4.2; default on, independent of Create/Fusion |
| `confectionery.block_backfaces` | Two-sided caramel bricks, slabs and stairs | Client, Create Confectionery 1.1.3b (metadata `1.1.3.`); default on |
| `linear_bearing.bubble_gum_backfaces` | Two-sided bubble-gum block; physical behavior unchanged | Client, Linear Bearing 1.3.5; default on |
| `dndecor.grate_backfaces` | Two-sided ornate grate, retaining processed connected textures | Client, Design n’ Decor 2.2b; default on |
| `bits_n_bobs.grating_backfaces` | Two-sided industrial grating, retaining processed connected textures | Client, Bits ’n’ Bobs 2.3.5; default on |
| `fusion.glass_backfaces` | Show exposed vanilla/Rechiseled glass from behind after Fusion processing | Client, Fusion 1.3.16; default on, no required Rechiseled installation |
| `create.window_culling` | Keep window faces visible across different window materials | Client, Create 6.0.10; independent of Fusion, Rechiseled and shaders |
| `rechiseled.glass_culling` | Keep differently oriented glass faces visible | Client, Rechiseled 1.2.6; independent of Create and Fusion APIs |

Both two-sided glass switches also separate touching rectangular surfaces during
mesh construction. No extra switch or Sodium/Iris dependency is needed. See
[glass backfaces](development/rendering.md#glass-backfaces) for the material policy,
coverage and unsupported geometry.

For rendering fixes alone, disable `terrain.preset`, `villages.clearance`,
`bluemap.viewer`, and unwanted independent adaptations. To make Skyweave: Compatibility Patches inert, set
every switch to false. An installed mod's own behavior and dependencies remain
its responsibility. Skyweave: Compatibility Patches still reads its own feature file and protects its own
saved generation identity; disabling terrain is not a way to convert a marked save.

The island preset is never chosen automatically. Generation-related switches are
part of saved generation identity; changed generation choices require a fresh
world. Ordinary worlds are not converted. Internal terrain helpers form one preset
feature; its geometry/interior/water settings tune that feature rather than
providing separate class-loading switches.

## Village tuning

`config/skyweave_compat-common.toml` is registered only when a village adaptation
is active. NeoForge's client config screen edits its distances. The startup feature
file owns activation, including `villages.clearance`; the tuning file does not
contain a second enable switch.

| Setting | Default | Meaning |
| --- | --- | --- |
| `minimumGap` | `64` | Minimum horizontal column distance to any island terrain |
| `maximumGap` | `512` | Maximum horizontal column distance to a sampled host island |
| `minimumIslandRadius` | `96` | Minimum native descriptor radius of the host island |

Distances accept integers from 0 to 2048. `maximumGap` must be at least
`minimumGap`; an inverted range fails startup. Even a zero minimum gap rejects
terrain inside the footprint. Radius is not habitable surface area. Settings are
captured at server startup; existing structures are not moved or removed.

## Island geometry

`config/skyweave_compat-generation.json` is created at startup and applies only
to **Skyweave Islands**. Edit it with the game/server stopped; restart the whole
process to reload it. It is separate from the NeoForge village settings screen.
Use matching files on client and server. Geometry changes require a fresh world;
the saved identity rejects changes to an existing world.

| Fields | Defaults | Purpose |
| --- | --- | --- |
| `spacing`, `jitter` | 2400, 750 | Cell spacing and bounded center displacement |
| `activation` | 0.85 | Native cluster activation before biome rejection |
| `centerMinY`, `centerMaxY` | 200, 490 | Requested anchor-center band |
| `anchorMinRadius`, `anchorMaxRadius` | 270, 370 | Anchor descriptor radii |
| `anchorPlateau` | 28 | Native upper-body shape |
| `anchorMinHeight`, `anchorMaxHeight` | 250, 400 | Uniform target total terrain height, before native relief and erosion |
| `companionMinRadius`, `companionMaxRadius` | 40, 110 | Companion radii; spires retain 12–28 |
| `companionMinOrbit`, `companionMaxOrbit` | 470, 680 | Horizontal orbit around host |
| `companionMinOffsetY`, `companionMaxOffsetY` | −60, 50 | Companion-center altitude relative to host |
| `minimumClusterGap` | 350 | Minimum separation between different clusters’ individual native plan support rectangles |
| `floorClearance`, `buildingHeadroom` | 32, 200 | Terrain clearance inside build bounds |

The loader rejects unknown/missing fields, nonintegral integer settings, impossible
vertical geometry, jitter reaching half the cell spacing, and profiles requiring
more than eight neighboring cells for conflict checks. It does not silently repair
input. Close candidate clusters are rejected by their complete island support
bounds; lowering spacing or increasing the gap can therefore reduce realized
density rather than making islands smaller. Changing the JSON does not migrate a save. The requested center band is intersected with safe shape placement:
each anchor derives its lower placement limit from its selected thickness. With
defaults, short anchors can start at Y=200; the tallest require at least Y=338.
The upper center limit remains Y=490, with independently fitted companions. Relief
scale must remain between zero and one for the verified shape envelope.

Geometry, activation, floor/headroom and gap controls have this single owner.
Upstream size presets, altitude bands, DYNAMIC/CONSISTENT spacing and common TOML
do not control this preset. Its embedded settings still own biome, archetype and
structure policy. The embedded spacing value is retained only because Sky
Archipelago's serialized type cannot represent the actual grid above 2000 blocks.
Ordinary Sky Archipelago worlds continue to use upstream controls.

## Island interiors

`config/skyweave_compat-interiors.json` controls interior selection in Skyweave
Islands. It is generated at startup, applies only to the opted-in preset, and is
part of saved generation identity. Restart with a fresh world after changing it.
All fields are required; invalid values and unknown fields fail explicitly.

| Fields | Defaults | Purpose |
| --- | --- | --- |
| `noiseChance`, `hollowChance` | 0.55, 0.18 | Descriptor selection probabilities; remaining islands keep baseline interiors |
| `shellThickness` | 12 | Vertical rock margin for ordinary cavities; selected openings bypass it |
| `noiseHorizontalScale`, `noiseVerticalScale` | 64, 32 | Passage/chamber noise scales in blocks |
| `passageWidth`, `chamberThreshold` | 0.075, 0.48 | Intersecting-band width and chamber-noise threshold |
| `hollowRadius`, `hollowHeight` | 0.58, 0.24 | Hollow radii relative to descriptor horizontal radius and cliff depth |
| `openingChance` | 0.35 | Selected interiors with an entrance and upper opening |
| `nativeCarvers` | true | Retain Minecraft's subsequent classic cave/canyon pass |

Small bodies without four shell-thicknesses of cliff depth keep baseline interiors.
Set both selection probabilities to zero for the uncaved comparison. This does not
remove upstream density cutouts or native classic carvers. Disable `nativeCarvers`
separately when comparing cavity stages. Interiors preserve exterior archetypes and
taper; they do not alter the anchor-relative ore datum. Large iron/copper veins use
the preset's native `ore_veins_enabled` setting independently of cavity selection.

## Ancient Cities

`config/skyweave_compat-ancient-cities.json` contains `minimumBuriedFraction`, default `0.90`. Values from 0 to 1 set the minimum sampled share of a normal/NOISE city inside its host's exterior; use `0.95` for a stricter check. Core and floor support remain required independently. HOLLOW cities must remain inside the host and fit an existing cavity floor and roof; they do not inherit the partial-exposure allowance.

This file uses the shared optimistic JSON editor under terrain tuning. Values apply to new worlds and are recorded in generation identity. The [terrain guide](development/terrain.md#structures) defines the metric, scope and fit limits. The `terrain.ancient_cities` startup switch owns interior placement and bounded room clearance together. Clearance uses the final piece layout, retains the island shell, foundations and authored water beds, and leaves natural terrain between separate pieces. Disabling the switch does not convert saved starts.

## Rivers and lakes

`config/skyweave_compat-rivers.json` controls native Sky Archipelago 1.4 hydrology in Skyweave Islands. It uses the upstream codec and explicit new-world defaults; missing, unknown or incorrectly typed fields fail instead of restoring legacy disabled defaults. Values apply at startup, belong to saved generation identity and require a fresh world when changed. The shared editor groups the native anchor and satellite settings.

| Fields | Defaults | Purpose |
| --- | --- | --- |
| `enabled`, `lakes_enabled` | true, true | Native rivers and lakes |
| `anchors.frequency`, `satellites.frequency` | 0.60, 0.25 | Candidate frequencies, before terrain admission |
| `anchors.minimum_radius`, `satellites.minimum_radius` | 52, 32 | Minimum host descriptor radius |
| `anchors.minimum_width`, `anchors.maximum_width` | 6, 12 | Anchor river widths |
| `satellites.minimum_width`, `satellites.maximum_width` | 3, 6 | Satellite river widths |
| `anchors.minimum_lake_radius`, `anchors.maximum_lake_radius` | 16, 30 | Anchor lake radii |
| `satellites.minimum_lake_radius`, `satellites.maximum_lake_radius` | 7, 14 | Satellite lake radii |
| `anchors.maximum_tributaries`, `satellites.maximum_tributaries` | 2, 0 | Tributary limits |
| `minimum_depth`, `maximum_depth` | 2, 4 | Native channel depth |
| `meander_strength` | 0.65 | Native meander parameter |
| `headwater_lake_chance`, `standalone_lake_chance` | 0.35, 0.35 | Native lake candidate choices |
| `fluid_block` | `minecraft:water` | Native supported source fluid |
| `temperate_biome`, `cold_biome` | `minecraft:river`, `minecraft:frozen_river` | Native river-biome projection |
| `ecological_bank_width` | 4 | Bank projection width |
| `excluded_biomes` | `[]` | Biomes excluded from native hydrology |

This does not enable the global ocean or change sea level. Configured frequency is not realized river coverage. The generator owns support checks, plans, materials, flow and river-biome projection; Compat does not run a second basin generator. Native fluid ticks and later features can change initial fluid states. See [terrain stages and ownership](development/terrain.md#local-water).

## Ocean habitats

`config/skyweave_compat-oceans.json` defaults to `enabled: true`. It admits native floating ocean archetypes in eligible ocean-biome locations, independently of the disabled global ocean layer. Explicit saved opt-outs remain authoritative. Native settings own archetype selection, weights, materials, biomes and underbellies. Water-archetype anchors have no land companions.

| Fields | Defaults | Purpose |
| --- | --- | --- |
| `enabled` | true | Admit floating ocean habitats |
| `coastalChance`, `perimeterChance` | 0.04, 0.01 | Deterministic edge-treatment choices on eligible Lagoon/Reef Ring descriptors |
| `perimeterMinRadius`, `perimeterMaxRadius` | 40, 64 | Descriptor radii for falling-perimeter hosts |
| `outletWidth`, `outletDepth` | 2, 2 | Coastal corridor width and depth below fluid surface |
| `sealThickness` | 4 | Minimum retaining-bed thickness |

Coastal mode lowers a selected native rim corridor; perimeter mode lowers the rim around the wet footprint. Unselected native profiles remain unchanged. There is no separate enclosed basin or underside-outlet backend. All fields are required and included in generation identity. Use a fresh world when changing them. These policies do not certify aquatic progression, natural populations or resource availability; [verification](development/verification.md) records actual tested coverage.

## Structure distribution

`config/skyweave_compat-structures.json` controls discoveries in the Skyweave preset.
Each family shares an allowance across the main island and its companions. Void
around the cluster contributes no area. Variants share the allowance: five village
biome IDs do not supply five separate village budgets, and ordinary/beached
shipwrecks count together.

`targetStartsPer1024Chunks` is an expected allowance per 1,024 eligible terrain
chunks, estimated with a bounded physical-terrain/biome sample. It is not a final
spawn chance. Fractional allowances use a stable seeded draw; caps, native biome
rules, fitting and finite fallback can reduce actual starts. A portal rate of
0.32 with 1,024 eligible chunks gives a 32% allowance for one portal before fitting.
Larger eligible areas increase the allowance up to `maximumPerCluster`.

| Family | Rate (rounded) | Maximum per cluster |
| --- | ---: | ---: |
| Ruined portals | 0.32 | 1 |
| Ordinary villages | 0.443 | 3 |
| Pillager outposts | 0.1 | 1 |
| Shipwrecks | 0.889 | 1 |
| Desert temples, jungle temples, swamp huts, igloos | 0.5 each | 1 each |
| Woodland mansions | 0.08 | 1 |
| Ancient Cities | 0.889 | 1 |
| Mineshafts | 2.048 | 3 |
| Trial chambers, trail ruins | 0.443 each | 1 each |

These starting rates use half the nominal vanilla source-opportunity density,
with separate biome and fit checks. They do not promise vanilla encounter rates.
`frequencyMultiplier` defaults to `1`, accepts `0..8`, and scales managed allowances
once. Family rates accept `0..16`; maximum counts accept integers `0..16`. A zero
rate, cap or multiplier disables the corresponding managed opportunities in a new
world. Stronghold rings, Sky Villages, ocean monuments/ruins, local placed features
and unknown/mixed modded sets keep their existing controls. City allowances are
inactive when the separate interior-city adaptation is disabled. Disabling native
custom structure rules, explicitly whitelisting a member or changing a family to
an unsupported native category leaves that entire registered set under native
placement. Its family rate/cap then does not apply. Supported category changes
continue through the corresponding native planner; denylisted variants stay denied.

`outpostVillageSeparationChunks` defaults to `10` (`0..64`). It excludes outposts
within that square distance of fitted ordinary-village anchors, rather than void
source positions or failed villages. Zero disables this exclusion. Multiple
ordinary villages may share a cluster; ordinary villages and Sky Villages are not
made mutually exclusive.

The policy preserves native layouts and loot. Selected destinations are fitted
before commitments, with three ranked alternatives per village slot and eight
per other slot. Owned igloos use an island-relative surface anchor for their top,
ladder and optional basement; their anchor persists through saving. Existing
city burial and cavern rules remain independently authoritative. Owned desert
pyramids retain their accepted island height instead of reanchoring to a void
column during block placement. Water structure
placement remains native.

All fields are required, validated at startup and editable through the existing
typed configuration page. Changes require a restart and a new world. The saved
generation identity includes the effective policy; incompatible worlds fail
clearly rather than changing generation silently. See [placement ownership and
update checks](development/terrain.md#cluster-structure-policy).

## Biome materials

The `skyweave_compat:surface_mud` biome tag selects mud for the upper three soil
blocks where upstream selected grass/dirt. It contains `minecraft:mangrove_swamp`
by default. Existing Sky Archipelago surface-override tags take precedence;
openings, fluids and deeper rock are preserved. Override the tag in a datapack
to change eligibility; the resolved set is recorded in generation identity.

## Interaction with Sky Archipelago

Keep `customStructureRulesEnabled` enabled and retain the SKY category for
`skyvillages:skyvillage`. Disabling custom rules bypasses the upstream validator
this mod extends. Changing the village to a ground category also bypasses this
SKY-specific rule and can change its placement behavior.

Leave the village out of `skyStructureWhitelist`: upstream whitelisting skips
native vertical-clearance validation. It does not disable this mod's horizontal
proximity rule. `DEFAULT` alone does not avoid SKY routing while the ID still
matches a sky category token.

The neighbor filter uses the resolved terrain settings and island field. In ordinary
Sky Archipelago worlds it works with upstream spacing modes without changing them.
Skyweave Islands uses the geometry profile above. Neither path overrides biome
eligibility.

## Tuning

Increase `maximumGap` to admit more distant floating villages. Decrease it to keep
villages closer, at the cost of more clusters without a village. Decrease
`minimumIslandRadius` to allow small islands to qualify as hosts. Increase
`minimumGap` to leave a wider void around villages.

In the versioned Skyweave preset, each anchor receives a deterministic allowance
of 0–3 villages. The weights are 96:24:8:3: approximately 73.3% receive zero, 18.3%
one, 6.1% two and 2.3% three. Companions share their anchor's allowance. These are
maximums, not promised counts: biome, layout and clearance rules can reject a site.
The profile owns this distribution and its candidate spacing; `villages.clearance` controls
the horizontal neighbor filter, not the preset's count policy.

In ordinary Sky Archipelago presets, frequency remains owned by Sky Villages'
structure-set datapack. The neighbor rule only filters those candidates.

## BlueMap viewer

On a dedicated server with BlueMap 5.7, edit
`config/skyweave_compat-bluemap.json`. The mod default selects no maps. Add map
IDs to `maps` to opt in; only maps of the running Sky Archipelago Overworld qualify.
Both the ordinary Sky Archipelago generator and Skyweave Islands are supported.
The current world's actual vertical bounds are used. Nether, End and foreign
generators retain the original viewer.

| Field | Default | Accepted values |
| --- | --- | --- |
| `maps` | `[]` | Up to 128 nonblank BlueMap map IDs |
| `detailDistance` | 4000 | 1000–8000; camera-distance cutoff for detailed drawing |
| `tileRadius` | 31 | 1–48; maximum detailed tile radius |
| `concurrentRequests` | 4 | 1–8 concurrent detailed tile loads |
| `geometryMiB` | 256 | 32–1024 MiB of retained detailed vertex/index buffers |

All fields are required. Malformed settings disable the adaptation instead of
silently changing values. Run `bluemap reload` after edits; open browsers refresh
the adapter settings every 30 seconds. Empty `maps` restores the stock renderer.
These settings do not change saved generation identity or require a new world.
They do not change BlueMap's map boundaries, tile generation, resource consent or
network binding. Keep full-height rendering and cave culling disabled in BlueMap
when underside geometry is required. See [implementation and limits](development/bluemap.md).


## Distant Horizons

DH 3.3.3 remains responsible for its ordinary generation, delivery, and rendering.
The feature table above owns Skyweave: Compatibility Patches' three independent startup switches.

Enable `distant_horizons.world_cache` to use a UUID saved in
`create-sky-lod-world-id` for Sky Archipelago saves. DH's `sendLevelKeys` must also
be enabled. Enable the cache adapter on the client to suppress ambiguous legacy
imports into those UUID namespaces. Foreign generators retain ordinary keys.

An explicit false setting selects upstream cache behavior and installs no cache
hooks, even if a Skyweave: Compatibility Patches identity marker exists. Enabling selects a different
client namespace without moving, deleting, or populating old caches. A retained
marker is reused when re-enabling the adapter; keep it with the save. Malformed
markers fail when the adapter is active. Server databases are not renamed.

The old per-world `savedWorldIdentity` setting is no longer read. Operators who
used it must explicitly enable the startup cache switch on the applicable sides.
See [DH implementation](development/lod.md) for boundaries and update checks.

## Questlog

`config/skyweave_compat-journal-client.toml` is registered when either client
journal feature is active. The Settings bookmark opens grouped Display and
Notifications preferences. Mods → Skyweave: Compatibility Patches → Config → Interface and accessibility → Questlog
edits the same values. Changes save locally and apply without restart.
The three startup switches remain independent.

| Setting | Default | Behavior |
| --- | --- | --- |
| `itemImages` | `true` | Show authored objective icons and native reward ItemStacks |
| `conditionSymbols` | `true` | Add small symbols beside explicit inventory, interaction, advancement and alternative labels |
| `notifications` | `PROGRESS_AND_COMPLETION` | First positive objective progress and first completion; alternatives are `COMPLETION` and `OFF` |

The Rewards tab is a preview; it never grants items. Incomplete choices are read-only.
Completed entries use Questlog's native collection protocol. The server validation
switch does not affect native automatic rewards or change inventory overflow rules.

Notifications honor `toast_on_complete` exclusions. First-progress notices use
that same flag unless a quest definition explicitly supplies
`"skyweave_compat": {"toast_on_progress": false}` (or `true`). This optional
metadata is not required by ordinary journals. Keep it in the owning authoring
source if used; do not hand-edit generated quest files. With the notification
adapter enabled, native unlock popups/toasts are suppressed. There are no notices
for each subsequent counter increment.

Full synchronization is silent. Completed-cycle resets may announce a new start;
non-monotonic third-party objectives and resets before completion have limits
explained in the [implementation contract](development/questlog.md#notifications).
Questlog's own dependencies still apply; its 3.5.0 client needs Cloth Config even
though its NeoForge metadata does not declare that prerequisite.


## Configuration interface

Open **Mods → Skyweave: Compatibility Patches → Config**. Collapsible gameplay
systems contain mod pages. A mod can appear under more than one system when it
owns different mechanics. Selecting a page collapses unrelated groups; its
setting links snap-scroll inside that page. Search above the sidebar matches
translated names and descriptions across pages. Clearing search restores the
navigation groups while keeping the selected page accessible.

The selected menu destination and its setting title are yellow. Selecting another
entry clears the previous title highlight. Descriptions use Minecraft's gray;
setting titles remain white. The text-only `i` opens feature details,
with a Details tooltip and a Unicode font applied only to that glyph. Search retains native text selection and clipboard
shortcuts: Command on macOS, Control on other platforms.

Each page has one ordered set of purpose groups: Preferences, Fixes, Tuning and
Shader workspace where applicable. The sidebar and content follow the same order;
empty groups are omitted. Thin outlines group related controls without filled cards.
Mod pages are sorted by their localized names within each system.

Forms use one or two columns according to available width. Setting links use
measured positions, including the last section when it cannot reach the top.
Page position and drafts survive navigation, resizing, details and editor returns.
**Status & files** links to the owning pages and provides explicit Reload files.

Journal preferences save immediately through their existing helpers. Startup
switches, owned JSON files and embedded TOML fields use **Save changes**, which covers all
staged files, including other pages. Reset changes a draft; it does not immediately
write. Invalid input remains editable and cannot be saved. Current activation,
saved values, and unsaved changes remain distinct; a configured-on adaptation can
stay inactive when its dependencies are absent.

| Destination | Editor | Application |
| --- | --- | --- |
| Adaptation switches | Staged switches and Details | Full game/server restart |
| Journal preferences | Shared immediate controls | Immediate |
| Village distances | Embedded staged fields | Next local server start |
| Island geometry, interiors, water, oceans, structures | Typed numeric/boolean forms | Game/server restart and a new world |
| BlueMap adaptation | Typed fields and map-ID list | Owning server's BlueMap reload |
| Redstone Link GUI client/common | Embedded staged enum/range fields | Native timing, after Save |

Small registered forms are embedded in their owning page. Native specs still
validate, serialize and apply them; editing alone does not change runtime values.
Application timing is shown per field. Preferences is a navigation purpose, not a
promise of immediate saving. Journal preferences keep their existing immediate
behavior and are not undone by discarding other drafts. Raw file access lives in Status & files, and opening a page never creates missing
configuration. Single-field files do not add a redundant file heading or menu item.

External edits refuse a stale save and retain the draft. Reload requires explicit
discard of pending edits. All staged files are validated before saving begins;
independent files are not one transaction. If a later write fails, feedback lists
committed files and the failed file, keeping unapplied drafts. A failed read keeps
any previously captured snapshot available with a warning.

This interface edits local installation files. A local common config does not
administer a connected server. Terrain changes retain saved-world identity checks:
saving never converts or regenerates a world. Shader assembly keeps its existing
rebuild control under Rendering → Iris.

The [implementation reference](development/configuration.md) owns persistence
safety, optional loading, native fixtures and dependency-update checks.

## Iris world text

Under **Rendering → Iris**, the two shadow-exclusion switches independently omit
text displays (including backgrounds) and ordinary entity nametags from Iris's
shadow map. Both default on when Iris is available; explicit false settings retain shadows. Restart Minecraft to change either policy. Neither requires the island
preset, Sable, Veil or Colorwheel.

Ordinary text rendering and entity-body shadows are retained. Item/block
displays, signs and custom world interfaces are outside these switches. A modded
nametag renderer that bypasses the base entity renderer is not covered.

A text display's native brightness override controls its lightmap. Its `shadow`
flag controls the glyph drop shadow, and its shadow radius controls Minecraft's
entity blob shadow. These are separate from Iris shadow-map rendering. Enabling
full-bright text does not itself stop a display casting a shader shadow.

## Redstone Link GUI

Under **Machines and signals → Redstone Link GUI**, inline fields
provide the client click mode and common relocation range. These are upstream settings, separate from
Skyweave: Compatibility Patches startup switches. Default `clickMode="SHIFT_SLOT"` requires an empty hand
and Shift + right-click on a frequency slot; default `moveRange=24` applies to
the relocation tool. Keep the common value consistent on both sides. Client
settings are personal; common settings are not server-to-client configuration sync.
