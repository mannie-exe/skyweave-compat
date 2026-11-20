# Redstone adapters

The optional adapters target Create: Redstone Link GUI 1.21.1-1.9.2 and Create: Redstone Additions 1.0.6 with Create 6.0.10. Upstream JARs remain unchanged. Terrain generation is not required. Configuration owns the individual switches.

## Link editing and relocation

Server guards cover custom packets and native ghost-slot clicks. They require a building-capable, non-spectating player, a nearby loaded target and vanilla use permission. Mutating packets must match the current menu, its original block entity and its channel. Removed or replaced targets invalidate old menus. Preset and frequency indices are checked before reaching upstream handlers.

With the separate Sable 2.0.6 adapter active, distances use projected world coordinates and menus retain the original construct identity. Plain links can move within the world or within one construct. Cross-frame moves are refused.

Relocation is restricted to `create:redstone_link` without factory-panel connections. Machinery, vector thrusters, and foreign link blocks can still use their applicable frequency menus, but cannot be relocated through this tool. The menu closes before a relocation is submitted; requiring a still-open menu would break that native workflow. The server therefore checks both endpoints again, including range, permissions and the source break event. Upstream retains placement, support and replaceability checks. The default range is 24 blocks.

`RedstoneEditEvent` lets integrations veto edits or either relocation endpoint. It exposes physical and projected positions. These checks do not establish compatibility with every external claims mod. Tiny/Void Link implementations are not in the tested pack; their reflection-based upstream paths remain unverified.

## Crossroad signal scope

Redstone Additions 1.0.6 supplies pure Conjunctor placement, support loss, the unpowered Disjunctor default, settled gate updates, Crossroad feedback attenuation and native Create wrench behavior. It also supplies tool-event Verdigris, nine block-drop tables, Ponder names and cutout models for both inverter states.

Crossroad's native feedback guard temporarily stores a position on the singleton block. An exact `redstone_additions.logic` adapter scopes the read to the querying thread, level and block position. It retains both native input queries and direct-signal attenuation. The [native fixture](testing.md) checks a same-position read in a second dimension while the first read is active.

When upgrading an independently managed configuration, remove the retired `redstone_additions.scraping`, `redstone_additions.wrench` and `redstone_additions.transparency` entries from the startup properties. The pack's managed configuration already omits them.

Recheck Additions' native `getInputSignalIgnoringOwnOutput`, its one guarded direct-signal read and its normal and exceptional cleanup paths before changing version admission. Link GUI hooks depend on exact packet lambda signatures and native menu initialization. Recheck Create ghost slots, Sable projection and client tooltip ownership when those dependencies change.
