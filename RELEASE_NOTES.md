# Skyweave: Compatibility Patches 0.45.0

Install with Minecraft 1.21.1, Java 21, NeoForge 21.1.265 or later in the stable 21.1 series, and [re:form 0.4.0](https://github.com/mannie-exe/reform/releases/tag/v0.4.0). Use the same Compat version on client and server. This release preserves C1, C2, and C4 while adding the qualified animation, equipment, and construct-rendering repairs below.

## Animation and equipment

- Match authored Moves overlay filters to the correct arm and held-item bones. Action/group names, animation selection, and clocks remain owned by Moves.
- Limit Better Combat's extra upward body and leg lean to 20 degrees while preserving composed arm aim and native attack authority. First-person, level, and downward adjustments retain Better Combat's behavior.
- Load the matching modpack lantern/soul-lantern resources and align the held model with its hand and native physics pivot. Keep native equip scale, arm profiles, and physics behavior without copied animation curves. The lantern switch requires the overlay-bone and item-definition adapters; disabling it or either prerequisite withdraws the complete managed bundle.
- Preserve Reliable Gliders' equipped attachment alongside Moves third-person items and Punchy first-person hands, with separate switches for the two render owners. Inventory and flight physics stay native.

## Construct rendering

- Retire old compiled material IDs when Iris requires a renderer rebuild, so constructs use the current shader map after switching packs. Sable retains rebuild and publication ownership.
- Apply camera rotation once for shader-rendered single-block constructs while retaining their body transform and translucent pass.

## Dependencies, settings, and update procedure

- Keep public re:form 0.4.0 and the matching Styled 0.2.0 cohort. No engineering UI or new water/physics provider is required. Newer re:form development work remains separate from this release.
- Keep exactly one Compat JAR on each side and restart Minecraft/server after updating startup switches. Existing explicit opt-outs, saved worlds, generation settings, and configuration ownership remain authoritative. No world reset or retrogen is required by these changes.
- Use the matching modpack's managed lantern bundle. Standalone Compat does not contain the pack's models, profiles, textures, or resource-only photograph/item handoffs. A missing or mismatched bundle does not activate partial lantern resources.
- Outside the modpack, the tested Curios/Accessories/layer combination still requires `accessories_compat_layer.curios_bindings=true` on both sides; see [installation](README.md#installation).

## Qualification and limits

- Implementation checks pass 554 Java and 24 tooling cases, including released-target transformation, optional/disabled/server admission, package, and upstream checks. [Verification](docs/development/verification.md#animation-maintenance-0450-preparation) retains exact preparation artifacts, native scope, and delivery checks.
- Actual OFF/default-ON controls preserve native attack input, one seven-damage server hit, release, and inactive animation without extra hits through 100 ticks. Original FF arm-consumer controls qualify canonical filtering separately from natural bow gameplay. Final-model and composed-arm comparisons support the posture repair; they do not establish every weapon, moving combat, or authenticated multiplayer.
- Lantern controls cover both IDs, both arm settings and equipment hands, both views, removal/re-equipment, native resource reload, Fluid & Foolish selected/unselected, and explicit opt-out. Geometry follows the actual native physics pivot; native motion is retained. Moving-player, shader, remote, equipped-lantern, and arbitrary foreign-provider action matrices remain outside this bounded check.
- Glider geometry/tint and shader OFF/BSL/Complementary-Euphoria controls retain their original artifact attribution. Construct repairs carry the native material-switch, CPU/GPU transform, raster, translucent-pass, and GL-state evidence. No complete C5, final PBR/refraction/shadow, universal model, or sustained-performance claim is made. Photon supported-GPU acceptance remains deferred.
- Native-use arms, selfie and other third-person pose conflicts, ordinary DW14, the Beyond-first force handoff, Longshot, and remaining audited corrections keep their separate workstreams. Existing intermittent native chunk-unload negatives remain unresolved; later positive fixtures do not establish a causal repair.

See the generated [changelog](CHANGELOG.md), [configuration matrix](docs/configuration.md#adaptation-switches), and [animation contract](docs/development/animation.md).
