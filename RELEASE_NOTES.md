# Skyweave: Compatibility Patches 0.32.0

Repair island-aware village fitting, foundation materials and structure discovery.
Minecraft remains 1.21.1; NeoForge remains 21.1.265.

- Choose structure-base materials from the island surface contributor and its
  natural painted skin. Sandy shores retain sand and sandstone instead of gaining
  generic grass/dirt foundations. Native bounded support filling remains active.
- Check meaningful village footing against completed terrain, retain connected
  inhabited buildings, and try bounded ranked alternatives before accepting a
  trimmed settlement. Use compatible supported native road endings where possible.
- Preserve family rates, weighted village donors, cluster allowances and separate
  floating Sky Villages. New support and work limits are configurable under the
  structure policy's Village placement group.
- Show one locator/map result per physical layout, including source/reference
  aliases. Distinct neighboring settlements remain separate. Native jigsaw reload
  padding no longer changes layout identity or reported reference coordinates.
- Include qualified source-to-layout displacement in radius searches. Unknown
  placement contracts explicitly report partial coverage. Preserve saved-start
  discovery, cancellation and readable empty-result feedback.
- Enforce operator permission on the islands and structures command branches,
  independently of other commands registered under the shared root.
- Keep Wayfinder debug feedback off by default for targeting, results and access
  denial. Replace the separate hotkey-warning setting with this shared policy.
- Disable the operator warp cooldown by default and search up to 16 blocks above
  the targeted block for safe footing, with no downward fallback. Both settings
  remain configurable; existing explicit values are preserved.

Generation behavior changes and uses a new saved generation identity. Existing
structures are not relocated or backfilled. Do not bypass an old world's identity
marker to continue generation under changed rules. Reader-side discovery fixes
require no deletion of structure starts, map records or personal waypoints.

Native checks cover the recorded unsupported villages and material patch, two
matched seed samples, persistence, aliases and locator command behavior. These
bounded samples do not establish a world-wide village encounter rate. See the
verification guide for exact artifacts, results and remaining limits.
