# Skyweave: Compatibility Patches 0.33.0

Unify island ownership, underground climate and village terrain support on Sky Archipelago 1.4. Minecraft remains 1.21.1; NeoForge remains 21.1.265.

- Use actual height-aware solid, water and cavity contributors for native underground-biome depth and island-relative temperature. Keep resource geology, physical positions and thermal altitude distinct.
- Synchronize bounded server-owned climate context for client weather and the qualified Iris precipitation path. Preserve cold biomes, native fluid/light rules and client lifecycle cleanup.
- Restore bounded ground-village terrain blending, original natural foundation materials and shallow physical contact. Respect processed output, authored damage, roads, water, cavities and overlapping ownership; verify required contacts after feature closure.
- Require complete support for inhabited buildings by default and try bounded alternatives without changing family allowances. Unsafe historical layouts may be rejected. Opt-in diagnostics distinguish support failures, missing information and exhausted work.
- Protect qualified village terrain from conflicting native feature writes while retaining unrelated features. Preserve intentional gaps and reject unexplained source changes.
- Give special Deep Dark islands a deepslate body, coherent sculk surface, sheltered cavern openings and separately bounded devices. Preserve player farming and brought animals. Ancient Cities remain independently eligible; none is forced on the special variant.
- Adapt native elevated-water habitat and movement rules, Dynamic Waters floe climate and cached-node heights. Add independent optional Sable parent-world temperature projection and repair dedicated DW/Sable reflection without replacing native force math.
- Accept the two exact qualified Fabric/Connector version spellings for Surveyor and Hoofprint, retaining strict rejection of other versions.

Generation uses a new saved identity. Create a fresh marked world; existing structures are not relocated or backfilled. Do not bypass an old world's identity marker. Update matching client/server binaries and authored configuration together.

Native evidence covers bounded processed foundation contacts, material provenance, alternative village fitting, save/reopen, reordered generation, climate ownership and transport, elevated-water behavior and optional/disabled controls. A packaged Prism client with the full resource stack verifies integrated reopening, both generated BSL/Colorwheel and Complementary/Euphoria shaders, precipitation uniforms and climate forms. Independent read-only implementation review found no blocking defect in its inspected boundaries. See the [verification record](docs/development/verification.md) for exact artifacts and limits.

These checks do not certify every donor road, a worldwide encounter rate or arbitrary modded feature writers. Matched support-enabled/disabled connector checks retain the same observed reachability. Euphoria's existing file watcher can warn about an unrelated BSL output during an explicit in-process rebuild; this does not prevent the tested shaders from rendering.
