# Skyweave: Compatibility Patches 0.26.0

Skyweave islands gain mixed granular perimeters and an intact-core Disintegrating
profile. Fragment sizes and local reach vary; outer fragments shrink and become sparser.
Detached fragments have their own tapered depth; the existing main
bodies, sparse placement and geological frame retain their roles. Deep Dark
selection rises to 10% of eligible native underground hosts, without guaranteeing
an Ancient City.

Optional Dynamic Waters 13.1.0 supplies island-aware mountain rivers, basin lakes
and edge waterfalls. The bridge preserves supported island rock, excludes empty
sky and native ocean habitats from carving, and keeps post-carver repair from
building foundations across outlets. A separate optional Sable adapter repairs
the dedicated-server current bridge. Sky Archipelago's native rivers and lakes
default off; Dynamic Waters requires VANILLA lowland mode and mountain tunnels off.

Climate-sensitive island queries use reference Y64, including native weather and
Iris precipitation. Native clouds move to Y469.3333. Marked worlds omit native
height-triggered void fog and the black underside sky. Matching versioned shader
recipes correct supported Complementary/Euphoria and BSL/Colorwheel environment
heuristics while retaining real cave, underwater and status effects.

The island locator now continues automatically within its finite search budget,
with concise progress, cancellation and clickable pages. Coordinates suggest a
teleport command for review rather than executing it. Configuration exposes the
new settings through existing grouped editors, supports validated TOML string
lists, and preserves generation drafts instead of saving them beneath a running
singleplayer world.

**Terrain changes require a fresh world.** Saved identity checks prevent silent
conversion; existing LOD databases are not a migration path. Rendering adaptations
remain independently configurable and physically client-sided.

Verification includes 219 Java tests, native generation/fluid ticks/reopening,
minimum and full-pack loading, climate/environment controls, a bounded DH job,
BlueMap selection/lifecycle and configuration save protection. Sable checks cover
current submission, not every vessel's sailing behavior. Unknown shaders, Photon
on Apple hardware and all Create-machine/fluid-rendering combinations are not
certified. The full-pack all-disabled control retains an upstream Sky Archipelago/
ZFastNoise conflict; default full-pack and standalone loading pass.

See [configuration](https://github.com/mannie-exe/skyweave-compat/blob/v0.26.0/docs/configuration.md),
[terrain](https://github.com/mannie-exe/skyweave-compat/blob/v0.26.0/docs/development/terrain.md)
and [verification](https://github.com/mannie-exe/skyweave-compat/blob/v0.26.0/docs/development/verification.md)
for controls, exact tested boundaries and dependency-update considerations.
