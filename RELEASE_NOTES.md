# Skyweave: Compatibility Patches 0.25.0

Skyweave islands gain area-preserving elliptical outlines, broad warping and
selectively broken edges. Strength varies deterministically between islands.
Native vertical bodies, sparse cluster spacing, geological depth and independent
NOISE/HOLLOW interiors retain their existing roles.

Mushroom Fields and Badlands can use fractured exteriors. A separate rare,
climate-eligible Deep Dark profile adds vanilla sculk ecology without guaranteeing
an Ancient City. Ordinary city burial, support and cavernization checks remain.

Eligible ocean-biome opportunities now have a 20% admission chance; rejected
opportunities remain void. Lagoon and Broken Reef share the enabled weights.
Lagoon edges select coastal outlets, falling perimeters or native retention;
separate solid spires can accompany ocean anchors. Reef Ring and Dead Ocean Shelf
remain configurable but default off. The continuous global ocean stays disabled.

Operators can use `/skyweavecompat islands search` to inspect bounded island
metadata without generating chunks. Native structure discovery rejects impossible
owned candidates before requesting starts or fitting unrelated families. A bounded
spawn fallback finds actual supported dry ground around fully wet hosts.

All new controls use the existing localized configuration editor. Released Ocean
profiles are accepted without rewriting their original values; startup and editor
validation remain strict. These terrain changes require a fresh world. Existing
worlds are protected by their saved generation identity and are not converted.

See [configuration](https://github.com/mannie-exe/skyweave-compat/blob/v0.25.0/docs/configuration.md), [terrain variation](https://github.com/mannie-exe/skyweave-compat/blob/v0.25.0/docs/development/terrain-variation.md)
and [verification](https://github.com/mannie-exe/skyweave-compat/blob/v0.25.0/docs/development/verification.md) for controls and tested scope.
