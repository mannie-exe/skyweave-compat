# Skyweave: Compatibility Patches 0.27.0

Repair island water continuity, support composed villages and selected YUNG
structures, and keep ocean variants on the normal island sizing/shaping system.
Minecraft remains 1.21.1, with Sky Archipelago 1.4 and Dynamic Waters 13.1.0.

- Rejected river cuts no longer create source shelves in void. Inclusive cave-roof
  support and shallower lake beds prevent the reported dry lake holes. Generated
  channels exclude additive fragments and terminate at physical void sinks.
- Lagoon treatments retain normal host sizes and native floors. Coastal mode
  opens a broad seeded cove with supported shoulders; perimeter falls and native
  Lagoon/Broken Reef habitats remain available. Ocean outlines share the normal
  seeded shaping and external fragment authority.
- Optional Lithostitched integration keeps native assembly for the pack's composed
  CTOV/Better Villages/Towns and Towers profiles, with explicit family membership
  and shared island discovery allowances.
- Optional YUNG integration preserves configured vanilla replacements, projected
  temple water/exclusion rules and supported buried rooms. Mineshaft entrances
  move with their layouts; mineshaft and hut supports stop at the island underside.
  All new adapters default on when their exact dependencies qualify.
- Owned configuration now lives under `config/skyweave/`; shader recipes live in
  `config/skyweave/shaders/recipes/`. Move existing owned files while stopped,
  preserving custom values and resolving conflicting copies before starting.
  Foreign mods retain their native paths. Startup refuses unmigrated settings.

**Terrain changes require a fresh world.** Existing marked saves are not silently
converted. Configuration relocation alone does not invalidate terrain or caches.

Verification covers native water stages, fluid ticks/reopening, composed layouts,
local door/bed support, retained loot, optional loading, registered configuration,
bounded DH generation and BlueMap lifecycle. Finite samples do not certify every
layout, villager pathfinding or survival balance. YUNG monuments retain habitat
checks; no suitable island monument was demonstrated in the bounded sample.
Geophilic's sampled ecology works through existing surface/feature owners.

See [configuration](https://github.com/mannie-exe/skyweave-compat/blob/v0.27.0/docs/configuration.md),
[terrain](https://github.com/mannie-exe/skyweave-compat/blob/v0.27.0/docs/development/terrain.md)
and [verification](https://github.com/mannie-exe/skyweave-compat/blob/v0.27.0/docs/development/verification.md)
for controls, tested boundaries and dependency-update checks.
