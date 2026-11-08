# Skyweave: Compatibility Patches

Optional terrain, gameplay, and rendering adaptations for Minecraft 1.21.1.
Every adaptation has a startup switch. The Skyweave modpack is not required.

## Installation

Install this mod with **Java 21 and NeoForge 21.1.264 or later in the 21.1 series**.
No content mod is required. With no supported targets installed, no adapters activate.
Use it on the client for rendering fixes; install it on both sides with matching
configuration when using the expanded island preset.

Edit `config/skyweave/skyweave_compat-features.properties` before startup to disable
unwanted features. Changes require a full process restart. Disabled adaptations
select no target mixins, callbacks, or data packs and do not constrain target
versions. An enabled adaptation activates only when its full dependency combination
is present on the applicable side; unsupported active combinations fail clearly.
See [all switches and supported combinations](docs/configuration.md#adaptation-switches).

| Optional integration | Supported version | Behavior |
| --- | --- | --- |
| Sky Archipelago | 1.4 | Expanded island preset, cluster discovery policy and separately selectable placement/climate adaptations |
| Create | 6.0.10 | Client window-face culling correction; preserves native connected textures |
| Glass backfaces | Create 6.0.10 or Fusion 1.3.16 | Independent default-on two-sided glass retaining native connected textures |
| Rechiseled | 1.2.6 | Client rotated-glass culling correction, including Rechiseled Create windows |
| Sky Villages + Architectury | 1.0.6 + 13.x (tested 13.0.11) | Village clearance and placement; needs Sky Archipelago |
| Sable | 2.0.6 | Island pressure; client single-block model-data correction |
| Aeronautics Delivery Quests | 1.1.1 | Separate route distance, chunk loading, and village landing adaptations |
| Linear Bearing + Create/Sable/Simulated | 1.3.5 + 6.0.10/2.0.6/1.3.2 | Separate startup/drop, carriage brake/recovery, transmission and gyro sensor adaptations |
| Linear Bearing guidance | 1.3.5 + Ponder 1.0.87+mc1.21.1 / Jade 15.10.6+neoforge | Optional tutorials and server-backed inspection; active carriage adaptation required |
| Transmission & Linkage + Create/Sable/Simulated | 0.2.9 + 6.0.10/2.0.6/1.3.2 | Deliberate rod removal, server-authorized edits and optional Jade inspection |
| Redstone Link GUI + Create | 1.21.1-1.9.2 + 6.0.10 | Server-authorized menus, bounded plain-link relocation and optional Sable 2.0.6 projection |
| Redstone Additions + Create | 1.0.5 + 6.0.10 | Separate gate logic, copper scraping, wrench and client transparency repairs |
| Analog Audio + Create | 0.1.5-hotfix.1 + 6.0.10 | Radio movement-handler registration |
| Analog Audio + AnalogLib | 0.1.5-hotfix.1 + 0.1.0 | Client playback timeline correction after loading and standby |
| Sable + You're in Grave Danger | 2.0.6 + 2.0.13 | Construct-relative graves, bounded loss fallback and moving location tracking |
| Sable Dynamic Lights + Lamb + Create | 2.0.1 + 4.8.11+1.21.1 + 6.0.10 | Client-world source cleanup; separate Sodium 0.8.13 lookup repair |
| Distant Horizons | 3.3.3 | Client handoff, island shutdown, and selectable cache isolation |
| Macaw’s Windows | 2.4.2 | Client mosaic block/pane backfaces; retains native vertical patterns |
| Decorative block backfaces | Create Confectionery 1.1.3b, Linear Bearing 1.3.5, Design n’ Decor 2.2b or Bits ’n’ Bobs 2.3.5 | Independent client switches for caramel bricks, bubble gum, ornate grate and industrial grating |
| Macaw’s Bridges | 3.1.2 | Standing/stair lantern selection matching pack models; enable on both sides |
| Questlog | 3.5.0, 3.5.3 | Readable conditions, reward previews, first-progress/completion notifications and server claim validation |
| BlueMap | 5.7 | Opt-in island viewer adaptation on dedicated servers |
| Surveyor + Sky Archipelago | 1.2.4 + 1.4 | Skip rejected structure starts in map records |
| Veil + Iris | 4.3.2 + 1.8.14-beta.1+mc1.21.1 | Framebuffer restoration, including offscreen scenes |
| Iris | 1.8.14-beta.1+mc1.21.1 | Independent neutral lighting and shadow exclusions for text displays and entity nametags |
| Aeronautics + Iris | 1.3.2 + the Iris version above | Exclude the balloon hot-air overlay from shadow rendering |
| Colorwheel | 1.3.2+mc1.21.1 | Separate non-OIT sampler allocation and direct Sable material metadata repairs |
| Sable + Iris + Sodium | 2.0.6 + the Iris version above + 0.8.13+mc1.21.1 | Shader block IDs and single-block render passes |
| SSRD + Sable + Iris | 1.8.8 + 2.0.6 + the Iris version above | Distant construct shader range; separate opaque-terrain occlusion with DH 3.3.3 |

Remove Veil Iris Fix when enabling the incorporated framebuffer adaptation.
Remove Sky Village Neighbors when enabling Skyweave: Compatibility Patches' village adaptations.
Upstream mods retain their own dependencies. Create/Propulsion ore IDs are optional
members of the relative-placement tags, not required mods.

No world preset is selected automatically. To use the expanded terrain, install
Sky Archipelago and create a new world with **Skyweave Islands**, or set
`level-type=skyweave_compat:islands_v1` before creating a dedicated-server world.
Do not change an existing save's generator or recorded generation configuration.
Ordinary Sky Archipelago worlds receive only applicable enabled adaptations.
With Dynamic Waters installed, set `Generation.normalRiverMode=VANILLA` and
`mountainRiverTunnels=false` before creating a Skyweave world; keep both native
river/lake flags off. The [configuration guide](docs/configuration.md) owns these
settings. Ordinary worlds keep their upstream policy.

The rendering adapters have background checks and native macOS shader, Ponder,
balloon, and single-block glass comparisons. The original flickering/gapped airship
shadow report remains unresolved; this is not a general shadow correction.
The Analog Audio registration fix likewise does not establish moving playback or
GUI behavior. See [verification](docs/development/verification.md) for exact scope.

[Playback synchronization](docs/development/audio.md) and
[construct grave recovery](docs/development/recovery.md) are separate adaptations.
They preserve upstream media and inventory ownership; neither requires island
generation. Recovery keeps its metadata in YiGD's saved grave records.

See [redstone editing and gate behavior](docs/development/redstone.md) for relocation
limits, analog signal changes and pack-owned resource fixes.

Shader recipes can assemble qualified shaders on the client before Iris loads them.
The [rendering guide](docs/development/rendering.md#shader-assembly) covers inputs,
ownership, caching and supported patch operations. No recipes are installed by this mod itself.

## Linear Bearing

Independent switches repair dedicated startup and gum drops, passive-guide braking
and recovery, magnetic/spring kinetic transmission, and gyro sensing. Links use
Create's real source and stress network. The gyro supplies a signal with zero
stress capacity. Linear Bearings Fix 1.0.3 can remain installed; Skyweave: Compatibility Patches defers its
overlapping startup/drop repairs. Read [machinery contracts and dependency
ownership](docs/development/linear-bearing.md) before changing or removing these adapters.

Optional Ponder scenes explain assembly, motion, braking, recovery, transmission,
and sensing. Jade shows actual brake/link state with extra detail on demand.
Both integrations can be disabled independently; neither makes Ponder or Jade
a mandatory dependency.

## Colorwheel

Colorwheel supplies Iris-aware Flywheel backends for compatible shaders. It does
not replace the framebuffer, single-block-pass or moving-light adaptations.
With Colorwheel installed, separate client switches release unused OIT texture
slots on unsupported renderers and extend construct material annotations to its
terrain/emission protocol. Ordinary world annotations and
backend selection remain Colorwheel's responsibility. See
[rendering](docs/development/rendering.md#colorwheel) for boundaries and updates.

## Moving lights

With Sable Dynamic Lights and LambDynamicLights installed, independent client
adaptations repair the selected Sodium API, keep transient illumination out of
integrated-server lighting and clear Create light owners when leaving a world. Neither requires the island preset or Iris. These are optional
repairs, not another lighting engine or a general cabin-occlusion fix. See
[lighting](docs/development/lighting.md) for dependencies and known limits.

## Distant terrain

DH generates and renders distant terrain through its ordinary pipeline. Skyweave: Compatibility Patches
adds focused lifecycle fixes and optional per-save cache isolation; it does not
provide a separate terrain predictor. For supported Sky Archipelago saves, cache isolation defaults on. It selects a
per-save namespace without moving or deleting older cached terrain; explicit false
retains upstream keys. See [DH configuration](docs/configuration.md#distant-horizons)
and [integration boundaries](docs/development/lod.md).

## Journal

With Questlog installed, conditions wrap and rewards can be inspected before an
entry is complete. Separate tabs preserve access to both. Scrollbars use the
content length; narrow windows show one section at a time. Item imagery and
condition symbols are controlled through an attached Settings bookmark, alongside
notification preferences. Questlog still owns objectives,
progress, reward grants and replaceable interface textures.

Journal notifications announce first observed objective progress and completion,
not prerequisite unlocks. Completing an entry immediately suppresses its start
notification. Reward collection remains explicit. The interface and notifications
work on clients connected to ordinary Questlog servers; invalid reward-request
protection requires Skyweave: Compatibility Patches on the server. See [journal settings](docs/configuration.md#questlog)
and [contracts and limitations](docs/development/questlog.md).

## Island terrain

**Skyweave Islands** uses a build range from Y=-128 through Y=767. Clusters have
large anchor islands surrounded by smaller companions. Default anchor height
targets are 250 to 400 blocks, with placement fitted to leave floor clearance and
building headroom. Ore placement, large iron/copper veins and classic cave carvers
use island-relative heights rather than vanilla world altitude. The preset retains
stacked exterior terrain and offers baseline, noise-cave and hollow interiors with
separate controls for openings and native carving. Sky Archipelago 1.4 supplies
the exterior shapes. An optional Dynamic Waters adapter supplies island-local
rivers and lakes while the native planner stays off. Granular aprons add detached
outer terrain without consuming the main body. Optional floating-ocean settings add coastal
outlets to native wet profiles; falling perimeters remain opt-in and the global ocean stays off.
Ancient Cities fit inside Deep Dark hosts, including suitable hollow interiors.
Normal and noise-cave hosts permit limited peripheral exposure while protecting
the core. Structure lookup resolves relocated starts instead of
rejecting them from their original source biome. Vanilla terrain systems are not
all reproduced; the [coverage limits](docs/development/terrain.md#feature-coverage-and-limits)
describe ocean content, fluids and native feature behavior.

The default candidate grid is 2400 blocks, with up to 750 blocks of displacement
in each horizontal axis. Accepted centers do not follow fixed spacing;
activation, biome eligibility, clearance and island shapes affect
actual travel distances. See [configuration](docs/configuration.md) for controls
and [terrain](docs/development/terrain.md) for implementation limits.

## Placement

The default rule requires at least **64 blocks** between the village's full
horizontal bounds and predicted island terrain. It also requires a sampled terrain
column within **512 blocks** belonging to an island with a native radius of at
least **96 blocks**. These distances are measured between block columns, not from
the village center.

The expanded preset shifts villages into its island altitude band and assigns
each archipelago zero to three villages, weighted toward zero. Buildings and loot
remain unchanged. Biome and terrain rejection can reduce the actual count. The
neighbor rule does not move villages onto islands or create roads. Ordinary Sky
Archipelago presets keep their original height and frequency. Existing generated
villages and regular villages are unaffected by the neighbor rule.

The host search uses a 16-block grid. It may miss a narrow eligible part of an
island and reject that candidate. Clearance checks every column in the exclusion
band, so that approximation cannot admit a village overlapping terrain. Terrain
predictions do not include later structures or player-built blocks.

## Structure search

Operators can verify structures without blocking a synchronous `/locate` search:

```text
/skyweavecompat structures search 24000 family=villages
/skyweavecompat structures search 24000 structure=ctov:medium/village_plains
/skyweavecompat structures search 24000 structure=#minecraft:village
/skyweavecompat structures status
/skyweavecompat structures page 2
/skyweavecompat structures cancel
```

Use one family, registered structure ID or structure tag. Results contain verified native starts; click coordinates to prepare a teleport. Where native placement has no meaningful altitude, the displayed Y comes from the saved start's bounds. Nothing teleports automatically. The search continues on its own, retains up to64 nearby matches and pages ten at a time. One structure search may run per server; its requester owns cancellation.

Verification can generate and save early chunk metadata. It requests `STRUCTURE_STARTS`, with at most two native requests at once, and does not deliberately generate full terrain. Time/candidate exhaustion returns **partial** results, not proof of absence or of the nearest possible structure. Only an exhausted supported search domain is complete. Unsupported placements, including concentric-ring strongholds, are reported explicitly. Ordinary vanilla `/locate`, exploration maps and other synchronous callers retain their existing behavior and can still be expensive.

Search budgets live in `config/skyweave/skyweave_compat-search.json` and apply after restart. They do not change terrain identity. See [search limits](docs/configuration.md#structure-search) and [maintenance](docs/development/terrain-variation.md#structure-discovery).

## Configuration

Open **Mods → Skyweave: Compatibility Patches → Config** for searchable gameplay
systems and mod pages. Setting links jump within the selected page; owned JSON
files and registered scalar TOML settings have inline forms.
Journal preferences apply immediately; fix changes save for the next startup. The menu edits local files, not a connected server.

Feature switches remain in `config/skyweave/skyweave_compat-features.properties`.
Village tuning uses `config/skyweave/skyweave_compat-common.toml`. Island geometry lives in
`config/skyweave/skyweave_compat-generation.json`; cavity and native-carver controls live in
`config/skyweave/skyweave_compat-interiors.json`. Native hydrology uses
`config/skyweave/skyweave_compat-rivers.json`; adapted watersheds use upstream
`config/dynamicwaters-common.toml`. Optional ocean edges use
`config/skyweave/skyweave_compat-oceans.json`, and city burial uses
`config/skyweave/skyweave_compat-ancient-cities.json`. Restart the game/server after changes;
changed generation settings require a fresh world.
The server's configuration controls multiplayer generation.

See [configuration](docs/configuration.md) for controls and limitations,
[development](docs/development/README.md) for builds and verification, and
[publishing](docs/publishing.md) for release instructions.

## License

Original implementation: MIT. Incorporated framebuffer code and its test infrastructure:
LGPL-3.0-only. See [NOTICE](NOTICE) and [license texts](licenses/). Upstream binaries
and templates are not bundled. This is an independent compatibility mod.

Skyweave: Compatibility Patches is independent of the unfinished Skyweave civilization mod. The playable pack is Create: Skyweave. The registry namespace is `skyweave_compat`; see [existing saves](docs/configuration.md#release-identity-and-existing-saves).
