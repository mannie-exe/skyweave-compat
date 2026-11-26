# Skyweave: Compatibility Patches 0.43.3

Install with Minecraft 1.21.1, Java 21, NeoForge 21.1.265 or later in the stable 21.1 series, and [re:form 0.3.0](https://github.com/mannie-exe/reform/releases/tag/v0.3.0). Use the same Compat version on client and server. This release collects the qualified changes on main since 0.42.1.

## Gameplay and world generation

- Coordinate Punchy! 3.2b's first-person bow mesh with Modefite 1.0.1's actual nocking event. Preserve native charge, release, ammunition, durability, handedness, and ordinary third-person/model queries. Refined Tools remains a separately installed resource pack.
- Apply the owning island's temperature to Snow! Real Magic! 12.2.7 and Snow Under Trees 1.7 consumers. Preserve native cold-biome snowlogging, physical placement, and the configured melting option. Existing personal snow is not removed or regenerated.
- Preserve authored Throwable Rope Connector 0.4.3 launcher ranges when Simulated's server configuration is not loaded yet, while retaining the actual loaded server ceiling.
- Reuse terrain query and style wrappers without changing their native decisions.

## Delivery quests and structure search

- Enable the independently optional `delivery.structure_search` repair for Aeronautics Delivery Quests 1.1.1 in the active island preset. Bound native structure-status admission and run locator continuations away from the server thread. Preserve native route radius, retry order, templates, RNG, rewards, landing validation, and endpoint custody.
- Retire search jobs and owned tickets on failure, expiry, logout, world unload, and server shutdown. Late results cannot publish into a successor job; cancelling a private waiter leaves the native engine's shared future alone.
- Let manual structure searches continue through the chosen finite radius without a whole-search time limit or cumulative memory-batch cutoff. Retain bounded background work, exact spill deduplication, native/saved-start verification, progress, paging, and cancellation. Island search is unchanged. Unsupported or unavailable coverage remains partial, rather than proof of absence.

## Visuals, controls, and settings

- Adopt public re:form 0.3.0 with responsive grouped settings, measured field relationships, retained drafts, document actions, and guarded reference help and previews. The future 0.4 configuration redesign is separate work.
- Preserve opaque-zero lighting samples and order initial C2ME client-light checks correctly on the production loader. Keep the separately configurable Curios/Accessories break-callback repair.
- Admit Grassrise 2.0.0's known generated shader families through verified recipe provenance, preserving upstream transformation guards and output filenames.
- Adopt the qualified Euphoria 1.11.2 recipe baseline, Aeronautics/Simulated 1.3.3 APIs, and Punchy! Moves 1.0.9b modifier and Touch'n Motion boundaries. Retire the superseded balloon hook.

## Documentation and licensing

- Correct installation and dependency guidance, verification navigation, and the distinction between installed adapters and the proposed Longshot design.
- Credit upstream content and implementation authors. Original project work now uses Apache-2.0, with incorporated LGPL-3.0-only and MIT portions retaining their notices. Previously published releases retain their distributed terms; see [NOTICE](NOTICE).
- Generate the [changelog](CHANGELOG.md) from commit history. [Verification](docs/development/verification.md) retains exact candidate identities and native limitations.

## Updating and qualification limits

- Replace the old Compat and re:form JARs; install exactly one of each. Explicit startup-switch opt-outs remain authoritative. Restart both sides after changing JARs or startup switches. Keep saved generation settings and world identities unchanged when updating an existing world.
- If installing the tested Curios/Accessories/layer combination outside the modpack, explicitly enable `accessories_compat_layer.curios_bindings=true` on both sides before startup; see [installation](README.md#installation).
- The delivery repair passes useful integrated and dedicated routes, persistence, cargo/reward, cancellation, fault, and successor controls on the tested Zfastnoise-free cohort. A 4 GB full-cohort run exhausted heap after publishing its route; whole-run acceptance used 6 GB. This is not a general heap guarantee or sustained modpack speedup.
- Bow and snow checks cover the actual native consumers and retained saved-state controls, not every resource pack, biome/weather combination, or shader. Photon supported-GPU visual/performance acceptance remains deferred.
- This release does not adopt DW14, the unfinished DH lifecycle/network repairs or shadows, the Beyond-first force handoff, or the future re:form 0.4 migration. Longshot's proposed loading and interaction policies remain unimplemented on this release line.
