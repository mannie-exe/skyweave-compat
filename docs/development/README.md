# Development

Use Java 21 and the checked-in Gradle wrapper for Minecraft 1.21.1 / NeoForge 21.1.265:

```sh
./gradlew --no-daemon check build
```

Build outputs are in `build/libs`. The minimum installation requires Minecraft, NeoForge, Compat, and the qualified re:form library on both physical sides. Content integrations remain optional. `build.gradle` owns exact compilation and development-runtime pins; development dependencies are not bundled or implied runtime requirements. [Publishing](../publishing.md#shared-settings-dependency) describes staged candidates and clean release resolution.

## Implementation boundaries

`FeatureSettings` reads immutable startup switches before Mixin target resolution. `FeatureSelection` admits dependencies, physical sides, and explicit prerequisites from loader metadata; `OptionalMixinPlugin` selects only admitted mixins. Disabled integrations neither resolve foreign classes nor constrain their versions. `SkyweaveCompat` registers admitted callbacks. `IntegrationPacks` exposes separately gated built-in data packs rather than unconditional integration data at the JAR root.

[Dependency admission](dependencies.md) owns development baselines, justified runtime ranges, reviewed aliases, and failure context. Keep private DH/Iris contracts separate from public API-family acceptance.

Keep geometry independent of Minecraft APIs and native adapters small. Generation queries must not load neighboring chunks or depend on visitation order. Minimum, enabled, disabled, and unsupported-disabled native fixtures qualify different boundaries. Unit results, startup, physical generation, and visible rendering retain separate evidence.

The standalone server and client probes are separate artifacts and never release contents. [Testing](testing.md) owns invocation, fixture inputs, and cleanup; [verification](verification.md) owns qualified artifact identities and limits. Historical comparisons use the exact old artifact, not a second implementation compiled into the current suite.

Original project work uses Apache-2.0. Preserve the LGPL-3.0-only rendering headers and BlueMap's MIT notice when editing incorporated work; [NOTICE](../../NOTICE) owns those exceptions. When adding an integration or adapting another implementation, update the [README credits](../../README.md#credits), retain its provenance and required notices, and check packaged license metadata and license texts. Calling an installed API does not change that upstream project's license. Keep build-tool notices, including the Gradle wrapper license, separate from project authorship.

`src/main/resources/logo.png` owns the mod-list image through NeoForge metadata. It is independent of the modpack's resource packs and hosted project icon. Preserve `logoBlur=false` when retaining pixel artwork.

## Owning guides

| Concern                                                            | Guide                                             |
| ------------------------------------------------------------------ | ------------------------------------------------- |
| Optional admission, dependency updates, and retirement             | [Adapter maintenance](adapters.md)                |
| Settings definitions, storage, commands, and native callbacks      | [Configuration](configuration.md)                 |
| IPN shortcut ownership, text focus, and native input boundaries    | [Input ownership](input.md)                       |
| Island geometry, geological frames, structures, and saved identity | [Island generation](terrain.md)                   |
| Shapes, ocean admission, and metadata discovery                    | [Terrain variation](terrain-variation.md)         |
| Targeting, search, teleport validation, and connection lifetimes   | [Wayfinder](wayfinder.md)                         |
| DH lifecycle, cache, and optional data boundaries                  | [DH integration](lod.md)                          |
| BlueMap's optional terrain view                                    | [BlueMap](bluemap.md)                             |
| Client renderers, shaders, and item/model handoffs                 | [Rendering](rendering.md)                         |
| Patrols, spawners, and Murk water rules                            | [Encounters](encounters.md)                       |
| Journal presentation, notifications, and rewards                   | [Questlog](questlog.md)                           |
| Live map zoom and the Connector boundary                           | [Hoofprint](hoofprint.md)                         |
| Playback synchronization and Create registration                   | [Analog Audio](audio.md)                          |
| Construct grave recovery                                           | [Recovery](recovery.md)                           |
| Sodium moving lights                                               | [Lighting](lighting.md)                           |
| Machinery, inspection, and native tutorial scenes                  | [Linear Bearing](linear-bearing.md)               |
| Transmission interaction and signal contracts                      | [Transmission & Linkage](transmission-linkage.md) |
| Macaw bridge lantern shapes and required resource models           | [Bridge lanterns](macaw.md)                       |

Linear Bearing scene prose and the bounded workbench are authored through `python3 tools/generate_ponder.py`. Gradle checks generated resources. Native compile/seek controls, playback, and visible interaction cues are separate qualifications; use the owning guide before updating scenes.
