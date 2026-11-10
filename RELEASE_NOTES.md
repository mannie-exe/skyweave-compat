# Skyweave: Compatibility Patches 0.31.0

Add native Wayfinder sight teleportation for operators. Minecraft remains 1.21.1;
NeoForge remains 21.1.265. Existing worlds and generation identity are preserved.

- Press G or use `/skyweavecompat wayfinder` to target ordinary terrain. Rebind
  through Minecraft Controls; chat and other screens suppress the hotkey.
- Optionally use Distant Horizons 3.3.3 data beyond native view distance, with
  progressive bounded reads and no configured distance cap. Missing data stops
  the query rather than inventing a destination.
- Validate permission, current connection, sight ray, actual collision support,
  player bounds and native teleport hooks before arrival. Unsafe or changed
  destinations leave the player in place. No vehicle or moving-construct warp
  support is added; Sable 2.0.6 frame guards reject unsupported constructs.
- Configure access, unsafe blocks/tags, cooldown and bounded query work under
  Maps → Skyweave: Compatibility Patches → Wayfinder. Operator level 2 and a
  two-second successful-warp cooldown are the defaults.
- Keep unauthorized hotkey presses silent by default. Explicit commands still
  explain denied access; servers can turn off hotkey-warning suppression.

Both native Wayfinder and its optional client DH adaptation default on where
supported. Explicit false switches remain authoritative. Remove the old Wayfinder
datapack when installing the native replacement; preserve unrelated scoreboard
and player data. No world reset is required.

See the development verification guide for exact native/client coverage and
remaining upstream observations. No target-indicator HUD or general navigation
framework is included.
