# Skyweave: Compatibility Patches 0.29.0

Repair construct picking, keep floating villages closer to their islands, and
make fragment materials and discovery terminology consistent.

- Fix the Macaw/Sable picking interaction that could stall rendering after a
  platform assembly. The new optional same-frame adapter defaults on.
- Default Sky Village footprint gaps to 64–128 blocks. Use closer candidate
  sampling and a substantial same-cluster host; retain exact overlap exclusion.
- Separate outline, shoreline and fragment-fringe metadata. Dry Lagoon fragments
  inherit sandy shores; Broken Reef fragments inherit native exposed rock palettes.
  Water membership, river ownership and accepted geometry remain separate.
- Increase ordinary-village and mansion encounter targets to 0.889 and 0.32 per
  1024 eligible terrain chunks. Keep family caps, fitting safeguards and intentional
  ruin variants. These are opportunities, not guaranteed per-island probabilities.
- Align island/structure progress, empty results and paging. Use `shoreline=` and
  `fringe=` island filters, with automatic continuation and `status/page/cancel`.
- Accept canonical configuration schemas only. The shape group is now `fringe`;
  obsolete partial-schema translation and stale Atlas input fallbacks are removed.

**A fresh Skyweave world is required:** generation identity is
`islands_v1-skyarch-1.4-6`; drainage remains `island_watersheds_v4`. Existing worlds
are not silently reinterpreted. Use the current shipped profiles; custom profiles
must supply their canonical fields. No automatic configuration migration is added.

Minecraft remains 1.21.1, Sky Archipelago 1.4 and Dynamic Waters 13.1.0.
See [verification](https://github.com/mannie-exe/skyweave-compat/blob/v0.29.0/docs/development/verification.md)
for native evidence, tested versions and remaining limits. Vanilla `/locate`
remains synchronous; broad searches should use the bounded structure command.
