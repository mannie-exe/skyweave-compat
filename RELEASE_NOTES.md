# Skyweave: Compatibility Patches 0.44.0

Install with Minecraft 1.21.1, Java 21, NeoForge 21.1.265 or later in the stable 21.1 series, and [re:form 0.4.0](https://github.com/mannie-exe/reform/releases/tag/v0.4.0). Use the same Compat version on client and server. This release preserves the published 0.43.3 gameplay fixes and adds the qualified settings migration and Distant Horizons repairs.

## Configuration

- Use separate Home pages for local files and the running singleplayer server, a stable context header, navigation history, retained drafts, and shared review, inspection, and operation results. Home opens no settings owner until selected; composed pages reuse the existing owners and save paths.
- Preserve native configuration formats, exact numeric input, coupled validation, explicit opt-outs, and operator permissions. Saved settings remain distinct from effective runtime values; saving a next-launch choice does not activate it in the current process.
- Preserve native input ownership. Settings search does not trigger IPN shortcuts, while ordinary inventory, wheel, drag, and keybinding actions retain their native behavior and item custody.

## Distant terrain

- Enable five independently configurable repairs for Distant Horizons 3.3.3: update-queue progress, executor lifetime, client completion, rendering resources, and network requests. Explicit false selections remain authoritative.
- Retire paused work and native subscriptions, refuse new requests during closure, service rendering dependencies during world close, and return settled buffer loans once. Preserve saved formats, completed terrain output, and render fidelity.

## Existing fixes and update procedure

- Preserve the qualified bow handoff, island snow and snowlogging climate, launcher range, delivery-search, lighting, equipment-callback, shader-family, Aeronautics, and Moves adaptations. Their earlier checks retain their original version and input attribution.
- Replace the old Compat and re:form JARs with exactly one of each, and restart both sides. Update any installed Styled consumer to its matching 0.4 library cohort. Do not mix old 0.3-only consumers with the new library.
- Keep saved worlds and generation settings unchanged. Existing startup-switch opt-outs remain authoritative. No retrogen, personal-world cleanup, or automatic configuration reset is part of this update.
- Outside the modpack, the tested Curios/Accessories/layer combination still requires the explicit `accessories_compat_layer.curios_bindings=true` selection on both sides; see [installation](README.md#installation).

## Qualification and limits

- The public-dependency preparation passes 542 Java and 24 tooling checks. Hosted Linux, Windows, and Intel Mac builds agree byte for byte; Windows skips the existing POSIX directory-permission case. Their binary carries the same compiler-generated climate-position bridges already shipped in public 0.43.3; the other 1,097 runtime classes and the source JAR match the local preparation. Minimum dedicated startup passes with the actual hosted binary and public library. A committed-source resolver check uses the public library without a development override, and an anonymous library download matches both published checksum manifests.
- The stable-bound engineering candidate supplies an actual minimum settings initial/reopen pair and an actual two-JVM production-loader Configure/save/reopen pair with the held JEI/EMI providers and final Styled 0.2.0. These results retain their original artifact attribution and the reviewed compiler-class difference described in [verification](docs/development/verification.md).
- The production-pack check covers title/Configure persistence, with Dynamic FPS and shader rendering disabled only in its disposable fixture. It opens no world and does not qualify world unloading, Styled's own interface or previews, shader output, sustained performance, or personal-instance acceptance. Independent provider and consumer evidence remains separate in [verification](docs/development/verification.md).
- Intermittent re:form qualification-fixture failures in Minecraft's native chunk-unload loop remain unresolved, including a no-recipe-viewer case. Later passing cases do not establish a causal repair or universal shutdown reliability.
- Photon supported-GPU visual/performance acceptance remains deferred. This release does not adopt ordinary DW14, the Beyond-first force handoff, Longshot, or unfinished shadow work. Configuration and runtime integration continue in their separate development workstreams.

See the generated [changelog](CHANGELOG.md), [configuration matrix](docs/configuration.md#adaptation-switches), and [verification scope](docs/development/verification.md) for exact switches, provider versions, and retained limitations.
