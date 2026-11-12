# Publishing

The GitHub workflow checks/builds on Linux, Windows and Intel macOS. Native Apple Silicon checks are recorded separately. The explicit `macos-15-intel` [runner label](https://docs.github.com/en/actions/reference/runners/github-hosted-runners) keeps the release matrix independent of the ARM runner pool. A `v<mod_version>` tag publishes an initial prerelease only after all jobs succeed and their binary and source JARs match byte for byte. It includes a complete source ZIP and SHA-256/ SHA-512 manifests. A draft becomes public after asset uploads succeed.

Main branch pushes run validation without publishing a release. Documentation changes need no version bump or tag.

Update `mod_version`, release notes and verification evidence before tagging. After the content commit, run `git-cliff --tag v<version> --output CHANGELOG.md` with git-cliff 2.14.2 and commit the generated changelog as release preparation. Never overwrite published release files. The release script accepts `--stage-only` to validate and stage downloaded CI artifacts without calling GitHub releases. Download CI artifacts into `build/ci/build-<runner>/` before staging; `build/release` must not already exist. The tag must resolve to the checked-out commit. No automatic Modrinth upload is configured.

Pending native client checks must remain explicit in release notes and verification. Background checks do not establish GPU or gameplay rendering behavior.

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

Sky Villages declares Architectury API as its own required dependency. Its 13.0.11 release is the tested runtime. Do not upload dependency JARs or the test fixture. Install the same Skyweave: Compatibility Patches version and applicable integrations on client and server. Check client-only mixins and server startup before distributing an update.

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
