# Development

Use JDK 21 and the checked-in Gradle wrapper:

```sh
./gradlew --no-daemon check build
```

The target is Minecraft 1.21.1 / NeoForge 21.1.265. Sky Archipelago 1.4 and
Sky Villages 1.0.6 are pinned by Modrinth version ID and SHA-256. They are downloaded
for compilation/testing, not redistributed. Architectury 13.0.11 is included in
the development runtime for Sky Villages. Output is in `build/libs`.

`src/main/resources/logo.png` owns the NeoForge mod-list image, referenced by
`logoFile` in the mod metadata. Replace this image independently of the modpack
and its resource packs. The current placeholder is an opaque white, indexed PNG
banner; `logoBlur=false` preserves crisp pixels. Gradle packages it at the JAR
root. It does not set a hosted project's publishing icon.

See [DH integration](lod.md) for optional lifecycle and cache fixes, pinned
implementation boundaries and dependency-update checks. The server and client
probes are separate artifacts and are never bundled in the mod.

[Wayfinder](wayfinder.md) owns sight targeting, destination validation, packet
lifetimes and its optional read-only DH data adapter.

See [testing](testing.md) for build, server and client probe procedures.

See the [configuration interface](configuration.md) for settings ownership, safe
startup editing, native UI fixtures, and platform update checks.

See [radio synchronization](audio.md) and [construct grave recovery](recovery.md)
for their independent optional boundaries, native fixtures and update checks.

See [Questlog integration](questlog.md) for client presentation, notification state,
server validation and the isolated journal fixture.

See [moving lights](lighting.md) for the optional SDL/Sodium hook, native fixture,
upstream boundaries and pack-integration requirements.

See the [Hoofprint zoom investigation](hoofprint.md) for the pinned config and
scroll limits, a proposed client adapter, and remaining Connector/runtime checks.

See [Linear Bearing](linear-bearing.md) for machinery contracts, standalone-fix
ownership, finite native fixtures and dependency-update boundaries.
Its optional client tutorials and Jade inspection use the same machinery contract.
Run `python3 tools/generate_ponder.py` after editing scene prose or the bounded
workbench; Gradle checks those resources. The disposable client probe accepts
`-Dskyweave_compat.probe.guidance=true` alongside `probe.linear=true`. Use
`compatprobe_guidance_check` to compile and seek the selected scenes, and
`compatprobe_ponder <item-id>` to inspect real playback. A successful compile/seek
does not establish visible geometry, reading time or correct interaction cues.

## Implementation

See [bridge lanterns](macaw.md) for the optional Macaw shape correction and its required resource model.

See [Transmission & Linkage](transmission-linkage.md) for optional interaction
authorization, Jade inspection, upstream limits and native machinery fixtures.

`FeatureSettings` reads immutable switches before Mixin target resolution.
`FeatureSelection` uses loader metadata, the physical side, and explicit feature
prerequisites. `OptionalMixinPlugin` selects only the active mixins. Disabled
adapters do not validate versions. `SkyweaveCompat` registers callbacks and
loads integration classes only for active selections. `IntegrationPacks` exposes
terrain, relative-placement, and pressure data as separately gated built-in packs.
No integration data is unconditionally exposed at the JAR's `data/` root.

See [adapter maintenance](adapters.md) for ownership, dependency updates, and
retirement conditions, and [rendering](rendering.md) for client-specific paths.
The minimum runtime is NeoForge plus Skyweave: Compatibility Patches; development dependencies are not
runtime requirements. `tools/startup_probe.py` covers disposable dedicated-server
startup combinations. It never launches a client or touches the installed save.

The Sable API is compile-only with a separate development runtime dependency.
Development runs retain the complete pinned integration set. For the minimal
native check, run:

```sh
python3 tools/probe.py --generation adapted --minimal --seed 42 --reopen --heap 6G \
  --server /path/to/server/game --java /path/to/java
```

It stages only Sky Archipelago, Skyweave: Compatibility Patches and the separate probe, without copying
other mods' configs.
The full check omits `--minimal`. These cover the general gameplay integration set. The optional BlueMap boundary
has a separate minimal-plus-BlueMap check described in [BlueMap](bluemap.md).

Loading references: [NeoForge metadata](https://docs.neoforged.net/docs/1.21.1/gettingstarted/modfiles/)
and [Mixin configuration plugins](https://javadoc.io/static/net.fabricmc/sponge-mixin/0.16.4%2Bmixin.0.8.7/org/spongepowered/asm/mixin/extensibility/IMixinConfigPlugin.html).

[Island generation](terrain.md) owns the expanded preset, geometry, geological
frame, saved-world compatibility and native terrain survey. The following describes
the retained village-neighbor predicate.

[Terrain variation](terrain-variation.md) describes horizontal sampling, fractured ecology, ocean admission, bounded metadata discovery, and the structure-lookup boundary. Configuration defaults and verification remain in their owning guides. Gargantuan islands remain deferred.

`placement/NeighborRule` owns the loader-independent geometry. `IslandTerrain`
adapts Sky Archipelago's resolved column field; it never requests world chunks.
`StructureSupportMixin` filters accepted SKY reports for exactly
`skyvillages:skyvillage`. Native height fitting happens before this hook; overlap
reservation, registration and structure references happen afterward.

The rule first rejects obvious overlap on a coarse grid, then finds a real nearby
terrain sample with a qualifying dominant island descriptor. Accepted candidates
must additionally pass an exhaustive column check in the minimum-clearance band.
All distances use the complete layout's X/Z bounding rectangle. Bounding-box gaps
between buildings are deliberately reserved too. This predicate introduces no mutable placement counters or visitation-order
choices. The dedicated preset separately applies the deterministic per-archipelago
allowance described in the island-generation document.

This predicate is deterministic for the supplied layout, terrain field and
settings. It does not remove upstream's existing structure overlap reservations
or prove that the entire upstream generation pipeline is independent of order.
Debug logging distinguishes `TOO_CLOSE` and `NO_HOST`; the upstream engine may also
report its generic air-clearance rejection stage.

`NeighborConfig` registers a NeoForge COMMON spec and captures immutable settings
at server startup. A client-only entry point registers NeoForge's config screen.
The server artifact has no dependency on loading client GUI classes.

## Analog Audio

The optional Analog Audio/Create reflection repair and its retirement checks are
owned by [audio maintenance](audio.md#create-registration). Playback timing is a
separate client adaptation in that same guide.
