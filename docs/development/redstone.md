# Redstone adapters

The optional adapters target Create: Redstone Link GUI 1.21.1-1.9.2 and Create: Redstone Additions 1.0.5 with Create 6.0.10. Upstream JARs remain unchanged. Terrain generation is not required. Configuration owns the individual switches.

## Link editing and relocation

Server guards cover custom packets and native ghost-slot clicks. They require a building-capable, non-spectating player, a nearby loaded target and vanilla use permission. Mutating packets must match the current menu, its original block entity and its channel. Removed or replaced targets invalidate old menus. Preset and frequency indices are checked before reaching upstream handlers.

With the separate Sable 2.0.6 adapter active, distances use projected world coordinates and menus retain the original construct identity. Plain links can move within the world or within one construct. Cross-frame moves are refused.

Relocation is restricted to `create:redstone_link` without factory-panel connections. Machinery, vector thrusters, and foreign link blocks can still use their applicable frequency menus, but cannot be relocated through this tool. The menu closes before a relocation is submitted; requiring a still-open menu would break that native workflow. The server therefore checks both endpoints again, including range, permissions and the source break event. Upstream retains placement, support and replaceability checks. The default range is 24 blocks.

`RedstoneEditEvent` lets integrations veto edits or either relocation endpoint. It exposes physical and projected positions. These checks do not establish compatibility with every external claims mod. Tiny/Void Link implementations are not in the tested pack; their reflection-based upstream paths remain unverified.

## Gates and scraping

Input queries no longer place Conjunctors into the world. Placement records partial inputs, steady inputs do not perpetually reschedule ticks, and output changes notify neighbors. Crossroads read both axes without feeding their own strong output back into their inputs. Strong output into solid blocks loses one signal level, allowing feedback loops to decay; ordinary wire behavior is preserved. This distinction matters when designing analog circuits.

Unsupported gates use vanilla support-loss handling. Disjunctors begin unpowered. The independent wrench adapter uses Create's rotation and recovery interactions, including neighbor updates when a gate turns.

Verdigris is awarded only for a non-simulated axe scrape with an actual copper weathering transition. A canceled modification awards nothing. Yield remains 3–6; normal block-drop rules still apply. This replaces the upstream early right-click listener, which could award material before a successful scrape. The client transparency adapter gives the lit inverter its cutout render layer.

## Resource ownership and updates

Loot tables and missing Ponder tag translations belong to the installing pack. Version 1.0.5 stores block tables under the pre-1.21 directory; provide the nine ordinary self-drops under `data/create_redstone_additions/loot_table/blocks/`. Skyweave: Compatibility Patches does not distribute upstream models, schematics or language files.

Review [upstream repair PR #6](https://github.com/SiliconCarbideCube/create-redstone-additions/pull/6) when updating Additions. Its placement, feedback, support and scraping findings informed these independently authored hooks; this is not a bundled fork. Compare actual signal semantics and remove overlaps individually after native verification. Link GUI hooks also depend on exact published packet lambda signatures and menu initialization; source filenames alone do not establish them. Recheck Create ghost behavior, Sable projection and client widget tooltip ownership when any of those dependencies changes.
