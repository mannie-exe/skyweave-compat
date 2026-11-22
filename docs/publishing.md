# Publishing

The GitHub workflow checks/builds on Linux, Windows and Intel macOS. Native Apple Silicon checks are recorded separately. The explicit `macos-15-intel` [runner label](https://docs.github.com/en/actions/reference/runners/github-hosted-runners) keeps the release matrix independent of the ARM runner pool. A `v<mod_version>` tag publishes an initial prerelease only after all jobs succeed and their binary and source JARs match byte for byte. It includes a complete source ZIP and SHA-256/ SHA-512 manifests. A draft becomes public after asset uploads succeed.

Main branch pushes run validation without publishing a release. Documentation changes need no version bump or tag.

Update `mod_version`, release notes and verification evidence before tagging. After the content commit, run `git-cliff --tag v<version> --output CHANGELOG.md` with git-cliff 2.14.2 and commit the generated changelog as release preparation. Never overwrite published release files. The release script accepts `--stage-only` to validate and stage downloaded CI artifacts without calling GitHub releases. Download CI artifacts into `build/ci/build-<runner>/` before staging; `build/release` must not already exist. The tag must resolve to the checked-out commit. No automatic Modrinth upload is configured.

Pending native client checks must remain explicit in release notes and verification. Background checks do not establish GPU or gameplay rendering behavior.

## Shared settings dependency

Compat builds against the exact `design.inherent:reform` version in `gradle.properties`. Its portable build resolves the combined release JAR through the artifact-only GitHub Ivy pattern declared in `build.gradle`. Installed sides may use an admitted stable 0.1 release from that baseline; [dependency admission](development/dependencies.md) owns that runtime contract. The game supplies the serialization dependencies; do not bundle re:form classes or its development core JAR. Gradle and packaged development metadata must agree with the qualified build artifact. The modpack continues selecting one exact qualified library on both sides.

An explicit `-Preform_repository=<staged-maven-repository>` selects an owned development candidate. Keep machine-local paths out of tracked configuration and release CI. Before publication, verify a clean consumer checkout against the intended immutable release filename and compare its digest. A local mirror verifies resolution mechanics and candidate bytes; it does not prove public release availability. Publish re:form before a Compat release that requires it, then update the modpack's two-sided dependency set. No build or local candidate test authorizes publication or personal-instance rollout.

Run `python3 tools/reform_resolution_probe.py --public --library <downloaded-release.jar> --output build/reform-resolution.json` after committing consumer inputs. It extracts that exact clean revision, asserts the configured public release repository and absence of a development override, then verifies the actual resolved coordinate and digest without a local mirror. Compare the downloaded JAR with the published checksum too. Omitting `--public` retains the loopback fixture for unpublished trials; it verifies resolver mechanics, not public availability. Gradle may normalize a cache filename; compare the configured immutable release path and bytes instead of equating that cache name with the published filename.

When updating re:form, requalify current format/validation boundaries, required generation/shape scopes, native callbacks, live client and integrated policy effects, retained drafts/receipts, keybindings and operator/search lifecycles. Keep Compat's canonical domain definitions and native feature gates. The [configuration development guide](development/configuration.md) owns the affected adapters and [verification](development/verification.md#shared-settings-owners) records exact qualified artifacts.

## Modrinth selections

Modrinth publication is deferred. Use these fields if publication is approved:

| Field        | Selection                                                                               |
| ------------ | --------------------------------------------------------------------------------------- |
| Project type | Mod                                                                                     |
| Name         | Skyweave: Compatibility Patches                                                         |
| Slug         | `skyweave-compat`                                                                       |
| Summary      | Optional terrain, gameplay, and rendering adaptations with independent startup switches |
| Category     | World Generation; Utility as an optional second category                                |
| Environment  | Required on client and server for the expanded preset                                   |
| License      | MIT AND LGPL-3.0-only; see NOTICE                                                       |
| Source       | `https://github.com/mannie-exe/skyweave-compat`                                         |
| Issues       | `https://github.com/mannie-exe/skyweave-compat/issues`                                  |
| Description  | README installation, placement and configuration sections                               |

For the current prerelease, select **Beta**, **Minecraft 1.21.1**, and **NeoForge**. Upload `skyweave-compat-<version>-mc1.21.1-neoforge.jar` as the primary file. Declare all integrated content mods optional, including Sky Archipelago. Use the [configuration matrix](configuration.md#adaptation-switches) for supported versions and multi-mod requirements instead of maintaining another dependency list.

Sky Villages declares Architectury API as its own required dependency. Its 13.0.12 release is the tested runtime. Do not upload dependency JARs or the test fixture. Install the same Skyweave: Compatibility Patches version and applicable integrations on client and server. Check client-only mixins and server startup before distributing an update.

## Upload process

1. After publication eligibility is resolved, create the draft project and fill the fields above. Use the README's player-facing sections for its description, with working project links.
2. Upload the tested GitHub release binary, select the exact dependency versions, and paste the matching release notes. A human-created project icon can be added.
3. Review the draft and submit it for moderation when ready.
4. Download the published primary file and compare its SHA-512 with the release manifest before referencing it in a pack.

For future automation, use a scoped repository-secret token and upload the tested release artifact through the version API. Keep tokens outside tracked files. These instructions describe publication; they do not establish moderation approval.

References: [project creation](https://docs.modrinth.com/api/operations/createproject/), [version uploads](https://docs.modrinth.com/api/operations/createversion/), [environment selections](https://modrinth.com/news/article/new-environments/).

## Authorship and publication eligibility

The implementation and documentation contain AI-generated contributions. There is no runtime AI functionality or AI-generated artwork. Preserve accurate code and text disclosures; rewriting documentation does not alter implementation provenance.

Modrinth's [content rules](https://modrinth.com/legal/rules), checked 2026-10-25, restrict public projects primarily composed of generated content. Publication eligibility remains unresolved and must be settled before submission. Disclosure alone is not approval. See the [disclosure guidance](https://support.modrinth.com/en/articles/16567675-content-disclosures).
